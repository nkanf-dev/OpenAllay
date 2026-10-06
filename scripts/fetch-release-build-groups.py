#!/usr/bin/env python3
"""Fetch approved passed build groups without changing their receipts or provenance."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def api(path):
    return json.loads(subprocess.check_output(['gh', 'api', path]))

def validate_provider(group, repository):
    run = api(f"repos/{repository}/actions/runs/{group['runId']}")
    if (run['head_sha'] != group['sourceSha'] or run['run_attempt'] != group['runAttempt']
            or run['status'] != 'completed' or run['path'] != '.github/workflows/minecraft-native.yml'):
        raise ValueError('Original package run identity differs')
    job = api(f"repos/{repository}/actions/jobs/{group['jobId']}")
    if (job['run_id'] != group['runId'] or job['name'] != group['jobName']
            or job['status'] != 'completed' or job['conclusion'] != 'success'
            or job['head_sha'] != group['sourceSha']):
        raise ValueError('Original individual package job has not succeeded')
    artifact = api(f"repos/{repository}/actions/artifacts/{group['artifactId']}")
    if (artifact['name'] != group['artifactName'] or artifact['expired']
            or artifact['workflow_run']['id'] != group['runId']
            or artifact['workflow_run']['head_sha'] != group['sourceSha']
            or artifact['digest'] != 'sha256:' + group['archiveSha256']):
        raise ValueError('Original archive identity differs')
    if not isinstance(artifact['size_in_bytes'], int) or not 0 < artifact['size_in_bytes'] <= 100 * 1024 * 1024:
        raise ValueError('Original group archive exceeds the bounded package size')
    return artifact

def fetch(group, artifact, repository, output):
    destination = output / group['artifactName']
    if destination.exists():
        raise ValueError('Preserve an existing imported stage')
    destination.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix='release-import-', dir=output) as temp:
        path = Path(temp, 'archive.zip')
        with path.open('wb') as stream:
            subprocess.run(['gh', 'api', f"repos/{repository}/actions/artifacts/{group['artifactId']}/zip"],
                           stdout=stream, check=True)
        if path.stat().st_size != artifact['size_in_bytes']:
            raise ValueError('Original archive size differs')
        digest = hashlib.sha256()
        with path.open('rb') as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(block)
        if digest.hexdigest() != group['archiveSha256']:
            raise ValueError('Original archive bytes differ')
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            if len(names) != len(set(names)) or archive.testzip() is not None:
                raise ValueError('Original group archive is corrupt or ambiguous')
            for item in archive.infolist():
                name = item.filename
                parsed = PurePosixPath(name)
                if (parsed.is_absolute() or '..' in parsed.parts or '\\' in name
                        or ':' in name or name != parsed.as_posix() + ('/' if item.is_dir() else '')
                        or (item.external_attr >> 16) & 0o170000 == 0o120000):
                    raise ValueError('Unsafe original archive path')
                target = destination / name
                if item.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with archive.open(item) as source, target.open('wb') as stream:
                        shutil.copyfileobj(source, stream)
    for row in group['familyArtifacts']:
        receipt_path = destination / 'build-receipts' / (row['id'] + '.json')
        if hashlib.sha256(receipt_path.read_bytes()).hexdigest() != row['receiptSha256']:
            raise ValueError('Original receipt bytes differ')
        receipt = json.loads(receipt_path.read_text())
        filename = receipt['family']['filenameTemplate'].replace('{version}', receipt['version'])
        path = destination / filename
        digest = hashlib.sha256()
        with path.open('rb') as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(block)
        if digest.hexdigest() != row['artifactSha256']:
            raise ValueError('Original package bytes differ')
    if hashlib.sha256((destination / 'build-receipts/engine-manifest.json').read_bytes()).hexdigest() != group['engineManifestSha256']:
        raise ValueError('Original engine manifest differs')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--verify-only', action='store_true')
    args = parser.parse_args()
    selection = json.loads((ROOT / 'distribution/release-build-selection.json').read_text())
    repository = os.environ['GITHUB_REPOSITORY']
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise ValueError('Invalid repository')
    if not args.verify_only and args.output is None:
        raise ValueError('Explicit output required')
    if args.output is not None:
        args.output.mkdir(parents=True, exist_ok=True)
    for group in selection['groups']:
        artifact = validate_provider(group, repository)
        if not args.verify_only:
            fetch(group, artifact, repository, args.output)
        print(json.dumps({'target': group['target'], 'sourceSha': group['sourceSha'],
                          'runId': group['runId'], 'jobId': group['jobId'],
                          'originalJobOutcome': 'success', 'originalRunOutcome': 'unchanged',
                          'archiveSha256': group['archiveSha256']}))

if __name__ == '__main__':
    main()
