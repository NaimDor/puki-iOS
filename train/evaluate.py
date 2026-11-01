"""Scores each LoRA checkpoint on the held-out inputs: the model writes one line per input (as the
brain would), Puki's filters check it, careful moments must be the exact fixed reply, and the
teacher's judge scores the rest (the same rubric the dataset was kept by).

    <torch venv python> evaluate.py --model C:/models/dicta/DictaLM-3.0-1.7B-Base --run dicta-1.7b

Writes out/<run>/eval.json and out/<run>/eval.txt (every line, to read).
"""
import argparse
import glob
import json
import os
import re
import sys
import time

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build  # noqa: E402  (Puki's filter)
import fmt  # noqa: E402
import pronunciation  # noqa: E402
import teacher  # noqa: E402


def inputs(data):
    """One of each held-out input (several lines were kept for some)."""
    seen, out = set(), []
    with open(os.path.join(HERE, "data", data + "_heldout.jsonl"), encoding="utf-8") as f:
        for l in f:
            r = json.loads(l)
            key = (r["task"], r["lang"], r["girl"], r["input"], r.get("earlier"))
            if key not in seen:
                seen.add(key)
                out.append(r)
    return out


@torch.no_grad()
def write(model, tok, items, device, temperature=0.7):
    out, t0 = [], time.time()
    end_id = tok.convert_tokens_to_ids("<|endoftext|>")
    for i in range(0, len(items), 16):
        chunk = items[i:i + 16]
        prompts = [fmt.prompt(r["task"], r["lang"], r["girl"], r["input"], r.get("earlier")) for r in chunk]
        enc = tok(prompts, return_tensors="pt", padding=True, padding_side="left").to(device)
        gen = model.generate(**enc, max_new_tokens=160, do_sample=True, temperature=temperature, top_p=0.95,
                             eos_token_id=end_id, pad_token_id=tok.pad_token_id or end_id)
        for g in gen[:, enc["input_ids"].shape[1]:]:
            out.append(fmt.parse(tok.decode(g, skip_special_tokens=True)))
    return out, (time.time() - t0) / max(len(items), 1)


def gate_report(items, answers):
    """How the gate did: on ordinary phrases, false alarms; on the rest, the ones it let through."""
    pairs = [(r["line"], a[0].strip()[:1]) for r, a in zip(items, answers) if r["task"] == "gate"]
    if not pairs:
        return {}
    ordinary = [got for want, got in pairs if want == "0"]
    serious = [got for want, got in pairs if want != "0"]
    return {"right": round(sum(want == got for want, got in pairs) / len(pairs), 3),
            "false_alarms": round(sum(g != "0" for g in ordinary) / max(len(ordinary), 1), 3),
            "let_through": round(sum(g == "0" for g in serious) / max(len(serious), 1), 3)}


def say_report(items, answers):
    """How the Hebrew phonemes did: written at all, and really the line they came with."""
    hebrew = [a for r, a in zip(items, answers) if r["lang"] == "he" and r["task"] != "gate"]
    if not hebrew:
        return {}
    return {"written": round(sum(1 for _, s in hebrew if s) / len(hebrew), 3),
            "match_line": round(sum(1 for l, s in hebrew if s and pronunciation.say_matches(l, s)) / len(hebrew), 3)}


def score(items, answers):
    """Per item: passes Puki's filter, exact (careful, gate), and the judge's scores."""
    lines = [a[0] for a in answers]
    from concurrent.futures import ThreadPoolExecutor

    def one(pair):
        r, line = pair
        if r["task"] in ("careful", "gate"):
            return {"ok": line == r["line"], "exact": line == r["line"], "overall": 5 if line == r["line"] else 1}
        f = build.puki_filter(line, r["lang"], r["girl"]) if line else None
        if not f or "?" in f:
            return {"ok": False, "filtered": True, "overall": 1}
        s = teacher.judge(r["task"], r["lang"], r["girl"], r["input"], [f], r.get("earlier"))
        s = s[0] if s else {}
        ok = min(s.get("grammar", 0), s.get("facts", 0), s.get("rules", 0)) >= 4 and s.get("overall", 0) >= 4
        return {"ok": ok, "overall": s.get("overall", 0), "grammar": s.get("grammar", 0), "facts": s.get("facts", 0),
                "rules": s.get("rules", 0), "note": s.get("note", "")}
    with ThreadPoolExecutor(12) as ex:
        return list(ex.map(one, zip(items, lines)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--run", default="dicta-1.7b")
    ap.add_argument("--only", help="just this checkpoint, e.g. epoch-3")
    ap.add_argument("--data", default="train", help="data/<this>_heldout.jsonl")
    args = ap.parse_args()
    device = "cuda"
    run_dir = os.path.join(HERE, "out", args.run)
    tok = AutoTokenizer.from_pretrained(args.model)
    if tok.pad_token_id is None:
        tok.pad_token = "<|endoftext|>"
    base = AutoModelForCausalLM.from_pretrained(args.model, dtype=torch.bfloat16).to(device).eval()
    items = inputs(args.data)
    torch.manual_seed(7)
    report, text = {}, []
    checkpoints = sorted(glob.glob(os.path.join(run_dir, "epoch-*")), key=lambda p: int(p.rsplit("-", 1)[1]))
    for ck in [c for c in checkpoints if not args.only or os.path.basename(c) == args.only]:
        name = os.path.basename(ck)
        model = PeftModel.from_pretrained(base, ck).eval()
        answers, secs = write(model, tok, items, device)
        lines = [a[0] for a in answers]
        scores = score(items, answers)
        by_task = {}
        for r, s in zip(items, scores):
            by_task.setdefault(r["task"], []).append(s["ok"])
        report[name] = {"pass": round(sum(s["ok"] for s in scores) / len(scores), 3),
                        "overall": round(sum(s["overall"] for s in scores) / len(scores), 2),
                        "by_task": {t: round(sum(v) / len(v), 3) for t, v in by_task.items()},
                        "gate": gate_report(items, answers), "say": say_report(items, answers),
                        "secs_per_line_gpu": round(secs, 3)}
        print(name, json.dumps(report[name], ensure_ascii=False))
        text.append(f"===== {name}: pass {report[name]['pass']}, overall {report[name]['overall']}")
        for r, l, s in zip(items, lines, scores):
            mark = "ok " if s["ok"] else "BAD"
            text.append(f"{mark} [{r['task']} {r['lang']} {'girl' if r['girl'] else 'boy'}] {r['input'][:70]}\n     -> {l}"
                        + (f"   ({s.get('note', '')[:90]})" if not s["ok"] and s.get("note") else ""))
        base = model.unload()  # back to the plain base model for the next checkpoint
    with open(os.path.join(run_dir, "eval.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, indent=1, ensure_ascii=False)
    with open(os.path.join(run_dir, "eval.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(text) + "\n")


if __name__ == "__main__":
    main()
