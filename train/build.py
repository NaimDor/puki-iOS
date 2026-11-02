"""Builds the dataset for Puki's small model: the teacher writes lines for each task's inputs,
Puki's own filters and the teacher's judge keep the best, and the result is written as
data/train.jsonl and data/heldout.jsonl (moments and phrases the model never trains on), plus
data/review.txt to read. data/ is git-ignored.

Tasks (add one for a new feature, make its data, retrain):
  moment      a game moment from the app's code -> Puki's line
  tap         the child tapped Puki during a scene -> a short cute line about it
  talk        something a toddler said -> Puki's answer
  careful     hurt, danger, wanting a parent... -> safety.py's fixed reply, word for word
  reach_back  a moment, and a similar one from an earlier session -> a line linking them
  silly, silly_fix, care, story_next, recap
              the games built around the model; see games.py

    python build.py --pilot 10              # a few inputs per task, to read before spending more
    python build.py                         # everything
    python build.py --tasks talk,careful    # just these
    python build.py --scale 3 --take 1 --seed 8 --name more1    # a top-up: more inputs, new lines for
                                                                # them all; train.jsonl is left alone
"""
import argparse
import hashlib
import json
import os
import random
import re
import sys
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "brain"))
import fmt  # noqa: E402
import games  # noqa: E402
import moments as moments_mod  # noqa: E402
import teacher  # noqa: E402

import persona  # noqa: E402  (Puki's line filter)
import safety  # noqa: E402
import memory  # noqa: E402  (which earlier moments are "the same thing")

DATA = os.path.join(HERE, "data")
STAND_IN = {"he": "נועה", "en": "Noa"}  # only for running Puki's filter on lines with @name
CAREFUL = [c for c, _, _ in safety.RULES]
KEEP_PER_INPUT = 2
TAKE = 0  # (--take)


def heldout(key, share=0.1):
    """Whether an input is held out (the same answer on every run)."""
    return int(hashlib.sha256(key.encode()).hexdigest()[:8], 16) / 0xFFFFFFFF < share


def puki_filter(line, lang, girl):
    """The line after Puki's own filter (persona.clean, the banned phrases), or None."""
    persona.configure({})
    persona.set_child(STAND_IN[lang], "girl" if girl else "boy")
    text = line.replace("@name", STAND_IN[lang])
    cleaned = persona.clean(text, lang)
    if not cleaned or not safety.reply_ok(cleaned):
        return None
    return cleaned.replace(STAND_IN[lang], "@name")


# ---------- Inputs for each task ----------

def inputs(tasks, pilot, rng, scale=1, seed=7):
    jobs = []
    ms = moments_mod.moments(seed, 12 if scale == 1 else 40)
    for lang in ("he", "en"):
        for girl in (True, False):
            if "moment" in tasks or "tap" in tasks:
                for m in ms:
                    task = "tap" if m["template"].startswith("tap: ") else "moment"
                    if task in tasks:
                        jobs.append({"task": task, "lang": lang, "girl": girl, "input": m[lang],
                                     "group": m["template"]})
            if "reach_back" in tasks:
                # The earlier moment is from the same game (another animal, the same color...), as
                # the brain's memory.related() would find, so the link is a real one
                plain = [m for m in ms if not m["template"].startswith("tap: ")]
                for m in rng.sample(plain, min(60 * scale, len(plain))):
                    words = memory._content_words(m["he"])
                    game = m["he"].split(":")[0] + ":" if ":" in m["he"] else None
                    alike = [o for o in plain if o is not m and
                             (o["template"] == m["template"] or (game and o["he"].startswith(game) and
                                                                 words & memory._content_words(o["he"])))]
                    if alike:
                        o = rng.choice(alike)
                        when = rng.choice([("yesterday", "אתמול"), ("earlier today", "קודם היום"), ("2 days ago", "לפני יומיים")])
                        jobs.append({"task": "reach_back", "lang": lang, "girl": girl, "input": m[lang],
                                     "earlier": f"{when[0] if lang == 'en' else when[1]}: {o[lang]}", "group": m["template"]})
    if "talk" in tasks:
        for lang in ("he", "en"):
            sets = 1 if pilot else (10 if lang == "he" else 4) * scale  # (Hebrew needs more: the writer drifts to English)
            phrases = {p for s in range(sets) for p in teacher.toddler_phrases(lang, "play", 40, s)
                       # (the writer slips into English now and then: only phrases in the asked language)
                       if any("א" <= ch <= "ת" for ch in p) == (lang == "he")}
            for p in sorted(phrases):
                for girl in (True, False):
                    jobs.append({"task": "talk", "lang": lang, "girl": girl, "input": p, "group": p})
    if "careful" in tasks:
        for lang in ("he", "en"):
            for cat in CAREFUL:
                for p in {p for s in range(scale) for p in teacher.toddler_phrases(lang, cat, 8 if pilot else 25, s)}:
                    # Only phrases in the right language that Puki's own word lists put in this
                    # category: the model learns exactly what the brain would answer
                    hebrew = any("א" <= ch <= "ת" for ch in p)
                    caught = safety.guard(p, lang, True)
                    if hebrew != (lang == "he") or not caught or caught[0] != cat:
                        continue
                    for girl in (True, False):
                        jobs.append({"task": "careful", "lang": lang, "girl": girl, "input": p, "group": p,
                                     "category": cat})
    # The games built around the model (games.py); their Hebrew inputs serve a girl and a boy alike
    if "silly" in tasks:
        for x in games.silly_inputs(rng):
            for lang in ("he", "en"):
                for girl in (True, False):
                    jobs.append({"task": "silly", "lang": lang, "girl": girl, "input": x[lang], "group": x["group"],
                                 "truth": x["truth_" + lang]})
    if "care" in tasks:
        for x in games.care_inputs(rng):
            for lang in ("he", "en"):
                for girl in (True, False):
                    jobs.append({"task": "care", "lang": lang, "girl": girl, "input": x[lang], "group": x["group"],
                                 "note": x["who"] + "\n" + teacher.CARE_MOMENTS[x["outcome"]]})
    if "recap" in tasks:
        for x in games.recap_inputs(rng, 150 * scale):
            for lang in ("he", "en"):
                for girl in (True, False):
                    jobs.append({"task": "recap", "lang": lang, "girl": girl, "input": x[lang], "group": x["group"]})
    if "story_next" in tasks:
        # A whole story is one job (written at once, then cut into its steps), for a girl or a boy
        for plan in games.story_plans(rng, 120 * scale):
            for lang in ("he", "en"):
                jobs.append({"task": "story_next", "lang": lang, "girl": rng.random() < 0.5, "plan": plan,
                             "input": games.story_input(plan, lang, [], 0), "group": plan["group"]})
    if pilot:  # a few of each task, in each language
        picked = []
        for task in tasks:
            if task == "silly_fix":  # (made from silly's results)
                continue
            for lang in ("he", "en"):
                pool = [j for j in jobs if j["task"] == task and j["lang"] == lang]
                picked += rng.sample(pool, min(pilot, len(pool)))
        jobs = picked
    return jobs


# ---------- One input: write, filter, judge ----------

def said_truth(line, truth):
    """Whether a silly line gives the real answer away (as a whole word or phrase)."""
    words = re.findall(r"[\w']+", line.lower())
    for alt in [truth] + games.NUMBER_WORDS.get(truth, []):  # (a number may be written out: 4, four, ארבעה)
        t = re.findall(r"[\w']+", alt.lower())
        if any(words[i:i + len(t)] == t or [w.lstrip("והבלמש") for w in words[i:i + len(t)]] == t
               for i in range(len(words))):
            return True
    return False


def run_story(job):
    """A story with choices: the teacher writes it whole, and each step becomes an example."""
    lang, girl, plan = job["lang"], job["girl"], job["plan"]
    steps = games.story_steps(plan, lang)
    hero = games.story_hero(plan, lang)
    stories = teacher.write_stories(lang, girl, hero, steps)
    passed, rejected = [], []
    for st in stories:
        f = [puki_filter(x, lang, girl) for x in st]
        if all(f) and not any("?" in x for x in f):
            passed.append(f)
        else:
            rejected.append(" / ".join(st))
    scores = teacher.judge_stories(lang, girl, hero, steps, passed) if passed else []
    good = sorted(((s.get("overall", 0) + s.get("grammar", 0), passed[s["i"]]) for s in scores
                   if 0 <= s["i"] < len(passed) and min(s.get("grammar", 0), s.get("facts", 0), s.get("rules", 0)) >= 4
                   and s.get("overall", 0) >= 4), key=lambda x: -x[0])
    best = good[0][1] if good else None
    out = []
    for i in range(len(steps)):
        out.append({**job, "input": games.story_input(plan, lang, best[:i] if best else [], i),
                    "kept": [best[i]] if best else [], "rejected": rejected if i == 0 else [],
                    "judged_out": [" / ".join(p) for p in passed if p is not best] if i == 0 else [], "scores": scores})
    return out


def run(job):
    if job["task"] == "story_next":
        return run_story(job)
    lang, girl = job["lang"], job["girl"]
    if job["task"] == "careful":
        reply = safety.replies(lang, girl)[job["category"]]
        return [{**job, "kept": [reply], "rejected": [], "scores": []}]
    lines = teacher.write_lines(job["task"], lang, girl, job["input"], job.get("earlier"), note=job.get("note"), take=TAKE)
    passed, rejected = [], []
    # What only the earlier moment had (the horse, not "hiding", which both have)
    earlier_words = (memory._content_words(job["earlier"].split(":", 1)[-1]) - memory._content_words(job["input"])
                     if job.get("earlier") else set())
    for l in lines:
        f = puki_filter(l, lang, girl)
        if f and "?" in f:  # Puki doesn't ask the child questions they have to answer
            f = None
        if f and job["task"] == "reach_back" and not (memory._content_words(f) & earlier_words):
            f = None  # a reach-back line names the earlier thing ("like the dog before!")
        if f and job["task"] == "silly" and said_truth(f, job["truth"]):
            f = None  # Silly Puki never gives the real answer away
        if f and job["task"] == "recap" and games.events_named(f, job["input"]) < 2:
            f = None  # a recap names two or three things from the day
        if f and job["task"] == "silly_fix" and not said_truth(f, job["truth"]):
            f = None  # ...and puts it right afterwards
        (passed if f else rejected).append(f or l)
    scores = teacher.judge(job["task"], lang, girl, job["input"], passed, job.get("earlier"),
                           note=job.get("note")) if passed else []
    good = []
    for s in scores:
        if 0 <= s["i"] < len(passed) and min(s.get("grammar", 0), s.get("facts", 0), s.get("rules", 0)) >= 4 \
                and s.get("overall", 0) >= 4:
            good.append((s.get("overall", 0) + s.get("grammar", 0), passed[s["i"]]))
    good.sort(key=lambda x: -x[0])
    kept = [l for _, l in good[:KEEP_PER_INPUT]]
    judged_out = [p for p in passed if p not in kept]
    return [{**job, "kept": kept, "rejected": rejected, "judged_out": judged_out, "scores": scores}]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pilot", type=int, default=0, help="only this many inputs per task and language")
    ap.add_argument("--tasks", default="moment,tap,talk,careful,reach_back,silly,silly_fix,care,story_next,recap")
    ap.add_argument("--workers", type=int, default=8)
    ap.add_argument("--scale", type=int, default=1, help="this many times the inputs")
    ap.add_argument("--take", type=int, default=0, help="a new take: the teacher writes new lines for every input")
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--name", default="", help="what to call the output (default train, or pilot)")
    ap.add_argument("--count", action="store_true", help="print the inputs per task and stop")
    args = ap.parse_args()
    global TAKE
    TAKE = args.take
    tasks = args.tasks.split(",")
    rng = random.Random(args.seed)
    jobs = inputs(tasks, args.pilot, rng, args.scale, args.seed)
    print(f"{len(jobs)} inputs ({', '.join(f'{t} {sum(1 for j in jobs if j["task"] == t)}' for t in tasks)})")
    if args.count:
        return
    with ThreadPoolExecutor(args.workers) as ex:
        results = [r for rs in ex.map(run, jobs) for r in rs]
        if "silly_fix" in tasks:
            # The child's answer to each silly line that was kept
            fixes = []
            for r in results:
                if r["task"] == "silly":
                    for line in r["kept"]:
                        outcome, inp = games.silly_fix_input(r["lang"], line, r["truth"], rng)
                        fixes.append({"task": "silly_fix", "lang": r["lang"], "girl": r["girl"], "input": inp,
                                      "group": r["group"], "truth": r["truth"], "outcome": outcome})
            print(f"{len(fixes)} silly_fix inputs")
            results += [r for rs in ex.map(run, fixes) for r in rs]

    os.makedirs(DATA, exist_ok=True)
    name = args.name or ("pilot" if args.pilot else "train")
    n_train = n_held = 0
    with open(os.path.join(DATA, f"{name}.jsonl"), "w", encoding="utf-8") as tr, \
            open(os.path.join(DATA, f"{name}_heldout.jsonl"), "w", encoding="utf-8") as ho:
        for r in results:
            out = ho if heldout(r["group"]) else tr
            for line in r["kept"]:
                rec = {"task": r["task"], "lang": r["lang"], "girl": r["girl"], "input": r["input"],
                       "earlier": r.get("earlier"), "line": line,
                       "text": fmt.example(r["task"], r["lang"], r["girl"], r["input"], line, r.get("earlier"))}
                out.write(json.dumps(rec, ensure_ascii=False) + "\n")
                if out is ho:
                    n_held += 1
                else:
                    n_train += 1
    with open(os.path.join(DATA, f"{name}_review.txt"), "w", encoding="utf-8") as f:
        for r in results:
            f.write(f"[{r['task']} {r['lang']} {'girl' if r['girl'] else 'boy'}] {r['input']}"
                    + (f"  (earlier: {r['earlier']})" if r.get("earlier") else "") + "\n")
            for l in r["kept"]:
                f.write(f"   KEEP  {l}\n")
            for l in r.get("judged_out", []):
                f.write(f"   judge {l}\n")
            for l in r["rejected"]:
                f.write(f"   filter {l}\n")
    kept_inputs = sum(1 for r in results if r["kept"])
    u = teacher.usage
    print(f"kept lines for {kept_inputs}/{len(results)} inputs: {n_train} train, {n_held} held out")
    print(f"teacher: {u['calls']} calls ({u['cached']} from the cache), {u['in']} tokens in "
          f"({u['in_cached']} cached), {u['out']} out")


if __name__ == "__main__":
    main()
