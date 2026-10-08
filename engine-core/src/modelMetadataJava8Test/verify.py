#!/usr/bin/env python3
"""Remote-only exact-byte full-owner modern oracle and genuine Java8 metadata batch."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import os
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--source-root", type=Path, required=True)
parser.add_argument("--jdk17", type=Path, required=True)
parser.add_argument("--jdk8", type=Path, required=True)
parser.add_argument("--gson", type=Path, required=True)
parser.add_argument("--junit-console", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
root = args.source_root.resolve()
verification = Path(__file__).resolve().parent
manifest = json.loads((verification / "source-contract.json").read_text())
request = manifest["converter"]
sha = lambda blob: hashlib.sha256(blob).hexdigest()

def run(command):
    result = subprocess.run([str(part) for part in command], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
    if result.returncode:
        raise RuntimeError("Command failed: " + " ".join(map(str, command)) + "\n" + result.stdout + result.stderr)
    return result

args.output.mkdir(parents=True, exist_ok=False)
# Exact canonical source custody and original immutable Git oracle. No source-file duplicates live in Git.
original_root = args.output / "original-git-owners"
for item in manifest["owners"]:
    current = (root / item["path"]).read_bytes()
    assert sha(current) == item["canonicalSha256"] and len(current) == item["canonicalBytes"], "Canonical source differs: " + item["path"]
    archived = subprocess.run(["git", "-C", str(root), "cat-file", "blob", item["originalGitBlob"]], check=True, stdout=subprocess.PIPE).stdout
    assert sha(archived) == item["originalSha256"] and len(archived) == item["originalBytes"], "Original Git oracle differs"
    assert hashlib.sha1(b"blob " + str(len(archived)).encode() + b"\0" + archived).hexdigest() == item["originalGitBlob"]
    staged = original_root / item["path"]
    staged.parent.mkdir(parents=True, exist_ok=True)
    staged.write_bytes(archived)
for item in manifest["unchangedCompileFrontier"]:
    assert sha((root / item["path"]).read_bytes()) == item["sha256"], "Support owner differs: " + item["path"]
assert sha((root / request["path"]).read_bytes()) == request["sha256"]
assert sha((root / manifest["fixture"]["path"]).read_bytes()) == manifest["fixture"]["sha256"]
assert sha(Path(__file__).read_bytes()) == manifest["runnerSha256"]
# Official Maven Central digest sidecars and HEAD sizes are frozen publication facts.
for path, name in [(args.gson, args.gson.name), (args.junit_console, "junit-platform-console-standalone-1.11.4.jar")]:
    pin = manifest["authenticatedLibraries"][name]
    blob = path.read_bytes()
    assert len(blob) == pin["bytes"], "Dependency size differs: " + name
    algorithm = "sha256" if "sha256" in pin else "sha1"
    assert hashlib.new(algorithm, blob).hexdigest() == pin[algorithm], "Dependency digest differs: " + name
java8 = args.jdk8 / "bin/java"
javac8 = args.jdk8 / "bin/javac"
java17 = args.jdk17 / "bin/java"
javac17 = args.jdk17 / "bin/javac"
versions = {"java8": run([java8, "-version"]).stderr, "javac8": run([javac8, "-version"]).stderr,
            "java17": run([java17, "-version"]).stderr}
assert '1.8.' in versions["java8"], "Genuine Java8 JVM required"
compiler_version = run([javac8, "-version"])
assert "1.8." in compiler_version.stdout + compiler_version.stderr, "Genuine Java8 compiler required"

# Independent structural proof: run only the accepted public JavacTask converter.
tooling = args.output / "converter-classes"
tooling.mkdir()
run([javac17, "--release", "17", "-d", tooling, root / request["path"]])
converted_owners = {}
for stage, items in enumerate(request["stages"], 1):
    rows = []
    for item in items:
        input_file = original_root / item["path"] if stage == 1 else args.output / "converted-stage1/candidate" / item["path"]
        assert sha(input_file.read_bytes()) == item["inputSha256"]
        rows.append("\t".join([str(input_file), item["path"], item["inputSha256"], item["selected"]]))
    tsv = args.output / ("converter-stage" + str(stage) + ".tsv")
    tsv.write_text("\n".join(rows) + "\n")
    converted = args.output / ("converted-stage" + str(stage))
    run([java17, "-cp", tooling, "dev.openallay.build.RecordValueSourceConverter", tsv, converted])
    for item in items:
        generated = converted / "candidate" / item["path"]
        blob = generated.read_bytes()
        assert sha(blob) == item["expectedSha256"] and len(blob) == item["expectedBytes"], "Accepted converter output differs: " + item["path"]
        converted_owners[item["path"]] = generated
for item in manifest["owners"]:
    path = original_root / item["path"] if item["isEnum"] else converted_owners[item["path"]]
    blob = path.read_bytes()
    assert sha(blob) == item["convertedSha256"] and len(blob) == item["convertedBytes"]
    lowered = blob.decode()
    for substitution in manifest["helperSubstitutions"]:
        lowered = lowered.replace(substitution["old"], substitution["new"])
    assert sha(lowered.encode()) == item["helperPostSha256"]
    # Root's explicit blank-line-only formatting cleanup is source-custodied separately.
    lowered = "".join("\n" if line.endswith("\n") and not line.strip() else line for line in lowered.splitlines(True))
    assert lowered.encode() == (root / item["path"]).read_bytes(), "Unproved production change"
support = [root / item["path"] for item in manifest["unchangedCompileFrontier"]]
fixture = root / manifest["fixture"]["path"]
existing_test = root / manifest["selectedExistingTest"]["path"]
assert sha(existing_test.read_bytes()) == manifest["selectedExistingTest"]["sha256"]
result_vectors = {}
class_majors = {}
logs = {}
for flavor in ("modern", "java8"):
    classes = args.output / (flavor + "-classes")
    classes.mkdir()
    owners = [original_root / item["path"] if flavor == "modern" else root / item["path"] for item in manifest["owners"]]
    javac = javac17 if flavor == "modern" else javac8
    java = java17 if flavor == "modern" else java8
    target = ["--release", "17"] if flavor == "modern" else ["-source", "8", "-target", "8"]
    classpath = os.pathsep.join(map(str, [args.gson, args.junit_console]))
    compile_result = run([javac] + target + ["-encoding", "UTF-8", "-classpath", classpath,
                       "-sourcepath", "", "-d", classes] + owners + support + [fixture, existing_test])
    logs[flavor + "-compile"] = compile_result.stdout + compile_result.stderr
    majors = {}
    for path in classes.rglob("*.class"):
        blob = path.read_bytes()
        assert blob[:4] == b"\xca\xfe\xba\xbe"
        major = int.from_bytes(blob[6:8], "big")
        if flavor == "java8": assert major == 52, str(path)
        majors[str(path.relative_to(classes))] = major
    class_majors[flavor] = majors
    execution = run([java, "-cp", os.pathsep.join(map(str, [classes, args.gson])),
                     "dev.openallay.model.metadata.ModelMetadataValuesFixture", flavor])
    result_vectors[flavor] = execution.stdout
    logs[flavor + "-fixture"] = execution.stdout + execution.stderr
    # Existing exact consumer test executes once for each complete-owner implementation.
    junit = run([java, "-jar", args.junit_console, "execute", "--class-path", os.pathsep.join(map(str, [classes, args.gson])),
                 "--select-class", "dev.openallay.model.metadata.ModelMetadataResolutionTest",
                 "--reports-dir", args.output / (flavor + "-junit-results"), "--fail-if-no-tests"])
    logs[flavor + "-junit"] = junit.stdout + junit.stderr
    reports = list((args.output / (flavor + "-junit-results")).glob("TEST-*.xml"))
    total = passed = 0
    for report in reports:
        suite = ET.parse(report).getroot()
        total += int(suite.get("tests", "0"))
        passed += int(suite.get("tests", "0")) - int(suite.get("failures", "0")) - int(suite.get("errors", "0")) - int(suite.get("skipped", "0"))
    assert total == 3 and passed == 3, "Expected three real ModelMetadataResolutionTest cases"
assert result_vectors["modern"] == result_vectors["java8"], "Exact original-modern / actual8 behavior vectors differ"
for name, contents in logs.items(): (args.output / (name + ".log")).write_text(contents)
receipt = {"scope": manifest["scope"], "changed_owner_count": 5, "explicit_schema_count": 5,
           "actual_production_compile_frontier_count": 16, "gson_sha256": sha(args.gson.read_bytes()),
           "junit_console_sha256": sha(args.junit_console.read_bytes()), "jdk_versions": versions,
           "class_majors": class_majors, "same_fixture_original_git_vs_actual8_vectors_equal": True,
           "behavior_vector_count": len(result_vectors["modern"].splitlines()),
           "selected_existing_test": "dev.openallay.model.metadata.ModelMetadataResolutionTest",
           "selected_existing_test_expected_cases_per_vm": 3,
           "accepted_converter_source_sha256": request["sha256"],
           "packet_manifest_sha256": sha((verification / "source-contract.json").read_bytes())}
(args.output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
print("PASS five-owner model metadata value batch original modern oracle / genuine Java8 complete source frontier")
