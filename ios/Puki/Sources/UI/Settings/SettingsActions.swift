import SwiftUI

/** What the parent changes in settings and on the welcome screen (Android's SettingsViewModel). */
@MainActor
extension AppModel {
    private var repo: SettingsRepository { settingsRepository }

    func toggleLanguage(_ current: Language) { repo.updateLanguage(current == .hebrew ? .english : .hebrew) }
    func toggleGender(_ current: Gender) { repo.updateGender(current == .girl ? .boy : .girl) }

    /** In the app's own task, so it carries on when the parent leaves settings (or the welcome screen). */
    func downloadDeviceBrain(_ langs: BrainLanguages) {
        BrainDownload.shared.error = nil
        let models = deviceModels, repo = repo
        Task {
            do {
                try await models.download(langs)
                // Once it's all in, Puki starts thinking on the device: that's what the parent asked for
                repo.updateBrainOnDevice(true)
            } catch {
                BrainDownload.shared.error = String(describing: type(of: error))
            }
        }
    }

    /** Which languages the brain is for; one already downloaded that's no longer picked is freed. */
    func setBrainLanguages(_ langs: BrainLanguages) {
        repo.updateBrainLanguages(langs)
        // (nothing to fetch: download() then only frees the reader that wasn't picked)
        if deviceModels.ready(langs) {
            let models = deviceModels
            Task { try? await models.download(langs) }
        }
    }

    func removeDeviceBrain() {
        repo.updateBrainOnDevice(false)
        repo.updateRememberBetween(false)  // (and what Puki remembered goes with him)
        let brain = localBrain, models = deviceModels
        Task.detached {
            brain.close()
            models.delete()
        }
    }

    func cycleTimeLimit(_ current: Int) {
        let options = [0, 15, 30, 45, 60]
        repo.updateTimeLimit(options[((options.firstIndex(of: current) ?? -1) + 1) % options.count])
    }
}

/** The brain's download error, kept while the download outlives the screen that started it. */
@MainActor final class BrainDownload: ObservableObject {
    static let shared = BrainDownload()
    @Published var error: String?
}

/** A download's size the way a parent reads it: MB under a gigabyte, else GB. */
func sizeText(_ bytes: Int64) -> String {
    bytes < 1_000_000_000 ? "\((bytes + 999_999) / 1_000_000) MB" : String(format: "%.1f GB", Double(bytes) / 1e9)
}

/** Hebrew, English or both: each language has its own reader (a few MB); the rest is shared. */
struct BrainLanguagePicker: View {
    let selected: BrainLanguages
    let language: Language
    var enabled = true
    let onPick: (BrainLanguages) -> Void
    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<3, id: \.self) { i in
                let option = [BrainLanguages.hebrew, .english, .both][i]
                let label = [BilingualText("עברית", "Hebrew"), BilingualText("אנגלית", "English"), BilingualText("שתיהן", "Both")][i]
                let on = option == selected
                Button { onPick(option) } label: {
                    Text(label.resolve(language)).font(PukiFont.titleMedium).foregroundColor(on ? PukiColors.cloudWhite : PukiColors.earthBrown)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 16).fill(on ? PukiColors.grassGreen : PukiColors.earthBrown.opacity(0.08)))
                }
                .buttonStyle(.plain)
                .disabled(!enabled || on)
            }
        }
    }
}
