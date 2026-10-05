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
from io import BytesIO
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

def target_reader():
    from importlib.util import module_from_spec, spec_from_file_location
    spec = spec_from_file_location("acceptance_minecraft_targets", REPO / "scripts/minecraft-target.py")
    reader = module_from_spec(spec)
    spec.loader.exec_module(reader)
    return reader


def minecraft_targets(repo=None):
    root = Path(repo) if repo is not None else REPO
    return tuple(sorted(path.stem for path in (root / "gradle/minecraft-targets").glob("*.properties")))


def runtime_pins(minecraft_target, repo=None):
    root = Path(repo) if repo is not None else REPO
    if minecraft_target not in minecraft_targets(root):
        raise ValueError("Unknown exact Minecraft target: " + minecraft_target)
    return target_reader().read_profile(root, minecraft_target)
MOD_VERSION = "0.4.1"
WORLD_PREFIX = "openallay-builder-"
SCENARIOS = ("builder-restricted", "builder-acceptance", "builder-reload",
             "builder-partial", "builder-cancel", "builder-live", "builder-live-copy", "builder-live-undo",
             "ui-stop", "ui-provider-failure", "ui-manual-regressions", "ui-live-ux-regressions")
UI_OUTCOMES = {"ui-stop": "CANCELLED", "ui-provider-failure": "FAILED", "ui-manual-regressions": "COMPLETED", "ui-live-ux-regressions": "COMPLETED"}
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
        if rule.get("action") not in ("allow", "disallow"):
            raise ValueError("Invalid installed library rule action")
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
        raise ValueError("Required locally installed Minecraft version metadata is missing: " + name
                         + "; install the pinned official client/loader profile first")
    return json.loads(path.read_text(encoding="utf-8"))


def maven_path(coordinate):
    group, artifact, version, *classifier = coordinate.split(":")
    filename = artifact + "-" + version + ("-" + classifier[0] if classifier else "") + ".jar"
    return Path(group.replace(".", "/")) / artifact / version / filename


def cached_library(coordinate, minecraft_root, gradle_cache, allow_gradle=True):
    official = minecraft_root / "libraries" / maven_path(coordinate)
    if official.is_file():
        return official.resolve()
    if not allow_gradle:
        raise ValueError("Required isolated official runtime library is missing: " + coordinate)
    group, artifact, version, *classifier = coordinate.split(":")
    filename = maven_path(coordinate).name
    matches = list((gradle_cache / group / artifact / version).glob("*/" + filename))
    if len(matches) != 1:
        raise ValueError("Required local runtime library is missing or ambiguous: " + coordinate)
    return matches[0].resolve()


def version_libraries(metadata, minecraft_root, gradle_cache, allow_gradle=True):
    result = []
    for library in metadata.get("libraries", []):
        if not rules_allow(library.get("rules")):
            continue
        artifact = library.get("downloads", {}).get("artifact")
        if artifact is None:
            if library.get("downloads") is not None or not library.get("url", "").startswith("https://maven.fabricmc.net/"):
                raise ValueError("Unsupported installed library metadata: " + library["name"])
            artifact = {"path": maven_path(library["name"]).as_posix(),
                        **{key: library[key] for key in ("sha1", "size") if key in library}}
        relative = artifact["path"]
        if (not isinstance(relative, str) or "\\" in relative or relative.startswith("/")
                or re.match(r"^[A-Za-z]:", relative) or any(part in ("", ".", "..") for part in relative.split("/"))):
            raise ValueError("Unsafe installed library metadata path")
        path = (minecraft_root / "libraries" / relative).resolve()
        if not path.is_relative_to(minecraft_root.resolve() / "libraries"):
            raise ValueError("Installed library metadata escapes runtime")
        if not path.is_file():
            path = cached_library(library["name"], minecraft_root, gradle_cache, allow_gradle)
        if (artifact.get("sha1") and digest_with(path, "sha1") != artifact["sha1"]
                or "size" in artifact and path.stat().st_size != artifact["size"]):
            raise ValueError("Local runtime library hash/size does not match installed metadata: " + library["name"])
        result.append((library["name"], path))
    return result


def native_libraries(metadata, minecraft_root):
    result = []
    for library in metadata.get("libraries", []):
        if not rules_allow(library.get("rules")):
            continue
        classifier = library.get("natives", {}).get(system_name())
        if not classifier:
            continue  # Modern LWJGL native artifacts stay on the official classpath.
        bits = "64" if platform.machine().lower() in ("x86_64", "amd64", "aarch64", "arm64") else "32"
        classifier = classifier.replace("${arch}", bits)
        artifact = library.get("downloads", {}).get("classifiers", {}).get(classifier)
        if not isinstance(artifact, dict) or not {"path", "sha1", "size"}.issubset(artifact):
            raise ValueError("Installed native classifier metadata is incomplete")
        path = (minecraft_root / "libraries" / artifact["path"]).resolve()
        if (not path.is_relative_to(minecraft_root.resolve() / "libraries") or not path.is_file()
                or path.stat().st_size != artifact["size"] or digest_with(path, "sha1") != artifact["sha1"]):
            raise ValueError("Installed native classifier does not match official hash/size")
        result.append((path, library.get("extract", {}).get("exclude", ["META-INF/"])))
    return result


def extract_natives(libraries, destination):
    """Extract only selected verified classifiers into this new disposable run."""
    destination.mkdir(parents=True)
    files = {}
    for jar, excludes in libraries:
        if not isinstance(excludes, list) or any(not isinstance(prefix, str) for prefix in excludes):
            raise ValueError("Invalid official native extraction excludes")
        with zipfile.ZipFile(jar) as archive:
            total = 0
            for info in archive.infolist():
                name = info.filename.rstrip("/")
                if (not name or "\\" in name or name.startswith("/") or re.match(r"^[A-Za-z]:", name)
                        or any(part in ("", ".", "..") for part in name.split("/"))
                        or (info.external_attr >> 16) & 0o170000 == 0o120000):
                    raise ValueError("Unsafe native archive path")
                total += info.file_size
                if info.file_size > 128 * 1024 * 1024 or total > 512 * 1024 * 1024:
                    raise ValueError("Native archive exceeds extraction byte limit")
                if info.is_dir() or any(info.filename.startswith(prefix) for prefix in excludes):
                    continue
                target = destination.joinpath(*name.split("/"))
                if not target.resolve().is_relative_to(destination.resolve()):
                    raise ValueError("Native extraction escapes disposable run")
                content = archive.read(info)
                sha256 = hashlib.sha256(content).hexdigest()
                if name in files and files[name] != sha256:
                    raise ValueError("Selected native JARs contain conflicting paths")
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(content)
                files[name] = sha256
    return files


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


def prepare_assets(minecraft_root, destination, repo=REPO, minecraft_target="26.2"):
    """Reuse verified installed object hashes; fetch only missing official assets."""
    destination = safe_output(destination, repo)
    version = read_version(minecraft_root, minecraft_target)
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
    write_json(destination / "verified.json", {"minecraft": minecraft_target, "assetIndex": index_meta["id"],
                                               "indexSha1": index_meta["sha1"], "objectCount": len(unique),
                                               "allObjectsVerified": True})
    print("Verified Minecraft" + minecraft_target + " assets ready: " + str(destination), flush=True)
    return destination


def bundled_extension_verifier():
    # Use the release verifier unchanged. Loading a script here does not run its
    # CLI, source preparation, Gradle, or remote access.
    from importlib.util import module_from_spec, spec_from_file_location
    spec = spec_from_file_location("acceptance_bundled_extensions", REPO / "scripts/verify-bundled-extensions.py")
    verifier = module_from_spec(spec)
    spec.loader.exec_module(verifier)
    return verifier


def packaged_artifact(path, loader, mod_version=MOD_VERSION, repo=REPO, minecraft_target="26.2"):
    path = path.resolve()
    expected_name = f"openallay-{loader}-{minecraft_target}-{mod_version}.jar"
    if path.name != expected_name or not path.is_file():
        raise ValueError("Use the default built OpenAllay production artifact: " + expected_name)
    verifier = bundled_extension_verifier()
    lock = verifier.prepare.load_manifest(repo / "distribution/extensions.lock.json")
    builder_sha256 = verifier.verify_package(path, loader, lock)
    builder_path = verifier.resource_path(lock)
    with zipfile.ZipFile(path) as archive:
        with zipfile.ZipFile(BytesIO(archive.read(builder_path))) as builder:
            descriptor = verifier.prepare.decode_json(builder.read(verifier.DESCRIPTOR))
            if not any(target["loader"] == loader and target["minecraftVersionRange"] in
                       (minecraft_target, "[" + minecraft_target + "]")
                       for target in descriptor["support"]["targets"]):
                raise ValueError("Pinned universal Builder does not declare this exact loader/Minecraft target")
        if loader == "fabric":
            metadata = verifier.prepare.decode_json(archive.read("fabric.mod.json"))
            if metadata["id"] != "openallay" or metadata["version"] != mod_version:
                raise ValueError("Unexpected packaged OpenAllay identity")
        else:
            descriptor_path = ("META-INF/mods.toml" if minecraft_target in ("1.20.1", "1.20.2", "1.20.3", "1.20.4")
                               else "META-INF/neoforge.mods.toml")
            mod_metadata = archive.read(descriptor_path).decode("utf-8")
            if (re.search(r'^modId\s*=\s*"openallay"', mod_metadata, re.MULTILINE) is None
                    or re.search(r'^version\s*=\s*"' + re.escape(mod_version) + r'"', mod_metadata, re.MULTILINE) is None):
                raise ValueError("Unexpected packaged OpenAllay identity")
        bootstrap = "dev/openallay/guide/e2e/GuideClientE2EController.class"
        if bootstrap not in archive.namelist():
            raise ValueError("Packaged artifact does not contain the opt-in E2E controller")
        instrumented = b"openallay.e2e.createWorld" in archive.read(bootstrap)
    return {"name": path.name, "sha256": digest(path), "bundledBuilder": builder_path,
            "bundledBuilderSha256": builder_sha256, "loader": loader, "minecraft": minecraft_target,
            "modVersion": mod_version, "kind": "acceptance-instrumented" if instrumented else "production-before-bootstrap",
            "nativeWorldBootstrapPresent": instrumented}


def validate_model_config(source):
    config = json.loads(Path(source).read_text(encoding="utf-8"))
    if not isinstance(config, dict) or set(config) != {"defaultProfileId", "profiles"}:
        raise ValueError("Model configuration must contain only defaultProfileId and profiles")
    if (not isinstance(config["profiles"], list) or not config["profiles"]
            or not isinstance(config["defaultProfileId"], str) or not config["defaultProfileId"]):
        raise ValueError("An explicit default model profile is required")
    for profile in config["profiles"]:
        if not isinstance(profile, dict):
            raise ValueError("Model profiles must be objects")
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
    if not any(profile.get("id") == config["defaultProfileId"] for profile in config["profiles"]):
        raise ValueError("defaultProfileId must name an explicit model profile")
    return config


def fixture_model_config(port):
    return {"defaultProfileId": "e2e-fixture", "profiles": [{
        "id": "e2e-fixture", "displayName": "OpenAllay E2E Fixture", "enabled": True,
        "protocol": "openai_chat", "baseUrl": f"http://127.0.0.1:{port}/v1/",
        "model": "openallay-e2e-fixture", "credentialRef": "env:OPENALLAY_E2E_FIXTURE_KEY",
        "contextWindowTokens": 256000, "maxOutputTokens": 8192,
        "connectTimeoutSeconds": 10, "requestTimeoutSeconds": 120}]}


def fixture_display_config():
    """Exact current GuideDisplayConfig/GuideUiConfig defaults, no historical shape."""
    return {"debugMode": False, "animationsEnabled": True, "assistantName": "OpenAllay",
            "ui": {
                "fullscreen": {"density": "COMFORTABLE", "sessionRailVisible": True,
                               "theme": "CHARCOAL"},
                "hud": {"enabled": False, "anchor": "TOP_LEFT", "offsetX": 12, "offsetY": 12,
                        "width": 320, "height": 240, "scale": 1, "backgroundOpacity": 0.78,
                        "collapsed": False, "maxReplyLines": 18, "showLatestReply": True,
                        "showStreamingPreview": False, "hideWithDebug": True, "hideOnOtherScreens": True},
                "notifications": {"enabled": False, "policy": "WHEN_GUIDE_NOT_VISIBLE",
                                  "replyCompleted": True, "cardBatches": True,
                                  "taskFailures": True, "durationSeconds": 6}}}


def fixture_voice_config(gameplay_action="SEND"):
    """Current eleven-field voice shape. Native GUI tests never open an audio device."""
    if gameplay_action not in ("SEND", "DRAFT"):
        raise ValueError("Unknown gameplay voice action")
    return {"enabled": False, "backend": "NATIVE", "deviceId": "default", "maxClipSeconds": 20,
            "language": "auto", "cpuThreads": max(1, min(2, os.cpu_count() or 1)),
            "nativeModelDirectory": "", "httpBaseUrl": "http://127.0.0.1:8080/v1",
            "httpModel": "whisper-1", "credentialRef": None, "gameplayAction": gameplay_action}


def graphical_scenario(scenario):
    return scenario in ("ui-manual-regressions", "ui-live-ux-regressions")


def write_json(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def safe_output(path, repo=REPO):
    path = path.resolve()
    root = (repo / "build/e2e").resolve()
    if not path.is_relative_to(root):
        raise ValueError("All acceptance output must be under ignored build/e2e")
    return path


def read_runtime_provision(minecraft_root, loader, repo=REPO, minecraft_target="26.2"):
    repo = repo.resolve()
    if minecraft_root != repo / "build/e2e/runtime" / minecraft_target / "minecraft":
        return None
    verifier = bundled_extension_verifier()
    receipt_path = minecraft_root / ".provision" / (loader + "-runtime.json")
    if not receipt_path.is_file():
        raise ValueError("Isolated runtime requires its official provision manifest")
    receipt = verifier.prepare.decode_json(receipt_path.read_text(encoding="utf-8"))
    profile = repo / "gradle/minecraft-targets" / (minecraft_target + ".properties")
    profile_pins = runtime_pins(minecraft_target, repo)
    pin_keys = ("minecraft_version", "java_version", "fabric_loader_version", "fabric_version", "neoforge_version")
    expected_pins = {key: profile_pins[key] for key in pin_keys}
    if (receipt.get("loader") != loader or receipt.get("minecraft") != minecraft_target
            or receipt.get("pins") != expected_pins
            or receipt.get("sourceProfile") != "gradle/minecraft-targets/" + minecraft_target + ".properties"
            or type(receipt.get("javaRequired")) is not int or receipt["javaRequired"] != int(profile_pins["java_version"])
            or receipt.get("minecraftRoot") != str(minecraft_root)
            or receipt.get("mechanism") != "official-client-installer"
            or receipt.get("sourceProfileSha256") != digest(profile)):
        raise ValueError("Isolated runtime provision manifest does not match the exact source target")
    if not isinstance(receipt.get("files"), dict):
        raise ValueError("Isolated runtime provision manifest has no file hashes")
    return {"path": str(receipt_path), "sha256": digest(receipt_path),
            "files": receipt["files"], "fabricApi": receipt.get("fabricApi"), "profile": receipt.get("profile")}


def validate_runtime_classpath(classpath, minecraft_root, loader, repo=REPO, minecraft_target="26.2"):
    repo = repo.resolve()
    minecraft_root = minecraft_root.resolve()
    isolated = repo / "build/e2e/runtime" / minecraft_target / "minecraft"
    if isolated.is_symlink() and minecraft_root == isolated.resolve():
        raise ValueError("Isolated official runtime must not redirect to an external installation")
    # An in-repository runtime is allowed only at the provisioner's isolated
    # official installation, never module build output or Gradle native JARs.
    for entry in classpath:
        path = Path(entry).resolve()
        official_library = path.is_relative_to(minecraft_root / "libraries")
        official_client = path == minecraft_root / "versions" / minecraft_target / (minecraft_target + ".jar")
        if (minecraft_root == isolated and not (official_library or official_client)
                or path.is_relative_to(repo) and (minecraft_root != isolated or not (official_library or official_client))):
            raise ValueError("Packaged launch cannot include project source classes or Gradle game artifacts")
    provision = read_runtime_provision(minecraft_root, loader, repo, minecraft_target)
    if provision:
        for entry in classpath:
            verify_runtime_record(Path(entry).resolve(), minecraft_root, provision["files"])
    return provision


def verify_runtime_record(path, minecraft_root, records):
    if not path.is_relative_to(minecraft_root):
        raise ValueError("Isolated runtime cannot use Gradle cache or external runtime files")
    relative = path.relative_to(minecraft_root).as_posix()
    record = records.get(relative)
    if (not isinstance(record, dict) or set(record) != {"sha256", "sha1", "size"}
            or not re.fullmatch(r"[0-9a-f]{64}", str(record.get("sha256", "")))
            or not re.fullmatch(r"[0-9a-f]{40}", str(record.get("sha1", "")))
            or type(record.get("size")) is not int or record["size"] < 0
            or not path.is_file() or path.stat().st_size != record["size"]
            or digest(path) != record["sha256"] or digest_with(path, "sha1") != record["sha1"]):
        raise ValueError("Isolated runtime file does not match provision manifest: " + relative)


def prepare(args, repo=REPO):
    loader = args.loader
    minecraft_target = args.minecraft_target
    run_id = args.run_id or datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:6]
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,90}", run_id):
        raise ValueError("run-id must be a short filesystem-safe identifier")
    if args.scenario not in SCENARIOS:
        raise ValueError("Unknown Builder scenario")
    if args.timeout_seconds <= 0:
        raise ValueError("Harness timeout must be a positive number of seconds")
    if args.scenario == "builder-live-undo":
        raise ValueError("Live undo must resume a reviewed prior builder-live-copy disposable world")
    ui_scenario = args.scenario in UI_OUTCOMES
    if (args.scenario == "builder-restricted" or ui_scenario) and args.enable_unrestricted:
        raise ValueError("Restricted Builder proof and UI scenarios cannot enable unrestricted JavaScript")
    if args.cancel_on_tool_start and args.scenario != "ui-stop":
        raise ValueError("--cancel-on-tool-start is allowed only for ui-stop")
    if args.scenario == "ui-stop" and not args.cancel_on_tool_start:
        raise ValueError("ui-stop requires explicit --cancel-on-tool-start")
    if graphical_scenario(args.scenario) and args.model_config:
        raise ValueError("Graphical regression uses the local deterministic fixture, never an external provider config")
    if args.scenario == "ui-live-ux-regressions" and args.question and not args.question.startswith("OpenAllay E2E UI live UX regressions hold"):
        raise ValueError("Live UX graphical scenario requires its explicit held loopback question")
    if args.scenario == "ui-live-ux-regressions":
        args.timeout_seconds = max(args.timeout_seconds, 600)
    if args.scenario.startswith("builder-live") and (not args.question or not args.model_config):
        raise ValueError("Live acceptance requires an explicit ordinary provider question and environment-reference model config")
    if args.professional_screenshots and (not args.screenshot_manual_profile or not args.screenshot_automatic_profile):
        raise ValueError("Professional screenshots require explicit manual and automatic profile IDs")
    if args.mod_version is None:
        properties = (repo / "gradle.properties").read_text(encoding="utf-8")
        versions = re.findall(r"^version=([^\r\n]+)$", properties, re.MULTILINE)
        if len(versions) != 1:
            raise ValueError("Checked-in Gradle release version is missing or ambiguous")
        args.mod_version = versions[0]
    if not re.fullmatch(r"[0-9]+(?:[.][0-9]+){2}", args.mod_version):
        raise ValueError("mod-version must be an explicit three-part release version")
    if args.review_package and not args.review_package.is_file():
        raise ValueError("An explicit local review package JAR is required")
    output = safe_output(repo / "build/e2e/packaged-builder" / loader / run_id, repo)
    if output.exists():
        raise ValueError("Acceptance directory already exists; use a new run-id")
    pins = runtime_pins(minecraft_target, repo)
    java_required = int(pins["java_version"])
    mcroot = args.minecraft_root.resolve()
    gradle_cache = args.gradle_cache.resolve()
    runtime_provision = read_runtime_provision(mcroot, loader, repo, minecraft_target)
    vanilla = read_version(mcroot, minecraft_target)
    if runtime_provision:
        verify_runtime_record(mcroot / "versions" / minecraft_target / (minecraft_target + ".json"),
                              mcroot, runtime_provision["files"])
    if vanilla.get("id") != minecraft_target or vanilla.get("javaVersion", {}).get("majorVersion") != java_required:
        raise ValueError("Minecraft metadata differs from the exact source target/Java pins")
    artifact = args.jar or repo / loader / "build/libs" / f"openallay-{loader}-{minecraft_target}-{args.mod_version}.jar"
    identity = packaged_artifact(artifact, loader, args.mod_version, repo, minecraft_target)
    models = validate_model_config(args.model_config) if args.model_config else fixture_model_config(args.fixture_port)
    libraries = version_libraries(vanilla, mcroot, gradle_cache, allow_gradle=runtime_provision is None)
    extra_jvm, extra_game, loader_files = [], [], []
    if loader == "fabric":
        profile_id = "fabric-loader-" + pins["fabric_loader_version"] + "-" + minecraft_target
        if runtime_provision and runtime_provision["profile"] != profile_id:
            raise ValueError("Pinned Fabric runtime profile ID is invalid")
        fabric = read_version(mcroot, profile_id)
        if (fabric.get("id") != profile_id or fabric.get("inheritsFrom") != minecraft_target
                or fabric.get("mainClass") != "net.fabricmc.loader.impl.launch.knot.KnotClient"):
            raise ValueError("Installed Fabric profile does not match the exact source target")
        if runtime_provision:
            verify_runtime_record(mcroot / "versions" / profile_id / (profile_id + ".json"),
                                  mcroot, runtime_provision["files"])
        fabric_libraries = version_libraries(fabric, mcroot, gradle_cache, allow_gradle=runtime_provision is None)
        replacements = {":".join(name.split(":")[:2]) for name, _ in fabric_libraries}
        libraries = [(name, path) for name, path in libraries if ":".join(name.split(":")[:2]) not in replacements]
        loader_files = [path for _, path in fabric_libraries]
        loader_jar = cached_library("net.fabricmc:fabric-loader:" + pins["fabric_loader_version"],
                                    mcroot, gradle_cache, allow_gradle=runtime_provision is None)
        if loader_jar not in loader_files:
            raise ValueError("Installed official Fabric profile must declare the pinned loader dependency")
        main_class = fabric["mainClass"]
        version_type = fabric["type"]
        extra_jvm = fabric.get("arguments", {}).get("jvm", [])
        extra_game = fabric.get("arguments", {}).get("game", [])
        game_jar = mcroot / "versions" / minecraft_target / (minecraft_target + ".jar")
        if not game_jar.is_file():
            raise ValueError("Official installed Minecraft client JAR is missing")
        client = vanilla["downloads"]["client"]
        if (game_jar.stat().st_size != client["size"]
                or digest_with(game_jar, "sha1") != client["sha1"]):
            raise ValueError("Official Minecraft client JAR does not match exact installed metadata")
        loader_files.append(game_jar.resolve())
        api_version = pins["fabric_version"]
        api = (args.fabric_api or (Path(runtime_provision["fabricApi"]) if runtime_provision and runtime_provision["fabricApi"]
                                  else repo / "fabric/runs/client/mods" / ("fabric-api-" + api_version + ".jar")))
        if not api.is_file():
            raise ValueError("A locally installed source-pinned Fabric API JAR is required")
        with zipfile.ZipFile(api) as archive:
            apimeta = json.loads(archive.read("fabric.mod.json"))
            if apimeta.get("id") != "fabric-api" or apimeta.get("version") != api_version:
                raise ValueError("Use the Fabric API pin from the exact source target profile")
    else:
        profile_id = ("1.20.1-forge-" + pins["neoforge_version"].removeprefix("1.20.1-")
                      if minecraft_target == "1.20.1" else "neoforge-" + pins["neoforge_version"])
        if runtime_provision:
            if runtime_provision["profile"] != profile_id:
                raise ValueError("Isolated NeoForge profile does not match the exact source target")
            verify_runtime_record(mcroot / "versions" / profile_id / (profile_id + ".json"),
                                  mcroot, runtime_provision["files"])
        neo = read_version(mcroot, profile_id)
        if (neo.get("id") != profile_id or neo.get("inheritsFrom") != minecraft_target
                or neo.get("mainClass") not in ("net.neoforged.fml.startup.Client", "cpw.mods.bootstraplauncher.BootstrapLauncher")):
            raise ValueError("Installed NeoForge profile does not match the exact source target")
        neo_libraries = version_libraries(neo, mcroot, gradle_cache, allow_gradle=runtime_provision is None)
        replacements = {name.split(":")[0] + ":" + name.split(":")[1] for name, _ in neo_libraries}
        libraries = [(name, path) for name, path in libraries if ":".join(name.split(":")[:2]) not in replacements]
        loader_files = [path for _, path in neo_libraries]
        main_class = neo["mainClass"]
        version_type = neo["type"]
        extra_jvm = neo["arguments"].get("jvm", [])
        extra_game = neo["arguments"].get("game", [])
        if runtime_provision:
            install_path = mcroot / ".provision/neoforge-install_profile.json"
            verify_runtime_record(install_path, mcroot, runtime_provision["files"])
            install = bundled_extension_verifier().prepare.decode_json(install_path.read_text(encoding="utf-8"))
            if install.get("minecraft") != minecraft_target or install.get("version") != profile_id:
                raise ValueError("Official installer data does not match the selected client profile")
            patched_coordinate = install.get("data", {}).get("PATCHED", {}).get("client", "")
            if not patched_coordinate.startswith("[") or not patched_coordinate.endswith("]"):
                raise ValueError("Official installer patched client data is missing")
            production = mcroot / "libraries" / maven_path(patched_coordinate[1:-1])
            verify_runtime_record(production.resolve(), mcroot, runtime_provision["files"])
            artifact_name = "forge" if minecraft_target == "1.20.1" else "neoforge"
            universal = mcroot / "libraries" / maven_path("net.neoforged:" + artifact_name + ":" + pins["neoforge_version"] + ":universal")
            verify_runtime_record(universal.resolve(), mcroot, runtime_provision["files"])
            for key in ("MC_SRG", "MC_EXTRA"):
                if key not in install.get("data", {}):
                    continue
                coordinate = install["data"][key].get("client", "")
                if not coordinate.startswith("[") or not coordinate.endswith("]"):
                    raise ValueError("Official mapped client library data is invalid: " + key)
                production = mcroot / "libraries" / maven_path(coordinate[1:-1])
                verify_runtime_record(production.resolve(), mcroot, runtime_provision["files"])
        # Production libraries are located by the official profile/FML. The
        # BootstrapLauncher and module path come directly from installed metadata.
    classpath = list(dict.fromkeys(str(path) for _, path in libraries))
    classpath += [str(path) for path in loader_files if str(path) not in classpath]
    runtime_provision = validate_runtime_classpath(classpath, mcroot, loader, repo, minecraft_target)
    if runtime_provision and loader == "fabric":
        if runtime_provision["fabricApi"] != str(api.resolve()):
            raise ValueError("Fabric API does not match the isolated runtime provision manifest")
        verify_runtime_record(api.resolve(), mcroot, runtime_provision["files"])
    assets_root = args.assets_root or repo / "build/e2e/runtime/assets"
    if not (assets_root / "indexes" / (vanilla["assetIndex"]["id"] + ".json")).is_file():
        assets_root = mcroot / "assets"
    asset_index = assets_root / "indexes" / (vanilla["assetIndex"]["id"] + ".json")
    if not asset_index.is_file():
        raise ValueError("Minecraft asset index is missing; run --prepare-assets for this exact target first")
    if vanilla["assetIndex"].get("sha1") and digest_with(asset_index, "sha1") != vanilla["assetIndex"]["sha1"]:
        raise ValueError("Minecraft asset index does not match exact official metadata")
    java = args.java.resolve()
    if runtime_provision:
        receipt = bundled_extension_verifier().prepare.decode_json(Path(runtime_provision["path"]).read_text(encoding="utf-8"))
        if receipt.get("java") != str(java):
            raise ValueError("Java executable does not match the isolated runtime provision manifest")
    if not java.is_file() or not os.access(java, os.X_OK):
        raise ValueError("An executable source-pinned game Java runtime is required")
    checked_java = subprocess.run([str(java), "-version"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                  text=True, timeout=15, check=False)
    if checked_java.returncode != 0 or re.search(r'version "' + str(java_required) + r'(?:[."]|$)', checked_java.stdout) is None:
        raise ValueError("Packaged acceptance must run on source-pinned Java" + str(java_required))
    selected_natives = native_libraries(vanilla, mcroot)
    if runtime_provision:
        for native, _ in selected_natives:
            verify_runtime_record(native, mcroot, runtime_provision["files"])
    output.mkdir(parents=True)
    native_files = extract_natives(selected_natives, output / "natives")
    game = output / "game"
    (game / "mods").mkdir(parents=True)
    config = game / "config/openallay"
    config.mkdir(parents=True)
    (game / "saves").mkdir()
    shutil.copyfile(artifact, game / "mods" / artifact.name)
    if loader == "fabric":
        shutil.copyfile(api, game / "mods" / api.name)
    write_json(config / "models.json", models)
    write_json(config / "unrestricted-javascript.json", {"enabled": args.enable_unrestricted})
    write_json(config / "experimental-commands.json", {"enabled": False})
    write_json(config / "display.json", fixture_display_config())
    write_json(config / "voice.json", fixture_voice_config())
    fps = 10 if args.low_impact else 30
    graphical = graphical_scenario(args.scenario)
    options = "onboardAccessibility:false\njoinedFirstServer:true\nrenderDistance:4\nsimulationDistance:5\nmaxFps:" + str(fps) + "\npauseOnLostFocus:false\n"
    if graphical:
        options += "lang:zh_cn\nguiScale:1\n"
    (game / "options.txt").write_text(options, encoding="utf-8")
    world = WORLD_PREFIX + loader + "-" + run_id
    values = {"natives_directory": output / "natives", "launcher_name": "OpenAllayPackagedAcceptance",
              "launcher_version": "1", "classpath": os.pathsep.join(classpath), "classpath_separator": os.pathsep,
              "library_directory": mcroot / "libraries", "auth_player_name": "BuilderProbe",
              "version_name": profile_id, "game_directory": game, "assets_root": assets_root.resolve(),
              "assets_index_name": vanilla["assetIndex"]["id"],
              "auth_uuid": offline_uuid("BuilderProbe"),
              # This harness has one synthetic offline session, not an authenticated account.
              # Minecraft Main defaults userType to legacy; this path never authenticates an MSA session.
              "auth_access_token": "0", "clientid": "", "auth_xuid": "", "user_type": "legacy",
              "version_type": version_type,
              "resolution_width": "850" if graphical else "854" if args.low_impact else "1100",
              "resolution_height": "480" if args.low_impact else "700"}
    if not re.fullmatch(r"[A-Za-z0-9_]{1,16}", values["auth_player_name"]):
        raise ValueError("Synthetic Minecraft username must contain 1 to 16 simple characters")
    jvm = expand_arguments(vanilla["arguments"]["jvm"] + extra_jvm, values)
    game_args = expand_arguments(vanilla["arguments"]["game"] + extra_game, values, {"has_custom_resolution": True})
    source_files = ["common/src/main/java/dev/openallay/guide/e2e/GuideGraphicalRegressionProbe.java",
                    "common/src/main/java/dev/openallay/guide/e2e/GuideClientE2EController.java",
                    "scripts/e2e-model-fixture.py", "scripts/run-packaged-builder-acceptance.py"]
    source_manifest = {"packagedArtifact": identity,
                       "files": {name: digest(repo / name) for name in source_files if (repo / name).is_file()}}
    source_manifest_path = output / "source-manifest.json"
    write_json(source_manifest_path, source_manifest)
    properties = {"enabled": "true", "createWorld": world, "scenario": args.scenario,
                  "sourceRevision": os.environ.get("OPENALLAY_E2E_SOURCE_REVISION", identity.get("sha256", "UNRECORDED")),
                  "sourceManifestSha256": digest(source_manifest_path),
                  "question": args.question or ("OpenAllay E2E UI live UX regressions hold" if args.scenario == "ui-live-ux-regressions"
                                               else "OpenAllay E2E UI " + args.scenario.removeprefix("ui-").replace("-", " ")
                                               if ui_scenario else "OpenAllay E2E Builder " + args.scenario.removeprefix("builder-")),
                  "report": str(output / "report.json"), "trace": str(output / "trace.json"),
                  "session": run_id, "modelMode": "client", "screenshotRoot": str(output / "screenshots"),
                  "shutdown": "false", "shutdownAfterScreenshots": "true",
                  "timeoutSeconds": str(args.timeout_seconds)}
    if args.professional_screenshots:
        properties["screenshotMatrix"] = "professional"
    if args.screenshot_manual_profile:
        properties["screenshotManualProfile"] = args.screenshot_manual_profile
    if args.screenshot_automatic_profile:
        properties["screenshotAutomaticProfile"] = args.screenshot_automatic_profile
    if args.review_package:
        properties["reviewPackage"] = str(args.review_package.resolve())
    if args.cancel_on_tool_start:
        properties["cancelOnToolStart"] = "true"
    heap = ["-Xms256M", "-Xmx1536M"] if args.low_impact else ["-Xms512M", "-Xmx3G"]
    command = [str(java)] + heap + jvm + proxy_arguments(args.http_proxy_from_env)
    if args.model_diagnostics:
        command.append("-Dopenallay.model.diagnostics=true")
    if loader == "fabric":
        command.append("-Dfabric.development=false")
    command += ["-Dopenallay.e2e." + key + "=" + value for key, value in properties.items()]
    command += [main_class] + game_args
    files = [p for p in game.rglob("*") if p.is_file()]
    manifest = {"loader": loader, "minecraft": minecraft_target, "javaRequired": java_required,
                "runId": run_id, "world": world, "scenario": args.scenario,
                "gameDirectory": str(game), "packagedArtifact": identity,
                "unrestrictedOptIn": args.enable_unrestricted, "lowImpact": args.low_impact,
                "modelDiagnostics": args.model_diagnostics, "timeoutSeconds": args.timeout_seconds, "wallTimeoutSeconds": args.timeout_seconds + 60, "command": command,
                "classPath": classpath, "nativeFiles": native_files, "nativesDirectory": str(output / "natives"),
                "runtimeProvision": ({"path": runtime_provision["path"], "sha256": runtime_provision["sha256"]}
                                     if runtime_provision else None),
                "preparedFiles": {str(p.relative_to(output)): digest(p) for p in files},
                "report": str(output / "report.json"), "trace": str(output / "trace.json"),
                "screenshots": str(output / "screenshots"),
                "uiCapture": ui_scenario, "expectedTerminalOutcome": UI_OUTCOMES.get(args.scenario),
                "professionalScreenshots": args.professional_screenshots,
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
    if args.scenario not in ("builder-reload", "builder-live-undo"):
        raise ValueError("World resume requires builder-reload or builder-live-undo")
    if args.scenario == "builder-live-undo" and (not args.question or not args.model_config):
        raise ValueError("Live undo requires an explicit ordinary provider question and environment-reference model config")
    if args.loader and args.loader != prior["loader"]:
        raise ValueError("Resume loader must match the prior manifest")
    if args.minecraft_target != prior["minecraft"]:
        raise ValueError("Resume exact Minecraft target must match the prior manifest")
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
        original_identity = packaged_artifact(original, prior["loader"], previous_identity.get("modVersion", MOD_VERSION), repo, prior["minecraft"])
        if original_identity["sha256"] != previous_identity["sha256"]:
            raise ValueError("Original packaged artifact hash does not match the accepted manifest")
        replacement = args.jar.resolve()
        new_identity = packaged_artifact(replacement, prior["loader"], previous_identity.get("modVersion", MOD_VERSION), repo, prior["minecraft"])
        identity_fields = ("loader", "minecraft", "modVersion", "bundledBuilder", "bundledBuilderSha256")
        if any(original_identity[field] != new_identity[field] for field in identity_fields):
            raise ValueError("Harness upgrade must preserve loader, Minecraft, OpenAllay version, and exact bundled Builder bytes")
        if not new_identity["nativeWorldBootstrapPresent"]:
            raise ValueError("Harness upgrade must retain the opt-in native E2E bootstrap")
        backup = safe_output(previous / "evidence/harness-artifacts" / (original_identity["sha256"] + ".jar"), repo)
        upgrade = {"developmentInstrumentationOnly": True,
                   "oldSha256": original_identity["sha256"], "newSha256": new_identity["sha256"],
                   "bundledBuilderSha256": original_identity["bundledBuilderSha256"],
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
    # Resume is restricted by default; full access needs a separate explicit opt-in.
    # Change only the prior disposable generated config, never an ordinary profile.
    write_json(config / "unrestricted-javascript.json", {"enabled": args.enable_unrestricted})
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
                "unrestrictedOptIn": args.enable_unrestricted,
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


def validate_ui_capture(manifest):
    report_path = Path(manifest["report"])
    if not report_path.is_file():
        raise ValueError("Client closed without a UI evidence report")
    report = json.loads(report_path.read_text(encoding="utf-8"))
    expected = UI_OUTCOMES.get(manifest["scenario"])
    if not expected or report.get("outcome") != expected:
        raise ValueError("UI evidence terminal outcome does not match its explicit scenario")
    if manifest["scenario"] == "ui-stop":
        stop = report.get("actualStop", {})
        if not all(stop.get(field) is True for field in ("requested", "accepted", "terminalCancelled", "pendingToolHasNoNormalizedResult")):
            raise ValueError("UI stop evidence did not retain an accepted real cancellation")
    if graphical_scenario(manifest["scenario"]):
        frames = report.get("nativeFrames", [])
        if not frames or report.get("interactKeyRestored") is not True:
            raise ValueError("Native graphical report lacks frames or restored interaction key")
        if manifest["scenario"] == "ui-manual-regressions" and report.get("themeChangeCount", 0) < 4:
            raise ValueError("Manual native graphical report lacks repeated theme frames")
        for frame in frames:
            path = Path(frame["path"])
            if not path.is_file() or digest(path) != frame["sha256"] or frame.get("source") != "native-mainRenderTarget":
                raise ValueError("Native graphical frame is missing or changed")
        if report.get("microphoneCaptureAttempted") is not False:
            raise ValueError("Native graphical report attempted microphone capture")
        if manifest["scenario"] == "ui-manual-regressions":
            if not report.get("export", {}).get("containsCurrentQuestionAndAnswer"):
                raise ValueError("Manual native graphical report lacks actual export")
        else:
            validate_live_ux_receipts(report)
    else:
        validate_final_screenshot(manifest)
    return report


def validate_live_ux_receipts(report):
    """Mechanical receipt gate only. Retained native PNGs still require separate expert review."""
    expected_names = {
        "live-01-initial-character-focus", "live-02-blur-resize-typed-ptt",
        "live-03-follow-up-accepted-active", "live-04-steer-accepted-active",
        "live-05-native-hover-known-budget-unknown-cost", "live-06-comfortable-tool-summary",
        "live-07-tool-detail-native-recipe", "live-08-native-child-priority-no-parent-drawer",
        "live-09-compact-tool-summary", "live-10-passive-hud-latest-48-no-carousel",
        "live-11-passive-hud-explicit-unbound-hint", "live-12-interactive-hud-opens-at-latest",
        "live-13-reader-anchor-with-new-content", "live-14-reader-native-latest-restores",
        "live-15-reader-prior-request-cards", "live-16-actual-card-title-description-native-toast",
        "live-17-visible-guide-owned-toast-hidden",
    }
    if not expected_names.issubset({value.get("name") for value in report.get("nativeFrames", [])}):
        raise ValueError("Live native scenario is missing required frame evidence")
    if report.get("pttKeyRestored") is not True or not all(report.get("focusReceipts", {}).values()):
        raise ValueError("Live native focus/key restoration receipts are incomplete")
    detail = report.get("actualNativeRecipeDetail", {})
    if not detail.get("detailNativeRecipeIds") or not detail.get("detailCardIds") or not detail.get("detailToolId"):
        raise ValueError("Actual right-detail native recipe paint is missing")
    capsule = report.get("actualCapsuleClicked", {})
    if not capsule.get("id") or capsule.get("action") not in ("BrowseRecipes", "ExactRecipe"):
        raise ValueError("Actual native capsule callback identity is missing")
    hover = report.get("nativeTelemetryHover", {})
    if hover.get("requestedNativeFrame", 0) <= 0 or hover.get("requestedLineCount", 0) < 3:
        raise ValueError("Native hover multiline extraction is missing")
    pending = report.get("pendingReceiptOrder", [])
    if (pending != [report.get("followUpAccepted", {}).get("id"), report.get("steerAccepted", {}).get("id")]
            or report.get("followUpAccepted", {}).get("kind") != "FOLLOW_UP"
            or report.get("steerAccepted", {}).get("kind") != "STEER"
            or not report.get("admittedSteerTimeline") or not report.get("followUpRequestId")):
        raise ValueError("Actual Follow-up/Steer admission order is missing")
    toast = report.get("actualCardNativeToast", {})
    hidden = report.get("actualOwnedToastHiddenOnGuide", {})
    if (not toast.get("title") or not toast.get("description") or toast.get("frame", 0) <= 0
            or toast.get("height") != 64 or toast.get("slots") != 2 or toast.get("noClickTarget") is not True
            or hidden.get("ownedHidden") is not True or hidden.get("visible") is not False
            or hidden.get("hideOrder", 0) <= hidden.get("showOrder", 0)):
        raise ValueError("Actual meaningful owned native card toast paint/hide is missing")
    source = report.get("sourceIdentity", {})
    if not source.get("revision") or source.get("revision") == "UNRECORDED" or not re.fullmatch(r"[0-9a-f]{64}", source.get("manifestSha256", "")):
        raise ValueError("Live source revision/manifest hash was not recorded")


def validate_final_screenshot(manifest):
    if manifest.get("professionalScreenshots"):
        final_name = "25-native-world-final.png"
    elif manifest.get("scenario") in ("ui-stop", "ui-provider-failure"):
        # These UI-only flows finish the actual native settings matrix at stage 11.
        # They never execute the Builder world screenshot stage; terminal outcome
        # and cancellation facts remain independently required by validate_ui_capture.
        final_name = "10-wide-about.png"
    else:
        final_name = "11-native-world-builds.png"
    screenshots = list(Path(manifest["screenshots"]).rglob(final_name))
    if len(screenshots) != 1 or screenshots[0].read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("Screenshot matrix did not retain its final PNG capture")


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
    native_directory = safe_output(Path(manifest["nativesDirectory"]), repo)
    original = safe_output(Path(manifest.get("resumeFrom", output)), repo)
    if native_directory != original / "natives":
        raise ValueError("Prepared native directory must belong to the original disposable run")
    native_actual = {path.relative_to(native_directory).as_posix(): digest(path)
                     for path in native_directory.rglob("*") if path.is_file()}
    if (native_actual != manifest["nativeFiles"] or any(path.is_symlink() for path in native_directory.rglob("*"))):
        raise ValueError("Prepared extracted natives changed")
    provision = manifest.get("runtimeProvision")
    if provision:
        mcroot = repo.resolve() / "build/e2e/runtime" / manifest["minecraft"] / "minecraft"
        receipt_path = mcroot / ".provision" / (manifest["loader"] + "-runtime.json")
        if provision.get("path") != str(receipt_path) or digest(receipt_path) != provision.get("sha256"):
            raise ValueError("Reviewed isolated runtime provision manifest changed")
        runtime = validate_runtime_classpath(manifest["classPath"], mcroot, manifest["loader"], repo, manifest["minecraft"])
        verifier = bundled_extension_verifier()
        for relative in runtime["files"]:
            verifier.prepare.relative_path(relative)
            verify_runtime_record((mcroot / relative).resolve(), mcroot, runtime["files"])
    models = validate_model_config(game / "config/openallay/models.json")
    launch_environment = os.environ.copy()
    if graphical_scenario(manifest["scenario"]):
        if any(profile.get("enabled") and (profile.get("model") != "openallay-e2e-fixture"
                or urlsplit(profile.get("baseUrl", "")).hostname != "127.0.0.1"
                or urlsplit(profile.get("baseUrl", "")).scheme != "http"
                or profile.get("credentialRef") != "env:OPENALLAY_E2E_FIXTURE_KEY")
                for profile in models["profiles"]):
            raise ValueError("Graphical acceptance can use only the deterministic loopback fixture")
        launch_environment["OPENALLAY_E2E_FIXTURE_KEY"] = "openallay-local-fixture-not-a-secret"
    for profile in models["profiles"]:
        if profile.get("enabled") and not launch_environment.get(profile["credentialRef"].removeprefix("env:")):
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
                                       start_new_session=True, env=launch_environment)
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
    if manifest.get("uiCapture"):
        report = validate_ui_capture(manifest)
        print("UI evidence capture completed; actual terminal outcome " + report["outcome"] + ": " + manifest["report"], flush=True)
    else:
        validate_report(manifest["report"])
        validate_final_screenshot(manifest)
        print("Packaged Builder native acceptance PASSED: " + manifest["report"], flush=True)


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("loader", choices=("fabric", "neoforge"), nargs="?")
    result.add_argument("--run-id")
    result.add_argument("--minecraft-target", choices=minecraft_targets(), default="26.2")
    result.add_argument("--scenario", choices=SCENARIOS, default="builder-restricted")
    result.add_argument("--enable-unrestricted", action="store_true",
                        help="Optional explicit full-access test opt-in; Builder writes work without it; never changes ordinary profiles")
    result.add_argument("--question")
    result.add_argument("--mod-version", help="Explicit packaged release version; fresh runs default to checked-in gradle.properties")
    result.add_argument("--professional-screenshots", action="store_true")
    result.add_argument("--screenshot-manual-profile", help="Explicit configured profile ID for manual-context screenshots")
    result.add_argument("--screenshot-automatic-profile", help="Explicit configured disabled public-model profile ID")
    result.add_argument("--review-package", type=Path, help="Explicit local JAR for advisory review screenshots")
    result.add_argument("--cancel-on-tool-start", action="store_true", help="UI-stop only: request actual cancellation when the Tool starts")
    result.add_argument("--model-diagnostics", action="store_true",
                        help="Opt in to prepared-JVM model diagnostics; default off, retained on resumed phases")
    result.add_argument("--timeout-seconds", type=int, default=300,
                        help="Development harness deadline; Java supervisor uses this deadline plus 60 seconds")
    result.add_argument("--low-impact", action="store_true", help="Disposable client only: 854x480, FPS10, 256M/1536M heap; retains render4/simulation5")
    result.add_argument("--http-proxy-from-env", action="store_true", help="Explicit JVM HTTP(S) proxy from conventional env settings; local/credential-free only")
    result.add_argument("--resume-prepared", type=Path, help="Prepare a reload phase using only a prior manifest's disposable world under build/e2e")
    result.add_argument("--jar", type=Path, help="Default production-named built artifact; no source classes")
    result.add_argument("--model-config", type=Path, help="Explicit secret-free model config with env credential references")
    result.add_argument("--fixture-port", type=int, default=18765, help="Loopback fixture is started separately")
    result.add_argument("--minecraft-root", type=Path, default=Path.home() / "Library/Application Support/minecraft")
    result.add_argument("--gradle-cache", type=Path, default=Path.home() / ".gradle/caches/modules-2/files-2.1")
    result.add_argument("--fabric-api", type=Path)
    result.add_argument("--assets-root", type=Path, help="Matching exact target assets; defaults to ignored runtime cache then installed assets")
    result.add_argument("--prepare-assets", "--fetch-assets", action="store_true", help="Explicitly fetch/hash-check exact target official assets into ignored runtime assets; no game launch")
    result.add_argument("--java", type=Path, default=Path("/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home/bin/java"))
    result.add_argument("--launch-prepared", type=Path, help="Explicitly launch an already prepared/reviewed directory; otherwise only prepare")
    return result


def main(argv=None):
    args = parser().parse_args(argv)
    try:
        if args.prepare_assets:
            if args.launch_prepared:
                raise ValueError("Asset preparation cannot launch a client")
            prepare_assets(args.minecraft_root, args.assets_root or REPO / "build/e2e/runtime/assets", minecraft_target=args.minecraft_target)
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
