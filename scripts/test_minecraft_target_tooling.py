"""Offline fixtures for the target pin CLI and its distribution/CI callers."""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PINS_26_2 = {
    "java_version": "25", "minecraft_version": "26.2", "minecraft_version_range": "[26.2, 26.3)",
    "neo_form_version": "26.2-1", "fabric_version": "0.152.1+26.2", "fabric_loader_version": "0.19.3",
    "neoforge_version": "26.2.0.25-beta", "neoforge_loader_version_range": "[4,)",
    "jei_version": "30.13.0.86", "rei_version": "26.2.820", "architectury_version": "21.0.4",
    "fabric_command_api_version": "3.1.0+00cb03469c", "fabric_resource_loader_version": "2.0.13+9edec1269c",
    "fabric_networking_api_version": "6.3.3+72073ef033", "fabric_lifecycle_events_version": "4.1.3+4575b05f9c",
    "fabric_key_mapping_api_version": "2.0.5+e2bdee789c", "fabric_rendering_version": "25.1.6+46a6d00c9c",
}


class MinecraftTargetToolingTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="openallay-target-cli-")
        self.addCleanup(self.temporary.cleanup)
        self.fixture = Path(self.temporary.name)
        (self.fixture / "scripts").mkdir()
        self.cli = self.fixture / "scripts/minecraft-target.py"
        shutil.copyfile(ROOT / "scripts/minecraft-target.py", self.cli)
        self.profiles = self.fixture / "gradle/minecraft-targets"
        self.profiles.mkdir(parents=True)
        for target in ("26.2", "26.3"):
            shutil.copyfile(ROOT / "gradle/minecraft-targets" / (target + ".properties"),
                            self.profiles / (target + ".properties"))
        self.default_profile = self.profiles / "26.2.properties"
        self.default_text = self.default_profile.read_text(encoding="utf-8")
        # An old root pin must never act as a fallback or override.
        (self.fixture / "gradle.properties").write_text("minecraft_version=wrong\n", encoding="utf-8")

    def cli_result(self, *args, env=None):
        return subprocess.run([sys.executable, "-B", str(self.cli), *args],
                              cwd=self.fixture / "scripts", env=env, text=True,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)

    def assert_failure(self, result):
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertTrue(result.stderr)

    def source(self, relative):
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_default_reads_all_17_accepted_26_2_pins_as_plain_stdout(self):
        for key, value in PINS_26_2.items():
            with self.subTest(property=key):
                result = self.cli_result("--property", key)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(result.stdout, value + "\n")
                self.assertEqual(result.stderr, "")
        selector = self.source("gradle/minecraft-targets.gradle")
        self.assertIn("getOrElse('26.2')", selector)
        for key in PINS_26_2:
            self.assertIn("'" + key + "'", selector)

    def test_explicit_26_3_reads_candidate_facts_without_support_output(self):
        for key, value in {"minecraft_version": "26.3", "minecraft_version_range": "[26.3]",
                           "java_version": "25", "fabric_version": "0.161.0+26.3"}.items():
            with self.subTest(property=key):
                result = self.cli_result("--target", "26.3", "--property", key)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(result.stdout, value + "\n")

    def test_unknown_target_property_and_empty_explicit_target_fail(self):
        for arguments in [("--target", "1.12.2", "--property", "minecraft_version"),
                          ("--property", "version"),
                          ("--target", "", "--property", "minecraft_version")]:
            with self.subTest(arguments=arguments):
                self.assert_failure(self.cli_result(*arguments))

    def test_missing_profile_does_not_fall_back_to_root_pin(self):
        self.default_profile.unlink()
        self.assert_failure(self.cli_result("--property", "minecraft_version"))

    def test_unknown_missing_and_duplicate_fields_fail(self):
        invalid_profiles = [self.default_text + "runtime_version=1\n",
                            self.default_text.replace("java_version=25\n", ""),
                            self.default_text + "java_version=25\n"]
        for text in invalid_profiles:
            with self.subTest(text=text):
                self.default_profile.write_text(text, encoding="utf-8")
                self.assert_failure(self.cli_result("--property", "minecraft_version"))

    def test_empty_padded_malformed_escaped_and_mismatched_fields_fail(self):
        changes = [("java_version=25", "java_version="),
                   ("java_version=25", "java_version= 25"),
                   ("java_version=25", "java_version=25 "),
                   ("java_version=25", "java_version:25"),
                   ("java_version=25", r"java_version=2\5"),
                   ("java_version=25", "java_version=0"),
                   ("java_version=25", "java_version=25.0"),
                   ("minecraft_version=26.2", "minecraft_version=26.3")]
        for old, new in changes:
            with self.subTest(value=new):
                self.default_profile.write_text(self.default_text.replace(old, new), encoding="utf-8")
                self.assert_failure(self.cli_result("--property", "minecraft_version"))

    def test_comments_crlf_cwd_and_ambient_env_do_not_change_default(self):
        text = "! Profile facts only\n\n  # comment\n" + self.default_text
        self.default_profile.write_bytes(text.replace("\n", "\r\n").encode("utf-8"))
        environment = dict(os.environ, OPENALLAY_MINECRAFT_TARGET="26.3")
        result = self.cli_result("--property", "minecraft_version", env=environment)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "26.2\n")

    def test_production_metadata_checks_reject_renamed_wrong_target_jars(self):
        # Metadata checks moved to one Python verifier; retain executable behavior,
        # not source-shaped heredoc guards that prevent meaningful gate refactoring.
        from importlib.util import module_from_spec, spec_from_file_location
        spec = spec_from_file_location("accepted_distribution_wiring", ROOT / "scripts/build-minecraft-artifacts.py")
        wiring = module_from_spec(spec)
        spec.loader.exec_module(wiring)
        for loader in ("fabric", "neoforge"):
            family = wiring.artifacts.resolve(wiring.catalog(), "26.2", loader)
            for target, success in (("26.2", True), ("26.3", False)):
                entries = {name: b"synthetic fixture" for name in (
                    "dev/openallay/OpenAllayBootstrap.class", "dev/openallay/guide/history/SqliteGuideHistoryStore.class",
                    "dev/openallay/guide/semantic/SemanticMessageParser.class")}
                nested = "META-INF/jars/" if loader == "fabric" else "META-INF/jarjar/"
                entries.update({nested + name: b"synthetic fixture" for name in (
                    "commonmark-0.28.0.jar", "commonmark-ext-gfm-tables-0.28.0.jar", "sqlite-jdbc-3.50.3.0.jar")})
                if loader == "fabric":
                    entries["fabric.mod.json"] = json.dumps({"id": "openallay", "name": "OpenAllay", "version": "0.4.1",
                        "environment": "*", "depends": {"minecraft": target}})
                else:
                    entries["META-INF/neoforge.mods.toml"] = ('[[mods]]\nmodId="openallay"\ndisplayName="OpenAllay"\nversion="0.4.1"\n'
                        '[[dependencies.openallay]]\nmodId="minecraft"\nversionRange="[' + target + ']"\n')
                jar = self.fixture / (loader + ".jar")
                with zipfile.ZipFile(jar, "w") as archive:
                    for entry, content in entries.items():
                        archive.writestr(entry, content)
                with self.subTest(loader=loader, success=success):
                    if success:
                        wiring.metadata(jar, family, "0.4.1")
                    else:
                        with self.assertRaises(ValueError):
                            wiring.metadata(jar, family, "0.4.1")

    def test_shell_selection_cli_contract_and_publication_gate_order(self):
        for filename in ("verify-distribution.sh", "verify-sqlite-packaging.sh", "publish-modrinth.sh"):
            text = self.source("scripts/" + filename)
            self.assertIn("minecraft_target=${OPENALLAY_MINECRAFT_TARGET-26.2}", text)
            self.assertIn('--target "$minecraft_target" --property minecraft_version', text)
            self.assertNotIn("s/^minecraft_version=//p", text)
            self.assertIn("s/^version=//p", text)
        distribution = self.source("scripts/verify-distribution.sh")
        self.assertIn("if (( $# > 1 )); then", distribution)
        self.assertIn("verify-distribution.sh [staged-release-directory]", distribution)
        sqlite = self.source("scripts/verify-sqlite-packaging.sh")
        self.assertIn('-PminecraftTarget="$minecraft_target"', sqlite)
        self.assertIn(":engine-core:testClasses :engine-core:printSqliteProofSupportClasspath", sqlite)
        publish = self.source("scripts/publish-modrinth.sh")
        gate = 'publication_records=$(python3 "$repository/scripts/build-minecraft-artifacts.py"'
        self.assertLess(publish.index(gate), publish.index("api=https://api.modrinth.com/v2"))
        self.assertIn('python3 "$repository/scripts/build-minecraft-artifacts.py" "${arguments[@]}"', distribution)

    def test_ci_target_matches_gradle_java_and_packaging_arguments(self):
        for workflow in ("quality.yml", "release.yml"):
            text = self.source(".github/workflows/" + workflow)
            self.assertIn('OPENALLAY_MINECRAFT_TARGET: "26.2"', text)
            self.assertIn('--target "$OPENALLAY_MINECRAFT_TARGET" --property java_version', text)
            self.assertIn("${{ steps.minecraft_target.outputs.java_version }}", text)
            self.assertIn("-p 'test_minecraft_target*.py'", text)
            self.assertNotIn("s/^minecraft_version=//p", text)
            if workflow == "quality.yml":
                verify, client = text.split("\n  client:\n", 1)
                self.assertNotIn("matrix:", verify, "Build shared tests and packages once")
                self.assertIn("needs: verify", client)
                self.assertIn("loader: [fabric, neoforge]", client)
                self.assertNotIn("target:", client, "Client jobs reuse default production artifacts")
                self.assertNotIn("./gradlew", client, "Never rebuild the tested JARs in client jobs")
                self.assertIn("client-production-${{ github.sha }}", verify)
                self.assertIn("client-production-${{ github.sha }}", client)
                self.assertIn("sha256sum --check SHA256SUMS", client)
                self.assertIn('-PminecraftTarget="$OPENALLAY_MINECRAFT_TARGET" clean :extension-api:test :common:test :fabric:build :neoforge:build', text)
                self.assertIn('--fabric "fabric/build/libs/openallay-fabric-${minecraft_version}-${version}.jar"', text)
                self.assertIn('--neoforge "neoforge/build/libs/openallay-neoforge-${minecraft_version}-${version}.jar"', text)
            else:
                self.assertNotIn("matrix:", text, "Publish accepted binary families, not a target matrix")
                self.assertIn('python3 scripts/build-minecraft-artifacts.py build-and-stage release', text)
                wiring = self.source("scripts/build-minecraft-artifacts.py")
                self.assertIn('"-PminecraftTarget=" + target, "-PminecraftArtifact=" + selection', wiring)
                self.assertIn('"clean", ":common:test"', wiring)
                self.assertIn('tokenizer.verify(path, family["loader"])', wiring)
                self.assertIn('scripts/verify-sqlite-packaging.sh', wiring)


if __name__ == "__main__":
    unittest.main()
