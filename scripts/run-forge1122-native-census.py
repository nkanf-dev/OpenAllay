#!/usr/bin/env python3
"""Start one isolated remote FG3 tooling island, then retain a bounded census."""
import argparse
import hashlib
import json
import os
import subprocess
import urllib.request
import zipfile
from pathlib import Path


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--workspace', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--javap', type=Path, required=True)
    args = p.parse_args()
    repo = Path(__file__).resolve().parents[1]
    island = repo / 'native-builds/forge1122-census'
    pins = json.loads((island/'public-input-lock.json').read_text())
    work, output = args.workspace.resolve(), args.output.resolve()
    if work.exists() or output.exists() or work==output or repo==work or repo in work.parents:
        raise ValueError('Use fresh separate external workspace and evidence paths')
    work.mkdir(parents=True); output.mkdir(parents=True)
    files = {}
    for name, record in pins.items():
        with urllib.request.urlopen(record['url'], timeout=60) as response:
            data = response.read(8*1024*1024+1)
        if len(data)>8*1024*1024 or digest(data)!=record['sha256']:
            raise ValueError('Pinned public input differs: '+name)
        path = work/record['filename']; path.write_bytes(data); files[name]=path
    with zipfile.ZipFile(files['mdk']) as archive:
        for name in ('gradlew','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties'):
            path=work/name; path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(archive.read(name))
    properties=work/'gradle/wrapper/gradle-wrapper.properties'
    properties.write_text(properties.read_text()+'\ndistributionSha256Sum='+pins['gradle_checksum']['sha256Text']+'\n')
    (work/'gradlew').chmod(0o755)
    cmd=[str(work/'gradlew'),'--no-daemon','--max-workers=2','-p',str(island),
        '-PcanonicalSourceRoot='+str(repo),'-PcensusOutput='+str(output),
        'exportNativeInputs','--stacktrace']
    with (output/'tooling.log').open('w') as log:
        result=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT,timeout=2700)
    (output/'tooling-command.json').write_text(json.dumps({'command':cmd,'exitCode':result.returncode,
        'toolJavaHome':os.environ.get('JAVA_HOME'),'applicationCompile':False,'gameLaunch':False},indent=2)+'\n')
    if result.returncode:
        raise SystemExit(result.returncode)
    subprocess.run(['python3','-B',str(repo/'scripts/collect-forge1122-native-census.py'),
        '--inputs',str(output/'native-inputs.json'),'--selection',str(output/'source-selection.json'),
        '--mcp-config',str(files['mcp_config']),'--snapshot',str(files['snapshot']),
        '--javap',str(args.javap),'--output',str(output/'inventory')],check=True,timeout=600)

if __name__=='__main__':
    main()
