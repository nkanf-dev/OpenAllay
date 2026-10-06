#!/usr/bin/env python3
"""Read only original group receipts on a remote runner and emit small provenance records."""
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def main():
    repo = os.environ['GITHUB_REPOSITORY']
    run_id = '37419529652'
    source = '29deb369a5684ed144be3373382c558d6238f79f'
    jobs = json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/actions/runs/{run_id}/jobs?per_page=100']))['jobs']
    artifacts = json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/actions/runs/{run_id}/artifacts?per_page=100']))['artifacts']
    groups = []
    base = ROOT / 'build/metadata-groups'
    output = ROOT / 'build/release-metadata'
    output.mkdir(parents=True, exist_ok=True)
    for directory in sorted(base.iterdir()):
        artifact = next(a for a in artifacts if a['name'] == directory.name)
        receipts = sorted((directory / 'build-receipts').glob('*.json'))
        rows = []
        target = None
        for path in receipts:
            if path.name == 'engine-manifest.json': continue
            value = json.loads(path.read_text())
            if value['sourceSha'] != source or value['sourceRunId'] != run_id or value['kind'] != 'compile-package':
                raise ValueError('Original group provenance differs')
            target = value['family']['buildTarget']
            rows.append({'id': value['family']['id'], 'artifactSha256':value['artifactSha256'],
                         'receiptSha256':hashlib.sha256(path.read_bytes()).hexdigest()})
            filename = value['family']['filenameTemplate'].replace('{version}', value['version'])
            if hashlib.sha256((directory / filename).read_bytes()).hexdigest() != value['artifactSha256']:
                raise ValueError('Original package does not match its receipt')
        if not rows: continue
        job = next(j for j in jobs if j['name'].startswith('build-packages ('+target+',') and j['conclusion']=='success')
        if job['head_sha'] != source or artifact['workflow_run']['head_sha'] != source:
            raise ValueError('Original successful source differs')
        engine = directory / 'build-receipts/engine-manifest.json'
        groups.append({'target':target, 'sourceSha':source, 'runId':int(run_id),'runAttempt':1,
                       'jobId':job['id'],'jobName':job['name'],'artifactId':artifact['id'],
                       'artifactName':artifact['name'],'archiveSha256':artifact['digest'].removeprefix('sha256:'),
                       'engineManifestSha256':hashlib.sha256(engine.read_bytes()).hexdigest(), 'familyArtifacts':rows})
        dest = output / target
        dest.mkdir()
        for path in receipts: (dest / path.name).write_bytes(path.read_bytes())
    if len(groups)!=7: raise ValueError('Expected seven successful original groups')
    (output/'groups.json').write_text(json.dumps(groups,indent=2)+'\n')
    print(json.dumps({'originalRun':run_id,'successfulGroups':len(groups),'retainedMetadata':'small JSON only'}))

if __name__=='__main__': main()
