"""Runs the Skyrim harness's pretrainer on Puki's corpus, unchanged but for the vocabulary size, which
it fixes at 16k. Everything after the first few arguments goes to it as they are.

    <harness train venv python> pt_run.py --vocab 12288 --tok C:/data/pretrain/puki-tok --run puki-48m --until 1.6e9

Checkpoints go to %SKYSLOP_RUNS%, set to C:/data/pretrain/puki-runs here.
"""
import os
import sys
from pathlib import Path

HARNESS = Path(os.environ.get("PUKI_HARNESS", r"C:\Users\user\Documents\Skyrim Follower Mod\harness"))
os.environ.setdefault("SKYSLOP_RUNS", r"C:\data\pretrain\puki-runs")
sys.path.insert(0, str(HARNESS))

import pretrain.train as t  # noqa: E402

at = sys.argv.index("--vocab")
wanted = int(sys.argv[at + 1])
del sys.argv[at:at + 2]
config = t.config
t.config = lambda vocab=wanted, size="50m", seq=t.SEQ: config(vocab=wanted, size=size, seq=seq)
t.main()
