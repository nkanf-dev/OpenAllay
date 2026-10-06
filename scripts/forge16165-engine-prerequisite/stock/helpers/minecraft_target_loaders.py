"""Actual loader identities and native compiler selection for checked-in targets."""
import json
from pathlib import Path
import re

FIELDS = frozenset(("loaders", "nativeToolchain"))
LOADERS = frozenset(("fabric", "forge", "neoforge"))


def read_target_loaders(root):
    def exact_object(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("Duplicate target loader map key: " + key)
            result[key] = value
        return result
    path = Path(root) / "gradle/minecraft-target-loaders.json"
    targets = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=exact_object)
    if not isinstance(targets, dict) or not targets:
        raise ValueError("Target loader map must be a nonempty object")
    for target, entry in targets.items():
        if not re.fullmatch(r"(?:1\.[0-9]+(?:\.[0-9]+)?|26\.[0-9]+(?:\.[0-9]+)?)", target):
            raise ValueError("Invalid target loader map target: " + target)
        if not isinstance(entry, dict) or set(entry) != FIELDS:
            raise ValueError("Invalid target loader map fields: " + target)
        loaders = entry["loaders"]
        if (not isinstance(loaders, list) or not loaders
                or any(not isinstance(loader, str) or loader not in LOADERS for loader in loaders)
                or len(set(loaders)) != len(loaders)):
            raise ValueError("Expected distinct actual target loaders: " + target)
        if entry["nativeToolchain"] not in ("legacyForge", "neoForge"):
            raise ValueError("Invalid native toolchain: " + target)
        if "forge" in loaders and entry["nativeToolchain"] != "legacyForge":
            raise ValueError("Forge needs its actual legacyForge toolchain: " + target)
        if "forge" in loaders and "neoforge" in loaders:
            raise ValueError("A target cannot alias Forge as NeoForge: " + target)
    return targets


def target_loaders(root, target):
    targets = read_target_loaders(root)
    if target not in targets:
        raise ValueError("Unknown Minecraft target: " + target)
    return targets[target]


def runtime_pin_fields(loader):
    if loader == "forge":
        return ("minecraft_version", "java_version", "forge_version")
    if loader in ("fabric", "neoforge"):
        # Preserve existing source receipt shape for the unchanged 23 profiles.
        return ("minecraft_version", "java_version", "fabric_loader_version", "fabric_version", "neoforge_version")
    raise ValueError("Unknown actual runtime loader: " + loader)


def fml_runtime_identity(pins, loader):
    target = pins["minecraft_version"]
    if loader == "forge":
        version = pins["forge_version"]
        if not version.startswith(target + "-"):
            raise ValueError("Official Forge coordinate differs from target")
        return {"group": "net.minecraftforge", "artifact": "forge", "version": version,
                "profile": target + "-forge-" + version.removeprefix(target + "-"),
                "maven": "https://maven.minecraftforge.net/",
                "mainClasses": ("cpw.mods.bootstraplauncher.BootstrapLauncher",)}
    if loader == "neoforge":
        version = pins["neoforge_version"]
        early = target == "1.20.1"
        if early and not version.startswith(target + "-"):
            raise ValueError("Early Forge-shaped NeoForge pin differs")
        return {"group": "net.neoforged", "artifact": "forge" if early else "neoforge", "version": version,
                "profile": target + "-forge-" + version.removeprefix(target + "-") if early else "neoforge-" + version,
                "maven": "https://maven.neoforged.net/releases/",
                "mainClasses": ("net.neoforged.fml.startup.Client", "cpw.mods.bootstraplauncher.BootstrapLauncher")}
    raise ValueError("Expected actual FML runtime loader")
