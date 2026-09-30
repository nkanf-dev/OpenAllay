#!/usr/bin/env python3
"""Prepare, then explicitly launch a packaged Builder acceptance client.

Development harness only. No Gradle source classes, existing saves, credentials,
network downloads, or launcher account files are used. See --help.
"""

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
import hashlib
from http.client import HTTPException
import io
import json
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import subprocess
import sys
import uuid
from urllib.parse import urlsplit
from urllib.request import urlopen
import zipfile

MC_VERSION = "26.2"
FABRIC_LOADER = "0.19.3"
NEOFORGE_VERSION = "26.2.0.25-beta"
MOD_VERSION = "0.2.3"
WORLD_PREFIX = "openallay-builder-"
SCENARIOS = ("builder-disabled", "builder-acceptance", "builder-reload",
             "builder-partial", "builder-cancel", "builder-live", "builder-live-copy", "builder-live-undo")
REPO = Path(__file__).resolve().parents[1]


def digest(path):
    return digest_with(path, "sha256")


def digest_with(path, algorithm):
    result = hashlib.new(algorithm)
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def system_name():
    return {"Darwin": "osx", "Linux": "linux", "Windows": "windows"}[platform.system()]


def offline_uuid(name):
    data = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode("utf-8")).digest())
    data[6] = (data[6] & 0x0f) | 0x30
    data[8] = (data[8] & 0x3f) | 0x80
    return uuid.UUID(bytes=bytes(data)).hex


def proxy_arguments(enabled):
    if not enabled:
        return []
    # Read only conventional explicit proxy settings. No environment dump or
    # credential-bearing URL is ever accepted or included in JVM arguments.
    endpoint = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy") or os.environ.get("HTTP_PROXY") or os.environ.get("http_proxy")
    if not endpoint:
        raise ValueError("--http-proxy-from-env requires a conventional HTTP(S) proxy environment variable")
    parsed = urlsplit(endpoint)
    if parsed.scheme not in ("http", "https") or parsed.hostname not in ("localhost", "127.0.0.1", "::1") or not parsed.port:
        raise ValueError("Acceptance Java proxy must be an explicit local HTTP(S) proxy")
    if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
        raise ValueError("Acceptance Java proxy must not contain credentials or path data")
    return ["-Dhttp.proxyHost=" + parsed.hostname, "-Dhttp.proxyPort=" + str(parsed.port),
            "-Dhttps.proxyHost=" + parsed.hostname, "-Dhttps.proxyPort=" + str(parsed.port),
            "-Dhttp.nonProxyHosts=localhost|127.*|[::1]"]


def rules_allow(rules, features=None, os_name=None, arch=None):
    if not rules:
        return True
    features = features or {}
    os_name = os_name or system_name()
    arch = arch or platform.machine()
    allowed = False
    for rule in rules:
        os_rule = rule.get("os", {})
        matches = (not os_rule.get("name") or os_rule["name"] == os_name)
        matches = matches and (not os_rule.get("arch") or re.fullmatch(os_rule["arch"], arch) is not None)
        matches = matches and (not os_rule.get("version") or re.search(os_rule["version"], platform.release()) is not None)
        matches = matches and all(features.get(key, False) == value for key, value in rule.get("features", {}).items())
        if matches:
            allowed = rule["action"] == "allow"
    return allowed


def expand_arguments(arguments, values, features=None):
    result = []
    for entry in arguments:
        if isinstance(entry, dict):
            if not rules_allow(entry.get("rules"), features):
                continue
            entry = entry["value"]
        for argument in ([entry] if isinstance(entry, str) else entry):
            def substitute(match):
                key = match.group(1)
                if key not in values:
                    raise ValueError("Unsupported launch metadata placeholder: " + key)
                return str(values[key])
            result.append(re.sub(r"\$\{([^}]+)\}", substitute, argument))
    return result


def read_version(minecraft_root, name):
    path = minecraft_root / "versions" / name / (name + ".json")
    if not path.is_file():
        raise ValueError("Required locally installed Minecraft version metadata is missing: " + name)
    return json.loads(path.read_text(encoding="utf-8"))


def maven_path(coordinate):
    group, artifact, version, *classifier = coordinate.split(":")
    filename = artifact + "-" + version + ("-" + classifier[0] if classifier else "") + ".jar"
    return Path(group.replace(".", "/")) / artifact / version / filename


def cached_library(coordinate, minecraft_root, gradle_cache):
    official = minecraft_root / "libraries" / maven_path(coordinate)
    if official.is_file():
        return official.resolve()
    group, artifact, version, *classifier = coordinate.split(":")
    filename = maven_path(coordinate).name
    matches = list((gradle_cache / group / artifact / version).glob("*/" + filename))
    if len(matches) != 1:
        raise ValueError("Required local runtime library is missing or ambiguous: " + coordinate)
    return matches[0].resolve()


def version_libraries(metadata, minecraft_root, gradle_cache):
    result = []
    for library in metadata.get("libraries", []):
        if not rules_allow(library.get("rules")):
            continue
        artifact = library.get("downloads", {}).get("artifact")
        if artifact is None:
            raise ValueError("Unsupported installed library metadata: " + library["name"])
        path = (minecraft_root / "libraries" / artifact["path"]).resolve()
        if not path.is_file():
            path = cached_library(library["name"], minecraft_root, gradle_cache)
        if artifact.get("sha1") and digest_with(path, "sha1") != artifact["sha1"]:
            raise ValueError("Local runtime library hash does not match installed metadata: " + library["name"])
        result.append((library["name"], path))
    return result


def verified_download(url, destination, expected_sha1, expected_size):
    parsed = urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname not in ("piston-meta.mojang.com", "resources.download.minecraft.net"):
        raise ValueError("Runtime downloads must use official Mojang asset hosts")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.name + ".download-" + uuid.uuid4().hex)
    try:
        last_failure = None
        for attempt in range(6):
            try:
                with urlopen(url, timeout=120) as response, temporary.open("wb") as stream:
                    shutil.copyfileobj(response, stream)
                if temporary.stat().st_size != expected_size or digest_with(temporary, "sha1") != expected_sha1:
                    raise ValueError("Official Minecraft asset download failed hash or size verification")
                temporary.replace(destination)
                return
            except (OSError, HTTPException) as failure:
                last_failure = failure
                temporary.unlink(missing_ok=True)
        raise ValueError("Official Minecraft asset fetch failed after retries: " + type(last_failure).__name__)
    finally:
        temporary.unlink(missing_ok=True)


def prepare_assets(minecraft_root, destination, repo=REPO):
    """Reuse verified installed object hashes; fetch only missing official assets."""
    destination = safe_output(destination, repo)
    version = read_version(minecraft_root, MC_VERSION)
    index_meta = version["assetIndex"]
    index = destination / "indexes" / (index_meta["id"] + ".json")
    if not index.is_file() or digest_with(index, "sha1") != index_meta["sha1"]:
        verified_download(index_meta["url"], index, index_meta["sha1"], index_meta["size"])
    objects = json.loads(index.read_text(encoding="utf-8"))["objects"]
    unique = {item["hash"]: item["size"] for item in objects.values()}
    if any(not re.fullmatch(r"[0-9a-f]{40}", sha1) for sha1 in unique):
        raise ValueError("Official asset index contains an invalid object hash")
    needed = []
    reused = 0
    for sha1, size in unique.items():
        target = destination / "objects" / sha1[:2] / sha1
        if target.is_file() and target.stat().st_size == size and digest_with(target, "sha1") == sha1:
            reused += 1
            continue
        source = minecraft_root / "assets/objects" / sha1[:2] / sha1
        if source.is_file() and source.stat().st_size == size and digest_with(source, "sha1") == sha1:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            reused += 1
        else:
            needed.append((sha1, size, target))
    print(f"Official asset index{index_meta['id']}: {reused} verified cached objects; "
          f"{len(needed)} missing objects ({sum(size for _, size, _ in needed)} bytes).", flush=True)
    def fetch(item):
        sha1, size, target = item
        verified_download("https://resources.download.minecraft.net/" + sha1[:2] + "/" + sha1,
                          target, sha1, size)
    with ThreadPoolExecutor(max_workers=12) as executor:
        futures = [executor.submit(fetch, item) for item in needed]
        for completed, future in enumerate(as_completed(futures), 1):
            future.result()
            if completed % 100 == 0:
                print(f"Verified official assets: {completed}/{len(needed)} downloaded.", flush=True)
    write_json(destination / "verified.json", {"minecraft": MC_VERSION, "assetIndex": index_meta["id"],
                                               "indexSha1": index_meta["sha1"], "objectCount": len(unique),
                                               "allObjectsVerified": True})
    print("Verified Minecraft26.2 assets ready: " + str(destination), flush=True)
    return destination


def packaged_artifact(path, loader):
    path = path.resolve()
    expected_name = f"openallay-{loader}-{MC_VERSION}-{MOD_VERSION}.jar"
    if path.name != expected_name or not path.is_file():
        raise ValueError("Use the default built OpenAllay production artifact: " + expected_name)
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if loader == "fabric":
            metadata = json.loads(archive.read("fabric.mod.json"))
            if metadata["id"] != "openallay" or metadata["version"] != MOD_VERSION:
                raise ValueError("Unexpected packaged OpenAllay identity")
            nested = [item["file"] for item in metadata.get("jars", []) if "openallay-builder-" in item["file"]]
        else:
            mod_metadata = archive.read("META-INF/neoforge.mods.toml").decode("utf-8")
            if (re.search(r'^modId\s*=\s*"openallay"', mod_metadata, re.MULTILINE) is None
                    or re.search(r'^version\s*=\s*"' + re.escape(MOD_VERSION) + r'"', mod_metadata, re.MULTILINE) is None):
                raise ValueError("Unexpected packaged OpenAllay identity")
            metadata = json.loads(archive.read("META-INF/jarjar/metadata.json"))
            nested = [item["path"] for item in metadata.get("jars", []) if "openallay-builder-" in item["path"]]
        if len(nested) != 1:
            raise ValueError("Default artifact must include exactly one nested Builder Extension")
        nested_bytes = archive.read(nested[0])
        nested_sha256 = hashlib.sha256(nested_bytes).hexdigest()
        with zipfile.ZipFile(io.BytesIO(nested_bytes)) as builder:
            resources = builder.namelist()
            if "assets/openallay_builder/building.js" not in resources:
                raise ValueError("Nested Builder has no building module")
        bootstrap = "dev/openallay/guide/e2e/GuideClientE2EController.class"
        if bootstrap not in names:
            raise ValueError("Packaged artifact does not contain the opt-in E2E controller")
        instrumented = b"openallay.e2e.createWorld" in archive.read(bootstrap)
    return {"name": path.name, "sha256": digest(path), "nestedBuilder": nested[0],
            "nestedBuilderSha256": nested_sha256, "loader": loader, "minecraft": MC_VERSION,
            "modVersion": MOD_VERSION, "kind": "acceptance-instrumented" if instrumented else "production-before-bootstrap",
            "nativeWorldBootstrapPresent": instrumented}


def validate_model_config(source):
    config = json.loads(Path(source).read_text(encoding="utf-8"))
    if config.get("schemaVersion") != 2 or not config.get("profiles"):
        raise ValueError("An explicit schema-2 model profile is required")
    for profile in config["profiles"]:
        credential = profile.get("credentialRef", "")
        if not re.fullmatch(r"env:[A-Za-z_][A-Za-z0-9_]*", credential):
            raise ValueError("Acceptance profile credentials must use environment references only")
        endpoint = urlsplit(profile.get("baseUrl", ""))
        if endpoint.scheme not in ("http", "https") or not endpoint.hostname:
            raise ValueError("Acceptance profile needs an explicit HTTP(S) endpoint")
        if endpoint.username or endpoint.password or endpoint.query or endpoint.fragment:
            raise ValueError("Acceptance endpoint must not contain credentials, queries, or fragments")
        if any(key.lower() in ("apikey", "token", "password", "secret") for key in profile):
            raise ValueError("Acceptance profile must not contain embedded secrets")
    return config


def fixture_model_config(port):
    return {"schemaVersion": 2, "defaultProfileId": "e2e-fixture", "profiles": [{
        "id": "e2e-fixture", "displayName": "OpenAllay E2E Fixture", "enabled": True,
        "protocol": "openai_chat", "baseUrl": f"http://127.0.0.1:{port}/v1/",
        "model": "openallay-e2e-fixture", "credentialRef": "env:OPENALLAY_E2E_FIXTURE_KEY",
        "contextWindowTokens": 256000, "maxOutputTokens": 8192,
        "connectTimeoutSeconds": 10, "requestTimeoutSeconds": 120}]}


def write_json(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def safe_output(path, repo=REPO):
    path = path.resolve()
    root = (repo / "build/e2e").resolve()
    if not path.is_relative_to(root):
        raise ValueError("All acceptance output must be under ignored build/e2e")
    return path


def prepare(args, repo=REPO):
    loader = args.loader
    run_id = args.run_id or datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:6]
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,90}", run_id):
        raise ValueError("run-id must be a short filesystem-safe identifier")
    if args.scenario not in SCENARIOS:
        raise ValueError("Unknown Builder scenario")
    if args.timeout_seconds <= 0:
        raise ValueError("Harness timeout must be a positive number of seconds")
    if args.scenario == "builder-live-undo":
        raise ValueError("Live undo must resume a reviewed prior builder-live-copy disposable world")
    if args.scenario == "builder-disabled" and args.enable_unrestricted:
        raise ValueError("Disabled scenario cannot enable unrestricted JavaScript")
    if args.scenario != "builder-disabled" and not args.enable_unrestricted:
        raise ValueError("Enabled scenarios require explicit --enable-unrestricted for this disposable game directory")
    if args.scenario.startswith("builder-live") and (not args.question or not args.model_config):
        raise ValueError("Live acceptance requires an explicit ordinary provider question and environment-reference model config")
    if args.revoke_unrestricted_after_capture and args.scenario != "builder-acceptance":
        raise ValueError("Frozen-authority probe applies only to builder-acceptance")
    output = safe_output(repo / "build/e2e/packaged-builder" / loader / run_id, repo)
    if output.exists():
        raise ValueError("Acceptance directory already exists; use a new run-id")
    mcroot = args.minecraft_root.resolve()
    gradle_cache = args.gradle_cache.resolve()
    vanilla = read_version(mcroot, MC_VERSION)
    if vanilla.get("javaVersion", {}).get("majorVersion") != 25:
        raise ValueError("Minecraft26.2 runtime metadata must require Java25")
    artifact = args.jar or repo / loader / "build/libs" / f"openallay-{loader}-{MC_VERSION}-{MOD_VERSION}.jar"
    identity = packaged_artifact(artifact, loader)
    models = validate_model_config(args.model_config) if args.model_config else fixture_model_config(args.fixture_port)
    libraries = version_libraries(vanilla, mcroot, gradle_cache)
    extra_jvm, extra_game, loader_files = [], [], []
    if loader == "fabric":
        loader_jar = cached_library(f"net.fabricmc:fabric-loader:{FABRIC_LOADER}", mcroot, gradle_cache)
        with zipfile.ZipFile(loader_jar) as archive:
            installer = json.loads(archive.read("fabric-installer.json"))
        loader_files = [loader_jar]
        for library in installer["libraries"]["common"] + installer["libraries"].get("client", []):
            path = cached_library(library["name"], mcroot, gradle_cache)
            if library.get("sha1") and digest_with(path, "sha1") != library["sha1"]:
                raise ValueError("Fabric runtime library does not match loader installer metadata: " + library["name"])
            loader_files.append(path)
        main_class = installer["mainClass"]["client"]
        game_jar = mcroot / "versions" / MC_VERSION / (MC_VERSION + ".jar")
        if not game_jar.is_file():
            raise ValueError("Official installed Minecraft client JAR is missing")
        loader_files.append(game_jar.resolve())
        api = args.fabric_api or repo / "fabric/runs/client/mods/fabric-api-0.155.2+26.2.jar"
        if not api.is_file():
            raise ValueError("A locally installed Fabric API26.2 JAR is required")
        with zipfile.ZipFile(api) as archive:
            apimeta = json.loads(archive.read("fabric.mod.json"))
            if apimeta.get("id") != "fabric-api" or "+26.2" not in apimeta.get("version", ""):
                raise ValueError("Use Fabric API for Minecraft26.2")
    else:
        neo = read_version(mcroot, "neoforge-" + NEOFORGE_VERSION)
        if neo.get("inheritsFrom") != MC_VERSION:
            raise ValueError("Installed NeoForge profile does not inherit Minecraft26.2")
        replacements = {name.split(":")[0] + ":" + name.split(":")[1] for name, _ in version_libraries(neo, mcroot, gradle_cache)}
        libraries = [(name, path) for name, path in libraries if ":".join(name.split(":")[:2]) not in replacements]
        loader_files = [path for _, path in version_libraries(neo, mcroot, gradle_cache)]
        main_class = neo["mainClass"]
        extra_jvm = neo["arguments"].get("jvm", [])
        extra_game = neo["arguments"].get("game", [])
        for coordinate in (f"net.neoforged:minecraft-client-patched:{NEOFORGE_VERSION}",
                           f"net.neoforged:neoforge:{NEOFORGE_VERSION}:universal"):
            if not (mcroot / "libraries" / maven_path(coordinate)).is_file():
                raise ValueError("Required locally installed NeoForge production artifact is missing: " + coordinate)
    classpath = list(dict.fromkeys(str(path) for _, path in libraries))
    classpath += [str(path) for path in loader_files if str(path) not in classpath]
    if any(Path(path).is_relative_to(repo.resolve()) for path in classpath):
        raise ValueError("Packaged launch cannot include project source classes or Gradle game artifacts")
    assets_root = args.assets_root or repo / "build/e2e/runtime/assets"
    if not (assets_root / "indexes" / (vanilla["assetIndex"]["id"] + ".json")).is_file():
        assets_root = mcroot / "assets"
    asset_index = assets_root / "indexes" / (vanilla["assetIndex"]["id"] + ".json")
    if not asset_index.is_file():
        raise ValueError("Minecraft asset index32 is missing; run --prepare-assets first")
    if vanilla["assetIndex"].get("sha1") and digest_with(asset_index, "sha1") != vanilla["assetIndex"]["sha1"]:
        raise ValueError("Minecraft asset index does not match official26.2 metadata")
    java = args.java.resolve()
    if not java.is_file() or not os.access(java, os.X_OK):
        raise ValueError("An executable Java25 runtime is required")
    checked_java = subprocess.run([str(java), "-version"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                  text=True, timeout=15, check=False)
    if checked_java.returncode != 0 or re.search(r'version "25(?:[."]|$)', checked_java.stdout) is None:
        raise ValueError("Packaged acceptance must run on Java25")
    output.mkdir(parents=True)
    game = output / "game"
    (game / "mods").mkdir(parents=True)
    config = game / "config/openallay"
    config.mkdir(parents=True)
    (game / "saves").mkdir()
    shutil.copyfile(artifact, game / "mods" / artifact.name)
    if loader == "fabric":
        shutil.copyfile(api, game / "mods" / api.name)
    write_json(config / "models.json", models)
    write_json(config / "unrestricted-javascript.json", {"schemaVersion": 1, "enabled": args.enable_unrestricted})
    write_json(config / "experimental-commands.json", {"schemaVersion": 1, "enabled": False})
    write_json(config / "display.json", {"schemaVersion": 3, "debugMode": True,
                                         "animationsEnabled": True, "assistantName": "OpenAllay"})
    fps = 10 if args.low_impact else 30
    (game / "options.txt").write_text("onboardAccessibility:false\njoinedFirstServer:true\nrenderDistance:4\nsimulationDistance:5\nmaxFps:" + str(fps) + "\npauseOnLostFocus:false\n", encoding="utf-8")
    world = WORLD_PREFIX + loader + "-" + run_id
    values = {"natives_directory": output / "natives", "launcher_name": "OpenAllayPackagedAcceptance",
              "launcher_version": "1", "classpath": os.pathsep.join(classpath),
              "library_directory": mcroot / "libraries", "auth_player_name": "BuilderProbe",
              "version_name": MC_VERSION, "game_directory": game, "assets_root": assets_root.resolve(),
              "assets_index_name": vanilla["assetIndex"]["id"],
              "auth_uuid": offline_uuid("BuilderProbe"),
              "auth_access_token": "0", "clientid": "", "auth_xuid": "", "version_type": "release",
              "resolution_width": "854" if args.low_impact else "1100",
              "resolution_height": "480" if args.low_impact else "700"}
    if not re.fullmatch(r"[A-Za-z0-9_]{1,16}", values["auth_player_name"]):
        raise ValueError("Synthetic Minecraft username must contain 1 to 16 simple characters")
    jvm = expand_arguments(vanilla["arguments"]["jvm"] + extra_jvm, values)
    game_args = expand_arguments(vanilla["arguments"]["game"] + extra_game, values, {"has_custom_resolution": True})
    properties = {"enabled": "true", "createWorld": world, "scenario": args.scenario,
                  "question": args.question or "OpenAllay E2E Builder " + args.scenario.removeprefix("builder-"),
                  "report": str(output / "report.json"), "trace": str(output / "trace.json"),
                  "session": run_id, "modelMode": "client", "screenshotRoot": str(output / "screenshots"),
                  "shutdown": "false", "shutdownAfterScreenshots": "true",
                  "timeoutSeconds": str(args.timeout_seconds)}
    if args.revoke_unrestricted_after_capture:
        properties["revokeUnrestrictedAfterCapture"] = "true"
    heap = ["-Xms256M", "-Xmx1536M"] if args.low_impact else ["-Xms512M", "-Xmx3G"]
    command = [str(java)] + heap + jvm + proxy_arguments(args.http_proxy_from_env)
    if args.model_diagnostics:
        command.append("-Dopenallay.model.diagnostics=true")
    if loader == "fabric":
        command.append("-Dfabric.development=false")
    command += ["-Dopenallay.e2e." + key + "=" + value for key, value in properties.items()]
    command += [main_class] + game_args
    files = [p for p in game.rglob("*") if p.is_file()]
    manifest = {"schemaVersion": 1, "loader": loader, "minecraft": MC_VERSION, "javaRequired": 25,
                "runId": run_id, "world": world, "scenario": args.scenario,
                "gameDirectory": str(game), "packagedArtifact": identity,
                "unrestrictedOptIn": args.enable_unrestricted, "lowImpact": args.low_impact,
                "modelDiagnostics": args.model_diagnostics, "timeoutSeconds": args.timeout_seconds, "wallTimeoutSeconds": args.timeout_seconds + 60, "command": command,
                "classPath": classpath,
                "preparedFiles": {str(p.relative_to(output)): digest(p) for p in files},
                "report": str(output / "report.json"), "trace": str(output / "trace.json"),
                "screenshots": str(output / "screenshots"),
                "credentials": "environment references only; no account credentials used",
                "worldBootstrap": "native WorldOpenFlows.createFreshLevel; survival, commands off, superflat",
                "noGameLaunched": True}
    write_json(output / "launch.json", manifest)
    return output, manifest


def prepare_resume(args, repo=REPO):
    previous = safe_output(args.resume_prepared, repo)
    prior = json.loads((previous / "launch.json").read_text(encoding="utf-8"))
    if args.timeout_seconds <= 0:
        raise ValueError("Harness timeout must be a positive number of seconds")
    if args.scenario not in ("builder-reload", "builder-live-undo") or not args.enable_unrestricted:
        raise ValueError("World resume requires builder-reload or builder-live-undo and explicit --enable-unrestricted")
    if args.scenario == "builder-live-undo" and (not args.question or not args.model_config):
        raise ValueError("Live undo requires an explicit ordinary provider question and environment-reference model config")
    if args.loader and args.loader != prior["loader"]:
        raise ValueError("Resume loader must match the prior manifest")
    game = safe_output(Path(prior["gameDirectory"]), repo)
    world = prior["world"]
    if game != previous / "game" or not world.startswith(WORLD_PREFIX + prior["loader"] + "-"):
        raise ValueError("Resume requires an original disposable packaged acceptance manifest")
    saves = list((game / "saves").iterdir())
    if len(saves) != 1 or saves[0].name != world or not (saves[0] / "level.dat").is_file():
        raise ValueError("Resume requires exactly the prior native-created disposable world")
    expected_previous = "builder-live-copy" if args.scenario == "builder-live-undo" else "builder-acceptance"
    if prior["scenario"] != expected_previous or prior["noGameLaunched"]:
        raise ValueError("Resume requires the matching previously launched acceptance phase")
    validate_report(prior["report"])
    mods = list((game / "mods").glob("*.jar"))
    expected_mods = {Path(name).name: sha256 for name, sha256 in prior["preparedFiles"].items() if name.startswith("game/mods/")}
    if {path.name: digest(path) for path in mods} != expected_mods:
        raise ValueError("Packaged mods changed after the original prepared run")
    run_id = args.run_id or datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-reload-" + uuid.uuid4().hex[:6]
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,90}", run_id):
        raise ValueError("run-id must be a short filesystem-safe identifier")
    output = safe_output(previous / "phases" / run_id, repo)
    if output.exists():
        raise ValueError("Resume phase output already exists")
    upgrade = None
    previous_identity = prior["packagedArtifact"]
    new_identity = previous_identity
    replacement = None
    if args.jar:
        original = game / "mods" / previous_identity["name"]
        original_identity = packaged_artifact(original, prior["loader"])
        if original_identity["sha256"] != previous_identity["sha256"]:
            raise ValueError("Original packaged artifact hash does not match the accepted manifest")
        replacement = args.jar.resolve()
        new_identity = packaged_artifact(replacement, prior["loader"])
        identity_fields = ("loader", "minecraft", "modVersion", "nestedBuilder", "nestedBuilderSha256")
        if any(original_identity[field] != new_identity[field] for field in identity_fields):
            raise ValueError("Harness upgrade must preserve loader, Minecraft, OpenAllay version, and exact nested Builder bytes")
        if not new_identity["nativeWorldBootstrapPresent"]:
            raise ValueError("Harness upgrade must retain the opt-in native E2E bootstrap")
        backup = safe_output(previous / "evidence/harness-artifacts" / (original_identity["sha256"] + ".jar"), repo)
        upgrade = {"developmentInstrumentationOnly": True,
                   "oldSha256": original_identity["sha256"], "newSha256": new_identity["sha256"],
                   "nestedBuilderSha256": original_identity["nestedBuilderSha256"],
                   "originalArtifactEvidence": str(backup)}
        previous_identity = original_identity
    config = game / "config/openallay"
    models = validate_model_config(args.model_config or config / "models.json")
    if upgrade:
        backup.parent.mkdir(parents=True, exist_ok=True)
        if backup.exists() and digest(backup) != previous_identity["sha256"]:
            raise ValueError("Original harness artifact evidence hash is invalid")
        if not backup.exists():
            shutil.copyfile(original, backup)
        staged = original.with_name(original.name + ".harness-upgrade")
        try:
            shutil.copyfile(replacement, staged)
            if digest(staged) != new_identity["sha256"]:
                raise ValueError("Staged harness artifact hash changed")
            staged.replace(original)
        finally:
            staged.unlink(missing_ok=True)
    if args.model_config:
        write_json(config / "models.json", models)
    # This explicit opt-in changes only the prior disposable generated config.
    write_json(config / "unrestricted-javascript.json", {"schemaVersion": 1, "enabled": True})
    command = []
    skip_next = False
    prior_command = prior["command"]
    for entry in prior_command:
        if skip_next:
            skip_next = False
            continue
        if entry == "--quickPlaySingleplayer":
            skip_next = True
            continue
        if entry.startswith("-Dopenallay.e2e."):
            continue
        command.append(entry)
    main_class = "net.fabricmc.loader.impl.launch.knot.KnotClient" if prior["loader"] == "fabric" else "net.neoforged.fml.startup.Client"
    insert = command.index(main_class)
    diagnostics_property = "-Dopenallay.model.diagnostics=true"
    if args.model_diagnostics and diagnostics_property not in command:
        command.insert(insert, diagnostics_property)
        insert += 1
    model_diagnostics = diagnostics_property in command
    properties = {"enabled": "true", "scenario": args.scenario, "question": args.question or "OpenAllay E2E Builder reload",
                  "report": str(output / "report.json"), "trace": str(output / "trace.json"),
                  "session": run_id, "modelMode": "client", "screenshotRoot": str(output / "screenshots"),
                  "shutdown": "false", "shutdownAfterScreenshots": "true", "resumeWorld": world,
                  "timeoutSeconds": str(args.timeout_seconds)}
    command[insert:insert] = ["-Dopenallay.e2e." + key + "=" + value for key, value in properties.items()]
    output.mkdir(parents=True)
    files = list((game / "mods").glob("*.jar")) + list(config.glob("*.json"))
    manifest = {**prior, "runId": run_id, "scenario": args.scenario, "command": command,
                "resumeFrom": str(previous), "nativeSavedWorldReuse": True,
                "modelDiagnostics": model_diagnostics, "timeoutSeconds": args.timeout_seconds, "wallTimeoutSeconds": args.timeout_seconds + 60,
                "preparedFiles": {str(path.relative_to(game)): digest(path) for path in files},
                "report": str(output / "report.json"), "trace": str(output / "trace.json"),
                "screenshots": str(output / "screenshots"), "noGameLaunched": True}
    if upgrade:
        manifest["previousPackagedArtifact"] = previous_identity
        manifest["newPackagedArtifact"] = new_identity
        manifest["packagedArtifact"] = new_identity
        manifest["testHarnessUpgrade"] = upgrade
    manifest.pop("launchedAt", None)
    write_json(output / "launch.json", manifest)
    return output, manifest


def validate_report(path):
    if not Path(path).is_file():
        raise ValueError("Client closed without an acceptance report")
    report = json.loads(Path(path).read_text(encoding="utf-8"))
    if report.get("outcome") != "COMPLETED":
        raise ValueError("Packaged acceptance request did not complete (" + str(report.get("outcome")) + ")")
    native = report.get("nativeAcceptance")
    if not isinstance(native, dict) or native.get("outcome") != "PASSED":
        raise ValueError("Native Builder acceptance did not pass (" + str((native or {}).get("outcome")) + ")")
    return report


def launch_prepared(path, repo=REPO):
    output = safe_output(path, repo)
    manifest = json.loads((output / "launch.json").read_text(encoding="utf-8"))
    if not manifest["packagedArtifact"]["nativeWorldBootstrapPresent"]:
        raise ValueError("Rebuild the default packaged JAR with the opt-in native fresh-world E2E hook before launching")
    game = safe_output(Path(manifest["gameDirectory"]), repo)
    if not manifest["world"].startswith(WORLD_PREFIX):
        raise ValueError("Prepared disposable world identity is invalid")
    expected = manifest["preparedFiles"]
    if manifest.get("resumeFrom"):
        previous = safe_output(Path(manifest["resumeFrom"]), repo)
        if game != previous / "game" or not output.is_relative_to(previous / "phases"):
            raise ValueError("Resume game directory must belong to the original disposable manifest")
        saves = list((game / "saves").iterdir())
        if len(saves) != 1 or saves[0].name != manifest["world"] or not (saves[0] / "level.dat").is_file():
            raise ValueError("Resume disposable native world is missing or changed")
        files = list((game / "mods").glob("*.jar")) + list((game / "config/openallay").glob("*.json"))
        actual = {str(p.relative_to(game)): digest(p) for p in files}
    else:
        if game != output / "game":
            raise ValueError("Prepared game directory is invalid")
        if any((game / "saves").iterdir()):
            raise ValueError("Prepared acceptance directory already contains a world; prepare a new disposable run")
        actual = {str(p.relative_to(output)): digest(p) for p in game.rglob("*") if p.is_file()}
    if actual != expected:
        raise ValueError("Prepared game files changed; prepare a new reviewed run")
    models = validate_model_config(game / "config/openallay/models.json")
    for profile in models["profiles"]:
        if profile.get("enabled") and not os.environ.get(profile["credentialRef"].removeprefix("env:")):
            raise ValueError("An enabled acceptance profile has no credential in its referenced environment variable")
    # The runtime command contains only the synthetic Minecraft token '0'. Provider
    # secrets stay in environment variables and are never serialized or printed.
    manifest["noGameLaunched"] = False
    manifest["launchedAt"] = datetime.now(timezone.utc).isoformat()
    write_json(output / "launch.json", manifest)
    print("Launching reviewed packaged " + manifest["loader"] + " acceptance in its disposable directory.", flush=True)
    print("Client log: " + str(output / "client.log"), flush=True)
    process = None
    previous_term_handler = signal.getsignal(signal.SIGTERM)
    def terminate_requested(signum, frame):
        raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, terminate_requested)
    with (output / "client.log").open("wb") as log:
        try:
            process = subprocess.Popen(manifest["command"], cwd=game, stdout=log, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            manifest["clientPid"] = process.pid
            write_json(output / "launch.json", manifest)
            exit_code = process.wait(timeout=manifest.get("wallTimeoutSeconds", 600))
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            if process is not None and process.poll() is None:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
            raise ValueError("Packaged acceptance client stopped after timeout or interrupt")
        finally:
            signal.signal(signal.SIGTERM, previous_term_handler)
    manifest["clientExitCode"] = exit_code
    manifest["closedAt"] = datetime.now(timezone.utc).isoformat()
    write_json(output / "launch.json", manifest)
    if exit_code != 0:
        raise ValueError("Packaged acceptance client exited unsuccessfully (" + str(exit_code) + ")")
    validate_report(manifest["report"])
    print("Packaged Builder native acceptance PASSED: " + manifest["report"], flush=True)


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("loader", choices=("fabric", "neoforge"), nargs="?")
    result.add_argument("--run-id")
    result.add_argument("--scenario", choices=SCENARIOS, default="builder-disabled")
    result.add_argument("--enable-unrestricted", action="store_true", help="Explicit disposable-directory opt-in; never changes ordinary profiles")
    result.add_argument("--revoke-unrestricted-after-capture", action="store_true")
    result.add_argument("--question")
    result.add_argument("--model-diagnostics", action="store_true",
                        help="Opt in to prepared-JVM model diagnostics; default off, retained on resumed phases")
    result.add_argument("--timeout-seconds", type=int, default=300,
                        help="Development harness deadline; Java supervisor uses this deadline plus 60 seconds")
    result.add_argument("--low-impact", action="store_true", help="Disposable client only: 854x480, FPS10, 256M/1536M heap; retains render4/simulation5")
    result.add_argument("--http-proxy-from-env", action="store_true", help="Explicit JVM HTTP(S) proxy from conventional env settings; local/credential-free only")
    result.add_argument("--resume-prepared", type=Path, help="Prepare a reload phase using only a prior manifest's disposable world under build/e2e")
    result.add_argument("--jar", type=Path, help="Default production-named built artifact; no source classes")
    result.add_argument("--model-config", type=Path, help="Explicit secret-free schema-2 config with env credential references")
    result.add_argument("--fixture-port", type=int, default=18765, help="Loopback fixture is started separately")
    result.add_argument("--minecraft-root", type=Path, default=Path.home() / "Library/Application Support/minecraft")
    result.add_argument("--gradle-cache", type=Path, default=Path.home() / ".gradle/caches/modules-2/files-2.1")
    result.add_argument("--fabric-api", type=Path)
    result.add_argument("--assets-root", type=Path, help="Matching32 assets; defaults to ignored runtime cache then installed assets")
    result.add_argument("--prepare-assets", "--fetch-assets", action="store_true", help="Explicitly fetch/hash-check official32 assets into ignored build/e2e/runtime/assets; no game launch")
    result.add_argument("--java", type=Path, default=Path("/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home/bin/java"))
    result.add_argument("--launch-prepared", type=Path, help="Explicitly launch an already prepared/reviewed directory; otherwise only prepare")
    return result


def main(argv=None):
    args = parser().parse_args(argv)
    try:
        if args.prepare_assets:
            if args.launch_prepared:
                raise ValueError("Asset preparation cannot launch a client")
            prepare_assets(args.minecraft_root, args.assets_root or REPO / "build/e2e/runtime/assets")
        elif args.launch_prepared:
            launch_prepared(args.launch_prepared)
        elif args.resume_prepared:
            output, manifest = prepare_resume(args)
            print("Prepared packaged reload of the prior disposable world. No game launched.")
            print("Launch metadata: " + str(output / "launch.json"))
            print("Launch after review: " + sys.executable + " scripts/run-packaged-builder-acceptance.py --launch-prepared " + str(output))
        elif not args.loader:
            raise ValueError("loader is required when preparing a new run")
        else:
            output, manifest = prepare(args)
            print("Prepared packaged " + manifest["loader"] + " acceptance. No game launched.")
            print("Artifact: " + manifest["packagedArtifact"]["name"] + " (" + manifest["packagedArtifact"]["kind"] + ")")
            print("Launch metadata: " + str(output / "launch.json"))
            print("World: " + manifest["world"])
            print("Launch after review: " + sys.executable + " scripts/run-packaged-builder-acceptance.py --launch-prepared " + str(output))
        return 0
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as failure:
        print("Packaged acceptance preparation refused: " + str(failure), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
