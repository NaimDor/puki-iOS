"""A page to hear each flagged word (data/flags.tsv) twice: as Puki says it now, and with the
corrected phonemes, plus each in its example line. Writes data/listen/index.html.

    <brain venv python> listen_flags.py
"""
import base64
import csv
import html
import io
import json
import os
import sys

import numpy as np
import soundfile as sf

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
cfg_path = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Puki", "brain", "config.json")
CFG = json.load(open(cfg_path, encoding="utf-8"))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "brain"))

from blue_onnx import BlueTTS  # noqa: E402

bt = os.path.expanduser(CFG["bluetts_dir"])
TTS = BlueTTS(
    onnx_dir=os.path.join(bt, "onnx_models"),
    config_path=os.path.join(bt, "config", "tts.json"),
    style_json=os.path.join(bt, "voices", f"{CFG.get('voice', 'noa')}.json"),
    renikud_path=os.path.expanduser(CFG["renikud_model"]),
)


def ogg(samples, sr):
    buf = io.BytesIO()
    sf.write(buf, np.concatenate([samples, np.zeros(int(sr * 0.1), dtype=np.float32)]), sr, format="OGG", subtype="VORBIS")
    return "data:audio/ogg;base64," + base64.b64encode(buf.getvalue()).decode()


def say_text(text):
    s, sr = TTS.synthesize(text, lang="he", total_step=8, speed=0.92)
    return ogg(s, sr)


def say_phonemes(ph):
    s, sr = TTS.synthesize(ph, lang="he", total_step=8, speed=0.92, text_is_phonemes=True)
    return ogg(s, sr)


def main():
    words = {r["word"]: r for r in csv.DictReader(open(os.path.join(DATA, "words.tsv"), encoding="utf-8"), delimiter="\t")}
    flags = list(csv.DictReader(open(os.path.join(DATA, "flags.tsv"), encoding="utf-8"), delimiter="\t"))
    rows = []
    for f in flags:
        w, fixed = f["word"], f["ipa_fixed"].strip()
        ex = words.get(w, {}).get("example", "")
        ex_ph = TTS.tts.g2p.phonemize(ex, "he") if ex else ""
        # The example line with just this word swapped for its fixed phonemes
        ex_fixed = ex_ph.replace(f["ipa_now"].strip(), fixed, 1) if ex_ph and f["ipa_now"].strip() in ex_ph else ""
        rows.append(f"<tr><td class=w>{html.escape(w)}</td><td>{html.escape(f['ipa_now'])}<br><audio controls src='{say_text(w)}'></audio></td>"
                    f"<td>{html.escape(fixed)}<br><audio controls src='{say_phonemes(fixed)}'></audio></td>"
                    f"<td class=c>{html.escape(f['confidence'])}</td><td>{html.escape(words.get(w, {}).get('count', ''))}</td>"
                    f"<td dir=rtl>{html.escape(ex)}<br>"
                    + (f"<audio controls src='{say_text(ex)}'></audio>" if ex else "")
                    + (f" fixed: <audio controls src='{say_phonemes(ex_fixed)}'></audio>" if ex_fixed else "")
                    + f"</td><td>{html.escape(f['note'])}</td></tr>")
    os.makedirs(os.path.join(DATA, "listen"), exist_ok=True)
    page = ("<!doctype html><meta charset=utf-8><title>Puki pronunciation check</title>"
            "<style>body{font-family:system-ui;margin:16px;background:#fff;color:#222}td{border-top:1px solid #ddd;padding:6px;vertical-align:top}"
            ".w{font-size:22px}.c{font-size:12px}audio{height:28px;width:180px}</style>"
            "<h1>Pronunciation check</h1><p>Each flagged word as Puki says it now, then with the fixed phonemes; "
            "and its example line both ways.</p><table><tr><th>word</th><th>now</th><th>fixed</th><th>conf.</th>"
            "<th>count</th><th>example</th><th>note</th></tr>" + "".join(rows) + "</table>")
    out = os.path.join(DATA, "listen", "index.html")
    open(out, "w", encoding="utf-8").write(page)
    print(f"{len(rows)} words -> {out}")


if __name__ == "__main__":
    main()
