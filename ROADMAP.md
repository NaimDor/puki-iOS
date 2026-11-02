# Roadmap

Where Puki is, what comes next, and the ideas after that. Order is a suggestion: real
play-testing with a toddler decides what comes first. Nothing here needs the cloud.

Sizes: **S** an hour or so, **M** a few hours, **L** a day or more.

## Where Puki is (0.9)

**The game**
- Ten islands with scene-based games: animals (with **shadow match**), colors, shapes,
  counting (**one tap, one number**), letters, music (with **Dance and Freeze**), **Peekaboo
  barn** (find the hiding animal), **Echo Puki** (Puki repeats her in a silly parrot voice, no
  brain needed), "Say it with Puki", and "My Day": bath, table, dressing, tidying, teeth,
  bedtime, a **cake in the kitchen** and a **car wash** in the garage. Plus a sticker album.
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
- A parent setting to hide islands (fewer choices at two).
- The game updates itself from GitHub releases (parent settings); a Play build is ready.
- **מלשין לרנינג ("What Puki said"):** parents see Puki's recent lines and flag anything that
  wasn't right; the report goes by the parent's own email.

**Puki's brain, on the device (done in 0.9)**
- Four small models, downloaded once (about 0.5 GB) and run offline on a 64-bit ARM device
  with about 3 GB of memory: **Brains** (46M parameters, trained from scratch for Puki: writes
  his lines and rates what the child said), **Beak** (reads plain Hebrew into phonemes),
  **Syrinx** (BlueTTS's voice in int8) and **Ears** (kol-stream). About 1.5 s a reply on a
  Galaxy Tab A7 Lite.
- Comments on what the child does, answers when she talks to him, listens for answers said out
  loud (Colors, Counting, Peekaboo, the shadow round), and plays "Say it with Puki" and "what
  does the cow say?". Hebrew only for now.
- **The PC brain is retired.** The app no longer talks to a PC; `brain/` stays only as the
  source of the safety word lists, the persona and the pronunciation table.

**Safe by design**
- Releases are signed with Puki's own key. Every model file the app downloads is pinned to its
  size and SHA-256.
- **Safety nets for what the child says:** word lists and Brains' own gate give hurt, danger
  and big questions a fixed, gentle answer that points to mommy or daddy; every line Puki says
  is filtered for promises and bonding, and a line that fails falls back to a recorded one.
- Nothing about the child leaves the device; her words are forgotten when the game closes.

## Next

1. **English on the device** (L). Brains already writes English; Puki needs an English voice
   on the device. One path is Android's own speech engine: Google's on-device voices speak
   English offline once downloaded, and Puki would only ever use a voice marked offline, never
   one that sends text to Google. BlueTTS stays Puki's Hebrew voice. (The alternative is
   Kokoro; BlueTTS's English needs espeak, which phones don't have.)
2. **Brains v2** (L). A longer pretraining on FineWeb-2's Hebrew (ODC-By), with the Muon
   optimizer and a mid-training stage before the fine-tuning, and a new tokenizer.
3. **Puki's eyes** (L). A small model that looks at her drawings, so Puki can react to them
   on the device (today the game plays its own lines there).
4. **Bedtime stories and memory on the device** (L). The bedtime story about her day, and a
   memory that stays on the device (see the design notes below).
5. **Google Play** (M). The Play build (`play` flavor), its listing and forms
   (`fastlane/PLAY_CHECKLIST.md`), and regular GitHub releases instead of pre-releases.

## v1.0: the Google Play launch

v1.0 is meant to be finished: what's below, then bug fixes for a few months, and new features
only from contributors. Features freeze about a week in; the rest of Play's 14-day closed test
is for polish and testers' reports.

**New and better games** (done in 0.7: counting one by one, shadow match, drawing modes,
cooking, the car wash, offline Puki, Dance and Freeze, tracing)
- **Counting, part two:** "give the bunny 3 carrots".

**Polish**
- A hand that shows the gesture the first time each game opens.
- A short vibration on success.
- A "new sticker!" moment at the end of a session.
- The hub at a steady 60 fps on the tablet.
- An about and credits card in settings.

**Release**
- A project status note in the README, issue templates, and GitHub Discussions for questions.

**Puki grows with her**
- **Answer out loud (done):** after a question in Colors, Counting ("how many?"), Peekaboo and
  the shadow round, Puki listens a few seconds and she can say the answer instead of tapping
  ("כחול!"). Tapping always works; a parent setting turns it off. Next: Shapes and My Day.
- New words for "Say it with Puki", and lines that follow her age.

## After v1: ideas for contributors

1. **Rewards and the parent's view** (M each)
   - More stickers, spaced further apart.
   - A parent progress page in settings: stars and level per game, games played, reset and
     level override.
   - A fridge for her drawings.
2. **Device brain polish** (M each)
   - A faster first start (the ears take about 17 s to load on a slow tablet).
   - Check the phone layouts: the food sits beside the animal and the hub's islands scale down
     on short screens (0.5.1), but neither has been seen on a phone yet.
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
- **Family photos:** the parent adds a few photos with names, and Puki asks
  "מי זה? זאת סבתא!" and waits for her answer. Photos stay on the device.
- **Feelings:** Puki is sad because his balloon flew away, and she comforts him.
- **Show-and-tell:** she holds a toy up to the camera and Puki names it.

**More to play** (M each)
- **Baby care corner:** open pretend play: feed, rock, bathe and tuck in a baby animal.
- **Act out the word:** after Say it, the animals act the verb out ("קופץ!").
- **A daytime picture book** where Puki pauses for her to finish the line.
- **Say it, grown up:** two-word phrases, and English words ("זה כלב. באנגלית: dog!").
- **New word categories** (vehicles, food, family, weather): BlueTTS voices them,
  `scripts/check_voices.py` confirms nothing is silent.

**Ongoing:** tune Puki's personality from parents' reports: which lines delight, which fall flat.

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
- **Nothing about her leaves the device:** no accounts, ads, analytics or cloud.
- **Free, forever:** no price, no purchases, no subscriptions, no ads, ever. Nothing in the
  game stops to ask for money; the only end to play is the parent's time limit.

## Design notes: Puki remembers

Today the device brain keeps nothing beyond its last few lines, and her words are forgotten
when the game closes. The plan for memory on the device:

- **The parent decides.** A parent can read, correct, pause and erase it, from settings.
- **The log is the memory.** Game moments and Puki's lines are appended to a local log on the
  device; everything else is derived from it and can be re-derived. Careful moments (hurt,
  scared, big questions) are never logged.
- **Work happens off the hot path.** After playing, a short **diary entry** and a few small
  **pages** (favorite things, words she says) are written while she isn't playing. This costs
  nothing while she plays.
- **Replies stay cheap.** Code decides what memory reaches a line ("the elephant again!"),
  only when an earlier session had the same thing, so the model can't link unrelated memories
  or make one up.
- **Start small.** A profile page and the last few entries first; retrieval only once the
  diary outgrows what fits.
