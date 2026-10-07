#!/usr/bin/env python3
"""Compile and execute only selected genuine canonical Rhino sources on Java8."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("archive", type=Path)
parser.add_argument("--javac", type=Path, required=True, help="Modern javac with --release 8")
parser.add_argument("--java8", type=Path, required=True, help="Genuine Java8 java executable")
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
args.output.mkdir(parents=True, exist_ok=False)
generated = args.output / "prepared"
subprocess.run([sys.executable, str(root / "scripts/prepare-rhino-sources.py"),
    str(args.archive), str(root / "runtime-rhino/patches/rhino-source-manifest.json"),
    str(root / "runtime-rhino/patches/rhino-java17-hunks.json"), str(generated)], check=True)
selected = ["dev/latvian/mods/rhino/MethodSignature.java",
    "dev/latvian/mods/rhino/util/Possible.java", "dev/latvian/mods/rhino/util/ListCompat.java"]
classes = args.output / "classes"
classes.mkdir()
fixture = root / "runtime-rhino/src/java8Fixture/java/dev/openallay/rhino/fixture/RhinoJava8LeafFixture.java"
subprocess.run([str(args.javac), "--release", "8", "-encoding", "UTF-8", "-d", str(classes)] +
    [str(generated / "java" / name) for name in selected] + [str(fixture)], check=True)
majors = {}
for path in classes.rglob("*.class"):
    data = path.read_bytes()
    if data[:4] != b"\xca\xfe\xba\xbe" or int.from_bytes(data[6:8], "big") != 52:
        raise RuntimeError("Fixture compile did not produce Java8 class: " + str(path))
    majors[str(path.relative_to(classes))] = 52
subprocess.run([str(args.java8), "-cp", str(classes), "dev.openallay.rhino.fixture.RhinoJava8LeafFixture"], check=True)
(args.output / "receipt.json").write_text(json.dumps({"scope": "canonical selected leaf subset only",
    "selectedCanonicalSources": selected, "classMajors": majors,
    "fullRuntimeJava8Acceptance": False}, indent=2) + "\n")
