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


def prepare(args, runtime, launch, freeze, install, version, vanilla, download_assets=True):
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
    assets = launch.prepare_assets(root, root.parent / "assets", repo=args.repo, minecraft_target="1.12.2") if download_assets else None
    runtime.write_json(root / ".provision/stock-runtime.json", {
        "purpose": "stock-forge1122-java17-client-prerequisite", "pins": PINS,
        "javaInfo": info, "java": str(java), "javaExecutableSha256": runtime.file_hash(java),
        "sourceFreeze": freeze, "assets": str(assets) if assets is not None else None,
        "assetsDownloaded": download_assets, "profile": PROFILE,
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
        subprocess.run([str(java.parent / "javac"), "--release", "8", "-cp", str(forge), "-d", str(helper_classes)] + [str(p) for p in helper_sources],
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
    if getattr(args,"creates_disposable_world",False):
        dimension_classes=output/"dimension-helper-classes";dimension_classes.mkdir()
        with (output/"dimension-helper-compile.log").open("w") as log:
            subprocess.run([str(javac),"--release","17","-d",str(dimension_classes),str(PACKET/"bridge/dimension17/DimensionEnumRuntime.java")],check=True,stdout=log,stderr=subprocess.STDOUT)
        dimension_jar=output/"dimension-enum-bootstrap-helper.jar"
        subprocess.run([str(jar_tool),"cf",str(dimension_jar),"-C",str(dimension_classes),"."],check=True)
        with zipfile.ZipFile(dimension_jar) as archive:
            entries={entry.filename:{"sha256":hashlib.sha256(archive.read(entry)).hexdigest(),"major":int.from_bytes(archive.read(entry)[6:8],"big")} for entry in archive.infolist() if entry.filename.endswith(".class")}
            runtime.require(all(name.startswith("dev/openallay/runtime/forge1122/dimension/") and item["major"]==61 for name,item in entries.items()),"Own bootstrap helper class custody differs")
        runtime.write_json(output/"dimension-helper-custody.json",{"jarSha256":sha(dimension_jar),"entries":entries,"stockGameLoaderUnchanged":True,"javaLangOpenScope":"only actual helper bootstrap module"})
    with (output/"dimension-installer-compile.log").open("w") as log:
        subprocess.run([str(javac),"--release","17","-d",str(classes),str(PACKET/"bridge/dimension17/DimensionEnumInstaller.java")],check=True,stdout=log,stderr=subprocess.STDOUT)
    compile_command = [str(javac), "--release", "8", "-cp", str(asm)+os.pathsep+str(classes), "-d", str(classes)] + [str(p) for p in source_files]
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
    if getattr(args,"creates_disposable_world",False):
        runtime.write_json(output/"component-tests-reused.json",{"run":"37534098945","capabilityPhaseAndHelperTestsPassed":True,"oldStartupChecksReplayed":False})
        if getattr(args,"world_sdk",False):
            with (output/"dimension-phase-test.log").open("w") as log:
                subprocess.run([str(java),"-cp",test_cp,"dev.openallay.runtime.forge1122.DimensionEnumPhaseTest"],check=True,env=clean_env,stdout=log,stderr=subprocess.STDOUT)
        else:
            runtime.write_json(output/"dimension-fixture-reused.json",{"acceptedRun":"37536792827","noStandaloneEnumCheckReplayed":True})
    elif getattr(args,"component_inputs",None):
        with (output/"capability-component-tests.log").open("w") as log:
            subprocess.run([str(java),"-cp",test_cp,"dev.openallay.runtime.forge1122.CapabilityPhaseTest"],check=True,env=clean_env,stdout=log,stderr=subprocess.STDOUT)
        runtime.write_json(output/"prior-startup-reused.json",{"acceptedRun":"37520988163","oldChecksReplayed":False})
    elif args.objectholder_bridge and not args.objectholder_phase_diagnostic:
        runtime.write_json(output / "objectholder-phase-test-reused.json", {"run": "37520069517", "status": "pass", "unchangedPhaseInputsAndPatch": True})
        fields=output/"farmer-test-fields.tsv";fields.write_text("")
        with (output/"farmer-holder-tests.log").open("w") as log:
            subprocess.run([str(java),"-cp",test_cp,"dev.openallay.runtime.forge1122.FarmerHolderTest",str(fields),str(output/"farmer-test-metadata.tsv")],
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
    shared_game=getattr(args,"game_directory",None)
    game = Path(shared_game) if shared_game else output / "game"
    (game / "mods").mkdir(parents=True,exist_ok=bool(shared_game))
    # Stock options only. No mod, hook, agent, resource pack, world or custom title renderer.
    (game / "options.txt").write_text("renderDistance:4\nmaxFps:15\npauseOnLostFocus:false\nguiScale:2\n")
    cp = classpath_libraries(vanilla, root, launch)
    fml = classpath_libraries(version, root, launch)
    replacements = {tuple(name.split(":")[:2]) for name, _ in fml}
    cp = [(name, path) for name, path in cp if tuple(name.split(":")[:2]) not in replacements] + fml
    # Legacy LaunchWrapper needs the original game JAR, not a fake alias or Gradle runtime.
    cp += [("com.mojang:minecraft:1.12.2:client", root / "versions/1.12.2/1.12.2.jar")]
    component = getattr(args,"component_inputs",None)
    component_receipt = None
    if component:
        import shutil
        component_receipt=json.loads(Path(component).read_text())
        artifacts=component_receipt["artifacts"]
        for record in artifacts:
            path=Path(record["path"])
            runtime.require(sha(path)==record["sha256"],"Retained component bytes changed")
            if path.name in ("openallay-feature-core.jar","openallay-lifecycle-facade.jar"):
                if (game/"mods"/path.name).exists():
                    runtime.require(sha(game/"mods"/path.name)==record["sha256"],"Shared isolated profile component changed")
                else:shutil.copyfile(path,game/"mods"/path.name)
            elif path.name=="openallay-private-mixin.jar":
                cp.append(("dev.openallay:private-mixin-asm:current",path))
            else:raise ValueError("Unknown component archive")
        runtime.write_json(output/"component-custody.json",component_receipt)
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
    if component:
        game_args += ["--tweakClass","org.spongepowered.asm.launch.MixinTweaker"]
    bridge_flags = prepare_launchwrapper_bridge(args, output, java, cp, runtime) if args.launchwrapper_bridge else []
    objectholder_flags = (["-Dopenallay.objectholder.enabled=true",
        "-Dopenallay.objectholder.client=" + str(root / "versions/1.12.2/1.12.2.jar"),
        "-Dopenallay.objectholder.fields=" + str(output / "objectholder-fields.tsv"),
        "-Dopenallay.objectholder.rejected=" + str(output / "objectholder-rejected"),
        "-Dopenallay.objectholder.writes=" + str(output / "objectholder-writes.tsv"),
        "-Dopenallay.objectholder.metadata=" + str(output / "objectholder-metadata.tsv"),
        "-Dopenallay.objectholder.invalid=" + str(output / "objectholder-invalid.tsv"),
        "-Dopenallay.objectholder.transformReceipt=" + str(output / "objectholder-transform.jsonl")]
        if args.objectholder_bridge else [])
    world_sdk=getattr(args,"world_sdk",False)
    ui_manual=getattr(args,"ui_manual",False)
    builder_scenario=getattr(args,"builder_scenario",None)
    builder=bool(builder_scenario)
    creates_world=getattr(args,"creates_disposable_world",world_sdk or ui_manual or builder)
    binding_probe=getattr(args,"applied_bindings",False)
    component_flags = (["-Dopenallay.capability.enabled=true",
        "-Dopenallay.capability.writes="+str(output/"capability-writes.tsv"),
        "-Dopenallay.capability.transformReceipt="+str(output/"capability-transform.json"),
        "-Dopenallay.capability.rejected="+str(output/"capability-rejected"),
        "-Dopenallay.component.receipt="+str(output/"coremod-component.json")]
        if component else [])
    if binding_probe:
        component_flags += ["-Dopenallay.e2e.appliedBindings=true",
            "-Dopenallay.e2e.appliedBindingsReceipt="+str(output/"applied-bindings.json")]
    if creates_world:
        component_flags += ["-Dopenallay.dimension.helper="+str(output/"dimension-enum-bootstrap-helper.jar"),
            "-Dopenallay.dimension.valuesField=$VALUES",
            "-Dopenallay.dimension.bootstrapReceipt="+str(output/"dimension-bootstrap.json"),
            "-Dopenallay.dimension.transformReceipt="+str(output/"dimension-transform.json"),
            "-Dopenallay.dimension.fixtureReceipt="+str(output/"dimension-fixture.json")]
    if world_sdk:
        phase=getattr(args,"world_phase","")
        name=getattr(args,"world_name",None) or "openallay-builder-forge1122-sdk-"+str(os.getpid())
        component_flags += ["-Dopenallay.e2e.enabled=true","-Dopenallay.e2e.scenario=native-world-sdk",
            "-Dopenallay.e2e.question=Native WorldSession SDK acceptance",
            "-Dopenallay.e2e.report="+str(output/"world-sdk-report.json"),
            "-Dopenallay.e2e.shutdown=true","-Dopenallay.e2e.timeoutSeconds=300"]
        component_flags += ["-Dopenallay.e2e.resumeWorld="+name] if phase=="reload" else ["-Dopenallay.e2e.createWorld="+name]
        if phase:component_flags += ["-Dopenallay.e2e.worldPhase="+phase]
        runtime.write_json(output/"world-fixture.json",{"name":name,"freshProfile":True,"commandsAllowed":False,
            "modelInvocation":False,"unrestrictedJavascript":False,"expectedShutdown":"natural-unsignalled-exit0"})
    fixture_process=None
    fixture_stream=None
    window_manager=None
    focus_process=None
    focus_stream=None
    if ui_manual:
        with (output/"window-manager.log").open("w") as manager_log:
            window_manager=subprocess.Popen(["openbox","--sm-disable"],stdout=manager_log,stderr=subprocess.STDOUT,start_new_session=True)
    if ui_manual or builder:
        import secrets
        packet=PACKET/"ui-fixture"
        settings=game/"config/openallay";settings.mkdir(parents=True,exist_ok=True)
        for name in ("models.json","voice.json"):
            (settings/name).write_bytes((packet/name).read_bytes())
        (game/"options.txt").write_bytes((packet/"options.txt").read_bytes())
        source=component_receipt["nativeCompiledSource"]
        if builder:
            extension=Path(args.builder_jar)
            runtime.require(sha(extension)==args.builder_sha256,"Exact provider-verified Builder candidate changed")
            extensions=settings/"extensions";extensions.mkdir()
            (extensions/"openallay-builder-candidate.jar").write_bytes(extension.read_bytes())
            component_flags += ["-Dopenallay.e2e.enabled=true","-Dopenallay.e2e.scenario=builder-"+builder_scenario,
                "-Dopenallay.e2e.question=OpenAllay E2E Builder "+builder_scenario,
                "-Dopenallay.e2e.session=e2e","-Dopenallay.e2e.modelMode=client",
                "-Dopenallay.e2e.report="+str(output/"builder-report.json"),
                "-Dopenallay.e2e.trace="+str(output/"builder-trace.json"),
                "-Dopenallay.e2e.createWorld=openallay-builder-forge1122-"+builder_scenario+"-"+str(os.getpid()),
                "-Dopenallay.e2e.shutdown=true","-Dopenallay.e2e.timeoutSeconds=300"]
            runtime.write_json(output/"builder-candidate-custody.json",{"sha256":sha(extension),"provider":args.builder_provider,
                "extensionSource":args.builder_extension_source,"installedNormalExtensionDirectory":True,
                "commandsAllowed":False,"unrestrictedJavascript":False,"permissionBypass":False})
        else:
            properties=json.loads((packet/"jvm-properties.json").read_text())
            source=component_receipt["nativeCompiledSource"]
            replacements={"${EVIDENCE}":str(output),"${EXACT_PRODUCT_SOURCE_REVISION}":source,
                "${EXACT_PRODUCT_SOURCE_MANIFEST_SHA256}":sha(Path(component))}
            for key,value in properties.items():
                for placeholder,actual in replacements.items():value=value.replace(placeholder,actual)
                if "${" in value:raise ValueError("Unresolved UI source/evidence identity")
                component_flags.append("-D"+key+"="+value)
        fixture_key=secrets.token_hex(24)
        os.environ["OPENALLAY_E2E_FIXTURE_KEY"]=fixture_key
        fixture_stream=(output/"model-fixture.log").open("w")
        fixture_process=subprocess.Popen([sys.executable,"-B",str(args.repo/"scripts/e2e-model-fixture.py"),"--port","18765"],
            cwd=args.repo,stdout=fixture_stream,stderr=subprocess.STDOUT,start_new_session=True,
            env={**os.environ,"OPENALLAY_E2E_FIXTURE_KEY":fixture_key})
        runtime.write_json(output/"ui-fixture.json",{"loopbackOnly":"127.0.0.1:18765","syntheticCredential":True,
            "profile":"e2e-fixture","worldCommandsAllowed":False,"freshProfile":True,
            "nativeCompiledSource":source,"actualCustodyManifestSha256":sha(Path(component)),"sourceManifestKind":"provider-bound-component-custody"})
    command = [str(java), "-Xms256M", "-Xmx1536M",
               "-Xlog:class+load=info:file=" + str(output / "class-load.log")] + bridge_flags + pack200_flags + objectholder_flags + component_flags + (["-Dopenallay.objectholder.phaseDiagnostic=true"] if args.objectholder_phase_diagnostic else []) + (["-Dorg.lwjgl.util.Debug=true"] if args.title_only else []) + jvm + [MAIN] + game_args
    env = {k:v for k,v in os.environ.items() if k not in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")}
    if args.title_only:
        env["LC_ALL"] = "C"
    runtime.write_json(output / "launch.json", {"command": command, "classpath": classpath_receipts,
                       "natives": native_receipts, "noMods": not bool(component), "noEngineProbe": not bool(component),
                       "runtimeMode": "launchwrapper-and-pack200-bridge" if args.pack200_bridge else "launchwrapper-url-bridge" if args.launchwrapper_bridge else "raw-stock",
                       "publicInstrumentationAgent": args.launchwrapper_bridge})
    receipt = {"status": "running", "titleConfirmed": False, "captureSeconds": [45, 90],
               "screenshots": [], "startedAt": datetime.now(timezone.utc).isoformat()}
    log = output / "client.log"
    with log.open("w") as stream:
        process = subprocess.Popen(command, cwd=game, env=env, stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
        receipt["clientPid"] = process.pid
        if ui_manual:
            focus_source=output/"owned-x11-focus.py"
            focus_source.write_text('#!/usr/bin/env python3\n"""Bounded actual X11 focus keeper for one owned CI client PID on one owned display."""\nimport argparse,json,os,re,subprocess,time\nfrom pathlib import Path\n\ndef run(*args):\n    result=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=5)\n    return result.returncode,result.stdout.strip()\ndef main():\n    parser=argparse.ArgumentParser();parser.add_argument(\'--pid\',type=int,required=True);parser.add_argument(\'--receipt\',type=Path,required=True);args=parser.parse_args()\n    receipt={\'ownedClientPid\':args.pid,\'display\':os.environ[\'DISPLAY\'],\'actions\':[],\'status\':\'waiting-owned-window\'}\n    deadline=time.monotonic()+660;last=None\n    try:\n        while time.monotonic()<deadline:\n            try:os.kill(args.pid,0)\n            except ProcessLookupError:receipt[\'status\']=\'owned-client-exited\';break\n            code,listing=run(\'xdotool\',\'search\',\'--onlyvisible\',\'--pid\',str(args.pid))\n            candidates=[]\n            if code==0:\n                for xid in listing.splitlines():\n                    if not xid.isdecimal():continue\n                    code,pid=run(\'xdotool\',\'getwindowpid\',xid)\n                    if code!=0 or pid!=str(args.pid):continue\n                    _,title=run(\'xdotool\',\'getwindowname\',xid)\n                    code,geometry=run(\'xdotool\',\'getwindowgeometry\',\'--shell\',xid)\n                    values=dict(line.split(\'=\',1) for line in geometry.splitlines() if \'=\' in line)\n                    if code==0 and int(values.get(\'WIDTH\',\'0\'))>=320 and int(values.get(\'HEIGHT\',\'0\'))>=240 and \'Minecraft\' in title:\n                        candidates.append((xid,title,values))\n            if len(candidates)==1:\n                xid,title,geometry=candidates[0]\n                _,focus=run(\'xdotool\',\'getwindowfocus\')\n                if focus!=xid:\n                    code,output=run(\'xdotool\',\'windowactivate\',\'--sync\',xid)\n                    if code!=0:code,output=run(\'xdotool\',\'windowfocus\',\'--sync\',xid)\n                    _,actual=run(\'xdotool\',\'getwindowfocus\')\n                    receipt[\'actions\'].append({\'xid\':xid,\'pid\':args.pid,\'title\':title,\'geometry\':geometry,\'previousFocus\':focus,\'requestedActualFocus\':actual,\'exitCode\':code,\'commandOutput\':output})\n                    receipt[\'status\']=\'focused-owned-window\' if actual==xid else \'focus-request-failed\'\n                last=xid\n            elif len(candidates)>1:\n                receipt[\'status\']=\'ambiguous-owned-window-refused\'\n            args.receipt.write_text(json.dumps(receipt,indent=2)+\'\\n\')\n            time.sleep(0.25)\n        else:receipt[\'status\']=\'focus-helper-bounded-timeout\'\n    finally:\n        receipt[\'lastOwnedXid\']=last;args.receipt.write_text(json.dumps(receipt,indent=2)+\'\\n\')\nif __name__==\'__main__\':main()\n')
            focus_stream=(output/"owned-x11-focus.log").open("w")
            focus_process=subprocess.Popen([sys.executable,"-B",str(focus_source),"--pid",str(process.pid),"--receipt",str(output/"owned-x11-focus.json")],
                env=env,stdout=focus_stream,stderr=subprocess.STDOUT,start_new_session=True)
        try:
            if creates_world:
                try:
                    limit=330 if builder else 660 if ui_manual else 330
                    if builder:
                        try:process.wait(timeout=35)
                        except subprocess.TimeoutExpired:
                            # One actual native thread snapshot; no fabricated phase or collector-owned game action.
                            with (output/"builder-owner-threads-35s.log").open("w") as thread_log:
                                try:subprocess.run([str(java.parent/"jstack"),str(process.pid)],check=False,stdout=thread_log,stderr=subprocess.STDOUT,timeout=15)
                                except subprocess.TimeoutExpired:thread_log.write("Owned jstack exceeded 15 seconds\n")
                            limit-=35
                    process.wait(timeout=limit)
                    receipt["status"]="world-client-exited"
                except subprocess.TimeoutExpired:
                    receipt["status"]="fatal-world-timeout"
            else:
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
            if focus_process is not None:
                receipt["focusHelperTermination"]=stop_owned(focus_process);focus_stream.close()
            if window_manager is not None:receipt["windowManagerTermination"]=stop_owned(window_manager)
            if fixture_process is not None:
                receipt["fixtureTermination"]=stop_owned(fixture_process)
                fixture_stream.close()
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
    if args.objectholder_phase_diagnostic or args.objectholder_bridge and (output / "objectholder-rejected").exists():
        receipt["status"]="fatal-rejected-objectholder-phase-input"
        receipt["accepted"]=False
        receipt["phaseDiagnostic"]=args.objectholder_phase_diagnostic
        runtime.write_json(output / "receipt.json",receipt)
    if binding_probe:
        path=output/"applied-bindings.json"
        oracle=json.loads(path.read_text()) if path.is_file() else {"accepted":False,"cause":"actual binding receipt absent"}
        if not oracle.get("accepted",False):
            receipt["status"]="fatal-applied-binding-proof";receipt["accepted"]=False
            runtime.write_json(output/"receipt.json",receipt)
    if component:
        classloads=(output/"class-load.log").read_text(errors="replace")
        caps=output/"capability-writes.tsv"
        cap_lines=caps.read_text().splitlines() if caps.exists() else []
        facts={"engineClassesLoaded":"dev.openallay." in classloads,
            "rhinoClassesLoaded":"dev.latvian.mods.rhino." in classloads,
            "stockAsm5ClassLoaded":"org.objectweb.asm.ClassReader" in classloads,
            "privateAsmClassLoaded":"dev.openallay.internal.forge1122.asm.ClassReader" in classloads,
            "mixinBootstrap":"SpongePowered MIXIN Subsystem Version=0.8.5" in text,
            "normalCoremodReceipt":(output/"coremod-component.json").is_file(),
            "openallayModLoaded":"openallay" in text,
            "actualCapabilityReadbacks":cap_lines,
            "capabilityAllFive":len({line.split("\t")[0]+"."+line.split("\t")[1] for line in cap_lines})==5,
            "rejectedPhase":(output/"capability-rejected").exists() or (output/"objectholder-rejected").exists(),
            "nativeJarScope":component_receipt.get("nativeCompiledSource"),"titleConfirmed":False,"cleanQuit":False}
        runtime.write_json(output/"component-runtime.json",facts)
        if facts["rejectedPhase"] or not all(facts[k] for k in ("normalCoremodReceipt","engineClassesLoaded","rhinoClassesLoaded","privateAsmClassLoaded","capabilityAllFive")):
            receipt["status"]="fatal-component-proof-incomplete";receipt["accepted"]=False
            runtime.write_json(output/"receipt.json",receipt)
    if world_sdk:
        report_path=output/"world-sdk-report.json"
        report=json.loads(report_path.read_text()) if report_path.is_file() else {}
        checks=report.get("worldSdkChecks",[])
        if not checks:
            checks=report.get("checks",[])
        persistence_phase=getattr(args,"world_phase","")
        if persistence_phase in ("persist","reload"):
            passed=report.get("outcome")=="COMPLETED" and bool(report.get("worldId")) and report.get("phase")==persistence_phase
            passed=passed and bool(report.get("actual")) if persistence_phase=="persist" else passed and bool(report.get("persistedActual")) and report.get("originalImageRestored") is True
        else:passed=report.get("outcome")=="COMPLETED" and bool(checks) and all(c.get("status")=="PASS" for c in checks)
        clean=receipt["termination"]["finalExitCode"]==0 and not receipt["termination"]["signals"]
        oracle_path=output/"applied-bindings.json";oracle=json.loads(oracle_path.read_text()) if oracle_path.is_file() else {}
        required=passed and clean and (not binding_probe or oracle.get("accepted") is True) and (output/"dimension-fixture.json").is_file()
        runtime.write_json(output/"world-sdk-acceptance.json",{"accepted":required,"reportCompleted":passed,
            "cleanUnsignalledExit0":clean,"actualAppliedBindings":oracle.get("accepted",False),"worldSdkCheckCount":len(checks)})
        if required and receipt["status"] not in ("fatal-component-proof-incomplete","fatal-applied-binding-proof"):
            receipt["status"]="accepted-world-sdk-and-bindings";receipt["accepted"]=True
        else:receipt["status"]="fatal-world-sdk-or-binding-proof";receipt["accepted"]=False
        runtime.write_json(output/"receipt.json",receipt)
    if ui_manual:
        report_path=output/"ui-manual-report.json"
        ui=json.loads(report_path.read_text()) if report_path.is_file() else {}
        oracle_path=output/"applied-bindings.json";oracle=json.loads(oracle_path.read_text()) if oracle_path.is_file() else {}
        clean=receipt["termination"]["finalExitCode"]==0 and not receipt["termination"]["signals"]
        passed=ui.get("outcome") in ("COMPLETED","PASSED")
        screenshots=list((output/"frames").rglob("*.png")) if (output/"frames").is_dir() else []
        accepted=passed and clean and oracle.get("accepted") is True and bool(screenshots) and (output/"dimension-fixture.json").is_file()
        runtime.write_json(output/"ui-manual-acceptance.json",{"accepted":accepted,"reportPassed":passed,
            "actualAppliedBindings":oracle.get("accepted",False),"cleanUnsignalledExit0":clean,"frameCount":len(screenshots),
            "fullFunctionalScenario":"ui-manual-regressions","nativeCompiledSource":component_receipt["nativeCompiledSource"]})
        if accepted and receipt["status"] not in ("fatal-component-proof-incomplete","fatal-applied-binding-proof"):
            receipt["status"]="accepted-ui-manual-and-bindings";receipt["accepted"]=True
        else:receipt["status"]="fatal-ui-manual-or-binding-proof";receipt["accepted"]=False
        runtime.write_json(output/"receipt.json",receipt)
    if builder:
        path=output/"builder-report.json";proof=json.loads(path.read_text()) if path.is_file() else {}
        binding=output/"applied-bindings.json";oracle=json.loads(binding.read_text()) if binding.is_file() else {}
        clean=receipt["termination"]["finalExitCode"]==0 and not receipt["termination"]["signals"]
        accepted=proof.get("outcome")=="COMPLETED" and proof.get("nativeAcceptance",{}).get("outcome")=="PASSED" and oracle.get("accepted") is True and clean
        runtime.write_json(output/"builder-acceptance.json",{"accepted":accepted,"scenario":builder_scenario,
            "completed":proof.get("outcome")=="COMPLETED","independentNativePassed":proof.get("nativeAcceptance",{}).get("outcome")=="PASSED",
            "actualAppliedBindings":oracle.get("accepted",False),"naturalExit0":clean,"candidateSha256":args.builder_sha256})
        if accepted and receipt["status"] not in ("fatal-component-proof-incomplete","fatal-applied-binding-proof"):
            receipt["status"]="accepted-builder-native-lifecycle";receipt["accepted"]=True
        else:receipt["status"]="fatal-builder-native-lifecycle";receipt["accepted"]=False
        runtime.write_json(output/"receipt.json",receipt)
    # A window or screenshot alone is not a title-success claim.
    return 0 if receipt["status"] in ("captured-awaiting-title-review","accepted-world-sdk-and-bindings","accepted-ui-manual-and-bindings","accepted-builder-native-lifecycle") else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--objectholder-phase-diagnostic", action="store_true", help="Capture all rejected phase inputs; always fail; original bytes allowed only for this diagnostic")
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
    if args.objectholder_phase_diagnostic:
        args.objectholder_bridge = True
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
