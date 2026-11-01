"""Inputs for the games built around the model: Silly Puki, the caring game, the story with
choices and the bedtime recap. Each input is a Hebrew and an English sentence, the way the app
will send it; the things in them come from the app's content (animals, foods, colors, shapes,
counting) or from the lists below. Hebrew inputs avoid the child's gender (בחרו, נתנו; past
forms like מצאת are written the same for a girl and a boy), so one input serves both.

Tasks:
  silly       a picture and what to get wrong on purpose -> Puki's silly mistake
  silly_fix   Puki's silly line and what the child answered -> Puki laughs and puts it right
  care        who needs help, with what, and what the child gave -> Puki's line
  story_next  the hero, the story so far and the child's pick -> the next sentence
  recap       what the child did today -> a short bedtime retelling

    python games.py            # a few inputs of each
"""
import random
import re

import moments as moments_mod


def _le(word):
    """Hebrew "to the ...": ל + the word, folding the article (הבובה -> לבובה)."""
    return "ל" + (word[1:] if word.startswith("ה") else word)


def _the(word):
    """Hebrew article, unless the word already has one."""
    return word if word.startswith("ה") else "ה" + word


# ---------- Silly Puki ----------

# Animal sounds a 2-year-old knows in both languages (only the animals whose Hebrew sound is clear)
SOUNDS = {"cow": ("מו", "moo"), "dog": ("הב הב", "woof"), "cat": ("מיאו", "meow"), "sheep": ("מה", "baa"),
          "duck": ("גע גע", "quack"), "pig": ("אוינק", "oink"), "horse": ("היהי", "neigh")}
# Things with one color everyone knows: (he, en, feminine in Hebrew, color id)
COLORED = [("בננה", "banana", True, "yellow"), ("תות", "strawberry", False, "red"), ("עגבנייה", "tomato", True, "red"),
           ("דשא", "grass", False, "green"), ("מלפפון", "cucumber", False, "green"), ("שמיים", "sky", False, "blue"),
           ("שמש", "sun", True, "yellow"), ("גזר", "carrot", False, "orange"), ("תפוז", "orange", False, "orange"),
           ("שלג", "snow", False, "white"), ("חלב", "milk", False, "white"), ("שוקולד", "chocolate", False, "brown"),
           ("חציל", "eggplant", False, "purple")]
PLAIN_SHAPES = ("circle", "square", "triangle", "star", "heart")
# A number as Puki may say it: the digit, or the word (Hebrew masculine and feminine)
NUMBER_WORDS = {str(n): w for n, w in enumerate([
    [], ["אחד", "אחת", "one"], ["שניים", "שתיים", "שני", "שתי", "two"], ["שלושה", "שלוש", "three"],
    ["ארבעה", "ארבע", "four"], ["חמישה", "חמש", "five"], ["שישה", "שש", "six"], ["שבעה", "שבע", "seven"],
    ["שמונה", "eight"], ["תשעה", "תשע", "nine"], ["עשרה", "עשר", "ten"]])}


def _a(word):
    return ("an " if word[0] in "aeiou" else "a ") + word


def silly_inputs(rng):
    """[{he, en, truth_he, truth_en, group}] for every picture and mistake the game can ask for."""
    c = moments_mod.content()
    colors = {x["id"]: x for x in c["colors"]}
    foods = {x["id"]: x for x in c["foods"]}
    out = []

    def add(group, pic_he, pic_en, she, kind_he, kind_en, truth_he, truth_en):
        about = kind_he[1] if she else kind_he[0]  # (the Hebrew differs for a feminine picture)
        out.append({"group": group, "truth_he": truth_he, "truth_en": truth_en,
                    "he": f"בתמונה: {pic_he}. תגיד טעות מצחיקה אחת בכוונה, על {about} (באמת: {truth_he}).",
                    "en": f"Picture: {pic_en}. Make one silly mistake on purpose, about its {kind_en} (really: {truth_en})."})

    for a in c["animals"]:
        if a["id"] in SOUNDS:
            he, en = SOUNDS[a["id"]]
            add(f"sound:{a['id']}", a["he"], _a(a["en"].lower()), a["she"], ("הקול שלו", "הקול שלה"), "sound", he, en)
        f = foods.get(a["food"])
        if f:
            add(f"food:{a['id']}", a["he"], _a(a["en"].lower()), a["she"], ("מה שהוא אוכל", "מה שהיא אוכלת"),
                "food", f["he"], f["en"].lower())
    for he, en, she, col in COLORED:
        add(f"color:{en}", he, "the " + en if en in ("sky", "sun", "grass", "snow", "milk") else _a(en), she,
            ("הצבע שלו", "הצבע שלה"), "color", colors[col]["he"], colors[col]["en"].lower())
    for s in c["shapes"]:
        if s["id"] in PLAIN_SHAPES:
            add(f"shape:{s['id']}", s["he"], _a(s["en"].lower()), False, ("הצורה שלו", "הצורה שלו"), "shape", s["he"], s["en"].lower())
    for r in c["counting"]:
        if r["n"] > 1:
            out.append({"group": f"count:{r['n']}:{r['en']}", "truth_he": str(r["n"]), "truth_en": str(r["n"]),
                        "he": f"בתמונה: {r['n']} {r['he']}. תגיד טעות מצחיקה אחת בכוונה, בכמה יש (באמת: {r['n']}).",
                        "en": f"Picture: {r['n']} {r['en']}. Make one silly mistake on purpose, about how many (really: {r['n']})."})
    rng.shuffle(out)
    return out


# Things a toddler might say that aren't the answer (for silly_fix's "other")
OTHER_SAID = {"he": ["בננה", "עוד", "אבא", "כן", "פוקי", "לא יודעת", "הנה", "בובה", "מה זה", "איה"],
              "en": ["banana", "more", "daddy", "yes", "Puki", "dunno", "there", "ball", "what's that", "uh oh"]}


def silly_fix_input(lang, silly_line, truth, rng):
    """(outcome, input) for one answer to Puki's silly line."""
    outcome = rng.choice(["right", "right", "other", "nothing"])
    if lang == "he":
        said = {"right": f'"{rng.choice(["", "לא! ", "לא, "])}{truth}!"', "other": f'"{rng.choice(OTHER_SAID["he"])}"',
                "nothing": "כלום, רק צחקו"}[outcome]
        head = f'פוקי אמר בצחוק: "{silly_line}" (באמת: {truth}). '
        tail = {"right": f"ענו לו: {said}. זה נכון!", "other": f"ענו לו: {said}.", "nothing": f"ענו לו: {said}."}[outcome]
    else:
        said = {"right": f'"{rng.choice(["", "No! ", "no, "])}{truth}!"', "other": f'"{rng.choice(OTHER_SAID["en"])}"',
                "nothing": "nothing, just laughed"}[outcome]
        head = f'Puki said as a joke: "{silly_line}" (really: {truth}). '
        tail = {"right": f"The child answered: {said}. That's right!", "other": f"The child answered: {said}.",
                "nothing": f"The child answered: {said}."}[outcome]
    return outcome, head + tail


# ---------- The caring game ----------

# Who needs help: (id, he name, en name, Hebrew gender m/f, or None for Puki himself)
PATIENTS = [("puki", None, None, None), ("teddy", "דובי", "the teddy bear", "m"), ("doll", "הבובה", "the doll", "f"),
            ("bunny", "הארנב", "the bunny", "m")]
# What's wrong: (id, he m, he f, he for Puki, en, en for Puki, what helps); {X} is the patient
PROBLEMS = [
    ("cold", "{le} קר", "{le} קר", "לך קר", "{X} is cold", "you are cold", {"blanket", "hug", "soup", "hat"}),
    ("sneezy", "{X} מתעטש", "{X} מתעטשת", "אתה מתעטש", "{X} keeps sneezing", "you keep sneezing", {"tissue", "soup"}),
    ("bump", "{le} יש מכה ברגל", "{le} יש מכה ברגל", "לך יש מכה ברגל", "{X} has a bump on the leg",
     "you have a bump on your leg", {"bandage", "kiss", "hug"}),
    ("thunder", "{X} מפחד מהרעם", "{X} מפחדת מהרעם", "אתה מפחד מהרעם", "{X} is scared of the thunder",
     "you are scared of the thunder", {"hug", "song", "blanket", "teddy"}),
    ("dark", "{X} מפחד מהחושך", "{X} מפחדת מהחושך", "אתה מפחד מהחושך", "{X} is scared of the dark",
     "you are scared of the dark", {"nightlight", "hug", "teddy"}),
    ("thirsty", "{X} צמא", "{X} צמאה", "אתה צמא", "{X} is thirsty", "you are thirsty", {"water"}),
    ("hungry", "{X} רעב", "{X} רעבה", "אתה רעב", "{X} is hungry", "you are hungry", {"soup", "apple"}),
    ("sleepy", "{X} עייף", "{X} עייפה", "אתה עייף", "{X} is sleepy", "you are sleepy", {"blanket", "song", "pillow"}),
    ("play", "{X} רוצה לשחק", "{X} רוצה לשחק", "אתה רוצה לשחק", "{X} wants to play", "you want to play",
     {"ball", "song", "book"}),
    ("hot", "{le} חם מהשמש", "{le} חם מהשמש", "לך חם מהשמש", "{X} is hot from the sun", "you are hot from the sun",
     {"hat", "water"}),
    ("sad", "{X} עצוב", "{X} עצובה", "אתה עצוב", "{X} is sad", "you are sad", {"hug", "kiss", "song", "ball"}),
]
ITEMS = {"blanket": ("שמיכה", "a blanket"), "hug": ("חיבוק", "a hug"), "bandage": ("פלסטר", "a bandage"),
         "soup": ("מרק חם", "warm soup"), "hat": ("כובע", "a hat"), "tissue": ("טישו", "a tissue"),
         "kiss": ("נשיקה", "a kiss"), "song": ("שיר", "a song"), "teddy": ("דובי", "a teddy bear"),
         "nightlight": ("מנורת לילה", "a night light"), "water": ("מים", "water"), "apple": ("תפוח", "an apple"),
         "pillow": ("כרית", "a pillow"), "ball": ("כדור", "a ball"), "book": ("ספר", "a book")}


def care_inputs(rng):
    """[{he, en, outcome, group}]: for every patient and problem, the start, a gift that helps and one that doesn't."""
    out = []
    for pid, he_name, en_name, g in PATIENTS:
        for prob, he_m, he_f, he_you, en_3, en_you, helps in PROBLEMS:
            if he_name:
                problem_he = (he_f if g == "f" else he_m).format(X=he_name, le=_le(he_name))
                problem_en = en_3.format(X=en_name)
                gave_he = f"נתנו {_le(he_name)}"
                better_he = "עכשיו היא מרגישה טוב יותר" if g == "f" else "עכשיו הוא מרגיש טוב יותר"
                better_en = ("now she feels better" if g == "f" else "now he feels better")
                to_en = "her" if g == "f" else "him"
            else:  # Puki himself is the one who needs help
                problem_he, problem_en = he_you, en_you
                gave_he, better_he, better_en, to_en = "נתנו לך", "עכשיו אתה מרגיש טוב יותר", "now you feel better", "you"
            problem_en = problem_en[0].upper() + problem_en[1:]
            useful = sorted(helps - ({"teddy"} if pid == "teddy" else set()))
            useless = sorted(set(ITEMS) - helps)
            group = f"care:{pid}:{prob}"
            # Spelled out for the teacher (it otherwise drifted to another problem, or spoke as the doll)
            who = (f"Who needs help: {en_name}, not Puki (Puki talks about {to_en})" if he_name else
                   "Who needs help: Puki himself (he speaks of himself)") + f"; the problem: {en_3.format(X='').strip()}."
            out.append({"group": group, "outcome": "start", "who": who,
                        "he": f"משחק הטיפול: {problem_he}. עוד לא נתנו כלום.",
                        "en": f"Caring game: {problem_en}. Nothing given yet."})
            for outcome, item in (("helps", rng.choice(useful)), ("not_yet", rng.choice(useless))):
                ih, ie = ITEMS[item]
                if outcome == "helps":
                    he = f"משחק הטיפול: {problem_he}. {gave_he}: {ih}. זה עוזר, {better_he}."
                    en = f"Caring game: {problem_en}. The child gave {to_en}: {ie}. That helps, {better_en}."
                else:
                    he = f"משחק הטיפול: {problem_he}. {gave_he}: {ih}. זה נחמד, אבל זה עוד לא עוזר."
                    en = f"Caring game: {problem_en}. The child gave {to_en}: {ie}. That's nice, but it doesn't help yet."
                out.append({"group": group, "outcome": outcome, "who": who, "he": he, "en": en})
    rng.shuffle(out)
    return out


# ---------- The story with choices ----------

STORY_HEROES = ("cow", "dog", "cat", "elephant", "lion", "rabbit", "duck", "sheep", "monkey", "giraffe", "penguin", "horse")
PLACES = [("הים", "the sea"), ("היער", "the forest"), ("הגינה", "the garden"), ("החווה", "the farm"),
          ("הגן", "the playground"), ("ההר", "the hill")]
THINGS = [("בננה", "a banana"), ("תפוח", "an apple"), ("כדור", "a ball"), ("פרח", "a flower"), ("כוכב", "a star"),
          ("קשת", "a rainbow"), ("בלון", "a balloon"), ("עוגה", "a cake")]
FRIENDS = ("dog", "cat", "duck", "sheep", "monkey", "rabbit", "penguin", "horse", "elephant", "cow")


def story_plans(rng, n):
    """[n] stories to write: a hero, a place, a friend and a thing the child picked."""
    animals = {a["id"]: a for a in moments_mod.content()["animals"]}
    plans = []
    seen = set()
    while len(plans) < n:
        hero = animals[rng.choice(STORY_HEROES)]
        friend = animals[rng.choice([f for f in FRIENDS if f != hero["id"]])]
        place, thing = rng.choice(PLACES), rng.choice(THINGS)
        key = (hero["id"], place[1], friend["id"], thing[1])
        if key in seen:
            continue
        seen.add(key)
        plans.append({"group": "story:" + ":".join(key), "hero": hero, "place": place, "friend": friend, "thing": thing})
    return plans


def story_steps(plan, lang):
    """The five choices of a story, as (what the child picked, in [lang]): start, place, friend, thing, end."""
    h, f = plan["hero"], plan["friend"]
    if lang == "he":
        return ["הסיפור מתחיל", f"בחרו ללכת {_le(plan['place'][0])}", f"בחרו חבר: {f['he']}",
                f"בחרו: {plan['thing'][0]}", "הסוף, " + ("הגיבורה חוזרת" if h["she"] else "הגיבור חוזר") + " הביתה לישון"]
    return ["the story begins", f"the child picked going to {plan['place'][1]}", f"the child picked a friend: a {f['en'].lower()}",
            f"the child picked: {plan['thing'][1]}", "the end, the hero goes home to sleep"]


def story_hero(plan, lang):
    h = plan["hero"]
    if lang == "he":
        return ("הגיבורה: " if h["she"] else "הגיבור: ") + h["he"] + (" קטנה" if h["she"] else " קטן")
    return "Hero: a little " + h["en"].lower()


def story_input(plan, lang, so_far, step):
    """What the app sends for one step: the hero, the sentences so far and what was picked now."""
    steps = story_steps(plan, lang)
    if lang == "he":
        past = f" עד עכשיו: {' '.join(so_far)}" if so_far else ""
        return f"סיפור עם בחירות. {story_hero(plan, lang)}.{past} עכשיו: {steps[step]}."
    past = f" So far: {' '.join(so_far)}" if so_far else ""
    return f"Story with choices. {story_hero(plan, lang)}.{past} Now: {steps[step]}."


# ---------- The bedtime recap ----------

def day_events(rng):
    """One made-up day: 2-5 things the child did, as (he, en) pairs."""
    c = moments_mod.content()
    foods = {x["id"]: x for x in c["foods"]}

    def fed():
        a = rng.choice(c["animals"])
        return f"האכלת את {_the(a['he'])} ב{foods[a['food']]['he']}", f"you fed the {a['en'].lower()} some {foods[a['food']]['en'].lower()}"

    def found():
        a = rng.choice(c["animals"])
        return f"מצאת את {_the(a['he'])} במחבואים", f"you found the {a['en'].lower()} hiding"

    def color():
        x = rng.choice(c["colors"])
        return f"מצאת את הצבע {x['he']}", f"you found the color {x['en'].lower()}"

    def shape():
        x = rng.choice([s for s in c["shapes"] if s["id"] in PLAIN_SHAPES])
        return f"שמת את {_the(x['he'])} במקום", f"you put the {x['en'].lower()} in its place"

    def counted():
        r = rng.choice([r for r in c["counting"] if r["n"] > 1])
        return f"ספרת {r['n']} {r['he']}", f"you counted {r['n']} {r['en']}"

    def said():
        w = rng.choice(c["letters"])
        return f"אמרת עם פוקי: {w['word_he']}", f"you said \"{w['word_en'].lower()}\" with Puki"

    def drew():
        x = rng.choice(c["colors"])
        return rng.choice([("ציירת ציור", "you drew a picture"), (f"ציירת ב{x['he']}", f"you drew with {x['en'].lower()}")])

    def sticker():
        s = rng.choice(c["stickers"])
        return f"קיבלת מדבקה של {s['he']}", f"you got a {s['en'].lower()} sticker"

    fixed = [("רקדת למוזיקה", "you danced to the music"), ("אפית עוגה", "you baked a cake"),
             ("שטפת את המכונית", "you washed the car"), ("סידרת את הצעצועים", "you tidied up the toys"),
             ("צחצחת שיניים לדובי", "you brushed the teddy's teeth"), ("רחצת את הדובי באמבטיה", "you gave the teddy a bath"),
             ("ערכת את השולחן", "you set the table"), ("הלבשת את הדובי", "you dressed the teddy")]
    makers = [fed, found, color, shape, counted, said, drew, sticker, lambda: rng.choice(fixed)]
    events, used = [], set()
    while len(events) < rng.randint(2, 5):
        m = rng.choice(makers)
        e = m()
        if m in used or e in events:
            continue
        used.add(m)
        events.append(e)
    return events


def recap_inputs(rng, n):
    out = []
    for i in range(n):
        ev = day_events(rng)
        out.append({"group": f"recap:{i}", "he": "סיכום לפני השינה. היום: " + "; ".join(e[0] for e in ev) + ".",
                    "en": "Bedtime recap. Today: " + "; ".join(e[1] for e in ev) + "."})
    return out


# Words every event shares, which say nothing about which one a line names
_COMMON = {"את", "היום", "מצאת", "found", "עם", "פוקי", "במקום", "מקום", "הצבע", "צבע", "מדבקה", "של", "you", "the", "some", "with",
           "puki", "its", "place", "color", "sticker", "today", "said", "got"}


def _forms(word):
    """A word with up to two Hebrew prefix letters taken off (וציירת -> ציירת)."""
    out = {word}
    for k in (1, 2):
        if len(word) - k >= 3 and all(ch in "והבלמש" for ch in word[:k]):
            out.add(word[k:])
    return out


def events_named(line, recap_input):
    """How many of a recap input's events [line] names (by a word only that event has)."""
    events = recap_input.split(":", 1)[1].rstrip(".").split(";")
    seen = set().union(*(_forms(w) for w in re.findall(r"[\w']+", line.lower())))
    n = 0
    for e in events:
        words = {f for w in re.findall(r"[\w']+", e.lower()) if w not in _COMMON and len(w) >= 3 for f in _forms(w)}
        n += bool(words & seen)
    return n


if __name__ == "__main__":
    import sys
    sys.stdout.reconfigure(encoding="utf-8")
    rng = random.Random(1)
    s = silly_inputs(rng)
    print(f"silly: {len(s)}")
    for x in s[:4]:
        print(" ", x["he"], "|", x["en"])
    print(" ", silly_fix_input("he", "הפרה עושה מיאו!", "מו", rng)[1])
    print(" ", silly_fix_input("en", "The cow says meow!", "moo", rng)[1])
    cs = care_inputs(rng)
    print(f"care: {len(cs)}")
    for x in cs[:4]:
        print(" ", x["he"], "|", x["en"])
    p = story_plans(rng, 1)[0]
    print("story:", story_input(p, "he", ["היה פעם ארנב קטן."], 1), "|", story_input(p, "en", [], 0))
    for x in recap_inputs(rng, 2):
        print("recap:", x["he"], "|", x["en"])
