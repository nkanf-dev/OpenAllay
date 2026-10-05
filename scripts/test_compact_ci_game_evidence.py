"""Synthetic client-diagnostic loader contract tests; no runtime/game evidence."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

from minecraft_target_loaders import LOADERS

SPEC = importlib.util.spec_from_file_location("compact_ci_game_evidence", Path(__file__).with_name("compact-ci-game-evidence.py"))
COMPACTOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COMPACTOR)


class CompactLoaderContractTests(unittest.TestCase):
    def test_forge_retains_actual_loader_identity_in_owned_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            batch = root / "build/e2e/ci-client/forge/offline-contract"
            batch.mkdir(parents=True)
            (batch / "summary.json").write_text(json.dumps({"scenarios": []}))
            diagnostics = mock.Mock()
            diagnostics.prepare.return_value = {"files": [], "sourceRoot": str(batch)}
            with mock.patch.object(COMPACTOR.importlib.util, "module_from_spec", return_value=diagnostics), mock.patch.object(COMPACTOR.importlib.util, "spec_from_file_location"):
                output, passed = COMPACTOR.compact("forge", "offline-contract", root)
            self.assertTrue(passed)
            self.assertEqual(root / "build/e2e/ci-client-compact/forge/offline-contract", output)
            receipt = json.loads((output / "collection.json").read_text())
            self.assertEqual("forge", receipt["loader"])
            self.assertFalse(receipt["originalsDeleted"])
            self.assertTrue((batch / "summary.json").is_file())
            self.assertEqual(frozenset(("fabric", "forge", "neoforge")), LOADERS)

    def test_unknown_loader_is_rejected_before_any_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            for loader in ("Forge", "../forge", "not-a-loader"):
                with self.subTest(loader=loader), self.assertRaisesRegex(ValueError, "actual client loader"):
                    COMPACTOR.compact(loader, "offline-contract", root)
            self.assertFalse((root / "build").exists())


if __name__ == "__main__":
    unittest.main()
