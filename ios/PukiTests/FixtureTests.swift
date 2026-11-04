import XCTest
@testable import Puki

/** The trainer's fixtures (app/src/test/resources, shared with Android): a port that passes them is right. */
private func fixture(_ name: String) throws -> Any {
    let bundle = Bundle(for: FixtureBundle.self)
    let url = bundle.url(forResource: name, withExtension: nil, subdirectory: "resources") ?? bundle.url(forResource: name, withExtension: nil)
    guard let url else { throw XCTSkip("fixture \(name) missing") }
    return try JSONSerialization.jsonObject(with: Data(contentsOf: url))
}
private final class FixtureBundle {}
private func obj(_ name: String) throws -> [String: Any] { try XCTUnwrap(try fixture(name) as? [String: Any]) }
private func strings(_ a: Any?) -> [String] { (a as? [Any] ?? []).compactMap { $0 as? String } }

/** The tokenizer gives exactly what HuggingFace tokenizers gives, and decoding gives the text back (BpeTest.kt). */
final class BpeTests: XCTestCase {
    private func bpe() throws -> Bpe {
        let bundle = Bundle(for: FixtureBundle.self)
        let url = try XCTUnwrap(bundle.url(forResource: "brains_tokenizer.json", withExtension: nil, subdirectory: "resources")
                                ?? bundle.url(forResource: "brains_tokenizer.json", withExtension: nil))
        return try Bpe(json: Data(contentsOf: url))
    }

    func testSameTokensAsTokenizersAndBackAgain() throws {
        let b = try bpe()
        let cases = try XCTUnwrap(try fixture("brains_bpe_cases.json") as? [[String: Any]])
        XCTAssertFalse(cases.isEmpty)
        for (i, c) in cases.enumerated() {
            let text = c["text"] as! String
            let want = (c["ids"] as! [Any]).map { ($0 as! NSNumber).intValue }
            XCTAssertEqual(b.encode(text), want, "case \(i): \(text)")
            XCTAssertEqual(b.decode(want), text, "case \(i)")
        }
    }

    func testTheEndToken() throws { XCTAssertEqual(try bpe().id("<|endoftext|>"), 0) }
}

/** Silly Puki's sentences against the trainer's (SillyContentTest.kt). */
final class SillyContentTests: XCTestCase {
    func testInputsAreTheOnesTheBrainLearned() throws {
        let f = try obj("silly.json")
        let want = Set((f["inputs"] as! [[String: Any]]).map { [$0["group"], $0["he"], $0["en"], $0["truth_he"], $0["truth_en"]].map { $0 as! String } })
        let got = Set(SillyContent.all.map { [$0.group, $0.he, $0.en, $0.truthHe, $0.truthEn] })
        XCTAssertEqual(want.subtracting(got), [], "trained but not in the app")
        XCTAssertEqual(got.subtracting(want), [], "in the app but not trained")
    }

    func testTruthCheckAgreesWithTheTrainer() throws {
        for c in try obj("silly.json")["said_truth"] as! [[String: Any]] {
            let line = c["line"] as! String, truth = c["truth"] as! String
            // The one place the app is stricter than the trainer: a feminine or plural color says the color too
            let stricter = line == "בננה צהובה!" && truth == "צהוב"
            XCTAssertEqual(SillyContent.saysTruth(line, truth), (c["said"] as! Bool) || stricter, "\(c)")
        }
    }

    func testAnswersAreWordedAsTrained() throws {
        let trained = Set((try obj("silly.json")["fix"] as! [[Any]]).map { $0[1] as! String })
        let made = [
            SillyContent.fixInput(english: false, line: "הפרה עושה מיאו!", truth: "מו", answer: .right),
            SillyContent.fixInput(english: false, line: "הפרה עושה מיאו!", truth: "מו", answer: .nothing),
            SillyContent.fixInput(english: true, line: "The cow says meow!", truth: "moo", answer: .right),
            SillyContent.fixInput(english: true, line: "The cow says meow!", truth: "moo", answer: .nothing),
        ]
        for m in made { XCTAssertTrue(trained.contains(m), "not as trained: \(m)") }
    }
}

/** Puki's eyes against the trainer's (LookContentTest.kt). */
final class LookContentTests: XCTestCase {
    private func facts(_ x: [String: Any]) -> LookFacts {
        let letter = (x["letter"] as? [String]).map { ($0[0], $0[1]) }
        let stamps = (x["stamps"] as? [[Any]] ?? []).map { ($0[0] as! String, ($0[1] as! NSNumber).intValue) }
        return LookFacts(page: x["page"] as! String, letter: letter, colors: strings(x["colors"]), stamps: stamps,
                         cover: (x["cover"] as! NSNumber).intValue, strokes: (x["strokes"] as? NSNumber)?.intValue)
    }

    func testDrawingsAreDescribedAsTrained() throws {
        for x in try obj("look.json")["inputs"] as! [[String: Any]] {
            let got = LookContent.describe(facts(x))
            XCTAssertEqual(got.he, x["he"] as? String)
            XCTAssertEqual(got.en, x["en"] as? String)
            XCTAssertEqual(got.presentHe, strings(x["present_he"]))
            XCTAssertEqual(got.presentEn, strings(x["present_en"]))
            XCTAssertEqual(got.absentHe, strings(x["absent_he"]))
            XCTAssertEqual(got.absentEn, strings(x["absent_en"]))
        }
    }

    func testTheCheckAgreesWithTheTrainer() throws {
        let f = try obj("look.json")
        let byGroup = Dictionary((f["inputs"] as! [[String: Any]]).map { ($0["group"] as! String, $0) }, uniquingKeysWith: { a, _ in a })
        for c in f["ok"] as! [[String: Any]] {
            let x = try XCTUnwrap(byGroup[c["x"] as! String])
            let lang = c["lang"] as! String
            XCTAssertEqual(LookContent.ok(c["line"] as! String, present: strings(x["present_\(lang)"]), absent: strings(x["absent_\(lang)"])),
                           c["ok"] as! Bool, "\(c)")
        }
    }
}

/** The bedtime recap against the trainer's (RecapContentTest.kt). */
final class RecapContentTests: XCTestCase {
    /** Every event the app can record, the way it records them. */
    private var possible: Set<DayEvent> {
        var s = Set<DayEvent>()
        for a in allAnimals { if let e = RecapContent.fed(a) { s.insert(e) }; s.insert(RecapContent.found(a)) }
        for c in level3Colors { s.insert(RecapContent.color(c)); s.insert(RecapContent.drew(c)) }
        s.insert(RecapContent.drew(nil))
        for sh in level3Shapes { s.insert(RecapContent.shape(sh)) }
        for r in level1CountingRounds + level2CountingRounds + level3CountingRounds { s.insert(RecapContent.counted(r)) }
        for l in hebrewLetters + englishLetters { s.insert(RecapContent.said(l.exampleWord)) }
        for st in allStickers { s.insert(RecapContent.sticker(st.name)) }
        s.formUnion(RecapContent.fixed.values)
        return s
    }

    func testTheTrainersEventsAreWordedTheSame() throws {
        // The trainer's days also have the teddy's teeth, bath and clothes, which the app's games aren't
        let notOurs: Set<String> = ["צחצחת שיניים לדובי", "רחצת את הדובי באמבטיה", "הלבשת את הדובי"]
        let possible = self.possible
        for e in try obj("recap.json")["events"] as! [[String]] {
            let ev = DayEvent(e[0], e[1])
            if notOurs.contains(ev.he) { continue }
            XCTAssertTrue(possible.contains(ev), "not as trained: \(ev)")
        }
    }

    func testTheCheckAgreesWithTheTrainer() throws {
        for c in try obj("recap.json")["named"] as! [[String: Any]] {
            XCTAssertEqual(RecapContent.eventsNamed(c["line"] as! String, c["input"] as! String), (c["named"] as! NSNumber).intValue, "\(c)")
        }
    }
}

/** Reaching back against the PC brain's words (ReachBackTest.kt). */
final class ReachBackTests: XCTestCase {
    func testContentWordsAreThePcBrains() throws {
        for r in try XCTUnwrap(try fixture("reach.json") as? [[String: Any]]) {
            let input = r["input"] as! String, earlier = r["earlier"] as! String, line = r["line"] as! String
            XCTAssertEqual(ReachBack.contentWords(input), Set(strings(r["input_words"])), input)
            XCTAssertEqual(ReachBack.contentWords(earlier.substringAfter(":")), Set(strings(r["earlier_words"])), earlier)
            XCTAssertEqual(ReachBack.contentWords(line), Set(strings(r["line_words"])), line)
        }
    }

    func testAnEarlierMomentOfTheSameGameIsFound() throws {
        let cal = Calendar.current
        let today = cal.date(from: DateComponents(year: 2026, month: 11, day: 3))!
        func at(_ daysAgo: Int, _ hour: Int, _ minute: Int = 0) -> Int64 {
            let d = cal.date(byAdding: .day, value: -daysAgo, to: today)!
            return Int64(cal.date(bySettingHour: hour, minute: minute, second: 0, of: d)!.timeIntervalSince1970 * 1000)
        }
        let now = at(0, 18)
        let history = [
            Moment(t: at(1, 10), he: "במשחק הצללים מצאו של מי הצל: תרנגולת!", en: "In the shadow game they found whose shadow it was: the chicken!", game: "animals"),
            Moment(t: at(0, 9), he: "במשחק הצבעים בחרו שחור, אבל חיפשנו חום.", en: "x", game: "colors"),
            Moment(t: at(2, 9), he: "ספרו 5 פרפרים!", en: "The child counted 5 butterflies!", game: "counting"),
            Moment(t: at(0, 17, 58), he: "ספרו 4 תפוחים!", en: "The child counted 4 apples!", game: "counting"),
        ]
        XCTAssertEqual(ReachBack.related("במשחק הצללים מצאו של מי הצל: ברווז!", game: "animals", history: history, nowMs: now, today: today)?.0.0, "אתמול")
        XCTAssertEqual(ReachBack.related("במשחק הצבעים בחרו חום, אבל חיפשנו ירוק.", game: "colors", history: history, nowMs: now, today: today)?.0.0, "קודם היום")
        // The butterflies two days ago, not the apples of two minutes ago (that's "just now")
        XCTAssertEqual(ReachBack.related("ספרו 3 דגים!", game: "counting", history: history, nowMs: now, today: today)?.0.0, "לפני יומיים")
        XCTAssertNil(ReachBack.related("במשחק הצללים מצאו של מי הצל: ברווז!", game: "peekaboo", history: history, nowMs: now, today: today))
    }

    func testTheLineMustNameTheEarlierThing() {
        let input = "במשחק הצבעים בחרו חום, אבל חיפשנו ירוק."
        let earlier = "אתמול: במשחק הצבעים בחרו שחור, אבל חיפשנו חום."
        XCTAssertTrue(ReachBack.namesEarlier("כמו אתמול עם שחור!", input: input, earlier: earlier))
        XCTAssertFalse(ReachBack.namesEarlier("יש, ירוק!", input: input, earlier: earlier))
    }
}

/** The caring game's and the story's sentences against the trainer's (CareStoryContentTest.kt). */
final class CareStoryContentTests: XCTestCase {
    func testCaringSentencesAreAsTrained() throws {
        for r in try obj("care_story.json")["care"] as! [[String: Any]] {
            let patient = try XCTUnwrap(CareContent.patients.first { $0.id == r["patient"] as? String })
            let problem = try XCTUnwrap(CareContent.problems.first { $0.id == r["problem"] as? String })
            let item = r["item"] as? String
            XCTAssertEqual(CareContent.input(english: false, patient: patient, problem: problem, item: item), r["he"] as? String)
            XCTAssertEqual(CareContent.input(english: true, patient: patient, problem: problem, item: item), r["en"] as? String)
        }
    }

    func testStorySentencesAreAsTrained() throws {
        let animals = Dictionary(allAnimals.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        for r in try obj("care_story.json")["story"] as! [[String: Any]] {
            let place = try XCTUnwrap(StoryContent.places.entries.first { $0.1.1 == r["place"] as? String }?.0)
            let thing = try XCTUnwrap(StoryContent.things.entries.first { $0.1.1 == r["thing"] as? String }?.0)
            let plan = StoryContent.Plan(hero: try XCTUnwrap(animals[r["hero"] as! String]), place: place,
                                         friend: try XCTUnwrap(animals[r["friend"] as! String]), thing: thing)
            let n = min((r["so_far"] as! NSNumber).intValue, 2)
            let step = (r["step"] as! NSNumber).intValue
            XCTAssertEqual(StoryContent.input(plan, english: false, soFar: Array(["משפט אחד.", "משפט שני."].prefix(n)), step: step), r["he"] as? String)
            XCTAssertEqual(StoryContent.input(plan, english: true, soFar: Array(["One sentence.", "Two sentences."].prefix(n)), step: step), r["en"] as? String)
        }
    }
}
