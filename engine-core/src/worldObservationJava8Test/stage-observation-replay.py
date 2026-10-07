#!/usr/bin/env python3
"""Authenticate and stage the actual world-observation Java8 conversion proof."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import tarfile

HERE = Path(__file__).resolve().parent
METADATA_NAME = "conversion-metadata.json"
# Metadata is also pinned by this driver; changes require a reviewed verification-input update.
METADATA_SHA256 = "1c484b331ac1ee2b85ba3bb19dfeaa3c675a1b9aa25615e12dacddef499f7754"

def sha(data):
    return hashlib.sha256(data).hexdigest()

def require_hash(data, expected, label):
    if sha(data) != expected:
        raise ValueError("SHA256 differs: " + label)
    return data

def logical_path(value):
    path = PurePosixPath(value)
    if not value or path.is_absolute() or ".." in path.parts or str(path) != value:
        raise ValueError("Invalid logical source path: " + value)
    return value

def run(command, cwd=None, env=None):
    result = subprocess.run(command, cwd=cwd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if result.returncode:
        raise RuntimeError("Command failed: " + str(command) + "\n" + result.stderr.decode("utf-8", errors="replace"))
    return result.stdout

def apply_api_patch(candidate, api_patch):
    # A candidate can live under an active checkout's build/. Without a discovery
    # ceiling, git apply filters root-relative paths against that checkout prefix
    # and can report success while skipping every detached candidate file.
    env = os.environ.copy()
    for name in ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_PREFIX"):
        env.pop(name, None)
    env["GIT_CEILING_DIRECTORIES"] = str(candidate.resolve().parent)
    run(["git", "apply", "--no-index", "--check", str(api_patch.resolve())], cwd=candidate, env=env)
    run(["git", "apply", "--no-index", str(api_patch.resolve())], cwd=candidate, env=env)

def checked_blank_lines(data, changes):
    lines = data.splitlines(keepends=True)
    previous = len(lines) + 1
    for change in reversed(changes):
        start, end = change["startLine"], change["endLine"]
        if not 0 <= start <= end <= len(lines) or end >= previous:
            raise ValueError("Overlapping or invalid blank-line change")
        before = bytes.fromhex(change["beforeHex"])
        after = bytes.fromhex(change["afterHex"])
        if b"".join(lines[start:end]) != before:
            raise ValueError("Blank-line preimage differs")
        if any(line.strip() for line in before.splitlines() + after.splitlines()):
            raise ValueError("Cleanup changes a nonblank source line")
        lines[start:end] = after.splitlines(keepends=True)
        previous = start
    return b"".join(lines)

def original_sources(source_root, original_source, entries):
    names = [logical_path(entry["path"]) for entry in entries]
    if len(set(names)) != len(names):
        raise ValueError("Duplicate source inventory")
    resolved = run(["git", "-C", str(source_root), "rev-parse", "--verify", original_source + "^{commit}"]).decode().strip()
    if resolved != original_source:
        raise ValueError("Original source must be the exact recorded commit")
    archive = run(["git", "-C", str(source_root), "archive", "--format=tar", original_source, "--"] + names)
    result = {}
    with tarfile.open(fileobj=io.BytesIO(archive)) as stream:
        for member in stream.getmembers():
            if member.isdir():
                continue
            if not member.isfile() or member.name not in names or member.name in result:
                raise ValueError("Unexpected Git source archive member: " + member.name)
            result[member.name] = stream.extractfile(member).read()
    if set(result) != set(names):
        raise ValueError("Git source inventory is incomplete")
    for entry in entries:
        require_hash(result[entry["path"]], entry["sha256"], entry["path"])
    return result

def write_sources(root, sources):
    for name, data in sources.items():
        target = root / logical_path(name)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)

def request_rows(owners, input_root, nested):
    rows = []
    for owner in owners:
        names = owner["nestedRecords"] if nested else [owner["record"]]
        if not names:
            continue
        path = owner["path"]
        expected = owner["originalSha256"] if nested or not owner["nestedRecords"] else owner["stage1Sha256"]
        physical = input_root / path
        require_hash(physical.read_bytes(), expected, "converter input " + path)
        rows.append("\t".join([str(physical), path, expected, ",".join(names)]))
    return "\n".join(rows) + "\n"

def convert(java, classpath, metadata, request, output):
    run([str(java), "-cp", classpath, metadata["converterClass"], str(request), str(output)])
    receipt = json.loads((output / "conversion-receipt.json").read_text(encoding="utf-8"))
    if receipt.get("kind") != "review-only-canonical-record-patch":
        raise ValueError("Converter receipt kind differs")
    rows = [row.split("\t") for row in request.read_text(encoding="utf-8").splitlines()]
    files = receipt.get("files", [])
    if len(files) != len(rows) or {item["path"] for item in files} != {row[1] for row in rows}:
        raise ValueError("Converter receipt owner inventory differs")
    by_name = {row[1]: row for row in rows}
    for item in files:
        row = by_name[item["path"]]
        if item["beforeSha256"] != row[2]:
            raise ValueError("Converter receipt preimage differs")
        data = (output / "candidate" / logical_path(item["path"])).read_bytes()
        require_hash(data, item["afterSha256"], "converter receipt output")
        if len(data) != item["afterBytes"] or len(Path(row[0]).read_bytes()) != item["beforeBytes"]:
            raise ValueError("Converter receipt byte count differs")

def stage(args):
    metadata_data = require_hash((HERE / METADATA_NAME).read_bytes(), METADATA_SHA256, "conversion metadata")
    metadata = json.loads(metadata_data)
    if args.original_source != metadata["originalSource"]:
        raise ValueError("Original source revision differs")
    root = args.source_root.resolve(strict=True)
    output = args.output.resolve()
    if output.exists() or output == root or output in root.parents:
        raise ValueError("Fresh detached output required")
    if root in output.parents and output.relative_to(root).parts[0] != "build":
        raise ValueError("Output inside source root is allowed only under build/")
    api_patch = HERE / logical_path(metadata["apiPatch"])
    require_hash(api_patch.read_bytes(), metadata["apiPatchSha256"], "API lowering patch")
    sources = original_sources(root, args.original_source, metadata["sources"])
    current = {}
    owned = {owner["path"] for owner in metadata["owners"]}
    for entry in metadata["sources"]:
        if entry["path"] not in owned:
            require_hash((root / entry["path"]).read_bytes(), entry["sha256"], "current support " + entry["path"])
    for owner in metadata["owners"]:
        path = logical_path(owner["path"])
        current[path] = require_hash((root / path).read_bytes(), owner["canonicalSha256"], "current canonical " + path)
    # All source and patch inputs authenticate before any output is created.
    output.mkdir(parents=True)
    original = output / "original-source"
    write_sources(original, sources)
    nested_request = output / "nested-request.tsv"
    nested_request.write_text(request_rows(metadata["owners"], original, True), encoding="utf-8")
    stage1 = output / "stage1"
    convert(args.java, args.converter_classpath, metadata, nested_request, stage1)
    for owner in metadata["owners"]:
        if owner["nestedRecords"]:
            require_hash((stage1 / "candidate" / owner["path"]).read_bytes(), owner["stage1Sha256"], "authentic nested conversion " + owner["path"])
        else:
            write_sources(stage1 / "candidate", {owner["path"]: sources[owner["path"]]})
    outer_request = output / "outer-request.tsv"
    outer_request.write_text(request_rows(metadata["owners"], stage1 / "candidate", False), encoding="utf-8")
    stage2 = output / "stage2"
    convert(args.java, args.converter_classpath, metadata, outer_request, stage2)
    candidate = stage2 / "candidate"
    for owner in metadata["owners"]:
        require_hash((candidate / owner["path"]).read_bytes(), owner["stage2Sha256"], "authentic outer conversion " + owner["path"])
    # --no-index keeps Git application confined to the detached candidate, without modifying active source.
    apply_api_patch(candidate, api_patch)
    receipt = {"originalSource": args.original_source, "productionSourceCount": len(sources), "owners": []}
    for owner in metadata["owners"]:
        data = require_hash((candidate / owner["path"]).read_bytes(), owner["apiLoweredSha256"], "API-lowered candidate " + owner["path"])
        cleaned = require_hash(checked_blank_lines(data, owner["blankLineChanges"]), owner["canonicalSha256"], "recorded blank-line cleanup " + owner["path"])
        if cleaned != current[owner["path"]]:
            raise ValueError("Converted candidate differs from active canonical source")
        receipt["owners"].append({"path": owner["path"], "canonicalSha256": sha(cleaned)})
    (output / "replay-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(str(original))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--original-source", required=True)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--converter-classpath", required=True)
    parser.add_argument("--output", type=Path, required=True)
    stage(parser.parse_args())

if __name__ == "__main__":
    main()
