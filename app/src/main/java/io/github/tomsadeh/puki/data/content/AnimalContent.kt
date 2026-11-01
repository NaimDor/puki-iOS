package io.github.tomsadeh.puki.data.content

import androidx.annotation.DrawableRes
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.models.BilingualText

data class AnimalDef(
    val id: String,
    val name: BilingualText,
    val foodId: String,
    @DrawableRes val drawableRes: Int
)

data class FoodDef(
    val id: String,
    val name: BilingualText,
    @DrawableRes val drawableRes: Int
)

/** Animals Hebrew speaks of as feminine (הפרה אומרת, הכלב אומר). */
val FEMININE_ANIMALS = setOf("cow", "chicken", "sheep", "goat", "giraffe")

/** All of them, for games that don't go by level. */
val allAnimals get() = level1Animals + level2Animals + level3Animals

val level1Animals = listOf(
    AnimalDef(
        id = "cow",
        name = BilingualText("פרה", "Cow"),
        foodId = "grass",
        drawableRes = R.drawable.ic_animal_cow
    ),
    AnimalDef(
        id = "dog",
        name = BilingualText("כלב", "Dog"),
        foodId = "bone",
        drawableRes = R.drawable.ic_animal_dog
    ),
    AnimalDef(
        id = "cat",
        name = BilingualText("חתול", "Cat"),
        foodId = "fish",
        drawableRes = R.drawable.ic_animal_cat
    ),
    AnimalDef(
        id = "chicken",
        name = BilingualText("תרנגולת", "Chicken"),
        foodId = "seeds",
        drawableRes = R.drawable.ic_animal_chicken
    )
)

val level2Animals = listOf(
    AnimalDef("horse", BilingualText("סוס", "Horse"), "hay", R.drawable.ic_animal_horse),
    AnimalDef("pig", BilingualText("חזיר", "Pig"), "corn", R.drawable.ic_animal_pig),
    AnimalDef("sheep", BilingualText("כבשה", "Sheep"), "clover", R.drawable.ic_animal_sheep),
    AnimalDef("duck", BilingualText("ברווז", "Duck"), "bread", R.drawable.ic_animal_duck),
    AnimalDef("rabbit", BilingualText("ארנב", "Rabbit"), "carrot", R.drawable.ic_animal_rabbit),
    AnimalDef("goat", BilingualText("עז", "Goat"), "leaves", R.drawable.ic_animal_goat)
)

val level3Animals = listOf(
    AnimalDef("lion", BilingualText("אריה", "Lion"), "meat", R.drawable.ic_animal_lion),
    AnimalDef("elephant", BilingualText("פיל", "Elephant"), "peanuts", R.drawable.ic_animal_elephant),
    AnimalDef("monkey", BilingualText("קוף", "Monkey"), "banana", R.drawable.ic_animal_monkey),
    AnimalDef("giraffe", BilingualText("ג׳ירפה", "Giraffe"), "leaves", R.drawable.ic_animal_giraffe),
    AnimalDef("penguin", BilingualText("פינגווין", "Penguin"), "fish", R.drawable.ic_animal_penguin)
)

val level1Foods = listOf(
    FoodDef(
        id = "grass",
        name = BilingualText("עשב", "Grass"),
        drawableRes = R.drawable.ic_food_grass
    ),
    FoodDef(
        id = "bone",
        name = BilingualText("עצם", "Bone"),
        drawableRes = R.drawable.ic_food_bone
    ),
    FoodDef(
        id = "fish",
        name = BilingualText("דג", "Fish"),
        drawableRes = R.drawable.ic_food_fish
    ),
    FoodDef(
        id = "seeds",
        name = BilingualText("גרעינים", "Seeds"),
        drawableRes = R.drawable.ic_food_seeds
    )
)

val level2Foods = level1Foods + listOf(
    FoodDef("hay", BilingualText("חציר", "Hay"), R.drawable.ic_food_hay),
    FoodDef("corn", BilingualText("תירס", "Corn"), R.drawable.ic_food_corn),
    FoodDef("clover", BilingualText("תלתן", "Clover"), R.drawable.ic_food_clover),
    FoodDef("bread", BilingualText("לחם", "Bread"), R.drawable.ic_food_bread),
    FoodDef("carrot", BilingualText("גזר", "Carrot"), R.drawable.ic_food_carrot),
    FoodDef("leaves", BilingualText("עלים", "Leaves"), R.drawable.ic_food_leaves)
)

val level3Foods = level2Foods + listOf(
    FoodDef("meat", BilingualText("בשר", "Meat"), R.drawable.ic_food_meat),
    FoodDef("peanuts", BilingualText("בוטנים", "Peanuts"), R.drawable.ic_food_peanuts),
    FoodDef("banana", BilingualText("בננה", "Banana"), R.drawable.ic_food_banana)
)


fun foodsForLevel(level: Int): List<FoodDef> = when {
    level >= 3 -> level3Foods
    level >= 2 -> level2Foods
    else -> level1Foods
}
