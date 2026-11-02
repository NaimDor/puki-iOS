# Putting Puki on Google Play

Everything Play asks for, in order. The texts and images are in `metadata/android/` next to this
file: `en-US` and `iw-IL` (Play's code for Hebrew). Play's own forms change now and then, so
expect the labels to differ a little.

## 0. Before the first upload (0.9.0)

- [ ] Brains is on Hugging Face (`dtsj89/puki-brains`), with Beak and Syrinx, and
      `DeviceModels.OFFERED` is `true`, so settings offer the device brain the listing describes.
- [x] `Contact.REPORT_EMAIL` is set (`hello+report@pukiparrot.com`, 0.9.1). Play's AI-generated content policy wants a way to flag
      offensive output to the developer from inside the app; that's the 🚩 in "What Puki said",
      and it only reaches you through this address.
- [ ] `pukiVersion` is 0.9.0, and the baseline profile is regenerated (it still names the PC
      pairing classes).
- [ ] The website commit is pushed, so the listing's privacy policy link shows the new policy.

## 1. The developer account (only the account owner can do this)

- [ ] Sign up at https://play.google.com/console as a **personal** account: a one-time **$25** fee,
      plus identity verification with a government ID.
- [ ] Pick the developer name shown on the store, and a contact email: `hello@pukiparrot.com`. The email is shown
      publicly, so a separate address is a good idea.

## 2. Create the app

- [ ] Create app: name "Puki: Toddler Games", default language English (en-US) or Hebrew (iw-IL),
      **App**, **Free**. (Free can never be changed to paid, which suits the promise.)
- [ ] Add the second language under the main store listing.

## 3. App signing: keep the same key as the GitHub APKs (important)

Play re-signs what it delivers. If Play makes a new key, the Play version and the GitHub APK
won't install over each other, and switching between them means uninstalling, which loses the
child's progress and Puki's downloaded brain. To avoid that, give Play the existing release key:

- [ ] At the first upload, under **App integrity → App signing**, choose to use your own key
      ("Export and upload a key from Java keystore") and follow Play's PEPK steps with
      `~/keys/puki-release.jks` (alias `puki`). Run it yourself: it asks for the key's password.
- [ ] Play then asks for an **upload key**. Simplest: use the same key (it's the one the
      build already signs with).

The certificate's SHA-256, to check against what Play shows:
`a7:99:ca:b5:bc:f8:3c:20:c7:54:49:0f:8d:0e:b2:71:cf:84:4e:2b:7e:69:51:87:60:78:9f:2a:96:eb:3c:3d`

## 4. The bundle

```bash
./gradlew bundlePlayRelease
```

Upload `app/build/outputs/bundle/playRelease/app-play-release.aab`. This is the `play` flavor:
no self-updater and no REQUEST_INSTALL_PACKAGES (Play doesn't allow apps to update themselves).
It targets API 36, and its native libraries meet the 16 KB page rule.

## 5. Closed testing: 12 testers for 14 days

New personal accounts must run a closed test before going public:

- [ ] Testing → Closed testing → create a track, upload the .aab, and add at least **12 testers**
      (a Google Group or a list of Gmail addresses). Family and friends with Android phones work.
- [ ] Each tester opts in through the link Play gives and **keeps the app installed for 14 days
      in a row**. Ask them to open it now and then; Play asks whether testing was real.
- [ ] After 14 days: **Apply for production** and answer the short questions about the test.

## 6. Store listing

From `metadata/android/<language>/`:

- [ ] App name: `title.txt`. Short description: `short_description.txt`. Full description:
      `full_description.txt`.
- [ ] App icon (512×512): `images/icon.png`.
- [ ] Feature graphic (1024×500): `images/featureGraphic.png` (one per language).
- [ ] Screenshots: `en-US/images/phoneScreenshots/`. Upload the same files as phone, 7-inch and
      10-inch tablet screenshots, for both languages (they're tablet shots, 1340×800).
- [ ] Category: **Games → Educational**. Tags: toddlers, educational, family.
- [ ] Website: https://pukiparrot.com/
- [ ] Privacy policy: **https://pukiparrot.com/privacy.html**

## 7. App content forms (Policy → App content)

**Privacy policy:** https://pukiparrot.com/privacy.html

**Ads:** No, the app contains no ads.

**App access:** All functionality is available without special access. Note for reviewers:
"Every game works on its own. Puki's talking brain is optional: in the parent settings, the
'Puki on this device' card downloads it once (about 0.5 GB, from Hugging Face), and it then runs
entirely on the device, offline. Without it the parrot uses recorded lines. Parent settings open
by holding the gear button for 2 seconds, then tapping the number shown in words (e.g.
'seventeen' among six numbers). The device brain needs a 64-bit device with about 3 GB of
memory; on smaller devices the card isn't shown."

**Target audience and content:** ages **5 and under** (add 6–8 if wanted). This puts the app in
the **Families program**: the Families policy applies (no ads, no data-collecting SDKs, a parent
gate before settings). Puki has no ad or analytics SDKs; its libraries are ONNX Runtime (Puki's
brain on the device) and AndroidX.
- Appeal to children: yes.
- Teacher Approved: optional, can be requested later.

**AI-generated content:** Puki's lines are written by a small language model of his own,
on the device. If the console asks about generative AI, say yes and describe it: a 46M-parameter
model trained only to write a toddler's playmate's short lines; word lists and a safety check
before it answers, a filter on every line after, recorded lines when a line fails; parents see
every line in settings and can flag one to the developer (🚩, which opens their email).

**Content rating (IARC questionnaire):** category **Game**. Answer no to violence, fear, sexual
content, gambling, drugs, crude language and controlled substances. For user interaction:
users can't communicate with each other or share content, there's no location sharing and no
purchases. Expected rating: everyone / 3+.

**Data safety:** what the form asks, and Puki's answers. Play counts data as "collected" only
when it leaves the device. With the PC brain retired, nothing about the child does: the voice is
understood by the brain on the device and not kept, and the name and drawings stay on it.

- Does the app collect or share any of the required user data types? **No**
- What does go out, none of it user data: the model download from Hugging Face and GitHub (only
  when a parent taps it), and in the GitHub flavor only, the daily update check (the play
  flavor has none). A report to the developer goes from the parent's own email app, written and
  sent by them, and holds Puki's line and the game moment, not the child's words (ReportCard's
  share text); Play treats that as the user's own action, not collection. The child's words are
  kept in memory only, for the parent's list, and never written to a file.
- With "No", the form skips the data types, encryption and deletion questions. If it still asks
  about deletion: nothing is held by the developer; uninstalling deletes everything.

**News app:** No. **COVID-19:** No. **Government app:** No. **Financial features:** none.
**Health:** none.

**Permissions declarations:** RECORD_AUDIO (talking to Puki, the voice games, and answering a
game's question out loud), plus INTERNET for the model download. There's no camera anymore. No
sensitive-permission forms are needed for the play flavor.

## 8. After it's public

- [ ] Add the Play badge to the website and README (ask Claude).
- [ ] Each release: bump `pukiVersion`, `python scripts/release.py --publish` for GitHub, then
      `./gradlew bundlePlayRelease` and upload the .aab to Play (production or a testing track).
