#!/usr/bin/env python3
"""Reuse one exact previously verified native product; never compile or change it."""
from pathlib import Path
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
ORIGINAL = {
    "runId": 37380001204, "attempt": 1, "jobId": 111999235609,
    "source": "9cb05cd1105ee93e4f69850154642f38354441e3", "artifactId": 11372734283,
    "jarSha256": "cff077bb6e9f4f82dbed2f3ca6bb3c50ce16402ff28224254a1e73787ba3ff9e",
}
NAME = "openallay-forge-1.19.2-0.4.2.jar"


def stage(root=ROOT):
    imported = root / "build/original-native-product"
    files = sorted(path for path in imported.rglob("*") if path.is_file())
    expected = {"build/ci-client-production/" + NAME, "build/ci-client-production/SHA256SUMS",
                "forge/build/libs/" + NAME}
    if {path.relative_to(imported).as_posix() for path in files} != expected or any(path.is_symlink() for path in files):
        raise ValueError("Original native artifact exact file set differs")
    for relative in ("build/ci-client-production/" + NAME, "forge/build/libs/" + NAME):
        if hashlib.sha256((imported / relative).read_bytes()).hexdigest() != ORIGINAL["jarSha256"]:
            raise ValueError("Original verified Forge bytes differ")
    sums = (imported / "build/ci-client-production/SHA256SUMS").read_text()
    if sums != ORIGINAL["jarSha256"] + "  " + NAME + "\n":
        raise ValueError("Original checksum manifest differs")
    output = root / "build/ci-client-production"
    if output.exists():
        raise ValueError("Production staging already exists")
    shutil.copytree(imported / "build/ci-client-production", output)
    (root / "build/original-native-product-receipt.json").write_text(json.dumps(ORIGINAL, indent=2) + "\n")
    return output


if __name__ == "__main__":
    stage()
