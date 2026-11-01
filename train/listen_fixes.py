"""A page to hear the reviewer's pronunciation fixes (data/word_fixes.tsv, data/line_fixes.tsv):
each in a real line, as Puki reads it now and with the fix. Writes data/listen/fixes.html.

    <brain venv python> listen_fixes.py
"""
import base64
import csv
import html
import io
import json
import os
import re
import sys

import numpy as np
import soundfile as sf

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
CFG = json.load(open(os.path.join(os.environ.get("LOCALAPPDATA", ""), "Puki", "brain", "config.json"), encoding="utf-8"))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "brain"))

from blue_onnx import BlueTTS  # noqa: E402

bt = os.path.expanduser(CFG["bluetts_dir"])
TTS = BlueTTS(
    onnx_dir=os.path.join(bt, "onnx_models"),
    config_path=os.path.join(bt, "config", "tts.json"),
    style_json=os.path.join(bt, "voices", f"{CFG.get('voice', 'noa')}.json"),
    renikud_path=os.path.expanduser(CFG["renikud_model"]),
)
PUNCT = '.,!?"\''
ORDER = {"high": 0, "medium": 1, "low": 2}


def audio(ph):
    s, sr = TTS.synthesize(ph.replace("@name", "nˈoʔa"), lang="he", total_step=8, speed=0.92, text_is_phonemes=True)
    buf = io.BytesIO()
    sf.write(buf, np.concatenate([s, np.zeros(int(sr * 0.1), dtype=np.float32)]), sr, format="OGG", subtype="VORBIS")
    return "data:audio/ogg;base64," + base64.b64encode(buf.getvalue()).decode()


def pool():
    """(line, girl or None, phonemes, kind): the recordings first, then the training lines."""
    out = []
    for r in csv.DictReader(open(os.path.join(DATA, "voice_audit.tsv"), encoding="utf-8"), delimiter="\t"):
        out.append((r["text"], None if not r["stem"].endswith(("_f", "_m")) else r["stem"].endswith("_f"), r["ipa_now"], "voice"))
    for raw in open(os.path.join(DATA, "say_fixed.jsonl"), encoding="utf-8"):
        r = json.loads(raw)
        out.append((r["line"], r["girl"], r["say"], "train"))
    return out


def find(word, when, where, lines):
    for line, girl, say, kind in lines:
        if where != "both" and where != kind:
            continue
        if when == "girl" and girl is False or when == "boy" and girl is True:
            continue
        if when not in ("all", "girl", "boy") and when not in line:
            continue
        words = line.split()
        for i, w in enumerate(words):
            if w.strip(PUNCT) == word and len(words) == len(say.split()):
                return line, say, i, kind
    return None


def row(word, ipa, why, level, hit, count):
    line, say, i, kind = hit
    parts = say.split()
    was = parts[i].strip(PUNCT)
    if was == ipa:
        return None
    fixed = list(parts)
    fixed[i] = parts[i].replace(was, ipa)
    return (f"<tr><td class=w dir=rtl>{html.escape(word)}</td><td>{html.escape(was)}<br>{html.escape(ipa)}</td>"
            f"<td dir=rtl>{html.escape(line)}</td><td><audio controls src='{audio(say)}'></audio></td>"
            f"<td><audio controls src='{audio(' '.join(fixed))}'></audio></td>"
            f"<td class=c>{level}, {kind}, {count}×</td><td>{html.escape(why)}</td></tr>")


def main():
    lines = pool()
    fixes = list(csv.DictReader(open(os.path.join(DATA, "word_fixes.tsv"), encoding="utf-8"), delimiter="\t"))
    counts = {}
    for f in fixes:
        pat = re.compile(rf"(^|\s){re.escape(f['word'])}([{PUNCT}\s]|$)")
        counts[id(f)] = sum(1 for l in lines if pat.search(l[0]))
    # What the ear should settle first: recordings, then doubts, then what's said most
    fixes.sort(key=lambda f: (f["where"] == "train", ORDER.get(f["confidence"], 3), -counts[id(f)]))
    rows = []
    for f in fixes[:110]:
        hit = find(f["word"], f["when"], f["where"], lines)
        made = row(f["word"], f["ipa"], f["why"], f["confidence"], hit, counts[id(f)]) if hit else None
        if made:
            rows.append(made)
            print(len(rows), flush=True)
    page = ("<!doctype html><meta charset=utf-8><title>Puki fixes</title><style>body{font-family:system-ui;margin:16px;"
            "background:#fff;color:#222}td{border-top:1px solid #ddd;padding:6px;vertical-align:top}.w{font-size:22px}"
            ".c{font-size:12px;color:#666}audio{height:30px;width:190px}</style><h1>Pronunciation fixes</h1>"
            "<p>Each word in a real line: first as Puki says it now, then fixed. Recordings first, then the doubtful "
            "ones, then the most common.</p><table><tr><th>word</th><th>was / fix</th><th>line</th><th>now</th>"
            "<th>fixed</th><th></th><th>why</th></tr>" + "".join(rows) + "</table>")
    os.makedirs(os.path.join(DATA, "listen"), exist_ok=True)
    out = os.path.join(DATA, "listen", "fixes.html")
    open(out, "w", encoding="utf-8").write(page)
    print(f"{len(rows)} rows -> {out}")


if __name__ == "__main__":
    main()
