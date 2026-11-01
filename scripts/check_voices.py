#!/usr/bin/env python3
"""Check that every voice line the app can play has an audio file in res/raw.

VoicePlayer skips missing files silently, so a gap means a game just stays
quiet. This script finds every voice key in the Kotlin code, expands the
templated ones (e.g. "voice_animal_${animal.id}") using the ids defined in
the content data, and reports keys with no file. Exits 1 if anything is missing.
Lines with the child's name (voice_*_name*) are optional: they're git-ignored, and the app
records them from the PC at run time. Audio files nothing plays are listed as a warning.

Usage: python scripts/check_voices.py [--lang he|en]
"""

import argparse
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC_DIR = os.path.join(ROOT, "app", "src", "main", "java")
RAW_DIR = os.path.join(ROOT, "app", "src", "main", "res", "raw")

# Templated key prefix -> the Kotlin constructor whose first argument is the id
TEMPLATE_SOURCES = {
    "voice_animal_": "AnimalDef",
    "voice_sound_": "AnimalDef",
    "voice_eats_": "AnimalDef",
    "voice_color_": "ColorDef",
    "voice_shape_": "ShapeDef",
    "voice_instrument_": "InstrumentDef",
    "voice_letter_": "LetterDef",
    "voice_body_": "BodyPart",
    "voice_clothing_": "ClothingItem",
    "voice_table_": "PlaceSetting",
    "voice_tidy_": "TidyItem",
    "voice_sticker_": "StickerDef",
    "voice_page_": "ColoringPage",
    "voice_food_": "FoodDef",
    "voice_say_": "SayWord",
    "voice_bedtime_": "BedtimeStep",
    "voice_number_": "CountingRound",  # ids are 1..max targetNumber
    "voice_island_": "IslandDef",
}

# Files whose voice keys are optional by design (e.g. praise variants picked at random)
OPTIONAL_KEY_FILES = {os.path.join("app", "src", "main", "java", "io", "github", "tomsadeh", "puki", "audio", "VoicePlayer.kt")}

KEY_RE = re.compile(r'"(voice_[a-z0-9_]*)(\$\{?[^"]*)?"')


def read_sources():
    sources = {}
    for dirpath, _, files in os.walk(SRC_DIR):
        for name in files:
            if name.endswith(".kt"):
                path = os.path.join(dirpath, name)
                with open(path, encoding="utf-8") as f:
                    sources[os.path.relpath(path, ROOT)] = f.read()
    return sources


def constructor_ids(all_code, constructor):
    if constructor == "CountingRound":
        numbers = [int(n) for n in re.findall(r"CountingRound\(\s*(\d+)", all_code)]
        return [str(n) for n in range(1, max(numbers) + 1)]
    pattern = rf'\b{constructor}\(\s*(?:id\s*=\s*)?"([a-z0-9_]+)"'
    return sorted(set(re.findall(pattern, all_code)))


def find_keys(sources):
    """Return {key: [where it's used]}, expanding templated keys."""
    all_code = "\n".join(sources.values())
    keys = {}
    unknown = []
    for path, code in sources.items():
        if path in OPTIONAL_KEY_FILES:
            continue
        for lineno, line in enumerate(code.splitlines(), 1):
            if "PreferencesKey(" in line:  # a settings key that happens to start with "voice_"
                continue
            for prefix, template in KEY_RE.findall(line):
                where = f"{path}:{lineno}"
                if not template:
                    keys.setdefault(prefix, []).append(where)
                elif prefix in TEMPLATE_SOURCES:
                    for id_ in constructor_ids(all_code, TEMPLATE_SOURCES[prefix]):
                        keys.setdefault(prefix + id_, []).append(where)
                else:
                    unknown.append(f"{where}  {prefix}{template}")
    return keys, unknown


def has_audio(key, lang, raw_files):
    # Mirrors VoicePlayer: Hebrew tries _he_f/_he_m first, then plain _he.
    # Gendered-only lines count as present only if both genders exist.
    if f"{key}_{lang}" in raw_files:
        return True
    return f"{key}_{lang}_f" in raw_files and f"{key}_{lang}_m" in raw_files


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--lang", choices=["he", "en"], action="append")
    args = parser.parse_args()
    langs = args.lang or ["he", "en"]

    raw_files = {os.path.splitext(f)[0] for f in os.listdir(RAW_DIR)}
    keys, unknown = find_keys(read_sources())

    problems = 0
    if unknown:
        problems += len(unknown)
        print("Templated voice keys this script doesn't know how to expand")
        print("(add them to TEMPLATE_SOURCES):")
        for u in unknown:
            print(f"  {u}")
        print()

    for lang in langs:
        missing = [k for k in sorted(keys) if not has_audio(k, lang, raw_files) and "_name" not in k]
        problems += len(missing)
        print(f"[{lang}] {len(keys) - len(missing)}/{len(keys)} voice lines have audio")
        for k in missing:
            print(f"  missing: {k}  (used at {keys[k][0]})")
        print()

    # Audio nothing plays (a warning: VoicePlayer also picks some lines by pattern, like praise)
    used = set(keys)
    optional_code = "\n".join(code for path, code in read_sources().items() if path in OPTIONAL_KEY_FILES)
    orphans = sorted({re.sub(r"_(he|en)(_[fm])?$", "", f) for f in raw_files if f.startswith("voice_")}
                     - used)
    orphans = [o for o in orphans if "_name" not in o and o.rstrip("_0123456789") not in optional_code]
    if orphans:
        print(f"Not played anywhere ({len(orphans)}; safe to delete, or wire them in):")
        for o in orphans:
            print(f"  {o}")
        print()

    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
