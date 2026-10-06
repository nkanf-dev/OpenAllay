#!/usr/bin/env python3
"""Rebuild only changed neutral engine seams; retain every other verified component."""
import hashlib,json,os,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote runner only')
    out=ROOT/'build/forge1122-changed-engine';out.mkdir(parents=True,exist_ok=False)
    meta=json.loads(subprocess.check_output(['gh','api','repos/'+os.environ['GITHUB_REPOSITORY']+'/actions/artifacts/11406765949']))
    expected='6d1ac947f59d983ea1b88db15d456cec3b7ddffd2b8f26320836a5b04de83802'
    if meta['expired'] or meta['workflow_run']['id']!=37450040748 or meta['workflow_run']['head_sha']!='7819dbea0601f1ae7ef8b9c03286b6f8a0589bb4' or meta['digest']!='sha256:'+expected:raise ValueError('Retained effective closure provider mismatch')
    archive=out/'effective-engine.zip'
    with archive.open('xb') as f:subprocess.run(['gh','api',meta['archive_download_url']],stdout=f,check=True)
    if sha(archive)!=expected:raise ValueError('Effective archive mismatch')
    retained=out/'retained';retained.mkdir()
    with zipfile.ZipFile(archive) as z:
        for name in z.namelist():
            p=retained/name
            if not p.resolve().is_relative_to(retained.resolve()):raise ValueError('Unsafe archive member')
            if name.endswith('/'):p.mkdir(parents=True,exist_ok=True)
            else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(name))
    original=next(retained.rglob('closure-input.json'));spec=json.loads(original.read_text())
    pins=json.loads((ROOT/'native-builds/engine-only/retained-inputs.json').read_text())
    components=out/'components';components.mkdir()
    for pin in pins['artifacts']:
        record=next(a for a in spec['artifacts'] if a['role']==pin['role'])
        p=original.parent/(pin['role']+'.jar')
        if record['sha256']!=pin['sha256'] or sha(p)!=pin['sha256']:raise ValueError('Retained constituent differs: '+pin['role'])
        (components/(pin['role']+'.jar')).write_bytes(p.read_bytes())
    request=out/'inputs.json';request.write_text(json.dumps({'sourceRoot':str(ROOT),'sourceRevision':os.environ['GITHUB_SHA'],'retainedDirectory':str(components),'builderJar':str(components/'builder.jar')},indent=2)+'\n')
    env=dict(os.environ);env['JAVA_HOME']=env['JAVA_HOME_21_X64'];env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
    command=[str(ROOT/'gradlew'),'--max-workers=2','--stacktrace','-p',str(ROOT/'native-builds/engine-only'),'-PengineOnlyInputs='+str(request),'-PfocusedNativeSeamTests=true',':engine-core:exportEngineOnlyClosure',':engine-core:test']
    for name in ['dev.openallay.client.gui.GuideWidgetInputTest','dev.openallay.command.GuideCommandSpecTest','dev.openallay.command.DevelopmentCommandSpecTest']:command+=['--tests',name]
    with (out/'engine.log').open('w') as log:result=subprocess.run(command,env=env,stdout=log,stderr=subprocess.STDOUT)
    (out/'receipt.json').write_text(json.dumps({'source':os.environ['GITHUB_SHA'],'exitCode':result.returncode,'engineRebuilt':True,'sdkRhinoBuilderRebuilt':False,'focusedSeamTestsOnly':True,'gameExecuted':False,'command':command},indent=2)+'\n')
    if result.returncode:raise SystemExit(result.returncode)
if __name__=='__main__':main()
