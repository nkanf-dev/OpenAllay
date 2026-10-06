#!/usr/bin/env python3
"""Bind publication to the successful package build and exact annotated source tag."""
import json
import os
import re
import subprocess

def main():
    tag = os.environ['RELEASE_TAG']
    source = os.environ['BUILD_SOURCE_SHA']
    run_id = os.environ['BUILD_RUN_ID']
    repository = os.environ['GITHUB_REPOSITORY']
    if not re.fullmatch(r'[0-9a-f]{40}', source) or not re.fullmatch(r'[1-9][0-9]*', run_id):
        raise ValueError('Exact source SHA and run ID required')
    subprocess.run(['./scripts/verify-release-tag.sh', tag, source], check=True)
    run = json.loads(subprocess.check_output(['gh', 'api', f'repos/{repository}/actions/runs/{run_id}']))
    if (run['head_sha'] != source or run['status'] != 'completed'
            or run['conclusion'] != 'success' or run['event'] != 'workflow_dispatch'
            or run['path'] != '.github/workflows/minecraft-native.yml'):
        raise ValueError('Source-bound package build has not succeeded')
    subprocess.run(['python3', '-B', 'scripts/fetch-release-build-groups.py', '--verify-only'], check=True)
    print(json.dumps({'tag': tag, 'source': source, 'buildRunId': int(run_id),
                      'evidence': 'compile-package', 'publicationSourceVerified': True}))

if __name__ == '__main__':
    main()
