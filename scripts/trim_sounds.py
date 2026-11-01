#!/usr/bin/env python3
"""Turns the downloaded animal recordings (see docs/sound-credits.md) into the game's short sounds:
the loudest stretch of each, soft edges, the loudness of the voice lines. Both languages use
the same file, voice_sound_<animal>_he.ogg and _en.ogg.

    python scripts/trim_sounds.py <folder of downloads> <output folder>
"""
import os
import sys

import numpy as np
import soundfile as sf

RATE = 44100
TARGET_RMS = 0.11  # about the voice lines' level
FRAME = 0.02

# animal: (a word in the file's name, seconds kept, level (1: the voices' level))
SOUNDS = {
    "cow": ("Cow moos", 2.2, 1.0),
    "cat": ("Meow cat", 1.6, 1.0),
    "horse": ("Horse neighing", 2.0, 1.0),
    "sheep": ("Sheep 1", 1.2, 1.0),
    "goat": ("Bleating goat", 1.6, 1.0),
    "dog": ("barking-dog", 1.1, 1.0),
    "pig": ("oink", 0.8, 1.0),
    "duck": ("single-quack", 0.8, 1.0),
    "monkey": ("affen-schreit", 1.6, 0.8),  # (a shrill cry: a little quieter)
    "rabbit": ("a-rabbit-is-eating", 1.8, 1.0),
    "elephant": ("elephant", 1.4, 1.0),
    "lion": ("lion", 1.5, 0.6),  # (one short roar, gently: she's two)
    "chicken": ("chicken", 1.8, 1.0),
}


def strip_skeleton(path):
    """An Ogg file's Vorbis stream alone: some files carry a Skeleton stream that soundfile can't read."""
    data = open(path, "rb").read()
    pages, i = [], 0
    while data[i:i + 4] == b"OggS":
        segments = data[i + 26]
        size = 27 + segments + sum(data[i + 27:i + 27 + segments])
        pages.append(data[i:i + size])
        i += size
    vorbis = next(p[14:18] for p in pages if b"\x01vorbis" in p[:64 + p[26]])
    out = path + ".vorbis.ogg"
    with open(out, "wb") as f:
        f.write(b"".join(p for p in pages if p[14:18] == vorbis))
    return out


def load(path):
    try:
        audio, rate = sf.read(path, dtype="float32", always_2d=True)
    except sf.LibsndfileError:
        audio, rate = sf.read(strip_skeleton(path), dtype="float32", always_2d=True)
    audio = audio.mean(axis=1)
    if rate != RATE:
        audio = np.interp(np.arange(0, len(audio) * RATE / rate) * rate / RATE, np.arange(len(audio)), audio)
    return audio - audio.mean()


def loudest(audio, seconds):
    """The stretch of [seconds] with the most sound in it, moved back to where that sound begins."""
    n = min(len(audio), int(seconds * RATE))
    power = np.concatenate([[0.0], np.cumsum(audio.astype(np.float64) ** 2)])
    start = int(np.argmax(power[n:] - power[:-n]))
    hop = int(FRAME * RATE)
    frames = np.sqrt((power[hop::hop] - power[:-hop:hop]) / hop)
    quiet = frames.max() * 0.12
    f = start // hop
    while f > 0 and frames[f] > quiet and start - f * hop < RATE // 3:
        f -= 1
    start = max(0, min(start, f * hop))
    return audio[start:start + n]


def finish(clip, level):
    hop = int(FRAME * RATE)
    power = np.concatenate([[0.0], np.cumsum(clip.astype(np.float64) ** 2)])
    frames = np.sqrt((power[hop::hop] - power[:-hop:hop]) / hop)
    loud = frames[frames > frames.max() * 0.3]
    clip = clip * (TARGET_RMS * level / max(np.sqrt(np.mean(loud ** 2)), 1e-5))
    peak = np.abs(clip).max()
    if peak > 0.97:
        clip = clip * 0.97 / peak
    fade_in, fade_out = int(0.012 * RATE), int(0.14 * RATE)
    clip[:fade_in] *= np.linspace(0, 1, fade_in)
    clip[-fade_out:] *= np.linspace(1, 0, fade_out) ** 2
    return np.concatenate([clip, np.zeros(int(0.05 * RATE), dtype=np.float32)])


def main():
    source, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    names = [n for n in os.listdir(source) if not n.endswith(".vorbis.ogg")]
    for animal, (word, seconds, level) in SOUNDS.items():
        match = [n for n in names if word.lower() in n.lower()]
        if not match:
            print("missing:", animal)
            continue
        audio = load(os.path.join(source, match[0]))
        clip = finish(loudest(audio, seconds), level)
        for lang in ("he", "en"):
            sf.write(os.path.join(out, f"voice_sound_{animal}_{lang}.ogg"), clip, RATE, format="OGG", subtype="VORBIS")
        print(f"{animal}: {len(audio) / RATE:.1f} s -> {len(clip) / RATE:.1f} s  peak {np.abs(clip).max():.2f}")


if __name__ == "__main__":
    main()
