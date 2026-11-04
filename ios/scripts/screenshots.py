#!/usr/bin/env python3
"""App Store screenshots from the simulators, in the folders fastlane deliver reads.

    python3 ios/scripts/screenshots.py path/to/Puki.app out_dir

A debug build opens a screen straight away with `-PukiScreenshot <route> -PukiLang en|he` (see AppModel). This
boots the largest iPhone (6.9") and iPad (13") simulators, takes each screen in each language, turns the
pictures to landscape, and writes out_dir/<locale>/<device>_<n>_<screen>.png.
"""
import json, os, subprocess, sys, time

BUNDLE = "com.naimdor.puki"
SCREENS = ["hub", "animals", "colors", "counting", "peekaboo", "music", "myday/kitchen", "myday/bath"]
LOCALES = {"en-US": "en", "he": "he"}
ROTATE = 90  # degrees clockwise: the simulator captures in portrait, the game is landscape


def run(*args, check=True):
    return subprocess.run(args, check=check, capture_output=True, text=True).stdout


def pick(devices, want):
    """The newest available simulator whose name contains `want`."""
    best = None
    for runtime, items in sorted(devices.items()):
        if "iOS" not in runtime:
            continue
        for d in items:
            if d.get("isAvailable") and want in d["name"]:
                best = d
    if not best:
        sys.exit(f"no simulator named like '{want}'")
    return best


def main():
    app, out = sys.argv[1], sys.argv[2]
    devices = json.loads(run("xcrun", "simctl", "list", "devices", "available", "-j"))["devices"]
    for tag, want in (("iphone", "Pro Max"), ("ipad", "iPad Pro 13")):
        d = pick(devices, want)
        udid = d["udid"]
        print(f"{tag}: {d['name']}", flush=True)
        run("xcrun", "simctl", "boot", udid, check=False)
        run("xcrun", "simctl", "bootstatus", udid, "-b")
        run("xcrun", "simctl", "install", udid, app)
        run("xcrun", "simctl", "privacy", udid, "grant", "all", BUNDLE, check=False)
        for locale, lang in LOCALES.items():
            os.makedirs(os.path.join(out, locale), exist_ok=True)
            for i, screen in enumerate(SCREENS, 1):
                run("xcrun", "simctl", "launch", "--terminate-running-process", udid, BUNDLE,
                    "-PukiScreenshot", screen, "-PukiLang", lang)
                time.sleep(9 if i == 1 else 7)  # the round's prompt, pop-ins and Puki settling
                path = os.path.join(out, locale, f"{tag}_{i:02d}_{screen.replace('/', '-')}.png")
                run("xcrun", "simctl", "io", udid, "screenshot", path)
                size = run("sips", "-g", "pixelWidth", "-g", "pixelHeight", path).split()
                w, h = int(size[-3]), int(size[-1])
                if h > w:
                    run("sips", "-r", str(ROTATE), path)
                print(f"  {locale} {screen}: {path} ({max(w, h)}x{min(w, h)})", flush=True)
        run("xcrun", "simctl", "shutdown", udid, check=False)


main()
