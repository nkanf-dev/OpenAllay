#!/usr/bin/env python3
"""Compile and execute only selected genuine canonical Rhino sources on Java8."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("archive", type=Path)
parser.add_argument("--javac", type=Path, required=True, help="Modern javac with --release 8")
parser.add_argument("--java8", type=Path, required=True, help="Genuine Java8 java executable")
parser.add_argument("--annotations", type=Path, required=True, help="Authentic org.jetbrains:annotations:24.1.0 JAR")
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
annotation_sha256 = "27a770dc7ce50500918bb8c3c0660c98290630ec796b5e3cf6b90f403b3033c6"
if hashlib.sha256(args.annotations.read_bytes()).hexdigest() != annotation_sha256:
    raise RuntimeError("JetBrains annotations24.1.0 publication bytes differ")
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
subprocess.run([str(args.javac), "--release", "8", "-encoding", "UTF-8", "-classpath", str(args.annotations), "-d", str(classes)] +
    [str(generated / "java" / name) for name in selected] + [str(fixture)], check=True)
majors = {}
for path in classes.rglob("*.class"):
    data = path.read_bytes()
    if data[:4] != b"\xca\xfe\xba\xbe" or int.from_bytes(data[6:8], "big") != 52:
        raise RuntimeError("Fixture compile did not produce Java8 class: " + str(path))
    majors[str(path.relative_to(classes))] = 52
# Nullable has CLASS retention. Check actual field/constructor/accessor/of bytecode metadata,
# not Java reflection (which cannot observe this annotation).
import re
metadata = subprocess.check_output([str(args.javac.with_name("javap")), "-p", "-v",
    "-classpath", str(classes), "dev.latvian.mods.rhino.util.Possible"], text=True)
body = metadata[metadata.index("\n{"):metadata.rindex("\n}")]
blocks = re.split(r"(?m)^  (?=(?:public|private|protected) )", body)
required = ["private final java.lang.Object value;", "Possible(java.lang.Object);",
    "public java.lang.Object value();", " of(T);"]
for signature in required:
    matched = [block for block in blocks if signature in block.split("\n", 1)[0]]
    if len(matched) != 1 or "org.jetbrains.annotations.Nullable" not in matched[0]:
        raise RuntimeError("Missing canonical Nullable class metadata: " + signature)
subprocess.run([str(args.java8), "-cp", str(classes), "dev.openallay.rhino.fixture.RhinoJava8LeafFixture"], check=True)
(args.output / "receipt.json").write_text(json.dumps({"scope": "canonical selected leaf subset only",
    "annotationsCoordinate": "org.jetbrains:annotations:24.1.0", "annotationsSha256": annotation_sha256,
    "selectedCanonicalSources": selected, "classMajors": majors,
    "nullableMetadataPositions": ["value field", "constructor parameter", "value accessor", "of parameter"],
    "fullRuntimeJava8Acceptance": False}, indent=2) + "\n")
