"""Puki's Hebrew lines through RenikudPlus (the pronunciation step BlueTTS uses), word by word,
to find words it says wrong: interjections, animal sounds, loanwords ("אופס" comes out "ops").

    <brain venv python> phonemes.py    # writes data/words.tsv: word, IPA, count, an example line

data/words.tsv is what a reviewer reads; the fixes go into Puki's override table.
"""
import collections
import glob
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
HEBREW = re.compile(r"[א-ת]")


def lines():
    """Every Hebrew line Puki says in the dataset (with @name as a plain stand-in name)."""
    out = []
    for path in glob.glob(os.path.join(DATA, "train*.jsonl")) + glob.glob(os.path.join(DATA, "*heldout.jsonl")):
        if "pilot" in os.path.basename(path):
            continue
        with open(path, encoding="utf-8") as f:
            for l in f:
                r = json.loads(l)
                if r["lang"] == "he":
                    out.append(r["line"].replace("@name", "נועה"))
    return list(dict.fromkeys(out))


def words(text):
    return [w for w in re.findall(r"[א-ת'\"״׳-]+", text) if HEBREW.search(w)]


def main():
    from renikud_onnx import G2P
    cfg = json.load(open(os.path.join(os.environ.get("LOCALAPPDATA", ""), "Puki", "brain", "config.json"), encoding="utf-8"))
    g2p = G2P(os.path.expanduser(cfg["renikud_model"]))
    count, ipa, example = collections.Counter(), {}, {}
    ls = lines()
    for line in ls:
        hw = words(line)
        # Phonemized whole (so context decides), split back into words where they line up
        ph = [p for p in re.split(r"\s+", re.sub(r"[!?,.;:\"״]", " ", g2p.phonemize(line))) if p.strip()]
        aligned = len(ph) == len(hw)
        for i, w in enumerate(hw):
            count[w] += 1
            example.setdefault(w, line)
            if w not in ipa:
                ipa[w] = ph[i] if aligned else g2p.phonemize(w).strip()
    os.makedirs(DATA, exist_ok=True)
    with open(os.path.join(DATA, "words.tsv"), "w", encoding="utf-8") as f:
        f.write("word\tipa\tcount\texample\n")
        for w, n in count.most_common():
            f.write(f"{w}\t{ipa[w]}\t{n}\t{example[w]}\n")
    print(f"{len(ls)} Hebrew lines, {len(count)} different words -> data/words.tsv")


if __name__ == "__main__":
    main()
