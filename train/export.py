"""Turns a trained LoRA into the model file the brain runs: merged into the base model, converted
to GGUF, an importance matrix (imatrix) built from Puki's own dataset, then quantized (Q4_K_M by
default) with it, so the 4-bit model keeps what matters for Puki's lines.

    <torch venv python> export.py --model C:/models/dicta/DictaLM-3.0-1.7B-Base \
        --adapter out/dicta-1.7b-final/epoch-3 --llama C:/Users/user/Documents/llama.cpp

Writes out/<adapter's run>/puki-<quant>.gguf (and the merged model and F16 GGUF beside it).
"""
import argparse
import json
import os
import subprocess
import sys

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer

HERE = os.path.dirname(os.path.abspath(__file__))


def tool(llama, name):
    for d in ("build/bin", "build/bin/Release"):
        p = os.path.join(llama, d, name + (".exe" if os.name == "nt" else ""))
        if os.path.exists(p):
            return p
    raise SystemExit(f"{name} not found under {llama}/build/bin")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--adapter", required=True)
    ap.add_argument("--llama", required=True, help="a llama.cpp checkout with build/bin (convert script, imatrix, quantize)")
    ap.add_argument("--quant", default="Q4_K_M")
    args = ap.parse_args()
    run_dir = os.path.dirname(os.path.abspath(args.adapter))
    merged = os.path.join(run_dir, "merged")

    # 1. Merge the LoRA into the base weights (bf16), with the tokenizer
    base = AutoModelForCausalLM.from_pretrained(args.model, dtype=torch.bfloat16)
    model = PeftModel.from_pretrained(base, args.adapter).merge_and_unload()
    model.save_pretrained(merged)
    AutoTokenizer.from_pretrained(args.model).save_pretrained(merged)
    print("merged ->", merged)

    # 2. GGUF (F16)
    f16 = os.path.join(run_dir, "puki-F16.gguf")
    env = dict(os.environ, PYTHONPATH=os.path.join(args.llama, "gguf-py"))
    subprocess.run([sys.executable, os.path.join(args.llama, "convert_hf_to_gguf.py"), merged,
                    "--outtype", "f16", "--outfile", f16], check=True, env=env)

    # 3. imatrix from Puki's own lines (the prompts and lines the model will really see)
    calib = os.path.join(run_dir, "calibration.txt")
    with open(os.path.join(HERE, "data", "train.jsonl"), encoding="utf-8") as f, open(calib, "w", encoding="utf-8") as out:
        for line in f:
            out.write(json.loads(line)["text"].replace("<|endoftext|>", "") + "\n\n")
    imatrix = os.path.join(run_dir, "imatrix.gguf")
    subprocess.run([tool(args.llama, "llama-imatrix"), "-m", f16, "-f", calib, "-o", imatrix,
                    "-c", "256", "-ngl", "99"], check=True)

    # 4. Quantize with it
    out = os.path.join(run_dir, f"puki-{args.quant}.gguf")
    subprocess.run([tool(args.llama, "llama-quantize"), "--imatrix", imatrix, f16, out, args.quant], check=True)
    print("done ->", out, f"({os.path.getsize(out) / 1e9:.2f} GB)")


if __name__ == "__main__":
    main()
