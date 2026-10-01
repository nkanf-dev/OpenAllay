#!/usr/bin/env python3
"""Verify default nested Extensions, loader registration, and source provenance."""
from __future__ import annotations

import argparse
from io import BytesIO
import json
from pathlib import Path
import sys
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROVENANCE = "META-INF/openallay/distribution.json"
DESCRIPTOR = "META-INF/openallay-extension.json"
SHARED_ENTRIES = {
    "dev/openallay/builder/BuilderExtension.class",
    "dev/openallay/builder/BuilderRuntime.class",
    "dev/openallay/builder/BuilderSession.class",
    "assets/openallay_builder/building.js",
    "assets/openallay_builder/terrain.js",
    "assets/openallay_builder/presets.js",
    "assets/openallay_builder/openallay_skills/minecraft-builder/SKILL.md",
    DESCRIPTOR,
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def class_annotations(content: bytes) -> dict:
    """Read class-level runtime annotations, without loading client classes."""
    stream = BytesIO(content)

    def read(size):
        value = stream.read(size)
        require(len(value) == size, "Truncated loader entrypoint class")
        return value

    def number(size):
        return int.from_bytes(read(size), "big")

    require(read(4) == b"\xca\xfe\xba\xbe", "Invalid loader entrypoint class")
    read(4)  # Class minor/major versions.
    pool = [None] * number(2)
    index = 1
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
             12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < len(pool):
        tag = number(1)
        if tag == 1:
            # Relevant annotation names and enum values are ASCII; unrelated JVM
            # modified-UTF8 strings need not be interpreted by this checker.
            pool[index] = read(number(2)).decode("utf-8", errors="replace")
        else:
            require(tag in sizes, "Unsupported constant-pool entry")
            read(sizes[tag])
            if tag in (5, 6):
                index += 1
        index += 1

    def text():
        value = pool[number(2)]
        require(isinstance(value, str), "Annotation references a non-string constant")
        return value

    def annotation():
        name = text()
        values = {}
        for _ in range(number(2)):
            key = text()
            values[key] = element()
        return name, values

    def element():
        tag = chr(number(1))
        if tag == "s":
            return text()
        if tag in "BCDFIJSZ":
            return (tag, number(2))
        if tag == "e":
            return ("enum", text(), text())
        if tag == "c":
            return ("class", text())
        if tag == "@":
            return annotation()
        if tag == "[":
            return [element() for _ in range(number(2))]
        raise ValueError("Unsupported annotation element")

    def skip_attributes():
        for _ in range(number(2)):
            read(2)
            read(number(4))

    read(6)  # Access flags, this class, super class.
    read(2 * number(2))  # Interfaces.
    for _ in range(2):  # Fields, then methods.
        for _ in range(number(2)):
            read(6)
            skip_attributes()
    annotations = {}
    for _ in range(number(2)):
        name = text()
        length = number(4)
        end = stream.tell() + length
        if name in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
            for _ in range(number(2)):
                annotation_name, values = annotation()
                annotations[annotation_name] = values
            require(stream.tell() == end, "Invalid annotation attribute length")
        else:
            read(length)
    return annotations


def verify_package(path: Path, loader: str, lock: dict, allow_unpinned: bool = False) -> None:
    with zipfile.ZipFile(path) as archive:
        entries = archive.namelist()
        require(len(entries) == len(set(entries)), "Duplicate entries in product JAR")
        require(not any(name.startswith("dev/openallay/builder/") for name in entries),
                "Builder classes were flattened into core instead of independently nested")
        provenance = json.loads(archive.read(PROVENANCE))
        require(provenance["source"]["repository"] == lock["source"]["repository"], "Source repository mismatch")
        require(provenance["project"] == lock["project"], "Extension source project mismatch")
        require(provenance["version"] == lock["version"], "Extension provenance version mismatch")
        require(provenance["modId"] == lock["modId"], "Extension provenance mod ID mismatch")
        if not allow_unpinned:
            require(provenance["source"].get("pinned") is True, "Unpinned development build is not a distribution")
            require(provenance["source"].get("dirty") is False, "Dirty Extension source is not a distribution")
            require(provenance["source"]["revision"] == lock["source"]["revision"], "Source revision mismatch")
        if loader == "fabric":
            metadata = json.loads(archive.read("fabric.mod.json"))
            registered = [item["file"] for item in metadata.get("jars", [])]
        else:
            metadata = json.loads(archive.read("META-INF/jarjar/metadata.json"))
            registered = [item["path"] for item in metadata["jars"]]
        candidates = [name for name in entries if name.endswith(".jar") and "openallay-builder-" in name]
        require(len(candidates) == 1, "Exactly one native Builder Extension must be nested")
        nested_path = candidates[0]
        require(nested_path in registered, "Nested Builder is not registered with the loader")
        require(Path(nested_path).name.endswith(Path(lock["artifacts"][loader]).name), "Wrong loader artifact nested")
        if loader == "neoforge":
            item = next(item for item in metadata["jars"] if item["path"] == nested_path)
            require(item["identifier"] == {"group": "dev.openallay.builder",
                    "artifact": f"openallay-builder-neoforge-{lock['minecraftVersion']}"}, "Invalid JarJar identity")
            require(item["version"]["artifactVersion"] == lock["version"], "Invalid JarJar version")
        with zipfile.ZipFile(BytesIO(archive.read(nested_path))) as nested:
            nested_entries = nested.namelist()
            require(len(nested_entries) == len(set(nested_entries)), "Duplicate entries in nested Extension")
            require(SHARED_ENTRIES.issubset(nested_entries), "Builder native classes, Skill, or JS library missing")
            require(not any(name.endswith(".class") and name.startswith("dev/openallay/")
                            and not name.startswith("dev/openallay/builder/") for name in nested_entries), "Nested Extension duplicates OpenAllay core classes")
            require(not any(name.startswith(("net/minecraft/", "net/fabricmc/", "net/neoforged/", "baritone/"))
                            for name in nested_entries), "Nested Extension bundles platform or unrelated classes")
            descriptor = json.loads(nested.read(DESCRIPTOR))
            require(descriptor["id"] == lock["extensionId"], "Wrong Extension ID")
            require(descriptor["version"] == lock["version"], "Wrong Extension version")
            require(descriptor["loaders"] == [loader], "Wrong Extension loader descriptor")
            require(descriptor["modIds"] == [lock["modId"]], "Wrong Extension mod binding")
            if loader == "fabric":
                mod = json.loads(nested.read("fabric.mod.json"))
                entrypoint = "dev.openallay.builder.fabric.BuilderFabricEntrypoint"
                require(mod["id"] == lock["modId"] and mod["version"] == lock["version"], "Wrong Fabric mod identity")
                require(mod.get("environment") == "client", "Fabric Builder must be client-only")
                require(entrypoint in mod["entrypoints"]["main"], "Fabric entrypoint is not registered")
                require("openallay" in mod["depends"], "Fabric core dependency is missing")
            else:
                mod = nested.read("META-INF/neoforge.mods.toml").decode("utf-8")
                entrypoint = "dev.openallay.builder.neoforge.BuilderNeoForgeEntrypoint"
                tables = re.findall(r"(?ms)^\[\[([^]\n]+)\]\]\s*\n(.*?)(?=^\[|\Z)", mod)
                values = [(name, dict(re.findall(r'(?m)^\s*(\w+)\s*=\s*"([^"\n]*)"', body)))
                          for name, body in tables]
                require(any(name == "mods" and item.get("modId") == lock["modId"]
                            and item.get("version") == lock["version"] for name, item in values),
                        "Wrong NeoForge mod identity")
                require(any(name == f"dependencies.{lock['modId']}" and item.get("modId") == "openallay"
                            and item.get("ordering") == "AFTER" for name, item in values),
                        "NeoForge core ordering is missing")
            entrypoint_path = entrypoint.replace(".", "/") + ".class"
            require(entrypoint_path in nested_entries, "Native loader entrypoint class is missing")
            if loader == "neoforge":
                annotations = class_annotations(nested.read(entrypoint_path))
                mod_annotation = annotations.get("Lnet/neoforged/fml/common/Mod;", {})
                require(mod_annotation.get("value") == lock["modId"], "NeoForge @Mod identity is missing")
                require(mod_annotation.get("dist") == [("enum", "Lnet/neoforged/api/distmarker/Dist;", "CLIENT")],
                        "NeoForge Builder @Mod must be client-only")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fabric", type=Path)
    parser.add_argument("neoforge", type=Path)
    parser.add_argument("--manifest", type=Path, default=ROOT / "distribution/extensions.lock.json")
    parser.add_argument("--allow-unpinned", action="store_true", help="Check local package contents, not release provenance")
    args = parser.parse_args()
    try:
        lock = json.loads(args.manifest.read_text(encoding="utf-8"))
        for loader in ("fabric", "neoforge"):
            verify_package(getattr(args, loader), loader, lock, args.allow_unpinned)
        print("bundled_extension_verification=passed")
        return 0
    except (ValueError, KeyError, IndexError, OSError, zipfile.BadZipFile, StopIteration) as exc:
        print(f"Bundled Extension verification failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
