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
        old='cbcf5e66c81d11e4d219fa6cc8da04ab996c7ce0'
        changed=subprocess.check_output(['git','diff','--name-only',old,'HEAD'],cwd=ROOT,text=True).splitlines()
        if any(not (path.startswith('scripts/forge16165-engine-prerequisite/') or path in
                ['gradle/forge16165-engine-export.init.gradle','.github/workflows/minecraft-native.yml']) for path in changed):
            raise ValueError('Shared producer inputs changed beyond this prerequisite source packet')
        if options.reuse_shared:
            old_source='794a6b0fe0537371ea737eed4ce477b737f325e3'
            run(['git','merge-base','--is-ancestor',old_source,'HEAD'])
            artifact=json.loads(subprocess.check_output(['gh','api','repos/'+os.environ['GITHUB_REPOSITORY']+'/actions/artifacts/11395936631']))
            if (artifact['expired'] or artifact['workflow_run']['id']!=37428024273
                    or artifact['workflow_run']['head_sha']!=old_source
                    or artifact['digest']!='sha256:baa4f3ef51e19ac6dc8f7a486686cb5023334a7d06ae7126ad67fdec741023bd'):
                raise ValueError('Immutable shared input archive identity differs')
            spec=json.loads((shared/'closure-input.json').read_text())
            for entry in spec['artifacts']:
                if digest(entry['path'])!=entry['sha256']:raise ValueError('Retained shared artifact bytes differ')
            (out/'shared-input-reuse.json').write_text(json.dumps({'source':old_source,'run':37428024273,
                'artifact':11395936631,'actualBuildOutcome':'shared compilation/export passed; original overall run failed',
                'currentRunnerSource':os.environ['GITHUB_SHA'],'artifactBytesUnchanged':True},indent=2)+'\n')
        else:
            run([str(ROOT/'gradlew'),'--configure-on-demand','--max-workers=2','--stacktrace','-PminecraftTarget=1.19.2',
                 '-PtestBundledExtensions=false','-I',str(ROOT/'gradle/forge16165-engine-export.init.gradle'),':engine-core:exportForge36Closure'])
        active_spec=shared/'closure-input.json'
        if options.reuse_shared:
            builder_source='e37eb4f325d6917b2a0b32afc47dd139a55acb83'
            retained_component=out/'retained-builder/builder-isolated.jar'
            retained_provenance=out/'retained-builder/builder-isolation-provenance.json'
            component=json.loads(subprocess.check_output(['gh','api','repos/'+os.environ['GITHUB_REPOSITORY']+'/actions/artifacts/11395574649']))
            if (component['expired'] or component['workflow_run']['id']!=37429372559
                    or component['workflow_run']['head_sha']!='41243d95af42217468e9ca62f703889ab292d5e8'
                    or component['digest']!='sha256:0495603d813ee58437d6976c36399e340d09c75458ceb2dce399ac9f11065e8e'):
                raise ValueError('Retained candidate Builder archive identity differs')
            provenance=json.loads(retained_provenance.read_text())
            if (provenance['source']!=builder_source or digest(retained_component)!=provenance['candidateBuilder']['sha256']
                    or provenance['retainedSdkSha256']!=digest(shared/'sdk.jar')):
                raise ValueError('Retained candidate Builder package/source/SDK identity differs')
            isolated=out/'builder-isolated.jar';shutil.copyfile(retained_component,isolated)
            with zipfile.ZipFile(isolated) as jar:
                names=jar.namelist()
                private=[name for name in names if name.startswith('dev/openallay/builder/internal/errorprone/annotations/') and name.endswith('.class')]
                if len(private)!=29 or any(name.startswith('com/google/errorprone/') for name in names):
                    raise ValueError('Candidate Builder annotation isolation is incomplete')
            builder_base=provenance['baseSource']
            original_builder=next(entry.copy() for entry in spec['artifacts'] if entry['role']=='builder')
            for entry in spec['artifacts']:
                if entry['role']=='builder':entry.update(path=str(isolated),sha256=digest(isolated))
            new_spec=out/'candidate-closure-input.json';new_spec.write_text(json.dumps(spec,indent=2)+'\n')
            (out/'builder-isolation-provenance.json').write_text(json.dumps({'source':builder_source,'baseSource':builder_base,
                'originalBuilder':original_builder,'candidateBuilder':next(entry for entry in spec['artifacts'] if entry['role']=='builder'),
                'retainedSdkSha256':digest(shared/'sdk.jar'),'otherEighteenInputs':'unchanged hashes verified',
                'privateAnnotationClasses':private,'producer':'normal universal Shadow assemble and verifyUniversalPackage'},indent=2)+'\n')
            active_spec=new_spec
        run(['python3','-B',str(PACKET/'pack.py'),'scan','--spec',str(active_spec),'--report',str(out/'closure-scan.json')])
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
        run(['python3','-B',str(PACKET/'pack.py'),'pack','--spec',str(out/'candidate-closure-input.json' if (out/'candidate-closure-input.json').exists() else shared/'closure-input.json'),
             '--gate',str(out/'closure-scan.json'),'--gate-sha256',digest(out/'closure-scan.json'),
             '--probes',str(out/'probe-inputs.json'),'--output',str(out/'openallay-engine-probe-fat.jar'),'--receipt',str(out/'pack-receipt.json')])
        (out/'fat-mod.sha256').write_text(digest(out/'openallay-engine-probe-fat.jar')+'\n')
if __name__=='__main__':main()
