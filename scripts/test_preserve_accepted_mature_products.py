import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("preserve", Path(__file__).with_name("preserve-accepted-mature-products.py"))
module = importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(module)

class OriginalPreservationTests(unittest.TestCase):
    def test_preserves_hashes_and_rejects_changed_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            product = {"artifact": 1, "run": 2, "source": "a"*40, "loader": "forge", "target": "1.19.2", "proof": "original"}
            blob = b"accepted original bytes"; product["sha256"] = hashlib.sha256(blob).hexdigest()
            source = root / "build/original-product-imports/1/openallay-forge-1.19.2-0.4.2.jar"
            source.parent.mkdir(parents=True); source.write_bytes(blob)
            with patch.object(module, "PRODUCTS", [product]): output = module.preserve(root)
            preserved = next(output.rglob("*.jar")); self.assertEqual(blob, preserved.read_bytes())
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / "build/original-product-imports/1/openallay-forge-1.19.2-0.4.2.jar"
            source.parent.mkdir(parents=True); source.write_bytes(b"changed")
            with patch.object(module, "PRODUCTS", [product]), self.assertRaises(ValueError): module.preserve(root)
