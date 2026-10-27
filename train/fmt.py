"""The prompt format the small model is trained on and run with: short, so a phone doesn't spend
its time reading Puki's long system prompt for every line. A base model learns it from the
data; the brain writes the same header and lets the model finish the line.

    lang: he
    child: girl
    task: moment
    input: במשחק הצבעים בחרו כחול. זה נכון!
    puki: יש! כחול כמו השמיים!<|endoftext|>

"@name" in a line is where the brain puts the child's name.
"""
END = "<|endoftext|>"  # (the base model's own end-of-text token, so it stops by itself)


def prompt(task, lang, girl, inp, earlier=None):
    lines = [f"lang: {lang}", f"child: {'girl' if girl else 'boy'}", f"task: {task}", f"input: {inp}"]
    if earlier:
        lines.append(f"earlier: {earlier}")
    return "\n".join(lines) + "\npuki:"


def example(task, lang, girl, inp, line, earlier=None):
    """One training text: the prompt, then the line and the end token."""
    return prompt(task, lang, girl, inp, earlier) + " " + line + END
