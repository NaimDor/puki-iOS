import XCTest
@testable import Puki

/** The level rule (LevelRuleTest on Android): two easy sessions move a game up, a hard one moves it down. */
final class LevelRuleTests: XCTestCase {
    func testTwoEasySessionsMoveUp() {
        var (level, streak) = ProgressRepository.nextLevel(level: 1, streak: 0, rounds: 5, mistakes: 0)
        XCTAssertEqual(level, 1); XCTAssertEqual(streak, 1)
        (level, streak) = ProgressRepository.nextLevel(level: level, streak: streak, rounds: 5, mistakes: 2)
        XCTAssertEqual(level, 2); XCTAssertEqual(streak, 0)
    }

    func testAHardSessionMovesDown() {
        let (level, streak) = ProgressRepository.nextLevel(level: 3, streak: 1, rounds: 5, mistakes: 5)
        XCTAssertEqual(level, 2); XCTAssertEqual(streak, 0)
    }

    func testNeverBelowOneOrAboveMax() {
        XCTAssertEqual(ProgressRepository.nextLevel(level: 1, streak: 0, rounds: 5, mistakes: 9).0, 1)
        XCTAssertEqual(ProgressRepository.nextLevel(level: ProgressRepository.maxLevel, streak: 5, rounds: 5, mistakes: 0).0, ProgressRepository.maxLevel)
    }

    func testInBetweenKeepsTheLevelAndResetsTheStreak() {
        let (level, streak) = ProgressRepository.nextLevel(level: 2, streak: 1, rounds: 6, mistakes: 4)
        XCTAssertEqual(level, 2); XCTAssertEqual(streak, 0)
    }
}
