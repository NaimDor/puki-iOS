"""The small model's corpus, in the format of the Skyrim harness's pretrain/corpus.py, so its trainer runs
on it unchanged: a byte-level BPE trained on the Hebrew-heavy mix plus Puki's own lines, every document as uint16 tokens in one file per source, a chunk index, and reference sets of
Puki's prompts and answers.

    <harness train venv python> pt_corpus.py compare --out C:/data/pretrain/puki-tok
    <harness train venv python> pt_corpus.py build --vocab 12288 --out C:/data/pretrain/puki-tok

"compare" trains the BPE at several sizes and prints what each costs per Puki line. English sources
leave out raw FineWeb: unfiltered web text has no place in a model that talks to a toddler.
"""
import argparse
import gzip
import hashlib
import json
import os
import random
from pathlib import Path

import numpy as np
import pyarrow.parquet as pq

HERE = Path(__file__).resolve().parent
RAW_EN = Path(os.environ.get("PUKI_RAW_EN", r"C:\data\pretrain\pilot"))
RAW_HE = Path(os.environ.get("PUKI_RAW_HE", r"C:\models\pt\hebrew"))
SPECIAL = ["<|endoftext|>", "<|im_start|>", "<|im_end|>"]
CHUNK = 512
MIN_CHUNK = 64
SEQ = 1024
HEBREW_SHARE = 0.75

# name -> (folder, format, share of the English tokens)
SOURCES = {
    "hebrew": (RAW_HE, "jsonl.gz", None),
    "fineweb-edu": (RAW_EN / "fineweb-edu", "parquet", 0.45),
    "cosmopedia": (RAW_EN / "cosmopedia", "parquet", 0.25),
    "gutenberg": (RAW_EN / "gutenberg", "jsonl.gz", 0.15),
    "youtube": (RAW_EN / "youtube", "jsonl.gz", 0.15),
}
# One language only (PUKI_LANG=he): no English sources, and only that language's lines of Puki's
LANG = os.environ.get("PUKI_LANG", "")
if LANG == "he":
    SOURCES = {"hebrew": SOURCES["hebrew"]}
    HEBREW_SHARE = 1.0
# The mid-train: Puki's own training lines as a last source, MID_REPEAT times over, kept out of the stable
# phase by an allowed-chunks list and mixed into one cooldown branch (PUKI_MID: the SFT jsonl files)
MID = [Path(p) for p in os.environ.get("PUKI_MID", "").split(",") if p]
MID_REPEAT = int(os.environ.get("PUKI_MID_REPEAT", "6"))
MID_WEB = float(os.environ.get("PUKI_MID_WEB", "0.64e9"))  # web tokens beside them in the mid branch's list
if MID:
    SOURCES["puki"] = (None, "puki", None)


def docs(name):
    """The text of every document of one source, in file order."""
    folder, fmt, _ = SOURCES[name]
    if fmt == "puki":
        rows = [strip(r["text"]) for f in MID for r in map(json.loads, open(f, encoding="utf-8")) if not LANG or r["lang"] == LANG]
        for _ in range(MID_REPEAT):
            yield from rows
        return
    for f in sorted(folder.rglob("*.parquet" if fmt == "parquet" else "*.json.gz")):
        if fmt == "parquet":
            for batch in pq.ParquetFile(f).iter_batches(columns=["text"], batch_size=2048):
                yield from (t for t in batch.column("text").to_pylist() if t)
        else:
            with gzip.open(f, "rt", encoding="utf-8") as fh:
                for line in fh:
                    t = json.loads(line).get("text")
                    if t:
                        yield t


def puki_rows():
    with open(HERE / "data" / "final.jsonl", encoding="utf-8") as f:
        return [r for r in map(json.loads, f) if not LANG or r["lang"] == LANG]


def strip(text):
    """A training example as the text model sees it: the reading model writes the phonemes."""
    return text.replace("<|endoftext|>", "").partition("\nsay:")[0]


def sample(seed, sample_mb):
    """Text for the BPE: the mix by share, and Puki's lines, several times over so their words get merges."""
    rng = random.Random(seed)
    total = sample_mb * 1_000_000
    for name, (_, fmt, share) in SOURCES.items():
        if fmt == "puki":
            continue  # (Puki's lines come in below, as before)
        want = total * (HEBREW_SHARE if share is None else (1 - HEBREW_SHARE) * share)
        got = 0
        for t in docs(name):
            if rng.random() < 0.2:
                t = t[:20_000]  # (a book contributes its opening, not a whole share)
                got += len(t)
                yield t
                if got >= want:
                    break
    rows = puki_rows()
    for _ in range(20):
        for r in rows:
            yield strip(r["text"])


def train_bpe(vocab, seed, sample_mb):
    from tokenizers import ByteLevelBPETokenizer
    tok = ByteLevelBPETokenizer()
    tok.train_from_iterator(sample(seed, sample_mb), vocab_size=vocab, min_frequency=2, special_tokens=SPECIAL)
    return tok


def costs(tok):
    """Tokens per answer, the part that's written at runtime: Hebrew, English, and the whole example."""
    he, en, whole = [], [], []
    for r in puki_rows()[::7]:
        text = strip(r["text"])
        n = len(tok.encode(text.partition("\npuki:")[2]).ids)
        whole.append(len(tok.encode(text).ids))
        if r["task"] != "gate":
            (he if r["lang"] == "he" else en).append(n)
    mid = lambda v: sorted(v)[len(v) // 2]
    return {"hebrew_answer": mid(he), "hebrew_p95": sorted(he)[int(len(he) * .95)], "english_answer": mid(en), "example": mid(whole)}


def compare(a):
    for vocab in (4096, 8192, 12288, 16384):
        tok = train_bpe(vocab, a.seed, a.sample_mb)
        tok.save(str(Path(a.out) / f"bpe-{vocab}.json"))
        print(vocab, costs(tok), flush=True)


def references(tok, out):
    def encode(text):
        prompt, _, answer = strip(text).partition("\npuki:")
        p = tok.encode(prompt + "\npuki:").ids
        ans = tok.encode(answer).ids + [tok.token_to_id("<|endoftext|>")]
        return {"ids": (p + ans)[-SEQ:], "start": len(p)}

    with open(HERE / "data" / "final_heldout.jsonl", encoding="utf-8") as f:
        rows = [r for r in map(json.loads, f) if not LANG or r["lang"] == LANG]
    sets = {"probe": [], "heldout": []}
    for r in rows:
        side = "heldout" if int(hashlib.sha1(r["input"].encode()).hexdigest()[:8], 16) % 2 else "probe"
        sets[side].append({**encode(r["text"]), "task": r["task"]})
    for k, v in sets.items():
        (out / f"reference-{k}.json").write_text(json.dumps(v), encoding="utf-8")
        print(k, len(v))


def build(a):
    from tokenizers import Tokenizer
    out = Path(a.out)
    picked = out / f"bpe-{a.vocab}.json"
    tok = Tokenizer.from_file(str(picked)) if picked.exists() else train_bpe(a.vocab, a.seed, a.sample_mb)
    tok.save(str(out / "tokenizer.json"))
    tok = Tokenizer.from_file(str(out / "tokenizer.json"))
    eot = tok.token_to_id("<|endoftext|>")
    chunks, meta = [], {}
    hebrew_tokens = 0
    for sid, (name, (_, fmt, share)) in enumerate(SOURCES.items()):
        budget = 1e18 if share is None else hebrew_tokens * (1 - HEBREW_SHARE) / HEBREW_SHARE * share
        n_tok = n_doc = 0
        with (out / f"{name}.bin").open("wb") as fbin:
            batch = []

            def flush():
                nonlocal n_tok, n_doc
                for enc in tok.encode_batch(batch):
                    ids = enc.ids + [eot]
                    for s in range(0, len(ids), CHUNK):
                        if len(ids) - s >= MIN_CHUNK:
                            chunks.append((sid, n_tok + s, min(CHUNK, len(ids) - s)))
                    np.asarray(ids, dtype=np.uint16).tofile(fbin)
                    n_tok += len(ids)
                    n_doc += 1
                batch.clear()

            for t in docs(name):
                batch.append(t)
                if len(batch) >= 1024:
                    flush()
                    if n_tok >= budget:
                        break
            if batch:
                flush()
        if name == "hebrew":
            hebrew_tokens = n_tok
        meta[name] = {"id": sid, "tokens": n_tok, "docs": n_doc}
        print(f"{name}: {n_doc} docs, {n_tok / 1e9:.3f}B tokens", flush=True)
    np.save(out / "chunks.npy", np.asarray(chunks, dtype=np.int64))
    if MID:
        # stable.npy: everything but Puki's lines. mid.npy: his lines and a random MID_WEB tokens of the rest, so
        # the mid branch's cooldown (which samples its allowed chunks evenly) is a few percent Puki
        mid = meta["puki"]["id"]
        web = [i for i, c in enumerate(chunks) if c[0] != mid]
        np.save(out / "stable.npy", np.asarray(web, dtype=np.int64))
        random.Random(a.seed).shuffle(web)
        sizes = np.asarray([chunks[i][2] for i in web])
        take = web[:int(np.searchsorted(np.cumsum(sizes), MID_WEB))]
        ours = [i for i, c in enumerate(chunks) if c[0] == mid]
        np.save(out / "mid.npy", np.asarray(sorted(take + ours), dtype=np.int64))
        print(f"mid branch: {meta['puki']['tokens'] / 1e6:.1f}M tokens of Puki's lines, {len(take)} web chunks")
    (out / "sources.json").write_text(json.dumps(meta, indent=1), encoding="utf-8")
    references(tok, out)
    print(f"{len(chunks)} chunks -> {out}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["compare", "build"])
    ap.add_argument("--out", required=True)
    ap.add_argument("--vocab", type=int, default=12288)
    ap.add_argument("--sample-mb", type=int, default=300)
    ap.add_argument("--seed", type=int, default=0)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    (compare if a.mode == "compare" else build)(a)


if __name__ == "__main__":
    main()
