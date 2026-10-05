#!/usr/bin/env python3
"""Build/stage reviewed native artifact families. Offline checks are not game acceptance."""
import argparse
from importlib.util import module_from_spec, spec_from_file_location
from io import BytesIO
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def module(name, filename):
    spec = spec_from_file_location(name, ROOT / "scripts" / filename)
    result = module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


artifacts = module("minecraft_artifact_catalog", "minecraft-artifacts.py")
builder = module("minecraft_artifact_builder", "verify-bundled-extensions.py")
native = module("minecraft_artifact_engine", "verify-native-target-package.py")
compiler = module("minecraft_artifact_compiler", "compile-native-target.py")
tokenizer = module("minecraft_artifact_tokenizer", "verify-tokenizer-packaging.py")
require = artifacts.require


def catalog():
    # No alternate catalog argument or user-provided admission labels.
    return artifacts.read_catalog(ROOT / "gradle/minecraft-artifacts.json")


def version():
    values = native.read_properties(ROOT / "gradle.properties")
    value = values["version"]
    artifacts.describe(catalog()["acceptedFamilies"][0], value)
    return value


def select(data, target, family_ids=None):
    if family_ids is None:
        # Exact-target development must not silently advertise an accepted range.
        families = [artifacts.resolve(data, target, loader) for loader in artifacts.LOADERS]
        require(all(family["supportedTargets"] == [target] for family in families),
                "An interval requires explicit accepted family IDs")
    else:
        ids = family_ids.split(",")
        require(ids and all(ids) and len(ids) == len(set(ids)), "Expected distinct accepted family IDs")
        families = [family for family in data["acceptedFamilies"] if family["id"] in ids]
        require(len(families) == len(ids), "Unknown/nonaccepted family; candidates do not publish")
    require(len({family["loader"] for family in families}) == len(families),
            "At most one accepted family per loader in one build")
    require(all(family["buildTarget"] == target for family in families),
            "Explicit minecraftTarget must equal every accepted family buildTarget")
    return families


def groups(data):
    result = {}
    for family in data["acceptedFamilies"]:
        result.setdefault(family["buildTarget"], []).append(family)
    return list(result.items())


def metadata(path, family, release_version):
    described = artifacts.describe(family, release_version)
    with zipfile.ZipFile(path) as archive:
        entries = archive.namelist()
        require(len(entries) == len(set(entries)), "Duplicate product JAR entries")
        require(archive.testzip() is None, "Corrupt product JAR entry")
        legacy = "tome" + "wisp"
        require(not any(re.search(r"(^|/)" + legacy + r"(/|\.|$)", name, re.I) for name in entries),
                "Legacy package branding is present")
        for entry in ("dev/openallay/OpenAllayBootstrap.class",
                      "dev/openallay/guide/history/SqliteGuideHistoryStore.class",
                      "dev/openallay/guide/semantic/SemanticMessageParser.class"):
            require(entry in entries, "Required product class missing: " + entry)
        for dependency in ("commonmark-0.28.0.jar", "commonmark-ext-gfm-tables-0.28.0.jar", "sqlite-jdbc-3.50.3.0.jar"):
            require(any(directory + dependency in entries for directory in ("META-INF/jars/", "META-INF/jarjar/")),
                    "Required product dependency missing: " + dependency)
        if family["loader"] == "fabric":
            value = json.loads(archive.read("fabric.mod.json"))
            require(value["id"] == "openallay" and value["name"] == "OpenAllay"
                    and value["version"] == release_version and value["environment"] == "*",
                    "Fabric product identity differs")
            require(value["depends"]["minecraft"] == described["fabricMinecraftPredicate"],
                    "Fabric Minecraft predicate differs from exact accepted family")
        else:
            descriptors = [entry for entry in ("META-INF/mods.toml", "META-INF/neoforge.mods.toml") if entry in entries]
            expected = "META-INF/mods.toml" if family["buildTarget"] in ("1.20.1", "1.20.2", "1.20.3", "1.20.4") else "META-INF/neoforge.mods.toml"
            require(descriptors == [expected], "Wrong or competing native loader descriptor")
            value = archive.read(expected).decode("utf-8")
            mods = value.split("[[mods]]", 1)[1].split("[[dependencies.", 1)[0]
            fields = dict(re.findall(r'(?m)^\s*(modId|displayName|version)\s*=\s*"([^"]+)"', mods))
            require(fields == {"modId": "openallay", "displayName": "OpenAllay", "version": release_version},
                    "NeoForge product identity differs")
            dependencies = []
            for block in value.split("[[dependencies.openallay]]")[1:]:
                fields = dict(re.findall(r'(?m)^\s*(modId|versionRange)\s*=\s*"([^"]+)"', block.split("[[", 1)[0]))
                if fields.get("modId") == "minecraft":
                    dependencies.append(fields)
            require(len(dependencies) == 1 and dependencies[0].get("versionRange") == described["minecraftMavenRange"],
                    "NeoForge Minecraft range differs from exact accepted family")


def builder_support(path, family, lock):
    with zipfile.ZipFile(path) as archive:
        with zipfile.ZipFile(BytesIO(archive.read(builder.resource_path(lock)))) as nested:
            descriptor = builder.verify_manifest(nested.read(builder.DESCRIPTOR), lock)
    declared = {item["minecraftVersionRange"] for item in descriptor["support"]["targets"]
                if item["loader"] == family["loader"]}
    require(set(family["supportedTargets"]).issubset(declared),
            "Builder declaration does not include every accepted family target")
    # The reviewed artifact catalog owns release admission. Builder declarations
    # alone do not admit candidates or prove same-JAR runtime compatibility.


def verify(families, directory=None):
    release_version = version()
    expected = [artifacts.describe(family, release_version)["filename"] for family in families]
    if directory is not None:
        require(directory.is_dir(), "Missing staged release directory")
        require(sorted(path.name for path in directory.glob("*.jar")) == sorted(expected),
                "Staged release must contain exactly the selected accepted artifacts")
    engine = native.engine_files(ROOT)
    lock = builder.prepare.load_manifest(ROOT / "distribution/extensions.lock.json")
    builder_bytes = None
    records = []
    for family, filename in zip(families, expected):
        path = (directory / filename if directory is not None else ROOT / family["loader"] / "build/libs" / filename).resolve(strict=True)
        metadata(path, family, release_version)
        with zipfile.ZipFile(path) as archive:
            for name, content in engine.items():
                require(archive.read(name) == content, "Shared engine was changed or omitted: " + name)
        builder.verify_package(path, family["loader"], lock)
        builder_support(path, family, lock)
        with zipfile.ZipFile(path) as archive:
            content = archive.read(builder.resource_path(lock))
        require(builder_bytes is None or content == builder_bytes, "All families must bundle identical universal Builder bytes")
        builder_bytes = content
        tokenizer.verify(path, family["loader"])
        environment = dict(os.environ, OPENALLAY_MINECRAFT_TARGET=family["buildTarget"])
        subprocess.run([str(ROOT / "scripts/verify-sqlite-packaging.sh"), family["loader"], str(path)],
                       cwd=ROOT, env=environment, check=True, stdout=sys.stderr)
        records.append({**artifacts.describe(family, release_version), "artifactPath": str(path),
                        "artifactSha256": artifacts.file_hash(path, artifacts.MAX_ARTIFACT_BYTES)})
    return records


def publication_records(families, directory, receipt_directory=None):
    original_selection = directory / "accepted-originals.json"
    if original_selection.exists():
        require(receipt_directory is not None, "Original accepted publication needs existing final-path receipts")
        promoter = module("accepted_original_publication", "promote-accepted-artifacts.py")
        selection_path = ROOT / "distribution/accepted-release-artifacts.json"
        require(original_selection.read_bytes() == selection_path.read_bytes(), "Original selection differs from checked-in release admission")
        return promoter.records(selection_path, ROOT / "build/accepted-originals", directory, receipt_directory)
    records = verify(families, directory)
    data = catalog()
    for record in records:
        family = artifacts.resolve(data, record["buildTarget"], record["loader"])
        if family["supportedTargets"] != ["26.2"]:
            require(receipt_directory is not None, "New accepted families require retained final-artifact runtime receipts")
            # This is consistency only. Admission is a reviewed acceptedFamilies source edit.
            artifacts.verify_receipt(family, receipt_directory / (family["id"] + ".json"),
                                     Path(record["artifactPath"]), record["artifactSha256"])
    return records


def build_and_stage(directory):
    require(not directory.exists(), "Refusing to overwrite an existing release directory")
    data = catalog()
    directory.mkdir(parents=True)
    # Full shared/native feature and pinned Builder tests run once on the mainline.
    # Native assemblies below reuse that tested Builder, with final byte/identity gates.
    subprocess.run([str(ROOT / "gradlew"), "--max-workers=2", "-PminecraftTarget=26.2",
                    "-PtestBundledExtensions=true", ":extension-api:test", ":common:test", ":fabric:test", ":neoforge:test",
                    ":stageBundledExtensions"], cwd=ROOT, check=True)
    for target, families in groups(data):
        selection = ",".join(family["id"] for family in families)
        # The existing compiler owns early NeoForge's actual isolated Java21 route.
        # No clean between families, no per-minor feature matrix, no candidate admission.
        compiler.compile_target(ROOT, target, loaders=tuple(family["loader"] for family in families),
                                artifact_ids=selection)
        for record in verify(families):
            shutil.copyfile(record["artifactPath"], directory / record["filename"])
    records = verify(data["acceptedFamilies"], directory)
    (directory / "SHA256SUMS").write_text("".join(record["artifactSha256"] + "  " + record["filename"] + "\n"
                                                  for record in records), encoding="utf-8")
    return records


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    selection = commands.add_parser("select", help="Pre-build accepted identity; not runtime acceptance")
    selection.add_argument("--target", required=True)
    selection.add_argument("--families", required=True)
    selection.add_argument("--version", required=True)
    build = commands.add_parser("build-and-stage")
    build.add_argument("directory", type=Path)
    verification = commands.add_parser("verify")
    verification.add_argument("directory", type=Path, nargs="?")
    verification.add_argument("--target", default="26.2")
    verification.add_argument("--families")
    publishing = commands.add_parser("publication-records")
    publishing.add_argument("directory", type=Path)
    publishing.add_argument("--receipt-directory", type=Path)
    args = parser.parse_args(argv)
    try:
        data = catalog()
        if args.command == "select":
            result = {family["loader"]: artifacts.describe(family, args.version)
                      for family in select(data, args.target, args.families)}
        elif args.command == "build-and-stage":
            result = build_and_stage(args.directory.resolve())
        elif args.command == "publication-records":
            result = publication_records(data["acceptedFamilies"], args.directory.resolve(), args.receipt_directory)
        else:
            families = data["acceptedFamilies"] if args.directory is not None and args.families is None else select(data, args.target, args.families)
            result = verify(families, args.directory.resolve() if args.directory is not None else None)
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except (OSError, ValueError, TypeError, KeyError, IndexError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        print("Accepted Minecraft artifact wiring refused: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
