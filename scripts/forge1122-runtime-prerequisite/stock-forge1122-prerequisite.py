#!/usr/bin/env python3
"""One isolated Linux Forge1122 stock-client prerequisite. No mod, engine or world probe."""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import re
import signal
import subprocess
import sys
import zipfile

PACKET = Path(__file__).resolve().parent
PINS = {"minecraft_version": "1.12.2", "forge_version": "1.12.2-14.23.5.2864",
        "java_version": "17", "vanilla_java_version": "8"}
PROFILE = "1.12.2-forge-14.23.5.2864"
MAIN = "net.minecraft.launchwrapper.Launch"
# Raw stock baseline: no Java17 launching repair is applied before its first failure.
FLAGS = []

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load_helpers(repo=None):
    freeze = json.loads((PACKET / "source-freeze.json").read_text())
    helpers = Path(repo) / "scripts" if repo is not None else PACKET.parent.parent
    for name, expected in freeze["helpers"].items():
        if sha(helpers / name) != expected:
            raise ValueError("Frozen helper differs: " + name)
    for name, expected in freeze["metadata"].items():
        if sha(PACKET / name) != expected:
            raise ValueError("Frozen official metadata differs: " + name)
    sys.path.insert(0, str(helpers))
    def module(name, path):
        spec = importlib.util.spec_from_file_location(name, path)
        result = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(result)
        return result
    return (module("forge1122_provision_helper", helpers / "prepare-ci-minecraft-runtime.py"),
            module("forge1122_launch_helper", helpers / "run-packaged-builder-acceptance.py"), freeze)


def validate_metadata(install, version, vanilla):
    if (install["minecraft"] != "1.12.2" or install["version"] != PROFILE
            or install["spec"] != 0 or install["processors"] != [] or install["data"] != {}
            or version["id"] != PROFILE or version["inheritsFrom"] != "1.12.2"
            or version["mainClass"] != MAIN or "arguments" in version
            or vanilla["id"] != "1.12.2" or vanilla["javaVersion"]["majorVersion"] != 8
            or "--tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker" not in version["minecraftArguments"]):
        raise ValueError("Exact stock Forge1122/Minecraft metadata identity differs")
    expected = {"net.minecraftforge:forge:1.12.2-14.23.5.2864",
                "org.ow2.asm:asm-debug-all:5.2", "net.minecraft:launchwrapper:1.12"}
    names = [library["name"] for library in version["libraries"]]
    for name in expected:
        if names.count(name) != 1:
            raise ValueError("Missing or duplicate official runtime library: " + name)
    if any(name.startswith("org.ow2.asm:") and name != "org.ow2.asm:asm-debug-all:5.2" for name in names):
        raise ValueError("Do not substitute a newer runtime ASM library")
    return expected


def stop_owned(process):
    receipt = {"pid": process.pid, "initialExitCode": process.poll(), "signals": []}
    # POSIX group was created by this exact Popen. Never signal another game's process.
    if process.poll() is None:
        if os.getpgid(process.pid) != process.pid:
            raise RuntimeError("Owned game lost its process-group identity")
        os.killpg(process.pid, signal.SIGTERM)
        receipt["signals"].append("SIGTERM")
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            receipt["signals"].append("SIGKILL")
            process.wait(timeout=5)
    receipt["finalExitCode"] = process.returncode
    receipt["reaped"] = process.poll() is not None
    return receipt


def inspect_classpath(cp, expected, runtime):
    result = []
    for name, path in cp:
        runtime.require(path.is_file(), "Missing installed classpath library: " + name)
        item = {"coordinate": name, "path": str(path), "sha256": runtime.file_hash(path)}
        if name in expected:
            with zipfile.ZipFile(path) as archive:
                item["manifest"] = archive.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
        with zipfile.ZipFile(path) as archive:
            majors = {}
            mr_classes = []
            for entry in archive.infolist():
                if not entry.filename.endswith(".class"):
                    continue
                with archive.open(entry) as stream:
                    header = stream.read(8)
                if len(header) != 8 or header[:4] != b"\xca\xfe\xba\xbe":
                    raise ValueError("Invalid class header: " + entry.filename)
                major = int.from_bytes(header[6:8], "big")
                majors[str(major)] = majors.get(str(major), 0) + 1
                if entry.filename.startswith("META-INF/versions/"):
                    mr_classes.append({"entry": entry.filename, "major": major})
            item["classMajorCounts"] = majors
            item["multiReleaseClasses"] = mr_classes
            item["asm5ScanAboveJava8"] = sum(n for major, n in majors.items() if int(major) > 52)
        result.append(item)
    return result


def prepare(args, runtime, launch, freeze, install, version, vanilla):
    root = runtime.safe_root(args.minecraft_root, args.repo)
    runtime.require(root == args.repo.resolve() / "build/e2e/runtime/forge1122-stock/minecraft",
                    "Use the dedicated stock prerequisite root")
    java = args.java.resolve()
    info = runtime.check_java(java, 17)
    runtime.require(args.java_release == "17.0.18+8" and "17.0.18" in info
                    and "Temurin" in info and "+8" in info,
                    "Use exact selected Temurin 17.0.18+8")
    runtime.require(not root.exists(), "This prerequisite requires a fresh root; do not retry in-place")
    runtime.claim_root(root, "1.12.2")
    # Vanilla's Java8 metadata is preserved. It is not a selected Java17 claim.
    prepared_vanilla, files = runtime.prepare_vanilla(root, {**PINS, "java_version": "8"})
    runtime.require(prepared_vanilla == vanilla, "Live official Minecraft metadata differs from frozen selection")
    installer = root / ".provision/forge-1.12.2-14.23.5.2864-installer.jar"
    pin = freeze["installer"]
    runtime.download(pin["url"], installer, pin["sha1"], pin["size"], maximum=8*1024*1024)
    runtime.require(runtime.file_hash(installer) == pin["sha256"], "Official installer SHA256 differs")
    actual_install, actual_version = runtime.inspect_installer(installer, "forge")
    runtime.require(actual_install == install and actual_version == version, "Installer metadata differs from frozen source")
    files += [installer]
    for name, value in (("forge-install_profile.json", install), ("forge-version.json", version)):
        destination = root / ".provision" / name
        runtime.write_json(destination, value)
        files.append(destination)
    # Empty-URL Forge entries refer to the same authentic installer-bundled Maven bytes.
    with zipfile.ZipFile(installer) as archive:
        for metadata in (install, version):
            for library in metadata["libraries"]:
                artifact = library["downloads"]["artifact"]
                if not artifact["url"]:
                    path = runtime.relative_file(root / "libraries", artifact["path"])
                    content = archive.read("maven/" + artifact["path"])
                    runtime.require(len(content) == artifact["size"]
                                    and hashlib.sha1(content).hexdigest() == artifact["sha1"], "Bundled library differs")
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(content)
                    files.append(path)
            external = {"libraries": [lib for lib in metadata["libraries"] if lib["downloads"]["artifact"]["url"]]}
            files += runtime.prepare_libraries(external, root)
    command = runtime.installer_command("forge", java, installer, root, PINS)
    # The stock installer remains the sole owner of the installed Forge profile.
    runtime.run_installer(command, root, "forge")
    profile_path = root / "versions" / PROFILE / (PROFILE + ".json")
    runtime.require(runtime.json_bytes(profile_path.read_bytes()) == version, "Installed profile differs")
    for metadata in (install, version, vanilla):
        runtime.verify_downloaded_libraries(metadata, root)
    client = root / "versions/1.12.2/1.12.2.jar"
    runtime.require(runtime.file_hash(client, "sha1") == vanilla["downloads"]["client"]["sha1"], "Original client changed")
    files += [profile_path]
    assets = launch.prepare_assets(root, root.parent / "assets", repo=args.repo, minecraft_target="1.12.2")
    runtime.write_json(root / ".provision/stock-runtime.json", {
        "purpose": "stock-forge1122-java17-client-prerequisite", "pins": PINS,
        "javaInfo": info, "java": str(java), "javaExecutableSha256": runtime.file_hash(java),
        "sourceFreeze": freeze, "assets": str(assets), "profile": PROFILE,
        "files": {str(p.relative_to(root)): {"sha256": runtime.file_hash(p), "size": p.stat().st_size} for p in sorted(set(files))},
        "gameLaunched": False, "engineProbe": False})
    return root, java, assets


def boot(args, root, java, assets, runtime, launch, expected, vanilla, version):
    output = launch.safe_output(args.output, args.repo)
    runtime.require(not output.exists(), "Capture directory must be fresh")
    output.mkdir(parents=True)
    game = output / "game"
    (game / "mods").mkdir(parents=True)
    # Stock options only. No mod, hook, agent, resource pack, world or custom title renderer.
    (game / "options.txt").write_text("renderDistance:4\nmaxFps:15\npauseOnLostFocus:false\nguiScale:2\n")
    cp = launch.version_libraries(vanilla, root, Path("/nonexistent"), allow_gradle=False)
    fml = launch.version_libraries(version, root, Path("/nonexistent"), allow_gradle=False)
    replacements = {tuple(name.split(":")[:2]) for name, _ in fml}
    cp = [(name, path) for name, path in cp if tuple(name.split(":")[:2]) not in replacements] + fml
    # Legacy LaunchWrapper needs the original game JAR, not a fake alias or Gradle runtime.
    cp += [("com.mojang:minecraft:1.12.2:client", root / "versions/1.12.2/1.12.2.jar")]
    classpath_receipts = inspect_classpath(cp, expected, runtime)
    native_receipts = launch.extract_natives(launch.native_libraries(vanilla, root), output / "natives")
    values = {"natives_directory": output / "natives", "launcher_name": "OpenAllayStockPrerequisite",
              "launcher_version": "source-only-candidate", "classpath": os.pathsep.join(str(p) for _, p in cp),
              "auth_player_name": "StockPrerequisite", "version_name": PROFILE, "game_directory": game,
              "assets_root": assets, "assets_index_name": vanilla["assetIndex"]["id"],
              "auth_uuid": launch.offline_uuid("StockPrerequisite"), "auth_access_token": "0",
              "user_type": "legacy", "version_type": version["type"], "resolution_width": "1280",
              "resolution_height": "960"}
    jvm = ["-Djava.library.path=" + str(output / "natives"), "-cp", values["classpath"]]
    import shlex
    game_args = shlex.split(version["minecraftArguments"])
    game_args = [re.sub(r"\$\{([A-Za-z0-9_]+)\}", lambda match: str(values[match[1]]), item)
                 for item in game_args] + ["--width", "1280", "--height", "960"]
    if any("${" in item for item in game_args):
        raise ValueError("Unresolved legacy launcher argument")
    command = [str(java), "-Xms256M", "-Xmx1536M",
               "-Xlog:class+load=info:file=" + str(output / "class-load.log")] + jvm + [MAIN] + game_args
    env = {k:v for k,v in os.environ.items() if k not in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")}
    runtime.write_json(output / "launch.json", {"command": command, "classpath": classpath_receipts,
                       "natives": native_receipts, "noMods": True, "noEngineProbe": True})
    receipt = {"status": "running", "titleConfirmed": False, "captureSeconds": [45, 90],
               "screenshots": [], "startedAt": datetime.now(timezone.utc).isoformat()}
    log = output / "client.log"
    with log.open("w") as stream:
        process = subprocess.Popen(command, cwd=game, env=env, stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
        receipt["clientPid"] = process.pid
        try:
            # Fixed waits in this remote collector, not a coordinator poll loop.
            for elapsed in (45, 90):
                try:
                    process.wait(timeout=45)
                    receipt["status"] = "fatal-client-exited-before-capture"
                    break
                except subprocess.TimeoutExpired:
                    # OS-level full-frame capture of the actual unmodified client.
                    from PIL import ImageGrab
                    screenshot = output / ("full-frame-" + str(elapsed) + ".png")
                    ImageGrab.grab(xdisplay=os.environ["DISPLAY"]).save(screenshot)
                    receipt["screenshots"].append({"elapsedSeconds": elapsed,
                                                   "file": screenshot.name, "sha256": sha(screenshot)})
                    receipt["status"] = "captured-awaiting-title-review"
        finally:
            receipt["termination"] = stop_owned(process)
            receipt["endedAt"] = datetime.now(timezone.utc).isoformat()
            stream.flush()
            receipt["clientLogSha256"] = sha(log)
            runtime.write_json(output / "receipt.json", receipt)
    text = log.read_text(errors="replace")
    diagnostics = {"rawStockJava17": True, "titleConfirmed": False, "libraryReplacement": False,
                   "customClassLoader": False, "bootstrapApplied": False,
                   "launchWrapperAppLoaderCastFailure": "ClassCastException" in text and "URLClassLoader" in text,
                   "moduleAccessFailure": "InaccessibleObjectException" in text or "IllegalAccessError" in text,
                   "unsupportedClassVersion": "UnsupportedClassVersionError" in text,
                   "asmParseFailure": "ClassReader" in text and "IllegalArgumentException" in text,
                   "engineLoaded": False, "sdkAndBuilderTarget": 8, "futureEngineAndRhinoTarget": 17}
    runtime.write_json(output / "diagnostics.json", diagnostics)
    # A window or screenshot alone is not a title-success claim.
    return 0 if receipt["status"] == "captured-awaiting-title-review" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata-only", action="store_true", help="No network, Java or filesystem runtime action")
    parser.add_argument("--repo", type=Path)
    parser.add_argument("--java", type=Path)
    parser.add_argument("--java-release", default="17.0.18+8")
    parser.add_argument("--minecraft-root", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    runtime, launch, freeze = load_helpers(args.repo)
    install = runtime.json_bytes((PACKET / "install_profile.json").read_bytes())
    version = runtime.json_bytes((PACKET / "version.json").read_bytes())
    vanilla = runtime.json_bytes((PACKET / "minecraft-1.12.2.json").read_bytes())
    expected = validate_metadata(install, version, vanilla)
    if args.metadata_only:
        print(json.dumps({"status": "metadata-only-pass", "profile": PROFILE, "mainClass": MAIN,
                          "runtimeExecuted": False, "requiredRuntimeClosure": sorted(expected)}, indent=2))
        return 0
    runtime.require(platform.system() == "Linux" and platform.machine() == "x86_64", "Use Linux x86_64 remote CI")
    runtime.require(all((args.repo, args.java, args.minecraft_root, args.output)), "All explicit remote runtime paths required")
    runtime.require(os.environ.get("DISPLAY"), "Use an owned Xvfb display")
    runtime.require(__import__("shutil").disk_usage(args.repo).free >= 10*1024**3, "Remote runner needs >=10 GiB free")
    args.repo = args.repo.resolve()
    capture = launch.safe_output(args.output, args.repo)
    runtime.require(not capture.exists(), "Capture directory must be fresh")
    try:
        root, java, assets = prepare(args, runtime, launch, freeze, install, version, vanilla)
        return boot(args, root, java, assets, runtime, launch, expected, vanilla, version)
    except Exception as error:
        # Keep the first failure and stop. Do not automatically retry or alter flags.
        failure_path = launch.safe_output(args.output, args.repo) / "failure.json"
        runtime.write_json(failure_path, {"status": "fatal-prerequisite-failure",
                           "exception": type(error).__name__, "cause": str(error),
                           "titleConfirmed": False, "engineProbe": False})
        raise


if __name__ == "__main__":
    sys.exit(main())
