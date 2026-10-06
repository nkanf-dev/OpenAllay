#!/usr/bin/env python3
"""Revalidate an unchanged native UI report using its lossless compaction receipts. No game launch."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def validate(directory):
    manifest = json.loads((directory / "launch.json").read_text())
    report_path = directory / "report.json"
    report = json.loads(report_path.read_text())
    compact = json.loads((directory / "diagnostics-manifest.json").read_text())
    records = {item["sourcePath"]: item for item in compact["files"]}
    if records["report.json"]["sourceSha256"] != digest(report_path):
        raise ValueError("Original native report bytes differ")
    if report.get("scenario") != "ui-live-ux-regressions" or report.get("outcome") != "COMPLETED":
        raise ValueError("Original native UI did not complete")
    if report.get("interactKeyRestored") is not True or report.get("microphoneCaptureAttempted") is not False:
        raise ValueError("Native interaction restoration or capture policy differs")
    spec = importlib.util.spec_from_file_location("retained_ui_gate", ROOT / "scripts/run-packaged-builder-acceptance.py")
    launcher = importlib.util.module_from_spec(spec); spec.loader.exec_module(launcher)
    launcher.validate_live_ux_receipts(report)
    for frame in report["nativeFrames"]:
        relative = "screenshots/" + Path(frame["path"]).name
        item = records[relative]
        image = item.get("image", {})
        output = directory / item["outputPath"]
        if (item["sourceSha256"] != frame["sha256"] or frame.get("source") != "native-mainRenderTarget"
                or digest(output) != item["outputSha256"] or image.get("webpSha256") != digest(output)
                or image.get("pixelsVerified") is not True or image.get("metadataVerified") is not True
                or image.get("width") != frame["width"] or image.get("height") != frame["height"]):
            raise ValueError("Native frame identity or lossless receipt differs")
    return {"outcome": "PASSED", "evidenceKind": "retained-original-native-ui-revalidation",
            "originalReportSha256": digest(report_path), "originalJar": manifest["packagedArtifact"],
            "sourceIdentity": report["sourceIdentity"], "nativeFrames": len(report["nativeFrames"]),
            "originalCIConclusion": "failure", "correction": "Graphical UI must not run the Builder final screenshot gate",
            "gameRelaunched": False, "productRebuilt": False}

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    print(json.dumps(validate(args.directory), indent=2))
