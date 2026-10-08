#!/usr/bin/env python3
"""Start one isolated remote FG3 tooling island, then retain a bounded census."""
import argparse
import hashlib
import json
import os
import subprocess
import urllib.error
import urllib.request
import zipfile
from pathlib import Path


def digest(data):
    return hashlib.sha256(data).hexdigest()


def download_pin(name, record, work, output):
    receipt = {'name': name, 'url': record['url'], 'expectedSha256': record['sha256'],
        'userAgent': 'OpenAllay-CI-Runtime', 'status': 'requesting'}
    receipt_path = output / ('download-' + name + '.json')
    def save():
        receipt_path.write_text(json.dumps(receipt, indent=2) + '\n')
    save()  # Preserve the exact attempted public URL even on network failure.
    request = urllib.request.Request(record['url'], headers={'User-Agent': receipt['userAgent']})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            receipt['finalUrl'] = response.geturl()
            receipt['httpStatus'] = response.status
            data = response.read(8*1024*1024+1)
        receipt.update(bytes=len(data), actualSha256=digest(data))
        if len(data)>8*1024*1024 or receipt['actualSha256']!=record['sha256']:
            raise ValueError('Pinned public input differs: '+name)
        path = work/record['filename']
        path.write_bytes(data)
        receipt.update(status='verified', path=str(path))
        save()
        return path
    except Exception as error:
        receipt.update(status='failed', errorType=type(error).__name__, error=str(error)[:2048])
        if isinstance(error, urllib.error.HTTPError):
            receipt.update(httpStatus=error.code, rejectedUrl=error.geturl())
        save()
        raise


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--workspace', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--javap', type=Path, required=True)
    p.add_argument('--native-build-request', type=Path)
    p.add_argument('--java8-diagnostic',action='store_true')
    p.add_argument('--language-candidates',action='store_true')
    p.add_argument('--native-package-probe',action='store_true')
    p.add_argument('--selected-owners',type=Path)
    args = p.parse_args()
    if args.native_package_probe and (args.java8_diagnostic or args.language_candidates):
        raise ValueError('Native package probe must use normal compile/AP/reobf task alone')
    repo = Path(__file__).resolve().parents[1]
    island = repo / 'native-builds/forge1122-census'
    pins = json.loads((island/'public-input-lock.json').read_text())
    work, output = args.workspace.resolve(), args.output.resolve()
    if work.exists() or output.exists() or work==output or repo==work or repo in work.parents:
        raise ValueError('Use fresh separate external workspace and evidence paths')
    work.mkdir(parents=True); output.mkdir(parents=True)
    files = {}
    for name, record in pins.items():
        files[name] = download_pin(name, record, work, output)
    with zipfile.ZipFile(files['mdk']) as archive:
        for name in ('gradlew','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties'):
            path=work/name; path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(archive.read(name))
    properties=work/'gradle/wrapper/gradle-wrapper.properties'
    properties.write_text(properties.read_text()+'\ndistributionSha256Sum='+pins['gradle_checksum']['sha256Text']+'\n')
    (work/'gradlew').chmod(0o755)
    cmd=[str(work/'gradlew'),'--no-daemon','--max-workers=2','-p',str(island),
        '-PcanonicalSourceRoot='+str(repo),'-PcensusOutput='+str(output),
        ('materializeNativeLanguageCandidates' if args.language_candidates else ('diagnoseNativeJava8' if args.java8_diagnostic else 'buildNativeApplication')) if args.native_build_request else 'exportNativeInputs','--full-stacktrace']
    if args.language_candidates:
        if args.java8_diagnostic or not args.native_build_request: raise ValueError('Distinct exact language candidate request required')
        cmd.append('-PnativeLanguageCandidates=true')
        if args.selected_owners:cmd.append('-PnativeCandidateOwners='+str(args.selected_owners.resolve()))
    if args.java8_diagnostic:
        if not args.native_build_request: raise ValueError('Diagnostic requires exact source/closure request')
        cmd.append('-PnativeJava8Diagnostic=true')
    if args.native_build_request:
        cmd.append('-PnativeBuildRequest='+str(args.native_build_request.resolve()))
    with (output/'tooling.log').open('w') as log:
        result=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT,timeout=2700)
    (output/'tooling-command.json').write_text(json.dumps({'command':cmd,'exitCode':result.returncode,
        'toolJavaHome':os.environ.get('JAVA_HOME'),'applicationCompile':False,'gameLaunch':False},indent=2)+'\n')
    if result.returncode:
        raise SystemExit(result.returncode)
    if args.native_build_request:
        return  # application mode captures only the new supplement, never repeats old census
    subprocess.run(['python3','-B',str(repo/'scripts/collect-forge1122-native-census.py'),
        '--inputs',str(output/'native-inputs.json'),'--selection',str(output/'source-selection.json'),
        '--mcp-config',str(files['mcp_config']),'--snapshot',str(files['snapshot']),
        '--javap',str(args.javap),'--output',str(output/'inventory')],check=True,timeout=600)

if __name__=='__main__':
    main()
