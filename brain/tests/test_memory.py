"""Puki remembers: exchanges are logged, a diary entry covers them, and the prompt gets the
profile, the last week's diary and today's session so far."""
import datetime
import os
import sys
import time

BRAIN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BRAIN)
import memory  # noqa: E402

DAY = 24 * 3600


def at(days_ago, hour=10):
    """[hour] o'clock, [days_ago] calendar days ago (by date, so a clock change doesn't shift the day)."""
    d = datetime.date.today() - datetime.timedelta(days=days_ago)
    return time.mktime((d.year, d.month, d.day, hour, 0, 0, 0, 0, -1))


def test_nothing_to_remember_yet(tmp_path):
    m = memory.Memory(str(tmp_path))
    assert m.prompt_block() == "" and m.unwritten() == [] and m.diary() == []


def test_the_session_so_far_is_in_the_prompt(tmp_path):
    m = memory.Memory(str(tmp_path))
    now = at(0, 11)
    m.note("say", "he", situation="במשחק הצבעים בחרו כחול. זה נכון!", said="יש! כחול!", t=now - 60)
    m.note("listen", "he", heard="פיל!", said="פיל גדול!", t=now - 30)
    block = m.prompt_block(now=now)
    assert "Just now:" in block and 'She said: "פיל!"' in block and "בחרו כחול" in block
    assert "never make one up" in block
    assert 'He said: "פיל!"' in m.prompt_block(now=now, girl=False)


def test_a_diary_entry_covers_the_session_and_is_remembered_by_day(tmp_path):
    m = memory.Memory(str(tmp_path))
    for i in range(4):
        m.note("say", "he", situation=f"רגע {i}", said="יש!", t=at(1) + i)
    assert len(m.unwritten()) == 4
    m.add_entry("She fed the elephant and said \"פיל!\" twice.", m.unwritten()[-1]["t"])
    assert m.unwritten() == []
    m.set_profile("- Loves elephants\n- Says \"פיל\"")
    block = m.prompt_block(now=at(0, 9))
    assert "Yesterday: She fed the elephant" in block and "About her:\n- Loves elephants" in block
    assert "Just now:" not in block  # yesterday's session is in the diary, not "just now"
    assert "רגע" not in block


def test_old_entries_leave_the_prompt(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.add_entry("A long time ago.", at(10))
    m.add_entry("Three days ago.", at(3))
    block = m.prompt_block(now=at(0))
    assert "3 days ago: Three days ago." in block and "A long time ago" not in block


def test_a_broken_line_doesnt_lose_the_log(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("say", "he", situation="א", said="ב")
    with open(m.log_path, "a", encoding="utf-8") as f:
        f.write('{"t": 1, "kind"')  # cut short by a crash
    m.note("say", "he", situation="ג", said="ד")
    assert [e["situation"] for e in m.entries()] == ["א", "ג"]


def test_the_prompts_quote_her_words_and_follow_gender(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("repeat", "he", situation="כלב", heard="", t=at(0))
    m.note("repeat", "he", situation="חתול", heard="חתול", t=at(0) + 1)
    entries = m.entries()
    diary = m.diary_prompt(entries, "the little boy", girl=False)
    assert 'he tried to say the word "כלב"' in diary and 'he said the word "חתול"' in diary and "his words" in diary
    assert "Only facts from the log" in diary
    assert "(empty: this is the first session)" in m.profile_prompt("He played.", "the little boy", girl=False)


def test_fields_are_capped(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("listen", "he", heard="א" * 5000, said="ב")
    assert len(m.entries()[0]["heard"]) == memory.FIELD_CHARS
    m.add_entry("x" * 5000, 1.0)
    assert len(m.diary()[0]["text"]) == memory.ENTRY_CHARS


def test_a_game_line_reaches_back_only_to_the_same_thing(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("say", "he", situation="במשחק החיות: הילדה האכילה את הפיל בבננה.", said="יאמי!", t=at(1, 10))
    m.note("say", "he", situation="במשחק החיות: הילדה האכילה את הקוף.", said="מממ!", t=at(1, 11))
    m.add_entry("She fed the elephant and the monkey.", at(1, 11))
    when, then = m.related("במשחק החיות: הילדה האכילה את הפיל.", now=at(0))
    assert when == ("yesterday", "אתמול") and "הפיל" in then["situation"]  # not the monkey just because both were fed
    assert m.related("במשחק הצבעים: מחפשים את הצבע צהוב.", now=at(0)) is None
    assert m.related("במשחק החיות: הפרה מחכה לאוכל.", now=at(0)) is None


def test_this_session_isnt_reached_back_to(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("say", "he", situation="במשחק החיות: הילדה האכילה את הפיל.", said="יאמי!", t=at(0, 9))
    assert m.related("במשחק החיות: הילדה האכילה את הפיל שוב.", now=at(0, 10)) is None  # no diary yet: it's "just now"


def test_word_forms_meet():
    assert memory._forms("הפילים") & memory._forms("פיל")
    assert memory._forms("בננות") & memory._forms("בננה")
    assert memory._forms("כחולה") & memory._forms("כחול")
    assert memory._forms("וְהַפִּיל") & memory._forms("פיל")  # nikud and two prefixes
    assert memory._forms("dogs") & memory._forms("dog")
    assert not memory._forms("פרה") & memory._forms("פרח")


def test_her_own_words_and_say_it_tries_are_found(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("listen", "he", heard="פילים! פילים גדולים!", said="וואו!", t=at(2, 10))
    m.note("repeat", "he", situation="כלב", heard="", t=at(1, 10))  # tried, not quite (brain.py keeps no transcript)
    m.add_entry("She talked about elephants and tried to say dog.", at(1, 10))
    when, moment = m.related("במשחק החיות: הילדה האכילה את הפיל.", now=at(0))
    assert when[0] == "2 days ago" and moment["kind"] == "listen"
    assert m.moment_text(moment, "he") == 'אמרה לך "פילים! פילים גדולים!"'
    assert m.moment_text(moment, "en", girl=False) == 'he said to you "פילים! פילים גדולים!"'
    when, moment = m.related("במשחק המחבואים: הכלב מתחבא ומחפשים אותו.", now=at(0))
    assert moment["kind"] == "repeat" and m.moment_text(moment, "he") == 'ניסתה להגיד "כלב"'
    assert m.moment_text({"kind": "repeat", "situation": "כלב", "heard": "כלב"}, "en") == 'she said the word "כלב"'


def test_a_diary_quote_is_found_when_the_log_has_nothing(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.add_entry('She drew a sun and said "שמש!" twice. She liked the yellow paint.', at(1, 10))
    when, moment = m.related("בציור: הילדה ציירה שמש צהובה.", now=at(0))
    assert when[0] == "yesterday" and moment == {"kind": "diary", "text": 'She drew a sun and said "שמש!" twice.'}


def test_names_dont_make_moments_alike(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("listen", "he", heard="פוקי! פוקי!", said="שלום!", t=at(1, 10))
    m.add_entry("She called Puki.", at(1, 10))
    assert m.related("במשחק הצבעים: פוקי מחפש כחול.", now=at(0), names=("פוקי", "Puki")) is None
    assert m.related("במשחק הצבעים: פוקי מחפש כחול.", now=at(0)) is not None  # (without the names, it would)


def test_forgetting_an_entry_forgets_its_play_too(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("say", "he", situation="במשחק החיות: הילדה האכילה את הפיל.", said="יאמי!", t=at(2, 10))
    m.add_entry("She fed the elephant.", at(2, 10))
    m.note("say", "he", situation="במשחק החיות: הילדה האכילה את הקוף.", said="מממ!", t=at(1, 10))
    m.add_entry("She fed the monkey.", at(1, 10))
    assert m.forget(at(2, 10)) and not m.forget(12345.0)
    assert [e["text"] for e in m.diary()] == ["She fed the monkey."]
    assert [e["situation"] for e in m.entries()] == ["במשחק החיות: הילדה האכילה את הקוף."]
    assert m.related("במשחק החיות: הילדה האכילה את הפיל.", now=at(0)) is None  # gone, not just hidden
    assert m.unwritten() == []


def test_forgetting_the_last_entry_doesnt_bring_its_play_back(tmp_path):
    m = memory.Memory(str(tmp_path))
    for i in range(3):
        m.note("say", "he", situation=f"רגע {i}", said="יש!", t=at(1, 10) + i)
    m.add_entry("Three moments.", at(1, 10) + 2)
    m.forget(at(1, 10) + 2)
    assert m.diary() == [] and m.unwritten() == []  # not written again from the same play


def test_erase_and_pause(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("say", "he", situation="א", said="ב")
    m.add_entry("Played.", time.time())
    m.set_profile("- Loves cows")
    m.erase()
    assert m.entries() == [] and m.diary() == [] and m.profile() == "" and m.prompt_block() == ""
    m.set_paused(True)
    m.note("say", "he", situation="ג", said="ד")
    assert m.paused() and m.entries() == []
    m.set_paused(False)
    m.note("say", "he", situation="ה", said="ו")
    assert not m.paused() and len(m.entries()) == 1


def test_old_memory_is_pruned(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("say", "he", situation="ישן", said="א", t=at(100))
    m.add_entry("Long ago.", at(100))
    m.note("say", "he", situation="חדש", said="ב", t=at(1))
    m.add_entry("Yesterday.", at(1))
    m.prune(90)
    assert [e["situation"] for e in m.entries()] == ["חדש"] and [e["text"] for e in m.diary()] == ["Yesterday."]
    m.prune(0)  # keeps everything
    assert len(m.entries()) == 1


def test_questions_for_later_are_kept_with_the_entry(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.add_entry("She fed the elephant.", at(1), ["איזו חיה האכלת היום?", "", "x" * 500, "ומה הפיל אכל?", "ועוד?"])
    e = m.diary()[0]
    assert e["ask"][0] == "איזו חיה האכלת היום?" and len(e["ask"]) == 3 and len(e["ask"][1]) == memory.ASK_CHARS
    m.add_entry("Played again.", at(0))
    assert "ask" not in m.diary()[1]
    prompt = m.ask_prompt("He fed the elephant.", "the little boy", "he", girl=False)
    assert "Hebrew" in prompt and "masculine" in prompt and "ask him" in prompt
