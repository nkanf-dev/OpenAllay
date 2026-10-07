#!/usr/bin/env python3
"""Prepare the exact pinned Rhino source publication for the one shared runtime source port."""
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
    if digest(raw) != manifest["source_archive"]["sha256"] or len(raw) != manifest["source_archive"]["bytes"]:
        raise ValueError("Rhino source publication does not match the source manifest")
    published_patch = hunks_path.parent / manifest["patch"]["filename"]
    patch_bytes = published_patch.read_bytes()
    if digest(patch_bytes) != manifest["patch"]["sha256"] or len(patch_bytes) != manifest["patch"]["bytes"]:
        raise ValueError("Rhino published source patch differs")
    if patch["patch_sha256"] != manifest["patch"]["sha256"]:
        raise ValueError("Rhino hunk recipe and published patch identity differ")
    operations = {entry["path"]: entry for entry in patch["files"]}
    expected = {entry["zip_path"]: entry for entry in manifest["source_closure"]}
    added = {entry["path"]: entry for entry in manifest["patch"]["added_sources"]}
    if len(operations) != len(patch["files"]) or len(expected) != len(manifest["source_closure"]) or len(added) != len(manifest["patch"]["added_sources"]):
        raise ValueError("Duplicate Rhino source path in recipe")
    if len(expected) != manifest["source_count"]:
        raise ValueError("Rhino upstream source count differs")
    for name, operation in operations.items():
        if operation["old_path"] is None:
            if name not in added or name in expected:
                raise ValueError("Unknown added Rhino support source: " + name)
        elif operation["old_path"] != name or name not in expected:
            raise ValueError("Rhino source operation is outside the pinned closure: " + name)
    for name in list(expected) + list(added):
        if "\\" in name or name.startswith("/") or any(part in ("", ".", "..") for part in name.split("/")):
            raise ValueError("Invalid Rhino source path: " + name)
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
            record = added[name]
            if digest(blob) != record["sha256"] or len(blob) != record["bytes"]:
                raise ValueError("Unexpected added Rhino source postimage: " + name)
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
