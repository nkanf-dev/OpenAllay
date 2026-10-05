"""Offline source and routing contracts. They do not establish native build/game acceptance."""
import importlib.util
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(os.environ.get("OPENALLAY_RECIPE_PACKET_ROOT", str(Path(__file__).resolve().parents[1])))
PROFILE_ROOT = Path(os.environ.get("OPENALLAY_RECIPE_SOURCE_ROOT", str(ROOT)))
SPEC = importlib.util.spec_from_file_location("compile_native_target", ROOT / "scripts/compile-native-target.py")
recipe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(recipe)


class CommandSelectionTest(unittest.TestCase):
    def setUp(self):
        # Packet mode reads profiles in the authorized source root; ordinary CI uses ROOT.
        original = recipe.target_exists
        guard = patch.object(recipe, "target_exists", side_effect=lambda root, target: original(PROFILE_ROOT, target))
        guard.start()
        self.addCleanup(guard.stop)

    def test_every_other_target_keeps_the_existing_root_command(self):
        targets = [file.stem for file in (PROFILE_ROOT / "gradle/minecraft-targets").glob("*.properties")]
        self.assertGreaterEqual(len(targets), 15)
        for target in targets:
            if target in recipe.EARLY:
                continue
            with self.subTest(target=target):
                self.assertEqual(recipe.commands(ROOT, target), [([
                    str(ROOT / "gradlew"), "--max-workers=2", "-PminecraftTarget=" + target,
                    "-PtestBundledExtensions=false", ":fabric:assemble", ":neoforge:assemble"], "root")])

    def test_only_two_failed_targets_use_actual_isolated_export(self):
        for target in ("1.20.2", "1.20.3"):
            command, native = recipe.commands(ROOT, target)
            self.assertEqual(command[0][-2:], [":fabric:assemble", ":neoforge:exportEarlyNeoForgeInputs"])
            self.assertEqual(native[1], "java21")
            self.assertEqual(native[0][-1], "assemble")
            self.assertIn("-PopenallayNativeInputs=" + str(ROOT / "build/early-neoforge" / target / "inputs.json"), native[0])
            self.assertNotIn(":neoforge:assemble", command[0])

    def test_unknown_target_and_path_escape_fail(self):
        for target in ("unknown", "../1.20.2", "1.20.9", "1.20.2 "):
            with self.subTest(target=target), self.assertRaises(ValueError):
                recipe.commands(ROOT, target)

    def test_java21_selection_does_not_change_root_runtime(self):
        with tempfile.TemporaryDirectory() as temporary:
            home = Path(temporary)
            (home / "bin").mkdir()
            (home / "bin/java").write_text("fixture, never executed")
            (home / "release").write_text('JAVA_VERSION="21.0.8"\n')
            original = {"JAVA_HOME": "/root/java25", "JAVA_HOME_21_X64": str(home), "PATH": "/bin"}
            calls = []
            recipe.compile_target(ROOT, "1.20.2", original,
                                  execute=lambda command, **options: calls.append((command, options)))
            self.assertEqual(len(calls), 2)
            self.assertEqual(calls[0][1]["env"], original)
            self.assertEqual(calls[1][1]["env"]["JAVA_HOME"], str(home))
            self.assertTrue(calls[1][1]["env"]["PATH"].startswith(str(home / "bin") + os.pathsep))
            self.assertEqual(original["JAVA_HOME"], "/root/java25")
            self.assertTrue(all(call[1]["check"] for call in calls))

    def test_wrong_isolated_jvm_fails_before_starting_gradle(self):
        with tempfile.TemporaryDirectory() as temporary:
            home = Path(temporary)
            (home / "bin").mkdir()
            (home / "bin/java").write_text("fixture, never executed")
            for version in ("17.0.1", "25", "broken"):
                (home / "release").write_text('JAVA_VERSION="' + version + '"\n')
                calls = []
                with self.subTest(version=version), self.assertRaises(ValueError):
                    recipe.compile_target(ROOT, "1.20.3", {"JAVA_HOME_21_X64": str(home)},
                                          execute=lambda command, **options: calls.append(command))
                self.assertEqual(calls, [])


class SourceReuseContractTest(unittest.TestCase):
    def text(self, relative):
        return (ROOT / relative).read_text()

    def test_guard_precedes_modern_loader_plugin_only_for_two_targets(self):
        source = self.text("neoforge/build.gradle")
        self.assertIn("if (minecraftTarget in ['1.20.2', '1.20.3'])", source)
        self.assertLess(source.index("early-neoforge-inputs.gradle"), source.index("pluginManager.apply("))
        self.assertIn("return\n}", source)
        self.assertIn("def legacyNativeLoader = minecraftTarget == '1.20.1'", source)

    def test_standalone_has_no_copied_feature_sources_or_root_plugin_framework(self):
        settings = self.text("native-builds/early-neoforge/settings.gradle")
        source = self.text("native-builds/early-neoforge/build.gradle")
        self.assertIn("include('extension-api', 'runtime-rhino')", settings)
        for forbidden in ("includeBuild", "build-logic", "engine-core", "net.fabricmc.fabric-loom", "moddev"):
            self.assertNotIn(forbidden, settings)
        self.assertIn("id 'net.neoforged.gradle.userdev' version '7.1.39'", source)
        self.assertIn('implementation("net.neoforged:neoforge:${props.neoforge_version}")', source)
        self.assertIn("source(data.nativeSources.collect", source)
        self.assertIn("java.setSrcDirs([])", source)
        self.assertIn("accessTransformers.files.from", source)
        self.assertIn("options.release = 17", source)
        self.assertIn("output.classesDirs.from(directories(data.engine) + directories(data.adapter))", source)
        self.assertNotIn("net.neoforged:forge", source)
        self.assertNotIn("reflection", source)

    def test_root_input_hashes_and_native_package_guards_remain_explicit(self):
        source = self.text("native-builds/early-neoforge/build.gradle")
        exporter = self.text("gradle/early-neoforge-inputs.gradle")
        for contract in ("commonSources.allJava.files", "outputRecords(engineOutput)", "outputRecords(adapterOutput)",
                         "outputRecords(files(resourceOutput))", "sdk: artifact", "rhino: artifact", "accessTransformer:"):
            self.assertIn(contract, exporter)
        for contract in ("sha256(input) != record.sha256", "data.engine + data.adapter + data.resources",
                         "[data.sdk, data.rhino]", "SDK/Rhino JarJar identity or singleton range differs",
                         "MixinConfigs", "verifyReusedPackage", "neoforge/build/libs"):
            self.assertIn(contract, source)
        self.assertIn("project.name == 'extension-api' ? 8 : 17", self.text("native-builds/early-neoforge/artifact-input.gradle"))

    def test_wrapper_isolated_official_checksum_and_root_wrapper_remains_modern(self):
        wrapper = self.text("native-builds/early-neoforge/gradle/wrapper/gradle-wrapper.properties")
        self.assertIn("https\\://services.gradle.org/distributions/gradle-8.14-bin.zip", wrapper)
        self.assertIn("distributionSha256Sum=61ad310d3c7d3e5da131b76bbf22b5a4c0786e9d892dae8c1658d4b484de3caa", wrapper)
        self.assertIn("gradle-9.5.0-bin.zip", (PROFILE_ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text())

    def test_native_workflow_keeps_package_and_builder_verification(self):
        workflow = self.text(".github/workflows/minecraft-native.yml")
        self.assertIn('python3 -B scripts/compile-native-target.py --target "$OPENALLAY_MINECRAFT_TARGET"', workflow)
        self.assertIn("verify-native-target-package.py", workflow)
        self.assertIn("--bundled-builder", workflow)
        self.assertIn("stage-ci-client-production.py", workflow)
        self.assertIn("build/early-neoforge/**/inputs.json", workflow)
        self.assertIn("native-builds/early-neoforge/build/reports/", workflow)


class InputClosureModel:
    """Offline policy model only. This does not execute the Groovy validator."""

    @staticmethod
    def digest(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def __init__(self, root, receipt):
        self.root, self.receipt = root.resolve(), receipt.resolve()
        self.receipt_hash = self.digest(self.receipt)
        self.data = json.loads(self.receipt.read_text())
        self.data_hash = self.data_digest()
        if set(self.data) != {"root", "target", "properties", "nativeSources", "engine", "adapter", "resources",
                              "sdk", "rhino", "accessTransformer"}:
            raise ValueError("model receipt shape")
        if set(self.data["properties"]) != {"version", "group", "mod_id", "mod_name", "mod_author", "java_version",
                "neoforge_version", "jeiArtifactTarget", "jei_version", "rei_version", "architectury_version",
                "sqlite_jdbc_version", "commonmark_version", "jtokkit_version"}:
            raise ValueError("model properties shape")
        if Path(self.data["root"]).resolve() != self.root:
            raise ValueError("model receipt root")
        self.records, self.roots, paths, entries = [], {}, set(), set()
        owners = ["nativeSources", "engine", "adapter", "resources", "sdk", "rhino"]
        if self.data["accessTransformer"] is not None:
            owners.append("accessTransformer")
        for owner in owners:
            records = self.data[owner] if owner in ("nativeSources", "engine", "adapter", "resources") else [self.data[owner]]
            if not records:
                raise ValueError("model empty input set")
            for record in records:
                output = owner in ("engine", "adapter", "resources")
                expected = {"path", "sha256"} | ({"entry"} if output else set())
                if owner in ("sdk", "rhino"):
                    expected |= {"group", "name", "version"}
                if set(record) != expected or any(not isinstance(value, str) or not value for value in record.values()):
                    raise ValueError("model record shape")
                if len(record["sha256"]) != 64 or any(char not in "0123456789abcdef" for char in record["sha256"]):
                    raise ValueError("model record hash")
                path = Path(record["path"])
                if not path.is_absolute() or path != path.resolve() or path in paths:
                    raise ValueError("model aliased or duplicated path: " + str(path))
                if not path.is_relative_to(self.root):
                    raise ValueError("model external path: " + str(path))
                paths.add(path)
                self.records.append(record)
                if owner == "nativeSources":
                    relative = path.relative_to(self.root).as_posix()
                    if not relative.startswith(("common/src/", "neoforge/src/")) or not relative.endswith(".java"):
                        raise ValueError("model non-native source: " + str(path))
                if not output:
                    continue
                entry = record["entry"]
                parts = entry.split("/")
                if "\\" in entry or any(part in ("", ".", "..") for part in parts) or entry in entries:
                    raise ValueError("model unsafe or colliding entry: " + entry)
                entries.add(entry)
                directory = path
                for _ in parts:
                    directory = directory.parent
                if directory == self.root or not directory.is_relative_to(self.root) or directory / entry != path:
                    raise ValueError("model inconsistent root/entry: " + str(path))
                if directory in self.roots and self.roots[directory][0] != owner:
                    raise ValueError("model colliding root: " + str(directory))
                self.roots.setdefault(directory, (owner, {}))[1][entry] = record
        directories = list(self.roots)
        for index, directory in enumerate(directories):
            if any(directory.is_relative_to(other) or other.is_relative_to(directory) for other in directories[index + 1:]):
                raise ValueError("model overlapping root: " + str(directory))
        self.validate()

    def data_digest(self):
        return hashlib.sha256(json.dumps(self.data, separators=(",", ":")).encode()).hexdigest()

    def validate(self):
        if (not self.receipt.is_file() or self.receipt != self.receipt.resolve()
                or self.digest(self.receipt) != self.receipt_hash or self.data_digest() != self.data_hash):
            raise ValueError("model changed receipt: " + str(self.receipt))
        for record in self.records:
            path = Path(record["path"])
            if not path.is_file() or path != path.resolve() or self.digest(path) != record["sha256"]:
                raise ValueError("model changed or missing input: " + str(path))
        for directory, (_, expected) in self.roots.items():
            if not directory.is_dir():
                raise ValueError("model missing root: " + str(directory))
            seen = set()
            for path in directory.rglob("*"):
                if path.is_symlink():
                    raise ValueError("model aliased input: " + str(path))
                if path.is_dir():
                    continue
                entry = path.relative_to(directory).as_posix()
                record = expected.get(entry)
                if (record is None or not path.is_file() or path != Path(record["path"])
                        or self.digest(path) != record["sha256"]):
                    raise ValueError("model unlisted or changed input: " + str(path))
                seen.add(entry)
            if seen != set(expected):
                raise ValueError("model missing entries: " + str(directory))


class InputClosureModelTest(unittest.TestCase):
    """Real tiny-file mutations against the model; native CI must test Groovy separately."""

    def setUp(self):
        temporary = tempfile.TemporaryDirectory(dir=os.environ.get("OPENALLAY_RECIPE_FIXTURE_ROOT"))
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.receipt = self.root / "build/early-neoforge/1.20.2/inputs.json"
        properties = dict.fromkeys(("version", "group", "mod_id", "mod_name", "mod_author", "java_version",
                "neoforge_version", "jeiArtifactTarget", "jei_version", "rei_version", "architectury_version",
                "sqlite_jdbc_version", "commonmark_version", "jtokkit_version"), "fixture")
        properties.update(java_version="17", neoforge_version="20.2.93", version="0.4.1", group="dev.openallay")
        self.data = {"root": str(self.root), "target": "1.20.2", "properties": properties, "accessTransformer": None}
        layouts = {"nativeSources": ["common/src/targets/1.20.2/java/example/A.java"],
                   "engine": ["engine/build/classes/example/Engine.class", "engine/build/resources/data/config.json"],
                   "adapter": ["adapter/build/classes/example/Adapter.class"],
                   "resources": ["neoforge/build/resources/META-INF/mods.toml"],
                   "sdk": ["sdk/build/libs/sdk.jar"], "rhino": ["rhino/build/libs/rhino.jar"]}
        for owner, paths in layouts.items():
            records = []
            for relative in paths:
                path = self.root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(relative.encode())
                record = {"path": str(path), "sha256": InputClosureModel.digest(path)}
                if owner in ("engine", "adapter", "resources"):
                    record["entry"] = relative.split("/", 3)[3]
                if owner in ("sdk", "rhino"):
                    record.update(group="dev.openallay", name="extension-api" if owner == "sdk" else "runtime-rhino",
                                  version="0.4.0" if owner == "sdk" else "fixture")
                records.append(record)
            self.data[owner] = records[0] if owner in ("sdk", "rhino") else records
        self.write_receipt()

    def write_receipt(self):
        self.receipt.parent.mkdir(parents=True, exist_ok=True)
        self.receipt.write_text(json.dumps(self.data))

    def model(self):
        return InputClosureModel(self.root, self.receipt)

    def test_exact_records_and_multiple_engine_roots_are_accepted_by_model(self):
        self.model().validate()

    def test_each_represented_root_rejects_extra_hidden_file(self):
        model = self.model()
        for directory in model.roots:
            with self.subTest(root=str(directory)):
                extra = directory / ".unlisted"
                extra.write_bytes(b"added after export")
                with self.assertRaisesRegex(ValueError, "unlisted"):
                    model.validate()
                extra.unlink()

    def test_every_source_artifact_output_changed_or_missing_is_rejected(self):
        model = self.model()
        for record in model.records:
            path = Path(record["path"])
            original = path.read_bytes()
            with self.subTest(path=str(path)):
                path.write_bytes(b"changed after setup")
                with self.assertRaisesRegex(ValueError, "changed or missing"):
                    model.validate()
                path.unlink()
                with self.assertRaisesRegex(ValueError, "changed or missing"):
                    model.validate()
                path.write_bytes(original)

    def test_receipt_bytes_and_parsed_data_are_pinned(self):
        model = self.model()
        self.receipt.write_text(self.receipt.read_text() + "\n")
        with self.assertRaisesRegex(ValueError, "changed receipt"):
            model.validate()
        self.write_receipt()
        model.data["target"] = "1.20.3"
        with self.assertRaisesRegex(ValueError, "changed receipt"):
            model.validate()

    def test_duplicate_paths_and_entries_rejected_even_when_bytes_identical(self):
        for owner in ("nativeSources", "engine"):
            with self.subTest(owner=owner):
                self.data[owner].append(dict(self.data[owner][0]))
                self.write_receipt()
                with self.assertRaisesRegex(ValueError, "duplicated path"):
                    self.model()
                self.data[owner].pop()
        self.data["adapter"][0]["entry"] = self.data["engine"][0]["entry"]
        self.write_receipt()
        with self.assertRaisesRegex(ValueError, "colliding entry"):
            self.model()

    def test_colliding_root_between_resource_and_engine_rejected(self):
        path = self.root / "engine/build/resources/data/other.json"
        path.write_bytes(b"other")
        self.data["resources"] = [{"path": str(path), "entry": "data/other.json", "sha256": InputClosureModel.digest(path)}]
        self.write_receipt()
        with self.assertRaisesRegex(ValueError, "colliding root"):
            self.model()

    def test_root_entry_inconsistency_and_unsafe_entry_rejected(self):
        for entry in ("other/Engine.class", "../Engine.class", "/Engine.class", "example//Engine.class"):
            with self.subTest(entry=entry):
                self.data["engine"][0]["entry"] = entry
                self.write_receipt()
                with self.assertRaises(ValueError):
                    self.model()

    def test_unlisted_symlink_and_extra_record_field_rejected(self):
        model = self.model()
        link = next(iter(model.roots)) / "link"
        link.symlink_to(self.root)
        with self.assertRaisesRegex(ValueError, "aliased input"):
            model.validate()
        link.unlink()
        self.data["sdk"]["unexpected"] = "field"
        self.write_receipt()
        with self.assertRaisesRegex(ValueError, "record shape"):
            self.model()

    def test_overlapping_represented_roots_are_rejected(self):
        path = self.root / "engine/build/classes/nested/Root.class"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"nested")
        self.data["engine"].append({"path": str(path), "entry": "Root.class", "sha256": InputClosureModel.digest(path)})
        self.write_receipt()
        with self.assertRaisesRegex(ValueError, "overlapping root"):
            self.model()

    def test_raw_compiler_paths_do_not_expand_the_source_directory(self):
        unlisted = self.root / "common/src/targets/1.20.2/java/example/Unselected.java"
        unlisted.write_bytes(b"not a compiler input")
        model = self.model()
        selected = {Path(record["path"]) for record in model.data["nativeSources"]}
        self.assertNotIn(unlisted, selected)
        model.validate()


if __name__ == "__main__":
    unittest.main()
