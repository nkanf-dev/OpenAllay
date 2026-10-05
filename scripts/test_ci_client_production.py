import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("stage_client", Path(__file__).with_name("stage-ci-client-production.py"))
STAGE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(STAGE)


class ClientProductionStageTest(unittest.TestCase):
    def test_same_original_jars_and_checksums(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "gradle.properties").write_text("version=0.4.1\n")
            for loader in ("fabric", "neoforge"):
                path = root / loader / "build/libs" / ("openallay-" + loader + "-26.2-0.4.1.jar")
                path.parent.mkdir(parents=True)
                path.write_bytes((loader + " fixture").encode())
            output = STAGE.stage(root)
            lines = (output / "SHA256SUMS").read_text().splitlines()
            self.assertEqual(len(lines), 2)
            for line in lines:
                digest, name = line.split("  ")
                self.assertEqual(digest, hashlib.sha256((output / name).read_bytes()).hexdigest())
            with self.assertRaisesRegex(ValueError, "already exists"):
                STAGE.stage(root)

    def test_missing_jar_refused_before_creating_stage(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "gradle.properties").write_text("version=0.4.1\n")
            with self.assertRaisesRegex(ValueError, "Missing"):
                STAGE.stage(root)
            self.assertFalse((root / "build/ci-client-production").exists())


if __name__ == "__main__":
    unittest.main()
