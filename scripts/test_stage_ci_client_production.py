import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("stage_production", Path(__file__).with_name("stage-ci-client-production.py"))
stage_production = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(stage_production)

class SelectedProductionTests(unittest.TestCase):
    def prepare(self, root, target, loaders):
        (root / "gradle").mkdir()
        (root / "gradle.properties").write_text("version=0.4.2\n")
        (root / "gradle/minecraft-target-loaders.json").write_text(json.dumps({target: {
            "loaders": loaders, "nativeToolchain": "legacyForge" if "forge" in loaders else "neoForge"}}))
        for loader in loaders:
            path = root / loader / "build/libs" / ("openallay-" + loader + "-" + target + "-0.4.2.jar")
            path.parent.mkdir(parents=True)
            path.write_bytes((loader + " original bytes").encode())

    def test_single_modern_representative_stages_only_selected_actual_loader(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.prepare(root, "26.2", ["fabric", "neoforge"])
            output = stage_production.stage(root, "26.2", loader="fabric")
            self.assertEqual(["openallay-fabric-26.2-0.4.2.jar"], [p.name for p in output.glob("*.jar")])

    def test_selected_loader_cannot_alias_an_absent_loader(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.prepare(root, "1.18.2", ["forge"])
            with self.assertRaises(ValueError):
                stage_production.stage(root, "1.18.2", loader="neoforge")
