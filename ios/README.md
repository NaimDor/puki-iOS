# Puki for iOS

The same game as the Android app (all ten islands, My Day, the drawing studio, stickers, bedtime, levels,
the time limit, Puki's on-device brain), written in SwiftUI. The Android sources stay the reference: each
Swift file follows its Kotlin twin, and the art drawn in code goes through `UI/Common/Draw.swift`, which
mirrors Compose's DrawScope calls.

## Build

Needs a Mac with Xcode 16 and [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
python3 ios/scripts/build_assets.py    # vectors -> SVG imagesets, the icon, OGG voices -> AAC (needs ffmpeg)
cd ios && xcodegen                      # makes Puki.xcodeproj from project.yml
open Puki.xcodeproj                     # iPad or iPhone, landscape only
```

The generated assets are checked in, so `build_assets.py` is only needed after the Android art or voices
change. `python3 ios/scripts/sync_safety_swift.py` copies the safety word lists from `Safety.kt` (which
comes from `brain/safety.py`) into `Brain/Safety.swift`; CI checks they match (`--check`).

CI (`.github/workflows/ios.yml`) builds the app and runs `PukiTests` on a simulator on every push.

## What's different on iOS

- **Updates** come from the App Store: no self-updater, and settings shows only the version.
- **No quit button** on the hub (iOS apps don't close themselves).
- **Saved drawings** go to the photo library (add-only access, asked once); Android uses a "Puki" album.
- **Puki's brain** downloads into Application Support, excluded from iCloud backup, with the same pinned
  URLs, sizes and SHA-256 as `DeviceModels.kt`. It's offered on devices with at least 2.5 GB of RAM.
  ONNX Runtime comes from its Swift package (`onnxruntime-swift-package-manager`).
- Voices are AAC (`.m4a`) instead of OGG; the lines and their keys are the same.

Privacy is the same as on Android: no network but the model download a parent asks for, nothing about the
child in logs (errors are logged by type), what the child said kept in memory only, and the same safety nets
(`Safety.swift`, `Persona.swift`) in front of every model line.

## Uploading to TestFlight / the App Store

`.github/workflows/testflight.yml` archives a signed release on GitHub's macOS runner and uploads it to App
Store Connect. Run it by hand from the Actions tab (TestFlight → Run workflow). The bundle ID is
`io.github.naimdor.puki` and the team is `DA3SQCMQ26`.

Before the first run:

1. In App Store Connect, create the app (My Apps → +) with bundle ID `io.github.naimdor.puki` (register the ID
   under Certificates, Identifiers & Profiles first if it isn't offered).
2. Create an API key under Users and Access → Integrations → App Store Connect API, with the **Admin** role
   (so Xcode can create the distribution certificate and profile on its own). Download the `.p8` once.
3. In the GitHub repo, add the secrets `ASC_ISSUER_ID`, `ASC_KEY_ID` and `ASC_KEY_P8` (the whole `.p8` text).

Each run's build number is 96 + the run number, so it always goes up. Puki is for children, so in App Store
Connect pick the Kids category (ages 5 and under), link the privacy policy (`docs/privacy.html`), and answer
the privacy questions as "Data Not Collected".
