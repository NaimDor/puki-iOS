"""Puts the reviewed pronunciations in: data/word_fixes.tsv and data/line_fixes.tsv, on the training
labels (data/say_fixed.jsonl -> data/say_final.jsonl) and on the recordings (printing what
scripts/generate_voices.py's PHONEMES needs, into data/voice_phonemes.json).

    <brain venv python> say_apply.py
"""
import csv
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "brain"))
import pronunciation  # noqa: E402

PUNCT = '.,!?"\'׳'
BLIND = {"מה", "הצלחת", "ספרו"}  # (table words that depend on the line: the reviewed rows decide those)


def rows(name):
    return list(csv.DictReader(open(os.path.join(DATA, name), encoding="utf-8"), delimiter="\t"))


WORDS, LINES = rows("word_fixes.tsv"), rows("line_fixes.tsv")


def word_fix(word, line, girl, kind):
    for r in WORDS:
        if r["word"] != word or r["where"] not in (kind, "both"):
            continue
        when = r["when"]
        if when == "all" or (when == "girl" and girl is not False) or (when == "boy" and girl is False) or \
                (when not in ("girl", "boy") and when in line):
            return r["ipa"]
    return None


def swap(token, ipa):
    """The token's punctuation around the new word."""
    core = token.strip(PUNCT)
    return token.replace(core, ipa) if core else token


def fix(line, girl, say, kind, table=False):
    src, ph = line.split(), say.split()
    if len(src) != len(ph):
        return say
    out = list(ph)
    done = set()
    for r in LINES:
        if r["line"] != line or (r["girl"] != "any" and (r["girl"] == "True") != bool(girl)):
            continue
        words = r["word"].split()
        for i in range(len(src) - len(words) + 1):
            if [w.strip(PUNCT) for w in src[i:i + len(words)]] == words:
                ipas = r["ipa"].split()
                if len(words) == len(ipas):
                    for k in range(len(words)):
                        out[i + k] = swap(ph[i + k], ipas[k])
                        done.add(i + k)
                else:
                    out[i] = swap(ph[i], r["ipa"])
                    done.add(i)
                break
    for i, s in enumerate(src):
        if i in done:
            continue
        w = s.strip(PUNCT)
        ipa = word_fix(w, line, girl, kind)
        if not ipa and table and w not in BLIND:
            ipa = pronunciation.lookup(w, girl is not False)
        if ipa:
            out[i] = swap(ph[i], ipa)
    return " ".join(out)


def main():
    changed = 0
    with open(os.path.join(DATA, "say_final.jsonl"), "w", encoding="utf-8") as out:
        for raw in open(os.path.join(DATA, "say_fixed.jsonl"), encoding="utf-8"):
            r = json.loads(raw)
            say = fix(r["line"], r["girl"], r["say"], "train")
            changed += say != r["say"]
            out.write(json.dumps({"line": r["line"], "girl": r["girl"], "say": say}, ensure_ascii=False) + "\n")
    voices = {}
    for r in rows("voice_audit.tsv"):
        stem = r["stem"]
        girl = None if not stem.endswith(("_f", "_m")) else stem.endswith("_f")
        say = fix(r["text"], girl, r["ipa_now"], "voice", table=True)
        if say != r["ipa_now"]:
            voices[stem] = say
    json.dump(voices, open(os.path.join(DATA, "voice_phonemes.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    print(f"{changed} training labels changed, {len(voices)} recordings changed")
    for k, v in voices.items():
        print(f"{k}\t{v}")


if __name__ == "__main__":
    main()
