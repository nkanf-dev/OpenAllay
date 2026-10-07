"""Offline accepted-family wiring fixtures. Synthetic archives do not prove game support."""
from copy import deepcopy
from importlib.util import module_from_spec, spec_from_file_location
from io import BytesIO
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = spec_from_file_location("build_minecraft_artifacts", ROOT / "scripts/build-minecraft-artifacts.py")
wiring = module_from_spec(SPEC)
SPEC.loader.exec_module(wiring)


def archive(entries):
    stream = BytesIO()
    with zipfile.ZipFile(stream, "w") as output:
        for name, content in entries.items():
            output.writestr(name, content)
    return stream.getvalue()


class AcceptedSelectionTest(unittest.TestCase):
    def setUp(self):
        self.data = deepcopy(wiring.catalog())
        self.range = wiring.artifacts.family_for("fabric", "26.1", ["26.1", "26.1.1", "26.1.2"])

    def add_range(self):
        self.data["acceptedFamilies"].append(self.range)
        self.data["candidateIntervals"] = self.data["candidateIntervals"][1:]

    def test_current_defaults_and_names_do_not_change(self):
        selected = wiring.select(self.data, "26.2")
        self.assertEqual([family["id"] for family in selected], ["fabric-26.2", "neoforge-26.2"])
        self.assertEqual([wiring.artifacts.describe(family, wiring.version())["filename"] for family in selected],
                         ["openallay-fabric-26.2-0.4.2.jar", "openallay-neoforge-26.2-0.4.2.jar"])

    def test_candidate_and_exact_target_does_not_silently_become_family(self):
        with self.assertRaises(ValueError):
            wiring.select(self.data, "26.1", self.range["id"])
        self.add_range()
        with self.assertRaises(ValueError):
            wiring.select(self.data, "26.1")
        self.assertEqual(wiring.select(self.data, "26.1", self.range["id"]), [self.range])
        with self.assertRaisesRegex(ValueError, "buildTarget"):
            wiring.select(self.data, "26.1.1", self.range["id"])

    def test_at_most_one_family_per_loader_no_duplicate_or_unknown_ids(self):
        self.add_range()
        for selection in ("", "fabric-26.2,fabric-26.2", "fabric-26.2,unknown",
                          "fabric-26.2," + self.range["id"]):
            with self.subTest(selection=selection), self.assertRaises(ValueError):
                wiring.select(self.data, "26.2", selection)

    def test_build_groups_deduplicate_build_target_not_supported_minors(self):
        self.add_range()
        self.assertEqual([(target, [family["id"] for family in group]) for target, group in wiring.groups(self.data)],
                         [("26.2", ["fabric-26.2", "neoforge-26.2"]), ("26.1", [self.range["id"]])])

    def test_build_plan_selects_only_reviewed_loader_once_and_retains_full_gates(self):
        self.add_range()
        with tempfile.TemporaryDirectory(dir=ROOT) as temporary:
            destination = Path(temporary) / "release"
            with patch.object(wiring, "catalog", return_value=self.data), patch.object(wiring, "verify", return_value=[]), \
                 patch.object(wiring.subprocess, "run") as run, patch.object(wiring.compiler, "compile_target") as compile_target:
                wiring.build_and_stage(destination)
            run.assert_called_once()
            command = run.call_args.args[0]
            for gate in ("-PminecraftTarget=26.2", "-PtestBundledExtensions=true", ":extension-api:test", ":common:test",
                         ":fabric:test", ":neoforge:test", ":stageBundledExtensions"):
                self.assertIn(gate, command)
            self.assertNotIn("clean", command)
            self.assertNotIn("-PbundleExtensions=false", command)
            self.assertEqual([(call.args[1], call.kwargs) for call in compile_target.call_args_list], [
                ("26.2", {"loaders": ("fabric", "neoforge"), "artifact_ids": "fabric-26.2,neoforge-26.2"}),
                ("26.1", {"loaders": ("fabric",), "artifact_ids": self.range["id"]})])
            with self.assertRaises(ValueError):
                wiring.build_and_stage(destination)

    def test_publication_receipt_uses_final_path_and_is_not_admission(self):
        self.add_range()
        record = {**wiring.artifacts.describe(self.range, "0.5.0"), "artifactPath": "/final/staged.jar", "artifactSha256": "a" * 64}
        with patch.object(wiring, "catalog", return_value=self.data), patch.object(wiring, "verify", return_value=[record]), \
             patch.object(wiring.artifacts, "verify_receipt") as receipts:
            with self.assertRaisesRegex(ValueError, "runtime receipts"):
                wiring.publication_records([self.range], Path("/final"))
            wiring.publication_records([self.range], Path("/final"), Path("/evidence"))
            receipts.assert_called_once_with(self.range, Path("/evidence") / (self.range["id"] + ".json"), Path("/final/staged.jar"), "a" * 64)


class MetadataGateTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(dir=ROOT)
        self.addCleanup(self.temporary.cleanup)
        self.path = Path(self.temporary.name) / "fixture.jar"
        self.product_entries = {name: b"synthetic offline fixture" for name in (
            "dev/openallay/OpenAllayBootstrap.class", "dev/openallay/guide/history/SqliteGuideHistoryStore.class",
            "dev/openallay/guide/semantic/SemanticMessageParser.class")}
        self.range = wiring.artifacts.family_for("fabric", "26.1", ["26.1", "26.1.1", "26.1.2"])

    def metadata_jar(self, loader, supported, extras=None):
        entries = dict(self.product_entries)
        nested = "META-INF/jars/" if loader == "fabric" else "META-INF/jarjar/"
        entries.update({nested + name: b"synthetic" for name in ("commonmark-0.28.0.jar", "commonmark-ext-gfm-tables-0.28.0.jar", "sqlite-jdbc-3.50.3.0.jar")})
        if loader == "fabric":
            entries["fabric.mod.json"] = json.dumps({"id": "openallay", "name": "OpenAllay", "version": "0.5.0", "environment": "*", "depends": {"minecraft": supported}})
        else:
            entries["META-INF/neoforge.mods.toml"] = ('[[mods]]\nmodId="openallay"\ndisplayName="OpenAllay"\nversion="0.5.0"\n'
                '[[dependencies.openallay]]\nmodId="minecraft"\nversionRange="' + supported + '"\n')
        entries.update(extras or {})
        self.path.write_bytes(archive(entries))

    def test_fabric_exact_closed_interval_and_reject_profile_or_renamed_jar(self):
        for predicate, success in ((">=26.1 <=26.1.2", True), ("~26.1", False), (">=26.1 <26.2", False), ("26.2", False)):
            self.metadata_jar("fabric", predicate)
            if success:
                wiring.metadata(self.path, self.range, "0.5.0")
            else:
                with self.assertRaises(ValueError):
                    wiring.metadata(self.path, self.range, "0.5.0")

    def test_neoforge_closed_singleton_or_range_and_reject_competing_descriptor(self):
        family = wiring.artifacts.family_for("neoforge", "26.1", self.range["supportedTargets"])
        self.metadata_jar("neoforge", "[26.1,26.1.2]")
        wiring.metadata(self.path, family, "0.5.0")
        self.metadata_jar("neoforge", "[26.1,26.2)")
        with self.assertRaises(ValueError):
            wiring.metadata(self.path, family, "0.5.0")
        self.metadata_jar("neoforge", "[26.1,26.1.2]", {"META-INF/mods.toml": "competing"})
        with self.assertRaises(ValueError):
            wiring.metadata(self.path, family, "0.5.0")

    def test_builder_must_cover_the_exact_accepted_family_without_admitting_candidates(self):
        lock = wiring.builder.prepare.load_manifest(ROOT / "distribution/extensions.lock.json")
        current = {"support": {"targets": [{"loader": "fabric", "minecraftVersionRange": "26.2"}]}}
        self.path.write_bytes(archive({wiring.builder.resource_path(lock): archive({wiring.builder.DESCRIPTOR: b"fixture"})}))
        with patch.object(wiring.builder, "verify_manifest", return_value=current):
            wiring.builder_support(self.path, wiring.catalog()["acceptedFamilies"][0], lock)
            with self.assertRaisesRegex(ValueError, "include every"):
                wiring.builder_support(self.path, self.range, lock)
        intended = {"support": {"targets": [{"loader": "fabric", "minecraftVersionRange": target}
                                             for target in self.range["supportedTargets"]]}}
        with patch.object(wiring.builder, "verify_manifest", return_value=intended):
            wiring.builder_support(self.path, self.range, lock)
        with self.assertRaises(ValueError):
            wiring.artifacts.resolve(wiring.catalog(), "26.1", "fabric")



class VerificationBehaviorTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(dir=ROOT)
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.family = wiring.catalog()["acceptedFamilies"][0]
        self.filename = wiring.artifacts.describe(self.family, "0.4.1")["filename"]
        self.jar = self.directory / self.filename
        self.engine = {"dev/openallay/OpenAllayBootstrap.class": b"same engine bytes"}
        self.resource = "META-INF/openallay/bundled-extensions/fixture.jar"
        self.jar.write_bytes(archive({**self.engine, self.resource: b"same Builder bytes"}))

    def execute(self):
        from contextlib import ExitStack
        with ExitStack() as stack:
            stack.enter_context(patch.object(wiring, "version", return_value="0.4.1"))
            stack.enter_context(patch.object(wiring, "metadata"))
            stack.enter_context(patch.object(wiring.native, "engine_files", return_value=self.engine))
            stack.enter_context(patch.object(wiring.builder.prepare, "load_manifest", return_value={}))
            stack.enter_context(patch.object(wiring.builder, "resource_path", return_value=self.resource))
            builder_gate = stack.enter_context(patch.object(wiring.builder, "verify_package"))
            support_gate = stack.enter_context(patch.object(wiring, "builder_support"))
            tokenizer_gate = stack.enter_context(patch.object(wiring.tokenizer, "verify"))
            sqlite_gate = stack.enter_context(patch.object(wiring.subprocess, "run"))
            result = wiring.verify([self.family], self.directory)
            for gate in (builder_gate, support_gate, tokenizer_gate, sqlite_gate):
                gate.assert_called_once()
            self.assertEqual(sqlite_gate.call_args.args[0][-2:], ["fabric", str(self.jar)])
            self.assertTrue(sqlite_gate.call_args.kwargs["check"])
            return result

    def test_full_gate_path_retains_actual_engine_and_final_artifact_hash(self):
        result = self.execute()
        self.assertEqual(result[0]["artifactPath"], str(self.jar))
        self.assertEqual(result[0]["artifactSha256"], wiring.artifacts.file_hash(self.jar, wiring.artifacts.MAX_ARTIFACT_BYTES))

    def test_changed_engine_and_extra_staged_jar_fail(self):
        self.jar.write_bytes(archive({"dev/openallay/OpenAllayBootstrap.class": b"changed bytes", self.resource: b"same Builder bytes"}))
        with self.assertRaisesRegex(ValueError, "Shared engine"):
            self.execute()
        (self.directory / "unexpected.jar").write_bytes(b"not admitted")
        with self.assertRaisesRegex(ValueError, "exactly"):
            self.execute()


class ProductionRoutingTest(unittest.TestCase):
    def test_gradle_naming_opt_in_and_exact_development_metadata(self):
        source = (ROOT / "build-logic/src/main/groovy/multiloader-common.gradle").read_text()
        self.assertIn("providers.gradleProperty('minecraftArtifact')", source)
        self.assertIn("'select', '--target', minecraftTarget, '--families',", source)
        self.assertIn("candidateSelection.isPresent() ? candidateSelection.get() : artifactSelection.get()", source)
        self.assertIn("Accepted and validation-only candidate selections cannot compete", source)
        self.assertIn('selectedArtifact != null ? selectedArtifact.fabricMinecraftPredicate : minecraft_version', source)
        self.assertIn('selectedArtifact != null ? selectedArtifact.minecraftMavenRange : "[${minecraft_version}]"', source)
        self.assertIn("if (name == 'fabric.mod.json')", source)
        self.assertIn('${base.archivesName.get()}-${project.version}.jar', (ROOT / "fabric/build.gradle").read_text())

    def test_all_verification_gates_remain_on(self):
        source = (ROOT / "scripts/build-minecraft-artifacts.py").read_text()
        for gate in ("engine = native.engine_files(ROOT)", "archive.read(name) == content", "builder.verify_package(path, family[", "builder_support(path, family, lock)", "tokenizer.verify(path, family[", "scripts/verify-sqlite-packaging.sh", "builder_bytes is None or content == builder_bytes"):
            self.assertIn(gate, source)
        self.assertNotIn("allow_unpinned=True", source)
        sqlite = (ROOT / "scripts/verify-sqlite-packaging.sh").read_text()
        self.assertIn('dev.openallay.guide.history.SqliteRuntimeCompatibilityTest "$extracted"', sqlite)
        self.assertIn(':engine-core:testClasses :engine-core:printSqliteProofSupportClasspath', sqlite)

    def test_publication_payload_exact_game_list_and_dependency(self):
        source = (ROOT / "scripts/publish-modrinth.sh").read_text()
        match = re.search(r"""python3 - "\$work/\$family_id-version.json".*? <<'PY'\n(.*?)\nPY""", source, re.S)
        self.assertIsNotNone(match)
        with tempfile.TemporaryDirectory(dir=ROOT) as temporary:
            output = Path(temporary) / "payload.json"
            result = subprocess.run([sys.executable, "-B", "-", str(output), "project", "0.5.0", '["26.1","26.1.1","26.1.2"]', "fabric", "release", "P7dR8mSH", "fabric-26.1-through-26.1.2"], input=match.group(1), text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            payload = json.loads(output.read_text())
            self.assertEqual(payload["game_versions"], ["26.1", "26.1.1", "26.1.2"])
            self.assertEqual(payload["loaders"], ["fabric"])
            self.assertEqual(payload["dependencies"], [{"project_id": "P7dR8mSH", "dependency_type": "required"}])
        self.assertLess(source.index("publication_records=$(python3"), source.index("api=https://api.modrinth.com/v2"))
        self.assertIn('existing $family_id version has different targets/bytes; release is immutable', source)

    def test_existing_release_matches_exact_targets_and_bytes_and_refuses_replacement(self):
        import hashlib
        source = (ROOT / "scripts/publish-modrinth.sh").read_text()
        match = re.search(r"""if python3 - "\$versions_response".*? <<'PY'\n(.*?)\nPY""", source, re.S)
        self.assertIsNotNone(match)
        with tempfile.TemporaryDirectory(dir=ROOT) as temporary:
            directory = Path(temporary)
            artifact = directory / "family.jar"
            artifact.write_bytes(b"same accepted immutable bytes")
            response = directory / "versions.json"
            existing = {"version_number": "0.5.0", "game_versions": ["26.1", "26.1.1", "26.1.2"], "loaders": ["fabric"],
                        "files": [{"filename": "family.jar", "primary": True, "hashes": {"sha512": hashlib.sha512(artifact.read_bytes()).hexdigest()}}]}
            for values, expected in (([], 1), ([existing], 0), ([dict(existing, game_versions=["26.1"])], 2),
                                     ([dict(existing, files=[])], 2)):
                response.write_text(json.dumps(values))
                result = subprocess.run([sys.executable, "-B", "-", str(response), "0.5.0", '["26.1","26.1.1","26.1.2"]', "fabric", str(artifact)],
                                        input=match.group(1), text=True, capture_output=True)
                self.assertEqual(result.returncode, expected, result.stderr)
        self.assertIn("Final staged artifact changed after verification; refusing upload", source)

    def test_release_only_builds_accepted_groups_without_new_upload_or_matrix(self):
        source = (ROOT / ".github/workflows/release.yml").read_text()
        self.assertIn('python3 scripts/build-minecraft-artifacts.py build-and-stage release', source)
        stage, runtime = source.split("\n  runtime:\n", 1)
        runtime, publish = runtime.split("\n  publish:\n", 1)
        self.assertNotIn("strategy:", stage, "Build each final artifact once")
        self.assertIn("strategy:", runtime)
        self.assertIn("    - stage\n    - runtime", publish)
        self.assertNotIn("build-and-stage", publish)
        self.assertIn("final-stage-receipts", publish)
        self.assertIn('subject-path: release/*', source)
        self.assertIn('release/SHA256SUMS', source)
        self.assertIn('run: ./scripts/publish-modrinth.sh "${GITHUB_REF_NAME}" release', source)


if __name__ == "__main__":
    unittest.main()
