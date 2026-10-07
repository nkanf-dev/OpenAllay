#!/usr/bin/env python3
"""Exact-input canonical Rhino preparation regressions; no Java compilation."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("rhino_prepare", ROOT / "scripts/prepare-rhino-sources.py")
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)
ARCHIVE = Path(os.environ["RHINO_SOURCE_ARCHIVE"])

class RhinoPreparationTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="rhino-preparation-test-")
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        patches = ROOT / "runtime-rhino/patches"
        self.manifest = json.loads((patches / "rhino-source-manifest.json").read_text())
        self.hunks = json.loads((patches / "rhino-java17-hunks.json").read_text())
        self.patch = (patches / "rhino-java17.patch").read_bytes()
    def run_prepare(self, archive=ARCHIVE):
        manifest = self.base / "manifest.json"; manifest.write_text(json.dumps(self.manifest))
        hunks = self.base / "hunks.json"; hunks.write_text(json.dumps(self.hunks))
        (self.base / self.manifest["patch"]["filename"]).write_bytes(self.patch)
        prepare.prepare(archive, manifest, hunks, self.base / "generated")
    def test_exact_closure_resources_and_postimages(self):
        self.run_prepare()
        produced = list((self.base / "generated/java").rglob("*.java"))
        self.assertEqual(277, len(produced))
        for entry in self.manifest["source_closure"]:
            actual = self.base / "generated/java" / entry["zip_path"]
            self.assertEqual(entry["patched_sha256"], prepare.digest(actual.read_bytes()))
        for entry in self.manifest["patch"]["added_sources"]:
            self.assertEqual(entry["sha256"], prepare.digest((self.base / "generated/java" / entry["path"]).read_bytes()))
        bundle = next(x for x in self.manifest["resources"] if x["zip_path"].endswith("Messages.properties"))
        self.assertEqual(bundle["sha256"], prepare.digest((self.base / "generated/resources" / bundle["zip_path"]).read_bytes()))
        self.assertEqual([bundle["zip_path"]], [str(p.relative_to(self.base / "generated/resources")) for p in (self.base / "generated/resources").rglob("*") if p.is_file()])
        self.assertIn("LICENSE-MPL-2.0.txt", (ROOT / "runtime-rhino/build.gradle").read_text())
        self.assertIn("sourcesJar", (ROOT / "runtime-rhino/build.gradle").read_text())
    def test_possible_nullable_source_fidelity(self):
        self.run_prepare()
        text = (self.base / "generated/java/dev/latvian/mods/rhino/util/Possible.java").read_text()
        self.assertIn("import org.jetbrains.annotations.Nullable;", text)
        self.assertIn("@Nullable\n\tprivate final Object value;", text)
        self.assertIn("Possible(@Nullable Object value)", text)
        self.assertIn("@Nullable\n\tpublic Object value()", text)
        self.assertIn("of(@Nullable T o)", text)
    def test_archive_bytes(self):
        bad = self.base / "bad.jar"; bad.write_bytes(ARCHIVE.read_bytes() + b"tamper")
        with self.assertRaisesRegex(ValueError, "pinned archive"): self.run_prepare(bad)
    def test_manifest_archive_identity(self):
        self.manifest["source_archive"]["bytes"] += 1
        with self.assertRaisesRegex(ValueError, "source manifest"): self.run_prepare()
    def test_published_patch_identity(self):
        self.patch += b"tamper"
        with self.assertRaisesRegex(ValueError, "published source patch"): self.run_prepare()
    def test_hunk_patch_identity(self):
        self.hunks["patch_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "patch identity"): self.run_prepare()
    def test_duplicate_operation(self):
        self.hunks["files"].append(copy.deepcopy(self.hunks["files"][0]))
        with self.assertRaisesRegex(ValueError, "Duplicate"): self.run_prepare()
    def test_unknown_operation(self):
        self.hunks["files"][0]["path"] = "unknown.java"
        with self.assertRaisesRegex(ValueError, "outside the pinned closure"): self.run_prepare()
    def test_hunk_uniqueness(self):
        self.hunks["files"][0]["hunks"][0]["before"] = "absent-preimage-marker"
        with self.assertRaisesRegex(ValueError, "exactly once"): self.run_prepare()
    def test_source_preimage(self):
        self.manifest["source_closure"][0]["source_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "source preimage"): self.run_prepare()
    def test_source_postimage(self):
        self.manifest["source_closure"][0]["patched_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "source postimage"): self.run_prepare()
    def test_added_postimage(self):
        self.manifest["patch"]["added_sources"][0]["sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "added Rhino source postimage"): self.run_prepare()
    def test_resource_identity(self):
        next(x for x in self.manifest["resources"] if x["zip_path"].endswith("Messages.properties"))["sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "message bundle"): self.run_prepare()

if __name__ == "__main__": unittest.main()
