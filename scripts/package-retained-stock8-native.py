#!/usr/bin/env python3
"""Reuse one normal native Java8 AP/reobf artifact; rebuild current engine runtime graph only."""
import argparse,hashlib,importlib.util,json,os,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path);r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r);return r
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def ref(path):return {'path':str(Path(path).resolve()),'sha256':sha(path)}
def one(root,name):
    paths=list(root.rglob(name))
    if len(paths)!=1:raise ValueError('One retained '+name+' required')
    return paths[0]
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--native-pin',type=Path,required=True);args=parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote package reuse only')
    transport=module('native_transport',ROOT/'scripts/build-forge1122-native.py');pin=json.loads(args.native_pin.read_text())
    if set(pin)!={'provider','nativeJarSha256','nativeSource'} or pin['nativeSource']!=pin['provider']['sourceRevision']:raise ValueError('Exact normal native provider/source pin required')
    reports=ROOT/'build/forge1122-stock8-package-reuse';reports.mkdir(parents=True,exist_ok=False)
    work=Path(os.environ['RUNNER_TEMP'])/'forge1122-stock8-package-reuse';work.mkdir(exist_ok=False)
    retained=transport.retained(pin['provider'],work/'retained-native',reports,'retained-native')
    native=one(retained,'openallay-forge1122-native.jar');original_receipt=one(retained,'native-build-receipt.json');receipt=json.loads(original_receipt.read_text())
    if sha(native)!=pin['nativeJarSha256'] or receipt['jarSha256']!=pin['nativeJarSha256'] or receipt['nativeRelease']!=8:raise ValueError('Actual native Java8 artifact receipt differs')
    selection_path=one(retained,'source-selection.json');selection=json.loads(selection_path.read_text());namespace_path=one(retained,'namespace-receipt.json');namespace=json.loads(namespace_path.read_text())
    if sha(namespace_path)!=receipt['namespaceReceiptSha256']:raise ValueError('Native namespace receipt differs')
    source_pairs={}
    for record in selection['java']+selection['resources']:
        absolute=record['path'];relative=absolute.split('/OpenAllay/OpenAllay/',1)[1] if '/OpenAllay/OpenAllay/' in absolute else None
        if not relative:raise ValueError('Unbound retained canonical source path')
        original=subprocess.check_output(['git','show',pin['nativeSource']+':'+relative],cwd=ROOT)
        if hashlib.sha256(original).hexdigest()!=record['sha256'] or sha(ROOT/relative)!=record['sha256']:raise ValueError('Native selected source changed; recompile required: '+relative)
        source_pairs[relative]=record['sha256']
    # Preserve normal namespace/AP/refmap/reobf outputs exactly; relocation only changes local evidence file path.
    local_native=work/'native';local_native.mkdir()
    transport.write(local_native/'native-build-receipt.json',{**receipt,'jar':str(native)})
    engine_metadata=work/'current-engine-classpath.json'
    env=dict(os.environ);env['JAVA_HOME']=env['JAVA_HOME_25_X64'];env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
    commands=[str(ROOT/'gradlew'),'--no-daemon','--max-workers=2',':engine-core:jar',':runtime-rhino:jar',':engine-core:exportCanonicalVarCompileClasspath','-PcanonicalVarClasspathOutput='+str(engine_metadata),'--init-script',str(ROOT/'scripts/stock8-runtime-inputs.gradle'),'stock8RuntimeInputs','-Pstock8RuntimeInputsOutput='+str(local_native/'normal-runtime-dependency-inputs.json'),'--stacktrace']
    with (reports/'current-engine-producer.log').open('w') as log:result=subprocess.run(commands,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=1800)
    if result.returncode:raise SystemExit(result.returncode)
    metadata=json.loads(engine_metadata.read_text());metadata['sourceRevision']=os.environ['GITHUB_SHA'];transport.write(engine_metadata,metadata)
    engine_jars=[p for p in (ROOT/'engine-core/build/libs').glob('*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar'))]
    if len(engine_jars)!=1 or sha(engine_jars[0])!=receipt['engineSha256']:raise ValueError('Native compile-input engine byte identity changed; native reuse not accepted')
    normal_engine_receipt=work/'current-engine8-receipt.json'
    subprocess.run(['python3','-B',str(ROOT/'scripts/verify-normal-engine-java8-artifact.py'),'--project',str(ROOT),'--classpath-metadata',str(engine_metadata),'--output',str(normal_engine_receipt)],check=True)
    previous=one(retained,'ordinary-stock8-package-inputs.json');oldrequest=json.loads(previous.read_text())
    sdk_record=next(r for r in oldrequest['components'] if r['role']=='sdk')
    current_sdk=[Path(p) for p in metadata['runtimeClasspath'] if '/extension-api/build/libs/' in p]
    if len(current_sdk)!=1 or sha(current_sdk[0])!=receipt['sdkSha256']:raise ValueError('Native SDK compile-input hash differs')
    closure=work/'compile-closure.json';transport.write(closure,[{'role':'sdk','path':str(current_sdk[0]),'sha256':sdk_record['sha256']}])
    request=work/'package-inputs.json'
    subprocess.run(['python3','-B',str(ROOT/'scripts/classify-stock-forge1122-java8-runtime.py'),'--metadata',str(engine_metadata),'--engine-receipt',str(normal_engine_receipt),'--native-root',str(local_native),'--closure',str(closure),'--output',str(request)],check=True)
    out=ROOT/'build/forge1122-stock8-product';out.mkdir(exist_ok=False)
    subprocess.run(['python3','-B',str(ROOT/'scripts/package-stock-forge1122-java8.py'),'--inputs',str(request),'--output',str(out/'openallay-forge-1.12.2-0.4.4.jar'),'--receipt',str(out/'package-custody.json')],check=True)
    fixture_classes=work/'sqlite-fixture-classes';fixture_classes.mkdir()
    java8=Path(os.environ['JAVA_HOME_8_X64'])/'bin'
    fixture_source=ROOT/'scripts/fixtures/SqliteGameRuntimeJava8Fixture.java'
    product=out/'openallay-forge-1.12.2-0.4.4.jar'
    with (reports/'actual-product-sqlite-java8.log').open('w') as log:
        compiled=subprocess.run([str(java8/'javac'),'-source','8','-target','8','-encoding','UTF-8','-d',str(fixture_classes),
            str(fixture_source)],stdout=log,stderr=subprocess.STDOUT,timeout=120)
        if compiled.returncode:raise SystemExit(compiled.returncode)
        sqlrun=subprocess.run([str(java8/'java'),'-cp',str(fixture_classes)+os.pathsep+str(product),
            'SqliteGameRuntimeJava8Fixture',str(work/'sqlite-proof/database.sqlite')],stdout=log,stderr=subprocess.STDOUT,timeout=120)
        if sqlrun.returncode:raise SystemExit(sqlrun.returncode)
    transport.write(out/'sqlite-final-product-java8-proof.json',{'productSha256':sha(product),'sourceSha256':sha(fixture_source),
        'javac':str(java8/'javac'),'java':str(java8/'java'),'databaseExistingProfile':False,'compileExitCode':compiled.returncode,
        'runtimeExitCode':sqlrun.returncode,'logSha256':sha(reports/'actual-product-sqlite-java8.log'),'upstreamSqliteRebuilt':False})
    transport.write(out/'native-producer-custody.json',{'provider':pin,'originalNativeReceipt':receipt,'originalNativeReceiptSha256':sha(original_receipt),'namespaceReceiptSha256':sha(namespace_path),'selectedSourceHashes':source_pairs,'nativeProducerSource':pin['nativeSource'],'packingSource':os.environ['GITHUB_SHA'],'currentEngineSha256':sha(engine_jars[0]),'nativeRecompiled':False,'fullEngineTestsReexecuted':False,'gameExecuted':False})
if __name__=='__main__':main()
