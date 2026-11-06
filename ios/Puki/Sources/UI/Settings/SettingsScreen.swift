import SwiftUI

/** The parents' settings: language, the child, sounds, Puki, the islands on the map, the time limit, the brain. */
struct SettingsScreen: View {
    let onBack: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.appSettings) private var settings
    @State private var childName = ""
    @State private var musicLevel: Float = 1
    @State private var voiceLevel: Float = 1
    @State private var sfxLevel: Float = 1
    @FocusState private var nameFocused: Bool

    private func t(_ he: String, _ en: String) -> String { BilingualText(he, en).resolve(language) }
    private var repo: SettingsRepository { app.settingsRepository }

    var body: some View {
        ZStack(alignment: .topLeading) {
            PukiColors.skyBlue.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 0) {
                    Text(t("הגדרות", "Settings")).font(PukiFont.headlineLarge).foregroundColor(PukiColors.cloudWhite)
                    Spacer().frame(height: 32)
                    mainCard
                    // Puki's brain on this device (only offered where it can run)
                    if DeviceModels.offered && DeviceModels.capable() {
                        Spacer().frame(height: 24)
                        DeviceBrainCard(models: app.deviceModels)
                    }
                    Spacer().frame(height: 24)
                    // מלשין לרנינג: what Puki said, with a flag for anything that wasn't right
                    ReportCard(companion: app.companion)
                    Spacer().frame(height: 24)
                    // Updates come from the App Store: just the version, for bug reports
                    Text("Puki \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")")
                        .font(PukiFont.bodyLarge).foregroundColor(PukiColors.earthBrown)
                    Spacer().frame(height: 16)
                    AboutCard()
                    Spacer().frame(height: 32)
                    Button(action: onBack) {
                        Text(t("חזרה", "Back")).font(PukiFont.titleLarge).foregroundColor(PukiColors.earthBrown)
                            .frame(width: 120, height: 60).background(Capsule().fill(PukiColors.sunYellow))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("settingsBack")
                }
                .padding(32)
                .frame(maxWidth: .infinity)
            }
            // Back to the hub
            BackButton(onBack: onBack).padding(16)
        }
        .onAppear {
            childName = settings.childName
            musicLevel = settings.musicVolume
            voiceLevel = settings.voiceVolume
            sfxLevel = settings.sfxVolume
        }
        .onChange(of: nameFocused) { _, f in if !f { repo.updateChildName(childName) } }
        .onDisappear { repo.updateChildName(childName) }
    }

    private var mainCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            ToggleRow(label: t("שפה", "Language"), icon: "ic_ui_globe", checked: language == .hebrew, labelOn: "עברית", labelOff: "English") {
                app.toggleLanguage(language)
            }
            ToggleRow(label: t("ילד/ה", "Boy/Girl"), icon: "ic_ui_child", checked: settings.gender == .girl, labelOn: t("ילדה", "Girl"),
                      labelOff: t("ילד", "Boy"), labelOnIcon: "ic_ui_girl", labelOffIcon: "ic_ui_boy") { app.toggleGender(settings.gender) }
            // The child's name: the parrot and the personal voice lines use it
            VStack(alignment: .leading, spacing: 4) {
                Text(t("שם הילד/ה", "Child's name")).font(PukiFont.bodyMedium).foregroundColor(PukiColors.earthBrown)
                TextField("", text: $childName)
                    .textFieldStyle(.roundedBorder)
                    .focused($nameFocused)
                    .submitLabel(.done)
                    .onSubmit { repo.updateChildName(childName) }
                    .onChange(of: childName) { _, v in if v.count > 30 { childName = String(v.prefix(30)) } }
            }
            ToggleRow(label: t("צלילים", "Sounds"), icon: "ic_ui_speaker", checked: settings.soundEnabled, labelOn: t("פועל", "On"), labelOff: t("כבוי", "Off")) {
                repo.updateSound(!settings.soundEnabled)
            }
            ToggleRow(label: t("מוזיקה", "Music"), icon: "ic_ui_music_note", checked: settings.musicEnabled, labelOn: t("פועל", "On"), labelOff: t("כבוי", "Off")) {
                repo.updateMusic(!settings.musicEnabled)
            }
            // Volumes: they apply while dragging, and are saved when the finger lifts
            VolumeRow(label: t("עוצמת מוזיקה", "Music volume"), icon: "ic_ui_music_note", value: $musicLevel,
                      onChange: { app.music.level = $0 }) { repo.updateVolumes(music: musicLevel) }
            VolumeRow(label: t("עוצמת קולות ופוקי", "Voices and Puki"), icon: "ic_companion_parrot", value: $voiceLevel,
                      onChange: { app.voice.level = $0 }) {
                repo.updateVolumes(voice: voiceLevel)
                app.voice.playPraise(language)  // a sample at the new volume
            }
            VolumeRow(label: t("עוצמת צלילים", "Sound effects"), icon: "ic_ui_speaker", value: $sfxLevel,
                      onChange: { app.sfx.level = $0 }) {
                repo.updateVolumes(sfx: sfxLevel)
                app.sfx.play(.pop)
            }
            ToggleRow(label: t("התוכי פוקי", "Puki the parrot"), icon: "ic_companion_parrot", checked: settings.companionEnabled,
                      labelOn: t("פועל", "On"), labelOff: t("כבוי", "Off")) { repo.updateCompanionEnabled(!settings.companionEnabled) }
            if settings.companionEnabled {
                // Answering the games out loud; switching it on asks for the microphone if needed
                ToggleRow(label: t("עונים בקול", "Answer out loud"), icon: "ic_companion_parrot", checked: settings.answersOutLoud,
                          labelOn: t("פועל", "On"), labelOff: t("כבוי", "Off")) {
                    let on = !settings.answersOutLoud
                    repo.updateAnswersOutLoud(on)
                    if on && !hasMicPermission() { requestMicPermission { _ in } }
                }
                Text(t("אחרי שאלה במשחק (\"איזה צבע?\", \"כמה?\", \"מי מתחבא?\") פוקי מקשיב כמה שניות, ואפשר לענות בקול במקום ללחוץ. ההקלטה נשארת במכשיר.",
                       "After a question in a game (\"which color?\", \"how many?\", \"who's hiding?\") Puki listens for a few seconds, so the answer can be said instead of tapped. The recording stays on this device."))
                    .font(PukiFont.bodyMedium).foregroundColor(PukiColors.earthBrown)
            }
            // Which games are on the map: fewer islands, fewer choices at two
            Text(t("משחקים במפה", "Games on the map")).font(PukiFont.titleLarge).foregroundColor(PukiColors.earthBrown)
            Text(t("לחיצה מסתירה משחק או מחזירה אותו. פחות איים, פחות בחירות לקטנטנים.", "Tap to hide a game or bring it back. Fewer islands, fewer choices for little ones."))
                .font(PukiFont.bodyMedium).foregroundColor(PukiColors.earthBrown)
            let rows = stride(from: 0, to: allIslands.count, by: 3).map { Array(allIslands[$0..<min($0 + 3, allIslands.count)]) }
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: 8) {
                    ForEach(row, id: \.id) { island in
                        let shown = !settings.hiddenIslands.contains(island.id)
                        let lastOne = shown && allIslands.filter { !settings.hiddenIslands.contains($0.id) }.count == 1
                        Button {
                            var hidden = settings.hiddenIslands
                            if shown { hidden.insert(island.id) } else { hidden.remove(island.id) }
                            repo.updateHiddenIslands(hidden)
                        } label: {
                            Text((shown ? "✓ " : "") + island.name.resolve(language))
                                .font(PukiFont.bodyLarge).foregroundColor(shown ? PukiColors.cloudWhite : PukiColors.earthBrown)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 10).padding(.vertical, 8)
                                .background(RoundedRectangle(cornerRadius: 14).fill(shown ? PukiColors.grassGreen : Color(argb: 0xFFEFEBE9)))
                        }
                        .buttonStyle(.plain)
                        .disabled(lastOne)
                    }
                    ForEach(0..<(3 - row.count), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity, maxHeight: 1) }
                }
            }
            // Time limit
            HStack {
                Pic("ic_ui_clock").frame(width: 36, height: 36)
                Text(t("הגבלת זמן", "Time Limit")).font(PukiFont.titleLarge).foregroundColor(PukiColors.earthBrown)
                Spacer()
                Button { app.cycleTimeLimit(settings.timeLimitMinutes) } label: {
                    Text(settings.timeLimitMinutes == 0 ? t("ללא", "Off") : "\(settings.timeLimitMinutes) \(t("דקות", "min"))")
                        .font(PukiFont.bodyLarge).foregroundColor(PukiColors.earthBrown)
                        .padding(.horizontal, 16).padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 12).fill(PukiColors.grassGreen.opacity(0.2)))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(24)
        .frame(width: 400)
        .background(RoundedRectangle(cornerRadius: 24).fill(PukiColors.cloudWhite))
    }
}

private struct ToggleRow: View {
    let label: String
    let icon: String
    let checked: Bool
    let labelOn: String
    let labelOff: String
    var labelOnIcon: String? = nil
    var labelOffIcon: String? = nil
    let onToggle: () -> Void
    var body: some View {
        HStack {
            Pic(icon).frame(width: 36, height: 36)
            Text(label).font(PukiFont.titleLarge).foregroundColor(PukiColors.earthBrown).lineLimit(1).minimumScaleFactor(0.6)
            Spacer()
            if let i = checked ? labelOnIcon : labelOffIcon { Pic(i).frame(width: 32, height: 32) }
            Text(checked ? labelOn : labelOff).font(PukiFont.bodyLarge).foregroundColor(PukiColors.earthBrown)
            Toggle("", isOn: Binding(get: { checked }, set: { _ in onToggle() })).labelsHidden().tint(PukiColors.grassGreen)
                .accessibilityIdentifier("toggle.\(icon)")
        }
    }
}

/** A volume slider with an icon and the level in percent. */
private struct VolumeRow: View {
    let label: String
    let icon: String
    @Binding var value: Float
    let onChange: (Float) -> Void
    let onDone: () -> Void
    var body: some View {
        VStack(spacing: 2) {
            HStack {
                Pic(icon).frame(width: 32, height: 32)
                Text(label).font(PukiFont.titleMedium).foregroundColor(PukiColors.earthBrown)
                Spacer()
                Text("\(Int((value * 100).rounded()))%").font(PukiFont.titleMedium).foregroundColor(PukiColors.earthBrown)
            }
            Slider(value: $value, in: 0...1) { editing in if !editing { onDone() } }
                .onChange(of: value) { _, v in onChange(v) }
        }
    }
}

/** Who made Puki and what it's built with; the full license texts open in a sheet. */
private struct AboutCard: View {
    @Environment(\.language) private var language
    @State private var licenses: String?
    var body: some View {
        VStack(spacing: 6) {
            Text(BilingualText("פוקי חינמי ובקוד פתוח (רישיון MIT), נוצר על ידי תום שדה. תודה ל-BlueTTS, ל-RenikudPlus, ל-kol-stream, ל-ivrit.ai, ל-LibriSpeech, ל-FineWeb, ל-Cosmopedia, ל-Common Pile, ל-espeak-ng, ל-DeepSeek ול-ONNX Runtime.",
                               "Puki is free and open source (MIT license), made by Tom Sadeh. With thanks to BlueTTS, RenikudPlus, kol-stream, ivrit.ai, LibriSpeech, FineWeb, Cosmopedia, the Common Pile, espeak-ng, DeepSeek and ONNX Runtime.").resolve(language))
                .font(PukiFont.bodyMedium).foregroundColor(PukiColors.earthBrown).multilineTextAlignment(.center)
            Text(BilingualText("שאלות? \(Contact.email)", "Questions? \(Contact.email)").resolve(language))
                .font(PukiFont.bodyMedium).foregroundColor(PukiColors.earthBrown).multilineTextAlignment(.center)
            HStack(spacing: 12) {
                // (the store asks for the privacy policy inside the app too; these settings are behind the parent check)
                Link(BilingualText("מדיניות פרטיות", "Privacy policy").resolve(language), destination: Contact.privacyPolicy)
                    .buttonStyle(.bordered).tint(PukiColors.earthBrown)
                Button(BilingualText("רישיונות", "Licenses").resolve(language)) {
                    licenses = Bundle.main.url(forResource: "licenses", withExtension: "txt").flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? ""
                }
                .buttonStyle(.bordered).tint(PukiColors.earthBrown)
            }
        }
        .frame(maxWidth: 520)
        .sheet(isPresented: Binding(get: { licenses != nil }, set: { if !$0 { licenses = nil } })) {
            VStack {
                // The texts are English, left to right whatever the app's language
                ScrollView { Text(licenses ?? "").font(.footnote).foregroundColor(PukiColors.earthBrown).frame(maxWidth: .infinity, alignment: .leading) }
                    .environment(\.layoutDirection, .leftToRight)
                HStack { Spacer(); Button(BilingualText("סגירה", "Close").resolve(language)) { licenses = nil }.tint(PukiColors.earthBrown) }
            }
            .padding(16)
        }
    }
}
