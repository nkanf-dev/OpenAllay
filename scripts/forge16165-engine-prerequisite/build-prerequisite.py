#!/usr/bin/env python3
"""Remote build/pack driver. One shared producer, normal FG probe, no test replay."""
import argparse,hashlib,json,os,shutil,subprocess,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
PACKET=ROOT/'scripts/forge16165-engine-prerequisite'
MDK_URL='https://maven.minecraftforge.net/net/minecraftforge/forge/1.16.5-36.2.42/forge-1.16.5-36.2.42-mdk.zip'
MDK_SHA1='3d95dac7c4f3ec7a0bdafed3f3c5cb6d284cec06'
def digest(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda:stream.read(65536),b''):h.update(block)
    return h.hexdigest()
def run(command,env=None):
    subprocess.run(command,cwd=ROOT,env=env,check=True)
def main():
    args=argparse.ArgumentParser();args.add_argument('--phase',choices=['scan','probe','pack'],required=True);args.add_argument('--reuse-shared',action='store_true');options=args.parse_args();phase=options.phase
    shared=ROOT/'build/forge36-shared';probe=PACKET/'probe';out=ROOT/'build/forge36-artifacts';out.mkdir(parents=True,exist_ok=True)
    if phase=='scan':
        source='4fafdc22a1e18d0601388b3c3c8497d89c1cf012'
        run(['git','merge-base','--is-ancestor',source,'HEAD'])
        changed=subprocess.check_output(['git','diff','--name-only',source,'HEAD'],cwd=ROOT,text=True).splitlines()
        allowed={'.github/workflows/minecraft-native.yml',
            'scripts/forge16165-engine-prerequisite/build-prerequisite.py',
            'scripts/forge16165-engine-prerequisite/probe/src/main/java/dev/openallay/forge36probe/Probe.java',
            'scripts/forge16165-engine-prerequisite/collect-engine-prerequisite.py'}
        if any(path not in allowed for path in changed):
            raise ValueError('Retained logging engine inputs changed beyond probe/orchestration source')
        repository=os.environ['GITHUB_REPOSITORY']
        artifact=json.loads(subprocess.check_output(['gh','api',f'repos/{repository}/actions/artifacts/11397973030']))
        if (artifact['expired'] or artifact['workflow_run']['id']!=37433650018
                or artifact['workflow_run']['head_sha']!=source
                or artifact['digest']!='sha256:7951228b7dba15097bcdf9cd50ab9b1fb28559da8a7f110e4ca334b91d2e33cf'):
            raise ValueError('Retained changed-engine archive provider identity differs')
        prefix=json.loads(subprocess.check_output(['gh','api',f'repos/{repository}/actions/artifacts/11397859809']))
        if (prefix['expired'] or prefix['workflow_run']['id']!=37434990163
                or prefix['workflow_run']['head_sha']!='0170a2cb8cc4fd2a7eb2fe9ee2502b8f1d094eff'
                or prefix['digest']!='sha256:c3bd5301ac8426cb2c62865145bb3c64c5a62acbe7ea143f107d84c4f6746c94'):
            raise ValueError('Original native prefix provider identity differs')
        spec=json.loads((shared/'closure-input.json').read_text())
        if spec['sourceRevision']!=source or len(spec['artifacts'])!=18:
            raise ValueError('Retained logging engine producer manifest differs')
        for entry in spec['artifacts']:
            if digest(entry['path'])!=entry['sha256']:
                raise ValueError('Retained closure artifact bytes differ: '+entry['role'])
        (out/'logging-input-reuse.json').write_text(json.dumps({'source':source,'run':37433650018,
            'artifact':11397973030,'currentProbeRunnerSource':os.environ['GITHUB_SHA'],
            'compiledInputsUnchanged':True,'originalRunOutcome':'FAIL; producer stages passed'},indent=2)+'\n')
        run(['python3','-B',str(PACKET/'pack.py'),'scan','--spec',str(shared/'closure-input.json'),
             '--report',str(out/'closure-scan.json')])
    elif phase=='probe':
        if json.loads((out/'closure-scan.json').read_text())['status']!='READY':raise ValueError('Pack input is not ready')
        archive=out/'official-forge36-mdk.zip'
        req=urllib.request.Request(MDK_URL,headers={'User-Agent':'OpenAllay-CI-Runtime'})
        with urllib.request.urlopen(req,timeout=60) as source,archive.open('xb') as target:shutil.copyfileobj(source,target)
        if hashlib.sha1(archive.read_bytes()).hexdigest()!=MDK_SHA1:raise ValueError('Official MDK checksum differs')
        with zipfile.ZipFile(archive) as jar:
            properties=jar.read('gradle/wrapper/gradle-wrapper.properties')
            if b'gradle-8.4-' not in properties:raise ValueError('Official MDK wrapper is not Gradle8.4')
            for name in ['gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties']:
                target=probe/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(jar.read(name))
        (probe/'gradlew').chmod(0o755)
        closure=probe/'closure';closure.mkdir()
        for role,name in [('engine','openallay-engine-core.jar'),('sdk','openallay-extension-api.jar'),('rhino','openallay-rhino.jar')]:shutil.copyfile(shared/(role+'.jar'),closure/name)
        env=dict(os.environ);home=env['JAVA_HOME_17_X64'];env['JAVA_HOME']=home;env['PATH']=home+'/bin:'+env['PATH']
        run([str(probe/'gradlew'),'--max-workers=2','--stacktrace','-p',str(probe),'emitProbeMetadata'],env)
        metadata=json.loads((probe/'build/probe-metadata/probe-build.json').read_text())
        pair={}
        for key,record in [('input',metadata['reobfInput']),('reobf',metadata['reobfOutput'])]:pair[key]={'role':'probe-'+('input' if key=='input' else 'reobf'),'coordinate':'dev.openallay:forge36-engine-probe:0.4.3','path':record['path'],'sha256':record['sha256']}
        (out/'probe-inputs.json').write_text(json.dumps(pair,indent=2)+'\n')
    else:
        run(['python3','-B',str(PACKET/'pack.py'),'pack','--spec',str(shared/'closure-input.json'),
             '--gate',str(out/'closure-scan.json'),'--gate-sha256',digest(out/'closure-scan.json'),
             '--probes',str(out/'probe-inputs.json'),'--output',str(out/'openallay-engine-probe-fat.jar'),'--receipt',str(out/'pack-receipt.json')])
        (out/'fat-mod.sha256').write_text(digest(out/'openallay-engine-probe-fat.jar')+'\n')
if __name__=='__main__':main()
