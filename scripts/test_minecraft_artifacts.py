"""Offline source fixtures only. Synthetic files do not establish game acceptance."""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
CLI = ROOT / "scripts/minecraft-artifacts.py"
SPEC = importlib.util.spec_from_file_location("minecraft_artifacts", CLI)
ARTIFACTS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ARTIFACTS)
CATALOG = json.loads((ROOT / "gradle/minecraft-artifacts.json").read_text(encoding="utf-8"))


class MinecraftArtifactsTest(unittest.TestCase):
    def setUp(self):
        # All disposable test files stay inside the packet/source root.
        self.temporary = tempfile.TemporaryDirectory(prefix=".artifact-fixture-", dir=ROOT)
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name).resolve()
        self.catalog = copy.deepcopy(CATALOG)
        self.catalog_path = self.directory / "catalog.json"
        self.write_catalog()
        self.family = ARTIFACTS.family_for("fabric", "26.1", ["26.1", "26.1.1", "26.1.2"])
        self.artifact = self.directory / "openallay-fabric-26.1-through-26.1.2-0.5.0.jar"
        self.artifact.write_bytes(b"Synthetic test bytes; not a game JAR or runtime proof.")
        self.artifact_sha = self.digest(self.artifact)
        self.receipt_path = self.directory / "receipt.json"
        self.receipt = {"familyId": self.family["id"], "loader": "fabric", "artifactPath": str(self.artifact),
                        "artifactSha256": self.artifact_sha, "runs": []}
        for target in self.family["supportedTargets"]:
            evidence = self.directory / ("fixture-" + target + ".txt")
            evidence.write_text("Synthetic offline receipt fixture for " + target, encoding="utf-8")
            self.receipt["runs"].append({"target": target, "loader": "fabric", "artifactPath": str(self.artifact),
                "artifactSha256": self.artifact_sha, "kind": "runtime", "outcome": "passed",
                "evidencePath": evidence.name, "evidenceSha256": self.digest(evidence)})
        self.write_receipt()

    @staticmethod
    def digest(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def write_catalog(self):
        self.catalog_path.write_text(json.dumps(self.catalog), encoding="utf-8")

    def write_receipt(self):
        self.receipt_path.write_text(json.dumps(self.receipt), encoding="utf-8")

    def run_cli(self, *arguments):
        environment = dict(os.environ, OPENALLAY_MINECRAFT_TARGET="26.3", PYTHONDONTWRITEBYTECODE="1")
        return subprocess.run([sys.executable, "-B", str(CLI), "--catalog", str(self.catalog_path), *arguments],
                              cwd=self.directory, env=environment, text=True, capture_output=True, check=False)

    def assert_failure(self, result):
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertTrue(result.stderr)

    def verify(self):
        return ARTIFACTS.verify_receipt(self.family, self.receipt_path, self.artifact, self.artifact_sha)

    def test_current_defaults_and_filenames_stay_singleton_26_2(self):
        self.assertEqual(len(self.catalog["acceptedFamilies"]), 34)
        for loader in ARTIFACTS.DEFAULT_LOADERS:
            result = self.run_cli("resolve", "--loader", loader, "--version", "0.4.1")
            self.assertEqual(result.returncode, 0, result.stderr)
            family = json.loads(result.stdout)
            self.assertEqual(family["id"], loader + "-26.2")
            self.assertEqual(family["buildTarget"], "26.2")
            self.assertEqual(family["supportedTargets"], ["26.2"])
            self.assertEqual(family["filename"], "openallay-" + loader + "-26.2-0.4.1.jar")
            self.assertEqual(family["minecraftMavenRange"], "[26.2]")
            self.assertEqual(family["fabricMinecraftPredicate"], "26.2")
            self.assertNotIn("runtimeAcceptance", family)

    def test_validate_cli_and_candidate_does_not_publish(self):
        self.assertEqual(self.run_cli("validate").returncode, 0)
        for target in self.catalog["targetOrder"]:
            if target != "26.2":
                with self.subTest(target=target):
                    self.assert_failure(self.run_cli("resolve", "--target", target, "--loader", "fabric"))
        for target in ("1.12.2", "", "../26.2", "26.02", "26.3-snapshot"):
            self.assert_failure(self.run_cli("resolve", "--target", target, "--loader", "fabric"))
        self.assert_failure(self.run_cli("resolve", "--loader", "forge"))

    def test_exact_shapes_duplicates_constants_and_types_refused(self):
        malformed = [dict(self.catalog, schemaVersion=1), {key:value for key,value in self.catalog.items() if key != "defaultTarget"},
                     dict(self.catalog, defaultTarget="26.3"), dict(self.catalog, acceptedFamilies={}),
                     dict(self.catalog, targetOrder=["26.2", "26.3", "26.1"]),
                     dict(self.catalog, targetOrder=["1.20.1", "1.21", "1.21.0", "26.2"]),
                     dict(self.catalog, targetOrder=["1.20", "26.2"])]
        for value in malformed:
            self.catalog_path.write_text(json.dumps(value), encoding="utf-8")
            self.assert_failure(self.run_cli("validate"))
        for text in ('{"defaultTarget":"26.2","defaultTarget":"26.2"}', '{"value":NaN}'):
            self.catalog_path.write_text(text, encoding="utf-8")
            self.assert_failure(self.run_cli("validate"))

    def test_family_loader_name_target_gap_and_overlap_refused(self):
        changes = [("loader", "forge"), ("id", "../fabric-26.2"), ("filenameTemplate", "../x.jar"),
                   ("filenameTemplate", "openallay-neoforge-26.2-{version}.jar"),
                   ("buildTarget", "26.3"), ("supportedTargets", []), ("supportedTargets", ["26.2", "26.2"])]
        for key, value in changes:
            self.catalog = copy.deepcopy(CATALOG)
            next(family for family in self.catalog["acceptedFamilies"] if family["id"] == "fabric-26.2")[key] = value
            self.write_catalog()
            self.assert_failure(self.run_cli("validate"))
        self.catalog = copy.deepcopy(CATALOG)
        self.catalog["acceptedFamilies"].append(ARTIFACTS.family_for("fabric", "26.2", ["26.1.2", "26.2"]))
        self.write_catalog()
        self.assert_failure(self.run_cli("validate"))
        with self.assertRaisesRegex(ValueError, "intermediate"):
            ARTIFACTS.interval(["1.21.9", "1.21.11"], CATALOG["targetOrder"])
        with self.assertRaisesRegex(ValueError, "ordered"):
            ARTIFACTS.interval(["1.21.10", "1.21.9"], CATALOG["targetOrder"])
        self.assertGreater(ARTIFACTS.target_key("1.21.10"), ARTIFACTS.target_key("1.21.9"))
        self.assertEqual(ARTIFACTS.target_key("1.21"), ARTIFACTS.target_key("1.21.0"))

    def test_candidates_exact_shape_and_never_publishing(self):
        for key, value in [("publishing", True), ("publishing", 0), ("loaders", ["fabric", "fabric"]),
                           ("loaders", ["forge"]), ("buildTarget", "26.2"), ("targets", ["26.1", "26.1.2"])]:
            self.catalog = copy.deepcopy(CATALOG)
            self.catalog["candidateIntervals"][0][key] = value
            self.write_catalog()
            self.assert_failure(self.run_cli("validate"))

    def test_interval_metadata_and_unsafe_release_version(self):
        metadata = ARTIFACTS.describe(self.family, "0.5.0-rc.1")
        self.assertEqual(metadata["minecraftMavenRange"], "[26.1,26.1.2]")
        self.assertEqual(metadata["fabricMinecraftPredicate"], ">=26.1 <=26.1.2")
        self.assertEqual(metadata["filename"], "openallay-fabric-26.1-through-26.1.2-0.5.0-rc.1.jar")
        for version in ("../0.5", "0.5/evil", "0.5 ", "0.5\n", "", "{version}"):
            with self.assertRaises(ValueError):
                ARTIFACTS.describe(self.family, version)

    def test_same_actual_bytes_all_targets_check_integrity_not_runtime(self):
        result = self.verify()
        self.assertEqual(result["receiptConsistency"], "passed")
        self.assertEqual(result["runtimeAcceptance"], "requires-external-review")
        command = self.run_cli("verify-receipt", "--family", self.family["id"], "--loader", "fabric",
                              "--receipt", str(self.receipt_path), "--artifact", str(self.artifact), "--sha256", self.artifact_sha)
        self.assertEqual(command.returncode, 0, command.stderr)
        self.assertEqual(json.loads(command.stdout), result)
        self.assert_failure(self.run_cli("resolve", "--target", "26.1.1", "--loader", "fabric"))

    def test_compilation_preparation_wrong_loader_path_hash_and_outcome_refused(self):
        for key, value in [("kind", "compilation"), ("kind", "prepared"), ("outcome", "pending"),
                           ("outcome", "failed"), ("loader", "neoforge"), ("artifactPath", str(self.directory / "rebuild.jar")),
                           ("artifactSha256", "0" * 64), ("artifactSha256", "a" * 63), ("target", "26.2")]:
            original = copy.deepcopy(self.receipt)
            self.receipt["runs"][1][key] = value
            self.write_receipt()
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                self.verify()
            self.receipt = original
        self.receipt["runs"][1]["target"] = "26.1"
        self.write_receipt()
        with self.assertRaises(ValueError):
            self.verify()
        self.receipt["runs"].pop()
        self.write_receipt()
        with self.assertRaises(ValueError):
            self.verify()

    def test_rebuilt_same_name_wrong_bytes_and_expected_hash_refused(self):
        self.artifact.write_bytes(b"Different rebuilt bytes with the same name")
        with self.assertRaisesRegex(ValueError, "Actual artifact SHA256"):
            self.verify()
        with self.assertRaises(ValueError):
            ARTIFACTS.verify_receipt(self.family, self.receipt_path, self.artifact, "f" * 64)

    def test_retained_evidence_hash_scope_duplicates_and_missing_refused(self):
        for value in ("../evidence.txt", "/tmp/evidence.txt", "a/../b", "missing.txt", "bad\\name.txt"):
            original = self.receipt["runs"][0]["evidencePath"]
            self.receipt["runs"][0]["evidencePath"] = value
            self.write_receipt()
            with self.assertRaises((ValueError, OSError)):
                self.verify()
            self.receipt["runs"][0]["evidencePath"] = original
        self.receipt["runs"][1]["evidencePath"] = self.receipt["runs"][0]["evidencePath"]
        self.write_receipt()
        with self.assertRaises(ValueError):
            self.verify()
        self.receipt["runs"][1]["evidencePath"] = "fixture-26.1.1.txt"
        (self.directory / "fixture-26.1.txt").write_text("Changed evidence", encoding="utf-8")
        self.write_receipt()
        with self.assertRaisesRegex(ValueError, "evidence SHA256"):
            self.verify()

    def test_symlink_relative_artifact_unbounded_and_wrong_receipt_identity_refused(self):
        link = self.directory / "linked.jar"
        link.symlink_to(self.artifact)
        with self.assertRaises(ValueError):
            ARTIFACTS.verify_receipt(self.family, self.receipt_path, link, self.artifact_sha)
        with self.assertRaises(ValueError):
            ARTIFACTS.verify_receipt(self.family, self.receipt_path, Path(self.artifact.name), self.artifact_sha)
        for key,value in [("familyId", "neoforge-26.1-through-26.1.2"), ("loader", "neoforge"), ("schemaVersion", 1)]:
            original = copy.deepcopy(self.receipt)
            self.receipt[key] = value
            self.write_receipt()
            with self.assertRaises(ValueError):
                self.verify()
            self.receipt = original
        self.catalog_path.write_bytes(b" " * (ARTIFACTS.MAX_JSON_BYTES + 1))
        self.assert_failure(self.run_cli("validate"))

    def test_artifact_change_between_receipt_reads_is_refused(self):
        actual_hash = ARTIFACTS.file_hash
        calls = 0
        def changed(path, limit):
            nonlocal calls
            if path == self.artifact:
                calls += 1
                if calls == 2:
                    self.artifact.write_bytes(b"Mutated during receipt check")
            return actual_hash(path, limit)
        with patch.object(ARTIFACTS, "file_hash", side_effect=changed):
            with self.assertRaisesRegex(ValueError, "changed during receipt"):
                self.verify()

    def test_shared_identity_receipt_evidence_and_empty_files_refused(self):
        link = self.directory / "evidence-link.txt"
        link.symlink_to(self.directory / self.receipt["runs"][0]["evidencePath"])
        self.receipt["runs"][0]["evidencePath"] = link.name
        self.write_receipt()
        with self.assertRaises(ValueError):
            self.verify()
        self.receipt["runs"][0]["evidencePath"] = self.artifact.name
        self.receipt["runs"][0]["evidenceSha256"] = self.artifact_sha
        self.write_receipt()
        with self.assertRaises(ValueError):
            self.verify()
        self.artifact.write_bytes(b"")
        with self.assertRaises(ValueError):
            self.verify()

    def test_catalog_addition_needs_receipt_and_partial_receipt_options_fail(self):
        # Replace the already-admitted interval only inside this synthetic receipt fixture.
        self.catalog["acceptedFamilies"] = [family for family in self.catalog["acceptedFamilies"] if family["id"] != self.family["id"]]
        self.catalog["acceptedFamilies"].append(self.family)
        self.write_catalog()
        self.assert_failure(self.run_cli("resolve", "--target", "26.1", "--loader", "fabric"))
        for options in [("--receipt", str(self.receipt_path)), ("--artifact", str(self.artifact)), ("--sha256", self.artifact_sha)]:
            self.assert_failure(self.run_cli("resolve", "--loader", "fabric", *options))
        result = self.run_cli("resolve", "--target", "26.1.1", "--loader", "fabric", "--receipt", str(self.receipt_path),
                              "--artifact", str(self.artifact), "--sha256", self.artifact_sha)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["verification"]["runtimeAcceptance"], "requires-external-review")


if __name__ == "__main__":
    unittest.main()
