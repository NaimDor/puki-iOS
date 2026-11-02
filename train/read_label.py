"""The reading model's pretraining set: short sentences of the clean Hebrew, each with RenikudPlus's
per-letter opinion, for a boy or a girl by the sentence's hash, so the gendered words get taught too.

    <brain venv python> read_label.py --sentences 600000 --out C:/data/pretrain/puki-read

Writes shard-NNNN.npz: chars (uint8 ids into ALPHABET, sentences end to end), starts (where each
begins, and the end), girl, and tags (uint8, letters x 9): consonant id, how sure of it, the six
vowel probabilities and the stress probability, the last eight out of 255. Stops and carries on: a shard
on disk is kept. The model takes one sentence at a time, about 8 a second on two threads, so it runs
in several processes, each with its own 1.2 GB copy. --gpu-workers needs an onnxruntime-gpu python (the
harness's tts venv) with the harness train venv's torch/lib on PATH for CUDA 13.
"""
import argparse
import glob
import gzip
import json
import multiprocessing
import os
import random
import re
import time
import zlib

import numpy as np

from read_tags import Teacher, clean

RAW = r"C:\models\pt\hebrew"
ALPHABET = "\0 " + "".join(chr(c) for c in range(ord("א"), ord("ת") + 1)) + ".,!?:;-'\"()"
INDEX = {c: i for i, c in enumerate(ALPHABET)}
SPLIT = re.compile(r"(?<=[.!?])\s+|\n+")
PER_DOC = 3  # (a site contributes a few sentences, not its whole page)
SHARD = 50_000


def sentences(want, seed):
    """Up to want usable sentences, spread evenly over the shards of the corpus."""
    rng = random.Random(seed)
    files = sorted(glob.glob(os.path.join(RAW, "*.json.gz")))
    seen = set()
    for n, path in enumerate(files):
        quota = (want * (n + 1) // len(files)) - (want * n // len(files))
        with gzip.open(path, "rt", encoding="utf-8") as f:
            for line in f:
                if quota <= 0:
                    break
                good = []
                for s in SPLIT.split(json.loads(line)["text"]):
                    s = clean(" ".join(s.split()))
                    if 6 <= len(s) <= 100 and s.count(" ") >= 1 and all(c in INDEX for c in s) and "\0" not in s:
                        good.append(s)
                for s in rng.sample(good, min(PER_DOC, len(good))):
                    key = zlib.crc32(s.encode())
                    if key not in seen and quota > 0:
                        seen.add(key)
                        quota -= 1
                        yield s


def softmax(a):
    a = np.exp(a - a.max(axis=-1, keepdims=True))
    return a / a.sum(axis=-1, keepdims=True)


def start(threads, gpu_left):
    global TEACHER
    with gpu_left.get_lock():
        gpu = gpu_left.value > 0
        gpu_left.value -= gpu
    TEACHER = Teacher(threads=threads, gpu=gpu)


def label(text):
    """(text, girl, tags) for one sentence."""
    t = TEACHER
    girl = bool(zlib.crc32(text.encode()) & 1)
    c, v, s = t.logits(text, 0, 2 if girl else 1)
    n = len(text)
    cons, vowel, stress = c[1:n + 1], softmax(v[1:n + 1]), softmax(s[1:n + 1])[:, 1]
    probs = softmax(cons)
    ids = cons.argmax(-1)
    for j, ch in enumerate(text):
        if ch in t.allowed and ids[j] not in t.allowed[ch]:
            ids[j] = max(t.allowed[ch], key=lambda k: cons[j, k])
    tags = np.zeros((n, 9), dtype=np.uint8)
    tags[:, 0] = ids
    tags[:, 1] = np.rint(probs[np.arange(n), ids] * 255)
    tags[:, 2:8] = np.rint(vowel * 255)
    tags[:, 8] = np.rint(stress * 255)
    return text, girl, tags


def write(path, rows):
    chars = np.frombuffer(bytes(INDEX[c] for s, _, _ in rows for c in s), dtype=np.uint8)
    starts = np.cumsum([0] + [len(s) for s, _, _ in rows], dtype=np.int64)
    tmp = path + ".tmp.npz"
    np.savez(tmp, chars=chars, starts=starts, girl=np.array([g for _, g, _ in rows], dtype=np.uint8), tags=np.concatenate([x for _, _, x in rows]))
    os.replace(tmp, path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sentences", type=int, default=600_000)
    ap.add_argument("--out", default=r"C:\data\pretrain\puki-read")
    ap.add_argument("--workers", type=int, default=6)
    ap.add_argument("--gpu-workers", type=int, default=0, help="how many of the workers use the GPU")
    ap.add_argument("--threads", type=int, default=2)
    ap.add_argument("--seed", type=int, default=0)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    done = len(glob.glob(os.path.join(a.out, "shard-*.npz")))

    def todo():
        for i, s in enumerate(sentences(a.sentences, a.seed)):
            if i >= done * SHARD:
                yield s

    began, count = time.time(), 0
    rows = []
    with multiprocessing.Pool(a.workers, start, (a.threads, multiprocessing.Value("i", a.gpu_workers))) as pool:
        for row in pool.imap(label, todo(), chunksize=8):
            rows.append(row)
            count += 1
            if len(rows) == SHARD:
                write(os.path.join(a.out, f"shard-{done:04d}.npz"), rows)
                rows, done = [], done + 1
            if count % 2000 == 0:
                print(f"{count} labelled, {count / (time.time() - began):.1f}/s, {done} shards", flush=True)
    if rows:
        write(os.path.join(a.out, f"shard-{done:04d}.npz"), rows)
    print(f"done: {count} labelled in {(time.time() - began) / 3600:.1f} h")


if __name__ == "__main__":
    main()
