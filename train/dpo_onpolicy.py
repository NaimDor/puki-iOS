"""Preference pairs from the fine-tuned model's own mistakes: it writes a few lines for each training input
of the tasks a check can judge (Silly Puki and its correction, the recap), and each line that fails Puki's
filter or the truth check (sft_small.judge_samples' checks) becomes the turned-down side, against the line
the builder kept. Its mistakes, not the teacher's, are what preference tuning should push away.

    python dpo_onpolicy.py --sft C:/data/pretrain/puki-sft/sft-46k/best.pt \
        --data data/final.jsonl data/more1.jsonl data/more2.jsonl --out data/dpo_onpolicy.jsonl

Writes the layout of dpo_pairs.py (kind "own"), for dpo_small.py --pairs.

With --judge, every task (not only the three a check can judge) and the teacher's editor (teacher.judge) scores
the model's own lines: the best becomes the chosen side and the worst the turned-down one, when the editor
sees a clear gap; when all of them are poor, the line the builder kept is the chosen side (kind "judged").
PUKI_LANG (as in pt_corpus) keeps one language's inputs only.
"""
import argparse
import json
import random
import re
import sys
from pathlib import Path

import torch

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import fmt  # noqa: E402
import sft_small as S  # noqa: E402

TASKS = ("silly", "silly_fix", "recap")
# With --judge: every task with a line to say (the gate is a yes/no, not a line); the free-form ones get more
JUDGED = {"talk": 3, "story_next": 3, "reach_back": 3, "silly": 2, "silly_fix": 2, "recap": 2, "care": 1, "look": 1,
          "moment": 1, "tap": 1, "careful": 1}


def ok(row, line):
    """Whether a line passes Puki's filter and the task's truth check."""
    import build
    import games
    if build.puki_filter(line, row["lang"], row["girl"]) is None:
        return False
    truth = S.TRUTH.search(row["input"])
    if row["task"] in ("silly", "silly_fix") and truth:
        return build.said_truth(line, truth.group(1).strip()) == (row["task"] == "silly_fix")
    if row["task"] == "recap":
        return games.events_named(line, row["input"]) >= 2
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sft", required=True)
    ap.add_argument("--data", nargs="+", required=True)
    ap.add_argument("--out", default=str(HERE / "data" / "dpo_onpolicy.jsonl"))
    ap.add_argument("--tok", default=r"C:\data\pretrain\puki-tok")
    ap.add_argument("--tries", type=int, default=4, help="lines it writes for each input")
    ap.add_argument("--inputs", type=int, default=3000, help="at most this many inputs (spread over the tasks)")
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--judge", action="store_true", help="every task, the teacher's editor picks the pairs")
    a = ap.parse_args()
    from tokenizers import Tokenizer
    from pt_corpus import LANG
    rng = random.Random(a.seed)
    torch.manual_seed(a.seed)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    tok = Tokenizer.from_file(str(Path(a.tok) / "tokenizer.json"))
    eot = tok.token_to_id("<|endoftext|>")
    tasks = JUDGED if a.judge else TASKS
    # Each input once, with every line the builder kept for it
    kept = {}
    for name in a.data:
        for r in map(json.loads, open(name, encoding="utf-8")):
            if r["task"] in tasks and (not LANG or r["lang"] == LANG):
                key = (r["task"], r["lang"], bool(r["girl"]), r["input"], r.get("earlier"))
                kept.setdefault(key, []).append(r["line"])
    keys = list(kept)
    rng.shuffle(keys)
    if a.judge:  # spread over the tasks by weight
        weight = sum(JUDGED.values())
        keys = [k for t, w in JUDGED.items() for k in [k for k in keys if k[0] == t][:a.inputs * w // weight]]
        rng.shuffle(keys)
    keys = keys[:a.inputs]
    model = S.load_model(a.sft, device).eval()
    if a.judge:
        return judged(a, model, tok, eot, device, keys, kept, rng)
    pairs = made = wrong = 0
    with open(a.out, "w", encoding="utf-8") as out, torch.no_grad():
        for n, (task, lang, girl, inp, earlier) in enumerate(keys):
            row = {"task": task, "lang": lang, "girl": girl, "input": inp, "earlier": earlier}
            ids = torch.tensor([tok.encode(fmt.prompt(task, lang, girl, inp, earlier)).ids], device=device)
            gen = model.generate(ids, max_new_tokens=48, do_sample=True, temperature=0.9, top_p=0.95, num_return_sequences=a.tries,
                                 eos_token_id=eot, pad_token_id=eot)
            lines = {tok.decode(g[ids.shape[1]:].tolist()).replace("<|endoftext|>", "").strip() for g in gen}
            made += len(lines)
            for line in lines:
                if not line or ok(row, line):
                    continue
                wrong += 1
                good = rng.choice(kept[(task, lang, girl, inp, earlier)])
                out.write(json.dumps({**row, "chosen": good, "rejected": line, "kind": "own"}, ensure_ascii=False) + "\n")
                pairs += 1
            if (n + 1) % 250 == 0:
                print(f"{n + 1}/{len(keys)} inputs, {wrong}/{made} of its lines wrong", flush=True)
    print(f"done: {pairs} pairs from {len(keys)} inputs ({wrong} of its {made} lines failed a check) -> {a.out}")


def judged(a, model, tok, eot, device, keys, kept, rng):
    """--judge: the model writes, the teacher's editor scores, the clear gaps become pairs."""
    import collections
    from concurrent.futures import ThreadPoolExecutor
    import teacher
    written = []
    with torch.no_grad():
        for n, (task, lang, girl, inp, earlier) in enumerate(keys):
            ids = torch.tensor([tok.encode(fmt.prompt(task, lang, girl, inp, earlier)).ids], device=device)
            gen = model.generate(ids, max_new_tokens=48, do_sample=True, temperature=0.9, top_p=0.95, num_return_sequences=a.tries,
                                 eos_token_id=eot, pad_token_id=eot)
            lines = sorted({fmt.parse(tok.decode(g[ids.shape[1]:].tolist()))[0] for g in gen} - {""})
            written.append(lines)
            if (n + 1) % 500 == 0:
                print(f"{n + 1}/{len(keys)} inputs written", flush=True)
    del model
    torch.cuda.empty_cache()

    def score(job):
        (task, lang, girl, inp, earlier), lines = job
        if len(lines) < 1:
            return []
        got = teacher.judge(task, lang, girl, inp, lines, earlier=earlier)
        return [(lines[s["i"]], int(s.get("overall", 0))) for s in got if 0 <= s["i"] < len(lines)]

    with ThreadPoolExecutor(16) as pool:
        scored = list(pool.map(score, zip(keys, written)))
    pairs, per_task = 0, collections.Counter()
    with open(a.out, "w", encoding="utf-8") as out:
        for (task, lang, girl, inp, earlier), s in zip(keys, scored):
            if not s:
                continue
            s.sort(key=lambda x: x[1])
            (worst, lo), (best, hi) = s[0], s[-1]
            if hi >= 4 and hi - lo >= 2:
                chosen = best
            elif hi <= 2:
                chosen = rng.choice(kept[(task, lang, girl, inp, earlier)])
            else:
                continue
            row = {"task": task, "lang": lang, "girl": girl, "input": inp, "earlier": earlier}
            out.write(json.dumps({**row, "chosen": chosen, "rejected": worst, "kind": "judged"}, ensure_ascii=False) + "\n")
            pairs += 1
            per_task[task] += 1
    print(f"done: {pairs} pairs from {len(keys)} inputs {dict(per_task)} -> {a.out}; DeepSeek {teacher.usage}")


if __name__ == "__main__":
    main()
