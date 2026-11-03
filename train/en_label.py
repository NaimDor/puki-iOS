"""Labels English words with espeak's phonemes, the way BlueTTS reads English (blue_onnx.TextProcessor: en-us,
with stress), for the English reading model: the 200k commonest words of the English pretraining text, plus
every word in Puki's English lines. Run with BlueTTS's venv, which has espeak (espeakng_loader).

    python en_label.py --words C:/data/pretrain/en-v2/en_words.tsv --out C:/data/pretrain/en-v2/en_read.tsv
"""
import argparse
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
WORD = re.compile(r"[A-Za-z]+(?:'[a-z]+)?")


def backend():
    import espeakng_loader
    from phonemizer.backend import EspeakBackend
    from phonemizer.backend.espeak.wrapper import EspeakWrapper
    EspeakWrapper.set_library(espeakng_loader.get_library_path())
    if hasattr(EspeakWrapper, "set_data_path"):
        EspeakWrapper.set_data_path(espeakng_loader.get_data_path())
    return EspeakBackend("en-us", preserve_punctuation=True, with_stress=True, language_switch="remove-flags")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--words", required=True, help="word<TAB>count, commonest first")
    ap.add_argument("--out", required=True)
    ap.add_argument("--top", type=int, default=200000)
    a = ap.parse_args()
    from phonemizer.separator import Separator
    words = [l.split("\t")[0] for l in open(a.words, encoding="utf-8")][:a.top]
    puki = set()
    for name in sorted((HERE / "data").glob("*.jsonl")):
        if name.name == "cache.jsonl":  # (the teacher's answers, not lines)
            continue
        for l in open(name, encoding="utf-8", errors="replace"):
            try:
                r = json.loads(l)
            except ValueError:
                continue
            if r.get("lang") == "en":
                for k in ("line", "input"):
                    puki.update(w.lower() for w in WORD.findall(str(r.get(k, ""))))
    seen = set(words)
    words += sorted(puki - seen)
    print(f"{len(words)} words ({len(puki)} in Puki's English lines, {len(puki - seen)} of them new)", flush=True)
    b = backend()
    sep = Separator(phone="", word=" ", syllable="")
    # In a sentence espeak reads the small words weak (a: ɐ, to: tə, I: aɪ), not as it reads them alone; so
    # each of Puki's words also gets its commonest reading inside his own lines, where the words line up
    lines = sorted({str(json.loads(l).get("line", "")) for name in sorted((HERE / "data").glob("*.jsonl")) if name.name != "cache.jsonl"
                    for l in open(name, encoding="utf-8", errors="replace") if l.strip().startswith("{") and '"en"' in l})
    lines = [l.replace("@name", "Maya") for l in lines if l and not any("֐" <= c <= "׿" for c in l)]
    seen_in = {}
    for s in range(0, len(lines), 2000):
        for line, ipa in zip(lines[s:s + 2000], b.phonemize(lines[s:s + 2000], separator=sep, strip=True)):
            ws = [w.lower() for w in WORD.findall(line)]
            ps = [re.sub(r"[^\wɐ-˿ᴀ-ᶿːˈˌ]", "", p) for p in ipa.split()]
            if len(ws) == len(ps):
                for w, p in zip(ws, ps):
                    seen_in.setdefault(w, {}).setdefault(p, 0)
                    seen_in[w][p] += 1
    # (but not what a word picks up from the next one: a linking r before a vowel, "cucumber is", or a flapped t, "it is")
    in_line = {w: re.sub("ɾ$", "t", re.sub("ɚɹ$", "ɚ", max(c, key=c.get))) for w, c in seen_in.items()}
    print(f"{len(lines)} lines of Puki's read whole; {len(in_line)} words with an in-line reading", flush=True)
    with open(a.out.replace(".tsv", "_inline.tsv"), "w", encoding="utf-8") as out:
        for w, p in sorted(in_line.items()):
            out.write(f"{w}\t{p}\n")
    with open(a.out, "w", encoding="utf-8") as out:
        for s in range(0, len(words), 5000):
            part = words[s:s + 5000]
            for w, ipa in zip(part, b.phonemize(part, separator=sep, strip=True)):
                ipa = re.sub(r"\s+", " ", ipa).strip()
                if ipa:
                    out.write(f"{w}\t{ipa}\t{int(w in puki)}\n")
            print(f"{s + len(part)}/{len(words)}", flush=True)


if __name__ == "__main__":
    main()
