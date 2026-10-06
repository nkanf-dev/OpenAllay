#!/usr/bin/env python3
"""Build only declaration-changed Builder; retained Java8 SDK is never rebuilt."""
import hashlib,json,os,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SOURCE='fe6422454450ceb739d1174d87c681e76df1ab84'
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote only')
    out=ROOT/'build/forge16165-builder-candidate';out.mkdir(parents=True,exist_ok=False)
    a=json.loads(subprocess.check_output(['gh','api','repos/'+os.environ['GITHUB_REPOSITORY']+'/actions/artifacts/11406765949']))
    if a['expired'] or a['workflow_run']['id']!=37450040748 or a['digest']!='sha256:6d1ac947f59d983ea1b88db15d456cec3b7ddffd2b8f26320836a5b04de83802':raise ValueError('SDK provider mismatch')
    archive=out/'retained.zip'
    with archive.open('xb') as f:subprocess.run(['gh','api',a['archive_download_url']],stdout=f,check=True)
    if sha(archive)!='6d1ac947f59d983ea1b88db15d456cec3b7ddffd2b8f26320836a5b04de83802':raise ValueError('Retained archive changed')
    sdk=out/'sdk.jar'
    with zipfile.ZipFile(archive) as z:
        candidates=[n for n in z.namelist() if n.endswith('/sdk.jar') or n=='sdk.jar']
        if len(candidates)!=1:raise ValueError('SDK sole artifact')
        sdk.write_bytes(z.read(candidates[0]))
    if sha(sdk)!='53fffa91059247a6f191f6ed77d1e7e74ac78318d0122c9ff503e2fcde18590a':raise ValueError('SDK unchanged pin')
    source=out/'source';subprocess.run(['git','clone','--filter=blob:none','--no-checkout','https://github.com/nkanf-dev/OpenAllay-Extensions.git',str(source)],check=True)
    subprocess.run(['git','-C',str(source),'checkout','--detach',SOURCE],check=True)
    project=source/'extensions/minecraft-builder'
    subprocess.run([str(project/'gradlew'),'--max-workers=2','--stacktrace','-p',str(project),'-PopenallayExtensionApiJar='+str(sdk),':universal:shadowJar','verifyUniversalPackage'],check=True)
    jar=project/'universal/build/libs/openallay-builder-universal-0.4.0.jar';target=out/'builder-candidate.jar';target.write_bytes(jar.read_bytes())
    with zipfile.ZipFile(target) as z:
        desc=json.loads(z.read('META-INF/openallay-extension.json'))
        if not any(t['loader']=='forge' and t['minecraftVersionRange']=='1.16.5' for t in desc['support']['targets']) or desc['support']['validatedTargetIds']:raise ValueError('Candidate support proof')
        for n in z.namelist():
            if n.endswith('.class') and int.from_bytes(z.read(n)[6:8],'big')!=52:raise ValueError('Builder must Java8')
    (out/'builder-candidate.json').write_text(json.dumps({'source':SOURCE,'coreRunnerSource':os.environ['GITHUB_SHA'],'jarSha256':sha(target),'retainedSdkSha256':sha(sdk),'descriptor':desc,'sdkRebuilt':False,'nativeExecuted':False},indent=2)+'\n')
if __name__=='__main__':main()
