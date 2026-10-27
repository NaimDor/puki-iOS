"""Puki's own pronunciations, for words RenikudPlus (BlueTTS's pronunciation step) gets wrong:
interjections and animal sounds ("אופס" is oops, not ops), Puki's name (Puki, not Poki), letter
names, and second-person verbs said to a girl ("מצאת" is matsat for her, matsata for a boy).

The table is pronunciation.tsv next to this file. When a line has a word from it, the brain
gets the line's phonemes itself, puts Puki's in for those words, and hands BlueTTS phonemes;
any other line is spoken exactly as before.
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
TAGGED = re.compile(r"<(\w+)>(.*?)</\1>", re.DOTALL)  # (BlueTTS's inline language spans)
HEBREW_WORD = re.compile(r"[א-ת][א-ת'\"״׳]*")
_table = None


def table():
    """{word: [(ipa, when)]}, read once."""
    global _table
    if _table is None:
        _table = {}
        path = os.path.join(HERE, "pronunciation.tsv")
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                for line in f:
                    if line.startswith("#") or line.startswith("word\t") or not line.strip():
                        continue
                    parts = line.rstrip("\n").split("\t")
                    if len(parts) >= 3 and parts[0] and parts[1]:
                        _table.setdefault(parts[0], []).append((parts[1], parts[2] or "all"))
    return _table


def lookup(word, girl):
    """Puki's pronunciation of [word] for this child, or None."""
    for ipa, when in table().get(word, ()):
        if when == "all" or when == ("girl" if girl else "boy"):
            return ipa
    return None


def _segments(text, lang):
    """(language, content) pieces of a line with BlueTTS's <xx>...</xx> spans."""
    out, last = [], 0
    for m in TAGGED.finditer(text):
        if m.start() > last:
            out.append((lang, text[last:m.start()]))
        out.append((m.group(1), m.group(2)))
        last = m.end()
    if last < len(text):
        out.append((lang, text[last:]))
    return [(l, c) for l, c in out if c.strip()]


def _fix_segment(content, ipa, girl):
    """A Hebrew segment's phonemes with Puki's words put in; None if the words don't line up."""
    src, ph = content.split(), ipa.split()
    if len(src) != len(ph):
        return None
    out = []
    for s, p in zip(src, ph):
        m = HEBREW_WORD.search(s)
        mine = lookup(m.group(0), girl) if m else None
        if mine:
            lead = re.match(r"^[^\wɐ-˿]*", p).group(0)
            trail = re.search(r"[^\wɐ-˿ˈː]*$", p).group(0)
            p = lead + mine + trail
        out.append(p)
    return " ".join(out)


def phonemes(text, lang, girl, phonemize_segment):
    """The line's phonemes (BlueTTS's tagged form) with Puki's pronunciations, or None when the
    line has none of the table's words (or has digits, which BlueTTS spells out itself), so
    it's spoken the usual way. [phonemize_segment](content, lang) is RenikudPlus via BlueTTS."""
    words = set(HEBREW_WORD.findall(text))
    if not any(lookup(w, girl) for w in words) or re.search(r"\d", text):
        return None
    pieces = []
    for seg_lang, content in _segments(text, lang):
        ipa = phonemize_segment(content, seg_lang)
        if not ipa:
            continue
        if any("א" <= c <= "ת" for c in content):
            fixed = _fix_segment(content, ipa, girl)
            if fixed is None:
                return None  # (couldn't line words up: the usual way is safer)
            ipa = fixed
        pieces.append(f"<{seg_lang}>{ipa}</{seg_lang}>")
    return "".join(pieces) or None
