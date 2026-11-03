#!/usr/bin/env python3
"""Builds the iOS app's resources from the Android sources, so the two stay one game.

- app/src/main/res/drawable/*.xml (VectorDrawables) -> Puki/Resources/Assets.xcassets/<name>.imageset/<name>.svg
  (vector data preserved, so they stay sharp at any size, like on Android)
- the launcher icon (ic_launcher_background + ic_launcher_foreground) -> AppIcon (1024 px PNG, needs Chromium via
  Playwright for the rasterising; skipped with --no-icon)
- app/src/main/res/raw: voice lines (.ogg Vorbis, which iOS can't play) -> AAC .m4a; .wav and .mp3 copied as they are
- app/src/main/assets/licenses.txt copied

Run from anywhere: python3 ios/scripts/build_assets.py [--no-audio] [--no-icon]
Re-run it after adding a drawable or a voice line (scripts/generate_voices.py writes the .ogg files).
"""
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "app", "src", "main", "res")
IOS = os.path.join(ROOT, "ios", "Puki", "Resources")
XCASSETS = os.path.join(IOS, "Assets.xcassets")
AUDIO = os.path.join(IOS, "Audio")
A = "{http://schemas.android.com/apk/res/android}"


def color(value):
    """Android #AARRGGBB / #RRGGBB / #RGB -> (svg color, opacity)."""
    if value is None:
        return None, 1.0
    if value.startswith("@android:color/transparent"):
        return "none", 0.0
    v = value.lstrip("#")
    if len(v) == 3:
        v = "".join(c * 2 for c in v)
    if len(v) == 4:
        v = "".join(c * 2 for c in v)
    if len(v) == 8:
        a = int(v[:2], 16) / 255.0
        return "#" + v[2:], a
    return "#" + v, 1.0


def num(x, default=0.0):
    return float(x) if x is not None else default


def convert_group(el, out, indent):
    for child in el:
        tag = child.tag
        if tag == "group":
            px, py = num(child.get(A + "pivotX")), num(child.get(A + "pivotY"))
            rot = num(child.get(A + "rotation"))
            sx, sy = num(child.get(A + "scaleX"), 1), num(child.get(A + "scaleY"), 1)
            tx, ty = num(child.get(A + "translateX")), num(child.get(A + "translateY"))
            # Android: T(translate + pivot) * R(rotation) * S(scale) * T(-pivot)
            t = []
            if tx or ty or px or py:
                t.append(f"translate({tx + px:g} {ty + py:g})")
            if rot:
                t.append(f"rotate({rot:g})")
            if sx != 1 or sy != 1:
                t.append(f"scale({sx:g} {sy:g})")
            if px or py:
                t.append(f"translate({-px:g} {-py:g})")
            attr = f' transform="{" ".join(t)}"' if t else ""
            out.append(f"{indent}<g{attr}>")
            convert_group(child, out, indent + "  ")
            out.append(f"{indent}</g>")
        elif tag == "path":
            d = child.get(A + "pathData")
            fill, fa = color(child.get(A + "fillColor"))
            fa *= num(child.get(A + "fillAlpha"), 1)
            stroke, sa = color(child.get(A + "strokeColor"))
            sa *= num(child.get(A + "strokeAlpha"), 1)
            sw = child.get(A + "strokeWidth")
            parts = [f'd="{d}"']
            if fill is None or fill == "none" or fa == 0:
                parts.append('fill="none"')
            else:
                parts.append(f'fill="{fill}"')
                if fa < 1:
                    parts.append(f'fill-opacity="{fa:.3g}"')
            if child.get(A + "fillType") == "evenOdd":
                parts.append('fill-rule="evenodd"')
            if stroke and stroke != "none" and sa > 0 and sw and float(sw) > 0:
                parts.append(f'stroke="{stroke}" stroke-width="{sw}"')
                if sa < 1:
                    parts.append(f'stroke-opacity="{sa:.3g}"')
                cap = child.get(A + "strokeLineCap")
                join = child.get(A + "strokeLineJoin")
                if cap:
                    parts.append(f'stroke-linecap="{cap}"')
                if join:
                    parts.append(f'stroke-linejoin="{join}"')
            out.append(f"{indent}<path {' '.join(parts)}/>")


def vector_to_svg(path):
    root = ET.parse(path).getroot()
    if root.tag != "vector":
        return None
    vw, vh = root.get(A + "viewportWidth"), root.get(A + "viewportHeight")
    w = re.sub(r"[a-z]+", "", root.get(A + "width"))
    h = re.sub(r"[a-z]+", "", root.get(A + "height"))
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {vw} {vh}">']
    convert_group(root, out, "  ")
    out.append("</svg>")
    return "\n".join(out) + "\n"


def write_json(path, obj):
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def build_images():
    os.makedirs(XCASSETS, exist_ok=True)
    write_json(os.path.join(XCASSETS, "Contents.json"), {"info": {"author": "xcode", "version": 1}})
    drawables = os.path.join(RES, "drawable")
    count = 0
    for name in sorted(os.listdir(drawables)):
        if not name.endswith(".xml"):
            continue
        base = name[:-4]
        svg = vector_to_svg(os.path.join(drawables, name))
        if svg is None:
            continue
        d = os.path.join(XCASSETS, base + ".imageset")
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, base + ".svg"), "w") as f:
            f.write(svg)
        write_json(os.path.join(d, "Contents.json"), {
            "images": [{"filename": base + ".svg", "idiom": "universal"}],
            "info": {"author": "xcode", "version": 1},
            "properties": {"preserves-vector-representation": True},
        })
        count += 1
    print(f"images: {count}")


def build_icon():
    """The launcher icon: the sky-and-island background with Puki inset like Android's adaptive icon."""
    bg = open(os.path.join(XCASSETS, "ic_launcher_background.imageset", "ic_launcher_background.svg")).read()
    parrot = open(os.path.join(XCASSETS, "ic_companion_parrot.imageset", "ic_companion_parrot.svg")).read()
    pr = ET.fromstring(parrot)
    pvb = pr.get("viewBox")
    inner = parrot.split(">", 1)[1].rsplit("</svg>", 1)[0]
    # Android insets on the 108 canvas: left/right 21, top 14, bottom 28
    fg = f'<svg x="21" y="14" width="66" height="66" viewBox="{pvb}">{inner}</svg>'
    icon = bg.replace("</svg>", fg + "</svg>")
    d = os.path.join(XCASSETS, "AppIcon.appiconset")
    os.makedirs(d, exist_ok=True)
    svg_path = os.path.join(d, "icon.svg")
    with open(svg_path, "w") as f:
        f.write(icon)
    png = os.path.join(d, "AppIcon.png")
    js = f"""
const {{ chromium }} = require('playwright');
(async () => {{
  const b = await chromium.launch();
  const p = await b.newPage({{ viewport: {{ width: 1024, height: 1024 }}, deviceScaleFactor: 1 }});
  const svg = require('fs').readFileSync({json.dumps(svg_path)}, 'utf8')
     .replace(/width="108" height="108"/, 'width="1024" height="1024"');
  await p.setContent('<html><body style="margin:0">' + svg + '</body></html>');
  await p.screenshot({{ path: {json.dumps(png)}, clip: {{ x: 0, y: 0, width: 1024, height: 1024 }} }});
  await b.close();
}})();
"""
    subprocess.run(["node", "-e", js], check=True)
    os.remove(svg_path)
    write_json(os.path.join(d, "Contents.json"), {
        "images": [{"filename": "AppIcon.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}],
        "info": {"author": "xcode", "version": 1},
    })
    print("icon: AppIcon.png")


def convert_one(args):
    src, dst = args
    if os.path.exists(dst) and os.path.getmtime(dst) >= os.path.getmtime(src):
        return
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", src, "-c:a", "aac", "-b:a", "64k", dst], check=True)


def build_audio():
    os.makedirs(AUDIO, exist_ok=True)
    raw = os.path.join(RES, "raw")
    jobs, wanted = [], set()
    for name in sorted(os.listdir(raw)):
        src = os.path.join(raw, name)
        base, ext = os.path.splitext(name)
        if ext == ".ogg":
            dst = os.path.join(AUDIO, base + ".m4a")
            jobs.append((src, dst))
        elif ext in (".wav", ".mp3"):
            dst = os.path.join(AUDIO, name)
            shutil.copy2(src, dst)
        else:
            continue
        wanted.add(os.path.basename(dst))
    for stale in set(os.listdir(AUDIO)) - wanted:
        os.remove(os.path.join(AUDIO, stale))
    with ThreadPoolExecutor(max_workers=os.cpu_count() or 4) as ex:
        list(ex.map(convert_one, jobs))
    print(f"audio: {len(wanted)} files")


def main():
    build_images()
    if "--no-icon" not in sys.argv:
        build_icon()
    if "--no-audio" not in sys.argv:
        build_audio()
    os.makedirs(IOS, exist_ok=True)
    shutil.copy2(os.path.join(ROOT, "app", "src", "main", "assets", "licenses.txt"), os.path.join(IOS, "licenses.txt"))


if __name__ == "__main__":
    main()
