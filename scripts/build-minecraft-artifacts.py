#!/usr/bin/env python3
"""Build/stage reviewed native artifact families. Offline checks are not game acceptance."""
import argparse
from collections import Counter
from importlib.util import module_from_spec, spec_from_file_location
from io import BytesIO
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile
from minecraft_target_loaders import target_loaders
from stock8_selection_custody import group_change_paths, verify_group_pair, early_producer_paths, verify_inactive_early_producer
from release_comment_custody import PATHS as COMMENT_PATHS, POLICY_PATH as COMMENT_POLICY_PATH, verify_pair as verify_comment_pair

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
commonmark_custody = module("commonmark_provider_custody", "verify-commonmark-provider-custody.py")
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
        families = [artifacts.resolve(data, target, loader) for loader in target_loaders(ROOT, target)["loaders"]]
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


def filter_groups(data, targets=None):
    planned = groups(data)
    if targets is None or targets == "":
        return planned
    require(type(targets) is str, "Build targets must be an exact comma-separated string")
    requested = targets.split(",")
    require(all(requested) and len(requested) == len(set(requested)), "Build targets must be nonempty and distinct")
    available = {target for target, _ in planned}
    require(set(requested).issubset(available), "Build targets must name exact accepted catalog build targets")
    return [(target, families) for target, families in planned if target in requested]


def groups(data):
    result = {}
    for family in data["acceptedFamilies"]:
        result.setdefault(family["buildTarget"], []).append(family)
    return list(result.items())


LEGACY_RECIPES = {"forge-flat": ("1.16.5", "jar", "forge16165"),
                  "forge-stock8": ("1.12.2", "jar", "forge1122")}


def package_path(family, release_version):
    filename = artifacts.describe(family, release_version)["filename"]
    if family["packagingRecipe"] in LEGACY_RECIPES:
        return ROOT / "native-builds" / LEGACY_RECIPES[family["packagingRecipe"]][2] / "build/libs" / filename
    return ROOT / family["loader"] / "build/libs" / filename


def legacy_package(path, family, release_version, approved_package_source=None):
    recipe = family["packagingRecipe"]
    target, kind, _ = LEGACY_RECIPES[recipe]
    require(family == artifacts.family_for("forge", target, [target]) and family["artifactKind"] == kind,
            "Legacy packaging recipe must match its exact accepted stock Forge tuple")
    packer = module("legacy_release_package", "materialize-stock8-release.py" if recipe == "forge-stock8" else "package-legacy-forge-release.py")
    result = (packer.verify_release(path, family, release_version, ROOT) if approved_package_source is None else
              packer.verify_release(path, family, release_version, ROOT, approved_package_source=approved_package_source))
    require(type(result) is dict and set(result) == {"coreBytes", "sqlite", "sharedRuntimes"},
            "Legacy verifier must return the current exact package proof shape")
    require(type(result["coreBytes"]) is bytes and 0 < len(result["coreBytes"]) <= artifacts.MAX_ARTIFACT_BYTES, "Legacy feature product missing")
    artifacts.shape(result["sqlite"], {"artifactSha256", "payloadSha256"}, "Legacy SQLite proof")
    for value in result["sqlite"].values():
        artifacts.hash_text(value)
    artifacts.shape(result["sharedRuntimes"], {"extension-api", "runtime-rhino"}, "Legacy shared runtime proof")
    for value in result["sharedRuntimes"].values():
        artifacts.hash_text(value)
    return result


def package_proofs(path, family, approved_package_source=None):
    if family["packagingRecipe"] in LEGACY_RECIPES:
        result = legacy_package(path, family, version(), approved_package_source)
        return result["sqlite"], result["sharedRuntimes"]
    return sqlite_package(path, family["loader"]), (forge_runtime_hashes(path) if family["loader"] == "forge" else {})


def legacy_builder(archive, family, lock):
    entries = builder.archive_entries(archive, "legacy feature JAR")
    expected = "META-INF/openallay/bundled-extensions/openallay-builder.jar" if family["packagingRecipe"] == "forge-stock8" else builder.resource_path(lock)
    require([name for name in entries if name.startswith(builder.RESOURCE_DIRECTORY) and name.endswith(".jar")] == [expected],
            "Legacy product must contain exactly one raw universal Builder")
    require(not any(name.startswith("dev/openallay/builder/") for name in entries)
            and builder.DESCRIPTOR not in entries, "Builder cannot be flattened into legacy core")
    content = archive.read(expected)
    provenance = builder.prepare.decode_json(archive.read(builder.PROVENANCE))
    if family["packagingRecipe"] == "forge-stock8":
        require(provenance["artifact"] == {"path": expected, "sha256": hashlib.sha256(content).hexdigest()}, "Actual stock8 Builder provenance differs")
        provenance = {**provenance, "artifact": {"path": builder.resource_path(lock), "sha256": hashlib.sha256(content).hexdigest()}}
    builder.verify_provenance(provenance, lock, hashlib.sha256(content).hexdigest(), False)
    additional = frozenset({("forge", "1.12.2"), ("forge", "1.16.5")}) if family["packagingRecipe"] == "forge-stock8" else frozenset()
    builder.verify_universal(content, lock, additional_target_pairs=additional)
    if family["packagingRecipe"] == "forge-stock8":
        require(json.loads(archive.read("mcmod.info"))[0]["modid"] == "openallay", "Stock8 core registration differs")
        require("META-INF/jarjar/metadata.json" not in entries, "Stock8 raw Builder cannot be JarJar registered")
        for path in entries:
            if path.endswith(".jar") and path != expected:
                with zipfile.ZipFile(BytesIO(archive.read(path))) as nested:
                    names = builder.archive_entries(nested, "stock8 nested resource")
                    require(builder.DESCRIPTOR not in names and not any(name.startswith("dev/openallay/builder/") for name in names),
                            "Stock8 product cannot embed a second Builder owner")
    else:
        builder.reject_builder_registration(archive, entries, "forge", lock)
    with zipfile.ZipFile(BytesIO(content)) as nested:
        descriptor = builder.verify_manifest(nested.read(builder.DESCRIPTOR), lock, additional_target_pairs=additional)
    declarations = {row["minecraftVersionRange"] for row in descriptor["support"]["targets"] if row["loader"] == "forge"}
    require(set(family["supportedTargets"]).issubset(declarations), "Builder does not declare accepted legacy target")
    return content


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
        for dependency in ("sqlite-jdbc-3.50.3.0.jar",):
            require(any(directory + dependency in entries for directory in ("META-INF/jars/", "META-INF/jarjar/")),
                    "Required product dependency missing: " + dependency)
        commonmark_package(archive, entries, family["loader"], family["buildTarget"])
        if family["loader"] == "fabric":
            require(not any(name in entries for name in ("META-INF/mods.toml", "META-INF/neoforge.mods.toml")),
                    "Fabric product cannot declare an FML loader")
            value = json.loads(archive.read("fabric.mod.json"))
            require(value["id"] == "openallay" and value["name"] == "OpenAllay"
                    and value["version"] == release_version and value["environment"] == "*",
                    "Fabric product identity differs")
            require(value["depends"]["minecraft"] == described["fabricMinecraftPredicate"],
                    "Fabric Minecraft predicate differs from exact accepted family")
        else:
            require("fabric.mod.json" not in entries, "FML product cannot declare Fabric")
            descriptors = [entry for entry in ("META-INF/mods.toml", "META-INF/neoforge.mods.toml") if entry in entries]
            expected = "META-INF/mods.toml" if family["loader"] == "forge" or family["buildTarget"] in ("1.20.1", "1.20.2", "1.20.3", "1.20.4") else "META-INF/neoforge.mods.toml"
            require(descriptors == [expected], "Wrong or competing native loader descriptor")
            value = archive.read(expected).decode("utf-8")
            mods = value.split("[[mods]]", 1)[1].split("[[dependencies.", 1)[0]
            fields = dict(re.findall(r'(?m)^\s*(modId|displayName|version)\s*=\s*"([^"]+)"', mods))
            require(fields == {"modId": "openallay", "displayName": "OpenAllay", "version": release_version},
                    "FML product identity differs")
            dependencies = []
            for block in value.split("[[dependencies.openallay]]")[1:]:
                fields = dict(re.findall(r'(?m)^\s*(modId|versionRange)\s*=\s*"([^"]+)"', block.split("[[", 1)[0]))
                if fields.get("modId") == "minecraft":
                    dependencies.append(fields)
            require(len(dependencies) == 1 and dependencies[0].get("versionRange") == described["minecraftMavenRange"],
                    "FML Minecraft range differs from exact accepted family")


# Accepted full canonical source-port artifact: run 37561859246 at source
# a2eff926602ca8e8bf94fde01a03dca53ec3bef6. This is a real artifact identity,
# not an internal protocol version or an upstream binary acceptance shortcut.
COMMONMARK_RUNTIME_SHA256 = "dff5404332182c794aec52538a9a620b61032041a3b08ddbb972ddf246021a02"


def commonmark_package(archive, entries, loader, build_target=None):
    version = native.read_properties(ROOT / "gradle.properties")["commonmark_version"]
    directory = "META-INF/jars/" if loader == "fabric" else "META-INF/jarjar/"
    isolated_early = loader == "neoforge" and build_target in ("1.20.2", "1.20.3", "1.20.5")
    expected = directory + ("" if loader == "fabric" or isolated_early else "dev.openallay.") + "openallay-commonmark-" + version + ".jar"
    matches = [name for name in entries if name.endswith(".jar") and "commonmark" in Path(name).name.lower()]
    require(matches == [expected], "Exactly one canonical CommonMark runtime must be nested; upstream binary JARs are forbidden")
    require(not any(name.startswith("org/commonmark/") and name.endswith(".class") for name in entries),
            "CommonMark classes cannot compete with the canonical nested source owner")
    if loader == "fabric":
        registered = [item["file"] for item in json.loads(archive.read("fabric.mod.json")).get("jars", [])]
        require(registered.count(expected) == 1, "Canonical CommonMark needs one Fabric registration")
    else:
        rows = json.loads(archive.read("META-INF/jarjar/metadata.json"))["jars"]
        registered = [row for row in rows if row["path"] == expected]
        require(len(registered) == 1 and registered[0]["identifier"] == {"group": "dev.openallay", "artifact": "runtime-commonmark"},
                "Canonical CommonMark needs exactly one project-owned FML registration")
        require(registered[0]["version"]["artifactVersion"] == version
                and registered[0]["version"]["range"] in ("[" + version + "]", "[" + version + ",)"),
                "Canonical CommonMark JarJar external version/range differs")
    content = archive.read(expected)
    commonmark_custody.verify_commonmark_payload(content, loader, version)
    with zipfile.ZipFile(BytesIO(content)) as nested:
        names = nested.namelist()
        require(len(names) == len(set(names)) and nested.testzip() is None, "Corrupt canonical CommonMark runtime")
        classes = [name for name in names if name.endswith(".class")]
        require(len(classes) == 213 and all(name.startswith("org/commonmark/") for name in classes),
                "Canonical CommonMark must retain its proved complete 213-class closure")
        for name in classes:
            blob = nested.read(name)
            require(len(blob) >= 8 and blob[:4] == b"\xca\xfe\xba\xbe" and int.from_bytes(blob[6:8], "big") == 52,
                    "Canonical CommonMark Java8 class ABI differs: " + name)
        for name in ("org/commonmark/parser/Parser.class", "org/commonmark/node/SourceSpan.class",
                     "org/commonmark/ext/gfm/tables/TablesExtension.class",
                     "org/commonmark/internal/util/entities.txt",
                     "META-INF/licenses/commonmark/LICENSE-commonmark.txt",
                     "META-INF/licenses/commonmark/core-LICENSE.txt", "META-INF/licenses/commonmark/tables-LICENSE.txt",
                     "META-INF/openallay/commonmark-source-changes/commonmark-source-manifest.json",
                     "META-INF/openallay/commonmark-source-changes/commonmark-java8.patch"):
            require(name in names, "Canonical CommonMark source/license/provenance missing: " + name)


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


def sqlite_package(path, loader):
    """Inspect final nested SQLite bytes without testClasses or a JVM probe."""
    expected = "sqlite-jdbc-" + native.read_properties(ROOT / "gradle.properties")["sqlite_jdbc_version"] + ".jar"
    directory = "META-INF/jars/" if loader == "fabric" else "META-INF/jarjar/"
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        matches = [name for name in names if name.endswith(".jar") and Path(name).name.startswith("sqlite-jdbc-")]
        require(matches == [directory + expected], "Exactly the pinned SQLite dependency must be nested")
        if loader == "fabric":
            registered = [item["file"] for item in json.loads(archive.read("fabric.mod.json")).get("jars", [])]
        else:
            jars = json.loads(archive.read("META-INF/jarjar/metadata.json"))["jars"]
            registered = [item["path"] for item in jars]
            rows = [item for item in jars if item["path"] == matches[0]]
            require(len(rows) == 1 and rows[0]["identifier"] == {"group": "org.xerial", "artifact": "sqlite-jdbc"}
                    and rows[0]["version"]["artifactVersion"] == expected[len("sqlite-jdbc-"):-4],
                    "SQLite JarJar coordinate differs")
        require(registered.count(matches[0]) == 1, "SQLite needs exactly one native loader registration")
        content = archive.read(matches[0])
    with zipfile.ZipFile(BytesIO(content)) as nested:
        names = nested.namelist()
        require(len(names) == len(set(names)) and nested.testzip() is None, "Invalid SQLite dependency archive")
        require("org/sqlite/JDBC.class" in names, "SQLite provider class missing")
        providers = nested.read("META-INF/services/java.sql.Driver").decode("utf-8").splitlines()
        require([line.strip() for line in providers if line.strip() and not line.lstrip().startswith("#")] == ["org.sqlite.JDBC"],
                "SQLite JDBC provider differs")
        for platform in ("Linux/x86_64", "Linux/aarch64", "Mac/x86_64", "Mac/aarch64", "Windows/x86_64", "Windows/aarch64"):
            require(any(name.startswith("org/sqlite/native/" + platform + "/") and not name.endswith("/") for name in names),
                    "SQLite native platform missing: " + platform)
        payload = {name: hashlib.sha256(nested.read(name)).hexdigest() for name in sorted(names)
                   if not name.endswith("/") and (name.startswith("org/sqlite/") or name == "META-INF/services/java.sql.Driver")}
    return {"artifactSha256": hashlib.sha256(content).hexdigest(), "payloadSha256": hashlib.sha256(
        json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()).hexdigest()}


def verify_sqlite_archive_identity(path, family, sqlite):
    """Authenticate raw SQLite or its one exact Loom wrapper without changing receipts."""
    raw_sha="a3f53a2aa15ae9425a9e793bbe9c8e5288febeb4b65ef5c1a4e80d4c2045cf08"
    loom_sha="0bc822a176492a4d3e2547b13a54bdf8540ea4e3a8ede8edc2d02f9a94c3c12a"
    if family["loader"] != "fabric":
        require(sqlite["artifactSha256"] == raw_sha, "SQLite raw upstream provider archive differs")
        return
    require(family["packagingRecipe"] == "nested-mod" and sqlite["artifactSha256"] == loom_sha,
            "Fabric SQLite must use the exact actual Loom wrapper")
    with zipfile.ZipFile(path) as product:
        matches=[name for name in product.namelist() if name=="META-INF/jars/sqlite-jdbc-3.50.3.0.jar"]
        require(len(matches)==1, "One exact Fabric SQLite registered archive required")
        raw=product.read(matches[0])
    require(hashlib.sha256(raw).hexdigest()==loom_sha, "Fabric SQLite wrapper bytes differ")
    with zipfile.ZipFile(BytesIO(raw)) as nested:
        names=[name for name in nested.namelist() if not name.endswith("/")]
        require(len(names)==163 and len(names)==len(set(names)) and nested.testzip() is None,
                "Fabric SQLite complete original closure differs")
        require(nested.read("fabric.mod.json")==b'{\n  "schemaVersion": 1,\n  "id": "org_xerial_sqlite-jdbc",\n  "version": "3.50.3.0",\n  "name": "sqlite-jdbc",\n  "custom": {\n    "fabric-loom:generated": true\n  }\n}',
                "Fabric SQLite generated metadata differs from exact Loom output")
        payload={name:hashlib.sha256(nested.read(name)).hexdigest() for name in sorted(names) if name!="fabric.mod.json"}
    require(len(payload)==162 and hashlib.sha256(json.dumps(payload,sort_keys=True,separators=(",", ":")).encode()).hexdigest()=="a8c3d6cd0c5d83b829693a80c1b0ddb347a995155998afc8109fe32b62f69e5f",
            "Fabric SQLite original 162-entry class/native/service/legal closure changed")


def sqlite_cross_family_payload(path, family):
    """Compare exact 149 provider/native entries and only two known JDBC service encodings."""
    with zipfile.ZipFile(path) as outer:
        if family["packagingRecipe"] == "nested-mod":
            matches=[name for name in outer.namelist() if name.endswith("/sqlite-jdbc-3.50.3.0.jar")]
            require(len(matches)==1, "One exact SQLite archive required for cross-family parity")
            content=outer.read(matches[0])
            with zipfile.ZipFile(BytesIO(content)) as nested:
                entries={name:nested.read(name) for name in nested.namelist()
                         if not name.endswith("/") and (name.startswith("org/sqlite/") or name=="META-INF/services/java.sql.Driver")}
        else:
            entries={name:outer.read(name) for name in outer.namelist()
                     if not name.endswith("/") and (name.startswith("org/sqlite/") or name=="META-INF/services/java.sql.Driver")}
    service=entries.pop("META-INF/services/java.sql.Driver")
    require(service in (b"org.sqlite.JDBC", b"org.sqlite.JDBC\n"),
            "SQLite JDBC registration must use an exact original service encoding")
    require(len(entries)==149 and all(name.startswith("org/sqlite/") for name in entries),
            "Exact 149 SQLite provider/native entry paths required")
    payload={name:hashlib.sha256(content).hexdigest() for name,content in sorted(entries.items())}
    return hashlib.sha256(json.dumps(payload,sort_keys=True,separators=(",", ":")).encode()).hexdigest()


def engine_entry(archive, family, name, path=None):
    """Keep executable/resource parity; three stock8 legal resources have exact flat owners."""
    if family["packagingRecipe"] == "forge-stock8" and name in ("META-INF/licenses/maven-artifact/LICENSE",
            "META-INF/licenses/maven-artifact/NOTICE", "data/openallay/models/LICENSE.models.dev"):
        stock8 = module("stock8_license_owner", "materialize-stock8-release.py")
        owner = stock8.engine_legal_owners(path)[name]
        relocated, owner_sha = owner["path"], owner["sha256"]
        require(name not in archive.namelist() and relocated in archive.namelist(), "Stock8 engine legal resource must have one exact engine legal owner")
        content = archive.read(relocated)
        require(hashlib.sha256(content).hexdigest() == owner_sha,
                "Stock8 engine legal resource differs from retained complete ownership proof")
        return content
    if family["packagingRecipe"] == "forge-flat":
        forge16 = module("forge16_legal_owner", "package-legacy-forge-release.py")
        if name in forge16.FORGE16_ENGINE_LEGAL_INPUTS:
            target, owner_sha = forge16.engine_legal_owner(path, name)
            require(name not in archive.namelist() and target in archive.namelist(), "Forge16 legal resource needs one exact relocated owner")
            content = archive.read(target)
            require(hashlib.sha256(content).hexdigest() == owner_sha, "Forge16 actual legal resource bytes differ")
            return content
    return archive.read(name)


def verify(families, directory=None, engine_manifest=None, approved_package_sources=None):
    release_version = version()
    expected = [artifacts.describe(family, release_version)["filename"] for family in families]
    if directory is not None:
        require(directory.is_dir(), "Missing staged release directory")
        require(sorted(path.name for path in directory.iterdir() if path.suffix in (".jar", ".zip")) == sorted(expected),
                "Staged release must contain exactly the selected accepted artifacts")
    engine = native.engine_files(ROOT) if engine_manifest is None else None
    expected_engine = ({name: hashlib.sha256(content).hexdigest() for name, content in engine.items()}
                       if engine_manifest is None else engine_manifest)
    require(type(expected_engine) is dict and expected_engine and all(
        type(name) is str and not name.startswith("/") and ".." not in name.split("/")
        and artifacts.hash_text(digest) for name, digest in expected_engine.items()), "Invalid compiled engine manifest")
    require("dev/openallay/guide/GuideService.class" in expected_engine
            and "dev/openallay/FeatureServices.class" in expected_engine, "Compiled engine manifest missing required owners")
    lock = builder.prepare.load_manifest(ROOT / "distribution/extensions.lock.json")
    require(lock["source"]["revision"] == "6e977110cbe8e0ca0b39c012f0cdfc10bafffef2",
            "All release families require the canonical accepted Builder source")
    builder_bytes = None
    records = []
    sqlite_payload = None
    for family, filename in zip(families, expected):
        path = (directory / filename if directory is not None else package_path(family, release_version)).resolve(strict=True)
        legacy = family["packagingRecipe"] in LEGACY_RECIPES
        proof = legacy_package(path, family, release_version, (approved_package_sources or {}).get(family["id"])) if legacy else None
        if not legacy:
            metadata(path, family, release_version)
        with zipfile.ZipFile(BytesIO(proof["coreBytes"]) if legacy else path) as archive:
            require(len(archive.namelist()) == len(set(archive.namelist())) and archive.testzip() is None,
                    "Invalid feature product archive")
            for name, digest in expected_engine.items():
                require(hashlib.sha256(engine_entry(archive, family, name, path)).hexdigest() == digest,
                        "Shared engine was changed or omitted: " + name)
            profile = native.read_properties(ROOT / "gradle/minecraft-targets" / (family["buildTarget"] + ".properties"))
            for name in archive.namelist():
                if name.endswith(".class"):
                    content = archive.read(name)
                    require(len(content) >= 8 and content[:4] == b"\xca\xfe\xba\xbe" and 45 <= int.from_bytes(content[6:8], "big") <= int(profile["java_version"]) + 44,
                            "Product class exceeds target Java: " + name)
        if legacy:
            with zipfile.ZipFile(BytesIO(proof["coreBytes"])) as archive:
                content = legacy_builder(archive, family, lock)
        else:
            builder.verify_package(path, family["loader"], lock)
            builder_support(path, family, lock)
            with zipfile.ZipFile(path) as archive:
                content = archive.read(builder.resource_path(lock))
        require(builder_bytes is None or content == builder_bytes, "All families must bundle identical universal Builder bytes")
        builder_bytes = content
        if not legacy:
            tokenizer.verify(path, family["loader"])
        sqlite = proof["sqlite"] if legacy else sqlite_package(path, family["loader"])
        verify_sqlite_archive_identity(path,family,sqlite)
        require(sqlite["payloadSha256"] in ("dbecc49b0d53558892cf78877c8d727a886a19e70d8b665b9595921f7a7759f6",
                                           "73a3c5413e82e8d3ffceea4b78690e9539b8e64d82764696dff52b8efbd81839"),
                "SQLite final-byte receipt differs from known full/flat service custody")
        cross_payload=sqlite_cross_family_payload(path,family)
        require(cross_payload == "b01426098b42da7b43ce237349492b58d58da1b66dfd126b333ba10ec7fda29c",
                "SQLite exact 149-entry provider/native byte ledger changed")
        require(sqlite_payload is None or cross_payload == sqlite_payload,
                "All accepted families must bundle identical SQLite provider/native entry bytes")
        sqlite_payload = cross_payload
        if not legacy:
            interval = module("accepted_package_guard", "verify-minecraft-binary-intervals.py")
            interval.package_guard(path, family, release_version, ROOT)
        if engine is not None and not legacy:
            native.verify(path, family["loader"], family["buildTarget"], int(profile["java_version"]),
                          engine, bundled_builder=True, family=family)
        records.append({**artifacts.describe(family, release_version), "artifactPath": str(path),
                        "artifactSha256": artifacts.file_hash(path, artifacts.MAX_ARTIFACT_BYTES)})
    return records


def source_identity():
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    require(re.fullmatch(r"[0-9a-f]{40}", sha) is not None, "Exact source commit required")
    require(not subprocess.check_output(["git", "status", "--porcelain", "--untracked-files=no"], cwd=ROOT, text=True).strip(),
            "Release build/publisher requires unchanged tracked source")
    require(os.environ.get("GITHUB_SHA", sha) == sha, "Workflow source differs from checked-out source")
    return sha


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def forge_runtime_hashes(path):
    """Record separately compiled SDK/Rhino bytes; verify Java17-compatible nesting."""
    result = {}
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if name.startswith("META-INF/jarjar/") and name.endswith(".jar"):
                with zipfile.ZipFile(BytesIO(archive.read(name))) as nested:
                    for entry in nested.namelist():
                        if entry.endswith(".class"):
                            blob = nested.read(entry)
                            require(len(blob) >= 8 and blob[:4] == b"\xca\xfe\xba\xbe" and 45 <= int.from_bytes(blob[6:8], "big") <= 61,
                                    "Nested Forge dependency exceeds Java17: " + name + "!" + entry)
        jars = json.loads(archive.read("META-INF/jarjar/metadata.json"))["jars"]
        for artifact, prefix, major in (("extension-api", "dev/openallay/api/", 52),
                                        ("runtime-rhino", "dev/latvian/mods/rhino/", 52)):
            rows = [row for row in jars if row["identifier"] == {"group": "dev.openallay", "artifact": artifact}]
            require(len(rows) == 1, "Shared Forge runtime has competing owners: " + artifact)
            row = rows[0]
            expected_version = (builder.SDK_VERSION if artifact == "extension-api"
                                else native.read_properties(ROOT / "gradle.properties")["rhino_version"])
            require(row["version"]["artifactVersion"] == expected_version, "Shared Forge runtime version differs")
            require(row["version"]["range"] == "[" + row["version"]["artifactVersion"] + "]",
                    "Shared Forge runtime needs its atomic singleton range")
            content = archive.read(row["path"])
            result[artifact] = hashlib.sha256(content).hexdigest()
            with zipfile.ZipFile(BytesIO(content)) as nested:
                names = nested.namelist()
                require(len(names) == len(set(names)) and nested.testzip() is None, "Corrupt shared Forge runtime")
                classes = [name for name in names if name.endswith(".class") and name.startswith(prefix)]
                require(classes, "Missing shared Forge runtime classes")
                for name in classes:
                    blob = nested.read(name)
                    require(len(blob) >= 8 and blob[:4] == b"\xca\xfe\xba\xbe" and int.from_bytes(blob[6:8], "big") == major,
                            "Shared Forge runtime ABI differs: " + name)
    return result


BUILD_RECEIPT_FIELDS = {"kind", "outcome", "sourceSha", "version", "sourceRunId", "sourceRunAttempt",
                        "family", "artifactSha256", "engineManifestSha256", "commands", "sqlite", "sharedRuntimes", "packagingProofSha256"}


RELEASE_BUILD_SELECTION = "distribution/release-build-selection.json"
REUSE_GROUP_FIELDS = {"target", "sourceSha", "runId", "runAttempt", "jobId", "jobName", "artifactId", "artifactName",
                      "archiveSha256", "engineManifestSha256", "familyArtifacts"}
# These exact orchestration paths do not contribute production source or pins.
REUSE_ORCHESTRATION_PATHS = {
    RELEASE_BUILD_SELECTION, ".github/workflows/minecraft-native.yml", ".github/workflows/release.yml",
    "scripts/build-minecraft-artifacts.py", "scripts/verify-release-package-source.py",
    "scripts/fetch-release-build-groups.py", "scripts/collect-release-group-metadata.py",
    "docs/releases/0.4.3.md", "README.md", "README.zh-CN.md", "docs/native-binary-artifacts.md",
    "docs/releases/0.4.4.md", "docs/forge-runtime-installation.md", "docs/minecraft-support-policy.md",
    "scripts/materialize-stock8-release.py", "scripts/package-legacy-forge-release.py",
    "scripts/test_release_stage_only.py", "scripts/test_stock8_release_admission.py",
    "distribution/stock8-nonselected-native-deltas.json", "scripts/stock8_selection_custody.py",
    "scripts/test_stock8_nonselected_custody.py", "scripts/test_release_native_api_family_sources.py",
    "scripts/capture-commonmark-provider-nested.py", "scripts/test_commonmark_provider_custody.py",
    "scripts/test_release_publication.py",
    "scripts/verify-native-target-package.py", "scripts/test_early_neoforge_recipe.py",
    "scripts/test_forge16_engine_legal_custody.py",
    "scripts/test_sqlite_cross_family_service.py", "scripts/test_sqlite_fabric_wrapper_identity.py",
    "scripts/run-builder-package-only-group.py",
    "distribution/builder-package-originals.json", "scripts/canonical_builder_provider.py",
    "scripts/package_canonical_builder.py", "scripts/test_canonical_builder_package_only.py",
    COMMENT_POLICY_PATH, "scripts/release_comment_custody.py", "scripts/test_release_comment_custody.py",
}
REUSE_NATIVE_PATHS = {
    "common/src/targets/1.20.1/java/dev/openallay/integration/jei/MinecraftJeiRecipeApi.java",
    "adapters/minecraft/src/targets/1.21.10/java/dev/openallay/adapter/minecraft/v26_2/world/NativeWorldRegistries.java",
    "common/src/targets/1.21.11/java/dev/openallay/platform/minecraft/MinecraftResourceAccess.java",
    "common/src/targets/1.21.10/java/dev/openallay/platform/minecraft/MinecraftResourceAccess.java",
    "common/src/targets/26.3/java/dev/openallay/platform/minecraft/MinecraftResourceAccess.java",
    "common/src/targets/1.19.2/java/dev/openallay/integration/jei/MinecraftJeiRecipeApi.java",
}
NATIVE_LEAF = re.compile(r"^(common|adapters/minecraft|fabric|neoforge|forge)/src/targets/([^/]+)/(java|resources)/(.+)$")


def git_output(*arguments, binary=False):
    return subprocess.check_output(["git", *arguments], cwd=ROOT, text=not binary)


def selected_native_roots(target):
    """Read the unchanged selector's literal external-target and parent maps."""
    source = (ROOT / "gradle/minecraft-targets.gradle").read_text()
    maps = []
    for name in ("nativeFamilies", "nativeFamilyParents"):
        found = re.findall(r"def " + name + r" = \[([^\n]+)\]", source)
        require(len(found) == 1, "Cannot read exact native source selector map: " + name)
        pairs = re.findall(r"'([^']+)': '([^']+)'", found[0])
        require(pairs and len(dict(pairs)) == len(pairs)
                and re.sub(r"'[^']+': '[^']+'", "", found[0]).replace(",", "").strip() == "",
                "Unsupported native source selector map syntax")
        maps.append(dict(pairs))
    families, parents = maps
    require(target in families, "Unknown selected native target")
    chain = [families[target]]
    while chain[0] in parents:
        parent = parents[chain[0]]
        require(parent not in chain, "Cyclic native source family parents")
        chain.insert(0, parent)
    if target not in chain:
        chain.append(target)
    return chain


def selected_native_blob(tree, revision, module, scope, relative, target, loader):
    """Match checked-in source-set override order, not an allowed target list."""
    chain = selected_native_roots(target)
    actual_modules = {"common", "adapters/minecraft", loader}
    if loader == "forge":
        actual_modules.add("neoforge")
    if module not in actual_modules:
        return None
    roots = (["neoforge", "forge"] if module in ("neoforge", "forge") and loader == "forge" else [module])
    if module in ("fabric", "neoforge", "forge") and module not in roots:
        return None
    candidates = []
    for index, root in enumerate(roots):
        # Forge has no own main source root in the actual loader convention.
        if index == 0:
            candidates.append(root + "/src/main/" + scope + "/" + relative)
        candidates.extend(root + "/src/targets/" + family + "/" + scope + "/" + relative for family in chain)
    selected = next((path for path in reversed(candidates) if path in tree), None)
    return None if selected is None else git_output("show", revision + ":" + selected, binary=True)


def verify_1201_refmap_build_scope(old_source, current_source, families):
    """Admit only the reviewed inactive 1.20.1 Fabric producer block repair."""
    require(all("1.20.1" not in family["supportedTargets"] for family in families),
            "The 1.20.1 refmap producer repair requires rebuilding its target, not reuse")
    old_block = b"    if (minecraftTarget == '1.20.1') {\n        mixin { defaultRefmapName.set('openallay.refmap.json') }\n    }"
    new_block = b"    if (minecraftTarget == '1.20.1') {\n        mixin {\n            useLegacyMixinAp.set(true)\n            defaultRefmapName.set('openallay.refmap.json')\n        }\n    }"
    old = git_output("show", old_source + ":fabric/build.gradle", binary=True)
    new = git_output("show", current_source + ":fabric/build.gradle", binary=True)
    require(old.count(old_block) == 1 and old.count(new_block) == 0
            and new.count(new_block) == 1 and new.count(old_block) == 0,
            "Expected the single exact reviewed Fabric 1.20.1 refmap block replacement")
    require(new.replace(new_block, old_block, 1) == old,
            "Fabric build source has changes beyond the inactive 1.20.1 refmap producer block")


def verify_reused_source(old_source, current_source, families):
    require(re.fullmatch(r"[0-9a-f]{40}", old_source) is not None and old_source != current_source,
            "Expected exact original source commit")
    subprocess.run(["git", "merge-base", "--is-ancestor", old_source, current_source], cwd=ROOT, check=True)
    changed = git_output("diff", "--no-renames", "--name-only", "-z", old_source, current_source).split("\0")
    native_units = set()
    finite_native = group_change_paths(ROOT)
    early_paths = early_producer_paths(ROOT)
    old_tree = set(git_output("ls-tree", "-r", "--name-only", "-z", old_source).split("\0"))
    current_tree = set(git_output("ls-tree", "-r", "--name-only", "-z", current_source).split("\0"))
    for path in filter(None, changed):
        if path in early_paths:
            original = git_output("show", old_source + ":" + path, binary=True) if path in old_tree else None
            current = git_output("show", current_source + ":" + path, binary=True) if path in current_tree else None
            verify_inactive_early_producer(ROOT,path,original,current,families)
            continue
        if path in COMMENT_PATHS:
            verify_comment_pair(ROOT, path, git_output("show", old_source + ":" + path, binary=True),
                                git_output("show", current_source + ":" + path, binary=True))
            continue
        if path == "gradle/distribution.gradle":
            old=git_output("show",old_source+":"+path,binary=True);new=git_output("show",current_source+":"+path,binary=True)
            require(hashlib.sha256(old).hexdigest()=="16a36d6850fba208eac6624c778cfde885887ab8ab268c95e4d40047a8341325" and hashlib.sha256(new).hexdigest()=="244be7b1c15fcd48e4b075a827b8247c3cb10b758a1e9650190a9cdcdc95dda0",
                    "Expected only the exact canonical-Builder release-producer branch replacement")
            continue
        if path == "fabric/build.gradle":
            verify_1201_refmap_build_scope(old_source, current_source, families)
            continue
        if path in REUSE_ORCHESTRATION_PATHS:
            continue
        if path in finite_native:
            original = git_output("show", old_source + ":" + path, binary=True) if path in old_tree else None
            current = git_output("show", current_source + ":" + path, binary=True) if path in current_tree else None
            verify_group_pair(ROOT, path, original, current)
        else:
            require(path in REUSE_NATIVE_PATHS, "Reused release source changes an unapproved native leaf: " + path)
        match = NATIVE_LEAF.fullmatch(path)
        require(match is not None, "Reused release source has an out-of-scope production change: " + path)
        module, _, scope, relative = match.groups()
        require(".." not in relative.split("/") and (scope != "java" or relative.endswith(".java")),
                "Expected a native target compilation/resource leaf")
        native_units.add((module, scope, relative))
    # Selector/conventions/profiles are outside the allowlist, so their bytes
    # cannot change. Check old/new effective source bytes, including relocations.
    old_tree = set(git_output("ls-tree", "-r", "--name-only", "-z", old_source).split("\0"))
    current_tree = set(git_output("ls-tree", "-r", "--name-only", "-z", current_source).split("\0"))
    for family in families:
        for target in family["supportedTargets"]:
            for module, scope, relative in native_units:
                old = selected_native_blob(old_tree, old_source, module, scope, relative, target, family["loader"])
                new = selected_native_blob(current_tree, current_source, module, scope, relative, target, family["loader"])
                require(old == new, "Reused native source changed for " + family["id"] + " on " + target
                        + ": " + module + "/" + scope + "/" + relative)


def read_reuse_selection():
    path = ROOT / RELEASE_BUILD_SELECTION
    if not path.exists():
        return {}
    data = artifacts.read_json(path)
    artifacts.shape(data, {"version", "groups"}, "Release build selection")
    require(data["version"] == version() and type(data["groups"]) is list,
            "Release reuse selection must match the current product release")
    selected = {}
    accepted = catalog()["acceptedFamilies"]
    for group in data["groups"]:
        artifacts.shape(group, REUSE_GROUP_FIELDS, "Approved original build group")
        target = group["target"]
        families = [family for family in accepted if family["buildTarget"] == target]
        require(families and target not in selected, "Unknown or duplicate approved build group")
        require(type(group["sourceSha"]) is str and re.fullmatch(r"[0-9a-f]{40}", group["sourceSha"]),
                "Approved group needs its exact original source commit")
        for key in ("runId", "runAttempt", "jobId", "artifactId"):
            require(type(group[key]) is int and group[key] > 0, "Approved group needs a positive external " + key)
        for key in ("jobName", "artifactName"):
            artifacts.text(group[key], key)
        for key in ("archiveSha256", "engineManifestSha256"):
            artifacts.hash_text(group[key])
        rows = group["familyArtifacts"]
        require(type(rows) is list and len(rows) == len(families), "Approved group must bind every original family")
        observed = set()
        for row in rows:
            artifacts.shape(row, {"id", "artifactSha256", "receiptSha256"}, "Approved original family bytes")
            require(row["id"] in {family["id"] for family in families} and row["id"] not in observed,
                    "Unknown or duplicate approved family bytes")
            observed.add(row["id"])
            artifacts.hash_text(row["artifactSha256"])
            artifacts.hash_text(row["receiptSha256"])
        selected[target] = group
    return selected


def validate_stage_only_selection():
    """No compilation: every current target must have exact checked-in original approval."""
    dispatch = os.environ.get("RELEASE_DISPATCH_INPUTS")
    if dispatch is not None:
        inputs = json.loads(dispatch)
        require(inputs.get("stage_only") is True and all(value in (False, "none", "")
                for key, value in inputs.items() if key != "stage_only"), "Stage-only cannot compete with compiler/game/metadata modes")
    data = catalog()
    selected = read_reuse_selection()
    expected = {target for target, _ in groups(data)}
    require(selected and set(selected) == expected, "Stage-only requires every accepted build target exactly once")
    current = source_identity()
    for target, group in selected.items():
        families = [family for family in data["acceptedFamilies"] if family["buildTarget"] == target]
        require({row["id"] for row in group["familyArtifacts"]} == {family["id"] for family in families},
                "Stage-only approval must cover every family in each target")
        verify_reused_source(group["sourceSha"], current, families)
    return {"stageOnly": True, "targetCount": len(selected), "familyCount": len(data["acceptedFamilies"]),
            "stageSourceSha": current, "originalPackageSources": sorted({group["sourceSha"] for group in selected.values()})}


def verify_receipt_source(family, receipt, receipt_path, current_source, engine_sha, selection, verified_groups):
    if receipt["sourceSha"] == current_source:
        return
    group = selection.get(family["buildTarget"])
    require(group is not None, "Older-source receipt lacks exact checked-in release reuse approval")
    require(receipt["sourceSha"] == group["sourceSha"] and receipt["sourceRunId"] == str(group["runId"])
            and receipt["sourceRunAttempt"] == str(group["runAttempt"])
            and engine_sha == group["engineManifestSha256"], "Original group source/run/engine differs from approval")
    row = next(row for row in group["familyArtifacts"] if row["id"] == family["id"])
    require(receipt["artifactSha256"] == row["artifactSha256"]
            and artifacts.file_hash(receipt_path, artifacts.MAX_JSON_BYTES) == row["receiptSha256"],
            "Original artifact/receipt bytes differ from exact reuse approval")
    if group["target"] not in verified_groups:
        group_families = [item for item in catalog()["acceptedFamilies"] if item["buildTarget"] == group["target"]]
        verify_reused_source(group["sourceSha"], current_source, group_families)
        verified_groups.add(group["target"])


def verify_build_receipts(families, directory, receipt_directory, original_directory=None):
    """Verify new compiled release bytes. This receipt class carries no game result."""
    require(receipt_directory.is_dir(), "Missing compile/package build receipts")
    require(sorted(path.name for path in receipt_directory.glob("*.json")) ==
            sorted(["engine-manifest.json"] + [family["id"] + ".json" for family in families]),
            "Build receipts must match exactly the selected accepted families")
    engine_path = receipt_directory / "engine-manifest.json"
    engine_sha = artifacts.file_hash(engine_path, artifacts.MAX_JSON_BYTES)
    engine = artifacts.read_json(engine_path)
    source = source_identity()
    reuse_selection = read_reuse_selection()
    verified_groups = set()
    release_version = version()
    receipts = []
    approved_package_sources = {}
    for family in families:
        receipt = artifacts.read_json(receipt_directory / (family["id"] + ".json"))
        if receipt.get("kind") == "package-only":
            packer=module("derived_builder_package", "package_canonical_builder.py")
            resolver=original_directory if original_directory is not None else os.environ.get("OPENALLAY_ORIGINAL_PACKAGE_DIRECTORY")
            require(resolver is not None, "Package-only verification requires an explicit authenticated original-input directory")
            group=packer.originals(ROOT)[family["buildTarget"]]
            if callable(resolver):
                original_group_directory=Path(resolver(family))
            elif isinstance(resolver,dict):
                require(family["buildTarget"] in resolver, "Original-input resolver lacks this exact target")
                original_group_directory=Path(resolver[family["buildTarget"]])
            else:
                original_group_directory=Path(resolver)/group["artifactName"]
            # Authenticate checked-in frozen compile evidence before any derived-output check.
            _, original_raw, _, _=packer.authenticate_original(ROOT,family,original_group_directory)
            original_receipt=json.loads(original_raw)
            verify_reused_source(original_receipt["sourceSha"],source,[family])
            if receipt["sourceSha"]!=source:
                approved=read_reuse_selection().get(family["buildTarget"])
                require(approved is not None and approved["sourceSha"]==receipt["sourceSha"] and
                        receipt["sourceRunId"]==str(approved["runId"]) and receipt["sourceRunAttempt"]==str(approved["runAttempt"]) and
                        receipt["engineManifestSha256"]==approved["engineManifestSha256"], "Derived package source/run/engine lacks exact current approval")
                row=next(row for row in approved["familyArtifacts"] if row["id"]==family["id"])
                require(receipt["artifactSha256"]==row["artifactSha256"] and artifacts.file_hash(receipt_directory/(family["id"]+".json"),artifacts.MAX_JSON_BYTES)==row["receiptSha256"], "Derived original receipt/artifact identity differs")
                verify_reused_source(receipt["sourceSha"],source,[family])
            old=packer.verify_receipt(ROOT,family,directory/artifacts.describe(family,release_version)["filename"],receipt,original_group_directory,receipt["sourceSha"])
            require(receipt["engineManifestSha256"]==engine_sha, "Derived package changed canonical engine manifest")
            approved_package_sources[family["id"]]=receipt["sourceSha"]
            receipts.append(receipt)
            continue
        artifacts.shape(receipt, BUILD_RECEIPT_FIELDS, "Compile/package release receipt")
        if family["packagingRecipe"] in LEGACY_RECIPES:
            sidecar = directory / (artifacts.describe(family, release_version)["filename"] + ".packaging.json")
            require(artifacts.file_hash(sidecar, artifacts.MAX_EVIDENCE_BYTES) == artifacts.hash_text(receipt["packagingProofSha256"]),
                    "Legacy complete package ownership proof changed after build")
        else:
            require(receipt["packagingProofSha256"] is None, "Nested recipe cannot carry a legacy bypass proof")
        require(receipt["kind"] == "compile-package" and receipt["outcome"] == "passed",
                "A compile/package release receipt is required; runtime receipts are a separate class")
        require(receipt["version"] == release_version
                and receipt["family"] == family and receipt["engineManifestSha256"] == engine_sha,
                "Release receipt source/version/family/engine identity differs")
        verify_receipt_source(family, receipt, receipt_directory / (family["id"] + ".json"), source, engine_sha, reuse_selection, verified_groups)
        approved_package_sources[family["id"]] = receipt["sourceSha"]
        require(type(receipt["sourceRunId"]) is str and re.fullmatch(r"[1-9][0-9]*", receipt["sourceRunId"])
                and type(receipt["sourceRunAttempt"]) is str and re.fullmatch(r"[1-9][0-9]*", receipt["sourceRunAttempt"]),
                "New release receipts must originate in an actual remote workflow run")
        commands = receipt["commands"]
        require(type(commands) is list and commands, "Missing checked-in native wrapper build provenance")
        for row in commands:
            artifacts.shape(row, {"command", "runtime"}, "Native build command")
            require(type(row["command"]) is list and row["command"] and all(type(value) is str for value in row["command"]),
                    "Invalid native build command")
            require(not any(value.split(":")[-1] in ("test", "build", "check", "runClient", "runServer") for value in row["command"]),
                    "Release receipt must describe compilation/packaging, not repeated game/test gates")
        if family["packagingRecipe"] in LEGACY_RECIPES:
            expected_commands = command_receipt(family["buildTarget"], [family])
        else:
            for row in commands:
                require(row["runtime"] in ("root", "java21") and row["command"][0] in ("gradlew", "native-builds/early-neoforge/gradlew"),
                        "Nested recipe must use its checked-in native wrapper")
            root_commands = [row["command"] for row in commands if row["runtime"] == "root"]
            require(len(root_commands) == 1 and "-PminecraftTarget=" + family["buildTarget"] in root_commands[0]
                    and "-PtestBundledExtensions=false" in root_commands[0]
                    and any(value.startswith("-PminecraftArtifact=") and family["id"] in value.split("=", 1)[1].split(",")
                            for value in root_commands[0]), "Receipt command does not select this exact accepted family")
            selection = next(value.split("=", 1)[1] for value in root_commands[0] if value.startswith("-PminecraftArtifact="))
            expected_commands = command_receipt(family["buildTarget"], select(catalog(), family["buildTarget"], selection))
        require(commands == expected_commands, "Receipt commands differ from the checked-in native compiler plan")
        receipts.append(receipt)
    records = verify(families, directory, engine_manifest=engine, approved_package_sources=approved_package_sources)
    for family, record, receipt in zip(families, records, receipts):
        path = Path(record["artifactPath"])
        require(record["artifactSha256"] == artifacts.hash_text(receipt["artifactSha256"]), "New release artifact changed after build")
        sqlite, runtimes = package_proofs(path, family, approved_package_sources[family["id"]])
        comparison_receipt=receipt
        if receipt["kind"]=="package-only":
            import base64
            comparison_receipt=json.loads(base64.b64decode(receipt["originalReceiptBase64"],validate=True))
        require(sqlite == comparison_receipt["sqlite"], "SQLite bytes changed after package checks")
        require(runtimes == comparison_receipt["sharedRuntimes"],
                "Separately compiled shared runtime bytes changed after build")
    return records


def publication_records(families, directory, receipt_directory=None, build_receipt_directory=None):
    require(receipt_directory is None or build_receipt_directory is None, "Runtime and build receipt modes cannot compete")
    if build_receipt_directory is not None:
        require(not (directory / "accepted-originals.json").exists(), "New build release cannot promote old accepted bytes")
        records = verify_build_receipts(families, directory, build_receipt_directory)
        for family, record in zip(families, records):
            receipt_path = build_receipt_directory / (family["id"] + ".json")
            receipt = artifacts.read_json(receipt_path)
            record.update({"artifactSourceSha": receipt["sourceSha"],
                           "stageSourceSha": source_identity(),
                           "buildRunId": receipt["sourceRunId"],
                           "buildRunAttempt": receipt["sourceRunAttempt"],
                           "verification": receipt["kind"],
                           "receiptSha256": artifacts.file_hash(receipt_path, artifacts.MAX_JSON_BYTES)})
            if receipt["kind"] == "package-only":
                import base64
                compiled=json.loads(base64.b64decode(receipt["originalReceiptBase64"],validate=True))
                record.update({"verification":"package-only", "originalCompileSourceSha":compiled["sourceSha"],
                               "originalCompileRunId":compiled["sourceRunId"], "originalCompileReceiptSha256":receipt["originalReceiptSha256"]})
            if family["packagingRecipe"] == "forge-stock8":
                stock8 = module("stock8_publication", "materialize-stock8-release.py").pin(ROOT)
                record.update({"artifactSourceSha": stock8["productSource"], "stageSourceSha": source_identity(), "originalPackageSourceSha": receipt["sourceSha"],
                               "originalProductProvider": stock8["provider"]})
        return records
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
            artifacts.verify_receipt(family, receipt_directory / (family["id"] + ".json"),
                                     Path(record["artifactPath"]), record["artifactSha256"])
    return records


def command_receipt(target, families):
    result = []
    for command, runtime in compiler.commands(ROOT, target, loaders=tuple(family["loader"] for family in families),
                                               artifact_ids=",".join(family["id"] for family in families)):
        result.append({"command": ["." if value == str(ROOT) else value.replace(str(ROOT) + "/", "") for value in command], "runtime": runtime})
    return result


def build_and_stage(directory, target=None, family_ids=None):
    require(not directory.exists(), "Refusing to overwrite an existing release directory")
    require((target is None) == (family_ids is None), "Select target and families together, or build all accepted groups")
    source = source_identity()
    run, attempt = os.environ.get("GITHUB_RUN_ID"), os.environ.get("GITHUB_RUN_ATTEMPT")
    require(type(run) is str and re.fullmatch(r"[1-9][0-9]*", run)
            and type(attempt) is str and re.fullmatch(r"[1-9][0-9]*", attempt), "New release builds require remote workflow provenance")
    data = catalog()
    selected_groups = groups(data) if target is None else [(target, select(data, target, family_ids))]
    selected = [family for _, families in selected_groups for family in families]
    directory.mkdir(parents=True)
    receipt_directory = directory / "build-receipts"
    expected_engine = None
    for target, families in selected_groups:
        selection = ",".join(family["id"] for family in families)
        # Compile actual accepted native families. The Builder dependency delegates
        # assemble+verifyUniversalPackage because testBundledExtensions stays false.
        compiler.compile_target(ROOT, target, loaders=tuple(family["loader"] for family in families), artifact_ids=selection,
                                execute=lambda command, **options: subprocess.run(command, stdout=sys.stderr, **options))
        engine = {name: hashlib.sha256(content).hexdigest() for name, content in native.engine_files(ROOT).items()}
        require(expected_engine is None or engine == expected_engine, "Compiled engine differs across accepted native groups")
        expected_engine = engine
        write_json(receipt_directory / "engine-manifest.json", engine)
        engine_sha = artifacts.file_hash(receipt_directory / "engine-manifest.json", artifacts.MAX_JSON_BYTES)
        for family, record in zip(families, verify(families)):
            path = Path(record["artifactPath"])
            destination = directory / record["filename"]
            shutil.copyfile(path, destination)
            require(artifacts.file_hash(destination, artifacts.MAX_ARTIFACT_BYTES) == record["artifactSha256"], "Staged release bytes changed")
            sqlite, runtimes = package_proofs(path, family)
            packaging_sha = None
            if family["packagingRecipe"] in LEGACY_RECIPES:
                sidecar = Path(str(path) + ".packaging.json")
                packaging_sha = artifacts.file_hash(sidecar, artifacts.MAX_EVIDENCE_BYTES)
                shutil.copyfile(sidecar, Path(str(destination) + ".packaging.json"))
            write_json(receipt_directory / (family["id"] + ".json"), {
                "kind": "compile-package", "outcome": "passed", "sourceSha": source, "version": version(),
                "sourceRunId": run, "sourceRunAttempt": attempt, "family": family,
                "artifactSha256": record["artifactSha256"], "engineManifestSha256": engine_sha,
                "commands": command_receipt(target, families), "sqlite": sqlite,
                "sharedRuntimes": runtimes, "packagingProofSha256": packaging_sha})
    records = verify_build_receipts(selected, directory, receipt_directory)
    checksums(directory, records)
    return records


def checksums(directory, records):
    (directory / "SHA256SUMS").write_text("".join(record["artifactSha256"] + "  " + record["filename"] + "\n"
                                               for record in records), encoding="utf-8")


def merge_staged(groups_directory, directory, original_directory=None):
    """Assemble independently compiled groups without rebuilding or launching Java."""
    require(not directory.exists(), "Preserve any previous release stage")
    directory.mkdir(parents=True)
    receipts = directory / "build-receipts"
    receipts.mkdir()
    data = catalog()
    observed = set()
    engine_bytes = None
    for group in sorted(groups_directory.iterdir()):
        require(group.is_dir() and not group.is_symlink(), "Expected retained group stage directories")
        ids = {path.stem for path in (group / "build-receipts").glob("*.json") if path.name != "engine-manifest.json"}
        families = [family for family in data["acceptedFamilies"] if family["id"] in ids]
        require(ids and len(families) == len(ids) and not observed.intersection(ids), "Unknown or repeated accepted group families")
        require(len({family["buildTarget"] for family in families}) == 1, "A group stage must have one actual build target")
        verify_build_receipts(families, group, group / "build-receipts", original_directory=original_directory)
        content = (group / "build-receipts/engine-manifest.json").read_bytes()
        require(engine_bytes is None or content == engine_bytes, "Independent groups changed shared engine bytes")
        engine_bytes = content
        for family in families:
            filename = artifacts.describe(family, version())["filename"]
            shutil.copyfile(group / filename, directory / filename)
            if family["packagingRecipe"] in LEGACY_RECIPES:
                shutil.copyfile(group / (filename + ".packaging.json"), directory / (filename + ".packaging.json"))
            shutil.copyfile(group / "build-receipts" / (family["id"] + ".json"), receipts / (family["id"] + ".json"))
        observed.update(ids)
    require(observed == {family["id"] for family in data["acceptedFamilies"]}, "Merged release must contain every accepted family exactly once")
    (receipts / "engine-manifest.json").write_bytes(engine_bytes)
    records = verify_build_receipts(data["acceptedFamilies"], directory, receipts, original_directory=original_directory)
    checksums(directory, records)
    return records


def builder_package_groups():
    dispatch=os.environ.get("RELEASE_DISPATCH_INPUTS")
    if dispatch is not None:
        inputs=json.loads(dispatch)
        require(inputs.get("package_only") is True and all(value in (False, "none", "")
                for key,value in inputs.items() if key!="package_only"), "Package-only cannot compete with other dispatch modes")
    data=catalog()
    result=[{"target":target,"families":",".join(family["id"] for family in families)} for target,families in groups(data)
            if all(family["packagingRecipe"]=="nested-mod" for family in families)]
    require(len(result)==18 and sum(len(row["families"].split(",")) for row in result)==33, "Exact18 nested groups/33 families required")
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    selection = commands.add_parser("select", help="Pre-build accepted identity; not runtime acceptance")
    selection.add_argument("--target", required=True)
    selection.add_argument("--families", required=True)
    selection.add_argument("--version", required=True)
    build = commands.add_parser("build-and-stage")
    build.add_argument("directory", type=Path)
    build.add_argument("--target")
    build.add_argument("--families")
    derived_grouping = commands.add_parser("builder-package-groups", help="Package-only exact original18 nested groups")
    derived_grouping.add_argument("--github-output", type=Path)
    grouping = commands.add_parser("groups", help="Exact compile-only build groups for admitted families")
    grouping.add_argument("--targets", help="Exact comma-separated catalog build targets; empty means all")
    grouping.add_argument("--github-output", type=Path, help="Append the exact compact group list to the workflow output file")
    commands.add_parser("stage-only-selection", help="Require complete exact approved original groups before fetching")
    merging = commands.add_parser("merge-staged", help="Merge retained compile/package stages without rebuilding")
    merging.add_argument("groups_directory", type=Path)
    merging.add_argument("directory", type=Path)
    verification = commands.add_parser("verify")
    verification.add_argument("directory", type=Path, nargs="?")
    verification.add_argument("--target", default="26.2")
    verification.add_argument("--families")
    publishing = commands.add_parser("publication-records")
    publishing.add_argument("directory", type=Path)
    publishing.add_argument("--receipt-directory", type=Path)
    publishing.add_argument("--build-receipt-directory", type=Path)
    args = parser.parse_args(argv)
    try:
        data = catalog()
        if args.command == "select":
            result = {family["loader"]: artifacts.describe(family, args.version)
                      for family in select(data, args.target, args.families)}
        elif args.command == "build-and-stage":
            result = build_and_stage(args.directory.resolve(), args.target, args.families)
        elif args.command == "builder-package-groups":
            result=builder_package_groups()
            if args.github_output is not None:
                with args.github_output.open("a",encoding="utf-8") as stream: stream.write("groups="+json.dumps(result,separators=(",", ":"))+"\n")
        elif args.command == "groups":
            reused_targets = set(read_reuse_selection())
            result = [{"target": target, "families": ",".join(family["id"] for family in families)}
                      for target, families in filter_groups(data, args.targets) if target not in reused_targets]
            if args.github_output is not None:
                with args.github_output.open("a", encoding="utf-8") as output:
                    output.write("groups=" + json.dumps(result, separators=(",", ":")) + "\n")
        elif args.command == "stage-only-selection":
            result = validate_stage_only_selection()
        elif args.command == "merge-staged":
            result = merge_staged(args.groups_directory.resolve(), args.directory.resolve())
        elif args.command == "publication-records":
            result = publication_records(data["acceptedFamilies"], args.directory.resolve(), args.receipt_directory, args.build_receipt_directory)
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
