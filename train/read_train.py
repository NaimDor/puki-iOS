"""Trains the reading model: a small bidirectional transformer that reads a line letter by letter and
gives each Hebrew letter a consonant, a vowel and stress, as RenikudPlus does (read_tags.py), with the
child's gender as the first token. Pretraining learns RenikudPlus's opinion over the clean Hebrew
(read_label.py); --finetune learns the reviewed lines.

    python read_train.py --run read-1 --epochs 12
    python read_train.py --run read-1 --steps 200    # a quick try

Every epoch it scores the reviewed lines (data/say_final.jsonl) the way they're judged: each word's
IPA, spelled from the predicted tags, against the reviewed IPA. RenikudPlus told the gender scores
90.22% of words, 63.1% of lines there (read_tags.py). Runs go to C:/data/pretrain/puki-read-runs.
"""
import argparse
import glob
import json
import math
import os
import re
import sys
import time
from types import SimpleNamespace

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

from read_label import ALPHABET, INDEX
from read_tags import NAME, NOTHING, Teacher, clean, is_hebrew

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
UNK, GIRL, BOY = len(ALPHABET), len(ALPHABET) + 1, len(ALPHABET) + 2
SYMBOLS = len(ALPHABET) + 3
MAX_LEN = 160
HEBREW_IDS = torch.tensor([is_hebrew(c) for c in ALPHABET] + [False] * 3)


def vocab():
    v = json.load(open(os.path.join(DATA, "read_vocab.json"), encoding="utf-8"))
    return SimpleNamespace(consonants={int(k): x for k, x in v["consonants"].items()},
                           vowels={int(k): x for k, x in v["vowels"].items()}, allowed=v["allowed"], geresh=v["geresh"])


def allowed_mask(v):
    """symbols x consonants: which consonants each letter may take (anything for non-letters, unused)."""
    m = torch.ones(SYMBOLS, len(v.consonants), dtype=torch.bool)
    for ch, ids in v.allowed.items():
        m[INDEX[ch]] = False
        m[INDEX[ch], ids] = True
    return m


class Reader(nn.Module):
    def __init__(self, n_cons, n_vowels, dim=128, layers=4, heads=4, ff=384):
        super().__init__()
        self.embed = nn.Embedding(SYMBOLS, dim)
        self.pos = nn.Embedding(MAX_LEN + 1, dim)
        layer = nn.TransformerEncoderLayer(dim, heads, ff, dropout=0.1, batch_first=True, norm_first=True, activation="gelu")
        self.body = nn.TransformerEncoder(layer, layers, enable_nested_tensor=False)
        self.norm = nn.LayerNorm(dim)
        self.cons, self.vowel, self.stress = nn.Linear(dim, n_cons), nn.Linear(dim, n_vowels), nn.Linear(dim, 1)

    def forward(self, ids, pad):
        x = self.embed(ids) + self.pos(torch.arange(ids.shape[1], device=ids.device))
        x = self.norm(self.body(x, src_key_padding_mask=pad))[:, 1:]  # (the gender token says nothing of its own)
        return self.cons(x), self.vowel(x), self.stress(x).squeeze(-1)


def encode(text, girl):
    return [GIRL if girl else BOY] + [INDEX.get(c, UNK) for c in text[:MAX_LEN]]


class Shards:
    """The labelled sentences, all in memory (600k sentences are about 40 MB of letters)."""

    def __init__(self, folder, held=2000):
        parts = [np.load(p) for p in sorted(glob.glob(os.path.join(folder, "shard-*.npz")))]
        self.chars = np.concatenate([p["chars"] for p in parts])
        self.tags = np.concatenate([p["tags"] for p in parts])
        offsets = np.cumsum([0] + [len(p["chars"]) for p in parts])
        self.starts = np.concatenate([p["starts"][:-1] + o for p, o in zip(parts, offsets)])
        self.ends = np.concatenate([p["starts"][1:] + o for p, o in zip(parts, offsets)])
        self.girl = np.concatenate([p["girl"] for p in parts])
        self.held = np.arange(len(self.starts))[-held:]
        self.train = np.arange(len(self.starts))[:-held]

    def batch(self, idx, device):
        n = max(self.ends[i] - self.starts[i] for i in idx) + 1
        ids = torch.zeros(len(idx), n, dtype=torch.long)
        tags = torch.zeros(len(idx), n - 1, 9, dtype=torch.float32)
        for row, i in enumerate(idx):
            s, e = self.starts[i], self.ends[i]
            ids[row, 0] = GIRL if self.girl[i] else BOY
            ids[row, 1:e - s + 1] = torch.from_numpy(self.chars[s:e].astype(np.int64))
            tags[row, :e - s] = torch.from_numpy(self.tags[s:e].astype(np.float32))
        return ids.to(device), tags.to(device)


class Lines(Shards):
    """Reviewed lines as tags, in the shards' layout: each word's reviewed IPA turned into the per-letter tags
    that spell it (read_tags.align), the closest to what `model` reads where several do. A word no tags
    can say (a letter's name, "mmm") has its letters' certainty at 0, so it teaches nothing."""

    def __init__(self, rows, v, model, mask, device):
        texts = [" ".join(clean(r["line"]).split()) for r in rows]
        girls = [bool(r["girl"]) for r in rows]
        near = read(model, texts, girls, v, mask, device)
        speller = SimpleNamespace(**vars(v))
        chars, tags, self.girl, starts, skipped = [], [], [], [0], 0
        for text, girl, guess, r in zip(texts, girls, near, rows):
            t = np.zeros((len(text), 9), dtype=np.uint8)
            at = 0
            for w, p in zip(text.split(), r["say"].split()):
                got = None if NAME in w else Teacher.align(speller, w, p, guess[at:at + len(w)])
                if got:
                    for i, (c, vw, s) in enumerate(got[0]):
                        if is_hebrew(w[i]):
                            t[at + i] = [c, 255] + [255 if k == vw else 0 for k in range(6)] + [255 if s else 0]
                elif any(map(is_hebrew, w)):
                    skipped += 1
                at += len(w) + 1
            chars.append(np.frombuffer(bytes(INDEX.get(c, UNK) for c in text), dtype=np.uint8))
            tags.append(t)
            self.girl.append(girl)
            starts.append(starts[-1] + len(text))
        self.chars, self.tags = np.concatenate(chars), np.concatenate(tags)
        self.starts, self.ends = np.array(starts[:-1]), np.array(starts[1:])
        self.girl = np.array(self.girl)
        self.train = np.arange(len(rows))
        self.held = self.train[:0]
        print(f"{len(rows)} reviewed lines as tags ({skipped} words no tags can say, left out)", flush=True)


def split_reviewed(rows):
    """(train, test): the test lines are the ones written for held-out inputs (build.py's heldout())."""
    with open(os.path.join(DATA, "train_heldout.jsonl"), encoding="utf-8") as f:
        held = {r["line"] for r in map(json.loads, f) if r["lang"] == "he"}
    return [r for r in rows if r["line"] not in held], [r for r in rows if r["line"] in held]


def losses(model, ids, tags, mask):
    """Consonant (the teacher's pick, weighted by how sure it was), vowel and stress (its probabilities).
    A letter with no certainty at all (a word reviewed lines can't spell) teaches nothing."""
    pad = ids == 0
    cons, vowel, stress = model(ids, pad)
    letters = HEBREW_IDS.to(ids.device)[ids[:, 1:]] & ~pad[:, 1:] & (tags[..., 1] > 0)
    cons = cons.masked_fill(~mask.to(ids.device)[ids[:, 1:]], -1e4)
    target, sure = tags[..., 0].long(), tags[..., 1] / 255
    l_cons = (F.cross_entropy(cons.transpose(1, 2), target, reduction="none") * sure)[letters].mean()
    l_vowel = -(F.log_softmax(vowel, -1) * (tags[..., 2:8] / 255))[letters].sum(-1).mean()
    l_stress = F.binary_cross_entropy_with_logits(stress, tags[..., 8] / 255, reduction="none")[letters].mean()
    return l_cons, l_vowel, l_stress


@torch.no_grad()
def read(model, texts, girls, v, mask, device):
    """Tags per character for each text, decided as G2P.phonemize decides them: allowed consonants, and
    the surest vowel-bearing letter of each word stressed."""
    model.eval()
    out = []
    for at in range(0, len(texts), 256):
        rows = [encode(t, g) for t, g in zip(texts[at:at + 256], girls[at:at + 256])]
        n = max(map(len, rows))
        ids = torch.zeros(len(rows), n, dtype=torch.long)
        for r, x in enumerate(rows):
            ids[r, :len(x)] = torch.tensor(x)
        ids = ids.to(device)
        cons, vowel, stress = model(ids, ids == 0)
        cons = cons.masked_fill(~mask.to(device)[ids[:, 1:]], -1e4).argmax(-1).cpu().numpy()
        vowel, stress = vowel.argmax(-1).cpu().numpy(), stress.float().cpu().numpy()
        for r, text in enumerate(texts[at:at + 256]):
            tags = [[int(cons[r, i]), int(vowel[r, i]), False] for i in range(len(text))]
            for w in re.finditer(r"\S+", text):
                voiced = [i for i in range(w.start(), w.end()) if tags[i][1] != NOTHING]
                if voiced:
                    tags[max(voiced, key=lambda i: stress[r, i])][2] = True
            out.append(tags)
    model.train()
    return out


def reviewed():
    with open(os.path.join(DATA, "say_final.jsonl"), encoding="utf-8") as f:
        return [r for r in map(json.loads, f) if r.get("say")]


def score(model, rows, v, mask, device):
    """Words and whole lines whose spelled IPA is the reviewed IPA, @name words left out."""
    texts = [" ".join(clean(r["line"]).split()) for r in rows]
    tags = read(model, texts, [bool(r["girl"]) for r in rows], v, mask, device)
    words = right = lines = 0
    for r, text, t in zip(rows, texts, tags):
        said, want = Teacher.say(v, text, t).split(), r["say"].split()
        if len(said) != len(want):
            continue
        pairs = [(a, b) for w, a, b in zip(text.split(), said, want) if NAME not in w]
        words += len(pairs)
        right += sum(a == b for a, b in pairs)
        lines += all(a == b for a, b in pairs)
    return right / max(words, 1), lines / max(len(rows), 1)


@torch.no_grad()
def agreement(model, data, mask, device):
    """How often it picks the teacher's tags on held-out sentences: consonant, vowel, stress (letters)."""
    model.eval()
    hits, n = np.zeros(3), 0
    for at in range(0, len(data.held), 256):
        ids, tags = data.batch(data.held[at:at + 256], device)
        pad = ids == 0
        cons, vowel, stress = model(ids, pad)
        letters = HEBREW_IDS.to(device)[ids[:, 1:]] & ~pad[:, 1:]
        cons = cons.masked_fill(~mask.to(device)[ids[:, 1:]], -1e4)
        hits += [((cons.argmax(-1) == tags[..., 0].long()) & letters).sum().item(),
                 ((vowel.argmax(-1) == tags[..., 2:8].argmax(-1)) & letters).sum().item(),
                 (((stress > 0) == (tags[..., 8] > 127)) & letters).sum().item()]
        n += letters.sum().item()
    model.train()
    return hits / max(n, 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--data", default=r"C:\data\pretrain\puki-read")
    ap.add_argument("--out", default=r"C:\data\pretrain\puki-read-runs")
    ap.add_argument("--epochs", type=float, default=12)
    ap.add_argument("--steps", type=int, default=0, help="stop after this many steps (a quick try)")
    ap.add_argument("--batch", type=int, default=256)
    ap.add_argument("--lr", type=float, default=2e-3)
    ap.add_argument("--dim", type=int, default=128)
    ap.add_argument("--layers", type=int, default=4)
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--finetune", help="a pretrained run's best.pt: learn the reviewed lines from it, half of each "
                                       "batch, the other half the labelled sentences (--steps sets the length)")
    ap.add_argument("--fit-share", type=float, default=1.0, help="fine-tune on this share of the reviewed lines")
    ap.add_argument("--extra", help="more lines to fine-tune on beside the reviewed ones (read_more.py: unreviewed)")
    a = ap.parse_args()
    torch.manual_seed(a.seed)
    rng = np.random.default_rng(a.seed)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    v = vocab()
    mask = allowed_mask(v)
    data = Shards(a.data)
    rows = reviewed()
    fit_rows, test_rows = split_reviewed(rows)
    if a.finetune:
        ck = torch.load(a.finetune, map_location="cpu")
        a.dim, a.layers = ck["args"]["dim"], ck["args"]["layers"]
    model = Reader(len(v.consonants), len(v.vowels), a.dim, a.layers, ff=a.dim * 3).to(device)
    lines = None
    if a.finetune:
        model.load_state_dict(ck["model"])
        fit_rows = [fit_rows[i] for i in sorted(rng.permutation(len(fit_rows))[:int(len(fit_rows) * a.fit_share)])]
        if a.extra:
            held = {r["line"] for r in test_rows}
            with open(a.extra, encoding="utf-8") as f:
                fit_rows += [r for r in map(json.loads, f) if r.get("say") and r["line"] not in held]
        lines = Lines(fit_rows, v, model, mask, device)
        rows = test_rows  # (what it learns from can't be what it's scored on)
    print(f"{a.run}: {sum(p.numel() for p in model.parameters()) / 1e6:.2f}M params, {len(data.train)} sentences, "
          f"{len(rows)} reviewed lines to score, {len(test_rows)} of them held out", flush=True)
    per_epoch = len(data.train) // a.batch
    total = a.steps or int(a.epochs * per_epoch)
    opt = torch.optim.AdamW(model.parameters(), lr=a.lr, weight_decay=0.01)
    warm = min(500, total // 10)
    sched = torch.optim.lr_scheduler.LambdaLR(opt, lambda s: min(1, (s + 1) / warm) * 0.5 * (1 + math.cos(math.pi * min(s, total) / total)))
    out = os.path.join(a.out, a.run)
    os.makedirs(out, exist_ok=True)
    log = open(os.path.join(out, "log.jsonl"), "a", encoding="utf-8")
    best, order, pos, t0 = -1.0, rng.permutation(data.train), 0, time.time()
    every = a.steps // 4 if a.steps else per_epoch
    for step in range(1, total + 1):
        web = a.batch // 2 if lines else a.batch
        if pos + web > len(order):
            order, pos = rng.permutation(data.train), 0
        ids, tags = data.batch(order[pos:pos + web], device)
        pos += web
        with torch.autocast(device, dtype=torch.bfloat16, enabled=device == "cuda"):
            lc, lv, ls = losses(model, ids, tags, mask)
            if lines:
                pc, pv, ps = losses(model, *lines.batch(rng.choice(lines.train, a.batch - web), device), mask)
                lc, lv, ls = (lc + pc) / 2, (lv + pv) / 2, (ls + ps) / 2
        loss = lc + lv + ls
        opt.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        opt.step()
        sched.step()
        if step % 100 == 0:
            print(f"step {step}/{total} loss {loss.item():.4f} (c {lc.item():.3f} v {lv.item():.3f} s {ls.item():.3f}) "
                  f"{step * a.batch / (time.time() - t0):.0f} sent/s", flush=True)
        if step % every == 0 or step == total:
            words, whole = score(model, rows, v, mask, device)
            held_words, held_whole = score(model, test_rows, v, mask, device)
            agree = agreement(model, data, mask, device)
            row = {"step": step, "epoch": round(step / per_epoch, 2), "words": words, "lines": whole,
                   "held_words": held_words, "held_lines": held_whole,
                   "teacher_cons": agree[0], "teacher_vowel": agree[1], "teacher_stress": agree[2], "time": time.strftime("%H:%M")}
            log.write(json.dumps(row) + "\n")
            log.flush()
            print(f"eval {row['epoch']} ep: reviewed words {words:.2%} lines {whole:.1%} | held out {held_words:.2%} / "
                  f"{held_whole:.1%} | teacher agreement c {agree[0]:.3%} v {agree[1]:.3%} s {agree[2]:.3%}", flush=True)
            if held_words > best:
                best = held_words
                torch.save({"model": model.state_dict(), "args": vars(a), "step": step, "words": held_words, "lines": held_whole},
                           os.path.join(out, "best.pt"))
    peak = f", peak VRAM {torch.cuda.max_memory_allocated() / 2**30:.2f} GB" if device == "cuda" else ""
    print(f"done: best held-out words {best:.2%}{peak}")


if __name__ == "__main__":
    sys.exit(main())
