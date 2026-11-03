#!/usr/bin/env python3
"""Copies the safety word lists from the Android app's Safety.kt (itself synced from brain/safety.py by
scripts/sync_safety_kt.py) into the iOS app's Safety.swift, between the GENERATED markers.

    python3 ios/scripts/sync_safety_swift.py          # rewrite Safety.swift's lists
    python3 ios/scripts/sync_safety_swift.py --check  # fail if they differ (CI)
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
KT = os.path.join(ROOT, "app/src/main/java/io/github/tomsadeh/puki/brain/Safety.kt")
SWIFT = os.path.join(ROOT, "ios/Puki/Sources/Brain/Safety.swift")
BEGIN = "    // BEGIN GENERATED (ios/scripts/sync_safety_swift.py)\n"
END = "    // END GENERATED\n"


def rules_from_kt():
    kt = open(KT, encoding="utf-8").read()
    block = kt[kt.index("val RULES = listOf("):]
    block = block[:block.index("\n    )\n")]
    rules = []
    for m in re.finditer(r'Rule\("([a-z_]+)",\s*listOf\((.*?)\),\s*listOf\((.*?)\)\)', block, re.S):
        he = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(2))
        en = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(3))
        rules.append((m.group(1), he, en))
    if len(rules) < 10:
        sys.exit("couldn't read the rules from Safety.kt")
    return rules


def render(rules):
    q = lambda xs: ", ".join(f'"{x}"' for x in xs)
    out = [BEGIN, "    static let rules: [Rule] = [\n"]
    for cat, he, en in rules:
        out.append(f'        Rule("{cat}",\n            [{q(he)}],\n            [{q(en)}]),\n')
    out.append("    ]\n")
    out.append(END)
    return "".join(out)


def main():
    swift = open(SWIFT, encoding="utf-8").read()
    start, end = swift.index(BEGIN), swift.index(END) + len(END)
    new = swift[:start] + render(rules_from_kt()) + swift[end:]
    if "--check" in sys.argv:
        if new != swift:
            sys.exit("Safety.swift's word lists differ from Safety.kt: run python3 ios/scripts/sync_safety_swift.py")
        print("Safety.swift matches Safety.kt")
        return
    open(SWIFT, "w", encoding="utf-8").write(new)
    print("Safety.swift updated")


if __name__ == "__main__":
    main()
