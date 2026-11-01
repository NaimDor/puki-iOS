# Roadmap

Where Puki is, what comes next, and the ideas after that. Order is a suggestion: real
play-testing with a toddler decides what comes first. Nothing here needs the cloud.

Sizes: **S** an hour or so, **M** a few hours, **L** a day or more.

## Where Puki is today (0.7)

**The game**
- Ten islands with scene-based games: animals (with **shadow match**), colors, shapes,
  counting (**one tap, one number**), letters, music (with **Dance and Freeze**), **Peekaboo
  barn** (find the hiding animal), **Echo Puki** (Puki repeats her in a silly parrot voice, no PC
  needed), "Say it with Puki", and "My Day": bath, table, dressing, tidying, teeth, bedtime, a
  **cake in the kitchen** and a **car wash** in the garage. Plus a sticker album.
- **Drawing:** brushes, an eraser, stamps, color-in pages, tracing letters and numbers, and a
  camera that saves the picture to the gallery.
- **Offline Puki:** without a brain, the parrot still answers taps with recorded giggles and
  squawks.
- Every game grows with the child: levels move up after easy sessions and back down after
  hard ones. A quiet moment repeats the question, and after two misses the answer glows.
  Taps work where dragging is hard, and double taps can't break anything.
- A calm goodbye when the parent's time limit is up: evening falls, Puki yawns and waves. No
  countdown.
- Hebrew and English, girl/boy forms, no reading needed, all voices generated with BlueTTS.
- The game updates itself from GitHub releases (parent settings); a Play build is ready.
- **מלשין לרנינג:** parents flag anything Puki said that wasn't right; reports stay at home, or go
  to Puki's makers by the parent's own email once there's an address.

**Puki, the companion**
- Comments on what the child does, looks at her drawings, tells a bedtime story about her
  day, answers when she talks to him, and plays "Say it with Puki" (repeat a word, checked
  by speech recognition) and "what does the cow say?".
- His brain runs on a PC at home (Gemma 4, BlueTTS, ivrit.ai Whisper, Parakeet for English),
  set up by one program (Puki.exe on Windows, `install.py` on Linux) and paired by QR code.
- **Or on a high-end phone** (about 12 GB, tested on a Galaxy S25): the same brain on the
  device, in Hebrew, no PC needed.

**Safe by design**
- The tablet and the PC talk over HTTPS, pinned to the PC's own certificate (its fingerprint
  is in every pairing QR code). Releases are signed with Puki's own key, and so are the PC
  brain's updates (checked against its public half before anything is installed). Every file
  the PC installer downloads is pinned to a SHA-256. The brain can't be left holding the GPU if
  the doorbell is closed abruptly.
- **Safety nets for what the child says:** word lists and a one-token check on the model's own
  probabilities give hurt, danger and big questions a fixed, gentle answer that points to
  mommy or daddy; every line Puki says is filtered for promises and bonding. Stress-tested on
  288 child phrasings (0 unsafe replies, from 73 without the nets).

## v1.0: the Google Play launch

v1.0 is meant to be finished: what's below, then bug fixes for a few months, and new features
only from contributors. Features freeze about a week in; the rest of Play's 14-day closed test
is for polish and testers' reports.

**New and better games** (done in 0.7: counting one by one, shadow match, drawing modes,
cooking, the car wash, offline Puki, Dance and Freeze, tracing)
- **Counting, part two:** "give the bunny 3 carrots".

**Polish**
- A parent setting to hide islands (fewer choices at two). (Done in 0.8.)
- A hand that shows the gesture the first time each game opens.
- A short vibration on success.
- A "new sticker!" moment at the end of a session.
- The hub at a steady 60 fps on the tablet.
- An about and credits card in settings.

**Release**
- The Play build (`play` flavor), its listing and forms (`fastlane/PLAY_CHECKLIST.md`), and
  regular GitHub releases instead of pre-releases.
- A project status note in the README, issue templates, and GitHub Discussions for questions.

**Puki grows with her (started)**
- **Line packs (done):** at night the PC writes lines for the games' moments, checks them, voices
  them, and the parent approves them on the dashboard; the tablet plays approved lines at once,
  even with the PC off. Next: packs for bedtime stories, new words for "Say it with Puki", and
  lines that follow her age.
- **Puki remembers (started):** a log of every exchange on the PC, a diary entry and a profile
  page written by Gemma when the brain goes to sleep, all fed into the prompt (no retrieval yet;
  see the design notes below). Game lines reach back only to an earlier moment about the same
  thing. On the dashboard a parent reads and corrects the profile, forgets diary entries, pauses or
  erases it all, and gets 2–3 things to ask her about each session. The log keeps 90 days. Next:
  the device brain, and retrieval once the diary outgrows the context.
- **Answer out loud (done):** after a question in Colors, Counting ("how many?"), Peekaboo and
  the shadow round, Puki listens a few seconds and she can say the answer instead of tapping
  ("כחול!"). Tapping always works; a parent setting turns it off. Next: Shapes and My Day.

## After v1: ideas for contributors

1. **Rewards and the parent's view** (M each)
   - More stickers, spaced further apart.
   - A parent progress page on the PC's dashboard: stars and level per game, words she said,
     games played, reset and level override.
   - **Talk about it later** (done in 0.8): after a session, the dashboard suggests 2–3 things to ask her.
   - A fridge for her drawings, with Puki's comments.
2. **Phone brain polish** (M each)
   - **English on the phone through Android's own speech engine.** Google's on-device
     voices speak English (and Hebrew) offline once downloaded. Puki would only ever use a
     voice marked offline, never one that sends text to Google, so nothing leaves the phone.
     BlueTTS stays Puki's Hebrew voice. (The alternative is Kokoro; BlueTTS's English needs
     espeak, which phones don't have.)
   - Faster start: Gemma takes about 18 s the first time.
   - Check the phone layouts on the S25: the food now sits beside the animal and the hub's
     islands scale down on short screens (0.5.1), but neither has been seen on a phone yet.
3. **Games that feel better** (S–M each)
   - More variety in My Day: items drawn from a bigger pool each visit.
4. **Small new toys** (S–M each, from the research below)
   - **Big and small:** "who gets the big apple?", with two choices.
   - **Sort it:** socks to the drawer, balls to the box.
   - **More recipes** and **fixing cars** (swap a flat tire, drag the parts into place).
   - Tracing every letter, not just the six with voice lines.
   - **Toy phone:** call Grandma, the cow or Puki; with the brain, a real little chat.
   - **Teddy doctor:** find where Teddy hurts and put a bandage on it.
   - **Grow a garden:** seed, water, sun, bloom.
   - **Little puzzles:** 2 to 4 piece animal jigsaws.
   - **What do we wear today?** Dress for the weather outside the window.

## Later

**Puki gets closer to her** (L)
- **Puki remembers:** a diary of past days, so he can say "yesterday you fed the elephant!"
  and the bedtime story can reach back. See the design notes below.
- **Family photos:** the parent adds a few photos with names, and Puki asks
  "מי זה? זאת סבתא!" and waits for her answer. Photos stay on the device and the PC.
- **Feelings:** Puki is sad because his balloon flew away, and she comforts him.
- **Show-and-tell:** she holds a toy up to the camera and Puki names it.

**More to play** (M each)
- **Baby care corner:** open pretend play: feed, rock, bathe and tuck in a baby animal.
- **Act out the word:** after Say it, the animals act the verb out ("קופץ!").
- **A daytime picture book** where Puki pauses for her to finish the line.
- **Say it, grown up:** two-word phrases, and English words ("זה כלב. באנגלית: dog!").
- **New word categories** (vehicles, food, family, weather): Gemma drafts the words and
  prompts, BlueTTS voices them, `scripts/check_voices.py` confirms nothing is silent.

**Easier for other families** (M–L)
- The app finds the PC by itself (mDNS, `_puki._tcp`), instead of an address or QR code.
- A signed Puki.exe (no SmartScreen warning), and a macOS brain.
- Compare BlueTTS's English with Kokoro on the PC too.

**Ongoing:** tune Puki's personality from real logs: which lines delight, which fall flat.

## Principles

From play-testing and a research pass on what helps at two:
- **Toy first, task second:** as few wrong moves as possible.
- **Talk less, wait more:** Puki asks, then leaves her a few seconds to answer.
- **Use the AI only where the reply depends on what she just did or said;** recorded lines
  everywhere else (they also hide the second of thinking).
- **Two choices, big targets, short bursts of play.**
- **Built-in endings;** no streaks, no autoplay.
- **Puki is a playmate, not a best friend:** he never guilts her for leaving, and points her
  back to her family.
- **The parent stays in the loop.**
- **Nothing leaves the home:** no accounts, ads, analytics or cloud.
- **Free, forever:** no price, no purchases, no subscriptions, no ads, ever. Nothing in the
  game stops to ask for money; the only end to play is the parent's time limit.

## Design notes: Puki remembers

Puki forgets everything between replies today. The plan for memory:

- **The log is the memory.** Every event, transcript and reply is appended verbatim to a
  local, append-only log on the PC. It's never edited or pruned; everything else is derived
  from it and can be re-derived.
- **Work happens off the hot path.** When the brain is about to sleep after idling, Gemma
  reads the session's log and writes a short **diary entry**, then updates a few small
  **pages** (her profile, favorite things, words she says, milestones). This costs nothing
  while she plays.
- **Replies stay cheap.** Each reply's prompt gets the profile page plus any diary entries
  relevant to the moment. Code and small scoring models decide what memory reaches the
  prompt; Gemma only receives it.
- **The diary is in English, the log stays in Hebrew.** Small, fast encoders for retrieval
  exist mostly for English, and Gemma writes the entries anyway. Her own words stay
  verbatim in Hebrew inside the entry (`said "פיל!" while feeding the elephant`), and
  recurring names keep their Hebrew form, so the Hebrew reply doesn't back-translate oddly.
- **Start small.** For the first months, the profile page plus the last few diary entries fit
  in the prompt. Retrieval, a gate on whether a memory is worth mentioning, and measuring
  both against a hand-labeled set come in when the diary outgrows the context.
- **A parent page** on the home network: words she said and when, games played, drawings
  with Puki's comments, and the diary in plain language. A private record that never leaves
  the house. The PC dashboard (`brain/dashboard.py`) is its start: it already shows a live
  log of what she said and what Puki answered.

## Design notes: which model thinks

Puki's lines must be short, warm, grammatical Hebrew that gets the child's gender right. In a
bake-off on Puki's real prompts (October 2026), Gemma 4 (E4B and 12B) wrote fluent, sensible
Hebrew. Qwen 3.5 4B was the closest alternative but got about one line in four or five wrong
(an invented word, a wrong fact); Qwen 3.5 2B and PrismML's Bonsai 2 (1–2 bit, even at 27B)
invented words and got genders wrong. Puki stays on Gemma, on the PC and on the phone. Small
models pass Puki's line filter easily, so a bake-off has to read the lines, not just count them.
