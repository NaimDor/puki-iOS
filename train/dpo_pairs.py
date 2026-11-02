"""Preference pairs for Puki's small model from what the builder already decided: for each input, a line
it kept (chosen) against one it turned down (rejected), by Puki's own filter and truth checks ("filter":
certain) or by the teacher's judge ("judge": a lower score, softer). Read from build.py's *_review.txt.

    python dpo_pairs.py data/train_review.txt data/more1_review.txt data/look1_review.txt

Writes data/dpo_pairs.jsonl: {task, lang, girl, input, earlier, chosen, rejected, kind}. Inputs that are
held out (any *_heldout.jsonl) are left out, as are careful (one fixed reply) and stories (whole stories).
"""
import argparse
import glob
import json
import os
import random
import re

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
HEAD = re.compile(r"^\[(\w+) (he|en) (girl|boy)\] (.*?)(?:  \(earlier: (.*)\))?$")
SKIP = {"careful", "story_next"}
PER_REJECTED = 2  # (each turned-down line against up to two kept ones)


def held_out():
    inputs = set()
    for name in glob.glob(os.path.join(DATA, "*_heldout.jsonl")):
        with open(name, encoding="utf-8") as f:
            inputs |= {json.loads(l)["input"] for l in f}
    return inputs


def blocks(path):
    """(task, lang, girl, input, earlier, kept, [(rejected, kind)]) for each input in a review file."""
    head, kept, rejected = None, [], []
    with open(path, encoding="utf-8") as f:
        for raw in f:
            line = raw.rstrip("\n")
            m = HEAD.match(line)
            if m:
                if head:
                    yield (*head, kept, rejected)
                head, kept, rejected = m.groups(), [], []
            elif line.startswith("   KEEP  "):
                kept.append(line[9:])
            elif line.startswith("   judge "):
                rejected.append((line[9:], "judge"))
            elif line.startswith("   filter "):
                rejected.append((line[10:], "filter"))
    if head:
        yield (*head, kept, rejected)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("files", nargs="+")
    ap.add_argument("--out", default=os.path.join(DATA, "dpo_pairs.jsonl"))
    ap.add_argument("--seed", type=int, default=0)
    a = ap.parse_args()
    rng = random.Random(a.seed)
    held = held_out()
    counts, seen = {}, set()
    with open(a.out, "w", encoding="utf-8") as out:
        for name in a.files:
            for task, lang, girl, inp, earlier, kept, rejected in blocks(name):
                if task in SKIP or inp in held or not kept:
                    continue
                for bad, kind in rejected:
                    if bad in kept:
                        continue
                    for good in rng.sample(kept, min(PER_REJECTED, len(kept))):
                        key = (inp, girl, good, bad)
                        if key in seen:
                            continue
                        seen.add(key)
                        out.write(json.dumps({"task": task, "lang": lang, "girl": girl == "girl", "input": inp, "earlier": earlier,
                                              "chosen": good, "rejected": bad, "kind": kind}, ensure_ascii=False) + "\n")
                        counts[(task, kind)] = counts.get((task, kind), 0) + 1
    total = sum(counts.values())
    print(f"{total} pairs -> {a.out}")
    for (task, kind), n in sorted(counts.items()):
        print(f"  {task:12} {kind:6} {n}")


if __name__ == "__main__":
    main()
