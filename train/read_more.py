"""More lines for the reading model to fine-tune on, unreviewed: Puki's new Hebrew lines (the top-ups
from build.py) read by RenikudPlus for the right gender, with Puki's own table (brain/pronunciation.tsv)
put in on top, as say.py does. Lines the reading model is scored on (the reviewed lines of held-out
inputs) are left out, and so are the held-out files.

    <brain venv python> read_more.py data/more1.jsonl data/look1.jsonl --out data/read_unreviewed.jsonl

Writes {line, girl, say} per line, the layout of say_final.jsonl (read_train.py --extra).
"""
import argparse
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "brain"))
import pronunciation  # noqa: E402
from read_tags import NAME, Teacher, clean  # noqa: E402

STAND_IN = "נועה"  # (for @name while reading, as say.py does; swapped back after)


def test_lines():
    with open(os.path.join(HERE, "data", "train_heldout.jsonl"), encoding="utf-8") as f:
        return {r["line"] for r in map(json.loads, f) if r["lang"] == "he"}


def read(t, line, girl):
    """The line's IPA, word for word with it, or None."""
    text = " ".join(clean(line.replace(NAME, STAND_IN)).split())
    words, said = text.split(), t.say(text, t.tags(text, 0, 2 if girl else 1)).split()
    if len(words) != len(said):
        return None
    out = []
    for w, p in zip(words, said):
        if STAND_IN in w:
            p = NAME
        m = pronunciation.HEBREW_WORD.search(w)
        mine = pronunciation.lookup(m.group(0), girl) if m else None
        if mine:
            lead = re.match(r"^[^\wɐ-˿]*", p).group(0)
            trail = re.search(r"[^\wɐ-˿ˈː]*$", p).group(0)
            p = lead + mine + trail
        out.append(p)
    return " ".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("files", nargs="+")
    ap.add_argument("--out", default=os.path.join(HERE, "data", "read_unreviewed.jsonl"))
    ap.add_argument("--gpu", action="store_true", help="run RenikudPlus on the GPU (an onnxruntime-gpu python)")
    a = ap.parse_args()
    t = Teacher(threads=6, gpu=a.gpu)
    skip = test_lines()
    seen, kept, missed = set(), 0, 0
    with open(a.out, "w", encoding="utf-8") as out:
        for name in a.files:
            if "heldout" in name:
                continue
            with open(name, encoding="utf-8") as f:
                for r in map(json.loads, f):
                    key = (r["line"], bool(r["girl"]))
                    if r["lang"] != "he" or key in seen or r["line"] in skip or re.search(r"[A-Za-z0-9]", r["line"].replace(NAME, "")):
                        continue
                    seen.add(key)
                    say = read(t, r["line"], bool(r["girl"]))
                    if say is None:
                        missed += 1
                        continue
                    out.write(json.dumps({"line": r["line"], "girl": bool(r["girl"]), "say": say}, ensure_ascii=False) + "\n")
                    kept += 1
                    if kept % 1000 == 0:
                        print(kept, flush=True)
    print(f"done: {kept} lines read, {missed} didn't line up -> {a.out}")


if __name__ == "__main__":
    main()
