# How Puki's models are made

Puki's device brain is four small models, all ONNX int8, downloaded once by the app
(`DeviceModels.kt` pins each file by size and SHA-256). Two are ours, trained here; two are
other people's, made ready for phones here.

| Model | Hugging Face | What it does | Ours? |
|---|---|---|---|
| **Brains** | [dtsj89/puki-brains](https://huggingface.co/dtsj89/puki-brains) | Writes Puki's lines, and rates what the child said (the safety gate) | Yes, from scratch |
| **Beak** | [dtsj89/puki-beak](https://huggingface.co/dtsj89/puki-beak) | Reads plain Hebrew into phonemes for the voice | Yes, from scratch |
| **Syrinx** | [dtsj89/puki-syrinx](https://huggingface.co/dtsj89/puki-syrinx) | Puki's voice: BlueTTS with the Noa voice | No: BlueTTS (MIT, Max Melichov), quantized here |
| **Ears** | [dtsj89/puki-ears](https://huggingface.co/dtsj89/puki-ears) | Hears the child, in Hebrew and English | No: kol-stream (Apache 2.0, MaorB), exported here |

`data/` and `out/` are git-ignored. Most scripts say at the top which Python environment they
need and how to run them.

## Brains: Puki's lines

A 46M-parameter language model, trained from scratch for Puki.

**1. Pretraining** on 1.6B tokens, about 74% Hebrew:

- `pt_clean.py`: the Hebrew side. HeDC4 (from HeRo, Shalumov & Haskey,
  [arXiv:2304.11077](https://arxiv.org/abs/2304.11077)), a cleaned Common Crawl corpus, cut down
  to text fit for a model that talks to a toddler.
- `pt_corpus.py`: the corpus and the tokenizer (a byte-level BPE trained on the Hebrew-heavy
  mix plus Puki's own lines). The English side is FineWeb-Edu (ODC-By), Cosmopedia (Apache 2.0)
  and the Common Pile's Gutenberg and YouTube sets; raw web text is left out.
- `pt_run.py`: runs the pretrainer on that corpus. The pretrainer itself is an external
  harness that is **not public yet**, so this step can't be reproduced from this repo alone
  for now. `pt_run.py` finds it through `PUKI_HARNESS`.
- `pt_manifest.py` writes [`pt_manifest.json`](pt_manifest.json): exactly which documents went
  in, so anyone with the same public data can check it. The text itself isn't published here.

**2. Puki's lines**, written by a teacher and filtered by Puki's own checks:

- `teacher.py`: the teacher, DeepSeek models through their API. Only synthetic inputs go to it:
  moments from the app's code and made-up toddler phrases, with "@name" in place of any name.
  Nothing a child said is ever sent. Answers are cached in `data/cache.jsonl`, so a rerun only
  pays for what's new.
- `build.py`: the dataset. The teacher writes several lines for each input, and Puki's filters
  (`brain/persona.py`, `brain/safety.py`) and the teacher's judge keep the best one or two. Some
  inputs are held out to test on. Inputs come from `moments.py` (game moments read from the
  app's code) and `games.py` (Silly Puki, the caring game, the story with choices, the bedtime
  recap). A new feature is a new task: add it, make its data, retrain.
- `gate.py`: the safety gate as a task of its own (what a toddler said, to one digit).
- `fmt.py`: the short prompt format Brains is trained on and run with (language, girl or boy,
  task, input).
- `final.py`: puts it together into `data/final.jsonl`. About 46k lines in the shipped model.

```bash
python moments.py          # how many game moments the app has
python build.py --pilot 10 # a few inputs per task, to read data/pilot_review.txt first
python build.py            # the whole dataset
python final.py            # data/final.jsonl
```

**3. Fine-tuning:**

- `sft_small.py`: fine-tunes the pretrained model on Puki's lines. Every epoch it scores the
  held-out inputs: how many lines pass Puki's filter, the gate's accuracy, and the truth checks.
  These are the evals to re-run after any model change.
- `dpo_pairs.py`, `dpo_onpolicy.py`, `dpo_small.py`: preference tuning, from lines the builder
  kept against ones it turned down, and from the fine-tuned model's own mistakes.

**4. Export:** `brains_export.py` writes the ONNX model with a KV cache, float and int8, each
checked against PyTorch on held-out prompts.

## Beak: reading Hebrew aloud

2.3M parameters. It reads a line letter by letter and gives each Hebrew letter a consonant, a
vowel and stress, for a girl or a boy, so Brains and the voice never need nikud.

- `read_label.py`: the pretraining set, 600k sentences of the clean Hebrew labelled by
  RenikudPlus (Apache 2.0).
- `read_tags.py`: the labels' shape, and the sums between them and IPA.
- `read_train.py`: pretraining on RenikudPlus's labels, then `--finetune` on the reviewed lines.
- `read_more.py`: more (unreviewed) lines to fine-tune on.
- `read_export.py`: the ONNX model, float and int8, checked against PyTorch.

The reviewed lines come from a pronunciation pass over Puki's Hebrew: `say.py` labels the lines
(RenikudPlus plus Puki's own table, `brain/pronunciation.tsv`), `say_review.py` has the teacher
flag words said wrong, `phonemes.py`, `listen_flags.py` and `listen_fixes.py` make lists and
pages for a person to read and hear, and `say_apply.py` puts the fixes in (also for the
recorded lines; `voice_audit.py` checks those).

## Syrinx: the voice

`squawk_quant.py` rewrites BlueTTS's vector estimator and vocoder so their 1x1 convolutions
become MatMuls, then quantizes them to int8: smaller and faster on weak tablets, with no
audible difference. Not trained by us.

## Ears: hearing the child

`kolstream_export.py` exports kol-stream
([MaorB/embeddinggemma-2-asr-hebrew-english-streaming](https://huggingface.co/MaorB/embeddinggemma-2-asr-hebrew-english-streaming),
Apache 2.0, on EmbeddingGemma 2, trained with ivrit.ai's data, CC BY 4.0, and LibriSpeech) to
ONNX int8 for `KolEars.kt`. Not trained by us.

## Shipping a model

1. Upload the exported files to the model's Hugging Face repo.
2. Put each file's size and SHA-256 in `DeviceModels.kt`.
3. Update `pt_manifest.json` if the pretraining changed.
4. Re-run the truth and safety evals (`sft_small.py`'s held-out scoring), and try it on a
   real tablet (the benches in `app/src/androidTest`).

## The teacher's key

The DeepSeek key comes from the `DEEPSEEK_API_KEY` environment variable, or from a local key
file outside the repo that is never committed. It is never printed.

## Legacy: the Dicta-LM / LoRA / GGUF path

Before Brains, Puki's small model was Dicta-LM 3.0 1.7B Base with a LoRA, run as GGUF through
llama.cpp. That path didn't ship. Its scripts are kept for reference only:

- `lora.py`: trained the LoRA.
- `export.py`: merged it, converted to GGUF, built an imatrix and quantized.
- `evaluate.py`: scored each LoRA checkpoint on the held-out inputs.
- `phone_try.py`: ran the GGUF on a phone over adb with llama-cli.
