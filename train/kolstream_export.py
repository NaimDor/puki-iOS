"""Exports kol-stream (MaorB/embeddinggemma-2-asr-hebrew-english-streaming, Apache 2.0) to ONNX for
the app's ears (KolEars.kt):
  encoder.onnx  frames (1, M, 320) float32 audio frames (10 ms hop, see frames()) -> e (1, T2, 512)
                log-mel, EmbeddingGemma 2's audio tower, PCA, reader, joiner encoder side
  step.onnx     e (N, 512), ctx (1, 2) int64 last two pieces -> logits (N, 2048)
  tokens.txt    the pieces, one per line
Both int8 (the matrix multiplies) and already optimized, so the phone loads them as they are (on a
Galaxy Tab A7 Lite that cut the encoder's load from 23 s to 17 s). They go in --out/release, with
their sizes and SHA-256 for DeviceModels.kt.

Needs its own environment (torch, transformers>=5.19, onnx, onnxruntime, soundfile, sentencepiece,
soxr) and local copies of both models:
    python kolstream_export.py --model C:/models/kolstream --base C:/models/embeddinggemma-2 --wav x.wav
"""
import argparse
import hashlib
import os
import sys

import numpy as np
import torch
import torch.nn as nn

ap = argparse.ArgumentParser()
ap.add_argument("--model", default=r"C:\models\kolstream", help="kol-stream's folder (its code and weights)")
ap.add_argument("--base", default=r"C:\models\embeddinggemma-2", help="google/embeddinggemma-2's folder")
ap.add_argument("--wav", default=r"C:\models\whisper-ivrit\sentence.wav", help="any short 16 kHz recording, to trace with")
ap.add_argument("--out", default=r"C:\models\kolstream\onnx")
ARGS = ap.parse_args()
sys.path.insert(0, ARGS.model)
import kolstream.model as km  # noqa: E402
from kolstream.nn import reader_hidden  # noqa: E402

OUT = ARGS.out
_orig = km.load_tower
km.load_tower = lambda _b, s, d: _orig(ARGS.base, s, d)  # the local copy, not a download


def _block_context(self, x):
    """Gemma4AudioAttention._extract_block_context without unfold (which the exporter can't do on a
    dynamic length): the same windows, gathered by index."""
    seq = x.shape[1]
    x = torch.nn.functional.pad(x, (0, 0, 0, 0, self.max_past_horizon, self.max_future_horizon + self.chunk_size - 1))
    n = (seq + self.chunk_size - 1) // self.chunk_size
    idx = (torch.arange(n) * self.chunk_size)[:, None] + torch.arange(self.context_size)[None, :]
    return x[:, idx]


def frames(wav):
    """16 kHz float audio -> (M, 320) frames, exactly as Gemma4AudioFeatureExtractor cuts them (160 zeros in front,
    hop 160), keeping only frames that lie wholly inside the audio."""
    w = np.concatenate([np.zeros(160, np.float32), wav.astype(np.float32)])
    m = (len(w) - 321) // 160 + 1
    return np.stack([w[i * 160: i * 160 + 320] for i in range(max(m, 0))])


class Enc(nn.Module):
    def __init__(self, asr):
        super().__init__()
        fe = asr.fe
        n = np.arange(320)
        k = np.arange(257)
        ang = 2 * np.pi * np.outer(n, k) / 512
        win = fe.window.astype(np.float64)[:, None]
        self.register_buffer("cos", torch.tensor(win * np.cos(ang), dtype=torch.float32))
        self.register_buffer("sin", torch.tensor(win * -np.sin(ang), dtype=torch.float32))
        self.register_buffer("mel", torch.tensor(fe.mel_filters, dtype=torch.float32))
        self.tower, self.rnnt = asr.tower, asr.rnnt
        self.register_buffer("pmean", asr.proj[0].float())
        self.register_buffer("pcomps", asr.proj[1].float())

    def forward(self, fr):
        re, im = fr @ self.cos, fr @ self.sin
        logmel = torch.log(torch.sqrt(re * re + im * im) @ self.mel + 1e-3)
        mask = torch.ones(fr.shape[:2], dtype=torch.bool)
        # The tower's forward, with its sliding-window mask built here (masking_utils uses vmap, which
        # the exporter can't trace); the same mask when all the audio is real, as here
        t, c = self.tower, self.tower.config
        h, _ = t.subsample_conv_projection(logmel, mask)
        pos = t.rel_pos_enc(h)
        i = torch.arange(h.shape[1])
        d = i[:, None] - i[None, :]
        ok = ((d >= 0) & (d < c.attention_context_left - 1)) | ((d < 0) & (-d < c.attention_context_right))
        am = t._convert_4d_mask_to_blocked_5d(torch.where(ok, 0.0, torch.finfo(torch.float32).min)[None, None])
        for layer in t.layers[: c.num_hidden_layers]:
            h = layer(h, attention_mask=am, position_embeddings=pos)
        h = t.output_proj(h)
        x = (h - self.pmean) @ self.pcomps
        lens = torch.full((1,), x.shape[1], dtype=torch.long)
        h = reader_hidden(self.rnnt.reader, x, lens)
        return self.rnnt.enc(h, lens)[0]


class Step(nn.Module):
    def __init__(self, asr):
        super().__init__()
        self.m = asr.rnnt

    def forward(self, e, ctx):
        p = self.m.pred_proj(self.m.pred.step(ctx))
        return self.m.out(torch.tanh(e + p))


def main():
    os.makedirs(OUT, exist_ok=True)
    asr = km.KolStream(ARGS.model, device="cpu")
    enc, step = Enc(asr).eval(), Step(asr).eval()
    import soundfile as sf
    wav, _ = sf.read(ARGS.wav, dtype="float32")
    fr = torch.tensor(frames(wav))[None]
    from transformers.models.gemma4.modeling_gemma4 import Gemma4AudioAttention
    with torch.no_grad():
        ref = enc(fr)
        Gemma4AudioAttention._extract_block_context = _block_context
        e = enc(fr)
        assert torch.allclose(ref, e, atol=1e-5), "the gathered block context differs from unfold"
        torch.onnx.export(enc, (fr,), os.path.join(OUT, "kol_enc.onnx"), input_names=["frames"], output_names=["e"],
                          dynamic_axes={"frames": {1: "M"}, "e": {1: "T"}}, opset_version=17, dynamo=False)
        torch.onnx.export(step, (e[0, :16], torch.zeros(1, 2, dtype=torch.long)), os.path.join(OUT, "kol_step.onnx"),
                          input_names=["e", "ctx"], output_names=["logits"], dynamic_axes={"e": {0: "N"}, "logits": {0: "N"}},
                          opset_version=17, dynamo=False)
    with open(os.path.join(OUT, "tokens.txt"), "w", encoding="utf-8") as f:
        for i in range(asr.sp.get_piece_size()):
            f.write(asr.sp.id_to_piece(i) + "\n")
    from onnxruntime.quantization import QuantType, quantize_dynamic
    for name in ("kol_enc", "kol_step"):
        quantize_dynamic(os.path.join(OUT, name + ".onnx"), os.path.join(OUT, name + ".int8.onnx"), weight_type=QuantType.QInt8,
                        op_types_to_quantize=["MatMul", "Gemm"])  # no ConvInteger in ONNX Runtime on Android
    import onnxruntime as ort
    rel = os.path.join(OUT, "release")
    os.makedirs(rel, exist_ok=True)
    for name, final in (("kol_enc", "encoder.onnx"), ("kol_step", "step.onnx")):
        o = ort.SessionOptions()
        # "extended": fusions that suit any CPU (not "all", whose layouts are this PC's own)
        o.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_EXTENDED
        o.optimized_model_filepath = os.path.join(rel, final)
        ort.InferenceSession(os.path.join(OUT, name + ".int8.onnx"), o)
    import shutil
    shutil.copy(os.path.join(OUT, "tokens.txt"), rel)
    for f in ("encoder.onnx", "step.onnx", "tokens.txt"):
        path = os.path.join(rel, f)
        print(f, os.path.getsize(path), hashlib.sha256(open(path, "rb").read()).hexdigest())


if __name__ == "__main__":
    main()
