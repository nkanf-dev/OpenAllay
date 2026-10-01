"""Regression checks for mature-tokenizer nesting; no provider or game runtime."""
from importlib.util import module_from_spec, spec_from_file_location
from io import BytesIO
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = spec_from_file_location("tokenizer_packaging", ROOT / "scripts/verify-tokenizer-packaging.py")
verify = module_from_spec(spec)
spec.loader.exec_module(verify)


def archive_bytes(entries):
    stream = BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        for name, value in entries.items():
            archive.writestr(name, value)
    return stream.getvalue()


class TokenizerPackagingTest(unittest.TestCase):
    def test_registered_resources_and_version_are_required(self):
        resources = {name: "fixture " + name for name in verify.RESOURCES}
        nested = archive_bytes(resources | {"com/knuddels/jtokkit/Encodings.class": b"fixture"})
        import hashlib
        expected = {name: hashlib.sha256(value.encode()).hexdigest() for name, value in resources.items()}
        registered = "META-INF/jars/jtokkit-1.1.0.jar"
        entries = {registered: nested, verify.LICENSE: "Permission is hereby granted",
                   "fabric.mod.json": json.dumps({"jars": [{"file": registered}]})}
        with tempfile.TemporaryDirectory() as folder, patch.object(verify, "RESOURCES", expected):
            path = Path(folder) / "product.jar"
            path.write_bytes(archive_bytes(entries))
            self.assertEqual(verify.verify(path, "fabric")["registered"], registered)
            entries["fabric.mod.json"] = json.dumps({"jars": []})
            path.write_bytes(archive_bytes(entries))
            with self.assertRaisesRegex(ValueError, "not registered"):
                verify.verify(path, "fabric")
            entries["fabric.mod.json"] = json.dumps({"jars": [{"file": registered}]})
            entries[registered] = archive_bytes({"com/knuddels/jtokkit/Encodings.class": b"fixture"})
            path.write_bytes(archive_bytes(entries))
            with self.assertRaisesRegex(ValueError, "BPE resource missing"):
                verify.verify(path, "fabric")


if __name__ == "__main__":
    unittest.main()
