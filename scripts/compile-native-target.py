#!/usr/bin/env python3
"""Select only the two old NeoForge native recipes; every other root recipe stays unchanged."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
EARLY = frozenset(("1.20.2", "1.20.3"))


def target_exists(root, target):
    if not re.fullmatch(r"(?:1\.[0-9]+(?:\.[0-9]+)?|26\.[0-9]+(?:\.[0-9]+)?)", target):
        raise ValueError("Invalid Minecraft target")
    if not (root / "gradle/minecraft-targets" / (target + ".properties")).is_file():
        raise ValueError("Unknown Minecraft target: " + target)


def java21_environment(environment):
    home = environment.get("JAVA_HOME_21_X64")
    if not home:
        raise ValueError("Isolated early NeoForge needs the installed JAVA_HOME_21_X64 from setup-java")
    directory = Path(home)
    release = directory / "release"
    if not release.is_file() or not (directory / "bin/java").is_file():
        raise ValueError("Missing installed Java21 release file or launcher")
    match = re.search(r'^JAVA_VERSION="([^"\n]+)"$', release.read_text(), re.M)
    if not match or match.group(1).split(".", 1)[0] != "21":
        raise ValueError("Isolated early NeoForge build runtime must be Java21")
    result = dict(environment)
    result["JAVA_HOME"] = str(directory)
    result["PATH"] = str(directory / "bin") + os.pathsep + result.get("PATH", "")
    return result


def commands(root, target, *, loaders=("fabric", "neoforge"), artifact_ids=None, candidate_ids=None):
    target_exists(root, target)
    if not loaders or len(set(loaders)) != len(loaders) or any(loader not in ("fabric", "neoforge") for loader in loaders):
        raise ValueError("Expected distinct Fabric/NeoForge loaders")
    if artifact_ids is not None and candidate_ids is not None:
        raise ValueError("Accepted and candidate family selection are mutually exclusive")
    selection = artifact_ids if artifact_ids is not None else candidate_ids
    if selection is not None:
        ids = selection.split(",")
        if not ids or len(set(ids)) != len(ids) or any(not re.fullmatch(r"[a-z0-9][a-z0-9.-]*", value) for value in ids):
            raise ValueError("Expected distinct safe native family IDs")
    shared = [str(root / "gradlew"), "--max-workers=2", "-PminecraftTarget=" + target,
              "-PtestBundledExtensions=false"]
    if artifact_ids is not None:
        shared.append("-PminecraftArtifact=" + artifact_ids)
    if candidate_ids is not None:
        shared.append("-PminecraftCandidateArtifact=" + candidate_ids)
    isolated_neo = target in EARLY and "neoforge" in loaders
    shared += [":" + loader + (":exportEarlyNeoForgeInputs" if isolated_neo and loader == "neoforge" else ":assemble")
               for loader in loaders]
    if not isolated_neo:
        return [(shared, "root")]
    receipt = root / "build/early-neoforge" / target / "inputs.json"
    isolated = root / "native-builds/early-neoforge"
    return [(shared, "root"),
            ([str(isolated / "gradlew"), "--max-workers=2", "-p", str(isolated),
              "-PopenallayNativeInputs=" + str(receipt), "assemble"], "java21")]


def compile_target(root, target, environment=None, execute=subprocess.run, *,
                   loaders=("fabric", "neoforge"), artifact_ids=None, candidate_ids=None):
    selected = commands(root, target, loaders=loaders, artifact_ids=artifact_ids, candidate_ids=candidate_ids)
    environment = dict(os.environ if environment is None else environment)
    # Validate the installed older build JVM before allocating any native build output.
    isolated_env = java21_environment(environment) if any(runtime == "java21" for _, runtime in selected) else None
    for command, runtime in selected:
        execute(command, cwd=root, env=isolated_env if runtime == "java21" else environment, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", required=True)
    parser.add_argument("--loaders", default="fabric,neoforge", help="Comma-separated selected native loaders")
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument("--families", help="Explicit reviewed accepted family IDs")
    selection.add_argument("--candidate-families", help="Validation-only candidate IDs; does not admit release support")
    parser.add_argument("--plan", action="store_true", help="Print exact commands without starting Gradle")
    args = parser.parse_args()
    try:
        options = dict(loaders=tuple(args.loaders.split(",")), artifact_ids=args.families,
                       candidate_ids=args.candidate_families)
        if args.plan:
            print(json.dumps([{"command": command, "runtime": runtime}
                              for command, runtime in commands(ROOT, args.target, **options)], indent=2))
        else:
            compile_target(ROOT, args.target, **options)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, "Native build selection failed: " + str(error) + "\n")


if __name__ == "__main__":
    main()
