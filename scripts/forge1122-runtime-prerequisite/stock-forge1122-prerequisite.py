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


def classpath_libraries(metadata, root, launch):
    # Legacy Minecraft metadata includes native-only coordinates such as jinput-platform.
    # They belong to native_libraries()/extract_natives(), not the Java classpath.
    libraries = []
    for library in metadata.get("libraries", []):
        downloads = library.get("downloads")
        if (isinstance(downloads, dict) and set(downloads) == {"classifiers"}
                and isinstance(downloads["classifiers"], dict) and downloads["classifiers"]
                and isinstance(library.get("natives"), dict) and library["natives"]):
            continue
        libraries.append(library)
    return launch.version_libraries({**metadata, "libraries": libraries}, root,
                                    Path("/nonexistent"), allow_gradle=False)


def pinned_pack200_java8(info):
    # Genuine Java8 reports 1.8, not 8. Reject runtime17 or any other build-only JDK.
    return (re.search(r'version "1\.8\.0_482"', info) is not None
            and "Temurin" in info and "build 1.8.0_482-b08" in info)


def prepare_pack200(args, output, java, cp, runtime):
    # Build-only JDK8 conversion of exact official Forge resource. Runtime stays Java17.
    import lzma
    forge = next(path for name, path in cp if name == "net.minecraftforge:forge:1.12.2-14.23.5.2864")
    runtime.require(runtime.file_hash(forge) == "ff578d670d2c720a72f8fff31ea3d6868595c7e980ecdecba3254f307ef2c2a9", "Official Forge bytes differ")
    java8_home = Path(os.environ["OPENALLAY_PACK200_JAVA8_HOME"])
    java8 = java8_home / "bin/java"
    runtime.require(java8.is_file() and os.access(java8, os.X_OK), "Captured build-only Java8 must be executable")
    checked = subprocess.run([str(java8), "-version"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                             text=True, timeout=15, check=False)
    java8_info = checked.stdout.strip()
    runtime.require(checked.returncode == 0 and pinned_pack200_java8(java8_info),
                    "Use exact pinned build-only Temurin8u482-b08")
    with zipfile.ZipFile(forge) as archive:
        compressed = archive.read("binpatches.pack.lzma")
    runtime.require(hashlib.sha256(compressed).hexdigest() == "ceebaefd4abca814aa0160e71e62c507d63733b7da1773c1268e04ac9a720882", "Exact bundled LZMA resource differs")
    packed = lzma.decompress(compressed)
    runtime.require(hashlib.sha256(packed).hexdigest() == "637960a65a320b359561f86016c467e6cfd6d31c0507c1f76effb46e85ea4db6", "Exact decompressed Pack200 bytes differ")
    packed_path = output / "forge-binpatches.pack"
    packed_path.write_bytes(packed)
    unpacked = output / "forge-binpatches-jdk8.jar"
    command = [str(java8_home / "bin/unpack200"), str(packed_path), str(unpacked)]
    with (output / "pack200-build.log").open("w") as log:
        subprocess.run(command, check=True, stdout=log, stderr=subprocess.STDOUT)
    with zipfile.ZipFile(unpacked) as archive:
        runtime.require(archive.testzip() is None, "Genuine unpacked patches corrupt")
        entries = {entry.filename: hashlib.sha256(archive.read(entry)).hexdigest()
                   for entry in archive.infolist() if not entry.is_dir()}
    runtime.require(any(name.startswith("binpatch/client/") for name in entries), "Actual client patches missing")
    runtime.write_json(output / "pack200-build.json", {
        "buildJavaInfo": java8_info, "runtimeJava": 17, "command": command,
        "forgeSha256": runtime.file_hash(forge), "lzmaSha256": hashlib.sha256(compressed).hexdigest(),
        "packedSha256": runtime.file_hash(packed_path), "unpackedJarSha256": runtime.file_hash(unpacked),
        "genuineUnpack200ExecutableSha256": runtime.file_hash(java8_home / "bin/unpack200"), "entries": entries})
    # A real explicit helper library, resolved by unchanged stock LaunchClassLoader.
    helper_classes = output / "pack200-helper-classes"
    helper_classes.mkdir()
    helper_sources = sorted((PACKET / "bridge/pack200").glob("*.java"))
    with (output / "pack200-helper-compile.log").open("w") as log:
        subprocess.run([str(java.parent / "javac"), "--release", "8", "-d", str(helper_classes)] + [str(p) for p in helper_sources],
                       check=True, stdout=log, stderr=subprocess.STDOUT)
    helper = output / "pack200-runtime-helper.jar"
    subprocess.run([str(java.parent / "jar"), "cf", str(helper), "-C", str(helper_classes), "."], check=True)
    clean_env = {k:v for k,v in os.environ.items() if k not in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")}
    if not args.title_only:
        with (output / "pack200-entries-test.log").open("w") as log:
            subprocess.run([str(java), "-cp", str(helper), "dev.openallay.runtime.forge1122.pack200.Pack200Runtime",
                            str(packed_path), str(unpacked), runtime.file_hash(unpacked),
                            str(output / "pack200-entries-test.json")], check=True, env=clean_env,
                           stdout=log, stderr=subprocess.STDOUT)
    packed_path.unlink()
    cp.append(("dev.openallay.runtime:pack200-entry-bridge:current", helper))
    return ["-Dopenallay.pack200.enabled=true", "-Dopenallay.pack200.forge=" + str(forge),
            "-Dopenallay.pack200.jar=" + str(unpacked), "-Dopenallay.pack200.jarSha256=" + runtime.file_hash(unpacked),
            "-Dopenallay.pack200.transformReceipt=" + str(output / "pack200-transform.json"),
            "-Dopenallay.pack200.runtimeReceipt=" + str(output / "pack200-runtime.json")]


def prepare_launchwrapper_bridge(args, output, java, cp, runtime):
    # Compile only this tiny Java8 agent remotely. Stock ASM5.2 is both compiler/runtime API.
    sources = PACKET / "bridge"
    classes = output / "bridge-classes"
    classes.mkdir()
    asm = next(path for name, path in cp if name == "org.ow2.asm:asm-debug-all:5.2")
    wrapper = next(path for name, path in cp if name == "net.minecraft:launchwrapper:1.12")
    javac = java.parent / "javac"
    jar_tool = java.parent / "jar"
    source_files = sorted(sources.glob("*.java"))
    compile_command = [str(javac), "--release", "8", "-cp", str(asm), "-d", str(classes)] + [str(p) for p in source_files]
    runtime.write_json(output / "bridge-build.json", {
        "command": compile_command, "javaRelease": 8, "stockAsmSha256": runtime.file_hash(asm),
        "stockLaunchWrapperSha256": runtime.file_hash(wrapper),
        "sources": {p.name: runtime.file_hash(p) for p in source_files}})
    with (output / "bridge-compile.log").open("w") as compile_log:
        subprocess.run(compile_command, check=True, stdout=compile_log, stderr=subprocess.STDOUT)
    manifest = output / "bridge-manifest.mf"
    manifest.write_text("Manifest-Version: 1.0\nPremain-Class: dev.openallay.runtime.forge1122.LaunchWrapperJava17Bridge\n\n")
    agent = output / "launchwrapper-java17-bridge.jar"
    subprocess.run([str(jar_tool), "cfm", str(agent), str(manifest), "-C", str(classes), "."], check=True)
    test_cp = os.pathsep.join([str(agent)] + [str(path) for _, path in cp])
    test_main = "dev.openallay.runtime.forge1122.LaunchWrapperJava17BridgeTest"
    clean_env = {k:v for k,v in os.environ.items() if k not in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")}
    if args.objectholder_bridge:
        forge=next(path for name,path in cp if name=="net.minecraftforge:forge:1.12.2-14.23.5.2864")
        with (output / "objectholder-tests.log").open("w") as log:
            subprocess.run([str(java),"-cp",test_cp,"dev.openallay.runtime.forge1122.ObjectHolderBridgeTest",str(forge)],
                           check=True,env=clean_env,stdout=log,stderr=subprocess.STDOUT)
            fields=output / "objectholder-test-empty-fields.tsv";fields.write_text("")
            subprocess.run([str(java),"-cp",test_cp,"dev.openallay.runtime.forge1122.pack200.ObjectHolderRuntime",str(fields)],
                           check=True,env=clean_env,stdout=log,stderr=subprocess.STDOUT)
    elif args.title_only:
        runtime.write_json(output / "bridge-tests-reused.json", json.loads((PACKET / "prior-bridge-tests.json").read_text()))
    elif args.pack200_bridge:
        forge = next(path for name, path in cp if name == "net.minecraftforge:forge:1.12.2-14.23.5.2864")
        with (output / "pack200-tests.log").open("w") as log:
            subprocess.run([str(java), "-cp", test_cp, "dev.openallay.runtime.forge1122.Pack200BridgeTest", str(forge)],
                           check=True, env=clean_env, stdout=log, stderr=subprocess.STDOUT)
    else:
        with (output / "bridge-tests.log").open("w") as log:
            subprocess.run([str(java), "-cp", test_cp, test_main, str(wrapper)], check=True, env=clean_env,
                           stdout=log, stderr=subprocess.STDOUT)
            subprocess.run([str(java), "-Dopenallay.bridge.receipt=" + str(output / "bridge-constructor-test.json"),
                            "-javaagent:" + str(agent) + "=" + str(wrapper), "-cp", test_cp,
                            test_main, str(wrapper), "--constructor"], check=True, env=clean_env,
                           stdout=log, stderr=subprocess.STDOUT)
    return ["-Dopenallay.bridge.receipt=" + str(output / "bridge-runtime.json"),
            "-javaagent:" + str(agent) + "=" + str(wrapper)]


def boot(args, root, java, assets, runtime, launch, expected, vanilla, version):
    output = launch.safe_output(args.output, args.repo)
    runtime.require(not output.exists(), "Capture directory must be fresh")
    output.mkdir(parents=True)
    game = output / "game"
    (game / "mods").mkdir(parents=True)
    # Stock options only. No mod, hook, agent, resource pack, world or custom title renderer.
    (game / "options.txt").write_text("renderDistance:4\nmaxFps:15\npauseOnLostFocus:false\nguiScale:2\n")
    cp = classpath_libraries(vanilla, root, launch)
    fml = classpath_libraries(version, root, launch)
    replacements = {tuple(name.split(":")[:2]) for name, _ in fml}
    cp = [(name, path) for name, path in cp if tuple(name.split(":")[:2]) not in replacements] + fml
    # Legacy LaunchWrapper needs the original game JAR, not a fake alias or Gradle runtime.
    cp += [("com.mojang:minecraft:1.12.2:client", root / "versions/1.12.2/1.12.2.jar")]
    pack200_flags = prepare_pack200(args, output, java, cp, runtime) if args.pack200_bridge else []
    if args.title_only:
        # Real legacy LWJGL invokes xrandr -q. Keep the official display/native code unchanged.
        checked = subprocess.run(["xrandr", "-q"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                 text=True, timeout=15, check=False, env={**os.environ, "LC_ALL": "C"})
        (output / "xrandr-query.log").write_text(checked.stdout)
        runtime.require(checked.returncode == 0 and re.search(r"^\S+ connected ", checked.stdout, re.M),
                        "Actual Xvfb xrandr query must expose a connected display")
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
    bridge_flags = prepare_launchwrapper_bridge(args, output, java, cp, runtime) if args.launchwrapper_bridge else []
    objectholder_flags = (["-Dopenallay.objectholder.enabled=true",
        "-Dopenallay.objectholder.client=" + str(root / "versions/1.12.2/1.12.2.jar"),
        "-Dopenallay.objectholder.fields=" + str(output / "objectholder-fields.tsv"),
        "-Dopenallay.objectholder.rejected=" + str(output / "objectholder-rejected"),
        "-Dopenallay.objectholder.writes=" + str(output / "objectholder-writes.tsv"),
        "-Dopenallay.objectholder.metadata=" + str(output / "objectholder-metadata.tsv"),
        "-Dopenallay.objectholder.transformReceipt=" + str(output / "objectholder-transform.jsonl")]
        if args.objectholder_bridge else [])
    command = [str(java), "-Xms256M", "-Xmx1536M",
               "-Xlog:class+load=info:file=" + str(output / "class-load.log")] + bridge_flags + pack200_flags + objectholder_flags + (["-Dorg.lwjgl.util.Debug=true"] if args.title_only else []) + jvm + [MAIN] + game_args
    env = {k:v for k,v in os.environ.items() if k not in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")}
    if args.title_only:
        env["LC_ALL"] = "C"
    runtime.write_json(output / "launch.json", {"command": command, "classpath": classpath_receipts,
                       "natives": native_receipts, "noMods": True, "noEngineProbe": True,
                       "runtimeMode": "launchwrapper-and-pack200-bridge" if args.pack200_bridge else "launchwrapper-url-bridge" if args.launchwrapper_bridge else "raw-stock",
                       "publicInstrumentationAgent": args.launchwrapper_bridge})
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
    diagnostics = {"rawStockJava17": not args.launchwrapper_bridge, "titleConfirmed": False, "libraryReplacement": False,
                   "customClassLoader": False, "bootstrapApplied": args.launchwrapper_bridge,
                   "bridgeReceipt": "bridge-runtime.json" if args.launchwrapper_bridge else None,
                   "pack200BridgeApplied": args.pack200_bridge,
                   "launchWrapperAppLoaderCastFailure": "ClassCastException" in text and "URLClassLoader" in text,
                   "moduleAccessFailure": "InaccessibleObjectException" in text or "IllegalAccessError" in text,
                   "unsupportedClassVersion": "UnsupportedClassVersionError" in text,
                   "asmParseFailure": "ClassReader" in text and "IllegalArgumentException" in text,
                   "engineLoaded": False, "sdkAndBuilderTarget": 8, "futureEngineAndRhinoTarget": 17}
    if args.objectholder_bridge:
        writes=output / "objectholder-writes.tsv"
        diagnostics["objectHolderPopulatedReadbackCount"] = len(writes.read_text().splitlines()) if writes.exists() else 0
        diagnostics["objectHolderRegistryApplied"] = "Holder lookups applied" in text
    runtime.write_json(output / "diagnostics.json", diagnostics)
    if args.objectholder_bridge and (output / "objectholder-rejected").exists():
        receipt["status"]="fatal-rejected-objectholder-phase-input"
        runtime.write_json(output / "receipt.json",receipt)
    # A window or screenshot alone is not a title-success claim.
    return 0 if receipt["status"] == "captured-awaiting-title-review" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--objectholder-bridge", action="store_true", help="Exact real holder-field instrumentation plus genuine Field.set")
    parser.add_argument("--title-only", action="store_true", help="Use real xrandr display harness; reuse exact already-passed bridge source tests")
    parser.add_argument("--pack200-bridge", action="store_true", help="Opt-in genuine build-JDK8 Pack200 conversion, runtime17 entry seam")
    parser.add_argument("--launchwrapper-bridge", action="store_true", help="Opt-in exact LaunchWrapper1.12 URL seam instrumentation; stock libraries stay unchanged")
    parser.add_argument("--metadata-only", action="store_true", help="No network, Java or filesystem runtime action")
    parser.add_argument("--repo", type=Path)
    parser.add_argument("--java", type=Path)
    parser.add_argument("--java-release", default="17.0.18+8")
    parser.add_argument("--minecraft-root", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.objectholder_bridge:
        args.title_only = True
    if args.title_only:
        args.pack200_bridge = True
        prior = json.loads((PACKET / "prior-bridge-tests.json").read_text())
        for relative, expected in prior["sources"].items():
            if sha(PACKET / relative) != expected:
                raise ValueError("Prior bridge tests cannot cover changed source: " + relative)
    if args.pack200_bridge:
        args.launchwrapper_bridge = True
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
                           "titleConfirmed": False, "engineProbe": False,
                           "runtimeMode": "launchwrapper-and-pack200-bridge" if args.pack200_bridge else "launchwrapper-url-bridge" if args.launchwrapper_bridge else "raw-stock"})
        raise


if __name__ == "__main__":
    sys.exit(main())
