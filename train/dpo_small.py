"""Preference tuning (DPO) for Puki's small model, after sft_small.py: the fine-tuned model learns to prefer
the line the builder kept over the one it turned down (dpo_pairs.py), against a frozen copy of itself, with
a little of the plain fine-tuning loss on the kept line so the language holds.

    python dpo_small.py --sft C:/data/pretrain/puki-sft/sft-10k/best.pt --run dpo-1

Scored as sft_small.py scores (held-out answer loss, Puki's filter, the gate, the truth checks), before
and after, plus how often it prefers the kept line on pairs it never trained on.
"""
import argparse
import copy
import json
import math
import random
import sys
import time
from pathlib import Path

import torch
import torch.nn.functional as F

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import fmt  # noqa: E402
import sft_small as S  # noqa: E402


def encode(tok, pair, eot, seq):
    """(prompt+chosen ids, prompt+rejected ids, where the answers start)."""
    p = tok.encode(fmt.prompt(pair["task"], pair["lang"], pair["girl"], pair["input"], pair.get("earlier"))).ids
    good = tok.encode(" " + pair["chosen"]).ids + [eot]
    bad = tok.encode(" " + pair["rejected"]).ids + [eot]
    p = p[-(seq - max(len(good), len(bad))):]
    return p + good, p + bad, len(p)


def answer_logps(model, items, device):
    """Each item's summed log-probability of its answer tokens, and the token count."""
    x, mask = S.batch_of(items, device)
    with torch.autocast("cuda", dtype=torch.bfloat16, enabled=x.is_cuda):
        logits = model(input_ids=x).logits[:, :-1].float()
    lp = -F.cross_entropy(logits.transpose(1, 2), x[:, 1:], reduction="none")
    m = mask[:, 1:]
    return (lp * m).sum(1), m.sum(1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sft", required=True, help="the fine-tuned model to start from (sft_small.py's best.pt)")
    ap.add_argument("--run", required=True)
    ap.add_argument("--pairs", default=str(HERE / "data" / "dpo_pairs.jsonl"))
    ap.add_argument("--kinds", default="filter,judge", help="which turned-down lines to learn from")
    ap.add_argument("--tok", default=r"C:\data\pretrain\puki-tok")
    ap.add_argument("--heldout", default=str(HERE / "data" / "final_heldout.jsonl"))
    ap.add_argument("--out", default=r"C:\data\pretrain\puki-sft")
    ap.add_argument("--steps", type=int, default=600)
    ap.add_argument("--batch", type=int, default=32)
    ap.add_argument("--lr", type=float, default=1e-5)
    ap.add_argument("--beta", type=float, default=0.1)
    ap.add_argument("--sft-weight", type=float, default=0.2, help="how much of the plain loss on the kept line")
    ap.add_argument("--seq", type=int, default=256)
    ap.add_argument("--samples", type=int, default=200)
    ap.add_argument("--seed", type=int, default=0)
    a = ap.parse_args()
    from tokenizers import Tokenizer
    import hashlib
    torch.manual_seed(a.seed)
    rng = random.Random(a.seed)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    tok = Tokenizer.from_file(str(Path(a.tok) / "tokenizer.json"))
    eot = tok.token_to_id("<|endoftext|>")
    kinds = set(a.kinds.split(","))
    pairs = [p for p in map(json.loads, open(a.pairs, encoding="utf-8")) if p["kind"] in kinds]
    rng.shuffle(pairs)
    val, pairs = pairs[:500], pairs[500:]  # (pairs it never trains on, to check the preference took)
    enc = [encode(tok, p, eot, a.seq) for p in pairs]
    val_enc = [encode(tok, p, eot, a.seq) for p in val]
    held_rows = [json.loads(l) for l in open(a.heldout, encoding="utf-8")]
    held_rows = [r for r in held_rows if int(hashlib.sha1(r["input"].encode()).hexdigest()[:8], 16) % 2]
    refs = json.loads((Path(a.tok) / "reference-heldout.json").read_text(encoding="utf-8"))
    policy = S.load_model(a.sft, device)
    reference = copy.deepcopy(policy).eval()
    for p in reference.parameters():
        p.requires_grad_(False)
    print(f"{a.run}: {len(enc)} pairs ({','.join(sorted(kinds))}), {len(val_enc)} to check, beta {a.beta}", flush=True)
    out = Path(a.out) / a.run
    out.mkdir(parents=True, exist_ok=True)
    log = open(out / "log.jsonl", "a", encoding="utf-8")

    @torch.no_grad()
    def prefers():
        policy.eval()
        right = 0
        for s in range(0, len(val_enc), 64):
            part = val_enc[s:s + 64]
            good, _ = answer_logps(policy, [(g, n) for g, _, n in part], device)
            bad, _ = answer_logps(policy, [(b, n) for _, b, n in part], device)
            right += int((good > bad).sum())
        policy.train()
        return right / len(val_enc)

    truth_rows = [r for name in sorted((HERE / "data").glob("*_heldout.jsonl")) if not name.name.startswith(("pilot", "train_", "look_pilot"))
                  for r in map(json.loads, open(name, encoding="utf-8")) if r["task"] in ("silly", "silly_fix", "recap")]

    def evaluate(step):
        loss = S.reference_loss(policy, refs, device)
        made = S.samples(policy, tok, held_rows, eot, device, a.samples, a.seed)
        rate, _, gate, gate_n, _ = S.judge_samples(made)
        # The truth checks on the same 400 extra held-out inputs sft_small.py scores
        _, _, _, _, truths = S.judge_samples(made + S.samples(policy, tok, truth_rows, eot, device, 400, a.seed + 1))
        pref = prefers()
        row = {"step": step, "ref_heldout": loss, "filter_pass": rate, "gate": gate, "truth": truths, "prefers_kept": pref,
               "time": time.strftime("%H:%M")}
        log.write(json.dumps(row, ensure_ascii=False) + "\n")
        log.flush()
        print(f"eval step {step}: held-out answer loss {loss:.3f} | filter pass {rate:.1%} | gate {gate:.1%} of {gate_n} | "
              f"truth {truths} | prefers the kept line {pref:.1%}", flush=True)
        with open(out / f"samples-{step}.txt", "w", encoding="utf-8") as f:
            for r, line in made:
                f.write(f"[{r['task']} {r['lang']} {'girl' if r['girl'] else 'boy'}] {r['input']}\n   model  {line}\n   data   {r['line']}\n")

    evaluate(0)
    opt = torch.optim.AdamW([p for p in policy.parameters()], lr=a.lr, weight_decay=0.0)
    warm = max(1, a.steps // 10)
    sched = torch.optim.lr_scheduler.LambdaLR(opt, lambda s: min(1, (s + 1) / warm) * 0.5 * (1 + math.cos(math.pi * min(s, a.steps) / a.steps)))
    order, pos, t0 = list(range(len(enc))), len(enc), time.time()
    for step in range(1, a.steps + 1):
        if pos + a.batch > len(order):
            rng.shuffle(order)
            pos = 0
        part = [enc[i] for i in order[pos:pos + a.batch]]
        pos += a.batch
        goods, bads = [(g, n) for g, _, n in part], [(b, n) for _, b, n in part]
        pc, nc = answer_logps(policy, goods, device)
        pr, _ = answer_logps(policy, bads, device)
        with torch.no_grad():
            rc, _ = answer_logps(reference, goods, device)
            rr, _ = answer_logps(reference, bads, device)
        margin = (pc - rc) - (pr - rr)
        loss = -F.logsigmoid(a.beta * margin).mean() + a.sft_weight * (-(pc / nc.clamp(min=1))).mean()
        opt.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(policy.parameters(), 1.0)
        opt.step()
        sched.step()
        if step % 50 == 0:
            acc = float((margin > 0).float().mean())
            print(f"step {step}/{a.steps} loss {loss.item():.3f} margin {margin.mean().item():.2f} acc {acc:.0%} "
                  f"{step * a.batch / (time.time() - t0):.0f} pairs/s", flush=True)
        if step % max(1, a.steps // 2) == 0 or step == a.steps:
            evaluate(step)
    torch.save({"model": policy.state_dict(), "config": policy.config.to_dict()}, out / "last.pt")
    print("done")


if __name__ == "__main__":
    main()
