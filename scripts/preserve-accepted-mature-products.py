#!/usr/bin/env python3
"""Preserve four exact accepted original products; no build, launch or publication."""
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
PRODUCTS = [{'target': '1.19.2', 'loader': 'forge', 'run': 37388892087, 'artifact': 11380706080, 'source': '438ba61a84c48333dc1cda92a55463f7b01e2c16', 'sha256': 'c09f84ccc7c680fccecacc36280fd3564c1bfd01c0950c4a2aeff7970b429a64', 'proof': 'building/reload/commands/UI'}, {'target': '1.18.2', 'loader': 'forge', 'run': 37397120987, 'artifact': 11383755758, 'source': '01636660736f417c7359485a5c834affe5a1ce7d', 'sha256': '8a03c0b111085bbb6ed5434b1605c93c74ada9e43bbaefda9a1f902d4369a2dd', 'proof': 'building/reload/commands'}, {'target': '1.18.2', 'loader': 'forge', 'run': 37398771932, 'artifact': 11383699840, 'source': '1122e5314677f5ea0483691a3cbd066fb6180866', 'sha256': 'e5cb3461452f5361e1981fa9de94157c302311c6ee8fd682259adcd14dabb2bf', 'proof': 'UI/editor/native-toasts'}, {'target': '26.2', 'loader': 'fabric', 'run': 37401212610, 'artifact': 11384878871, 'source': '9396684bd95f48bdfbc7cf9c6db5d261247cc6e6', 'sha256': '98aece007f0b4177276160f4db87b1db38c78338253e5d14079bb1c8cfcbc6af', 'proof': 'affected modern UI with correction receipt'}]


def preserve(root=ROOT):
    output = root / "build/preserved-mature-products"
    if output.exists():
        raise ValueError("Preservation output already exists")
    output.mkdir(parents=True)
    receipts = []
    for index, product in enumerate(PRODUCTS):
        imported = root / "build/original-product-imports" / str(product["artifact"])
        expected_name = "openallay-" + product["loader"] + "-" + product["target"] + "-0.4.2.jar"
        candidates = list(imported.rglob(expected_name))
        if not candidates or len(candidates) > 2 or any(path.is_symlink() for path in candidates):
            raise ValueError("Original archive product paths differ")
        for path in candidates:
            if hashlib.sha256(path.read_bytes()).hexdigest() != product["sha256"]:
                raise ValueError("Original accepted product bytes differ")
        destination = output / (str(product["run"]) + "-" + product["source"])
        destination.mkdir()
        shutil.copyfile(candidates[0], destination / expected_name)
        if hashlib.sha256((destination / expected_name).read_bytes()).hexdigest() != product["sha256"]:
            raise ValueError("Preserved product bytes changed")
        receipts.append({**product, "attempt": 1, "file": (destination / expected_name).relative_to(output).as_posix()})
    (output / "original-product-provenance.json").write_text(json.dumps(receipts, indent=2) + "\n")
    print(json.dumps({"preservedOriginalProducts": receipts, "rebuilt": False, "published": False}, indent=2))
    return output


if __name__ == "__main__":
    preserve()
