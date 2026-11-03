"""Blind A/B of two fine-tuned small models on the same held-out inputs, judged by DeepSeek.

    python ab.py --a C:/data/pretrain/puki-sft/sft-all/best.pt --a-tok C:/data/pretrain/puki-tok
                 --b C:/data/pretrain/heb-v2/sft/he-plain-2ep/best.pt --b-tok C:/data/pretrain/heb-v2/tok
                 --lang he --out C:/data/pretrain/heb-v2/ab/old-vs-plain

Both models answer the same inputs (stratified by task, every round's held-out set) with the same sampling seed.
The judge sees each pair twice, once in each order, and a model wins a pair only if it wins both times; anything
else is a tie. Two checks ride along: B against itself with another seed (should come out even) and B against
a line written for another input of the same task (B should win nearly all). Writes answers.jsonl, verdicts.jsonl,
report.txt, and blind.txt (20 pairs for a person to judge, with the key in blind_key.txt).
"""
import argparse
import collections
import json
import random
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import torch

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import fmt  # noqa: E402
import teacher  # noqa: E402
from sft_small import load_model  # noqa: E402

# How many inputs of each task (the hard three get the most)
MIX = {"silly": 45, "silly_fix": 45, "recap": 40, "talk": 25, "care": 25, "look": 20, "moment": 20,
       "story_next": 15, "reach_back": 10, "careful": 5}
CHECKS = 20


def inputs(lang, seed):
    rows = [r for name in sorted((HERE / "data").glob("*_heldout.jsonl")) if not name.name.startswith(("pilot", "train_", "look_pilot"))
            for r in map(json.loads, open(name, encoding="utf-8")) if r["lang"] == lang]
    rng = random.Random(seed)
    picked = []
    for task, n in MIX.items():
        pool = [r for r in rows if r["task"] == task]
        picked += rng.sample(pool, min(n, len(pool)))
    return picked, rows


@torch.no_grad()
def answer(ckpt, tok_dir, rows, seed, temperature=0.7):
    from tokenizers import Tokenizer
    tok = Tokenizer.from_file(str(Path(tok_dir) / "tokenizer.json"))
    eot = tok.token_to_id("<|endoftext|>")
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model = load_model(ckpt, device).eval()
    out = []
    for i, r in enumerate(rows):
        torch.manual_seed(seed * 100003 + i)  # the same draw for each input, whichever model
        ids = torch.tensor([tok.encode(fmt.prompt(r["task"], r["lang"], r["girl"], r["input"], r.get("earlier"))).ids], device=device)
        gen = model.generate(ids, max_new_tokens=48, do_sample=True, temperature=temperature, top_p=0.9, eos_token_id=eot, pad_token_id=eot)
        text = tok.decode(gen[0, ids.shape[1]:].tolist()).replace("<|endoftext|>", "").strip()
        out.append(fmt.parse(text)[0])
    del model
    torch.cuda.empty_cache()
    return out


def situation(r):
    if r["task"] == "talk":
        return f'The child said to Puki: "{r["input"]}"'
    s = f"Situation: {r['input']}"
    if r["task"] in teacher.GAME_BRIEFS:
        s += f"\nThe game: {teacher.GAME_BRIEFS[r['task']]}"
    if r.get("earlier"):
        s += f"\nEarlier: {r['earlier']}"
    return s


def judge_pair(r, first, second):
    """'1', '2' or 'tie': which of two lines is better for this moment."""
    language = "Hebrew" if r["lang"] == "he" else "English"
    system = (f"You compare two short lines a toy parrot could say to a toddler, in {language}.\n" + teacher.RULES +
              teacher.GAME_RULES.get(r["task"], "") + f"\n\nThe child is {teacher._child(r['lang'], r['girl'])}.\n"
              "Pick the line a careful parent would rather hear here: right for the moment and the game, correct facts, "
              f"natural {language} with the right genders, warm, short, following every rule. If they are about equally "
              'good (or equally bad), say tie. Answer as JSON: {"better": "1" or "2" or "tie", "why": "a few words"}')
    user = situation(r) + f"\n\nLine 1: {first}\nLine 2: {second}"
    out = teacher.ask_json(teacher.JUDGE, system, user, temperature=0.0, max_tokens=200)
    v = str(out.get("better", "")).strip() if isinstance(out, dict) else ""
    return v if v in ("1", "2", "tie") else "tie"


def verdict(r, a, b):
    """'a', 'b' or 'tie', from both orders: a model wins only if it wins both."""
    x, y = judge_pair(r, a, b), judge_pair(r, b, a)
    if x == "1" and y == "2":
        return "a"
    if x == "2" and y == "1":
        return "b"
    return "tie"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--a", required=True)
    ap.add_argument("--a-tok", required=True)
    ap.add_argument("--b", required=True)
    ap.add_argument("--b-tok", required=True)
    ap.add_argument("--lang", default="he")
    ap.add_argument("--out", required=True)
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--a-temp", type=float, default=0.7)
    ap.add_argument("--b-temp", type=float, default=0.7)
    a = ap.parse_args()
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    rows, every = inputs(a.lang, a.seed)
    cached = out / "answers.jsonl"
    if cached.exists():
        ans = [json.loads(l) for l in open(cached, encoding="utf-8")]
    else:
        la = answer(a.a, a.a_tok, rows, a.seed, a.a_temp)
        lb = answer(a.b, a.b_tok, rows, a.seed, a.b_temp)
        lb2 = answer(a.b, a.b_tok, rows[:CHECKS], a.seed + 1, a.b_temp)
        rng = random.Random(a.seed + 7)
        ans = []
        for i, r in enumerate(rows):
            e = {"task": r["task"], "lang": r["lang"], "girl": r["girl"], "input": r["input"], "earlier": r.get("earlier"),
                 "data": r["line"], "a": la[i], "b": lb[i]}
            if i < CHECKS:
                e["b_again"] = lb2[i]
            others = [o for o in every if o["task"] == r["task"] and o["input"] != r["input"]]
            e["wrong"] = rng.choice(others)["line"]
            ans.append(e)
        with open(cached, "w", encoding="utf-8") as f:
            for e in ans:
                f.write(json.dumps(e, ensure_ascii=False) + "\n")
    print(f"{len(ans)} inputs answered; judging", flush=True)

    jobs = [("ab", i, e["a"], e["b"]) for i, e in enumerate(ans)]
    jobs += [("self", i, e["b"], e["b_again"]) for i, e in enumerate(ans) if "b_again" in e]
    jobs += [("wrong", i, ans[i]["b"], ans[i]["wrong"]) for i in range(0, len(ans), len(ans) // CHECKS)][:CHECKS]
    with ThreadPoolExecutor(16) as pool:
        got = list(pool.map(lambda j: verdict(ans[j[1]], j[2], j[3]), jobs))
    with open(out / "verdicts.jsonl", "w", encoding="utf-8") as f:
        for (kind, i, _, _), v in zip(jobs, got):
            f.write(json.dumps({"kind": kind, "i": i, "task": ans[i]["task"], "verdict": v}) + "\n")

    lines = []
    for kind, label in (("ab", "A vs B"), ("self", "check: B vs B, another seed (should be even)"),
                        ("wrong", "check: B vs a line for another input (B should win)")):
        vs = [(ans[i]["task"], v) for (k, i, _, _), v in zip(jobs, got) if k == kind]
        c = collections.Counter(v for _, v in vs)
        lines.append(f"{label}: A {c['a']}, B {c['b']}, tie {c['tie']} of {len(vs)}")
        if kind == "ab":
            for task in MIX:
                t = collections.Counter(v for tk, v in vs if tk == task)
                if t:
                    lines.append(f"  {task:11} A {t['a']:3}  B {t['b']:3}  tie {t['tie']:3}")
    lines.append(f"(A = {a.a} at {a.a_temp}, B = {a.b} at {a.b_temp}; judge {teacher.JUDGE}; DeepSeek {teacher.usage})")
    (out / "report.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))

    # 20 pairs for a person, in a random order of lines, with the key apart
    rng = random.Random(a.seed + 11)
    pick = rng.sample(range(len(ans)), 20)
    with open(out / "blind.txt", "w", encoding="utf-8") as f, open(out / "blind_key.txt", "w", encoding="utf-8") as k:
        for n, i in enumerate(pick, 1):
            e, swap = ans[i], rng.random() < 0.5
            one, two = (e["b"], e["a"]) if swap else (e["a"], e["b"])
            f.write(f"{n}. [{e['task']}, {'girl' if e['girl'] else 'boy'}] {e['input']}\n   1: {one}\n   2: {two}\n\n")
            judge_v = dict(((kk, ii), v) for (kk, ii, _, _), v in zip(jobs, got))[("ab", i)]
            k.write(f"{n}. 1 = {'B' if swap else 'A'}, 2 = {'A' if swap else 'B'}; judge: {judge_v}\n")


if __name__ == "__main__":
    main()
