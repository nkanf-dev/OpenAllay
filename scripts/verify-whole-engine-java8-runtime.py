#!/usr/bin/env python3
"""Genuine whole-engine Java8 compile/runtime/JAR proof using normal exported production paths."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import zipfile

def digest(blob): return hashlib.sha256(blob).hexdigest()
def class_entries(path):
    if path.is_file():
        with zipfile.ZipFile(path) as archive:
            return [(name, archive.read(name)) for name in archive.namelist() if name.endswith(".class")]
    if path.is_dir():
        return [(str(file.relative_to(path)).replace(os.sep, "/"), file.read_bytes()) for file in sorted(path.rglob("*.class"))]
    raise ValueError("Missing actual classpath artifact: " + str(path))
def identity(path):
    if path.is_file(): return {"bytes": path.stat().st_size, "sha256": digest(path.read_bytes())}
    rows = [{"path": str(file.relative_to(path)).replace(os.sep, "/"), "bytes": file.stat().st_size,
        "sha256": digest(file.read_bytes())} for file in sorted(path.rglob("*")) if file.is_file()]
    return {"files": rows, "sha256": digest(json.dumps(rows, sort_keys=True).encode())}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, required=True)
    parser.add_argument("--classpath-metadata", type=Path, required=True)
    parser.add_argument("--javac8", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--expected-sources", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(); project = args.project.resolve(); output = args.output.resolve()
    if output == project or project in output.parents: raise ValueError("Fresh external output required")
    output.mkdir(parents=True, exist_ok=False)
    metadata = json.loads(args.classpath_metadata.read_text()); root = project / "engine-core/src/main/java"
    sources = sorted(root.rglob("*.java")); paths = [str(path.resolve()) for path in sources]
    if Path(metadata["sourceRoot"]).resolve() != root or sorted(metadata["sources"]) != paths or metadata["producer"] != ":engine-core:compileJava":
        raise ValueError("Require exact normal current complete engine source/classpath export")
    if len(sources) != args.expected_sources: raise ValueError("Source count differs: " + str(len(sources)))
    rows = []; covered = set()
    for path in sources:
        blob = path.read_bytes(); match = re.search(rb"\bpackage\s+([A-Za-z_$][A-Za-z0-9_$.]*)\s*;", blob)
        if not match: raise ValueError("Missing production package")
        covered.add(match.group(1).decode().replace(".", "/") + "/" + path.stem)
        rows.append({"path": str(path.relative_to(project)), "bytes": len(blob), "sha256": digest(blob)})
    compile_items = [Path(item).resolve() for item in metadata["compileClasspath"]]
    runtime_items = [Path(item).resolve() for item in metadata["runtimeClasspath"]]
    records = []; replacements = set(); blockers = []
    for path in sorted(set(compile_items + runtime_items)):
        entries = class_entries(path); base = {}; mr = {}; overlap = []
        for name, blob in entries:
            if blob[:4] != b"\xca\xfe\xba\xbe": raise ValueError("Bad class: " + name)
            major = struct.unpack(">H", blob[6:8])[0]
            multi = name.startswith("META-INF/versions/"); counts = mr if multi else base
            counts[str(major)] = counts.get(str(major), 0) + 1
            if not multi and name.endswith(".class") and name[:-6].split("$", 1)[0] in covered: overlap.append(name)
        owned_output = path == project / "engine-core/build/classes/java/main" or project / "engine-core/build/classes" in path.parents
        replace = bool(overlap) or owned_output
        if replace:
            # Replace only fully source-covered output, never discard unique runtime JSON/Maven support.
            extras = [name for name, blob in entries if not name.startswith("META-INF/versions/") and name[:-6].split("$", 1)[0] not in covered]
            if extras: raise ValueError("Mixed engine artifact cannot be silently removed: " + str(path) + ": " + str(extras[:8]))
            replacements.add(path)
        high = [{"class": name, "major": struct.unpack(">H", blob[6:8])[0]} for name, blob in entries
            if not name.startswith("META-INF/versions/") and struct.unpack(">H", blob[6:8])[0] > 52]
        if high and not replace: blockers.append({"path": str(path), "baseClassesAbove52": high})
        records.append({"path": str(path), "identity": identity(path), "baseClassMajors": base, "multiReleaseClassMajors": mr,
            "java8MultiReleaseHandling": "Physical MR entries are retained and ignored by the Java8 VM",
            "replaceWithCompleteFreshEngineOutput": replace, "sourceCoveredClasses": overlap,
            "compileDependency": path in compile_items, "runtimeDependency": path in runtime_items})
    (output / "source-receipt.json").write_text(json.dumps(rows, indent=2) + "\n")
    (output / "classpath-receipt.json").write_text(json.dumps({"artifacts": records, "externalBaseBlockers": blockers}, indent=2) + "\n")
    if blockers: raise ValueError("Actual runtime dependency base classes exceed Java8; see classpath-receipt.json")
    compile_cp = [path for path in compile_items if path not in replacements]
    runtime_cp = [path for path in runtime_items if path not in replacements]
    version = subprocess.run([str(args.javac8), "-version"], text=True, capture_output=True, check=True)
    compiler = (version.stdout + version.stderr).strip()
    if not compiler.startswith("javac 1.8."): raise ValueError("Require genuine javac8: " + compiler)
    classes = output / "classes"; classes.mkdir()
    fixtures = [project / "engine-core/src/java8Fixture/java/dev/openallay/script/UnrestrictedJava8FactsFixture.java",
        project / "engine-core/src/java8Fixture/java/dev/openallay/script/WholeEngineJava8RuntimeFixture.java"]
    arguments = output / "all-production-and-real-fixtures.args"
    arguments.write_text("\n".join(json.dumps(str(path)) for path in sources + fixtures) + "\n")
    command = [str(args.javac8), "-source", "8", "-target", "8", "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "10000",
        "-classpath", os.pathsep.join(str(path) for path in compile_cp), "-d", str(classes), "@" + str(arguments)]
    compiled = subprocess.run(command, text=True, capture_output=True, cwd=project)
    (output / "javac8.stdout.log").write_text(compiled.stdout); (output / "javac8.stderr.log").write_text(compiled.stderr)
    (output / "compile-command.json").write_text(json.dumps(command, indent=2) + "\n")
    if compiled.returncode: print(compiled.stderr, file=sys.stderr); sys.exit(compiled.returncode)
    majors = {}; hashes = []
    for path in sorted(classes.rglob("*.class")):
        blob = path.read_bytes(); major = struct.unpack(">H", blob[6:8])[0]
        if major != 52: raise ValueError("Not genuine Java8 class: " + str(path))
        majors[str(major)] = majors.get(str(major), 0) + 1
        hashes.append({"path": str(path.relative_to(classes)).replace(os.sep, "/"), "sha256": digest(blob)})
    # Build a verification-only whole engine JAR, excluding standalone fixture classes. Not a release artifact.
    jar_path = output / "canonical-engine-java8-proof.jar"
    with zipfile.ZipFile(jar_path, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        for path in sorted(classes.rglob("*.class")):
            name = str(path.relative_to(classes)).replace(os.sep, "/")
            if name.startswith("dev/openallay/script/UnrestrictedJava8FactsFixture") or name.startswith("dev/openallay/script/WholeEngineJava8RuntimeFixture"): continue
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED; jar.writestr(info, path.read_bytes())
        resources = project / "engine-core/src/main/resources"
        if resources.exists():
            for path in sorted(resources.rglob("*")):
                if path.is_file():
                    info = zipfile.ZipInfo(str(path.relative_to(resources)).replace(os.sep, "/"), (1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED; jar.writestr(info, path.read_bytes())
    # Fixture classes are separate so production is actually loaded from the audited whole-engine proof JAR.
    fixture_classes = output / "fixture-classes"; fixture_classes.mkdir()
    for path in classes.rglob("*.class"):
        name = str(path.relative_to(classes)).replace(os.sep, "/")
        if name.startswith("dev/openallay/script/UnrestrictedJava8FactsFixture") or name.startswith("dev/openallay/script/WholeEngineJava8RuntimeFixture"):
            destination = fixture_classes / name; destination.parent.mkdir(parents=True, exist_ok=True); destination.write_bytes(path.read_bytes())
    # Use normal resource/dependency entries plus complete whole production JAR. No aliases/DTO copies.
    runtime_command = [str(args.java8), "-cp", os.pathsep.join([str(fixture_classes), str(jar_path)] + [str(path) for path in runtime_cp]),
        "dev.openallay.script.WholeEngineJava8RuntimeFixture"]
    executed = subprocess.run(runtime_command, text=True, capture_output=True, cwd=project)
    (output / "java8.stdout.log").write_text(executed.stdout); (output / "java8.stderr.log").write_text(executed.stderr)
    (output / "runtime-command.json").write_text(json.dumps(runtime_command, indent=2) + "\n")
    current_rows = [{"path": str(path.relative_to(project)), "bytes": len(path.read_bytes()), "sha256": digest(path.read_bytes())} for path in sorted(root.rglob("*.java"))]
    if rows != current_rows: raise ValueError("Production source changed during proof")
    (output / "receipt.json").write_text(json.dumps({"scope": "whole current canonical engine genuine javac8/runtime8/JAR closure proof",
        "compiler": compiler, "compilerExitCode": compiled.returncode, "runtimeExitCode": executed.returncode, "sourceCount": len(sources),
        "sourceReceiptSha256": digest((output / "source-receipt.json").read_bytes()), "classpathReceiptSha256": digest((output / "classpath-receipt.json").read_bytes()),
        "compiledClassMajors": majors, "classHashes": hashes, "productionLoadedFromProofJar": True, "runtimeOutput": executed.stdout.strip(),
        "proofJar": {"path": str(jar_path), "bytes": jar_path.stat().st_size, "sha256": digest(jar_path.read_bytes()), "releaseArtifact": False},
        "wholeEngineJava8Acceptance": executed.returncode == 0, "ForgeAcceptance": False}, indent=2) + "\n")
    print(executed.stdout, end=""); print(executed.stderr, end="", file=sys.stderr); sys.exit(executed.returncode)
if __name__ == "__main__": main()
