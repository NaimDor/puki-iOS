"""The teacher reads each labelled line in context and corrects the words said wrong: the verbs
that change with who's spoken to, the words whose meaning decides how they're said, and sounds.
It's reliable on the -t / -ta ending of a verb said to a girl or a boy, so only those go in by
themselves. Anything else it says is a suggestion, for data/say_flags.tsv and a person's ear.

    python say_review.py --pilot 80    # a sample, printed to read
    python say_review.py               # every line: data/say_fixed.jsonl and data/say_flags.tsv
"""
import argparse
import json
import os
import random
from concurrent.futures import ThreadPoolExecutor

import teacher

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")

SYSTEM = """You check Hebrew pronunciation for a toy parrot speaking to a toddler. You get a line in
plain Hebrew and its phonemes, word for word, and who the child is. Find the words read wrongly.

The phonemes are IPA: stress mark ˈ before the stressed vowel, ʃ for ש, χ for ח and כ, ʁ for ר, ʔ for
א and ע at the start of a syllable, ts for צ, j for י, v for ו and ב. Keep to these symbols and this style.

Look for:
- Second-person verbs and pronouns: speaking to a girl, past tense ends -t (מצאת matsˈat, האכלת heʔeχˈalt),
  to a boy -ta (matsˈata). לך is lˈaχ to a girl, lˈeχa to a boy; אותך ʔotˈaχ / ʔotχˈa; איתך ʔitˈaχ / ʔitχˈa.
- Words spelled the same but read differently here: הצלחת is hatsalˈaχat "the plate" or hitslˈaχt "you
  succeeded"; ספרו is safˈeʁu "their book", sifʁˈu "they counted"; choose by the sentence.
- Wrong vowels, stress or letters on an ordinary word.
- Sounds, interjections and loanwords read as if they were Hebrew words.
- Number and noun agreement read wrongly.

Do not change a word that is fine, and don't change style or spelling. "@name" stays as it is.
Answer with JSON only: {"fixes": [{"word": "<the Hebrew word as written>", "index": <0-based place in
the line>, "ipa": "<corrected phonemes of that word only, without punctuation>", "why": "<a few words>"}]}.
An empty list when every word is right."""


def gender_fix(was, ipa, girl):
    """True if [ipa] only moves [was] to the verb ending for this child."""
    return ipa == was[:-1] if girl and was.endswith("ta") else (not girl and was.endswith("t") and ipa == was + "a")


def review(item):
    who = "a girl" if item["girl"] else "a boy"
    user = f"Child: {who}\nLine: {item['line']}\nPhonemes: {item['say']}"
    try:
        answer = teacher.ask_json(teacher.JUDGE, SYSTEM, user, temperature=0.2)
    except Exception as e:
        return item, [], f"failed: {type(e).__name__}"
    if not isinstance(answer, dict):  # (no answer after the retries: the line stays as it was, marked unreviewed)
        return item, [], "failed: no answer"
    words, parts = item["line"].split(), item["say"].split()
    kept = []
    for fix in answer.get("fixes", []):
        i = fix.get("index")
        if not isinstance(i, int) or not 0 <= i < len(words) or fix.get("word", "") not in words[i]:
            continue  # (a fix for a word that isn't there)
        ipa = str(fix.get("ipa", "")).strip(' .,!?"')
        if not ipa or any("א" <= c <= "ת" for c in ipa):
            continue
        was = parts[i].strip(' .,!?"')
        if ipa == was:
            continue  # (nothing changed: it was only saying so)
        kept.append({"index": i, "word": words[i], "was": was, "ipa": ipa, "why": str(fix.get("why", ""))[:80],
                     "sure": gender_fix(was, ipa, item["girl"])})
    return item, kept, None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pilot", type=int, default=0)
    ap.add_argument("--workers", type=int, default=8)
    ap.add_argument("--input", default=os.path.join(DATA, "say.jsonl"))
    ap.add_argument("--output", default=os.path.join(DATA, "say_fixed.jsonl"))
    ap.add_argument("--flags", default=os.path.join(DATA, "say_flags.tsv"))
    args = ap.parse_args()
    items = [json.loads(l) for l in open(args.input, encoding="utf-8")]
    items = [i for i in items if i["say"]]
    if args.pilot:
        random.seed(7)
        items = random.sample(items, args.pilot)
    with ThreadPoolExecutor(args.workers) as ex:
        results = list(ex.map(review, items))
    failed = sum(1 for _, _, e in results if e)
    flagged = sum(1 for _, k, _ in results if k)
    if args.pilot:
        for item, kept, _ in results:
            for k in kept:
                print(f"{'girl' if item['girl'] else 'boy '} {k['word']} {k['was']} -> {k['ipa']} ({k['why']}) | {item['line'][:50]}")
        print(f"{len(results)} lines, {flagged} with fixes, {failed} failed")
        return
    applied = 0
    with open(args.output, "w", encoding="utf-8") as out, open(args.flags, "w", encoding="utf-8") as tsv:
        tsv.write("girl\tapplied\tword\twas\tfixed\twhy\tline\n")
        for item, kept, err in results:
            parts = item["say"].split()
            for k in kept:
                if k["sure"]:
                    parts[k["index"]] = parts[k["index"]].replace(k["was"], k["ipa"])
                    applied += 1
                tsv.write(f"{item['girl']}\t{k['sure']}\t{k['word']}\t{k['was']}\t{k['ipa']}\t{k['why']}\t{item['line']}\n")
            out.write(json.dumps({**item, "say": " ".join(parts), "reviewed": err is None}, ensure_ascii=False) + "\n")
    print(f"{len(results)} lines, {flagged} flagged, {applied} gender fixes applied, {failed} failed")


if __name__ == "__main__":
    main()
