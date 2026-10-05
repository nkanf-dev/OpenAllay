#!/usr/bin/env python3
"""Verify and stage one universal Builder resource with exact source provenance.

The Builder JAR is a raw core-owned resource, never a loader-registered mod.
Only the explicit source preparation command may access Git remotes.
"""
from __future__ import annotations

import argparse
import hashlib
from importlib.util import module_from_spec, spec_from_file_location
from io import BytesIO
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROVENANCE = "META-INF/openallay/distribution.json"
DESCRIPTOR = "META-INF/openallay-extension.json"
RESOURCE_DIRECTORY = "META-INF/openallay/bundled-extensions/"
SHARED_ENTRIES = {
    "dev/openallay/builder/BuilderExtension.class",
    "dev/openallay/builder/BuilderRuntime.class",
    "dev/openallay/builder/BuilderSession.class",
    "dev/openallay/builder/internal/gson/Strictness.class",
    "assets/openallay_builder/building.js",
    "assets/openallay_builder/terrain.js",
    "assets/openallay_builder/presets.js",
    "assets/openallay_builder/openallay_skills/minecraft-builder/SKILL.md",
    DESCRIPTOR,
}
MANIFEST_FIELDS = {"schemaVersion", "id", "name", "version", "entrypoint", "provider", "summary", "source", "support"}
SUPPORT_FIELDS = {"targets", "minimumJavaVersion", "requiredHostFeatures", "validatedTargetIds"}
TARGET_FIELDS = {"loader", "minecraftVersionRange", "openAllayVersionRange", "openAllayApiVersionRange"}
PROVENANCE_FIELDS = {"source", "project", "version", "extensionId", "openAllayApiVersion", "artifact"}
SOURCE_FIELDS = {"repository", "revision", "dirty", "pinned"}
FORBIDDEN_PREFIXES = ("net/minecraft/", "net/minecraftforge/", "net/fabricmc/", "net/neoforged/",
    "cpw/mods/", "org/spongepowered/asm/", "baritone/", "com/google/gson/", "META-INF/versions/", "dev/openallay/builder/fabric/",
    "dev/openallay/builder/neoforge/")
FORBIDDEN_ENTRIES = {"fabric.mod.json", "META-INF/neoforge.mods.toml", "META-INF/mods.toml",
    "mcmod.info", "module-info.class"}
# This verifier checks the currently pinned independently released Builder package,
# not community package compatibility. The host uses Maven for range semantics.
SDK_VERSION = "0.4.0"
SDK_SUPPORT_RANGE = "[0.4.0,0.5.0)"

_spec = spec_from_file_location("distribution_source", ROOT / "scripts/prepare-distribution.py")
prepare = module_from_spec(_spec)
_spec.loader.exec_module(prepare)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def resource_path(lock: dict) -> str:
    return RESOURCE_DIRECTORY + Path(lock["artifact"]).name


def string(value: object, name: str) -> str:
    require(isinstance(value, str) and bool(value.strip()), f"Expected nonblank {name} string")
    return value


def strings(value: object, name: str, pattern: str) -> list[str]:
    require(isinstance(value, list), f"Expected {name} array")
    require(all(isinstance(item, str) and re.fullmatch(pattern, item) for item in value),
        f"Invalid {name} string")
    require(len(value) == len(set(value)), f"Duplicate {name} string")
    return value


def exact(value: object, fields: set[str], name: str) -> None:
    prepare.exact_fields(value, fields, name)


def support_range(value: object, name: str) -> str:
    """Validate SDK declaration syntax only; Maven range matching stays in the host."""
    value = string(value, name)
    version = r"[0-9A-Za-z][0-9A-Za-z._+\-]*"
    if re.fullmatch(version, value):
        return value
    require(len(value) >= 3 and value[0] in "[(" and value[-1] in ")]", f"Invalid {name}")
    body = value[1:-1]
    if "," not in body:
        require(value[0] == "[" and value[-1] == "]" and re.fullmatch(version, body), f"Invalid {name}")
    else:
        require(body.count(",") == 1, f"Invalid {name}")
        lower, upper = (item.strip() for item in body.split(","))
        require(bool(lower or upper), f"Invalid {name}")
        require(bool(lower) or value[0] == "(", f"Invalid {name}")
        require(bool(upper) or value[-1] == ")", f"Invalid {name}")
        require(not lower or re.fullmatch(version, lower), f"Invalid {name}")
        require(not upper or re.fullmatch(version, upper), f"Invalid {name}")
    return value


def verify_manifest(content: bytes, lock: dict) -> dict:
    descriptor = prepare.decode_json(content)
    require(isinstance(descriptor, dict), "Expected universal manifest object")
    exact({key: value for key, value in descriptor.items() if key != "requirements"},
        MANIFEST_FIELDS, "Universal manifest")
    require(type(descriptor["schemaVersion"]) is int and descriptor["schemaVersion"] == 2,
        "Not a schema 2 universal Extension package")
    for field in ("id", "name", "version", "entrypoint", "provider", "summary", "source"):
        string(descriptor[field], field)
    require(descriptor["id"] == lock["extensionId"], "Wrong Extension ID")
    require(descriptor["version"] == lock["version"], "Wrong Extension version")
    require(descriptor["entrypoint"] == "dev.openallay.builder.BuilderExtension", "Wrong Builder entrypoint")
    support = descriptor["support"]
    exact(support, SUPPORT_FIELDS, "Universal support")
    require(type(support["minimumJavaVersion"]) is int and support["minimumJavaVersion"] == 8,
        "Universal Builder must declare Java8 support")
    features = strings(support["requiredHostFeatures"], "requiredHostFeatures",
        r"[a-z0-9][a-z0-9_.-]*(?::[a-z0-9_][a-z0-9_./-]*)?")
    require(features == ["minecraft:world-access"], "Wrong Builder host features")
    strings(support["validatedTargetIds"], "validatedTargetIds",
        r"[a-z0-9][a-z0-9_.-]*(?::[a-z0-9_][a-z0-9_./-]*)?")
    targets = support["targets"]
    require(isinstance(targets, list) and bool(targets), "Support targets must be a nonempty array")
    require(lock["openAllayApiVersion"] == SDK_VERSION, "Unsupported pinned Builder SDK version")
    encoded_targets = []
    for target in targets:
        exact(target, TARGET_FIELDS, "Universal support target")
        for field in TARGET_FIELDS - {"loader"}:
            support_range(target[field], field)
        string(target["loader"], "loader")
        require(re.fullmatch(r"[a-z][a-z0-9_.-]*", target["loader"]) is not None, "Invalid loader ID")
        require(target["openAllayApiVersionRange"] == SDK_SUPPORT_RANGE, "Wrong Builder SDK support range")
        encoded_targets.append(tuple(target[field] for field in sorted(TARGET_FIELDS)))
    require(len(encoded_targets) == len(set(encoded_targets)), "Duplicate support target")
    intended_games = {path.stem for path in (ROOT / "gradle/minecraft-targets").glob("*.properties")}
    require({(target["loader"], target["minecraftVersionRange"]) for target in targets}
        == {(loader, game) for loader in ("fabric", "neoforge") for game in intended_games},
        "Builder declaration must cover the prepared native target profiles in one payload")
    requirements = descriptor.get("requirements", {})
    require(isinstance(requirements, dict) and set(requirements) <= {"capabilities", "extensions", "skills"},
        "Invalid universal requirements fields")
    patterns = {"capabilities": r"[a-z0-9][a-z0-9_.-]*(?::[a-z0-9_][a-z0-9_./-]*)?",
        "extensions": r"[a-z0-9_.-]+:[a-z0-9_./-]+", "skills": r"[a-z0-9]+(?:-[a-z0-9]+)*"}
    for field, pattern in patterns.items():
        values = strings(requirements.get(field, []), f"requirements.{field}", pattern)
        if field == "skills":
            require(all(len(item) <= 64 for item in values), "Invalid Skill ID length")
    require(not any(requirements.get(field, []) for field in ("capabilities", "extensions", "skills")),
        "Builder must not add an independent permission requirement")
    return descriptor


def archive_entries(archive: zipfile.ZipFile, name: str) -> list[str]:
    entries = archive.namelist()
    require(len(entries) == len(set(entries)), f"Duplicate entries in {name}")
    for entry in entries:
        if not entry.endswith("/"):
            prepare.relative_path(entry)
    return entries


def verify_universal(content: bytes, lock: dict) -> None:
    with zipfile.ZipFile(BytesIO(content)) as nested:
        entries = archive_entries(nested, "universal Extension")
        require(SHARED_ENTRIES.issubset(entries), "Builder classes, canonical Skill/JS or private Gson missing")
        verify_manifest(nested.read(DESCRIPTOR), lock)
        for name in entries:
            require(name not in FORBIDDEN_ENTRIES and not name.startswith(FORBIDDEN_PREFIXES)
                and "BuilderEntrypoint" not in name and "BuilderEntry.class" not in name,
                f"Nonportable or legacy loader payload: {name}")
            if name.endswith(".class"):
                require(not name.startswith("dev/openallay/") or name.startswith("dev/openallay/builder/"),
                    f"Extension duplicates OpenAllay core/SDK classes: {name}")
                header = nested.read(name)[:8]
                require(len(header) == 8 and header[:4] == b"\xca\xfe\xba\xbe", f"Invalid class header: {name}")
                require(int.from_bytes(header[6:8], "big") == 52, f"Non-Java8 class: {name}")


def verify_provenance(provenance: dict, lock: dict, digest: str, allow_unpinned: bool) -> None:
    exact(provenance, PROVENANCE_FIELDS, "Distribution provenance")
    source = provenance["source"]
    exact(source, SOURCE_FIELDS, "Distribution source provenance")
    require(source["repository"] == lock["source"]["repository"], "Source repository mismatch")
    require(isinstance(source["revision"], str) and re.fullmatch(r"[0-9a-f]{40}", source["revision"]),
        "Invalid source revision")
    require(type(source["dirty"]) is bool and type(source["pinned"]) is bool, "Source flags must be booleans")
    for field in ("project", "version", "extensionId", "openAllayApiVersion"):
        require(provenance[field] == lock[field], f"Extension provenance {field} mismatch")
    if not allow_unpinned or source["pinned"]:
        require(source["pinned"] is True, "Unpinned development build is not a distribution")
        require(source["dirty"] is False, "Dirty Extension source is not a distribution")
        require(source["revision"] == lock["source"]["revision"], "Source revision mismatch")
    artifact = provenance["artifact"]
    exact(artifact, {"path", "sha256"}, "Distribution artifact provenance")
    require(artifact["path"] == resource_path(lock), "Wrong distribution artifact path")
    require(artifact["sha256"] == digest, "Bundled Builder SHA-256 mismatch")


def reject_builder_registration(archive: zipfile.ZipFile, entries: list[str], loader: str, lock: dict) -> None:
    if loader == "fabric":
        metadata = prepare.decode_json(archive.read("fabric.mod.json"))
        registered = [item["file"] for item in metadata.get("jars", [])]
        require(metadata.get("id") != "openallay_builder", "Core is a duplicate Builder mod")
    else:
        metadata = (prepare.decode_json(archive.read("META-INF/jarjar/metadata.json"))
            if "META-INF/jarjar/metadata.json" in entries else {"jars": []})
        registered = [item["path"] for item in metadata["jars"]]
        descriptors = [name for name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml") if name in entries]
        require(len(descriptors) == 1, "Core must contain one actual native loader descriptor")
        require(not re.search(r"\bmodId\s*=\s*['\"]openallay_builder['\"]",
            archive.read(descriptors[0]).decode("utf-8")), "Core is a duplicate Builder mod")
    require(resource_path(lock) not in registered, "Raw Builder resource must not be loader registered")
    for path in registered:
        require(isinstance(path, str) and path in entries, "Registered loader JAR is missing")
    # Detect renamed duplicate Builder mods/Extensions too, not only known filenames.
    for path in entries:
        if not path.endswith(".jar") or path == resource_path(lock):
            continue
        with zipfile.ZipFile(BytesIO(archive.read(path))) as nested:
            names = archive_entries(nested, "ordinary nested dependency")
            if DESCRIPTOR in names:
                descriptor = prepare.decode_json(nested.read(DESCRIPTOR))
                require(descriptor.get("id") != lock["extensionId"], "Duplicate bundled Builder Extension ID")
            require(not any(name.startswith("dev/openallay/builder/") for name in names),
                "Duplicate Builder classes in ordinary loader dependency")
            if "fabric.mod.json" in names:
                mod = prepare.decode_json(nested.read("fabric.mod.json"))
                require(mod.get("id") != "openallay_builder", "Duplicate legacy Builder mod")
            for name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
                if name in names:
                    require(not re.search(r"\bmodId\s*=\s*['\"]openallay_builder['\"]",
                        nested.read(name).decode("utf-8")), "Duplicate legacy Builder mod")
            if "mcmod.info" in names:
                require("openallay_builder" not in nested.read("mcmod.info").decode("utf-8"),
                    "Duplicate legacy Builder mod")


def verify_package(path: Path, loader: str, lock: dict, allow_unpinned: bool = False) -> str:
    prepare.validate_manifest(lock)
    require(loader in {"fabric", "forge", "neoforge"}, "Unknown core loader")
    with zipfile.ZipFile(path) as archive:
        entries = archive_entries(archive, "product JAR")
        require(not any(name.startswith("dev/openallay/builder/") for name in entries),
            "Builder classes were flattened into core instead of a raw resource")
        expected = resource_path(lock)
        candidates = [name for name in entries if name.endswith(".jar")
            and (name.startswith(RESOURCE_DIRECTORY) or "openallay-builder-" in name)]
        require(candidates == [expected], "Exactly one universal Builder resource must be bundled")
        require(DESCRIPTOR not in entries, "Universal Extension descriptor was flattened into core")
        require(PROVENANCE in entries, "Missing distribution provenance")
        content = archive.read(expected)
        digest = hashlib.sha256(content).hexdigest()
        provenance = prepare.decode_json(archive.read(PROVENANCE))
        verify_provenance(provenance, lock, digest, allow_unpinned)
        reject_builder_registration(archive, entries, loader, lock)
        verify_universal(content, lock)
        return digest


def verify_packages(fabric: Path, neoforge: Path, lock: dict, allow_unpinned: bool = False) -> str:
    fabric_digest = verify_package(fabric, "fabric", lock, allow_unpinned)
    neoforge_digest = verify_package(neoforge, "neoforge", lock, allow_unpinned)
    require(fabric_digest == neoforge_digest, "Both loaders must bundle identical universal Builder bytes")
    with zipfile.ZipFile(fabric) as fabric_archive, zipfile.ZipFile(neoforge) as neoforge_archive:
        require(fabric_archive.read(resource_path(lock)) == neoforge_archive.read(resource_path(lock)),
            "Both loaders must bundle identical decompressed universal Builder bytes")
    return fabric_digest


def stage_distribution(source: Path, output: Path, lock: dict, allow_unpinned: bool = False) -> dict:
    """Run only after the delegated build; failed verification leaves prior output intact."""
    prepare.validate_manifest(lock)
    source_evidence = prepare.verify_source(source, lock, allow_unpinned)
    artifact = source / lock["project"] / lock["artifact"]
    require(artifact.resolve().is_relative_to(source.resolve()), "Artifact escapes source checkout")
    content = artifact.read_bytes()
    verify_universal(content, lock)
    provenance = {"source": {**lock["source"], **source_evidence},
        **{field: lock[field] for field in ("project", "version", "extensionId", "openAllayApiVersion")},
        "artifact": {"path": resource_path(lock), "sha256": hashlib.sha256(content).hexdigest()}}
    verify_provenance(provenance, lock, provenance["artifact"]["sha256"], allow_unpinned)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".bundled-extensions-", dir=output.parent) as directory:
        staged = Path(directory) / "resources"
        artifact_output = staged / resource_path(lock)
        artifact_output.parent.mkdir(parents=True)
        artifact_output.write_bytes(content)
        provenance_output = staged / PROVENANCE
        provenance_output.write_text(json.dumps(provenance, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        previous = Path(directory) / "previous"
        if output.exists():
            os.replace(output, previous)
        try:
            os.replace(staged, output)
        except OSError:
            if previous.exists():
                os.replace(previous, output)
            raise
    return provenance


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fabric", type=Path, nargs="?")
    parser.add_argument("neoforge", type=Path, nargs="?")
    parser.add_argument("--manifest", type=Path, default=ROOT / "distribution/extensions.lock.json")
    parser.add_argument("--allow-unpinned", action="store_true", help="Explicit local development only")
    parser.add_argument("--stage", action="store_true", help="Stage resources after a successful delegated build")
    parser.add_argument("--source-directory", type=Path)
    parser.add_argument("--output-directory", type=Path)
    args = parser.parse_args()
    try:
        lock = prepare.load_manifest(args.manifest)
        if args.stage:
            if args.fabric or args.neoforge or not args.source_directory or not args.output_directory:
                raise ValueError("--stage requires only --source-directory and --output-directory")
            provenance = stage_distribution(args.source_directory.resolve(), args.output_directory.resolve(),
                lock, args.allow_unpinned)
            print(json.dumps(provenance, sort_keys=True))
        else:
            if not args.fabric or not args.neoforge or args.source_directory or args.output_directory:
                raise ValueError("Verification requires both Fabric and NeoForge JARs")
            digest = verify_packages(args.fabric, args.neoforge, lock, args.allow_unpinned)
            print(f"bundled_extension_sha256={digest}")
            print("bundled_extension_verification=passed")
        return 0
    except (ValueError, KeyError, TypeError, IndexError, OSError, subprocess.CalledProcessError,
            zipfile.BadZipFile) as exc:
        print(f"Bundled Extension verification failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
