import SwiftUI

/** "Puki on this device": pick his languages, download the models once, then think here. */
struct DeviceBrainCard: View {
    @ObservedObject var models: DeviceModels
    @ObservedObject private var download = BrainDownload.shared
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.appSettings) private var settings

    private func t(_ he: String, _ en: String) -> String { BilingualText(he, en).resolve(language) }

    var body: some View {
        let progress = models.progress
        let langs = models.languagesFor(settings.brainLanguages, game: settings.language)
        let ready = progress == nil && models.ready(langs)
        let size = sizeText(models.totalBytes(langs))
        let missing = sizeText(models.missingBytes(langs))
        VStack(alignment: .leading, spacing: 12) {
            Text(t("פוקי במכשיר הזה", "Puki on this device")).font(PukiFont.titleLarge).foregroundColor(PukiColors.earthBrown)
            Text(t("פוקי יכול לחשוב, לדבר ולהקשיב כאן במכשיר, בלי אינטרנט: גם מחוץ לבית. מורידים פעם אחת (עדיף ב-Wi-Fi), בשפה שבוחרים:",
                   "Puki can think, talk and listen right here on this device, with no internet: even away from home. It's a one-time download (Wi-Fi is best), in the languages you pick:"))
                .font(PukiFont.bodyMedium).foregroundColor(PukiColors.earthBrown)
            BrainLanguagePicker(selected: langs, language: language, enabled: progress == nil) { app.setBrainLanguages($0) }
            if let p = progress {
                ProgressView(value: Double(p)).tint(PukiColors.grassGreen)
                Text(t("מוריד… \(Int(p * 100))%", "Downloading… \(Int(p * 100))%")).font(PukiFont.bodyMedium).foregroundColor(PukiColors.earthBrown)
            } else {
                // What the pick still needs (a whole brain, or one more language's reader)
                if !ready {
                    Button { app.downloadDeviceBrain(langs) } label: {
                        Text(t("הורדה (\(missing))", "Download (\(missing))")).font(PukiFont.titleMedium).foregroundColor(PukiColors.cloudWhite)
                            .frame(maxWidth: .infinity).padding(14)
                            .background(RoundedRectangle(cornerRadius: 16).fill(PukiColors.grassGreen))
                    }
                    .buttonStyle(.plain)
                }
                // The rest whenever there's a brain on the device (or memory kept, so it can always be turned off)
                if models.ready() || settings.rememberBetween { controls(size) }
            }
            if let e = download.error {
                Text(t("ההורדה נעצרה: \(e). אפשר ללחוץ שוב כדי להמשיך.", "The download stopped: \(e). Tap again to carry on."))
                    .font(PukiFont.bodyMedium).foregroundColor(PukiColors.earthBrown)
            }
        }
        .padding(24)
        .frame(width: 400)
        .background(RoundedRectangle(cornerRadius: 24).fill(PukiColors.cloudWhite))
    }

    /** Thinking on the device, remembering between sessions, and removing it all. */
    private func controls(_ size: String) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Toggle(isOn: Binding(get: { settings.brainOnDevice }, set: { app.settingsRepository.updateBrainOnDevice($0) })) {
                Text(t("לחשוב במכשיר הזה", "Think on this device")).font(PukiFont.titleMedium).foregroundColor(PukiColors.earthBrown)
            }
            .tint(PukiColors.grassGreen)
            // Off unless the parent turns it on: the site promises nothing outlives the session
            Toggle(isOn: Binding(get: { settings.rememberBetween }, set: { app.settingsRepository.updateRememberBetween($0) })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(t("פוקי זוכר בין משחקים", "Puki remembers between sessions")).font(PukiFont.titleMedium).foregroundColor(PukiColors.earthBrown)
                    Text(t("כבוי: פוקי שוכח הכל כשסוגרים את המשחק. דולק: הוא זוכר את המשחקים של היום לסיכום לפני השינה, ואת היומיים האחרונים (\"כמו הפרה אתמול!\"). רק בטאבלט, אף פעם לא מה שהילדים אמרו, ונמחק כשמכבים.",
                           "Off: Puki forgets it all when the game closes. On: he keeps today's games for the bedtime recap, and the last two days (\"like the cow yesterday!\"). Only on this tablet, never what the child said, and deleted when you turn it off."))
                        .font(.footnote).foregroundColor(PukiColors.earthBrown.opacity(0.75))
                }
            }
            .tint(PukiColors.grassGreen)
            Button { app.removeDeviceBrain() } label: {
                Text(t("הסרה (מפנה \(size))", "Remove (frees \(size))")).font(PukiFont.bodyMedium).foregroundColor(PukiColors.earthBrown.opacity(0.7)).padding(.vertical, 4)
            }
            .buttonStyle(.plain)
        }
    }
}
