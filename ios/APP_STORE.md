# Putting Puki on the App Store

What's automated, and the forms only you can fill in App Store Connect (appstoreconnect.apple.com → Apps → Puki).

## Already done for you

| What | Where |
|---|---|
| The build (signed, uploaded to TestFlight) | `.github/workflows/testflight.yml`, a tag `ios-v…` |
| Name, subtitle, description, keywords, promotional text, support and privacy URLs, copyright, review notes (Hebrew and English) | `ios/fastlane/metadata/` |
| Screenshots: iPhone 6.9" and iPad 13", Hebrew and English, 8 screens each | `ios/scripts/screenshots.py`, made by `.github/workflows/appstore.yml` |
| Uploading the texts and screenshots | `.github/workflows/appstore.yml` (Actions tab once on main, or a tag `ios-store-…`) |
| Support page and privacy policy | `ios/site/`, published by `.github/workflows/pages.yml` to https://naimdor.github.io/puki-iOS/ |
| Export compliance ("no non-exempt encryption") | `ITSAppUsesNonExemptEncryption = NO` in the app |
| Parent gate, no ads, no analytics, no third-party SDKs (Kids category rules) | in the app |

## Once, before anything else

1. **Support email.** `python3 ios/scripts/set_support_email.py you@example.com`, commit and push. It goes into the app
   ("Questions?" and "What Puki said" reports), the support page, the privacy policy and the review notes. The
   upload workflows refuse to run until it's set. **Then upload a new build:** build 98 still has the old address.
2. **GitHub Pages.** Repo → Settings → Pages → Source: **GitHub Actions**. If the deploy is refused from the
   branch, Settings → Environments → github-pages → add `claude/dazzling-faraday-untz5o` (or merge to main).
   Check that https://naimdor.github.io/puki-iOS/privacy.html opens before submitting: Apple visits it.

## The forms in App Store Connect

**App Information**
- Primary language: Hebrew (or English). Add the other one under Localizations if fastlane didn't.
- Category: **Education**, secondary **Games → Family** (set by the setup workflow). Apple has no separate Kids category any more: the age rating's age band (**5 and Under**) is what makes it a kids app.
- Content rights: **Yes, it contains third-party content, and I have the rights**: it's MIT-licensed code by Tom
  Sadeh, and the voice and speech models are used under their licenses (the list is in the app: Settings →
  Licenses, and in `ios/Puki/Resources/licenses.txt`).

**Age rating** (answer the questionnaire this way)
- Violence, sexual content, profanity, horror, drugs, alcohol, gambling, contests, medical information: **None**.
- Unrestricted web access: **No**. User-generated content shared with others: **No**. Messaging or chat: **No**.
- Advertising: **No**. In-app purchases: **No**.
- The app is in the Kids category, so the rating comes out 4+.

**App Privacy**
- Privacy policy URL: `https://naimdor.github.io/puki-iOS/privacy.html`
- "Do you or your third-party partners collect data from this app?" **No, we do not collect data from this app.**
  (Everything stays on the device; the one network use, the brain download, sends nothing about the user.)

**Pricing and Availability**
- Price: **Free**. Availability: all countries, or the ones you choose.

**The version (App Store tab → the version under "iOS App")**
- The texts and screenshots arrive from the App Store listing workflow. Look them over in both languages.
- Version number: the workflow sets it to the app's version (0.9.6). If you'd rather launch as 1.0, change
  `MARKETING_VERSION` in `ios/project.yml` first, and upload a new build.
- Build: pick the newest TestFlight build (one made after the support email was set).
- App Review Information: your name and phone number (Apple calls only if they need to). No sign-in is needed.
  The notes are filled in from `ios/fastlane/metadata/review_information/notes.txt`.
- Release: "Manually release this version" lets you choose the day.

Then **Add for Review** → **Submit**. Kids apps get a closer look; the review notes explain the parent gate
and the on-device brain, which is what reviewers usually ask about.
