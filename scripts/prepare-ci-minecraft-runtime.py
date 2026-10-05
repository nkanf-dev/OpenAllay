#!/usr/bin/env python3
"""Provision exact source-pinned official Minecraft production runtimes for packaged CI clients.

Only this explicit command downloads files or runs the official loader installer.
No game, Gradle task, player account, existing world or source classes are used.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import signal
import subprocess
from urllib.parse import unquote, urlsplit
from urllib.request import HTTPRedirectHandler, build_opener
import uuid
import zipfile

REPO = Path(__file__).resolve().parents[1]
MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
FABRIC_INSTALLER = "1.1.0"
HOSTS = frozenset(("piston-meta.mojang.com", "piston-data.mojang.com",
                   "launchermeta.mojang.com", "launcher.mojang.com",
                   "libraries.minecraft.net", "maven.fabricmc.net",
                   "meta.fabricmc.net", "maven.neoforged.net"))
MAX_METADATA = 16 * 1024 * 1024
MAX_ARTIFACT = 512 * 1024 * 1024
CHUNK = 1024 * 1024
def target_reader():
    from importlib.util import module_from_spec, spec_from_file_location
    spec = spec_from_file_location("ci_runtime_targets", REPO / "scripts/minecraft-target.py")
    reader = module_from_spec(spec)
    spec.loader.exec_module(reader)
    return reader


def minecraft_targets(repo=REPO):
    return tuple(sorted(path.stem for path in (Path(repo) / "gradle/minecraft-targets").glob("*.properties")))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def file_hash(path, algorithm="sha256"):
    result = hashlib.new(algorithm)
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(CHUNK), b""):
            result.update(chunk)
    return result.hexdigest()


def relative_file(root, value):
    require(isinstance(value, str) and value and "\\" not in value and "%" not in value,
            "Unsafe runtime relative path")
    parts = value.split("/")
    require(all(re.fullmatch(r"[A-Za-z0-9_+.-]+", part) and part not in (".", "..") for part in parts),
            "Unsafe runtime relative path: " + value)
    target = Path(root).joinpath(*parts)
    require(not target.is_symlink() and target.resolve().is_relative_to(Path(root).resolve()),
            "Runtime path escapes its isolated directory")
    return target


def safe_root(path, repo=REPO):
    repository = Path(repo).resolve()
    requested = Path(path).absolute()
    logical_root = repository / "build/e2e/runtime"
    require(requested.is_relative_to(logical_root), "Runtime path must be inside ignored build/e2e/runtime")
    for candidate in (repository / "build", repository / "build/e2e", logical_root):
        require(not candidate.is_symlink(), "Ignored runtime parent must not be a symlink")
    candidate = logical_root
    for part in requested.relative_to(logical_root).parts:
        candidate = candidate / part
        require(part not in (".", "..") and not candidate.is_symlink(), "Runtime path must not traverse a symlink")
    root = logical_root.resolve()
    require(root.is_relative_to(repository), "Ignored runtime directory must not escape the repository")
    require(not requested.is_symlink(), "Minecraft root must not be a symlink alias")
    target = requested.resolve()
    require(target != root and target.is_relative_to(root),
            "minecraft-root must be a private directory under ignored build/e2e/runtime")
    require(target.name == "minecraft", "minecraft-root must end with minecraft")
    return target


def official_url(url):
    require(isinstance(url, str), "Official URL must be text")
    parsed = urlsplit(url)
    require(parsed.scheme == "https" and parsed.hostname in HOSTS and parsed.port in (None, 443)
            and not parsed.username and not parsed.password and not parsed.query and not parsed.fragment,
            "Runtime URL must use an exact allowed official HTTPS host, without credentials")
    require(parsed.path.startswith("/") and "\\" not in unquote(parsed.path)
            and not any(part in (".", "..") for part in unquote(parsed.path).split("/")),
            "Unsafe official URL path")
    return url


class OfficialRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        official_url(newurl)
        return super().redirect_request(request, fp, code, msg, headers, newurl)


def open_official(url):
    # Per-command opener only. No monkey patches, mirror URLs or global redirects.
    return build_opener(OfficialRedirect()).open(official_url(url), timeout=120)


def content_size(response, maximum):
    value = response.headers.get("Content-Length")
    if value is None:
        return None
    require(re.fullmatch(r"[0-9]+", value), "Invalid official Content-Length")
    size = int(value)
    require(0 < size <= maximum, "Official response is empty or exceeds its byte limit")
    return size


def fetch_bytes(url, maximum=MAX_METADATA):
    with open_official(url) as response:
        official_url(response.geturl())
        size = content_size(response, maximum)
        result = bytearray()
        while True:
            chunk = response.read(min(CHUNK, maximum + 1 - len(result)))
            if not chunk:
                break
            result.extend(chunk)
            require(len(result) <= maximum and (size is None or len(result) <= size),
                    "Official metadata exceeds its byte limit or Content-Length")
        require(result and (size is None or len(result) == size), "Official metadata response is empty or truncated")
    return bytes(result)


def json_bytes(data):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, "Duplicate metadata key: " + key)
            result[key] = value
        return result
    def no_constant(value):
        raise ValueError("Nonstandard JSON constant: " + value)
    require(len(data) <= MAX_METADATA, "Metadata exceeds byte limit")
    return json.loads(data.decode("utf-8"), object_pairs_hook=unique, parse_constant=no_constant)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".part-" + uuid.uuid4().hex)
    try:
        temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def valid_hash(value, algorithm="sha1"):
    require(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{" + str(hashlib.new(algorithm).digest_size * 2) + r"}", value),
            "Expected lowercase " + algorithm + " checksum")
    return value


def download(url, destination, expected_sha1, expected_size=None, maximum=MAX_ARTIFACT):
    """Stream into a temporary file; accept caches only after checksum/size checks."""
    official_url(url)
    valid_hash(expected_sha1)
    if expected_size is not None:
        require(type(expected_size) is int and 0 < expected_size <= maximum,
                "Invalid official artifact size")
    destination = Path(destination)
    require(not destination.is_symlink(), "Artifact cache must not be a symlink")
    if destination.is_file() and 0 < destination.stat().st_size <= maximum:
        if (expected_size is None or destination.stat().st_size == expected_size) and file_hash(destination, "sha1") == expected_sha1:
            return destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.name + ".part-" + uuid.uuid4().hex)
    try:
        with open_official(url) as response, temporary.open("xb") as stream:
            official_url(response.geturl())
            size = content_size(response, maximum)
            require(size is None or expected_size is None or size == expected_size,
                    "Official artifact Content-Length differs from metadata size")
            limit = expected_size or size or maximum
            digest = hashlib.sha1()
            count = 0
            for chunk in iter(lambda: response.read(CHUNK), b""):
                count += len(chunk)
                require(count <= limit, "Official artifact exceeds its declared size or byte limit")
                digest.update(chunk)
                stream.write(chunk)
        require(count > 0 and (size is None or count == size)
                and (expected_size is None or count == expected_size) and digest.hexdigest() == expected_sha1,
                "Official artifact failed checksum or size verification")
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return destination


def checksum_artifact(url, destination):
    checksum = fetch_bytes(official_url(url) + ".sha1", 1024).decode("ascii").strip()
    valid_hash(checksum)
    return download(url, destination, checksum)


def maven_path(coordinate):
    require(isinstance(coordinate, str), "Maven coordinate must be text")
    spec = coordinate.split("@")
    require(len(spec) in (1, 2), "Unsafe Maven extension")
    extension = spec[1] if len(spec) == 2 else "jar"
    require(extension in ("jar", "zip", "txt", "tsrg.lzma"), "Unsupported official Maven extension")
    parts = spec[0].split(":")
    require(len(parts) in (3, 4) and all(re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_+.-]*", part)
            and part not in (".", "..") for part in parts), "Unsafe Maven coordinate: " + coordinate)
    group, artifact, version = parts[:3]
    filename = artifact + "-" + version + ("-" + parts[3] if len(parts) == 4 else "") + "." + extension
    value = group.replace(".", "/") + "/" + artifact + "/" + version + "/" + filename
    relative_file(Path("/maven"), value)
    return value


def neoforge_identity(pins):
    version = pins["neoforge_version"]
    if pins["minecraft_version"] == "1.20.1":
        require(version.startswith("1.20.1-"), "Early Forge-shaped NeoForge pin differs")
        return "forge", "1.20.1-forge-" + version.removeprefix("1.20.1-")
    return "neoforge", "neoforge-" + version


def installer_data(install, key):
    value = install.get("data", {}).get(key, {}).get("client")
    require(isinstance(value, str) and value, "Missing official client processor data: " + key)
    return value


def data_library(root, install, key):
    value = installer_data(install, key)
    require(value.startswith("[") and value.endswith("]"), "Expected official data Maven coordinate: " + key)
    return relative_file(root / "libraries", maven_path(value[1:-1]))


def client_processors(install):
    processors = [processor for processor in install.get("processors", [])
                  if "sides" not in processor or "client" in processor["sides"]]
    require(processors, "Official client processor list is empty")
    return processors


def processor_outputs(root, install):
    """Known official mapped-client and unobfuscated-client processor mechanisms."""
    processors = client_processors(install)
    keys = set()
    allowed = [
        ["--task", "PROCESS_MINECRAFT_JAR", "--no-mod-manifest", "--input", "{MINECRAFT_JAR}",
         "--output", "{PATCHED}", "--extract-libraries-to", "{ROOT}/libraries/", "--apply-patches", "{BINPATCH}"],
        ["--task", "MCP_DATA", "--input", None, "--output", "{MAPPINGS}", "--key", "mappings"],
        ["--task", "DOWNLOAD_MOJMAPS", "--version", install["minecraft"], "--side", "{SIDE}", "--output", "{MOJMAPS}"],
        ["--task", "MERGE_MAPPING", "--left", "{MAPPINGS}", "--right", "{MOJMAPS}",
         "--output", "{MERGED_MAPPINGS}", "--classes", "--reverse-right"],
        ["--task", "MERGE_MAPPING", "--left", "{MAPPINGS}", "--right", "{MOJMAPS}",
         "--output", "{MERGED_MAPPINGS}", "--classes", "--fields", "--methods", "--reverse-right"],
        ["--input", "{MINECRAFT_JAR}", "--slim", "{MC_SLIM}", "--extra", "{MC_EXTRA}", "--srg", "{MERGED_MAPPINGS}"],
        ["--input", "{MC_SLIM}", "--output", "{MC_SRG}", "--names", "{MERGED_MAPPINGS}",
         "--ann-fix", "--ids-fix", "--src-fix", "--record-fix"],
        ["--clean", "{MC_SRG}", "--output", "{PATCHED}", "--apply", "{BINPATCH}"],
    ]
    # Additional shapes from the published mapped-client installers. Match the
    # processor owner and arguments, not Minecraft or tool version numbers.
    mapped_owner = ("net.neoforged.installertools", "installertools")
    mapped = [
        ["--task", "MERGE_MAPPING", "--merge", "{MAPPINGS}", "--base", "{MOJMAPS}",
         "--output", "{MERGED_MAPPINGS}", "--reverse-base"],
        ["--task", "PROCESS_MINECRAFT_JAR", "--input", "{MINECRAFT_JAR}",
         "--input-mappings", "{MOJMAPS}", "--output", "{PATCHED}",
         "--extract-libraries-to", "{ROOT}/libraries/", "--neoform-data", None,
         "--apply-patches", "{BINPATCH}"],
    ]
    def matches(args, templates):
        return any(len(args) == len(template) and all(expected is None or actual == expected
                   for actual, expected in zip(args, template)) for template in templates)

    # Omitted @jar and explicit @jar name the same Maven artifact. Keep all
    # other coordinate fields exact and bind identity to the verified download.
    libraries = set()
    for library in install.get("libraries", []):
        identity = maven_path(library["name"])
        artifact = library.get("downloads", {}).get("artifact")
        require(not artifact or artifact.get("path") == identity,
                "Official processor library path differs from its Maven coordinate")
        libraries.add(identity)
    for processor in processors:
        args = processor.get("args", [])
        jar = processor.get("jar", "")
        require(matches(args, allowed) or (tuple(jar.split(":")[:2]) == mapped_owner and matches(args, mapped)),
                "Unknown official client processor mechanism; inspect before admitting")
        require(maven_path(jar) in libraries and isinstance(processor.get("classpath"), list)
                and all(maven_path(coordinate) in libraries for coordinate in processor["classpath"]),
                "Official client processor dependency is absent from verified metadata")
        for option in ("--output", "--slim", "--extra"):
            if option in args:
                value = args[args.index(option) + 1]
                require(re.fullmatch(r"\{[A-Z_]+\}", value), "Unsafe processor output reference")
                keys.add(value[1:-1])
        if "MCP_DATA" in args or "--neoform-data" in args:
            option = "--neoform-data" if "--neoform-data" in args else "--input"
            coordinate = args[args.index(option) + 1]
            require(isinstance(coordinate, str) and coordinate.startswith("[") and coordinate.endswith("]")
                    and maven_path(coordinate[1:-1]) in libraries,
                    "Official mapping archive must be a verified installer dependency")
            if option == "--neoform-data":
                require(coordinate[1:-1].startswith("net.neoforged:neoform:")
                        and coordinate.endswith(":mappings@tsrg.lzma]"),
                        "Expected official NeoForm compressed mapping dependency")
    require("PATCHED" in keys, "Official processor must produce a patched client")
    return {key: data_library(root, install, key) for key in keys}


def verify_processor_outputs(root, install, outputs):
    for key, path in outputs.items():
        require(path.is_file() and path.stat().st_size > 0, "Official processor output is missing: " + key)
        if key + "_SHA" in install.get("data", {}):
            expected = valid_hash(installer_data(install, key + "_SHA").strip("'"))
            require(file_hash(path, "sha1") == expected, "Official processor output hash differs: " + key)
    for processor in client_processors(install):
        for value, checksum in processor.get("outputs", {}).items():
            require(re.fullmatch(r"\{[A-Z_]+\}", value) and re.fullmatch(r"\{[A-Z_]+\}", checksum),
                    "Unknown official processor output hash reference")
            path = data_library(root, install, value[1:-1])
            expected = valid_hash(installer_data(install, checksum[1:-1]).strip("'"))
            require(path.is_file() and file_hash(path, "sha1") == expected, "Official processor output checksum differs")

def read_pins(repo=REPO, minecraft_target="26.2"):
    require(minecraft_target in minecraft_targets(repo), "Unknown exact Minecraft target")
    result = target_reader().read_profile(Path(repo), minecraft_target)
    for key in ("minecraft_version", "java_version", "fabric_loader_version", "fabric_version", "neoforge_version"):
        require(re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_+.-]*", result[key]), "Unsafe runtime source pin: " + key)
    return result

def rules_allow(rules, os_name=None, arch=None, release=None, features=None):
    if not rules:
        return True
    os_name = os_name or {"Linux": "linux", "Darwin": "osx", "Windows": "windows"}[platform.system()]
    arch = arch or platform.machine()
    release = release or platform.release()
    features = features or {}
    allowed = False
    for rule in rules:
        require(rule.get("action") in ("allow", "disallow"), "Invalid library rule action")
        os_rule = rule.get("os", {})
        match = not os_rule.get("name") or os_rule["name"] == os_name
        match = match and (not os_rule.get("arch") or re.fullmatch(os_rule["arch"], arch) is not None)
        match = match and (not os_rule.get("version") or re.search(os_rule["version"], release) is not None)
        match = match and all(features.get(key, False) == value for key, value in rule.get("features", {}).items())
        if match:
            allowed = rule["action"] == "allow"
    return allowed


def library_downloads(library, os_name=None, arch=None, release=None):
    if not rules_allow(library.get("rules"), os_name, arch, release):
        return []
    result = []
    downloads = library.get("downloads", {})
    if downloads.get("artifact"):
        result.append(downloads["artifact"])
    if library.get("natives"):
        os_name = os_name or {"Linux": "linux", "Darwin": "osx", "Windows": "windows"}[platform.system()]
        classifier = library["natives"].get(os_name)
        if classifier:
            arch = arch or platform.machine()
            bits = "64" if arch.lower() in ("x86_64", "amd64", "aarch64", "arm64") else "32"
            classifier = classifier.replace("${arch}", bits)
            require(classifier in downloads.get("classifiers", {}), "Missing official native classifier")
            result.append(downloads["classifiers"][classifier])
    return result


def prepare_libraries(metadata, minecraft_root):
    files = []
    for library in metadata.get("libraries", []):
        for artifact in library_downloads(library):
            require(set(("path", "url", "sha1", "size")).issubset(artifact), "Incomplete official library download")
            target = relative_file(minecraft_root / "libraries", artifact["path"])
            download(artifact["url"], target, artifact["sha1"], artifact["size"])
            files.append(target)
    return files


def verify_downloaded_libraries(metadata, minecraft_root):
    """Check installer output without replacing bytes changed by a processor."""
    for library in metadata.get("libraries", []):
        for artifact in library_downloads(library):
            path = relative_file(minecraft_root / "libraries", artifact["path"])
            require(path.is_file() and path.stat().st_size == artifact["size"]
                    and file_hash(path, "sha1") == valid_hash(artifact["sha1"]),
                    "Official installer changed a verified input library: " + library["name"])


def claim_root(root, minecraft_target="26.2"):
    owner = {"purpose": "openallay-official-ci-client-runtime", "minecraft": minecraft_target}
    require(not root.is_symlink(), "Minecraft root must not be a symlink")
    root.mkdir(parents=True, exist_ok=True)
    control = root / ".provision"
    marker = control / "owner.json"
    if not marker.is_file():
        require(not list(root.iterdir()), "Refusing an existing launcher root; use a fresh private CI directory")
        write_json(marker, owner)
        write_json(root / "launcher_profiles.json", {"profiles": {}})
    else:
        require(json_bytes(marker.read_bytes()) == owner, "Runtime ownership marker differs")
    allowed = {".provision", "libraries", "versions", "launcher_profiles.json"}
    require(all(item.name in allowed for item in root.iterdir()),
            "Private runtime must not contain accounts, worlds, mods or player configuration")
    require(not any(item.is_symlink() for item in root.rglob("*")), "Private runtime cache contains a symlink")
    profiles = json_bytes((root / "launcher_profiles.json").read_bytes())
    require(isinstance(profiles.get("profiles"), dict) and not any(key in profiles for key in ("authenticationDatabase", "selectedUser", "accounts")),
            "Private launcher profile must not contain player account data")
    for profile in profiles["profiles"].values():
        require(not any(key in profile for key in ("gameDir", "javaDir", "javaArgs", "accessToken")),
                "Private launcher profile must not borrow a game or Java directory")


def check_java(java, major):
    require(java.is_file() and os.access(java, os.X_OK), "--java must be an executable source-pinned game Java binary")
    result = subprocess.run([str(java), "-version"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, timeout=15, check=False)
    require(result.returncode == 0 and re.search(r'version "' + str(major) + r'(?:[."]|$)', result.stdout),
            "Runtime must use the source-pinned Java major " + str(major))
    return result.stdout.strip()


def prepare_vanilla(root, pins):
    manifest_data = fetch_bytes(MANIFEST_URL)
    manifest = json_bytes(manifest_data)
    matches = [entry for entry in manifest["versions"] if entry["id"] == pins["minecraft_version"]]
    require(len(matches) == 1 and matches[0].get("type") == "release", "Exact official source-pinned release is missing")
    entry = matches[0]
    version = pins["minecraft_version"]
    target = root / "versions" / version / (version + ".json")
    download(entry["url"], target, entry["sha1"], maximum=MAX_METADATA)
    metadata = json_bytes(target.read_bytes())
    require(metadata.get("id") == version and metadata.get("javaVersion", {}).get("majorVersion") == int(pins["java_version"]),
            "Official vanilla metadata differs from source target/Java pins")
    client = metadata["downloads"]["client"]
    client_path = root / "versions" / version / (version + ".jar")
    download(client["url"], client_path, client["sha1"], client["size"])
    files = [target, client_path] + prepare_libraries(metadata, root)
    # Preserve the exact official discovery metadata for provenance. The offline
    # NeoForge installer sees the already-present verified vanilla JSON/JAR.
    (root / ".provision/version_manifest_v2.json").write_bytes(manifest_data)
    return metadata, files


def inspect_installer(jar, loader):
    with zipfile.ZipFile(jar) as archive:
        for info in archive.infolist():
            # Directories are legal; no archive content is extracted by this script.
            name = info.filename.rstrip("/")
            require(name and not name.startswith("/") and "\\" not in name
                    and all(part not in ("", ".", "..") for part in name.split("/"))
                    and not re.match(r"^[A-Za-z]:", name)
                    and (info.external_attr >> 16) & 0o170000 != 0o120000,
                    "Unsafe installer archive path")
            require(info.file_size <= MAX_ARTIFACT, "Installer entry exceeds byte limit")
        if loader == "fabric":
            return None, None
        install = json_bytes(archive.read("install_profile.json"))
        version = json_bytes(archive.read("version.json"))
    return install, version


def installer_command(loader, java, installer, root, pins):
    base = [str(java), "-jar", str(installer)]
    if loader == "fabric":
        return base + ["client", "-dir", str(root), "-mcversion", pins["minecraft_version"],
                       "-loader", pins["fabric_loader_version"], "-noprofile"]
    # Mapped-client processors download Mojang maps themselves. Their output
    # file is not the installer's LOCAL artifact cache. Do not pretend it is offline.
    options = ["--offline"] if pins.get("installer_network_policy", "offline") == "offline" else []
    return base + options + ["--installClient", str(root)]


def run_installer(command, root, loader):
    log = root / ".provision" / (loader + "-installer.log")
    # Do not permit inherited Java options or account-bearing launcher arguments.
    env = {key: value for key, value in os.environ.items()
           if key not in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")}
    write_json(root / ".provision" / (loader + "-installer-command.json"), {"command": command})
    with log.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=root / ".provision", env=env, stdout=output,
                                   stderr=subprocess.STDOUT, start_new_session=os.name == "posix")
        try:
            exit_code = process.wait(timeout=900)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            # Official processors are child JVMs. Stop only this owned installer
            # group on Linux CI, not unrelated Java/game processes.
            if os.name == "posix":
                os.killpg(process.pid, signal.SIGTERM)
            else:
                process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                if os.name == "posix":
                    os.killpg(process.pid, signal.SIGKILL)
                else:
                    process.kill()
                process.wait(timeout=10)
            raise
    require(exit_code == 0, "Official " + loader + " installer failed; see " + str(log))


def prepare_fabric(root, java, pins):
    installer = root / ".provision" / ("fabric-installer-" + FABRIC_INSTALLER + ".jar")
    url = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/" + FABRIC_INSTALLER + "/" + installer.name
    checksum_artifact(url, installer)
    inspect_installer(installer, "fabric")
    profile_id = "fabric-loader-" + pins["fabric_loader_version"] + "-" + pins["minecraft_version"]
    profile_url = ("https://meta.fabricmc.net/v2/versions/loader/" + pins["minecraft_version"]
                   + "/" + pins["fabric_loader_version"] + "/profile/json")
    expected = json_bytes(fetch_bytes(profile_url))
    require(expected.get("id") == profile_id and expected.get("inheritsFrom") == pins["minecraft_version"]
            and expected.get("mainClass") == "net.fabricmc.loader.impl.launch.knot.KnotClient",
            "Official pinned Fabric metadata differs from source pins")
    # Validate and preload every dependency before the official installer sees its
    # profile. The installer does not get a user-selected Maven mirror or URL.
    for library in expected["libraries"]:
        path = maven_path(library["name"])
        base = official_url(library.get("url", "https://maven.fabricmc.net/"))
        require(base.endswith("/"), "Fabric Maven base URL must end with slash")
        target = relative_file(root / "libraries", path)
        if library.get("sha1") and library.get("size"):
            download(base + path, target, library["sha1"], library["size"])
        else:
            checksum_artifact(base + path, target)
    write_json(root / ".provision/fabric-profile.json", expected)
    run_installer(installer_command("fabric", java, installer, root, pins), root, "fabric")
    profile_path = root / "versions" / profile_id / (profile_id + ".json")
    profile = json_bytes(profile_path.read_bytes())
    normalized = lambda value: {key: item for key, item in value.items() if key not in ("time", "releaseTime")}
    require(normalized(profile) == normalized(expected),
            "Fabric installed profile differs from official pinned metadata")
    require(profile.get("id") == profile_id and profile.get("inheritsFrom") == pins["minecraft_version"]
            and profile.get("mainClass") == "net.fabricmc.loader.impl.launch.knot.KnotClient",
            "Official installed Fabric profile differs from source pins")
    files = [installer, profile_path, root / ".provision/fabric-profile.json"]
    for library in profile["libraries"]:
        path = maven_path(library["name"])
        base = official_url(library.get("url", "https://maven.fabricmc.net/"))
        require(base.endswith("/"), "Fabric Maven base URL must end with slash")
        target = relative_file(root / "libraries", path)
        if library.get("sha1") and library.get("size"):
            download(base + path, target, library["sha1"], library["size"])
        else:
            checksum_artifact(base + path, target)
        files.append(target)
    loader_path = relative_file(root / "libraries", maven_path("net.fabricmc:fabric-loader:" + pins["fabric_loader_version"]))
    with zipfile.ZipFile(loader_path) as archive:
        loader_metadata = json_bytes(archive.read("fabric-installer.json"))
    require(loader_metadata.get("mainClass", {}).get("client") == profile["mainClass"], "Fabric loader main class differs")
    for library in loader_metadata["libraries"]["common"] + loader_metadata["libraries"].get("client", []):
        path = maven_path(library["name"])
        base = official_url(library.get("url", "https://maven.fabricmc.net/"))
        require(base.endswith("/"), "Fabric loader Maven URL must end with slash")
        target = relative_file(root / "libraries", path)
        if library.get("sha1") and library.get("size"):
            download(base + path, target, library["sha1"], library["size"])
        else:
            checksum_artifact(base + path, target)
        files.append(target)
    api_coordinate = "net.fabricmc.fabric-api:fabric-api:" + pins["fabric_version"]
    api_path = relative_file(root / "libraries", maven_path(api_coordinate))
    checksum_artifact("https://maven.fabricmc.net/" + maven_path(api_coordinate), api_path)
    with zipfile.ZipFile(api_path) as archive:
        api_metadata = json_bytes(archive.read("fabric.mod.json"))
    require(api_metadata.get("id") == "fabric-api" and api_metadata.get("version") == pins["fabric_version"],
            "Fabric API identity differs from checked-in source profile")
    files.append(api_path)
    return profile_id, api_path, files


def prepare_neoforge(root, java, pins):
    version = pins["neoforge_version"]
    artifact, profile_id = neoforge_identity(pins)
    installer = root / ".provision" / (artifact + "-" + version + "-installer.jar")
    url = "https://maven.neoforged.net/releases/net/neoforged/" + artifact + "/" + version + "/" + installer.name
    checksum_artifact(url, installer)
    install, expected = inspect_installer(installer, "neoforge")
    require(install.get("minecraft") == pins["minecraft_version"] and install.get("version") == profile_id
            and expected.get("id") == profile_id and expected.get("inheritsFrom") == pins["minecraft_version"]
            and expected.get("mainClass") in ("net.neoforged.fml.startup.Client", "cpw.mods.bootstraplauncher.BootstrapLauncher"),
            "Official NeoForge installer metadata differs from source pins")
    write_json(root / ".provision/neoforge-install_profile.json", install)
    write_json(root / ".provision/neoforge-version.json", expected)
    files = [installer, root / ".provision/neoforge-install_profile.json", root / ".provision/neoforge-version.json"]
    outputs = processor_outputs(root, install)
    # Regenerate unchecked intermediate/patched output. Never trust opaque caches,
    # and verify every checksum the official installer actually declares.
    for path in outputs.values():
        path.unlink(missing_ok=True)
    files += prepare_libraries(install, root) + prepare_libraries(expected, root)
    mapped_client = "MOJMAPS" in outputs
    mappings = None
    if mapped_client:
        vanilla = json_bytes((root / "versions" / pins["minecraft_version"] / (pins["minecraft_version"] + ".json")).read_bytes())
        mappings = vanilla["downloads"]["client_mappings"]
        official_url(mappings["url"])
        valid_hash(mappings["sha1"])
        require(type(mappings["size"]) is int and 0 < mappings["size"] <= MAX_ARTIFACT,
                "Official client mapping byte size is invalid")
    installer_pins = {**pins, "installer_network_policy": "official-mojang-mappings" if mapped_client else "offline"}
    run_installer(installer_command("neoforge", java, installer, root, installer_pins), root, "neoforge")
    if mappings:
        path = outputs["MOJMAPS"]
        require(path.is_file() and path.stat().st_size == mappings["size"]
                and file_hash(path, "sha1") == mappings["sha1"],
                "Official processor Mojang client mappings differ from exact verified vanilla metadata")
    profile_path = root / "versions" / profile_id / (profile_id + ".json")
    actual = json_bytes(profile_path.read_bytes())
    require(actual == expected, "NeoForge installed profile differs from official installer version.json")
    verify_downloaded_libraries(install, root)
    verify_downloaded_libraries(actual, root)
    verify_processor_outputs(root, install, outputs)
    universal = relative_file(root / "libraries", maven_path("net.neoforged:" + artifact + ":" + version + ":universal"))
    require(universal.is_file(), "Official NeoForge universal artifact is required")
    with zipfile.ZipFile(outputs["PATCHED"]) as archive:
        require(archive.testzip() is None, "Official patched client JAR is corrupt")
    files += [profile_path, universal] + list(outputs.values())
    return profile_id, None, files

def provision(args, repo=REPO):
    require(args.loader in ("fabric", "neoforge"), "Unknown production loader")
    pins = read_pins(repo, args.minecraft_target)
    target = pins["minecraft_version"]
    root = safe_root(args.minecraft_root, repo)
    require(root == Path(repo).resolve() / "build/e2e/runtime" / target / "minecraft",
            "Exact target runtime must use build/e2e/runtime/<target>/minecraft")
    java = args.java.resolve()
    java_info = check_java(java, int(pins["java_version"]))
    claim_root(root, target)
    manifest_path = root / ".provision" / (args.loader + "-runtime.json")
    manifest_path.unlink(missing_ok=True)
    vanilla, files = prepare_vanilla(root, pins)
    vanilla_json = root / "versions" / target / (target + ".json")
    vanilla_json_sha256 = file_hash(vanilla_json)
    if args.loader == "fabric":
        profile_id, api, loader_files = prepare_fabric(root, java, pins)
    else:
        profile_id, api, loader_files = prepare_neoforge(root, java, pins)
    claim_root(root, target)
    verify_downloaded_libraries(vanilla, root)
    require(file_hash(vanilla_json) == vanilla_json_sha256,
            "Official installer changed the verified vanilla metadata")
    client = root / "versions" / target / (target + ".jar")
    require(client.stat().st_size == vanilla["downloads"]["client"]["size"]
            and file_hash(client, "sha1") == vanilla["downloads"]["client"]["sha1"],
            "Official installer changed the verified vanilla input JAR")
    unique_files = sorted(set(files + loader_files + [root / ".provision/version_manifest_v2.json"]))
    receipt = {"loader": args.loader, "minecraft": pins["minecraft_version"], "javaRequired": int(pins["java_version"]),
               "java": str(java), "javaInfo": java_info, "minecraftRoot": str(root), "profile": profile_id,
               "fabricApi": str(api) if api else None, "manifest": str(manifest_path),
               "preparedAt": datetime.now(timezone.utc).isoformat(),
               "sourceProfile": "gradle/minecraft-targets/" + target + ".properties",
               "sourceProfileSha256": file_hash(Path(repo) / "gradle/minecraft-targets" / (target + ".properties")),
               "sourceScriptSha256": file_hash(Path(__file__)),
               "pins": {key: pins[key] for key in ("minecraft_version", "java_version", "fabric_loader_version", "fabric_version", "neoforge_version")},
               "files": {str(path.relative_to(root)): {"sha256": file_hash(path), "sha1": file_hash(path, "sha1"), "size": path.stat().st_size} for path in unique_files},
               "assetsPrepared": False, "gameLaunched": False,
               "mechanism": "official-client-installer", "patchedCachePolicy": "regenerated-by-official-processor" if args.loader == "neoforge" else None,
               "installerNetworkPolicy": ("official-mojang-mappings" if args.loader == "neoforge"
                    and "MOJMAPS" in json_bytes((root / ".provision/neoforge-install_profile.json").read_bytes()).get("data", {})
                    else "offline" if args.loader == "neoforge" else "official-fabric-installer")}
    write_json(manifest_path, receipt)
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--loader", required=True, choices=("fabric", "neoforge"))
    parser.add_argument("--minecraft-target", choices=minecraft_targets(), default="26.2")
    parser.add_argument("--java", required=True, type=Path, help="Explicit source-pinned game Java runtime")
    parser.add_argument("--minecraft-root", required=True, type=Path,
                        help="Fresh or owned CI cache under build/e2e/runtime/<target>/minecraft")
    args = parser.parse_args()
    try:
        receipt = provision(args)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, subprocess.SubprocessError) as error:
        parser.exit(1, "Official CI runtime preparation failed: " + str(error) + "\n")
    print(json.dumps(receipt, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
