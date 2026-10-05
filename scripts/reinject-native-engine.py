#!/usr/bin/env python3
"""Copy a native reobfuscated archive and restore its sole compiled engine owner."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path, PurePosixPath
import tempfile
import os
import zipfile


def check(condition, message):
    if not condition:
        raise ValueError(message)


def digest(blob):
    return hashlib.sha256(blob).hexdigest()


def engine_entries(directories):
    entries = {}
    for directory in directories:
        check(directory.is_dir(), "Missing engine output: " + str(directory))
        for path in sorted(directory.rglob("*")):
            check(not path.is_symlink(), "Engine output is a symlink: " + str(path))
            if not path.is_file():
                continue
            name = path.relative_to(directory).as_posix()
            blob = path.read_bytes()
            check(name not in entries or entries[name] == blob,
                  "Conflicting engine output: " + name)
            entries[name] = blob
    check("dev/openallay/FeatureServices.class" in entries
          and "dev/openallay/guide/GuideService.class" in entries,
          "Missing compiled engine identity")
    check("META-INF/MANIFEST.MF" not in entries,
          "Engine cannot own the native manifest")
    return entries


def reinject(source, destination, directories):
    check(source.resolve() != destination.resolve(), "Native input/output paths must differ")
    engine = engine_entries(directories)
    destination.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=destination.name + ".", suffix=".tmp",
                                            dir=destination.parent)
    os.close(descriptor)
    temporary = Path(temporary)
    changed = []
    try:
        with zipfile.ZipFile(source) as native, zipfile.ZipFile(temporary, "w") as final:
            counts = Counter(native.namelist())
            check(all(count == 1 for count in counts.values()), "Duplicate native entries")
            final.comment = native.comment
            for info in native.infolist():
                name = info.filename
                check(not name.startswith("/") and ".." not in PurePosixPath(name).parts,
                      "Unsafe native entry: " + name)
                original = native.read(info)
                replacement = engine.get(name, original)
                if replacement != original:
                    changed.append({"entry": name, "beforeSha256": digest(original),
                                    "afterSha256": digest(replacement)})
                final.writestr(info, replacement)
            for name in sorted(set(engine) - set(counts)):
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                final.writestr(info, engine[name])
                changed.append({"entry": name, "beforeSha256": None,
                                "afterSha256": digest(engine[name])})
        # Independently verify every member, not just the corrected class.
        with zipfile.ZipFile(source) as native, zipfile.ZipFile(temporary) as final:
            check(len(final.namelist()) == len(set(final.namelist())), "Duplicate final entries")
            check(set(final.namelist()) == set(native.namelist()) | set(engine),
                  "Native entries added or lost outside the engine owner")
            for name in native.namelist():
                expected = engine.get(name, native.read(name))
                check(final.read(name) == expected, "Final archive differs: " + name)
            for name, blob in engine.items():
                check(final.read(name) == blob, "Engine bytes differ: " + name)
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)
    return {"nativeInput": str(source), "nativeInputSha256": digest(source.read_bytes()),
            "productionOutput": str(destination),
            "productionOutputSha256": digest(destination.read_bytes()),
            "engineOutputDirectories": [str(directory.resolve()) for directory in directories],
            "engineEntries": len(engine), "changedEngineEntries": len(changed),
            "changedEngineEntriesManifest": changed, "nonEngineEntryBytes": "identical"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--engine-output", required=True, action="append", type=Path)
    parser.add_argument("--receipt", required=True, type=Path)
    args = parser.parse_args()
    result = reinject(args.input, args.output, args.engine_output)
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
