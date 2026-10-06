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
        retained=ROOT/'build/forge36-retained-shared'
        builder=ROOT/'build/forge36-retained-builder/builder-isolated.jar'
        old_source='794a6b0fe0537371ea737eed4ce477b737f325e3'
        run(['git','merge-base','--is-ancestor',old_source,'HEAD'])
        repository=os.environ['GITHUB_REPOSITORY']
        for artifact_id,run_id,source,expected in [
            (11395936631,37428024273,old_source,'sha256:baa4f3ef51e19ac6dc8f7a486686cb5023334a7d06ae7126ad67fdec741023bd'),
            (11395574649,37429372559,'41243d95af42217468e9ca62f703889ab292d5e8','sha256:0495603d813ee58437d6976c36399e340d09c75458ceb2dce399ac9f11065e8e')]:
            artifact=json.loads(subprocess.check_output(['gh','api',f'repos/{repository}/actions/artifacts/{artifact_id}']))
            if (artifact['expired'] or artifact['workflow_run']['id']!=run_id
                    or artifact['workflow_run']['head_sha']!=source or artifact['digest']!=expected):
                raise ValueError('Immutable input provider identity differs')
        original_spec=json.loads((retained/'closure-input.json').read_text())
        for entry in original_spec['artifacts']:
            path=retained/(entry['role']+'.jar')
            if digest(path)!=entry['sha256']:raise ValueError('Retained component bytes differ')
        provenance=json.loads((ROOT/'build/forge36-retained-builder/builder-isolation-provenance.json').read_text())
        if (provenance['source']!='e37eb4f325d6917b2a0b32afc47dd139a55acb83'
                or digest(builder)!=provenance['candidateBuilder']['sha256']
                or provenance['retainedSdkSha256']!=digest(retained/'sdk.jar')):
            raise ValueError('Builder package/source/SDK identity differs')
        # Source-owned SDK/Rhino bytes have no mandatory SLF4J linkage. SQLite retains its own optional fallback.
        for role in ['sdk','rhino']:
            with zipfile.ZipFile(retained/(role+'.jar')) as jar:
                if any(b'org/slf4j/' in jar.read(name) for name in jar.namelist() if name.endswith('.class')):
                    raise ValueError('Unexpected retained component SLF4J linkage: '+role)
        source=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
        request=out/'engine-only-inputs.json'
        request.write_text(json.dumps({'sourceRoot':str(ROOT),'sourceRevision':source,
            'retainedDirectory':str(retained),'builderJar':str(builder)},indent=2)+'\n')
        run([str(ROOT/'gradlew'),'--max-workers=2','--stacktrace','--project-dir',str(ROOT/'native-builds/engine-only'),
             '-PengineOnlyInputs='+str(request),':engine-core:exportEngineOnlyClosure'])
        spec=json.loads((shared/'closure-input.json').read_text())
        if len(spec['artifacts'])!=18 or any(entry['role']=='slf4j' for entry in spec['artifacts']):
            raise ValueError('New effective closure role set differs')
        (out/'engine-component-provenance.json').write_text(json.dumps({'actualEngineSource':source,
            'retainedSharedSource':old_source,'retainedSharedArtifact':11395936631,
            'candidateBuilderSource':provenance['source'],'candidateBuilderArtifact':11395574649,
            'ownedSdkRhinoSlf4jClassReferences':'none in retained class bytes',
            'newEngine':next(entry for entry in spec['artifacts'] if entry['role']=='engine'),
            'otherInputs':'exact retained hashes, normal resolved classpath verified by producer'},indent=2)+'\n')
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
