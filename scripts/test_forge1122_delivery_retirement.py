"""Finite obsolete Java17 delivery retirement and shared Forge16 custody checks."""
import ast
import hashlib
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
PROVIDER_SHA256 = "181484f599c9e4121c787507eec670de60aa609811cf7834a7dc4e02efc32cd0"
RETIRED_DRIVERS = (
    "boot-forge1122-component.py", "run-forge1122-component.py",
    "build-forge1122-component.py", "refresh-forge1122-component-native.py",
    "refresh-forge1122-component-engine.py", "repair-forge1122-private-mixin.py",
    "bundle-forge1122-builder.py", "build-forge1122-changed-engine.py",
)


class Forge1122DeliveryRetirementTest(unittest.TestCase):
    def test_dedicated_delivery_trees_and_drivers_are_absent(self):
        for path in ("native-builds/forge1122-component", "scripts/forge1122-runtime-prerequisite"):
            self.assertFalse((ROOT/path).exists(), path)
        for filename in RETIRED_DRIVERS:
            self.assertFalse((ROOT/"scripts"/filename).exists(), filename)

    def test_shared_builder_provider_has_exact_original_bytes(self):
        path = ROOT/"distribution/builder-candidate-provider.json"
        self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), PROVIDER_SHA256)
        pin = json.loads(path.read_text())
        lock = json.loads((ROOT/"distribution/extensions.lock.json").read_text())
        self.assertEqual(pin["extensionSource"], lock["source"]["revision"])
        self.assertEqual(pin["jarSha256"], "bf8cfff9b82f84914aa1c84173d531f2256f537215a66a8d2057adc504632345")
        source = (ROOT/"scripts/prepare-legacy-forge-release.py").read_text()
        self.assertEqual(source.count("distribution/builder-candidate-provider.json"), 2)
        self.assertNotIn("native-builds/forge1122-component", source)

    def test_obsolete_workflow_modes_jobs_and_paths_are_removed(self):
        source = (ROOT/".github/workflows/native-adaptation.yml").read_text()
        modes = re.search(r"options: \[(.*?)\]", source).group(1).split(", ")
        self.assertEqual(len(modes), len(set(modes)))
        for mode in modes:
            self.assertFalse(mode.startswith(("stock1122", "component1122")), mode)
            self.assertNotIn(mode, ("engine1122", "builder1122", "release-prep12"))
        for path in ("forge1122-runtime-prerequisite", "forge1122-component", "forge1122-builder-candidate"):
            self.assertNotIn(path, source)
        for filename in RETIRED_DRIVERS:
            self.assertNotIn(filename, source)
        for mode in ("tooling", "native", "pack", "engine", "census1122", "native1122", "reobf1122", "release-prep16"):
            self.assertIn(mode, modes)
        self.assertIn("scripts/build-forge16165-native.py", source)
        self.assertIn("scripts/run-forge1122-native-census.py", source)
        self.assertIn("scripts/run-forge1122-reobf-only.py", source)
        self.assertIn("--target '1.16.5'", source)

    def test_no_surviving_executable_imports_deleted_driver(self):
        for path in (ROOT/"scripts").rglob("*.py"):
            if path == Path(__file__):
                continue
            tree = ast.parse(path.read_text(), filename=str(path))
            constants = [node.value for node in ast.walk(tree)
                         if isinstance(node, ast.Constant) and isinstance(node.value, str)]
            for value in constants:
                for deleted in RETIRED_DRIVERS:
                    self.assertNotIn(deleted, value, str(path))
                self.assertNotIn("forge1122-runtime-prerequisite", value, str(path))
                self.assertNotIn("native-builds/forge1122-component", value, str(path))

    def test_canonical_feature_and_real_native_source_owners_remain(self):
        for path in ("engine-core/src/main/java", "common/src/targets/1.12.2",
                     "adapters/minecraft/src/targets/1.12.2", "forge/src/targets/1.12.2",
                     "native-builds/engine-only", "native-builds/forge16165",
                     "native-builds/forge1122-census", "native-builds/forge1122-reobf"):
            self.assertTrue((ROOT/path).is_dir(), path)
        for filename in ("build-forge1122-native.py", "run-forge1122-native-census.py",
                         "collect-forge1122-native-census.py", "run-forge1122-reobf-only.py"):
            self.assertTrue((ROOT/"scripts"/filename).is_file(), filename)
        catalog = json.loads((ROOT/"gradle/minecraft-artifacts.json").read_text())
        self.assertEqual(len(catalog["acceptedFamilies"]), 34)
        self.assertFalse(any(f["id"] == "forge-1.12.2" for f in catalog["acceptedFamilies"]))
        self.assertIn({"loaders":["forge"], "buildTarget":"1.12.2", "targets":["1.12.2"], "publishing":False},
                      catalog["candidateIntervals"])


if __name__ == "__main__":
    unittest.main()
