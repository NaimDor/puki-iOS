"""Puki's own pronunciations: table words get his phonemes (by the child's gender where it
differs), everything else is left to RenikudPlus, and odd lines fall back to the usual way."""
import os
import sys

BRAIN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BRAIN)
import pronunciation  # noqa: E402

FAKE = {"וואו,": "wˈaʔu,", "כבשה": "kivsˈa", "מחכה.": "meχakˈa.", "מצאת": "matsˈata", "את": "ʔˈet",
        "הפרה!": "hapaʁˈa!", "פוקי": "pˈoki", "שמח": "samˈeaχ"}


def g2p(content, lang):
    """A stand-in for RenikudPlus: word by word from FAKE, English untouched."""
    if lang != "he":
        return content
    return " ".join(FAKE.get(w, "x") for w in content.split())


def test_the_table_loads_with_the_known_fixes():
    assert pronunciation.lookup("וואו", True) == "wˈaw"
    assert pronunciation.lookup("פוקי", False) == "pˈuki"
    assert pronunciation.lookup("מצאת", True) == "matsˈat" and pronunciation.lookup("מצאת", False) is None


def test_table_words_get_pukis_phonemes_with_punctuation_kept():
    ph = pronunciation.phonemes("וואו, כבשה מחכה.", "he", True, g2p)
    assert ph == "<he>wˈaw, kivsˈa meχakˈa.</he>"


def test_gender_decides_second_person_verbs():
    girl = pronunciation.phonemes("מצאת את הפרה!", "he", True, g2p)
    assert girl == "<he>matsˈat ʔˈet hapaʁˈa!</he>"
    assert pronunciation.phonemes("מצאת את הפרה!", "he", False, g2p) is None  # (RenikudPlus is right for a boy)


def test_lines_without_table_words_or_with_digits_are_spoken_as_before():
    assert pronunciation.phonemes("כבשה מחכה.", "he", True, g2p) is None
    assert pronunciation.phonemes("וואו, 3 כבשים", "he", True, g2p) is None


def test_english_spans_stay_and_misaligned_lines_fall_back():
    ph = pronunciation.phonemes("פוקי <en>yay</en> שמח", "he", True, g2p)
    assert ph == "<he>pˈuki</he><en>yay</en><he>samˈeaχ</he>"
    assert pronunciation.phonemes("פוקי שמח", "he", True, lambda c, l: "only-one") is None
