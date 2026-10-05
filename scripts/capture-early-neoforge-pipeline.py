#!/usr/bin/env python3
"""Capture two official failing source pipelines. Never package or accept a mod artifact."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MEMBERS = ("net/minecraft/world/level/block/Blocks.java", "net/minecraft/world/level/block/ChorusFlowerBlock.java")


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def capture(target):
    spec = importlib.util.spec_from_file_location("native_recipe", ROOT / "scripts/compile-native-target.py")
    native = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(native)
    if target not in native.EARLY:
        raise ValueError("Capture is restricted to the two failed early NeoForge targets")
    environment = native.java21_environment(dict(os.environ))
    commands = native.commands(ROOT, target, loaders=("neoforge",))
    isolated = ROOT / "native-builds/early-neoforge"
    report = isolated / "build/reports/early-pipeline-capture"
    report.mkdir(parents=True, exist_ok=True)
    subprocess.run(commands[0][0], cwd=ROOT, check=True)
    command = commands[1][0][:-1] + ["--init-script", str(ROOT / "gradle/capture-early-neoforge-pipeline.init.gradle"),
                                    "neoFormPatchUserDev"]
    result = None
    try:
        with (report / "patch-pipeline.log").open("w") as stream:
            result = subprocess.run(command, cwd=ROOT, env=environment, stdout=stream, stderr=subprocess.STDOUT)
    finally:
        snapshot = report / "resolved-task-inputs.json"
        manifest = {"target": target, "purpose": "diagnostic-only", "productionArtifactAccepted": False,
                    "failedOfficialPatchIsFatal": True, "exitCode": None if result is None else result.returncode,
                    "command": command, "archives": []}
        for runtime in sorted((isolated / "build/neoForm").glob("neoFormJoined" + target + "-*")):
            for step in ("decompile", "inject", "patch", "patchUserDev"):
                directory = runtime / "steps" / step
                console = directory / "console.log"
                if console.is_file():
                    destination = report / "task-log-headers" / runtime.name / (step + ".txt")
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    with console.open(errors="replace") as stream:
                        # NG Execute logs exact JVM, classpath and interpolated args first.
                        lines = [next(stream, "") for _ in range(12)]
                    destination.write_text("".join(lines))
                for archive in sorted(directory.glob("*.jar")) + sorted(directory.glob("rejects.zip")):
                    record = {"path": str(archive), "sha256": digest(archive), "members": []}
                    with zipfile.ZipFile(archive) as source:
                        for entry in source.namelist():
                            if entry in MEMBERS or (archive.name == "rejects.zip" and
                                    any(name.rsplit("/", 1)[-1] in entry for name in MEMBERS)):
                                content = source.read(entry)
                                # Fixed names avoid trusting archive paths for extraction.
                                destination = report / "source-members" / runtime.name / step / (
                                    ("reject-" if archive.name == "rejects.zip" else "") + Path(entry).name)
                                destination.parent.mkdir(parents=True, exist_ok=True)
                                destination.write_bytes(content)
                                record["members"].append({"entry": entry, "bytes": len(content),
                                                          "sha256": hashlib.sha256(content).hexdigest(),
                                                          "capture": str(destination.relative_to(report))})
                    manifest["archives"].append(record)
        (report / "capture-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    # No production steps follow a capture, whether patching succeeds or fails.
    if result is None or result.returncode:
        raise SystemExit(1 if result is None else result.returncode)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", required=True, choices=("1.20.2", "1.20.3"))
    capture(parser.parse_args().target)
