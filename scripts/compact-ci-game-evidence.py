#!/usr/bin/env python3
"""Retain exact client evidence and lossless compact images without deleting originals."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
MAX_FILE = 128 * 1024 * 1024
MAX_TOTAL = 512 * 1024 * 1024


def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def exact_copy(record, output, root, totals):
    path = Path(record["path"])
    tree = (root / "build/e2e").resolve()
    if not path.is_absolute() or path.resolve(strict=True) != path or not path.is_relative_to(tree):
        raise ValueError("Evidence path escaped the disposable CI tree or contains symlinks")
    if not path.is_file() or path.stat().st_size != record["sizeBytes"] or not 0 <= record["sizeBytes"] <= MAX_FILE:
        raise ValueError("Evidence file changed or exceeds the file bound")
    relative = path.relative_to(tree)
    if any(part in {"saves", "assets", "libraries", "natives", "mods", "models", "credentials"} for part in relative.parts):
        raise ValueError("Evidence inventory contains runtime/player data")
    allowed_game = ("game" not in relative.parts or any(marker in relative.as_posix() for marker in
                    ("/game/openallay/exports/", "/game/config/openallay/e2e/", "/game/crash-reports/"))
                    or relative.as_posix().endswith(("/game/logs/latest.log", "/game/logs/debug.log")))
    if not allowed_game:
        raise ValueError("Evidence inventory contains a non-diagnostic game file")
    expected = record["sha256"]
    if digest(path) != expected:
        raise ValueError("Source evidence SHA256 changed")
    destination = output / "retained-exact" / relative
    if destination.exists():
        if digest(destination) != expected:
            raise ValueError("Conflicting retained evidence")
        return
    totals[0] += record["sizeBytes"]
    if totals[0] > MAX_TOTAL:
        raise ValueError("Retained exact evidence exceeds the total bound")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(path, destination)
    if digest(destination) != expected or digest(path) != expected:
        raise ValueError("Evidence changed while retaining the exact copy")


def compact(loader, batch_id, root=ROOT):
    root = root.resolve()
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,55}", batch_id):
        raise ValueError("Unsafe client batch id")
    spec = importlib.util.spec_from_file_location("ci_lossless_diagnostics", root / "scripts/prepare-ci-diagnostics.py")
    diagnostics = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(diagnostics)
    batch = root / "build/e2e/ci-client" / loader / batch_id
    summary = batch / "summary.json"
    output = root / "build/e2e/ci-client-compact" / loader / batch_id
    if output.exists():
        raise ValueError("Compact diagnostic output already exists")
    output.mkdir(parents=True)
    receipts, failures, totals = [], [], [0]
    value = json.loads(summary.read_text()) if summary.is_file() else {}
    scenarios = value.get("scenarios", [])
    # Only known exact scenario roots; source snapshots are handled by their own receipts.
    sources = [batch] if batch.is_dir() else []
    sources += [Path(record["directory"]) for record in scenarios if record.get("directory")]
    for index, source in enumerate(dict.fromkeys(sources)):
        canonical = source.resolve(strict=True)
        if canonical != source or not source.is_relative_to(root / "build/e2e"):
            raise ValueError("Diagnostic source escaped the disposable CI tree or contains symlinks")
        includes = ["game/logs/" + name for name in ("latest.log", "debug.log")
                    if (source / "game/logs" / name).is_file()]
        try:
            receipts.append(diagnostics.prepare(source, output / ("run-" + str(index)), includes=includes, repo=root))
        except (ValueError, OSError) as failure:
            failures.append({"source": str(source), "error": str(failure)})
            # The encoder did not publish. Keep each selected original diagnostic instead.
            for relative in diagnostics.select_files(source, includes):
                path = source / relative
                exact_copy({"path": str(path), "sha256": digest(path), "sizeBytes": path.stat().st_size}, output, root, totals)
    for record in scenarios:
        for evidence in record.get("evidence", []):
            path = Path(evidence["path"])
            # Successful image compression already retained a pixel-identical copy.
            if path.suffix == ".png":
                matching = [entry for receipt in receipts for entry in receipt["files"]
                            if str(Path(receipt["sourceRoot"]) / entry["sourcePath"]) == str(path)
                            and entry["kind"] == "screenshot"]
                if matching:
                    if matching[0]["sourceSha256"] != evidence["sha256"] or digest(path) != evidence["sha256"]:
                        raise ValueError("Compressed screenshot source changed")
                    continue
            exact_copy(evidence, output, root, totals)
    for source in value.get("sources", {}).values():
        exact_copy(source["retained"], output, root, totals)
    source_receipts = batch / "source-receipts.json"
    if source_receipts.is_file():
        exact_copy({"path": str(source_receipts), "sha256": digest(source_receipts), "sizeBytes": source_receipts.stat().st_size}, output, root, totals)
    collection = {"loader": loader, "batchId": batch_id, "originalsDeleted": False,
                  "diagnosticRuns": len(receipts), "exactEvidenceBytes": totals[0],
                  "compressionFailures": failures,
                  "manifestPaths": [str(path.relative_to(output)) for path in output.glob("run-*/diagnostics-manifest.json")]}
    (output / "collection.json").write_text(json.dumps(collection, indent=2) + "\n")
    print(output)
    return output, not failures


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--loader", required=True, choices=("fabric", "neoforge"))
    parser.add_argument("--batch-id", required=True)
    arguments = parser.parse_args()
    try:
        _, passed = compact(arguments.loader, arguments.batch_id)
        raise SystemExit(0 if passed else 1)
    except (OSError, ValueError, KeyError) as failure:
        parser.exit(1, "Client diagnostic compression failed: " + str(failure) + "\n")
