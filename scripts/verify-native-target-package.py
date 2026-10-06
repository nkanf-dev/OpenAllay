#!/usr/bin/env python3
"""Check compiled shared-engine identity in two native target packages, not game support."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import zipfile
from minecraft_target_loaders import target_loaders

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
                      root / "runtime-json/build/classes/java/main",
                      root / "engine-core/build/generated/private-maven"]:
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


def verify_forge_shared_runtimes(archive, counts, root, original_engine):
    from io import BytesIO
    sdk_version = re.search(r"(?m)^version = '([^']+)'", (root / "extension-api/build.gradle").read_text()).group(1)
    rhino_version = read_properties(root / "gradle.properties")["rhino_version"]
    references = [
        ("SDK", "dev.openallay", "extension-api", sdk_version, root / "extension-api/build/libs" / ("openallay-extension-api-" + sdk_version + ".jar"),
         "dev/openallay/api/", 52),
        ("Rhino", "dev.openallay", "runtime-rhino", rhino_version, root / "runtime-rhino/build/libs" / ("openallay-rhino-" + rhino_version + ".jar"),
         "dev/latvian/mods/rhino/", 61),
    ]
    nested = []
    for name in counts:
        if name.endswith(".jar") and name.startswith("META-INF/jarjar/"):
            content = archive.read(name)
            with zipfile.ZipFile(BytesIO(content)) as dependency:
                entries = {entry: dependency.read(entry) for entry in dependency.namelist() if entry.endswith(".class")}
                check(len(dependency.namelist()) == len(set(dependency.namelist())), "Duplicate nested runtime entries: " + name)
            nested.append((name, content, entries))
    check(counts["META-INF/jarjar/metadata.json"] == 1, "Missing sole Forge JarJar metadata")
    registration = json.loads(archive.read("META-INF/jarjar/metadata.json"))
    check(isinstance(registration, dict) and isinstance(registration.get("jars"), list), "Invalid Forge JarJar metadata")
    registered_paths = [item["path"] for item in registration["jars"]]
    check(len(registered_paths) == len(set(registered_paths)), "Duplicate JarJar dependency registration")
    for label, group, artifact, version, reference, prefix, major in references:
        check(reference.is_file(), "Missing compiled shared " + label + " reference")
        with zipfile.ZipFile(reference) as original:
            expected = {name: original.read(name) for name in original.namelist() if name.endswith(".class") and name.startswith(prefix)}
        check(bool(expected), "Missing shared " + label + " classes")
        owners = [(name, content, entries) for name, content, entries in nested if any(entry.startswith(prefix) for entry in entries)]
        check(len(owners) == 1, "Shared " + label + " must have one nested identity")
        name, content, entries = owners[0]
        check(content == reference.read_bytes(), "Shared " + label + " JAR bytes changed: " + name)
        registered = [item for item in registration["jars"] if item["identifier"] == {"group": group, "artifact": artifact}]
        check(len(registered) == 1 and registered[0]["path"] == name
              and registered[0]["version"]["artifactVersion"] == version
              and registered[0]["version"]["range"] in ("[" + version + "]", "[" + version + "," + version + "]"),
              "Shared " + label + " JarJar identity or singleton range differs")
        check(not any(entry.startswith(prefix) and entry.endswith(".class") for entry in counts),
              "Shared " + label + " classes flattened into native mod")
        for entry, blob in expected.items():
            check(entries.get(entry) == blob and blob[:4] == b"\xca\xfe\xba\xbe"
                  and int.from_bytes(blob[6:8], "big") == major, "Shared " + label + " class ABI differs: " + entry)
    for name, content, entries in nested:
        for entry, blob in entries.items():
            check(blob[:4] == b"\xca\xfe\xba\xbe" and int.from_bytes(blob[6:8], "big") <= 61,
                  "Nested Forge dependency exceeds Java17: " + name + "!" + entry)
            check(entry not in original_engine, "Shared engine has a competing nested owner: " + entry)


def verify(path, loader, target, java, original_engine, bundled_builder=False, family=None):
    check(loader in target_loaders(ROOT, target)["loaders"], "Loader is not an actual target identity")
    if family is not None:
        check(family['loader'] == loader and family['buildTarget'] == target,
              'Native candidate must use its real build target and loader')
        import importlib.util
        spec = importlib.util.spec_from_file_location('native_interval', ROOT / 'scripts/verify-minecraft-binary-intervals.py')
        interval = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(interval)
        interval.package_guard(path, family, read_properties(ROOT / 'gradle.properties')['version'], ROOT)
    with zipfile.ZipFile(path) as archive:
        counts = Counter(archive.namelist())
        check(all(count == 1 for count in counts.values()), "Duplicate package entries: " + str(path))
        mismatches = []
        for name, blob in original_engine.items():
            actual = archive.read(name) if counts[name] == 1 else None
            if actual != blob:
                mismatches.append({"entry": name, "count": counts[name],
                                   "expectedSha256": hashlib.sha256(blob).hexdigest(),
                                   "actualSha256": hashlib.sha256(actual).hexdigest() if actual is not None else None})
        if mismatches:
            diagnostic = ROOT / "build/forge-native-contracts" / (loader + "-engine-parity.json")
            diagnostic.parent.mkdir(parents=True, exist_ok=True)
            diagnostic.write_text(json.dumps({"jar": str(path), "jarSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                                               "mismatches": mismatches}, indent=2) + "\n")
        check(not mismatches, "Shared engine was changed or omitted: "
              + (mismatches[0]["entry"] if mismatches else "") + " in " + str(path))
        check(not any(name.startswith("org/apache/maven/") for name in counts),
              "Raw Maven classes must not enter the native mod")
        from io import BytesIO
        for name in counts:
            if name.endswith(".jar") and name.startswith(("META-INF/jars/", "META-INF/jarjar/")):
                with zipfile.ZipFile(BytesIO(archive.read(name))) as dependency:
                    check(not any(entry.startswith("org/apache/maven/") for entry in dependency.namelist()),
                          "Raw Maven dependency must not be nested: " + name)
        for name in counts:
            if name.endswith(".class"):
                blob = archive.read(name)
                check(blob[:4] == b"\xca\xfe\xba\xbe" and int.from_bytes(blob[6:8], "big") <= java + 44,
                      "Native class exceeds target Java: " + name)
        if loader == "fabric":
            metadata = json.loads(archive.read("fabric.mod.json"))
            check(metadata["id"] == "openallay", "Fabric mod identity differs")
            check(family is not None or metadata["depends"]["minecraft"] in (target, "~" + target), "Fabric target metadata differs")
            check(metadata["depends"]["java"] == ">=" + str(java), "Fabric Java metadata differs")
        else:
            descriptor = "META-INF/mods.toml" if loader == "forge" or target in ("1.20.1", "1.20.4", "1.20.3", "1.20.2") else "META-INF/neoforge.mods.toml"
            other = "META-INF/neoforge.mods.toml" if descriptor.endswith("/mods.toml") else "META-INF/mods.toml"
            check(counts[descriptor] == 1 and counts[other] == 0, "Wrong or competing native loader descriptor")
            metadata = archive.read(descriptor).decode()
            mod = metadata.split("[[mods]]", 1)[1].split("[[dependencies.", 1)[0]
            check(re.search(r'(?m)^modId\s*=\s*"openallay"', mod) is not None, "FML mod identity differs")
            blocks = metadata.split("[[dependencies.")[1:]
            minecraft = [block for block in blocks if re.search(r'(?m)^modId\s*=\s*"minecraft"', block)]
            check(len(minecraft) == 1 and (family is not None or 'versionRange="[' + target + ']"' in minecraft[0].replace(" ", "")), "FML target metadata differs")
            if loader == "forge":
                compact = metadata.replace(" ", "")
                check('modId="forge"' in compact and 'modId="neoforge"' not in compact,
                      "Forge must declare its actual loader identity")
                profile = read_properties(ROOT / "gradle/minecraft-targets" / (target + ".properties"))
                forge_dependencies = [block for block in blocks if re.search(r'(?m)^modId\s*=\s*"forge"', block)]
                forge_native_version = profile["forge_version"].removeprefix(target + "-")
                check(len(forge_dependencies) == 1 and 'versionRange="[' + forge_native_version + ',)"' in forge_dependencies[0].replace(" ", ""),
                      "Forge loader dependency differs")
                check('loaderVersion="' + profile["forge_loader_version_range"] + '"' in compact,
                      "Forge FML dependency differs")
            if loader == "forge" or target == "1.20.1":
                check('modId="forge"' in metadata.replace(" ", ""), "Legacy FML must use actual Forge loader dependency")
                mixins = json.loads(archive.read("openallay.client.mixins.json"))
                check(mixins.get("refmap") == "openallay.refmap.json" and counts["openallay.refmap.json"] == 1,
                      "Legacy native Mixin refmap missing")
        if loader == "forge":
            verify_forge_shared_runtimes(archive, counts, ROOT, original_engine)
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
    parser.add_argument("--loader", choices=("fabric", "forge", "neoforge"))
    args = parser.parse_args()
    try:
        profile = read_properties(ROOT / "gradle/minecraft-targets" / (args.target + ".properties"))
        check(profile["minecraft_version"] == args.target, "Target/profile mismatch")
        version = read_properties(ROOT / "gradle.properties")["version"]
        original = engine_files(ROOT)
        selected_loaders = target_loaders(ROOT, args.target)["loaders"]
        if args.loader:
            check(args.loader in selected_loaders, "Loader is not an actual target identity")
            selected_loaders = [args.loader]
        packages = [verify(ROOT / loader / "build/libs" / ("openallay-" + loader + "-" + args.target + "-" + version + ".jar"),
                           loader, args.target, int(profile["java_version"]), original, args.bundled_builder) for loader in selected_loaders]
        print(json.dumps({"nativePackageChecks": "passed", "packages": packages,
                          "gameMixinAndSameJarRangeAcceptance": "not established"}, indent=2))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, "Native package verification failed: " + str(error) + "\n")


if __name__ == "__main__":
    main()
