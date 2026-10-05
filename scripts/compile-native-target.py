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


def commands(root, target):
    target_exists(root, target)
    shared = [str(root / "gradlew"), "--max-workers=2", "-PminecraftTarget=" + target,
              "-PtestBundledExtensions=false", ":fabric:assemble"]
    if target not in EARLY:
        return [(shared + [":neoforge:assemble"], "root")]
    receipt = root / "build/early-neoforge" / target / "inputs.json"
    isolated = root / "native-builds/early-neoforge"
    return [(shared + [":neoforge:exportEarlyNeoForgeInputs"], "root"),
            ([str(isolated / "gradlew"), "--max-workers=2", "-p", str(isolated),
              "-PopenallayNativeInputs=" + str(receipt), "assemble"], "java21")]


def compile_target(root, target, environment=None, execute=subprocess.run):
    selected = commands(root, target)
    environment = dict(os.environ if environment is None else environment)
    # Validate the installed older build JVM before allocating any native build output.
    isolated_env = java21_environment(environment) if target in EARLY else None
    for command, runtime in selected:
        execute(command, cwd=root, env=isolated_env if runtime == "java21" else environment, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", required=True)
    parser.add_argument("--plan", action="store_true", help="Print exact commands without starting Gradle")
    args = parser.parse_args()
    try:
        if args.plan:
            print(json.dumps([{"command": command, "runtime": runtime}
                              for command, runtime in commands(ROOT, args.target)], indent=2))
        else:
            compile_target(ROOT, args.target)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, "Native build selection failed: " + str(error) + "\n")


if __name__ == "__main__":
    main()
