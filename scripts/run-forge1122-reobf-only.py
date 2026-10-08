#!/usr/bin/env python3
"""Remote-only normal FG3 reobfuscation of the accepted, unmodified native Java17 jar."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
PIN={'artifactId':11443627635,'runId':37529222133,
     'sourceRevision':'8e4d03b51764e7a4dfee9e37e5c9d3c7c95e5a08',
     'sha256':'54e02e4affc6a6e7230ef3a8fa5a30a20d55c1d1cde0789b9a0812eaddfa10ae'}
RAW_SHA='bfbec7221cab50aa18ffbbda59fc52a33e469b2cb0f5d0e8aea703411c03d0ae'
MAP_SHA='6657cd372063d1f1347012139dce69da14d98b11c25a5e387e570fa4ea092c81'

def load(name,file):
    spec=importlib.util.spec_from_file_location(name,file)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module

def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote runner only')
    reports=ROOT/'build/forge1122-reobf-report';reports.mkdir(parents=True,exist_ok=False)
    work=Path(os.environ['RUNNER_TEMP'])/'forge1122-reobf-inputs';work.mkdir(exist_ok=False)
    native=load('native_driver',ROOT/'scripts/build-forge1122-native.py')
    tooling=load('census_runner',ROOT/'scripts/run-forge1122-native-census.py')
    retained=native.retained(PIN,work/'accepted-native',reports,'accepted-native')
    jars=list(retained.rglob('openallay-forge1122-native.jar'))
    if len(jars)!=1 or native.sha(jars[0])!=RAW_SHA:raise ValueError('Exact accepted native raw jar required')
    raw=jars[0]
    with zipfile.ZipFile(raw) as z:
        if z.testzip():raise ValueError('Accepted native jar CRC differs')
        classes=[n for n in z.namelist() if n.endswith('.class')]
        if len(classes)!=402 or any(int.from_bytes(z.read(n)[6:8],'big')!=61 for n in classes):
            raise ValueError('Accepted native Java17 class set differs')
    namespace=list(retained.rglob('namespace-receipt.json'))
    mixins=list(retained.rglob('mixins.srg'))
    if len(namespace)!=1 or len(mixins)!=1:raise ValueError('Exact namespace/AP evidence required')
    # The preserved raw jar comes from the failed reobf task's immutable input, not a new source build.
    native.write(reports/'accepted-stage-custody.json',{'provider':PIN,'rawSha256':RAW_SHA,
        'rawClasses':402,'major':61,'namespaceSha256':native.sha(namespace[0]),
        'mixinSrgSha256':native.sha(mixins[0]),'originalOverallStatus':'FAILED',
        'acceptedStages':['namespace','native-javac17','normal-MixinAP','raw-native-packaging'],
        'applicationCompileReplayed':False})
    mdkpin=json.loads((ROOT/'native-builds/forge1122-census/public-input-lock.json').read_text())
    mdk=tooling.download_pin('mdk',mdkpin['mdk'],work,reports)
    with zipfile.ZipFile(mdk) as z:
        for name in ('gradlew','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties'):
            target=work/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(name))
    wrapper=work/'gradle/wrapper/gradle-wrapper.properties'
    wrapper.write_text(wrapper.read_text()+'\ndistributionSha256Sum='+mdkpin['gradle_checksum']['sha256Text']+'\n')
    (work/'gradlew').chmod(0o755)
    request={'rawJar':str(raw),'rawJarSha256':RAW_SHA,'mixinSrg':str(mixins[0]),
        'mixinSrgSha256':native.sha(mixins[0]),'mcpToSrgSha256':MAP_SHA,
        'nativeSourceRevision':PIN['sourceRevision'],'output':str(Path(os.environ['RUNNER_TEMP'])/'forge1122-reobf')}
    native.write(work/'request.json',request);native.write(reports/'request.json',request)
    command=[str(work/'gradlew'),'--no-daemon','--max-workers=2','-p',str(ROOT/'native-builds/forge1122-reobf'),
        '-PreobfOnlyRequest='+str(work/'request.json'),'reobfJar','--full-stacktrace']
    env=dict(os.environ);env['JAVA_HOME']=env['JAVA_HOME_8_X64'];env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
    with (reports/'gradle.log').open('w') as log:
        result=subprocess.run(command,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=900)
    native.write(reports/'RESULT.json',{'command':command,'exitCode':result.returncode,
        'applicationCompileReplayed':False,'namespaceReplayed':False,'gameLaunch':False})
    print((reports/'gradle.log').read_text()[-16000:])
    if native.sha(raw)!=RAW_SHA:raise ValueError('Retained raw native input changed')
    if result.returncode:raise SystemExit(result.returncode)
    output=Path(request['output'])/'openallay-forge1122-native-reobf.jar'
    with zipfile.ZipFile(output) as z:
        outputclasses=[n for n in z.namelist() if n.endswith('.class')]
        if len(outputclasses)!=402 or any(int.from_bytes(z.read(n)[6:8],'big')!=61 for n in outputclasses):
            raise ValueError('Reobf changed native Java17 class count/version')
    native.write(reports/'accepted-reobf.json',{'beforeSha256':RAW_SHA,'afterSha256':native.sha(output),
        'nativeClasses':402,'nativeMajor':61,'normalFg3Task':'reobfJar','applicationCompileReplayed':False})

if __name__=='__main__':main()
