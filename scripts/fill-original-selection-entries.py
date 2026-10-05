#!/usr/bin/env python3
"""Fill original-entry fields on Root's remote runner. No build, game, or test."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import zipfile

REQUIRED_ENGINE_ENTRIES = (
    "dev/openallay/guide/GuideService.class",
    "dev/openallay/FeatureServices.class",
)
MAX_ARCHIVE_BYTES = 2 * 1024**3


def fail(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def write_new(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2, sort_keys=True)
        stream.write("\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--draft", type=Path, required=True)
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--member-receipt", type=Path, required=True)
    args = parser.parse_args()
    root = args.root.resolve(strict=True)
    fail(not args.output.exists() and not args.member_receipt.exists(), "Preserve previous output")
    spec = importlib.util.spec_from_file_location("promote_originals", root / "scripts/promote-accepted-artifacts.py")
    promoter = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(promoter)
    data = json.loads(args.draft.read_text())
    fixture = json.loads((root / "scripts/fixtures/mainline-feature-acceptance.json").read_text())
    args.cache.mkdir(parents=True, exist_ok=True)
    receipts = []
    indexes = {}
    identities = {a["id"]: a for a in data["archives"]}

    def api(endpoint):
        raw = subprocess.run(["gh", "api", "repos/" + data["repository"] + "/" + endpoint], check=True, capture_output=True).stdout
        return json.loads(raw)

    # Each unique immutable ZIP is downloaded once. A retained cache is reusable.
    for item in data["archives"]:
        aid = item["id"]
        directory = args.cache / str(aid)
        directory.mkdir(exist_ok=True)
        identity_path = directory / "identity.json"
        original = directory / "original.zip"
        if identity_path.exists():
            identity = json.loads(identity_path.read_text())
        else:
            identity = {
                "artifact": api("actions/artifacts/" + str(aid)),
                "run": api("actions/runs/" + str(item["runId"]) + "/attempts/" + str(item["runAttempt"])),
                "job": api("actions/jobs/" + str(item["jobId"])),
            }
            promoter.verify_archive_identity(data, item, identity["artifact"], identity["run"], identity["job"])
            write_new(identity_path, identity)
        promoter.verify_archive_identity(data, item, identity["artifact"], identity["run"], identity["job"])
        if not original.exists():
            partial = directory / "original.zip.partial"
            with partial.open("xb") as stream:
                subprocess.run(["gh", "api", "repos/" + data["repository"] + "/actions/artifacts/" + str(aid) + "/zip"], stdout=stream, check=True)
            fail(partial.stat().st_size <= MAX_ARCHIVE_BYTES and digest(partial) == item["sha256"], "Original ZIP digest differs")
            partial.rename(original)
        fail(original.stat().st_size <= MAX_ARCHIVE_BYTES and digest(original) == item["sha256"], "Cached original ZIP digest differs")
        with zipfile.ZipFile(original) as source:
            entries = source.infolist()
            fail(len(entries) <= 20000 and sum(e.file_size for e in entries) <= MAX_ARCHIVE_BYTES, "Archive exceeds extraction bound")
            names = set()
            for entry in entries:
                name = entry.filename.rstrip("/")
                promoter.reference({"archiveId": aid, "path": name}, set(identities))
                fail(name not in names and ((entry.external_attr >> 16) & 0o170000) != 0o120000, "Duplicate or symlink original entry")
                names.add(name)
            indexes[aid] = {e.filename for e in entries if not e.is_dir()}
        (directory / "files").mkdir(exist_ok=True)

    def member(reference, expected=None):
        fail(reference is not None, "Missing pending original reference")
        aid = reference["archiveId"]
        path = reference["path"]
        names = indexes[aid]
        if path not in names:
            # Workflow upload LCA can differ. Resolve only an exact unique suffix.
            suffix = expected or path
            matches = [n for n in names if n == suffix or n.endswith("/" + suffix)]
            fail(len(matches) == 1, "Missing or ambiguous original member: " + str(aid) + "/" + path)
            path = matches[0]
            reference["path"] = path
        promoter.reference(reference, set(identities))
        destination = args.cache / str(aid) / "files" / path
        fail(not destination.is_symlink(), "Aliased extracted member")
        destination.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(args.cache / str(aid) / "original.zip") as archive:
            h = hashlib.sha256()
            if destination.exists():
                with archive.open(path) as stream:
                    for block in iter(lambda: stream.read(1024 * 1024), b""):
                        h.update(block)
                fail(digest(destination) == h.hexdigest(), "Existing extraction differs")
            else:
                with archive.open(path) as stream, destination.open("xb") as target:
                    for block in iter(lambda: stream.read(1024 * 1024), b""):
                        h.update(block)
                        target.write(block)
        receipts.append({"archiveId": aid, "path": path, "sha256": h.hexdigest(), "bytes": destination.stat().st_size})
        return destination

    for row in data["families"]:
        if row["production"] is None:
            continue  # Pending producer remains explicit; never invent an archive.
        jar = member(row["production"], row["filename"])
        sha = digest(jar)
        fail(row["artifactSha256"] in (None, sha), "Retained JAR digest differs")
        row["artifactSha256"] = sha
        sums = member({"archiveId": row["production"]["archiveId"], "path": str(Path(row["production"]["path"]).parent / "SHA256SUMS")})
        fail(sums.read_text().splitlines().count(sha + "  " + row["filename"]) == 1, "Original checksum missing or ambiguous")
        if row["stageRecord"] is not None:
            stage = json.loads(member(row["stageRecord"]).read_text())
            producer = identities[row["production"]["archiveId"]]
            fail(stage["sourceSha"] == row["sourceSha"] and str(stage["sourceRunId"]) == str(producer["runId"]) and str(stage["sourceRunAttempt"]) == str(producer["runAttempt"]), "Stage source/run/attempt differs")
            matches = [a for a in stage["artifacts"] if a["id"] == row["familyId"]]
            fail(len(matches) == 1 and matches[0]["filename"] == row["filename"] and matches[0]["artifactSha256"] == sha, "Stage family identity differs")
        # Exact two mandatory shared-engine entries, not every JAR entry and not
        # current compiled output. This is the promoter's minimal admitted map.
        with zipfile.ZipFile(jar) as package:
            names = package.namelist()
            fail(len(names) == len(set(names)), "Duplicate original JAR member")
            row["engineEntries"] = {name: hashlib.sha256(package.read(name)).hexdigest() for name in REQUIRED_ENGINE_ENTRIES}
        for run in row["runs"]:
            if run["summary"] is None:
                continue  # No unexecuted pair becomes a PASS.
            for key in ("summary", "profile", "result", "sourceReceipt"):
                if run[key] is not None:
                    member(run[key])
    # Authentic composition inputs stay separate, with their real job outcomes.
    for selected in fixture["clients"]:
        aid = selected["artifactId"]
        fail(aid in identities, "Missing pinned mainline archive")
        for path in (selected["summaryPath"], "ci-verification/artifact-source.json", "ci-verification/source-run.json", "ci-verification/source-jobs.json"):
            reference = {"archiveId": aid, "path": path}
            member(reference)
            fail(reference["path"] == path, "Reviewed feature fixture path differs")
    write_new(args.output, data)
    unique = {(r["archiveId"], r["path"]): r for r in receipts}
    write_new(args.member_receipt, {
        "engineEntryFilter": list(REQUIRED_ENGINE_ENTRIES),
        "members": list(unique.values()),
        "archives": [{"id": a["id"], "sha256": a["sha256"]} for a in data["archives"]],
        "pendingRuntimePairs": [{"familyId": r["familyId"], "target": p["target"]} for r in data["families"] for p in r["runs"] if p["summary"] is None],
        "cache": str(args.cache.resolve()),
    })


if __name__ == "__main__":
    main()
