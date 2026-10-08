#!/usr/bin/env python3
"""Exact cached publication projection regressions; no JVM/native database run."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
import zipfile
ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("sqlite_runtime_role", ROOT / "scripts/sqlite-runtime-role.py")
helper = importlib.util.module_from_spec(spec); spec.loader.exec_module(helper)
ARCHIVE = Path(os.environ["SQLITE_PINNED_ARCHIVE"])
class ProjectionTest(unittest.TestCase):
    def test_exact_publication_preserves_all_runtime_bytes(self):
        entries, receipt = helper.project_sqlite_runtime(ARCHIVE, helper.COORDINATE)
        self.assertEqual(5, len(receipt["excludedEntries"])); self.assertEqual(125, receipt["preservedBaseClasses"])
        self.assertEqual(24, receipt["preservedNativePayloads"]); self.assertTrue(helper.audit_projected_entries(entries, receipt))
        with zipfile.ZipFile(ARCHIVE) as original:
            for name, blob in entries.items(): self.assertEqual(original.read(name), blob, name)
        self.assertEqual(b"org.sqlite.JDBC", entries["META-INF/services/java.sql.Driver"])
        self.assertFalse(any(name.startswith("META-INF/versions/") for name in entries))
        self.assertNotIn("META-INF/native-image/org.xerial/sqlite-jdbc/native-image.properties", entries)
    def test_wrong_coordinate_fail_closed(self):
        with self.assertRaisesRegex(ValueError, "coordinate"): helper.project_sqlite_runtime(ARCHIVE, "org.xerial:sqlite-jdbc:other")
    def test_unknown_multi_release_entry_fail_closed(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "unknown.jar"
            with zipfile.ZipFile(ARCHIVE) as source, zipfile.ZipFile(path, "w") as dest:
                for info in source.infolist(): dest.writestr(info, source.read(info.filename))
                dest.writestr("META-INF/versions/11/new.class", b"not-real")
            with self.assertRaisesRegex(ValueError, "multi-release"): helper.project_sqlite_runtime(path, helper.COORDINATE)
    def test_changed_projected_native_or_class_fail_closed(self):
        entries, receipt = helper.project_sqlite_runtime(ARCHIVE, helper.COORDINATE)
        native = next(name for name in entries if name.startswith("org/sqlite/native/"))
        entries[native] += b"changed"
        with self.assertRaisesRegex(ValueError, "changed"): helper.audit_projected_entries(entries, receipt)
if __name__ == "__main__": unittest.main()
