#!/usr/bin/env python3
"""One remote exact selected native Java8 compiler diagnostic, no application/runtime packaging."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]
PIN={'artifactId':11406765949,'runId':37450040748,
    'sourceRevision':'7819dbea0601f1ae7ef8b9c03286b6f8a0589bb4',
    'sha256':'6d1ac947f59d983ea1b88db15d456cec3b7ddffd2b8f26320836a5b04de83802'}

def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote diagnostic only')
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--closure-pin',type=Path)
    parser.add_argument('--language-candidates',action='store_true')
    a=parser.parse_args()
    spec=importlib.util.spec_from_file_location('native_transport',ROOT/'scripts/build-forge1122-native.py')
    transport=importlib.util.module_from_spec(spec);spec.loader.exec_module(transport)
    reports=ROOT/'build/forge1122-java8-diagnostic';reports.mkdir(parents=True,exist_ok=False)
    work=Path(os.environ['RUNNER_TEMP'])/'forge1122-java8-diagnostic-inputs';work.mkdir(exist_ok=False)
    # Historical major61 closure is explicitly only a real modern-compiler type oracle.
    if a.closure_pin is None and (ROOT/'native-builds/forge1122-census/changed-engine-input.json').is_file():
        a.closure_pin=ROOT/'native-builds/forge1122-census/changed-engine-input.json'
    pin=json.loads(a.closure_pin.read_text()) if a.closure_pin else PIN
    retained=transport.retained(pin,work/'type-oracle',reports,'type-oracle')
    paths=list(retained.rglob('closure-input.json'))
    if len(paths)!=1:raise ValueError('One exact actual closure manifest required')
    record=json.loads(paths[0].read_text());compile_roles={'engine','sdk','rhino','commonmark','tables','jtokkit'}
    closure=[]
    for artifact in record['artifacts']:
        path=paths[0].parent/(artifact['role']+'.jar')
        if transport.sha(path)!=artifact['sha256']:raise ValueError('Actual type oracle constituent hash differs')
        closure.append({'role':artifact['role'],'path':str(path),'sha256':artifact['sha256'],'compile':artifact['role'] in compile_roles})
    tool=os.environ['JAVA_HOME_17_X64']
    request={'canonicalSourceRoot':str(ROOT),'javac17':tool+'/bin/javac','java17':tool+'/bin/java',
        'closure':closure,'actualMcpUnits':transport.actual_units(),
        'output':str(Path(os.environ['RUNNER_TEMP'])/'forge1122-java8-native')}
    transport.write(work/'request.json',request)
    transport.write(reports/'input-custody.json',{'sourceRevision':os.environ['GITHUB_SHA'],'typeOracleProvider':pin,
        'runtimeClosureAccepted':False,'packagedProduct':False,'engineJava8Acceptance':False})
    command=['python3','-B',str(ROOT/'scripts/run-forge1122-native-census.py'),
        '--workspace',str(work/'tool'),'--output',str(Path(os.environ['RUNNER_TEMP'])/'forge1122-java8-tooling'),
        '--javap',tool+'/bin/javap','--native-build-request',str(work/'request.json'),('--language-candidates' if a.language_candidates else '--java8-diagnostic')]
    env=dict(os.environ);env['JAVA_HOME']=env['JAVA_HOME_8_X64'];env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
    with (reports/'driver.log').open('w') as log:result=subprocess.run(command,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=3300)
    transport.write(reports/'RESULT.json',{'command':command,'exitCode':result.returncode,
        'scope':'actual whole native source Java8 syntax/API compiler frontier','runtimeAcceptance':False,'gameLaunch':False})
    print((reports/'driver.log').read_text()[-16000:])
    raise SystemExit(result.returncode)

if __name__=='__main__':main()
