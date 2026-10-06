#!/usr/bin/env python3
"""Build only current engine and focused custody tests; reuse all other pinned inputs."""
import hashlib,json,os,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def run(args,env=None):subprocess.run(list(map(str,args)),cwd=ROOT,env=env,check=True)
def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote runner only')
    out=ROOT/'build/changed-engine';out.mkdir(parents=True,exist_ok=False)
    retained=out/'retained';retained.mkdir();builder=out/'builder';builder.mkdir()
    specs=[(11395936631,37428024273,'794a6b0fe0537371ea737eed4ce477b737f325e3','baa4f3ef51e19ac6dc8f7a486686cb5023334a7d06ae7126ad67fdec741023bd',retained),
           (11395574649,37429372559,'41243d95af42217468e9ca62f703889ab292d5e8','0495603d813ee58437d6976c36399e340d09c75458ceb2dce399ac9f11065e8e',builder)]
    for aid,rid,source,digest,dest in specs:
        repo=os.environ['GITHUB_REPOSITORY'];record=json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/actions/artifacts/{aid}']))
        if record['expired'] or record['workflow_run']['id']!=rid or record['workflow_run']['head_sha']!=source or record['digest']!='sha256:'+digest:raise ValueError('Retained provider mismatch')
        archive=out/(str(aid)+'.zip')
        with archive.open('xb') as stream:subprocess.run(['gh','api',f'repos/{repo}/actions/artifacts/{aid}/zip'],stdout=stream,check=True)
        if sha(archive)!=digest:raise ValueError('Retained archive mismatch')
        with zipfile.ZipFile(archive) as z:
            for name in z.namelist():
                p=dest/name
                if not p.resolve().is_relative_to(dest.resolve()):raise ValueError('Unsafe retained path')
                if name.endswith('/'):p.mkdir(parents=True,exist_ok=True)
                else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(name))
    pin=json.loads((ROOT/'native-builds/engine-only/retained-inputs.json').read_text())
    component_root=out/'components';component_root.mkdir()
    for item in pin['artifacts']:
        if item['role']=='builder':continue
        candidates=[p for p in retained.rglob('*.jar') if sha(p)==item['sha256']]
        if not candidates:raise ValueError('Missing retained role '+item['role'])
        (component_root/(item['role']+'.jar')).write_bytes(candidates[0].read_bytes())
    bp=next(a for a in pin['artifacts'] if a['role']=='builder');builders=[p for p in builder.rglob('*.jar') if sha(p)==bp['sha256']]
    if not builders:raise ValueError('Isolated Builder bytes absent')
    request=out/'inputs.json';request.write_text(json.dumps({'sourceRoot':str(ROOT),'sourceRevision':os.environ['GITHUB_SHA'],'retainedDirectory':str(component_root),'builderJar':str(builders[0])},indent=2)+'\n')
    env=dict(os.environ);env['JAVA_HOME']=env['JAVA_HOME_21_X64'];env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
    run([ROOT/'gradlew','--max-workers=2','--stacktrace','-p',ROOT/'native-builds/engine-only','-PengineOnlyInputs='+str(request),'-PfocusedBridgeTests=true',':engine-core:exportEngineOnlyClosure',':engine-core:test','--tests','dev.openallay.server.ServerCancellationCorrelationTest'],env)
    (out/'receipt.json').write_text(json.dumps({'source':os.environ['GITHUB_SHA'],'engineRebuilt':True,'unchangedComponentsRebuilt':False,'focusedTests':True,'gameExecuted':False},indent=2)+'\n')
if __name__=='__main__':main()
