# Contributing to Puki

Thanks for wanting to help! Puki is a game for two-to-four-year-olds and a companion whose
brain runs on the family's own device. Both shape what gets accepted. Please read this before opening a
pull request; small, focused PRs that follow it get merged quickly.

**Not sure an idea fits?** Open an issue first and describe it. That saves you building
something that can't be merged. Bug fixes and small polish can go straight to a PR.

## The hard rules

A PR that breaks any of these won't be merged, however good it is otherwise.

**For the child**
- **No failure states.** No "wrong", no red X, no sad sounds, no lost stars or lives. A wrong
  try gets a gentle, playful redirect ("let's try again", the right answer modeled).
- **Nothing to read.** The child can't read: every action is reachable through pictures,
  sound and motion. Text on screen is only for the grown-up.
- **Big targets.** Anything tappable or draggable is at least 100 dp (120 dp preferred).
- **Calm, not compulsive.** No timers that pressure, no streaks, no "come back tomorrow"
  hooks, no flashing for its own sake. The parent's time limit must keep working.
- **Free, forever.** No payments, in-app purchases, subscriptions, ads, "premium" content or
  anything else that stops play to ask for money, now or later. Puki exists because toddler
  games do that.
- **Both languages.** Every new line or prompt exists in Hebrew and English, and Hebrew lines
  that address the child have both a girl and a boy form (`heGirl`, `_he_f` / `_he_m`).

**For the family's privacy**
- **Nothing about the child leaves the device.** No analytics, crash reporting, ads, accounts
  or cloud APIs. The app uses the network for two things only: the model download a parent
  asks for (pinned by size and SHA-256 in `DeviceModels.kt`), and the GitHub update check in
  the `github` flavor. Don't add any other.
- **No personal data in the repo.** No children's names, voices, photos or recordings in
  code, tests, screenshots, commit messages or fixtures. Personal files stay git-ignored
  (`brain/config.json`, `voice_*_name*`).
- **The child's words stay private.** Don't log or store transcripts or recordings to disk.
  What she said is kept in memory only, for the parent's "What Puki said" list, and is
  forgotten when the game closes.

**For what Puki says**
- **The safety rails stay on.** Changes to the prompt format, the reply filter or the safety
  nets (`Persona.kt`, `Safety.kt`, and their source in `brain/persona.py` and
  `brain/safety.py`) must keep replies short, warm, age-appropriate, in the child's
  grammatical gender, with no compliments tacked on. Show sample outputs in the PR.
- **A new model** comes with new pins in `DeviceModels.kt`, an updated
  `train/pt_manifest.json` if the pretraining changed, and the truth and safety scores
  (`train/sft_small.py`) before and after.

**Legal**
- Contributions are under the repo's [MIT license](LICENSE). Only add art, sound and music
  that you made or that has a license compatible with MIT (say which in the PR). No assets
  from other games or apps, and no AI-generated voices of real people.

## What a PR needs to show

| If you change... | Show that... |
|---|---|
| Anything in the app | `./gradlew assembleGithubDebug` builds, and you played the changed screen on a tablet or emulator (landscape). For visual changes, **before and after screenshots**, in Hebrew (RTL) if layout is involved. |
| Voice lines | They're added to `scripts/generate_voices.py` and generated with BlueTTS (Noa); `python scripts/check_voices.py` reports nothing missing. Plain text without nikud; ambiguous words go in `PHONEMES`. |
| The device brain (`app/.../puki/brain/`) | `./gradlew testGithubDebugUnitTest` passes, and you tried it on a real device with the models downloaded: a few sample situations and Puki's replies in the PR. Say which device. |
| The word lists or the filter | Change them in `brain/safety.py` / `brain/persona.py`, run `python scripts/sync_safety_kt.py` so `Safety.kt` matches (`--check` passes), and `python -m pytest brain/tests`. |
| The PC brain (`brain/`) | It's retired in 0.9.0 and takes no new features. Only changes to `safety.py`, `persona.py` and `pronunciation.tsv` (the source the app and `train/` use) are accepted. |
| Dependencies | Why it's needed and its license. Prefer none: no new Android libraries for things Compose can do (animations, drawing). Pin exact versions, at least two weeks old. |

## Style

- Match the code around you: naming, comment density, idioms. [`CLAUDE.md`](CLAUDE.md) has the
  app's conventions (MVVM, `StateFlow` in view models, `BilingualText`, shared components in
  `ui/components/`).
- Reuse what's there before adding new: `GameBackground(scene = ...)`, `SquishIndication`,
  `idleBob`, `popIn`, `PromptBubble`, `ConfettiOverlay`, `RoundCompleteSheet`.
- Graphics are vector drawables or Compose `Canvas`, drawn in the existing friendly style. No
  bitmap-heavy art, no third-party animation libraries.
- One topic per PR, with a commit message that says what changed and why.

## Using AI tools

Welcome. Much of Puki was built with [Claude Code](https://claude.com/claude-code). The same
bar applies: you're responsible for every line, you've run it, and the PR describes what you
tested yourself. Unreviewed bulk changes will be closed.

## Review

Reviews may take a few days. Expect questions about how a change feels for a two-year-old;
if you can, describe how a child reacted to it.
