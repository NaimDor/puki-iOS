import XCTest
@testable import Puki

/** The safety nets for what the child says to Puki (SafetyTest.kt, same cases). */
final class SafetyTests: XCTestCase {
    func testCatches() {
        let cases: [(String, String, String)] = [
            ("כואב לי", "he", "hurt"),
            ("וכואב לי בבטן", "he", "hurt"),  // with a prefix letter
            ("יש דם", "he", "hurt"),
            ("אני רוצה אמא", "he", "parent"),
            ("בוא נשחק עם סכין", "he", "danger"),
            ("בוא נדליק אש", "he", "danger"),
            ("בוא נעשה מדורה", "he", "danger"),
            ("אני רוצה לאכול את התרופה", "he", "danger"),
            ("הכלב מת?", "he", "big_question"),
            ("בוא נסתיר מאמא", "he", "secrets"),
            ("די פוקי! שקט!", "he", "quiet"),
            ("אני לא רוצה ללכת לישון", "he", "bedtime"),
            ("אתה טיפש", "he", "unkind"),
            ("אבא כועס עליי", "he", "feelings"),
            ("I'm sad", "en", "feelings"),
            ("אתה אוהב אותי?", "he", "love"),
            ("I fell down", "en", "hurt"),
            ("let's play with a knife", "en", "danger"),
            ("is the doggy dead?", "en", "big_question"),
            ("I want mommy", "en", "parent"),
            ("buy me a game", "en", "buying"),
        ]
        for (heard, lang, cat) in cases { XCTAssertEqual(Safety.guardReply(heard, lang: lang, girl: true)?.0, cat, heard) }
    }

    func testOrdinaryPlayPasses() {
        let cases: [(String, String)] = [
            ("אני רוצה מתוק", "he"), ("מתנה לי", "he"), ("שמש גדולה", "he"), ("ראש", "he"), ("אני אוהבת פרה", "he"), ("פרה!", "he"),
            ("a warm hug", "en"), ("I love the pillow", "en"), ("let us match colors", "en"), ("the doggy says woof", "en"),
            ("בוא נספור מספרים", "he"), ("פוקי נפלא", "he"), ("אני רוצה סודה", "he"), ("שמים את העוגה בסיר", "he"),
            ("החתול נעלם", "he"), ("אמא שלי אוהבת תפוזים", "he"), ("הכלב הלך", "he"),
            ("the tower fell", "en"), ("fire truck", "en"), ("play store", "en"), ("out the window, a bird", "en"), ("all gone!", "en"),
        ]
        for (heard, lang) in cases { XCTAssertNil(Safety.guardReply(heard, lang: lang, girl: true), heard) }
    }

    func testStackedHebrewPrefixes() {
        for heard in ["מהתרופות", "והסכין", "בהשקע", "וכשנפלתי"] { XCTAssertNotNil(Safety.guardReply(heard, lang: "he", girl: true), heard) }
    }

    func testAGameMayUseItsOwnWords() {
        let knife = "בוא נחתוך את העוגה עם סכין"
        XCTAssertNil(Safety.guardReply(knife, lang: "he", girl: true, game: "myday/kitchen"))
        XCTAssertEqual(Safety.guardReply(knife, lang: "he", girl: true)?.0, "danger")
    }

    func testRepliesFollowTheChildsGender() {
        XCTAssertEqual(Safety.guardReply("כואב לי", lang: "he", girl: true)!.1.components(separatedBy: "בואי").count - 1, 1)
        XCTAssertTrue(Safety.guardReply("כואב לי", lang: "he", girl: false)!.1.contains("בוא "))
        XCTAssertFalse(Safety.guardReply("כואב לי", lang: "he", girl: false)!.1.contains("בואי"))
    }

    func testEveryCategoryHasAReplyInBothLanguages() {
        for lang in ["he", "en"] {
            for girl in [true, false] {
                let r = Safety.replies(lang, girl: girl)
                for rule in Safety.rules { XCTAssertNotNil(r[rule.category], rule.category) }
                for c in Safety.gateCategoryMap.values { XCTAssertNotNil(r[c], c) }
            }
        }
    }

    func testSameRepliesAsThePc() {
        XCTAssertEqual(Safety.replies("he", girl: true)["danger"], "זה מסוכן! רק עם מבוגר. בואי נקרא לאמא או לאבא.")
        XCTAssertEqual(Safety.replies("en", girl: false)["hurt"], "Oh! Let's call mommy or daddy, they can help.")
    }

    func testBannedPhrases() {
        XCTAssertFalse(Safety.replyOk("Hooray! I love you so much!"))
        XCTAssertFalse(Safety.replyOk("אמא בדרך!"))
        XCTAssertFalse(Safety.replyOk("אני החבר הכי טוב שלך"))
        XCTAssertTrue(Safety.replyOk("יש! ירוק כמו העשב!"))
        XCTAssertTrue(Safety.replyOk("בדרך כלל הפרה אוכלת עשב"))
    }

    func testGateThreshold() {
        let ordinary = ["0": 0.97, "1": 0.02, "2": 0.01]
        XCTAssertNil(Safety.gateCategory(ordinary, threshold: 0.95))
        XCTAssertEqual(Safety.gateCategory(ordinary, threshold: 0.99), "hurt")
        XCTAssertEqual(Safety.gateCategory(["0": 0.2, "2": 0.7, "1": 0.1], threshold: 0.9), "danger")
        // Fails closed: when the first token wasn't mostly a digit, it's "unclear", not ordinary
        XCTAssertEqual(Safety.gateCategory([:], threshold: 0.9), "unclear")
        XCTAssertEqual(Safety.gateCategory(["0": 0.3, "1": 0.05], threshold: 0.9), "unclear")
    }

    func testGateFromOneGreedyDigit() {
        XCTAssertNil(Safety.gateCategory(answer: "0"))
        XCTAssertEqual(Safety.gateCategory(answer: "1"), "hurt")
        XCTAssertEqual(Safety.gateCategory(answer: " 2\n"), "danger")
        XCTAssertEqual(Safety.gateCategory(answer: "3"), "big_question")
        XCTAssertEqual(Safety.gateCategory(answer: "Yes"), "unclear")
        XCTAssertEqual(Safety.gateCategory(answer: ""), "unclear")
    }

    func testNoOpenerWhenAnsweringTheChild() {
        let persona = Persona()
        for _ in 0..<20 {
            XCTAssertEqual(persona.situationPrompt("x", lang: "en", listening: true), "x")
            XCTAssertEqual(persona.situationPrompt("x", lang: "he", listening: true), "x")
        }
    }

    struct GateDown: Error {}

    func testAFailingGateFailsClosed() {
        var errors = 0
        let caught = Safety.guardedReply("בוא נשחק בכדור", lang: "he", girl: true, gate: { _ in throw GateDown() }, onError: { _ in errors += 1 })
        XCTAssertEqual(caught?.0, "unclear")
        XCTAssertEqual(caught?.1, Safety.replies("he", girl: true)["unclear"])
        XCTAssertEqual(errors, 1)
    }

    func testTheGateDecidesAfterTheWordLists() {
        XCTAssertNil(Safety.guardedReply("בוא נשחק בכדור", lang: "he", girl: true, gate: { _ in nil }))
        XCTAssertEqual(Safety.guardedReply("let's play", lang: "en", girl: false, gate: { _ in "hurt" })?.0, "hurt")
        // The word lists answer first, without asking the gate
        XCTAssertEqual(Safety.guardedReply("I want to play with fire", lang: "en", girl: false, gate: { _ in throw GateDown() })?.0, "danger")
    }
}
