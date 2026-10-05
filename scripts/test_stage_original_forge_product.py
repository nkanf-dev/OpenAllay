import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import hashlib

SPEC = importlib.util.spec_from_file_location("original_stage", Path(__file__).with_name("stage-original-forge-product.py"))
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)

class OriginalProductTests(unittest.TestCase):
    def test_exact_original_copies_and_checksum_must_match(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            imported = root / "build/original-native-product"
            blob = b"original mocked native bytes"
            digest = hashlib.sha256(blob).hexdigest()
            for relative in ("build/ci-client-production/" + module.NAME, "forge/build/libs/" + module.NAME):
                path = imported / relative; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(blob)
            (imported / "build/ci-client-production/SHA256SUMS").write_text(digest + "  " + module.NAME + "\n")
            with patch.dict(module.ORIGINAL, jarSha256=digest):
                output = module.stage(root)
            self.assertEqual(blob, (output / module.NAME).read_bytes())
            self.assertTrue((root / "build/original-native-product-receipt.json").is_file())

    def test_changed_or_missing_original_bytes_are_fatal(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            imported = root / "build/original-native-product"
            for relative in ("build/ci-client-production/" + module.NAME, "forge/build/libs/" + module.NAME):
                path = imported / relative; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(b"foreign")
            (imported / "build/ci-client-production/SHA256SUMS").write_text("wrong")
            with self.assertRaises(ValueError):
                module.stage(root)
            self.assertFalse((root / "build/ci-client-production").exists())
