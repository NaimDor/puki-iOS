#!/usr/bin/env python3
"""The companion's brain: turns "what just happened in the game" into a short,
spoken, toddler-safe line. Started and stopped by doorbell.py; listens on
127.0.0.1 only.

POST /say {"situation": "...", "correct": true|false|null, "lang": "he"|"en"}
  -> 200 audio/ogg with header X-Text (URL-encoded line), or 204 if no safe line.

Needs the BlueTTS virtualenv (blue_onnx, numpy, soundfile) and a running
llama-server with Gemma (the doorbell starts both).
"""

import base64
import hmac
import io
import json
import math
import os
import random
import re
import sys
import threading
import time
import urllib.parse
import urllib.request
from collections import deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import numpy as np
import soundfile as sf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from doorbell import CFG, HERE, LLAMA_URL, log  # noqa: E402
import memory  # noqa: E402
import pronunciation  # noqa: E402
import persona  # noqa: E402
import safety  # noqa: E402
from persona import (  # noqa: E402
    set_child,
    child,
    capital,
    system_prompt,
    examples,
    clean,
    FOOD_WORDS,
    OPENERS,
    fix_misreads,
    hotwords,
    word_match,
    listen_situation,
)

persona.configure(CFG)

# One request at a time: Gemma, Whisper and BlueTTS run one job at a time anyway, and the
# child's name/gender (persona.set_child) are per request
REQUEST_LOCK = threading.Lock()

# The doorbell makes a token at each wake and gives it to this process and to llama-server:
# requests without it (another program, a web page in this PC's browser) are refused
TOKEN = os.environ.get("PUKI_BRAIN_TOKEN", "")
LLAMA_HEADERS = {"Content-Type": "application/json", **({"Authorization": f"Bearer {TOKEN}"} if TOKEN else {})}


# What Puki remembers from playing (memory.py); "memory": false in config.json turns it off
MEMORY = memory.Memory(HERE) if CFG.get("memory", True) else None


def remembered():
    """The memory block for the system prompt, or "" (memory off, or nothing yet)."""
    if not MEMORY:
        return ""
    try:
        return MEMORY.prompt_block(girl=persona.GIRL)
    except Exception as e:  # a bad file never stops Puki from talking
        log(f"memory unreadable ({type(e).__name__})")
        return ""


def note(kind, lang, **fields):
    if MEMORY:
        try:
            MEMORY.note(kind, lang, **fields)
        except OSError as e:
            log(f"memory not written ({type(e).__name__})")


def write_diary():
    """Gemma writes the diary entry for the play since the last one, then updates the profile
    page. Called by the doorbell before the brain goes to sleep. Returns what it wrote."""
    if not MEMORY:
        return None
    MEMORY.prune(CFG.get("memory_keep_days", 90))
    entries = MEMORY.unwritten()
    if len(entries) < 3:  # a moment of play: wait for more
        return None
    who = child("en")
    entry = chat([{"role": "user", "content": MEMORY.diary_prompt(entries, who, persona.GIRL)}],
                 max_tokens=220, temperature=0.3, timeout=120).strip()
    if len(entry) < 20:
        return None
    # Questions for the parent to ask later, in the language she played in
    lang = "en" if sum(e.get("lang") == "en" for e in entries) * 2 > len(entries) else "he"
    try:
        raw = chat([{"role": "user", "content": MEMORY.ask_prompt(entry, child(lang), lang, persona.GIRL)}],
                   max_tokens=160, temperature=0.5, timeout=120)
        ask = [l.strip()[2:].strip() for l in raw.splitlines() if l.strip().startswith("- ")]
        ask = [q for q in ask if safety.reply_ok(q) and q.endswith("?")][:3]
    except Exception as e:  # the diary matters more than the questions
        log(f"no questions for later ({type(e).__name__})")
        ask = []
    MEMORY.add_entry(entry, entries[-1]["t"], ask)
    page = chat([{"role": "user", "content": MEMORY.profile_prompt(entry, who, persona.GIRL)}],
                max_tokens=300, temperature=0.2, timeout=120)
    lines = [l.strip() for l in page.splitlines() if l.strip().startswith("- ")][:8]
    if lines:
        MEMORY.set_profile("\n".join(lines))
    return entry


# Small models repeat themselves, so each request suggests a different opening
# sound and lists the last few lines to avoid.
recent = deque(maxlen=6)


def ask_gemma(situation, lang, listening=False, avoid=None, remember=False, reach_back=False):
    kind = "food" if any(w in situation for w in FOOD_WORDS) else "other"
    # A suggested opening sound keeps game lines varied; not for answering the child, where a
    # forced "Hooray!" can land on "I fell down"
    before = MEMORY.related(situation, names=(persona.NAME, persona.COMPANION, persona.COMPANION_EN)) \
        if reach_back and MEMORY else None
    opener = None if listening or before else random.choice(OPENERS[lang][kind])
    hint = situation
    if lang == "he":
        if opener:
            hint += f"\n(תתחיל את התשובה ב-'{opener}!')"
        if avoid or recent:
            hint += "\n(אל תחזור על מה שאמרת קודם: " + " / ".join(avoid or recent) + ")"
    else:
        if opener:
            hint += f"\n(Start your reply with '{opener}!')"
        if avoid or recent:
            hint += "\n(Don't repeat what you said before: " + " / ".join(avoid or recent) + ")"
    memories = remembered() if remember or before else ""
    if before:
        # A game line that reaches back (memory_say_chance of them), when an earlier session had the
        # same thing. Picked here: the small model ignores a mere invitation, and left to choose it
        # links to unrelated memories and mixes them up
        (when_en, when_he), moment = before
        then = MEMORY.moment_text(moment, lang, persona.GIRL)
        hint += (f"\n(גם {when_he} היה רגע כזה. {when_he.capitalize()}: {then}. תזכיר את זה במילים ספורות, בלי להוסיף פרטים.)"
                 if lang == "he" else
                 f"\n(Something like this happened {when_en} too. {when_en.capitalize()}: {then}. Mention it in a "
                 "few words, adding no details.)")
    system = system_prompt(lang) + safety.system_extra(lang, persona.GIRL) + memories
    messages = [{"role": "system", "content": system}]
    for user, reply in examples(lang):
        messages += [{"role": "user", "content": user}, {"role": "assistant", "content": reply}]
    messages.append({"role": "user", "content": hint})
    return chat(messages)


def digit_probs(prompt):
    """The probability of each digit 0-3 as the model's first (greedy) token for [prompt]."""
    payload = {
        "messages": [{"role": "user", "content": prompt}],
        "max_tokens": 1,
        "temperature": 0,
        "logprobs": True,
        "top_logprobs": 20,
        "chat_template_kwargs": {"enable_thinking": False},
    }
    req = urllib.request.Request(f"{LLAMA_URL}/v1/chat/completions", data=json.dumps(payload).encode(),
                                 headers=LLAMA_HEADERS)
    with urllib.request.urlopen(req, timeout=20) as r:
        top = json.load(r)["choices"][0]["logprobs"]["content"][0]["top_logprobs"]
    probs = {}
    for t in top:
        d = t["token"].strip()
        if d in ("0", "1", "2", "3"):
            probs[d] = probs.get(d, 0.0) + math.exp(t["logprob"])
    return probs


def gate(heard):
    """The model's one-token read of what the child said (greedy): the category to answer with
    a fixed reply (see safety.py), or None when it's ordinary play. P(ordinary) comes from the
    first token's probabilities, so the threshold (safety_threshold) can be set as strict as wanted."""
    return safety.gate_category(digit_probs(safety.classify_prompt(heard)), CFG.get("safety_threshold", 0.8))


def pack_lines(situation, lang, n, avoid=()):
    """Up to [n] new lines for a game moment, for a pack a parent will approve: each passes the
    line filter, reply_ok and the model's own one-token check, and differs from [avoid] (lines
    already written for it) and from the others."""
    def norm(t):
        return re.sub(r"[^\w]", "", persona._plain(t)).lower()
    seen = {norm(t) for t in avoid}
    out = []
    for _ in range(n * 4):
        if len(out) >= n:
            break
        text = clean(ask_gemma(situation, lang, avoid=list(avoid)[-6:] + out), lang)
        if not text or not safety.reply_ok(text) or norm(text) in seen:
            continue
        try:
            if not safety.line_ok(digit_probs(safety.line_check_prompt(text)), CFG.get("pack_line_threshold", 0.9)):
                continue
        except Exception as e:  # no answer from the check: not in the pack
            log(f"line check failed ({type(e).__name__})")
            continue
        seen.add(norm(text))
        out.append(text)
    return out


def shown(text):
    """What the log shows of something the child said or Puki answered: its length, or the text
    itself with log_text on (for debugging). The log may outlive the evening (journald, a
    terminal), and the child's words and name don't belong in it."""
    if text is None:
        return "nothing"
    text = str(text)
    return repr(text) if CFG.get("log_text") else f"({len(text)} characters)"


def guarded_reply(heard, lang, game=""):
    """(category, fixed reply) when what the child said needs a careful answer, else None.
    [game]: the app's screen, for words that game uses itself (a knife in the kitchen)."""
    return safety.guarded_reply(heard, lang, persona.GIRL, game, gate=gate,
                                on_error=lambda e: log(f"safety gate failed ({type(e).__name__}); answering with the neutral line"))

def chat(messages, max_tokens=48, temperature=0.9, response_format=None, timeout=45):
    payload = {
        "messages": messages,
        "max_tokens": max_tokens,
        "temperature": temperature,
        "top_p": 0.95,
        "chat_template_kwargs": {"enable_thinking": False},
    }
    if response_format:
        payload["response_format"] = response_format
    req = urllib.request.Request(f"{LLAMA_URL}/v1/chat/completions", data=json.dumps(payload).encode(),
                                 headers=LLAMA_HEADERS)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.load(r)["choices"][0]["message"]["content"]


# ---------- Looking at the child's drawing (Gemma vision) ----------

def look_line(png_bytes, lang):
    """A warm one-liner about a toddler's drawing, or None."""
    if lang == "en":
        ask = (f"{capital(child(lang))} just drew this picture (a 2-year-old's drawing, probably scribbles). "
               "In one or two short English sentences, say what you see: colors, lines, shapes, maybe a playful "
               "guess at what it is. Be delighted. Never say it doesn't look like something.")
    else:
        ask = (f"{persona.CHILD} {'ציירה' if persona.GIRL else 'צייר'} עכשיו את הציור הזה (ציור של פעוטה בת שנתיים, כנראה קשקושים). "
               "תגיב במשפט אחד או שניים קצרים: תגיד מה אתה רואה (צבעים, קווים, צורות, אולי ניחוש שובב מה זה) "
               "בהתלהבות. לעולם אל תגיד שזה לא דומה למשהו.")
    image = "data:image/png;base64," + base64.b64encode(png_bytes).decode()
    messages = [
        {"role": "system", "content": system_prompt(lang)},
        {"role": "user", "content": [{"type": "text", "text": ask}, {"type": "image_url", "image_url": {"url": image}}]},
    ]
    for _ in range(3):
        raw = chat(messages, max_tokens=60, temperature=0.8, timeout=60)
        text = clean(raw, lang)
        if text and safety.reply_ok(text):
            return text
        log(f"rejected look: {shown(raw)}")
    return None


# ---------- Bedtime story ----------

STORY_SCHEMA = {
    "type": "object",
    "properties": {"sentences": {"type": "array", "minItems": 4, "maxItems": 7, "items": {
        "type": "object",
        "properties": {"text": {"type": "string"}, "icon": {"type": "string"}},
        "required": ["text", "icon"],
    }}},
    "required": ["sentences"],
}


def story(played, icons, lang):
    """A short calm bedtime story: [{"text", "icon"}], each icon one of [icons] (name -> label)."""
    names = "\n".join(f"- {k}: {v}" for k, v in icons.items())
    if lang == "en":
        c = child(lang)
        ask = (f"Tell {c} a very short, calm bedtime story in simple English (5-6 sentences, each at most "
               f"10 simple words). {capital(c)} is the hero. Today {c} played with: {played}. Use one or two "
               "of those things. Slow, warm and sleepy, ending with everyone falling asleep. No questions. "
               "For each sentence, choose the picture (icon) that fits it best from this list:\n" + names +
               "\nAnswer as JSON.")
        closing = f"Good night{', ' + persona.NAME if persona.NAME else ''}."
    else:
        ask = (f"ספר ל{persona.CHILD} סיפור לפני השינה קצר ורגוע מאוד (5-6 משפטים, כל משפט עד 10 מילים פשוטות). "
               f"{persona.CHILD} {'היא הגיבורה' if persona.GIRL else 'הוא הגיבור'}. היום {persona.CHILD} {'שיחקה' if persona.GIRL else 'שיחק'} עם: {played}. "
               "תשתמש בדבר אחד או שניים מהם. איטי, חם ומנומנם, ובסוף כולם נרדמים. בלי שאלות. "
               "לכל משפט בחר את התמונה (icon) שהכי מתאימה לו מהרשימה הזאת:\n" + names + "\nתענה ב-JSON.")
        closing = f"לילה טוב{', ' + persona.NAME if persona.NAME else ''}."
    schema = json.loads(json.dumps(STORY_SCHEMA))
    schema["properties"]["sentences"]["items"]["properties"]["icon"]["enum"] = list(icons)
    # The bedtime story may reach back to what she did this week
    messages = [{"role": "system", "content": system_prompt(lang) + remembered()}, {"role": "user", "content": ask}]
    for _ in range(2):
        raw = chat(messages, max_tokens=400, temperature=0.8, timeout=90,
                   response_format={"type": "json_schema", "json_schema": {"schema": schema}})
        try:
            items = json.loads(raw)["sentences"]
        except (ValueError, KeyError, TypeError):
            log(f"story not JSON: {shown(raw)}")
            continue
        out = []
        for it in items:
            text = clean(str(it.get("text", "")), lang)
            if text and safety.reply_ok(text) and it.get("icon") in icons:
                out.append({"text": text, "icon": it["icon"]})
        if len(out) >= 3:
            out.append({"text": closing, "icon": "ic_bedtime_moon" if "ic_bedtime_moon" in icons else out[-1]["icon"]})
            return out
        log(f"story rejected: {shown(raw)}")
    return None


def line_for(situation, lang, listening=False, remember=False, reach_back=False):
    for _ in range(3):
        raw = ask_gemma(situation, lang, listening, remember=remember, reach_back=reach_back)
        text = clean(raw, lang)
        if text and not safety.reply_ok(text):
            text = None
        if text:
            recent.append(text)
            return text
        log(f"rejected: {shown(raw)}")
    return None


log("loading BlueTTS")
from blue_onnx import BlueTTS  # noqa: E402

_bt = CFG["bluetts_dir"]
TTS = BlueTTS(
    onnx_dir=os.path.join(_bt, "onnx_models"),
    style_json=os.path.join(_bt, "voices", f"{CFG['voice']}.json"),
    config_path=os.path.join(_bt, "config", "tts.json"),
    renikud_path=CFG["renikud_model"],
)


# A run of English words, e.g. "dog" or "good night" inside a Hebrew line
ENGLISH_RUN = re.compile(r"[A-Za-z]+(?:[\s'’,-]+[A-Za-z]+)*")


def tag_english(text):
    """BlueTTS reads English inside a Hebrew line as gibberish unless it's wrapped in <en>…</en>."""
    if "<en>" in text:
        return text
    return ENGLISH_RUN.sub(lambda m: f"<en>{m.group(0)}</en>", text)


# A run of Hebrew words, e.g. the child's name typed in Hebrew inside an English line
HEBREW_RUN = re.compile(r"[\u05D0-\u05EA]+(?:[\s'\-]+[\u05D0-\u05EA]+)*")


def tag_hebrew(text):
    """The same the other way round: Hebrew inside an English line goes in <he>…</he>."""
    if "<he>" in text:
        return text
    return HEBREW_RUN.sub(lambda m: f"<he>{m.group(0)}</he>", text)


def speak(text, lang, speed=0.92):
    # BlueTTS mispronounces text that already has nikud (e.g. a name with nikud came out garbled)
    text = re.sub(r"[\u0591-\u05C7]", "", text)
    text = fix_misreads(text)
    text = tag_english(text) if lang == "he" else tag_hebrew(text)
    # Words RenikudPlus says wrong ("אופס" is oops; Puki, not Poki): their phonemes from Puki's table
    ph = pronunciation.phonemes(text, lang, persona.GIRL, TTS.tts.g2p._phonemize_segment)
    if ph:
        samples, sr = TTS.synthesize(ph, lang=lang, total_step=8, speed=speed, text_is_phonemes=True, normalize_text=False)
    else:
        samples, sr = TTS.synthesize(text, lang=lang, total_step=8, speed=speed)
    samples = np.concatenate([samples, np.zeros(int(sr * 0.15), dtype=np.float32)])
    buf = io.BytesIO()
    sf.write(buf, samples, sr, format="OGG", subtype="VORBIS")
    return buf.getvalue()


# ---------- Speech to text (ivrit.ai Whisper for Hebrew, Parakeet for English) ----------

def _add_cuda_dlls():
    """Let CTranslate2 find the cuBLAS / cuDNN DLLs installed as nvidia-* pip packages.
    (Windows only; on Linux the doorbell puts them in LD_LIBRARY_PATH before the brain starts.)"""
    if os.name != "nt":
        return
    base = os.path.join(sys.prefix, "Lib", "site-packages", "nvidia")
    if not os.path.isdir(base):
        return
    for pkg in os.listdir(base):
        bin_dir = os.path.join(base, pkg, "bin")
        if os.path.isdir(bin_dir):
            os.add_dll_directory(bin_dir)
            os.environ["PATH"] = bin_dir + os.pathsep + os.environ["PATH"]


def _wait_for_gemma(timeout_s=180):
    """Until llama-server says Gemma is loaded (it starts at the same moment as this), so the
    free GPU memory measured next is what's really left for Whisper."""
    end = time.time() + timeout_s
    while time.time() < end:
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{CFG['llama_port']}/health", timeout=2) as r:
                if r.status == 200:
                    return
        except Exception:
            pass
        time.sleep(0.5)


def _load_whisper():
    from faster_whisper import WhisperModel
    # GPU when there's room next to Gemma (and the training runs), CPU otherwise: measured once
    # Gemma has taken its share, or Whisper would land on the CPU (or overfill the GPU) by chance
    from doorbell import free_vram_mb
    _wait_for_gemma()
    if free_vram_mb() >= CFG["whisper_min_free_vram_mb"]:
        try:
            _add_cuda_dlls()
            model = WhisperModel(CFG["whisper_model"], device="cuda", compute_type="float16")
            log("whisper on GPU")
            return model
        except Exception as e:
            log(f"whisper GPU failed ({e}), using CPU")
    log("whisper on CPU")
    return WhisperModel(CFG["whisper_model"], device="cpu", compute_type="int8")


log("loading Whisper")
WHISPER = _load_whisper()
# A first, silent pass: the first time she talks shouldn't also pay for CUDA's start-up
try:
    list(WHISPER.transcribe(np.zeros(16000, np.float32), language="he", vad_filter=False)[0])
except Exception as e:
    log(f"whisper warm-up failed ({type(e).__name__})")

# English: NVIDIA's Parakeet (int8 ONNX) on the CPU, so it needs no VRAM next to Gemma.
# Loaded in the background on the first English request.
from parakeet import ParakeetOnnxEngine  # noqa: E402

PARAKEET = ParakeetOnnxEngine(CFG.get("parakeet_model") or "")


def has_parakeet():
    return os.path.isfile(os.path.join(PARAKEET.model_dir, "encoder-model.int8.onnx"))


def preload_parakeet():
    if PARAKEET.loaded:
        return
    if not has_parakeet():
        log(f"no Parakeet at {CFG.get('parakeet_model')!r} (parakeet_model): English uses the Hebrew Whisper")
        return
    threading.Thread(target=PARAKEET.warmup, daemon=True).start()


# Whisper "hears" these in silence or noise; treat them as nothing heard
HALLUCINATIONS = ("כתוביות", "תודה רבה", "תודה שצפיתם", "subtitles", "thank you for watching",
                  "thanks for watching", "please subscribe", "amara.org")
# ...and, in English, these when they're all it heard
HALLUCINATIONS_ALONE = ("you", "thank you", "thanks", "bye")

def transcribe(wav_bytes, lang, hints=None, whisper=False):
    """[hints]: words to expect instead of the usual ones (Whisper only). [whisper]: use Whisper
    even for English (Parakeet is weak on a single word said alone)."""
    # Decode the WAV ourselves (Whisper wants 16 kHz mono float samples)
    audio, sr = sf.read(io.BytesIO(wav_bytes), dtype="float32", always_2d=True)
    audio = audio.mean(axis=1)
    if sr != 16000:
        import librosa
        audio = librosa.resample(audio, orig_sr=sr, target_sr=16000)
    if lang == "en" and has_parakeet() and not whisper:
        heard = parakeet_transcribe(audio)
    else:
        segments, _ = WHISPER.transcribe(
            audio, language=lang, beam_size=1, vad_filter=True, hotwords=(hints or hotwords()) if lang == "he" else hints,
            condition_on_previous_text=False,
        )
        heard = " ".join(seg.text.strip() for seg in segments).strip()
    if any(h in heard.lower() for h in HALLUCINATIONS):
        return ""
    if lang == "en" and heard.lower().strip(" .!?,") in HALLUCINATIONS_ALONE:
        return ""
    return heard


def parakeet_transcribe(audio):
    """Parakeet has no voice detection of its own, so cut the recording to where someone talks."""
    from faster_whisper.vad import get_speech_timestamps
    speech = get_speech_timestamps(audio)
    if not speech:
        return ""
    pad = 16000 // 5
    return PARAKEET.transcribe(audio[max(0, speech[0]["start"] - pad):speech[-1]["end"] + pad], 16000).strip()


# ---------- Say it with Puki: did the child say the word? ----------

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def do_GET(self):
        self.send_response(200 if self.path == "/health" else 404)
        self.end_headers()

    def _late(self):
        """True once the app has stopped waiting for this request (the doorbell passes on when)."""
        try:
            return time.time() > float(self.headers.get("X-Puki-Deadline", ""))
        except ValueError:
            return False

    def _reply(self, text, lang, heard=None, speed=0.92):
        if not text:
            self.send_response(204)
            self.end_headers()
            return
        if self._late():  # nobody's listening any more: don't spend the voice on it
            return self._fail(503)
        audio = speak(text, lang, speed)
        self.send_response(200)
        self.send_header("Content-Type", "audio/ogg")
        self.send_header("X-Text", urllib.parse.quote(text))
        if heard is not None:
            self.send_header("X-Heard", urllib.parse.quote(heard))
        self.send_header("Content-Length", str(len(audio)))
        self.end_headers()
        self.wfile.write(audio)

    def _line(self, situation, lang, listening=False, remember=False, reach_back=False):
        try:
            return line_for(situation, lang, listening, remember, reach_back)
        except Exception as e:
            log(f"gemma failed ({type(e).__name__})")
            return None

    def _json(self, obj):
        body = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _fail(self, code):
        self.send_response(code)
        self.end_headers()

    def do_POST(self):
        if TOKEN and not hmac.compare_digest(self.headers.get("X-Puki-Brain-Token", ""), TOKEN):
            return self._fail(403)
        # The app sends the child's name and gender with every request (?name=...&gender=girl)
        query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        with REQUEST_LOCK:
            if self._late():  # waited behind another request until the app gave up on it
                return self._fail(503)
            set_child(query.get("name", [""])[0], query.get("gender", [""])[0])
            self._post()

    def _post(self):
        url = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(url.query)
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        try:
            if url.path in ("/say", "/speak", "/story", "/pack/make"):
                req = json.loads(body or b"{}")
                lang = "en" if req.get("lang") == "en" else "he"
            else:
                lang = "en" if query.get("lang") == ["en"] else "he"
        except ValueError:
            return self._fail(400)
        if lang == "en" and url.path != "/speak":  # /speak en also comes from Hebrew tablets (name lines)
            preload_parakeet()

        if url.path == "/say":
            situation = str(req.get("situation", ""))[:300]
            # Now and then a game line may reach back to a memory; most stay about the moment
            text = self._line(situation, lang, reach_back=random.random() < CFG.get("memory_say_chance", 0.3))
            log(f"{shown(situation)} -> {shown(text)}")
            note("say", lang, game=query.get("game", [""])[0][:40], situation=situation, said=text or "")
            self._reply(text, lang)
        elif url.path == "/speak":
            # Fixed text from the app (a question, a story sentence): just say it
            text = str(req.get("text", ""))[:200].strip()
            try:
                speed = float(req.get("speed", 0.92))
            except (TypeError, ValueError):
                speed = 0.92
            speed = min(max(speed, 0.5), 1.5) if math.isfinite(speed) else 0.92  # 0, NaN or 1e9 would break TTS
            self._reply(text, lang, speed=speed) if text else self._fail(204)
        elif url.path == "/listen":
            # Body: a short WAV recording of the child; optional context=what Puki just asked
            try:
                heard = transcribe(body, lang)
            except Exception as e:
                log(f"whisper failed ({type(e).__name__})")
                return self._fail(400)
            # Hurt, danger, big questions and the like get a fixed, kind reply (safety.py)
            caught = guarded_reply(heard, lang, query.get("game", [""])[0][:40])
            if caught:
                log(f"heard {shown(heard)} -> [{caught[0]}] fixed reply")
                return self._reply(caught[1], lang, heard)
            situation = listen_situation(heard, lang)
            context = query.get("context", [""])[0][:200]
            if context:
                situation = context + " " + situation
            text = self._line(situation, lang, listening=True, remember=True)
            log(f"heard {shown(heard)} -> {shown(text)}")
            if heard:  # (a careful answer above is never kept)
                note("listen", lang, game=query.get("game", [""])[0][:40], situation=context, heard=heard, said=text or "")
            self._reply(text, lang, heard)
        elif url.path == "/repeat":
            # Say it with Puki. Body: a WAV of the child trying to say ?target= (with ?alt= for
            # related words like the animal's sound). Just speech recognition, no Gemma.
            target = query.get("target", [""])[0][:40]
            alts = [a[:40] for a in query.get("alt", []) if a][:4]
            try:
                heard = transcribe(body, lang, hints=" ".join([target] + alts))
            except Exception as e:
                log(f"whisper failed ({type(e).__name__})")
                return self._fail(400)
            match = word_match(heard, target, alts)
            if lang == "en" and match != "word" and has_parakeet():
                # Parakeet often misses one short word on its own; ask Whisper too, keep the better
                try:
                    heard2 = transcribe(body, lang, hints=" ".join([target] + alts), whisper=True)
                except Exception as e:
                    log(f"whisper failed ({type(e).__name__})")
                    heard2 = ""
                match2 = word_match(heard2, target, alts)
                if heard2 and ["tried", "close", "word"].index(match2) > ["tried", "close", "word"].index(match):
                    heard, match = heard2, match2
            log(f"say {shown(target)}: heard {shown(heard)} -> {match}")
            if heard:
                # Only the word and whether she said it: what Whisper heard otherwise may be noise,
                # misheard into anything ("...the blood"), and mustn't reach the diary
                note("repeat", lang, situation=target, heard=target if match == "word" else "")
            self._json({"heard": heard, "match": match})
        elif url.path == "/hear":
            # A game asked a question and the child answered out loud. Body: a WAV; ?hint= are the
            # answers on screen (Whisper expects them). Only the words go back: the tablet decides
            # which answer that was. Whisper even for English, as Parakeet misses one word alone
            hints = [h[:40] for h in query.get("hint", []) if h][:16]
            try:
                heard = transcribe(body, lang, hints=" ".join(hints), whisper=True)
            except Exception as e:
                log(f"whisper failed ({type(e).__name__})")
                return self._fail(400)
            log(f"answer out loud: heard {shown(heard)}")
            # Not kept in memory: the brain can't tell an answer from noise, and noise misheard
            # ("...the blood") mustn't reach the diary; the game moment itself is kept (/say)
            self._json({"heard": heard})
        elif url.path == "/look":
            # Body: PNG of the child's drawing
            try:
                text = look_line(body, lang)
            except Exception as e:
                log(f"look failed ({type(e).__name__})")
                text = None
            log(f"looked at a drawing -> {shown(text)}")
            if text:
                note("look", lang, said=text)
            self._reply(text, lang)
        elif url.path == "/story":
            # The pictures the story may use (the app sends about 20); capped, it all goes into the prompt
            icons = {str(k)[:60]: str(v)[:60] for k, v in list(dict(req.get("icons", {})).items())[:40]}
            played = str(req.get("played", ""))[:300] or ("משחקים" if lang == "he" else "games")
            try:
                sentences = story(played, icons, lang) if icons else None
            except Exception as e:
                log(f"story failed ({type(e).__name__})")
                sentences = None
            log(f"story about {shown(played)} -> {len(sentences or [])} sentences")
            if sentences:
                note("story", lang, situation=played)
            self._json({"sentences": sentences}) if sentences else self._fail(204)
        elif url.path == "/pack/make":
            # Night work for the doorbell: new lines for one game moment, voiced, for a parent to approve
            situation = str(req.get("situation", ""))[:300]
            n = max(1, min(int(req.get("n", 3)), 5))
            avoid = [str(t)[:200] for t in list(req.get("avoid", []))[:20]]
            try:
                lines = pack_lines(situation, lang, n, avoid)
            except Exception as e:
                log(f"pack lines failed ({type(e).__name__})")
                lines = []
            out = [{"text": t, "audio": base64.b64encode(speak(t, lang)).decode()} for t in lines]
            log(f"pack: {shown(situation)} -> {len(out)} lines")
            self._json({"lines": out})
        elif url.path == "/memory/diary":
            # From the doorbell, before the brain sleeps: the diary entry for this session
            try:
                entry = write_diary()
            except Exception as e:
                log(f"diary not written ({type(e).__name__})")
                entry = None
            log(f"diary: {shown(entry)}")
            self._json({"entry": entry})
        else:
            self._fail(404)


if __name__ == "__main__":
    speak("שלום", "he")  # loads the Hebrew pronunciation model before the first real request
    if has_parakeet():
        preload_parakeet()  # English speech recognition, in the background
    log(f"brain listening on 127.0.0.1:{CFG['brain_port']}")
    ThreadingHTTPServer(("127.0.0.1", CFG["brain_port"]), Handler).serve_forever()
