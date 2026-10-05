#!/usr/bin/env python3
"""Mock process/fixture tests for the CI runner. These are not Minecraft proof."""

from contextlib import ExitStack, redirect_stdout
import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import time
import unittest
from unittest.mock import MagicMock, patch

MODULE_PATH = Path(__file__).with_name("run-ci-client-acceptance.py")
spec = importlib.util.spec_from_file_location("ci_client_acceptance", MODULE_PATH)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
REFERENCE_REPO = Path(os.environ.get("OPENALLAY_CI_ACCEPTANCE_REFERENCE_REPO", MODULE_PATH.parents[1]))
launcher = runner.load_launcher(REFERENCE_REPO)
PNG = b"\x89PNG\r\n\x1a\nsynthetic-unit-test-image-not-native"
LIVE_NAMES = (
    "live-01-initial-character-focus", "live-02-blur-resize-typed-ptt",
    "live-03-follow-up-accepted-active", "live-04-steer-accepted-active",
    "live-05-native-hover-known-budget-unknown-cost", "live-06-comfortable-tool-summary",
    "live-07-tool-detail-native-recipe", "live-08-native-child-priority-no-parent-drawer",
    "live-09-compact-tool-summary", "live-10-passive-hud-latest-48-no-carousel",
    "live-11-passive-hud-explicit-unbound-hint", "live-12-interactive-hud-opens-at-latest",
    "live-13-reader-anchor-with-new-content", "live-14-reader-native-latest-restores",
    "live-15-reader-prior-request-cards", "live-16-actual-card-title-description-native-toast",
    "live-17-visible-guide-owned-toast-hidden",
)


class Simulator:
    """Write synthetic launch/report receipts instead of starting any runtime."""
    def __init__(self, test, args):
        self.test = test
        self.args = args
        self.calls = []
        self.prepare_failures = set()
        self.launch_failures = set()
        self.missing_reports = set()
        self.bad_reports = set()
        self.nonzero_client_exits = set()
        self.change_artifact_after = set()
        self.external_models = set()
        self.unrestricted_manifests = set()
        self.unrestricted_configs = set()
        self.port = 12345

    def identity(self):
        return {"sha256": self.args.artifact_sha256, "name": self.args.jar.name,
                "loader": self.args.loader, "minecraft": "26.2", "nativeWorldBootstrapPresent": True,
                "bundledBuilder": "assets/openallay/openallay_extensions/builder/main.js"}

    def command(self, command, log_path, deadline, environment, repo, prepared=None):
        self.calls.append(command)
        self.test.assertEqual(runner.SYNTHETIC_KEY, environment["OPENALLAY_E2E_FIXTURE_KEY"])
        self.test.assertGreater(deadline, time.monotonic())
        self.test.assertNotIn("--prepare-assets", command)
        self.test.assertNotIn("--model-config", command)
        Path(log_path).write_text("synthetic mocked launcher log\n")
        if "--launch-prepared" in command:
            directory = Path(command[command.index("--launch-prepared") + 1])
            manifest = runner.read_json(directory / "launch.json")
            scenario = manifest["scenario"]
            manifest.update(noGameLaunched=False, clientExitCode=7 if scenario in self.nonzero_client_exits else 0)
            runner.write_json(directory / "launch.json", manifest)
            (directory / "client.log").write_text("mock client log; not real Minecraft\n")
            (directory / "trace.json").write_text('{"synthetic":true}')
            (directory / "screenshots").mkdir()
            final_name = "10-wide-about.png" if scenario in ("ui-stop", "ui-provider-failure") else "11-native-world-builds.png"
            (directory / "screenshots" / final_name).write_bytes(PNG)
            game = Path(manifest["gameDirectory"])
            (game / "saves" / manifest["world"]).mkdir(parents=True, exist_ok=True)
            (game / "saves" / manifest["world"] / "level.dat").write_bytes(b"unit-test-world")
            if scenario not in self.missing_reports:
                report = self.report(scenario, directory)
                if scenario in self.bad_reports:
                    report["outcome"] = "HARNESS_FAILED"
                runner.write_json(directory / "report.json", report)
            if scenario in self.change_artifact_after:
                self.args.jar.write_bytes(b"changed production artifact")
            if scenario in self.launch_failures:
                raise ValueError("mock launcher timeout or failure")
            return 0
        scenario = command[command.index("--scenario") + 1]
        if scenario in self.prepare_failures:
            raise ValueError("mock prepare failure")
        run_id = command[command.index("--run-id") + 1]
        if "--resume-prepared" in command:
            previous = Path(command[command.index("--resume-prepared") + 1])
            prior = runner.read_json(previous / "launch.json")
            directory = previous / "phases" / run_id
            game = previous / "game"
            world = prior["world"]
            extra = {"resumeFrom": str(previous), "nativeSavedWorldReuse": True}
            mod_key = "mods/" + self.args.jar.name
        else:
            directory = Path(repo) / "build/e2e/packaged-builder" / self.args.loader / run_id
            game = directory / "game"
            world = "openallay-builder-" + self.args.loader + "-" + run_id
            extra = {}
            mod_key = "game/mods/" + self.args.jar.name
            (game / "mods").mkdir(parents=True)
            (game / "mods" / self.args.jar.name).write_bytes(self.args.jar.read_bytes())
            (game / "config/openallay").mkdir(parents=True)
            (game / "saves").mkdir()
            models = launcher.fixture_model_config(self.port)
            if scenario in self.external_models:
                models["profiles"][0]["baseUrl"] = "https://paid-provider.invalid/v1/"
            runner.write_json(game / "config/openallay/models.json", models)
        directory.mkdir(parents=True, exist_ok=True)
        runner.write_json(game / "config/openallay/unrestricted-javascript.json",
                          {"enabled": scenario in self.unrestricted_configs})
        if scenario == "ui-manual-regressions":
            exports = game / "openallay/exports"
            exports.mkdir(parents=True)
            (exports / "synthetic.md").write_text("unit-test exported conversation")
        timeout = runner.effective_timeout(scenario, self.args.timeout_seconds)
        manifest = {"loader": self.args.loader, "minecraft": "26.2", "runId": run_id,
                    "world": world, "scenario": scenario, "gameDirectory": str(game),
                    "packagedArtifact": self.identity(), "unrestrictedOptIn": scenario in self.unrestricted_manifests,
                    "timeoutSeconds": timeout, "wallTimeoutSeconds": timeout + 60,
                    "preparedFiles": {mod_key: self.args.artifact_sha256}, "command": ["synthetic-java"],
                    "report": str(directory / "report.json"), "trace": str(directory / "trace.json"),
                    "screenshots": str(directory / "screenshots"), "noGameLaunched": True,
                    "uiCapture": scenario.startswith("ui-"), **extra}
        runner.write_json(directory / "launch.json", manifest)
        return 0

    def report(self, scenario, directory):
        if not scenario.startswith("ui-"):
            return {"scenario": scenario, "outcome": "COMPLETED", "nativeAcceptance": {"outcome": "PASSED"}}
        result = {"scenario": scenario, "outcome": launcher.UI_OUTCOMES[scenario]}
        if scenario == "ui-stop":
            result["actualStop"] = {field: True for field in
                ("requested", "accepted", "terminalCancelled", "pendingToolHasNoNormalizedResult")}
        if scenario in ("ui-manual-regressions", "ui-live-ux-regressions"):
            names = LIVE_NAMES if scenario == "ui-live-ux-regressions" else ("manual-frame",)
            frames = []
            for name in names:
                path = directory / "screenshots" / (name + ".png")
                path.write_bytes(PNG)
                frames.append({"name": name, "path": str(path), "sha256": runner.sha256(path),
                               "source": "native-mainRenderTarget"})
            result.update(nativeFrames=frames, interactKeyRestored=True, microphoneCaptureAttempted=False,
                          themeChangeCount=4, export={"containsCurrentQuestionAndAnswer": True})
        if scenario == "ui-live-ux-regressions":
            result.update(pttKeyRestored=True, focusReceipts={"synthetic": True},
                          actualNativeRecipeDetail={"detailNativeRecipeIds": ["unit"], "detailCardIds": ["unit"], "detailToolId": "unit"},
                          actualCapsuleClicked={"id": "unit", "action": "BrowseRecipes"},
                          nativeTelemetryHover={"requestedNativeFrame": 1, "requestedLineCount": 3},
                          pendingReceiptOrder=["follow", "steer"], followUpAccepted={"id": "follow", "kind": "FOLLOW_UP"},
                          steerAccepted={"id": "steer", "kind": "STEER"}, admittedSteerTimeline=["unit"], followUpRequestId="unit",
                          actualCardNativeToast={"title": "unit", "description": "unit", "frame": 1, "height": 64, "slots": 2, "noClickTarget": True},
                          actualOwnedToastHiddenOnGuide={"ownedHidden": True, "visible": False, "hideOrder": 2, "showOrder": 1},
                          sourceIdentity={"revision": "synthetic-unit-test", "manifestSha256": "a" * 64})
        return result


class ClientAcceptanceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name) / "repo"
        self.repo.mkdir()
        (self.repo / "gradle.properties").write_text("version=0.4.1\n")
        (self.repo / "gradle/minecraft-targets").mkdir(parents=True)
        (self.repo / "gradle/minecraft-targets/26.2.properties").write_bytes(
            (REFERENCE_REPO / "gradle/minecraft-targets/26.2.properties").read_bytes())
        (self.repo / "scripts").mkdir()
        (self.repo / "scripts/e2e-model-fixture.py").write_text("# synthetic fixture source")
        artifact = self.repo / "openallay-fabric-26.2-0.4.1.jar"
        artifact.write_bytes(b"synthetic packaged artifact")
        java = self.repo / "java"
        java.write_text("not executed")
        mcroot, assets = self.repo / "runtime", self.repo / "assets"
        mcroot.mkdir()
        assets.mkdir()
        api = self.repo / "fabric-api.jar"
        api.write_bytes(b"synthetic fabric API")
        self.args = runner.parser().parse_args([
            "fabric", "--jar", str(artifact), "--artifact-sha256", runner.sha256(artifact),
            "--java", str(java), "--minecraft-root", str(mcroot), "--assets-root", str(assets),
            "--fabric-api", str(api), "--batch-id", "unit-batch",
        ])
        self.simulator = Simulator(self, self.args)
        self.fixture = MagicMock()
        self.fixture.command = ["synthetic-fixture", "--port", "12345"]
        self.fixture.log_path = self.repo / "build/e2e/ci-client/fabric/unit-batch/fixture.log"
        self.fixture.process.pid = 1234
        self.fixture.start.side_effect = lambda timeout: self.fixture.log_path.write_text("synthetic fixture log")

    def run_batch(self):
        wrapped_launcher = MagicMock(wraps=launcher)
        wrapped_launcher.packaged_artifact.side_effect = lambda *args: self.simulator.identity()
        with ExitStack() as stack:
            stack.enter_context(patch.object(runner.sys, "platform", "linux"))
            stack.enter_context(patch.dict(os.environ, {"DISPLAY": ":99"}))
            stack.enter_context(patch.object(runner, "load_launcher", return_value=wrapped_launcher))
            stack.enter_context(patch.object(runner, "Fixture", return_value=self.fixture))
            stack.enter_context(patch.object(runner, "available_port", return_value=self.simulator.port))
            stack.enter_context(patch.object(runner, "run_command", side_effect=self.simulator.command))
            stack.enter_context(redirect_stdout(io.StringIO()))
            code = runner.run_batch(self.args, self.repo)
        output = self.repo / "build/e2e/ci-client/fabric/unit-batch"
        return code, runner.read_json(output / "summary.json"), wrapped_launcher

    def test_exact_target_cli_catalog_forwards_fresh_and_reload_without_global_mutation(self):
        for target in ("1.20.1", "1.21.1", "26.3", "26.2"):
            args = runner.parser().parse_args([
                "fabric", "--minecraft-target", target, "--jar", str(self.args.jar),
                "--artifact-sha256", self.args.artifact_sha256, "--java", str(self.args.java),
                "--minecraft-root", str(self.args.minecraft_root), "--assets-root", str(self.args.assets_root)])
            self.assertEqual(target, args.minecraft_version)
            for scenario in ("builder-restricted", "builder-reload"):
                command = runner.prepare_command(args, scenario, "exact-target", 12345, self.repo,
                    accepted={"directory": self.repo / "build/e2e/prior"})
                self.assertEqual(target, command[command.index("--minecraft-target") + 1])
                if scenario == "builder-reload":
                    self.assertNotIn("--jar", command)
            alias = runner.parser().parse_args([
                "fabric", "--minecraft-version", target, "--jar", str(self.args.jar),
                "--artifact-sha256", self.args.artifact_sha256, "--java", str(self.args.java),
                "--minecraft-root", str(self.args.minecraft_root), "--assets-root", str(self.args.assets_root)])
            self.assertEqual(target, alias.minecraft_version)
        choice = next(action for action in runner.parser()._actions if action.dest == "minecraft_version")
        self.assertEqual(set(launcher.minecraft_targets()), set(choice.choices))
        self.assertEqual(23, len(choice.choices))

    def test_default_all_nine_keep_restricted_javascript_and_use_actual_validator_gates(self):
        code, summary, validated = self.run_batch()
        self.assertEqual(0, code)
        self.assertEqual("PASSED", summary["status"])
        self.assertEqual(list(runner.SCENARIOS), [item["scenario"] for item in summary["scenarios"]])
        self.assertEqual(18, len(self.simulator.calls))
        self.assertEqual(5 + 1, validated.validate_report.call_count)  # Five phases plus reviewed reload origin.
        self.assertEqual(4, validated.validate_ui_capture.call_count)
        self.fixture.stop.assert_called_once()
        prepares = self.simulator.calls[::2]
        for command in prepares:
            scenario = command[command.index("--scenario") + 1]
            self.assertNotIn("--enable-unrestricted", command)
            self.assertEqual(scenario == "ui-stop", "--cancel-on-tool-start" in command)
            self.assertNotIn("--prepare-assets", command)
            self.assertNotIn("--model-config", command)
        reload_command = prepares[4]
        self.assertIn("--resume-prepared", reload_command)
        self.assertNotIn("--jar", reload_command)
        for record in summary["scenarios"]:
            self.assertEqual(self.args.artifact_sha256, record["artifactBefore"]["sha256"])
            self.assertEqual(self.args.artifact_sha256, record["artifactAfter"]["sha256"])
            self.assertTrue(any(item["path"].endswith("report.json") for item in record["evidence"]))
            manifest = runner.read_json(Path(record["directory"]) / "launch.json")
            self.assertFalse(manifest["unrestrictedOptIn"])
            self.assertEqual({"enabled": False}, runner.read_json(
                Path(manifest["gameDirectory"]) / "config/openallay/unrestricted-javascript.json"))
        reload_record = next(item for item in summary["scenarios"] if item["scenario"] == "builder-reload")
        accepted_record = next(item for item in summary["scenarios"] if item["scenario"] == "builder-acceptance")
        reload_manifest = runner.read_json(Path(reload_record["directory"]) / "launch.json")
        accepted_manifest = runner.read_json(Path(accepted_record["directory"]) / "launch.json")
        self.assertTrue(reload_manifest["nativeSavedWorldReuse"])
        self.assertEqual(accepted_manifest["gameDirectory"], reload_manifest["gameDirectory"])
        self.assertNotEqual(accepted_manifest["report"], reload_manifest["report"])
        self.assertEqual(9, len(summary["diagnostics"]["scenarioDirectories"]))
        manual = next(item for item in summary["scenarios"] if item["scenario"] == "ui-manual-regressions")
        self.assertTrue(any(item["path"].endswith("synthetic.md") for item in manual["evidence"]))
        self.assertTrue(summary["sources"])
        self.assertIn("no model accuracy", summary["acceptanceBoundary"])

    def test_ci_rejects_full_access_positive_manifest_before_launch(self):
        self.args.scenarios = ["builder-acceptance", "builder-reload"]
        self.simulator.unrestricted_manifests.add("builder-acceptance")
        code, summary, _ = self.run_batch()
        self.assertEqual(1, code)
        records = {item["scenario"]: item for item in summary["scenarios"]}
        self.assertIn("Prepared manifest mismatch: unrestrictedOptIn", records["builder-acceptance"]["failures"][0])
        self.assertIn("builder-acceptance did not pass", records["builder-reload"]["failures"][0])
        self.assertEqual(1, len(self.simulator.calls))
        self.assertTrue(summary["diagnostics"]["logFiles"])
        self.assertTrue(any(item["path"].endswith("launch.json") for item in records["builder-acceptance"]["evidence"]))

    def test_ci_rejects_full_access_positive_config_before_launch(self):
        self.args.scenarios = ["builder-partial"]
        self.simulator.unrestricted_configs.add("builder-partial")
        code, summary, _ = self.run_batch()
        self.assertEqual(1, code)
        record = summary["scenarios"][0]
        self.assertEqual("FAILED", record["status"])
        self.assertIn("keep unrestricted JavaScript disabled", record["failures"][0])
        self.assertEqual(1, len(self.simulator.calls))
        self.assertTrue(summary["diagnostics"]["logFiles"])

    def test_ci_rejects_full_access_reload_config_before_resume_launch(self):
        self.args.scenarios = ["builder-acceptance", "builder-reload"]
        self.simulator.unrestricted_configs.add("builder-reload")
        code, summary, validated = self.run_batch()
        self.assertEqual(1, code)
        records = {item["scenario"]: item for item in summary["scenarios"]}
        self.assertEqual("PASSED", records["builder-acceptance"]["status"])
        self.assertEqual("FAILED", records["builder-reload"]["status"])
        self.assertIn("keep unrestricted JavaScript disabled", records["builder-reload"]["failures"][0])
        self.assertEqual(3, len(self.simulator.calls))
        self.assertIn("--resume-prepared", self.simulator.calls[-1])
        self.assertNotIn("--enable-unrestricted", self.simulator.calls[-1])
        self.assertEqual(2, validated.validate_report.call_count)  # Accepted run and reviewed reload origin.
        self.assertTrue(any(item["path"].endswith("launch.json") for item in records["builder-reload"]["evidence"]))
        self.assertTrue(summary["diagnostics"]["logFiles"])

    def test_prepare_failure_aggregates_and_always_stops_fixture(self):
        self.simulator.prepare_failures.add("builder-restricted")
        code, summary, _ = self.run_batch()
        self.assertEqual(1, code)
        self.assertEqual("FAILED", summary["scenarios"][0]["status"])
        self.assertEqual("PASSED", summary["scenarios"][-1]["status"])
        self.fixture.stop.assert_called_once()
        self.assertEqual(1, sum("builder-restricted" in command for command in self.simulator.calls))

    def test_launch_timeout_keeps_diagnostics_and_stops_fixture_without_retry(self):
        self.simulator.launch_failures.add("builder-restricted")
        code, summary, _ = self.run_batch()
        self.assertEqual(1, code)
        first = summary["scenarios"][0]
        self.assertEqual("FAILED", first["status"])
        self.assertTrue(any(item["path"].endswith("client.log") for item in first["evidence"]))
        self.fixture.stop.assert_called_once()
        self.assertEqual(18, len(self.simulator.calls))

    def test_failed_acceptance_blocks_reload_and_no_alternate_world_is_used(self):
        self.simulator.bad_reports.add("builder-acceptance")
        code, summary, _ = self.run_batch()
        self.assertEqual(1, code)
        reload_record = next(item for item in summary["scenarios"] if item["scenario"] == "builder-reload")
        self.assertEqual("FAILED", reload_record["status"])
        self.assertIn("no other world", " ".join(reload_record["failures"]))
        self.assertFalse(any("--resume-prepared" in command for command in self.simulator.calls))

    def test_changed_original_sha_fails_batch_and_blocks_remaining_native_launches(self):
        self.simulator.change_artifact_after.add("builder-restricted")
        code, summary, _ = self.run_batch()
        self.assertEqual(1, code)
        self.assertEqual(2, len(self.simulator.calls))
        self.assertTrue(all(item["status"] == "FAILED" for item in summary["scenarios"]))
        self.assertIn("SHA256 changed", " ".join(summary["scenarios"][0]["failures"]))
        self.assertTrue(summary["failures"])
        self.fixture.stop.assert_called_once()

    def test_external_provider_config_is_rejected_before_launch(self):
        self.simulator.external_models.add("builder-restricted")
        code, summary, _ = self.run_batch()
        self.assertEqual(1, code)
        self.assertIn("loopback", " ".join(summary["scenarios"][0]["failures"]))
        self.assertEqual(17, len(self.simulator.calls))

    def test_missing_report_or_native_nonzero_exit_can_never_pass(self):
        self.simulator.missing_reports.add("builder-restricted")
        self.simulator.nonzero_client_exits.add("builder-partial")
        code, summary, _ = self.run_batch()
        self.assertEqual(1, code)
        records = {item["scenario"]: item for item in summary["scenarios"]}
        self.assertEqual("FAILED", records["builder-restricted"]["status"])
        self.assertEqual("FAILED", records["builder-partial"]["status"])

    def test_fixture_start_failure_marks_every_missing_scenario_failed(self):
        self.fixture.start.side_effect = ValueError("mock fixture bind failed")
        code, summary, _ = self.run_batch()
        self.assertEqual(1, code)
        self.assertTrue(all(item["status"] == "FAILED" for item in summary["scenarios"]))
        self.assertEqual([], self.simulator.calls)
        self.fixture.stop.assert_called_once()

    def test_spent_batch_deadline_fails_all_unrun_scenarios_without_starting_them(self):
        real_start = self.fixture.start.side_effect
        def consume_budget(timeout):
            real_start(timeout)
            self.clock.return_value = 2200
        self.fixture.start.side_effect = consume_budget
        with patch.object(runner.time, "monotonic", return_value=0) as self.clock:
            code, summary, _ = self.run_batch()
        self.assertEqual(1, code)
        self.assertEqual([], self.simulator.calls)
        self.assertTrue(all(item["status"] == "FAILED" for item in summary["scenarios"]))
        self.fixture.stop.assert_called_once()

    def test_reload_receipt_changes_reject_resume(self):
        code, summary, _ = self.run_batch()
        self.assertEqual(0, code)
        acceptance = next(item for item in summary["scenarios"] if item["scenario"] == "builder-acceptance")
        directory = Path(acceptance["directory"])
        accepted = {"directory": directory, "runId": acceptance["runId"],
                    "world": runner.read_json(directory / "launch.json")["world"],
                    "launchSha256": runner.sha256(directory / "launch.json"),
                    "reportSha256": runner.sha256(directory / "report.json")}
        runner.write_json(directory / "report.json", {"outcome": "COMPLETED"})
        with self.assertRaisesRegex(ValueError, "receipts changed"):
            runner.review_passed_acceptance(accepted, self.args, launcher, self.repo, 12345)

    def test_reload_only_accepts_own_disposable_world_and_no_symlinked_save(self):
        self.args.scenarios = ["builder-acceptance"]
        _, summary, _ = self.run_batch()
        record = summary["scenarios"][0]
        directory = Path(record["directory"])
        manifest = runner.read_json(directory / "launch.json")
        accepted = {"directory": directory, "runId": record["runId"], "world": manifest["world"],
                    "launchSha256": runner.sha256(directory / "launch.json"),
                    "reportSha256": runner.sha256(directory / "report.json")}
        save = directory / "game/saves" / manifest["world"]
        (save / "level.dat").unlink()
        save.rmdir()
        external = self.repo / "user-world"
        external.mkdir()
        (external / "level.dat").write_bytes(b"never touched user world")
        save.symlink_to(external, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "disposable acceptance world"):
            runner.review_passed_acceptance(accepted, self.args, launcher, self.repo, 12345)
        self.assertEqual(b"never touched user world", (external / "level.dat").read_bytes())

    def test_repeat_output_is_refused_not_retried_or_overwritten(self):
        self.run_batch()
        with patch.object(runner.sys, "platform", "linux"), patch.dict(os.environ, {"DISPLAY": ":99"}):
            with self.assertRaisesRegex(ValueError, "already exists"):
                runner.validate_arguments(self.args, self.repo)

    def test_safe_ids_paths_and_explicit_runtime_inputs(self):
        with patch.object(runner.sys, "platform", "linux"), patch.dict(os.environ, {"DISPLAY": ":99"}):
            self.args.batch_id = "../../user-save"
            with self.assertRaisesRegex(ValueError, "filesystem-safe"):
                runner.validate_arguments(self.args, self.repo)
            self.args.batch_id = "safe"
            self.args.batch_timeout_seconds = 2101
            with self.assertRaisesRegex(ValueError, "2100"):
                runner.validate_arguments(self.args, self.repo)
        with self.assertRaisesRegex(ValueError, "build/e2e"):
            runner.safe_output(self.repo / "user-world", self.repo)
        with self.assertRaisesRegex(ValueError, "build/e2e"):
            runner.safe_output(self.repo / "build/e2e", self.repo)

    def test_default_ids_are_deterministic_and_live_ux_deadline_is_not_shortened(self):
        self.args.batch_id = None
        with patch.object(runner.sys, "platform", "linux"), patch.dict(os.environ, {"DISPLAY": ":99"}):
            first, _ = runner.validate_arguments(self.args, self.repo)
            second, _ = runner.validate_arguments(self.args, self.repo)
        self.assertEqual(first, second)
        self.assertLessEqual(len(first + "-ui-live-ux-regressions"), 91)
        self.assertEqual(600, runner.effective_timeout("ui-live-ux-regressions", 180))
        self.assertEqual(180, runner.effective_timeout("builder-restricted", 180))

    def test_paid_live_scenarios_and_external_model_option_do_not_exist(self):
        self.assertNotIn("builder-live", runner.SCENARIOS)
        options = runner.parser().format_help()
        self.assertNotIn("--model-config", options)
        self.assertNotIn("--prepare-assets", options)
        self.assertNotIn("--resume-prepared", options)
        self.args.scenarios = ["builder-reload"]
        with patch.object(runner.sys, "platform", "linux"), patch.dict(os.environ, {"DISPLAY": ":99"}):
            with self.assertRaisesRegex(ValueError, "same batch"):
                runner.validate_arguments(self.args, self.repo)

    def test_diagnostics_inventory_cannot_follow_an_external_game_directory(self):
        directory = self.repo / "build/e2e/unit"
        directory.mkdir(parents=True)
        runner.write_json(directory / "launch.json", {"gameDirectory": str(self.repo / "user-world")})
        with self.assertRaisesRegex(ValueError, "build/e2e"):
            runner.retain_evidence({}, directory, self.repo)

    def test_builder_native_pass_without_final_frame_cannot_pass_ci(self):
        directory = self.repo / "build/e2e/missing-final"
        directory.mkdir(parents=True)
        report_path = directory / "report.json"
        runner.write_json(report_path, {"scenario": "builder-restricted", "outcome": "COMPLETED",
                                        "nativeAcceptance": {"outcome": "PASSED"}})
        launcher = runner.load_launcher(REFERENCE_REPO)
        manifest = {"scenario": "builder-restricted", "report": str(report_path),
                    "screenshots": str(directory / "screenshots")}
        with self.assertRaisesRegex(ValueError, "final PNG"):
            runner.validate_run(directory, manifest, launcher)

    def test_wrong_report_scenario_is_not_a_native_pass(self):
        directory = self.repo / "build/e2e/unit"
        directory.mkdir(parents=True)
        report_path = directory / "report.json"
        runner.write_json(report_path, {"scenario": "other", "outcome": "COMPLETED", "nativeAcceptance": {"outcome": "PASSED"}})
        with self.assertRaisesRegex(ValueError, "scenario differs"):
            runner.validate_run(directory, {"scenario": "builder-restricted", "report": str(report_path)}, launcher)

    def test_native_frame_hash_validation_is_the_existing_launcher_gate(self):
        directory = self.repo / "build/e2e/unit"
        (directory / "screenshots").mkdir(parents=True)
        report = self.simulator.report("ui-live-ux-regressions", directory)
        runner.write_json(directory / "report.json", report)
        manifest = {"scenario": "ui-live-ux-regressions", "uiCapture": True,
                    "report": str(directory / "report.json"), "screenshots": str(directory / "screenshots")}
        runner.validate_run(directory, manifest, launcher)
        Path(report["nativeFrames"][0]["path"]).write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "changed"):
            runner.validate_run(directory, manifest, launcher)

    def test_run_command_timeout_stops_only_its_owned_launcher(self):
        process = MagicMock()
        process.wait.side_effect = subprocess.TimeoutExpired("synthetic", 1)
        log = self.repo / "command.log"
        with patch.object(runner.subprocess, "Popen", return_value=process) as start, \
                patch.object(runner, "stop_launcher") as stop:
            with self.assertRaisesRegex(ValueError, "deadline"):
                runner.run_command(["python", "synthetic-launcher"], log, time.monotonic() + 1,
                                   {"KEY": "synthetic"}, self.repo, prepared=self.repo / "build/e2e/own")
        self.assertTrue(start.call_args.kwargs["start_new_session"])
        stop.assert_called_once_with(process, self.repo / "build/e2e/own")

    def test_process_group_cleanup_never_uses_global_process_names(self):
        process = MagicMock(pid=4312)
        process.poll.return_value = None
        process.wait.side_effect = [subprocess.TimeoutExpired("owned", 3), 0]
        with patch.object(runner.os, "getpgid", return_value=4312), patch.object(runner.os, "killpg") as kill:
            runner.terminate_group(process)
        self.assertEqual([(4312, signal.SIGTERM), (4312, signal.SIGKILL)], [call.args for call in kill.call_args_list])
        with patch.object(runner.os, "getpgid", return_value=999), patch.object(runner.os, "killpg") as kill:
            with self.assertRaisesRegex(RuntimeError, "dedicated"):
                runner.terminate_group(process)
            kill.assert_not_called()

    def test_fixture_cli_is_actual_owned_child_with_retained_log(self):
        output = self.repo / "build/e2e/fixture"
        output.mkdir(parents=True)
        fixture = runner.Fixture(self.repo, output, 12345, {"KEY": "synthetic"})
        self.assertEqual([runner.sys.executable, str(self.repo / "scripts/e2e-model-fixture.py"), "--port", "12345"], fixture.command)
        process = MagicMock()
        process.stdout = io.BytesIO(b"OpenAllay E2E model fixture listening on 127.0.0.1:12345\n")
        process.poll.return_value = None
        with patch.object(runner.subprocess, "Popen", return_value=process) as start, patch.object(runner, "terminate_group") as stop:
            fixture.start(1)
            fixture.stop()
        self.assertTrue(start.call_args.kwargs["start_new_session"])
        self.assertEqual(self.repo, start.call_args.kwargs["cwd"])
        self.assertIn("listening", fixture.log_path.read_text())
        stop.assert_called_once_with(process)


if __name__ == "__main__":
    unittest.main()
