#!/usr/bin/env python3
"""Test the real CommonMark preparer ownership guards using authentic source inputs.

Run remotely after the normal Gradle proof; this creates only bounded temporary
source directories and removes its own TemporaryDirectory at exit.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile


def tree_snapshot(path):
    return {p.relative_to(path).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in path.rglob("*") if p.is_file()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["source-root", "source-core", "source-tables"]:
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args()
    root = args.source_root.resolve()
    patches = root / "runtime-commonmark/patches"
    command = ["python3", "-B", str(root / "scripts/prepare-commonmark-sources.py"),
               str(args.source_core.resolve()), str(args.source_tables.resolve()),
               str(patches / "commonmark-source-manifest.json"),
               str(patches / "commonmark-java8-hunks.json")]
    manifest = json.loads((patches / "commonmark-source-manifest.json").read_text(encoding="utf-8"))
    marker_text = "OpenAllay CommonMark generated source output\n"
    with tempfile.TemporaryDirectory(prefix="commonmark-provenance-") as temp:
        scope = Path(temp)
        def invoke(output, success):
            result = subprocess.run(command + [str(output)], stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT)
            if (result.returncode == 0) != success:
                raise AssertionError(result.stdout.decode("utf-8", errors="replace"))
        output = scope / "gradle-precreated-empty"
        output.mkdir()
        invoke(output, True)
        marker = output / ".commonmark-generated-sources"
        assert marker.read_text(encoding="utf-8") == marker_text
        snapshot = tree_snapshot(output)
        assert len(list((output / "java").rglob("*.java"))) == manifest["produced_source_count"]
        invoke(output, True)
        assert tree_snapshot(output) == snapshot
        print("empty-precreated-adoption=PASS")
        print("owned-marked-rerun=PASS")

        output = scope / "unmarked-nonempty"
        output.mkdir()
        (output / "preserve.txt").write_bytes(b"unique non-generated evidence")
        before = tree_snapshot(output)
        invoke(output, False)
        assert tree_snapshot(output) == before
        print("unmarked-nonempty-preserved=PASS")

        target = scope / "symlink-target"
        target.mkdir()
        output = scope / "symlink-output"
        output.symlink_to(target, target_is_directory=True)
        invoke(output, False)
        assert output.is_symlink() and not list(target.iterdir())
        print("empty-symlink-rejected=PASS")
        (target / ".commonmark-generated-sources").write_text(marker_text, encoding="utf-8")
        (target / "preserve.txt").write_bytes(b"unique symlink target evidence")
        before = tree_snapshot(target)
        invoke(output, False)
        assert output.is_symlink() and tree_snapshot(target) == before
        print("marked-symlink-rejected-preserved=PASS")

        output = scope / "foreign-marker"
        output.mkdir()
        (output / ".commonmark-generated-sources").write_text("Foreign output owner\n", encoding="utf-8")
        (output / "preserve.txt").write_bytes(b"unique foreign-owner evidence")
        before = tree_snapshot(output)
        invoke(output, False)
        assert tree_snapshot(output) == before
        print("foreign-marker-rejected-preserved=PASS")
    print("COMMONMARK_PROVENANCE_GUARDS_PASS")


if __name__ == "__main__":
    main()
