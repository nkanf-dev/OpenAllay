#!/usr/bin/env python3
"""Restore three exact historical source-recipe files for one compiler baseline oracle."""
import hashlib
from pathlib import Path
import subprocess
import sys
base = Path(__file__).resolve().parent
rows = [{'path': 'runtime-rhino/patches/rhino-source-manifest.json', 'source': 'ea9ac1d615e459586d4b0b6f645afba4ab917bf3', 'sha256': '0f0116bf3f526e82fec10c89041e4440763c856f5b6ee7074dda6d1f6d3d7bf4'}, {'path': 'runtime-rhino/patches/rhino-java17-hunks.json', 'source': 'ea9ac1d615e459586d4b0b6f645afba4ab917bf3', 'sha256': '90c4b46daf3437283f685c9e49ab532605f886b0e35e28758b5b0d91fd2aebd8'}, {'path': 'runtime-rhino/patches/rhino-java17.patch', 'source': 'ea9ac1d615e459586d4b0b6f645afba4ab917bf3', 'sha256': 'e2b711afdb041fbb3538922e97f60ce171553971daff39197cac3064911ecf75'}]
project = Path(sys.argv[1]).resolve()
output = Path(sys.argv[2]).resolve()
output.mkdir(parents=True, exist_ok=False)
for row in rows:
    blob = subprocess.run(["git", "-C", str(project), "show", row["source"] + ":" + row["path"]], check=True, stdout=subprocess.PIPE).stdout
    assert hashlib.sha256(blob).hexdigest() == row["sha256"]
    target = output / "baseline/patches" / Path(row["path"]).name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(blob)
for name in ["FunctionLengthBaselineOracle.java", "run-baseline-oracle.py"]:
    (output / name).write_bytes((base / name).read_bytes())
