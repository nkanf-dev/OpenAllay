import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

MODULE_PATH = Path(__file__).with_name("run-packaged-builder-acceptance.py")
spec = importlib.util.spec_from_file_location("packaged_builder_acceptance", MODULE_PATH)
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)


class PackagedBuilderLauncherTests(unittest.TestCase):
    def test_target_catalog_cli_and_java_pins_are_exact_and_per_call(self):
        self.assertEqual(24, len(launcher.minecraft_targets()))
        for target, java in (("1.20.1", "17"), ("1.21.1", "21"), ("26.3", "25"), ("26.2", "25")):
            args = launcher.parser().parse_args(["fabric", "--minecraft-target", target])
            self.assertEqual(target, args.minecraft_target)
            self.assertEqual(java, launcher.runtime_pins(target)["java_version"])
        self.assertFalse(hasattr(launcher, "MC_VERSION"))

    def test_verified_native_classifier_is_extracted_not_added_as_game_classpath(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = root / "libraries/org/example/native.jar"
            jar.parent.mkdir(parents=True)
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("META-INF/MANIFEST.MF", b"manifest")
                archive.writestr("liblwjgl.so", b"tiny native fixture")
            native = {"path": "org/example/native.jar", "sha1": launcher.digest_with(jar, "sha1"), "size": jar.stat().st_size}
            metadata = {"libraries": [{"name": "org.example:native:1", "natives": {"linux": "natives-linux"},
                                       "downloads": {"classifiers": {"natives-linux": native}}, "extract": {"exclude": ["META-INF/"]}}]}
            with patch.object(launcher, "system_name", return_value="linux"):
                selected = launcher.native_libraries(metadata, root)
            files = launcher.extract_natives(selected, root / "run/natives")
            self.assertEqual({"liblwjgl.so": launcher.hashlib.sha256(b"tiny native fixture").hexdigest()}, files)
            self.assertFalse((root / "run/natives/META-INF").exists())
            jar.write_bytes(b"modified selected native")
            with patch.object(launcher, "system_name", return_value="linux"), self.assertRaisesRegex(ValueError, "hash/size"):
                launcher.native_libraries(metadata, root)

    def test_native_extraction_rejects_unsafe_zip_paths_symlinks_and_conflicts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for number, name in enumerate(("../escape", "/absolute", "a/../escape", "a\\escape", "C:/escape", "a//escape")):
                jar = root / (str(number) + ".jar")
                with zipfile.ZipFile(jar, "w") as archive:
                    archive.writestr(name, b"unsafe")
                with self.assertRaisesRegex(ValueError, "Unsafe native"):
                    launcher.extract_natives([(jar, [])], root / ("out" + str(number)))
            jar = root / "symlink.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                info = zipfile.ZipInfo("lib.so")
                info.external_attr = (0o120777 << 16)
                archive.writestr(info, b"outside")
            with self.assertRaisesRegex(ValueError, "Unsafe native"):
                launcher.extract_natives([(jar, [])], root / "symlink")
            jars = []
            for number in (1, 2):
                jar = root / ("conflict" + str(number) + ".jar")
                with zipfile.ZipFile(jar, "w") as archive:
                    archive.writestr("lib.so", bytes([number]))
                jars.append((jar, []))
            with self.assertRaisesRegex(ValueError, "conflicting paths"):
                launcher.extract_natives(jars, root / "conflicts")

    def test_same_builder_payload_verifies_all_exact_candidates_without_release_admission(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            for target in launcher.minecraft_targets():
                selected_loader = "forge" if target == "1.19.2" else "fabric"
                artifact = self.artifact(repo, loader=selected_loader, minecraft_target=target)
                identity = launcher.packaged_artifact(artifact, selected_loader, repo=repo, minecraft_target=target)
                self.assertEqual(target, identity["minecraft"])
                self.assertTrue(identity["nativeWorldBootstrapPresent"])
                # This header-only fixture verifies declarations; it does not widen
                # distribution/support admission or constitute real-game acceptance.

    def test_postclosure_persistence_audit_and_reload_hash_binding(self):
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory).resolve()
            original = repo / "build/e2e/original"
            reload = original / "phases/reload"
            reload.mkdir(parents=True)
            script = repo / "scripts/validate-builder-live-acceptance.py"
            script.parent.mkdir()
            script.write_text("""def validate_acceptance(original, repo, reload=None):
    return {"templateSha256": "actual-hash", "journalRows": {"actual-id": {"status": "completed"}}}
def read_json(path):
    import json
    return json.loads(path.read_text())
""")
            launcher.audit_builder_persistence(original, {"scenario": "builder-acceptance"}, repo)
            self.assertTrue((original / "persistence-audit.json").is_file())
            launcher.audit_builder_persistence(reload, {"scenario": "builder-reload", "resumeFrom": str(original)}, repo)
            self.assertTrue((reload / "persistence-audit.json").is_file())
            launcher.write_json(original / "persistence-audit.json", {"templateSha256": "changed", "journalRows": {}})
            with self.assertRaisesRegex(ValueError, "Reload changed verified native persistence"):
                launcher.audit_builder_persistence(reload, {"scenario": "builder-reload", "resumeFrom": str(original)}, repo)

    def official_launch_contract(self, target):
        path = MODULE_PATH.parent / "fixtures/minecraft-launch" / (target + ".json")
        expected = {"1.20.1": "d47ff966c68b13fac17d214eea8acfe45b1f08a15c432cdd2136e36c3d315de3",
                    "1.21.1": "1ed901815b195cc714f36aee0fa4a8ee2bcf6d42154e6fbb346003cfc34d1b0d"}
        self.assertEqual(expected[target], launcher.digest(path))
        return json.loads(path.read_text())

    def official_arguments_environment(self, repo, target, loader):
        repo = repo.resolve()
        # Only metadata paths/rules/arguments come from the saved official producers.
        # Small test files replace binaries and get their own exact receipt hashes.
        # This checks prepare/command assembly, not game execution or account authentication.
        if target == "1.19.2":
            # Synthetic vanilla launch arguments; actual Forge metadata is retained verbatim.
            contract = self.official_launch_contract("1.20.1")
            fixture = MODULE_PATH.parent / "fixtures/forge-1.19.2-43.5.0"
            contract = {"vanilla": contract["vanilla"], "forge": json.loads((fixture / "version.json").read_text()),
                        "install": json.loads((fixture / "install_profile.json").read_text())}
            contract["vanilla"]["id"] = target
        else:
            contract = self.official_launch_contract(target)
        mcroot = repo / "build/e2e/runtime" / target / "minecraft"
        mcroot.mkdir(parents=True)
        profile = repo / "gradle/minecraft-targets" / (target + ".properties")
        profile.parent.mkdir(parents=True, exist_ok=True)
        profile.write_bytes((launcher.REPO / "gradle/minecraft-targets" / (target + ".properties")).read_bytes())
        pins = launcher.runtime_pins(target, repo)
        index = mcroot / "assets/indexes" / (contract["vanilla"]["assetIndex"]["id"] + ".json")
        index.parent.mkdir(parents=True)
        launcher.write_json(index, {"objects": {}})
        contract["vanilla"]["assetIndex"]["sha1"] = launcher.digest_with(index, "sha1")
        client = mcroot / "versions" / target / (target + ".jar")
        client.parent.mkdir(parents=True)
        client.write_bytes(b"unit fixture client; not executable Minecraft")
        contract["vanilla"]["downloads"] = {"client": {"size": client.stat().st_size,
                                                       "sha1": launcher.digest_with(client, "sha1")}}
        metadata = contract[loader]
        owners = [contract["vanilla"], metadata]
        if loader == "forge":
            owners.append(contract["install"])
        for owner in owners:
            for library in owner["libraries"]:
                artifact = library.get("downloads", {}).get("artifact")
                relative = artifact["path"] if artifact else launcher.maven_path(library["name"])
                binary = mcroot / "libraries" / relative
                binary.parent.mkdir(parents=True, exist_ok=True)
                binary.write_bytes(("unit fixture library " + library["name"]).encode())
                if artifact is not None:
                    artifact.update({"sha1": launcher.digest_with(binary, "sha1"), "size": binary.stat().st_size})
                else:
                    library.update({"sha1": launcher.digest_with(binary, "sha1"), "size": binary.stat().st_size})
        profile_path = mcroot / "versions" / metadata["id"] / (metadata["id"] + ".json")
        profile_path.parent.mkdir(parents=True)
        launcher.write_json(profile_path, metadata)
        launcher.write_json(client.with_suffix(".json"), contract["vanilla"])
        api = None
        if loader == "fabric":
            api = mcroot / "libraries" / launcher.maven_path("net.fabricmc.fabric-api:fabric-api:" + pins["fabric_version"])
            api.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(api, "w") as archive:
                archive.writestr("fabric.mod.json", json.dumps({"id": "fabric-api", "version": pins["fabric_version"]}))
        else:
            install_path = mcroot / ".provision" / (loader + "-install_profile.json")
            install_path.parent.mkdir()
            launcher.write_json(install_path, contract["install"])
            launcher.write_json(mcroot / ".provision" / (loader + "-version.json"), metadata)
            coordinates = [contract["install"]["data"][key]["client"][1:-1]
                           for key in ("PATCHED", "MC_SRG", "MC_EXTRA")]
            identity = launcher.fml_runtime_identity(pins, loader)
            coordinates.append(identity["group"] + ":" + identity["artifact"] + ":" + identity["version"] + ":universal")
            for coordinate in coordinates:
                path = mcroot / "libraries" / launcher.maven_path(coordinate)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(("unit fixture installed client " + coordinate).encode())
            # Installer outputs can share a library coordinate with version metadata.
            # Refresh the test producer hashes after its final mock write.
            for owner in owners:
                for library in owner["libraries"]:
                    artifact = library.get("downloads", {}).get("artifact")
                    relative = artifact["path"] if artifact else launcher.maven_path(library["name"])
                    binary = mcroot / "libraries" / relative
                    record = artifact if artifact is not None else library
                    record.update({"sha1": launcher.digest_with(binary, "sha1"), "size": binary.stat().st_size})
            launcher.write_json(profile_path, metadata)
            launcher.write_json(client.with_suffix(".json"), contract["vanilla"])
            launcher.write_json(install_path, contract["install"])
            launcher.write_json(mcroot / ".provision" / (loader + "-version.json"), metadata)
        java = repo.parent / "java"
        java.write_bytes(b"unit fixture Java path; subprocess.run is mocked")
        java.chmod(0o755)
        receipt = {"loader": loader, "minecraft": target, "javaRequired": int(pins["java_version"]),
                   "java": str(java), "minecraftRoot": str(mcroot), "profile": metadata["id"],
                   "fabricApi": str(api) if api else None, "sourceProfile": str(profile.relative_to(repo)),
                   "sourceProfileSha256": launcher.digest(profile),
                   "pins": {key: pins[key] for key in launcher.runtime_pin_fields(loader)},
                   "mechanism": "official-client-installer"}
        if loader == "forge":
            receipt["sourceLoaderMap"] = "gradle/minecraft-target-loaders.json"
            receipt["sourceLoaderMapSha256"] = launcher.digest(repo / receipt["sourceLoaderMap"])
        receipt_path = mcroot / ".provision" / (loader + "-runtime.json")
        receipt_path.parent.mkdir(exist_ok=True)
        self.refresh_runtime_receipt(mcroot, receipt_path, receipt)
        return mcroot, repo.parent / "unused-gradle-cache", java, receipt_path, contract

    def refresh_runtime_receipt(self, mcroot, receipt_path, receipt=None):
        receipt = receipt if receipt is not None else json.loads(receipt_path.read_text())
        receipt["files"] = {path.relative_to(mcroot).as_posix(): self.runtime_record(path)
                            for path in mcroot.rglob("*") if path.is_file() and path != receipt_path}
        launcher.write_json(receipt_path, receipt)

    def test_old_official_metadata_prepares_complete_offline_commands_for_both_loaders(self):
        from types import SimpleNamespace
        for target in ("1.20.1", "1.21.1"):
            for loader in ("fabric", "neoforge"):
                with self.subTest(target=target, loader=loader), tempfile.TemporaryDirectory() as directory:
                    repo = Path(directory) / "repo with spaces"
                    artifact = self.artifact(repo, loader=loader, minecraft_target=target)
                    mcroot, cache, java, receipt, contract = self.official_arguments_environment(repo, target, loader)
                    args = launcher.parser().parse_args([loader, "--minecraft-target", target, "--run-id", "official-args",
                        "--minecraft-root", str(mcroot), "--gradle-cache", str(cache), "--java", str(java), "--jar", str(artifact)])
                    with patch.object(launcher, "system_name", return_value="linux"), \
                            patch.object(launcher.platform, "machine", return_value="x86_64"), \
                            patch.object(launcher.subprocess, "run", return_value=SimpleNamespace(returncode=0,
                                stdout='openjdk version "' + str(contract["vanilla"]["javaVersion"]["majorVersion"]) + '.0.1"')) as java_run:
                        output, manifest = launcher.prepare(args, repo)
                    java_run.assert_called_once()
                    self.assertEqual([str(java), "-version"], java_run.call_args.args[0])
                    command = manifest["command"]
                    main_class = contract[loader]["mainClass"]
                    self.assertEqual(target, manifest["minecraft"])
                    self.assertEqual(contract["vanilla"]["javaVersion"]["majorVersion"], manifest["javaRequired"])
                    self.assertEqual([str(java), "-Xms512M", "-Xmx3G"], command[:3])
                    substitutions = {"natives_directory": str(output / "natives"),
                        "launcher_name": "OpenAllayPackagedAcceptance", "launcher_version": "1",
                        "classpath": os.pathsep.join(manifest["classPath"]), "version_name": contract[loader]["id"],
                        "library_directory": str(mcroot / "libraries"), "classpath_separator": os.pathsep}
                    expected_jvm = []
                    for argument in [item for item in contract["vanilla"]["arguments"]["jvm"] if isinstance(item, str)] + contract[loader]["arguments"]["jvm"]:
                        for name, value in substitutions.items():
                            argument = argument.replace("${" + name + "}", value)
                        output_owners = {"jna.tmpdir": "jna", "org.lwjgl.system.SharedLibraryExtractPath": "lwjgl",
                                         "io.netty.native.workdir": "netty"}
                        key = argument.removeprefix("-D").partition("=")[0]
                        if argument.startswith("-D") and key in output_owners:
                            argument = "-D" + key + "=" + str(output / "native-runtime" / output_owners[key])
                        expected_jvm.append(argument)
                    self.assertEqual(expected_jvm, command[3:3 + len(expected_jvm)])
                    expected_game = ["--username", "BuilderProbe", "--version", contract[loader]["id"],
                        "--gameDir", str(output / "game"), "--assetsDir", str(mcroot / "assets"),
                        "--assetIndex", contract["vanilla"]["assetIndex"]["id"], "--uuid", launcher.offline_uuid("BuilderProbe"),
                        "--accessToken", "0", "--clientId", "", "--xuid", "", "--userType", "legacy",
                        "--versionType", contract[loader]["type"], "--width", "1100", "--height", "700"]
                    expected_game += contract[loader]["arguments"]["game"]
                    self.assertEqual(expected_game, command[command.index(main_class) + 1:])
                    self.assertFalse(any("${" in argument for argument in command))
                    self.assertTrue(manifest["noGameLaunched"])
                    self.assertEqual([], list((output / "game/saves").iterdir()))
                    self.assertEqual({}, manifest["nativeFiles"])
                    self.assertEqual(str(output / "natives"), manifest["nativesDirectory"])
                    self.assertTrue(all(Path(path).is_relative_to(mcroot) for path in manifest["classPath"]))
                    expected_libraries = []
                    for owner in (contract["vanilla"], contract[loader]):
                        selected = [library for library in owner["libraries"]
                                    if not library.get("rules") or library["rules"] == [{"action": "allow", "os": {"name": "linux"}}]]
                        if owner is contract[loader]:
                            replacements = {":".join(library["name"].split(":")[:2]) for library in selected}
                            expected_libraries = [(name, path) for name, path in expected_libraries
                                                  if ":".join(name.split(":")[:2]) not in replacements]
                        for library in selected:
                            relative = library.get("downloads", {}).get("artifact", {}).get("path") or launcher.maven_path(library["name"])
                            expected_libraries.append((library["name"], str(mcroot / "libraries" / relative)))
                    expected_classpath = list(dict.fromkeys(path for _, path in expected_libraries))
                    if loader == "fabric":
                        expected_classpath.append(str(mcroot / "versions" / target / (target + ".jar")))
                    self.assertEqual(expected_classpath, manifest["classPath"])
                    self.assertTrue(any("natives-linux.jar" in path for path in manifest["classPath"]))
                    self.assertFalse(any("natives-macos" in path or "natives-windows" in path for path in manifest["classPath"]))
                    self.assertEqual({"path": str(receipt), "sha256": launcher.digest(receipt)}, manifest["runtimeProvision"])
                    self.assertEqual(launcher.digest(artifact), manifest["packagedArtifact"]["sha256"])
                    self.assertEqual({"enabled": False}, json.loads((output / "game/config/openallay/unrestricted-javascript.json").read_text()))
                    if loader == "neoforge":
                        module_path = command[command.index("-p") + 1].split(os.pathsep)
                        self.assertTrue(all(Path(path).is_file() and Path(path).is_relative_to(mcroot / "libraries") for path in module_path))
                        for key in ("PATCHED", "MC_SRG", "MC_EXTRA"):
                            relative = "libraries/" + launcher.maven_path(contract["install"]["data"][key]["client"][1:-1]).as_posix()
                            self.assertIn(relative, json.loads(receipt.read_text())["files"])

    def test_actual_forge1192_profile_arguments_and_persisted_resume_main_class(self):
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            artifact = self.artifact(repo, loader="forge", minecraft_target="1.19.2")
            mcroot, cache, java, receipt, contract = self.official_arguments_environment(repo, "1.19.2", "forge")
            args = launcher.parser().parse_args(["forge", "--minecraft-target", "1.19.2", "--run-id", "forge-owned",
                "--minecraft-root", str(mcroot), "--gradle-cache", str(cache), "--java", str(java), "--jar", str(artifact)])
            with patch.object(launcher, "system_name", return_value="linux"), \
                    patch.object(launcher.platform, "machine", return_value="x86_64"), \
                    patch.object(launcher.subprocess, "run", return_value=SimpleNamespace(returncode=0, stdout='openjdk version "17.0.1"')):
                output, manifest = launcher.prepare(args, repo)
            main = "cpw.mods.bootstraplauncher.BootstrapLauncher"
            self.assertEqual(main, manifest["mainClass"])
            self.assertEqual(main, launcher.resume_main_class(manifest, repo))
            self.assertEqual(contract["forge"]["arguments"]["game"], manifest["command"][-len(contract["forge"]["arguments"]["game"]):])
            self.assertNotIn("net.neoforged.fml.startup.Client", manifest["command"])
            self.assertEqual([], list((output / "game/saves").iterdir()))
            self.assertEqual({"minecraft_version", "java_version", "forge_version"}, set(json.loads(receipt.read_text())["pins"]))
            for changed in (dict(manifest, mainClass="net.neoforged.fml.startup.Client"),
                            {key: value for key, value in manifest.items() if key != "mainClass"}):
                with self.assertRaisesRegex(ValueError, "persisted actual"):
                    launcher.resume_main_class(changed, repo)

    def test_old_official_prepare_keeps_unknown_active_arguments_fail_closed(self):
        from types import SimpleNamespace
        for loader in ("fabric", "neoforge"):
            for kind in ("jvm", "game"):
                with self.subTest(loader=loader, kind=kind), tempfile.TemporaryDirectory() as directory:
                    repo = Path(directory) / "repo"
                    artifact = self.artifact(repo, loader=loader, minecraft_target="1.21.1")
                    mcroot, cache, java, receipt, contract = self.official_arguments_environment(repo, "1.21.1", loader)
                    path = mcroot / "versions" / contract[loader]["id"] / (contract[loader]["id"] + ".json")
                    metadata = json.loads(path.read_text())
                    metadata["arguments"][kind].append("${unrecognized_account_or_quickplay_value}")
                    launcher.write_json(path, metadata)
                    self.refresh_runtime_receipt(mcroot, receipt)
                    args = launcher.parser().parse_args([loader, "--minecraft-target", "1.21.1", "--run-id", "unknown",
                        "--minecraft-root", str(mcroot), "--gradle-cache", str(cache), "--java", str(java), "--jar", str(artifact)])
                    with patch.object(launcher.subprocess, "run", return_value=SimpleNamespace(returncode=0, stdout='openjdk version "21.0.1"')), \
                            self.assertRaisesRegex(ValueError, "Unsupported launch metadata placeholder: unrecognized_account_or_quickplay_value"):
                        launcher.prepare(args, repo)
                    self.assertFalse((repo / "build/e2e/packaged-builder" / loader / "unknown/launch.json").exists())

    def test_version_type_uses_installed_profile_metadata_without_release_fallback(self):
        from types import SimpleNamespace
        for loader in ("fabric", "neoforge"):
            for version_type in ("snapshot", None):
                with self.subTest(loader=loader, version_type=version_type), tempfile.TemporaryDirectory() as directory:
                    repo = Path(directory) / "repo"
                    artifact = self.artifact(repo, loader=loader, minecraft_target="1.21.1")
                    mcroot, cache, java, receipt, contract = self.official_arguments_environment(repo, "1.21.1", loader)
                    path = mcroot / "versions" / contract[loader]["id"] / (contract[loader]["id"] + ".json")
                    metadata = json.loads(path.read_text())
                    if version_type is None:
                        del metadata["type"]
                    else:
                        metadata["type"] = version_type
                    launcher.write_json(path, metadata)
                    self.refresh_runtime_receipt(mcroot, receipt)
                    args = launcher.parser().parse_args([loader, "--minecraft-target", "1.21.1", "--run-id", "profile-type",
                        "--minecraft-root", str(mcroot), "--gradle-cache", str(cache), "--java", str(java), "--jar", str(artifact)])
                    with patch.object(launcher.subprocess, "run", return_value=SimpleNamespace(returncode=0, stdout='openjdk version "21.0.1"')):
                        if version_type is None:
                            with self.assertRaisesRegex(KeyError, "type"):
                                launcher.prepare(args, repo)
                        else:
                            _, manifest = launcher.prepare(args, repo)
                            self.assertEqual(version_type, manifest["command"][manifest["command"].index("--versionType") + 1])

    def test_new_target_fabric_prepare_uses_game_java_and_installed_profile_arguments(self):
        for target in ("26.3",):
            with self.subTest(target=target), tempfile.TemporaryDirectory() as directory:
                repo = Path(directory) / "repo"
                artifact = self.artifact(repo, minecraft_target=target)
                mcroot, cache, java = self.environment(repo)
                pins = launcher.runtime_pins(target)
                vanilla = launcher.read_version(mcroot, "26.2")
                old = mcroot / "versions/26.2"
                new = mcroot / "versions" / target
                old.rename(new)
                (new / "26.2.jar").rename(new / (target + ".jar"))
                (new / "26.2.json").unlink()
                vanilla["id"] = target
                vanilla["javaVersion"]["majorVersion"] = int(pins["java_version"])
                launcher.write_json(new / (target + ".json"), vanilla)
                profile_path = repo / "gradle/minecraft-targets" / (target + ".properties")
                profile_path.write_bytes((launcher.REPO / "gradle/minecraft-targets" / (target + ".properties")).read_bytes())
                fabric_id = "fabric-loader-" + pins["fabric_loader_version"] + "-" + target
                profile_file = mcroot / "versions" / fabric_id / (fabric_id + ".json")
                profile_file.parent.mkdir()
                launcher.write_json(profile_file, {"id": fabric_id, "inheritsFrom": target,
                    "type": "release", "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
                    "libraries": [{"name": "net.fabricmc:fabric-loader:" + pins["fabric_loader_version"], "url": "https://maven.fabricmc.net/"}],
                    "arguments": {"jvm": ["-Dexact.target=" + target], "game": []}})
                loader = cache / "net.fabricmc/fabric-loader" / pins["fabric_loader_version"] / "hash" / ("fabric-loader-" + pins["fabric_loader_version"] + ".jar")
                loader.parent.mkdir(parents=True, exist_ok=True)
                loader.write_bytes(b"installed profile dependency")
                api = repo / ("fabric-api-" + target + ".jar")
                with zipfile.ZipFile(api, "w") as archive:
                    archive.writestr("fabric.mod.json", json.dumps({"id": "fabric-api", "version": pins["fabric_version"]}))
                args = launcher.parser().parse_args(["fabric", "--minecraft-target", target,
                    "--minecraft-root", str(mcroot), "--gradle-cache", str(cache), "--java", str(java),
                    "--fabric-api", str(api), "--jar", str(artifact), "--run-id", "per-target"])
                from types import SimpleNamespace
                with patch.object(launcher.subprocess, "run", return_value=SimpleNamespace(returncode=0,
                        stdout='openjdk version "' + pins["java_version"] + '.0.1"')):
                    output, manifest = launcher.prepare(args, repo)
                self.assertEqual(target, manifest["minecraft"])
                self.assertEqual(int(pins["java_version"]), manifest["javaRequired"])
                self.assertIn("-Dexact.target=" + target, manifest["command"])
                self.assertIn(str((new / (target + ".jar")).resolve()), manifest["classPath"])
                self.assertEqual({}, manifest["nativeFiles"])
                self.assertFalse(list((output / "game/saves").iterdir()))

    def test_os_rules_do_not_enable_demo_or_quickplay_for_fresh_world(self):
        self.assertFalse(launcher.rules_allow(
            [{"action": "allow", "features": {"is_demo_user": True}}]))
        self.assertFalse(launcher.rules_allow(
            [{"action": "allow", "features": {"is_quick_play_singleplayer": True}}]))
        self.assertTrue(launcher.rules_allow(
            [{"action": "allow", "os": {"name": "osx"}}], os_name="osx"))

    def test_argument_substitution_preserves_paths_and_rejects_unknowns(self):
        self.assertEqual(["-cp", "/cache/a jar.jar:/cache/b.jar"], launcher.expand_arguments(
            ["-cp", "${classpath}"], {"classpath": "/cache/a jar.jar:/cache/b.jar"}))
        with self.assertRaises(ValueError):
            launcher.expand_arguments(["${missing}"], {})

    def test_maven_path_supports_classifiers(self):
        self.assertEqual(Path("net/neoforged/neoforge/26.2.0.25-beta/neoforge-26.2.0.25-beta-universal.jar"),
                         launcher.maven_path("net.neoforged:neoforge:26.2.0.25-beta:universal"))

    def test_display_fixture_has_exact_current_nested_ui_shape_and_defaults(self):
        display = launcher.fixture_display_config()
        self.assertEqual({"debugMode", "animationsEnabled", "assistantName", "ui"}, set(display))
        self.assertFalse(display["debugMode"])
        ui = display["ui"]
        self.assertEqual({"fullscreen", "hud", "notifications"}, set(ui))
        self.assertEqual({"density", "sessionRailVisible", "theme"}, set(ui["fullscreen"]))
        self.assertEqual({"density": "COMFORTABLE", "sessionRailVisible": True,
                          "theme": "CHARCOAL"}, ui["fullscreen"])
        self.assertEqual({"enabled", "anchor", "offsetX", "offsetY", "width", "height", "scale",
                          "backgroundOpacity", "collapsed", "maxReplyLines", "showLatestReply",
                          "showStreamingPreview", "hideWithDebug", "hideOnOtherScreens"}, set(ui["hud"]))
        self.assertEqual(320, ui["hud"]["width"])
        self.assertEqual(240, ui["hud"]["height"])
        self.assertEqual(18, ui["hud"]["maxReplyLines"])
        self.assertFalse(ui["hud"]["enabled"])
        self.assertEqual({"enabled", "policy", "replyCompleted", "cardBatches", "taskFailures", "durationSeconds"},
                         set(ui["notifications"]))
        self.assertFalse(ui["notifications"]["enabled"])
        self.assertNotIn("version", display)
        self.assertNotIn("schemaVersion", display)

    def test_voice_fixture_is_current_eleven_fields_disabled_and_action_explicit(self):
        voice = launcher.fixture_voice_config()
        self.assertEqual({"enabled", "backend", "deviceId", "maxClipSeconds", "language", "cpuThreads",
                          "nativeModelDirectory", "httpBaseUrl", "httpModel", "credentialRef", "gameplayAction"}, set(voice))
        self.assertFalse(voice["enabled"])
        self.assertEqual("SEND", voice["gameplayAction"])
        self.assertEqual("DRAFT", launcher.fixture_voice_config("DRAFT")["gameplayAction"])
        self.assertIsNone(voice["credentialRef"])
        with self.assertRaises(ValueError):
            launcher.fixture_voice_config("legacy")

    def test_live_graphical_scenario_is_distinct_and_rejects_external_profile(self):
        self.assertIn("ui-live-ux-regressions", launcher.SCENARIOS)
        self.assertTrue(launcher.graphical_scenario("ui-live-ux-regressions"))
        self.assertEqual("COMPLETED", launcher.UI_OUTCOMES["ui-live-ux-regressions"])
        args = launcher.parser().parse_args(["fabric", "--scenario", "ui-live-ux-regressions",
                                             "--model-config", "/unused/ordinary-provider.json"])
        with self.assertRaisesRegex(ValueError, "local deterministic fixture"):
            launcher.prepare(args)
        args = launcher.parser().parse_args(["fabric", "--scenario", "ui-live-ux-regressions",
                                             "--question", "ordinary paid question"])
        with self.assertRaisesRegex(ValueError, "explicit held loopback"):
            launcher.prepare(args)

    def test_model_configuration_rejects_embedded_credentials_and_url_queries(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "model.json"
            config = launcher.fixture_model_config(18765)
            launcher.write_json(path, config)
            self.assertEqual(config, launcher.validate_model_config(path))
            for reference in ("local:secret", "api-key", "env:bad-variable"):
                config["profiles"][0]["credentialRef"] = reference
                launcher.write_json(path, config)
                with self.assertRaises(ValueError):
                    launcher.validate_model_config(path)
            config["profiles"][0]["credentialRef"] = "env:FIXTURE_KEY"
            config["profiles"][0]["baseUrl"] = "https://example.invalid/v1/?token=secret"
            launcher.write_json(path, config)
            with self.assertRaises(ValueError):
                launcher.validate_model_config(path)

    def test_model_configuration_requires_current_shape_without_rewriting(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "models.json"
            valid = launcher.fixture_model_config(18765)
            self.assertEqual({"defaultProfileId", "profiles"}, set(valid))
            for invalid in (
                [], {}, {"profiles": valid["profiles"]},
                {**valid, "extra": True}, {**valid, "profiles": []},
                {**valid, "profiles": {}}, {**valid, "profiles": [False]},
                {**valid, "defaultProfileId": None}, {**valid, "defaultProfileId": "missing"},
            ):
                launcher.write_json(path, invalid)
                original = path.read_bytes()
                with self.assertRaises(ValueError):
                    launcher.validate_model_config(path)
                self.assertEqual(original, path.read_bytes())

    def test_output_cannot_escape_ignored_e2e_tree(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self.assertEqual((repo / "build/e2e/client").resolve(), launcher.safe_output(repo / "build/e2e/client", repo))
            with self.assertRaises(ValueError):
                launcher.safe_output(repo / "saves", repo)
            with self.assertRaises(ValueError):
                launcher.safe_output(repo / "build/e2e/../../../saves", repo)

    def artifact(self, repo, loader="fabric", bootstrap=True, minecraft_target="26.2"):
        path = repo / loader / "build/libs" / f"openallay-{loader}-{minecraft_target}-0.4.1.jar"
        path.parent.mkdir(parents=True, exist_ok=True)
        (repo / "gradle").mkdir(exist_ok=True)
        (repo / "gradle/minecraft-target-loaders.json").write_bytes((launcher.REPO / "gradle/minecraft-target-loaders.json").read_bytes())
        (repo / "gradle.properties").write_text("version=0.4.1\n", encoding="utf-8")
        lock_path = repo / "distribution/extensions.lock.json"
        lock_path.parent.mkdir(parents=True, exist_ok=True)
        lock_path.write_bytes((launcher.REPO / "distribution/extensions.lock.json").read_bytes())
        verifier = launcher.bundled_extension_verifier()
        lock = verifier.prepare.load_manifest(lock_path)
        builder_path = verifier.resource_path(lock)
        descriptor = {"schemaVersion": 2, "id": lock["extensionId"], "name": "Minecraft Builder",
                      "version": lock["version"], "entrypoint": "dev.openallay.builder.BuilderExtension",
                      "provider": "OpenAllay", "summary": "Construction on the integrated server.",
                      "source": "https://github.com/nkanf-dev/OpenAllay-Extensions",
                      "support": {"targets": [{"loader": target, "minecraftVersionRange": game,
                                                "openAllayVersionRange": "[0.4.1,)",
                                                "openAllayApiVersionRange": "[0.4.0,0.5.0)"}
                                               for game in launcher.minecraft_targets() for target in (("forge",) if game == "1.19.2" else ("fabric", "neoforge"))],
                                  "minimumJavaVersion": 8,
                                  "requiredHostFeatures": ["minecraft:world-access"], "validatedTargetIds": []},
                      "requirements": {"capabilities": [], "extensions": [], "skills": []}}
        embedded = io.BytesIO()
        with zipfile.ZipFile(embedded, "w") as builder:
            for name in sorted(verifier.SHARED_ENTRIES):
                # Header-only fixtures meet the verifier's actual class checks.
                # They are not JVM-linkable and do not prove Minecraft behavior.
                content = (b"\xca\xfe\xba\xbe\x00\x00\x00\x34" if name.endswith(".class")
                           else b"canonical resource")
                builder.writestr(name, json.dumps(descriptor) if name == verifier.DESCRIPTOR else content)
        builder_content = embedded.getvalue()
        provenance = {"source": {**lock["source"], "dirty": False, "pinned": True},
                      **{key: lock[key] for key in ("project", "version", "extensionId", "openAllayApiVersion")},
                      "artifact": {"path": builder_path,
                                   "sha256": launcher.hashlib.sha256(builder_content).hexdigest()}}
        with zipfile.ZipFile(path, "w") as archive:
            if loader == "fabric":
                archive.writestr("fabric.mod.json", json.dumps({"id": "openallay", "version": "0.4.1", "jars": []}))
            else:
                archive.writestr("META-INF/mods.toml" if loader == "forge" or minecraft_target in ("1.20.1", "1.20.2", "1.20.3", "1.20.4") else "META-INF/neoforge.mods.toml",
                                 'modId="openallay"\nversion="0.4.1"\n' + ('[[dependencies.openallay]]\nmodId="forge"\n' if loader == "forge" else ''))
                archive.writestr("META-INF/jarjar/metadata.json", json.dumps({"jars": []}))
            archive.writestr(builder_path, builder_content)
            archive.writestr(verifier.PROVENANCE, json.dumps(provenance))
            archive.writestr("dev/openallay/guide/e2e/GuideClientE2EController.class",
                             b"openallay.e2e.createWorld" if bootstrap else b"old production controller")
        return path

    def environment(self, repo):
        mcroot = repo.parent / "minecraft"
        version = mcroot / "versions/26.2"
        version.mkdir(parents=True)
        (version / "26.2.jar").write_bytes(b"official Minecraft")
        launcher.write_json(version / "26.2.json", {
            "id": "26.2", "type": "release", "javaVersion": {"majorVersion": 25}, "libraries": [], "assetIndex": {"id": "32"},
            "downloads": {"client": {"sha1": launcher.digest_with(version / "26.2.jar", "sha1"),
                                     "size": (version / "26.2.jar").stat().st_size}},
            "arguments": {"jvm": [{"rules": [{"action": "allow", "os": {"name": "osx"}}],
                                   "value": "-XstartOnFirstThread"}, "-cp", "${classpath}"],
                          "game": ["--gameDir", "${game_directory}", "--assetsDir", "${assets_root}",
                                   "--username", "${auth_player_name}", "--uuid", "${auth_uuid}",
                                   "--accessToken", "${auth_access_token}",
                                   {"rules": [{"action": "allow", "features": {"is_demo_user": True}}],
                                    "value": "--demo"}]}})
        (mcroot / "assets/indexes").mkdir(parents=True)
        launcher.write_json(mcroot / "assets/indexes/32.json", {"objects": {}})
        cache = repo.parent / "gradle"
        loader_dir = cache / "net.fabricmc/fabric-loader/0.19.3/hash"
        loader_dir.mkdir(parents=True)
        with zipfile.ZipFile(loader_dir / "fabric-loader-0.19.3.jar", "w") as archive:
            archive.writestr("fabric-installer.json", json.dumps({"libraries": {"common": []},
                                                               "mainClass": {"client": "net.fabricmc.loader.impl.launch.knot.KnotClient"}}))
        profile = repo / "gradle/minecraft-targets/26.2.properties"
        profile.parent.mkdir(parents=True)
        profile.write_bytes((launcher.REPO / "gradle/minecraft-targets/26.2.properties").read_bytes())
        fabric_id = "fabric-loader-0.19.3-26.2"
        fabric_profile = mcroot / "versions" / fabric_id / (fabric_id + ".json")
        fabric_profile.parent.mkdir(parents=True)
        launcher.write_json(fabric_profile, {"id": fabric_id, "inheritsFrom": "26.2", "type": "release",
                                             "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
                                             "libraries": [{"name": "net.fabricmc:fabric-loader:0.19.3", "url": "https://maven.fabricmc.net/"}],
                                             "arguments": {"jvm": ["-DFabricMcEmu= net.minecraft.client.main.Main "],
                                                           "game": []}})
        api = repo / "fabric/runs/client/mods/fabric-api-0.152.1+26.2.jar"
        api.parent.mkdir(parents=True)
        with zipfile.ZipFile(api, "w") as archive:
            archive.writestr("fabric.mod.json", json.dumps({"id": "fabric-api", "version": "0.152.1+26.2"}))
        java = repo.parent / "java"
        java.write_text("#!/bin/sh\necho 'openjdk version \"25.0.2\"' >&2\n", encoding="utf-8")
        java.chmod(0o755)
        return mcroot, cache, java

    def rewrite_archive(self, path, entries):
        with zipfile.ZipFile(path, "w") as archive:
            for name, content in entries.items():
                archive.writestr(name, content)

    def artifact_entries(self, path):
        with zipfile.ZipFile(path) as archive:
            return {name: archive.read(name) for name in archive.namelist()}

    def change_builder_resources(self, entries, change):
        resource = next(name for name in entries if "openallay-builder-" in name)
        with zipfile.ZipFile(io.BytesIO(entries[resource])) as builder:
            resources = {name: builder.read(name) for name in builder.namelist()}
        change(resources)
        embedded = io.BytesIO()
        with zipfile.ZipFile(embedded, "w") as builder:
            for name, content in resources.items():
                builder.writestr(name, content)
        entries[resource] = embedded.getvalue()
        provenance_path = "META-INF/openallay/distribution.json"
        provenance = json.loads(entries[provenance_path])
        provenance["artifact"]["sha256"] = launcher.hashlib.sha256(entries[resource]).hexdigest()
        entries[provenance_path] = json.dumps(provenance)

    def test_both_loaders_verify_current_raw_universal_builder_and_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            identities = []
            for loader in ("fabric", "neoforge"):
                artifact = self.artifact(repo, loader)
                identity = launcher.packaged_artifact(artifact, loader, repo=repo)
                identities.append(identity)
                self.assertEqual("META-INF/openallay/bundled-extensions/openallay-builder-universal-0.4.0.jar",
                                 identity["bundledBuilder"])
                self.assertNotIn("nestedBuilder", identity)
            self.assertEqual(identities[0]["bundledBuilderSha256"], identities[1]["bundledBuilderSha256"])

    def test_packaged_builder_requires_exact_hash_and_pinned_clean_source_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            artifact = self.artifact(repo)
            original = self.artifact_entries(artifact)
            for field, value, expected in (("revision", "0" * 40, "Source revision mismatch"),
                                           ("dirty", True, "Dirty Extension"),
                                           ("pinned", False, "Unpinned development"),
                                           ("sha256", "0" * 64, "SHA-256 mismatch")):
                with self.subTest(field=field):
                    entries = dict(original)
                    path = "META-INF/openallay/distribution.json"
                    provenance = json.loads(entries[path])
                    provenance["artifact" if field == "sha256" else "source"][field] = value
                    entries[path] = json.dumps(provenance)
                    self.rewrite_archive(artifact, entries)
                    with self.assertRaisesRegex(ValueError, expected):
                        launcher.packaged_artifact(artifact, "fabric", repo=repo)

    def test_packaged_builder_rejects_loader_registration_and_renamed_duplicate_payloads(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            for loader in ("fabric", "neoforge"):
                artifact = self.artifact(repo, loader)
                entries = self.artifact_entries(artifact)
                resource = next(name for name in entries if "openallay-builder-" in name)
                if loader == "fabric":
                    metadata = json.loads(entries["fabric.mod.json"])
                    metadata["jars"] = [{"file": resource}]
                    entries["fabric.mod.json"] = json.dumps(metadata)
                else:
                    entries["META-INF/jarjar/metadata.json"] = json.dumps({"jars": [{"path": resource}]})
                self.rewrite_archive(artifact, entries)
                with self.assertRaisesRegex(ValueError, "must not be loader registered"):
                    launcher.packaged_artifact(artifact, loader, repo=repo)
                entries["fabric.mod.json" if loader == "fabric" else "META-INF/jarjar/metadata.json"] = (
                    json.dumps({"id": "openallay", "version": "0.4.1", "jars": []}) if loader == "fabric"
                    else json.dumps({"jars": []}))
                entries["META-INF/jars/renamed.jar"] = entries[resource]
                self.rewrite_archive(artifact, entries)
                with self.assertRaisesRegex(ValueError, "Duplicate bundled Builder"):
                    launcher.packaged_artifact(artifact, loader, repo=repo)

    def test_packaged_builder_rejects_duplicate_archive_entries(self):
        import warnings
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            artifact = self.artifact(repo)
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(artifact, "a") as archive:
                    archive.writestr("fabric.mod.json", archive.read("fabric.mod.json"))
            with self.assertRaisesRegex(ValueError, "Duplicate entries"):
                launcher.packaged_artifact(artifact, "fabric", repo=repo)

    def test_packaged_builder_rejects_native_loader_classes_and_non_java8_header(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            artifact = self.artifact(repo)
            original = self.artifact_entries(artifact)
            for name, content, expected in (
                ("fabric.mod.json", b'{}', "Nonportable or legacy"),
                ("net/minecraft/Bad.class", b"\xca\xfe\xba\xbe\x00\x00\x00\x34", "Nonportable or legacy"),
                ("dev/openallay/builder/BuilderExtension.class", b"\xca\xfe\xba\xbe\x00\x00\x00\x45", "Non-Java8")):
                with self.subTest(name=name):
                    entries = dict(original)
                    self.change_builder_resources(entries, lambda resources: resources.update({name: content}))
                    self.rewrite_archive(artifact, entries)
                    with self.assertRaisesRegex(ValueError, expected):
                        launcher.packaged_artifact(artifact, "fabric", repo=repo)

    def test_packaged_builder_rejects_old_api_and_wrong_java_manifest_floor(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            artifact = self.artifact(repo)
            original = self.artifact_entries(artifact)
            for field in ("api", "java", "minecraft"):
                entries = dict(original)
                def change(resources):
                    manifest = json.loads(resources["META-INF/openallay-extension.json"])
                    if field == "api":
                        manifest["support"]["targets"][0]["openAllayApiVersionRange"] = "[0.1.0,0.2.0)"
                    elif field == "minecraft":
                        manifest["support"]["targets"][0]["minecraftVersionRange"] = "[1.21.1,26.3)"
                    else:
                        manifest["support"]["minimumJavaVersion"] = 17
                    resources["META-INF/openallay-extension.json"] = json.dumps(manifest)
                self.change_builder_resources(entries, change)
                self.rewrite_archive(artifact, entries)
                expected = {"api": "SDK support range", "java": "Java8 support", "minecraft": "prepared native target loaders"}
                with self.assertRaisesRegex(ValueError, expected[field]):
                    launcher.packaged_artifact(artifact, "fabric", repo=repo)

    def runtime_record(self, path):
        return {"sha256": launcher.digest(path), "sha1": launcher.digest_with(path, "sha1"),
                "size": path.stat().st_size}

    def isolated_environment(self, repo):
        repo = repo.resolve()
        mcroot, cache, java = self.environment(repo)
        isolated = repo / "build/e2e/runtime/26.2/minecraft"
        isolated.parent.mkdir(parents=True)
        launcher.shutil.move(str(mcroot), str(isolated))
        loader = isolated / "libraries/net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.jar"
        loader.parent.mkdir(parents=True)
        loader.write_bytes((cache / "net.fabricmc/fabric-loader/0.19.3/hash/fabric-loader-0.19.3.jar").read_bytes())
        api = isolated / "libraries/net/fabricmc/fabric-api/fabric-api/0.152.1+26.2/fabric-api-0.152.1+26.2.jar"
        api.parent.mkdir(parents=True)
        api.write_bytes((repo / "fabric/runs/client/mods/fabric-api-0.152.1+26.2.jar").read_bytes())
        files = {path.relative_to(isolated).as_posix(): self.runtime_record(path)
                 for path in isolated.rglob("*") if path.is_file()}
        receipt = {"loader": "fabric", "minecraft": "26.2", "javaRequired": 25, "java": str(java),
                   "minecraftRoot": str(isolated), "profile": "fabric-loader-0.19.3-26.2", "fabricApi": str(api),
                   "sourceProfile": "gradle/minecraft-targets/26.2.properties",
                   "sourceProfileSha256": launcher.digest(repo / "gradle/minecraft-targets/26.2.properties"),
                   "pins": {"minecraft_version": "26.2", "java_version": "25", "fabric_loader_version": "0.19.3",
                            "fabric_version": "0.152.1+26.2", "neoforge_version": "26.2.0.25-beta"},
                   "files": files, "mechanism": "official-client-installer"}
        receipt_path = isolated / ".provision/fabric-runtime.json"
        receipt_path.parent.mkdir()
        launcher.write_json(receipt_path, receipt)
        return isolated, cache, java, receipt_path

    def test_isolated_runtime_prepares_hash_verified_classpath_and_real_profile_arguments(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java, receipt = self.isolated_environment(repo)
            args = launcher.parser().parse_args(["fabric", "--run-id", "isolated", "--minecraft-root", str(mcroot),
                                                 "--gradle-cache", str(cache), "--java", str(java)])
            output, manifest = launcher.prepare(args, repo)
            self.assertEqual({"path": str(receipt), "sha256": launcher.digest(receipt)}, manifest["runtimeProvision"])
            self.assertTrue(all(Path(path).is_relative_to(mcroot) for path in manifest["classPath"]))
            self.assertIn("-DFabricMcEmu= net.minecraft.client.main.Main ", manifest["command"])
            self.assertEqual("0", manifest["command"][manifest["command"].index("--accessToken") + 1])
            self.assertEqual([], list((output / "game/saves").iterdir()))
            self.assertTrue((output / "game/mods/fabric-api-0.152.1+26.2.jar").is_file())

    def test_isolated_runtime_rejects_missing_receipt_wrong_pins_and_modified_files(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java, receipt_path = self.isolated_environment(repo)
            receipt = json.loads(receipt_path.read_text())
            receipt_path.unlink()
            with self.assertRaisesRegex(ValueError, "requires its official provision manifest"):
                launcher.read_runtime_provision(mcroot, "fabric", repo)
            for field, value in (("loader", "neoforge"), ("minecraft", "1.21.1"), ("javaRequired", 21),
                                 ("sourceProfileSha256", "0" * 64), ("pins", {})):
                with self.subTest(field=field):
                    launcher.write_json(receipt_path, {**receipt, field: value})
                    with self.assertRaisesRegex(ValueError, "exact source target"):
                        launcher.read_runtime_provision(mcroot, "fabric", repo)
            launcher.write_json(receipt_path, receipt)
            loader_jar = mcroot / "libraries/net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.jar"
            loader_jar.unlink()
            with self.assertRaisesRegex(ValueError, "isolated official runtime library is missing"):
                launcher.cached_library("net.fabricmc:fabric-loader:0.19.3", mcroot, cache, allow_gradle=False)
            game_jar = mcroot / "versions/26.2/26.2.jar"
            game_jar.write_bytes(b"modified runtime")
            with self.assertRaisesRegex(ValueError, "does not match provision manifest"):
                launcher.validate_runtime_classpath([str(game_jar)], mcroot, "fabric", repo)

    def test_runtime_classpath_rejects_project_build_gradle_fallback_and_wrong_isolation(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java, receipt = self.isolated_environment(repo)
            for path in (repo / "common/build/classes/java/main", repo / "fabric/build/moddev/a.jar",
                         repo / ".gradle/caches/minecraft/a.jar", cache / "native-game.jar",
                         mcroot / "versions/1.21.1/1.21.1.jar"):
                with self.subTest(path=path):
                    with self.assertRaisesRegex(ValueError, "project source classes or Gradle"):
                        launcher.validate_runtime_classpath([str(path)], mcroot, "fabric", repo)
            wrong = repo / "build/e2e/another-runtime/minecraft"
            with self.assertRaisesRegex(ValueError, "project source classes or Gradle"):
                launcher.validate_runtime_classpath([str(wrong / "libraries/a.jar")], wrong, "fabric", repo)
            external = repo.parent / "installed-minecraft"
            self.assertIsNone(launcher.validate_runtime_classpath([str(external / "libraries/a.jar")], external,
                                                                  "fabric", repo))

    def test_missing_installed_fabric_profile_fails_instead_of_synthetic_native_alias(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            profile = mcroot / "versions/fabric-loader-0.19.3-26.2/fabric-loader-0.19.3-26.2.json"
            profile.unlink()
            args = launcher.parser().parse_args(["fabric", "--minecraft-root", str(mcroot),
                                                 "--gradle-cache", str(cache), "--java", str(java)])
            with self.assertRaisesRegex(ValueError, "locally installed Minecraft version metadata is missing"):
                launcher.prepare(args, repo)

    def test_linux_rules_keep_current_host_artifacts_and_natives_arguments(self):
        self.assertTrue(launcher.rules_allow([{"action": "allow", "os": {"name": "linux", "arch": "x86_64"}}],
                                            os_name="linux", arch="x86_64"))
        self.assertFalse(launcher.rules_allow([{"action": "allow", "os": {"name": "osx"}}], os_name="linux"))
        self.assertEqual(["-Djava.library.path=/runtime/native path"], launcher.expand_arguments(
            ["-Djava.library.path=${natives_directory}"], {"natives_directory": "/runtime/native path"}))

    def test_prepare_loads_only_packaged_mod_with_default_restricted_javascript(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            args = launcher.parser().parse_args(["fabric", "--run-id", "test-restricted",
                                                 "--minecraft-root", str(mcroot), "--gradle-cache", str(cache),
                                                 "--java", str(java)])
            with patch.object(launcher, "system_name", return_value="osx"):
                output, manifest = launcher.prepare(args, repo)
            self.assertTrue(manifest["noGameLaunched"])
            self.assertEqual("builder-restricted", manifest["scenario"])
            self.assertFalse(manifest["unrestrictedOptIn"])
            self.assertEqual("acceptance-instrumented", manifest["packagedArtifact"]["kind"])
            self.assertTrue(manifest["world"].startswith("openallay-builder-fabric-"))
            self.assertEqual([], list((output / "game/saves").iterdir()))
            config = output / "game/config/openallay"
            self.assertEqual({"enabled": False}, json.loads((config / "unrestricted-javascript.json").read_text()))
            self.assertEqual({"enabled": False}, json.loads((config / "experimental-commands.json").read_text()))
            self.assertEqual(launcher.fixture_display_config(),
                             json.loads((config / "display.json").read_text()))
            self.assertEqual({"defaultProfileId", "profiles"}, set(json.loads((config / "models.json").read_text())))
            self.assertNotIn("schemaVersion", manifest)
            self.assertIn("-XstartOnFirstThread", manifest["command"])
            self.assertNotIn("--demo", manifest["command"])
            self.assertNotIn("--quickPlaySingleplayer", manifest["command"])
            self.assertTrue(all(not Path(path).is_relative_to(repo) for path in manifest["classPath"]))
            self.assertEqual(2, len(list((output / "game/mods").glob("*.jar"))))
            self.assertIn("-Dopenallay.e2e.shutdown=false", manifest["command"])
            username = manifest["command"][manifest["command"].index("--username") + 1]
            player_uuid = manifest["command"][manifest["command"].index("--uuid") + 1]
            self.assertEqual("BuilderProbe", username)
            self.assertLessEqual(len(username), 16)
            self.assertEqual(launcher.offline_uuid(username), player_uuid)
            self.assertIn("simulationDistance:5", (output / "game/options.txt").read_text())
            with self.assertRaises(ValueError):
                launcher.prepare(args, repo)

    def test_positive_builder_scenarios_prepare_without_full_access(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            common = ["fabric", "--minecraft-root", str(mcroot), "--gradle-cache", str(cache), "--java", str(java)]
            for scenario in ("builder-restricted", "builder-acceptance", "builder-partial", "builder-cancel"):
                with self.subTest(scenario=scenario):
                    args = launcher.parser().parse_args(common + ["--run-id", scenario, "--scenario", scenario])
                    output, manifest = launcher.prepare(args, repo)
                    self.assertEqual(scenario, manifest["scenario"])
                    self.assertFalse(args.enable_unrestricted)
                    self.assertFalse(manifest["unrestrictedOptIn"])
                    self.assertEqual({"enabled": False}, json.loads(
                        (output / "game/config/openallay/unrestricted-javascript.json").read_text()))
                    self.assertIn("-Dopenallay.e2e.scenario=" + scenario, manifest["command"])
                    self.assertIn("-Dopenallay.e2e.question=OpenAllay E2E Builder " + scenario.removeprefix("builder-"),
                                  manifest["command"])
                    self.assertNotIn("--quickPlaySingleplayer", manifest["command"])

    def test_full_access_remains_a_separate_explicit_disposable_opt_in(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            common = ["fabric", "--scenario", "builder-acceptance", "--minecraft-root", str(mcroot),
                      "--gradle-cache", str(cache), "--java", str(java)]
            for run_id, extra, enabled in (("restricted-positive", [], False),
                                          ("explicit-full-access", ["--enable-unrestricted"], True)):
                with self.subTest(run_id=run_id):
                    output, manifest = launcher.prepare(
                        launcher.parser().parse_args(common + ["--run-id", run_id] + extra), repo)
                    self.assertIs(manifest["unrestrictedOptIn"], enabled)
                    self.assertEqual({"enabled": enabled}, json.loads(
                        (output / "game/config/openallay/unrestricted-javascript.json").read_text()))

    def test_restricted_builder_proof_rejects_full_access(self):
        with tempfile.TemporaryDirectory() as directory:
            for extra in ([], ["--scenario", "builder-restricted"]):
                with self.subTest(extra=extra):
                    args = launcher.parser().parse_args(["fabric", "--enable-unrestricted"] + extra)
                    with self.assertRaisesRegex(ValueError, "Restricted Builder proof and UI scenarios"):
                        launcher.prepare(args, Path(directory))

    def test_launch_refuses_prebootstrap_production_jar_without_exec(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo, bootstrap=False)
            mcroot, cache, java = self.environment(repo)
            args = launcher.parser().parse_args(["fabric", "--run-id", "production", "--minecraft-root", str(mcroot),
                                                 "--gradle-cache", str(cache), "--java", str(java)])
            output, _ = launcher.prepare(args, repo)
            with patch.object(launcher.subprocess, "Popen") as execute:
                with self.assertRaisesRegex(ValueError, "native fresh-world"):
                    launcher.launch_prepared(output, repo)
                execute.assert_not_called()

    def test_launch_refuses_modified_prepared_mods_or_existing_world(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            args = launcher.parser().parse_args(["fabric", "--run-id", "tamper", "--minecraft-root", str(mcroot),
                                                 "--gradle-cache", str(cache), "--java", str(java)])
            output, _ = launcher.prepare(args, repo)
            with patch.object(launcher.subprocess, "Popen") as execute:
                (output / "game/mods/duplicate.jar").write_bytes(b"not approved")
                with self.assertRaisesRegex(ValueError, "files changed"):
                    launcher.launch_prepared(output, repo)
                (output / "game/mods/duplicate.jar").unlink()
                (output / "game/saves/existing-world").mkdir()
                with self.assertRaisesRegex(ValueError, "already contains a world"):
                    launcher.launch_prepared(output, repo)
                execute.assert_not_called()


    def test_proxy_is_explicit_local_and_never_accepts_credentials(self):
        with patch.dict(launcher.os.environ, {"HTTPS_PROXY": "http://127.0.0.1:7890"}, clear=True):
            self.assertEqual([], launcher.proxy_arguments(False))
            args = launcher.proxy_arguments(True)
            self.assertIn("-Dhttps.proxyHost=127.0.0.1", args)
            self.assertIn("-Dhttps.proxyPort=7890", args)
        for proxy in ("http://user:secret@127.0.0.1:7890", "http://127.0.0.1:7890/secret",
                      "https://example.invalid:7890", "http://127.0.0.1:7890?token=secret"):
            with patch.dict(launcher.os.environ, {"HTTPS_PROXY": proxy}, clear=True):
                with self.assertRaises(ValueError):
                    launcher.proxy_arguments(True)

    def test_resume_only_opens_prior_disposable_world_and_uses_separate_reports(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            args = launcher.parser().parse_args(["fabric", "--run-id", "original", "--scenario", "builder-acceptance",
                                                 "--minecraft-root", str(mcroot),
                                                 "--gradle-cache", str(cache), "--java", str(java)])
            previous, prior = launcher.prepare(args, repo)
            self.assertFalse(prior["unrestrictedOptIn"])
            resume = launcher.parser().parse_args(["--resume-prepared", str(previous), "--run-id", "reload",
                                                   "--scenario", "builder-reload"])
            with self.assertRaisesRegex(ValueError, "prior native-created disposable world"):
                launcher.prepare_resume(resume, repo)
            world = previous / "game/saves" / prior["world"]
            world.mkdir()
            (world / "level.dat").write_bytes(b"native-placeholder")
            with self.assertRaisesRegex(ValueError, "previously launched"):
                launcher.prepare_resume(resume, repo)
            prior["noGameLaunched"] = False
            launcher.write_json(previous / "report.json", {"outcome": "COMPLETED", "nativeAcceptance": {"outcome": "PASSED"}})
            launcher.write_json(previous / "launch.json", prior)
            old_manifest = (previous / "launch.json").read_bytes()
            old_report = (previous / "report.json").read_bytes()
            output, manifest = launcher.prepare_resume(resume, repo)
            self.assertFalse(resume.enable_unrestricted)
            self.assertFalse(manifest["unrestrictedOptIn"])
            self.assertEqual({"enabled": False}, json.loads(
                (previous / "game/config/openallay/unrestricted-javascript.json").read_text()))
            self.assertEqual(old_manifest, (previous / "launch.json").read_bytes())
            self.assertEqual(old_report, (previous / "report.json").read_bytes())
            self.assertEqual(previous / "phases/reload", output)
            self.assertEqual(prior["gameDirectory"], manifest["gameDirectory"])
            self.assertNotEqual(prior["report"], manifest["report"])
            self.assertIn("-Dopenallay.e2e.resumeWorld=" + prior["world"], manifest["command"])
            self.assertTrue(all(not arg.startswith("-Dopenallay.e2e.createWorld") for arg in manifest["command"]))
            self.assertNotIn("--quickPlaySingleplayer", manifest["command"])
            self.assertEqual({"sha256": prior["packagedArtifact"]["sha256"]},
                             {"sha256": manifest["packagedArtifact"]["sha256"]})
            with patch.object(launcher.subprocess, "Popen") as execute:
                (previous / "game/saves/another-world").mkdir()
                with self.assertRaisesRegex(ValueError, "native world is missing or changed"):
                    launcher.launch_prepared(output, repo)
                execute.assert_not_called()

    def test_asset_download_rejects_non_official_hosts_before_network(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(launcher, "urlopen") as request:
                with self.assertRaisesRegex(ValueError, "official Mojang"):
                    launcher.verified_download("https://example.invalid/file", Path(directory) / "file", "0" * 40, 1)
                request.assert_not_called()

    def test_asset_fetch_retries_and_checks_exact_bytes(self):
        content = b"verified asset bytes"
        sha1 = launcher.hashlib.sha1(content).hexdigest()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "asset"
            with patch.object(launcher, "urlopen", side_effect=[OSError("temporary"), io.BytesIO(content)]) as request:
                launcher.verified_download("https://resources.download.minecraft.net/ab/hash", path, sha1, len(content))
                self.assertEqual(content, path.read_bytes())
                self.assertEqual(2, request.call_count)
            with patch.object(launcher, "urlopen", return_value=io.BytesIO(content)):
                with self.assertRaisesRegex(ValueError, "hash or size"):
                    launcher.verified_download("https://resources.download.minecraft.net/ab/hash", path, "0" * 40, len(content))
            self.assertEqual([], list(Path(directory).glob("*.download-*")))

    def test_final_gate_rejects_request_or_native_failure_even_when_java_exit_zero(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "report.json"
            for value in ({"outcome": "HARNESS_FAILED"}, {"outcome": "FAILED"},
                          {"outcome": "COMPLETED"},
                          {"outcome": "COMPLETED", "nativeAcceptance": {"outcome": "FAILED"}}):
                launcher.write_json(report, value)
                with self.assertRaises(ValueError):
                    launcher.validate_report(report)
            good = {"outcome": "COMPLETED", "nativeAcceptance": {"outcome": "PASSED"}}
            launcher.write_json(report, good)
            self.assertEqual(good, launcher.validate_report(report))

    def test_live_requires_explicit_real_question_and_profile(self):
        with tempfile.TemporaryDirectory() as directory:
            for scenario in ("builder-live", "builder-live-copy"):
                with self.subTest(scenario=scenario):
                    args = launcher.parser().parse_args(["fabric", "--scenario", scenario])
                    with self.assertRaisesRegex(ValueError, "explicit ordinary provider question"):
                        launcher.prepare(args, Path(directory))

    def test_live_builder_and_live_undo_prepare_with_restricted_javascript(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            model_config = repo / "live-models.json"
            models = launcher.fixture_model_config(18765)
            models["profiles"][0]["baseUrl"] = "https://paid-provider.invalid/v1/"
            models["profiles"][0]["credentialRef"] = "env:OPENALLAY_PAID_TEST_KEY"
            launcher.write_json(model_config, models)
            common = ["fabric", "--minecraft-root", str(mcroot), "--gradle-cache", str(cache), "--java", str(java),
                      "--question", "Build the reviewed test structure.", "--model-config", str(model_config)]
            for scenario in ("builder-live", "builder-live-copy"):
                with self.subTest(scenario=scenario):
                    output, manifest = launcher.prepare(launcher.parser().parse_args(
                        common + ["--run-id", scenario, "--scenario", scenario]), repo)
                    self.assertFalse(manifest["unrestrictedOptIn"])
                    self.assertEqual({"enabled": False}, json.loads(
                        (output / "game/config/openallay/unrestricted-javascript.json").read_text()))
                    self.assertEqual(models, json.loads((output / "game/config/openallay/models.json").read_text()))
                    self.assertIn("-Dopenallay.e2e.question=Build the reviewed test structure.", manifest["command"])
            # The last prepared phase is builder-live-copy, which alone may resume live undo.
            world = output / "game/saves" / manifest["world"]
            world.mkdir()
            (world / "level.dat").write_bytes(b"native-placeholder")
            manifest["noGameLaunched"] = False
            launcher.write_json(output / "report.json", {"outcome": "COMPLETED", "nativeAcceptance": {"outcome": "PASSED"}})
            launcher.write_json(output / "launch.json", manifest)
            missing = launcher.parser().parse_args(["--resume-prepared", str(output), "--scenario", "builder-live-undo"])
            with self.assertRaisesRegex(ValueError, "explicit ordinary provider question"):
                launcher.prepare_resume(missing, repo)
            resume = launcher.parser().parse_args(["--resume-prepared", str(output), "--run-id", "live-undo",
                                                   "--scenario", "builder-live-undo", "--question", "Undo the reviewed copy.",
                                                   "--model-config", str(model_config)])
            resumed, resumed_manifest = launcher.prepare_resume(resume, repo)
            self.assertEqual(output / "phases/live-undo", resumed)
            self.assertFalse(resumed_manifest["unrestrictedOptIn"])
            self.assertTrue(resumed_manifest["nativeSavedWorldReuse"])
            self.assertEqual(manifest["gameDirectory"], resumed_manifest["gameDirectory"])
            self.assertEqual({"enabled": False}, json.loads(
                (output / "game/config/openallay/unrestricted-javascript.json").read_text()))
            self.assertIn("-Dopenallay.e2e.scenario=builder-live-undo", resumed_manifest["command"])
            self.assertIn("-Dopenallay.e2e.question=Undo the reviewed copy.", resumed_manifest["command"])
            self.assertNotEqual(manifest["report"], resumed_manifest["report"])

    def test_offline_identity_matches_java_name_uuid(self):
        self.assertEqual("0ca3b4023d2036a9b3f027e089e111ae", launcher.offline_uuid("BuilderAcceptance"))

    def test_launch_supervises_java_and_checks_native_passed_report(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            args = launcher.parser().parse_args(["fabric", "--run-id", "supervised", "--minecraft-root", str(mcroot),
                                                 "--gradle-cache", str(cache), "--java", str(java)])
            output, prior = launcher.prepare(args, repo)
            launcher.write_json(output / "report.json", {"outcome": "COMPLETED", "nativeAcceptance": {"outcome": "PASSED"}})
            (output / "screenshots").mkdir()
            (output / "screenshots/11-native-world-builds.png").write_bytes(b"\x89PNG\r\n\x1a\nmock native final")
            process = unittest.mock.Mock(pid=12345)
            process.wait.return_value = 0
            with patch.object(launcher.subprocess, "Popen", return_value=process) as start, \
                 patch.dict(launcher.os.environ, {"OPENALLAY_E2E_FIXTURE_KEY": "fixture-only"}):
                launcher.launch_prepared(output, repo)
            self.assertTrue(start.call_args.kwargs["start_new_session"])
            self.assertEqual(output / "game", start.call_args.kwargs["cwd"])
            process.wait.assert_called_once_with(timeout=360)
            updated = json.loads((output / "launch.json").read_text())
            self.assertEqual(12345, updated["clientPid"])
            self.assertEqual(0, updated["clientExitCode"])

    def test_launch_timeout_kills_only_its_java_process_group(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            args = launcher.parser().parse_args(["fabric", "--run-id", "timeout", "--minecraft-root", str(mcroot),
                                                 "--gradle-cache", str(cache), "--java", str(java)])
            output, _ = launcher.prepare(args, repo)
            process = unittest.mock.Mock(pid=23456)
            process.poll.return_value = None
            process.wait.side_effect = [launcher.subprocess.TimeoutExpired("java", 600), 143]
            with patch.object(launcher.subprocess, "Popen", return_value=process), \
                 patch.object(launcher.os, "killpg") as stop, \
                 patch.dict(launcher.os.environ, {"OPENALLAY_E2E_FIXTURE_KEY": "fixture-only"}):
                with self.assertRaisesRegex(ValueError, "timeout or interrupt"):
                    launcher.launch_prepared(output, repo)
            stop.assert_called_once_with(23456, launcher.signal.SIGTERM)

    def test_low_impact_is_scoped_and_keeps_native_fixture_chunk_coverage(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            args = launcher.parser().parse_args(["fabric", "--run-id", "low-impact", "--low-impact",
                                                 "--minecraft-root", str(mcroot), "--gradle-cache", str(cache),
                                                 "--java", str(java)])
            output, manifest = launcher.prepare(args, repo)
            self.assertTrue(manifest["lowImpact"])
            self.assertIn("-Xms256M", manifest["command"])
            self.assertIn("-Xmx1536M", manifest["command"])
            options = (output / "game/options.txt").read_text()
            self.assertIn("renderDistance:4", options)
            self.assertIn("simulationDistance:5", options)
            self.assertIn("maxFps:10", options)

    def test_explicit_timeout_sets_both_native_and_supervisor_deadlines(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            args = launcher.parser().parse_args(["fabric", "--run-id", "long-native", "--timeout-seconds", "900",
                                                 "--minecraft-root", str(mcroot), "--gradle-cache", str(cache),
                                                 "--java", str(java)])
            _, manifest = launcher.prepare(args, repo)
            self.assertEqual(900, manifest["timeoutSeconds"])
            self.assertEqual(960, manifest["wallTimeoutSeconds"])
            self.assertIn("-Dopenallay.e2e.timeoutSeconds=900", manifest["command"])
            args.timeout_seconds = 0
            args.run_id = "invalid-timeout"
            with self.assertRaisesRegex(ValueError, "positive"):
                launcher.prepare(args, repo)

    def accepted_original(self, repo, run_id="original", enable_unrestricted=False):
        artifact = self.artifact(repo)
        mcroot, cache, java = self.environment(repo)
        args = launcher.parser().parse_args(["fabric", "--run-id", run_id, "--scenario", "builder-acceptance",
                                             "--minecraft-root", str(mcroot),
                                             "--gradle-cache", str(cache), "--java", str(java)]
                                            + (["--enable-unrestricted"] if enable_unrestricted else []))
        previous, prior = launcher.prepare(args, repo)
        world = previous / "game/saves" / prior["world"]
        world.mkdir()
        (world / "level.dat").write_bytes(b"native-placeholder")
        prior["noGameLaunched"] = False
        launcher.write_json(previous / "report.json", {"outcome": "COMPLETED", "nativeAcceptance": {"outcome": "PASSED"}})
        launcher.write_json(previous / "launch.json", prior)
        return previous, prior, artifact

    def replacement_artifact(self, original, directory, change_builder=False):
        destination = Path(directory) / original.name
        destination.parent.mkdir(parents=True)
        with zipfile.ZipFile(original) as old:
            entries = {name: old.read(name) for name in old.namelist()}
        for name in entries:
            if name.endswith("GuideClientE2EController.class"):
                entries[name] += b"fixed retained-anchor development harness"
            if change_builder and "openallay-builder-" in name:
                with zipfile.ZipFile(io.BytesIO(entries[name])) as builder:
                    resources = {entry: builder.read(entry) for entry in builder.namelist()}
                resources["assets/openallay_builder/building.js"] = b"return {changed:true};"
                embedded = io.BytesIO()
                with zipfile.ZipFile(embedded, "w") as builder:
                    for entry, content in resources.items():
                        builder.writestr(entry, content)
                entries[name] = embedded.getvalue()
                provenance_path = "META-INF/openallay/distribution.json"
                provenance = json.loads(entries[provenance_path])
                provenance["artifact"]["sha256"] = launcher.hashlib.sha256(entries[name]).hexdigest()
                entries[provenance_path] = json.dumps(provenance).encode("utf-8")
        with zipfile.ZipFile(destination, "w") as new:
            for name, content in entries.items():
                new.writestr(name, content)
        return destination

    def test_resume_defaults_to_restricted_and_full_access_requires_new_explicit_opt_in(self):
        for prior_enabled, resumed_enabled in ((False, False), (True, False), (False, True)):
            with self.subTest(prior_enabled=prior_enabled, resumed_enabled=resumed_enabled):
                with tempfile.TemporaryDirectory() as directory:
                    repo = Path(directory) / "repo"
                    previous, prior, _ = self.accepted_original(repo, enable_unrestricted=prior_enabled)
                    old_manifest = (previous / "launch.json").read_bytes()
                    old_report = (previous / "report.json").read_bytes()
                    resume = launcher.parser().parse_args([
                        "--resume-prepared", str(previous), "--run-id", "authority-choice", "--scenario", "builder-reload"]
                        + (["--enable-unrestricted"] if resumed_enabled else []))
                    output, manifest = launcher.prepare_resume(resume, repo)
                    self.assertIs(prior["unrestrictedOptIn"], prior_enabled)
                    self.assertIs(manifest["unrestrictedOptIn"], resumed_enabled)
                    self.assertEqual({"enabled": resumed_enabled}, json.loads(
                        (previous / "game/config/openallay/unrestricted-javascript.json").read_text()))
                    self.assertEqual(old_manifest, (previous / "launch.json").read_bytes())
                    self.assertEqual(old_report, (previous / "report.json").read_bytes())
                    self.assertEqual(prior["gameDirectory"], manifest["gameDirectory"])
                    self.assertTrue(manifest["nativeSavedWorldReuse"])
                    self.assertNotEqual(prior["report"], manifest["report"])
                    self.assertEqual(manifest, json.loads((output / "launch.json").read_text()))

    def test_resume_rejects_non_resume_scenario_even_with_full_access(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            previous, _, _ = self.accepted_original(repo)
            for extra in ([], ["--enable-unrestricted"]):
                resume = launcher.parser().parse_args(["--resume-prepared", str(previous),
                                                       "--scenario", "builder-acceptance"] + extra)
                with self.assertRaisesRegex(ValueError, "World resume requires builder-reload or builder-live-undo"):
                    launcher.prepare_resume(resume, repo)

    def test_resume_upgrade_retains_original_evidence_and_records_both_artifacts(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            previous, prior, original = self.accepted_original(repo)
            old_manifest = (previous / "launch.json").read_bytes()
            old_report = (previous / "report.json").read_bytes()
            replacement = self.replacement_artifact(original, Path(directory) / "replacement")
            args = launcher.parser().parse_args(["--resume-prepared", str(previous), "--run-id", "upgraded",
                                                 "--scenario", "builder-reload",
                                                 "--jar", str(replacement)])
            output, manifest = launcher.prepare_resume(args, repo)
            self.assertEqual(old_manifest, (previous / "launch.json").read_bytes())
            self.assertEqual(old_report, (previous / "report.json").read_bytes())
            upgrade = manifest["testHarnessUpgrade"]
            self.assertTrue(upgrade["developmentInstrumentationOnly"])
            self.assertEqual(prior["packagedArtifact"]["sha256"], upgrade["oldSha256"])
            self.assertNotEqual(upgrade["oldSha256"], upgrade["newSha256"])
            self.assertEqual(launcher.digest(replacement), upgrade["newSha256"])
            self.assertEqual(upgrade["oldSha256"], launcher.digest(upgrade["originalArtifactEvidence"]))
            self.assertEqual(manifest["previousPackagedArtifact"]["bundledBuilderSha256"],
                             manifest["newPackagedArtifact"]["bundledBuilderSha256"])
            installed = previous / "game/mods" / replacement.name
            self.assertEqual(upgrade["newSha256"], launcher.digest(installed))
            self.assertEqual(upgrade["newSha256"], manifest["preparedFiles"]["mods/" + installed.name])
            self.assertEqual(manifest["newPackagedArtifact"], manifest["packagedArtifact"])

    def test_resume_upgrade_rejects_changed_builder_before_any_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            previous, prior, original = self.accepted_original(repo)
            replacement = self.replacement_artifact(original, Path(directory) / "replacement", change_builder=True)
            before = {str(path.relative_to(previous)): path.read_bytes() for path in previous.rglob("*") if path.is_file()}
            args = launcher.parser().parse_args(["--resume-prepared", str(previous), "--run-id", "bad-upgrade",
                                                 "--scenario", "builder-reload",
                                                 "--jar", str(replacement)])
            with self.assertRaisesRegex(ValueError, "exact bundled Builder bytes"):
                launcher.prepare_resume(args, repo)
            after = {str(path.relative_to(previous)): path.read_bytes() for path in previous.rglob("*") if path.is_file()}
            self.assertEqual(before, after)
            self.assertFalse((previous / "phases/bad-upgrade").exists())
            self.assertFalse((previous / "evidence/harness-artifacts").exists())

    def test_resume_upgrade_checks_original_hash_before_replacement(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            previous, prior, original = self.accepted_original(repo)
            replacement = self.replacement_artifact(original, Path(directory) / "replacement")
            installed = previous / "game/mods" / original.name
            installed.write_bytes(b"unexpected installed change")
            args = launcher.parser().parse_args(["--resume-prepared", str(previous), "--run-id", "changed-original",
                                                 "--scenario", "builder-reload",
                                                 "--jar", str(replacement)])
            with self.assertRaisesRegex(ValueError, "mods changed"):
                launcher.prepare_resume(args, repo)
            self.assertEqual(b"unexpected installed change", installed.read_bytes())

    def test_model_diagnostics_is_default_off_and_opt_in_command_only(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            common = ["fabric", "--minecraft-root", str(mcroot), "--gradle-cache", str(cache), "--java", str(java)]
            ordinary, ordinary_manifest = launcher.prepare(launcher.parser().parse_args(common + ["--run-id", "ordinary"]), repo)
            opted, opted_manifest = launcher.prepare(launcher.parser().parse_args(common + ["--run-id", "diagnostics", "--model-diagnostics"]), repo)
            prop = "-Dopenallay.model.diagnostics=true"
            self.assertFalse(ordinary_manifest["modelDiagnostics"])
            self.assertNotIn(prop, ordinary_manifest["command"])
            self.assertTrue(opted_manifest["modelDiagnostics"])
            self.assertEqual(1, opted_manifest["command"].count(prop))
            self.assertEqual((ordinary / "game/config/openallay/models.json").read_bytes(),
                             (opted / "game/config/openallay/models.json").read_bytes())

    def test_resume_retains_diagnostics_and_opt_in_does_not_duplicate_property(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            previous, prior, _ = self.accepted_original(repo)
            prop = "-Dopenallay.model.diagnostics=true"
            main = prior["command"].index("net.fabricmc.loader.impl.launch.knot.KnotClient")
            prior["command"].insert(main, prop)
            prior["modelDiagnostics"] = True
            launcher.write_json(previous / "launch.json", prior)
            for run_id, extra in (("retained", []), ("explicit", ["--model-diagnostics"])):
                args = launcher.parser().parse_args(["--resume-prepared", str(previous), "--run-id", run_id,
                                                     "--scenario", "builder-reload"] + extra)
                _, manifest = launcher.prepare_resume(args, repo)
                self.assertTrue(manifest["modelDiagnostics"])
                self.assertEqual(1, manifest["command"].count(prop))

    def test_ui_scenarios_keep_unrestricted_off_and_map_only_explicit_flags(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            artifact = self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            common = ["fabric", "--minecraft-root", str(mcroot), "--gradle-cache", str(cache), "--java", str(java)]
            args = launcher.parser().parse_args(common + ["--run-id", "ui-stop", "--scenario", "ui-stop",
                                                         "--cancel-on-tool-start", "--professional-screenshots",
                                                         "--screenshot-manual-profile", "manual-public-luna",
                                                         "--screenshot-automatic-profile", "automatic-public-reference",
                                                         "--review-package", str(artifact)])
            output, manifest = launcher.prepare(args, repo)
            self.assertTrue(manifest["uiCapture"])
            self.assertEqual("CANCELLED", manifest["expectedTerminalOutcome"])
            self.assertFalse(manifest["unrestrictedOptIn"])
            self.assertFalse(json.loads((output / "game/config/openallay/unrestricted-javascript.json").read_text())["enabled"])
            for prop in ("-Dopenallay.e2e.cancelOnToolStart=true", "-Dopenallay.e2e.screenshotMatrix=professional",
                         "-Dopenallay.e2e.screenshotManualProfile=manual-public-luna",
                         "-Dopenallay.e2e.screenshotAutomaticProfile=automatic-public-reference",
                         "-Dopenallay.e2e.reviewPackage=" + str(artifact.resolve())):
                self.assertIn(prop, manifest["command"])

    def test_ui_stop_requires_explicit_cancel_and_rejects_unrestricted(self):
        with tempfile.TemporaryDirectory() as directory:
            for options, message in ((["--scenario", "ui-stop"], "explicit --cancel-on-tool-start"),
                                     (["--scenario", "ui-stop", "--cancel-on-tool-start", "--enable-unrestricted"], "cannot enable"),
                                     (["--scenario", "ui-provider-failure", "--cancel-on-tool-start"], "only for ui-stop")):
                args = launcher.parser().parse_args(["fabric"] + options)
                with self.assertRaisesRegex(ValueError, message):
                    launcher.prepare(args, Path(directory))

    def test_professional_capture_requires_explicit_profiles(self):
        args = launcher.parser().parse_args(["fabric", "--scenario", "ui-provider-failure", "--professional-screenshots"])
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "explicit manual and automatic"):
                launcher.prepare(args, Path(directory))

    def test_ui_failure_default_question_matches_the_provider_fixture(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            args = launcher.parser().parse_args(["fabric", "--run-id", "ui-failure-question",
                                                 "--scenario", "ui-provider-failure",
                                                 "--minecraft-root", str(mcroot), "--gradle-cache", str(cache),
                                                 "--java", str(java)])
            _, manifest = launcher.prepare(args, repo)
            self.assertIn("-Dopenallay.e2e.question=OpenAllay E2E UI provider failure", manifest["command"])
            self.assertNotIn("-Dopenallay.e2e.question=OpenAllay E2E UI provider-failure", manifest["command"])

    def test_ui_provider_failure_accepts_failed_capture_not_builder_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / "report.json"
            screenshots = root / "screenshots/screenshots"
            screenshots.mkdir(parents=True)
            manifest = {"report": str(report), "screenshots": str(root / "screenshots"),
                        "scenario": "ui-provider-failure", "professionalScreenshots": True}
            launcher.write_json(report, {"outcome": "FAILED", "failureCode": "provider_unavailable"})
            (screenshots / "25-native-world-final.png").write_bytes(b"\x89PNG\r\n\x1a\nretained capture")
            actual = launcher.validate_ui_capture(manifest)
            self.assertEqual("FAILED", actual["outcome"])
            self.assertNotIn("nativeAcceptance", actual)
            with self.assertRaises(ValueError):
                launcher.validate_report(report)

    def test_ui_capture_requires_final_png_and_exact_terminal_outcome(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / "report.json"
            manifest = {"report": str(report), "screenshots": str(root / "screenshots"),
                        "scenario": "ui-provider-failure", "professionalScreenshots": True}
            launcher.write_json(report, {"outcome": "FAILED"})
            with self.assertRaisesRegex(ValueError, "final PNG"):
                launcher.validate_ui_capture(manifest)
            (root / "screenshots").mkdir()
            (root / "screenshots/25-native-world-final.png").write_bytes(b"not png")
            with self.assertRaisesRegex(ValueError, "final PNG"):
                launcher.validate_ui_capture(manifest)
            launcher.write_json(report, {"outcome": "COMPLETED"})
            with self.assertRaisesRegex(ValueError, "terminal outcome"):
                launcher.validate_ui_capture(manifest)

    def test_final_native_frame_matches_the_actual_ui_or_builder_producer(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            screenshots = root / "screenshots"
            screenshots.mkdir()
            png = b"\x89PNG\r\n\x1a\nsynthetic frame, not a game proof"
            for scenario, expected in (("ui-stop", "10-wide-about.png"),
                                       ("ui-provider-failure", "10-wide-about.png"),
                                       ("builder-restricted", "11-native-world-builds.png"),
                                       ("builder-acceptance", "11-native-world-builds.png"),
                                       ("builder-reload", "11-native-world-builds.png")):
                with self.subTest(scenario=scenario):
                    manifest = {"scenario": scenario, "screenshots": str(screenshots)}
                    with self.assertRaisesRegex(ValueError, "final PNG"):
                        launcher.validate_final_screenshot(manifest)
                    path = screenshots / expected
                    path.write_bytes(png)
                    launcher.validate_final_screenshot(manifest)
                    path.unlink()
            # A Builder world image is not a UI terminal matrix image.
            (screenshots / "11-native-world-builds.png").write_bytes(png)
            with self.assertRaisesRegex(ValueError, "final PNG"):
                launcher.validate_final_screenshot({"scenario": "ui-stop", "screenshots": str(screenshots)})

    def test_ui_stop_requires_actual_accepted_cancellation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "screenshots").mkdir()
            (root / "screenshots/10-wide-about.png").write_bytes(b"\x89PNG\r\n\x1a\nretained capture")
            report = root / "report.json"
            manifest = {"report": str(report), "screenshots": str(root / "screenshots"), "scenario": "ui-stop"}
            launcher.write_json(report, {"outcome": "CANCELLED"})
            with self.assertRaisesRegex(ValueError, "accepted real cancellation"):
                launcher.validate_ui_capture(manifest)
            actual = {"outcome": "CANCELLED", "actualStop": {"requested": True, "accepted": True, "terminalCancelled": True,
                                                               "pendingToolHasNoNormalizedResult": True}}
            launcher.write_json(report, actual)
            self.assertEqual(actual, launcher.validate_ui_capture(manifest))

    def test_fresh_version_defaults_to_checked_gradle_metadata_and_verifies_024_artifact(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            old_artifact = self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            new_artifact = old_artifact.with_name("openallay-fabric-26.2-0.4.2.jar")
            with zipfile.ZipFile(old_artifact) as old, zipfile.ZipFile(new_artifact, "w") as new:
                for name in old.namelist():
                    content = old.read(name)
                    if name == "fabric.mod.json":
                        metadata = json.loads(content)
                        metadata["version"] = "0.4.2"
                        content = json.dumps(metadata).encode()
                    new.writestr(name, content)
            (repo / "gradle.properties").write_text("version=0.4.2\n", encoding="utf-8")
            args = launcher.parser().parse_args(["fabric", "--run-id", "new-release", "--scenario", "ui-provider-failure",
                                                 "--minecraft-root", str(mcroot), "--gradle-cache", str(cache), "--java", str(java)])
            _, manifest = launcher.prepare(args, repo)
            self.assertEqual("0.4.2", manifest["packagedArtifact"]["modVersion"])
            self.assertEqual(new_artifact.name, manifest["packagedArtifact"]["name"])
            self.assertEqual("0.4.1", launcher.packaged_artifact(old_artifact, "fabric", "0.4.1")["modVersion"])
            with self.assertRaises(ValueError):
                launcher.packaged_artifact(new_artifact, "fabric", "0.4.1")

    def test_resume_uses_prior_manifest_version_not_current_gradle_release(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            previous, prior, original = self.accepted_original(repo)
            (repo / "gradle.properties").write_text("version=0.4.2\n", encoding="utf-8")
            replacement = self.replacement_artifact(original, Path(directory) / "replacement")
            args = launcher.parser().parse_args(["--resume-prepared", str(previous), "--run-id", "retained-old-version",
                                                 "--scenario", "builder-reload", "--jar", str(replacement)])
            _, manifest = launcher.prepare_resume(args, repo)
            self.assertEqual("0.4.1", manifest["packagedArtifact"]["modVersion"])

    def test_manual_graphical_report_requires_real_frame_hash_and_restore(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            frame = root / "native.png"
            frame.write_bytes(b"native-frame-test")
            report_path = root / "report.json"
            manifest = {"report": str(report_path), "scenario": "ui-manual-regressions"}
            report = {"outcome": "COMPLETED", "themeChangeCount": 4, "interactKeyRestored": True,
                      "microphoneCaptureAttempted": False,
                      "export": {"containsCurrentQuestionAndAnswer": True},
                      "nativeFrames": [{"path": str(frame), "sha256": launcher.digest(frame),
                                        "source": "native-mainRenderTarget"}]}
            launcher.write_json(report_path, report)
            self.assertEqual(report, launcher.validate_ui_capture(manifest))
            for key, value in (("themeChangeCount", 3), ("interactKeyRestored", False),
                               ("microphoneCaptureAttempted", True)):
                invalid = {**report, key: value}
                launcher.write_json(report_path, invalid)
                with self.assertRaises(ValueError):
                    launcher.validate_ui_capture(manifest)
            launcher.write_json(report_path, report)
            frame.write_bytes(b"changed-native-frame-test")
            with self.assertRaisesRegex(ValueError, "changed"):
                launcher.validate_ui_capture(manifest)

    def test_live_receipt_validator_never_substitutes_test_notification_for_card_paint(self):
        # Deliberately generated unit data. This validates the gate, not a native GUI pass.
        report = {"nativeFrames": [], "testNotification": {"title": "test"}, "outcome": "COMPLETED"}
        with self.assertRaisesRegex(ValueError, "frame evidence"):
            launcher.validate_live_ux_receipts(report)
        source = MODULE_PATH.read_text()
        self.assertIn('"detailNativeRecipeIds"', source)
        self.assertIn('"admittedSteerTimeline"', source)
        self.assertIn('"ownedHidden"', source)
        self.assertNotIn('"toolsCollapsed":', source)

    def test_ui_stop_rejects_missing_or_false_pending_tool_fact(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "screenshots").mkdir()
            (root / "screenshots/25-native-world-final.png").write_bytes(b"\x89PNG\r\n\x1a\nretained capture")
            report = root / "report.json"
            manifest = {"report": str(report), "screenshots": str(root / "screenshots"),
                        "scenario": "ui-stop", "professionalScreenshots": True}
            for pending in (None, False):
                stop = {"requested": True, "accepted": True, "terminalCancelled": True}
                if pending is not None:
                    stop["pendingToolHasNoNormalizedResult"] = pending
                launcher.write_json(report, {"outcome": "CANCELLED", "actualStop": stop})
                with self.assertRaisesRegex(ValueError, "accepted real cancellation"):
                    launcher.validate_ui_capture(manifest)

if __name__ == "__main__":
    unittest.main()
