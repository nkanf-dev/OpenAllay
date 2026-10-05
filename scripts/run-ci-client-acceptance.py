#!/usr/bin/env python3
"""Run non-paid packaged Minecraft client scenarios on a provisioned Linux CI host.

The workflow owns Xvfb/Mesa and runtime installation. This script downloads nothing,
uses one unchanged production JAR, and starts its own deterministic loopback fixture.
A passing summary means the existing launcher's mechanical acceptance gates passed.
Native screenshots remain evidence for separate visual review, not model accuracy.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import subprocess
import sys
import threading
import time
import traceback

REPO = Path(__file__).resolve().parents[1]
SCENARIOS = (
    "builder-restricted", "builder-acceptance", "builder-partial", "builder-cancel",
    "builder-reload", "ui-stop", "ui-provider-failure", "ui-manual-regressions",
    "ui-live-ux-regressions",
)
SYNTHETIC_KEY = "openallay-local-fixture-not-a-secret"
MAX_BATCH_SECONDS = 35 * 60
CLEANUP_RESERVE_SECONDS = 20
SOURCE_FILES = (
    "scripts/run-ci-client-acceptance.py", "scripts/test_ci_client_acceptance.py",
    "scripts/run-packaged-builder-acceptance.py", "scripts/e2e-model-fixture.py",
    "common/src/main/java/dev/openallay/guide/e2e/GuideClientE2EController.java",
    "common/src/main/java/dev/openallay/guide/e2e/GuideBuilderE2EProbe.java",
    "common/src/main/java/dev/openallay/guide/e2e/GuideGraphicalRegressionProbe.java",
)


def sha256(path):
    result = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def receipt(path):
    path = Path(path).resolve()
    stat = path.stat()
    return {"path": str(path), "sha256": sha256(path), "sizeBytes": stat.st_size,
            "mtimeNs": stat.st_mtime_ns}


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".writing")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def read_json(path):
    value = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Expected a JSON object: " + str(path))
    return value


def safe_output(path, repo):
    root = (Path(repo) / "build/e2e").resolve()
    path = Path(path).resolve()
    if path == root or not path.is_relative_to(root):
        raise ValueError("Acceptance output must be a child of ignored build/e2e")
    return path


def verify_artifact(path, expected):
    observed = receipt(path)
    if observed["sha256"] != expected:
        raise ValueError("Original production artifact SHA256 changed: expected " + expected
                         + ", observed " + observed["sha256"])
    return observed


def load_launcher(repo):
    path = Path(repo) / "scripts/run-packaged-builder-acceptance.py"
    spec = importlib.util.spec_from_file_location("ci_packaged_acceptance_validator", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def available_port():
    # Binding is only for port selection; the owned fixture must then prove its own bind.
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def terminate_group(process, grace=3):
    """Signal only the process group created by this Popen call."""
    if process.poll() is not None:
        return
    try:
        if os.getpgid(process.pid) != process.pid:
            raise RuntimeError("Owned child did not retain its dedicated process group")
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=grace)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait(timeout=2)


def native_child_identity(launcher_process, prepared):
    """Capture only this launcher's direct Java child, with Linux PID birth identity."""
    if prepared is None or launcher_process.poll() is not None:
        return None
    try:
        manifest = read_json(Path(prepared) / "launch.json")
        pid = manifest["clientPid"]
        if type(pid) is not int or pid <= 1:
            return None
        fields = Path(f"/proc/{pid}/stat").read_text().rsplit(")", 1)[1].split()
        arguments = Path(f"/proc/{pid}/cmdline").read_bytes().rstrip(b"\0").split(b"\0")
        if (int(fields[1]) != launcher_process.pid or os.getpgid(pid) != pid
                or arguments != [os.fsencode(value) for value in manifest["command"]]):
            return None
        return pid, fields[19]  # Linux stat field 22: start time since boot.
    except (OSError, ValueError, KeyError, IndexError):
        return None


def terminate_native_identity(identity):
    if identity is None:
        return
    pid, birth = identity
    try:
        fields = Path(f"/proc/{pid}/stat").read_text().rsplit(")", 1)[1].split()
        if fields[19] == birth and os.getpgid(pid) == pid:
            os.killpg(pid, signal.SIGKILL)
    except (OSError, IndexError):
        pass


def stop_launcher(process, prepared):
    # The normal launcher handles SIGTERM and reaps its own Java process group.
    # Preserve a birth-checked direct-child identity in case that cleanup itself hangs.
    identity = native_child_identity(process, prepared)
    try:
        terminate_group(process, grace=10)
    finally:
        terminate_native_identity(identity)


class Fixture:
    def __init__(self, repo, output, port, environment):
        self.repo = Path(repo)
        self.command = [sys.executable, str(self.repo / "scripts/e2e-model-fixture.py"), "--port", str(port)]
        self.log_path = Path(output) / "fixture.log"
        self.environment = environment
        self.port = port
        self.process = None
        self.reader = None

    def start(self, timeout):
        ready = threading.Event()
        self.process = subprocess.Popen(self.command, cwd=self.repo, env=self.environment,
                                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                        start_new_session=True)
        def retain_log():
            with self.log_path.open("wb") as log:
                for line in iter(self.process.stdout.readline, b""):
                    log.write(line)
                    log.flush()
                    if line.rstrip() == f"OpenAllay E2E model fixture listening on 127.0.0.1:{self.port}".encode():
                        ready.set()
            ready.set()  # Wake on early failure, but the process check still rejects it.
        self.reader = threading.Thread(target=retain_log, name="owned-fixture-log", daemon=True)
        self.reader.start()
        if not ready.wait(timeout) or self.process.poll() is not None:
            raise ValueError("Owned loopback fixture did not start; see " + str(self.log_path))
        # An EOF without the exact readiness line is not proof of a bound fixture.
        if f"listening on 127.0.0.1:{self.port}" not in self.log_path.read_text(encoding="utf-8", errors="replace"):
            raise ValueError("Owned fixture did not report its actual loopback bind")

    def check(self):
        if self.process is None or self.process.poll() is not None:
            raise ValueError("Owned loopback fixture exited; see " + str(self.log_path))

    def stop(self):
        if self.process is not None:
            terminate_group(self.process)
        if self.reader is not None:
            self.reader.join(timeout=2)
        if self.process is not None and self.process.stdout is not None:
            self.process.stdout.close()


def run_command(command, log_path, deadline, environment, repo, prepared=None):
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise ValueError("Batch deadline exhausted before command start")
    write_json(Path(log_path).with_suffix(".command.json"), {"argv": command, "timeoutSeconds": remaining})
    process = None
    with Path(log_path).open("wb") as log:
        try:
            process = subprocess.Popen(command, cwd=repo, env=environment, stdout=log,
                                        stderr=subprocess.STDOUT, start_new_session=True)
            code = process.wait(timeout=remaining)
        except subprocess.TimeoutExpired:
            if process is not None:
                stop_launcher(process, prepared)
            raise ValueError("Command stopped after its bounded deadline; see " + str(log_path))
        except BaseException:
            if process is not None:
                stop_launcher(process, prepared)
            raise
    if code != 0:
        raise ValueError("Launcher command exited " + str(code) + "; see " + str(log_path))
    return code


def effective_timeout(scenario, timeout):
    return max(timeout, 600) if scenario == "ui-live-ux-regressions" else timeout


def prepare_command(args, scenario, run_id, port, repo, accepted=None):
    command = [sys.executable, str(Path(repo) / "scripts/run-packaged-builder-acceptance.py"), args.loader,
               "--scenario", scenario, "--run-id", run_id, "--minecraft-target", args.minecraft_version,
               "--timeout-seconds", str(effective_timeout(scenario, args.timeout_seconds))]
    if scenario == "builder-reload":
        if accepted is None:
            raise ValueError("builder-reload requires this batch's reviewed passed builder-acceptance world")
        command += ["--resume-prepared", str(accepted["directory"])]
        # No --jar replacement: retain exactly the originally accepted game/mods bytes.
    else:
        command += ["--jar", str(args.jar), "--minecraft-root", str(args.minecraft_root),
                    "--java", str(args.java), "--assets-root", str(args.assets_root),
                    "--fixture-port", str(port)]
        if args.fabric_api is not None:
            command += ["--fabric-api", str(args.fabric_api)]
        if args.gradle_cache is not None:
            command += ["--gradle-cache", str(args.gradle_cache)]
        if args.mod_version is not None:
            command += ["--mod-version", args.mod_version]
    if getattr(args, "artifact_family", None):
        command += ["--artifact-family", args.artifact_family]
    if scenario == "ui-stop":
        command.append("--cancel-on-tool-start")
    return command


def review_manifest(directory, args, scenario, run_id, port, launcher, repo, accepted=None, launched=False):
    directory = safe_output(directory, repo)
    manifest = read_json(directory / "launch.json")
    identity = manifest.get("packagedArtifact", {})
    expected_fields = {"loader": args.loader, "minecraft": args.minecraft_version,
                       "runId": run_id, "scenario": scenario,
                       "unrestrictedOptIn": False,
                       "timeoutSeconds": effective_timeout(scenario, args.timeout_seconds),
                       "wallTimeoutSeconds": effective_timeout(scenario, args.timeout_seconds) + 60,
                       "noGameLaunched": not launched}
    for field, expected in expected_fields.items():
        if manifest.get(field) != expected:
            raise ValueError("Prepared manifest mismatch: " + field)
    if (identity.get("sha256") != args.artifact_sha256 or identity.get("name") != args.jar.name
            or identity.get("loader") != args.loader or identity.get("minecraft") != args.minecraft_version
            or identity.get("nativeWorldBootstrapPresent") is not True):
        raise ValueError("Prepared manifest does not retain the exact executable packaged artifact")
    if any(key in manifest for key in ("testHarnessUpgrade", "newPackagedArtifact", "previousPackagedArtifact")):
        raise ValueError("CI batch cannot replace or upgrade its accepted production JAR")
    for key in ("report", "trace", "screenshots"):
        expected_path = directory / (key + ".json" if key != "screenshots" else key)
        if safe_output(manifest[key], repo) != expected_path:
            raise ValueError("Manifest evidence path does not belong to this run: " + key)
    if accepted is None:
        game = directory / "game"
        world = "openallay-builder-" + args.loader + "-" + run_id
        if "resumeFrom" in manifest:
            raise ValueError("Fresh CI scenario cannot resume an external world")
        mod_key = "game/mods/" + args.jar.name
    else:
        game = accepted["directory"] / "game"
        world = accepted["world"]
        if (safe_output(manifest.get("resumeFrom", ""), repo) != accepted["directory"]
                or manifest.get("nativeSavedWorldReuse") is not True):
            raise ValueError("Reload must reuse only this batch's reviewed passed disposable world")
        mod_key = "mods/" + args.jar.name
    if safe_output(manifest["gameDirectory"], repo) != game or manifest.get("world") != world:
        raise ValueError("Manifest game/world identity differs from this batch's disposable directory")
    if manifest.get("preparedFiles", {}).get(mod_key) != args.artifact_sha256:
        raise ValueError("Prepared files do not pin the original packaged JAR")
    verify_artifact(game / "mods" / args.jar.name, args.artifact_sha256)
    if read_json(game / "config/openallay/models.json") != launcher.fixture_model_config(port):
        raise ValueError("Every CI scenario must use only this batch's deterministic loopback fixture")
    if read_json(game / "config/openallay/unrestricted-javascript.json") != {"enabled": False}:
        raise ValueError("Every CI scenario must keep unrestricted JavaScript disabled")
    if launched and (manifest.get("clientExitCode") != 0 or type(manifest.get("clientExitCode")) is not int):
        raise ValueError("Native client did not retain an actual successful exit receipt")
    return manifest


def review_passed_acceptance(accepted, args, launcher, repo, port):
    directory = safe_output(accepted["directory"], repo)
    if (sha256(directory / "launch.json") != accepted["launchSha256"]
            or sha256(directory / "report.json") != accepted["reportSha256"]):
        raise ValueError("Reviewed passed acceptance receipts changed before reload")
    manifest = review_manifest(directory, args, "builder-acceptance", accepted["runId"], port,
                               launcher, repo, launched=True)
    launcher.validate_report(manifest["report"])
    saves = list((directory / "game/saves").iterdir())
    if (len(saves) != 1 or saves[0].name != accepted["world"]
            or not (saves[0] / "level.dat").is_file()
            or saves[0].resolve() != directory / "game/saves" / accepted["world"]):
        raise ValueError("Reload requires only the reviewed native-created disposable acceptance world")


def validate_run(directory, manifest, launcher):
    if manifest.get("uiCapture"):
        if not manifest["scenario"].startswith("ui-"):
            raise ValueError("Non-UI scenario claimed a UI capture")
        report = launcher.validate_ui_capture(manifest)
    else:
        if manifest["scenario"].startswith("ui-"):
            raise ValueError("UI scenario lacks its native capture gate")
        report = launcher.validate_report(manifest["report"])
    if report.get("scenario") != manifest["scenario"]:
        raise ValueError("Actual report scenario differs from the launched scenario")
    if not manifest.get("uiCapture"):
        launcher.validate_final_screenshot(manifest)
    return report


def retain_sources(repo, output):
    files = {}
    for name in SOURCE_FILES:
        source = Path(repo) / name
        if not source.is_file():
            continue
        destination = output / "sources" / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
        original = receipt(source)
        retained = receipt(destination)
        if retained["sha256"] != original["sha256"]:
            raise ValueError("Retained source bytes changed: " + name)
        files[name] = {"original": original, "retained": retained}
    write_json(output / "source-receipts.json", files)
    return files


def retain_evidence(record, directory, repo):
    # Inventory exact outputs without copying worlds, assets, or large shared runtimes.
    if directory is None or not directory.is_dir():
        return
    record["directory"] = str(directory)
    evidence = []
    for name in ("launch.json", "source-manifest.json", "report.json", "trace.json", "client.log"):
        path = directory / name
        if path.is_file():
            evidence.append(receipt(path))
    screenshots = directory / "screenshots"
    if screenshots.is_dir():
        evidence.extend(receipt(path) for path in sorted(screenshots.rglob("*")) if path.is_file())
    manifest_path = directory / "launch.json"
    if manifest_path.is_file():
        game = safe_output(read_json(manifest_path)["gameDirectory"], repo)
        own_games = {directory.resolve() / "game"}
        if directory.parent.name == "phases":
            own_games.add(directory.parent.parent.resolve() / "game")
        if game not in own_games:
            raise ValueError("Evidence game directory must belong to this batch's own disposable run")
        # Native export and Builder persistence receipts are small unique diagnostics.
        # Do not inventory whole worlds, history databases, runtime libraries or assets.
        for folder in (game / "openallay/exports", game / "config/openallay/e2e", game / "crash-reports"):
            if folder.is_dir():
                evidence.extend(receipt(path) for path in sorted(folder.rglob("*")) if path.is_file())
    record["evidence"] = evidence


def validate_arguments(args, repo):
    if sys.platform != "linux":
        raise ValueError("This CI runner requires Linux; the workflow supplies Xvfb and Mesa")
    if not os.environ.get("DISPLAY"):
        raise ValueError("DISPLAY is missing; the workflow must start Xvfb before the runner")
    target_pins = load_launcher(REPO).runtime_pins(args.minecraft_version, repo)
    if target_pins["minecraft_version"] != args.minecraft_version:
        raise ValueError("Exact Minecraft target differs from the source profile")
    if not re.fullmatch(r"[0-9a-f]{64}", args.artifact_sha256):
        raise ValueError("--artifact-sha256 must be the exact lowercase SHA256 of the production JAR")
    if args.timeout_seconds <= 0 or not CLEANUP_RESERVE_SECONDS < args.batch_timeout_seconds <= MAX_BATCH_SECONDS:
        raise ValueError("Use a positive per-run timeout and a batch timeout between 21 and 2100 seconds")
    if not 0 <= args.fixture_port <= 65535:
        raise ValueError("Fixture port must be zero (auto) or a valid TCP port")
    if not args.scenarios or len(set(args.scenarios)) != len(args.scenarios):
        raise ValueError("Choose at least one scenario without duplicates")
    if "builder-reload" in args.scenarios and ("builder-acceptance" not in args.scenarios
            or args.scenarios.index("builder-reload") < args.scenarios.index("builder-acceptance")):
        raise ValueError("builder-reload must follow builder-acceptance in this same batch")
    for name in ("jar", "java", "minecraft_root", "assets_root", "fabric_api", "gradle_cache"):
        value = getattr(args, name)
        if value is not None:
            setattr(args, name, value.resolve())
    for name in ("jar", "java"):
        if not getattr(args, name).is_file():
            raise ValueError("Required provisioned file is missing: --" + name.replace("_", "-"))
    for name in ("minecraft_root", "assets_root"):
        if not getattr(args, name).is_dir():
            raise ValueError("Required provisioned directory is missing: --" + name.replace("_", "-"))
    if args.loader == "fabric" and (args.fabric_api is None or not args.fabric_api.is_file()):
        raise ValueError("Fabric requires its provisioned --fabric-api JAR")
    batch_id = args.batch_id or f"ci-{args.loader}-{args.minecraft_version.replace('.', '-')}-{args.artifact_sha256[:12]}"
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,55}", batch_id):
        raise ValueError("batch-id must be a filesystem-safe identifier of at most 56 characters")
    output = safe_output(Path(repo) / "build/e2e/ci-client" / args.loader / batch_id, repo)
    if output.exists():
        raise ValueError("Batch output already exists; preserve it and choose a new --batch-id (no automatic retry)")
    for scenario in args.scenarios:
        fresh = Path(repo) / "build/e2e/packaged-builder" / args.loader / (batch_id + "-" + scenario)
        if fresh.exists():
            raise ValueError("Scenario output already exists; choose a new batch-id")
    return batch_id, output


def run_batch(args, repo=REPO):
    repo = Path(repo).resolve()
    batch_id, output = validate_arguments(args, repo)
    output.mkdir(parents=True)
    started = time.monotonic()
    work_deadline = started + args.batch_timeout_seconds - CLEANUP_RESERVE_SECONDS
    environment = os.environ.copy()
    environment["OPENALLAY_E2E_FIXTURE_KEY"] = SYNTHETIC_KEY
    records = [{"scenario": scenario, "runId": batch_id + "-" + scenario,
                "status": "NOT_RUN", "nativeAcceptance": "NOT_RUN", "failures": []}
               for scenario in args.scenarios]
    summary = {"loader": args.loader, "minecraft": args.minecraft_version, "batchId": batch_id,
               "startedAt": datetime.now(timezone.utc).isoformat(), "status": "FAILED",
               "artifactSha256": args.artifact_sha256, "batchTimeoutSeconds": args.batch_timeout_seconds,
               "scenarios": records, "failures": [], "noPaidModel": True,
               "diagnostics": {"batchDirectory": str(output), "summary": str(output / "summary.json"),
                               "sourceFolders": [str(output / "sources")], "logFiles": [],
                               "scenarioDirectories": []},
               "model": "deterministic-loopback-fixture-not-live-model",
               "acceptanceBoundary": "Existing packaged launcher exit, actual client exit/report, and mechanical native/UI gates only; no model accuracy or physical-device claim. Native images remain for separate visual review."}
    fixture = None
    launcher = None
    previous_term = signal.getsignal(signal.SIGTERM)
    def interrupted(signum, frame):
        raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, interrupted)
    try:
        summary["sources"] = retain_sources(repo, output)
        summary["originalArtifact"] = verify_artifact(args.jar, args.artifact_sha256)
        launcher = load_launcher(repo)
        launcher.runtime_pins(args.minecraft_version, repo)
        mod_version = args.mod_version
        if mod_version is None:
            matches = re.findall(r"^version=([^\r\n]+)$", (Path(repo) / "gradle.properties").read_text(), re.MULTILINE)
            if len(matches) != 1:
                raise ValueError("Repository release version is missing or ambiguous")
            mod_version = matches[0]
        summary["packagedArtifact"] = launcher.packaged_artifact(args.jar, args.loader, mod_version, repo, args.minecraft_version, getattr(args, "artifact_family", None))
        if (summary["packagedArtifact"].get("sha256") != args.artifact_sha256
                or summary["packagedArtifact"].get("nativeWorldBootstrapPresent") is not True):
            raise ValueError("Exact production artifact must include its opt-in native client bootstrap")
        port = args.fixture_port or available_port()
        fixture = Fixture(repo, output, port, environment)
        summary["fixture"] = {"argv": fixture.command, "log": str(fixture.log_path), "port": port,
                              "credential": "synthetic local fixture value, not a secret"}
        fixture.start(min(10, max(0, work_deadline - time.monotonic())))
        summary["fixture"]["pid"] = fixture.process.pid
        accepted = None
        fatal = None
        for record in records:
            scenario = record["scenario"]
            directory = None
            try:
                if fatal:
                    raise ValueError("Not run after batch integrity failure: " + fatal)
                fixture.check()
                if time.monotonic() >= work_deadline:
                    raise ValueError("Not run: bounded batch deadline exhausted")
                record["artifactBefore"] = verify_artifact(args.jar, args.artifact_sha256)
                if scenario == "builder-reload":
                    if accepted is None:
                        raise ValueError("Not run: builder-acceptance did not pass; no other world may be resumed")
                    review_passed_acceptance(accepted, args, launcher, repo, port)
                    directory = accepted["directory"] / "phases" / record["runId"]
                else:
                    directory = Path(repo) / "build/e2e/packaged-builder" / args.loader / record["runId"]
                command = prepare_command(args, scenario, record["runId"], port, repo,
                                          accepted if scenario == "builder-reload" else None)
                record["prepareExitCode"] = run_command(command, output / (scenario + ".prepare.log"),
                                                        min(work_deadline, time.monotonic() + 90), environment, repo)
                manifest = review_manifest(directory, args, scenario, record["runId"], port, launcher, repo,
                                           accepted if scenario == "builder-reload" else None)
                record["reviewedPreparedManifest"] = receipt(directory / "launch.json")
                fixture.check()
                record["launcherExitCode"] = run_command(
                    [sys.executable, str(Path(repo) / "scripts/run-packaged-builder-acceptance.py"),
                     "--launch-prepared", str(directory)], output / (scenario + ".launch.log"),
                    min(work_deadline, time.monotonic() + manifest["wallTimeoutSeconds"] + 5),
                    environment, repo, prepared=directory)
                manifest = review_manifest(directory, args, scenario, record["runId"], port, launcher, repo,
                                           accepted if scenario == "builder-reload" else None, launched=True)
                report = validate_run(directory, manifest, launcher)
                record["clientExitCode"] = manifest["clientExitCode"]
                record["actualTerminalOutcome"] = report["outcome"]
                if scenario.startswith("builder-"):
                    record["nativeAcceptance"] = report["nativeAcceptance"]["outcome"]
                else:
                    record["nativeAcceptance"] = "UI_MECHANICAL_CAPTURE_PASSED"
                    record["visualReview"] = "REQUIRED: retained native images"
                record["status"] = "PASSED"
                if scenario == "builder-acceptance":
                    accepted = {"directory": directory, "world": manifest["world"], "runId": record["runId"],
                                "launchSha256": sha256(directory / "launch.json"),
                                "reportSha256": sha256(directory / "report.json")}
            except (Exception, KeyboardInterrupt) as failure:
                record["status"] = "FAILED"
                record["failures"].append(str(failure) or type(failure).__name__)
                (output / (scenario + ".runner-error.log")).write_text(traceback.format_exc(), encoding="utf-8")
                if isinstance(failure, KeyboardInterrupt):
                    fatal = "runner interrupted"
            finally:
                try:
                    record["artifactAfter"] = verify_artifact(args.jar, args.artifact_sha256)
                except Exception as failure:
                    record["status"] = "FAILED"
                    record["failures"].append(str(failure))
                    fatal = str(failure)
                try:
                    retain_evidence(record, directory, repo)
                except Exception as failure:
                    record["status"] = "FAILED"
                    record["failures"].append("Evidence inventory failed: " + str(failure))
                if scenario == "builder-acceptance" and record["status"] != "PASSED":
                    accepted = None
                write_json(output / "summary.json", summary)
                print(scenario + ": " + record["status"] + (" — " + "; ".join(record["failures"]) if record["failures"] else ""), flush=True)
    except (Exception, KeyboardInterrupt) as failure:
        summary["failures"].append(str(failure) or type(failure).__name__)
        (output / "batch-error.log").write_text(traceback.format_exc(), encoding="utf-8")
    finally:
        try:
            if fixture is not None:
                fixture.stop()
        except Exception as failure:
            summary["failures"].append("Owned fixture cleanup failed: " + str(failure))
        signal.signal(signal.SIGTERM, previous_term)
        for record in records:
            if record["status"] == "NOT_RUN":
                record["status"] = "FAILED"
                record["failures"].append("Scenario was not run; see batch failures")
        try:
            summary["finalArtifact"] = verify_artifact(args.jar, args.artifact_sha256)
        except Exception as failure:
            summary["failures"].append(str(failure))
        summary["diagnostics"]["scenarioDirectories"] = [record["directory"] for record in records if "directory" in record]
        summary["diagnostics"]["logFiles"] = [str(path) for path in sorted(output.glob("*.log")) if path.is_file()]
        summary["status"] = ("PASSED" if not summary["failures"] and all(record["status"] == "PASSED" for record in records)
                             else "FAILED")
        summary["elapsedSeconds"] = round(time.monotonic() - started, 3)
        summary["closedAt"] = datetime.now(timezone.utc).isoformat()
        write_json(output / "summary.json", summary)
        print("Batch " + summary["status"] + ": " + str(output / "summary.json"), flush=True)
    return 0 if summary["status"] == "PASSED" else 1


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("loader", choices=("fabric", "neoforge"))
    result.add_argument("--minecraft-target", "--minecraft-version", dest="minecraft_version", default="26.2",
                        choices=load_launcher(REPO).minecraft_targets(REPO))
    result.add_argument("--artifact-family", help="Explicit catalog interval with unchanged production bytes")
    result.add_argument("--jar", type=Path, required=True)
    result.add_argument("--artifact-sha256", "--jar-sha256", required=True)
    result.add_argument("--java", type=Path, required=True)
    result.add_argument("--minecraft-root", type=Path, required=True)
    result.add_argument("--assets-root", type=Path, required=True)
    result.add_argument("--fabric-api", type=Path)
    result.add_argument("--gradle-cache", type=Path)
    result.add_argument("--mod-version")
    result.add_argument("--batch-id", help="Safe unique CI invocation ID; default is deterministic loader/version/JAR prefix")
    result.add_argument("--scenarios", nargs="+", choices=SCENARIOS, default=list(SCENARIOS))
    result.add_argument("--fixture-port", type=int, default=0, help="Owned loopback fixture port; zero selects a free local port")
    result.add_argument("--timeout-seconds", "--timeout", type=int, default=180,
                        help="Per-client deadline; existing live-UX gate requires at least 600 seconds")
    result.add_argument("--batch-timeout-seconds", type=int, default=MAX_BATCH_SECONDS,
                        help="Total loader scope, at most 2100 seconds, including a 20-second cleanup reserve")
    return result


def main(argv=None):
    args = parser().parse_args(argv)
    try:
        return run_batch(args)
    except (ValueError, OSError) as failure:
        print("CI packaged client acceptance refused: " + str(failure), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
