#!/usr/bin/env python3
"""Prepare the exact pinned Rhino source publication for the shared Java17 runtime."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def prepare(archive, manifest_path, hunks_path, output):
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    patch = json.loads(hunks_path.read_text(encoding="utf-8"))
    raw = archive.read_bytes()
    if digest(raw) != patch["source_archive_sha256"]:
        raise ValueError("Rhino source publication does not match the pinned archive")
    operations = {entry["path"]: entry for entry in patch["files"]}
    expected = {entry["zip_path"]: entry for entry in manifest["source_closure"]}
    produced = set()
    with zipfile.ZipFile(archive) as sources:
        for name, record in expected.items():
            blob = sources.read(name)
            if digest(blob) != record["source_sha256"]:
                raise ValueError("Unexpected Rhino source preimage: " + name)
            text = blob.decode("utf-8")
            for hunk in operations.get(name, {}).get("hunks", []):
                if text.count(hunk["before"]) != 1:
                    raise ValueError("Rhino source hunk must match exactly once: " + name)
                text = text.replace(hunk["before"], hunk["after"], 1)
            patched = text.encode("utf-8")
            if digest(patched) != record["patched_sha256"]:
                raise ValueError("Unexpected Rhino source postimage: " + name)
            destination = output / "java" / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(patched)
            produced.add(name)
        for name, operation in operations.items():
            if operation["old_path"] is not None:
                continue
            if name in produced or len(operation["hunks"]) != 1:
                raise ValueError("Invalid added Rhino support source")
            blob = operation["hunks"][0]["after"].encode("utf-8")
            destination = output / "java" / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(blob)
            produced.add(name)
        if len(produced) != manifest["produced_source_count"]:
            raise ValueError("Rhino generated source count differs from its pinned closure")
        bundle = "dev/latvian/mods/rhino/resources/Messages.properties"
        record = next(entry for entry in manifest["resources"] if entry["zip_path"] == bundle)
        blob = sources.read(bundle)
        if digest(blob) != record["sha256"]:
            raise ValueError("Rhino runtime message bundle differs")
        destination = output / "resources" / bundle
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(blob)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("hunks", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    prepare(args.archive, args.manifest, args.hunks, args.output)


if __name__ == "__main__":
    main()
