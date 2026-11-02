# פוקי's brain (companion server)

> **Retired in 0.9.0.** Puki's brain now runs on the device itself (parent settings, **Puki on
> this device**), and the app no longer talks to a PC. This folder takes no new features. It is
> kept only because `safety.py`, `persona.py` and `pronunciation.tsv` are the source that
> `scripts/sync_safety_kt.py` (for the app's `Safety.kt`) and `train/` use.
>
> **If your family used the PC brain:** update the app, turn on **Puki on this device**, then
> remove the PC program and its models (about 10 GB):
>
> - **Puki.exe (Windows):** right-click Puki's tray icon (behind the **^** arrow if you don't see
>   it), choose **Uninstall Puki...**, and answer yes to each part. Then delete Puki.exe.
> - **Installed from this repo:** `python brain\install.py --uninstall` on Windows, or
>   `python3 brain/install.py --uninstall` on Linux (add `--dir <folder>` if you installed it
>   somewhere other than `~/puki-brain`). It stops Puki and asks before removing each part: the
>   login shortcut (on Linux, the `puki` systemd user service), the firewall rule (on Linux, the
>   `ufw` rule for port 8790), the models, llama.cpp, BlueTTS and the Python environment, and
>   your settings, pairing code and certificate.
> - **By hand:** quit Puki (the tray icon's **Quit**, or end `pythonw.exe`); delete the "Puki
>   brain" shortcut, or any shortcut you made to `doorbell.py`, from `shell:startup`; in an
>   administrator PowerShell, run `Remove-NetFirewallRule -DisplayName "Puki companion"`; then
>   delete the folder it ran from (`~/puki-brain`, or `%LOCALAPPDATA%\Puki` for Puki.exe).
>
> The rest of this page describes the old PC brain, for reference.

The parrot in the games gets its words from a PC at home. Gemma 4 E4B writes a short
line about what just happened, and [BlueTTS](https://github.com/maxmelichov/BlueTTS)
speaks it in Hebrew or English. Holding the parrot records the child, and
[ivrit.ai](https://huggingface.co/ivrit-ai)'s Hebrew-tuned Whisper (or NVIDIA's Parakeet, when the
app is in English) transcribes the recording on the PC so the parrot can answer.

The tablet only talks to the PC over the home network. Nothing goes to the internet.
When the PC can't be reached (it's off, you're away, or a reply is too slow), the
parrot simply doesn't appear and the games use their recorded voice lines.

```
tablet ──Wi-Fi──▶ doorbell.py :8790 ──starts on demand──▶ llama-server :8792 (Gemma)
                  (always on, no GPU)                    brain.py     :8791 (Whisper + prompt + BlueTTS)
```

- **doorbell.py** runs from login. It uses no GPU and almost no memory, and only needs
  the Python standard library.
- When a game opens, the tablet calls `/wake`. The doorbell starts Gemma on the GPU if at
  least `min_free_vram_mb` is free, otherwise on the CPU (slower, about 2-4 s per line).
  Warming up takes about 30 seconds.
- After `idle_minutes` without requests, both heavy processes exit and the GPU memory is freed.

**Requirements:** a Windows or Linux PC with Python 3.10 or newer (Puki.exe brings its own) and,
ideally, an NVIDIA GPU with about 6 GB of free VRAM. Gemma and Whisper together use about
5.6 GB. Without a GPU everything runs on the CPU, just slower.

## Quick setup (Windows and Linux)

With [Python 3.12](https://www.python.org/downloads/) installed (or `winget install Python.Python.3.12`),
run this from the repo folder:

```
python brain\install.py
```

It shows what it will do, then:

1. downloads llama.cpp: the CUDA build if there's an NVIDIA GPU (matched to the driver),
   otherwise the CPU build. If the NVIDIA driver is there but `nvidia-smi` fails (most often
   after a driver update without a restart), it stops and says so instead of picking the CPU
   build: restart and run it again, or add `--cpu`;
2. asks which Gemma to use: **E4B** (lighter, faster), **12B** (smarter, sees drawings better;
   needs about 9 GB of free GPU memory) or **both** (12B when the GPU has room, E4B when a
   game or a training run is using it), then downloads it with its vision part, the ivrit.ai Hebrew Whisper, Parakeet for English,
   and BlueTTS with its voice models and Hebrew pronunciation (about 10 GB in all, into
   `~/puki-brain`). That's every model Puki uses: the brain never downloads while it runs;
3. creates the Python environment (without the NVIDIA libraries on PCs without a GPU);
4. copies the brain's files into `~/puki-brain/brain`, where Puki runs from (in its own Python
   environment). Pulling or editing the folder you installed from doesn't change the running
   Puki until you run the installer again;
5. writes `~/puki-brain/brain/config.json` with all the paths;
6. adds the home-network-only firewall rule (Windows asks for permission once), and tells
   you if your Wi-Fi is set to *Public*;
7. makes Puki start at login, starts it, and opens the [dashboard](#dashboard) with the
   pairing code and QR code;
8. wakes the brain and asks it for a line in Hebrew and in English, the way the tablet does
   (HTTPS, the pairing code), and prints what Puki said. If Puki doesn't talk, the setup
   stops there and says where the error is.

Before it starts, it shows how much it will download and about how long that takes (from a
short speed test). Everything it downloads (the models, llama.cpp, uv, the BlueTTS code) is
pinned in `pins.json`: the Hugging Face files at a fixed commit, and every file's SHA-256. A
download that doesn't match is deleted and refused, so a file changed upstream, by accident
or not, never reaches the PC; a file is checked once and remembered. After changing a version
or a model in `install.py`, run `python brain/make_pins.py` and commit the new `pins.json`. Everything it prints also goes to
`install.log` in the folder, to attach when asking for help. At the end it shows the pairing
code and a QR code to scan with the tablet. `--gui` shows the same setup in a window, with
progress bars.

Downloads show progress and resume if interrupted, and running it again only does what's
missing. Options: `--dry-run` (just show the plan), `--model e4b|12b|both`, `--dir D:\puki-brain`, `--cpu`,
`--only firewall,autostart` (repeat single steps), `--no-firewall`, `--no-autostart`.

**Later:** `python brain\install.py --update` gets the newest Puki release and redoes only what
changed, including a newer llama.cpp or BlueTTS. Updates are **signed**, like the tablet's: the
release's brain comes with a signature (`ssh-keygen -Y`, made with Puki's update key), and the
update is used only if it checks out against `brain/update_signers`, the key's public half. A
release can't pass for a newer one either: the version is inside what's signed. The folder you
installed from is left as it is (the release is unpacked into `~/puki-brain/update`); after a
`git pull`, run the installer itself (without `--update`) to install what you pulled. `python brain\install.py --uninstall` stops Puki and removes the login shortcut,
the firewall rule it added, and everything it downloaded, asking before each part.

## Tray app and Puki.exe

`tray.py` runs the doorbell with an icon in the Windows tray: grey while Puki sleeps,
yellow while it wakes, green when it's awake. Its menu opens the dashboard, shows the
pairing code, puts the brain to sleep, turns "start at login" on or off, uninstalls Puki
(the same `--uninstall`, in a window that asks before each part), and quits. Windows
first puts new tray icons behind the **^** arrow; drag it onto the taskbar to keep it in view.

```
%USERPROFILE%\puki-brain\.venv\Scripts\pythonw.exe %USERPROFILE%\puki-brain\brain\tray.py     (the brain's environment has pystray and pillow)
```

`python brain\build_exe.py` builds **Puki.exe** (about 17 MB): the same tray app with the
brain's files inside. The first time it runs, it unpacks them into `%LOCALAPPDATA%\Puki`
and runs this installer there in a setup window (the model choice, progress bars, and the QR
code at the end). Python comes along via
[uv](https://github.com/astral-sh/uv), kept inside that folder, so the PC needs no Python.
After that it's just the tray icon, and it starts at login. A newer Puki.exe unpacks its files
again and runs the installer in an "Updating Puki" window, which only does what changed (as
`--update` does), before Puki starts; if another version is running, it asks you to quit that
one first. It's unsigned, so Windows
SmartScreen may warn the first time; the build prints its SHA-256 to compare against.

**On Linux** (tested for Ubuntu-like systems with an NVIDIA driver), run the same installer
with the system's Python 3.10+ (`sudo apt install python3-venv` if `venv` is missing):

```
python3 brain/install.py
```

The differences: it downloads llama.cpp's Linux build (CUDA 12.8 or 13.4, matching the driver,
or the CPU build), starts Puki at login as a systemd user service (`systemctl --user status
puki`; `sudo loginctl enable-linger $USER` makes it start before anyone logs in), and if `ufw`
is on, offers to open port 8790 to the home network only (with `sudo`). There's no tray icon
on Linux; the dashboard works the same. `--update` and `--uninstall` work too.

The steps below are the same thing by hand (for macOS too).

## Setup

Everything goes in `~/puki-brain` by default. You can change any path in the config.

1. **llama.cpp:** download a Windows CUDA build plus the matching `cudart` zip from
   [llama.cpp releases](https://github.com/ggml-org/llama.cpp/releases) and unzip both into
   `~/puki-brain/llama`.
2. **Gemma:** download `gemma-4-E4B-it-Q4_0.gguf` from
   [unsloth/gemma-4-E4B-it-GGUF](https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF) into
   `~/puki-brain/models`. Also download `mmproj-F16.gguf` from the same repo and save it as
   `~/puki-brain/models/mmproj-gemma-4-E4B-F16.gguf`. That's Gemma's vision part, which lets
   Puki see drawings (about 1 GB of extra VRAM; leave it out and everything else still works).
   Gemma is subject to Google's [Gemma terms of use](https://ai.google.dev/gemma/terms).
3. **Whisper:** download all files of
   [ivrit-ai/whisper-large-v3-turbo-ct2](https://huggingface.co/ivrit-ai/whisper-large-v3-turbo-ct2)
   into `~/puki-brain/models/whisper-ivrit-turbo`. For English, also download `encoder-model.int8.onnx`, `decoder_joint-model.int8.onnx`, `nemo128.onnx`, `vocab.txt` and `config.json` from
   [istupakov/parakeet-tdt-0.6b-v2-onnx](https://huggingface.co/istupakov/parakeet-tdt-0.6b-v2-onnx) (about 660 MB) into
   `~/puki-brain/models/parakeet-tdt-0.6b-v2-onnx`. It runs on the CPU, so it needs no VRAM, and
   loads the first time the app talks to Puki in English. Without it, English uses the Hebrew
   Whisper, which understands English poorly.
4. **BlueTTS:** clone [BlueTTS](https://github.com/maxmelichov/BlueTTS) into
   `~/puki-brain/BlueTTS` and download its ONNX models as its README describes. Its Hebrew
   pronunciation model goes in `models` too: [RenikudPlus `model.onnx`](https://huggingface.co/notmax123/RenikudPlus/blob/679c56ca449d41873fb8ff7711ddf7563d28198f/model.onnx)
   (1.2 GB) as `~/puki-brain/models/renikud-plus/model.onnx`. The brain runs offline, so
   without it the brain doesn't start.
5. **Python environment** for brain.py:
   ```
   python -m venv ~/puki-brain/.venv
   ~/puki-brain/.venv/Scripts/pip install ~/puki-brain/BlueTTS -r brain/requirements.txt
   ```
6. **Your settings:** create `brain/config.json`. It's git-ignored, so the child's name stays
   private, and it overrides anything in `config.example.json`:
   ```json
   { "child_name": "<child's name>", "child_gender": "girl" }
   ```
   Write the name **without nikud**. BlueTTS mispronounces text that already has nikud.
7. **Firewall:** let the tablet through the Windows Firewall, home network only. Run this once
   in an **administrator** PowerShell:
   ```powershell
   New-NetFirewallRule -DisplayName "Puki companion" -Direction Inbound -Protocol TCP -LocalPort 8790 -Profile Private -Action Allow
   ```
   Your Wi-Fi must be set to *Private* in Windows network settings. Don't let Python itself
   through the firewall: if Windows asks when the doorbell first starts, choose **Cancel**,
   so the rule above (port 8790, home networks only) is the only way in, and the PC stays
   closed on public Wi-Fi.
8. **Start it automatically at login:** put a shortcut in `shell:startup` with this target,
   adjusting the path to where you cloned the repo:
   ```
   pythonw.exe "C:\path\to\puki\brain\doorbell.py"
   ```
9. **In the app:** open parent settings (long-press the gear), turn on **התוכי פוקי**, and
   scan the QR code on the [dashboard](#dashboard): it carries the PC's address, the
   [pairing code](#pairing-code) and the certificate's fingerprint. (Or type the address, for
   example `192.168.1.20:8790`, and the code.) A DHCP reservation in your router keeps that
   address from changing.

## Pairing code

The doorbell answers only requests that carry its **pairing code**, so another device on
the network (a guest's phone, a smart TV) can't make Puki talk or send it recordings.

- The code is made the first time the doorbell starts, printed in its console
  (`pairing code: K7M2-QX9P`), and kept in `pairing_code.txt` next to it (installed:
  `~/puki-brain/brain/pairing_code.txt`; by hand: `brain/pairing_code.txt`, git-ignored). With
  `pythonw` there's no console, so open that file, or the [dashboard](#dashboard).
- Scan the dashboard's QR code in the app (welcome screen or parent settings), or type it once
  under the PC address; case and the dash don't matter. Below the field, the app shows whether
  it's connected, the code is wrong, the PC can't be found, or something answered that isn't
  the paired PC (then scan the QR code again).
- Wrong codes are answered after a 1-second delay, and an address that sends 10 wrong codes
  within 10 minutes is shut out for 15 minutes.
- `python doorbell.py --new-code` makes a new code, for example if it leaked. Every tablet
  then needs the new code.
- Everything travels over HTTPS with the PC's own certificate (`tls.py` makes it on first
  start, in `puki_cert.pem` and `puki_key.pem`, never in git). The QR code carries its
  fingerprint, and the dashboard shows the start of it. Deleting the two files makes a new
  certificate on the next start; every tablet then needs pairing again.

## Dashboard

While the doorbell runs, **http://127.0.0.1:8793** on the PC shows:

- whether the brain is asleep, waking up or awake, which Gemma runs and on the GPU or the
  CPU, and when it will go back to sleep; a button puts it to sleep right away (handy before
  a training run);
- **Puki's mind:** Gemma E4B or 12B (whichever are downloaded). The choice applies the next
  time Puki wakes up; when 12B is chosen but the GPU is busy, E4B fills in;
- the pairing code, the PC's address, and a QR code with both (for the app to scan once it
  can), plus a button that makes a new code;
- how many idle minutes before it sleeps (saved to `config.json`);
- **Puki's new lines:** lines for the games' moments, written at night (see below), waiting for
  you to listen and approve (✓) or turn down (✗); "Write lines now" runs the writing at once;
- a live log: what each game asked, what Whisper heard, what Puki said and how long it took,
  and any wrong pairing codes. If `nvidia-smi` starts failing (so the brain wakes on the CPU
  next to a GPU), the log says so, and again when it works; the free GPU memory line shows the
  error meanwhile.

The dashboard is served on 127.0.0.1 only, never to the home network, because the log holds
the child's words. The log lives in memory and disappears when the doorbell stops. Pages from
other websites can't read or use it (requests must address it as 127.0.0.1/localhost, and its
buttons need a header a foreign page can't send). The port is `dashboard_port` in the config.

## Line packs: Puki's lines, written at night

The games ask Puki to react to moments ("in the animal game: the cow found its food"). The
doorbell counts each such moment (text the app writes, never the child's words; one that names
the child is skipped). At `pack_hour` (3 at night; `null` turns it off), after 20 quiet minutes,
the brain writes up to `pack_lines_per_moment` (3) lines for each of the `pack_moments_per_night`
(20) most common moments that don't have enough yet. Each line has to pass the line filter,
`reply_ok`, and a one-token check on the model's own probabilities (`pack_line_threshold`,
0.9); the ones that pass are voiced and wait on the dashboard. Only approved lines go to the
tablet: it fetches them with their audio (`GET /pack`, `GET /pack/audio?id=`, with the pairing
code) and plays one at once when that moment comes up, instead of the recorded fallback when
the brain is asleep or the PC is off, and half the time even when it's awake. A turned-down line
is never said, and never written again. They're kept in `pack.json` and `pack/` next to the
brain (git-ignored; a line may say the child's name), and in the tablet's own storage.

## Puki remembers

Every exchange (a game moment and Puki's line, what the child said and his answer, a word tried
in Say it) is appended to `brain/memory/log.jsonl`. When the brain goes to sleep after playing,
Gemma reads the session and writes a short diary entry (`diary.json`, in English, with the
child's own words quoted as said) and updates a profile page (`profile.json`: favorite things,
words, what's being learned). The dashboard shows both.

No retrieval yet: Puki's prompt gets the profile, the last week's diary and the session so far
(`context` is 8192 for that). When the child talks to Puki and in the bedtime story, the memory
is just there; about one game line in three (`memory_say_chance`) reaches back, but only when an
earlier session had the same thing (the elephant again), picked in code, so Gemma can't link
unrelated memories or make one up. The lookup searches the last week across all games: game
moments, what the child said (to Puki, and in Say it), and the words the diary
quotes. Hebrew word forms meet ("הפילים" and "פיל", "כחולה" and "כחול"); the names don't count. Careful moments (hurt, scared, big questions) are
never logged, and from Say it only the word is kept (with whether she said it), never what
Whisper heard. It all stays on the PC; `"memory": false` turns it off.

On the dashboard a parent can edit the profile page Puki reads, forget a diary entry (and the
play it came from, so Puki can't reach back to it either), pause memory, or erase it all. Each
entry also has 2–3 questions to ask the child about that session later at home, in the language
she played in. The log and diary keep `memory_keep_days` (90 by default; 0 keeps everything).

## Trying it

```
python brain/doorbell.py
curl -X POST -H "X-Puki-Code: K7M2-QX9P" localhost:8790/wake      # your code from pairing_code.txt
curl -H "X-Puki-Code: K7M2-QX9P" localhost:8790/health            # wait for "ready"
```

Every request needs the `X-Puki-Code` header; without it the doorbell answers 401.

| Endpoint | Takes | Returns |
|---|---|---|
| `POST /say` | `{"situation": "...", "lang": "he"}` | a short spoken reaction: OGG audio, with the line in `X-Text` |
| `POST /listen?lang=he[&context=...]` | a WAV recording of the child | the same, plus `X-Heard` with what Whisper heard |
| `POST /hear?lang=he&hint=אדום&hint=כחול` | a WAV of the child answering a game out loud | `{"heard"}`; the tablet decides which answer on screen it was |
| `POST /repeat?lang=he&target=כלב[&alt=הב]` | a WAV of the child saying a word | `{"heard", "match": "word" \| "close" \| "tried"}` for Say it with Puki; no Gemma, just speech recognition |
| `POST /look?lang=he` | a PNG of a drawing | a spoken reaction to the drawing (Gemma vision) |
| `POST /story` | `{"played": "...", "icons": {"ic_animal_cow": "פרה", ...}, "lang": "he"}` | `{"sentences": [{"text", "icon"}]}`, a short bedtime story with one picture per sentence |
| `POST /speak` | `{"text": "...", "lang": "he", "speed": 0.9}` | the text spoken in the parrot's voice |

Every endpoint returns 204 (nothing) when no safe line came out, and the app falls back
to its recorded voices. The doorbell's console log shows every
situation, transcript and reply, which helps when tuning.

To test over USB without the firewall rule, run `adb reverse tcp:8790 tcp:8790` and set
the app's PC address to `127.0.0.1:8790`.

## Safety rails

- The prompt sets up a warm persona for a 2-year-old: short sentences, no "no" or "wrong",
  gentle encouragement after a mistake, no compliments tacked on, and the child's
  grammatical gender.
- A filter only lets through short lines in clean Hebrew or English with no banned words.
  If a reply fails the filter, the request returns nothing and the app falls back to its
  recorded lines.
- Small models repeat themselves, so each request suggests a different opening sound and
  lists the last few lines to avoid.
- **What the child says is checked first** (`safety.py`): word lists for hurt, danger, wanting a
  parent, big questions and secrets, then a one-token check on Gemma's own probabilities
  (`safety_threshold`, default 0.8). Either gives a fixed, gentle reply that points to mommy or
  daddy; the model never writes those. If the check fails, Puki answers with a neutral line.
  Every line Puki says then passes `reply_ok`: no promises ("mommy is on her way") and no
  bonding ("best friend", "I love you"). `scripts/safety_eval.py` is the stress test.
- The log shows how long things were, not what the child said or what Puki answered;
  `"log_text": true` in `config.json` shows the text, for debugging.
