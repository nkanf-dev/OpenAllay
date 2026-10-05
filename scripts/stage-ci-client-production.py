#!/usr/bin/env python3
"""Stage already-verified default production JARs, never rebuild them."""
import hashlib
from pathlib import Path
import shutil
from minecraft_target_loaders import target_loaders

ROOT = Path(__file__).resolve().parents[1]


def stage(root=ROOT, minecraft_target="26.2"):
    version = next(line.split("=", 1)[1] for line in (root / "gradle.properties").read_text().splitlines()
                   if line.startswith("version="))
    output = root / "build/ci-client-production"
    if output.exists():
        raise ValueError("Client production staging already exists")
    inputs = [root / loader / "build/libs" / ("openallay-" + loader + "-" + minecraft_target + "-" + version + ".jar")
              for loader in target_loaders(root, minecraft_target)["loaders"]]
    if any(not path.is_file() or path.is_symlink() for path in inputs):
        raise ValueError("Missing default verified production JAR")
    output.mkdir(parents=True)
    records = []
    for source in inputs:
        original = hashlib.sha256(source.read_bytes()).hexdigest()
        copied = output / source.name
        shutil.copyfile(source, copied)
        if hashlib.sha256(copied.read_bytes()).hexdigest() != original:
            raise ValueError("Production artifact bytes changed during staging")
        records.append(original + "  " + copied.name)
    (output / "SHA256SUMS").write_text("\n".join(records) + "\n")
    print(output)
    return output


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft-target", default="26.2")
    args = parser.parse_args()
    stage(minecraft_target=args.minecraft_target)
