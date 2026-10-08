"""Source and synthetic package checks for the actual Forge anchor; no Gradle or game run."""
import importlib.util
from io import BytesIO
import json
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from minecraft_target_loaders import read_target_loaders, target_loaders

ROOT = Path(__file__).resolve().parents[1]


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / filename)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


PINS = module("forge_target_pins", "minecraft-target.py")
COMPILER = module("forge_target_compiler", "compile-native-target.py")
PACKAGE = module("forge_target_package", "verify-native-target-package.py")


def archive_bytes(entries):
    buffer = BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        for name, content in entries.items():
            archive.writestr(name, content)
    return buffer.getvalue()


def bytecode(major):
    return b"\xca\xfe\xba\xbe\x00\x00" + major.to_bytes(2, "big") + b"fixture"


class ForgeAnchorSourceTest(unittest.TestCase):
    def test_only_actual_forge_is_selected(self):
        self.assertEqual(target_loaders(ROOT, "1.19.2"), {"loaders": ["forge"], "nativeToolchain": "legacyForge"})
        pins = PINS.read_profile(ROOT, "1.19.2")
        self.assertEqual(set(pins), PINS.FORGE_FIELDS)
        self.assertEqual((pins["forge_version"], pins["mcp_version"], pins["java_version"]),
                         ("1.19.2-43.5.0", "1.19.2", "17"))
        command = COMPILER.commands(ROOT, "1.19.2")
        self.assertEqual(len(command), 1)
        self.assertEqual(command[0][0][-1], ":forge:assemble")
        for loaders in (("fabric",), ("neoforge",), ("forge", "neoforge")):
            with self.assertRaises(ValueError):
                COMPILER.commands(ROOT, "1.19.2", loaders=loaders)

    def test_old_loaders_and_profiles_keep_their_exact_shape(self):
        for target, selection in read_target_loaders(ROOT).items():
            if selection["loaders"] == ["forge"]:
                self.assertEqual(set(PINS.read_profile(ROOT, target)), PINS.FORGE1122_FIELDS if target == "1.12.2" else PINS.FORGE_FIELDS)
                continue
            self.assertEqual(selection["loaders"], ["fabric", "neoforge"])
            self.assertEqual(set(PINS.read_profile(ROOT, target)), PINS.MODERN_FIELDS)
        self.assertEqual(COMPILER.commands(ROOT, "26.2")[0][0][-2:], [":fabric:assemble", ":neoforge:assemble"])

    def test_forge_profile_rejects_fabric_neoforge_and_missing_actual_pins(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "gradle/minecraft-targets").mkdir(parents=True)
            shutil.copyfile(ROOT / "gradle/minecraft-target-loaders.json", root / "gradle/minecraft-target-loaders.json")
            text = (ROOT / "gradle/minecraft-targets/1.19.2.properties").read_text()
            path = root / "gradle/minecraft-targets/1.19.2.properties"
            for invalid in (text + "neoforge_version=wrong\n", text + "fabric_loader_version=wrong\n",
                            text.replace("forge_version=1.19.2-43.5.0\n", "")):
                path.write_text(invalid)
                with self.assertRaises(ValueError):
                    PINS.read_profile(root, "1.19.2")

    def test_native_recipe_reuses_selected_sources_and_keeps_official_forge_identity(self):
        thin = (ROOT / "forge/build.gradle").read_text()
        self.assertIn("project.name == 'forge' ? rootProject.file('neoforge')", (ROOT / "build-logic/src/main/groovy/multiloader-loader.gradle").read_text())
        self.assertIn("gradle/fml-loader.gradle", thin)
        self.assertLess(len(thin.splitlines()), 15)
        shared = (ROOT / "gradle/fml-loader.gradle").read_text()
        self.assertIn("if (actualForge) {\n        version = forge_version", shared)
        self.assertIn("from({ project(':engine-core').sourceSets.main.output })", shared)
        for module_name in ("common", "adapters/minecraft"):
            text = (ROOT / module_name / "build.gradle").read_text()
            self.assertIn("minecraftNativeToolchain == 'legacyForge'", text)
            self.assertIn("minecraftTargetProfile.getOrDefault('mcp_version'", text)
        selector = (ROOT / "gradle/minecraft-targets.gradle").read_text()
        self.assertIn("'1.19.2': '1.20.1'", selector)
        self.assertIn("GuideGraphics.wrap(poseStack)", (ROOT / "forge/src/targets/1.19.2/java/dev/openallay/neoforge/NeoForgeNativeHudRegistration.java").read_text())


class ForgeAnchorPackageTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.path = self.root / "forge.jar"
        for name in ("extension-api", "runtime-rhino"):
            (self.root / name / "build/libs").mkdir(parents=True)
        (self.root / "gradle/minecraft-targets").mkdir(parents=True)
        shutil.copyfile(ROOT / "gradle/minecraft-target-loaders.json", self.root / "gradle/minecraft-target-loaders.json")
        shutil.copyfile(ROOT / "gradle/minecraft-targets/1.19.2.properties", self.root / "gradle/minecraft-targets/1.19.2.properties")
        (self.root / "gradle.properties").write_text("rhino_version=fixture\n")
        (self.root / "extension-api/build.gradle").write_text("version = 'fixture'\n")
        self.sdk = archive_bytes({"dev/openallay/api/World.class": bytecode(52)})
        self.rhino = archive_bytes({"dev/latvian/mods/rhino/Context.class": bytecode(61)})
        (self.root / "extension-api/build/libs/openallay-extension-api-fixture.jar").write_bytes(self.sdk)
        (self.root / "runtime-rhino/build/libs/openallay-rhino-fixture.jar").write_bytes(self.rhino)
        self.engine = {"dev/openallay/FeatureServices.class": bytecode(61)}
        self.values = dict(self.engine)
        self.values.update({
            "META-INF/mods.toml": 'modLoader="javafml"\nloaderVersion="[43,)"\n[[mods]]\nmodId="openallay"\n[[dependencies.openallay]]\nmodId="minecraft"\nversionRange="[1.19.2]"\n[[dependencies.openallay]]\nmodId="forge"\nversionRange="[43.5.0,)"\n',
            "openallay.client.mixins.json": '{"refmap":"openallay.refmap.json"}',
            "openallay.refmap.json": '{}',
            "META-INF/jarjar/sdk.jar": self.sdk,
            "META-INF/jarjar/rhino.jar": self.rhino,
            "META-INF/jarjar/metadata.json": json.dumps({"jars": [
                {"identifier": {"group": "dev.openallay", "artifact": name},
                 "version": {"artifactVersion": "fixture", "range": "[fixture]"},
                 "path": "META-INF/jarjar/" + filename}
                for name, filename in (("extension-api", "sdk.jar"), ("runtime-rhino", "rhino.jar"))]}),
        })

    def verify(self):
        self.path.write_bytes(archive_bytes(self.values))
        with patch.object(PACKAGE, "ROOT", self.root):
            return PACKAGE.verify(self.path, "forge", "1.19.2", 17, self.engine)

    def test_forge_package_keeps_shared_bytes_and_single_identities(self):
        self.assertEqual(self.verify()["loader"], "forge")

    def test_fake_neoforge_descriptor_is_rejected(self):
        self.values["META-INF/neoforge.mods.toml"] = '[[mods]]\nmodId="openallay"'
        with self.assertRaisesRegex(ValueError, "descriptor"):
            self.verify()

    def test_changed_sdk_and_competing_nested_owner_are_rejected(self):
        self.values["META-INF/jarjar/duplicate.jar"] = self.sdk
        with self.assertRaisesRegex(ValueError, "one nested identity"):
            self.verify()

    def test_modern_nested_bytecode_is_rejected(self):
        self.values["META-INF/jarjar/modern.jar"] = archive_bytes({"foreign/Modern.class": bytecode(65)})
        with self.assertRaisesRegex(ValueError, "exceeds Java17"):
            self.verify()

    def test_unbundled_gate_still_rejects_unverified_builder_bytes(self):
        self.values["META-INF/openallay/bundled-extensions/builder.jar"] = b"fixture"
        with self.assertRaisesRegex(ValueError, "unverified Builder"):
            self.verify()


if __name__ == "__main__":
    unittest.main()
