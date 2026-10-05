"""Offline B1 source contracts and tiny source-family fixtures.

These tests do not execute Groovy or Gradle. Fixture selection is an independent
model of file-level replacement, not compile, package, or native acceptance.
"""
from pathlib import Path
import re
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

PROFILE_FIELDS = {
    "java_version", "minecraft_version", "minecraft_version_range", "neo_form_version",
    "fabric_version", "fabric_loader_version", "neoforge_version", "neoforge_loader_version_range",
    "jei_version", "rei_version", "architectury_version", "fabric_command_api_version",
    "fabric_resource_loader_version", "fabric_networking_api_version", "fabric_message_api_version", "fabric_lifecycle_events_version",
    "fabric_key_mapping_api_version", "fabric_rendering_version",
}
FABRIC_COMPONENTS = {
    "fabric-command-api-v2": "fabric_command_api_version",
    "fabric-resource-loader-v1": "fabric_resource_loader_version",
    "fabric-networking-api-v1": "fabric_networking_api_version",
    "fabric-message-api-v1": "fabric_message_api_version",
    "fabric-lifecycle-events-v1": "fabric_lifecycle_events_version",
    "fabric-key-mapping-api-v1": "fabric_key_mapping_api_version",
    "fabric-rendering-v1": "fabric_rendering_version",
}
PINS_26_2 = {
    "java_version": "25", "minecraft_version": "26.2", "minecraft_version_range": "[26.2, 26.3)",
    "neo_form_version": "26.2-1", "fabric_version": "0.152.1+26.2", "fabric_loader_version": "0.19.3",
    "neoforge_version": "26.2.0.25-beta", "neoforge_loader_version_range": "[4,)",
    "jei_version": "30.13.0.86", "rei_version": "26.2.820", "architectury_version": "21.0.4",
    "fabric_command_api_version": "3.1.0+00cb03469c", "fabric_resource_loader_version": "2.0.13+9edec1269c",
    "fabric_networking_api_version": "6.3.3+72073ef033", "fabric_message_api_version": "7.0.7+086d547a9c", "fabric_lifecycle_events_version": "4.1.3+4575b05f9c",
    "fabric_key_mapping_api_version": "2.0.5+e2bdee789c", "fabric_rendering_version": "25.1.6+46a6d00c9c",
}
PINS_26_3 = {
    "java_version": "25", "minecraft_version": "26.3", "minecraft_version_range": "[26.3]",
    "neo_form_version": "26.3-1", "fabric_version": "0.161.0+26.3", "fabric_loader_version": "0.19.5",
    "neoforge_version": "26.3.0.48-beta", "neoforge_loader_version_range": "[12,)",
    "jei_version": "31.9.0.57", "rei_version": "26.3.823", "architectury_version": "22.0.3",
    "fabric_command_api_version": "3.1.2+fcdff87f5d", "fabric_resource_loader_version": "3.0.4+fcdff87f5d",
    "fabric_networking_api_version": "6.3.8+fcdff87f5d", "fabric_message_api_version": "7.0.10+3434d6d95d", "fabric_lifecycle_events_version": "4.1.9+ffef5f675d",
    "fabric_key_mapping_api_version": "2.0.8+3434d6d95d", "fabric_rendering_version": "27.0.14+901a437c5d",
}


def properties(text):
    """Read only the simple key=value syntax used in these checked-in fixtures."""
    result = {}
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith(("#", "!")):
            continue
        key, value = line.split("=", 1)
        if key in result:
            raise ValueError("duplicate field: " + key)
        result[key] = value
    return result


def validate_profile(text, target="26.2", competing=()):
    if not (ROOT / "gradle/minecraft-targets" / (target + ".properties")).is_file():
        raise ValueError("unknown target")
    profile = properties(text)
    if set(profile) != PROFILE_FIELDS:
        raise ValueError("missing or unknown fields")
    if any(not value or value != value.strip() for value in profile.values()):
        raise ValueError("empty or padded field")
    if profile["minecraft_version"] != target or not re.fullmatch(r"[1-9][0-9]*", profile["java_version"]):
        raise ValueError("native target or Java mismatch")
    if PROFILE_FIELDS.intersection(competing):
        raise ValueError("competing field")
    return profile


def selected_entries(base, override, kind):
    """Model replacement by exact relative path, not glob or package heuristics."""
    def entries(root):
        return {path.relative_to(root).as_posix(): path for path in root.rglob("*")
                if path.is_file() and (kind != "java" or path.suffix == ".java")}
    base_entries, override_entries = entries(base), entries(override)
    selected = [(relative, path) for relative, path in base_entries.items() if relative not in override_entries]
    selected.extend(override_entries.items())
    return selected


def validate_entries(entries, kind):
    seen = {}
    package_pattern = re.compile(r"^\s*package\s+([\w.$]+)\s*;", re.MULTILINE)
    for relative, path in entries:
        identity = relative
        if kind == "java":
            package_match = package_pattern.search(path.read_text(encoding="utf-8"))
            package = package_match.group(1) if package_match else ""
            expected = (package.replace(".", "/") + "/" if package else "") + path.name
            if relative != expected:
                raise ValueError("package/file mismatch")
            identity = (package + "." if package else "") + path.stem
        if identity in seen and seen[identity] != path:
            raise ValueError("duplicate selected identity: " + identity)
        seen[identity] = path
    return seen


def write_fixture(root, relative, content):
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return path


class MinecraftTargetProfileSourceTest(unittest.TestCase):
    def source(self, relative):
        return (ROOT / relative).read_text(encoding="utf-8")

    def profile(self, target):
        return self.source("gradle/minecraft-targets/" + target + ".properties")

    def test_default_profile_retains_every_current_26_2_pin(self):
        self.assertEqual(PINS_26_2, validate_profile(self.profile("26.2")))
        selector = self.source("gradle/minecraft-targets.gradle")
        self.assertIn("getOrElse('26.2')", selector)
        for target in ("26.1", "26.1.1", "26.1.2"):
            self.assertIn("'" + target + "': '26.1'", selector)
        self.assertIn("'1.21.11': '1.21.11'", selector)

    def test_explicit_26_3_is_the_audited_candidate_tuple(self):
        self.assertEqual(PINS_26_3, validate_profile(self.profile("26.3"), "26.3"))
        self.assertIn("compile candidate only", self.profile("26.3"))
        self.assertNotIn("validatedTargetIds", self.source("gradle/minecraft-targets.gradle"))

    def test_1_21_11_build_family(self):
        profile = validate_profile(self.profile("1.21.11"), "1.21.11")
        self.assertEqual(("21", "1.21.11-20251209.172050"),
                         (profile["java_version"], profile["neo_form_version"]))
        fabric = self.source("fabric/build.gradle")
        self.assertLess(fabric.index("pluginManager.apply("), fabric.index("dependencies {"))
        self.assertIn("mappings(loom.officialMojangMappings())", fabric)
        self.assertIn("if (!remapMinecraft) include(project", fabric)
        self.assertIn("Integer.parseInt(java_version)", self.source("adapters/minecraft-26.2/build.gradle"))

    def test_descending_primitive_profiles_reuse_native_families_with_external_component_coordinates(self):
        selector = self.source("gradle/minecraft-targets.gradle")
        for target in ("1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10"):
            with self.subTest(target=target):
                profile = validate_profile(self.profile(target), target)
                self.assertEqual("21", profile["java_version"])
                self.assertEqual(target, profile["fabric_version"].split("+", 1)[1])
                self.assertEqual("[" + target + "]", profile["minecraft_version_range"])
        for pair in ("'1.21.7': '1.21.8'", "'1.21.9': '1.21.10'",
                     "'1.21.2': '1.21.3'", "'1.21.3': '1.21.4'", "'1.21.4': '1.21.5'", "'1.21.5': '1.21.6'", "'1.21.6': '1.21.8'", "'1.21.8': '1.21.10'",
                     "'1.21.10': '1.21.11'"):
            self.assertIn(pair, selector)
        self.assertFalse((ROOT / "common/src/targets/1.21.7/java").exists())
        self.assertFalse((ROOT / "common/src/targets/1.21.9/java").exists())
        self.assertIn("'1.21.2': '1.21.1'", selector)
        self.assertIn("'1.21.3': '1.21.1'", selector)
        self.assertIn("'1.21.6': '1.21.5'", selector)
        self.assertIn(".getOrDefault(selectedTarget, selectedTarget)", selector)
        for relative in ("common/build.gradle", "fabric/build.gradle", "neoforge/build.gradle"):
            source = self.source(relative)
            self.assertIn("jei-${jeiArtifactTarget}", source)
            self.assertNotIn("jei-${minecraft_version}", source)
        self.assertIn("minecraftNativeSourceFamilies.contains('1.21.8')", self.source("fabric/build.gradle"))

    def test_profile_is_loaded_first_and_shared_product_pins_stay_shared(self):
        root_build = self.source("build.gradle")
        self.assertLess(root_build.index("apply from: 'gradle/minecraft-targets.gradle'"),
                        root_build.index("apply from: 'gradle/distribution.gradle'"))
        shared = properties(self.source("gradle.properties"))
        self.assertFalse(PROFILE_FIELDS.intersection(shared))
        for key, value in {"version": "0.4.2", "group": "dev.openallay", "sqlite_jdbc_version": "3.50.3.0",
                           "commonmark_version": "0.28.0", "rhino_version": "2101.2.8-build.91",
                           "jtokkit_version": "1.1.0"}.items():
            self.assertEqual(value, shared[key])
        selector = self.source("gradle/minecraft-targets.gradle")
        self.assertIn("allprojects { candidate ->", selector)
        self.assertIn("profile.each { key, value -> candidate.extensions.extraProperties.set(key, value) }", selector)
        self.assertIn("}.asImmutable()", selector)

    def test_unknown_missing_duplicate_blank_and_competing_fields_reject(self):
        text = self.profile("26.2")
        invalid = [text + "runtime_version=1\n", text.replace("java_version=25\n", ""),
                   text + "java_version=25\n", text.replace("java_version=25", "java_version= "),
                   text.replace("minecraft_version=26.2", "minecraft_version=26.3")]
        for candidate in invalid:
            with self.subTest(candidate=candidate), self.assertRaises(ValueError):
                validate_profile(candidate)
        with self.assertRaises(ValueError):
            validate_profile(text, "1.12.2")
        with self.assertRaises(ValueError):
            validate_profile(text, competing={"fabric_loader_version"})
        selector = self.source("gradle/minecraft-targets.gradle")
        for required in ["containsKey(key)", "profileFields - actualFields", "actualFields - profileFields",
                         "providers.gradleProperty(key).isPresent()", "extraProperties.has(key)"]:
            self.assertIn(required, selector)

    def test_six_fabric_components_read_the_selected_profile(self):
        fabric = self.source("fabric/build.gradle")
        for module, field in FABRIC_COMPONENTS.items():
            artifact = ('${keyApiArtifact}' if field == 'fabric_key_mapping_api_version'
                        else '${resourceApiArtifact}' if field == 'fabric_resource_loader_version'
                        else module)
            self.assertIn('net.fabricmc.fabric-api:' + artifact + ':${' + field + '}', fabric)
            self.assertNotIn('net.fabricmc.fabric-api:' + module + ':' + PINS_26_2[field], fabric)

    def test_exact_path_replacement_keeps_override_and_unrelated_shared_files(self):
        with tempfile.TemporaryDirectory(prefix="openallay-source-fixture-") as folder:
            root = Path(folder)
            base = root / "src/main/java"
            write_fixture(base, "demo/Native.java", "package demo; public class Native {}")
            shared = write_fixture(base, "demo/Shared.java", "package demo; public class Shared {}")
            target_262 = root / "src/targets/26.2/java"
            target_263 = root / "src/targets/26.3/java"
            native = write_fixture(target_263, "demo/Native.java", "package demo; public class Native { int newer; }")
            self.assertEqual(2, len(validate_entries(selected_entries(base, target_262, "java"), "java")))
            selected = validate_entries(selected_entries(base, target_263, "java"), "java")
            self.assertEqual({"demo.Native": native, "demo.Shared": shared}, selected)
        family = self.source("gradle/minecraft-source-family.gradle")
        self.assertIn("element.file.toPath().startsWith(it.toPath())", family)
        self.assertIn("roots.drop(index + 1).any", family)
        self.assertIn("new File(it, element.relativePath.pathString).isFile()", family)
        self.assertIn("families.collect { file(\"src/targets/${it}/java\") }", family)
        self.assertIn("families.collect { file(\"src/targets/${it}/resources\") }", family)
        self.assertIn("if (!families.contains(selectedTarget))", family)
        self.assertNotIn("**/Native.java", family)

    def test_java_loader_collisions_and_misplaced_fqn_fail(self):
        with tempfile.TemporaryDirectory(prefix="openallay-source-fixture-") as folder:
            root = Path(folder)
            common = root / "common"
            loader = root / "loader"
            first = write_fixture(common, "demo/Native.java", "package demo; public class Native {}")
            second = write_fixture(loader, "demo/Native.java", "package demo; public class Native {}")
            with self.assertRaisesRegex(ValueError, "duplicate"):
                validate_entries([("demo/Native.java", first), ("demo/Native.java", second)], "java")
            misplaced = write_fixture(loader, "wrong/Native.java", "package demo; public class Native {}")
            with self.assertRaisesRegex(ValueError, "package/file"):
                validate_entries([("wrong/Native.java", misplaced)], "java")
        family = self.source("gradle/minecraft-source-family.gradle")
        self.assertIn("Duplicate selected Java FQN", family)
        self.assertIn("Java package/file path mismatch", family)
        loader_source = self.source("build-logic/src/main/groovy/multiloader-loader.gradle")
        self.assertIn("validateSelectedMinecraftJava([sourceSets.main.allJava, commonSources.allJava])", loader_source)

    def test_resources_replace_exactly_and_loader_collision_fails(self):
        with tempfile.TemporaryDirectory(prefix="openallay-source-fixture-") as folder:
            root = Path(folder)
            base, overrides, loader = root / "base", root / "overrides", root / "loader"
            write_fixture(base, "openallay.client.mixins.json", '{"client":["Base"]}')
            shared = write_fixture(base, "assets/openallay/shared.txt", "shared")
            selected_mixin = write_fixture(overrides, "openallay.client.mixins.json", '{"client":["Selected"]}')
            selected = selected_entries(base, overrides, "resources")
            self.assertEqual({"openallay.client.mixins.json": selected_mixin,
                              "assets/openallay/shared.txt": shared}, validate_entries(selected, "resources"))
            duplicate = write_fixture(loader, "openallay.client.mixins.json", '{"client":["Duplicate"]}')
            with self.assertRaisesRegex(ValueError, "duplicate"):
                validate_entries(selected + [("openallay.client.mixins.json", duplicate)], "resources")
        family = self.source("gradle/minecraft-source-family.gradle")
        self.assertIn("Duplicate selected resource", family)
        loader_source = self.source("build-logic/src/main/groovy/multiloader-loader.gradle")
        self.assertIn("validateSelectedMinecraftResources([sourceSets.main.resources, commonSources.resources])", loader_source)
        self.assertNotIn("DuplicatesStrategy.EXCLUDE", family + loader_source)

    def test_loader_uses_common_selected_sources_and_keeps_one_output_tree(self):
        common = self.source("common/build.gradle")
        loader = self.source("build-logic/src/main/groovy/multiloader-loader.gradle")
        self.assertNotIn("sourceDirectories.singleFile", common)
        self.assertNotIn("configurations.commonJava", loader)
        self.assertNotIn("configurations.commonResources", loader)
        self.assertIn("evaluationDependsOn(':common')", loader)
        self.assertIn("source(commonSources.allJava)", loader)
        self.assertIn("from(commonSources.resources)", loader)
        self.assertIn("from(commonSources.allJava)", loader)
        self.assertIn("inputs.property('minecraftTarget', rootProject.ext.minecraftTarget)", loader)
        self.assertIn("duplicatesStrategy = DuplicatesStrategy.FAIL", loader)
        family = self.source("gradle/minecraft-source-family.gradle")
        self.assertIn("inputs.files(sourceSets.main.allJava, minecraftMixinSelection.rawJava)", family)
        self.assertIn("inputs.files(sourceSets.main.resources)", family)
        self.assertIn("withPathSensitivity(PathSensitivity.RELATIVE)", family)
        self.assertIn("dependsOn(validateSelection)", family)
        build = self.source("build.gradle")
        self.assertIn("version '1.17.21'", build)
        self.assertIn("version '2.0.148'", build)
        # No buildDirectory/run/cache mutation belongs to this foundation.
        for text in (family, loader, self.source("gradle/minecraft-targets.gradle")):
            self.assertNotIn("buildDirectory.set", text)
            self.assertNotIn("runDir(", text)
            self.assertNotIn("gameDirectory =", text)
            self.assertNotIn("GRADLE_USER_HOME", text)


if __name__ == "__main__":
    unittest.main()
