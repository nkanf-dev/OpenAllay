#!/usr/bin/env python3
"""Stage an immutable ordinary Forge14 Java8 JAR without recompiling native code."""
import argparse
import base64
import hashlib
from importlib.util import module_from_spec, spec_from_file_location
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tempfile
import zipfile

from stock8_selection_custody import POLICY_PATH as NATIVE_DELTA_POLICY, policy as native_delta_policy, verify_unselected_pair, verify_selection
from release_comment_custody import PATHS as COMMENT_PATHS, POLICY_PATH as COMMENT_POLICY_PATH, verify_pair as verify_comment_pair

ROOT = Path(__file__).resolve().parents[1]
PIN_PATH = "native-builds/forge1122-census/accepted-stock8-product.json"
CUSTODY_PATH = "distribution/stock8-release-custody.json"
BUILDER_PATH = "META-INF/openallay/bundled-extensions/openallay-builder.jar"
BUILDER_SHA = "bf8cfff9b82f84914aa1c84173d531f2256f537215a66a8d2057adc504632345"
ENGINE_SHA = "4054a951ff338a71b44e2ecd7eb44da5abe4a7dad79bd62cac49efabe5ceda77"
SHA = re.compile(r"[0-9a-f]{64}")


def require(ok, message):
    if not ok:
        raise ValueError(message)


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def pairs(items):
    result = {}
    for key, value in items:
        require(key not in result, "Duplicate JSON member")
        result[key] = value
    return result


def decode(raw):
    return json.loads(raw, object_pairs_hook=pairs)


def pin(root):
    value = decode((root / PIN_PATH).read_bytes())
    require(set(value) == {"provider", "jarSha256", "jarName", "productSource"}, "Exact stock8 product pin required")
    provider = value["provider"]
    require(set(provider) == {"artifactId", "runId", "sourceRevision", "sha256"}, "Exact provider pin required")
    require(all(type(provider[key]) is int and provider[key] > 0 for key in ("artifactId", "runId")), "Positive provider IDs required")
    require(SHA.fullmatch(value["jarSha256"]) and SHA.fullmatch(provider["sha256"]), "Exact product/archive hashes required")
    require(re.fullmatch(r"[0-9a-f]{40}", value["productSource"]) and value["productSource"] == provider["sourceRevision"], "Packing/provider source differs")
    version = re.findall(r"(?m)^version=([^\r\n]+)$", (root / "gradle.properties").read_text())
    require(version == ["0.4.4"] and value["jarName"] == "openallay-forge-1.12.2-0.4.4.jar", "Current actual stock8 release version required")
    return value


def safe(name):
    parts = PurePosixPath(name)
    require(name and not parts.is_absolute() and name == parts.as_posix() and
            not any(x in ("", ".", "..") for x in name.split("/")) and
            not any(c in name for c in "\\:\x00"), "Unsafe archive member")


def archive(raw):
    result = {}
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        require(len(z.infolist()) <= 20000 and sum(i.file_size for i in z.infolist()) <= 512 * 1024 * 1024, "Archive bounds exceeded")
        for item in z.infolist():
            safe(item.filename.rstrip("/") if item.is_dir() else item.filename)
            require(((item.external_attr >> 16) & 0o170000) != 0o120000, "Archive symlink refused")
            if item.is_dir():
                continue
            require(item.filename not in result and item.file_size <= 64 * 1024 * 1024, "Duplicate or large archive member")
            result[item.filename] = z.read(item)
    return result


def git(root, *arguments):
    return subprocess.check_output(["git", "-C", str(root), *arguments])


def source_custody(root, packing_source, native_custody, release_source):
    require(re.fullmatch(r"[0-9a-f]{40}", release_source), "Exact release source required")
    subprocess.run(["git", "-C", str(root), "merge-base", "--is-ancestor", packing_source, release_source], check=True)
    changed = git(root, "diff", "--no-renames", "--name-only", "-z", packing_source, release_source).decode().split("\0")
    admitted = {"gradle/minecraft-artifacts.json", "gradle/minecraft-target-loaders.json",
                "native-builds/forge16165/build.gradle", PIN_PATH, CUSTODY_PATH, COMMENT_POLICY_PATH, NATIVE_DELTA_POLICY}
    native_delta_paths={row['path'] for row in native_delta_policy(root)['files']}
    original_tree=set(git(root,"ls-tree","-r","--name-only","-z",packing_source).decode().split("\0"))
    for path in filter(None, changed):
        if path in native_delta_paths:
            original=git(root,"show",packing_source+":"+path) if path in original_tree else None
            verify_unselected_pair(root,path,original,(root/path).read_bytes())
            continue
        if path in COMMENT_PATHS:
            verify_comment_pair(root, path, git(root, "show", packing_source + ":" + path), (root / path).read_bytes())
            continue
        require(path in admitted or path in ("README.md", "README.zh-CN.md", "AGENTS.md") or
                path.startswith(("scripts/", "docs/", ".github/workflows/")),
                "Retained stock8 product source changed; produce new accepted bytes: " + path)
    native_source = native_custody["nativeProducerSource"]
    require(native_custody["packingSource"] == packing_source, "Original packing source differs")
    selected = native_custody["selectedSourceHashes"]
    require(type(selected) is dict and selected, "Original selected native source ledger required")
    verify_selection(root,selected,native_source)
    for path, expected in selected.items():
        safe(path)
        require(SHA.fullmatch(expected) and digest(git(root, "show", native_source + ":" + path)) == expected and
                digest((root / path).read_bytes()) == expected, "Selected native source changed: " + path)
    return {"packingSource": packing_source, "nativeProducerSource": native_source,
            "releaseSourceSha": release_source, "sourceChanges": sorted(filter(None, changed)),
            "selectedNativeSourceCount": len(selected), "originalSourceRecordsUnchanged": True}


def check_product(raw, accepted, package_raw, native_raw, root, release_source):
    require(digest(raw) == accepted["jarSha256"], "Accepted product bytes differ")
    custody = decode((root / CUSTODY_PATH).read_bytes())
    require(set(custody) == {"jarSha256", "packageCustodySha256", "nativeCustodySha256"} and
            custody["jarSha256"] == accepted["jarSha256"] and digest(package_raw) == custody["packageCustodySha256"] and
            digest(native_raw) == custody["nativeCustodySha256"], "Original complete custody bytes differ")
    package, native = decode(package_raw), decode(native_raw)
    require(package["sourceRevision"] == accepted["productSource"] and package["version"] == "0.4.4" and
            package["target"] == "forge1122" and package["outputSha256"] == accepted["jarSha256"], "Original product/source identity differs")
    require(package["ordinaryModsJar"] is True and package["javaRequired"] == 8 and package["helperBuild"] is None and
            package["hostNamespacesReplaced"] is False and package["allPhysicalClassesAtMost52"] is True,
            "Ordinary stock Java8 product custody required")
    contents = archive(raw)
    actual = {name: {"sha256": digest(blob), "bytes": len(blob),
                   "major": int.from_bytes(blob[6:8], "big") if name.endswith(".class") else None}
              for name, blob in contents.items()}
    require(actual == package["entries"], "Complete product entry custody differs")
    classes = {name for name in contents if name.endswith(".class")}
    require(len(classes) == 5334 and set(package["runtimeClassPathOwnership"]) == classes, "Exact runtime class ownership required")
    for name in classes:
        blob = contents[name]
        require(len(blob) >= 8 and blob[:4] == b"\xca\xfe\xba\xbe" and 45 <= actual[name]["major"] <= 52 and
                digest(blob) == package["runtimeClassPathOwnership"][name]["sha256"], "Runtime Java8 class bytes/path differ: " + name)
    forbidden = ("java/", "net/minecraft/", "net/minecraftforge/", "org/objectweb/asm/", "com/google/gson/",
                 "com/google/common/", "org/apache/logging/log4j/", "dev/openallay/forge1122agent/")
    require(not any(name.startswith(forbidden) for name in classes), "Stock host namespaces or agent cannot be embedded")
    require(not any(name.startswith("META-INF/licenses/") and name.endswith(".class") for name in contents), "Runtime class misplaced as legal resource")
    descriptor = decode(contents["mcmod.info"])
    require(len(descriptor) == 1 and descriptor[0]["modid"] == "openallay" and descriptor[0]["version"] == "0.4.4" and
            descriptor[0]["mcversion"] == "1.12.2", "Genuine current Forge14 mod descriptor required")
    manifest = contents["META-INF/MANIFEST.MF"].decode()
    require("TweakClass: org.spongepowered.asm.launch.MixinTweaker" in manifest and "Premain-Class:" not in manifest,
            "Original ordinary stock Forge MixinTweaker required")
    for name in ("openallay.client.mixins.json", "openallay.world.mixins.json", "openallay.forge.mixins.json"):
        require(decode(contents[name])["compatibilityLevel"] == "JAVA_8", "Actual native Java8 Mixin metadata required")
    require(contents["META-INF/services/java.sql.Driver"].decode().strip() == "org.sqlite.JDBC", "SQLite service differs")
    require(digest(contents[BUILDER_PATH]) == BUILDER_SHA == package["builderSha256"], "Exact accepted raw universal Builder differs")
    lock = decode((root / "distribution/extensions.lock.json").read_bytes())
    provenance = decode(contents["META-INF/openallay/distribution.json"])
    require(provenance["source"] == {**lock["source"], "dirty": False, "pinned": True} and
            provenance["artifact"] == {"path": BUILDER_PATH, "sha256": BUILDER_SHA}, "Actual raw Builder provenance differs")
    require(next(item for item in package["inputs"] if item["role"] == "engine")["sha256"] == ENGINE_SHA == native["currentEngineSha256"],
            "One canonical engine provider required")
    binding = source_custody(root, accepted["productSource"], native, release_source)
    payload = {name: digest(blob) for name, blob in sorted(contents.items())
               if name.startswith("org/sqlite/") or name == "META-INF/services/java.sql.Driver"}
    sqlite = next(item for item in package["inputs"] if item["role"] == "sqlite")
    runtimes = {module: next(item for item in package["inputs"] if item["role"] == role)["sha256"]
                for module, role in (("extension-api", "sdk"), ("runtime-rhino", "rhino"))}
    return {"coreBytes": raw, "sqlite": {"artifactSha256": sqlite["sha256"], "payloadSha256":
            digest(json.dumps(payload, sort_keys=True, separators=(",", ":")).encode())}, "sharedRuntimes": runtimes}, binding


def verify_release(path, family, version, root, *, approved_package_source=None):
    accepted = pin(root)
    require(family == {"id": "forge-1.12.2", "loader": "forge", "buildTarget": "1.12.2", "supportedTargets": ["1.12.2"],
                      "filenameTemplate": "openallay-forge-1.12.2-{version}.jar", "packagingRecipe": "forge-stock8",
                      "artifactKind": "jar", "publicationChannels": ["github", "modrinth"]} and version == "0.4.4", "Exact stock8 release family required")
    wrapper = decode(Path(str(path) + ".packaging.json").read_bytes())
    require(set(wrapper) == {"kind", "pin", "releaseSourceSha", "originalPackageCustody", "originalNativeCustody", "sourceBinding"} and
            wrapper["kind"] == "retained-stock8" and wrapper["pin"] == accepted, "Exact retained stock8 stage custody required")
    current = git(root, "rev-parse", "HEAD").decode().strip()
    package_source = current if approved_package_source is None else approved_package_source
    require(wrapper["releaseSourceSha"] == package_source, "Stage source differs from approved original package source")
    package_raw = base64.b64decode(wrapper["originalPackageCustody"], validate=True)
    native_raw = base64.b64decode(wrapper["originalNativeCustody"], validate=True)
    proof, binding = check_product(Path(path).read_bytes(), accepted, package_raw, native_raw, root, package_source)
    require(binding == wrapper["sourceBinding"], "Retained source binding changed after staging")
    return proof


def provider_identity(record, run, accepted, repository):
    p = accepted["provider"]
    require(record["id"] == p["artifactId"] and record["expired"] is False and record["digest"] == "sha256:" + p["sha256"] and
            record["workflow_run"]["id"] == p["runId"] and record["workflow_run"]["head_sha"] == p["sourceRevision"], "Retained artifact provider differs")
    require(run["id"] == p["runId"] and run["head_sha"] == p["sourceRevision"] and run["status"] == "completed" and
            run["conclusion"] == "success" and run["event"] == "workflow_dispatch" and
            run["path"] == ".github/workflows/minecraft-native.yml" and run["repository"]["full_name"] == repository and
            run["head_repository"]["full_name"] == repository, "Original successful stock8 package run differs")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--plan", action="store_true")
    args = parser.parse_args()
    accepted = pin(ROOT)
    if args.plan:
        print(json.dumps({"provider": accepted, "output": str(args.output), "nativeRecompiled": False, "gameExecuted": False}, indent=2))
        return
    require(os.environ.get("GITHUB_ACTIONS") == "true", "Retained stock8 stage runs remotely only")
    source = git(ROOT, "rev-parse", "HEAD").decode().strip()
    require(os.environ.get("GITHUB_SHA") == source and not git(ROOT, "status", "--porcelain", "--untracked-files=no").strip(), "Clean exact release source required")
    sidecar = Path(str(args.output) + ".packaging.json")
    require(not args.output.exists() and not sidecar.exists() and not args.output.is_symlink() and not sidecar.is_symlink(), "Preserve existing release outputs")
    require(args.output.name == accepted["jarName"] and args.output.absolute().is_relative_to(ROOT / "native-builds/forge1122/build/libs"), "Exact stock8 build output required")
    repository = os.environ["GITHUB_REPOSITORY"]
    api = lambda endpoint: decode(subprocess.check_output(["gh", "api", "repos/" + repository + "/" + endpoint]))
    provider_identity(api("actions/artifacts/" + str(accepted["provider"]["artifactId"])),
                      api("actions/runs/" + str(accepted["provider"]["runId"])), accepted, repository)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="stock8-release-", dir=os.environ["RUNNER_TEMP"]) as work:
        transport = Path(work) / "provider.zip"
        with transport.open("xb") as stream:
            subprocess.run(["gh", "api", "repos/" + repository + "/actions/artifacts/" + str(accepted["provider"]["artifactId"]) + "/zip"], stdout=stream, check=True)
        require(digest(transport.read_bytes()) == accepted["provider"]["sha256"], "Retained archive bytes differ")
        files = archive(transport.read_bytes())
        def one(filename):
            values = [raw for name, raw in files.items() if PurePosixPath(name).name == filename]
            require(len(values) == 1, "One retained " + filename + " required")
            return values[0]
        raw, package_raw, native_raw = one(accepted["jarName"]), one("package-custody.json"), one("native-producer-custody.json")
        _, binding = check_product(raw, accepted, package_raw, native_raw, ROOT, source)
        wrapper = {"kind": "retained-stock8", "pin": accepted, "releaseSourceSha": source,
                   "originalPackageCustody": base64.b64encode(package_raw).decode(), "originalNativeCustody": base64.b64encode(native_raw).decode(), "sourceBinding": binding}
        with args.output.open("xb") as stream:
            stream.write(raw)
        with sidecar.open("xb") as stream:
            stream.write((json.dumps(wrapper, sort_keys=True, indent=2) + "\n").encode())
    print(json.dumps({"artifactSha256": accepted["jarSha256"], "artifactSourceSha": accepted["productSource"],
                      "releaseSourceSha": source, "nativeRecompiled": False, "gameExecuted": False}))


if __name__ == "__main__":
    main()
