#!/usr/bin/env python3
"""Remote pack/component startup from provider-verified immutable Forge1122 inputs."""
import argparse,hashlib,importlib.util,json,os,shutil,subprocess,sys,zipfile
from pathlib import Path
from types import SimpleNamespace
ROOT=Path(__file__).resolve().parents[1]
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path);result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--mode',choices=['pack'],default='pack');args=parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote only; root owns dispatch')
    provider=module('native_provider',ROOT/'scripts/build-forge1122-native.py')
    pin=json.loads((ROOT/'native-builds/forge1122-component/provider-pins.json').read_text())
    report=ROOT/'build/forge1122-component-report';report.mkdir(parents=True,exist_ok=False)
    work=ROOT/'build/forge1122-component-inputs';work.mkdir(exist_ok=False)
    base=provider.retained(pin['base'],work/'base',report,'base')
    engine_root=provider.retained(pin['engine'],work/'engine',report,'engine')
    native_root=provider.retained(pin['native'],work/'native',report,'native')
    specs=list(base.rglob('closure-input.json'))
    if len(specs)!=1:raise ValueError('One retained full closure required')
    spec=json.loads(specs[0].read_text());artifacts={}
    for record in spec['artifacts']:
        path=specs[0].parent/(record['role']+'.jar')
        if provider.sha(path)!=record['sha256']:raise ValueError('Retained closure bytes differ: '+record['role'])
        artifacts[record['role']]={**record,'path':str(path)}
    changed_specs=list(engine_root.rglob('closure-input.json'))
    if len(changed_specs)!=1:raise ValueError('One effective engine closure required')
    changed=json.loads(changed_specs[0].read_text());engine=next(r for r in changed['artifacts'] if r['role']=='engine');engine_path=changed_specs[0].parent/'engine.jar'
    if provider.sha(engine_path)!=pin['engineJarSha256'] or engine['sha256']!=pin['engineJarSha256']:raise ValueError('Exact effective engine differs')
    artifacts['engine'].update(path=str(engine_path),sha256=pin['engineJarSha256'])
    jars=list(native_root.rglob('jar/openallay-forge1122-native.jar'));receipts=list(native_root.rglob('native-build-receipt.json'));results=list(native_root.rglob('RESULT.json'))
    if len(jars)!=1 or len(receipts)!=1 or len(results)!=1:raise ValueError('One normal current native output/build receipt required')
    native=json.loads(receipts[0].read_text());native_result=json.loads(results[0].read_text())
    if (provider.sha(jars[0])!=pin['nativeJarSha256'] or native['jarSha256']!=pin['nativeJarSha256']
            or native_result['sourceRevision']!=pin['nativeCompiledSource'] or native_result['status']!='passed'
            or native_result['nativeCompiled'] is not True or native_result['engineRebuilt'] is not False
            or (native['minecraft'],native['forge'],native['nativeRelease'])!=('1.12.2','14.23.5.2864',17)):
        raise ValueError('Exact current native source/remap custody differs')
    provider.write(report/'custody.json',{'providers':pin,'nativeReceipt':native,'closure':list(artifacts.values()),'oldNativeScopeOnly':False,'currentSourceRestamped':False})
    stock=module('component_stock_runner',ROOT/'scripts/forge1122-runtime-prerequisite/stock-forge1122-prerequisite.py')
    runtime,launch,freeze=stock.load_helpers(ROOT)
    install=json.loads((stock.PACKET/'install_profile.json').read_text());version=json.loads((stock.PACKET/'version.json').read_text());vanilla=json.loads((stock.PACKET/'minecraft-1.12.2.json').read_text());expected=stock.validate_metadata(install,version,vanilla)
    runargs=SimpleNamespace(repo=ROOT,java=Path(os.environ['OPENALLAY_COMPONENT_JAVA17_HOME'])/'bin/java',java_release='17.0.18+8',minecraft_root=ROOT/'build/e2e/runtime/forge1122-stock/minecraft',output=ROOT/'build/e2e/forge1122-component',title_only=True,pack200_bridge=True,launchwrapper_bridge=True,objectholder_bridge=True,objectholder_phase_diagnostic=False,component_inputs=None)
    runtime_root,java,assets=stock.prepare(runargs,runtime,launch,freeze,install,version,vanilla)
    selected=['engine','sdk','rhino','commonmark','tables','jtokkit','sqlite','jsr305','checkerqual','errorprone','j2objc']
    staging=work/'feature-staging';staging.mkdir();inventory=[];owned={};services={}
    # Whole immutable feature archives only; original class bytes copied unchanged.
    inputs=[{'role':'native','path':str(jars[0]),'sha256':pin['nativeJarSha256']}]+[artifacts[r] for r in selected]
    for record in inputs:
        with zipfile.ZipFile(record['path']) as archive:
            for entry in archive.infolist():
                name=entry.filename
                if entry.is_dir():continue
                if name.startswith('/') or any(p in ('','..','.') for p in name.split('/')):raise ValueError('Unsafe feature entry')
                data=archive.read(entry);digest=hashlib.sha256(data).hexdigest();destination=name;disposition='copied'
                if name=='META-INF/MANIFEST.MF' or name=='mcmod.info' or name=='META-INF/mods.toml' or name=='module-info.class' or name.startswith('META-INF/versions/') and name.endswith('/module-info.class') or name.endswith(('.SF','.RSA','.DSA')):disposition='excluded-original-container-metadata'
                elif name.startswith('META-INF/services/'):
                    services.setdefault(name,[]).extend(data.decode().splitlines());disposition='merged-real-service-declarations'
                elif not name.endswith('.class') and ('LICENSE' in Path(name).name.upper() or 'NOTICE' in Path(name).name.upper()):
                    destination='META-INF/licenses/'+record['role']+'/'+name.replace('/','_')
                if disposition=='copied':
                    if destination in owned:
                        if owned[destination]['sha256']!=digest:raise ValueError('Conflicting whole archive ownership: '+destination)
                        disposition='identical-byte-duplicate'
                    else:
                        target=staging/destination;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data);owned[destination]={'owner':record['role'],'sha256':digest}
                inventory.append({'owner':record['role'],'input':name,'output':destination,'sha256':digest,'disposition':disposition})
    for name,lines in services.items():
        path=staging/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text('\n'.join(dict.fromkeys(l for l in lines if l.strip() and not l.startswith('#')))+'\n');owned[name]={'owner':'merged-services','sha256':provider.sha(path)}
    provider.write(report/'entry-ownership.json',{'inventory':inventory,'outputEntries':owned,'inputArchives':inputs,'classesAltered':False})
    def bound(path):return {'path':str(path),'sha256':provider.sha(path)}
    libraries=runtime_root/'libraries'
    request={'forge':bound(libraries/'net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864.jar'),'launchwrapper':bound(libraries/'net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar'),'stockAsm':bound(libraries/'org/ow2/asm/asm-debug-all/5.2/asm-debug-all-5.2.jar'),'engine':bound(engine_path),'native':bound(jars[0]),'sdk':bound(Path(artifacts['sdk']['path'])),'rhino':bound(Path(artifacts['rhino']['path'])),'featureDependencies':[bound(Path(artifacts[r]['path'])) for r in selected if r not in ('engine','sdk','rhino')],'output':str(ROOT/'build/forge1122-component-product'),'java17':str(java),'featureStaging':str(staging)}
    req=work/'component-inputs.json';provider.write(req,request)
    env=dict(os.environ);env['JAVA_HOME']=env['OPENALLAY_COMPONENT_JAVA21_HOME'];env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
    with (report/'component-pack.log').open('w') as log:
        result=subprocess.run([str(ROOT/'gradlew'),'--max-workers=2','--stacktrace','-p',str(ROOT/'native-builds/forge1122-component'),'-PcomponentInputs='+str(req),'componentCensus'],env=env,stdout=log,stderr=subprocess.STDOUT)
    if result.returncode:raise SystemExit(result.returncode)
    product=Path(request['output']);inputs_json=json.loads((product/'component-inputs.json').read_text());records=inputs_json['artifacts'];byname={Path(r['path']).name:Path(r['path']) for r in records}
    with zipfile.ZipFile(byname['openallay-feature-core.jar']) as archive:
        for name,record in owned.items():
            if hashlib.sha256(archive.read(name)).hexdigest()!=record['sha256']:raise ValueError('Packed retained bytes changed: '+name)
    with zipfile.ZipFile(byname['openallay-private-mixin.jar']) as archive:
        if any(n.startswith('org/objectweb/asm/') for n in archive.namelist()):raise ValueError('Stock ASM namespace replacement forbidden')
        if 'dev/openallay/internal/forge1122/asm/ClassReader.class' not in archive.namelist():raise ValueError('Real private ASM reader missing')
    shutil.copyfile(artifacts['builder']['path'],product/'builder-retained.jar')
    provider.write(product/'builder-provenance.json',{'coordinate':artifacts['builder']['coordinate'],'sha256':artifacts['builder']['sha256'],'nativeTarget':8,'installedForComponentProbe':False})
    provider.write(report/'RESULT.json',{'status':'packed','mode':args.mode,'nativeCompiledSource':pin['nativeCompiledSource'],'nativeProvider':pin['native'],'engineProvider':pin['engine'],'engineRebuilt':False,'nativeRebuilt':False,'stockStartupReplayed':False,'product':inputs_json})
if __name__=='__main__':main()
