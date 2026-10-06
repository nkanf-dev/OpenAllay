#!/usr/bin/env python3
"""Rebuild only affected private Mixin and core bootstrap; retain all feature bytes and facade."""
import importlib.util,json,os,subprocess,zipfile,hashlib,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path);r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r);return r

def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote only')
    provider=module('native_provider',ROOT/'scripts/build-forge1122-native.py')
    pin=json.loads((ROOT/'native-builds/forge1122-component/accepted-product-provider.json').read_text())
    report=ROOT/'build/forge1122-component-report';report.mkdir(parents=True,exist_ok=False)
    work=ROOT/'build/forge1122-component-inputs';work.mkdir(exist_ok=False)
    kept=provider.retained(pin,work/'accepted-product',report,'accepted-product')
    manifest=next(kept.rglob('component-inputs.json'));product=manifest.parent;old=json.loads(manifest.read_text())
    for r in old['artifacts']:
        if provider.sha(product/Path(r['path']).name)!=r['sha256']:raise ValueError('Accepted component differs')
    core=product/'openallay-feature-core.jar';staging=work/'feature-staging';staging.mkdir();entries={}
    with zipfile.ZipFile(core) as z:
        for item in z.infolist():
            name=item.filename
            if item.is_dir() or name=='META-INF/MANIFEST.MF' or name.startswith('dev/openallay/forge1122/bootstrap/'):continue
            p=staging/name
            if not p.resolve().is_relative_to(staging.resolve()):raise ValueError('Unsafe accepted entry')
            p.parent.mkdir(parents=True,exist_ok=True);data=z.read(item);p.write_bytes(data);entries[name]=hashlib.sha256(data).hexdigest()
    original_custody=next(kept.rglob('custody.json'));custody=json.loads(original_custody.read_text());provider.write(report/'custody.json',custody)
    # Provider gate exact artifact constituent archives supply compile references; never feature rebuild.
    base=provider.retained(custody['providers']['base'],work/'base',report,'base')
    eng=provider.retained(custody['providers']['engine'],work/'engine',report,'engine')
    nat=provider.retained(custody['providers']['native'],work/'native',report,'native')
    spec=next(base.rglob('closure-input.json'));records=json.loads(spec.read_text())['artifacts'];byrole={r['role']:spec.parent/(r['role']+'.jar') for r in records}
    for r in records:
        if provider.sha(byrole[r['role']])!=r['sha256']:raise ValueError('Compile constituent differs')
    engine=next(eng.rglob('engine.jar'));native_candidates=list(nat.rglob('openallay-forge1122-native-reobf.jar'))+list(nat.rglob('openallay-forge1122-native.jar'))
    expected_native=custody['providers']['nativeJarSha256'];native=next(p for p in native_candidates if provider.sha(p)==expected_native)
    stock=module('stock',ROOT/'scripts/forge1122-runtime-prerequisite/stock-forge1122-prerequisite.py');runtime,launch,freeze=stock.load_helpers(ROOT)
    from types import SimpleNamespace
    run=SimpleNamespace(repo=ROOT,java=Path(os.environ['OPENALLAY_COMPONENT_JAVA17_HOME'])/'bin/java',java_release='17.0.18+8',minecraft_root=ROOT/'build/e2e/runtime/forge1122-stock/minecraft')
    install=json.loads((stock.PACKET/'install_profile.json').read_text());version=json.loads((stock.PACKET/'version.json').read_text());vanilla=json.loads((stock.PACKET/'minecraft-1.12.2.json').read_text())
    root,java,_=stock.prepare(run,runtime,launch,freeze,install,version,vanilla,download_assets=False)
    def bound(p):return {'path':str(p),'sha256':provider.sha(p)}
    lib=root/'libraries';out=ROOT/'build/forge1122-component-product'
    request={'forge':bound(lib/'net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864.jar'),'launchwrapper':bound(lib/'net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar'),'stockAsm':bound(lib/'org/ow2/asm/asm-debug-all/5.2/asm-debug-all-5.2.jar'),'engine':bound(engine),'native':bound(native),'sdk':bound(byrole['sdk']),'rhino':bound(byrole['rhino']),'featureDependencies':[],'output':str(out),'java17':str(java),'featureStaging':str(staging)}
    req=work/'request.json';provider.write(req,request)
    env=dict(os.environ);env['JAVA_HOME']=env['OPENALLAY_COMPONENT_JAVA21_HOME'];env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
    with (report/'private-mixin-repair.log').open('w') as log:
        proc=subprocess.run([str(ROOT/'gradlew'),'--max-workers=2','--stacktrace','-p',str(ROOT/'native-builds/forge1122-component'),'-PcomponentInputs='+str(req),'repairPrivateMixin'],env=env,stdout=log,stderr=subprocess.STDOUT)
    if proc.returncode:raise SystemExit(proc.returncode)
    shutil.copyfile(product/'openallay-lifecycle-facade.jar',out/'openallay-lifecycle-facade.jar')
    for name in ['builder-retained.jar','builder-provenance.json']:
        if (product/name).exists():shutil.copyfile(product/name,out/name)
    with zipfile.ZipFile(out/'openallay-feature-core.jar') as z:
        for name,digest in entries.items():
            if hashlib.sha256(z.read(name)).hexdigest()!=digest:raise ValueError('Retained feature entry changed: '+name)
    records=[bound(out/name) for name in ['openallay-feature-core.jar','openallay-lifecycle-facade.jar','openallay-private-mixin.jar']]
    provider.write(out/'component-inputs.json',{**old,'artifacts':records,'coreContainerChanged':True,'bootClassChanged':True,'featureEntriesByteIdentical':True,'facadeCopiedUnchanged':True,'nativeCompiledSource':custody['providers']['nativeCompiledSource']})
    provider.write(report/'RESULT.json',{'status':'passed','acceptedProvider':pin,'privateMixinRebuilt':True,'coreBootClassRebuilt':True,'featureEntryCountUnchanged':len(entries),'engineNativeSdkRhinoRebuilt':False,'facadeCopiedUnchanged':True})
if __name__=='__main__':main()
