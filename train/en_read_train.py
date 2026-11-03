"""Trains the English reading model: English words to espeak's phonemes (en_label.py), for the words the
lookup table doesn't have (the child's name, what the child says). Beak's shape, a small bidirectional
transformer over the letters, but English letters and sounds don't line up one to one ("th", "ough", a
silent e), so each letter gets three output slots and CTC learns which of them stay empty. One forward
pass on the tablet, then the usual CTC reading (drop repeats and blanks).

    python en_read_train.py --run en-read-1 --epochs 20

Every epoch it scores words it never trained on: how many come out exactly as espeak says them.
Runs go to C:/data/pretrain/en-read-runs.
"""
import argparse
import json
import math
import random
import time
from pathlib import Path

import torch
import torch.nn as nn
import torch.nn.functional as F

LETTERS = "abcdefghijklmnopqrstuvwxyz'"
SLOTS = 3
MAX_LETTERS = 24


class Reader(nn.Module):
    def __init__(self, n_out, dim=128, layers=4, heads=4):
        super().__init__()
        self.embed = nn.Embedding(len(LETTERS) + 2, dim)  # (0 is padding, 1 the word's edge)
        self.pos = nn.Embedding(MAX_LETTERS + 2, dim)
        layer = nn.TransformerEncoderLayer(dim, heads, dim * 4, dropout=0.1, batch_first=True, norm_first=True)
        self.encoder = nn.TransformerEncoder(layer, layers)
        self.norm = nn.LayerNorm(dim)
        self.out = nn.Linear(dim, n_out * SLOTS)
        self.n_out = n_out

    def forward(self, x):
        """[batch, letters] ids -> [batch, letters * SLOTS, n_out] log-probabilities (blank is 0)."""
        h = self.embed(x) + self.pos(torch.arange(x.shape[1], device=x.device))
        h = self.norm(self.encoder(h, src_key_padding_mask=x == 0))
        b, n, _ = h.shape
        return F.log_softmax(self.out(h).view(b, n * SLOTS, self.n_out), -1)


def letters(word):
    return [1] + [LETTERS.index(c) + 2 for c in word if c in LETTERS][:MAX_LETTERS] + [1]


def decode(logp, symbols):
    """Greedy CTC reading of one word's [slots, n_out] log-probabilities."""
    best, out, prev = logp.argmax(-1).tolist(), [], 0
    for k in best:
        if k != prev and k != 0:
            out.append(symbols[k])
        prev = k
    return "".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--data", default=r"C:\data\pretrain\en-v2\en_read.tsv")
    ap.add_argument("--out", default=r"C:\data\pretrain\en-read-runs")
    ap.add_argument("--epochs", type=int, default=20)
    ap.add_argument("--batch", type=int, default=512)
    ap.add_argument("--lr", type=float, default=2e-3)
    ap.add_argument("--dim", type=int, default=128)
    ap.add_argument("--layers", type=int, default=4)
    ap.add_argument("--seed", type=int, default=0)
    a = ap.parse_args()
    torch.manual_seed(a.seed)
    rng = random.Random(a.seed)
    rows = []
    for l in open(a.data, encoding="utf-8"):
        w, ipa, puki = l.rstrip("\n").split("\t")
        w = w.lower()
        if all(c in LETTERS for c in w) and 0 < len(w) <= MAX_LETTERS and len(ipa) <= (len(w) + 2) * SLOTS:
            rows.append((w, ipa, puki == "1"))
    symbols = ["<blank>"] + sorted({c for _, ipa, _ in rows for c in ipa})
    index = {c: i for i, c in enumerate(symbols)}
    rng.shuffle(rows)
    test = [r for r in rows if not r[2]][:3000]  # (never Puki's own words: the table has those)
    held = {r[0] for r in test}
    train = [r for r in rows if r[0] not in held]
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model = Reader(len(symbols), a.dim, a.layers).to(device)
    print(f"{a.run}: {sum(p.numel() for p in model.parameters()) / 1e6:.2f}M params, {len(symbols)} symbols, "
          f"{len(train)} words to learn, {len(test)} to test", flush=True)
    out = Path(a.out) / a.run
    out.mkdir(parents=True, exist_ok=True)
    (out / "symbols.json").write_text(json.dumps({"letters": LETTERS, "slots": SLOTS, "symbols": symbols}, ensure_ascii=False), encoding="utf-8")

    def batch(part):
        xs = [letters(w) for w, _, _ in part]
        x = torch.zeros(len(xs), max(map(len, xs)), dtype=torch.long)
        for i, s in enumerate(xs):
            x[i, :len(s)] = torch.tensor(s)
        ys = [torch.tensor([index[c] for c in ipa]) for _, ipa, _ in part]
        return x.to(device), ys, [len(s) * SLOTS for s in xs]

    @torch.no_grad()
    def score():
        model.eval()
        right, wrong = 0, []
        for s in range(0, len(test), 1000):
            part = test[s:s + 1000]
            x, _, lens = batch(part)
            logp = model(x)
            for i, (w, ipa, _) in enumerate(part):
                got = decode(logp[i, :lens[i]], symbols)
                if got == ipa:
                    right += 1
                elif len(wrong) < 12:
                    wrong.append(f"{w}: {got} (espeak {ipa})")
        model.train()
        return right / len(test), wrong

    steps = a.epochs * math.ceil(len(train) / a.batch)
    opt = torch.optim.AdamW(model.parameters(), lr=a.lr, weight_decay=0.01)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, a.lr, total_steps=steps, pct_start=0.05)
    best, step, t0 = 0.0, 0, time.time()
    for epoch in range(1, a.epochs + 1):
        rng.shuffle(train)
        for s in range(0, len(train), a.batch):
            x, ys, lens = batch(train[s:s + a.batch])
            logp = model(x).transpose(0, 1)  # [slots, batch, n_out] for ctc_loss
            loss = F.ctc_loss(logp, torch.cat(ys).to(device), torch.tensor(lens), torch.tensor([len(y) for y in ys]),
                              blank=0, zero_infinity=True)
            opt.zero_grad(set_to_none=True)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            opt.step()
            sched.step()
            step += 1
        acc, wrong = score()
        print(f"epoch {epoch}: loss {loss.item():.3f} | words exactly as espeak {acc:.1%} | {time.time() - t0:.0f}s", flush=True)
        if acc > best:
            best = acc
            torch.save({"model": model.state_dict(), "dim": a.dim, "layers": a.layers, "symbols": symbols}, out / "best.pt")
            (out / "wrong.txt").write_text("\n".join(wrong) + "\n", encoding="utf-8")
    print(f"done: best {best:.1%}")


if __name__ == "__main__":
    main()
