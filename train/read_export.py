"""The reading model as ONNX for the phone: float, and int8 (MatMul weights only, which Android's ONNX
Runtime runs), each checked against PyTorch on the held-out reviewed lines.

    <kolstream venv python> read_export.py C:/data/pretrain/puki-read-runs/ft-2m/best.pt --out C:/models/puki-read

Input: ids (int64, 1 x n), the gender token then one id per character (read_train.encode). Outputs:
consonant, vowel and stress logits for each character, decided the way read_train.read decides them.
"""
import argparse
import os
import sys

import numpy as np
import torch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import read_train as R  # noqa: E402
from read_tags import NAME, NOTHING, Teacher, clean  # noqa: E402


class Single(torch.nn.Module):
    """One line, no padding."""

    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, ids):
        # Reader.forward spelled out: torch's own attention traces its sizes into the graph, so a line of
        # another length fails; this is the same arithmetic on the same weights, with no fixed sizes.
        m = self.model
        x = m.embed(ids) + m.pos(torch.arange(ids.shape[1], device=ids.device))
        for layer in m.body.layers:
            x = x + self.attend(layer.self_attn, layer.norm1(x))
            x = x + layer.linear2(torch.nn.functional.gelu(layer.linear1(layer.norm2(x))))
        x = m.norm(x)[:, 1:]
        return m.cons(x), m.vowel(x), m.stress(x).squeeze(-1)

    @staticmethod
    def attend(att, x):
        heads = att.num_heads
        q, k, v = torch.nn.functional.linear(x, att.in_proj_weight, att.in_proj_bias).chunk(3, dim=-1)
        split = lambda t: t.reshape(t.shape[0], t.shape[1], heads, -1).transpose(1, 2)
        q, k, v = split(q), split(k), split(v)
        w = torch.softmax(q @ k.transpose(-1, -2) / (q.shape[-1] ** 0.5), dim=-1)
        out = (w @ v).transpose(1, 2).reshape(x.shape[0], x.shape[1], -1)
        return att.out_proj(out)


def decide(text, cons, vowel, stress, mask_rows):
    tags = []
    for i, ch in enumerate(text):
        tags.append([int(np.where(mask_rows[i], cons[i], -1e4).argmax()), int(vowel[i].argmax()), False])
    for w in __import__("re").finditer(r"\S+", text):
        voiced = [i for i in range(w.start(), w.end()) if tags[i][1] != NOTHING]
        if voiced:
            tags[max(voiced, key=lambda i: stress[i])][2] = True
    return tags


def score(session, rows, v, mask):
    words = right = whole = 0
    for r in rows:
        text = " ".join(clean(r["line"]).split())
        ids = np.array([R.encode(text, bool(r["girl"]))], dtype=np.int64)
        cons, vowel, stress = (x[0] for x in session.run(None, {"ids": ids}))
        tags = decide(text, cons, vowel, stress, mask[ids[0, 1:]].numpy())
        said, want = Teacher.say(v, text, tags).split(), r["say"].split()
        if len(said) != len(want):
            continue
        pairs = [(a, b) for w, a, b in zip(text.split(), said, want) if NAME not in w]
        words += len(pairs)
        right += sum(a == b for a, b in pairs)
        whole += all(a == b for a, b in pairs)
    return right / words, whole / len(rows)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("checkpoint")
    ap.add_argument("--out", default=r"C:\models\puki-read")
    a = ap.parse_args()
    import onnxruntime as ort
    from onnxruntime.quantization import QuantType, quantize_dynamic
    torch.backends.mha.set_fastpath_enabled(False)
    os.makedirs(a.out, exist_ok=True)
    ck = torch.load(a.checkpoint, map_location="cpu")
    v = R.vocab()
    mask = R.allowed_mask(v)
    model = R.Reader(len(v.consonants), len(v.vowels), ck["args"]["dim"], ck["args"]["layers"], ff=ck["args"]["dim"] * 3)
    model.load_state_dict(ck["model"])
    model.eval()
    plain = os.path.join(a.out, "read.onnx")
    small = os.path.join(a.out, "read_int8.onnx")
    example = torch.tensor([R.encode("שלום לך", True)])
    torch.onnx.export(Single(model), (example,), plain, input_names=["ids"], output_names=["consonant", "vowel", "stress"],
                      dynamic_axes={"ids": {1: "n"}, "consonant": {1: "n"}, "vowel": {1: "n"}, "stress": {1: "n"}},
                      opset_version=17, dynamo=False)
    quantize_dynamic(plain, small, op_types_to_quantize=["MatMul"], weight_type=QuantType.QInt8)
    _, test = R.split_reviewed(R.reviewed())
    for path in (plain, small):
        s = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
        words, whole = score(s, test, v, mask)
        print(f"{os.path.basename(path)}: {os.path.getsize(path) / 1e6:.2f} MB, held-out words {words:.2%}, lines {whole:.1%}")
    print(f"PyTorch at training: {ck['words']:.2%} / {ck['lines']:.1%}")


if __name__ == "__main__":
    main()
