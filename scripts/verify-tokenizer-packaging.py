#!/usr/bin/env python3
"""Verify JTokkit loader registration, BPE resources and license without loading a game."""
from __future__ import annotations

import argparse
import hashlib
from io import BytesIO
import json
from pathlib import Path
import zipfile

VERSION = "1.1.0"
JAR_SHA256 = "1501ce0259ab897c6746ccfafa1d208acd404fb17e1ac62e157172f2678b1183"
RESOURCES = {
    "com/knuddels/jtokkit/cl100k_base.tiktoken":
        "223921b76ee99bde995b7ff738513eef100fb51d18c93597a113bcffe865b2a7",
    "com/knuddels/jtokkit/o200k_base.tiktoken":
        "446a9538cb6c348e3516120d7c08b09f57c36495e2acfffe59a5bf8b0cfb1a2d",
}
LICENSE = "META-INF/licenses/jtokkit-MIT.txt"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def verify(path: Path, loader: str) -> dict:
    with zipfile.ZipFile(path) as outer:
        entries = outer.namelist()
        require(LICENSE in entries, "JTokkit MIT license missing from product")
        require("Permission is hereby granted" in outer.read(LICENSE).decode("utf-8"),
                "JTokkit license is not MIT")
        require(not any(name.startswith("com/knuddels/jtokkit/") for name in entries),
                "Tokenizer was flattened instead of nested")
        candidates = [name for name in entries if name.endswith(".jar")
                      and Path(name).name.startswith("jtokkit-")]
        require(len(candidates) == 1, "Exactly one JTokkit dependency must be nested")
        nested_path = candidates[0]
        require(Path(nested_path).name == f"jtokkit-{VERSION}.jar", "Wrong JTokkit version nested")
        if loader == "fabric":
            metadata = json.loads(outer.read("fabric.mod.json"))
            registered = [item["file"] for item in metadata.get("jars", [])]
        else:
            metadata = json.loads(outer.read("META-INF/jarjar/metadata.json"))
            registered = [item["path"] for item in metadata["jars"]]
            item = next((item for item in metadata["jars"] if item["path"] == nested_path), None)
            require(item is not None, "JTokkit JarJar registration missing")
            require(item["identifier"] == {"group": "com.knuddels", "artifact": "jtokkit"},
                    "Wrong JTokkit JarJar identity")
            require(item["version"]["artifactVersion"] == VERSION, "Wrong JTokkit JarJar version")
        require(nested_path in registered, "JTokkit is not registered with the loader")
        content = outer.read(nested_path)
        if loader in ("forge", "neoforge"):
            # Fabric Loom adds synthetic fabric.mod.json, so its nested JAR hash changes.
            require(hashlib.sha256(content).hexdigest() == JAR_SHA256,
                    "Nested JTokkit differs from the pinned Maven artifact")
        with zipfile.ZipFile(BytesIO(content)) as nested:
            names = nested.namelist()
            require(len(names) == len(set(names)), "Duplicate tokenizer JAR entries")
            for resource, expected in RESOURCES.items():
                require(resource in names, f"BPE resource missing: {resource}")
                require(hashlib.sha256(nested.read(resource)).hexdigest() == expected,
                        f"BPE resource hash mismatch: {resource}")
            require("com/knuddels/jtokkit/Encodings.class" in names, "Tokenizer API missing")
            require(not any(name.endswith((".dylib", ".so", ".dll")) for name in names),
                    "Unexpected native tokenizer dependency")
    return {"loader": loader, "artifact": str(path), "tokenizer": f"com.knuddels:jtokkit:{VERSION}",
            "registered": nested_path, "encodings": list(RESOURCES)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fabric", type=Path, required=True)
    parser.add_argument("--neoforge", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps([verify(args.fabric, "fabric"), verify(args.neoforge, "neoforge")], indent=2))


if __name__ == "__main__":
    main()
