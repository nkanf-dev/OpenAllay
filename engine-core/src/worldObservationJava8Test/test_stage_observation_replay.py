#!/usr/bin/env python3
"""Stdlib regressions for strict observation replay metadata and custody helpers."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("observation_replay", HERE / "stage-observation-replay.py")
replay = importlib.util.module_from_spec(spec)
spec.loader.exec_module(replay)

class ReplayTests(unittest.TestCase):
    def test_metadata_and_api_patch_are_authenticated(self):
        data = (HERE / replay.METADATA_NAME).read_bytes()
        replay.require_hash(data, replay.METADATA_SHA256, "metadata")
        metadata = json.loads(data)
        self.assertEqual(46, len(metadata["sources"]))
        self.assertEqual(5, len(metadata["owners"]))
        self.assertEqual(2, sum(bool(owner["nestedRecords"]) for owner in metadata["owners"]))
        self.assertEqual(27, sum(1 + len(owner["nestedRecords"]) for owner in metadata["owners"]))
        replay.require_hash((HERE / metadata["apiPatch"]).read_bytes(), metadata["apiPatchSha256"], "api")

    def test_hash_mismatch_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "SHA256 differs"):
            replay.require_hash(b"changed", replay.sha(b"original"), "source")

    def test_paths_cannot_escape(self):
        for name in ["../source.java", "/tmp/source.java", "x/../source.java", "", "x//source.java"]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                replay.logical_path(name)
        self.assertEqual("engine-core/src/main/java/Owner.java", replay.logical_path("engine-core/src/main/java/Owner.java"))

    def test_cleanup_is_exact_and_blank_only(self):
        original = b"public class Owner {\n    \n}\n"
        change = {"startLine": 1, "endLine": 2, "beforeHex": b"    \n".hex(), "afterHex": b"\n".hex()}
        self.assertEqual(b"public class Owner {\n\n}\n", replay.checked_blank_lines(original, [change]))
        with self.assertRaisesRegex(ValueError, "preimage"):
            replay.checked_blank_lines(original.replace(b"    \n", b" \n"), [change])
        with self.assertRaisesRegex(ValueError, "nonblank"):
            replay.checked_blank_lines(original, [dict(change, afterHex=b"unsafe();\n".hex())])
        with self.assertRaisesRegex(ValueError, "Overlapping"):
            replay.checked_blank_lines(original, [change, change])

    def test_requests_use_exact_authenticated_stage_inputs(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            owner = {"path": "Outer.java", "record": "Outer", "nestedRecords": ["Outer.Inner"],
                     "originalSha256": replay.sha(b"original"), "stage1Sha256": replay.sha(b"authentic nested")}
            (root / "Outer.java").write_bytes(b"original")
            nested = replay.request_rows([owner], root, True)
            self.assertTrue(nested.endswith("\tOuter.Inner\n"))
            with self.assertRaisesRegex(ValueError, "SHA256"):
                replay.request_rows([owner], root, False)
            (root / "Outer.java").write_bytes(b"authentic nested")
            outer = replay.request_rows([owner], root, False)
            self.assertTrue(outer.endswith("\tOuter\n"))
            self.assertIn(owner["stage1Sha256"], outer)

    def test_git_revision_alias_is_rejected(self):
        with patch.object(replay, "run", return_value=b"different-commit\n"):
            with self.assertRaisesRegex(ValueError, "exact recorded commit"):
                replay.original_sources(Path("."), "recorded-commit", [])

    def test_api_patch_applies_inside_checkout_build_without_prefix_filter(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder) / "repo"
            root.mkdir()
            replay.run(["git", "init", "-q", str(root)])
            candidate = root / "build" / "proof" / "stage2" / "candidate"
            source = candidate / "engine-core" / "Owner.java"
            source.parent.mkdir(parents=True)
            source.write_bytes(b"old\n")
            api_patch = Path(folder) / "api.patch"
            api_patch.write_bytes(b"diff --git a/engine-core/Owner.java b/engine-core/Owner.java\n"
                                  b"--- a/engine-core/Owner.java\n+++ b/engine-core/Owner.java\n"
                                  b"@@ -1 +1 @@\n-old\n+new\n")
            # Reproduce the exact original bug: exit0 while every path is skipped.
            replay.run(["git", "apply", "--no-index", str(api_patch)], cwd=candidate)
            self.assertEqual(b"old\n", source.read_bytes())
            replay.apply_api_patch(candidate, api_patch)
            self.assertEqual(b"new\n", source.read_bytes())
            self.assertFalse((root / "engine-core" / "Owner.java").exists())

    def test_api_patch_missing_preimage_fails_and_preserves_source(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder) / "repo"
            root.mkdir()
            replay.run(["git", "init", "-q", str(root)])
            candidate = root / "build" / "proof" / "candidate"
            source = candidate / "engine-core" / "Owner.java"
            source.parent.mkdir(parents=True)
            source.write_bytes(b"unchanged\n")
            api_patch = Path(folder) / "api.patch"
            api_patch.write_bytes(b"diff --git a/engine-core/Owner.java b/engine-core/Owner.java\n"
                                  b"--- a/engine-core/Owner.java\n+++ b/engine-core/Owner.java\n"
                                  b"@@ -1 +1 @@\n-absent\n+unsafe\n")
            with self.assertRaisesRegex(RuntimeError, "Command failed"):
                replay.apply_api_patch(candidate, api_patch)
            self.assertEqual(b"unchanged\n", source.read_bytes())

    def test_existing_output_is_rejected_without_converter(self):
        with tempfile.TemporaryDirectory() as folder:
            args = type("Args", (), {"source_root": Path(folder), "output": Path(folder),
                "original_source": json.loads((HERE / replay.METADATA_NAME).read_bytes())["originalSource"]})()
            with patch.object(replay, "run", side_effect=AssertionError("must not run")):
                with self.assertRaisesRegex(ValueError, "Fresh detached"):
                    replay.stage(args)

if __name__ == "__main__":
    unittest.main()
