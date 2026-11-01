"""Labels Puki's Hebrew lines with their phonemes, which the model learns to write beside each line,
so RenikudPlus is needed here and never at runtime: its reading, with Puki's own pronunciations
(brain/pronunciation.tsv) put in for the child's gender.

    <brain venv python> say.py    # writes data/say.jsonl and data/say_words.tsv

data/say.jsonl: line, girl, say (null when the words wouldn't line up), fixed (the words the table
changed). data/say_words.tsv: each word as read, how often, and an example, for review.
"""
import collections
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "brain"))
import pronunciation  # noqa: E402

NAME = "נועה"  # (a stand-in for "@name", swapped back afterwards)
NAME_IPA = "@name"


def wanted():
    """(line, girl) for every Hebrew line in the dataset, once each."""
    seen = {}
    for name in ("train.jsonl", "train_heldout.jsonl"):
        with open(os.path.join(DATA, name), encoding="utf-8") as f:
            for raw in f:
                r = json.loads(raw)
                if r["lang"] == "he":
                    seen[(r["line"], bool(r["girl"]))] = True
    return list(seen)


def label(line, girl, g2p):
    """The line's phonemes, word for word with it, or None."""
    text = line.replace("@name", NAME)
    ipa = g2p.phonemize(text)
    src, ph = text.split(), ipa.split()
    if len(src) != len(ph):
        return None, []
    out, fixed = [], []
    for s, p in zip(src, ph):
        if NAME in s:
            p = p.replace(g2p.phonemize(NAME).strip(".,!?"), NAME_IPA)
        m = pronunciation.HEBREW_WORD.search(s)
        mine = pronunciation.lookup(m.group(0), girl) if m else None
        if mine:
            lead = re.match(r"^[^\wɐ-˿]*", p).group(0)
            trail = re.search(r"[^\wɐ-˿ˈː]*$", p).group(0)
            p = lead + mine + trail
            fixed.append(m.group(0))
        out.append(p)
    return " ".join(out), fixed


def main():
    from renikud_onnx import G2P
    cfg = json.load(open(os.path.join(os.environ.get("LOCALAPPDATA", ""), "Puki", "brain", "config.json"), encoding="utf-8"))
    g2p = G2P(os.path.expanduser(cfg["renikud_model"]))
    todo = wanted()
    print(len(todo), "lines (line, gender)", flush=True)
    counts, examples = collections.Counter(), {}
    missed = odd = changed = 0
    with open(os.path.join(DATA, "say.jsonl"), "w", encoding="utf-8") as out:
        for i, (line, girl) in enumerate(todo):
            say, fixed = label(line, girl, g2p)
            if say is None:
                missed += 1
            else:
                changed += bool(fixed)
                if re.search(r"[A-Za-z0-9]", re.sub(r"@name", "", line)):
                    odd += 1
                for w, p in zip(line.split(), say.split()):
                    key = (w.strip('.,!?"\''), p.strip('.,!?"\''))
                    counts[key] += 1
                    examples.setdefault(key, line)
            out.write(json.dumps({"line": line, "girl": girl, "say": say, "fixed": fixed}, ensure_ascii=False) + "\n")
            if i % 500 == 0:
                print(i, flush=True)
    with open(os.path.join(DATA, "say_words.tsv"), "w", encoding="utf-8") as f:
        f.write("word\tipa\tcount\texample\n")
        for (w, p), n in counts.most_common():
            f.write(f"{w}\t{p}\t{n}\t{examples[(w, p)]}\n")
    print(f"done: {len(todo)} lines, {changed} changed by the table, {missed} didn't line up, {odd} with Latin or digits, "
          f"{len({w for w, _ in counts})} words, {len(counts)} word+reading pairs")


if __name__ == "__main__":
    main()
