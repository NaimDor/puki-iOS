"""The reading model's labels: a consonant, a vowel and stress for every Hebrew letter, the shape of
RenikudPlus's own predictions, and the sums that go between those and an IPA string.

    <brain venv python> read_tags.py    # how many reviewed lines those tags can say exactly

The check prints why a word doesn't fit, and writes data/read_misfits.tsv.
"""
import collections
import json
import os
import re
import sys
import unicodedata

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
NAME = "@name"
NOTHING = 0  # consonant and vowel id for "none"
STRESS = "ˈ"
FURTIVE = "ח"
VRAM = 2_500_000_000  # (per GPU worker, beside whatever else is training)


def is_hebrew(ch):
    return "א" <= ch <= "ת"


class Teacher:
    """RenikudPlus's per-letter logits, with the vocabularies out of its metadata."""

    def __init__(self, model=None, threads=8, gpu=False):
        import onnxruntime as ort
        if model is None:
            with open(os.path.join(os.environ["LOCALAPPDATA"], "Puki", "brain", "config.json"), encoding="utf-8") as f:
                model = os.path.expanduser(json.load(f)["renikud_model"])
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = threads
        self.session = ort.InferenceSession(model, opts, providers=([("CUDAExecutionProvider", {"gpu_mem_limit": VRAM})] if gpu else []) + ["CPUExecutionProvider"])
        meta = self.session.get_modelmeta().custom_metadata_map
        self.vocab = json.loads(meta["vocab"])
        self.consonants = {int(k): v for k, v in json.loads(meta["consonant_vocab"]).items()}
        self.vowels = {int(k): v for k, v in json.loads(meta["vowel_vocab"]).items()}
        self.allowed = json.loads(meta["letter_consonant_constraints"])
        self.geresh = json.loads(meta.get("geresh_map", "{}"))
        self.cls, self.sep = int(meta["cls_token_id"]), int(meta["sep_token_id"])

    def logits(self, text, speaker=0, target=0):
        """(consonant, vowel, stress) logits, letters x classes, one text at a time: the export won't batch."""
        unk = self.vocab.get("[UNK]", 0)
        ids = np.array([[self.cls] + [self.vocab.get(c, unk) for c in text] + [self.sep]], dtype=np.int64)
        got = self.session.run(["consonant_logits", "vowel_logits", "stress_logits"], {
            "input_ids": ids, "attention_mask": np.ones_like(ids),
            "speaker": np.array([speaker], dtype=np.int64), "target_speaker": np.array([target], dtype=np.int64),
        })
        return [a[0] for a in got]

    def tags(self, text, speaker=0, target=0):
        """[(consonant id, vowel id, stressed)] per character of text, as G2P.phonemize decides them."""
        c, v, s = self.logits(text, speaker, target)
        out = []
        for t, ch in enumerate(text, 1):
            cons = int(c[t].argmax())
            if ch in self.allowed and cons not in self.allowed[ch]:
                cons = max(self.allowed[ch], key=lambda k: c[t, k])
            out.append([cons, int(v[t].argmax()), False])
        for word in re.finditer(r"\S+", text):
            voiced = [i for i in range(word.start(), word.end()) if out[i][1] != NOTHING]
            if voiced:
                out[max(voiced, key=lambda i: s[i + 1, 1])][2] = True
        return out

    def say(self, text, tags):
        """The IPA those tags spell, the same way G2P.phonemize spells them."""
        out = []
        for i, (ch, (c, v, stress)) in enumerate(zip(text, tags)):
            if not is_hebrew(ch):
                if ch not in "'\"":
                    out.append(ch)
                continue
            cons = self.consonants[c]
            if ch in self.geresh and i + 1 < len(text) and text[i + 1] == "'":
                cons = self.geresh[ch]
            final = i + 1 >= len(text) or not text[i + 1].isalpha()
            mark = STRESS if stress and v != NOTHING else ""
            if ch == FURTIVE and final and self.vowels[v] == "a":
                out.append(mark + "aχ")
                continue
            out.append(("" if cons == "∅" else cons) + mark + ("" if v == NOTHING else self.vowels[v]))
        return "".join(out)

    def align(self, word, ipa, near=None):
        """The per-letter tags that spell ipa on word, closest to near (the teacher's tags) where several
        do, as (tags, tags changed), or None. A word has at most one stressed letter."""
        cons = {k: [v for v in self.allowed[k]] for k in self.allowed}
        best = {}

        def go(i, j, stressed):
            if i == len(word):
                return (0, []) if j == len(ipa) else None
            key = (i, j, stressed)
            if key in best:
                return best[key]
            ch = word[i]
            found = None
            if not is_hebrew(ch):
                options = [(0, None, go(i + 1, j, stressed))] if ch in "'\"" else []
                if ipa.startswith(ch, j) and ch not in "'\"":
                    options.append((0, None, go(i + 1, j + 1, stressed)))
                for cost, tag, rest in options:
                    if rest and (found is None or rest[0] < found[0]):
                        found = (rest[0], [[NOTHING, NOTHING, False]] + rest[1])
            else:
                final = i + 1 >= len(word) or not word[i + 1].isalpha()
                geresh = ch in self.geresh and i + 1 < len(word) and word[i + 1] == "'"
                for c in cons.get(ch, [NOTHING]):
                    sound = self.geresh[ch] if geresh else self.consonants[c]
                    sound = "" if sound == "∅" else sound
                    for v, vowel in self.vowels.items():
                        vowel = "" if v == NOTHING else vowel
                        for stress in (False, True) if v != NOTHING and not stressed else (False,):
                            mark = STRESS if stress else ""
                            spelled = mark + "aχ" if ch == FURTIVE and final and vowel == "a" else sound + mark + vowel
                            if not ipa.startswith(spelled, j):
                                continue
                            rest = go(i + 1, j + len(spelled), stressed or stress)
                            if rest is None:
                                continue
                            cost = rest[0]
                            if near is not None:
                                cost += sum(a != b for a, b in zip(near[i], (c, v, stress)))
                            if found is None or cost < found[0]:
                                found = (cost, [[c, v, stress]] + rest[1])
            best[key] = found
            return found

        got = go(0, 0, False)
        return None if got is None else (got[1], got[0])


def reviewed():
    with open(os.path.join(DATA, "say_final.jsonl"), encoding="utf-8") as f:
        for raw in f:
            r = json.loads(raw)
            if r.get("say"):
                yield r


def masked(word):
    """A word with @name in it, down to what's around the name: its phonemes are spliced in on the device."""
    return word.replace(NAME, "")


def clean(text):
    return unicodedata.normalize("NFD", re.sub(r"[׳'`´]", "'", re.sub(r'[״"]', '"', text)))


def check():
    t = Teacher()
    words = lines = fit_lines = changed_lines = 0
    tags_changed = letters = bar_words = bar_right = bar_lines = 0
    why, misfits = collections.Counter(), collections.Counter()
    for r in reviewed():
        lines += 1
        src, ph = clean(r["line"]).split(), r["say"].split()
        if len(src) != len(ph):
            why["words don't line up"] += 1
            misfits[(r["line"], r["say"])] += 1
            continue
        whole = t.tags(" ".join(src), 0, 2 if r["girl"] else 1)
        said = t.say(" ".join(src), whole).split()
        counted = [(a, b) for w, a, b in zip(src, said, ph) if NAME not in w]
        bar_words += len(counted)
        bar_right += sum(a == b for a, b in counted)
        bar_lines += all(a == b for a, b in counted)
        at, ok, moved = 0, True, 0
        for w, p in zip(src, ph):
            near = whole[at:at + len(w)]
            at += len(w) + 1
            words += 1
            if NAME in w:
                continue
            got = t.align(w, p, near)
            if got is None:
                got = t.align(w, p)
                why["doesn't fit" if got is None else "fits"] += 1
                ok = False
                misfits[(w, p)] += 1
                continue
            moved += got[1]
            letters += sum(map(is_hebrew, w))
            if p.count(STRESS) == 0 and re.search("[aeiou]", p):
                why["no stress mark"] += 1
        fit_lines += ok
        changed_lines += moved > 0
        tags_changed += moved
    print(f"{lines} lines, {words} words")
    print(f"exactly sayable: {fit_lines} lines ({fit_lines / lines:.1%})")
    print(f"lines where the review differs from Renikud's tags: {changed_lines} ({tags_changed} of {letters} letters' tags)")
    print(dict(why))
    print(f"the bar, Renikud told the gender: {bar_right / bar_words:.2%} of words, {bar_lines / lines:.1%} of lines as reviewed")
    with open(os.path.join(DATA, "read_misfits.tsv"), "w", encoding="utf-8") as f:
        f.write("word\tipa\tcount\n")
        for (w, p), n in misfits.most_common():
            f.write(f"{w}\t{p}\t{n}\n")
    print(len(misfits), "distinct misfits, leaving out lone letters:")
    for (w, p), n in misfits.most_common():
        if len(re.sub(r"[^א-ת]", "", w)) > 1:
            print(f"  {n:4}  {w}  {p}")


if __name__ == "__main__":
    sys.exit(check())
