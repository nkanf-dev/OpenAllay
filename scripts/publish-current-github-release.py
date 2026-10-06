#!/usr/bin/env python3
"""Publish the exact staged files; never rebuild or overwrite a prior release."""
import os
from pathlib import Path
import subprocess

def main():
    tag = os.environ['RELEASE_TAG']
    release = Path('release')
    jars = sorted(release.glob('openallay-*.jar'))
    if not jars or not (release / 'SHA256SUMS').is_file():
        raise ValueError('Missing verified release packages')
    command = ['gh', 'release', 'create', tag]
    command += [str(path) for path in jars]
    command += [str(path) for path in sorted((release / 'build-receipts').glob('*.json'))]
    command += [str(release / 'SHA256SUMS'), 'release-publication-records.json',
                '--notes-file', 'release-notes.md', '--title', 'OpenAllay ' + tag,
                '--verify-tag']
    if '-' in tag:
        command.append('--prerelease')
    subprocess.run(command, check=True)

if __name__ == '__main__':
    main()
