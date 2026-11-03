import Foundation

struct LetterDef: Hashable {
    let id: String
    let hebrewLetter: String
    let englishLetter: String
    let name: BilingualText
    let exampleWord: BilingualText
    let exampleIcon: String
    init(_ id: String, _ he: String, _ en: String, _ name: BilingualText, _ word: BilingualText, _ icon: String) {
        self.id = id; hebrewLetter = he; englishLetter = en; self.name = name; exampleWord = word; exampleIcon = icon
    }
}

let hebrewLetters = [
    LetterDef("alef", "א", "A", BilingualText("אָלֶף", "Alef"), BilingualText("אריה", "Lion"), "ic_animal_lion"),
    LetterDef("bet", "ב", "B", BilingualText("בֵּית", "Bet"), BilingualText("בית", "House"), "ic_word_house"),
    LetterDef("gimel", "ג", "G", BilingualText("גִּימֶל", "Gimel"), BilingualText("גמל", "Camel"), "ic_word_camel"),
    LetterDef("dalet", "ד", "D", BilingualText("דָּלֶת", "Dalet"), BilingualText("דג", "Fish"), "ic_food_fish"),
    LetterDef("he", "ה", "H", BilingualText("הֵא", "He"), BilingualText("הר", "Mountain"), "ic_word_mountain"),
    LetterDef("vav", "ו", "V", BilingualText("וָו", "Vav"), BilingualText("ורד", "Rose"), "ic_word_rose"),
    LetterDef("het", "ח", "H", BilingualText("חֵית", "Het"), BilingualText("חתול", "Cat"), "ic_animal_cat"),
    LetterDef("tet", "ט", "T", BilingualText("טֵית", "Tet"), BilingualText("טיל", "Rocket"), "ic_sticker_rocket"),
    LetterDef("yod", "י", "Y", BilingualText("יוּד", "Yod"), BilingualText("ירח", "Moon"), "ic_bedtime_moon"),
    LetterDef("kaf", "כ", "K", BilingualText("כָּף", "Kaf"), BilingualText("כלב", "Dog"), "ic_animal_dog"),
    LetterDef("lamed", "ל", "L", BilingualText("לָמֶד", "Lamed"), BilingualText("לחם", "Bread"), "ic_food_bread"),
    LetterDef("mem", "מ", "M", BilingualText("מֵם", "Mem"), BilingualText("מכונית", "Car"), "ic_myday_car"),
    LetterDef("nun", "נ", "N", BilingualText("נוּן", "Nun"), BilingualText("נעליים", "Shoes"), "ic_clothing_shoes"),
    LetterDef("samekh", "ס", "S", BilingualText("סָמֶך", "Samekh"), BilingualText("סוס", "Horse"), "ic_animal_horse"),
    LetterDef("ayin", "ע", "A", BilingualText("עַיִן", "Ayin"), BilingualText("עז", "Goat"), "ic_animal_goat"),
    LetterDef("pe", "פ", "P", BilingualText("פֵּא", "Pe"), BilingualText("פיל", "Elephant"), "ic_animal_elephant"),
    LetterDef("qof", "ק", "Q", BilingualText("קוּף", "Qof"), BilingualText("קוף", "Monkey"), "ic_animal_monkey"),
    LetterDef("shin", "ש", "S", BilingualText("שִׁין", "Shin"), BilingualText("שמש", "Sun"), "ic_sticker_sun"),
    LetterDef("tav", "ת", "T", BilingualText("תָּו", "Tav"), BilingualText("תוף", "Drum"), "ic_instrument_drum"),
]

/** Letters easily mistaken for each other: the top level puts them side by side. */
let letterLookalikes: [String: String] = [
    "bet": "kaf", "kaf": "bet", "he": "het", "het": "he",
    "vav": "yod", "yod": "vav", "gimel": "nun", "nun": "gimel",
    "tet": "samekh", "samekh": "tet", "ayin": "shin", "shin": "ayin",
    "e": "f", "f": "e", "p": "r", "r": "p", "b": "d", "d": "b", "c": "g", "g": "c",
]

let englishLetters = [
    LetterDef("a", "א", "A", BilingualText("אֵי", "A"), BilingualText("תפוח", "Apple"), "ic_word_apple"),
    LetterDef("b", "ב", "B", BilingualText("בִּי", "B"), BilingualText("כדור", "Ball"), "ic_word_ball"),
    LetterDef("c", "ג", "C", BilingualText("סִי", "C"), BilingualText("חתול", "Cat"), "ic_animal_cat"),
    LetterDef("d", "ד", "D", BilingualText("דִי", "D"), BilingualText("כלב", "Dog"), "ic_animal_dog"),
    LetterDef("e", "ה", "E", BilingualText("אִי", "E"), BilingualText("פיל", "Elephant"), "ic_animal_elephant"),
    LetterDef("f", "ו", "F", BilingualText("אֶף", "F"), BilingualText("דג", "Fish"), "ic_food_fish"),
    LetterDef("g", "ז", "G", BilingualText("גִ׳י", "G"), BilingualText("עז", "Goat"), "ic_animal_goat"),
    LetterDef("h", "ח", "H", BilingualText("אֵייץ׳", "H"), BilingualText("סוס", "Horse"), "ic_animal_horse"),
    LetterDef("l", "ט", "L", BilingualText("אֶל", "L"), BilingualText("אריה", "Lion"), "ic_animal_lion"),
    LetterDef("m", "י", "M", BilingualText("אֶם", "M"), BilingualText("ירח", "Moon"), "ic_bedtime_moon"),
    LetterDef("p", "כ", "P", BilingualText("פִּי", "P"), BilingualText("חזיר", "Pig"), "ic_animal_pig"),
    LetterDef("r", "ל", "R", BilingualText("אַר", "R"), BilingualText("ארנב", "Rabbit"), "ic_animal_rabbit"),
    LetterDef("s", "מ", "S", BilingualText("אֶס", "S"), BilingualText("שמש", "Sun"), "ic_sticker_sun"),
    LetterDef("t", "נ", "T", BilingualText("טִי", "T"), BilingualText("דובי", "Teddy"), "ic_tidy_teddy"),
]
