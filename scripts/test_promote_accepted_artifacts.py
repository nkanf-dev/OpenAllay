#!/usr/bin/env python3
"""Small runtime-free accepted-original publication contracts."""
from importlib.util import module_from_spec, spec_from_file_location
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SPEC = spec_from_file_location("accepted_publication_contract", Path(__file__).with_name("promote-accepted-artifacts.py"))
publication = module_from_spec(SPEC)
SPEC.loader.exec_module(publication)

class AcceptedOriginalContracts(unittest.TestCase):
    def test_original_class_names_and_hidden_provision_paths(self):
        for name in ("dev/openallay/FeatureServices$State.class", "build/.provision/fabric-runtime.json"):
            publication.reference({"archiveId": 1, "path": name}, {1})
        for name in ("../outside", "/absolute", "build/../outside", "build//empty"):
            with self.assertRaises(ValueError):
                publication.reference({"archiveId": 1, "path": name}, {1})

    def test_extraction_must_remain_identical_to_original_archive(self):
        with tempfile.TemporaryDirectory() as temporary:
            cache = Path(temporary).resolve()
            files = cache / "1/files"
            files.mkdir(parents=True)
            path = files / "summary.json"
            path.write_bytes(b'{"status":"PASSED"}')
            with zipfile.ZipFile(cache / "1/original.zip", "w") as archive:
                archive.write(path, "summary.json")
            ref = {"archiveId": 1, "path": "summary.json"}
            self.assertEqual(publication.original_file(cache, ref), path)
            path.write_bytes(b'{"status":"FAILED"}')
            with self.assertRaises(ValueError):
                publication.original_file(cache, ref)

    def test_duplicate_json_keys_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "selection.json"
            path.write_text('{"repository":"one/repo","repository":"other/repo"}')
            with self.assertRaises(ValueError):
                publication.selection(path)

    def test_no_existing_receipt_overwrite(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "proof.json"
            publication.write_json(path, {"original": True})
            with self.assertRaises(FileExistsError):
                publication.write_json(path, {"original": False})
            self.assertEqual(json.loads(path.read_text()), {"original": True})

    def test_archive_wrong_run_or_digest_rejected_before_job_check(self):
        archive = {"id": 1, "name": "original", "sha256": "a" * 64, "runId": 2,
                   "runAttempt": 1, "headSha": "b" * 40}
        metadata = {"id": 1, "name": "original", "expired": False,
                    "digest": "sha256:" + "c" * 64,
                    "workflow_run": {"id": 2, "head_sha": "b" * 40}}
        with self.assertRaises(ValueError):
            publication.verify_archive_identity({"repository": "owner/repo"}, archive, metadata, {}, {})
        metadata["digest"] = "sha256:" + "a" * 64
        metadata["workflow_run"]["id"] = 3
        with self.assertRaises(ValueError):
            publication.verify_archive_identity({"repository": "owner/repo"}, archive, metadata, {}, {})

if __name__ == "__main__":
    unittest.main()
