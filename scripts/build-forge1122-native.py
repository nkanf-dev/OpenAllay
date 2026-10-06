#!/usr/bin/env python3
"""Remote-only first Forge1122 native application compile from retained components."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
BASE_PIN={'artifactId':11406765949,'runId':37450040748,
    'sourceRevision':'7819dbea0601f1ae7ef8b9c03286b6f8a0589bb4',
    'sha256':'6d1ac947f59d983ea1b88db15d456cec3b7ddffd2b8f26320836a5b04de83802'}

def sha(path):
    d=hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda:source.read(65536),b''):d.update(block)
    return d.hexdigest()

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n')

def retained(pin,destination,reports,label):
    if set(pin)!={'artifactId','runId','sourceRevision','sha256'}:
        raise ValueError('Exact retained provider pin required')
    repo=os.environ['GITHUB_REPOSITORY']
    record=json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/actions/artifacts/{pin["artifactId"]}']))
    write(reports/(label+'-provider.json'),record)
    if (record['expired'] or record['workflow_run']['id']!=pin['runId'] or
            record['workflow_run']['head_sha']!=pin['sourceRevision'] or record['digest']!='sha256:'+pin['sha256']):
        raise ValueError('Retained provider identity differs: '+label)
    archive=destination.parent/(label+'.zip')
    with archive.open('xb') as stream:
        subprocess.run(['gh','api',f'repos/{repo}/actions/artifacts/{pin["artifactId"]}/zip'],stdout=stream,check=True)
    if sha(archive)!=pin['sha256']:raise ValueError('Retained archive digest differs: '+label)
    destination.mkdir()
    with zipfile.ZipFile(archive) as z:
        for info in z.infolist():
            path=destination/info.filename
            if not path.resolve().is_relative_to(destination.resolve()) or ((info.external_attr>>16)&0o170000)==0o120000:
                raise ValueError('Unsafe retained archive entry')
            if info.is_dir():path.mkdir(parents=True,exist_ok=True)
            else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(z.read(info))
    return destination

def actual_units():
    plan=json.loads((ROOT/'native-builds/forge16165/source-selection.json').read_text())
    families=plan['sourceFamilies']+['1.16.5','1.12.2']
    explicit=set(plan['actualMcpUnits'])
    units=[]
    for owner,module in [('common','common'),('adapter','adapters/minecraft'),('fml','neoforge')]:
        roots=[ROOT/module/'src/main/java']+[ROOT/module/f'src/targets/{version}/java' for version in families]
        if owner=='fml':roots += [ROOT/'forge'/f'src/targets/{version}/java' for version in families]
        selected={}
        for root in roots:
            if root.exists():
                for source in root.rglob('*.java'):selected[str(source.relative_to(root))]=source
        for path in selected.values():
            relative=path.relative_to(ROOT).as_posix()
            if '/src/targets/1.12.2/' in relative or relative in explicit:units.append(str(path.resolve()))
    retired=json.loads((ROOT/'native-builds/forge1122-census/source-retirements.json').read_text())
    retired_paths={str((ROOT/r['origin']).resolve()) for r in retired['java']}
    return sorted(set(units)-retired_paths)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--engine-pin',type=Path)
    args=parser.parse_args()
    if args.engine_pin is None and (ROOT/'native-builds/forge1122-census/changed-engine-input.json').is_file():
        args.engine_pin=ROOT/'native-builds/forge1122-census/changed-engine-input.json'
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote runner only')
    report=ROOT/'build/forge1122-native-report';report.mkdir(parents=True,exist_ok=False)
    work=Path(os.environ['RUNNER_TEMP'])/'forge1122-application-inputs';work.mkdir(exist_ok=False)
    result={'sourceRevision':os.environ['GITHUB_SHA'],'engineRebuilt':False,
        'sdkBuilderRebuilt':False,'gameExecuted':False,'status':'preparing'}
    write(report/'RESULT.json',result)
    try:
        components=retained(BASE_PIN,work/'base-closure',report,'base-closure')
        manifests=list(components.rglob('closure-input.json'))
        if len(manifests)!=1:raise ValueError('One retained closure-input.json required')
        original=manifests[0];spec=json.loads(original.read_text())
        if len(spec['artifacts'])!=18 or len({r['role'] for r in spec['artifacts']})!=18:
            raise ValueError('Exact18-role retained closure required')
        for record in spec['artifacts']:
            path=original.parent/(record['role']+'.jar')
            if sha(path)!=record['sha256']:raise ValueError('Retained constituent differs: '+record['role'])
            record['path']=str(path)
        if args.engine_pin:
            pin=json.loads(args.engine_pin.read_text())
            changed=retained(pin,work/'changed-engine',report,'changed-engine')
            changed_manifests=list(changed.rglob('closure-input.json'))
            if len(changed_manifests)!=1:raise ValueError('One changed-engine closure manifest required')
            changed_spec=json.loads(changed_manifests[0].read_text())
            # Only engine is replaced. Every independently retained component stays the original exact bytes.
            engine=next(r for r in changed_spec['artifacts'] if r['role']=='engine')
            jar=changed_manifests[0].parent/'engine.jar'
            if sha(jar)!=engine['sha256']:raise ValueError('Changed engine digest differs')
            original_engine=next(r for r in spec['artifacts'] if r['role']=='engine')
            original_engine.update(path=str(jar),sha256=engine['sha256'])
            result['engineProvider']=pin
        compile_roles={'engine','sdk','rhino','commonmark','tables','jtokkit'}
        closure=[{'role':r['role'],'path':r['path'],'sha256':r['sha256'],'compile':r['role'] in compile_roles}
            for r in spec['artifacts']]
        request={'canonicalSourceRoot':str(ROOT),'javac17':os.environ['OPENALLAY_NATIVE_JAVA17_HOME']+'/bin/javac',
            'java17':os.environ['OPENALLAY_NATIVE_JAVA17_HOME']+'/bin/java','closure':closure,
            'actualMcpUnits':actual_units(),'output':str(Path(os.environ['RUNNER_TEMP'])/'forge1122-native')}
        request_path=work/'request.json';write(request_path,request)
        write(report/'native-request.json',request);write(report/'retained-closure.json',closure)
        env=dict(os.environ);env['JAVA_HOME']=env['JAVA_HOME_8_X64'];env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
        cmd=['python3','-B',str(ROOT/'scripts/run-forge1122-native-census.py'),
            '--workspace',str(work/'tool'),'--output',str(Path(os.environ['RUNNER_TEMP'])/'forge1122-native-inputs'),
            '--javap',os.environ['OPENALLAY_NATIVE_JAVA17_HOME']+'/bin/javap','--native-build-request',str(request_path)]
        result['command']=cmd
        with (report/'driver.log').open('w') as log:
            proc=subprocess.run(cmd,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=3300)
        result.update(status='passed' if proc.returncode==0 else 'failed',exitCode=proc.returncode,
            nativeCompiled=proc.returncode==0)
        write(report/'RESULT.json',result)
        print((report/'driver.log').read_text()[-12000:])
        if proc.returncode:raise SystemExit(proc.returncode)
    except Exception as error:
        result.update(status='failed',errorType=type(error).__name__,error=str(error)[:4096])
        write(report/'RESULT.json',result)
        raise

if __name__=='__main__':main()
