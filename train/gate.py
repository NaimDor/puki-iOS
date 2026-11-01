"""The safety gate as a task of its own: what a toddler said -> one digit (safety.classify_prompt's
0 to 3), so the small model can answer the gate that decides whether it may reply at all.

Unlike the "careful" task these phrases aren't filtered by Puki's word lists: what the lists miss
is exactly what the gate is for. The writer's category isn't enough to go on (it files "how does
a bird fly?" under big questions), so each phrase is also put to the gate's own question, and only
those where both agree are kept. Harmless look-alikes teach it not to raise false alarms.

    python gate.py    # writes data/gate.jsonl
"""
import json
import os
import sys
from concurrent.futures import ThreadPoolExecutor

import teacher

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "brain"))
import safety  # noqa: E402

DIGITS = {"hurt": "1", "needs": "1", "parent": "1", "feelings": "1", "danger": "2",
          "big_question": "3", "secrets": "3", "buying": "3"}
PLAY_SETS = 6
CATEGORY_SETS = 3
LOOK_ALIKE_SETS = 5


def phrases(lang, kind, n, sets):
    out = set()
    for s in range(sets):
        for p in teacher.toddler_phrases(lang, kind, n, s):
            if any("א" <= c <= "ת" for c in p) == (lang == "he"):  # (only the language asked for)
                out.add(p.strip())
    return out


LOOK_ALIKE = """You write made-up things a 2-to-4-year-old might say to a toy parrot, for testing a safety
filter that must NOT trigger on them. Write ordinary, happy play and curiosity that only sounds a
little serious: a child asking how birds fly or why the moon is round, a toy or story animal falling or
crying in a game ("the dolly fell down", "teddy is sad, I'll hug him"), mommy or daddy mentioned in
passing with nothing wrong ("mommy has a blue bag", "daddy is laughing"), playing doctor, a pretend
monster, food they like, wanting more of the game. Nobody is really hurt, scared, hungry, or asking for
help. Answer with JSON only: {"phrases": ["...", ...]}, in the language asked for, no nikud."""


def look_alikes(lang, n=40, seed=0):
    language = "Hebrew" if lang == "he" else "English"
    answer = teacher.ask_json(teacher.WRITER, LOOK_ALIKE, f"Write {n} different phrases in {language}. Set {seed}.")
    return {p.strip() for p in answer.get("phrases", []) if any("א" <= c <= "ת" for c in p) == (lang == "he")}


CHECK = """You label what a toddler said for a toy's safety gate. Be strict about 0: curiosity, pretend play
and toys or story animals in trouble are ordinary. Answer with JSON only: {"digit": "<0, 1, 2 or 3>"}."""


def check(phrase):
    """The gate's own question, put to the teacher."""
    answer = teacher.ask_json(teacher.JUDGE, CHECK, safety.classify_prompt(phrase), temperature=0.0)
    return str(answer.get("digit", "")).strip()


def main():
    rows = {}
    for lang in ("he", "en"):
        for kind, digit in DIGITS.items():
            for p in phrases(lang, kind, 25, CATEGORY_SETS):
                rows[(lang, p)] = (digit, kind)
        ordinary = set(phrases(lang, "play", 40, PLAY_SETS))
        for seed in range(LOOK_ALIKE_SETS):
            ordinary |= look_alikes(lang, seed=seed)
        with open(os.path.join(DATA, "train.jsonl"), encoding="utf-8") as f:
            ordinary |= {r["input"] for r in map(json.loads, f) if r["task"] == "talk" and r["lang"] == lang}
        for p in ordinary:
            if (lang, p) not in rows and not safety.guard(p, lang, True):  # (the lists agree it's ordinary)
                rows[(lang, p)] = ("0", "play")
    items = sorted(rows.items())
    with ThreadPoolExecutor(8) as ex:
        answers = list(ex.map(lambda item: check(item[0][1]), items))
    kept = [(k, v) for (k, v), a in zip(items, answers) if a == v[0]]
    with open(os.path.join(DATA, "gate.jsonl"), "w", encoding="utf-8") as f:
        for (lang, p), (digit, kind) in kept:
            f.write(json.dumps({"lang": lang, "input": p, "digit": digit, "kind": kind}, ensure_ascii=False) + "\n")
    counts = {}
    for _, (digit, _kind) in kept:
        counts[digit] = counts.get(digit, 0) + 1
    print(f"{len(kept)} of {len(items)} phrases agreed", dict(sorted(counts.items())))


if __name__ == "__main__":
    main()
