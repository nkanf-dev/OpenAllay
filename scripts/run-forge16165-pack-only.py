#!/usr/bin/env python3
"""Consume successful artifacts and pack once; no compiler, reobf or game replay."""
import hashlib,importlib.util,json,os,shutil,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
NATIVE='5005f39b582518ada548e3526a1e606b8b136587'
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def ref(p):return {'path':str(Path(p).resolve()),'sha256':sha(p)}
def save(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,indent=2)+'\n');return ref(p)
def load_module(name,p):
    sp=importlib.util.spec_from_file_location(name,p);m=importlib.util.module_from_spec(sp);sys.modules[name]=m;sp.loader.exec_module(m);return m
def artifact(aid,rid,source,digest,dest):
    repo=os.environ['GITHUB_REPOSITORY'];info=json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/actions/artifacts/{aid}']))
    if info['expired'] or info['workflow_run']['id']!=rid or info['workflow_run']['head_sha']!=source or info['digest']!='sha256:'+digest:raise ValueError('Original artifact provider identity mismatch')
    archive=dest.with_suffix('.zip')
    with archive.open('xb') as output:subprocess.run(['gh','api',f'repos/{repo}/actions/artifacts/{aid}/zip'],stdout=output,check=True)
    if sha(archive)!=digest:raise ValueError('Original artifact checksum mismatch')
    dest.mkdir()
    with zipfile.ZipFile(archive) as z:
        for name in z.namelist():
            p=dest/name
            if not p.resolve().is_relative_to(dest.resolve()):raise ValueError('Unsafe archive member')
            if name.endswith('/'):p.mkdir(parents=True,exist_ok=True)
            else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(name))
    return archive

def runtime_proof(work,source):
    # Normal official installer/processor route, not a game launch or library replacement.
    stock=load_module('pack_stock_runtime',source/'scripts/forge16165-engine-prerequisite/stock/stock-forge36-prerequisite.py')
    runtime,launch,freeze=stock.load_helpers()
    pins=stock.PINS;root=source/'build/e2e/runtime/forge16165-stock/minecraft'
    runtime.claim_root(root,'1.16.5')
    vanilla,_=runtime.prepare_vanilla(root,{**pins,'java_version':'8'})
    installer=root/'.provision/forge-installer.jar';pin=freeze['installer'];runtime.download(pin['url'],installer,pin['sha1'],pin['size'],maximum=8*1024*1024)
    if sha(installer)!=pin['sha256']:raise ValueError('Official installer SHA mismatch')
    install,version=runtime.inspect_installer(installer,'forge');stock.validate_metadata(install,version,vanilla)
    with zipfile.ZipFile(installer) as z:
        for metadata in [install,version]:
            for library in metadata['libraries']:
                a=library['downloads']['artifact']
                if not a['url']:
                    p=runtime.relative_file(root/'libraries',a['path']);data=z.read('maven/'+a['path'])
                    if len(data)!=a['size'] or hashlib.sha1(data).hexdigest()!=a['sha1']:raise ValueError('Bundled installer library mismatch')
                    p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
            runtime.prepare_libraries({'libraries':[x for x in metadata['libraries'] if x['downloads']['artifact']['url']]},root)
    java=Path(os.environ['JAVA_HOME_17_X64'])/'bin/java'
    runtime.run_installer(runtime.installer_command('forge',java,installer,root,pins),root,'forge')
    outputs={k:runtime.data_library(root,install,k) for k in ['MAPPINGS','MC_SLIM','MC_EXTRA','MC_SRG','PATCHED']};runtime.verify_processor_outputs(root,install,outputs)
    cp=launch.version_libraries(vanilla,root,Path('/nonexistent'),allow_gradle=False)
    fml=launch.version_libraries(version,root,Path('/nonexistent'),allow_gradle=False);replacements={tuple(n.split(':')[:2]) for n,_ in fml}
    cp=[(n,p) for n,p in cp if tuple(n.split(':')[:2]) not in replacements]+fml
    # Forge's transforming loader also owns the installed universal and patched client archives.
    # They are not ordinary version.libraries JVM entries.
    universal=next(x for x in install['libraries'] if x['name']=='net.minecraftforge:forge:1.16.5-36.2.42:universal')
    cp.append((universal['name'],runtime.relative_file(root/'libraries',universal['downloads']['artifact']['path'])))
    cp.append(('net.minecraftforge:forge:1.16.5-36.2.42:client',outputs['PATCHED']))
    targets={'com/google/gson/Gson.class','com/google/common/collect/ImmutableList.class','org/apache/logging/log4j/Logger.class','org/spongepowered/asm/mixin/Mixin.class','net/minecraftforge/fml/common/Mod.class','net/minecraft/client/Minecraft.class'}
    classes=[];artifacts=[];seen_paths=set()
    for coordinate,p in cp:
        if p in seen_paths:continue
        seen_paths.add(p)
        with zipfile.ZipFile(p) as z:
            present=targets.intersection(z.namelist())
            if present:
                digest=sha(p);artifacts.append({'role':'host-'+str(len(artifacts)),'coordinate':coordinate,'path':str(p),'sha256':digest})
                for entry in present:classes.append({'entry':entry,'coordinate':coordinate,'archiveSha256':digest,'classSha256':hashlib.sha256(z.read(entry)).hexdigest()})
    save(work/'runtime-sentinel-inspection.json',{'classes':classes,'artifacts':artifacts,'missing':sorted(targets-{c['entry'] for c in classes})})
    if len(classes)!=6 or {c['entry'] for c in classes}!=targets:raise ValueError('Actual runtime sentinel ownership incomplete/competing; see runtime-sentinel-inspection.json')
    return save(work/'runtime-ownership.json',{'minecraft':'1.16.5','forge':'36.2.42','artifacts':artifacts,'classes':classes})

def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote runner only')
    work=ROOT/'build/forge16165-pack-inputs';work.mkdir(parents=True,exist_ok=False)
    source=work/'source';subprocess.run(['git','worktree','add','--detach',str(source),NATIVE],cwd=ROOT,check=True)
    enginezip=artifact(11406765949,37450040748,'7819dbea0601f1ae7ef8b9c03286b6f8a0589bb4','6d1ac947f59d983ea1b88db15d456cec3b7ddffd2b8f26320836a5b04de83802',work/'engine')
    nativezip=artifact(11410338919,37459210222,NATIVE,'4f102d6264e628750d610e4d89518fb80457a5185d8ace23971bd92390bd09eb',work/'native')
    original=next((work/'engine').rglob('closure-input.json'));spec=json.loads(original.read_text());originalResolution=next((work/'engine').rglob('effective-runtime-resolution.json'))
    original_native_spec=json.loads(original.read_text())
    old_root='/home/runner/work/OpenAllay/OpenAllay/build/forge16165-native-inputs/closure'
    for a in original_native_spec['artifacts']:a['path']=old_root+'/'+a['role']+'.jar'
    original_native_spec['resolution']={'path':old_root+'/effective-runtime-resolution.json','sha256':sha(originalResolution)}
    orig_native_path=work/'original-native-closure.json';save(orig_native_path,original_native_spec)
    if sha(orig_native_path)!='233bea524b35fdf74e79101c1b13ddad0856842bdf40268df221a3db8f97a947':raise ValueError('Reproduced original compile input serialization/hash differs')
    for a in spec['artifacts']:
        p=original.parent/(a['role']+'.jar')
        if sha(p)!=a['sha256']:raise ValueError('Immutable component differs')
        a['path']=str(p)
    spec['resolution']=ref(originalResolution);closure_ref=save(work/'relocated-closure.json',spec)
    resolution=next((work/'engine').rglob('raw-gradle-resolution.json'))
    builderroot=work/'builder-source';subprocess.run(['git','clone','--filter=blob:none','--no-checkout','https://github.com/nkanf-dev/OpenAllay-Extensions.git',str(builderroot)],check=True)
    subprocess.run(['git','-C',str(builderroot),'checkout','--detach','e37eb4f325d6917b2a0b32afc47dd139a55acb83'],check=True)
    scopes=['extensions/minecraft-builder','gradle.properties','settings.gradle','build.gradle']
    paths=subprocess.check_output(['git','-C',str(builderroot),'ls-files','-z','--',*scopes]).decode().split('\0');inv={p:sha(builderroot/p) for p in paths if p}
    builder=next(a for a in spec['artifacts'] if a['role']=='builder');lock=source/'native-builds/forge16165/extensions.lock.json'
    builderproof=save(work/'builder-source-proof.json',{'archiveSha256':builder['sha256'],'retainedSourceRevision':'e37eb4f325d6917b2a0b32afc47dd139a55acb83','lockSha256':sha(lock),'lockedSourceRevision':'e37eb4f325d6917b2a0b32afc47dd139a55acb83','retainedSourceRoot':str(builderroot),'lockedSourceRoot':str(builderroot),'sourceScopes':scopes,'retainedInputs':inv,'lockedInputs':inv})
    runtime=runtime_proof(work,source)
    inputs=save(work/'postprocess-inputs.json',{'sourceRoot':str(source),'nativeArtifact':ref(nativezip),'rootReceipt':ref(ROOT/'native-builds/forge16165/native-compile-receipt.json'),'closureSpec':closure_ref,'closurePolicy':ref(source/'scripts/forge16165-engine-prerequisite/ownership.json'),'retainedResolution':ref(resolution),'runtimeOwnership':runtime,'builderSourceProof':builderproof,'originalClosureSpec':ref(orig_native_path)})
    output=ROOT/'build/forge16165-product';output.mkdir();subprocess.run([sys.executable,'-B',str(ROOT/'scripts/forge16165-postprocess-pack.py'),'--inputs',inputs['path'],'--work',str(work/'postprocessed'),'--output',str(output/'openallay-forge-1.16.5-0.4.3.jar'),'--receipt',str(output/'pack-receipt.json')],check=True)
if __name__=='__main__':main()
