#!/usr/bin/env python3
"""Check compiled shared-engine identity in two native target packages, not game support."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def check(condition, message):
    if not condition:
        raise ValueError(message)


def read_properties(path):
    return dict(line.split("=", 1) for line in path.read_text().splitlines()
                if line and not line.startswith(("#", "!")) and "=" in line)


def engine_files(root):
    files = {}
    for directory in [root / "engine-core/build/classes/java/main",
                      root / "engine-core/build/resources/main",
                      root / "runtime-json/build/classes/java/main"]:
        if not directory.is_dir():
            continue
        for path in directory.rglob("*"):
            if path.is_file():
                name = path.relative_to(directory).as_posix()
                blob = path.read_bytes()
                check(name not in files or files[name] == blob, "Conflicting compiled engine entry: " + name)
                files[name] = blob
    check("dev/openallay/guide/GuideService.class" in files
          and "dev/openallay/FeatureServices.class" in files, "Compiled engine output missing")
    return files


def verify(path, loader, target, java, original_engine, bundled_builder=False):
    with zipfile.ZipFile(path) as archive:
        counts = Counter(archive.namelist())
        check(all(count == 1 for count in counts.values()), "Duplicate package entries: " + str(path))
        for name, blob in original_engine.items():
            check(counts[name] == 1 and archive.read(name) == blob,
                  "Shared engine was changed or omitted: " + name + " in " + str(path))
        for name in counts:
            if name.endswith(".class"):
                blob = archive.read(name)
                check(blob[:4] == b"\xca\xfe\xba\xbe" and int.from_bytes(blob[6:8], "big") <= java + 44,
                      "Native class exceeds target Java: " + name)
        if loader == "fabric":
            metadata = json.loads(archive.read("fabric.mod.json"))
            check(metadata["id"] == "openallay", "Fabric mod identity differs")
            check(metadata["depends"]["minecraft"] in (target, "~" + target), "Fabric target metadata differs")
            check(metadata["depends"]["java"] == ">=" + str(java), "Fabric Java metadata differs")
        else:
            descriptor = "META-INF/mods.toml" if target in ("1.20.1", "1.20.4", "1.20.3", "1.20.2") else "META-INF/neoforge.mods.toml"
            other = "META-INF/neoforge.mods.toml" if descriptor.endswith("/mods.toml") else "META-INF/mods.toml"
            check(counts[descriptor] == 1 and counts[other] == 0, "Wrong or competing native loader descriptor")
            metadata = archive.read(descriptor).decode()
            mod = metadata.split("[[mods]]", 1)[1].split("[[dependencies.", 1)[0]
            check(re.search(r'(?m)^modId\s*=\s*"openallay"', mod) is not None, "NeoForge mod identity differs")
            blocks = metadata.split("[[dependencies.")[1:]
            minecraft = [block for block in blocks if re.search(r'(?m)^modId\s*=\s*"minecraft"', block)]
            check(len(minecraft) == 1 and 'versionRange="[' + target + ']"' in minecraft[0].replace(" ", ""), "NeoForge target metadata differs")
            if target == "1.20.1":
                check('modId="forge"' in metadata.replace(" ", ""), "Early NeoForge must use actual Forge loader dependency")
                mixins = json.loads(archive.read("openallay.client.mixins.json"))
                check(mixins.get("refmap") == "openallay.refmap.json" and counts["openallay.refmap.json"] == 1,
                      "Legacy native Mixin refmap missing")
        if bundled_builder:
            import importlib.util
            spec = importlib.util.spec_from_file_location("native_builder_package", ROOT / "scripts/verify-bundled-extensions.py")
            builder = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(builder)
            lock = builder.prepare.load_manifest(ROOT / "distribution/extensions.lock.json")
            builder.verify_package(path, loader, lock)
            from io import BytesIO
            with zipfile.ZipFile(BytesIO(archive.read(builder.resource_path(lock)))) as payload:
                declaration = builder.verify_manifest(payload.read(builder.DESCRIPTOR), lock)
            check(any(item["loader"] == loader and item["minecraftVersionRange"] == target
                      for item in declaration["support"]["targets"]), "Builder does not declare this exact native target")
        else:
            check(not any(name.startswith("META-INF/openallay/bundled-extensions/") for name in counts),
                  "Core-only native check cannot silently include unverified Builder bytes")
    return {"loader": loader, "target": target, "path": str(path),
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "sharedEngineEntries": len(original_engine)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", required=True)
    parser.add_argument("--bundled-builder", action="store_true")
    args = parser.parse_args()
    try:
        profile = read_properties(ROOT / "gradle/minecraft-targets" / (args.target + ".properties"))
        check(profile["minecraft_version"] == args.target, "Target/profile mismatch")
        version = read_properties(ROOT / "gradle.properties")["version"]
        original = engine_files(ROOT)
        packages = [verify(ROOT / loader / "build/libs" / ("openallay-" + loader + "-" + args.target + "-" + version + ".jar"),
                           loader, args.target, int(profile["java_version"]), original, args.bundled_builder) for loader in ("fabric", "neoforge")]
        print(json.dumps({"nativePackageChecks": "passed", "packages": packages,
                          "gameMixinAndSameJarRangeAcceptance": "not established"}, indent=2))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, "Native package verification failed: " + str(error) + "\n")


if __name__ == "__main__":
    main()
