import XCTest
@testable import Puki

/** KolEarsTest.kt: quiet speech is raised towards the training level, at most 10x, never lowered. */
final class KolEarsTests: XCTestCase {
    private func tone(_ amp: Float, _ n: Int = 16000) -> [Float] { (0..<n).map { amp * sin(Float($0) * 0.1) } }
    private func rms(_ x: [Float]) -> Float { sqrt(x.reduce(0) { $0 + $1 * $1 } / Float(x.count)) }

    func testQuietSpeechIsRaisedTowardsTheTrainingLevel() { XCTAssertEqual(rms(KolEars.louder(tone(0.02))), 0.1, accuracy: 0.01) }

    func testLoudAudioAndSilenceAreLeftAlone() {
        let loud = tone(0.5)
        XCTAssertEqual(KolEars.louder(loud), loud)
        let silence = [Float](repeating: 0, count: 16000)
        XCTAssertEqual(KolEars.louder(silence), silence)
    }

    func testTheBoostStopsAtTenTimes() { XCTAssertEqual(rms(KolEars.louder(tone(0.001))), 10 * rms(tone(0.001)), accuracy: 1e-4) }
}

/** VoiceTextTest.kt: the BlueTTS text steps. */
final class VoiceTextTests: XCTestCase {
    func testCollapsesRepeatedPunctuation() {
        XCTAssertEqual(VoiceText.prepareTextForSynthesis("יש!!!"), "יש!")
        XCTAssertEqual(VoiceText.prepareTextForSynthesis("מה???"), "מה?")
    }
    func testDropsHyphensInsideHebrewWords() { XCTAssertEqual(VoiceText.prepareTextForSynthesis("טה-דה!"), "טהדה!") }
    func testSqueezesSpaces() { XCTAssertEqual(VoiceText.prepareTextForSynthesis("  שלום    חבר  "), "שלום חבר") }
    func testStripsLanguageTags() { XCTAssertEqual(VoiceText.stripLangTags("<he>shalom</he>   <he>haver</he>"), "shalom haver") }
    func testChunksBySentence() {
        XCTAssertEqual(VoiceText.chunkText("One two. Three four. Five six.", maxLen: 12), ["One two.", "Three four.", "Five six."])
        XCTAssertEqual(VoiceText.chunkText("One two. Three four.", maxLen: 300), ["One two. Three four."])
    }
    func testEncodeEndsWithPunctuationAndPadsUnknowns() {
        let ids: [UInt32: Int] = [97: 1, 98: 2, 46: 3, 33: 4]  // a, b, ., !
        XCTAssertEqual(VoiceText.encode("ab", charToId: ids, padId: 0), [1, 2, 3])
        XCTAssertEqual(VoiceText.encode("a!", charToId: ids, padId: 0), [1, 4])
        XCTAssertEqual(VoiceText.encode("az", charToId: ids, padId: 0), [1, 0, 3])
    }
}

/** DeviceChecksTest.kt: the checks the device puts on the brain's lines that the trainer didn't have. */
final class DeviceChecksTests: XCTestCase {
    private var plan: StoryContent.Plan {
        let animals = Dictionary(allAnimals.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        return StoryContent.Plan(hero: animals["cow"]!, place: "hill", friend: animals["dog"]!, thing: "banana")
    }

    func testAStorySentenceNamesWhatWasPicked() {
        XCTAssertEqual(StoryContent.pickWord(plan, english: false, step: 1), "הר")
        XCTAssertEqual(StoryContent.pickWord(plan, english: true, step: 1), "hill")
        XCTAssertEqual(StoryContent.pickWord(plan, english: true, step: 2), "dog")
        XCTAssertEqual(StoryContent.pickWord(plan, english: true, step: 3), "banana")
        XCTAssertNil(StoryContent.pickWord(plan, english: true, step: 0))
        XCTAssertTrue(ReachBack.says("הפרה הלכה להר הגבוה.", "הר"))
        XCTAssertTrue(ReachBack.says("The cow met two dogs.", "dog"))
        XCTAssertFalse(ReachBack.says("The cow went to the farm.", "hill"))
    }

    func testAndNothingThatWasNot() {
        let offered = [1: ["hill", "garden"], 2: ["dog", "cat"], 3: ["banana", "cake"]]
        XCTAssertEqual(StoryContent.notPicked(plan, english: false, step: 1, offered: offered), ["גינה"])
        XCTAssertEqual(StoryContent.notPicked(plan, english: true, step: 3, offered: offered), ["garden", "cat", "cake"])
        XCTAssertTrue(StoryContent.notPicked(plan, english: true, step: 0, offered: offered).isEmpty)
    }

    func testARecapWordTwoEventsShareNamesNeither() {
        let input = "סיכום לפני השינה. היום: ספרת 3 דגים; ספרת 5 פרפרים."
        XCTAssertEqual(RecapContent.eventsNamedApart("ספרת יפה!", input), 0)
        XCTAssertEqual(RecapContent.eventsNamedApart("ספרת דגים ופרפרים.", input), 2)
        XCTAssertEqual(RecapContent.eventsNamedApart("דגים!", input), 1)
    }
}

/**
 * RegexPortabilityTest.kt: Java-only regex flags crash ICU engines (Android's, and NSRegularExpression), as 0.3.0
 * did with (?U). This scans the app's Swift for them (the tests run on a Mac, next to the sources).
 */
final class RegexPortabilityTests: XCTestCase {
    func testNoJavaOnlyRegexFlags() throws {
        let sources = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("Puki/Sources")
        guard let files = FileManager.default.enumerator(at: sources, includingPropertiesForKeys: nil) else { throw XCTSkip("no sources here") }
        var problems: [String] = []
        for case let url as URL in files where url.pathExtension == "swift" {
            for (i, raw) in try String(contentsOf: url, encoding: .utf8).components(separatedBy: "\n").enumerated() {
                let t = raw.trimmingCharacters(in: .whitespaces)
                if t.hasPrefix("*") || t.hasPrefix("//") || t.hasPrefix("/*") { continue }
                for flag in ["(?U)", "(?u)", "UNICODE_CHARACTER_CLASS"] where raw.contains(flag) { problems.append("\(url.lastPathComponent):\(i + 1) uses \(flag)") }
            }
        }
        XCTAssertTrue(problems.isEmpty, problems.joined(separator: "\n"))
    }
}
