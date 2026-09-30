"""Regression tests for source pinning and nested distribution checks. No game needed."""
from copy import deepcopy
from importlib.util import module_from_spec, spec_from_file_location
from io import BytesIO
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
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


def annotated_class(dist_values=("CLIENT",), mod_id="openallay_builder"):
    """Minimal class-file fixture with a real class-level JVM annotation table."""
    def integer(value, size=2):
        return value.to_bytes(size, "big")
    constants = ["RuntimeVisibleAnnotations", "Lnet/neoforged/fml/common/Mod;", "value",
                 mod_id, "dist", "Lnet/neoforged/api/distmarker/Dist;", *dist_values]
    pool = b"".join(b"\x01" + integer(len(item)) + item.encode("ascii") for item in constants)
    annotation = integer(2) + integer(2) + integer(3) + b"s" + integer(4)
    annotation += integer(5) + b"[" + integer(len(dist_values))
    for index, _ in enumerate(dist_values, start=7):
        annotation += b"e" + integer(6) + integer(index)
    body = integer(1) + annotation
    # No interfaces, fields, or methods. This fixture tests annotation parsing,
    # not JVM linkage; production entrypoints come from the Java compiler.
    return (b"\xca\xfe\xba\xbe" + integer(0) + integer(69)
            + integer(len(constants) + 1) + pool + b"\x00" * 12
            + integer(1) + integer(1) + integer(len(body), 4) + body)


class SourceLockTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.source = Path(self.temporary.name)
        self.lock = prepare.load_manifest(prepare.DEFAULT_MANIFEST)
        subprocess.run(["git", "init", "-q", str(self.source)], check=True)
        prepare.git(self.source, "config", "user.name", "Distribution Test")
        prepare.git(self.source, "config", "user.email", "test@example.invalid")
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
        self.assertEqual(prepare.verify_source(self.source, self.lock, True)["pinned"], False)

    def test_ignored_build_output_does_not_break_pin(self):
        (self.source / "build").mkdir()
        (self.source / "build/generated.jar").write_bytes(b"fixture")
        self.assertFalse(prepare.verify_source(self.source, self.lock)["dirty"])

    def test_missing_project_does_not_silently_skip(self):
        self.lock["project"] = "missing"
        with self.assertRaisesRegex(ValueError, "project is missing"):
            prepare.verify_source(self.source, self.lock)

    def test_lock_rejects_floating_ref_and_path_traversal(self):
        path = self.source / "lock.json"
        for field, value in [("revision", "main"), ("project", "../other")]:
            invalid = deepcopy(self.lock)
            (invalid["source"] if field == "revision" else invalid)[field] = value
            path.write_text(json.dumps(invalid), encoding="utf-8")
            with self.assertRaises(ValueError):
                prepare.load_manifest(path)

    def test_preparation_refuses_dirty_checkout_without_fetching(self):
        (self.source / "untracked.txt").write_text("changed", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "dirty source"):
            prepare.prepare_source(self.source, self.lock)


class PackageTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.lock = prepare.load_manifest(prepare.DEFAULT_MANIFEST)

    def fixture(self, loader, mutation=None):
        nested = {name: b"fixture" for name in verify.SHARED_ENTRIES}
        nested[verify.DESCRIPTOR] = json.dumps({"id": self.lock["extensionId"],
            "version": self.lock["version"], "loaders": [loader], "modIds": ["openallay_builder"]})
        if loader == "fabric":
            entrypoint = "dev.openallay.builder.fabric.BuilderFabricEntrypoint"
            nested["fabric.mod.json"] = json.dumps({"id": "openallay_builder", "version": self.lock["version"],
                "environment": "client", "depends": {"openallay": ">=0.2.3"}, "entrypoints": {"main": [entrypoint]}})
        else:
            entrypoint = "dev.openallay.builder.neoforge.BuilderNeoForgeEntrypoint"
            nested["META-INF/neoforge.mods.toml"] = (
                '[[mods]]\nmodId="openallay_builder"\nversion="' + self.lock["version"]
                + '"\n[[dependencies.openallay_builder]]\nmodId="openallay"\nordering="AFTER"\n')
        nested[entrypoint.replace(".", "/") + ".class"] = annotated_class() if loader == "neoforge" else b"fixture"
        nested["dev/openallay/"] = b""
        directory = "jars" if loader == "fabric" else "jarjar"
        nested_path = f"META-INF/{directory}/" + Path(self.lock["artifacts"][loader]).name
        provenance = {"schemaVersion": 1, "source": {**self.lock["source"], "dirty": False, "pinned": True},
            "project": self.lock["project"], "version": self.lock["version"], "modId": self.lock["modId"]}
        entries = {verify.PROVENANCE: json.dumps(provenance)}
        if loader == "fabric":
            entries["fabric.mod.json"] = json.dumps({"jars": [{"file": nested_path}]})
        else:
            entries["META-INF/jarjar/metadata.json"] = json.dumps({"jars": [{"path": nested_path,
                "identifier": {"group": "dev.openallay.builder", "artifact": "openallay-builder-neoforge-26.2"},
                "version": {"artifactVersion": self.lock["version"]}}]})
        if mutation:
            mutation(entries, nested, provenance)
            entries[verify.PROVENANCE] = json.dumps(provenance)
        entries[nested_path] = archive_bytes(nested)
        path = self.root / f"{loader}.jar"
        path.write_bytes(archive_bytes(entries))
        return path

    def test_both_registered_native_loader_packages_pass(self):
        for loader in ["fabric", "neoforge"]:
            with self.subTest(loader=loader):
                verify.verify_package(self.fixture(loader), loader, self.lock)

    def test_unregistered_nested_jar_fails(self):
        path = self.fixture("fabric", lambda outer, nested, provenance:
            outer.update({"fabric.mod.json": '{"jars": []}'}))
        with self.assertRaisesRegex(ValueError, "not registered"):
            verify.verify_package(path, "fabric", self.lock)

    def test_core_class_in_extension_fails(self):
        path = self.fixture("neoforge", lambda outer, nested, provenance:
            nested.update({"dev/openallay/OpenAllayBootstrap.class": b"duplicate"}))
        with self.assertRaisesRegex(ValueError, "duplicates OpenAllay"):
            verify.verify_package(path, "neoforge", self.lock)

    def test_flattened_builder_class_fails(self):
        path = self.fixture("fabric", lambda outer, nested, provenance:
            outer.update({"dev/openallay/builder/BuilderSession.class": b"flattened"}))
        with self.assertRaisesRegex(ValueError, "flattened"):
            verify.verify_package(path, "fabric", self.lock)

    def test_missing_skill_fails(self):
        path = self.fixture("fabric", lambda outer, nested, provenance:
            nested.pop("assets/openallay_builder/openallay_skills/minecraft-builder/SKILL.md"))
        with self.assertRaisesRegex(ValueError, "missing"):
            verify.verify_package(path, "fabric", self.lock)

    def test_unpinned_build_rejected_by_default_but_can_check_contents(self):
        path = self.fixture("fabric", lambda outer, nested, provenance:
            provenance["source"].update({"pinned": False, "dirty": True}))
        with self.assertRaisesRegex(ValueError, "Unpinned"):
            verify.verify_package(path, "fabric", self.lock)
        verify.verify_package(path, "fabric", self.lock, allow_unpinned=True)

    def test_fabric_common_environment_fails(self):
        def change(outer, nested, provenance):
            metadata = json.loads(nested["fabric.mod.json"])
            metadata["environment"] = "*"
            nested["fabric.mod.json"] = json.dumps(metadata)
        path = self.fixture("fabric", change)
        with self.assertRaisesRegex(ValueError, "client-only"):
            verify.verify_package(path, "fabric", self.lock)

    def test_neoforge_server_or_both_distributions_fail(self):
        for distributions in [(), ("DEDICATED_SERVER",), ("CLIENT", "DEDICATED_SERVER")]:
            with self.subTest(distributions=distributions):
                path = self.fixture("neoforge", lambda outer, nested, provenance:
                    nested.update({"dev/openallay/builder/neoforge/BuilderNeoForgeEntrypoint.class":
                                   annotated_class(distributions)}))
                with self.assertRaisesRegex(ValueError, "client-only"):
                    verify.verify_package(path, "neoforge", self.lock)

    def test_neoforge_wrong_mod_annotation_fails(self):
        path = self.fixture("neoforge", lambda outer, nested, provenance:
            nested.update({"dev/openallay/builder/neoforge/BuilderNeoForgeEntrypoint.class":
                           annotated_class(mod_id="unrelated")}))
        with self.assertRaisesRegex(ValueError, "@Mod identity"):
            verify.verify_package(path, "neoforge", self.lock)

    def test_annotation_parser_rejects_truncated_or_invalid_class(self):
        for content in [b"fixture", annotated_class()[:-1]]:
            with self.assertRaises(ValueError):
                verify.class_annotations(content)

    def test_wrong_revision_fails(self):
        path = self.fixture("fabric", lambda outer, nested, provenance:
            provenance["source"].update({"revision": "0" * 40}))
        with self.assertRaisesRegex(ValueError, "revision mismatch"):
            verify.verify_package(path, "fabric", self.lock)


if __name__ == "__main__":
    unittest.main()
