"""Offline regressions for one universal resource, source pinning, and provenance."""
from copy import deepcopy
from importlib.util import module_from_spec, spec_from_file_location
from io import BytesIO
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def module(name, filename):
    spec = spec_from_file_location(name, ROOT / "scripts" / filename)
    result = module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


prepare = module("prepare_distribution", "prepare-distribution.py")
verify = module("verify_bundled_extensions", "verify-bundled-extensions.py")


def archive_bytes(entries):
    stream = BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        for name, content in entries.items():
            archive.writestr(name, content)
    return stream.getvalue()


def java8_header(major=52):
    # Only the class header is read. These are not JVM-linkable fixtures.
    return b"\xca\xfe\xba\xbe\x00\x00" + major.to_bytes(2, "big")


def descriptor(lock):
    return {"schemaVersion": 2, "id": lock["extensionId"], "name": "Minecraft Builder",
        "version": lock["version"], "entrypoint": "dev.openallay.builder.BuilderExtension",
        "provider": "OpenAllay", "summary": "Construction on the integrated server.",
        "source": "https://github.com/nkanf-dev/OpenAllay-Extensions",
        "support": {"targets": [{"loader": loader, "minecraftVersionRange": "26.2",
            "openAllayVersionRange": "[0.4.1,)", "openAllayApiVersionRange": "[0.3.0,0.4.0)"}
            for loader in ("fabric", "neoforge")], "minimumJavaVersion": 8,
            "requiredHostFeatures": ["minecraft:world-access"], "validatedTargetIds": []},
        "requirements": {"capabilities": ["openallay_builder:world_write"], "extensions": [], "skills": []}}


def universal_entries(lock):
    entries = {name: java8_header() if name.endswith(".class") else b"canonical resource"
        for name in verify.SHARED_ENTRIES}
    entries[verify.DESCRIPTOR] = json.dumps(descriptor(lock))
    return entries


class SourceLockTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.source = Path(self.temporary.name)
        self.lock = prepare.load_manifest(prepare.DEFAULT_MANIFEST)
        subprocess.run(["git", "init", "-q", str(self.source)], check=True)
        prepare.git(self.source, "config", "user.name", "Distribution Test")
        prepare.git(self.source, "config", "user.email", "test@example.invalid")
        prepare.git(self.source, "remote", "add", "origin", self.lock["source"]["repository"])
        project = self.source / self.lock["project"]
        project.mkdir(parents=True)
        (project / "settings.gradle").write_text("// source fixture\n", encoding="utf-8")
        (self.source / ".gitignore").write_text("build/\n", encoding="utf-8")
        prepare.git(self.source, "add", ".")
        prepare.git(self.source, "-c", "commit.gpgsign=false", "commit", "-qm", "Fixture")
        self.lock["source"]["revision"] = prepare.git(self.source, "rev-parse", "HEAD")

    def test_exact_clean_pin_passes(self):
        self.assertEqual(prepare.verify_source(self.source, self.lock), {
            "revision": self.lock["source"]["revision"], "dirty": False, "pinned": True})

    def test_wrong_revision_fails_even_when_source_exists(self):
        self.lock["source"]["revision"] = "0" * 40
        with self.assertRaisesRegex(ValueError, "revision mismatch"):
            prepare.verify_source(self.source, self.lock)

    def test_dirty_source_requires_explicit_override(self):
        (self.source / "untracked.txt").write_text("changed", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "uncommitted"):
            prepare.verify_source(self.source, self.lock)
        self.assertFalse(prepare.verify_source(self.source, self.lock, True)["pinned"])

    def test_ignored_build_output_does_not_break_pin(self):
        (self.source / "build").mkdir()
        (self.source / "build/generated.jar").write_bytes(b"fixture")
        self.assertFalse(prepare.verify_source(self.source, self.lock)["dirty"])

    def test_missing_project_does_not_silently_skip(self):
        self.lock["project"] = "missing"
        with self.assertRaisesRegex(ValueError, "project is missing"):
            prepare.verify_source(self.source, self.lock)

    def test_repository_mismatch_fails(self):
        prepare.git(self.source, "remote", "set-url", "origin", "https://example.invalid/other.git")
        with self.assertRaisesRegex(ValueError, "origin"):
            prepare.verify_source(self.source, self.lock)

    def test_lock_rejects_legacy_unknown_missing_and_wrong_types(self):
        path = self.source / "lock.json"
        invalids = []
        for field in ("schemaVersion", "minecraftVersion", "modId", "artifacts"):
            item = deepcopy(self.lock)
            item[field] = {} if field == "artifacts" else "old"
            invalids.append(item)
        for field in self.lock:
            item = deepcopy(self.lock)
            del item[field]
            invalids.append(item)
        item = deepcopy(self.lock)
        item["source"]["revision"] = 123
        invalids.append(item)
        for item in invalids:
            with self.subTest(item=item):
                path.write_text(json.dumps(item), encoding="utf-8")
                with self.assertRaises(ValueError):
                    prepare.load_manifest(path)

    def test_lock_rejects_floating_ref_and_path_traversal(self):
        path = self.source / "lock.json"
        for field, value in [("revision", "main"), ("project", "../other"),
                ("artifact", "../other.jar"), ("artifact", "/other.jar"), ("artifact", "x\\other.jar")]:
            item = deepcopy(self.lock)
            (item["source"] if field == "revision" else item)[field] = value
            path.write_text(json.dumps(item), encoding="utf-8")
            with self.assertRaises(ValueError):
                prepare.load_manifest(path)

    def test_duplicate_json_member_fails(self):
        path = self.source / "lock.json"
        path.write_text(json.dumps(self.lock).replace('"version": "0.3.0"',
            '"version": "0.3.0", "version": "0.3.0"'))
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            prepare.load_manifest(path)

    def test_preparation_refuses_dirty_checkout_without_fetching(self):
        dirty = self.source / "untracked.txt"
        dirty.write_text("preserve", encoding="utf-8")
        with patch.object(prepare, "git", wraps=prepare.git) as calls:
            with self.assertRaisesRegex(ValueError, "dirty source"):
                prepare.prepare_source(self.source, self.lock)
        self.assertFalse(any("fetch" in call.args for call in calls.call_args_list))
        self.assertEqual(dirty.read_text(), "preserve")

    def test_preparation_refuses_non_checkout_without_overwrite(self):
        directory = self.source / "existing"
        directory.mkdir()
        data = directory / "keep.txt"
        data.write_text("keep")
        with self.assertRaisesRegex(ValueError, "overwrite"):
            prepare.prepare_source(directory, self.lock)
        self.assertEqual(data.read_text(), "keep")


class PackageTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.lock = prepare.load_manifest(prepare.DEFAULT_MANIFEST)

    def fixture(self, loader, mutation=None):
        nested = universal_entries(self.lock)
        provenance = {"source": {**self.lock["source"], "dirty": False, "pinned": True},
            **{field: self.lock[field] for field in ("project", "version", "extensionId", "openAllayApiVersion")},
            "artifact": {"path": verify.resource_path(self.lock), "sha256": ""}}
        # Ordinary loader dependencies remain registered. Builder must not be registered.
        if loader == "fabric":
            entries = {"fabric.mod.json": json.dumps({"id": "openallay",
                "jars": [{"file": "META-INF/jars/openallay-extension-api-0.3.0.jar"}]}),
                "META-INF/jars/openallay-extension-api-0.3.0.jar": archive_bytes({})}
        else:
            entries = {"META-INF/neoforge.mods.toml": '[[mods]]\nmodId="openallay"\n',
                "META-INF/jarjar/metadata.json": json.dumps({"jars": [{
                    "path": "META-INF/jarjar/openallay-extension-api-0.3.0.jar", "identifier": {
                        "group": "dev.openallay", "artifact": "openallay-extension-api"}}]}),
                "META-INF/jarjar/openallay-extension-api-0.3.0.jar": archive_bytes({})}
        if mutation:
            mutation(entries, nested, provenance)
        data = archive_bytes(nested)
        if not provenance["artifact"]["sha256"]:
            provenance["artifact"]["sha256"] = hashlib.sha256(data).hexdigest()
        entries[verify.resource_path(self.lock)] = data
        entries[verify.PROVENANCE] = json.dumps(provenance)
        path = self.root / f"{loader}.jar"
        path.write_bytes(archive_bytes(entries))
        return path

    def test_one_identical_unregistered_resource_passes_both_loaders(self):
        fabric, neoforge = self.fixture("fabric"), self.fixture("neoforge")
        self.assertEqual(verify.verify_package(fabric, "fabric", self.lock),
            verify.verify_package(neoforge, "neoforge", self.lock))
        verify.verify_packages(fabric, neoforge, self.lock)

    def test_pair_byte_identity_rejects_individually_valid_different_resources(self):
        fabric = self.fixture("fabric")
        neoforge = self.fixture("neoforge", lambda outer, nested, provenance:
            nested.update({"different.txt": b"different final artifact"}))
        verify.verify_package(neoforge, "neoforge", self.lock)
        with self.assertRaisesRegex(ValueError, "identical"):
            verify.verify_packages(fabric, neoforge, self.lock)

    def test_registered_builder_resource_fails(self):
        for loader in ("fabric", "neoforge"):
            def change(outer, nested, provenance):
                key = "fabric.mod.json" if loader == "fabric" else "META-INF/jarjar/metadata.json"
                field = "file" if loader == "fabric" else "path"
                outer[key] = json.dumps({"jars": [{field: verify.resource_path(self.lock)}]})
            with self.subTest(loader=loader):
                with self.assertRaisesRegex(ValueError, "registered"):
                    verify.verify_package(self.fixture(loader, change), loader, self.lock)

    def test_legacy_duplicate_builder_jar_fails(self):
        path = self.fixture("fabric", lambda outer, nested, provenance:
            outer.update({"META-INF/jars/openallay-builder-fabric-26.2-0.2.1.jar": archive_bytes({})}))
        with self.assertRaisesRegex(ValueError, "Exactly one"):
            verify.verify_package(path, "fabric", self.lock)

    def test_registered_other_named_legacy_builder_id_fails(self):
        def change(outer, nested, provenance):
            path = "META-INF/jars/innocent-name.jar"
            outer[path] = archive_bytes({"fabric.mod.json": '{"id":"openallay_builder"}'})
            outer["fabric.mod.json"] = json.dumps({"jars": [{"file": path}]})
        with self.assertRaisesRegex(ValueError, "Builder mod"):
            verify.verify_package(self.fixture("fabric", change), "fabric", self.lock)

    def test_sdk_game_loader_and_unrelocated_gson_payloads_fail(self):
        for name in ("dev/openallay/api/extension/Extension.class", "net/minecraft/World.class",
                "net/minecraftforge/Entry.class", "net/fabricmc/Entry.class", "net/neoforged/Entry.class",
                "com/google/gson/Gson.class", "dev/openallay/OpenAllayBootstrap.class", "baritone/Entry.class",
                "cpw/mods/Loader.class", "org/spongepowered/asm/Mixin.class"):
            with self.subTest(name=name):
                path = self.fixture("fabric", lambda outer, nested, provenance:
                    nested.update({name: java8_header()}))
                with self.assertRaisesRegex(ValueError, "payload|duplicates"):
                    verify.verify_package(path, "fabric", self.lock)

    def test_flattened_builder_class_fails(self):
        path = self.fixture("fabric", lambda outer, nested, provenance:
            outer.update({"dev/openallay/builder/BuilderSession.class": java8_header()}))
        with self.assertRaisesRegex(ValueError, "flattened"):
            verify.verify_package(path, "fabric", self.lock)

    def test_missing_canonical_skill_js_or_private_gson_fails(self):
        for name in ("assets/openallay_builder/building.js", "assets/openallay_builder/terrain.js",
                "assets/openallay_builder/presets.js",
                "assets/openallay_builder/openallay_skills/minecraft-builder/SKILL.md",
                "dev/openallay/builder/internal/gson/Strictness.class"):
            with self.subTest(name=name):
                path = self.fixture("fabric", lambda outer, nested, provenance: nested.pop(name))
                with self.assertRaisesRegex(ValueError, "missing"):
                    verify.verify_package(path, "fabric", self.lock)

    def test_checksum_mismatch_fails(self):
        path = self.fixture("fabric", lambda outer, nested, provenance:
            provenance["artifact"].update({"sha256": "0" * 64}))
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            verify.verify_package(path, "fabric", self.lock)

    def test_wrong_resource_path_fails(self):
        path = self.fixture("fabric", lambda outer, nested, provenance:
            provenance["artifact"].update({"path": "META-INF/jars/builder.jar"}))
        with self.assertRaisesRegex(ValueError, "artifact path"):
            verify.verify_package(path, "fabric", self.lock)

    def test_dirty_unpinned_package_requires_explicit_override(self):
        path = self.fixture("fabric", lambda outer, nested, provenance:
            provenance["source"].update({"pinned": False, "dirty": True}))
        with self.assertRaisesRegex(ValueError, "Unpinned"):
            verify.verify_package(path, "fabric", self.lock)
        verify.verify_package(path, "fabric", self.lock, allow_unpinned=True)

    def test_wrong_revision_fails(self):
        path = self.fixture("fabric", lambda outer, nested, provenance:
            provenance["source"].update({"revision": "0" * 40}))
        with self.assertRaisesRegex(ValueError, "revision mismatch"):
            verify.verify_package(path, "fabric", self.lock)

    def test_provenance_unknown_missing_and_boolean_types_fail(self):
        for mutate in (lambda p: p.update({"schemaVersion": 1}), lambda p: p.pop("extensionId"),
                lambda p: p["source"].update({"pinned": 1}), lambda p: p["source"].update({"dirty": "false"})):
            with self.subTest(mutate=mutate):
                path = self.fixture("fabric", lambda outer, nested, provenance: mutate(provenance))
                with self.assertRaises(ValueError):
                    verify.verify_package(path, "fabric", self.lock)

    def test_external_schema1_is_not_current_universal_manifest(self):
        def change(outer, nested, provenance):
            nested[verify.DESCRIPTOR] = json.dumps({"schemaVersion": 1, "id": self.lock["extensionId"],
                "loaders": ["fabric"], "modIds": ["openallay_builder"]})
        with self.assertRaises(ValueError):
            verify.verify_package(self.fixture("fabric", change), "fabric", self.lock)

    def test_manifest_unknown_missing_null_duplicate_and_nonfinite_fail(self):
        good = json.dumps(descriptor(self.lock))
        invalids = [good.replace('"schemaVersion": 2', '"schemaVersion": 2, "modIds": []'),
            good.replace('"schemaVersion": 2', '"schemaVersion": 2, "schemaVersion": 2'),
            good.replace('"minimumJavaVersion": 8', '"minimumJavaVersion": NaN'),
            good.replace('"minimumJavaVersion": 8', '"minimumJavaVersion": true'),
            good.replace('"requiredHostFeatures": ["minecraft:world-access"]', '"requiredHostFeatures": null'),
            good.replace('"requiredHostFeatures": ["minecraft:world-access"]',
                '"requiredHostFeatures": ["minecraft:world-access", "minecraft:world-access"]'),
            good + '{}']
        for invalid in invalids:
            with self.subTest(invalid=invalid):
                path = self.fixture("fabric", lambda outer, nested, provenance:
                    nested.update({verify.DESCRIPTOR: invalid}))
                with self.assertRaises(ValueError):
                    verify.verify_package(path, "fabric", self.lock)

    def test_wrong_sdk_support_and_entrypoint_fail(self):
        for mutate in (lambda d: d["support"]["targets"][0].update({"openAllayApiVersionRange": "[0.2.0,0.3.0)"}),
                lambda d: d.update({"entrypoint": "../Escape"}),
                lambda d: d["support"].update({"minimumJavaVersion": 9})):
            def change(outer, nested, provenance):
                value = descriptor(self.lock)
                mutate(value)
                nested[verify.DESCRIPTOR] = json.dumps(value)
            with self.subTest(mutate=mutate), self.assertRaises(ValueError):
                verify.verify_package(self.fixture("fabric", change), "fabric", self.lock)

    def test_legacy_mod_metadata_multirelease_and_entrypoints_fail(self):
        for name in ("fabric.mod.json", "META-INF/neoforge.mods.toml", "META-INF/mods.toml",
                "mcmod.info", "META-INF/versions/9/Test.class", "module-info.class",
                "dev/openallay/builder/fabric/BuilderFabricEntrypoint.class",
                "dev/openallay/builder/neoforge/BuilderNeoForgeEntrypoint.class"):
            with self.subTest(name=name):
                path = self.fixture("fabric", lambda outer, nested, provenance:
                    nested.update({name: java8_header()}))
                with self.assertRaisesRegex(ValueError, "payload"):
                    verify.verify_package(path, "fabric", self.lock)

    def test_class_magic_truncation_and_non_java8_major_fail(self):
        for content in (b"fixture", java8_header()[:7], java8_header(69)):
            with self.subTest(content=content):
                path = self.fixture("fabric", lambda outer, nested, provenance:
                    nested.update({"dev/openallay/builder/BuilderSession.class": content}))
                with self.assertRaisesRegex(ValueError, "class|Java8"):
                    verify.verify_package(path, "fabric", self.lock)

    def test_duplicate_zip_entries_fail(self):
        path = self.fixture("fabric")
        with zipfile.ZipFile(path, "a") as archive:
            import warnings
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                archive.writestr(verify.PROVENANCE, "{}")
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            verify.verify_package(path, "fabric", self.lock)

    def test_post_build_staging_uses_actual_hash_and_verifies_source(self):
        source = self.root / "source"
        artifact = source / self.lock["project"] / self.lock["artifact"]
        artifact.parent.mkdir(parents=True)
        data = archive_bytes(universal_entries(self.lock))
        artifact.write_bytes(data)
        output = self.root / "staged"
        evidence = {"revision": self.lock["source"]["revision"], "dirty": False, "pinned": True}
        with patch.object(verify.prepare, "verify_source", return_value=evidence) as check:
            verify.stage_distribution(source, output, self.lock)
        check.assert_called_once_with(source, self.lock, False)
        self.assertEqual((output / verify.resource_path(self.lock)).read_bytes(), data)
        result = json.loads((output / verify.PROVENANCE).read_text())
        self.assertEqual(result["artifact"]["sha256"], hashlib.sha256(data).hexdigest())

    def test_support_target_and_requirements_exact_shapes_fail(self):
        mutations = [lambda d: d["support"].update({"unknown": []}),
            lambda d: d["support"]["targets"][0].update({"loaders": []}),
            lambda d: d["support"]["targets"][0].pop("minecraftVersionRange"),
            lambda d: d["support"]["targets"][0].update({"minecraftVersionRange": "[]"}),
            lambda d: d["support"]["targets"].append(deepcopy(d["support"]["targets"][0])),
            lambda d: d.update({"requirements": None}),
            lambda d: d["requirements"].update({"permission": "granted"}),
            lambda d: d["requirements"].update({"skills": ["../escape"]})]
        for mutate in mutations:
            def change(outer, nested, provenance):
                value = descriptor(self.lock)
                mutate(value)
                nested[verify.DESCRIPTOR] = json.dumps(value)
            with self.subTest(mutate=mutate), self.assertRaises(ValueError):
                verify.verify_package(self.fixture("fabric", change), "fabric", self.lock)

    def test_corrupt_nested_zip_is_rejected(self):
        path = self.fixture("fabric")
        with zipfile.ZipFile(path) as archive:
            entries = {name: archive.read(name) for name in archive.namelist()}
        entries[verify.resource_path(self.lock)] = b"not a ZIP archive"
        provenance = json.loads(entries[verify.PROVENANCE])
        provenance["artifact"]["sha256"] = hashlib.sha256(entries[verify.resource_path(self.lock)]).hexdigest()
        entries[verify.PROVENANCE] = json.dumps(provenance)
        path.write_bytes(archive_bytes(entries))
        with self.assertRaises(zipfile.BadZipFile):
            verify.verify_package(path, "fabric", self.lock)

    def test_staging_failure_does_not_replace_previous_outputs(self):
        source = self.root / "source"
        output = self.root / "staged"
        output.mkdir()
        previous = output / verify.PROVENANCE
        previous.parent.mkdir(parents=True)
        previous.write_text("preserve")
        with patch.object(verify.prepare, "verify_source", side_effect=ValueError("dirty source")):
            with self.assertRaisesRegex(ValueError, "dirty source"):
                verify.stage_distribution(source, output, self.lock)
        self.assertEqual(previous.read_text(), "preserve")


class GradleContractTest(unittest.TestCase):
    def test_task_sequence_and_optout_are_source_only_and_sdk_only(self):
        text = (ROOT / "gradle/distribution.gradle").read_text()
        self.assertIn("if (bundleExtensions)", text)
        self.assertIn("dependsOn verifySource, ':extension-api:jar'", text)
        self.assertIn("dependsOn buildExtensions", text)
        self.assertIn("dependsOn stageExtensions", text)
        self.assertIn("-PopenallayExtensionApiJar=", text)
        self.assertIn("'build', 'verifyUniversalPackage'", text)
        self.assertIn("from(distributionDirectory)", text)
        self.assertIn("tasks.withType(ProcessResources).configureEach", text)
        self.assertIn("if (name == 'processResources')", text)
        self.assertNotIn("tasks.named('processResources'", text)
        self.assertNotIn("tasks.withType(Jar)", text)
        self.assertIn("command.add(command.indexOf('build'), 'clean')", text)
        self.assertIn("gradle.startParameter.offline", text)
        self.assertIn("outputs.upToDateWhen { false }", text)
        self.assertIn("mustRunAfter tasks.matching { it.name == 'clean' }", text)
        for old in ("bundledExtensionFabric", "bundledExtensionNeoforge", "extensionLock.artifacts",
                "extensionLock.minecraftVersion", "openallayCommonJar", "verifyLoaderPackages", "':common:jar'",
                "extension-test-support.gradle"):
            self.assertNotIn(old, text)


if __name__ == "__main__":
    unittest.main()
