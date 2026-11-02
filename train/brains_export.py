"""Puki's small model (Brains) as ONNX for the phone, with a KV cache: one graph for the prompt and for
each next token. Float and int8 (MatMul weights only, which Android's ONNX Runtime runs), each checked
against PyTorch: the same tokens, greedy, for held-out prompts.

    <kolstream venv python> brains_export.py C:/data/pretrain/puki-sft/sft-46k/best.pt --out C:/models/puki-brains

Inputs: ids (int64, 1 x n), past_k and past_v (float, layers x kv heads x T x head dim; T = 0 for the
prompt). Outputs: logits for the last token (1 x vocab), and the cache with the n new positions. The
forward is spelled out (as read_export.py does), so nothing about the lengths is fixed in the graph.
"""
import argparse
import json
import os
import shutil
import sys

import numpy as np
import torch
import torch.nn.functional as F

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


class Step(torch.nn.Module):
    def __init__(self, sd, cfg):
        super().__init__()
        self.L, self.H, self.KV = cfg["num_hidden_layers"], cfg["num_attention_heads"], cfg["num_key_value_heads"]
        self.D = cfg.get("head_dim") or cfg["hidden_size"] // self.H
        self.eps = cfg["rms_norm_eps"]
        theta = (cfg.get("rope_parameters") or {}).get("rope_theta", cfg.get("rope_theta", 10000.0))
        self.register_buffer("inv_freq", 1.0 / theta ** (torch.arange(0, self.D, 2).float() / self.D))
        self.w = torch.nn.ParameterDict({k.replace(".", "_"): torch.nn.Parameter(v.float(), requires_grad=False) for k, v in sd.items()})

    def p(self, name):
        return self.w[name.replace(".", "_")]

    def norm(self, x, weight):
        return x * torch.rsqrt(x.pow(2).mean(-1, keepdim=True) + self.eps) * weight

    @staticmethod
    def rotate(x):
        a, b = x.chunk(2, dim=-1)
        return torch.cat((-b, a), dim=-1)

    def forward(self, ids, past_k, past_v):
        n, T = ids.shape[1], past_k.shape[2]
        pos = torch.arange(n, device=ids.device) + T
        freqs = pos[:, None].float() * self.inv_freq[None, :]
        emb = torch.cat((freqs, freqs), dim=-1)
        cos, sin = emb.cos()[None], emb.sin()[None]  # 1 x n x D
        keys_all = torch.arange(n + T, device=ids.device)
        masked = keys_all[None, :] > pos[:, None]  # a token sees itself and what came before
        x = F.embedding(ids[0], self.p("model.embed_tokens.weight"))  # n x hidden
        new_k, new_v = [], []
        for i in range(self.L):
            pre = f"model.layers.{i}."
            h = self.norm(x, self.p(pre + "input_layernorm.weight"))
            q = (h @ self.p(pre + "self_attn.q_proj.weight").T).reshape(n, self.H, self.D).transpose(0, 1)
            k = (h @ self.p(pre + "self_attn.k_proj.weight").T).reshape(n, self.KV, self.D).transpose(0, 1)
            v = (h @ self.p(pre + "self_attn.v_proj.weight").T).reshape(n, self.KV, self.D).transpose(0, 1)
            q = q * cos + self.rotate(q) * sin
            k = k * cos + self.rotate(k) * sin
            k = torch.cat((past_k[i], k), dim=1)
            v = torch.cat((past_v[i], v), dim=1)
            new_k.append(k)
            new_v.append(v)
            rep = self.H // self.KV
            kr, vr = k.repeat_interleave(rep, dim=0), v.repeat_interleave(rep, dim=0)
            att = (q @ kr.transpose(1, 2)) / (self.D ** 0.5)
            att = att.masked_fill(masked[None], float("-inf")).softmax(-1)
            out = (att @ vr).transpose(0, 1).reshape(n, self.H * self.D)
            x = x + out @ self.p(pre + "self_attn.o_proj.weight").T
            h = self.norm(x, self.p(pre + "post_attention_layernorm.weight"))
            gate = h @ self.p(pre + "mlp.gate_proj.weight").T
            up = h @ self.p(pre + "mlp.up_proj.weight").T
            x = x + (F.silu(gate) * up) @ self.p(pre + "mlp.down_proj.weight").T
        last = self.norm(x[-1:], self.p("model.norm.weight"))
        logits = last @ self.p("model.embed_tokens.weight").T  # (tied: the output layer is the embedding)
        return logits, torch.stack(new_k), torch.stack(new_v)


def greedy_onnx(session, ids, eot, L, KV, D, steps=40):
    past = np.zeros((L, KV, 0, D), dtype=np.float32)
    k, v, out, cur = past, past, [], np.array([ids], dtype=np.int64)
    for _ in range(steps):
        logits, k, v = session.run(None, {"ids": cur, "past_k": k, "past_v": v})
        t = int(logits[0].argmax())
        if t == eot:
            break
        out.append(t)
        cur = np.array([[t]], dtype=np.int64)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("checkpoint")
    ap.add_argument("--out", default=r"C:\models\puki-brains")
    ap.add_argument("--tok", default=r"C:\data\pretrain\puki-tok")
    ap.add_argument("--check", type=int, default=30, help="held-out prompts to compare, greedy")
    a = ap.parse_args()
    import onnxruntime as ort
    from onnxruntime.quantization import QuantType, quantize_dynamic
    from tokenizers import Tokenizer
    from transformers import LlamaConfig, LlamaForCausalLM
    import fmt
    os.makedirs(a.out, exist_ok=True)
    ck = torch.load(a.checkpoint, map_location="cpu")
    cfg = ck["config"]
    sd = {k.removeprefix("_orig_mod."): v for k, v in ck["model"].items() if k != "lm_head.weight"}
    step = Step(sd, cfg).eval()
    L, KV, D = step.L, step.KV, step.D
    plain, small = os.path.join(a.out, "brains.onnx"), os.path.join(a.out, "brains_int8.onnx")
    example = (torch.tensor([[5, 6, 7]]), torch.zeros(L, KV, 2, D), torch.zeros(L, KV, 2, D))
    torch.onnx.export(step, example, plain, input_names=["ids", "past_k", "past_v"], output_names=["logits", "next_k", "next_v"],
                      dynamic_axes={"ids": {1: "n"}, "past_k": {2: "T"}, "past_v": {2: "T"}, "next_k": {2: "T2"}, "next_v": {2: "T2"}},
                      opset_version=17, dynamo=False)
    quantize_dynamic(plain, small, op_types_to_quantize=["MatMul"], weight_type=QuantType.QInt8, per_channel=True)  # (as good as float on the truth checks)
    shutil.copy(os.path.join(a.tok, "tokenizer.json"), os.path.join(a.out, "tokenizer.json"))

    # Check: greedy tokens from PyTorch's own model against both exports
    tok = Tokenizer.from_file(os.path.join(a.tok, "tokenizer.json"))
    eot = tok.token_to_id("<|endoftext|>")
    hf = LlamaForCausalLM(LlamaConfig(**cfg)).eval()
    hf.load_state_dict({k.removeprefix("_orig_mod."): v for k, v in ck["model"].items()})
    rows = [json.loads(l) for l in open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "data", "final_heldout.jsonl"), encoding="utf-8")]
    rows = [r for r in rows if r["task"] != "gate"][:a.check]
    for path in (plain, small):
        s = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
        same = 0
        for r in rows:
            ids = tok.encode(fmt.prompt(r["task"], r["lang"], r["girl"], r["input"], r.get("earlier"))).ids
            with torch.no_grad():
                ref = hf.generate(torch.tensor([ids]), max_new_tokens=40, do_sample=False, eos_token_id=eot, pad_token_id=eot)[0, len(ids):].tolist()
            ref = ref[:ref.index(eot)] if eot in ref else ref
            got = greedy_onnx(s, ids, eot, L, KV, D)
            same += got == ref
            if path == small and r is rows[0]:
                print("  e.g.", tok.decode(got))
        print(f"{os.path.basename(path)}: {os.path.getsize(path) / 1e6:.1f} MB, {same}/{len(rows)} held-out prompts give PyTorch's tokens exactly")
    json.dump({"layers": L, "kv_heads": KV, "head_dim": D, "eot": eot, "vocab": cfg["vocab_size"], "context": cfg["max_position_embeddings"]},
              open(os.path.join(a.out, "brains.json"), "w"), indent=1)


if __name__ == "__main__":
    main()
