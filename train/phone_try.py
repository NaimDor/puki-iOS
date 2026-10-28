"""Runs the exported model on an Android phone over adb (llama-cli in /data/local/tmp/puki): one
line for each of a few held-out inputs, with the time it took. Pushes the model first if asked.

    python phone_try.py --gguf out/dicta-1.7b-final/puki-Q4_K_M.gguf --push --threads 2
"""
import argparse
import json
import os
import re
import subprocess
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
import fmt  # noqa: E402

SDK = re.search(r"sdk\.dir=(.*)", open(os.path.join(os.path.dirname(HERE), "local.properties")).read()).group(1)
ADB = [SDK.replace("\\:", ":").replace("\\\\", "\\") + r"\platform-tools\adb.exe", "-s", "RFCY11GGHAP"]
DIR = "/data/local/tmp/puki"


def adb(*args, **kw):
    return subprocess.run(ADB + list(args), capture_output=True, text=True, encoding="utf-8", errors="replace", **kw)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gguf", required=True)
    ap.add_argument("--push", action="store_true")
    ap.add_argument("--threads", type=int, default=2)
    ap.add_argument("--n", type=int, default=12)
    args = ap.parse_args()
    if args.push:
        r = adb("push", args.gguf, f"{DIR}/puki.gguf")
        print((r.stdout + r.stderr).strip().splitlines()[-1])
    held = [json.loads(l) for l in open(os.path.join(HERE, "data", "train_heldout.jsonl"), encoding="utf-8")]
    seen, picks = set(), []
    for task in ("moment", "talk", "careful", "reach_back", "tap"):
        for lang in ("he", "en"):
            for r in held:
                key = (r["task"], r["lang"], r["input"])
                if r["task"] == task and r["lang"] == lang and key not in seen:
                    seen.add(key)
                    picks.append(r)
                    if sum(1 for p in picks if p["task"] == task and p["lang"] == lang) >= 2:
                        break
    for r in picks[:args.n]:
        prompt = fmt.prompt(r["task"], r["lang"], r["girl"], r["input"], r.get("earlier"))
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False, suffix=".txt") as f:
            f.write(prompt)
        adb("push", f.name, f"{DIR}/prompt.txt")
        os.unlink(f.name)
        out = adb("shell", f"cd {DIR} && ./llama-completion -m puki.gguf -f prompt.txt -n 40 -t {args.threads} --temp 0.7 "
                           "--top-p 0.95 -no-cnv -c 256 2>&1").stdout
        text = out.split("puki:")[-1] if "puki:" in out else out
        line = re.split(r"\n|llama_perf|\[end of text\]", text.strip())[0].strip()
        load = re.search(r"load time =\s*([\d.]+) ms", out)
        pe = re.search(r"prompt eval time =\s*([\d.]+) ms /\s*(\d+) tokens", out)
        ev = re.search(r"\beval time =\s*([\d.]+) ms /\s*(\d+) runs", out)
        timing = (f"prompt {pe.group(2)} tok {float(pe.group(1)):.0f} ms, write {ev.group(2)} tok {float(ev.group(1)):.0f} ms"
                  if pe and ev else "(no timings)")
        print(f"[{r['task']} {r['lang']} {'girl' if r['girl'] else 'boy'}] {r['input'][:60]}"
              + (f"  (earlier: {r['earlier'][:40]})" if r.get("earlier") else "") + f"\n   -> {line}   [{timing}]")


if __name__ == "__main__":
    main()
