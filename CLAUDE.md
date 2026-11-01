# CLAUDE.md — Puki (פוקי)

## What This Project Is

An Android tablet game for toddlers (ages 2–4), bilingual in Hebrew and English. A hub world with ten islands: animals, colors, shapes, counting, music, letters, Peekaboo barn, Echo Puki, Say it with Puki, and daily routines (brushing teeth, tidying up, getting dressed, etc.). No ads, no tracking, no cloud. An optional talking parrot companion runs on a PC at home (`brain/`) or on a high-end phone itself.

See `olam-katan-claude-code-prompt.md` for the full game design document.

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (no XML layouts, no Views)
- **Min SDK:** 26 | **Target SDK:** 36
- **Architecture:** Single-activity, MVVM. One `ViewModel` per mini-game screen.
- **Navigation:** Compose Navigation (`NavHost` / `NavController`)
- **Persistence:** Jetpack DataStore (Preferences) for progress, stars, stickers, difficulty levels, settings. No Room unless complexity demands it later.
- **Audio:** Pre-recorded voice lines (OGG in `res/raw`, generated offline with BlueTTS by `scripts/generate_voices.py`) played by `VoicePlayer`. `SoundPool` for short SFX. `MediaPlayer` for background music loops.
- **Graphics:** Vector drawables + Compose `Canvas` for custom drawings and particle effects. No Lottie, no third-party animation libraries.
- **Orientation:** Landscape only (locked in manifest).

---

## Project Structure

```
app/src/main/java/io/github/tomsadeh/puki/
├── MainActivity.kt              # Single activity, sets up Compose + audio players
├── PukiApp.kt              # Top-level composable, NavHost, the companion's brain choice
├── PukiApplication.kt      # App-wide objects: repositories, Companion, the device brain
├── navigation/NavGraph.kt       # All routes
├── ui/
│   ├── hub/                     # Hub world (islands on the sea)
│   ├── animals/ colors/ shapes/ counting/ music/ words/   # The islands
│   ├── say/                     # Say it with Puki (speech, needs the brain)
│   ├── peekaboo/                # Peekaboo barn (find the hiding animal)
│   ├── echo/                    # Echo Puki (repeats her, higher and faster; no brain needed)
│   ├── myday/                   # My Day house + one screen per routine, bedtime story
│   ├── canvas/                  # Free painting (Puki looks at the drawing)
│   ├── stickers/  onboarding/  settings/
│   ├── common/                  # CompositionLocals, AppViewModel, GameSession (levels, stars)
│   └── components/              # Shared composables (see below)
├── data/
│   ├── models/  content/        # Data classes and the static game content
│   ├── ProgressRepository.kt    # Stars, stickers, levels (and the level rule, nextLevel)
│   └── SettingsRepository.kt    # Language, gender, sound, time limit, companion
├── audio/                       # VoicePlayer, SfxPlayer, MusicPlayer, MicRecorder, EchoVoice,
│                                #   Companion (talks to a brain)
├── brain/                       # Brain, HttpBrain + PinnedTls (the PC, over pinned HTTPS); the brain on a
│                                #   high-end phone: LocalBrain, Persona, Mind (Gemma), KolEars (kol-stream,
│                                #   ONNX), voice/ (BlueTTS port), DeviceModels
├── update/Updater.kt            # In-app updates from GitHub releases
└── theme/
brain/                           # The PC brain (Python): doorbell.py (HTTPS), brain.py, persona.py, safety.py, tls.py,
                                 #   common.py (pairing, private files), memory.py (log, diary, profile), packs.py (night-written lines a parent
                                 #   approves), install.py + pins.json (make_pins.py),
                                 #   update_signers, bundle.py, tray.py / build_exe.py (Puki.exe)
scripts/                         # Voices (generate/check), release.py, safety_eval.py (the stress test),
                                 #   sync_safety_kt.py (safety.py's word lists -> Safety.kt)
```

---

## Conventions

### Kotlin / Compose

- Use Kotlin idioms. Prefer `data class`, `sealed class`, `when`, extension functions.
- Composable functions are PascalCase. Non-composable functions are camelCase.
- Every game screen has a ViewModel that owns what outlives a round: its `GameSession` (stars, the
  level, the end of the session) and any game logic worth testing. Small games define it in the
  Screen file. The Screen observes it via `collectAsStateWithLifecycle()`.
- A round's moment-to-moment state (the step of the car wash, which pieces are on, animations, drag
  positions) lives in the composable with `remember`, `mutableStateOf` and `derivedStateOf`.
- ViewModels expose `StateFlow`s, not `mutableStateOf`.

### Shared Components (`ui/components/`)

Build and reuse these across all islands:

| Component | Purpose |
|-----------|---------|
| `GameBackground`, `SceneBackdrop` | A game's backdrop: an illustrated scene (farm, stage, bathroom...) or bubbles |
| `GameTopBar` / `BackButton` | Back, round dots and stars (optional) / the one way out of every screen |
| `rememberStepGame` / `ToolButton` | A game of a few steps (car wash, teeth, cake, bedtime): step, stars, party, end / a tool on a round plate |
| `rememberCompact()` | True on a short screen (a phone on its side): drop what's optional |
| `RoundPrompt` | Starts a round: tells Puki the scene, then plays the spoken prompt |
| `DragTarget` / `DropZone` | Drag and drop, also tap-to-place (`tapToZone`); see Drag and Drop |
| `RoundCompleteSheet` | End-of-session card: stars landing, sun rays, confetti, continue/home |
| `ConfettiOverlay` | Two confetti cannons and a gentle shower (about 90 pieces) |
| `CompanionParrot` | Puki in the corner: tap to chat, hold to talk |
| `Help.kt` | Hints when she's stuck (`rememberHelp`, `hintGlow`) |
| `Juice.kt`, `Reactions.kt`, `Scatter.kt` | Squish on press, pop-ins, idle bobbing, reactions, scattered layouts |
| `LongPressGate` | Two-second hold for parent-only actions |
| `TimeLimitOverlay` | The calm goodbye at the time limit: evening falls, Puki yawns and waves (no countdown) |
| `WigglingItem` | Gently wiggles to attract attention |

Drawing: build Paths once (`drawWithCache`), put still parts in their own layer (`graphicsLayer()`)
and redraw only what moves; the tablet (Galaxy Tab A7 Lite) is slow.

### Naming

- Route names: `"hub"`, `"animals"`, `"colors"`, `"shapes"`, `"counting"`, `"music"`, `"dance"`, `"words"`, `"say"`, `"peekaboo"`, `"echo"`, `"myday"`, `"myday/bath"`, `"myday/brush"`, `"myday/tidy"`, `"myday/dress"`, `"myday/table"`, `"myday/bedtime"`, `"myday/kitchen"`, `"myday/garage"`, `"canvas"`, `"stickers"`, `"settings"`. Safety's game allowances key on these.
- Resource files: `ic_animal_cow.xml`, `ic_shape_circle.xml`, `snd_tap.ogg`, `snd_success.ogg`. Prefix with `ic_` for icons, `snd_` for sounds, `bg_` for backgrounds.
- Data classes: `Animal`, `ShapeItem`, `ColorItem`, `Sticker`, `GameProgress`, `AppSettings`.
- ViewModels: `AnimalGardenViewModel`, `ColorSplashViewModel`, etc.

### Language / Bilingual

- All user-facing strings go through a bilingual content system, NOT Android string resources (since we switch language at runtime without recreating the activity).
- Create a `BilingualText` data class: `data class BilingualText(val he: String, val en: String)`. Access via a `currentLanguage` state.
- Layout direction switches with language: wrap content in `CompositionLocalProvider(LocalLayoutDirection provides if (isHebrew) LayoutDirection.Rtl else LayoutDirection.Ltr)`.

### Drag and Drop

- `DragTarget` handles the drag; `DragDropState` finds the zone under the piece (its centre, or
  at least 30% overlap). A wrong drop springs back home.
- `tapToZone`: a plain tap flies the piece to that zone, as if dragged (toddlers tap more than drag).
- The game decides right or wrong in `onDragEnd`; a second tap after a win is ignored (`null`).

### Animations

- Prefer Compose animation APIs: `animateFloatAsState`, `AnimatedVisibility`, `Animatable`, `updateTransition`.
- Spring animations for bounces: `spring(dampingRatio = 0.4f, stiffness = 300f)`.
- Confetti: custom `Canvas` drawing with a list of `Particle` data classes updated via `LaunchedEffect` + `withFrameMillis`.
- Idle animations (breathing, blinking): `rememberInfiniteTransition`.
- Keep frame rate reasonable: animations run only while their thing is on screen, and particles stay under ~100.

### Audio

- All speech is recorded: add a line to `scripts/generate_voices.py`, generate it, then run `scripts/check_voices.py` to confirm nothing the app plays is missing. Play with `voicePlayer.play(key)` or `playSequence(keys)` for sequences (counting: "one… two… three…").
- Lines that address the child come as `_he_f` / `_he_m` files; `VoicePlayer` picks the one matching the gender setting.
- Personal lines with the child's name (`voice_*_name*`) are git-ignored and optional; use `voicePlayer.personalKey(...)` / `playPraise(...)`, which fall back to generic lines.
- `SfxPlayer` uses `SoundPool` with max 6 streams. Preload all SFX on app start. Keep the pool small (<20 sounds total).
- Background music via `MediaPlayer` with `isLooping = true`. Fade out on pause/stop.
- Respect settings: check `SettingsRepository` before playing any audio.

### Difficulty System

- Each island has a level, 1 to `ProgressRepository.MAX_LEVEL` (3), in DataStore (`"difficulty_animals"`).
- At the end of a session (`GameSession.finish`): two easy sessions in a row (at most one slip every
  other round) move it up; a hard one (two slips a round) moves it down. The rule is
  `ProgressRepository.nextLevel`, tested in `LevelRuleTest`.
- The ViewModel reads the level when it starts (`GameSession.level()`) and picks content for it.

### Testing Considerations

- JVM unit tests in `app/src/test` (`./gradlew testGithubDebugUnitTest`): the phone brain's Persona,
  Safety and voice text, the level rule, pairing links, `PinnedTlsTest`, `UpdaterTest` (which version
  is newer), and `RegexPortabilityTest` (no Java-only regex flags: Android's ICU engine rejects
  `(?U)`, which crashed 0.3.0 at start).
- The PC brain: `python -m pytest brain/tests` (persona, safety and its mirror in Safety.kt,
  pairing and the lockout, TLS, the signed update, the bundle, reports).
- `python scripts/sync_safety_kt.py --check`: Safety.kt has safety.py's word lists.
- `python scripts/check_voices.py`: every voice line the app plays has audio.
- CI (`.github/workflows/ci.yml`) runs all of these on every push.
- Before a release, run it on a real device: the debug build's `SelfTestReceiver`
  (`adb shell am broadcast -n io.github.tomsadeh.puki/.SelfTestReceiver`, then `adb logcat -s PukiSelfTest`).
  Extras: `-e host <ip:port> -e code <CODE> -e pin <fingerprint>` checks the HTTPS connection to a PC;
  `-e timelimit <minutes>` sets the time limit (0 = off; put it back after testing); `-e echotest 1`
  plays a tone through Echo Puki's voice.
- Keep composables "dumb" — they render state and emit events, nothing else.

### Security and privacy

- **The PC connection** is HTTPS only, pinned to the PC's own certificate (`brain/tls.py` makes it;
  the pairing QR code carries its fingerprint; `PinnedTls` refuses any other). Never add a cleartext
  fallback.
- **The child's name and words** never go in git, logs or crash reports: personal voice lines and
  `brain/config.json` are git-ignored, errors are logged by type, not message, and brain.py logs
  lengths, not what she said or what Puki answered (`log_text` turns that on for debugging).
- **What the child says goes through safety.py first** (brain.py's /listen; Safety.kt on the
  phone, with the same word lists via `scripts/sync_safety_kt.py`): word lists, then a one-token
  gate on the model's probabilities (`safety_threshold`, default 0.8; on the phone one greedy
  digit) give hurt, danger, big questions, secrets and the like a fixed reply that points to mommy
  or daddy; the model never writes those. The gate fails closed: an error or a non-digit is
  "unclear", a neutral line. Every model line (replies, drawings, stories) then passes `reply_ok`
  (no promises, no "best friend"). A stress test (October 2026) showed why: with Puki's cheerful
  prompt, every Gemma size cheered at "let's make a fire" or "I fell down". Don't remove a net
  without re-running it (`scripts/safety_eval.py`).
- **Installer downloads** are pinned in `brain/pins.json`; regenerate with `brain/make_pins.py` after
  changing a version or model.
- **Releases** are signed with the key in `~/keys`; `release.py` won't publish a debug-signed APK.
- **Puki.exe** freezes the PC brain's code inside the exe: PC-side changes need a new exe on the
  release. In it there's no stdout and no console, so every subprocess uses `CREATE_NO_WINDOW`.


## Things to Avoid

- **No XML layouts.** Everything is Compose.
- **No failing states.** Never show error messages, red indicators, sad sounds, or negative feedback to the child. Wrong actions get gentle, playful redirects.
- **Art drawn in code:** a darker tone for outlines, one highlight, a soft contact shadow, so it matches the rest.
- **No small tap targets.** If it's tappable, it's at least 100dp. 120dp preferred. (`BackButton` looks 64dp, so it doesn't compete with the game, but takes a 100dp touch.)
- **No text-only navigation.** The child cannot read. All navigation is visual (icons, pictures, animations).
- **No internet.** The only network use is the optional companion on a PC at home, over the home network (see `brain/`), and checking GitHub for app updates. Everything else works fully offline.
- **No third-party analytics, ads, or tracking.**
- **No `Thread.sleep` or blocking calls on main thread.** Use coroutines.
- **No hardcoded strings in composables.** All text goes through `BilingualText`.

---

## Build & Run

```bash
# Build debug APK
./gradlew assembleGithubDebug

# Install on connected device/emulator
./gradlew installGithubDebug

# Run tests
./gradlew testGithubDebugUnitTest
python -m pytest brain/tests
```

Test on a real tablet or phone over adb; the x86 emulator is very heavy. Releases: `python scripts/release.py --publish`
(builds the shrunk release APK and uploads it as a GitHub pre-release; installed apps offer the update).
Two flavors: `github` (those APKs; updates itself from the GitHub releases) and `play` (Google Play:
no updater, no REQUEST_INSTALL_PACKAGES, via `BuildConfig.SELF_UPDATE` and `app/src/play/AndroidManifest.xml`).
The Play bundle: `./gradlew bundlePlayRelease` (app/build/outputs/bundle/playRelease/app-play-release.aab).
The Play listing (texts, icon, feature graphic, screenshots) is in `fastlane/metadata/android/`, and the
steps and form answers in `fastlane/PLAY_CHECKLIST.md`; the privacy policy is `docs/privacy.html`.
All builds are signed with the release key in `~/keys` (puki-release.jks + puki-signing.properties,
never in git); without it they fall back to the debug key, which release.py won't publish.
The PC brain's updates are signed the same way: release.py adds `puki-brain-<version>.zip` (the brain
folder, with `brain/release.txt`) and its `.sig`, made with `~/keys/puki-update` (an ssh-keygen ed25519
key, never in git), and `install.py --update` uses a release only if it verifies against
`brain/update_signers`. Losing that key means installed PCs can't take signed updates any more; a new
key reaches them only in a release signed with the old one.

---

## Where things stand

The original build plan is done (all islands, My Day, canvas, stickers, bedtime, levels), and so
are the security pass and the first new toys (0.6: Peekaboo barn, Echo Puki, the calm goodbye).
0.7 is most of v1: counting one by one, shadow match, drawing modes with an eraser and a camera,
the cake and the car wash, offline Puki, Dance and Freeze, tracing, a redrawn dress-up and bath,
safety nets for what the child says (PC and phone), מלשין לרנינג, signed PC updates, and a
hygiene, security and performance pass. What's next is in ROADMAP.md (the Play launch).
