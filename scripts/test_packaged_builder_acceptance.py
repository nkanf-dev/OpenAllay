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

    def artifact(self, repo, loader="fabric", bootstrap=True):
        path = repo / loader / "build/libs" / f"openallay-{loader}-26.2-0.2.3.jar"
        path.parent.mkdir(parents=True)
        (repo / "gradle.properties").write_text("version=0.2.3\n", encoding="utf-8")
        nested_path = "META-INF/jars/openallay-builder-fabric-26.2-0.1.0.jar"
        embedded = io.BytesIO()
        with zipfile.ZipFile(embedded, "w") as builder:
            builder.writestr("assets/openallay_builder/building.js", "return {};")
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("fabric.mod.json", json.dumps({"id": "openallay", "version": "0.2.3",
                                                          "jars": [{"file": nested_path}]}))
            archive.writestr(nested_path, embedded.getvalue())
            archive.writestr("dev/openallay/guide/e2e/GuideClientE2EController.class",
                             b"openallay.e2e.createWorld" if bootstrap else b"old production controller")
        return path

    def environment(self, repo):
        mcroot = repo.parent / "minecraft"
        version = mcroot / "versions/26.2"
        version.mkdir(parents=True)
        (version / "26.2.jar").write_bytes(b"official Minecraft")
        launcher.write_json(version / "26.2.json", {
            "id": "26.2", "javaVersion": {"majorVersion": 25}, "libraries": [], "assetIndex": {"id": "32"},
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
        api = repo / "fabric/runs/client/mods/fabric-api-0.155.2+26.2.jar"
        api.parent.mkdir(parents=True)
        with zipfile.ZipFile(api, "w") as archive:
            archive.writestr("fabric.mod.json", json.dumps({"id": "fabric-api", "version": "0.155.2+26.2"}))
        java = repo.parent / "java"
        java.write_text("#!/bin/sh\necho 'openjdk version \"25.0.2\"' >&2\n", encoding="utf-8")
        java.chmod(0o755)
        return mcroot, cache, java

    def test_prepare_loads_only_packaged_mod_and_keeps_default_authority_off(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            self.artifact(repo)
            mcroot, cache, java = self.environment(repo)
            args = launcher.parser().parse_args(["fabric", "--run-id", "test-disabled",
                                                 "--minecraft-root", str(mcroot), "--gradle-cache", str(cache),
                                                 "--java", str(java)])
            with patch.object(launcher, "system_name", return_value="osx"):
                output, manifest = launcher.prepare(args, repo)
            self.assertTrue(manifest["noGameLaunched"])
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

    def test_enabled_scenario_requires_explicit_disposable_opt_in(self):
        with tempfile.TemporaryDirectory() as directory:
            args = launcher.parser().parse_args(["fabric", "--scenario", "builder-acceptance"])
            with self.assertRaisesRegex(ValueError, "explicit --enable-unrestricted"):
                launcher.prepare(args, Path(directory))
            args = launcher.parser().parse_args(["fabric", "--enable-unrestricted"])
            with self.assertRaisesRegex(ValueError, "Disabled and UI scenarios"):
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
                                                 "--enable-unrestricted", "--minecraft-root", str(mcroot),
                                                 "--gradle-cache", str(cache), "--java", str(java)])
            previous, prior = launcher.prepare(args, repo)
            resume = launcher.parser().parse_args(["--resume-prepared", str(previous), "--run-id", "reload",
                                                   "--scenario", "builder-reload", "--enable-unrestricted"])
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
            output, manifest = launcher.prepare_resume(resume, repo)
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
            args = launcher.parser().parse_args(["fabric", "--scenario", "builder-live", "--enable-unrestricted"])
            with self.assertRaisesRegex(ValueError, "explicit ordinary provider question"):
                launcher.prepare(args, Path(directory))

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

    def accepted_original(self, repo, run_id="original"):
        artifact = self.artifact(repo)
        mcroot, cache, java = self.environment(repo)
        args = launcher.parser().parse_args(["fabric", "--run-id", run_id, "--scenario", "builder-acceptance",
                                             "--enable-unrestricted", "--minecraft-root", str(mcroot),
                                             "--gradle-cache", str(cache), "--java", str(java)])
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
        with zipfile.ZipFile(original) as old, zipfile.ZipFile(destination, "w") as new:
            for name in old.namelist():
                content = old.read(name)
                if name.endswith("GuideClientE2EController.class"):
                    content += b"fixed retained-anchor development harness"
                if change_builder and "openallay-builder-" in name:
                    embedded = io.BytesIO()
                    with zipfile.ZipFile(embedded, "w") as builder:
                        builder.writestr("assets/openallay_builder/building.js", "return {changed:true};")
                    content = embedded.getvalue()
                new.writestr(name, content)
        return destination

    def test_resume_upgrade_retains_original_evidence_and_records_both_artifacts(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            previous, prior, original = self.accepted_original(repo)
            old_manifest = (previous / "launch.json").read_bytes()
            old_report = (previous / "report.json").read_bytes()
            replacement = self.replacement_artifact(original, Path(directory) / "replacement")
            args = launcher.parser().parse_args(["--resume-prepared", str(previous), "--run-id", "upgraded",
                                                 "--scenario", "builder-reload", "--enable-unrestricted",
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
            self.assertEqual(manifest["previousPackagedArtifact"]["nestedBuilderSha256"],
                             manifest["newPackagedArtifact"]["nestedBuilderSha256"])
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
                                                 "--scenario", "builder-reload", "--enable-unrestricted",
                                                 "--jar", str(replacement)])
            with self.assertRaisesRegex(ValueError, "exact nested Builder bytes"):
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
                                                 "--scenario", "builder-reload", "--enable-unrestricted",
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
                                                     "--scenario", "builder-reload", "--enable-unrestricted"] + extra)
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
            self.assertNotIn("-Dopenallay.e2e.revokeUnrestrictedAfterCapture=true", manifest["command"])

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

    def test_ui_stop_requires_actual_accepted_cancellation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "screenshots").mkdir()
            (root / "screenshots/11-native-world-builds.png").write_bytes(b"\x89PNG\r\n\x1a\nretained capture")
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
            new_artifact = old_artifact.with_name("openallay-fabric-26.2-0.2.4.jar")
            with zipfile.ZipFile(old_artifact) as old, zipfile.ZipFile(new_artifact, "w") as new:
                for name in old.namelist():
                    content = old.read(name)
                    if name == "fabric.mod.json":
                        metadata = json.loads(content)
                        metadata["version"] = "0.2.4"
                        content = json.dumps(metadata).encode()
                    new.writestr(name, content)
            (repo / "gradle.properties").write_text("version=0.2.4\n", encoding="utf-8")
            args = launcher.parser().parse_args(["fabric", "--run-id", "new-release", "--scenario", "ui-provider-failure",
                                                 "--minecraft-root", str(mcroot), "--gradle-cache", str(cache), "--java", str(java)])
            _, manifest = launcher.prepare(args, repo)
            self.assertEqual("0.2.4", manifest["packagedArtifact"]["modVersion"])
            self.assertEqual(new_artifact.name, manifest["packagedArtifact"]["name"])
            self.assertEqual("0.2.3", launcher.packaged_artifact(old_artifact, "fabric", "0.2.3")["modVersion"])
            with self.assertRaises(ValueError):
                launcher.packaged_artifact(new_artifact, "fabric", "0.2.3")

    def test_resume_uses_prior_manifest_version_not_current_gradle_release(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory) / "repo"
            previous, prior, original = self.accepted_original(repo)
            (repo / "gradle.properties").write_text("version=0.2.4\n", encoding="utf-8")
            replacement = self.replacement_artifact(original, Path(directory) / "replacement")
            args = launcher.parser().parse_args(["--resume-prepared", str(previous), "--run-id", "retained-old-version",
                                                 "--scenario", "builder-reload", "--enable-unrestricted", "--jar", str(replacement)])
            _, manifest = launcher.prepare_resume(args, repo)
            self.assertEqual("0.2.3", manifest["packagedArtifact"]["modVersion"])

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
