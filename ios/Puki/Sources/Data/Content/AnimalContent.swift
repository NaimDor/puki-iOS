import SwiftUI

struct AnimalDef: Hashable {
    let id: String
    let name: BilingualText
    let foodId: String
    let drawable: String
    init(_ id: String, _ name: BilingualText, _ foodId: String, _ drawable: String) {
        self.id = id; self.name = name; self.foodId = foodId; self.drawable = drawable
    }
}

struct FoodDef: Hashable {
    let id: String
    let name: BilingualText
    let drawable: String
    init(_ id: String, _ name: BilingualText, _ drawable: String) {
        self.id = id; self.name = name; self.drawable = drawable
    }
}

/** Animals Hebrew speaks of as feminine (הפרה אומרת, הכלב אומר). */
let feminineAnimals: Set<String> = ["cow", "chicken", "sheep", "goat", "giraffe"]

/** All of them, for games that don't go by level. */
var allAnimals: [AnimalDef] { level1Animals + level2Animals + level3Animals }

let level1Animals = [
    AnimalDef("cow", BilingualText("פרה", "Cow"), "grass", "ic_animal_cow"),
    AnimalDef("dog", BilingualText("כלב", "Dog"), "bone", "ic_animal_dog"),
    AnimalDef("cat", BilingualText("חתול", "Cat"), "fish", "ic_animal_cat"),
    AnimalDef("chicken", BilingualText("תרנגולת", "Chicken"), "seeds", "ic_animal_chicken"),
]

let level2Animals = [
    AnimalDef("horse", BilingualText("סוס", "Horse"), "hay", "ic_animal_horse"),
    AnimalDef("pig", BilingualText("חזיר", "Pig"), "corn", "ic_animal_pig"),
    AnimalDef("sheep", BilingualText("כבשה", "Sheep"), "clover", "ic_animal_sheep"),
    AnimalDef("duck", BilingualText("ברווז", "Duck"), "bread", "ic_animal_duck"),
    AnimalDef("rabbit", BilingualText("ארנב", "Rabbit"), "carrot", "ic_animal_rabbit"),
    AnimalDef("goat", BilingualText("עז", "Goat"), "leaves", "ic_animal_goat"),
]

let level3Animals = [
    AnimalDef("lion", BilingualText("אריה", "Lion"), "meat", "ic_animal_lion"),
    AnimalDef("elephant", BilingualText("פיל", "Elephant"), "peanuts", "ic_animal_elephant"),
    AnimalDef("monkey", BilingualText("קוף", "Monkey"), "banana", "ic_animal_monkey"),
    AnimalDef("giraffe", BilingualText("ג׳ירפה", "Giraffe"), "leaves", "ic_animal_giraffe"),
    AnimalDef("penguin", BilingualText("פינגווין", "Penguin"), "fish", "ic_animal_penguin"),
]

let level1Foods = [
    FoodDef("grass", BilingualText("עשב", "Grass"), "ic_food_grass"),
    FoodDef("bone", BilingualText("עצם", "Bone"), "ic_food_bone"),
    FoodDef("fish", BilingualText("דג", "Fish"), "ic_food_fish"),
    FoodDef("seeds", BilingualText("גרעינים", "Seeds"), "ic_food_seeds"),
]

let level2Foods = level1Foods + [
    FoodDef("hay", BilingualText("חציר", "Hay"), "ic_food_hay"),
    FoodDef("corn", BilingualText("תירס", "Corn"), "ic_food_corn"),
    FoodDef("clover", BilingualText("תלתן", "Clover"), "ic_food_clover"),
    FoodDef("bread", BilingualText("לחם", "Bread"), "ic_food_bread"),
    FoodDef("carrot", BilingualText("גזר", "Carrot"), "ic_food_carrot"),
    FoodDef("leaves", BilingualText("עלים", "Leaves"), "ic_food_leaves"),
]

let level3Foods = level2Foods + [
    FoodDef("meat", BilingualText("בשר", "Meat"), "ic_food_meat"),
    FoodDef("peanuts", BilingualText("בוטנים", "Peanuts"), "ic_food_peanuts"),
    FoodDef("banana", BilingualText("בננה", "Banana"), "ic_food_banana"),
]

func foodsForLevel(_ level: Int) -> [FoodDef] {
    level >= 3 ? level3Foods : level >= 2 ? level2Foods : level1Foods
}
