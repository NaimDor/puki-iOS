#!/usr/bin/env python3
"""Generate the voice lines for Puki with BlueTTS (offline, free).

Writes OGG Vorbis files to app/src/main/res/raw, named <key>_<lang>.ogg, or
<key>_he_f.ogg / <key>_he_m.ogg for lines that address the child by gender
(VoicePlayer picks the variant matching the gender setting).

Run with the BlueTTS virtualenv, which has blue_onnx installed:

    set PYTHONUTF8=1
    <BlueTTS checkout>\\.venv\\Scripts\\python scripts\\generate_voices.py
    ... --only voice_tidy_shirt,voice_prompt_bath    # just some lines
    ... --child-name "<name with nikud>" --child-gender girl    # also make the personal lines
    ... --out C:\\temp\\samples --voice lily         # try a voice without touching res/raw

Personal lines (voice_*_name*) say the child's name; they are git-ignored so the
name stays out of the repo, and the app falls back to the generic lines without them.

Then run scripts/check_voices.py to confirm nothing the app plays is missing.
"""

import argparse
import os
import re
import sys
import zlib

import numpy as np
import soundfile as sf

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RAW_DIR = os.path.join(ROOT, "app", "src", "main", "res", "raw")
# Default: the BlueTTS checkout whose .venv is running this script
DEFAULT_BLUETTS_DIR = os.environ.get("BLUETTS_DIR", os.path.dirname(sys.prefix))

# Toddler-friendly: a bit slower than normal speech
SPEED = 0.9
STEPS = 8


# ============================================================
# VOICE LINE DEFINITIONS
# Format: (resource_name, english_text, hebrew_text)
#
# hebrew_text is either a string, or a (girl, boy) tuple for lines that
# address the child directly — those become _he_f / _he_m files.
#
# Single words are wrapped in "זה/זאת X" so the toddler hears a short,
# natural sentence ("this is a cow") rather than a bare word.
# ============================================================

voice_lines = [
    # --- Animal names ---
    ("voice_animal_cow", "Cow", "זאת פרה."),
    ("voice_animal_dog", "Dog", "זה כלב."),
    ("voice_animal_cat", "Cat", "זה חתול."),
    ("voice_animal_chicken", "Chicken", "זאת תרנגולת."),
    ("voice_animal_horse", "Horse", "זה סוס."),
    ("voice_animal_pig", "Pig", "זה חזיר."),
    ("voice_animal_sheep", "Sheep", "זאת כבשה."),
    ("voice_animal_duck", "Duck", "זה ברווז."),
    ("voice_animal_rabbit", "Rabbit", "זה ארנב."),
    ("voice_animal_goat", "Goat", "זאת עז."),
    ("voice_animal_lion", "Lion", "זה אריה."),
    ("voice_animal_elephant", "Elephant", "זה פיל."),
    ("voice_animal_monkey", "Monkey", "זה קוף."),
    ("voice_animal_giraffe", "Giraffe", "זאת ג׳ירפה."),
    ("voice_animal_penguin", "Penguin", "זה פינגווין."),

    # --- Animal sounds ---
    ("voice_sound_cow", "Moo!", "מוּ!"),
    ("voice_sound_dog", "Woof woof!", "הַב הַב!"),
    ("voice_sound_cat", "Meow!", "מיאו!"),
    ("voice_sound_chicken", "Cluck cluck!", "קוֹ קוֹ קוֹ!"),  # a hen clucks; the rooster crows
    ("voice_sound_horse", "Neigh!", "אִיהַהַהָה!"),
    ("voice_sound_pig", "Oink oink!", "חְרְר חְרְר!"),
    ("voice_sound_sheep", "Baa!", "מֶעֶעֶ!"),
    ("voice_sound_duck", "Quack quack!", "גַע גַע!"),
    ("voice_sound_goat", "Maa!", "מֶעֶעֶ!"),
    ("voice_sound_lion", "Roar!", "רוֹאַאָר!"),
    ("voice_sound_elephant", "Trumpet!", "טְרוּוּוּ!"),
    ("voice_sound_monkey", "Ooh ooh ah ah!", "אוּ אוּ אָה אָה!"),
    ("voice_sound_rabbit", "Hop hop hop!", "הוֹפּ, הוֹפּ, הוֹפּ!"),
    ("voice_sound_giraffe", "Munch munch!", "אָם, אָם, אָם!"),
    ("voice_sound_penguin", "Waddle waddle!", "טִיפּ, טִיפּ, טִיפּ!"),

    # --- Who eats what (after a wrong food: the right one, as a little fact) ---
    ("voice_eats_cow", "The cow eats grass!", "הפרה אוכלת עשב!"),
    ("voice_eats_dog", "The dog eats a bone!", "הכלב אוכל עצם!"),
    ("voice_eats_cat", "The cat eats fish!", "החתול אוכל דג!"),
    ("voice_eats_chicken", "The chicken eats seeds!", "התרנגולת אוכלת גרעינים!"),
    ("voice_eats_horse", "The horse eats hay!", "הסוס אוכל חציר!"),
    ("voice_eats_pig", "The pig eats corn!", "החזיר אוכל תירס!"),
    ("voice_eats_sheep", "The sheep eats clover!", "הכבשה אוכלת תלתן!"),
    ("voice_eats_duck", "The duck eats bread!", "הברווז אוכל לחם!"),
    ("voice_eats_rabbit", "The rabbit eats a carrot!", "הארנב אוכל גזר!"),
    ("voice_eats_goat", "The goat eats leaves!", "העז אוכלת עלים!"),
    ("voice_eats_lion", "The lion eats meat!", "האריה אוכל בשר!"),
    ("voice_eats_elephant", "The elephant eats peanuts!", "הפיל אוכל בוטנים!"),
    ("voice_eats_monkey", "The monkey eats a banana!", "הקוף אוכל בננה!"),
    ("voice_eats_giraffe", "The giraffe eats leaves!", "הג'ירפה אוכלת עלים!"),
    ("voice_eats_penguin", "The penguin eats fish!", "הפינגווין אוכל דג!"),

    # --- Food names ---
    ("voice_food_grass", "Grass", "זה עשב."),
    ("voice_food_bone", "Bone", "זאת עצם."),
    ("voice_food_fish", "Fish", "זה דג."),
    ("voice_food_seeds", "Seeds", "אלה גרעינים."),
    ("voice_food_hay", "Hay", "זה חציר."),
    ("voice_food_corn", "Corn", "זה תירס."),
    ("voice_food_clover", "Clover", "זה תלתן."),
    ("voice_food_bread", "Bread", "זה לחם."),
    ("voice_food_carrot", "Carrot", "זה גזר."),
    ("voice_food_leaves", "Leaves", "אלה עלים."),
    ("voice_food_meat", "Meat", "זה בשר."),
    ("voice_food_peanuts", "Peanuts", "אלה בוטנים."),
    ("voice_food_banana", "Banana", "זאת בננה."),

    # --- Color names ---
    ("voice_color_red", "Red", "זה אדום."),
    ("voice_color_blue", "Blue", "זה כחול."),
    ("voice_color_yellow", "Yellow", "זה צהוב."),
    ("voice_color_green", "Green", "זה ירוק."),
    ("voice_color_orange", "Orange", "זה כתום."),
    ("voice_color_purple", "Purple", "זה סגול."),
    ("voice_color_pink", "Pink", "זה ורוד."),
    ("voice_color_brown", "Brown", "זה חום."),
    ("voice_color_white", "White", "זה לבן."),
    ("voice_color_black", "Black", "זה שחור."),

    # --- Shape names ---
    ("voice_shape_circle", "Circle", "זה עיגול."),
    ("voice_shape_square", "Square", "זה ריבוע."),
    ("voice_shape_triangle", "Triangle", "זה משולש."),
    ("voice_shape_star", "Star", "זה כוכב."),
    ("voice_shape_heart", "Heart", "זה לב."),
    ("voice_shape_diamond", "Diamond", "זה מעוין."),
    ("voice_shape_hexagon", "Hexagon", "זה משושה."),
    ("voice_shape_pentagon", "Pentagon", "זה מחומש."),
    ("voice_shape_octagon", "Octagon", "זה מתומן."),

    # --- Numbers 1-10 ---
    ("voice_number_1", "One", "אחת."),
    ("voice_number_2", "Two", "שתיים."),
    ("voice_number_3", "Three", "שלוש."),
    ("voice_number_4", "Four", "ארבע."),
    ("voice_number_5", "Five", "חמש."),
    ("voice_number_6", "Six", "שש."),
    ("voice_number_7", "Seven", "שבע."),
    ("voice_number_8", "Eight", "שמונה."),
    ("voice_number_9", "Nine", "תשע."),
    ("voice_number_10", "Ten", "עשר."),

    # --- Instrument names ---
    ("voice_instrument_drum", "Drum", "זה תוף."),
    ("voice_instrument_xylophone", "Xylophone", "זה קסילופון."),
    ("voice_instrument_tambourine", "Tambourine", "זה תוף מרים."),
    ("voice_instrument_maracas", "Maracas", "אלה רעשנים."),

    # --- Hebrew letters ---
    ("voice_letter_alef", "Alef", "זאת האות אָלֶף."),
    ("voice_letter_bet", "Bet", "זאת האות בֵּית."),
    ("voice_letter_gimel", "Gimel", "זאת האות גִּימֶל."),
    ("voice_letter_dalet", "Dalet", "זאת האות דָּלֶת."),
    ("voice_letter_he", "He", "זאת האות הֵא."),
    ("voice_letter_vav", "Vav", "זאת האות וָו."),
    ("voice_letter_het", "Het", "זאת האות חית."),
    ("voice_letter_tet", "Tet", "זאת האות טית."),
    ("voice_letter_yod", "Yod", "זאת האות יוד."),
    ("voice_letter_kaf", "Kaf", "זאת האות כף."),
    ("voice_letter_lamed", "Lamed", "זאת האות למד."),
    ("voice_letter_mem", "Mem", "זאת האות מם."),
    ("voice_letter_nun", "Nun", "זאת האות נון."),
    ("voice_letter_samekh", "Samekh", "זאת האות סמך."),
    ("voice_letter_ayin", "Ayin", "זאת האות עין."),
    ("voice_letter_pe", "Pe", "זאת האות פא."),
    ("voice_letter_qof", "Qof", "זאת האות קוף."),
    ("voice_letter_shin", "Shin", "זאת האות שין."),
    ("voice_letter_tav", "Tav", "זאת האות תו."),

    # --- English letters ---
    ("voice_letter_a", "A, like Apple", "זאת האות <en>A</en>."),
    ("voice_letter_b", "B, like Ball", "זאת האות <en>B</en>."),
    ("voice_letter_c", "C, like Cat", "זאת האות <en>C</en>."),
    ("voice_letter_d", "D, like Dog", "זאת האות <en>D</en>."),
    ("voice_letter_e", "E, like Elephant", "זאת האות <en>E</en>."),
    ("voice_letter_f", "F, like Fish", "זאת האות <en>F</en>."),
    ("voice_letter_g", "G, like Goat", "זאת האות <en>G</en>."),
    ("voice_letter_h", "H, like Horse", "זאת האות <en>H</en>."),
    ("voice_letter_l", "L, like Lion", "זאת האות <en>L</en>."),
    ("voice_letter_m", "M, like Moon", "זאת האות <en>M</en>."),
    ("voice_letter_p", "P, like Pig", "זאת האות <en>P</en>."),
    ("voice_letter_r", "R, like Rabbit", "זאת האות <en>R</en>."),
    ("voice_letter_s", "S, like Sun", "זאת האות <en>S</en>."),
    ("voice_letter_t", "T, like Teddy", "זאת האות <en>T</en>."),

    # --- Island names (said on the hub when she taps one) ---
    ("voice_island_animals", "Animal Garden", "גן החיות."),
    ("voice_island_colors", "Colors", "צבעים."),
    ("voice_island_shapes", "Shapes", "צורות."),
    ("voice_island_counting", "Counting", "ספירה."),
    ("voice_island_peekaboo", "Peekaboo", "מחבואים."),
    ("voice_island_music", "Music", "מוזיקה."),
    ("voice_island_words", "Words", "מילים."),
    ("voice_island_echo", "Echo Puki", "תוכי מחקה."),
    ("voice_island_myday", "My Day", "היום שלי."),
    ("voice_island_say", "Say it with Puki", "מדברים עם פוקי."),

    # --- Game prompts ---
    ("voice_prompt_whos_hungry", "Who's hungry?", "מי רוצה לאכול?"),
    ("voice_prompt_whos_hiding", "Who's hiding?", "מי מתחבא?"),
    ("voice_peekaboo", "Peekaboo!", "קוקו!"),
    ("voice_prompt_echo", "Say something to Puki!", ("תגידי משהו לפוקי!", "תגיד משהו לפוקי!")),
    ("voice_prompt_tap_the", "Tap the right one!", ("תלחצי על הנכון!", "תלחץ על הנכון!")),
    ("voice_prompt_drag_it", "Drag it to its place!", ("תזיזי את זה למקום שלו!", "תזיז את זה למקום שלו!")),
    ("voice_prompt_try_again", "Oops, let's try again!", "אופס! ננסה שוב!"),
    ("voice_prompt_great_job", "Great job!", "כל הכבוד!"),
    ("voice_prompt_well_done", "Well done!", "יפה מאוד!"),
    ("voice_prompt_count_along", "Let's count together!", ("בואי נספור ביחד!", "בוא נספור ביחד!")),
    ("voice_prompt_how_many", "How many are there?", "כמה יש?"),
    ("voice_prompt_whose_shadow", "Whose shadow is this?", "של מי הצל הזה?"),
    # --- Kitchen: baking a cake ---
    ("voice_cook_lets_bake", "Let's bake a cake! Put everything in the bowl.", ("בואי נאפה עוגה! תשימי הכול בקערה.", "בוא נאפה עוגה! תשים הכול בקערה.")),
    ("voice_cook_add", "Put everything in the bowl!", ("תשימי הכול בקערה!", "תשים הכול בקערה!")),
    ("voice_cook_flour", "Flour", "זה קמח."),
    ("voice_cook_sugar", "Sugar", "זה סוכר."),
    ("voice_cook_eggs", "Eggs", "אלה ביצים."),
    ("voice_cook_milk", "Milk", "זה חלב."),
    ("voice_cook_stir", "Now stir, round and round!", ("עכשיו תערבבי, סביב סביב!", "עכשיו תערבב, סביב סביב!")),
    ("voice_cook_oven", "Into the oven! Tap the oven.", ("עכשיו לתנור! תלחצי על התנור.", "עכשיו לתנור! תלחץ על התנור.")),
    ("voice_cook_ready", "Ding! The cake is ready!", "דינג! העוגה מוכנה!"),
    ("voice_cook_decorate", "Let's decorate the cake!", ("בואי נקשט את העוגה!", "בוא נקשט את העוגה!")),
    ("voice_cook_yum", "Yummy! What a delicious cake!", "יאמי! איזו עוגה טעימה!"),
    # --- Puki without his brain: what he says when tapped ---
    ("voice_puki_hi", "Squawk! Hi there!", "קוואק! שלום שלום!"),
    ("voice_puki_tickle", "Hee hee! That tickles!", "חי חי! זה מדגדג!"),
    ("voice_puki_fun", "Hooray! Playing is fun!", "יש! כיף לשחק!"),
    ("voice_puki_here", "Squawk squawk! Puki is here!", "קוואק קוואק! פוקי כאן!"),
    ("voice_puki_dance", "Look, I'm dancing!", "תראו, אני רוקד!"),
    # --- Dance and Freeze ---
    ("voice_dance_intro", "Let's dance! When the music stops, do what Puki says.", ("בואי נרקוד! כשהמוזיקה נעצרת, עושים מה שפוקי אומר.", "בוא נרקוד! כשהמוזיקה נעצרת, עושים מה שפוקי אומר.")),
    ("voice_dance_freeze", "Freeze!", "קופאים!"),
    ("voice_dance_jump", "Jump, jump!", "קופצים, קופצים!"),
    ("voice_dance_clap", "Clap your hands!", "מוחאים כפיים!"),
    ("voice_dance_spin", "Spin around!", "מסתובבים!"),
    ("voice_dance_again", "And dance!", "ורוקדים!"),
    # --- Garage: the car wash ---
    ("voice_car_intro", "The car is all muddy! Scrub it with the sponge.", ("המכונית מלאה בוץ! תשפשפי אותה עם הספוג.", "המכונית מלאה בוץ! תשפשף אותה עם הספוג.")),
    ("voice_car_scrub", "Scrub off the mud!", ("תשפשפי את הבוץ!", "תשפשף את הבוץ!")),
    ("voice_car_rinse", "Now rinse it with the hose!", ("עכשיו תשטפי עם הצינור!", "עכשיו תשטוף עם הצינור!")),
    ("voice_car_dry", "Dry it with the towel!", ("עכשיו תנגבי עם המגבת!", "עכשיו תנגב עם המגבת!")),
    ("voice_car_clean", "Beep beep! The car is clean!", "טוט טוט! המכונית נקייה!"),
    # --- Brushing Teddy's teeth ---
    ("voice_teeth_intro", "Let's brush Teddy's teeth! Put toothpaste on the brush.", ("בואי נצחצח לדובי את השיניים! תשימי משחה על המברשת.", "בוא נצחצח לדובי את השיניים! תשים משחה על המברשת.")),
    ("voice_teeth_paste", "Tap the toothpaste!", ("תלחצי על המשחה!", "תלחץ על המשחה!")),
    ("voice_teeth_scrub", "Brush every tooth!", ("תצחצחי כל שן!", "תצחצח כל שן!")),
    ("voice_teeth_rinse", "Now give Teddy some water!", ("עכשיו תני לדובי מים!", "עכשיו תן לדובי מים!")),
    ("voice_teeth_clean", "Sparkly teeth! What a big smile!", "שיניים מבריקות! איזה חיוך גדול!"),

    # --- My Day prompts ---
    ("voice_prompt_bath", "Let's take a bath!", ("בואי נתרחץ!", "בוא נתרחץ!")),
    ("voice_prompt_tidy_up", "Let's tidy up the room!", ("בואי נסדר את החדר!", "בוא נסדר את החדר!")),
    ("voice_prompt_get_dressed", "Let's get dressed! What goes on first?",
     ("בואי נתלבש! מה שמים קודם?", "בוא נתלבש! מה שמים קודם?")),
    ("voice_prompt_set_table", "Let's set the table!", ("בואי נערוך את השולחן!", "בוא נערוך את השולחן!")),
    ("voice_prompt_time_break", "Time for a break!", "הגיע הזמן להפסקה!"),
    ("voice_prompt_see_you_soon", "See you soon!", "נתראה בקרוב!"),

    # --- Body parts ---
    ("voice_bath_rinse", "Now rinse off in the shower!", "עכשיו שוטפים במקלחת!"),
    ("voice_bath_dry", "Now dry off with the towel!", "עכשיו מתנגבים במגבת!"),
    ("voice_bath_clean", "All clean!", "נקי נקי!"),
    ("voice_body_arms", "Arms", "אלה ידיים."),
    ("voice_body_legs", "Legs", "אלה רגליים."),
    ("voice_body_tummy", "Tummy", "זאת בטן."),
    ("voice_body_face", "Face", "אלה פנים."),

    # --- Clothing ---
    ("voice_dress_done", "All dressed! Let's go!", "התלבשנו! אפשר לצאת!"),
    ("voice_clothing_shirt", "Shirt", "זאת חולצה."),
    ("voice_clothing_pants", "Pants", "אלה מכנסיים."),
    ("voice_clothing_socks", "Socks", "אלה גרביים."),
    ("voice_clothing_shoes", "Shoes", "אלה נעליים."),

    # --- Table items ---
    ("voice_table_done", "Dinner's ready! Yum!", "הארוחה מוכנה! יאמי!"),
    ("voice_tidy_done", "All tidy! Everything's in its place!", "החדר מסודר! הכול במקום!"),
    ("voice_table_plate", "Plate", "זאת צלחת."),
    ("voice_table_cup", "Cup", "זאת כוס."),
    ("voice_table_fork", "Fork", "זה מזלג."),
    ("voice_table_spoon", "Spoon", "זאת כפית."),

    # --- Tidy items ---
    ("voice_tidy_teddy", "Teddy", "זה דובי."),
    ("voice_tidy_book", "Book", "זה ספר."),
    ("voice_tidy_shirt", "Shirt", "זאת חולצה."),
    ("voice_tidy_ball", "Ball", "זה כדור."),

    # --- Sticker names ---
    ("voice_sticker_cow", "Cow", "זאת פרה."),
    ("voice_sticker_dog", "Dog", "זה כלב."),
    ("voice_sticker_cat", "Cat", "זה חתול."),
    ("voice_sticker_rainbow", "Rainbow", "זאת קשת."),
    ("voice_sticker_star", "Star", "זה כוכב."),
    ("voice_sticker_flower", "Flower", "זה פרח."),
    ("voice_sticker_butterfly", "Butterfly", "זה פרפר."),
    ("voice_sticker_heart", "Heart", "זה לב."),
    ("voice_sticker_music", "Music", "זה תו של מוזיקה."),
    ("voice_sticker_sun", "Sun", "זאת שמש."),
    ("voice_sticker_moon", "Moon", "זה ירח."),
    ("voice_sticker_rocket", "Rocket", "זה טיל."),
    ("voice_sticker_crown", "Crown", "זה כתר."),
    ("voice_sticker_trophy", "Trophy", "זה גביע."),
    ("voice_sticker_diamond", "Diamond", "זה יהלום."),
    ("voice_sticker_unicorn", "Unicorn", "זה חד קרן."),
    ("voice_sticker_duck", "Duck", "זה ברווז."),
    ("voice_sticker_horse", "Horse", "זה סוס."),
    ("voice_sticker_sheep", "Sheep", "זאת כבשה."),
    ("voice_sticker_pig", "Pig", "זה חזיר."),
    ("voice_sticker_rabbit", "Rabbit", "זה ארנב."),
    ("voice_sticker_chicken", "Chicken", "זאת תרנגולת."),
    ("voice_sticker_goat", "Goat", "זאת עז."),
    ("voice_sticker_monkey", "Monkey", "זה קוף."),
    ("voice_sticker_lion", "Lion", "זה אריה."),
    ("voice_sticker_elephant", "Elephant", "זה פיל."),
    ("voice_sticker_giraffe", "Giraffe", "זאת ג׳ירפה."),
    ("voice_sticker_penguin", "Penguin", "זה פינגווין."),
    ("voice_sticker_new", "A new sticker!", "מדבקה חדשה!"),
    ("voice_album_hello", "Here are your stickers!", "הנה המדבקות שלך!"),
    ("voice_more_stars", "A few more stars!", "עוד כמה כוכבים!"),

    # --- Drawing ---
    ("voice_canvas_hello", "Let's paint!", "בואו נצייר!"),
    ("voice_page_blank", "A clean page!", "דף חלק!"),
    ("voice_page_fish", "A fish!", "דג!"),
    ("voice_page_flower", "A flower!", "פרח!"),
    ("voice_page_house", "A house!", "בית!"),
    ("voice_page_car", "A car!", "מכונית!"),
    ("voice_page_letters", "Letters!", "אותיות!"),
    ("voice_page_numbers", "Numbers!", "מספרים!"),

    # --- Bedtime steps ---
    ("voice_bedtime_pajamas", "Let's put on pajamas!", "נִלְבַּשׁ פִּיגָ׳מָה!"),
    ("voice_bedtime_brush", "Let's brush our teeth!", "נְצַחְצֵחַ שִׁנַּיִם!"),
    ("voice_bedtime_teddy", "Let's put teddy in bed!", "נָשִׂים אֶת הַדֻּבִּי בַּמִּטָּה!"),
    ("voice_bedtime_light", "Let's turn off the light!", "נְכַבֶּה אֶת הָאוֹר!"),
    ("voice_bedtime_stars", "Stars in the sky! Good night!", "כּוֹכָבִים בַּשָּׁמַיִם! לַיְלָה טוֹב!"),

    # --- Music Meadow ---
    ("voice_music_free", "Tap the instruments and play!", ("תִּלְחֲצִי עַל הַכֵּלִים וּתְנַגְּנִי!", "תִּלְחַץ עַל הַכֵּלִים וּתְנַגֵּן!")),
    ("voice_music_listen", "Listen and remember!", ("תַּקְשִׁיבִי וְתִזְכְּרִי!", "תַּקְשִׁיב וְתִזְכֹּר!")),
    ("voice_music_your_turn", "Your turn!", ("עַכְשָׁיו תּוֹרֵךְ!", "עַכְשָׁיו תּוֹרְךָ!")),

    # --- Praise (VoicePlayer picks one at random after a right answer) ---
    ("voice_praise_1", "Yay!", "יֵשׁ!"),
    ("voice_praise_2", "Wonderful!", "יוֹפִי!"),
    ("voice_praise_3", "You're a champion!", ("אֵיזוֹ אַלּוּפָה!", "אֵיזֶה אַלּוּף!")),
    ("voice_praise_4", "Wow, excellent!", "וָאוּ, מְצוּיָּן!"),
    ("voice_praise_5", "You did it!", ("את הצלחת!", "אתה הצלחת!")),

    # --- Say it with Puki: Puki says a word, the child says it back ---
    # Bare words, so the child hears exactly what to repeat
    ("voice_say_dog", "Dog!", "כֶּלֶב!"),
    ("voice_say_cat", "Cat!", "חָתוּל!"),
    ("voice_say_cow", "Cow!", "פָּרָה!"),
    ("voice_say_duck", "Duck!", "בַּרְוָז!"),
    ("voice_say_horse", "Horse!", "סוּס!"),
    ("voice_say_sheep", "Sheep!", "כִּבְשָׂה!"),
    ("voice_say_elephant", "Elephant!", "פִּיל!"),
    ("voice_say_monkey", "Monkey!", "קוֹף!"),
    ("voice_say_lion", "Lion!", "אַרְיֵה!"),
    ("voice_say_fish", "Fish!", "דָּג!"),
    ("voice_say_banana", "Banana!", "בָּנָנָה!"),
    ("voice_say_bread", "Bread!", "לֶחֶם!"),
    ("voice_say_carrot", "Carrot!", "גֶּזֶר!"),
    ("voice_say_apple", "Apple!", "תַּפּוּחַ!"),
    ("voice_say_ball", "Ball!", "כַּדּוּר!"),
    ("voice_say_book", "Book!", "סֵפֶר!"),
    ("voice_say_teddy", "Teddy!", "דֻּבִּי!"),
    ("voice_say_cup", "Cup!", "כּוֹס!"),
    ("voice_say_shoes", "Shoes!", "נַעֲלַיִם!"),
    ("voice_say_moon", "Moon!", "יָרֵחַ!"),
    ("voice_say_house", "House!", "בַּיִת!"),
    ("voice_say_drum", "Drum!", "תּוֹף!"),
    ("voice_say_intro", "Let's say words with Puki!", ("בּוֹאִי נַגִּיד מִלִּים עִם פּוּקִי!", "בּוֹא נַגִּיד מִלִּים עִם פּוּקִי!")),
    ("voice_say_your_turn", "Now you!", ("עַכְשָׁיו אַתְּ!", "עַכְשָׁיו אַתָּה!")),
    ("voice_say_again", "One more time?", "עוֹד פַּעַם?"),
    ("voice_say_nice", "Nice!", "יוֹפִי!"),
    ("voice_say_almost", "Almost!", "כִּמְעַט!"),
    ("voice_say_listen", "Listen:", ("תַּקְשִׁיבִי טוֹב:", "תַּקְשִׁיב טוֹב:")),

    # --- Greeting on the hub, and the end of a game ---
    ("voice_hello", "Hello! Where shall we sail today?", "שָׁלוֹם! לְאָן נָשׁוּט הַיּוֹם?"),
    ("voice_round_done", "All done! Great job!", ("את סיימת! כל הכבוד!", "אתה סיימת! כל הכבוד!")),
]

# Lines with the child's name, generated only with --child-name. "{name}" is
# replaced by the name; (girl, boy) picks the form by --child-gender.
name_lines = [
    ("voice_praise_name_1", "כָּל הַכָּבוֹד, {name}!"),
    ("voice_praise_name_2", ("{name}, את אלופה!", "{name}, אתה אלוף!")),
    ("voice_praise_name_3", "יוֹפִי, {name}!"),
    ("voice_praise_name_4", "וָאוּ {name}, אֵיזֶה יוֹפִי!"),
    ("voice_hello_name", "שָׁלוֹם {name}! לְאָן נָשׁוּט הַיּוֹם?"),
    ("voice_round_done_name", ("{name}, את סיימת! כל הכבוד!", "{name}, אתה סיימת! כל הכבוד!")),
]


# BlueTTS's Hebrew pronunciation model garbles text that already has nikud, so
# nikud above is only a reading aid and is stripped before synthesis. Where plain
# spelling is ambiguous (letter names, animal sounds, some verb forms), the exact
# pronunciation is given as phonemes instead, keyed by output file name.
PHONEMES = {
    "voice_sound_cow_he": "mˈuuu!",
    "voice_sound_chicken_he": "ko ko kˈo!",
    "voice_eats_pig_he": "haχazˈiʁ ʔoχˈel tiʁˈas!",  # plain spelling came out "טרס"
    "voice_eats_sheep_he": "hakivsˈa ʔoχˈelet tiltˈan!",  # and "טלטן"
    "voice_sound_horse_he": "ʔihihihˈi!",
    "voice_sound_pig_he": "ʔˈojnk ʔˈojnk!",
    "voice_sound_sheep_he": "mˈeee!",
    "voice_sound_goat_he": "mˈeee!",
    "voice_sound_elephant_he": "tʁˈuuu!",
    "voice_sound_giraffe_he": "hˈam, hˈam, hˈam!",
    "voice_letter_alef_he": "zˈot haʔˈot ʔˈalef.",
    "voice_letter_bet_he": "zˈot haʔˈot bˈet.",
    "voice_letter_dalet_he": "zˈot haʔˈot dˈalet.",
    "voice_letter_he_he": "zˈot haʔˈot hˈe.",
    "voice_letter_vav_he": "zˈot haʔˈot vˈav.",
    "voice_letter_het_he": "zˈot haʔˈot χˈet.",
    "voice_letter_tet_he": "zˈot haʔˈot tˈet.",
    "voice_letter_yod_he": "zˈot haʔˈot jˈud.",
    "voice_letter_kaf_he": "zˈot haʔˈot kˈaf.",
    "voice_letter_lamed_he": "zˈot haʔˈot lˈamed.",
    "voice_letter_mem_he": "zˈot haʔˈot mˈem.",
    "voice_letter_nun_he": "zˈot haʔˈot nˈun.",
    "voice_letter_samekh_he": "zˈot haʔˈot sˈameχ.",
    "voice_letter_ayin_he": "zˈot haʔˈot ʔˈajin.",
    "voice_letter_pe_he": "zˈot haʔˈot pˈe.",
    "voice_letter_qof_he": "zˈot haʔˈot kˈuf.",
    "voice_letter_shin_he": "zˈot haʔˈot ʃˈin.",
    "voice_letter_tav_he": "zˈot haʔˈot tˈav.",
    "voice_say_book_he": "sˈefeʁ!",  # not sapar (barber)
    "voice_say_your_turn_he_f": "ʔaχʃˈav ʔˈat!",
    "voice_bedtime_brush_he": "netsaχtsˈeaχ ʃinˈajim!",
    "voice_bedtime_teddy_he": "nasˈim ʔˈet hadˈubi bamitˈa!",
    "voice_music_listen_he_m": "takʃˈiv vetizkˈoʁ!",
    "voice_music_your_turn_he_f": "ʔaχʃˈav toʁˈeχ!",
    "voice_music_your_turn_he_m": "ʔaχʃˈav toʁχˈa!",
}

NIKUD = re.compile(r"[\u0591-\u05C7]")


def strip_nikud(text):
    return NIKUD.sub("", text)


# Words BlueTTS reads wrong (the brain swaps them too, in persona.py's MISREAD): a recorded line
# with one of them is refused, so it gets reworded instead of recorded wrong
MISREAD = {"הידד": "it comes out as 'אהדה'"}


def check_misreads(lines):
    bad = [(name, word, why) for name, _, he in lines for text in (he if isinstance(he, tuple) else (he,))
           for word, why in MISREAD.items() if word in strip_nikud(text)]
    if bad:
        sys.exit("These lines use words BlueTTS misreads; reword them:\n" +
                 "\n".join(f"  {name}: {word} ({why})" for name, word, why in bad))


def outputs_for(name, en_text, he_text, lang):
    """Yield (file_stem, text) pairs to synthesize for one line."""
    if lang == "en":
        yield f"{name}_en", en_text
    elif isinstance(he_text, tuple):
        girl, boy = he_text
        yield f"{name}_he_f", girl
        yield f"{name}_he_m", boy
    else:
        yield f"{name}_he", he_text


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--lang", choices=["he", "en"], default="he")
    parser.add_argument("--voice", default="noa", help="BlueTTS voice: noa, lily, daniel, adam")
    parser.add_argument("--only", help="comma-separated resource names to generate")
    parser.add_argument("--out", default=RAW_DIR, help="output directory (default: res/raw)")
    parser.add_argument("--bluetts-dir", default=DEFAULT_BLUETTS_DIR)
    parser.add_argument("--child-name", help="the child's name, with nikud for clear pronunciation")
    parser.add_argument("--child-gender", choices=["girl", "boy"], default="girl")
    args = parser.parse_args()

    lines = list(voice_lines)
    if args.child_name and args.lang == "he":
        pick = 0 if args.child_gender == "girl" else 1
        for name, he in name_lines:
            text = he[pick] if isinstance(he, tuple) else he
            lines.append((name, "", text.replace("{name}", args.child_name)))
    if args.only:
        wanted = set(args.only.split(","))
        unknown = wanted - {name for name, _, _ in lines}
        if unknown:
            sys.exit(f"Unknown voice lines: {', '.join(sorted(unknown))}")
        lines = [line for line in lines if line[0] in wanted]

    check_misreads(lines)
    from blue_onnx import BlueTTS

    bluetts_dir = os.path.abspath(args.bluetts_dir)
    tts = BlueTTS(
        onnx_dir=os.path.join(bluetts_dir, "onnx_models"),
        style_json=os.path.join(bluetts_dir, "voices", f"{args.voice}.json"),
        config_path=os.path.join(bluetts_dir, "config", "tts.json"),
    )
    os.makedirs(args.out, exist_ok=True)

    jobs = [job for name, en, he in lines for job in outputs_for(name, en, he, args.lang)]
    print(f"Generating {len(jobs)} files with BlueTTS voice '{args.voice}' -> {args.out}")
    for i, (stem, text) in enumerate(jobs, 1):
        # Seed per file so re-running gives the same take
        np.random.seed(zlib.crc32(stem.encode()))
        if stem in PHONEMES:
            text = PHONEMES[stem]
            samples, sr = tts.synthesize(text, lang=args.lang, total_step=STEPS, speed=SPEED, text_is_phonemes=True)
        else:
            text = strip_nikud(text)
            samples, sr = tts.synthesize(text, lang=args.lang, total_step=STEPS, speed=SPEED)
        # A short tail so the last syllable isn't cut off by the player
        samples = np.concatenate([samples, np.zeros(int(sr * 0.15), dtype=np.float32)])
        sf.write(os.path.join(args.out, f"{stem}.ogg"), samples, sr, format="OGG", subtype="VORBIS")
        print(f"  [{i}/{len(jobs)}] {stem}  {text}")

    # A line that became gendered leaves a stale plain _he file behind; remove it
    if args.lang == "he" and os.path.abspath(args.out) == os.path.abspath(RAW_DIR):
        for name, _, he in lines:
            stale = os.path.join(args.out, f"{name}_he.ogg")
            if isinstance(he, tuple) and os.path.exists(stale):
                os.remove(stale)
                print(f"  removed {name}_he.ogg (replaced by _he_f / _he_m)")


if __name__ == "__main__":
    main()
