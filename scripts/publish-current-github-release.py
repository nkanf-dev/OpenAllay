#!/usr/bin/env python3
"""Publish exact staged player files; never rebuild or overwrite a prior release."""
import json
import os
from pathlib import Path
import subprocess

from release_publication import select_records

ROOT = Path(__file__).resolve().parents[1]


def main():
    tag = os.environ["RELEASE_TAG"]
    release = (ROOT / "release").resolve(strict=True)
    records = json.loads((ROOT / "release-publication-records.json").read_text())
    selected = select_records(ROOT, release, records, tag, "github")
    command = ["gh", "release", "create", tag]
    command += [row["artifactPath"] for row in selected]
    # Internal source/build JSON stays in CI artifacts, not player downloads.
    command += [str(release / "SHA256SUMS"),
                "--notes-file", str(ROOT / "release-notes.md"), "--title", "OpenAllay " + tag,
                "--verify-tag"]
    if "-" in tag:
        command.append("--prerelease")
    subprocess.run(command, check=True, cwd=ROOT)


if __name__ == "__main__":
    main()
