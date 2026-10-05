#!/usr/bin/env python3
"""Retain bounded generated native source facts after an actual Forge compile."""
from pathlib import Path
import hashlib
import json
import zipfile
ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "build/forge-native-contracts"
OUTPUT.mkdir(parents=True, exist_ok=True)
MEMBERS = ("net/minecraft/client/player/LocalPlayer.java", "net/minecraft/client/gui/screens/ChatScreen.java")
records = []
for archive in sorted((ROOT / "common/build/moddev/artifacts").glob("*sources.jar")):
    with zipfile.ZipFile(archive) as source:
        for name in MEMBERS:
            if name not in source.namelist():
                continue
            content = source.read(name)
            lines = content.decode().splitlines()
            excerpts = set()
            for index, line in enumerate(lines):
                if "commandSigned" in line or "handleChatInput(" in line or "sendCommand(" in line:
                    excerpts.update(range(max(0,index-8), min(len(lines),index+45)))
            destination = OUTPUT / (archive.name + "--" + Path(name).name + ".txt")
            destination.write_text("\n".join(str(index+1)+": "+lines[index] for index in sorted(excerpts))+"\n")
            records.append({"archive":str(archive.relative_to(ROOT)), "member":name,
                            "memberSha256":hashlib.sha256(content).hexdigest(),
                            "excerpt":destination.name})
(OUTPUT / "receipt.json").write_text(json.dumps(records,indent=2)+"\n")
