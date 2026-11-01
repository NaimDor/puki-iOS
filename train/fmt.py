"""The prompt format the small model is trained on and run with: short, so a phone doesn't spend
its time reading Puki's long system prompt for every line. A base model learns it from the
data; the brain writes the same header and lets the model finish the line.

    lang: he
    child: girl
    task: moment
    input: במשחק הצבעים בחרו כחול. זה נכון!
    puki: יש! כחול כמו השמיים!
    say: jˈeʃ! kaχˈol kmˈo haʃamˈajim!<|endoftext|>

"@name" in a line is where the brain puts the child's name. A Hebrew line comes with its phonemes,
which BlueTTS speaks directly, so no pronunciation model is needed at runtime.
"""
END = "<|endoftext|>"  # (the base model's own end-of-text token, so it stops by itself)


def prompt(task, lang, girl, inp, earlier=None):
    lines = [f"lang: {lang}", f"child: {'girl' if girl else 'boy'}", f"task: {task}", f"input: {inp}"]
    if earlier:
        lines.append(f"earlier: {earlier}")
    return "\n".join(lines) + "\npuki:"


def example(task, lang, girl, inp, line, earlier=None, say=None):
    """One training text: the prompt, then the line, its phonemes if it has them, and the end token."""
    return prompt(task, lang, girl, inp, earlier) + " " + line + (f"\nsay: {say}" if say else "") + END


def parse(answer):
    """(line, phonemes or None) from what the model wrote."""
    line, _, say = answer.replace(END, "").partition("\nsay:")
    return line.strip(), say.strip() or None
