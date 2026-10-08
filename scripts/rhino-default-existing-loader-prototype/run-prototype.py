#!/usr/bin/env python3
"""Proof-only all277 canonical-source prototype with exact declared verification deltas."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("archive", type=Path)
parser.add_argument("--project", type=Path, required=True)
parser.add_argument("--api-recipe", type=Path, required=True, help="Frozen a31 post runtime-rhino/patches")
parser.add_argument("--javac", type=Path, required=True)
parser.add_argument("--java8", type=Path, required=True)
parser.add_argument("--classpath", required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args(); packet = Path(__file__).resolve().parent
args.output.mkdir(parents=True, exist_ok=False); prepared = args.output / "prepared"
subprocess.run([sys.executable, str(args.project / "scripts/prepare-rhino-sources.py"), str(args.archive),
    str(args.api_recipe / "rhino-source-manifest.json"), str(args.api_recipe / "rhino-java17-hunks.json"), str(prepared)], check=True)
deltas = json.loads((packet / "manifest.json").read_text())["verification_source_deltas"]
for item in deltas:
    source = prepared / "java" / item["path"]
    if hashlib.sha256(source.read_bytes()).hexdigest() != item["pre_sha256"]: raise ValueError("Prototype preimage differs")
    blob = (packet / "post" / item["path"]).read_bytes()
    if hashlib.sha256(blob).hexdigest() != item["post_sha256"]: raise ValueError("Prototype postimage differs")
    source.write_bytes(blob)
sources = sorted((prepared / "java").rglob("*.java"));
if len(sources) != 277: raise ValueError("Not genuine277closure")
classes = args.output / "classes"; classes.mkdir()
argfile = args.output / "source-list.txt"; argfile.write_text("\n".join(str(p) for p in sources + [packet / "ExistingLoaderDefaultPrototype.java"]) + "\n")
subprocess.run([str(args.javac), "--release", "8", "-proc:none", "-encoding", "UTF-8", "-classpath", args.classpath,
    "-d", str(classes), "@" + str(argfile)], check=True)
majors = {}
for path in classes.rglob("*.class"):
    data = path.read_bytes(); major = int.from_bytes(data[6:8], "big")
    if data[:4] != b"\xca\xfe\xba\xbe" or major != 52: raise ValueError("Not major52")
    majors[str(path.relative_to(classes))] = major
completed = subprocess.run([str(args.java8), "-cp", str(classes) + os.pathsep + str(prepared / "resources") + os.pathsep + args.classpath,
    "dev.latvian.mods.rhino.ExistingLoaderDefaultPrototype"], text=True, capture_output=True)
(args.output / "java8.stdout.log").write_text(completed.stdout); (args.output / "java8.stderr.log").write_text(completed.stderr)
(args.output / "receipt.json").write_text(json.dumps({"scope": "prototypeONLYexistingRhino-loader generatedchildLookup defaultinvocation",
    "sourceCount":277, "classMajors":majors, "runtimeExitCode":completed.returncode,
    "canonicalProductionDefaultPath": "NOT_INTEGRATED; absentJava8reflectionpath failsexplicitly and isnotcalledbyprototype",
    "fullRuntimeJava8Acceptance":False}, indent=2)+"\n")
print(completed.stdout, end=""); print(completed.stderr, end="", file=sys.stderr); sys.exit(completed.returncode)
