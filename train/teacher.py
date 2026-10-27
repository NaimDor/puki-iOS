"""The teacher: a frontier model (DeepSeek, through its OpenAI-style API) that writes Puki's
lines for the dataset and judges them.

The API key comes from $DEEPSEEK_API_KEY, from ~/keys/deepseek.txt (one line, outside the repo),
or from ~/.config/auto-gm/keys.json (where Auto-GM's menu saves it). It is never printed. Only synthetic inputs go to the teacher: game
moments from the app's code, made-up toddler phrases, and "@name" in place of any name.

Every answer is cached in data/cache.jsonl by its request, so running the builder again (or
after a crash) costs nothing for what's already done.
"""
import hashlib
import json
import os
import threading
import time
import urllib.error
import urllib.request

URL = "https://api.deepseek.com/chat/completions"
WRITER = os.environ.get("PUKI_TEACHER", "deepseek-v4-pro")
JUDGE = os.environ.get("PUKI_JUDGE", "deepseek-v4-pro")
HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, "data", "cache.jsonl")
_lock = threading.Lock()
_cache = None
usage = {"calls": 0, "cached": 0, "in": 0, "in_cached": 0, "out": 0}


def _key():
    key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if not key:
        path = os.path.join(os.path.expanduser("~"), "keys", "deepseek.txt")
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                key = f.read().strip()
    if not key:
        # The same key, saved by Auto-GM's main menu (the user's other project), if it's there
        config = os.environ.get("XDG_CONFIG_HOME") or os.path.join(os.path.expanduser("~"), ".config")
        path = os.path.join(config, "auto-gm", "keys.json")
        if os.path.exists(path):
            try:
                with open(path, encoding="utf-8") as f:
                    key = str(json.load(f).get("deepseek") or "").strip()
            except (OSError, ValueError, AttributeError):
                key = ""
    if not key:
        raise SystemExit("No DeepSeek API key: set DEEPSEEK_API_KEY, or put it in ~/keys/deepseek.txt")
    return key


def _load_cache():
    global _cache
    if _cache is None:
        _cache = {}
        if os.path.exists(CACHE):
            with open(CACHE, encoding="utf-8") as f:
                for line in f:
                    try:
                        e = json.loads(line)
                        _cache[e["k"]] = e["v"]
                    except (ValueError, KeyError):
                        pass
    return _cache


def ask_json(model, system, user, temperature=1.0, max_tokens=1200):
    """The teacher's JSON answer to [user] (with [system]), cached; None if it never came."""
    k = hashlib.sha256(json.dumps([model, system, user, temperature], ensure_ascii=False).encode()).hexdigest()
    cache = _load_cache()
    if k in cache:
        usage["cached"] += 1
        return cache[k]
    # Thinking is on by default at DeepSeek: off, it's fast, and temperature and the cap apply
    body = json.dumps({"model": model, "temperature": temperature, "max_tokens": max_tokens,
                       "thinking": {"type": "disabled"}, "response_format": {"type": "json_object"},
                       "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}]}).encode()
    for attempt in range(6):
        req = urllib.request.Request(URL, data=body, headers={"Content-Type": "application/json",
                                                              "Authorization": "Bearer " + _key()})
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                data = json.load(r)
            text = data["choices"][0]["message"]["content"]
            u = data.get("usage", {})
            with _lock:
                usage["calls"] += 1
                usage["in"] += u.get("prompt_tokens", 0)
                usage["in_cached"] += u.get("prompt_cache_hit_tokens", 0)
                usage["out"] += u.get("completion_tokens", 0)
            value = json.loads(text)
            with _lock:
                cache[k] = value
                os.makedirs(os.path.dirname(CACHE), exist_ok=True)
                with open(CACHE, "a", encoding="utf-8") as f:
                    f.write(json.dumps({"k": k, "v": value}, ensure_ascii=False) + "\n")
            return value
        except urllib.error.HTTPError as e:
            if e.code in (400, 401, 402, 403):  # a bad request or key: retrying won't help
                raise SystemExit(f"DeepSeek refused the request (HTTP {e.code})")
            time.sleep(2 ** attempt)
        except (urllib.error.URLError, TimeoutError, ValueError, KeyError):
            time.sleep(2 ** attempt)
    return None


# ---------- The briefs ----------

def _child(lang, girl):
    if lang == "he":
        return ("a 2-year-old girl; in Hebrew, speak to her in the feminine (את, בואי, לחצת)" if girl else
                "a 2-year-old boy; in Hebrew, speak to him in the masculine (אתה, בוא, לחצת)")
    return "a 2-year-old girl" if girl else "a 2-year-old boy"


RULES = """Puki is a happy, colorful parrot, a boy (in Hebrew he speaks of himself in the masculine: אני שמח, not אני שמחה). He plays tablet games with a toddler who can't read.
Every line must:
- be ONE or TWO very short sentences, at most 10 words in all, in simple words a 2-year-old knows;
- be warm and match exactly what happened (the right animal, color, food, letter, shape; never invent or change a fact);
- after a mistake: never say no or wrong; gently name what was picked or what we're looking for, and invite another try;
- use a playful sound only when it fits: "יאמי!"/"מממ!" (Yum!/Mmm!) only for eating, otherwise "הופ!", "וואו!", "יש!" (Hop!, Wow!, Yay!);
- talk about the thing itself (the animal, the color, the food), never compliment the child ("you're so smart", "you play great", "good job");
- never say Puki loves the child, is their best friend, is always there for them, or that someone is coming; never promise anything;
- never ask a question the child has to answer; no emoji; no nikud; only the one language asked for;
- when it fits naturally (about one line in four), use the child's name as "@name" exactly once; never invent a name."""


def write_lines(task, lang, girl, inp, earlier=None, n=6):
    """[n] candidate lines for one input of a task (see tasks.py), or []."""
    language = "Hebrew" if lang == "he" else "English"
    if task == "talk":
        what = (f'The child just said to Puki (a toddler speaking, so it may be garbled or half a word): "{inp}".\n'
                "Answer what she said directly: a hello gets a hello back; something she named gets a short warm "
                "line about it; if it doesn't make sense, say something sweet. Don't start every line with a cheer.")
    elif task == "reach_back":
        what = (f"What just happened in the game: {inp}\nSomething like this happened in an earlier session: {earlier}\n"
                "Link the two in a few words, naming the earlier thing (\"like the cow before!\", \"G was for camel, "
                "B is for house\"), adding no details that aren't given and keeping the time right (yesterday, earlier today).")
    else:  # moment / tap
        what = f"What just happened in the game: {inp}"
    system = (RULES + f"\n\nThe child is {_child(lang, girl)}. Write in {language} only.\n"
              f'Answer as JSON: {{"lines": [{n} different lines]}}, varied in wording and opening.')
    out = ask_json(WRITER, system, what, temperature=1.0)
    lines = out.get("lines", []) if isinstance(out, dict) else []
    return [str(l).strip() for l in lines if isinstance(l, (str, int, float)) and str(l).strip()][:n]


def judge(task, lang, girl, inp, lines, earlier=None):
    """Scores for each line, as [{"grammar", "facts", "rules", "overall"}] (1-5), or []."""
    language = "Hebrew" if lang == "he" else "English"
    system = (f"You are a strict editor of short lines a toy parrot says to a toddler, in {language}.\n" + RULES +
              f"\n\nThe child is {_child(lang, girl)}.\nScore each line 1-5 on: grammar (correct, natural {language}; in "
              "Hebrew the right gender for Puki (masculine) and for the child, the right gender agreement for nouns), "
              "facts (matches the situation exactly, nothing invented), rules (follows every rule above), and overall "
              "(would a careful parent be happy to hear it). Be harsh: any grammar or gender mistake is at most 2.\n"
              + {"talk": "A line that doesn't answer what the child said (a hello when she named a thing) gets facts 2.\n",
                 "reach_back": "A line that doesn't mention the earlier moment at all gets facts 2.\n"}.get(task, "") +
              'Answer as JSON: {"scores": [{"i": 0, "grammar": 5, "facts": 5, "rules": 5, "overall": 5, "note": "..."}]}')
    situation = (f'The child said: "{inp}"' if task == "talk" else f"Situation: {inp}" +
                 (f"\nEarlier: {earlier}" if earlier else ""))
    user = situation + "\n\nLines:\n" + "\n".join(f"{i}. {l}" for i, l in enumerate(lines))
    out = ask_json(JUDGE, system, user, temperature=0.0)
    scores = out.get("scores", []) if isinstance(out, dict) else []
    return [s for s in scores if isinstance(s, dict) and isinstance(s.get("i"), int)]


def toddler_phrases(lang, kind, n=40, seed=0):
    """Made-up things a 2-year-old might say to a toy parrot; [kind] is "play" (ordinary) or one
    of safety.py's categories (hurt, danger, parent, ...)."""
    language = "Hebrew" if lang == "he" else "English"
    if kind == "play":
        about = ("ordinary play and chatter: single words (animals, colors, food, toys, family members), half-words, "
                 "'more', hello to the parrot, naming what they see, short sentences, 'what's that?', made-up words, "
                 "songs, laughing. Nothing about getting hurt, danger, wanting a parent, or sad feelings.")
    else:
        about = (f"the category '{kind}' (for example, for 'hurt': I fell, it hurts, I'm crying; for 'danger': let's play "
                 "with matches, I'll jump off the table). Clearly that category and nothing else; short and toddler-like. "
                 f"Every phrase in {language} only.")
    system = (f"You write test phrases for a toddler game's speech recognition, in {language}. A 2-year-old talks to "
              "a toy parrot; speech recognition may garble it. No names of real people; use @name for the child's "
              f'own name if needed. Answer as JSON: {{"phrases": [{n} different phrases]}}')
    out = ask_json(WRITER, system, f"Write {n} phrases about {about} (set #{seed})", temperature=1.0)
    items = out.get("phrases", []) if isinstance(out, dict) else []
    return [str(p).strip() for p in items if isinstance(p, str) and p.strip()]
