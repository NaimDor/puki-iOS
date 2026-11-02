"""Preference pairs from the fine-tuned model's own mistakes: it writes a few lines for each training input
of the tasks a check can judge (Silly Puki and its correction, the recap), and each line that fails Puki's
filter or the truth check (sft_small.judge_samples' checks) becomes the turned-down side, against the line
the builder kept. Its mistakes, not the teacher's, are what preference tuning should push away.

    python dpo_onpolicy.py --sft C:/data/pretrain/puki-sft/sft-46k/best.pt \
        --data data/final.jsonl data/more1.jsonl data/more2.jsonl --out data/dpo_onpolicy.jsonl

Writes the layout of dpo_pairs.py (kind "own"), for dpo_small.py --pairs.
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
    a = ap.parse_args()
    from tokenizers import Tokenizer
    rng = random.Random(a.seed)
    torch.manual_seed(a.seed)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    tok = Tokenizer.from_file(str(Path(a.tok) / "tokenizer.json"))
    eot = tok.token_to_id("<|endoftext|>")
    # Each input once, with every line the builder kept for it
    kept = {}
    for name in a.data:
        for r in map(json.loads, open(name, encoding="utf-8")):
            if r["task"] in TASKS:
                key = (r["task"], r["lang"], bool(r["girl"]), r["input"], r.get("earlier"))
                kept.setdefault(key, []).append(r["line"])
    keys = list(kept)
    rng.shuffle(keys)
    keys = keys[:a.inputs]
    model = S.load_model(a.sft, device).eval()
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


if __name__ == "__main__":
    main()
