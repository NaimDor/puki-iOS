"""Pretraining for Puki's small language model (Brains): a Llama-style decoder (tied embeddings, GQA, SwiGLU, RoPE)
trained from scratch on the corpus pt_corpus.py builds. The recipe comes from a TOML file in recipes/; any flag on
the command line overrides it.

    <python> pretrain.py --recipe recipes/brains-he.toml --run he-stable --until 2.56e9 --allowed <tok>/stable.npy
    <python> pretrain.py --recipe recipes/brains-he.toml --run he-mid --branch-from he-stable --until 3.2e9 --allowed <tok>/mid.npy

A step packs random chunks of the allowed set into rows of --seq tokens. The learning rate follows the global token
count: a cosine to --total, or WSD (a linear warm-up, the peak held, then a 1-sqrt cooldown from --decay-start).
--branch-from starts a cooldown from another run's checkpoint, so one stable phase can end in several cooldowns
(plain, or with Puki's own lines mixed in: pt_corpus.py's mid.npy). Every --eval-every steps: the answer-token loss
on Puki's two reference sets and the loss on held-out corpus chunks, to <runs>/<run>/log.jsonl. The checkpoint
(<run>/ckpt.pt) keeps the optimizer state.

Written for Puki from the pretraining code of the author's research harness, with only what Puki's runs use; the
same arithmetic, so its runs reproduce.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import shutil
import time
import tomllib
from pathlib import Path

import numpy as np
import torch
from transformers import LlamaConfig, LlamaForCausalLM

HERE = Path(__file__).resolve().parent
RUNS = Path(os.environ.get("PUKI_RUNS", HERE / "out" / "pretrain"))

# The 46M shape Brains uses (14 layers, width 512, 8 heads with 2 key/value heads, head size 64)
SIZES = {
    "50m": dict(hidden_size=512, intermediate_size=1408, num_hidden_layers=14, num_attention_heads=8, num_key_value_heads=2),
}


def config(vocab: int, size: str = "50m", seq: int = 1024) -> LlamaConfig:
    c = LlamaConfig(vocab_size=vocab, max_position_embeddings=seq, rope_theta=10000.0, rms_norm_eps=1e-5,
                    tie_word_embeddings=True, bos_token_id=0, eos_token_id=0, pad_token_id=0, **SIZES[size])
    c._attn_implementation = "sdpa"  # a model built from a config may default to eager (a full attention matrix)
    return c


class Corpus:
    """Token files and the chunk index (pt_corpus.py's output)."""

    def __init__(self, tok_dir: Path):
        meta = json.loads((tok_dir / "sources.json").read_text(encoding="utf-8"))
        self.bins = {m["id"]: np.memmap(tok_dir / f"{name}.bin", dtype=np.uint16, mode="r")
                     for name, m in meta.items() if m["tokens"]}  # an empty source has no chunks either
        self.chunks = np.load(tok_dir / "chunks.npy")

    def tokens(self, k: int) -> np.ndarray:
        sid, start, n = self.chunks[k]
        return self.bins[int(sid)][start:start + n]

    def batch(self, ids: np.ndarray, seq: int) -> torch.Tensor:
        """Pack the chunk ids in order into rows of seq tokens (a row's last chunk is cut): packing v1."""
        rows, cur = [], []
        for k in ids:
            cur.append(self.tokens(int(k)))
            if sum(len(c) for c in cur) >= seq:
                rows.append(np.concatenate(cur)[:seq])
                cur = []
        return torch.from_numpy(np.stack(rows).astype(np.int64))

    def pack(self, order: np.ndarray, pos: int, rows: int, seq: int) -> tuple[torch.Tensor, int]:
        """Rows of exactly seq tokens from order[pos:], nothing drawn wasted (a chunk that overflows a row starts the
        next one): packing v2. Returns (rows, new pos)."""
        stream, need = [], rows * seq
        while sum(len(s) for s in stream) < need:
            if pos >= len(order):
                pos = 0
            stream.append(self.tokens(int(order[pos])))
            pos += 1
        flat = np.concatenate(stream)[:need]
        return torch.from_numpy(flat.reshape(rows, seq).astype(np.int64)), pos


def lm_loss(model, x: torch.Tensor, backbone=None, chunk: int = 256) -> torch.Tensor:
    """Per-token next-token loss (B, T-1), never holding the full fp32 logits: the backbone runs once, then the output
    layer and the cross-entropy go over slices of `chunk` positions, each checkpointed when gradients are on (its
    logits recomputed in the backward pass). The same numbers as the full computation, up to float rounding."""
    inner = backbone if backbone is not None else model.model
    h = inner(input_ids=x).last_hidden_state[:, :-1]
    tgt = x[:, 1:]

    def piece(hs, ts):
        return torch.nn.functional.cross_entropy(model.lm_head(hs).float().flatten(0, 1), ts.flatten(),
                                                 reduction="none").view(ts.shape)
    out = []
    for s in range(0, tgt.shape[1], chunk):
        hs, ts = h[:, s:s + chunk], tgt[:, s:s + chunk]
        if torch.is_grad_enabled():
            out.append(torch.utils.checkpoint.checkpoint(piece, hs, ts, use_reentrant=False))
        else:
            out.append(piece(hs, ts))
    return torch.cat(out, 1)


@torch.no_grad()
def ref_loss(model, refs: list[dict], device, batch: int = 16) -> float:
    """Mean answer-token loss (nats) over one of Puki's reference sets (pt_corpus.py: prompt, then the answer)."""
    model.eval()
    tot = n = 0.0
    for s in range(0, len(refs), batch):
        part = refs[s:s + batch]
        L = max(len(r["ids"]) for r in part)
        x = torch.zeros(len(part), L, dtype=torch.long)
        mask = torch.zeros(len(part), L, dtype=torch.bool)
        for i, r in enumerate(part):
            x[i, :len(r["ids"])] = torch.tensor(r["ids"])
            mask[i, r["start"]:len(r["ids"])] = True
        x, mask = x.to(device), mask.to(device)
        with torch.autocast("cuda", dtype=torch.bfloat16):
            lp = lm_loss(model, x)
        m = mask[:, 1:]
        tot += float((lp * m).sum())
        n += float(m.sum())
    model.train()
    return tot / max(1, n)


@torch.no_grad()
def corpus_loss(model, corpus: Corpus, ids: np.ndarray, seq: int, device) -> float:
    model.eval()
    x = corpus.batch(ids, seq).to(device)
    out = []
    for s in range(0, len(x), 16):
        with torch.autocast("cuda", dtype=torch.bfloat16):
            out.append(float(lm_loss(model, x[s:s + 16]).mean()))
    model.train()
    return sum(out) / len(out)


class Optimizers:
    """Several optimizers as one (the training loop's param_groups, step, zero_grad, state dicts)."""

    def __init__(self, *opts):
        self.opts = opts

    @property
    def param_groups(self):
        return [g for o in self.opts for g in o.param_groups]

    def step(self):
        for o in self.opts:
            o.step()

    def zero_grad(self, set_to_none: bool = True):
        for o in self.opts:
            o.zero_grad(set_to_none=set_to_none)

    def state_dict(self):
        return {"opts": [o.state_dict() for o in self.opts]}

    def load_state_dict(self, sd):
        for o, s in zip(self.opts, sd["opts"]):
            o.load_state_dict(s)


def decay_groups(params: list, wd: float, decay_norms: bool) -> list[dict]:
    """AdamW groups: the 1-D parameters (RMSNorm gains; there are no biases) without weight decay unless
    decay_norms, every matrix with wd."""
    if decay_norms:
        return [{"params": params, "weight_decay": wd}]
    return [{"params": [p for p in params if p.ndim >= 2], "weight_decay": wd},
            {"params": [p for p in params if p.ndim < 2], "weight_decay": 0.0}]


def make_optimizer(model, kind: str, lr: float, beta2: float, wd: float, decay_norms: bool, adam_lr: float,
                   adam_wd: float, eps: float):
    """adamw: AdamW on everything. muon: Muon on the transformer blocks' 2-D weights, its update scaled to AdamW's RMS
    (so the same peak rate and schedule apply), and AdamW for the embeddings and the norms at adam_lr (0: lr) and
    adam_wd (<0: wd); each group's "lr_scale" is its rate's share of the schedule's."""
    if kind == "adamw":
        return torch.optim.AdamW(decay_groups(list(model.parameters()), wd, decay_norms), lr=lr, betas=(0.9, beta2),
                                 eps=eps, fused=True)
    blocks = [p for n, p in model.named_parameters() if p.ndim == 2 and ".layers." in n]
    ids = {id(p) for p in blocks}
    rest = [p for p in model.parameters() if id(p) not in ids]
    print(f"muon: {sum(p.numel() for p in blocks) / 1e6:.1f}M params in {len(blocks)} matrices; "
          f"adamw: {sum(p.numel() for p in rest) / 1e6:.1f}M", flush=True)
    alr = adam_lr or lr
    groups = decay_groups(rest, wd if adam_wd < 0 else adam_wd, decay_norms)
    for g in groups:
        g["lr_scale"] = alr / lr
    return Optimizers(torch.optim.Muon(blocks, lr=lr, weight_decay=wd, momentum=0.95, adjust_lr_fn="match_rms_adamw"),
                      torch.optim.AdamW(groups, lr=alr, betas=(0.9, beta2), eps=eps, fused=True))


def lr_at(tokens: float, total: float, peak: float, warm: float) -> float:
    """The cosine: a linear warm-up, then down to a tenth of the peak at total."""
    if tokens < warm:
        return peak * tokens / warm
    p = min(1.0, (tokens - warm) / max(1.0, total - warm))
    return peak * (0.1 + 0.9 * 0.5 * (1 + math.cos(math.pi * p)))


def scheduled_lr(a, tokens: float) -> float:
    """The rate at `tokens`. wsd: the warm-up, the peak held, and from --decay-start a cooldown to --decay-floor x
    peak at --until (1-sqrt, which beats linear: Hagele et al. 2024). cosine: lr_at, with an optional cooldown."""
    start = a.decay_start
    if a.schedule == "wsd":
        if not start or tokens < start:
            return a.lr * min(1.0, tokens / max(1.0, a.warm))
        lr0, end = a.lr, a.lr * a.decay_floor
        frac = min(1.0, (tokens - start) / max(1.0, a.until - start))
        return end + (lr0 - end) * (1 - frac ** 0.5 if a.decay_shape == "1-sqrt" else 1 - frac)
    if not start or tokens < start:
        return lr_at(tokens, a.total, a.lr, a.warm)
    lr0, end = lr_at(start, a.total, a.lr, a.warm), a.lr * a.decay_floor
    frac = min(1.0, (tokens - start) / max(1.0, a.until - start))
    return end + (lr0 - end) * (1 - frac ** 0.5 if a.decay_shape == "1-sqrt" else 1 - frac)


def branch(a) -> None:
    """--branch-from: the parent's checkpoint (and its eval log, for one curve) copied into this run's folder."""
    if not a.branch_from:
        return
    src, dst = RUNS / a.branch_from, RUNS / a.run
    dst.mkdir(parents=True, exist_ok=True)
    if not (dst / "ckpt.pt").exists():
        shutil.copyfile(src / "ckpt.pt", dst / "ckpt.pt")
        if (src / "log.jsonl").exists():
            shutil.copyfile(src / "log.jsonl", dst / "log.jsonl")
    a.resume = True


def parse_args():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--recipe", default="", help="a TOML file of defaults (recipes/); command-line flags override it")
    ap.add_argument("--run", required=True)
    ap.add_argument("--until", type=float, required=True, help="stop at this many training tokens (global)")
    ap.add_argument("--tok", help="pt_corpus.py's output folder")
    ap.add_argument("--vocab", type=int, help="the tokenizer's size")
    ap.add_argument("--total", type=float, default=1.6e9, help="the cosine's length in tokens")
    ap.add_argument("--allowed", default="", help="npy of allowed chunk ids (default: all, minus the eval chunks)")
    ap.add_argument("--resume", action="store_true")
    ap.add_argument("--branch-from", default="", help="start this run from that run's checkpoint (implies --resume)")
    ap.add_argument("--optimizer", default="adamw", choices=["adamw", "muon"])
    ap.add_argument("--lr", type=float, default=2e-3)
    ap.add_argument("--beta2", type=float, default=0.95)
    ap.add_argument("--wd", type=float, default=0.1)
    ap.add_argument("--decay-norms", type=int, default=1, help="0: no weight decay on the RMSNorm gains")
    ap.add_argument("--eps", type=float, default=1e-8)
    ap.add_argument("--adam-lr", type=float, default=0.0, help="muon: the AdamW group's peak rate (0: --lr)")
    ap.add_argument("--adam-wd", type=float, default=-1.0, help="muon: the AdamW group's weight decay (<0: --wd)")
    ap.add_argument("--warm", type=float, default=50e6, help="warm-up tokens")
    ap.add_argument("--schedule", default="cosine", choices=["cosine", "wsd"])
    ap.add_argument("--decay-start", type=float, default=0, help="tokens where the cooldown starts (wsd default: "
                    "the last --cooldown share of --until)")
    ap.add_argument("--cooldown", type=float, default=0.2)
    ap.add_argument("--decay-shape", default="1-sqrt", choices=["1-sqrt", "linear"])
    ap.add_argument("--decay-floor", type=float, default=0.0)
    ap.add_argument("--micro", type=int, default=16)
    ap.add_argument("--accum", type=int, default=8)
    ap.add_argument("--seq", type=int, default=1024)
    ap.add_argument("--size", default="50m", choices=list(SIZES))
    ap.add_argument("--pack", default="v1", choices=["v1", "v2"], help="v1: the first run's packing; v2: exact rows")
    ap.add_argument("--ce", default="full", choices=["full", "chunked"], help="chunked: sliced loss, ~2-3 GB less")
    ap.add_argument("--compile", type=int, default=1)
    ap.add_argument("--eval-every", type=int, default=500)
    ap.add_argument("--save-every", type=int, default=1000)
    ap.add_argument("--seed", type=int, default=0)
    first, _ = ap.parse_known_args()
    if first.recipe:
        recipe = tomllib.loads(Path(first.recipe).read_text(encoding="utf-8"))
        ap.set_defaults(**{k.replace("-", "_"): v for k, v in recipe.items()})
    a = ap.parse_args()
    if not a.tok or not a.vocab:
        ap.error("--tok and --vocab (or a recipe that sets them) are required")
    return a


def main():
    a = parse_args()
    branch(a)
    tok_dir, run_dir = Path(a.tok), RUNS / a.run
    run_dir.mkdir(parents=True, exist_ok=True)
    device = "cuda"
    torch.manual_seed(a.seed)
    corpus = Corpus(tok_dir)
    rng = np.random.default_rng(a.seed)
    eval_ids = np.random.default_rng(12345).choice(len(corpus.chunks), 512, replace=False)  # same for every run
    allowed = np.load(a.allowed) if a.allowed else np.arange(len(corpus.chunks))
    allowed = np.setdiff1d(allowed, eval_ids)
    refs = {k: json.loads((tok_dir / f"reference-{k}.json").read_text(encoding="utf-8")) for k in ("probe", "heldout")}
    model = LlamaForCausalLM(config(a.vocab, a.size, a.seq)).to(device)
    opt = make_optimizer(model, a.optimizer, a.lr, a.beta2, a.wd, bool(a.decay_norms), a.adam_lr, a.adam_wd, a.eps)
    if a.schedule == "wsd" and not a.decay_start:
        a.decay_start = a.until * (1 - a.cooldown)
    tokens, step = 0.0, 0
    if a.resume:
        ck = torch.load(run_dir / "ckpt.pt", map_location=device)
        model.load_state_dict(ck["model"])
        opt.load_state_dict(ck["opt"])
        tokens, step = ck["tokens"], ck["step"]
        rng = np.random.default_rng(a.seed + step)
    # Compiled for training (a 512-wide model is launch-bound); evals and checkpoints use the plain module
    fwd = torch.compile(model) if a.compile and a.ce == "full" else model
    backbone = torch.compile(model.model) if a.compile and a.ce == "chunked" else model.model
    print(f"{a.run}: {sum(p.numel() for p in model.parameters()) / 1e6:.1f}M params, {len(allowed)} allowed chunks, "
          f"from {tokens / 1e9:.3f}B to {a.until / 1e9:.3f}B tokens", flush=True)
    order, pos = rng.permutation(allowed), 0
    per_step = a.micro * a.accum * a.seq
    log = (run_dir / "log.jsonl").open("a", encoding="utf-8")

    def evaluate():
        row = {"step": step, "tokens": tokens, "ref_probe": ref_loss(model, refs["probe"], device),
               "ref_heldout": ref_loss(model, refs["heldout"], device),
               "corpus": corpus_loss(model, corpus, eval_ids, a.seq, device), "time": time.strftime("%H:%M")}
        log.write(json.dumps(row) + "\n")
        log.flush()
        print(f"eval step {step} ({tokens / 1e9:.3f}B): ref probe {row['ref_probe']:.4f} heldout "
              f"{row['ref_heldout']:.4f} corpus {row['corpus']:.4f}", flush=True)

    def save():
        tmp = run_dir / "ckpt.pt.tmp"  # written whole, then renamed: a crash mid-save keeps the last good one
        torch.save({"model": model.state_dict(), "opt": opt.state_dict(), "tokens": tokens, "step": step,
                    "config": model.config.to_dict(), "arch": "llama"}, tmp)
        tmp.replace(run_dir / "ckpt.pt")

    t0, recent = time.time(), []
    while tokens < a.until:
        for g in opt.param_groups:
            g["lr"] = scheduled_lr(a, tokens) * g.get("lr_scale", 1.0)  # muon: the AdamW group at its own share
        for _ in range(a.accum):
            if a.pack == "v2":
                x, pos = corpus.pack(order, pos, a.micro, a.seq)
                x = x.to(device)
            else:
                need = a.micro * 3  # chunks: enough to fill micro rows (most chunks are 512)
                if pos + need > len(order):
                    order, pos = rng.permutation(allowed), 0
                x = corpus.batch(order[pos:pos + need], a.seq)[:a.micro].to(device)
                pos += need
            with torch.autocast("cuda", dtype=torch.bfloat16):
                if a.ce == "chunked":
                    loss = lm_loss(model, x, backbone).mean() / a.accum
                else:
                    loss = fwd(input_ids=x, labels=x).loss / a.accum
            loss.backward()
            recent = (recent + [float(loss) * a.accum])[-50:]
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        opt.step()
        opt.zero_grad(set_to_none=True)
        step += 1
        tokens += per_step
        if step % 20 == 0:
            tps = per_step * 20 / (time.time() - t0)
            t0 = time.time()
            print(f"step {step} {tokens / 1e9:.3f}B loss {sum(recent) / len(recent):.3f} "
                  f"lr {opt.param_groups[0]['lr']:.2e} {tps / 1e3:.0f}k tok/s", flush=True)
        if step % a.eval_every == 0:
            evaluate()
        if a.save_every and step % a.save_every == 0:
            save()
    evaluate()
    save()
    print(f"saved {run_dir / 'ckpt.pt'}")


if __name__ == "__main__":
    main()
