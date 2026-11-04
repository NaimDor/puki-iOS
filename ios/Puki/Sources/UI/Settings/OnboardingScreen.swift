import SwiftUI

private let ink = Color(argb: 0xFF5D4037)
private let cream = Color(argb: 0xFFFFF8E1)
private let wood = Color(argb: 0xFFBC8F5F)

/**
 * Shown once, on first launch, for a parent: language, the child's name and gender, whether to use the parrot, and
 * (on devices that can run it) his brain's download. Everything can be changed later in parent settings.
 */
struct OnboardingScreen: View {
    let settings: AppSettings
    @EnvironmentObject private var app: AppModel
    @State private var name: String
    @FocusState private var nameFocused: Bool

    init(settings: AppSettings) {
        self.settings = settings
        _name = State(initialValue: settings.childName)
    }

    private func t(_ he: String, _ en: String) -> String { BilingualText(he, en).resolve(settings.language) }
    private var repo: SettingsRepository { app.settingsRepository }

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(argb: 0xFF81D4FA), Color(argb: 0xFFE1F5FE)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            ScrollView {
                VStack(spacing: 12) {
                    Pic("ic_companion_parrot").frame(width: 80, height: 80)
                    Text(t("ברוכים הבאים לפוקי!", "Welcome to Puki!")).font(PukiFont.headlineMedium).foregroundColor(ink)
                    Text(t("כמה פרטים קטנים, ומתחילים לשחק.", "A few quick details, and then it's playtime.")).font(PukiFont.bodyLarge).foregroundColor(ink)

                    // Language, and girl/boy (for Hebrew grammar: "תלחצי" / "תלחץ"), on one row
                    HStack(spacing: 12) {
                        Choice(label: "עברית", selected: settings.language == .hebrew) { repo.updateLanguage(.hebrew) }
                        Choice(label: "English", selected: settings.language == .english) { repo.updateLanguage(.english) }
                        Spacer().frame(width: 16)
                        Choice(label: t("בת", "Girl"), icon: "ic_ui_girl", selected: settings.gender == .girl) { repo.updateGender(.girl) }
                        Choice(label: t("בן", "Boy"), icon: "ic_ui_boy", selected: settings.gender == .boy) { repo.updateGender(.boy) }
                    }

                    // Child's name
                    VStack(alignment: .leading, spacing: 4) {
                        Text(t("איך קוראים לילד/ה?", "What's your child's name?")).font(PukiFont.bodyMedium).foregroundColor(ink)
                        TextField("", text: $name)
                            .textFieldStyle(.roundedBorder)
                            .foregroundColor(ink)
                            .focused($nameFocused)
                            .submitLabel(.done)
                            .onSubmit { nameFocused = false }
                            .onChange(of: name) { _, v in if v.count > 30 { name = String(v.prefix(30)) } }
                    }
                    .frame(minWidth: 320, maxWidth: 360)

                    // The parrot companion
                    HStack(spacing: 12) {
                        Text(t("התוכי פוקי", "Puki the parrot")).font(PukiFont.titleLarge).foregroundColor(ink)
                        Toggle("", isOn: Binding(get: { settings.companionEnabled }, set: { repo.updateCompanionEnabled($0) })).labelsHidden()
                    }

                    // His brain: offered here so parents know it exists; it downloads only if they tap
                    if settings.companionEnabled && DeviceModels.offered && DeviceModels.capable() {
                        BrainOffer(models: app.deviceModels, settings: settings)
                    }

                    Button {
                        repo.updateChildName(name)
                        repo.setOnboardingDone()
                    } label: {
                        Text(t("יאללה, משחקים!", "Let's play!")).font(PukiFont.headlineMedium).foregroundColor(.white)
                            .padding(.horizontal, 40).padding(.vertical, 14)
                            .background(Capsule().fill(Color(argb: 0xFF66BB6A)).shadow(color: .black.opacity(0.25), radius: 5, y: 3))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 28).padding(.vertical, 18)
                .background(RoundedRectangle(cornerRadius: 28).fill(cream).shadow(color: .black.opacity(0.25), radius: 8, y: 4))
                .overlay(RoundedRectangle(cornerRadius: 28).stroke(wood, lineWidth: 3))
                .frame(maxWidth: 640)
                .padding(12)
                .frame(maxWidth: .infinity)
            }
        }
        .environment(\.language, settings.language)
        .environment(\.layoutDirection, settings.language == .hebrew ? .rightToLeft : .leftToRight)
    }
}

private struct BrainOffer: View {
    @ObservedObject var models: DeviceModels
    let settings: AppSettings
    @EnvironmentObject private var app: AppModel
    private func t(_ he: String, _ en: String) -> String { BilingualText(he, en).resolve(settings.language) }

    var body: some View {
        let langs = models.languagesFor(settings.brainLanguages, game: settings.language)
        let missing = models.missingBytes(langs)
        let size = sizeText(missing > 0 ? missing : models.totalBytes(langs))
        VStack(spacing: 8) {
            Text(t("פוקי יכול לדבר באמת, עם מוח משלו שרץ בטאבלט הזה, בלי אינטרנט. מורידים אותו פעם אחת (\(size), עדיף ב-Wi-Fi), בשפה שבוחרים:",
                   "Puki can really talk, with a brain of his own that runs on this tablet, with no internet. It's a one-time download (\(size), Wi-Fi is best), in the languages you pick:"))
                .font(PukiFont.bodyLarge).foregroundColor(ink)
            if models.progress != nil {
                Text(t("מוריד ברקע. כשזה ייגמר, פוקי יתחיל לדבר.", "Downloading in the background. When it's done, Puki starts talking.")).font(PukiFont.bodyMedium.bold()).foregroundColor(ink)
            } else if models.ready(langs) {
                Text(t("המוח של פוקי כבר כאן.", "Puki's brain is already here.")).font(PukiFont.bodyMedium.bold()).foregroundColor(ink)
            } else {
                BrainLanguagePicker(selected: langs, language: settings.language) { app.setBrainLanguages($0) }
                Choice(label: t("הורדה עכשיו", "Download now"), selected: false) { app.downloadDeviceBrain(langs) }
                Text(t("או אחר כך, בהגדרות ההורים: \"פוקי במכשיר הזה\".", "Or later, in the parent settings: \"Puki on this device\"."))
                    .font(PukiFont.bodyMedium).foregroundColor(ink.opacity(0.75))
            }
        }
        .padding(14)
        .frame(maxWidth: 520)
        .background(RoundedRectangle(cornerRadius: 20).fill(Color.white))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(wood, lineWidth: 1))
    }
}

private struct Choice: View {
    let label: String
    var icon: String? = nil
    let selected: Bool
    let onClick: () -> Void
    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 8) {
                if let icon { Pic(icon).frame(width: 36, height: 36) }
                Text(label).font(PukiFont.titleLarge.bold()).foregroundColor(ink)
            }
            .padding(.horizontal, 22).padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 20).fill(selected ? Color(argb: 0xFFFFE082) : .white))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(selected ? Color(argb: 0xFFFFA000) : wood, lineWidth: selected ? 3 : 1))
        }
        .buttonStyle(.plain)
    }
}
