"""Builds the game and publishes it as a GitHub release, where the game finds its updates.

    python scripts/release.py              # build only: dist/Puki-<version>.apk
    python scripts/release.py --publish    # build, then upload to TomSadeh/puki release v<version>

The version comes from app/build.gradle.kts (pukiVersion): bump it first. Publishing only works
from the public puki checkout, and stops if any personal voice lines (voice_*_name*, which say
the child's name) are in res/raw, so a release never carries them. It also refuses to publish an
APK signed with the debug key: releases are signed with the key in ~/keys (see app/build.gradle.kts).

Puki's brain runs on the tablet itself (0.9.0 on), so a release carries only the APK.
"""
import argparse
import glob
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = "TomSadeh/puki"


def run(cmd, **kw):
    print("  $", " ".join(cmd))
    return subprocess.run(cmd, cwd=ROOT, check=True, **kw)


def sdk_dir():
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(env):
            return os.environ[env]
    props = os.path.join(ROOT, "local.properties")
    if os.path.exists(props):
        for line in open(props, encoding="utf-8"):
            if line.startswith("sdk.dir="):
                return line.split("=", 1)[1].strip().replace("\\:", ":").replace("\\\\", "\\")  # local.properties escapes them
    return os.path.join(os.path.expanduser("~"), "AppData", "Local", "Android", "Sdk")


def signer(apk):
    """The APK's signing certificate: (subject line, SHA-256 fingerprint)."""
    tools = sorted(glob.glob(os.path.join(sdk_dir(), "build-tools", "*")))
    exe = os.path.join(tools[-1], "apksigner.bat" if os.name == "nt" else "apksigner")
    out = subprocess.run([exe, "verify", "--print-certs", apk], capture_output=True, text=True, check=True).stdout
    dn = re.search(r"certificate DN: (.*)", out).group(1).strip()
    fp = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", out).group(1)
    return dn, fp


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--publish", action="store_true", help="upload to the GitHub release (asks gh to create it)")
    ap.add_argument("--notes", default="", help="what's new, for the release page")
    args = ap.parse_args()

    gradle = open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8").read()
    version = re.search(r'val pukiVersion = "([\d.]+)"', gradle).group(1)
    print(f"Puki {version}")

    personal = glob.glob(os.path.join(ROOT, "app", "src", "main", "res", "raw", "voice_*_name*"))
    if personal:
        sys.exit(f"Stopping: {len(personal)} personal voice lines (with the child's name) are in res/raw. "
                 "Release from the puki checkout, which has none.")
    if args.publish:
        remote = subprocess.run(["git", "remote", "get-url", "origin"], cwd=ROOT, capture_output=True, text=True).stdout
        if REPO not in remote:
            sys.exit(f"Stopping: publish from the {REPO} checkout (this one is {remote.strip()}).")
        if subprocess.run(["git", "status", "--porcelain"], cwd=ROOT, capture_output=True, text=True).stdout.strip():
            sys.exit("Stopping: commit first, so the release matches the code on GitHub.")

    gradlew = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
    run([gradlew, "-q", "assembleGithubRelease"])
    apk = os.path.join(ROOT, "app", "build", "outputs", "apk", "github", "release", "app-github-release.apk")
    os.makedirs(os.path.join(ROOT, "dist"), exist_ok=True)
    out = os.path.join(ROOT, "dist", f"Puki-{version}.apk")
    shutil.copyfile(apk, out)
    sha = hashlib.sha256(open(out, "rb").read()).hexdigest()
    print(f"  {out}  {os.path.getsize(out) / 1e6:.1f} MB  sha256 {sha}")
    dn, fp = signer(out)
    print(f"  signed by {dn}  (certificate SHA-256 {fp})")
    if "Android Debug" in dn:
        if args.publish:
            sys.exit("Stopping: this APK is signed with the debug key. Put the release key in ~/keys first "
                     "(puki-release.jks and puki-signing.properties, see app/build.gradle.kts).")
        print("  (debug key: fine for testing, but it can't be published)")
    if not args.publish:
        return

    tag = f"v{version}"
    have = subprocess.run(["gh", "release", "view", tag, "-R", REPO, "--json", "assets"], cwd=ROOT,
                          capture_output=True, text=True)
    if have.returncode == 0:
        if any(a["name"].endswith(".apk") for a in json.loads(have.stdout)["assets"]):
            sys.exit(f"Stopping: {tag} already has an APK. Bump pukiVersion for a new release.")
        run(["gh", "release", "upload", tag, out, "-R", REPO])
    else:
        notes = args.notes or f"Puki {version}. Installed games update themselves from here (parent settings)."
        run(["gh", "release", "create", tag, out, "-R", REPO, "--title", f"Puki {version}", "--notes", notes,
             "--prerelease"])
    print(f"Published {tag}: games will offer the update.")


if __name__ == "__main__":
    main()
