"""Exports the English reading model (en_read_train.py) for the tablet: ONNX with int8 weights, the symbols, and
the word table (espeak's own reading of the words Puki says and the commonest English words, so the model only
reads the rest). Checks the int8 model against espeak on the words it never trained on.

    python en_read_export.py --run C:/data/pretrain/en-read-runs/en-read-2 --out C:/data/pretrain/en-v2/beak-en
"""
import argparse
import json
import os
import random
import sys
from pathlib import Path

import numpy as np
import torch

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from en_read_train import LETTERS, MAX_LETTERS, Reader, decode, letters  # noqa: E402

class Single(torch.nn.Module):
    """One word, no padding, the encoder written out by hand: nn.TransformerEncoder's export bakes the example's
    length into its reshapes, so any other word length fails at run time."""
    def __init__(self, m):
        super().__init__()
        self.m = m

    def forward(self, ids):
        import torch.nn.functional as F
        m = self.m
        n = ids.shape[1]
        h = m.embed(ids) + m.pos(torch.arange(n, device=ids.device))
        for layer in m.encoder.layers:  # (norm_first: x + attn(norm(x)), then x + ff(norm(x)))
            att = layer.self_attn
            heads, dim = att.num_heads, att.embed_dim
            q, k, v = F.linear(layer.norm1(h), att.in_proj_weight, att.in_proj_bias).chunk(3, -1)
            q, k, v = (t.reshape(1, n, heads, dim // heads).transpose(1, 2) for t in (q, k, v))
            w = torch.softmax(q @ k.transpose(-1, -2) / (dim // heads) ** 0.5, -1)
            h = h + att.out_proj((w @ v).transpose(1, 2).reshape(1, n, dim))
            h = h + layer.linear2(F.relu(layer.linear1(layer.norm2(h))))
        h = m.norm(h)
        return torch.log_softmax(m.out(h).reshape(1, n * 3, m.n_out), -1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--data", default=r"C:\data\pretrain\en-v2\en_read.tsv")
    ap.add_argument("--out", required=True)
    ap.add_argument("--table", type=int, default=30000, help="the commonest words go in the table (Puki's always)")
    a = ap.parse_args()
    import onnxruntime as ort
    from onnxruntime.quantization import QuantType, quantize_dynamic
    os.makedirs(a.out, exist_ok=True)
    ck = torch.load(Path(a.run) / "best.pt", map_location="cpu")
    symbols = ck["symbols"]
    model = Reader(len(symbols), ck["dim"], ck["layers"])
    model.load_state_dict(ck["model"])
    model.eval()
    plain, small = os.path.join(a.out, "reader.onnx"), os.path.join(a.out, "reader_int8.onnx")
    example = torch.tensor([letters("parrot")])
    torch.onnx.export(Single(model), (example,), plain, input_names=["ids"], output_names=["logp"],
                      dynamic_axes={"ids": {1: "n"}, "logp": {1: "slots"}}, opset_version=17, dynamo=False)
    quantize_dynamic(plain, small, op_types_to_quantize=["MatMul"], weight_type=QuantType.QInt8)
    os.remove(plain)
    sess = ort.InferenceSession(small, providers=["CPUExecutionProvider"])

    def word_ipa(w):
        ids = np.array([letters(w)], dtype=np.int64)
        return decode(torch.from_numpy(sess.run(None, {"ids": ids})[0][0]), symbols)

    rows = [l.rstrip("\n").split("\t") for l in open(a.data, encoding="utf-8")]
    # The same words en_read_train.py held out (its filter and shuffle, seed 0)
    kept = [(w.lower(), ipa, p == "1") for w, ipa, p in rows
            if all(c in LETTERS for c in w.lower()) and 0 < len(w) <= MAX_LETTERS and len(ipa) <= (len(w) + 2) * 3]
    random.Random(0).shuffle(kept)
    test = [r for r in kept if not r[2]][:3000]
    right = sum(word_ipa(w) == ipa for w, ipa, _ in test)
    print(f"int8: {right / len(test):.1%} of {len(test)} unseen words exactly as espeak")

    table = {}
    for i, (w, ipa, p) in enumerate(rows):
        if p == "1" or i < a.table:
            table[w.lower()] = ipa
    inline = Path(a.data.replace(".tsv", "_inline.tsv"))
    if inline.exists():  # (en_label.py: how espeak reads Puki's words inside his lines, the small words weak)
        table.update(l.rstrip("\n").split("\t") for l in open(inline, encoding="utf-8"))
    with open(os.path.join(a.out, "words.tsv"), "w", encoding="utf-8") as f:
        for w, ipa in table.items():
            f.write(f"{w}\t{ipa}\n")
    Path(a.out, "symbols.json").write_text(json.dumps({"letters": LETTERS, "slots": 3, "max_letters": MAX_LETTERS,
                                                         "symbols": symbols}, ensure_ascii=False), encoding="utf-8")
    sizes = {n: os.path.getsize(os.path.join(a.out, n)) for n in os.listdir(a.out)}
    print(f"wrote {a.out}: {sizes}; {len(table)} words in the table")


if __name__ == "__main__":
    main()
