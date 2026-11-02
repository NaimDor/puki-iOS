"""A faster voice for weak tablets: BlueTTS's vector estimator and vocoder in int8. Almost all their weights
sit in 1x1 convolutions, which ONNX Runtime's int8 doesn't reach (Android's lacks ConvInteger), so each
becomes the same sum as a MatMul (transpose, MatMul, bias, transpose back) first; then MatMul weights go
to int8, per channel. Lines are rendered from the original, the rewrite and the int8 build with the same
noise, for the ear and for timing.

    <brain venv python> squawk_quant.py --bundle C:/Users/user/AppData/Local/Puki/BlueTTS --out C:/models/squawk

Writes <out>/fp32 (the rewrite, which must sound exactly like the original), <out>/int8, and WAVs.
"""
import argparse
import os
import shutil
import time

import numpy as np
import onnx
from onnx import helper, numpy_helper

SMALL = ["duration_predictor_style.onnx", "text_encoder.onnx", "uncond.npz", "stats.npz", "vocab.json", "tts.json"]
BIG = ["vector_estimator.onnx", "vocoder.onnx"]
LINES = ["hatsˈel niʁʔˈa kmˈo pˈil ʔanˈak!", "haχazˈiʁ ʁaʔˈev.",
         "haχajˈa hazˈot ʔosˈa hˈop! vehˈop! bˈoʔi navˈi lˈo ʔˈoχel, hˈu mamˈaʃ ʁaʔˈev."]


def attr(node, name, default):
    for a in node.attribute:
        if a.name == name:
            return helper.get_attribute_value(a)
    return default


def rewrite(src, dst):
    """Every 1x1, ungrouped, unpadded Conv1d with its weight in the file, as a MatMul. Returns how many."""
    model = onnx.load(src)
    g = model.graph
    inits = {i.name: i for i in g.initializer}
    nodes, n = [], 0
    for node in g.node:
        w = inits.get(node.input[1]) if node.op_type == "Conv" and len(node.input) > 1 else None
        if w is None or len(w.dims) != 3 or w.dims[2] != 1 or attr(node, "group", 1) != 1 or \
                any(p != 0 for p in attr(node, "pads", [0, 0])) or any(s != 1 for s in attr(node, "strides", [1])) or \
                any(d != 1 for d in attr(node, "dilations", [1])):
            nodes.append(node)
            continue
        n += 1
        tag = f"mm{n}"
        weight = numpy_helper.to_array(w)[:, :, 0].T.copy()  # (out, in, 1) -> (in, out)
        g.initializer.append(numpy_helper.from_array(weight, tag + "_w"))
        x, out = node.input[0], node.output[0]
        nodes.append(helper.make_node("Transpose", [x], [tag + "_t"], perm=[0, 2, 1]))
        nodes.append(helper.make_node("MatMul", [tag + "_t", tag + "_w"], [tag + "_m"]))
        last = tag + "_m"
        if len(node.input) > 2 and node.input[2]:
            nodes.append(helper.make_node("Add", [last, node.input[2]], [tag + "_b"]))
            last = tag + "_b"
        nodes.append(helper.make_node("Transpose", [last], [out], perm=[0, 2, 1]))
    del g.node[:]
    g.node.extend(nodes)
    # Conv weights no longer used go, so the file doesn't carry both
    used = {i for node in g.node for i in node.input}
    keep = [i for i in g.initializer if i.name in used]
    del g.initializer[:]
    g.initializer.extend(keep)
    onnx.save(model, dst)
    return n


def render(bundle, voice, out_prefix, steps=5):
    from blue_onnx import load_text_to_speech, load_voice_style
    import soundfile as sf
    tts = load_text_to_speech(bundle, use_gpu=False) if "use_gpu" in load_text_to_speech.__code__.co_varnames else load_text_to_speech(bundle)
    style = load_voice_style([voice])
    waves, took = [], 0.0
    for i, line in enumerate(LINES):
        np.random.seed(7)
        t0 = time.perf_counter()
        wav, _ = tts(line, "he", style, steps, speed=0.92, text_is_phonemes=True)
        took += time.perf_counter() - t0
        wav = np.asarray(wav).reshape(-1)
        waves.append(wav)
        sf.write(f"{out_prefix}_{i}.wav", wav, tts.sample_rate)
    return waves, took


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--bundle", default=r"C:\Users\user\AppData\Local\Puki\BlueTTS")
    ap.add_argument("--out", default=r"C:\models\squawk")
    a = ap.parse_args()
    from onnxruntime.quantization import QuantType, quantize_dynamic
    src = os.path.join(a.bundle, "onnx_models")
    voice = os.path.join(a.bundle, "voices", "noa.json")
    for kind in ("fp32", "int8"):
        os.makedirs(os.path.join(a.out, kind), exist_ok=True)
        for f in SMALL:
            shutil.copy(os.path.join(src, f), os.path.join(a.out, kind, f))
    for f in BIG:
        n = rewrite(os.path.join(src, f), os.path.join(a.out, "fp32", f))
        quantize_dynamic(os.path.join(a.out, "fp32", f), os.path.join(a.out, "int8", f), op_types_to_quantize=["MatMul"],
                         weight_type=QuantType.QInt8, per_channel=True)
        sizes = [os.path.getsize(os.path.join(d, f)) / 1e6 for d in (src, os.path.join(a.out, "fp32"), os.path.join(a.out, "int8"))]
        print(f"{f}: {n} 1x1 convs as MatMul; {sizes[0]:.0f} MB -> {sizes[1]:.0f} MB rewritten -> {sizes[2]:.0f} MB int8", flush=True)
    original, t_orig = render(src, voice, os.path.join(a.out, "original"))
    same, t_same = render(os.path.join(a.out, "fp32"), voice, os.path.join(a.out, "rewrite"))
    small, t_small = render(os.path.join(a.out, "int8"), voice, os.path.join(a.out, "int8"))
    for name, got, took in (("rewrite", same, t_same), ("int8", small, t_small)):
        diff = max(float(np.abs(o[:len(g)] - g[:len(o)]).max()) for o, g in zip(original, got))
        print(f"{name}: largest sample difference from the original {diff:.5f}; PC time {took:.1f} s against {t_orig:.1f} s")


if __name__ == "__main__":
    main()
