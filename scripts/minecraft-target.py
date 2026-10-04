#!/usr/bin/env python3
"""Read checked-in native target pins. A profile is not a support declaration."""
import argparse
from pathlib import Path
import re

FIELDS = set("""java_version minecraft_version minecraft_version_range neo_form_version
fabric_version fabric_loader_version neoforge_version neoforge_loader_version_range
jei_version rei_version architectury_version fabric_command_api_version
fabric_resource_loader_version fabric_networking_api_version fabric_lifecycle_events_version
fabric_key_mapping_api_version fabric_rendering_version""".split())


def read_profile(root, target):
    path = root / "gradle/minecraft-targets" / (target + ".properties")
    values = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip() or line.lstrip().startswith(("#", "!")):
            continue
        # These current profiles use plain key=value text, without escapes or continuations.
        if "=" not in line or "\\" in line:
            raise ValueError(f"{path}:{number}: expected plain key=value")
        key, value = line.split("=", 1)
        if key not in FIELDS:
            raise ValueError(f"{path}:{number}: unknown field {key!r}")
        if key in values:
            raise ValueError(f"{path}:{number}: duplicate field {key!r}")
        if not value or value != value.strip():
            raise ValueError(f"{path}:{number}: empty or padded field {key!r}")
        values[key] = value
    if set(values) != FIELDS:
        raise ValueError(f"{path}: missing fields: {', '.join(sorted(FIELDS - values.keys()))}")
    if values["minecraft_version"] != target:
        raise ValueError(f"{path}: minecraft_version does not match target {target}")
    if not re.fullmatch(r"[1-9][0-9]*", values["java_version"]):
        raise ValueError(f"{path}: java_version must be a positive Java major")
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=("1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2", "26.3"), default="26.2")
    parser.add_argument("--property", choices=sorted(FIELDS), required=True)
    args = parser.parse_args()
    try:
        values = read_profile(Path(__file__).resolve().parents[1], args.target)
    except (OSError, UnicodeError, ValueError) as error:
        parser.error(str(error))
    print(values[args.property])


if __name__ == "__main__":
    main()
