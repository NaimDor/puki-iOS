import XCTest
@testable import Puki

/** The filter every line Puki says passes, and the word check for Say it with Puki (PersonaTest.kt). */
final class PersonaTests: XCTestCase {
    private let persona = Persona()

    func testKeepsAWarmShortLine() {
        XCTAssertEqual(persona.clean("הפרה רעבה!", lang: "he"), "הפרה רעבה!")
        XCTAssertEqual(persona.clean("The cow is hungry!", lang: "en"), "The cow is hungry!")
    }

    func testStripsTagsQuotesAndExtraLines() {
        XCTAssertEqual(persona.clean("\"<b>שלום!</b>\"", lang: "he"), "שלום!")
        XCTAssertEqual(persona.clean("Hi there!\nAnd a second line", lang: "en"), "Hi there!")
    }

    func testRejectsHarshWords() {
        XCTAssertNil(persona.clean("זו טעות.", lang: "he"))
        XCTAssertNil(persona.clean("והטעות קטנה.", lang: "he"))
        XCTAssertNil(persona.clean("לא נכון!", lang: "he"))
        XCTAssertNil(persona.clean("That's wrong!", lang: "en"))
        XCTAssertNil(persona.clean("Not right, try again.", lang: "en"))
    }

    func testRejectsLongLinesAndOtherScripts() {
        XCTAssertNil(persona.clean(String(repeating: "מילה ", count: 15).trimmed() + ".", lang: "he"))
        XCTAssertNil(persona.clean(String(repeating: "x", count: 91), lang: "en"))
        XCTAssertNil(persona.clean("Hello שלום", lang: "en"))
        XCTAssertNil(persona.clean("", lang: "he"))
    }

    func testDropsComplimentsAboutTheChild() {
        XCTAssertEqual(persona.clean("את כל כך חכמה! הפרה אכלה.", lang: "he"), "הפרה אכלה.")
        XCTAssertEqual(persona.clean("You're so smart! The cow ate.", lang: "en"), "The cow ate.")
        XCTAssertEqual(persona.clean("You are here!", lang: "en"), "You are here!")
    }

    func testVoiceSwapsMisreadWordsAndDropsNikud() {
        XCTAssertEqual(persona.forVoice("הידד!"), "יש!")
        XCTAssertEqual(persona.forVoice("והידד"), "ויש")
        XCTAssertEqual(persona.forVoice("שָׁלוֹם"), "שלום")
        XCTAssertEqual(persona.forVoice("הידדים"), "הידדים")
    }

    func testChildNameLosesNikud() {
        persona.setChild("  שָׁלוֹם ", gender: "girl")
        XCTAssertEqual(persona.name, "שלום")
        persona.setChild("", gender: "boy")
        XCTAssertEqual(persona.child("en"), "the little boy")
    }

    func testWordMatchIsGenerous() {
        XCTAssertEqual(persona.wordMatch("הכלב", target: "כלב"), "word")
        XCTAssertEqual(persona.wordMatch("the dog", target: "dog"), "word")
        XCTAssertEqual(persona.wordMatch("כלבים", target: "כלב"), "word")
        XCTAssertEqual(persona.wordMatch("פה", target: "פרה"), "close")
        XCTAssertEqual(persona.wordMatch("הב הב", target: "כלב", alts: ["הב"]), "close")
        XCTAssertEqual(persona.wordMatch("בננה", target: "כלב"), "tried")
        XCTAssertEqual(persona.wordMatch("", target: "כלב"), "tried")
        XCTAssertEqual(persona.wordMatch("סוסים", target: "סוס"), "word")
        XCTAssertEqual(persona.wordMatch("פרים", target: "פרים"), "word")
        XCTAssertEqual(persona.wordMatch("ספרים", target: "ספרימ"), "word")
    }

    func testSimilarityMatchesPythonDifflib() {
        XCTAssertEqual(Persona.similarity("abcd", "abce"), 0.75, accuracy: 1e-9)
        XCTAssertEqual(Persona.similarity("abc", "xyz"), 0, accuracy: 1e-9)
        XCTAssertEqual(Persona.similarity("", ""), 1, accuracy: 1e-9)
        XCTAssertEqual(Persona.similarity("פרה", "פרפרים"), 2.0 * 2 / 9, accuracy: 1e-9)
    }
}

final class SpokenAnswerTests: XCTestCase {
    private let colorsHe = [["אדום"], ["כחול"], ["צהוב"]]
    private let colorsEn = [["Red"], ["Blue"], ["Yellow"]]

    func testAColorSaidAloneOrWithAPrefixOrAFeminineEnding() {
        XCTAssertEqual(SpokenAnswer.pick("אדום!", colorsHe), 0)
        XCTAssertEqual(SpokenAnswer.pick("הכחול", colorsHe), 1)
        XCTAssertEqual(SpokenAnswer.pick("צהובה.", colorsHe), 2)
        XCTAssertEqual(SpokenAnswer.pick("זה כָּחוֹל", colorsHe), 1)
        XCTAssertEqual(SpokenAnswer.pick("the blue one", colorsEn), 1)
    }

    func testTheLastAnswerSaidWins() {
        XCTAssertEqual(SpokenAnswer.pick("אדום... לא, כחול!", colorsHe), 1)
        let counts = [2, 3, 4].map { numberWords($0, english: false) }
        XCTAssertEqual(SpokenAnswer.pick("אחת, שתיים, שלוש", counts), 1)
        XCTAssertEqual(SpokenAnswer.pick("4", counts), 2)
        XCTAssertEqual(SpokenAnswer.pick("one two", [2, 3, 4].map { numberWords($0, english: true) }), 0)
    }

    func testNumbersInEitherGender() {
        let counts = [5, 6, 7].map { numberWords($0, english: false) }
        XCTAssertEqual(SpokenAnswer.pick("חמישה", counts), 0)
        XCTAssertEqual(SpokenAnswer.pick("שש", counts), 1)
        XCTAssertEqual(SpokenAnswer.pick("שבעה", counts), 2)
    }

    func testANearMissCountsOnlyWhenItsClearlyOneAnswer() {
        XCTAssertEqual(SpokenAnswer.pick("אדו", colorsHe), 0)
        XCTAssertEqual(SpokenAnswer.pick("כלב", colorsHe), -1)
        XCTAssertEqual(SpokenAnswer.pick("", colorsHe), -1)
        XCTAssertEqual(SpokenAnswer.pick("hello Puki", colorsEn), -1)
    }

    func testAnimalNames() {
        let animals = [["פרה"], ["כלב"], ["חתול"]]
        XCTAssertEqual(SpokenAnswer.pick("הפרה", animals), 0)
        XCTAssertEqual(SpokenAnswer.pick("חתול חתול", animals), 2)
        XCTAssertEqual(SpokenAnswer.pick("dog", [["Cow"], ["Dog"]]), 1)
    }
}

/** The drawing studio's coloring pages: the SVG paths parse, and a tap finds the shape in front. */
final class ColoringPageTests: XCTestCase {
    func testPagesParseAndTapsFindTheFrontShape() {
        for case .coloring(let page)? in coloringPages {
            for s in page.shapes { XCTAssertFalse(s.boundingRect.isEmpty, page.id) }
        }
        guard case .coloring(let fish)? = coloringPages[1] else { return XCTFail("the fish") }
        let shapes = fish.placed(CGSize(width: 100 / 0.88, height: 100 / 0.88))
        // The eye sits on the body: a tap on it finds the eye (index 4), not the body (2)
        let eye = shapes[4].boundingRect
        XCTAssertEqual(shapeAt(shapes, CGPoint(x: eye.midX, y: eye.midY)), 4)
        XCTAssertEqual(shapeAt(shapes, CGPoint(x: 1, y: 1)), -1)
    }
}
