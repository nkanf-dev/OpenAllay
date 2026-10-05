import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


WORKFLOW = load("client_workflow", "run-ci-game-workflow.py")
EVIDENCE = load("client_evidence", "compact-ci-game-evidence.py")


class ClientWorkflowGlueTest(unittest.TestCase):
    def test_exact_staged_jar_and_official_runtime_invocation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            staged = root / "build/ci-client-production"
            staged.mkdir(parents=True)
            jar = staged / "openallay-fabric-26.2-0.4.1.jar"
            jar.write_bytes(b"synthetic artifact")
            sha = hashlib.sha256(jar.read_bytes()).hexdigest()
            (staged / "SHA256SUMS").write_text(sha + "  " + jar.name + "\n")
            runtime = root / "build/e2e/runtime/26.2/minecraft"
            (runtime / ".provision").mkdir(parents=True)
            (runtime / ".provision/fabric-runtime.json").write_text(json.dumps({
                "loader": "fabric", "minecraft": "26.2", "minecraftRoot": str(runtime), "fabricApi": str(runtime / "libraries/api.jar")}))
            with mock.patch.object(WORKFLOW.subprocess, "run", return_value=mock.Mock(returncode=0)) as command:
                self.assertEqual(WORKFLOW.run("fabric", Path("/fixture/java"), "ci-run", root), 0)
                arguments = command.call_args.args[0]
                self.assertEqual(arguments[arguments.index("--artifact-sha256") + 1], sha)
                self.assertIn(str(jar), arguments)
                self.assertIn("--fabric-api", arguments)
            jar.write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "changed"):
                WORKFLOW.run("fabric", Path("/fixture/java"), "ci-run", root)

    def test_batch_orchestration_logs_are_retained_by_exact_inventory(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            batch = root / "build/e2e/ci-client/fabric/ci-run"
            batch.mkdir(parents=True)
            log = batch / "builder-acceptance.prepare.log"
            log.write_bytes(b"prepare failure")
            (batch / "summary.json").write_text(json.dumps({"scenarios": [], "diagnostics": {"logFiles": [str(log)]}}))
            scripts = root / "scripts"
            scripts.mkdir()
            (scripts / "prepare-ci-diagnostics.py").write_bytes(Path(__file__).with_name("prepare-ci-diagnostics.py").read_bytes())
            output, passed = EVIDENCE.compact("fabric", "ci-run", root)
            self.assertTrue(passed)
            self.assertEqual((output / "run-0" / log.name).read_bytes(), log.read_bytes())
            self.assertTrue(log.is_file())

    def test_evidence_exact_copy_hash_and_original_preservation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            path = root / "build/e2e/run/game/config/openallay/e2e/proof.json"
            path.parent.mkdir(parents=True)
            path.write_bytes(b"native fixture proof")
            record = {"path": str(path), "sha256": EVIDENCE.digest(path), "sizeBytes": path.stat().st_size}
            output = root / "build/e2e/compact"
            EVIDENCE.exact_copy(record, output, root, [0])
            copied = output / "retained-exact/run/game/config/openallay/e2e/proof.json"
            self.assertEqual(copied.read_bytes(), path.read_bytes())
            self.assertTrue(path.is_file())
            path.write_bytes(b"altered")
            with self.assertRaises(ValueError):
                EVIDENCE.exact_copy(record, output, root, [0])

    def test_source_game_world_and_external_evidence_refused(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            for name in ("build/e2e/run/game/saves/world/level.dat", "build/e2e/run/game/config/openallay/models.json", "outside.txt"):
                with self.subTest(name=name):
                    path = root / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(b"fixture")
                    record = {"path": str(path), "sha256": EVIDENCE.digest(path), "sizeBytes": path.stat().st_size}
                    with self.assertRaises(ValueError):
                        EVIDENCE.exact_copy(record, root / "build/e2e/output", root, [0])

    def test_selected_native_game_log_retained_on_compression_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            path = root / "build/e2e/run/game/logs/latest.log"
            path.parent.mkdir(parents=True)
            path.write_bytes(b"native error")
            record = {"path": str(path), "sha256": EVIDENCE.digest(path), "sizeBytes": path.stat().st_size}
            output = root / "build/e2e/compact"
            EVIDENCE.exact_copy(record, output, root, [0])
            self.assertEqual((output / "retained-exact/run/game/logs/latest.log").read_bytes(), b"native error")


if __name__ == "__main__":
    unittest.main()
