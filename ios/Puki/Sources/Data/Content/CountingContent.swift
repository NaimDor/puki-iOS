import Foundation

/** Where a round's things live. */
enum Habitat { case bed, tree, pond, night, sky }

struct CountingRound: Hashable {
    let targetNumber: Int
    let itemName: BilingualText
    let itemIcon: String
    let habitat: Habitat
    init(_ n: Int, _ name: BilingualText, _ icon: String, _ habitat: Habitat) {
        targetNumber = n; itemName = name; itemIcon = icon; self.habitat = habitat
    }
}

let level1CountingRounds = [
    CountingRound(1, BilingualText("פרח", "flower"), "ic_sticker_flower", .bed),
    CountingRound(2, BilingualText("פרפרים", "butterflies"), "ic_sticker_butterfly", .bed),
    CountingRound(3, BilingualText("חיפושיות", "ladybugs"), "ic_stamp_ladybug", .bed),
    CountingRound(4, BilingualText("פרחים", "flowers"), "ic_count_hibiscus", .bed),
    // The same numbers with other things, so level 1 isn't the same four rounds
    CountingRound(1, BilingualText("תפוח", "apple"), "ic_count_apple", .tree),
    CountingRound(1, BilingualText("כוכב", "star"), "ic_sticker_star", .night),
    CountingRound(1, BilingualText("דג", "fish"), "ic_food_fish", .pond),
    CountingRound(2, BilingualText("תפוחים", "apples"), "ic_count_apple_green", .tree),
    CountingRound(2, BilingualText("ציפורים", "birds"), "ic_count_bird", .sky),
    CountingRound(3, BilingualText("דגים", "fish"), "ic_food_fish", .pond),
    CountingRound(3, BilingualText("לבבות", "hearts"), "ic_sticker_heart", .sky),
    CountingRound(4, BilingualText("כוכבים", "stars"), "ic_sticker_star", .night),
    CountingRound(4, BilingualText("פרפרים", "butterflies"), "ic_sticker_butterfly", .bed),
]

/** The most carrots the rabbit asks for. */
let mostToGive = 5

/** A hungry rabbit asks for `wanted` carrots. */
func giveRound(_ wanted: Int) -> CountingRound {
    CountingRound(min(max(wanted, 1), mostToGive), BilingualText("גזרים", "carrots"), "ic_food_carrot", .bed)
}

let level2CountingRounds = [
    CountingRound(2, BilingualText("תפוחים", "apples"), "ic_count_apple", .tree),
    CountingRound(3, BilingualText("כוכבים", "stars"), "ic_sticker_star", .night),
    CountingRound(4, BilingualText("לבבות", "hearts"), "ic_sticker_heart", .sky),
    CountingRound(5, BilingualText("דגים", "fish"), "ic_food_fish", .pond),
]

let level3CountingRounds = [
    CountingRound(5, BilingualText("ציפורים", "birds"), "ic_count_bird", .sky),
    CountingRound(6, BilingualText("פרפרים", "butterflies"), "ic_sticker_butterfly", .bed),
    CountingRound(7, BilingualText("פרחים", "flowers"), "ic_sticker_flower", .bed),
    CountingRound(8, BilingualText("כוכבים", "stars"), "ic_sticker_star", .night),
    CountingRound(9, BilingualText("לבבות", "hearts"), "ic_count_heart_purple", .sky),
    CountingRound(10, BilingualText("תפוחים", "apples"), "ic_count_apple_green", .tree),
]

func countingRoundsForLevel(_ level: Int) -> [CountingRound] {
    level >= 3 ? level3CountingRounds : level >= 2 ? level2CountingRounds : level1CountingRounds
}

/** The words that count as saying `n` out loud (with the digit, as Whisper may write it). */
func numberWords(_ n: Int, english: Bool) -> [String] {
    let table = english ? numbersEn : numbersHe
    let words = (n >= 1 && n <= table.count) ? table[n - 1] : []
    return ["\(n)"] + words
}

private let numbersHe = [
    ["אחת", "אחד"], ["שתיים", "שתים", "שניים", "שנים"], ["שלוש", "שלושה"],
    ["ארבע", "ארבעה"], ["חמש", "חמישה", "חמשה"], ["שש", "שישה", "ששה"],
    ["שבע", "שבעה"], ["שמונה"], ["תשע", "תשעה"], ["עשר", "עשרה"],
]
private let numbersEn = [["one"], ["two"], ["three"], ["four"], ["five"], ["six"], ["seven"], ["eight"], ["nine"], ["ten"]]
