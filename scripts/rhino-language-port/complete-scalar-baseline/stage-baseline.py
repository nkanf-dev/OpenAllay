#!/usr/bin/env python3
"""Restore three exact historical source-recipe files for one compiler baseline oracle."""
import hashlib
from pathlib import Path
import subprocess
import sys
base = Path(__file__).resolve().parent
rows = [{'path': 'runtime-rhino/patches/rhino-source-manifest.json', 'source': '992658bec71e27c1b70574111e07d1882f45959d', 'sha256': '386566a8b8e04b8730f62f3b33df84d6a44a0d11890ef58f61891b6c9c703f57'}, {'path': 'runtime-rhino/patches/rhino-java17-hunks.json', 'source': '992658bec71e27c1b70574111e07d1882f45959d', 'sha256': 'cbf1b064fd02745a103f6de96aed03ba1344ec7fc1b7af69b9a940075fcaf984'}, {'path': 'runtime-rhino/patches/rhino-java17.patch', 'source': '992658bec71e27c1b70574111e07d1882f45959d', 'sha256': '7ecb5228e302ebe9474129f4d3c59e9746b6112b45a7c47801a6d9f2e99936d5'}]
project = Path(sys.argv[1]).resolve()
output = Path(sys.argv[2]).resolve()
output.mkdir(parents=True, exist_ok=False)
for row in rows:
    blob = subprocess.run(["git", "-C", str(project), "show", row["source"] + ":" + row["path"]], check=True, stdout=subprocess.PIPE).stdout
    assert hashlib.sha256(blob).hexdigest() == row["sha256"]
    target = output / "baseline/patches" / Path(row["path"]).name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(blob)
for name in ["CompleteScalarBaselineOracle.java", "run-baseline-oracle.py"]:
    (output / name).write_bytes((base / name).read_bytes())
