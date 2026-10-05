#!/usr/bin/env python3
"""Offline contract tests. These do not launch or accept a Minecraft client."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("ci_runtime", Path(__file__).with_name("prepare-ci-minecraft-runtime.py"))
runtime = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runtime)


class Response(io.BytesIO):
    def __init__(self, body, url, size=None):
        super().__init__(body)
        self.url = url
        self.headers = {"Content-Length": str(len(body) if size is None else size)}

    def geturl(self):
        return self.url


class RuntimeTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name).resolve()
        self.repo = self.root / "repo"
        self.repo.mkdir()
        (self.repo / "gradle").mkdir()
        (self.repo / "gradle/minecraft-target-loaders.json").write_bytes((runtime.REPO / "gradle/minecraft-target-loaders.json").read_bytes())
        self.mcroot = self.repo / "build/e2e/runtime/26.2/minecraft"

    def tearDown(self):
        self.temporary.cleanup()

    def test_all_prepared_exact_target_profiles_are_read_through_one_authority(self):
        targets = runtime.minecraft_targets()
        self.assertEqual(24, len(targets))
        self.assertIn("1.20.1", targets)
        self.assertIn("1.21.1", targets)
        self.assertIn("26.3", targets)
        for target in targets:
            pins = runtime.read_pins(runtime.REPO, target)
            self.assertEqual(target, pins["minecraft_version"])
            self.assertIn(int(pins["java_version"]), (17, 21, 25))
        for target, java in (("1.20.1", "17"), ("1.21.1", "21"), ("26.3", "25")):
            self.assertEqual(java, runtime.read_pins(runtime.REPO, target)["java_version"])
        for target in ("../26.2", "1.20", "26.4"):
            with self.assertRaises(ValueError):
                runtime.read_pins(runtime.REPO, target)

    def test_exact_target_root_and_owner_refuse_cross_target_reuse(self):
        from types import SimpleNamespace
        self.assertEqual(("forge", "1.20.1-forge-47.1.106"), runtime.neoforge_identity(
            runtime.read_pins(runtime.REPO, "1.20.1")))
        root = self.repo / "build/e2e/runtime/1.20.1/minecraft"
        runtime.claim_root(root, "1.20.1")
        with self.assertRaisesRegex(ValueError, "ownership"):
            runtime.claim_root(root, "26.2")
        with patch.object(runtime, "read_pins", return_value=runtime.read_pins(runtime.REPO, "1.20.1")), \
                patch.object(runtime, "check_java") as checked:
            with self.assertRaisesRegex(ValueError, "<target>"):
                runtime.provision(SimpleNamespace(loader="fabric", minecraft_target="1.20.1",
                                                  minecraft_root=self.mcroot, java=self.root / "java"), self.repo)
            checked.assert_not_called()

    def test_real_early_and_modern_mapped_processor_shapes(self):
        # Exact mechanisms from the SHA-verified official 1.20.1 / 1.21.1 installers.
        # The tiny byte fixtures are not executable game acceptance.
        for target, mappings_group, tool_group in (("1.20.1", "de.oceanlabs.mcp:mcp_config", "net.minecraftforge"),
                                                  ("1.21.1", "net.neoforged:neoform", "net.neoforged.installertools")):
            with self.subTest(target=target):
                tool = tool_group + ":installertools:1"
                splitter = tool_group + ":jarsplitter:1"
                renamer = tool_group + ":AutoRenamingTool:1:all"
                patcher = tool_group + ":binarypatcher:1"
                mappings = mappings_group + ":" + target + "@zip"
                names = [tool, splitter, renamer, patcher, mappings]
                data = {key: {"client": "[net.minecraft:client:" + target + ":" + key.lower() + "]"}
                        for key in ("MAPPINGS", "MOJMAPS", "MERGED_MAPPINGS", "MC_SLIM", "MC_EXTRA", "MC_SRG", "PATCHED")}
                args = [
                    ["--task", "MCP_DATA", "--input", "[" + mappings + "]", "--output", "{MAPPINGS}", "--key", "mappings"],
                    ["--task", "DOWNLOAD_MOJMAPS", "--version", target, "--side", "{SIDE}", "--output", "{MOJMAPS}"],
                    ["--task", "MERGE_MAPPING", "--left", "{MAPPINGS}", "--right", "{MOJMAPS}", "--output", "{MERGED_MAPPINGS}",
                     "--classes", *(["--fields", "--methods"] if target == "1.21.1" else []), "--reverse-right"],
                    ["--input", "{MINECRAFT_JAR}", "--slim", "{MC_SLIM}", "--extra", "{MC_EXTRA}", "--srg", "{MERGED_MAPPINGS}"],
                    ["--input", "{MC_SLIM}", "--output", "{MC_SRG}", "--names", "{MERGED_MAPPINGS}", "--ann-fix", "--ids-fix", "--src-fix", "--record-fix"],
                    ["--clean", "{MC_SRG}", "--output", "{PATCHED}", "--apply", "{BINPATCH}"],
                ]
                install = {"minecraft": target, "libraries": [{"name": name} for name in names], "data": data,
                           "processors": [{"jar": name, "classpath": [name], "args": values}
                                          for name, values in zip([tool, tool, tool, splitter, renamer, patcher], args)]}
                outputs = runtime.processor_outputs(self.mcroot, install)
                self.assertEqual(set(data), set(outputs))
                for path in outputs.values():
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(b"tiny processor output")
                data["PATCHED_SHA"] = {"client": "'" + runtime.file_hash(outputs["PATCHED"], "sha1") + "'"}
                runtime.verify_processor_outputs(self.mcroot, install, outputs)
                outputs["PATCHED"].write_bytes(b"tampered processor output")
                with self.assertRaisesRegex(ValueError, "hash differs"):
                    runtime.verify_processor_outputs(self.mcroot, install, outputs)
                install["processors"][-1]["args"].append("--unknown")
                with self.assertRaisesRegex(ValueError, "Unknown official client processor"):
                    runtime.processor_outputs(self.mcroot, install)

    def test_old_mapped_installer_is_explicit_online_and_maps_verified_afterward(self):
        import zipfile
        for target in ("1.20.1", "1.21.1"):
            with self.subTest(target=target):
                root = self.repo / "build/e2e/runtime" / target / "minecraft"
                runtime.claim_root(root, target)
                pins = runtime.read_pins(runtime.REPO, target)
                artifact, profile_id = runtime.neoforge_identity(pins)
                tool = "net.neoforged.installertools:installertools:2.1.2"
                universal = "net.neoforged:" + artifact + ":" + pins["neoforge_version"] + ":universal"
                def library(name, body):
                    path = runtime.maven_path(name)
                    return {"name": name, "downloads": {"artifact": {"path": path,
                            "url": "https://maven.neoforged.net/releases/" + path,
                            "sha1": hashlib.sha1(body).hexdigest(), "size": len(body)}}}
                lib_tool, lib_universal = library(tool, b"tool"), library(universal, b"universal")
                install = {"minecraft": target, "version": profile_id,
                    "data": {"MOJMAPS": {"client": "[net.minecraft:client:" + target + ":mappings@txt]"},
                             "PATCHED": {"client": "[net.neoforged:" + artifact + ":" + pins["neoforge_version"] + ":client]"}},
                    "libraries": [lib_tool, lib_universal],
                    "processors": [{"jar": tool, "classpath": [tool], "args": ["--task", "DOWNLOAD_MOJMAPS", "--version", target,
                        "--side", "{SIDE}", "--output", "{MOJMAPS}"]},
                        {"jar": tool, "classpath": [tool], "args": ["--clean", "{MC_SRG}", "--output", "{PATCHED}", "--apply", "{BINPATCH}"]}]}
                expected = {"id": profile_id, "inheritsFrom": target,
                            "mainClass": "cpw.mods.bootstraplauncher.BootstrapLauncher", "libraries": [], "arguments": {"jvm": [], "game": []}}
                mapping_bytes = b"actual tiny official mappings fixture"
                version = root / "versions" / target / (target + ".json")
                runtime.write_json(version, {"downloads": {"client_mappings": {
                    "url": "https://piston-data.mojang.com/mappings.txt", "sha1": hashlib.sha1(mapping_bytes).hexdigest(), "size": len(mapping_bytes)}}})
                def installer_file(url, destination):
                    self.assertIn("/net/neoforged/" + artifact + "/", url)
                    with zipfile.ZipFile(destination, "w") as archive:
                        archive.writestr("install_profile.json", json.dumps(install))
                        archive.writestr("version.json", json.dumps(expected))
                    return destination
                corrupt = False
                def install_client(command, root, loader):
                    self.assertNotIn("--offline", command)
                    self.assertNotIn("--skipHashCheck", command)
                    runtime.write_json(root / "versions" / profile_id / (profile_id + ".json"), expected)
                    outputs = runtime.processor_outputs(root, install)
                    outputs["MOJMAPS"].parent.mkdir(parents=True, exist_ok=True)
                    outputs["MOJMAPS"].write_bytes(b"tampered" if corrupt else mapping_bytes)
                    outputs["PATCHED"].parent.mkdir(parents=True, exist_ok=True)
                    with zipfile.ZipFile(outputs["PATCHED"], "w") as archive:
                        archive.writestr("Main.class", b"not a game")
                bodies = {lib_tool["downloads"]["artifact"]["url"]: b"tool", lib_universal["downloads"]["artifact"]["url"]: b"universal"}
                with patch.object(runtime, "checksum_artifact", side_effect=installer_file), \
                        patch.object(runtime, "open_official", side_effect=lambda url: Response(bodies[url], url)), \
                        patch.object(runtime, "run_installer", side_effect=install_client):
                    result = runtime.prepare_neoforge(root, self.root / "java", pins)
                    self.assertEqual(profile_id, result[0])
                    corrupt = True
                    with self.assertRaisesRegex(ValueError, "client mappings differ"):
                        runtime.prepare_neoforge(root, self.root / "java", pins)

    def test_actual_forge1192_official_metadata_and_source_pin_shape(self):
        from minecraft_target_loaders import fml_runtime_identity, runtime_pin_fields
        pins = runtime.read_pins(runtime.REPO, "1.19.2", "forge")
        self.assertEqual(("minecraft_version", "java_version", "forge_version"), runtime_pin_fields("forge"))
        identity = fml_runtime_identity(pins, "forge")
        self.assertEqual("net.minecraftforge", identity["group"])
        fixture = Path(__file__).with_name("fixtures") / "forge-1.19.2-43.5.0"
        install = runtime.json_bytes((fixture / "install_profile.json").read_bytes())
        version = runtime.json_bytes((fixture / "version.json").read_bytes())
        self.assertEqual("1.19.2-forge-43.5.0", version["id"])
        self.assertEqual("cpw.mods.bootstraplauncher.BootstrapLauncher", version["mainClass"])
        self.assertEqual(6, len(runtime.client_processors(install)))
        self.assertEqual({"MAPPINGS", "MOJMAPS", "MERGED_MAPPINGS", "MC_SLIM", "MC_EXTRA", "MC_SRG", "PATCHED"},
                         set(runtime.processor_outputs(self.mcroot, install)))
        runtime.official_url(identity["maven"] + runtime.maven_path("net.minecraftforge:forge:" + pins["forge_version"] + ":installer"))
        for loader in ("fabric", "neoforge"):
            with self.assertRaisesRegex(ValueError, "actual source target"):
                runtime.read_pins(runtime.REPO, "1.19.2", loader)

    def test_output_only_isolated_runtime(self):
        self.assertEqual(runtime.safe_root(self.mcroot, self.repo), self.mcroot)
        for bad in [self.repo, self.repo / "build/e2e", self.root / "profile", self.repo / "build/e2e/runs/game"]:
            with self.assertRaises(ValueError):
                runtime.safe_root(bad, self.repo)
        (self.repo / "build/e2e/runtime").mkdir(parents=True)
        (self.repo / "build/e2e/runtime/escape").symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(ValueError):
            runtime.safe_root(self.repo / "build/e2e/runtime/escape/minecraft", self.repo)

    def test_relative_paths_and_maven_coordinates(self):
        self.assertEqual(runtime.relative_file(self.root, "org/example/lib/1/lib-1.jar"), self.root / "org/example/lib/1/lib-1.jar")
        self.assertEqual(runtime.maven_path("net.neoforged:neoforge:26.2.0.25-beta:universal"), "net/neoforged/neoforge/26.2.0.25-beta/neoforge-26.2.0.25-beta-universal.jar")
        for bad in ["../x", "/x", "a/../b", "a\\b", "a//b", "", "C:/x", "a/%2e%2e/b"]:
            with self.assertRaises(ValueError):
                runtime.relative_file(self.root, bad)
        for bad in ["a:b", "a:b:../1", "a:b:1:../../x", "a:b:1@exe"]:
            with self.assertRaises(ValueError):
                runtime.maven_path(bad)

    def test_urls_have_exact_hosts_and_no_credentials(self):
        runtime.official_url("https://libraries.minecraft.net/a.jar")
        for bad in ["http://libraries.minecraft.net/a", "https://libraries.minecraft.net.evil/a", "https://u:p@libraries.minecraft.net/a", "https://libraries.minecraft.net:444/a", "https://libraries.minecraft.net/a?q=secret", "https://libraries.minecraft.net/a#fragment", "https://evil.invalid/a", "https://libraries.minecraft.net/a/../b"]:
            with self.assertRaises(ValueError):
                runtime.official_url(bad)

    def test_ordered_rules_features_arch_and_native_classifiers(self):
        rules = [{"action": "allow"}, {"action": "disallow", "os": {"name": "osx"}}]
        self.assertTrue(runtime.rules_allow(rules, "linux", "x86_64", "6.1"))
        self.assertFalse(runtime.rules_allow(rules, "osx", "arm64", "24.1"))
        self.assertFalse(runtime.rules_allow([{"action": "allow", "features": {"is_demo_user": True}}], "linux", "x86_64", "6.1"))
        self.assertTrue(runtime.rules_allow([{"action": "allow", "os": {"arch": "x86_64|amd64", "version": "^6"}}], "linux", "x86_64", "6.1"))
        artifact = {"path": "x/lib.jar", "url": "https://libraries.minecraft.net/x/lib.jar", "sha1": "a" * 40, "size": 3}
        native = dict(artifact, path="x/native.jar")
        library = {"name": "x:y:1", "downloads": {"artifact": artifact, "classifiers": {"natives-linux-64": native}}, "natives": {"linux": "natives-linux-${arch}"}}
        self.assertEqual(runtime.library_downloads(library, "linux", "x86_64", "6.1"), [artifact, native])
        with self.assertRaises(ValueError):
            runtime.rules_allow([{"action": "accept"}], "linux", "x86_64", "6.1")

    def test_streamed_download_hash_size_and_verified_cache(self):
        body = b"small official artifact"
        sha1 = hashlib.sha1(body).hexdigest()
        url = "https://libraries.minecraft.net/x.jar"
        destination = self.root / "x.jar"
        with patch.object(runtime, "open_official", return_value=Response(body, url)) as opened:
            runtime.download(url, destination, sha1, len(body))
            self.assertEqual(destination.read_bytes(), body)
            runtime.download(url, destination, sha1, len(body))
            self.assertEqual(opened.call_count, 1)
        destination.write_bytes(b"corrupt cache")
        with patch.object(runtime, "open_official", return_value=Response(body, url)):
            runtime.download(url, destination, sha1, len(body))
        self.assertEqual(destination.read_bytes(), body)
        with patch.object(runtime, "open_official", return_value=Response(body, url)):
            with self.assertRaises(ValueError):
                runtime.download(url, self.root / "wrong.jar", "0" * 40, len(body))
        self.assertFalse((self.root / "wrong.jar").exists())
        self.assertEqual(list(self.root.glob("*.part-*")), [])
        with patch.object(runtime, "open_official", return_value=Response(body, url, size=1)):
            with self.assertRaises(ValueError):
                runtime.download(url, self.root / "size.jar", sha1, len(body))

    def test_untrusted_redirect_is_rejected_before_request(self):
        handler = runtime.OfficialRedirect()
        from urllib.request import Request
        with self.assertRaises(ValueError):
            handler.redirect_request(Request("https://libraries.minecraft.net/a"), None, 302, "Found", {}, "https://evil.invalid/b")

    def test_unsafe_installer_zip_path_is_rejected(self):
        import zipfile
        jar = self.root / "installer.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("../outside", b"x")
        with self.assertRaises(ValueError):
            runtime.inspect_installer(jar, "neoforge")

    def test_java25_and_official_installer_commands(self):
        java = self.root / "java"
        fabric = runtime.installer_command("fabric", java, self.root / "fabric.jar", self.mcroot,
                                           {"minecraft_version": "26.2", "fabric_loader_version": "0.19.3"})
        self.assertEqual(fabric, [str(java), "-jar", str(self.root / "fabric.jar"), "client", "-dir", str(self.mcroot), "-mcversion", "26.2", "-loader", "0.19.3", "-noprofile"])
        neo = runtime.installer_command("neoforge", java, self.root / "neo.jar", self.mcroot, {})
        self.assertEqual(neo, [str(java), "-jar", str(self.root / "neo.jar"), "--offline", "--installClient", str(self.mcroot)])
        from types import SimpleNamespace
        with patch.object(runtime.os, "access", return_value=True), patch.object(Path, "is_file", return_value=True), patch.object(runtime.subprocess, "run", return_value=SimpleNamespace(returncode=0, stdout='openjdk version "25.0.1"')):
            self.assertEqual(runtime.check_java(java, 25), 'openjdk version "25.0.1"')
        with patch.object(runtime.os, "access", return_value=True), patch.object(Path, "is_file", return_value=True), patch.object(runtime.subprocess, "run", return_value=SimpleNamespace(returncode=0, stdout='openjdk version "21.0.1"')):
            with self.assertRaises(ValueError):
                runtime.check_java(java, 25)

    def test_existing_runtime_cannot_have_accounts_worlds_or_mods(self):
        self.mcroot.mkdir(parents=True)
        runtime.claim_root(self.mcroot)
        self.assertEqual(json.loads((self.mcroot / "launcher_profiles.json").read_text()), {"profiles": {}})
        (self.mcroot / "launcher_accounts.json").write_text("{}")
        with self.assertRaises(ValueError):
            runtime.claim_root(self.mcroot)

    def test_new_root_refuses_borrowed_profile(self):
        self.mcroot.mkdir(parents=True)
        (self.mcroot / "launcher_profiles.json").write_text('{"profiles":{"personal":{}}}')
        with self.assertRaises(ValueError):
            runtime.claim_root(self.mcroot)

    def test_source_profile_is_the_pin_authority(self):
        profile = self.repo / "gradle/minecraft-targets/26.2.properties"
        profile.parent.mkdir(parents=True)
        profile.write_bytes((runtime.REPO / "gradle/minecraft-targets/26.2.properties").read_bytes())
        pins = runtime.read_pins(self.repo)
        self.assertEqual(pins["fabric_version"], "0.152.1+26.2")
        profile.write_text(profile.read_text().replace("minecraft_version=26.2", "minecraft_version=1.21.1"))
        with self.assertRaises(ValueError):
            runtime.read_pins(self.repo)
        profile.write_text(profile.read_text().replace("java_version=21", "java_version=25") + "fabric_version=bad\n")
        with self.assertRaises(ValueError):
            runtime.read_pins(self.repo)

    def test_containing_runtime_parent_cannot_be_a_symlink_escape(self):
        (self.repo / "build/e2e").mkdir(parents=True)
        (self.repo / "build/e2e/runtime").symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(ValueError):
            runtime.safe_root(self.repo / "build/e2e/runtime/26.2/minecraft", self.repo)

    def test_modern_linux_native_artifact_rule(self):
        artifact = {"path": "org/lwjgl/native.jar", "url": "https://libraries.minecraft.net/org/lwjgl/native.jar", "sha1": "a" * 40, "size": 5}
        library = {"name": "org.lwjgl:lwjgl:3.4.1:natives-linux", "rules": [{"action": "allow", "os": {"name": "linux"}}], "downloads": {"artifact": artifact}}
        self.assertEqual(runtime.library_downloads(library, "linux", "x86_64", "6.8"), [artifact])
        self.assertEqual(runtime.library_downloads(library, "windows", "amd64", "11"), [])

    def test_json_duplicate_keys_and_nonstandard_constants_fail(self):
        for data in [b'{"id":"26.2","id":"other"}', b'{"size":NaN}']:
            with self.assertRaises(ValueError):
                runtime.json_bytes(data)

    def test_metadata_is_bounded_and_truncation_fails(self):
        url = "https://meta.fabricmc.net/v2/x"
        with patch.object(runtime, "open_official", return_value=Response(b"abcdef", url)):
            self.assertEqual(runtime.fetch_bytes(url), b"abcdef")
        for response in [Response(b"abcdef", url, 9), Response(b"abcdef", url, 3)]:
            with patch.object(runtime, "open_official", return_value=response):
                with self.assertRaises(ValueError):
                    runtime.fetch_bytes(url)
        with patch.object(runtime, "open_official", return_value=Response(b"abcdef", url)):
            with self.assertRaises(ValueError):
                runtime.fetch_bytes(url, 5)

    def test_checksum_download_does_not_trust_existing_bytes(self):
        url = "https://maven.fabricmc.net/x.jar"
        target = self.root / "x.jar"
        body = b"official checksum-bound bytes"
        sha1 = hashlib.sha1(body).hexdigest()
        target.write_bytes(b"unverified old cache")
        responses = [Response(sha1.encode(), url + ".sha1"), Response(body, url)]
        with patch.object(runtime, "open_official", side_effect=responses):
            runtime.checksum_artifact(url, target)
        self.assertEqual(target.read_bytes(), body)
        with patch.object(runtime, "fetch_bytes", return_value=b"not-a-checksum"):
            with self.assertRaises(ValueError):
                runtime.checksum_artifact(url, target)

    def test_download_truncation_does_not_replace_destination(self):
        url = "https://libraries.minecraft.net/library.jar"
        destination = self.root / "library.jar"
        destination.write_bytes(b"old")
        with patch.object(runtime, "open_official", return_value=Response(b"truncated", url, size=50)):
            with self.assertRaises(ValueError):
                runtime.download(url, destination, "0" * 40, 50)
        self.assertEqual(destination.read_bytes(), b"old")
        self.assertEqual(list(self.root.glob("*.part-*")), [])

    def test_installer_log_and_environment_are_private(self):
        from types import SimpleNamespace
        (self.mcroot / ".provision").mkdir(parents=True)
        command = ["/java25", "-jar", "/official.jar", "--offline", "--installClient", str(self.mcroot)]
        with patch.dict(runtime.os.environ, {"JAVA_TOOL_OPTIONS": "-Dsecret=value", "JDK_JAVA_OPTIONS": "bad", "_JAVA_OPTIONS": "bad"}), patch.object(runtime.subprocess, "Popen", return_value=SimpleNamespace(wait=lambda timeout: 0)) as run:
            runtime.run_installer(command, self.mcroot, "neoforge")
        kwargs = run.call_args.kwargs
        self.assertEqual(kwargs["cwd"], self.mcroot / ".provision")
        self.assertEqual(kwargs["start_new_session"], runtime.os.name == "posix")
        self.assertNotIn("JAVA_TOOL_OPTIONS", kwargs["env"])
        self.assertNotIn("JDK_JAVA_OPTIONS", kwargs["env"])
        self.assertNotIn("_JAVA_OPTIONS", kwargs["env"])
        self.assertEqual(json.loads((self.mcroot / ".provision/neoforge-installer-command.json").read_text())["command"], command)
        with patch.object(runtime.subprocess, "Popen", return_value=SimpleNamespace(wait=lambda timeout: 1)):
            with self.assertRaises(ValueError):
                runtime.run_installer(command, self.mcroot, "neoforge")

    def test_tiny_official_vanilla_metadata_and_jars(self):
        runtime.claim_root(self.mcroot)
        client = b"small client fixture only"
        native = b"small native fixture only"
        version_url = "https://piston-meta.mojang.com/v1/packages/fake/26.2.json"
        client_url = "https://piston-data.mojang.com/v1/objects/fake/client.jar"
        native_url = "https://libraries.minecraft.net/org/lwjgl/native.jar"
        metadata = {"id": "26.2", "javaVersion": {"majorVersion": 25}, "downloads": {"client": {"url": client_url, "sha1": hashlib.sha1(client).hexdigest(), "size": len(client)}}, "libraries": [{"name": "org.lwjgl:lwjgl:3.4.1:natives-linux", "rules": [{"action": "allow", "os": {"name": "linux"}}], "downloads": {"artifact": {"path": "org/lwjgl/native.jar", "url": native_url, "sha1": hashlib.sha1(native).hexdigest(), "size": len(native)}}}]}
        version = json.dumps(metadata).encode()
        manifest = json.dumps({"versions": [{"id": "26.2", "type": "release", "url": version_url, "sha1": hashlib.sha1(version).hexdigest()}]}).encode()
        bodies = {runtime.MANIFEST_URL: manifest, version_url: version, client_url: client, native_url: native}
        with patch.object(runtime, "open_official", side_effect=lambda url: Response(bodies[url], url)), patch.object(runtime.platform, "system", return_value="Linux"), patch.object(runtime.platform, "machine", return_value="x86_64"):
            actual, files = runtime.prepare_vanilla(self.mcroot, {"minecraft_version": "26.2", "java_version": "25"})
        self.assertEqual(actual, metadata)
        self.assertEqual(len(files), 3)
        self.assertEqual((self.mcroot / "versions/26.2/26.2.jar").read_bytes(), client)
        self.assertEqual((self.mcroot / "libraries/org/lwjgl/native.jar").read_bytes(), native)

    def test_mutated_installer_input_library_is_rejected(self):
        path = self.mcroot / "libraries/x/y/1/y-1.jar"
        path.parent.mkdir(parents=True)
        path.write_bytes(b"good")
        artifact = {"path": "x/y/1/y-1.jar", "url": "https://libraries.minecraft.net/x/y/1/y-1.jar", "sha1": hashlib.sha1(b"good").hexdigest(), "size": 4}
        metadata = {"libraries": [{"name": "x:y:1", "downloads": {"artifact": artifact}}]}
        runtime.verify_downloaded_libraries(metadata, self.mcroot)
        path.write_bytes(b"evil")
        with self.assertRaises(ValueError):
            runtime.verify_downloaded_libraries(metadata, self.mcroot)

    def test_runtime_receipt_contains_only_known_verified_inputs(self):
        from types import SimpleNamespace
        profile = self.repo / "gradle/minecraft-targets/26.2.properties"
        profile.parent.mkdir(parents=True)
        profile.write_bytes((runtime.REPO / "gradle/minecraft-targets/26.2.properties").read_bytes())
        def vanilla(root, pins):
            client = root / "versions/26.2/26.2.jar"
            client.parent.mkdir(parents=True)
            client.write_bytes(b"tiny verified input")
            (root / "versions/26.2/26.2.json").write_text("{}")
            (root / ".provision/version_manifest_v2.json").write_text("{}")
            return {"downloads": {"client": {"size": client.stat().st_size, "sha1": runtime.file_hash(client, "sha1")}}, "libraries": []}, [client]
        def fabric(root, java, pins):
            api = root / "libraries/net/fabric/api.jar"
            api.parent.mkdir(parents=True)
            api.write_bytes(b"tiny verified API fixture")
            profile = root / "versions/fabric-loader-0.19.3-26.2/fabric-loader-0.19.3-26.2.json"
            profile.parent.mkdir(parents=True)
            profile.write_text("{}")
            (root / "libraries/untrusted.jar").write_bytes(b"not part of official inputs")
            return "fabric-loader-0.19.3-26.2", api, [api, profile]
        with patch.object(runtime, "check_java", return_value='openjdk version "25"'), patch.object(runtime, "prepare_vanilla", side_effect=vanilla), patch.object(runtime, "prepare_fabric", side_effect=fabric):
            receipt = runtime.provision(SimpleNamespace(loader="fabric", minecraft_target="26.2", java=self.root / "java", minecraft_root=self.mcroot), self.repo)
        self.assertEqual(receipt["minecraftRoot"], str(self.mcroot))
        self.assertFalse(receipt["gameLaunched"])
        self.assertFalse(receipt["assetsPrepared"])
        self.assertEqual(receipt["pins"]["fabric_version"], "0.152.1+26.2")
        self.assertNotIn("libraries/untrusted.jar", receipt["files"])
        self.assertIn("versions/fabric-loader-0.19.3-26.2/fabric-loader-0.19.3-26.2.json", receipt["files"])
        for entry in receipt["files"].values():
            self.assertEqual(set(entry), {"sha256", "sha1", "size"})
        self.assertEqual(json.loads(Path(receipt["manifest"]).read_text()), receipt)

    def test_official_neoforge_client_processor_flow_is_offline_and_regenerated(self):
        import zipfile
        runtime.claim_root(self.mcroot)
        pins = {"minecraft_version": "26.2", "neoforge_version": "26.2.0.25-beta"}
        coordinate = "net.neoforged:minecraft-client-patched:26.2.0.25-beta"
        patched = runtime.relative_file(self.mcroot / "libraries", runtime.maven_path(coordinate))
        patched.parent.mkdir(parents=True)
        patched.write_bytes(b"opaque old patched cache")
        tool = b"tiny official processor fixture"
        universal_body = b"tiny universal fixture"
        def library(name, body):
            path = runtime.maven_path(name)
            return {"name": name, "downloads": {"artifact": {"path": path, "url": "https://maven.neoforged.net/releases/" + path, "sha1": hashlib.sha1(body).hexdigest(), "size": len(body)}}}
        processor_name = "net.neoforged.installertools:installertools:4.0.12:fatjar"
        universal_name = "net.neoforged:neoforge:26.2.0.25-beta:universal"
        tool_library = library(processor_name, tool)
        universal_library = library(universal_name, universal_body)
        expected = {"id": "neoforge-26.2.0.25-beta", "inheritsFrom": "26.2", "mainClass": "net.neoforged.fml.startup.Client", "arguments": {"game": [], "jvm": []}, "libraries": []}
        install = {"minecraft": "26.2", "version": expected["id"], "data": {"PATCHED": {"client": "[" + coordinate + "]"}}, "libraries": [tool_library, universal_library], "processors": [{"jar": processor_name, "classpath": [processor_name], "args": ["--task", "PROCESS_MINECRAFT_JAR", "--no-mod-manifest", "--input", "{MINECRAFT_JAR}", "--output", "{PATCHED}", "--extract-libraries-to", "{ROOT}/libraries/", "--apply-patches", "{BINPATCH}"]}]}
        def installer_file(url, destination):
            with zipfile.ZipFile(destination, "w") as archive:
                archive.writestr("install_profile.json", json.dumps(install))
                archive.writestr("version.json", json.dumps(expected))
            return destination
        def install_client(command, root, loader):
            self.assertIn("--offline", command)
            self.assertEqual(command[-2:], ["--installClient", str(root)])
            self.assertFalse(patched.exists())
            for lib in install["libraries"]:
                self.assertTrue(runtime.relative_file(root / "libraries", lib["downloads"]["artifact"]["path"]).is_file())
            profile = root / "versions" / expected["id"] / (expected["id"] + ".json")
            runtime.write_json(profile, expected)
            with zipfile.ZipFile(patched, "w") as archive:
                archive.writestr("net/minecraft/client/Main.class", b"fixture not a game")
        bodies = {tool_library["downloads"]["artifact"]["url"]: tool, universal_library["downloads"]["artifact"]["url"]: universal_body}
        with patch.object(runtime, "checksum_artifact", side_effect=installer_file), patch.object(runtime, "open_official", side_effect=lambda url: Response(bodies[url], url)), patch.object(runtime, "run_installer", side_effect=install_client):
            profile_id, api, files = runtime.prepare_neoforge(self.mcroot, self.root / "java", pins)
        self.assertEqual(profile_id, expected["id"])
        self.assertIsNone(api)
        self.assertIn(patched, files)
        self.assertIn(runtime.relative_file(self.mcroot / "libraries", runtime.maven_path(universal_name)), files)
        # Fail closed on an unsupported new processor instead of inventing support.
        install["processors"][0]["args"].append("unexpected-new-argument")
        with patch.object(runtime, "checksum_artifact", side_effect=installer_file), patch.object(runtime, "open_official", side_effect=lambda url: Response(bodies[url], url)), patch.object(runtime, "run_installer") as run:
            with self.assertRaises(ValueError):
                runtime.prepare_neoforge(self.mcroot, self.root / "java", pins)
            run.assert_not_called()

    def test_official_fabric_installer_profile_api_and_loader_metadata(self):
        import zipfile
        runtime.claim_root(self.mcroot)
        pins = {"minecraft_version": "26.2", "fabric_loader_version": "0.19.3", "fabric_version": "0.152.1+26.2"}
        loader_name = "net.fabricmc:fabric-loader:0.19.3"
        asm_name = "org.ow2.asm:asm:9.10.1"
        asm_body = b"tiny ASM fixture"
        asm_library = {"name": asm_name, "url": "https://maven.fabricmc.net/", "sha1": hashlib.sha1(asm_body).hexdigest(), "size": len(asm_body)}
        expected = {"id": "fabric-loader-0.19.3-26.2", "inheritsFrom": "26.2", "time": "request-time", "releaseTime": "request-time", "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient", "arguments": {"jvm": ["-DFabricMcEmu= net.minecraft.client.main.Main "], "game": []}, "libraries": [asm_library, {"name": loader_name, "url": "https://maven.fabricmc.net/"}]}
        loader_metadata = {"libraries": {"common": [asm_library], "client": []}, "mainClass": {"client": expected["mainClass"]}}
        def checksum_file(url, destination):
            destination.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(destination, "w") as archive:
                if "fabric-loader" in destination.name:
                    archive.writestr("fabric-installer.json", json.dumps(loader_metadata))
                elif "fabric-api" in destination.name:
                    archive.writestr("fabric.mod.json", json.dumps({"id": "fabric-api", "version": pins["fabric_version"]}))
                else:
                    archive.writestr("installer.class", b"fixture")
            return destination
        def install_client(command, root, loader):
            self.assertEqual(command[3], "client")
            self.assertIn("-noprofile", command)
            # Simulate another official request timestamp. All functional fields must match.
            actual = dict(expected, time="install-time", releaseTime="install-time")
            profile = root / "versions" / expected["id"] / (expected["id"] + ".json")
            runtime.write_json(profile, actual)
        with patch.object(runtime, "checksum_artifact", side_effect=checksum_file), patch.object(runtime, "fetch_bytes", return_value=json.dumps(expected).encode()), patch.object(runtime, "open_official", side_effect=lambda url: Response(asm_body, url)), patch.object(runtime, "run_installer", side_effect=install_client):
            profile_id, api, files = runtime.prepare_fabric(self.mcroot, self.root / "java", pins)
        self.assertEqual(profile_id, expected["id"])
        self.assertEqual(api.name, "fabric-api-0.152.1+26.2.jar")
        self.assertIn(api, files)
        self.assertIn(runtime.relative_file(self.mcroot / "libraries", runtime.maven_path(loader_name)), files)
        # A generated profile may change time, never functional JVM args.
        def tampered_client(command, root, loader):
            actual = dict(expected, arguments={"jvm": ["-Dwrong=true"], "game": []})
            runtime.write_json(root / "versions" / expected["id"] / (expected["id"] + ".json"), actual)
        with patch.object(runtime, "checksum_artifact", side_effect=checksum_file), patch.object(runtime, "fetch_bytes", return_value=json.dumps(expected).encode()), patch.object(runtime, "open_official", side_effect=lambda url: Response(asm_body, url)), patch.object(runtime, "run_installer", side_effect=tampered_client):
            with self.assertRaises(ValueError):
                runtime.prepare_fabric(self.mcroot, self.root / "java", pins)

    def test_failure_invalidates_previous_runtime_receipt(self):
        from types import SimpleNamespace
        profile = self.repo / "gradle/minecraft-targets/26.2.properties"
        profile.parent.mkdir(parents=True)
        profile.write_bytes((runtime.REPO / "gradle/minecraft-targets/26.2.properties").read_bytes())
        runtime.claim_root(self.mcroot)
        receipt = self.mcroot / ".provision/fabric-runtime.json"
        receipt.write_text('{"old":"receipt"}')
        with patch.object(runtime, "check_java", return_value='openjdk version "25"'), patch.object(runtime, "prepare_vanilla", side_effect=ValueError("checksum failed")):
            with self.assertRaises(ValueError):
                runtime.provision(SimpleNamespace(loader="fabric", minecraft_target="26.2", java=self.root / "java", minecraft_root=self.mcroot), self.repo)
        self.assertFalse(receipt.exists())

    def test_official_installer_archive_inner_class_names_are_legal(self):
        import zipfile
        jar = self.root / "installer.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("net/example/Main$Inner.class", b"tiny class fixture")
        self.assertEqual(runtime.inspect_installer(jar, "fabric"), (None, None))
        with zipfile.ZipFile(jar, "w") as archive:
            entry = zipfile.ZipInfo("net/example/link")
            entry.create_system = 3
            entry.external_attr = (0o120777 << 16)
            archive.writestr(entry, b"/outside")
        with self.assertRaises(ValueError):
            runtime.inspect_installer(jar, "fabric")

    def test_pinned_fabric_profile_rejects_external_library_host_before_installer(self):
        import zipfile
        runtime.claim_root(self.mcroot)
        pins = {"minecraft_version": "26.2", "fabric_loader_version": "0.19.3", "fabric_version": "0.152.1+26.2"}
        profile = {"id": "fabric-loader-0.19.3-26.2", "inheritsFrom": "26.2", "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient", "libraries": [{"name": "net.fabricmc:fabric-loader:0.19.3", "url": "https://evil.invalid/"}]}
        def installer_file(url, destination):
            with zipfile.ZipFile(destination, "w") as archive:
                archive.writestr("installer.class", b"fixture")
        with patch.object(runtime, "checksum_artifact", side_effect=installer_file), patch.object(runtime, "fetch_bytes", return_value=json.dumps(profile).encode()), patch.object(runtime, "run_installer") as run:
            with self.assertRaises(ValueError):
                runtime.prepare_fabric(self.mcroot, self.root / "java", pins)
            run.assert_not_called()

    def test_installer_timeout_stops_only_its_owned_process_group(self):
        from unittest.mock import Mock
        (self.mcroot / ".provision").mkdir(parents=True)
        process = Mock(pid=12345)
        process.wait.side_effect = [runtime.subprocess.TimeoutExpired("official installer", 900), 0]
        with patch.object(runtime.os, "name", "posix"), patch.object(runtime.subprocess, "Popen", return_value=process), patch.object(runtime.os, "killpg") as kill:
            with self.assertRaises(runtime.subprocess.TimeoutExpired):
                runtime.run_installer(["/java", "-jar", "/official.jar"], self.mcroot, "neoforge")
        kill.assert_called_once_with(12345, runtime.signal.SIGTERM)
        self.assertEqual(process.wait.call_args_list[0].kwargs, {"timeout": 900})

    def test_chunked_official_response_remains_hash_size_and_bound_checked(self):
        url = "https://libraries.minecraft.net/chunked.jar"
        body = b"official chunked artifact"
        response = Response(body, url)
        response.headers = {}
        with patch.object(runtime, "open_official", return_value=response):
            runtime.download(url, self.root / "chunked.jar", hashlib.sha1(body).hexdigest(), len(body))
        self.assertEqual((self.root / "chunked.jar").read_bytes(), body)
        response = Response(body, url)
        response.headers = {}
        with patch.object(runtime, "open_official", return_value=response):
            with self.assertRaises(ValueError):
                runtime.download(url, self.root / "chunked-wrong-size.jar", hashlib.sha1(body).hexdigest(), len(body) + 1)
        response = Response(body, url)
        response.headers = {}
        with patch.object(runtime, "open_official", return_value=response):
            with self.assertRaises(ValueError):
                runtime.fetch_bytes(url, 3)

    def test_runtime_parent_cannot_redirect_to_source_even_inside_repository(self):
        source = self.repo / "source"
        source.mkdir()
        (self.repo / "build/e2e").mkdir(parents=True)
        (self.repo / "build/e2e/runtime").symlink_to(source, target_is_directory=True)
        with self.assertRaises(ValueError):
            runtime.safe_root(self.repo / "build/e2e/runtime/26.2/minecraft", self.repo)


if __name__ == "__main__":
    unittest.main()
