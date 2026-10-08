#!/usr/bin/env python3
"""Restore three exact historical source-recipe files for one compiler baseline oracle."""
import hashlib
from pathlib import Path
import subprocess
import sys
base = Path(__file__).resolve().parent
rows = [{'path': 'runtime-rhino/patches/rhino-source-manifest.json', 'source': '8aaa97fb8d2ea82065624f13c39fdb6e615e15c7', 'sha256': '4f108395e06ffc285f3cca8a45775a092ddfdd51d516fa66cfa32f26302b74f6'}, {'path': 'runtime-rhino/patches/rhino-java17-hunks.json', 'source': '8aaa97fb8d2ea82065624f13c39fdb6e615e15c7', 'sha256': '457104d35dbe21aa80161e7f7b6c1a7c68acb9820081240f1ded1c0c87314748'}, {'path': 'runtime-rhino/patches/rhino-java17.patch', 'source': '8aaa97fb8d2ea82065624f13c39fdb6e615e15c7', 'sha256': '41fb57471901f9fbc9796676c44345a52a08ca93997d966daf7a51a1c849f65a'}]
project = Path(sys.argv[1]).resolve()
output = Path(sys.argv[2]).resolve()
output.mkdir(parents=True, exist_ok=False)
for row in rows:
    blob = subprocess.run(["git", "-C", str(project), "show", row["source"] + ":" + row["path"]], check=True, stdout=subprocess.PIPE).stdout
    assert hashlib.sha256(blob).hexdigest() == row["sha256"]
    target = output / "baseline/patches" / Path(row["path"]).name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(blob)
for name in ["ParserMalformedBaselineOracle.java", "run-baseline-oracle.py"]:
    (output / name).write_bytes((base / name).read_bytes())
