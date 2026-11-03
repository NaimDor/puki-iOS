import Foundation

/**
 * The bookkeeping every round-based game does: its level, the slips this session, stars saved as they're earned,
 * and the session recorded at the end (which may move the level, see ProgressRepository.recordSession).
 */
@MainActor
final class GameSession {
    private let island: String
    private let progress: ProgressRepository
    private var mistakes = 0

    init(_ island: String, _ progress: ProgressRepository) {
        self.island = island
        self.progress = progress
    }

    /** This game's level, 1 (easiest) to ProgressRepository.maxLevel. */
    func level() -> Int { progress.difficulty(island) }

    func star() { progress.addStars(1) }

    func slip() { mistakes += 1 }

    func finish(rounds: Int) {
        progress.recordSession(island, rounds: rounds, mistakes: mistakes)
        DayLog.record(RecapContent.fixed[island])  // (the games that are one thing all along: baked a cake, danced)
    }
}
