#!/usr/bin/env python3
"""Compile the complete canonical runtime with genuine javac8 and execute the real Java8 graph."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("archive", type=Path)
parser.add_argument("--javac8", type=Path, required=True)
parser.add_argument("--java8", type=Path, required=True)
parser.add_argument("--classpath", required=True, help="Actual canonical Rhino dependency JARs only")
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args(); root = Path(__file__).resolve().parents[1]
version = subprocess.run([str(args.javac8), "-version"], text=True, capture_output=True, check=True)
compiler_version = (version.stdout + version.stderr).strip()
if "javac 1.8." not in compiler_version: raise ValueError("Require genuine javac8, got " + compiler_version)
args.output.mkdir(parents=True, exist_ok=False); prepared = args.output / "prepared"
manifest = root / "runtime-rhino/patches/rhino-source-manifest.json"
subprocess.run([sys.executable, str(root / "scripts/prepare-rhino-sources.py"), str(args.archive),
    str(manifest), str(root / "runtime-rhino/patches/rhino-java17-hunks.json"), str(prepared)], check=True)
sources = sorted((prepared / "java").rglob("*.java"))
if len(sources) != 277: raise ValueError("Not all277canonical sources")
fixture = root / "runtime-rhino/src/java8Fixture/java/dev/latvian/mods/rhino/CanonicalDefaultGraphFixture.java"
classes = args.output / "classes"; classes.mkdir()
argfile = args.output / "source-list.txt"; argfile.write_text("\n".join(str(p) for p in sources + [fixture]) + "\n")
command = [str(args.javac8), "-source", "8", "-target", "8", "-proc:none", "-encoding", "UTF-8", "-classpath", args.classpath,
    "-d", str(classes), "@" + str(argfile)]
compiled = subprocess.run(command, text=True, capture_output=True)
(args.output / "javac8.stdout.log").write_text(compiled.stdout); (args.output / "javac8.stderr.log").write_text(compiled.stderr)
if compiled.returncode: print(compiled.stderr, file=sys.stderr); sys.exit(compiled.returncode)
majors = {}
for path in classes.rglob("*.class"):
    blob = path.read_bytes(); major = int.from_bytes(blob[6:8], "big")
    if blob[:4] != b"\xca\xfe\xba\xbe" or major != 52: raise ValueError("Not genuine52 compiled output")
    majors[str(path.relative_to(classes))] = major
completed = subprocess.run([str(args.java8), "-cp", str(classes) + os.pathsep + str(prepared / "resources") + os.pathsep + args.classpath,
    "dev.latvian.mods.rhino.CanonicalDefaultGraphFixture"], text=True, capture_output=True)
(args.output / "java8.stdout.log").write_text(completed.stdout); (args.output / "java8.stderr.log").write_text(completed.stderr)
(args.output / "receipt.json").write_text(json.dumps({"scope": "all277canonicalproduction javac8/runtime8 ordinaryscript+publicdefaultgraph",
    "compiler":compiler_version, "compilerExitCode":compiled.returncode, "runtimeExitCode":completed.returncode,
    "sourceCount":277, "recipeManifestSha256":hashlib.sha256(manifest.read_bytes()).hexdigest(), "classMajors":majors,
    "runtimeOutput":completed.stdout.strip(), "fullForgeAcceptance":False}, indent=2)+"\n")
print(completed.stdout, end=""); print(completed.stderr, end="", file=sys.stderr); sys.exit(completed.returncode)
