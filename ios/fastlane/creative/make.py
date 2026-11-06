"""Run: python3 make.py && node render.js (Playwright), then save the PNGs as RGB (opaque).

Puki's App Store header (21:9) and search-results art (3:2): the sea, the islands, Puki on his perch. No text."""
import re, sys, os, random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1] if len(sys.argv) > 1 else HERE


ASSETS = os.path.join(HERE, "..", "..", "Puki", "Resources", "Assets.xcassets")


def inner(name):
    s = open(os.path.join(ASSETS, name + ".imageset", name + ".svg")).read()
    return re.sub(r"^.*?<svg[^>]*>|</svg>\s*$", "", s, flags=re.S)


def icon(name, cx, cy, size):
    k = size / 100
    return f'<g transform="translate({cx - size/2:.1f},{cy - size/2:.1f}) scale({k:.4f})">{inner(name)}</g>'


def cloud(x, y, s, o=0.95):
    return (f'<g opacity="{o}" transform="translate({x},{y}) scale({s})">'
            '<ellipse cx="0" cy="18" rx="150" ry="16" fill="#A9DDF3" opacity=".5"/>'
            '<circle cx="-70" cy="0" r="55" fill="#fff"/><circle cx="0" cy="-30" r="75" fill="#fff"/>'
            '<circle cx="75" cy="-5" r="58" fill="#fff"/><rect x="-125" y="0" width="255" height="55" rx="27" fill="#fff"/>'
            '<circle cx="-15" cy="-55" r="28" fill="#fff" opacity=".9"/></g>')


def island(cx, cy, w, art, palm=None):
    """A sandy island with grass, a soft shadow on the water, and the island's picture standing on it."""
    h = w * 0.26
    g = [f'<ellipse cx="{cx}" cy="{cy + h*0.55}" rx="{w*0.62}" ry="{h*0.35}" fill="#1E88C8" opacity=".25"/>',
         f'<ellipse cx="{cx}" cy="{cy + h*0.2}" rx="{w*0.56}" ry="{h*0.42}" fill="#E8F7FF" opacity=".75"/>',  # foam
         f'<ellipse cx="{cx}" cy="{cy}" rx="{w*0.5}" ry="{h*0.5}" fill="#D9A95B"/>',
         f'<ellipse cx="{cx}" cy="{cy - h*0.08}" rx="{w*0.48}" ry="{h*0.42}" fill="#F6D58E"/>',
         f'<ellipse cx="{cx}" cy="{cy - h*0.16}" rx="{w*0.36}" ry="{h*0.26}" fill="#7CC36A"/>',
         f'<ellipse cx="{cx - w*0.1}" cy="{cy - h*0.24}" rx="{w*0.16}" ry="{h*0.08}" fill="#A5D98F"/>']
    if palm:
        px, py = cx + palm * w * 0.3, cy - h * 0.15
        t = w * 0.55
        g.append(f'<path d="M{px},{py} Q{px + palm*t*0.12},{py - t*0.5} {px + palm*t*0.05},{py - t*0.9}" stroke="#8D6E63" stroke-width="{w*0.035}" fill="none" stroke-linecap="round"/>')
        tx, ty = px + palm * t * 0.05, py - t * 0.9
        for a, b in [(-1, -0.2), (1, -0.25), (-0.8, 0.25), (0.9, 0.3), (0.1, -0.5)]:
            g.append(f'<path d="M{tx},{ty} Q{tx + a*t*0.3},{ty + b*t*0.5 - t*0.15} {tx + a*t*0.45},{ty + b*t*0.4 + t*0.1}" stroke="#43A047" stroke-width="{w*0.05}" fill="none" stroke-linecap="round"/>')
        g.append(f'<circle cx="{tx}" cy="{ty + t*0.04}" r="{w*0.025}" fill="#6D4C41"/>')
    if art:
        s = w * 0.5
        g.append(icon(art, cx, cy - h * 0.2 - s * 0.42, s))
    return "\n".join(g)


def scene(W, H, layout):
    sea = H * layout["sea"]
    p = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
         '<defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5EC4F2"/>'
         '<stop offset="1" stop-color="#C7EEFF"/></linearGradient>'
         '<linearGradient id="sea" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4FC3F7"/>'
         '<stop offset="1" stop-color="#1E88E5"/></linearGradient>'
         '<radialGradient id="sun"><stop offset="0" stop-color="#FFF59D"/><stop offset=".55" stop-color="#FFEB3B"/>'
         '<stop offset="1" stop-color="#FFEB3B" stop-opacity="0"/></radialGradient></defs>',
         f'<rect width="{W}" height="{H}" fill="url(#sky)"/>']
    sx, sy, sr = layout["sun"]
    p.append(f'<circle cx="{sx*W}" cy="{sy*H}" r="{sr*H*1.9}" fill="url(#sun)" opacity=".55"/>')
    p.append(f'<circle cx="{sx*W}" cy="{sy*H}" r="{sr*H}" fill="#FFE34D"/><circle cx="{sx*W - sr*H*.3}" cy="{sy*H - sr*H*.3}" r="{sr*H*.35}" fill="#FFF7B0" opacity=".8"/>')
    for x, y, s in layout["clouds"]:
        p.append(cloud(x * W, y * H, s * H / 1000))
    p.append(f'<rect y="{sea}" width="{W}" height="{H - sea}" fill="url(#sea)"/>')
    # gentle wave lines on the water, the same ones every time
    rnd = random.Random(7)
    for i in range(int(W / 90)):
        x = rnd.uniform(0, W); y = rnd.uniform(sea + 30, H - 20); l = rnd.uniform(40, 110) * (0.6 + (y - sea) / (H - sea))
        p.append(f'<path d="M{x},{y} q{l/2},-{l/5} {l},0" stroke="#fff" stroke-opacity=".45" stroke-width="{4 + 5*(y - sea)/(H - sea):.1f}" fill="none" stroke-linecap="round"/>')
    p.append(f'<path d="M0,{sea} ' + " ".join(f"q{W/40},-{H*0.012} {W/20},0" for _ in range(20)) + f' V{sea+6} H0Z" fill="#B3E5FC"/>')
    for cx, cy, w, art, palm in layout["islands"]:
        p.append(island(cx * W, cy * H, w * H, art, palm))
    # Puki on his perch, on his own island in the middle
    px, py, ps = layout["puki"]
    ix, iy, iw = layout["home"]
    p.append(island(ix * W, iy * H, iw * H, None, None))
    perch_y = py * H + 0.38 * ps * H          # the perch in his picture is at 88 of 100
    post_x = px * W + 0.02 * ps * H
    ground = iy * H - iw * H * 0.26 * 0.18
    p.append(f'<ellipse cx="{post_x}" cy="{ground}" rx="{ps*H*0.09}" ry="{ps*H*0.025}" fill="#5D4037" opacity=".3"/>')
    p.append(f'<rect x="{post_x - ps*H*0.025}" y="{perch_y}" width="{ps*H*0.05}" height="{ground - perch_y}" rx="{ps*H*0.02}" fill="#8D6E63"/>')
    p.append(f'<rect x="{post_x - ps*H*0.025}" y="{perch_y}" width="{ps*H*0.014}" height="{ground - perch_y}" rx="{ps*H*0.007}" fill="#A1887F"/>')
    p.append(icon("ic_companion_parrot", px*W, py*H, ps*H))
    p.append("</svg>")
    return "\n".join(p)


HEADER = dict(  # 21:9. The middle carries the picture; the store lays its own things over the bottom
    sea=0.60, sun=(0.08, 0.17, 0.085), shadow=0.86,
    clouds=[(0.26, 0.16, 1.0), (0.55, 0.10, 0.75), (0.80, 0.22, 0.9), (0.94, 0.08, 0.6)],
    islands=[(0.13, 0.66, 0.30, "ic_island_music", 1), (0.30, 0.71, 0.36, "ic_island_animals", None),
             (0.70, 0.71, 0.36, "ic_island_colors", None), (0.87, 0.66, 0.30, "ic_island_shapes", -1),
             ],
    home=(0.5, 0.80, 0.42), puki=(0.5, 0.36, 0.52))

SEARCH = dict(  # 3:2
    sea=0.58, sun=(0.12, 0.14, 0.075), shadow=0.84,
    clouds=[(0.38, 0.12, 0.8), (0.78, 0.17, 0.9), (0.95, 0.06, 0.5)],
    islands=[(0.12, 0.66, 0.25, "ic_island_music", 1), (0.27, 0.75, 0.27, "ic_island_animals", None),
             (0.73, 0.75, 0.27, "ic_island_colors", None), (0.88, 0.66, 0.25, "ic_island_shapes", -1),
             ],
    home=(0.5, 0.80, 0.38), puki=(0.5, 0.36, 0.48))

for name, lay, W, H in [("puki_header_3840x1646", HEADER, 3840, 1646), ("puki_search_3840x2560", SEARCH, 3840, 2560)]:
    open(os.path.join(HERE, name + ".svg"), "w").write(scene(W, H, lay))
