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
    args=argparse.ArgumentParser();args.add_argument('--phase',choices=['scan','probe','pack'],required=True);phase=args.parse_args().phase
    shared=ROOT/'build/forge36-shared';probe=PACKET/'probe';out=ROOT/'build/forge36-artifacts';out.mkdir(parents=True,exist_ok=True)
    if phase=='scan':
        old='cbcf5e66c81d11e4d219fa6cc8da04ab996c7ce0'
        changed=subprocess.check_output(['git','diff','--name-only',old,'HEAD'],cwd=ROOT,text=True).splitlines()
        if any(not (path.startswith('scripts/forge16165-engine-prerequisite/') or path in
                ['gradle/forge16165-engine-export.init.gradle','.github/workflows/minecraft-native.yml']) for path in changed):
            raise ValueError('Shared producer inputs changed beyond this prerequisite source packet')
        run([str(ROOT/'gradlew'),'--configure-on-demand','--max-workers=2','--stacktrace','-PminecraftTarget=1.19.2',
             '-PtestBundledExtensions=false','-I',str(ROOT/'gradle/forge16165-engine-export.init.gradle'),':engine-core:exportForge36Closure'])
        run(['python3','-B',str(PACKET/'pack.py'),'scan','--spec',str(shared/'closure-input.json'),'--report',str(out/'closure-scan.json')])
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
