"""The Hebrew side of the small model's pretraining: HeDC4 (a cleaned Common Crawl corpus) cut down to
text fit for a model that talks to a toddler. There's far more than a small model can use, so this
can be picky: whole documents go at the first sign of adult, gambling or drug sites, SEO filler
or machine-spun text.

    python pt_clean.py --csv C:/models/pt/hedc4/HeDC4.csv --out C:/models/pt/hebrew --keep 0.4 --max-gb 14
    python pt_clean.py --parquet ~/data/puki-pt/fineweb2 --out ~/data/puki-pt/hebrew --keep 1 --max-gb 40

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


def parquet_part(task):
    """One slice of a FineWeb-2 shard (50 row groups, 50k documents), cleaned in a worker and written to
    its own file, so nothing big passes between processes. FineWeb-2 is MinHash-deduplicated already."""
    import pyarrow.parquet as pq
    f, n, first, last, out = task
    why, written = collections.Counter(), 0
    pf = pq.ParquetFile(f)
    with gzip.open(out + ".tmp", "wt", encoding="utf-8") as fh:
        for rg in range(first, last):
            for k, t in enumerate(pf.read_row_group(rg, columns=["text"]).column("text").to_pylist()):
                text, reason = clean((t or "", n * 10 ** 8 + rg * 10 ** 4 + k))
                why[reason] += 1
                if text:
                    fh.write(json.dumps({"text": text}, ensure_ascii=False) + "\n")
                    written += len(text.encode("utf-8"))
    os.replace(out + ".tmp", out)
    return why, written


def clean_parquet(a):
    """Every 50 row groups of every shard as one task; a rerun skips the slices already written."""
    import pathlib
    import pyarrow.parquet as pq
    tasks = []
    for n, f in enumerate(sorted(pathlib.Path(a.parquet).rglob("*.parquet"))):
        groups = pq.ParquetFile(f).metadata.num_row_groups
        for first in range(0, groups, 50):
            out = os.path.join(a.out, f"hebrew-{n:03d}-{first:05d}.json.gz")
            if not os.path.exists(out):
                tasks.append((str(f), n, first, min(groups, first + 50), out))
    why, written = collections.Counter(), 0
    with multiprocessing.Pool(a.workers, init, (a.keep,)) as pool:
        for i, (w, size) in enumerate(pool.imap_unordered(parquet_part, tasks)):
            why.update(w)
            written += size
            print(f"{i + 1}/{len(tasks)}", f"{written / 1e9:.2f} GB", dict(why), flush=True)
            if written > a.max_gb * 1e9:
                pool.terminate()
                break
    print(f"done: {written / 1e9:.2f} GB", dict(why), flush=True)


def main():
    ap = argparse.ArgumentParser()
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--csv", help="HeDC4's CSV")
    src.add_argument("--parquet", help="a folder of parquet shards with a text column (FineWeb-2's heb_Hebr)")
    ap.add_argument("--out", required=True)
    ap.add_argument("--keep", type=float, default=0.4, help="the share of documents even looked at, spread over the file")
    ap.add_argument("--max-gb", type=float, default=14)
    ap.add_argument("--workers", type=int, default=max(2, (os.cpu_count() or 4) - 4))
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    if a.parquet:
        return clean_parquet(a)
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
