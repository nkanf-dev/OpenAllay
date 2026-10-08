"""No-network checks for explicit immutable stock8 release admission."""
from copy import deepcopy
import hashlib
from importlib.util import module_from_spec, spec_from_file_location
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def load(name, filename):
    spec = spec_from_file_location(name, ROOT / "scripts" / filename)
    result = module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


catalog = load("stock8_catalog_test", "minecraft-artifacts.py")
compiler = load("stock8_compile_test", "compile-native-target.py")
materializer = load("stock8_materializer_test", "materialize-stock8-release.py")


class Stock8AdmissionTest(unittest.TestCase):
    def test_exact_ordinary_jar_family_and_counts(self):
        data = catalog.read_catalog(ROOT / "gradle/minecraft-artifacts.json")
        family = catalog.resolve(data, "1.12.2", "forge")
        self.assertEqual(family["packagingRecipe"], "forge-stock8")
        self.assertEqual(family["artifactKind"], "jar")
        self.assertEqual(family["publicationChannels"], ["github", "modrinth"])
        self.assertEqual(len(data["acceptedFamilies"]), 35)
        self.assertEqual(sum(len(row["supportedTargets"]) for row in data["acceptedFamilies"]), 50)
        self.assertEqual(len({target for row in data["acceptedFamilies"] for target in row["supportedTargets"]}), 27)
        for key, value in (("artifactKind", "zip"), ("packagingRecipe", "nested-mod"), ("publicationChannels", ["github"])):
            changed = dict(family, **{key: value})
            with self.subTest(key=key), self.assertRaises(ValueError):
                catalog.validate_family(changed, data["targetOrder"])

    def test_native_plan_materializes_no_forge12_compile(self):
        result = compiler.commands(ROOT, "1.12.2", loaders=("forge",), artifact_ids="forge-1.12.2")
        self.assertEqual([row[1] for row in result], ["root", "retained-stock8"])
        joined = " ".join(value for row in result for value in row[0])
        self.assertIn("materialize-stock8-release.py", joined)
        self.assertNotIn("native-package-probe", joined)
        self.assertNotIn("runClient", joined)
        self.assertNotIn("runServer", joined)
        self.assertNotIn("forge1122/gradlew", joined)
        with self.assertRaises(ValueError):
            compiler.commands(ROOT, "1.12.2", loaders=("forge",), artifact_ids="forge-1.16.5")

    def test_pin_and_custody_match_exact_current_product(self):
        pin = materializer.pin(ROOT)
        custody = json.loads((ROOT / materializer.CUSTODY_PATH).read_text())
        self.assertEqual(pin["jarSha256"], custody["jarSha256"])
        self.assertEqual(pin["productSource"], pin["provider"]["sourceRevision"])
        self.assertEqual(pin["jarName"], "openallay-forge-1.12.2-0.4.4.jar")

    def test_provider_identity_refuses_wrong_run_or_expiry(self):
        accepted = materializer.pin(ROOT)
        p = accepted["provider"]
        record = {"id": p["artifactId"], "expired": False, "digest": "sha256:" + p["sha256"],
                  "workflow_run": {"id": p["runId"], "head_sha": p["sourceRevision"]}}
        run = {"id": p["runId"], "head_sha": p["sourceRevision"], "status": "completed", "conclusion": "success",
               "event": "workflow_dispatch", "path": ".github/workflows/minecraft-native.yml",
               "repository": {"full_name": "test/project"}, "head_repository": {"full_name": "test/project"}}
        materializer.provider_identity(record, run, accepted, "test/project")
        for changed in (dict(run, conclusion="failure"), dict(run, head_sha="0" * 40), dict(run, path="untrusted.yml")):
            with self.assertRaises(ValueError):
                materializer.provider_identity(record, changed, accepted, "test/project")
        with self.assertRaises(ValueError):
            materializer.provider_identity(dict(record, expired=True), run, accepted, "test/project")

    def test_archive_rejects_duplicate_escape_symlink(self):
        for names, symlink in ((["../escape"], False), (["a", "a"], False), (["a"], True)):
            raw = io.BytesIO()
            with zipfile.ZipFile(raw, "w") as z:
                import warnings
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore")
                    for name in names:
                        item = zipfile.ZipInfo(name)
                        if symlink:
                            item.external_attr = 0o120777 << 16
                        z.writestr(item, b"value")
            with self.assertRaises(ValueError):
                materializer.archive(raw.getvalue())

    def test_source_binding_keeps_original_sources_and_refuses_feature_changes(self):
        packing, native_source, release = "a" * 40, "b" * 40, "c" * 40
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "common/src/main/java/Test.java"
            source.parent.mkdir(parents=True)
            source.write_bytes(b"genuine selected source")
            custody = {"nativeProducerSource": native_source, "packingSource": packing,
                       "selectedSourceHashes": {"common/src/main/java/Test.java": materializer.digest(source.read_bytes())}}
            def fake_git(root, *args):
                if args[0] == "diff":
                    return b"scripts/materialize-stock8-release.py\0docs/releases/0.4.4.md\0native-builds/forge16165/build.gradle\0"
                self.assertEqual(args, ("show", native_source + ":common/src/main/java/Test.java"))
                return b"genuine selected source"
            with patch.object(materializer, "git", side_effect=fake_git), patch.object(materializer.subprocess, "run"):
                result = materializer.source_custody(root, packing, custody, release)
                self.assertEqual(result["packingSource"], packing)
                self.assertEqual(result["nativeProducerSource"], native_source)
                self.assertEqual(result["releaseSourceSha"], release)
                source.write_bytes(b"changed production")
                with self.assertRaises(ValueError):
                    materializer.source_custody(root, packing, custody, release)
            with patch.object(materializer, "git", return_value=b"engine-core/src/main/java/Changed.java\0"), \
                    patch.object(materializer.subprocess, "run"), self.assertRaisesRegex(ValueError, "produce new accepted bytes"):
                materializer.source_custody(root, packing, custody, release)

    def test_unchanged_runtime_guards_and_raw_custody_survive_stage(self):
        text = (ROOT / "scripts/materialize-stock8-release.py").read_text()
        for guard in ("allPhysicalClassesAtMost52", "runtimeClassPathOwnership", "Selected native source changed",
                      "Complete product entry custody differs", "Original complete custody bytes differ",
                      "base64.b64decode", "originalSourceRecordsUnchanged", "Premain-Class:"):
            self.assertIn(guard, text)
        build = (ROOT / "scripts/build-minecraft-artifacts.py").read_text()
        self.assertIn('"artifactSourceSha": stock8["productSource"]', build)
        self.assertIn('"stageSourceSha": receipt["sourceSha"]', build)
        self.assertIn('sqlite["payloadSha256"] == sqlite_payload', build)
        self.assertIn('"Shared engine was changed or omitted: "', build)
        self.assertIn('"All families must bundle identical universal Builder bytes"', build)
        self.assertIn('"Stock8 product cannot embed a second Builder owner"', build)
        self.assertIn('"Stock8 raw Builder cannot be JarJar registered"', build)


if __name__ == "__main__":
    unittest.main()
