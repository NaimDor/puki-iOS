"""Every recorded Hebrew line as BlueTTS reads it: from its own entry in PHONEMES, or RenikudPlus,
and what Puki's pronunciation table would say instead, to find the lines recorded wrongly.

    <brain venv python> voice_audit.py    # writes data/voice_audit.tsv and data/voice_words.tsv
"""
import collections
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "brain"))
sys.path.insert(0, os.path.join(ROOT, "scripts"))
import generate_voices as gv  # noqa: E402
import pronunciation  # noqa: E402


def lines():
    """(file stem, plain text, girl) for each Hebrew recording."""
    for name, en, he in gv.voice_lines:
        if isinstance(he, tuple):
            yield f"{name}_he_f", gv.strip_nikud(he[0]), True
            yield f"{name}_he_m", gv.strip_nikud(he[1]), False
        else:
            yield f"{name}_he", gv.strip_nikud(he), True


def main():
    from renikud_onnx import G2P
    cfg = json.load(open(os.path.join(os.environ.get("LOCALAPPDATA", ""), "Puki", "brain", "config.json"), encoding="utf-8"))
    g2p = G2P(os.path.expanduser(cfg["renikud_model"]))
    words = collections.Counter()
    examples = {}
    changed = 0
    with open(os.path.join(DATA, "voice_audit.tsv"), "w", encoding="utf-8") as out:
        out.write("stem\tkind\ttext\tipa_now\tipa_table\n")
        for stem, text, girl in lines():
            own = gv.PHONEMES.get(stem)
            ipa = own or g2p.phonemize(text)
            fixed = pronunciation._fix_segment(text, ipa, girl) if not own else None
            if fixed and fixed != ipa:
                changed += 1
            out.write(f"{stem}\t{'own' if own else 'renikud'}\t{text}\t{ipa}\t{fixed if fixed and fixed != ipa else ''}\n")
            for w, p in zip(text.split(), ipa.split()):
                key = (w.strip('.,!?"\''), p.strip('.,!?"\''))
                words[key] += 1
                examples.setdefault(key, stem)
    with open(os.path.join(DATA, "voice_words.tsv"), "w", encoding="utf-8") as f:
        f.write("word\tipa\tcount\texample\n")
        for (w, p), n in words.most_common():
            f.write(f"{w}\t{p}\t{n}\t{examples[(w, p)]}\n")
    print(f"{sum(1 for _ in lines())} recordings, {changed} the table would change, {len(words)} word+reading pairs")


if __name__ == "__main__":
    main()
