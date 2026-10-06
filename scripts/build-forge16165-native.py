#!/usr/bin/env python3
"""Remote-only normal FG native compilation using the already-passed effective engine."""
import hashlib,importlib.util,json,os,shutil,subprocess,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def run(cmd,env):
    log=ROOT/'build/forge16165-native-report/gradle.log';log.parent.mkdir(parents=True,exist_ok=True)
    with log.open('a') as output:
        result=subprocess.run(list(map(str,cmd)),cwd=ROOT,env=env,stdout=output,stderr=subprocess.STDOUT)
    print(log.read_text()[-8000:])
    if result.returncode:raise subprocess.CalledProcessError(result.returncode,cmd)
def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote runner only')
    work=ROOT/'build/forge16165-native-inputs';work.mkdir(parents=True,exist_ok=False)
    aid=11406765949;repo=os.environ['GITHUB_REPOSITORY']
    meta=json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/actions/artifacts/{aid}']))
    expected='6d1ac947f59d983ea1b88db15d456cec3b7ddffd2b8f26320836a5b04de83802'
    if meta['expired'] or meta['workflow_run']['id']!=37450040748 or meta['workflow_run']['head_sha']!='7819dbea0601f1ae7ef8b9c03286b6f8a0589bb4' or meta['digest']!='sha256:'+expected:raise ValueError('Effective engine provider mismatch')
    archive=work/'effective-engine.zip'
    with archive.open('xb') as stream:subprocess.run(['gh','api',f'repos/{repo}/actions/artifacts/{aid}/zip'],stdout=stream,check=True)
    if sha(archive)!=expected:raise ValueError('Effective closure archive mismatch')
    closure_dir=work/'closure';closure_dir.mkdir()
    with zipfile.ZipFile(archive) as z:
        for name in z.namelist():
            p=closure_dir/name
            if not p.resolve().is_relative_to(closure_dir.resolve()):raise ValueError('Unsafe archive path')
            if name.endswith('/'):p.mkdir(parents=True,exist_ok=True)
            else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(name))
    original=next(closure_dir.rglob('closure-input.json'));spec=json.loads(original.read_text())
    for artifact in spec['artifacts']:
        p=original.parent/(artifact['role']+'.jar')
        if sha(p)!=artifact['sha256']:raise ValueError('Retained constituent changed')
        artifact['path']=str(p)
    resolution=next(closure_dir.rglob('effective-runtime-resolution.json'));spec['resolution']={'path':str(resolution),'sha256':sha(resolution)}
    spec_path=work/'closure-input.json';spec_path.write_text(json.dumps(spec,indent=2)+'\n')
    module_path=ROOT/'scripts/forge16165-engine-prerequisite/focused-tooling-acceptance.py'
    module_spec=importlib.util.spec_from_file_location('native_inputs',module_path);helper=importlib.util.module_from_spec(module_spec);module_spec.loader.exec_module(helper)
    reports=ROOT/'build/forge16165-native-report';reports.mkdir()
    mapping_dir=helper.mappings(work,reports)
    mapping_inputs=work/'mapping-inputs.json';mapping_inputs.write_text(json.dumps({k:{'path':str(mapping_dir/(k+'.txt' if k!='tsrg' else 'joined.tsrg')),'sha256':v} for k,v in helper.HASHES.items()},indent=2)+'\n')
    build=ROOT/'native-builds/forge16165'
    mdk=work/'mdk.zip';helper.fetch(helper.MDK,mdk,2000000,sha1='3d95dac7c4f3ec7a0bdafed3f3c5cb6d284cec06')
    with zipfile.ZipFile(mdk) as z:
        for name in ['gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar']:
            p=build/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(name))
    (build/'gradlew').chmod(0o755)
    acceptance=work/'acceptance.properties';lock=work/'compiler-lock.json'
    request=work/'request.json';request.write_text(json.dumps({'sourceRoot':str(ROOT),'sourceRevision':os.environ['GITHUB_SHA'],'closureSpec':str(spec_path),'sourceSelectionPlan':str(build/'source-selection.json'),'mappingInputs':str(mapping_inputs),'metadataAcceptance':str(acceptance),'compilerArtifactLock':str(lock)},indent=2)+'\n')
    env=dict(os.environ);env['JAVA_HOME']=env['JAVA_HOME_17_X64'];env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
    cmd=[build/'gradlew','--max-workers=2','--stacktrace','-p',build,'-PnativeBuildInputs='+str(request)]
    run(cmd+['exportNativeToolingInputs','acceptNamespaceMetadata'],env)
    proposed=build/'build/native-tooling/compiler-input-lock.proposed.json';shutil.copyfile(proposed,lock)
    run(cmd+['emitNativeBuildMetadata'],env)
    (reports/'RESULT.json').write_text(json.dumps({'source':os.environ['GITHUB_SHA'],'nativeCompiled':True,'normalApReobf':True,'effectiveEngineRun':37450040748,'engineRebuilt':False,'gameExecuted':False},indent=2)+'\n')
if __name__=='__main__':main()
