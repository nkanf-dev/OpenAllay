#!/usr/bin/env python3
"""Run the exact same guest fixture on all authentic pre-lowering canonical Rhino sources."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("archive", type=Path)
parser.add_argument("--project", type=Path, required=True)
parser.add_argument("--javac", type=Path, required=True)
parser.add_argument("--java", type=Path, required=True)
parser.add_argument("--classpath", required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
base = Path(__file__).resolve().parent
args.output.mkdir(parents=True, exist_ok=False)
generated = args.output / "prepared"
subprocess.run([sys.executable, str(args.project / "scripts/prepare-rhino-sources.py"), str(args.archive),
    str(base / "baseline/patches/rhino-source-manifest.json"), str(base / "baseline/patches/rhino-java17-hunks.json"), str(generated)], check=True)
sources = sorted((generated / "java").rglob("*.java"))
if len(sources) != 277: raise ValueError("Not canonical277 baseline")
classes = args.output / "classes"; classes.mkdir()
argfile = args.output / "sources.txt"
argfile.write_text("\n".join(str(source) for source in sources + [base / "CompleteRegExpBaselineOracle.java"]) + "\n")
subprocess.run([str(args.javac), "--release", "17", "-proc:none", "-encoding", "UTF-8", "-classpath", args.classpath, "-d", str(classes), "@" + str(argfile)], check=True)
completed = subprocess.run([str(args.java), "-cp", str(classes) + ":" + str(generated / "resources") + ":" + args.classpath,
    "dev.latvian.mods.rhino.CompleteRegExpBaselineOracle"], check=True, text=True, capture_output=True)
print(completed.stdout, end="")
(args.output / "receipt.json").write_text(json.dumps({"scope": "exactpre-lowering277baseline complete RegExp guest fixture",
    "baselineManifestSha256": hashlib.sha256((base / "baseline/patches/rhino-source-manifest.json").read_bytes()).hexdigest(),
    "actual": completed.stdout.strip(), "fullRuntimeJava8Acceptance": False}, indent=2) + "\n")
