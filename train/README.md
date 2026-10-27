# Puki's small model

A small Hebrew/English model for Puki's lines, so the brain can run on modest phones and be
quick on any PC: Dicta-LM 3.0 1.7B **Base** (Qwen3-1.7B trained on Hebrew, Apache-2.0), taught
Puki's job with a LoRA on lines a frontier model writes.

1. **Data** (`build.py`): the teacher (DeepSeek) writes several lines for each input, Puki's own
   filters (`brain/persona.py`, `brain/safety.py`) and the teacher's judge keep the best one or
   two, and some inputs are held out to test on. Tasks today: game moments read from the app's
   code (`moments.py`), tapping Puki, talking back, careful moments (the exact fixed replies),
   and reaching back to an earlier moment. A new feature is a new task: add it, make its data,
   retrain.
2. **Format** (`fmt.py`): a short header (language, girl or boy, task, input) instead of Puki's
   long system prompt; "@name" where the brain puts the child's name.
3. **Train** a LoRA on the base model, merge it, convert to GGUF, build an imatrix from the
   dataset itself, quantize to Q4_K_M, and run the same bake-off on the held-out set.

Only synthetic inputs go to the teacher: moments from the app's code and made-up toddler
phrases, with "@name" in place of any name. Nothing a child said is ever sent.

The DeepSeek key comes from `$DEEPSEEK_API_KEY`, `~/keys/deepseek.txt`, or Auto-GM's `~/.config/auto-gm/keys.json`; answers are cached in
`data/cache.jsonl`, so a rerun only pays for what's new. `data/` and `out/` are git-ignored.

```bash
python moments.py          # how many game moments the app has
python build.py --pilot 10 # a few inputs per task, to read data/pilot_review.txt first
python build.py            # the whole dataset
```
