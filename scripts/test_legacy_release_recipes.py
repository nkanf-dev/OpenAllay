"""Focused stock legacy catalog/recipe fixtures. No downloads, Gradle or games."""
from copy import deepcopy
import hashlib
from importlib.util import module_from_spec, spec_from_file_location
from io import BytesIO
import json
import os
from pathlib import Path
import re
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = Path(os.environ.get("OPENALLAY_RECIPE_SOURCE_ROOT", str(ROOT)))


def load(name, filename):
    spec = spec_from_file_location(name, ROOT / "scripts" / filename)
    result = module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


CATALOG = load("legacy_catalog_test", "minecraft-artifacts.py")
COMPILER = load("legacy_compiler_test", "compile-native-target.py")
PINS = load("legacy_pins_test", "minecraft-target.py")
WIRING = load("legacy_wiring_test", "build-minecraft-artifacts.py")


def archive_bytes(entries):
    stream = BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        for name, content in entries.items():
            archive.writestr(name, content)
    return stream.getvalue()


class LegacyRecipeTest(unittest.TestCase):
    def setUp(self):
        self.data = CATALOG.read_catalog(ROOT / "gradle/minecraft-artifacts.json")
        self.f16 = CATALOG.resolve(self.data, "1.16.5", "forge")

    def test_exact_release_math_and_modern_identities_preserved(self):
        families = self.data["acceptedFamilies"]
        self.assertEqual((len(families), len(self.data["targetOrder"]), sum(len(f["supportedTargets"]) for f in families)), (34, 27, 49))
        modern = [{key: value for key, value in f.items() if key not in ("packagingRecipe", "artifactKind", "publicationChannels")}
                  for f in families if f["packagingRecipe"] == "nested-mod"]
        self.assertEqual(len(modern), 33)
        self.assertEqual(hashlib.sha256(json.dumps(modern, sort_keys=True, separators=(",", ":")).encode()).hexdigest(), "b3ade78e4f9490c90b4cfca39f3a27a345fec67d7d9ae2b97d48e7abe849b0e1")
        modrinth = [f for f in families if "modrinth" in f["publicationChannels"]]
        self.assertEqual((len(modrinth), sum(len(f["supportedTargets"]) for f in modrinth),
                          len({t for f in modrinth for t in f["supportedTargets"]})), (34, 49, 26))
        self.assertEqual(len(WIRING.groups(self.data)), 19)

    def test_legacy_kind_recipe_channels_are_exact_not_inferred(self):
        with self.assertRaisesRegex(ValueError, "candidates do not publish"):
            CATALOG.resolve(self.data, "1.12.2", "forge")
        self.assertIn({"loaders":["forge"],"buildTarget":"1.12.2","targets":["1.12.2"],"publishing":False},
                      self.data["candidateIntervals"])
        self.assertEqual((self.f16["artifactKind"], self.f16["packagingRecipe"], self.f16["publicationChannels"]),
                         ("jar", "forge-flat", ["github", "modrinth"]))
        for family in (self.f16, CATALOG.resolve(self.data, "26.2", "fabric")):
            self.assertEqual(CATALOG.describe(family, "0.4.4")["filename"], family["filenameTemplate"].replace("{version}", "0.4.4"))
            for key, value in (("packagingRecipe", "nested-mod" if family == self.f16 else "forge-flat"),
                               ("artifactKind", "zip" if family["artifactKind"] == "jar" else "jar"),
                               ("publicationChannels", ["github"]),
                               ("filenameTemplate", "openallay-fake-{version}.jar")):
                changed = dict(family, **{key: value})
                if changed != family:
                    with self.subTest(family=family["id"], key=key), self.assertRaises(ValueError):
                        CATALOG.validate_family(changed, self.data["targetOrder"])
        with self.assertRaises(ValueError):
            CATALOG.validate_family(CATALOG.family_for("forge", "1.16.5", ["1.16.5", "1.18.2"]), self.data["targetOrder"])
        changed = dict(self.f16, schemaVersion=1)
        with self.assertRaises(ValueError):
            CATALOG.validate_family(changed, self.data["targetOrder"])

    def test_actual_external_profiles_and_native_source_roots(self):
        for target, forge in (("1.16.5", "36.2.42"),):
            pins = PINS.read_profile(ROOT, target)
            self.assertEqual((pins["minecraft_version"], pins["java_version"], pins["forge_version"]),
                             (target, "17", target + "-" + forge))
            self.assertNotIn("neoforge_version", pins)
            self.assertNotIn("fabric_version", pins)
            with patch.object(WIRING, "ROOT", ROOT):
                chain = WIRING.selected_native_roots(target)
            self.assertEqual(chain[-1], target)
            self.assertIn("1.18.2", chain)
            for owner in ("common", "adapters/minecraft", "forge"):
                self.assertTrue((SOURCE_ROOT / owner / "src/targets" / target).is_dir(), (owner, target))
        for owner in ("common", "adapters/minecraft", "forge"):
            self.assertTrue((SOURCE_ROOT / owner / "src/targets/1.12.2").is_dir())

    def test_legacy_exact_commands_never_compile_a_root_loader_alias(self):
        for family in (self.f16,):
            target = family["buildTarget"]
            commands = COMPILER.commands(ROOT, target, loaders=("forge",), artifact_ids=family["id"])
            self.assertEqual([runtime for _, runtime in commands], ["root", "legacy-forge"])
            canonical, preparer = [command for command, _ in commands]
            self.assertIn("-PminecraftTarget=26.2", canonical)
            self.assertIn(":buildBundledExtensions", canonical)
            self.assertFalse(any(value.startswith((":forge:", ":neoforge:", ":fabric:")) for value in canonical))
            self.assertEqual(preparer[:3], ["python3", "-B", str(ROOT / "scripts/prepare-legacy-forge-release.py")])
            self.assertEqual(preparer[preparer.index("--target") + 1], target)
            self.assertEqual(preparer[preparer.index("--family") + 1], family["id"])
            release_version = WIRING.version()
            self.assertEqual(preparer[preparer.index("--version") + 1], release_version)
            output = WIRING.package_path(family, release_version)
            self.assertEqual(preparer[preparer.index("--output") + 1], str(output))
            self.assertEqual(preparer[-1], str(output) + ".packaging.json")
            for options in ({}, {"artifact_ids": "forge-1.18.2"}, {"candidate_ids": family["id"]},
                            {"loaders": ("neoforge",), "artifact_ids": family["id"]}):
                with self.subTest(target=target, options=options), self.assertRaises(ValueError):
                    COMPILER.commands(ROOT, target, **options)
            calls = []
            with self.assertRaisesRegex(ValueError, "remotely only"):
                COMPILER.compile_target(ROOT, target, environment={}, artifact_ids=family["id"], execute=lambda *a, **k: calls.append(a))
            self.assertEqual(calls, [])

    def test_forge12_candidate_cannot_use_obsolete_or_root_release_recipe(self):
        for options in ({}, {"artifact_ids":"forge-1.12.2"}, {"candidate_ids":"forge-1.12.2"}):
            with self.assertRaisesRegex(ValueError, "Java8 port is in progress"):
                COMPILER.commands(ROOT,"1.12.2",**options)

    def test_modern_compiler_command_semantics_unchanged(self):
        original_path = SOURCE_ROOT / "scripts/compile-native-target.py"
        if original_path.read_bytes() == (ROOT / "scripts/compile-native-target.py").read_bytes():
            # In integrated CI exact modern plans are asserted without a prepatch file.
            self.assertEqual(COMPILER.commands(ROOT, "26.2")[0][0][-2:], [":fabric:assemble", ":neoforge:assemble"])
        else:
            spec = spec_from_file_location("original_modern_compiler", original_path)
            original = module_from_spec(spec)
            spec.loader.exec_module(original)
            for target in self.data["targetOrder"][2:]:
                self.assertEqual(COMPILER.commands(ROOT, target), original.commands(ROOT, target))
        for target in COMPILER.EARLY:
            self.assertEqual(COMPILER.commands(ROOT, target)[1][1], "java21")

    def test_empty_current_reuse_selection_is_valid(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            file = root / WIRING.RELEASE_BUILD_SELECTION
            file.parent.mkdir(parents=True)
            file.write_text(json.dumps({"version": "0.4.4", "groups": []}))
            with patch.object(WIRING, "ROOT", root), patch.object(WIRING, "version", return_value="0.4.4"), patch.object(WIRING, "catalog", return_value=self.data):
                self.assertEqual(WIRING.read_reuse_selection(), {})
                file.write_text(json.dumps({"version": "0.4.3", "groups": []}))
                with self.assertRaises(ValueError):
                    WIRING.read_reuse_selection()

    def test_flat_verifier_is_mandatory_and_exact(self):
        class Stub:
            def verify_release(self, *args):
                return {"coreBytes": b"fixture", "sqlite": {"artifactSha256": "a" * 64, "payloadSha256": "b" * 64},
                        "sharedRuntimes": {"extension-api": "c" * 64, "runtime-rhino": "d" * 64}}
        with patch.object(WIRING, "module", return_value=Stub()) as provider:
            self.assertEqual(WIRING.legacy_package(Path("fixture.jar"), self.f16, "0.4.4")["coreBytes"], b"fixture")
            provider.assert_called_once_with("legacy_release_package", "package-legacy-forge-release.py")
        with self.assertRaises(ValueError):
            WIRING.legacy_package(Path("fixture.jar"), dict(self.f16, artifactKind="zip"), "0.4.4")
        stub = Stub()
        stub.verify_release = lambda *args: {"coreBytes": b"fixture"}
        with patch.object(WIRING, "module", return_value=stub), self.assertRaises(ValueError):
            WIRING.legacy_package(Path("fixture.jar"), self.f16, "0.4.4")

    def test_legacy_engine_builder_and_sqlite_match_every_family(self):
        engine = {"dev/openallay/guide/GuideService.class": b"\xca\xfe\xba\xbe\x00\x00\x00=fixture",
                  "dev/openallay/FeatureServices.class": b"\xca\xfe\xba\xbe\x00\x00\x00=fixture"}
        manifests = {name: hashlib.sha256(value).hexdigest() for name, value in engine.items()}
        families = [self.f16]
        lock = {"source": {"revision": "6e977110cbe8e0ca0b39c012f0cdfc10bafffef2"}}
        with tempfile.TemporaryDirectory() as folder:
            stage = Path(folder)
            for f in families:
                (stage / CATALOG.describe(f, WIRING.version())["filename"]).write_bytes(b"outer fixture")
            proof = {"coreBytes": archive_bytes(engine), "sqlite": {"artifactSha256": "a" * 64, "payloadSha256": "b" * 64},
                     "sharedRuntimes": {"extension-api": "c" * 64, "runtime-rhino": "d" * 64}}
            with patch.object(WIRING.builder.prepare, "load_manifest", return_value=lock), \
                 patch.object(WIRING, "legacy_package", return_value=proof), \
                 patch.object(WIRING, "legacy_builder", return_value=b"sameBuilder"), \
                 patch.object(WIRING.tokenizer, "verify") as modern_tokenizer:
                records = WIRING.verify(families, stage, engine_manifest=manifests)
                self.assertEqual([r["artifactKind"] for r in records], ["jar"])
                modern_tokenizer.assert_not_called()
                proof["coreBytes"] = archive_bytes(dict(engine, **{"dev/openallay/FeatureServices.class": b"changed"}))
                with self.assertRaisesRegex(ValueError, "Shared engine"):
                    WIRING.verify(families, stage, engine_manifest=manifests)
                proof["coreBytes"] = archive_bytes(engine)

    def test_staged_asset_scan_cannot_hide_extra_zip(self):
        with tempfile.TemporaryDirectory() as folder:
            stage = Path(folder)
            (stage / CATALOG.describe(self.f16, WIRING.version())["filename"]).write_bytes(b"jar")
            (stage / "obsolete-forge12.zip").write_bytes(b"wrong")
            with self.assertRaisesRegex(ValueError, "exactly the selected"):
                WIRING.verify([self.f16], stage, engine_manifest={"dev/openallay/FeatureServices.class": "a" * 64})


if __name__ == "__main__":
    unittest.main()
