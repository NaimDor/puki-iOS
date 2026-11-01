"""Puts the training set together: the lines from build.py, their phonemes from say_apply.py, the
gate from gate.py. Lines with a known text error go, and careful and talk count extra, since the
games' data outnumbers them.

    python final.py    # writes data/final.jsonl and data/final_heldout.jsonl
"""
import collections
import json
import os
import re
import sys
import zlib

import fmt

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "brain"))
import pronunciation  # noqa: E402

REPEAT = {"careful": 3, "talk": 2}
GATE_REPEAT = {"0": 1, "1": 2, "2": 3, "3": 3}

MASCULINE = "לבבות|כוכבים|דגים|פרחים|תפוחים|פרפרים|גזרים|עצים|כדורים"
FEMININE = "ציפורים|פרות|כבשים|תרנגולות|עוגיות|מכוניות"
BAD_TEXT = re.compile(
    rf"\b(?:שלוש|ארבע|חמש|שש|שבע|שמונה|תשע|עשר)\s+(?:{MASCULINE})\b"  # number and noun disagree
    rf"|\b(?:שלושה|ארבעה|חמישה|שישה|שבעה|שמונה|תשעה|עשרה)\s+(?:{FEMININE})\b"
    r"|זה פרה|הלכת את הדובי|ספרים כוכבים|הלום @name|האות ז פותחת|מתחילה בז|כ זה מכתב|ש זורחת|מגפיים צהובות"
    r"|לבבות לילה|קודם היום בחרו|בחרו את ח|כוכב בזהב|החם מהשמש|הוא רוכב מהר|מלטפת אותו"
)


def load(name):
    with open(os.path.join(DATA, name), encoding="utf-8") as f:
        return [json.loads(l) for l in f]


def main():
    says = {(r["line"], r["girl"]): r["say"] for r in load("say_final.jsonl")}
    why = collections.Counter()
    for name, out_name, train in (("train.jsonl", "final.jsonl", True), ("train_heldout.jsonl", "final_heldout.jsonl", False)):
        rows = []
        for r in load(name):
            say = None
            if r["lang"] == "he":
                say = says.get((r["line"], bool(r["girl"])))
                if BAD_TEXT.search(r["line"]):
                    why["text error"] += 1
                    continue
                if not say:
                    why["no phonemes"] += 1
                    continue
                if not pronunciation.say_matches(r["line"], say):
                    why["phonemes don't match"] += 1
                    continue
            text = fmt.example(r["task"], r["lang"], r["girl"], r["input"], r["line"], r["earlier"], say)
            for _ in range(REPEAT.get(r["task"], 1) if train else 1):
                rows.append({**{k: r[k] for k in ("task", "lang", "girl", "input", "earlier", "line")}, "say": say, "text": text})
        for r in load("gate.jsonl"):
            # (a tenth held out, by the phrase itself, so it stays the same one each build)
            held = zlib.crc32(r["input"].encode()) % 10 == 0
            if held == train:
                continue
            text = fmt.example("gate", r["lang"], True, r["input"], r["digit"])
            for _ in range(GATE_REPEAT[r["digit"]] if train else 1):
                rows.append({"task": "gate", "lang": r["lang"], "girl": True, "input": r["input"], "earlier": None,
                             "line": r["digit"], "say": None, "text": text})
        with open(os.path.join(DATA, out_name), "w", encoding="utf-8") as f:
            for row in rows:
                f.write(json.dumps(row, ensure_ascii=False) + "\n")
        print(out_name, len(rows), dict(collections.Counter(r["task"] for r in rows)))
    print("dropped", dict(why))


if __name__ == "__main__":
    main()
