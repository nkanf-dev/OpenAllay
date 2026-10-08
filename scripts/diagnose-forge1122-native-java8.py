#!/usr/bin/env python3
"""One remote exact selected native Java8 compiler diagnostic, no application/runtime packaging."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
PIN={'artifactId':11406765949,'runId':37450040748,
    'sourceRevision':'7819dbea0601f1ae7ef8b9c03286b6f8a0589bb4',
    'sha256':'6d1ac947f59d983ea1b88db15d456cec3b7ddffd2b8f26320836a5b04de83802'}

def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote diagnostic only')
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--closure-pin',type=Path)
    parser.add_argument('--language-candidates',action='store_true')
    parser.add_argument('--native-package-probe',action='store_true')
    parser.add_argument('--selected-owners',type=Path)
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
    # Current normal Gradle engine publication is the authentic external type oracle.
    # It is never labeled Java8 runtime output, even if all current source lowering is accepted.
    engine_env=dict(os.environ)
    engine_home=engine_env['JAVA_HOME_25_X64']
    engine_env['JAVA_HOME']=engine_home;engine_env['PATH']=engine_home+'/bin:'+engine_env['PATH']
    engine_metadata=work/'current-engine-classpath.json'
    engine_command=[str(ROOT/'gradlew'),'--no-daemon','--max-workers=2',':engine-core:jar',':runtime-rhino:jar',
        ':engine-core:exportCanonicalVarCompileClasspath',
        '-PcanonicalVarClasspathOutput='+str(engine_metadata),'--stacktrace']
    with (reports/'current-engine-producer.log').open('w') as log:
        engine_result=subprocess.run(engine_command,cwd=ROOT,env=engine_env,stdout=log,stderr=subprocess.STDOUT,timeout=1800)
    if engine_result.returncode:
        transport.write(reports/'current-engine-producer.json',{'command':engine_command,'exitCode':engine_result.returncode,
            'sourceRevision':os.environ['GITHUB_SHA'],'typeOracleOnly':True,'runtimeJava8Accepted':False})
        raise SystemExit(engine_result.returncode)
    jars=[p for p in (ROOT/'engine-core/build/libs').glob('*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar','-test-fixtures.jar'))]
    if len(jars)!=1:raise ValueError('One genuine current normal engine production JAR required')
    engine_jar=jars[0]
    with zipfile.ZipFile(engine_jar) as archive:
        for required in ['dev/openallay/value/ValueType.class','dev/openallay/value/ValueSchema.class',
            'dev/openallay/value/ValueSchema$Provider.class','dev/openallay/value/RecordMetadata.class','dev/openallay/value/ValueSchemas.class']:
            if required not in archive.namelist():raise ValueError('Genuine current engine type owner absent: '+required)
    major_hist={}
    with zipfile.ZipFile(engine_jar) as archive:
        for name in archive.namelist():
            if name.endswith('.class'):
                major=int.from_bytes(archive.read(name)[6:8],'big');major_hist[str(major)]=major_hist.get(str(major),0)+1
    metadata=json.loads(engine_metadata.read_text())
    expected_sources=sorted(str(p.resolve()) for p in (ROOT/'engine-core/src/main/java').rglob('*.java'))
    if metadata['sources']!=expected_sources or metadata['producer']!=':engine-core:compileJava':
        raise ValueError('Current complete engine producer/source metadata differs')
    engine_row=next(r for r in closure if r['role']=='engine')
    engine_row.update(path=str(engine_jar.resolve()),sha256=transport.sha(engine_jar))
    transport.write(reports/'current-engine-producer.json',{'command':engine_command,'exitCode':0,
        'sourceRevision':os.environ['GITHUB_SHA'],'engine':engine_row,'classMajors':major_hist,
        'metadataSha256':transport.sha(engine_metadata),'typeOracleOnly':True,'runtimeJava8Accepted':False,
        'completeCanonicalSourceCount':len(expected_sources)})
    rhino_jars=[p for p in (ROOT/'runtime-rhino/build/libs').glob('*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar'))]
    if len(rhino_jars)!=1:raise ValueError('One current ordinary Rhino production JAR required')
    rhino_jar=rhino_jars[0];rhino_majors={}
    with zipfile.ZipFile(rhino_jar) as archive:
        for name in archive.namelist():
            if name.endswith('.class'):
                major=int.from_bytes(archive.read(name)[6:8],'big');rhino_majors[str(major)]=rhino_majors.get(str(major),0)+1
                if major>52:raise ValueError('Current ordinary Rhino production archive exceeds Java8')
    if rhino_majors.get('52',0)==0:raise ValueError('Current actual Rhino class output absent')
    rhino_row=next(r for r in closure if r['role']=='rhino')
    rhino_row.update(path=str(rhino_jar.resolve()),sha256=transport.sha(rhino_jar))
    transport.write(reports/'current-rhino-producer.json',{'sourceRevision':os.environ['GITHUB_SHA'],
        'jarSha256':rhino_row['sha256'],'classMajors':rhino_majors,'ordinaryRuntimeArchive':True,
        'productionRelease':8,'fullTestSuiteReexecuted':False})
    # Compile inputs contain one actual engine and one actual Rhino class owner.
    actual_compile=[r for r in closure if r['compile']]
    class_owners={};competing=[]
    for r in actual_compile:
        with zipfile.ZipFile(r['path']) as archive:
            for name in archive.namelist():
                if not name.endswith('.class') or name.startswith('META-INF/versions/') or name=='module-info.class':continue
                previous=class_owners.setdefault(name,r['role'])
                if previous!=r['role']:competing.append({'entry':name,'firstRole':previous,'secondRole':r['role']})
    transport.write(reports/'current-type-oracle-class-owners.json',{'compileRoles':[r['role'] for r in actual_compile],
        'competingClassOwners':competing,'runtimeClosureAccepted':False})
    if competing:raise ValueError('Competing actual current engine/Rhino/compiler input owner')
    tool=os.environ['JAVA_HOME_17_X64']
    request={'canonicalSourceRoot':str(ROOT),'javac17':tool+'/bin/javac','java17':tool+'/bin/java',
        'closure':closure,'actualMcpUnits':transport.actual_units(),
        'output':str(Path(os.environ['RUNNER_TEMP'])/'forge1122-java8-native')}
    transport.write(work/'request.json',request)
    transport.write(reports/'input-custody.json',{'sourceRevision':os.environ['GITHUB_SHA'],'typeOracleProvider':pin,
        'runtimeClosureAccepted':False,'packagedProduct':False,'engineJava8Acceptance':False})
    command=['python3','-B',str(ROOT/'scripts/run-forge1122-native-census.py'),
        '--workspace',str(work/'tool'),'--output',str(Path(os.environ['RUNNER_TEMP'])/'forge1122-java8-tooling'),
        '--javap',tool+'/bin/javap','--native-build-request',str(work/'request.json'),('--language-candidates' if a.language_candidates else ('--native-package-probe' if a.native_package_probe else '--java8-diagnostic'))]
    if a.selected_owners:
        if not a.language_candidates:raise ValueError('Selected owner subset applies only to source language candidates')
        command.extend(['--selected-owners',str(a.selected_owners.resolve())])
    env=dict(os.environ);env['JAVA_HOME']=env['JAVA_HOME_8_X64'];env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
    with (reports/'driver.log').open('w') as log:result=subprocess.run(command,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=3300)
    transport.write(reports/'RESULT.json',{'command':command,'exitCode':result.returncode,
        'scope':'actual whole native source Java8 syntax/API compiler frontier','runtimeAcceptance':False,'gameLaunch':False})
    print((reports/'driver.log').read_text()[-16000:])
    raise SystemExit(result.returncode)

if __name__=='__main__':main()
