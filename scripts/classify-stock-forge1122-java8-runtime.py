#!/usr/bin/env python3
"""Classify the actual current normal engine graph for one stock Java8 flat package."""
import argparse,hashlib,importlib.util,json,os,re,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def ref(path):return {'path':str(Path(path).resolve()),'sha256':sha(path)}
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--metadata',type=Path,required=True);parser.add_argument('--engine-receipt',type=Path,required=True);parser.add_argument('--native-root',type=Path,required=True);parser.add_argument('--closure',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote normal package classification only')
    metadata=json.loads(args.metadata.read_text());engine_receipt=json.loads(args.engine_receipt.read_text());source=os.environ['GITHUB_SHA']
    if engine_receipt['engineSha256']!=sha(next((ROOT/'engine-core/build/libs').glob('openallay-engine-core-*.jar'))):raise ValueError('Exact actual normal engine producer receipt differs')
    engines=list((ROOT/'engine-core/build/libs').glob('openallay-engine-core-*.jar'));engines=[p for p in engines if not p.name.endswith(('-sources.jar','-javadoc.jar'))]
    if len(engines)!=1:raise ValueError('One normal complete current engine JAR required')
    engine=engines[0]
    with zipfile.ZipFile(engine) as z:engine_entries={e.filename:z.read(e) for e in z.infolist() if not e.is_dir()}
    components=[{'role':'engine','coordinate':'dev.openallay:openallay-engine-core:0.4.4',**ref(engine)}];classified=[];roles={'engine'}
    known={('org.commonmark','commonmark'):'commonmark',('org.commonmark','commonmark-ext-gfm-tables'):'tables',('com.knuddels','jtokkit'):'jtokkit',('org.xerial','sqlite-jdbc'):'sqlite',('com.google.code.findbugs','jsr305'):'jsr305',('org.checkerframework','checker-qual'):'checkerqual',('com.google.errorprone','error_prone_annotations'):'errorprone',('com.google.j2objc','j2objc-annotations'):'j2objc',('org.slf4j','slf4j-api'):'slf4j'}
    hosts={('com.google.code.gson','gson'),('com.google.guava','guava'),('com.google.guava','failureaccess'),('com.google.guava','listenablefuture')}
    for raw in metadata['runtimeClasspath']:
        path=Path(raw)
        if path.is_dir():
            checks=[]
            for file in path.rglob('*'):
                if not file.is_file():continue
                name=file.relative_to(path).as_posix();content=file.read_bytes()
                if engine_entries.get(name)!=content:raise ValueError('Normal runtime project output not merged into engine: '+name)
                checks.append({'entry':name,'sha256':hashlib.sha256(content).hexdigest()})
            classified.append({'path':raw,'disposition':'whole-current-project-output-byte-owned-by-engine','entries':checks});continue
        if not path.is_file() or path.suffix!='.jar':raise ValueError('Unknown actual runtime input: '+raw)
        match=re.search(r'/files-2.1/([^/]+)/([^/]+)/([^/]+)/',raw)
        if match:
            group,artifact,version=match.groups();identity=(group,artifact);coordinate=group+':'+artifact+':'+version
            if identity in hosts:
                classified.append({**ref(path),'coordinate':coordinate,'disposition':'actual-host-namespace-input-not-packed','actualHostMatchRequiresRuntime':True});continue
            role=known.get(identity)
            if role is None:raise ValueError('Unclassified actual engine runtime coordinate: '+coordinate)
        else:
            if '/runtime-rhino/build/libs/' in raw:role='rhino';coordinate='dev.openallay:openallay-rhino:2101.2.8-build.91'
            elif '/runtime-commonmark/build/libs/' in raw:role='commonmark';coordinate='dev.openallay:openallay-commonmark:0.28.0'
            elif '/extension-api/build/libs/' in raw:role='sdk';coordinate='dev.openallay:openallay-extension-api:0.4.0'
            elif '/runtime-maven/' in raw or '/runtime-json/' in raw:
                with zipfile.ZipFile(path) as z:
                    for name in z.namelist():
                        if name.endswith('.class') and engine_entries.get(name)!=z.read(name):raise ValueError('Constituent proof class not whole-engine-owned')
                classified.append({**ref(path),'disposition':'constituent-proof-already-byte-owned-engine'});continue
            else:raise ValueError('Unknown actual project runtime JAR owner: '+raw)
        if role in roles:raise ValueError('Competing runtime owner '+role)
        roles.add(role);components.append({'role':role,'coordinate':coordinate,**ref(path)});classified.append({**ref(path),'coordinate':coordinate,'role':role,'disposition':'copied-whole-current-artifact'})
    closure=json.loads(args.closure.read_text());byrole={r['role']:r for r in closure}
    if 'sdk' not in roles:
        sdk=Path(byrole['sdk']['path']);components.append({'role':'sdk','coordinate':'dev.openallay:openallay-extension-api:0.4.0',**ref(sdk)});classified.append({**ref(sdk),'role':'sdk','disposition':'exact-public-SDK-from-normal-native-compile-closure'});roles.add('sdk')
    if 'rhino' not in roles:raise ValueError('Current normal runtime Rhino absent from graph')
    native_receipts=list(args.native_root.rglob('native-build-receipt.json'))
    if len(native_receipts)!=1:raise ValueError('One successful normal native compiler/AP/reobf receipt required')
    native_receipt=json.loads(native_receipts[0].read_text());native=Path(native_receipt['jar'])
    if native_receipt.get('nativeRelease')!=8 or native_receipt['jarSha256']!=sha(native):raise ValueError('Normal native Java8 JAR/AP/reobf gate not passed')
    # The genuine runtime artifact is resolved separately from the fat annotation processor.
    inputs=list(args.native_root.rglob('normal-runtime-dependency-inputs.json'))
    if len(inputs)!=1:raise ValueError('Actual normal unclassified Mixin runtime dependency receipt required')
    runtime_records=json.loads(inputs[0].read_text());mixin_rows=[r for r in runtime_records if r['coordinate']=='org.spongepowered:mixin:0.8.5' and r.get('classifier')=='']
    if len(mixin_rows)!=1:raise ValueError('One actual genuine Mixin0.8.5 input required')
    row=mixin_rows[0];mixin=Path(row['path'])
    if sha(mixin)!=row['sha256']:raise ValueError('Actual normal Mixin bytes differ')
    with zipfile.ZipFile(mixin) as z:
        if 'org/spongepowered/asm/launch/MixinTweaker.class' not in z.namelist():raise ValueError('Actual Mixin dependency does not carry genuine runtime bootstrap')
    components.append({'role':'mixin','coordinate':'org.spongepowered:mixin:0.8.5'+(':'+row['classifier'] if row.get('classifier') else ''),**ref(mixin)})
    classified.append({**ref(mixin),'coordinate':components[-1]['coordinate'],'disposition':'whole-genuine-resolved-Mixin-runtime-owner'})
    builderlock=ROOT/'distribution/extensions.lock.json'
    candidate_pin=json.loads((ROOT/'distribution/builder-candidate-provider.json').read_text())
    spec=importlib.util.spec_from_file_location('native_transport',ROOT/'scripts/build-forge1122-native.py');transport=importlib.util.module_from_spec(spec);spec.loader.exec_module(transport)
    retained=transport.retained(candidate_pin['provider'],args.output.parent/'current-builder-candidate',args.output.parent,'current-builder-candidate')
    builder_matches=[file for file in retained.rglob('*.jar') if sha(file)==candidate_pin['jarSha256']]
    if len(builder_matches)!=1:raise ValueError('One exact current independently released Builder candidate required')
    builder=builder_matches[0]
    lock=json.loads(builderlock.read_text())
    if lock['source']['revision']!=candidate_pin['extensionSource']:raise ValueError('Current Builder lock/provider source differs')
    classified.append({**ref(builder),'disposition':'normal-bundled-Builder-current-independent-provider','provider':candidate_pin})
    if engine_receipt['sourceRevision']!=source:raise ValueError('Original same-job engine receipt source differs; no restamp')
    bound=args.engine_receipt
    request={'sourceRoot':str(ROOT),'sourceRevision':source,'version':'0.4.4','native':ref(native),'nativeReceipt':ref(native_receipts[0]),'engineReceipt':ref(bound),'engineSource':source,'components':components,'builder':ref(builder),'builderLock':ref(builderlock)}
    args.output.write_text(json.dumps(request,indent=2)+'\n');(args.output.parent/'actual-runtime-classification.json').write_text(json.dumps({'sourceRevision':source,'actualRuntimeClasspath':metadata['runtimeClasspath'],'classified':classified,'notCopiedHostNamespaces':sorted(':'.join(x) for x in hosts),'gameAccepted':False,'physicalMRCheck':'whole flat pack scan, no functional entry omission'},indent=2)+'\n')
if __name__=='__main__':main()
