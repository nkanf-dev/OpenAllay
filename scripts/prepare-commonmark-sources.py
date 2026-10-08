#!/usr/bin/env python3
"""Prepare the two exact CommonMark 0.28.0 source publications for Java 8."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def safe_path(name):
    if "\\" in name or name.startswith("/") or any(p in ("", ".", "..") for p in name.split("/")):
        raise ValueError("Unsafe archive path: " + name)
    return name


def prepare(core, tables, manifest_path, hunks_path, output):
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    recipe = json.loads(hunks_path.read_text(encoding="utf-8"))
    patch = manifest_path.parent / manifest["patch"]["filename"]
    patch_bytes = patch.read_bytes()
    if len(patch_bytes) != manifest["patch"]["bytes"] or sha256(patch_bytes) != manifest["patch"]["sha256"]:
        raise ValueError("Published CommonMark patch differs")
    if recipe["patch_sha256"] != sha256(patch_bytes):
        raise ValueError("CommonMark recipe and published patch differ")
    operations = {(r["module"], r["path"]): r for r in recipe["files"]}
    closure = {(r["module"], r["path"]): r for r in manifest["source_closure"]}
    if len(operations) != len(recipe["files"]) or len(closure) != manifest["source_count"]:
        raise ValueError("Duplicate or missing CommonMark source path")
    if not set(operations).issubset(closure):
        raise ValueError("CommonMark operation outside the source closure")
    produced = {}
    for module, archive in [("core", core), ("tables", tables)]:
        pin = next(r for r in manifest["source_archives"] if r["module"] == module)
        archive_bytes = archive.read_bytes()
        if len(archive_bytes) != pin["bytes"] or sha256(archive_bytes) != pin["sha256"] or recipe["source_archive_sha256"][module] != pin["sha256"]:
            raise ValueError("CommonMark source publication differs: " + module)
        with zipfile.ZipFile(archive) as sources:
            names = sources.namelist()
            if len(names) != len(set(names)):
                raise ValueError("Duplicate upstream archive path")
            expected_java = {path for m, path in closure if m == module}
            if {p for p in names if p.endswith(".java")} != expected_java:
                raise ValueError("Upstream Java source closure differs: " + module)
            for path in sorted(expected_java):
                record = closure[(module, path)]
                blob = sources.read(path)
                if len(blob) != record["source_bytes"] or sha256(blob) != record["source_sha256"]:
                    raise ValueError("Source preimage differs: " + path)
                operation = operations.get((module, path))
                if operation and operation["remove"]:
                    if path != "module-info.java" or record["patched_sha256"] is not None:
                        raise ValueError("Only JPMS descriptors may be omitted")
                    continue
                text = blob.decode("utf-8")
                for hunk in operation["hunks"] if operation else []:
                    if text.count(hunk["before"]) != 1:
                        raise ValueError("Source hunk must match exactly once: " + path)
                    text = text.replace(hunk["before"], hunk["after"], 1)
                patched = text.encode("utf-8")
                if len(patched) != record["patched_bytes"] or sha256(patched) != record["patched_sha256"]:
                    raise ValueError("Source postimage differs: " + path)
                destination = "java/" + safe_path(path)
                if destination in produced:
                    raise ValueError("Duplicate produced path: " + destination)
                produced[destination] = patched
            for record in manifest["resources"]:
                if record["module"] != module:
                    continue
                blob = sources.read(record["path"])
                if len(blob) != record["bytes"] or sha256(blob) != record["sha256"]:
                    raise ValueError("Resource differs: " + record["path"])
                path = "resources/" + ("META-INF/licenses/commonmark/" + module + "-LICENSE.txt" if record["path"] == "META-INF/LICENSE.txt" else safe_path(record["path"]))
                if path in produced:
                    raise ValueError("Duplicate produced resource")
                produced[path] = blob
    for record in manifest["added_sources"]:
        blob = record["text"].encode("utf-8")
        path = "java/" + safe_path(record["path"])
        if path in produced or len(blob) != record["bytes"] or sha256(blob) != record["sha256"]:
            raise ValueError("Added source differs")
        produced[path] = blob
    if sum(path.endswith(".java") for path in produced) != manifest["produced_source_count"]:
        raise ValueError("Produced Java source count differs")
    # Nothing is written until every input and postimage has passed validation.
    marker = output / ".commonmark-generated-sources"
    if output.is_symlink():
        raise ValueError("Refusing a symlink source output directory")
    if output.exists():
        if not output.is_dir():
            raise ValueError("Source output must be a directory")
        if any(output.iterdir()):
            if marker.is_symlink() or not marker.is_file() or marker.read_text(encoding="utf-8") != "OpenAllay CommonMark generated source output\n":
                raise ValueError("Refusing to remove an unowned source output directory")
            shutil.rmtree(output)
    output.mkdir(parents=True, exist_ok=True)
    marker.write_text("OpenAllay CommonMark generated source output\n", encoding="utf-8")
    for path, blob in produced.items():
        destination = output / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(blob)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["core", "tables", "manifest", "hunks", "output"]:
        parser.add_argument(name, type=Path)
    args = parser.parse_args()
    prepare(args.core, args.tables, args.manifest, args.hunks, args.output)


if __name__ == "__main__":
    main()
