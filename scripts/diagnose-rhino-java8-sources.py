#!/usr/bin/env python3
"""Compile all277genuinecanonicalRhino sources against Java8 APIs; always preserve real diagnostics."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("archive", type=Path)
parser.add_argument("--javac", type=Path, required=True)
parser.add_argument("--classpath", required=True, help="Actual Rhino compileClasspath, no compiled Rhino/engine substitutes")
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
args.output.mkdir(parents=True, exist_ok=False)
generated = args.output / "prepared"
manifest = root / "runtime-rhino/patches/rhino-source-manifest.json"
hunks = root / "runtime-rhino/patches/rhino-java17-hunks.json"
subprocess.run([sys.executable, str(root / "scripts/prepare-rhino-sources.py"), str(args.archive),
    str(manifest), str(hunks), str(generated)], check=True)
sources = sorted((generated / "java").rglob("*.java"))
if len(sources) != 277: raise ValueError("Not exactcanonical277sourceclosure")
classpath = []
for item in args.classpath.split(os.pathsep):
    path = Path(item)
    if not path.is_file() or path.suffix != ".jar": raise ValueError("Require actual dependency JAR: " + item)
    with zipfile.ZipFile(path) as archive:
        if any(name.startswith(("dev/latvian/mods/rhino/", "dev/openallay/")) and name.endswith(".class") for name in archive.namelist()):
            raise ValueError("No compiled Rhino/engine substitution allowed: " + item)
    classpath.append({"path": item, "bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
classes = args.output / "diagnostic-classes"; classes.mkdir()
argfile = args.output / "canonical-source-list.txt"
argfile.write_text("\n".join(str(source) for source in sources) + "\n")
command = [str(args.javac), "--release", "8", "-proc:none", "-encoding", "UTF-8", "-XDrawDiagnostics",
    "-Xmaxerrs", "10000", "-classpath", args.classpath, "-d", str(classes), "@" + str(argfile)]
completed = subprocess.run(command, text=True, capture_output=True)
(args.output / "javac-java8.stdout.log").write_text(completed.stdout)
(args.output / "javac-java8.stderr.log").write_text(completed.stderr)
majors = {}
for path in classes.rglob("*.class"):
    blob = path.read_bytes(); major = int.from_bytes(blob[6:8], "big")
    if blob[:4] != b"\xca\xfe\xba\xbe": raise ValueError("Invalid class output")
    majors[str(path.relative_to(classes))] = major
receipt = {"scope": "all277actualcanonicalsourceJava8compilerdiagnostics", "compilerExitCode": completed.returncode,
    "sourceCount": len(sources), "recipeManifestSha256": hashlib.sha256(manifest.read_bytes()).hexdigest(),
    "sourceArchiveSha256": hashlib.sha256(args.archive.read_bytes()).hexdigest(), "classpath": classpath,
    "command": command, "classMajors": majors, "compilationPass": completed.returncode == 0,
    "fullRuntimeJava8Acceptance": False, "note": "Diagnostic compiler pass never substitutes actualJava8behavior/stockForgeacceptance"}
(args.output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
print("Rhino actualJava8compiler exit=" + str(completed.returncode) + " sources=" + str(len(sources)))
print(completed.stdout, end=""); print(completed.stderr, end="", file=sys.stderr)
# A failed compile remains a failed command, while its complete receipt/logs survive for artifact upload.
sys.exit(completed.returncode)
