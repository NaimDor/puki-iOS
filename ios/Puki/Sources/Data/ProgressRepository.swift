import Foundation
import Combine

/** Stars, stickers and each game's level (DataStore's "progress" on Android, same keys). */
@MainActor
final class ProgressRepository: ObservableObject {
    static let maxLevel = 3

    /** The rule behind `recordSession`: the new (level, easy-session streak). */
    nonisolated static func nextLevel(level: Int, streak: Int, rounds: Int, mistakes: Int) -> (Int, Int) {
        if mistakes <= rounds / 2 {  // easy: at most one slip every other round
            return (streak + 1 >= 2 && level < maxLevel) ? (level + 1, 0) : (level, streak + 1)
        } else if mistakes >= rounds {  // a miss or a hint every round
            return (max(1, level - 1), 0)
        }
        return (level, 0)
    }

    @Published private(set) var progress = GameProgress()
    private let d: UserDefaults

    init(defaults: UserDefaults = UserDefaults(suiteName: "progress") ?? .standard) {
        d = defaults
        reload()
    }

    private func reload() {
        let stars = d.integer(forKey: "total_stars")
        progress = GameProgress(
            totalStars: stars,
            // (nothing stored yet: everything she has so far counts as seen)
            rewardedStars: d.object(forKey: "rewarded_stars") == nil ? stars : d.integer(forKey: "rewarded_stars"),
            albumSeenStars: d.object(forKey: "album_seen_stars") == nil ? stars : d.integer(forKey: "album_seen_stars")
        )
    }

    func addStars(_ count: Int) {
        d.set(d.integer(forKey: "total_stars") + count, forKey: "total_stars")
        reload()
    }

    func markRewarded(_ stars: Int) { d.set(stars, forKey: "rewarded_stars"); reload() }
    func markAlbumSeen(_ stars: Int) { d.set(stars, forKey: "album_seen_stars"); reload() }

    /** A game's level, 1 (easiest) to `maxLevel`. */
    func difficulty(_ island: String) -> Int {
        let v = d.object(forKey: "difficulty_\(island)") == nil ? 1 : d.integer(forKey: "difficulty_\(island)")
        return min(max(v, 1), Self.maxLevel)
    }

    /**
     * After a session of `rounds` rounds with `mistakes` wrong answers: two easy sessions in a
     * row move the game up a level, and a hard one moves it back down. Returns the new level.
     */
    @discardableResult
    func recordSession(_ island: String, rounds: Int, mistakes: Int) -> Int {
        let (next, streak) = Self.nextLevel(level: difficulty(island), streak: d.integer(forKey: "clean_streak_\(island)"),
                                            rounds: rounds, mistakes: mistakes)
        d.set(next, forKey: "difficulty_\(island)")
        d.set(streak, forKey: "clean_streak_\(island)")
        return next
    }
}
