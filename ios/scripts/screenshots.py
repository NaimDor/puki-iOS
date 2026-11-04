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
# The headline over each screenshot (the first is the one search results show)
CAPTIONS = {
    "en": ["Gentle games for toddlers", "Feed the animals, learn their names", "Find the colors, tap or say it",
           "Count along with Puki", "Who's hiding in the barn?", "Make music, then dance",
           "My Day: bake, bathe, brush", "No ads. No tracking. Ever."],
    "he": ["משחקים רכים לפעוטות", "מאכילים את החיות ולומדים שמות", "מוצאים צבעים, בלחיצה או בקול",
           "סופרים יחד עם פוקי", "מי מתחבא באסם?", "מנגנים, ואז רוקדים",
           "היום שלי: אופים, מתרחצים, מצחצחים", "בלי פרסומות. בלי מעקב. אף פעם."],
}
TOUR_SECONDS = 30
LOCALES = {"en-US": "en", "he": "he"}
ROTATE = 270  # degrees clockwise: the simulator captures in portrait, the game is landscape


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
        if tag == "ipad":
            # An upright iPad shows a landscape-only game letterboxed: turn the simulated iPad on its side
            # (the Simulator app's Rotate Left, Cmd+Left), as a child holds it
            run("open", "-a", "Simulator", "--args", "-CurrentDeviceUDID", udid, check=False)
            time.sleep(12)
            run("osascript", "-e", 'tell application "Simulator" to activate', check=False)
            time.sleep(2)
            out_rot = subprocess.run(["osascript", "-e", 'tell application "System Events" to key code 123 using command down'],
                                     capture_output=True, text=True)
            print(f"  rotate: {out_rot.returncode} {out_rot.stderr.strip()}", flush=True)
            time.sleep(3)
        run("xcrun", "simctl", "privacy", udid, "grant", "all", BUNDLE, check=False)
        for locale, lang in LOCALES.items():
            os.makedirs(os.path.join(out, locale), exist_ok=True)
            for i, screen in enumerate(SCREENS, 1):
                run("xcrun", "simctl", "launch", "--terminate-running-process", udid, BUNDLE,
                    "-PukiScreenshot", screen, "-PukiLang", lang, "-PukiCaption", CAPTIONS[lang][i - 1])
                time.sleep(9 if i == 1 else 7)  # the round's prompt, pop-ins and Puki settling
                path = os.path.join(out, locale, f"{tag}_{i:02d}_{screen.replace('/', '-')}.png")
                run("xcrun", "simctl", "io", udid, "screenshot", path)
                size = run("sips", "-g", "pixelWidth", "-g", "pixelHeight", path).split()
                w, h = int(size[-3]), int(size[-1])
                if h > w:
                    run("sips", "-r", str(ROTATE), path)
                print(f"  {locale} {screen}: {path} ({max(w, h)}x{min(w, h)})", flush=True)
            # The preview video: the game walking through its islands by itself (raw, made App Store-ready later)
            os.makedirs(out + "-previews-raw", exist_ok=True)
            video = os.path.join(out + "-previews-raw", f"{tag}_{locale}.mov")
            run("xcrun", "simctl", "terminate", udid, BUNDLE, check=False)
            rec = subprocess.Popen(["xcrun", "simctl", "io", udid, "recordVideo", "--codec=h264", "--force", video],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            time.sleep(3)
            run("xcrun", "simctl", "launch", udid, BUNDLE, "-PukiScreenshot", "hub", "-PukiLang", lang, "-PukiTour", "1")
            time.sleep(TOUR_SECONDS + 2)
            rec.send_signal(2)  # SIGINT: the recording is finished and saved
            rec.wait(timeout=60)
            print(f"  {locale} preview: {video} ({os.path.getsize(video) // 1000} KB)", flush=True)
        run("xcrun", "simctl", "shutdown", udid, check=False)


main()
