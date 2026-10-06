#!/usr/bin/env python3
"""Offline boundary tests; no Java, game, Gradle, network or official binary download."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

PACKET = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("stock1122", PACKET / "stock-forge1122-prerequisite.py")
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)

class Boundaries(unittest.TestCase):
    def setUp(self):
        self.metadata = [json.loads((PACKET / name).read_text()) for name in
                         ("install_profile.json", "version.json", "minecraft-1.12.2.json")]

    def test_official_identity(self):
        self.assertIn("org.ow2.asm:asm-debug-all:5.2", probe.validate_metadata(*self.metadata))
        self.assertEqual([], probe.FLAGS)

    def test_no_new_asm_substitution(self):
        changed = copy.deepcopy(self.metadata)
        changed[1]["libraries"].append({"name": "org.ow2.asm:asm:9.6"})
        with self.assertRaises(ValueError):
            probe.validate_metadata(*changed)

    def test_no_processor_alias(self):
        self.metadata[0]["processors"] = [{"jar": "fake:game:1"}]
        with self.assertRaises(ValueError):
            probe.validate_metadata(*self.metadata)

    def test_genuine_jinput_native_only_not_on_java_classpath(self):
        root = Path(__import__("os").environ.get("STOCK1122_TEST_REPO", str(PACKET.parents[1])))
        _, launch, _ = probe.load_helpers(root)
        jinput = next(lib for lib in self.metadata[2]["libraries"]
                      if lib["name"] == "net.java.jinput:jinput-platform:2.0.5")
        self.assertEqual([], probe.classpath_libraries({"libraries": [jinput]}, root, launch))
        self.assertIn("natives-linux", jinput["downloads"]["classifiers"])
        self.assertEqual("natives-linux", jinput["natives"]["linux"])

    def test_native_only_filter_preserves_remaining_metadata(self):
        original = copy.deepcopy(self.metadata[2])
        class Launch:
            @staticmethod
            def version_libraries(metadata, root, cache, allow_gradle):
                return metadata["libraries"]
        result = probe.classpath_libraries(original, PACKET, Launch())
        expected = [lib for lib in original["libraries"]
                    if not (set(lib["downloads"]) == {"classifiers"} and lib.get("natives"))]
        self.assertEqual(expected, result)
        self.assertEqual(original, self.metadata[2])

    def test_no_native_declaration_keeps_unknown_metadata_rejection(self):
        root = Path(__import__("os").environ.get("STOCK1122_TEST_REPO", str(PACKET.parents[1])))
        _, launch, _ = probe.load_helpers(root)
        unknown = {"name": "unknown:library:1", "downloads": {"classifiers": {"other": {}}}}
        with self.assertRaisesRegex(ValueError, "Unsupported installed library metadata"):
            probe.classpath_libraries({"libraries": [unknown]}, root, launch)

    def test_major61_and_mr_bytes_are_visible_and_unchanged(self):
        class Runtime:
            @staticmethod
            def require(ok, message):
                if not ok:
                    raise ValueError(message)
            @staticmethod
            def file_hash(path):
                return hashlib.sha256(path.read_bytes()).hexdigest()
        with tempfile.TemporaryDirectory(prefix="openallay1122-test-") as directory:
            jar = Path(directory) / "boundary.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("base.class", bytes.fromhex("cafebabe00000034"))
                archive.writestr("META-INF/versions/17/engine.class", bytes.fromhex("cafebabe0000003d"))
            before = jar.read_bytes()
            receipt = probe.inspect_classpath([("boundary:synthetic:1", jar)], set(), Runtime())[0]
            self.assertEqual({"52": 1, "61": 1}, receipt["classMajorCounts"])
            self.assertEqual(1, receipt["asm5ScanAboveJava8"])
            self.assertEqual(61, receipt["multiReleaseClasses"][0]["major"])
            self.assertEqual(before, jar.read_bytes())

if __name__ == "__main__":
    unittest.main()
