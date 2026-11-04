#!/usr/bin/env python3
"""Puts the public support address everywhere the iOS app and its store pages show it.

    python3 ios/scripts/set_support_email.py support@example.com

Replaces the SUPPORT_EMAIL placeholder (or the address set before) in the app, the privacy and support pages,
and the App Store review notes. The upload workflows refuse to run while the placeholder is still there.
"""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILES = ["Puki/Sources/Data/Models.swift", "site/privacy.html", "site/index.html", "fastlane/metadata/review_information/email_address.txt"]
MARK = "ios/.support_email"

def main():
    if len(sys.argv) != 2 or not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", sys.argv[1]):
        sys.exit(__doc__)
    new = sys.argv[1]
    mark = os.path.join(os.path.dirname(ROOT), MARK)
    old = open(mark).read().strip() if os.path.exists(mark) else "SUPPORT_EMAIL"
    for f in FILES:
        p = os.path.join(ROOT, f)
        if f.endswith("email_address.txt"):
            open(p, "w").write(new)
            continue
        s = open(p, encoding="utf-8").read()
        open(p, "w", encoding="utf-8").write(s.replace(old, new))
    open(mark, "w").write(new + "\n")
    print(f"support email: {new}")

main()
