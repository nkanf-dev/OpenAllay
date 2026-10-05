#!/usr/bin/env python3
"""Invoke the real client suite with the same SHA-verified default production JAR."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from minecraft_target_loaders import target_loaders

ROOT = Path(__file__).resolve().parents[1]


def run(loader, java, batch_id, root=ROOT, minecraft_target="26.2", scenarios=None):
    if loader not in target_loaders(root, minecraft_target)["loaders"]:
        raise ValueError("Client workflow loader is not an actual source target identity")
    staged = root / "build/ci-client-production"
    checksums = {}
    for line in (staged / "SHA256SUMS").read_text().splitlines():
        sha, name = line.split("  ", 1)
        if Path(name).name != name or name in checksums:
            raise ValueError("Invalid production checksum manifest")
        checksums[name] = sha
    names = [name for name in checksums if name.startswith("openallay-" + loader + "-" + minecraft_target + "-") and name.endswith(".jar")]
    if len(names) != 1:
        raise ValueError("Expected exactly one verified default loader JAR")
    jar = staged / names[0]
    if hashlib.sha256(jar.read_bytes()).hexdigest() != checksums[names[0]]:
        raise ValueError("Original production JAR changed")
    runtime = root / "build/e2e/runtime" / minecraft_target / "minecraft"
    receipt = json.loads((runtime / ".provision" / (loader + "-runtime.json")).read_text())
    if receipt["loader"] != loader or receipt["minecraft"] != minecraft_target or receipt["minecraftRoot"] != str(runtime.resolve()):
        raise ValueError("Official runtime identity differs")
    command = [sys.executable, "-B", str(root / "scripts/run-ci-client-acceptance.py"), loader,
               "--minecraft-target", minecraft_target, "--batch-id", batch_id, "--jar", str(jar.resolve()), "--artifact-sha256", checksums[names[0]],
               "--java", str(java.resolve()), "--minecraft-root", str(runtime.resolve()),
               "--assets-root", str((root / "build/e2e/runtime" / minecraft_target / "assets").resolve())]
    if loader == "fabric":
        command.extend(["--fabric-api", receipt["fabricApi"]])
    if scenarios:
        command.extend(["--scenarios"] + list(scenarios))
    return subprocess.run(command, cwd=root, check=False).returncode


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--loader", required=True, choices=("fabric", "forge", "neoforge"))
    parser.add_argument("--java", required=True, type=Path)
    parser.add_argument("--batch-id", required=True)
    parser.add_argument("--minecraft-target", default="26.2")
    parser.add_argument("--scenarios", nargs="+")
    arguments = parser.parse_args()
    try:
        raise SystemExit(run(arguments.loader, arguments.java, arguments.batch_id, minecraft_target=arguments.minecraft_target, scenarios=arguments.scenarios))
    except (OSError, ValueError, KeyError) as failure:
        parser.exit(1, "Client workflow refused: " + str(failure) + "\n")
