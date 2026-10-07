#!/usr/bin/env python3
"""Compile only the canonical Rhino JDK-only collection helper; execute on actual Java8."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("archive", type=Path)
parser.add_argument("--javac", type=Path, required=True)
parser.add_argument("--java8", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
args.output.mkdir(parents=True, exist_ok=False)
generated = args.output / "prepared"
subprocess.run([sys.executable, str(root / "scripts/prepare-rhino-sources.py"), str(args.archive),
    str(root / "runtime-rhino/patches/rhino-source-manifest.json"),
    str(root / "runtime-rhino/patches/rhino-java17-hunks.json"), str(generated)], check=True)
classes = args.output / "classes"; classes.mkdir()
source = generated / "java/dev/latvian/mods/rhino/util/ListCompat.java"
fixture = root / "runtime-rhino/src/java8Fixture/java/dev/openallay/rhino/fixture/RhinoJava8CollectionsFixture.java"
subprocess.run([str(args.javac), "--release", "8", "-encoding", "UTF-8", "-d", str(classes), str(source), str(fixture)], check=True)
majors = {}
for path in classes.rglob("*.class"):
    blob = path.read_bytes()
    if blob[:4] != b"\xca\xfe\xba\xbe" or int.from_bytes(blob[6:8], "big") != 52:
        raise RuntimeError("Not major52: " + str(path))
    majors[str(path.relative_to(classes))] = 52
subprocess.run([str(args.java8), "-cp", str(classes), "dev.openallay.rhino.fixture.RhinoJava8CollectionsFixture"], check=True)
(args.output / "receipt.json").write_text(json.dumps({"scope": "canonical collection helper only",
    "classMajors": majors, "fullRuntimeJava8Acceptance": False}, indent=2) + "\n")
