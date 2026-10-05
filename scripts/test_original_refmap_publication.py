"""Original-byte refmap severity checks; no game or compiler execution."""
from importlib.util import module_from_spec, spec_from_file_location
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
SPEC = spec_from_file_location("intervals", Path(__file__).with_name("verify-minecraft-binary-intervals.py"))
intervals = module_from_spec(SPEC)
SPEC.loader.exec_module(intervals)
class OriginalRefmapSeverity(unittest.TestCase):
    def test_unproven_missing_refmap_stays_fatal_and_proven_identical_bytes_are_reported(self):
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "original.jar"
            with zipfile.ZipFile(jar, "w") as output:
                output.writestr("fabric.mod.json", json.dumps({"depends":{"fabricloader":">=0.18.2","java":">=17"}}))
                output.writestr("test.mixins.json", json.dumps({"package":"example", "client":["Binding"], "refmap":"missing.json"}))
                output.writestr("example/Binding.class", b"original fixture bytes")
            family = {"loader":"fabric", "buildTarget":"1.20.1"}
            original_module = intervals.module
            class Build:
                @staticmethod
                def metadata(*args): pass
            class Target:
                @staticmethod
                def read_profile(*args): return {"fabric_loader_version":"0.18.2", "java_version":"17"}
            def module(name, root):
                return Build if name == "build-minecraft-artifacts.py" else Target
            with patch.object(intervals, "module", side_effect=module):
                with self.assertRaisesRegex(ValueError, "refmap missing"):
                    intervals.package_guard(jar, family, "0.4.2")
                sha = hashlib.sha256(jar.read_bytes()).hexdigest()
                proof = intervals.package_guard(jar, family, "0.4.2", accepted_original_runtime_sha=sha)
                self.assertEqual("nonfatal", proof["findings"][0]["severity"])
                with self.assertRaisesRegex(ValueError, "bytes differ"):
                    intervals.package_guard(jar, family, "0.4.2", accepted_original_runtime_sha="0" * 64)
if __name__ == "__main__": unittest.main()
