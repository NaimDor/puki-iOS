#!/usr/bin/env python3
"""Compare this repo with its sister (puki <-> olam-katan), line endings ignored, so a port can be
checked at a glance. Prints file names and counts of differing lines only, never their contents:
the family repo's files can hold personal values.

    python scripts/sister_diff.py [path to the other repo]    # default: ../olam-katan or ../puki

Exits 1 if anything differs beyond the files the two repos differ in on purpose.
"""

import difflib
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

# Only one of the repos has these (the public one's docs, store text and training)
ONE_SIDED = ("README.md", "ROADMAP.md", "CONTRIBUTING.md", "LICENSE", "docs/", "fastlane/", "train/", ".github/",
             "Starry Sleep Steps.mp3", "Sunny Steps.mp3", "sfx_olam_katan.md")
# Different on purpose
EXPECTED = {
    "app/src/main/java/io/github/tomsadeh/puki/data/models/AppSettings.kt",  # the family's default address
    "brain/brain.py",  # no renikud config lines in the family repo
    "brain/doorbell.py",  # the same
    ".gitignore",  # leftovers of the family repo's whisper.cpp build
    "CLAUDE.md",
}


def tracked(repo):
    out = subprocess.run(["git", "-C", repo, "ls-files"], capture_output=True, encoding="utf-8", check=True).stdout
    return {p for p in out.splitlines() if not p.startswith(ONE_SIDED)}


def lines(path):
    """The file's lines without their line endings, or None for a binary file."""
    with open(path, "rb") as f:
        data = f.read()
    if b"\0" in data:
        return data
    return data.replace(b"\r\n", b"\n").split(b"\n")


def main():
    default = "olam-katan" if os.path.basename(ROOT) == "puki" else "puki"
    other = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "..", default))
    mine, theirs = tracked(ROOT), tracked(other)
    surprises = 0
    for p in sorted(mine ^ theirs):
        print(f"only in {os.path.basename(ROOT) if p in mine else os.path.basename(other)}: {p}")
        surprises += 1
    for p in sorted(mine & theirs):
        a, b = lines(os.path.join(ROOT, p)), lines(os.path.join(other, p))
        if a == b:
            continue
        expected = p in EXPECTED
        if isinstance(a, bytes) or isinstance(b, bytes):
            print(f"differs (binary): {p}")
        else:
            changed = sum(1 for d in difflib.ndiff(
                [x.decode("utf-8", "replace") for x in a], [x.decode("utf-8", "replace") for x in b]) if d[0] in "+-")
            print(f"differs ({changed} lines{', expected' if expected else ''}): {p}")
        surprises += not expected
    print("the same, apart from the expected differences" if not surprises else f"{surprises} unexpected differences")
    sys.exit(1 if surprises else 0)


if __name__ == "__main__":
    main()
