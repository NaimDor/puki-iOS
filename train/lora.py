"""Teaches the base model Puki's job with a LoRA, saving a checkpoint after every epoch.

Two kinds of run: --schedule constant (the default) compares 1, 2, 3... epochs in one run (the
learning rate stays flat after a short warm-up); --schedule cosine is the final run at the
winning number of epochs, the learning rate cooling to zero by the end, so the model isn't a
warm mid-run checkpoint.

    <torch venv python> lora.py --model C:/models/dicta/DictaLM-3.0-1.7B-Base --epochs 4

Only the line after "puki:" counts toward the loss (the header is given, not learned).
Writes out/<run>/epoch-<n>/ (the adapter) and out/<run>/log.json (train and held-out loss).
"""
import argparse
import json
import math
import os
import random
import time

import torch
from peft import LoraConfig, get_peft_model
from transformers import AutoModelForCausalLM, AutoTokenizer

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")


def load(path):
    with open(path, encoding="utf-8") as f:
        return [json.loads(l) for l in f]


def encode(tok, text, max_len):
    """input ids, and labels that are -100 (ignored) for the header up to and including "puki:"."""
    head, line = text.split("\npuki:", 1)
    head_ids = tok(head + "\npuki:", add_special_tokens=False)["input_ids"]
    line_ids = tok(line, add_special_tokens=False)["input_ids"]
    ids = (head_ids + line_ids)[:max_len]
    labels = ([-100] * len(head_ids) + line_ids)[:max_len]
    return ids, labels


def batches(items, size, pad_id, shuffle, rng):
    order = list(range(len(items)))
    if shuffle:
        rng.shuffle(order)
    for i in range(0, len(order), size):
        chunk = [items[j] for j in order[i:i + size]]
        n = max(len(ids) for ids, _ in chunk)
        ids = torch.full((len(chunk), n), pad_id)
        labels = torch.full((len(chunk), n), -100)
        mask = torch.zeros((len(chunk), n), dtype=torch.long)
        for k, (a, b) in enumerate(chunk):
            ids[k, :len(a)] = torch.tensor(a)
            labels[k, :len(b)] = torch.tensor(b)
            mask[k, :len(a)] = 1
        yield ids, labels, mask


@torch.no_grad()
def held_out_loss(model, items, pad_id, device):
    model.eval()
    total, count = 0.0, 0
    for ids, labels, mask in batches(items, 16, pad_id, False, None):
        out = model(input_ids=ids.to(device), attention_mask=mask.to(device), labels=labels.to(device))
        n = int((labels[:, 1:] != -100).sum())
        total += out.loss.item() * n
        count += n
    model.train()
    return total / max(count, 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--epochs", type=int, default=4)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--rank", type=int, default=32)
    ap.add_argument("--batch", type=int, default=8)
    ap.add_argument("--accum", type=int, default=2, help="batches to a step: batch x accum examples, in a batch's memory")
    ap.add_argument("--vram", type=float, default=0.9, help="share of the GPU's memory it may use: past that it stops, not spills")
    ap.add_argument("--max-len", type=int, default=160)
    ap.add_argument("--run", default="dicta-1.7b")
    ap.add_argument("--data", default="train", help="data/<this>.jsonl and data/<this>_heldout.jsonl")
    ap.add_argument("--schedule", choices=["constant", "cosine"], default="constant",
                    help="constant: compare epochs within one run; cosine: the final run, cooled down to the end")
    args = ap.parse_args()
    torch.manual_seed(7)
    rng = random.Random(7)
    device = "cuda"

    if device == "cuda":
        torch.cuda.set_per_process_memory_fraction(args.vram)
    tok = AutoTokenizer.from_pretrained(args.model)
    pad_id = tok.pad_token_id if tok.pad_token_id is not None else tok.eos_token_id
    end = "<|endoftext|>"
    assert tok.convert_tokens_to_ids(end) is not None, "the base model needs <|endoftext|> to stop on"
    train = [encode(tok, r["text"], args.max_len) for r in load(os.path.join(DATA, args.data + ".jsonl"))]
    held = [encode(tok, r["text"], args.max_len) for r in load(os.path.join(DATA, args.data + "_heldout.jsonl"))]

    model = AutoModelForCausalLM.from_pretrained(args.model, dtype=torch.bfloat16).to(device)
    model.gradient_checkpointing_enable()
    model.enable_input_require_grads()
    model = get_peft_model(model, LoraConfig(
        r=args.rank, lora_alpha=args.rank, lora_dropout=0.05, task_type="CAUSAL_LM",
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"]))
    model.print_trainable_parameters()

    steps_per_epoch = math.ceil(len(train) / (args.batch * args.accum))
    warmup = max(10, steps_per_epoch // 10)
    opt = torch.optim.AdamW([p for p in model.parameters() if p.requires_grad], lr=args.lr, weight_decay=0.0)
    total = steps_per_epoch * args.epochs

    def lr_at(step):
        if step < warmup:
            return (step + 1) / warmup
        if args.schedule == "cosine":  # down to zero by the last step
            return 0.5 * (1 + math.cos(math.pi * (step - warmup) / max(1, total - warmup)))
        return 1.0
    sched = torch.optim.lr_scheduler.LambdaLR(opt, lr_at)
    out_dir = os.path.join(HERE, "out", args.run)
    os.makedirs(out_dir, exist_ok=True)
    log = {"args": vars(args), "train_examples": len(train), "held_examples": len(held),
           "held_loss": [{"epoch": 0, "loss": held_out_loss(model, held, pad_id, device)}], "train_loss": []}
    print(f"{len(train)} train, {len(held)} held out; held-out loss before: {log['held_loss'][0]['loss']:.3f}", flush=True)
    step, t0 = 0, time.time()
    model.train()
    for epoch in range(1, args.epochs + 1):
        running, n = 0.0, 0
        for i, (ids, labels, mask) in enumerate(batches(train, args.batch, pad_id, True, rng)):
            out = model(input_ids=ids.to(device), attention_mask=mask.to(device), labels=labels.to(device))
            (out.loss / args.accum).backward()
            running += out.loss.item()
            n += 1
            if (i + 1) % args.accum == 0:
                torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
                opt.step()
                sched.step()
                opt.zero_grad(set_to_none=True)
                step += 1
        held_loss = held_out_loss(model, held, pad_id, device)
        log["train_loss"].append({"epoch": epoch, "loss": running / n})
        log["held_loss"].append({"epoch": epoch, "loss": held_loss})
        model.save_pretrained(os.path.join(out_dir, f"epoch-{epoch}"))
        peak = torch.cuda.max_memory_reserved() / 2**30 if device == "cuda" else 0
        print(f"epoch {epoch}: train {running / n:.3f}, held out {held_loss:.3f}  ({time.time() - t0:.0f} s, peak {peak:.1f} GB)", flush=True)
        with open(os.path.join(out_dir, "log.json"), "w", encoding="utf-8") as f:
            json.dump(log, f, indent=1)


if __name__ == "__main__":
    main()
