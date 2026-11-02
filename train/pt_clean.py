"""The Hebrew side of the small model's pretraining: HeDC4 (a cleaned Common Crawl corpus) cut down to
text fit for a model that talks to a toddler. There's far more than a small model can use, so this
can be picky: whole documents go at the first sign of adult, gambling or drug sites, SEO filler
or machine-spun text.

    python pt_clean.py --csv C:/models/pt/hedc4/HeDC4.csv --out C:/models/pt/hebrew --keep 0.4 --max-gb 14

Writes <out>/hebrew-NNNN.json.gz ({"text": ...} per line, what pretrain/corpus.py reads) and prints
why documents went.
"""
import argparse
import collections
import csv
import gzip
import json
import multiprocessing
import os
import random
import re
import zlib

BLOCK = re.compile("|".join([
    # adult
    "סקס", "פורנ", "ארוטי", "עירום", "עירומ", "זונה", "זונות", "נערות ליווי", "נערת ליווי", "דירות דיסקרטיות",
    "דירה דיסקרטית", "דיסקרטי", "אורגזמה", "מין אוראלי", "בורדל", "חשפנ", "אונס", "נאנס", "תקיפה מינית", "הטרדה מינית",
    "פדופיל", "איבר מין", "הגדלת פין", "הגדלת חזה", "ויאגרה", "סיאליס", "זיון", "לזיין", "מזיין",
    # gambling
    "קזינו", "הימורים", "הימור", "פוקר", "רולטה", "בלאק ג'ק", "מכונות מזל",
    # drugs and self-harm
    "קנאביס", "מריחואנה", "חשיש", "קוקאין", "הרואין", "סמים", "התאבד", "התאבדות", "אובדנ", "לחתוך ורידים",
    # graphic violence
    "ערופי", "עריפת", "רצח", "נרצח", "טבח", "שחיטה",
]))
SEO = re.compile("|".join([
    "ברוכים הבאים לאתר", "בדף זה תמצאו", "לחצו כאן", "לחץ כאן", "מחירון", "כמה עולה", "הצעת מחיר", "קידום אתרים", "השאירו פרטים",
    "צרו קשר", "צור קשר", "לפרטים נוספים", "משלוח חינם", "הוסף לסל", "כל הזכויות שמורות", "תנאי שימוש", "חברה מובילה",
    "הלוואה", "הלוואות", "משכנתא", "ביטוח", "עורך דין", "עורכי דין", "מנעולן", "אינסטלטור", "הובלות", "קריפטו", "ביטקוין", "מסחר בבינארי",
]))
HEBREW = re.compile("[א-ת]")
LATIN = re.compile("[A-Za-z]")
NIKUD = re.compile("[\u0591-\u05C7]")
URL = re.compile(r"https?://\S+|www\.\S+")
MIN_CHARS = 300
MAX_CHARS = 40_000
MIN_HEBREW = 0.96  # share of letters: mixed Latin in Hebrew text is mostly machine-spun
MIN_PUNCTUATION = 0.006  # a list of keywords has almost no sentence ends


def clean(args):
    """(text or None, why it went) for one document."""
    text, seed = args
    if not random.Random(seed).random() < KEEP:
        return None, "sampled out"
    if len(text) < MIN_CHARS:
        return None, "too short"
    text = NIKUD.sub("", URL.sub("", text))[:MAX_CHARS].strip()
    hebrew, latin = len(HEBREW.findall(text)), len(LATIN.findall(text))
    if hebrew < 200 or hebrew / (hebrew + latin) < MIN_HEBREW:
        return None, "not Hebrew enough"
    if BLOCK.search(text):
        return None, "blocked subject"
    if SEO.search(text):
        return None, "SEO filler"
    if sum(text.count(c) for c in ".!?") / len(text) < MIN_PUNCTUATION:
        return None, "no sentences"
    lines = [l for l in text.split("\n") if l.strip()]
    if len(lines) >= 6 and len(set(lines)) / len(lines) < 0.8:
        return None, "repeats itself"
    return text, "kept"


def init(keep):
    global KEEP
    KEEP = keep


def rows(path):
    csv.field_size_limit(10 ** 9)
    with open(path, encoding="utf-8", errors="replace", newline="") as f:
        reader = csv.reader(f)
        next(reader)
        for i, row in enumerate(reader):
            if row:
                yield row[0], i


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--keep", type=float, default=0.4, help="the share of documents even looked at, spread over the file")
    ap.add_argument("--max-gb", type=float, default=14)
    ap.add_argument("--workers", type=int, default=max(2, (os.cpu_count() or 4) - 4))
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    why, seen = collections.Counter(), set()
    written = shard = in_shard = 0
    out = gzip.open(os.path.join(a.out, "hebrew-0000.json.gz"), "wt", encoding="utf-8")
    with multiprocessing.Pool(a.workers, init, (a.keep,)) as pool:
        for n, (text, reason) in enumerate(pool.imap(clean, rows(a.csv), chunksize=256)):
            if text:
                key = zlib.crc32(text[:400].encode("utf-8", "ignore"))
                if key in seen:
                    reason, text = "duplicate", None
                else:
                    seen.add(key)
            why[reason] += 1
            if text:
                out.write(json.dumps({"text": text}, ensure_ascii=False) + "\n")
                size = len(text.encode("utf-8"))
                written += size
                in_shard += size
                if in_shard > 500_000_000:
                    out.close()
                    shard += 1
                    in_shard = 0
                    out = gzip.open(os.path.join(a.out, f"hebrew-{shard:04d}.json.gz"), "wt", encoding="utf-8")
            if n % 200_000 == 0:
                print(n, f"{written / 1e9:.2f} GB", dict(why), flush=True)
            if written > a.max_gb * 1e9:
                pool.terminate()
                break
    out.close()
    print(f"done: {written / 1e9:.2f} GB", dict(why), flush=True)


if __name__ == "__main__":
    main()
