#!/usr/bin/env python3
"""Paints the simulator's Dynamic Island out of iPhone screenshots.

Recent simulators draw the island into their screenshots: in landscape it is a black pill on the left edge, over
the gradient around the captioned game. Each row's dark run near that edge gets the colour just past it.
  ios/scripts/hide_island.py shots          (every iphone_*.png under it, in place)
"""
import glob
import os
import sys

from PIL import Image

DARK = 70      # darker than this is the island (the gradient's darkest corner is far brighter)
BAND = 0.09    # the island sits within this fraction of the width from the left edge
GROW = 0.012   # the box around it grows by this fraction of the width, for the pill's soft edge


def hide(path):
    img = Image.open(path).convert("RGB")
    w, h = img.size
    px = img.load()
    band = int(w * BAND)
    dark = [(x, y) for y in range(0, h, 2) for x in range(0, band, 2) if sum(px[x, y]) / 3 < DARK]
    if len(dark) < 50:
        return 0
    g = int(w * GROW)
    x0 = max(0, min(x for x, _ in dark) - g)
    x1 = min(w - 1, max(x for x, _ in dark) + g)
    y0 = max(0, min(y for _, y in dark) - g)
    y1 = min(h - 1, max(y for _, y in dark) + g)
    for y in range(y0, y1 + 1):
        right = px[min(w - 1, x1 + 1), y]
        left = px[x0 - 1, y] if x0 > 0 else right
        for x in range(x0, x1 + 1):
            t = (x - x0) / max(1, x1 - x0)
            px[x, y] = tuple(round(left[i] * (1 - t) + right[i] * t) for i in range(3))
    img.save(path)
    return y1 - y0 + 1


def main():
    root = sys.argv[1]
    for path in sorted(glob.glob(os.path.join(root, "**", "iphone_*.png"), recursive=True)):
        print(f"{path}: {hide(path)} rows painted over", flush=True)


if __name__ == "__main__":
    main()
