#!/usr/bin/env python3
"""Offline negative/identity tests only. No game/runtime acceptance claims."""
from importlib.util import module_from_spec, spec_from_file_location
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = spec_from_file_location("intervals", ROOT / "scripts/verify-minecraft-binary-intervals.py")
intervals = module_from_spec(spec)
spec.loader.exec_module(intervals)


class BinaryIntervalsTest(unittest.TestCase):
    def setUp(self):
        self.catalog = intervals.catalog()
        self.family = intervals.candidates(self.catalog)[0]

    def test_seven_build_rows_fourteen_jars_thirty_exact_game_profiles(self):
        plan = intervals.matrices(self.catalog)
        self.assertEqual(7, len(plan["build_matrix"]["include"]))
        self.assertEqual(14, sum(len(row["loaders"].split(",")) for row in plan["build_matrix"]["include"]))
        rows = plan["runtime_matrix"]["include"]
        self.assertEqual(30, len(rows))
        self.assertEqual(30, len({(row["family"], row["loader"], row["target"]) for row in rows}))
        self.assertEqual(["26.1", "26.1.1", "26.1.2"], [row["target"] for row in rows if row["family"] == self.family["id"]])

    def test_failure_retry_can_select_only_one_loader_family(self):
        rows = intervals.matrices(self.catalog, self.family["id"])
        self.assertEqual(1, len(rows["build_matrix"]["include"]))
        self.assertEqual("fabric", rows["build_matrix"]["include"][0]["loaders"])
        self.assertEqual(3, len(rows["runtime_matrix"]["include"]))

    def test_candidate_does_not_unlock_normal_release_resolve(self):
        with self.assertRaises(ValueError):
            intervals.artifacts.resolve(self.catalog, "26.1", "fabric")
        with self.assertRaises(ValueError):
            intervals.select(self.catalog, "26.2", "fabric-26.2")
        with self.assertRaises(ValueError):
            intervals.select(self.catalog, "26.1.1", self.family["id"])
        with self.assertRaises(ValueError):
            intervals.matrices(self.catalog, "fabric-26.2")
        with self.assertRaises(ValueError):
            intervals.matrices(self.catalog, self.family["id"] + "," + self.family["id"])

    def test_exact_family_identity_never_aliases_target(self):
        self.assertEqual(self.family, intervals.family_for_id(self.catalog, self.family["id"], "fabric", "26.1.2"))
        for loader, target in (("neoforge", "26.1"), ("fabric", "26.2"), ("fabric", "1.20.1")):
            with self.assertRaises(ValueError):
                intervals.family_for_id(self.catalog, self.family["id"], loader, target)
        selected = intervals.select(self.catalog, "26.1", self.family["id"])
        self.assertEqual(">=26.1 <=26.1.2", intervals.artifacts.describe(selected[0])["fabricMinecraftPredicate"])

    def test_quality_requires_exact_successful_source_not_active_or_other_workflow(self):
        source = "a" * 40
        run = {"head_sha": source, "status": "completed", "conclusion": "success", "path": ".github/workflows/quality.yml",
               "repository": {"full_name": "owner/OpenAllay"}, "id": 9}
        self.assertEqual(9, intervals.feature_run_guard(run, source, "owner/OpenAllay")["featureRunId"])
        for key, value in (("head_sha", "b" * 40), ("status", "in_progress"), ("conclusion", "failure"),
                           ("path", ".github/workflows/minecraft-native.yml"), ("repository", {"full_name": "other/repo"})):
            bad = copy.deepcopy(run)
            bad[key] = value
            with self.assertRaises(ValueError):
                intervals.feature_run_guard(bad, source, "owner/OpenAllay")

    def test_original_stage_same_bytes_source_and_family(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary).resolve()
            version = "0.4.2"
            row = intervals.artifacts.describe(self.family, version)
            jar = directory / row["filename"]
            jar.write_bytes(b"offline-fixture-not-a-game-artifact")
            sha = hashlib.sha256(jar.read_bytes()).hexdigest()
            row.update(artifactSha256=sha)
            record = {"sourceSha": "a" * 40, "publishing": False, "buildTarget": "26.1", "artifacts": [row]}
            intervals.write_json(directory / "build-record.json", record)
            (directory / "SHA256SUMS").write_text(sha + "  " + jar.name + "\n")
            self.assertEqual((jar, sha), intervals.staged_artifact(directory, self.family, version, "a" * 40)[:2])
            with self.assertRaises(ValueError):
                intervals.staged_artifact(directory, self.family, version, "b" * 40)
            jar.write_bytes(b"replacement")
            with self.assertRaises(ValueError):
                intervals.staged_artifact(directory, self.family, version, "a" * 40)

    def test_summary_rejects_compile_only_skips_changed_bytes_and_wrong_game(self):
        jar = Path("/original.jar")
        sha = "a" * 64
        summary = {"loader": "fabric", "minecraft": "26.1.2", "artifactSha256": sha, "status": "PASSED",
                   "noPaidModel": True, "failures": [],
                   "originalArtifact": {"sha256": sha, "path": str(jar)}, "finalArtifact": {"sha256": sha, "path": str(jar)},
                   "scenarios": [{"scenario": scenario, "status": "PASSED", "failures": [], "artifactAfter": {"sha256": sha}}
                                 for scenario in intervals.SCENARIOS]}
        intervals.verify_summary(summary, self.family, "26.1.2", sha, jar)
        for key, value in (("minecraft", "26.1"), ("status", "COMPILED"), ("artifactSha256", "b" * 64)):
            bad = copy.deepcopy(summary)
            bad[key] = value
            with self.assertRaises(ValueError):
                intervals.verify_summary(bad, self.family, "26.1.2", sha, jar)
        for mutation in ("skip", "changed", "failed"):
            bad = copy.deepcopy(summary)
            if mutation == "skip":
                bad["scenarios"].pop()
            elif mutation == "changed":
                bad["scenarios"][0]["artifactAfter"]["sha256"] = "b" * 64
            else:
                bad["scenarios"][0]["status"] = "FAILED"
            with self.assertRaises(ValueError):
                intervals.verify_summary(bad, self.family, "26.1.2", sha, jar)

    def test_existing_launcher_receives_family_not_compatibility_override(self):
        runner = intervals.module("run-ci-client-acceptance.py")
        from argparse import Namespace
        args = Namespace(loader="fabric", minecraft_version="26.1.2", timeout_seconds=180,
                         jar=Path("/original.jar"), minecraft_root=Path("/official-runtime"), java=Path("/java"),
                         assets_root=Path("/assets"), fabric_api=Path("/official-api.jar"), gradle_cache=None,
                         mod_version="0.4.2", artifact_family=self.family["id"])
        command = runner.prepare_command(args, "ui-stop", "test", 9, ROOT)
        self.assertEqual(self.family["id"], command[command.index("--artifact-family") + 1])
        self.assertEqual("26.1.2", command[command.index("--minecraft-target") + 1])
        self.assertIn("--cancel-on-tool-start", command)
        self.assertFalse(any("force" in arg or "ignore" in arg for arg in command))

    def synthetic_package(self, directory, family, version="0.4.2", minecraft_override=None, missing_refmap=False):
        profile = intervals.module("minecraft-target.py").read_profile(ROOT, family["buildTarget"])
        described = intervals.artifacts.describe(family, version)
        jar = directory / described["filename"]
        with zipfile.ZipFile(jar, "w") as archive:
            for entry in ("dev/openallay/OpenAllayBootstrap.class", "dev/openallay/guide/history/SqliteGuideHistoryStore.class",
                          "dev/openallay/guide/semantic/SemanticMessageParser.class", "example/mixins/Binding.class"):
                archive.writestr(entry, b"offline-not-executable")
            nested = "META-INF/jars/" if family["loader"] == "fabric" else "META-INF/jarjar/"
            for dependency in ("commonmark-0.28.0.jar", "commonmark-ext-gfm-tables-0.28.0.jar", "sqlite-jdbc-3.50.3.0.jar"):
                archive.writestr(nested + dependency, b"offline-not-a-dependency")
            archive.writestr("example.mixins.json", json.dumps({"package": "example.mixins", "client": ["Binding"], "refmap": "example.refmap.json"}))
            if not missing_refmap:
                archive.writestr("example.refmap.json", "{}")
            if family["loader"] == "fabric":
                archive.writestr("fabric.mod.json", json.dumps({"id": "openallay", "name": "OpenAllay", "version": version,
                    "environment": "*", "depends": {"minecraft": minecraft_override or described["fabricMinecraftPredicate"],
                    "fabricloader": ">=" + profile["fabric_loader_version"], "java": ">=" + profile["java_version"]}}))
            else:
                descriptor = "META-INF/mods.toml" if family["buildTarget"] == "1.20.3" else "META-INF/neoforge.mods.toml"
                metadata = ('modLoader="javafml"\nloaderVersion="' + profile["neoforge_loader_version_range"] + '"\n'
                    '[[mods]]\nmodId="openallay"\ndisplayName="OpenAllay"\nversion="' + version + '"\n'
                    '[[dependencies.openallay]]\nmodId="neoforge"\nversionRange="[' + profile["neoforge_version"] + ',)"\n'
                    '[[dependencies.openallay]]\nmodId="minecraft"\nversionRange="' + (minecraft_override or described["minecraftMavenRange"]) + '"\n')
                archive.writestr(descriptor, metadata)
        return jar

    def test_all_candidate_real_metadata_guards_and_missing_refmap_fail_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for family in intervals.candidates(self.catalog):
                jar = self.synthetic_package(directory, family)
                self.assertEqual("not-established", intervals.package_guard(jar, family, "0.4.2")["runtimeAcceptance"])
                singleton = family["buildTarget"] if family["loader"] == "fabric" else "[" + family["buildTarget"] + "]"
                jar = self.synthetic_package(directory, family, minecraft_override=singleton)
                with self.assertRaises(ValueError):
                    intervals.package_guard(jar, family, "0.4.2")
                jar = self.synthetic_package(directory, family, missing_refmap=True)
                with self.assertRaises(ValueError):
                    intervals.package_guard(jar, family, "0.4.2")

    def test_aggregate_uses_existing_receipt_and_rejects_cross_byte_target(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary).resolve()
            jar = self.synthetic_package(directory, self.family)
            sha = hashlib.sha256(jar.read_bytes()).hexdigest()
            results = directory / "results"
            for target in self.family["supportedTargets"]:
                summary = {"loader": "fabric", "minecraft": target, "artifactSha256": sha, "status": "PASSED",
                           "noPaidModel": True, "failures": [],
                           "originalArtifact": {"path": str(jar), "sha256": sha}, "finalArtifact": {"path": str(jar), "sha256": sha},
                           "scenarios": [{"scenario": scenario, "status": "PASSED", "failures": [], "artifactAfter": {"sha256": sha}}
                                         for scenario in intervals.SCENARIOS]}
                value = {"familyId": self.family["id"], "loader": "fabric", "target": target,
                         "artifactSha256": sha, "sourceSha": "a" * 40, "kind": "runtime", "outcome": "passed", "runtimeSummary": summary}
                intervals.write_json(results / (self.family["id"] + "-" + target + ".json"), value)
            receipt_directory = directory / "receipts"
            result = intervals.aggregate_receipt(self.family["id"], jar, results, receipt_directory)
            self.assertEqual("passed", result["receiptConsistency"])
            self.assertEqual("requires-external-review", result["runtimeAcceptance"])
            bad_path = results / (self.family["id"] + "-26.1.2.json")
            bad = json.loads(bad_path.read_text())
            bad["artifactSha256"] = "b" * 64
            intervals.write_json(bad_path, bad)
            with self.assertRaises(ValueError):
                intervals.aggregate_receipt(self.family["id"], jar, results, directory / "bad-receipts")

    def test_release_workflow_tests_and_publishes_original_stage_without_native_rebuild(self):
        text = (ROOT / ".github/workflows/release.yml").read_text()
        self.assertEqual(1, text.count("build-and-stage release"))
        self.assertIn("final-stage-receipts", text)
        self.assertIn("OPENALLAY_MINECRAFT_RECEIPT_DIRECTORY", text)
        self.assertIn("release-production-${{ github.sha }}", text)
        self.assertIn("needs:", text)
        self.assertIn("max-parallel: 3", text)
        self.assertIn("publication-records release --receipt-directory", text)
        self.assertNotIn("continue-on-error", text)
        published = text.split("  publish:", 1)[1]
        self.assertNotIn("build-and-stage", published)
        self.assertNotIn("compile-native-target", published)

    def test_workflow_max_parallel_normal_official_runtimes_and_never_promotes(self):
        text = (ROOT / ".github/workflows/minecraft-binary-intervals.yml").read_text()
        self.assertEqual(2, text.count("max-parallel: 3"))
        self.assertIn("fail-fast: false", text)
        self.assertIn("prepare-ci-minecraft-runtime.py", text)
        self.assertIn("sha256sum --check SHA256SUMS", text)
        self.assertIn("cancel-in-progress: false", text)
        self.assertNotIn("acceptedFamilies", text)
        self.assertNotIn("--unrestricted", text)
        self.assertNotIn("forcecompat", text)


if __name__ == "__main__":
    unittest.main()
