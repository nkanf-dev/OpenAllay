"""No-network publication regressions. No builds, release writes, or secrets."""
from copy import deepcopy
import hashlib
from importlib.util import module_from_spec, spec_from_file_location
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from release_publication import select_records

ROOT = Path(__file__).resolve().parents[1]


def load_publisher():
    spec = spec_from_file_location("github_publisher", ROOT / "scripts/publish-current-github-release.py")
    result = module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


class PublicationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve(strict=True)
        (self.root / "gradle").mkdir()
        (self.root / "gradle.properties").write_text("version=0.4.4\n")
        self.directory = self.root / "release"
        self.directory.mkdir()
        self.families = deepcopy(json.loads((ROOT / "gradle/minecraft-artifacts.json").read_text())["acceptedFamilies"])
        self.records = []
        for family in self.families:
            filename = family["filenameTemplate"].replace("{version}", "0.4.4")
            path = self.directory / filename
            path.write_bytes((family["id"] + " genuine product fixture").encode())
            record = {key: family[key] for key in ("id", "loader", "supportedTargets", "artifactKind", "publicationChannels")}
            record.update(filename=filename, artifactPath=str(path), artifactSha256=hashlib.sha256(path.read_bytes()).hexdigest())
            self.records.append(record)
        self.write_catalog()
        self.write_checksums()

    def write_catalog(self):
        (self.root / "gradle/minecraft-artifacts.json").write_text(json.dumps({"acceptedFamilies": self.families}))

    def write_checksums(self):
        (self.directory / "SHA256SUMS").write_text("".join(row["artifactSha256"] + "  " + row["filename"] + "\n" for row in self.records))

    def select(self, channel="github"):
        return select_records(self.root, self.directory, self.records, "v0.4.4", channel)

    def test_exact_channel_counts_and_targets(self):
        github = self.select()
        modrinth = self.select("modrinth")
        self.assertEqual(len(github), 35)
        self.assertEqual(len(modrinth), 35)
        self.assertEqual(len({target for row in github for target in row["supportedTargets"]}), 27)
        self.assertEqual(len({target for row in modrinth for target in row["supportedTargets"]}), 27)
        self.assertEqual(sum(len(row["supportedTargets"]) for row in github), 50)
        self.assertEqual(sum(len(row["supportedTargets"]) for row in modrinth), 50)
        self.assertEqual([row["id"] for row in github if row["artifactKind"] == "zip"], [])
        self.assertTrue(all(row["artifactKind"] == "jar" for row in modrinth))
        self.assertIn("forge-1.16.5", [row["id"] for row in modrinth])

    def test_modrinth_production_filter_returns_35_real_mods(self):
        source = (ROOT / "scripts/publish-modrinth.sh").read_text()
        match = re.search(r"python3 - \"\$repository\" \"\$distribution\" \"\$tag\" \"\$publication_records\" <<'PY'\n(.*?)\nPY", source, re.S)
        (self.root / "scripts").symlink_to(ROOT / "scripts", target_is_directory=True)
        result = subprocess.run([sys.executable, "-B", "-", str(self.root), str(self.directory), "v0.4.4", json.dumps(self.records)],
                                input=match.group(1), text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        selected = json.loads(result.stdout)
        self.assertEqual(len(selected), 35)
        self.assertIn("forge-1.12.2", [row["id"] for row in selected])
        self.records[-1]["publicationChannels"] = ["github"]
        result = subprocess.run([sys.executable, "-B", "-", str(self.root), str(self.directory), "v0.4.4", json.dumps(self.records)],
                                input=match.group(1), text=True, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("source family", result.stderr)

    def test_github_command_includes_jars_and_checksums_not_internal_json(self):
        (self.root / "release-publication-records.json").write_text(json.dumps(self.records))
        (self.directory / "diagnostics.json").write_text("{}")
        (self.root / "release-notes.md").write_text("Release notes")
        publisher = load_publisher()
        with patch.object(publisher, "ROOT", self.root), patch.dict(publisher.os.environ, RELEASE_TAG="v0.4.4"), \
                patch.object(publisher.subprocess, "run") as run:
            publisher.main()
        command = run.call_args.args[0]
        assets = command[4:command.index("--notes-file")]
        self.assertEqual(len(assets), 36)
        self.assertTrue(all(not name.endswith(".zip") for name in assets))
        self.assertIn(str(self.directory / "SHA256SUMS"), assets)
        self.assertTrue(all(not name.endswith(".json") for name in assets))
        self.assertIn("--verify-tag", command)

    def test_old_version_and_ambiguous_source_are_rejected(self):
        self.records[-1]["filename"] = "openallay-fabric-26.3-0.4.3.jar"
        with self.assertRaisesRegex(ValueError, "current source version"):
            self.select()
        (self.root / "gradle.properties").write_text("version=0.4.4\nversion=0.4.3\n")
        with self.assertRaisesRegex(ValueError, "one safe"):
            self.select()

    def test_record_cannot_change_channel_or_kind(self):
        self.records[-1]["publicationChannels"] = ["github"]
        with self.assertRaisesRegex(ValueError, "source family"):
            self.select("modrinth")
        self.records[-1]["publicationChannels"] = ["github", "modrinth"]
        self.records[-1]["artifactKind"] = "zip"
        with self.assertRaisesRegex(ValueError, "source family"):
            self.select()
        self.families[-1]["artifactKind"] = "zip"
        self.write_catalog()
        with self.assertRaisesRegex(ValueError, "single-mod JAR"):
            self.select()

    def test_obsolete_forge12_zip_record_is_not_admitted(self):
        row = dict(self.records[-1], id="forge-1.12.2",loader="forge",supportedTargets=["1.12.2"],
                   artifactKind="zip",publicationChannels=["github"])
        self.records[-1] = row
        with self.assertRaises(ValueError):
            self.select()

    def test_checksum_missing_extra_duplicate_and_changed_bytes_rejected(self):
        checksums = self.directory / "SHA256SUMS"
        original = checksums.read_text()
        for content in ("", original + original.splitlines()[0] + "\n", original + "0" * 64 + "  diagnostic.json\n"):
            checksums.write_text(content)
            with self.subTest(content=content[:80]), self.assertRaises(ValueError):
                self.select()
        checksums.write_text(original)
        Path(self.records[-1]["artifactPath"]).write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "changed after verification"):
            self.select()

    def test_extra_package_unsafe_path_duplicate_record_and_symlink_fail(self):
        extra = self.directory / "openallay-facade-0.4.4.jar"
        extra.write_bytes(b"not a whole product")
        with self.assertRaisesRegex(ValueError, "exactly"):
            self.select()
        extra.unlink()
        row = self.records[-1]
        original = row["artifactPath"]
        row["artifactPath"] = str(self.directory / "../" / "release" / row["filename"])
        with self.assertRaisesRegex(ValueError, "exact final"):
            self.select()
        row["artifactPath"] = original
        self.records[-1] = self.records[0]
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            self.select()
        self.records[-1] = row
        path = Path(original)
        target = self.root / "bundle.zip"
        path.rename(target)
        path.symlink_to(target)
        with self.assertRaises(ValueError):
            self.select()


class ModrinthBoundaryTest(unittest.TestCase):
    def test_production_filter_runs_before_api(self):
        source = (ROOT / "scripts/publish-modrinth.sh").read_text()
        match = re.search(r"python3 - \"\$repository\" \"\$distribution\" \"\$tag\" \"\$publication_records\" <<'PY'\n(.*?)\nPY", source, re.S)
        self.assertIsNotNone(match)
        self.assertIn('select_records(Path(root), Path(directory), json.loads(records), tag, "modrinth")', match.group(1))
        self.assertLess(source.index('selected = select_records'), source.index('api=https://api.modrinth.com/v2'))

    def test_existing_primary_is_exact_single_file(self):
        source = (ROOT / "scripts/publish-modrinth.sh").read_text()
        match = re.search(r"if python3 - \"\$versions_response\".*? <<'PY'\n(.*?)\nPY", source, re.S)
        self.assertIsNotNone(match)
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            artifact = directory / "product.jar"
            artifact.write_bytes(b"actual mod")
            response = directory / "versions.json"
            file = {"filename": "product.jar", "primary": True, "hashes": {"sha512": hashlib.sha512(artifact.read_bytes()).hexdigest()}}
            existing = {"version_number": "0.4.4", "game_versions": ["1.16.5"], "loaders": ["forge"], "files": [file]}
            for values, code in (([], 1), ([existing], 0), ([dict(existing, files=[file, file])], 2),
                                 ([dict(existing, files=[dict(file, primary=False)])], 2),
                                 ([dict(existing, game_versions=["1.12.2"])], 2)):
                response.write_text(json.dumps(values))
                result = subprocess.run([sys.executable, "-B", "-", str(response), "0.4.4", '["1.16.5"]', "forge", str(artifact)],
                                        input=match.group(1), text=True, capture_output=True)
                self.assertEqual(result.returncode, code, result.stderr)

    def test_workflow_and_current_selection_guards(self):
        workflow = (ROOT / ".github/workflows/release.yml").read_text()
        self.assertIn("default: v0.4.4", workflow)
        self.assertIn("verify-release-package-source.py", workflow)
        self.assertIn("release-staged-${{ inputs.build_source_sha }}", workflow)
        self.assertNotIn("build-and-stage", workflow)
        self.assertEqual(json.loads((ROOT / "distribution/release-build-selection.json").read_text()), {"version": "0.4.4", "groups": []})
        source = (ROOT / "scripts/verify-release-package-source.py").read_text()
        for guard in ("verify-release-tag.sh", "run['head_sha'] != source", "run['conclusion'] != 'success'", ".github/workflows/minecraft-native.yml"):
            self.assertIn(guard, source)
        normal = (ROOT / ".github/workflows/minecraft-native.yml").read_text()
        self.assertIn("group: release-packages-${{ github.ref }}", normal)
        self.assertIn("cancel-in-progress: false", normal)
        self.assertIn("    permissions:\n      contents: read\n      actions: read", normal)
        self.assertIn("inputs.stage_only || (needs.build-packages.result == 'success' && inputs.build_targets == '')", normal)


if __name__ == "__main__":
    unittest.main()
