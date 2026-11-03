import Foundation

/**
 * A word for Say it with Puki. Puki says it (voice_say_<id>), the child says it back.
 * `alsoCounts` are related things a toddler might say instead, like the animal's sound;
 * they count as a good try (Puki then says the word itself).
 */
struct SayWord: Hashable {
    let id: String
    let word: BilingualText
    let drawable: String
    var alsoCountsHe: [String] = []
    var alsoCountsEn: [String] = []
    init(_ id: String, _ word: BilingualText, _ drawable: String, _ he: [String] = [], _ en: [String] = []) {
        self.id = id; self.word = word; self.drawable = drawable; alsoCountsHe = he; alsoCountsEn = en
    }
}

/** Early words: things a two-year-old sees every day, most of them short. */
let sayWords = [
    SayWord("dog", BilingualText("כלב", "Dog"), "ic_animal_dog", ["הב", "כלבלב"], ["woof", "doggy", "puppy"]),
    SayWord("cat", BilingualText("חתול", "Cat"), "ic_animal_cat", ["מיאו", "חתולה", "חתלתול"], ["meow", "kitty", "kitten"]),
    SayWord("cow", BilingualText("פרה", "Cow"), "ic_animal_cow", ["מו", "פה"], ["moo"]),
    SayWord("duck", BilingualText("ברווז", "Duck"), "ic_animal_duck", ["גע", "ברוז"], ["quack", "ducky"]),
    SayWord("horse", BilingualText("סוס", "Horse"), "ic_animal_horse", ["סו"], ["neigh", "horsey"]),
    SayWord("sheep", BilingualText("כבשה", "Sheep"), "ic_animal_sheep", ["מה"], ["baa"]),
    SayWord("elephant", BilingualText("פיל", "Elephant"), "ic_animal_elephant", ["פי"], ["ellie"]),
    SayWord("monkey", BilingualText("קוף", "Monkey"), "ic_animal_monkey", ["קו"], ["monk"]),
    SayWord("lion", BilingualText("אריה", "Lion"), "ic_animal_lion", ["ראו"], ["roar"]),
    SayWord("fish", BilingualText("דג", "Fish"), "ic_food_fish"),
    SayWord("banana", BilingualText("בננה", "Banana"), "ic_food_banana", ["ננה", "בנה"], ["nana"]),
    SayWord("bread", BilingualText("לחם", "Bread"), "ic_food_bread", ["חם"], []),
    SayWord("carrot", BilingualText("גזר", "Carrot"), "ic_food_carrot", ["גזל"], []),
    SayWord("apple", BilingualText("תפוח", "Apple"), "ic_word_apple", ["פוח"], []),
    SayWord("ball", BilingualText("כדור", "Ball"), "ic_tidy_ball", ["כדו"], ["bah"]),
    SayWord("book", BilingualText("ספר", "Book"), "ic_tidy_book"),
    SayWord("teddy", BilingualText("דובי", "Teddy"), "ic_tidy_teddy", ["דוב", "דובון"], ["bear", "teddy bear"]),
    SayWord("cup", BilingualText("כוס", "Cup"), "ic_table_cup"),
    SayWord("shoes", BilingualText("נעליים", "Shoes"), "ic_clothing_shoes", ["נעל"], ["shoe"]),
    SayWord("moon", BilingualText("ירח", "Moon"), "ic_bedtime_moon"),
    SayWord("house", BilingualText("בית", "House"), "ic_word_house"),
    SayWord("drum", BilingualText("תוף", "Drum"), "ic_instrument_drum"),
]
