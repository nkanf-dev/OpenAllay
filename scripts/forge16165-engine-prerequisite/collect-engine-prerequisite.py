#!/usr/bin/env python3
"""Remote-only genuine Forge36 engine collector. Never calls the stock title collector."""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import select
import shutil
import subprocess
import sys
import time
import traceback

PACKET = Path(__file__).resolve().parent
STAGES = ["identity", "engine-logging", "bound-engine-json", "json-trees-readers", "engine-rhino-record-schema",
          "rhino-default-interface-java-adapter", "existing-tool-envelope-copy", "builder-descriptor-only"]
PASS = "OA36 ENGINE_PREREQUISITE_PASS"
FAIL = "OA36 ENGINE_PREREQUISITE_FAIL stage="


def load_stock(repo):
    freeze = json.loads((PACKET / "source-freeze.json").read_text())
    path = PACKET / "stock/stock-forge36-prerequisite.py"
    if hashlib.sha256(path.read_bytes()).hexdigest() != freeze["stockCollectorSha256"]:
        raise ValueError("Stock source differs from frozen helper contract")
    spec = importlib.util.spec_from_file_location("engine_stock_forge36", path)
    stock = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(stock)
    runtime, launch, original = stock.load_helpers()
    if original != freeze["stockSourceFreeze"]:
        raise ValueError("Stock helper/metadata freeze differs")
    return stock, runtime, launch, original


def terminal(text):
    if FAIL in text:
        return "probe-failure-marker"
    if PASS in text:
        return "probe-pass-marker"
    return None


def read_receipt(path, fatmod_hash, classpath, expected_pid=None, client_log=None):
    value = json.loads(path.read_text())
    if value.get("prerequisiteOnly") is not True or value.get("fullNativeSupport") is not False:
        raise ValueError("Receipt must describe only the engine prerequisite")
    stages = value.get("stages", [])
    names = [stage.get("stage") for stage in stages]
    if names != STAGES[:len(names)] or len(names) > len(STAGES):
        raise ValueError("Stage ordering differs")
    if value.get("status") == "PASS":
        if names != STAGES or any(stage.get("status") != "PASS" for stage in stages):
            raise ValueError("Incomplete prerequisite PASS")
        identity = stages[0]["details"]
        if expected_pid is not None and identity.get("pid") != str(expected_pid):
            raise ValueError("Probe did not run in the launched client process")
        shared = [("dev.openallay.forge36probe.Probe", 61),
                  ("dev.openallay.script.RhinoJavascriptRuntime", 61),
                  ("dev.openallay.json.EngineJson", 61),
                  ("dev.latvian.mods.rhino.Context", 61),
                  ("dev.openallay.api.extension.OpenAllayExtension", 52),
                  ("dev.openallay.OpenAllayConstants", 61),
                  ("dev.openallay.logging.OpenAllayLogger", 61)]
        loader = identity[shared[0][0]]["loaderIdentity"]
        mod_source = identity[shared[0][0]]["codeSource"]
        for name, major in shared:
            item = identity[name]
            if (item["classMajor"] != major or item["archiveSha256"] != fatmod_hash
                    or item["loaderIdentity"] != loader or item["codeSource"] != mod_source):
                raise ValueError("Shared ordinary mod identity differs: " + name)
        builder = stages[-1]["details"]["dev.openallay.builder.BuilderExtension"]
        if (builder["classMajor"] != 52 or builder["archiveSha256"] != fatmod_hash
                or builder["loaderIdentity"] != loader or builder["codeSource"] != mod_source):
            raise ValueError("Builder sole SDK/mod identity differs")
        origins = {(str(Path(item["path"]).resolve()), item["sha256"]): item["coordinate"] for item in classpath}
        for name, coordinate in [("com.google.gson.Gson", "com.google.code.gson:gson:2.8.0"),
                                 ("com.google.common.collect.ImmutableList", "com.google.guava:guava:21.0"),
                                 ("org.apache.logging.log4j.Logger", "org.apache.logging.log4j:log4j-api:2.15.0")]:
            item = identity[name]
            actual = origins.get((item["codeSource"], item["archiveSha256"]))
            if actual is None or item["archiveSha256"] == fatmod_hash:
                raise ValueError("Host origin is not an unchanged official classpath archive: " + name)
            if coordinate is not None and actual != coordinate:
                raise ValueError("Host coordinate differs: " + name)
        jdk_logging = identity["jdkLogging"]
        if (jdk_logging.get("class") != "java.lang.System$Logger"
                or jdk_logging.get("loader") != "bootstrap" or jdk_logging.get("module") != "java.base"
                or jdk_logging.get("name") != "OpenAllay"
                or not jdk_logging.get("implementationClass") or not jdk_logging.get("implementationModule")):
            raise ValueError("JDK System.Logger identity differs")
        logging = stages[1]["details"]
        if (logging.get("loggerClass") != "dev.openallay.logging.OpenAllayLogger"
                or logging.get("loggingProof") != "formatted-info-warning-error-and-throwable"):
            raise ValueError("Engine logging invocation proof differs")
        if client_log is None:
            raise ValueError("Engine logging requires the launched client log")
        text = client_log.read_text(errors="replace")
        markers = ["OA36 ENGINE_LOGGING_INFO engine=OpenAllay value=brace-ok",
                   "OA36 ENGINE_LOGGING_WARN engine=OpenAllay value=brace-ok",
                   "OA36 ENGINE_LOGGING_ERROR engine=OpenAllay value=brace-ok",
                   "java.lang.IllegalStateException: OA36 ENGINE_LOGGING_THROWABLE"]
        throwable = text.partition(markers[-1])[2]
        stack_line = next((line for line in throwable.splitlines()[:8] if line.strip()), "")
        if (any(marker not in text for marker in markers)
                or "at dev.openallay.forge36probe.Probe$Client.run(" not in stack_line):
            raise ValueError("engine-logging: formatted messages or exception stack missing from client log")
        return value
    if (value.get("status") != "FAIL" or not names or stages[-1].get("status") != "FAIL"
            or any(stage.get("status") != "PASS" for stage in stages[:-1])
            or value.get("failureStage") != names[-1] or not value.get("fullCause")):
        raise ValueError("Failure receipt must retain first stage and full cause")
    return value


def collect(process, log, timeout):
    deadline = time.monotonic() + timeout
    cursor = 0
    pending = ""
    while True:
        with log.open(errors="replace") as stream:
            stream.seek(cursor)
            pending += stream.read()
            cursor = stream.tell()
        marker = terminal(pending)
        if marker:
            return marker
        if process.poll() is not None:
            return "client-exited-before-terminal-marker"
        if time.monotonic() >= deadline:
            return "probe-timeout"
        # Internal remote collector wait, not a coordinator polling loop.
        try:
            process.wait(timeout=min(0.2, max(0.001, deadline - time.monotonic())))
        except subprocess.TimeoutExpired:
            pass


def boot(args, root, java, assets, stock, runtime, launch, expected, vanilla, version, output):
    game = output / "game"
    (game / "mods").mkdir(parents=True)
    installed = game / "mods/openallay-engine-prerequisite.jar"
    shutil.copyfile(args.mod, installed)
    runtime.require(stock.sha(installed) == args.mod_sha256, "Installed fat mod differs")
    (game / "options.txt").write_text("renderDistance:4\nmaxFps:15\npauseOnLostFocus:false\nguiScale:2\n")
    cp = launch.version_libraries(vanilla, root, Path("/nonexistent"), allow_gradle=False)
    fml = launch.version_libraries(version, root, Path("/nonexistent"), allow_gradle=False)
    replacements = {tuple(name.split(":")[:2]) for name, _ in fml}
    cp = [(name, path) for name, path in cp if tuple(name.split(":")[:2]) not in replacements] + fml
    classpath = stock.inspect_classpath(cp, expected, runtime)
    natives = launch.extract_natives(launch.native_libraries(vanilla, root), output / "natives")
    values = {"natives_directory": output / "natives", "launcher_name": "OpenAllayEnginePrerequisite",
              "launcher_version": "source-probe", "classpath": os.pathsep.join(str(p) for _, p in cp),
              "auth_player_name": "devGameUser", "version_name": stock.PROFILE, "game_directory": game,
              "assets_root": assets, "assets_index_name": vanilla["assetIndex"]["id"],
              "auth_uuid": launch.offline_uuid("devGameUser"), "auth_access_token": "0",
              "user_type": "legacy", "version_type": version["type"], "resolution_width": "1280",
              "resolution_height": "960"}
    features = {"has_custom_resolution": True}
    jvm = launch.expand_arguments(vanilla["arguments"]["jvm"], values, features) + stock.FLAGS
    game_args = launch.expand_arguments(vanilla["arguments"]["game"], values, features) + version["arguments"]["game"]
    probe_receipt = output / "probe-receipt.json"
    command = [str(java), "-Xms256M", "-Xmx1536M", "-Doa36.receipt=" + str(probe_receipt),
               "-Xlog:class+load=info:file=" + str(output / "class-load.log")] + jvm + [stock.MAIN] + game_args
    env = {k: v for k, v in os.environ.items() if k not in
           ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH", "DISPLAY")}
    receipt = {"status": "running", "prerequisiteOnly": True, "fullNativeSupport": False,
               "titleScreenRetested": False, "startedAt": datetime.now(timezone.utc).isoformat()}
    runtime.write_json(output / "launch.json", {"command": command, "classpath": classpath,
                       "natives": natives, "fatModSha256": args.mod_sha256,
                       "profileIsolated": True, "offlineDevelopmentUser": "devGameUser"})
    xvfb = None
    process = None
    log = output / "client.log"
    try:
        with (output / "xvfb.log").open("w") as display_log:
            xvfb = subprocess.Popen(["Xvfb", "-displayfd", "1", "-screen", "0", "1280x960x24", "-nolisten", "tcp"],
                                    stdout=subprocess.PIPE, stderr=display_log, env=env, start_new_session=True)
            ready, _, _ = select.select([xvfb.stdout], [], [], 15)
            runtime.require(ready, "Owned Xvfb did not become ready")
            display = xvfb.stdout.readline().decode().strip()
            runtime.require(display.isdecimal() and xvfb.poll() is None, "Owned Xvfb failed startup")
            env["DISPLAY"] = ":" + display
            receipt["xvfbPid"] = xvfb.pid
            receipt["display"] = env["DISPLAY"]
            with log.open("w") as stream:
                process = subprocess.Popen(command, cwd=game, env=env, stdout=stream,
                                           stderr=subprocess.STDOUT, start_new_session=True)
                receipt["clientPid"] = process.pid
                outcome = collect(process, log, args.timeout)
                receipt["terminalCollection"] = outcome
                if outcome in ("probe-pass-marker", "probe-failure-marker"):
                    proof = read_receipt(probe_receipt, args.mod_sha256, classpath, process.pid, log)
                    expected_status = "PASS" if outcome == "probe-pass-marker" else "FAIL"
                    runtime.require(proof["status"] == expected_status, "Terminal marker and receipt disagree")
                    receipt["status"] = expected_status
                    receipt["probeReceiptSha256"] = stock.sha(probe_receipt)
                else:
                    receipt["status"] = outcome
                    receipt["crashReports"] = [str(p.relative_to(output)) for p in sorted(game.glob("crash-reports/*")) if p.is_file()]
                    receipt["lastLogLines"] = log.read_text(errors="replace").splitlines()[-120:]
    except Exception as failure:
        receipt["status"] = "collector-failure"
        receipt["fullCause"] = traceback.format_exc()
        raise
    finally:
        # Every group is created and owned by this collector. Stop fast after terminal proof.
        stop_failures = []
        for name, owned in (("termination", process), ("xvfbTermination", xvfb)):
            if owned is not None:
                try:
                    receipt[name] = stock.stop_owned(owned)
                except Exception:
                    stop_failures.append({"group": name, "fullCause": traceback.format_exc()})
        if xvfb is not None and xvfb.stdout is not None:
            xvfb.stdout.close()
        if stop_failures:
            receipt["priorStatus"] = receipt["status"]
            receipt["status"] = "owned-process-stop-failure"
            receipt["stopFailures"] = stop_failures
        receipt["collectionStopIntentional"] = True
        receipt["cleanClientShutdownProven"] = False
        receipt["endedAt"] = datetime.now(timezone.utc).isoformat()
        if log.exists():
            receipt["clientLogSha256"] = stock.sha(log)
        runtime.write_json(output / "collector-receipt.json", receipt)
        print(json.dumps(receipt, sort_keys=True), flush=True)
    return 0 if receipt["status"] == "PASS" else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, type=Path)
    parser.add_argument("--java", required=True, type=Path)
    parser.add_argument("--java-release", default="17.0.18+8")
    parser.add_argument("--minecraft-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--mod", required=True, type=Path)
    parser.add_argument("--mod-sha256", required=True)
    parser.add_argument("--timeout", type=int, default=120)
    args = parser.parse_args(argv)
    args.repo = args.repo.resolve()
    stock, runtime, launch, freeze = load_stock(args.repo)
    output = launch.safe_output(args.output, args.repo)
    runtime.require(output.is_relative_to(args.repo / "build/e2e/forge16165-engine-prerequisite")
                    and output != args.repo / "build/e2e/forge16165-engine-prerequisite",
                    "Use a fresh capture below dedicated engine prerequisite output")
    runtime.require(not output.exists(), "Collector output must be fresh")
    runtime.require(platform.system() == "Linux" and platform.machine() == "x86_64", "Remote Linux x86_64 only")
    runtime.require(15 <= args.timeout <= 300, "Timeout must be bounded")
    runtime.require(len(args.mod_sha256) == 64 and all(c in "0123456789abcdef" for c in args.mod_sha256), "Exact SHA256 required")
    runtime.require(args.mod.is_file() and stock.sha(args.mod) == args.mod_sha256, "Hash-bound sole fat mod required")
    runtime.require(shutil.disk_usage(args.repo).free >= 10*1024**3, "Remote runner needs >=10 GiB free")
    output.mkdir(parents=True)
    metadata = stock.PACKET
    install = runtime.json_bytes((metadata / "install_profile.json").read_bytes())
    version = runtime.json_bytes((metadata / "version.json").read_bytes())
    vanilla = runtime.json_bytes((metadata / "minecraft-1.16.5.json").read_bytes())
    expected = stock.validate_metadata(install, version, vanilla)
    try:
        # Exact unchanged prepare contract, processor flags, assets, verified libraries.
        root, java, assets = stock.prepare(args, runtime, launch, freeze, install, version, vanilla)
        return boot(args, root, java, assets, stock, runtime, launch, expected, vanilla, version, output)
    except Exception:
        runtime.write_json(output / "failure.json", {"status": "fatal-engine-prerequisite-failure",
                           "fullCause": traceback.format_exc(), "prerequisiteOnly": True,
                           "fullNativeSupport": False, "titleScreenRetested": False})
        raise


if __name__ == "__main__":
    sys.exit(main())
