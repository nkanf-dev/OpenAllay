#!/usr/bin/env python3
"""Read checked-in native target pins. A profile is not a support declaration."""
import argparse
from pathlib import Path
import re
from minecraft_target_loaders import read_target_loaders, target_loaders

MODERN_FIELDS = set("""java_version minecraft_version minecraft_version_range neo_form_version
fabric_version fabric_loader_version neoforge_version neoforge_loader_version_range
jei_version rei_version architectury_version fabric_command_api_version
fabric_resource_loader_version fabric_networking_api_version fabric_message_api_version fabric_lifecycle_events_version
fabric_key_mapping_api_version fabric_rendering_version""".split())
FORGE_FIELDS = set("""java_version minecraft_version minecraft_version_range mcp_version
forge_version forge_loader_version_range jei_version rei_version architectury_version""".split())
FIELDS = MODERN_FIELDS | FORGE_FIELDS


def profile_fields(root, target):
    selection = target_loaders(root, target)
    return FORGE_FIELDS if "forge" in selection["loaders"] else MODERN_FIELDS


def read_profile(root, target):
    path = root / "gradle/minecraft-targets" / (target + ".properties")
    fields = profile_fields(root, target)
    values = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip() or line.lstrip().startswith(("#", "!")):
            continue
        # These current profiles use plain key=value text, without escapes or continuations.
        if "=" not in line or "\\" in line:
            raise ValueError(f"{path}:{number}: expected plain key=value")
        key, value = line.split("=", 1)
        if key not in fields:
            raise ValueError(f"{path}:{number}: unknown field {key!r}")
        if key in values:
            raise ValueError(f"{path}:{number}: duplicate field {key!r}")
        if not value or value != value.strip():
            raise ValueError(f"{path}:{number}: empty or padded field {key!r}")
        values[key] = value
    if set(values) != fields:
        raise ValueError(f"{path}: missing fields: {', '.join(sorted(fields - values.keys()))}")
    if values["minecraft_version"] != target:
        raise ValueError(f"{path}: minecraft_version does not match target {target}")
    if not re.fullmatch(r"[1-9][0-9]*", values["java_version"]):
        raise ValueError(f"{path}: java_version must be a positive Java major")
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    root = Path(__file__).resolve().parents[1]
    parser.add_argument("--target", choices=tuple(read_target_loaders(root)), default="26.2")
    parser.add_argument("--property", choices=sorted(FIELDS), required=True)
    args = parser.parse_args()
    try:
        values = read_profile(Path(__file__).resolve().parents[1], args.target)
    except (OSError, UnicodeError, ValueError) as error:
        parser.error(str(error))
    if args.property not in values:
        parser.error("Field is not part of this target profile: " + args.property)
    print(values[args.property])


if __name__ == "__main__":
    main()
