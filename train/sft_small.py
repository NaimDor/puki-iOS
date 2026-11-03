"""Fine-tunes Puki's own small model (the pretrain, pt_run.py) on Puki's lines: the answer tokens only,
from data/final.jsonl without the phonemes (the reading model writes those).

    python sft_small.py --ckpt C:/data/pretrain/puki-runs/puki-48m/ckpt.pt --run sft-1

Every epoch: the answer-token loss on reference-heldout (the same set and numbers the pretrain logged),
then samples for held-out inputs: how many pass Puki's own filter (persona.clean, safety.reply_ok), the
safety gate's accuracy, and a few lines to read. Runs go to C:/data/pretrain/puki-sft.
"""
import argparse
import collections
import hashlib
import json
import math
import os
import random
import re
import sys
import time
from pathlib import Path

import torch
import torch.nn.functional as F

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from pt_corpus import LANG, strip  # noqa: E402


def load_model(ckpt, device):
    from transformers import LlamaConfig, LlamaForCausalLM
    ck = torch.load(ckpt, map_location="cpu")
    model = LlamaForCausalLM(LlamaConfig(**ck["config"]))
    model.load_state_dict({k.removeprefix("_orig_mod."): v for k, v in ck["model"].items()})
    return model.to(device)


def examples(tok, rows, eot, seq):
    """(ids, where the answer starts) for each row, cut to seq tokens."""
    out = []
    for r in rows:
        prompt, _, answer = strip(r["text"]).partition("\npuki:")
        p = tok.encode(prompt + "\npuki:").ids
        a = tok.encode(answer).ids + [eot]
        ids = (p + a)[-seq:]
        out.append((ids, max(0, len(ids) - len(a))))
    return out


def batch_of(items, device):
    n = max(len(i) for i, _ in items)
    x = torch.zeros(len(items), n, dtype=torch.long)
    mask = torch.zeros(len(items), n, dtype=torch.bool)
    for row, (ids, start) in enumerate(items):
        x[row, :len(ids)] = torch.tensor(ids)
        mask[row, start:len(ids)] = True
    return x.to(device), mask.to(device)


def answer_loss(model, x, mask):
    with torch.autocast("cuda", dtype=torch.bfloat16, enabled=x.is_cuda):
        logits = model(input_ids=x).logits[:, :-1].float()
    lp = F.cross_entropy(logits.transpose(1, 2), x[:, 1:], reduction="none")
    m = mask[:, 1:]
    return (lp * m).sum() / m.sum().clamp(min=1)


@torch.no_grad()
def reference_loss(model, refs, device):
    model.eval()
    tot = n = 0.0
    for s in range(0, len(refs), 32):
        part = [(r["ids"], r["start"]) for r in refs[s:s + 32]]
        x, mask = batch_of(part, device)
        with torch.autocast("cuda", dtype=torch.bfloat16, enabled=x.is_cuda):
            logits = model(input_ids=x).logits[:, :-1].float()
        lp = F.cross_entropy(logits.transpose(1, 2), x[:, 1:], reduction="none")
        m = mask[:, 1:]
        tot += float((lp * m).sum())
        n += float(m.sum())
    model.train()
    return tot / max(n, 1)


@torch.no_grad()
def samples(model, tok, rows, eot, device, n, seed):
    """[(row, line)] generated for n held-out inputs (temperature 0.7, top-p 0.9)."""
    import fmt
    model.eval()
    rng = random.Random(seed)
    picked = rng.sample(rows, min(n, len(rows)))
    out = []
    torch.manual_seed(seed)
    for r in picked:
        prompt = fmt.prompt(r["task"], r["lang"], r["girl"], r["input"], r.get("earlier"))
        ids = torch.tensor([tok.encode(prompt).ids], device=device)
        gen = model.generate(ids, max_new_tokens=48, do_sample=True, temperature=0.7, top_p=0.9,
                             eos_token_id=eot, pad_token_id=eot)
        text = tok.decode(gen[0, ids.shape[1]:].tolist()).replace("<|endoftext|>", "").strip()
        out.append((r, text))
    model.train()
    return out


TRUTH = re.compile(r"\((?:באמת|really): ([^)]+)\)")


def judge_samples(made):
    """How many pass Puki's filter (by task), the gate's accuracy, and the truth checks the builder keeps
    lines by: a Silly Puki correction says the real answer, the silly line itself doesn't, a recap names
    two or more of the day's things."""
    import build
    import games
    passed, total, gate_right, gate_n = collections.Counter(), collections.Counter(), 0, 0
    true_, checked = collections.Counter(), collections.Counter()
    for r, line in made:
        if r["task"] == "gate":
            gate_n += 1
            gate_right += line[:1] == r["line"].strip()[:1]
            continue
        total[r["task"]] += 1
        passed[r["task"]] += build.puki_filter(line, r["lang"], r["girl"]) is not None
        truth = TRUTH.search(r["input"])
        if r["task"] in ("silly", "silly_fix") and truth:
            checked[r["task"]] += 1
            true_[r["task"]] += build.said_truth(line, truth.group(1).strip()) == (r["task"] == "silly_fix")
        elif r["task"] == "recap":
            checked["recap"] += 1
            true_["recap"] += games.events_named(line, r["input"]) >= 2
    rate = sum(passed.values()) / max(sum(total.values()), 1)
    truths = {t: f"{true_[t]}/{checked[t]}" for t in checked}
    return rate, {t: f"{passed[t]}/{total[t]}" for t in total}, gate_right / max(gate_n, 1), gate_n, truths


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", required=True)
    ap.add_argument("--run", required=True)
    ap.add_argument("--tok", default=r"C:\data\pretrain\puki-tok")
    ap.add_argument("--data", nargs="+", default=[str(HERE / "data" / "final.jsonl")],
                    help="training files (build.py's top-ups too; final.py's known text errors are left out)")
    ap.add_argument("--heldout", default=str(HERE / "data" / "final_heldout.jsonl"))
    ap.add_argument("--out", default=r"C:\data\pretrain\puki-sft")
    ap.add_argument("--epochs", type=int, default=3)
    ap.add_argument("--batch", type=int, default=64)
    ap.add_argument("--lr", type=float, default=3e-4)
    ap.add_argument("--seq", type=int, default=256)
    ap.add_argument("--samples", type=int, default=200)
    ap.add_argument("--truth-samples", type=int, default=400,
                    help="also this many held-out silly, silly_fix and recap inputs (every round's), for the truth checks")
    ap.add_argument("--steps", type=int, default=0, help="stop after this many steps (a quick try)")
    ap.add_argument("--eval-only", action="store_true", help="score --ckpt (a fine-tuned best.pt too) and stop")
    ap.add_argument("--seed", type=int, default=0)
    a = ap.parse_args()
    from tokenizers import Tokenizer
    torch.manual_seed(a.seed)
    rng = random.Random(a.seed)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    tok = Tokenizer.from_file(str(Path(a.tok) / "tokenizer.json"))
    eot = tok.token_to_id("<|endoftext|>")
    from final import BAD_TEXT
    rows = [r for name in a.data for r in map(json.loads, open(name, encoding="utf-8")) if not BAD_TEXT.search(r.get("line", ""))]
    held_rows = [json.loads(l) for l in open(a.heldout, encoding="utf-8")]
    # The pretrain's held-out half (pt_corpus.references): the same inputs it was scored on
    held_rows = [r for r in held_rows if int(hashlib.sha1(r["input"].encode()).hexdigest()[:8], 16) % 2]
    refs = json.loads((Path(a.tok) / "reference-heldout.json").read_text(encoding="utf-8"))
    # (train_heldout is final_heldout before final.py; the pilots and old versions aren't held-out sets of their own)
    truth_rows = [r for name in sorted((HERE / "data").glob("*_heldout.jsonl")) if not name.name.startswith(("pilot", "train_", "look_pilot"))
                  for r in map(json.loads, open(name, encoding="utf-8")) if r["task"] in ("silly", "silly_fix", "recap")]
    if LANG:  # a one-language model (PUKI_LANG, as in pt_corpus): only its lines
        rows, held_rows, truth_rows = ([r for r in x if r["lang"] == LANG] for x in (rows, held_rows, truth_rows))
    train = examples(tok, rows, eot, a.seq)
    model = load_model(a.ckpt, device)
    print(f"{a.run}: {sum(p.numel() for p in model.parameters()) / 1e6:.1f}M params, {len(train)} examples, "
          f"{len(refs)} reference answers, {len(held_rows)} held-out inputs to sample", flush=True)
    per_epoch = math.ceil(len(train) / a.batch)
    total = a.steps or a.epochs * per_epoch
    opt = torch.optim.AdamW(model.parameters(), lr=a.lr, weight_decay=0.1, betas=(0.9, 0.95))
    warm = min(100, total // 10)
    sched = torch.optim.lr_scheduler.LambdaLR(opt, lambda s: min(1, (s + 1) / warm) * 0.5 * (1 + math.cos(math.pi * min(s, total) / total)))
    out = Path(a.out) / a.run
    out.mkdir(parents=True, exist_ok=True)
    log = open(out / "log.jsonl", "a", encoding="utf-8")

    def evaluate(step):
        loss = reference_loss(model, refs, device)
        made = samples(model, tok, held_rows, eot, device, a.samples, a.seed)
        rate, by_task, gate, gate_n, _ = judge_samples(made)
        # The truth checks on many more inputs: a few dozen can't show a change of a few points
        _, _, _, _, truths = judge_samples(made + samples(model, tok, truth_rows, eot, device, a.truth_samples, a.seed + 1))
        row = {"step": step, "epoch": round(step / per_epoch, 2), "ref_heldout": loss, "filter_pass": rate,
               "by_task": by_task, "gate": gate, "gate_n": gate_n, "truth": truths, "time": time.strftime("%H:%M")}
        log.write(json.dumps(row, ensure_ascii=False) + "\n")
        log.flush()
        print(f"eval {row['epoch']} ep: held-out answer loss {loss:.3f} | filter pass {rate:.1%} | "
              f"gate {gate:.1%} of {gate_n} | truth {truths}", flush=True)
        with open(out / f"samples-{step}.txt", "w", encoding="utf-8") as f:
            for r, line in made:
                f.write(f"[{r['task']} {r['lang']} {'girl' if r['girl'] else 'boy'}] {r['input']}\n   model  {line}\n   data   {r['line']}\n")
        return loss

    best = evaluate(0)
    if a.eval_only:
        return
    torch.save({"model": model.state_dict(), "config": model.config.to_dict()}, out / "best.pt")
    order, pos, t0 = list(range(len(train))), len(train), time.time()
    for step in range(1, total + 1):
        if pos + a.batch > len(order):
            rng.shuffle(order)
            pos = 0
        x, mask = batch_of([train[i] for i in order[pos:pos + a.batch]], device)
        pos += a.batch
        loss = answer_loss(model, x, mask)
        opt.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        opt.step()
        sched.step()
        if step % 50 == 0:
            print(f"step {step}/{total} loss {loss.item():.3f} {step * a.batch / (time.time() - t0):.0f} ex/s", flush=True)
        if step % per_epoch == 0 or step == total:
            got = evaluate(step)
            if got < best:
                best = got
                torch.save({"model": model.state_dict(), "config": model.config.to_dict()}, out / "best.pt")
    print(f"done: best held-out answer loss {best:.3f}")


if __name__ == "__main__":
    main()
