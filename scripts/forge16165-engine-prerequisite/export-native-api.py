#!/usr/bin/env python3
"""Remote-only native declarations from the exact normal FG compile dependency."""
import argparse,hashlib,json,os,re,shutil,subprocess,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
P=ROOT/'scripts/forge16165-engine-prerequisite';probe=P/'probe'
URL='https://maven.minecraftforge.net/net/minecraftforge/forge/1.16.5-36.2.42/forge-1.16.5-36.2.42-mdk.zip'
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--supplement',action='store_true');args=parser.parse_args()
    out=ROOT/'build/forge36-native-api';out.mkdir(parents=True,exist_ok=False)
    archive=out/'official-mdk.zip'
    with urllib.request.urlopen(urllib.request.Request(URL,headers={'User-Agent':'OpenAllay-CI-Runtime'}),timeout=60) as stream,archive.open('xb') as target:shutil.copyfileobj(stream,target)
    if hashlib.sha1(archive.read_bytes()).hexdigest()!='3d95dac7c4f3ec7a0bdafed3f3c5cb6d284cec06':raise ValueError('Official MDK hash differs')
    with zipfile.ZipFile(archive) as z:
        for name in ['gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties']:
            f=probe/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(z.read(name))
    (probe/'gradlew').chmod(0o755)
    env=dict(os.environ);home=env['JAVA_HOME_17_X64'];env['JAVA_HOME']=home;env['PATH']=home+'/bin:'+env['PATH']
    command=[str(probe/'gradlew'),'--max-workers=2','--stacktrace','-p',str(probe),'exportNativeApi']
    if args.supplement:command.append('-PnativeApiSupplement=true')
    subprocess.run(command,cwd=ROOT,env=env,check=True)
    generated=probe/'build/native-api';cp=(generated/'classpath.txt').read_text().strip()
    request='requested-native-api-supplement.txt' if args.supplement else 'requested-native-api-classes.txt'
    classes=(P/request).read_text().splitlines();index=[]
    if args.supplement:
        resolved=json.loads((generated/'resolved-artifacts.json').read_text())
        jei=[a for a in resolved['artifacts'] if a['coordinate']=='mezz.jei:jei-1.16.5:7.8.1.1018']
        if len(jei)!=1 or jei[0]['sha256']!='e5b8c8f45db74124fe35e76006c37c6bb07cfd5e9a89cddc9c868c5ae477d188':
            raise ValueError('Pinned JEI7 api publication hash differs')
    for name in classes:
        if not name or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.$' for c in name):raise ValueError('Invalid requested native class')
        filename=name.replace('.','/')+'.class';origins=[]
        for item in cp.split(os.pathsep):
            path=Path(item)
            if path.is_file() and path.suffix=='.jar':
                with zipfile.ZipFile(path) as jar:
                    if filename in jar.namelist():origins.append({'archive':str(path),'classSha256':hashlib.sha256(jar.read(filename)).hexdigest()})
        if len(origins)>1:raise ValueError('Native class has competing archive owners: '+name)
        result=subprocess.run([home+'/bin/javap','-classpath',cp,'-p','-s','-c',name],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,check=False)
        dest=out/(name+'.javap.txt');dest.write_text(result.stdout)
        declared=re.search(r'(?m)^(?:public |protected |private |abstract |final |static )*(?:class|interface|enum) ([^ <]+)',result.stdout)
        if result.returncode==0 and (not origins or declared is None or declared.group(1)!=name):
            raise ValueError('Declaration resolved without exact binary-name origin identity: '+name)
        if result.returncode!=0 and origins:raise ValueError('Owned class declaration failed: '+name)
        index.append({'class':name,'status':'FOUND' if result.returncode==0 and origins else 'ABSENT',
                      'origins':origins,'declarationsSha256':hashlib.sha256(dest.read_bytes()).hexdigest()})
    shutil.copyfile(generated/'resolved-artifacts.json',out/'resolved-artifacts.json')
    (out/'declaration-index.json').write_text(json.dumps({'source':os.environ['GITHUB_SHA'],'classes':index,
        'requestFile':request,'requestSha256':hashlib.sha256((P/request).read_bytes()).hexdigest(),
        'supplementOnly':args.supplement,'nativeFeatureCompiled':False,'gameExecuted':False},indent=2)+'\n')
    print(json.dumps({'requested':len(classes),'found':sum(x['status']=='FOUND' for x in index),'gameExecuted':False}))
if __name__=='__main__':main()
