"""The game moments Puki reacts to, read from the app's own code so the dataset follows the games.

Every `companion.react("...", "...")`, `RoundPrompt(..., scene = "...", sceneEn = "...")` and
`setScene(...)` call is a template: a Hebrew and an English sentence with blanks such as
`${color.name.he}`. The blanks are filled from the app's content files (animals and their food,
colors, shapes, letters, counting things, stickers), keeping each template's sense: a success
gets the right food or color, a miss gets a wrong one. A few moments built from variables in
the app (Peekaboo, the counting cheer, tapping Puki in a game) are added here by hand.

    python moments.py            # prints how many moments, and a few of them
"""
import glob
import itertools
import os
import random
import re

APP = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app", "src", "main", "java", "io", "github", "tomsadeh", "puki")
STR = r'"((?:[^"\\]|\\.)*)"'
CALL = re.compile(r'(?:react|RoundPrompt|setScene)\((?:[^"()]*?,)?\s*(?:scene\s*=\s*)?' + STR +
                  r'\s*,\s*(?:sceneEn\s*=\s*)?' + STR)
BLANK = re.compile(r"\$\{([^}]*)\}")


def _read(*parts):
    with open(os.path.join(APP, *parts), encoding="utf-8") as f:
        return f.read()


def _defs(text, kind):
    """(id, he, en) for each `<kind>(id, BilingualText(he, en), ...)` in a content file."""
    pat = kind + r'\(\s*(?:id\s*=\s*)?"(\w+)",\s*(?:name\s*=\s*)?BilingualText\("([^"]*)",\s*"([^"]*)"'
    return [{"id": i, "he": he, "en": en} for i, he, en in re.findall(pat, text)]


def content():
    """The things the blanks are filled with, from the app's content files."""
    animals_kt = _read("data", "content", "AnimalContent.kt")
    animals = _defs(animals_kt, "AnimalDef")
    # Named arguments (the first animals) or positional ones, the food right after the color
    food_of = dict(re.findall(r'id = "(\w+)",[^)]*?\)[\s\S]*?foodId = "(\w+)"', animals_kt))
    food_of.update(re.findall(r'AnimalDef\("(\w+)",.*?Color\([^)]*\),\s*"(\w+)"', animals_kt))
    for a in animals:
        a["food"] = food_of.get(a["id"])
    feminine = set(re.findall(r'"(\w+)"', re.search(r"FEMININE_ANIMALS = setOf\(([^)]*)\)", animals_kt).group(1)))
    for a in animals:
        a["she"] = a["id"] in feminine
    letters = [{"id": i, "he_letter": hl, "en_letter": el, "word_he": wh, "word_en": we} for i, hl, el, wh, we in re.findall(
        r'LetterDef\("(\w+)",\s*"([^"]+)",\s*"([^"]+)",\s*BilingualText\([^)]*\),\s*BilingualText\("([^"]*)",\s*"([^"]*)"\)',
        _read("data", "content", "WordContent.kt"))]
    counting = [{"n": int(n), "he": he, "en": en} for n, he, en in re.findall(
        r'CountingRound\((\d+),\s*BilingualText\("([^"]*)",\s*"([^"]*)"\)', _read("data", "content", "CountingContent.kt"))]
    def unique(items, key):
        seen, out = set(), []
        for it in items:
            if it[key] not in seen:
                seen.add(it[key])
                out.append(it)
        return out

    return {
        "animals": unique([a for a in animals if a["food"]], "id"),
        "foods": _defs(animals_kt, "FoodDef"),
        "colors": _defs(_read("data", "content", "ColorContent.kt"), "ColorDef"),
        "shapes": _defs(_read("data", "content", "ShapeContent.kt"), "ShapeDef"),
        "letters": unique(letters, "he_letter"),
        "counting": counting,
        "stickers": _defs(_read("ui", "stickers", "StickerAlbumScreen.kt"), "StickerDef"),
    }


def templates():
    """(file, he, en) for each moment template in the app's code."""
    out = []
    for f in sorted(glob.glob(os.path.join(APP, "**", "*.kt"), recursive=True)):
        with open(f, encoding="utf-8") as fh:
            for m in CALL.finditer(fh.read()):
                if "$scene" in m.group(1):  # (the tap-on-Puki wrapper, added below for each scene)
                    continue
                out.append((os.path.basename(f), m.group(1), m.group(2)))
    return out


def _value(expr, picks):
    """The text for one blank, e.g. `state.targetColor.name.en.lowercase()`."""
    lower = expr.endswith(".lowercase()")
    path = expr.replace(".lowercase()", "").split(".")
    if path[0] == "state":
        path = path[1:]
    obj = picks[path[0]]
    rest = path[1:]
    key = {("name", "he"): "he", ("name", "en"): "en", ("itemName", "he"): "he", ("itemName", "en"): "en",
           ("hebrewLetter",): "he_letter", ("englishLetter",): "en_letter",
           ("exampleWord", "he"): "word_he", ("exampleWord", "en"): "word_en"}[tuple(rest)]
    text = str(obj[key])
    return text.lower() if lower else text


def fill(template_he, template_en, c, rng):
    """One filled-in moment for a template, or None if a blank isn't one we know."""
    blanks = {BLANK.sub("", b) or b for b in BLANK.findall(template_he + " " + template_en)}
    roots = {b.replace("state.", "").split(".")[0] for b in BLANK.findall(template_he + " " + template_en)}
    # A miss gets a wrong one: "...but we were looking for", "tried to put X where Y goes"
    miss = any(w in template_he for w in ("אבל", "ניסו")) or any(w in template_en for w in ("but ", "tried to"))
    picks = {}
    try:
        if roots & {"currentAnimal", "animal", "target"}:
            a = rng.choice(c["animals"])
            for r in ("currentAnimal", "animal", "target"):
                picks[r] = a
            if "food" in roots:
                foods = {f["id"]: f for f in c["foods"]}
                right = foods.get(a["food"])
                picks["food"] = rng.choice([f for f in c["foods"] if f["id"] != a["food"]]) if miss else right
                if picks["food"] is None:
                    return None
        for one, target, pool in (("color", "targetColor", "colors"), ("shape", "targetShape", "shapes"),
                                  ("letter", "targetLetter", "letters")):
            if roots & {one, target}:
                t = rng.choice(c[pool])
                picks[target] = t
                shown = "he_letter" if pool == "letters" else "he"  # (compared by what the child sees)
                picks[one] = rng.choice([x for x in c[pool] if x[shown] != t[shown]]) if miss else t
        if "round" in roots:
            picks["round"] = rng.choice(c["counting"])
        if "sticker" in roots:
            picks["sticker"] = rng.choice(c["stickers"])
        he = BLANK.sub(lambda m: _value(m.group(1), picks), template_he)
        en = BLANK.sub(lambda m: _value(m.group(1), picks), template_en)
    except (KeyError, IndexError):
        return None
    return {"he": he, "en": en}


def by_hand(c, rng):
    """Moments the app builds from variables (so the extractor can't see them)."""
    out = []
    spots = [("ברפת", "in the barn"), ("מאחורי השיח", "behind the bush"),
             ("מאחורי ערמת החציר", "behind the haystack"), ("בתוך הגזע", "in the tree stump")]
    for a in c["animals"]:
        where, where_en = rng.choice(spots)
        out.append({"template": "peekaboo found",
                    "he": f"מצאו את ה{a['he']}! {'היא התחבאה' if a['she'] else 'הוא התחבא'} {where}.",
                    "en": f"The child found the {a['en'].lower()}! It was hiding {where_en}."})
        out.append({"template": "peekaboo scene",
                    "he": f"במשחק המחבואים: {a['he']} {'מתחבאת' if a['she'] else 'מתחבא'} ומחפשים {'אותה' if a['she'] else 'אותו'}.",
                    "en": f"In the peekaboo game: the {a['en'].lower()} is hiding, and we're looking for it."})
    for r in c["counting"]:
        out.append({"template": "counting cheer", "he": f"ספרו {r['n']} {r['he']}!",
                    "en": f"The child counted {r['n']} {r['en'].lower()}!"})
    return out


def moments(seed=7, per_template=12):
    """Every moment, as {"template", "he", "en"}: each template filled several ways, the
    hand-made ones, and tapping Puki during a scene."""
    rng = random.Random(seed)
    c = content()
    out, seen = [], set()
    for f, he, en in templates():
        tries = per_template if BLANK.search(he) else 1
        for _ in range(tries * 3):
            m = fill(he, en, c, rng)
            if m and m["he"] not in seen:
                seen.add(m["he"])
                out.append({"template": he, **m})
            if sum(1 for o in out if o["template"] == he) >= tries:
                break
    out += by_hand(c, rng)
    # Tapping Puki during a scene: the app wraps the scene (Companion.kt)
    scenes = [o for o in out if ":" in o["he"].split(" ")[0] or o["he"].startswith("במשחק") and ":" in o["he"]]
    for s in rng.sample(scenes, min(len(scenes), 30)):
        out.append({"template": "tap: " + s["template"],
                    "he": f"לחצו על התוכי כדי שידבר. {s['he']} תגיד משהו חמוד וקצר על זה.",
                    "en": f"The child tapped you so you'd talk. {s['en']} Say something short and cute about it."})
    return out


if __name__ == "__main__":
    ms = moments()
    print(len(ms), "moments from", len({m["template"] for m in ms}), "templates")
    for m in random.Random(1).sample(ms, 8):
        print("-", m["he"], "|", m["en"])
