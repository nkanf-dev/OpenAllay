#!/usr/bin/env python3
"""Remote-only authentic bridge history converter custody and complete-owner Java8 proof."""
import argparse
import difflib
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--source-root", type=Path, required=True)
parser.add_argument("--jdk17", type=Path, required=True)
parser.add_argument("--jdk8", type=Path, required=True)
parser.add_argument("--gson", type=Path, required=True)
parser.add_argument("--junit-console", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--prepare", action="store_true", help="Emit authentic owner patch only; no application/production compilation")
args = parser.parse_args()
root = args.source_root.resolve()
contract_path = Path(__file__).with_name("source-contract.json")
contract = json.loads(contract_path.read_bytes())
sha = lambda blob: hashlib.sha256(blob).hexdigest()
assert sha(Path(__file__).read_bytes()) == contract["runner_sha256"], "Runner source differs"

def run(command):
    result = subprocess.run(list(map(str, command)), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError("Command failed: " + " ".join(map(str, command)) + "\n" + (result.stdout + result.stderr).decode(errors="replace"))
    return result

def admitted(path, pins):
    blob = path.read_bytes()
    digests = {"sha256": sha(blob), "sha1": hashlib.sha1(blob).hexdigest()}
    matched = [pin for pin in pins if len(blob) == pin["bytes"] and digests[pin["digest_algorithm"]] == pin["digest"]]
    if len(matched) != 1: raise ValueError("Dependency bytes not pinned: " + str(path))
    return {"name": matched[0]["name"], "bytes": len(blob), **digests}

# No dependency filenames or child receipts establish custody.
admission = {"gson": admitted(args.gson, contract["dependency_pins"]["gson"]),
             "junit_console": admitted(args.junit_console, contract["dependency_pins"]["junit_console"])}
converter = contract["converter"]
assert sha((root / converter["path"]).read_bytes()) == converter["sha256"], "Accepted converter source differs"
for item in contract["support"] + contract["fixtures"]:
    assert sha((root / item["path"]).read_bytes()) == item["sha256"], "Actual support/fixture differs: " + item["path"]
args.output.mkdir(parents=True, exist_ok=False)
original = args.output / "original"
for item in contract["owners"]:
    blob = run(["git", "-C", root, "cat-file", "blob", item["git_blob"]]).stdout
    assert sha(blob) == item["pre_sha256"] and len(blob) == item["pre_bytes"], "Original Git owner differs"
    path = original / item["path"]; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(blob)
for item in contract["support"]:
    # Exact unchanged canonical support is shared; a compact original source tree is test-only custody.
    path = original / item["path"]; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes((root / item["path"]).read_bytes())
java17, javac17 = args.jdk17 / "bin/java", args.jdk17 / "bin/javac"
java8, javac8 = args.jdk8 / "bin/java", args.jdk8 / "bin/javac"
versions = {}
for name, binary in [("java17", java17), ("java8", java8), ("javac8", javac8)]:
    result = run([binary, "-version"]); versions[name] = (result.stdout + result.stderr).decode()
assert "1.8." in versions["java8"] and "1.8." in versions["javac8"], "Genuine Java8 compiler/runtime required"
tools = args.output / "converter-classes"; tools.mkdir()
run([javac17, "--release", "17", "-d", tools, root / converter["path"]])
rows = ["\t".join([str(original / item["path"]), item["path"], item["pre_sha256"], item["record"]])
        for item in contract["owners"] if item["record"]]
request = args.output / "converter-request.tsv"; request.write_text("\n".join(rows) + "\n")
converted = args.output / "converted"
run([java17, "-cp", tools, "dev.openallay.build.RecordValueSourceConverter", request, converted])
records, patches, mismatches = [], [], []
for item in contract["owners"]:
    source = converted / "candidate" / item["path"] if item["record"] else original / item["path"]
    before_lowering = source.read_bytes()
    text = before_lowering.decode()
    for change in item["api_substitutions"]:
        assert text.count(change["old"]) == change["count"], "Mechanical API count differs: " + item["path"]
        text = text.replace(change["old"], change["new"])
    # Only declared blank-line trailing whitespace cleanup follows authentic AST/API output.
    text = re.sub(r"(?m)^[ \t]+(?=\r?$)", "", text)
    blob = text.encode()
    destination = args.output / "authenticated" / item["path"]
    destination.parent.mkdir(parents=True, exist_ok=True); destination.write_bytes(blob)
    actual = (root / item["path"]).read_bytes()
    if args.prepare:
        assert sha(actual) == item["pre_sha256"] and len(actual) == item["pre_bytes"], "Prepare requires exact unchanged original canonical owner: " + item["path"]
    records.append({"path": item["path"], "original_sha256": item["pre_sha256"],
                    "converter_sha256": sha(before_lowering), "authenticated_sha256": sha(blob),
                    "actual_sha256": sha(actual), "matches_actual": blob == actual})
    if blob != actual:
        mismatches.append(item["path"])
        patches.append("".join(difflib.unified_diff(actual.decode().splitlines(True), text.splitlines(True),
                                                fromfile="a/" + item["path"], tofile="b/" + item["path"])))
(args.output / "authentic-owner-correction.patch").write_text("".join(patches))
(args.output / "converter-custody.json").write_text(json.dumps({"accepted_converter_sha256": converter["sha256"], "owners": records}, indent=2) + "\n")
if args.prepare:
    print("PREPARED authentic converter/API owner patch; mismatches=" + str(len(mismatches)))
    raise SystemExit(0)
assert not mismatches, "Canonical sources differ from authentic converter+recordedAPI output: " + ", ".join(mismatches)
cp = os.pathsep.join(map(str, [args.gson, args.junit_console]))
support = [root / item["path"] for item in contract["support"]]
fixtures = [root / item["path"] for item in contract["fixtures"]]
vectors, majors, junit = {}, {}, {}
for flavor in ["original17", "release8", "javac8"]:
    classes = args.output / (flavor + "-classes"); classes.mkdir()
    owners = [original / item["path"] if flavor == "original17" else root / item["path"] for item in contract["owners"]]
    compiler = javac8 if flavor == "javac8" else javac17
    options = ["-source", "8", "-target", "8"] if flavor == "javac8" else ["--release", "17" if flavor == "original17" else "8"]
    result = run([compiler] + options + ["-encoding", "UTF-8", "-classpath", cp, "-sourcepath", "", "-d", classes] + owners + support + fixtures)
    (args.output / (flavor + "-compile.log")).write_bytes(result.stdout + result.stderr)
    majors[flavor] = {}
    for path in classes.rglob("*.class"):
        blob = path.read_bytes(); assert blob[:4] == b"\xca\xfe\xba\xbe"
        major = int.from_bytes(blob[6:8], "big"); majors[flavor][str(path.relative_to(classes))] = major
        if flavor != "original17": assert major == 52
    vm = java17 if flavor == "original17" else java8
    execution = run([vm, "-cp", os.pathsep.join(map(str, [classes, args.gson])), "dev.openallay.bridge.protocol.BridgeHistoryJava8Fixture"] + ([] if flavor == "original17" else ["java8"]))
    vectors[flavor] = execution.stdout
    (args.output / (flavor + "-vectors.log")).write_bytes(execution.stdout + execution.stderr)
    result = run([vm, "-jar", args.junit_console, "--class-path", os.pathsep.join(map(str, [classes, args.gson])),
                  "--select-class", "dev.openallay.bridge.protocol.BridgeHistoryJava8Test", "--reports-dir", args.output / (flavor + "-junit"), "--fail-if-no-tests"])
    (args.output / (flavor + "-junit.log")).write_bytes(result.stdout + result.stderr)
    junit[flavor] = True
assert vectors["original17"] == vectors["release8"] == vectors["javac8"], "Actual original-modern / genuineJava8 bridge history vectors differ"
receipt = {"scope": contract["scope"], "dependencies": admission, "jdk_versions": versions,
           "converter_custody": records, "class_majors": majors, "junit": junit,
           "vectors_equal": True, "vector_sha256": sha(vectors["original17"]),
           "contract_sha256": sha(contract_path.read_bytes()), "runner_sha256": sha(Path(__file__).read_bytes())}
(args.output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
print("PASS authentic bridge history owners original17 / release8 / genuine javac8+java8")
