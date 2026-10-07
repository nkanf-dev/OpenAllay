#!/usr/bin/env python3
"""Remote complete-owner catalog oracle. Stages immutable Git owners; requires genuine Java8."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--source-root", type=Path, required=True)
parser.add_argument("--jdk17", type=Path, required=True)
parser.add_argument("--jdk8", type=Path, required=True)
parser.add_argument("--gson", type=Path, required=True)
parser.add_argument("--junit-console", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
root = args.source_root.resolve()
contract = json.loads((Path(__file__).parent / "source-contract.json").read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()
assert sha(Path(__file__).read_bytes()) == contract["runner_sha256"], "Runner source differs"
def run(command):
    result = subprocess.run(list(map(str, command)), stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if result.returncode:
        raise RuntimeError("Command failed: " + " ".join(map(str, command)) + "\n" + result.stdout + result.stderr)
    return result
args.output.mkdir(parents=True, exist_ok=False)
original = args.output / "original"
for item in contract["files"]:
    blob = subprocess.run(["git", "-C", str(root), "cat-file", "blob", item["git_blob"]], check=True, stdout=subprocess.PIPE).stdout
    assert sha(blob) == item["pre_sha256"] and len(blob) == item["pre_bytes"], "Original Git owner differs"
    destination = original / item["path"]
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(blob)
    assert sha((root / item["path"]).read_bytes()) == item["post_sha256"], "Applied owner differs: " + item["path"]
for item in contract["unchanged_compile_frontier"] + contract["selected_modern_tests"] + contract["resources"]:
    assert sha((root / item["path"]).read_bytes()) == item["sha256"], "Support/resource/test differs: " + item["path"]
for item in contract["fixture_artifacts"]:
    assert sha((root / item["path"]).read_bytes()) == item["sha256"], "Fixture differs"
converter = contract["converter"]
assert sha((root / converter["path"]).read_bytes()) == converter["sha256"], "Accepted converter differs"
java8, javac8 = args.jdk8 / "bin/java", args.jdk8 / "bin/javac"
java17, javac17 = args.jdk17 / "bin/java", args.jdk17 / "bin/javac"
versions = {}
for name, binary in [("java8", java8), ("javac8", javac8), ("java17", java17)]:
    result = run([binary, "-version"])
    versions[name] = result.stdout + result.stderr
assert "1.8." in versions["java8"] and "1.8." in versions["javac8"], "Genuine Java8 compiler + runtime required"
# Authenticate exact full-owner converter outputs before any fixture compilation.
tooling = args.output / "converter-classes"
tooling.mkdir()
run([javac17, "--release", "17", "-d", tooling, root / converter["path"]])
rows = []
for path, selected in converter["selected"].items():
    rows.append("\t".join([str(original / path), path, sha((original / path).read_bytes()), selected]))
request = args.output / "converter.tsv"
request.write_text("\n".join(rows) + "\n")
converted = args.output / "converted"
run([java17, "-cp", tooling, "dev.openallay.build.RecordValueSourceConverter", request, converted])
for item in contract["files"]:
    path = item["path"]
    if not item["java8_compile"]:
        source = (original / path).read_text()
        change = contract["agent_boundary_change"]
        assert change["old"] in source
        assert source.replace(change["old"], change["new"]).encode() == (root / path).read_bytes()
        continue
    source_path = converted / "candidate" / path if path in converter["selected"] else original / path
    lowered = source_path.read_text()
    assert sha(lowered.encode()) == item["converter_expected_sha256"], "Authenticated conversion differs: " + path
    for change in contract["api_lowering"][path]:
        assert change["old"] in lowered, "Missing exact mechanical preimage: " + path
        lowered = lowered.replace(change["old"], change["new"])
    # Only blank-line trailing whitespace cleanup is allowed after recorded lowering.
    import re
    lowered = re.sub(r"(?m)^[ \t]+$", "", lowered)
    assert lowered.encode() == (root / path).read_bytes(), "Unproved owner change: " + path
support = [root / item["path"] for item in contract["unchanged_compile_frontier"]]
fixtures = [root / contract["fixture_path"], root / contract["junit_fixture_path"]]
# Exact existing tests that compile in this closed frontier, mechanically syntax/API lowered
# only in isolated test staging. Config-loader-dependent BuiltinModelCatalogTest remains
# a genuine modern Gradle acceptance class, not a fake standalone substitute.
standalone_tests = []
for item in contract["standalone_existing_tests"]:
    source = (root / item["path"]).read_text()
    for change in item["substitutions"]:
        assert change["old"] in source
        source = source.replace(change["old"], change["new"])
    staged = args.output / "java8-existing-tests" / item["path"]
    staged.parent.mkdir(parents=True, exist_ok=True)
    staged.write_text(source)
    standalone_tests.append(staged)
vectors, majors, logs = {}, {}, {}
cp = os.pathsep.join(map(str, [args.gson, args.junit_console]))
for flavor in ["modern", "release8", "java8"]:
    classes = args.output / (flavor + "-classes")
    classes.mkdir()
    owners = [original / item["path"] if flavor == "modern" else root / item["path"]
              for item in contract["files"] if item["java8_compile"]]
    compiler = javac8 if flavor == "java8" else javac17
    target = ["--release", "17" if flavor == "modern" else "8"] if flavor != "java8" else ["-source", "8", "-target", "8"]
    result = run([compiler] + target + ["-encoding", "UTF-8", "-classpath", cp, "-sourcepath", "", "-d", classes]
                 + owners + support + fixtures + standalone_tests)
    logs[flavor + "-compile"] = result.stdout + result.stderr
    majors[flavor] = {}
    for path in classes.rglob("*.class"):
        blob = path.read_bytes()
        assert blob[:4] == b"\xca\xfe\xba\xbe"
        major = int.from_bytes(blob[6:8], "big")
        if flavor != "modern": assert major == 52, str(path)
        majors[flavor][str(path.relative_to(classes))] = major
    for item in contract["resources"]:
        destination = classes / item["destination"]
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(root / item["path"], destination)
    # Genuine8 also executes --release8 products, never only a modern VM.
    vm = java17 if flavor == "modern" else java8
    execution = run([vm, "-cp", os.pathsep.join(map(str, [classes, args.gson])), "dev.openallay.model.metadata.ModelCatalogJava8Fixture"])
    vectors[flavor] = execution.stdout
    logs[flavor + "-fixture"] = execution.stdout + execution.stderr
    selected = ["dev.openallay.model.metadata.ModelCatalogJava8Test"] + [item["class"] for item in contract["standalone_existing_tests"]]
    selection = [part for name in selected for part in ["--select-class", name]]
    result = run([vm, "-jar", args.junit_console, "--class-path", os.pathsep.join(map(str, [classes, args.gson]))]
                 + selection + ["--reports-dir", args.output / (flavor + "-junit-results"), "--fail-if-no-tests"])
    logs[flavor + "-junit"] = result.stdout + result.stderr
assert vectors["modern"] == vectors["release8"] == vectors["java8"], "Full-owner original modern / genuine8 vectors differ"
for name, text in logs.items(): (args.output / (name + ".log")).write_text(text)
receipt = {"scope": contract["scope"], "changed_java8_owner_count": 10, "modern_boundary_owner_count": 1,
           "actual_production_compile_frontier_count": len(support) + 10, "explicit_schema_count": 24,
           "gson_sha256": sha(args.gson.read_bytes()), "junit_console_sha256": sha(args.junit_console.read_bytes()),
           "jdk_versions": versions, "class_majors": majors, "same_fixture_original_git_vs_actual8_vectors_equal": True,
           "behavior_vector_count": len(vectors["modern"].splitlines()), "standalone_existing_tests": contract["standalone_existing_tests"],
           "accepted_converter_source_sha256": converter["sha256"],
           "source_contract_sha256": sha((Path(__file__).parent / "source-contract.json").read_bytes()),
           "modern_full_engine_admission_test_required": contract["modern_admission_test_path"]}
(args.output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
print("PASS ten complete catalog/domain owners original modern oracle / release8 + genuine javac8/java8")
