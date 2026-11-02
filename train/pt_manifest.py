"""What went into the small model's pretraining, exactly: writes pt_manifest.json, which goes in the repo, so
anyone with the same public data can check it. The text itself isn't published here.

    <harness train venv python> pt_manifest.py
    <harness train venv python> pt_manifest.py --chunks used.npy    # also saves the chunk ids trained on

The trainer keeps no list of what it read, but its order comes only from the seed, so replay() walks the
same draws from the chunk lengths alone. A chunk is (source, token offset, length), and the document it
came from is the one counted by the end-of-text tokens before that offset, in the file order of
pt_corpus.docs(). Each source used its first "docs" documents in that order.
"""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path

import numpy as np

from pt_corpus import HEBREW_SHARE, SOURCES

HERE = Path(__file__).resolve().parent
HF = "https://huggingface.co/datasets/"
HEBREW_CSV = Path(r"C:\models\pt\hedc4\HeDC4.csv")
EOT = 0  # <|endoftext|>, the first of pt_corpus.SPECIAL

# Where each source came from. English commits are read from the download's own cache; the licences are as
# each source states them, checked at the source on 2026-11-02.
ORIGIN = {
    "hebrew": {"repo": "HeNLP/HeDC4", "files": "HeDC4.csv", "commit": "84395a7af1fc1be19ad02df0af609069253a9f9e",
               "licence": None, "note": "cleaned by pt_clean.py, see \"cleaning\"; the card states no licence and asks to cite "
                                        "Shalumov & Haskey, HeRo, arXiv:2304.11077"},
    "fineweb-edu": {"repo": "HuggingFaceFW/fineweb-edu", "files": "sample/10BT/", "licence": "ODC-By",
                    "note": "also subject to the Common Crawl terms of use"},
    "cosmopedia": {"repo": "HuggingFaceTB/cosmopedia", "files": "data/stories/", "licence": "Apache-2.0"},
    "gutenberg": {"repo": "common-pile/project_gutenberg_filtered", "files": "project_gutenberg-dolma-*", "licence": None,
                  "note": "no licence tag; books marked public domain in upstream metadata"},
    "youtube": {"repo": "common-pile/youtube_filtered", "files": "youtube-commons-*", "licence": None,
                "note": "no licence tag; each video's row states its own, CC BY as the card describes"},
}

# The steps, as they were run.
CLEANING = {
    "command": "pt_clean.py --csv HeDC4.csv --out hebrew --keep 0.45 --max-gb 12",
    "documents": {"sampled out": 4679097, "kept": 2166567, "too short": 54510, "not Hebrew enough": 394140,
                  "blocked subject": 745563, "SEO filler": 382401, "no sentences": 59563, "repeats itself": 7637},
    "note": "the duplicates dropped weren't kept in the run's output",
}
CORPUS = {"commands": ["pt_corpus.py compare --out puki-tok", "pt_corpus.py build --vocab 12288 --out puki-tok"], "hebrew_share": HEBREW_SHARE}
RUN = {"name": "puki-48m", "command": "pt_run.py --vocab 12288 --tok puki-tok --run puki-48m --until 1.6e9",
       "seed": 0, "micro": 16, "accum": 8, "seq": 1024, "until": 1.6e9, "pack": "v1", "resumed": False,
       "optimizer": "adamw", "lr": 2e-3, "beta2": 0.95, "weight_decay": 0.1, "size": "50m"}


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while block := f.read(1 << 24):
            h.update(block)
    return h.hexdigest()


def raw_files(name):
    if name == "hebrew":
        return sorted(SOURCES[name][0].glob("*.json.gz"))
    pattern = "*.parquet" if SOURCES[name][1] == "parquet" else "*.json.gz"
    return sorted(p for p in SOURCES[name][0].rglob(pattern) if ".cache" not in p.parts)


def commit(name):
    """The commit the download came from, out of huggingface_hub's cache beside it."""
    if "commit" in ORIGIN[name]:
        return ORIGIN[name]["commit"]
    found = {p.read_text().split()[0] for p in (SOURCES[name][0] / ".cache").rglob("*.metadata")}
    return found.pop() if len(found) == 1 else sorted(found)


def replay(chunks, r=RUN):
    """Chunk ids and tokens used of each, in the trainer's order: pretrain/train.py main(), packing v1. Each
    micro-batch draws three chunks a row and packs them until its rows are full, a row's last chunk cut there;
    what's left of the draw goes unread."""
    rng = np.random.default_rng(r["seed"])
    eval_ids = np.random.default_rng(12345).choice(len(chunks), 512, replace=False)
    allowed = np.setdiff1d(np.arange(len(chunks)), eval_ids)
    order, pos = rng.permutation(allowed), 0
    lengths = chunks[:, 2]
    per_step = r["micro"] * r["accum"] * r["seq"]
    steps = int(np.ceil(r["until"] / per_step))
    need = r["micro"] * 3
    ids, used = [], []
    for _ in range(steps * r["accum"]):
        if pos + need > len(order):
            order, pos = rng.permutation(allowed), 0
        rows, row, have = 0, [], 0
        for k in order[pos:pos + need]:
            row.append((k, min(int(lengths[k]), r["seq"] - have)))
            have += int(lengths[k])
            if have >= r["seq"]:
                ids += [x for x, _ in row]
                used += [n for _, n in row]
                rows, row, have = rows + 1, [], 0
                if rows == r["micro"]:
                    break
        pos += need
    return np.asarray(ids), np.asarray(used)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tok", type=Path, default=Path(r"C:\data\pretrain\puki-tok"))
    ap.add_argument("--chunks", type=Path, help="also save the chunk ids trained on, in order, as npy")
    a = ap.parse_args()
    meta = json.loads((a.tok / "sources.json").read_text(encoding="utf-8"))
    chunks = np.load(a.tok / "chunks.npy")
    ids, used = replay(chunks)
    total = int(used.sum())
    if a.chunks:
        np.save(a.chunks, ids)

    sources = {}
    for name, m in meta.items():
        bin_path = a.tok / f"{name}.bin"
        tokens = np.memmap(bin_path, dtype=np.uint16, mode="r")
        ends = np.flatnonzero(tokens == EOT)
        assert len(ends) == m["docs"], (name, len(ends), m["docs"])
        mine = chunks[ids, 0] == m["id"]
        reached = np.searchsorted(ends, chunks[ids[mine], 1])
        files = raw_files(name)
        sources[name] = {
            **ORIGIN[name], "url": HF + ORIGIN[name]["repo"], "commit": commit(name),
            "corpus": {"docs": m["docs"], "tokens": m["tokens"], "sha256": sha256(bin_path)},
            "trained_on": {"tokens": int(used[mine].sum()), "chunks": int(np.unique(ids[mine]).size), "docs": int(np.unique(reached).size)},
            "raw_files": {str(f.relative_to(SOURCES[name][0])).replace("\\", "/"): sha256(f) for f in files},
        }
        print(name, sources[name]["trained_on"], flush=True)
    if HEBREW_CSV.exists():
        sources["hebrew"]["csv_sha256"] = sha256(HEBREW_CSV)
    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=HERE, capture_output=True, text=True).stdout.strip()
    manifest = {
        "code_commit": head, "cleaning": CLEANING, "corpus": CORPUS, "run": RUN,
        "tokenizer_sha256": sha256(a.tok / "tokenizer.json"),
        "chunk_index_sha256": sha256(a.tok / "chunks.npy"),
        "chunks_trained_on": {"count": len(ids), "unique": int(np.unique(ids).size), "tokens": total,
                              "order_sha256": hashlib.sha256(ids.astype(np.int64).tobytes()).hexdigest()},
        "references": {k: sha256(a.tok / f"reference-{k}.json") for k in ("probe", "heldout")},
        "sources": sources,
    }
    (HERE / "pt_manifest.json").write_text(json.dumps(manifest, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(ids)} chunks ({np.unique(ids).size} unique), {total / 1e9:.3f}B tokens -> pt_manifest.json")


if __name__ == "__main__":
    main()
