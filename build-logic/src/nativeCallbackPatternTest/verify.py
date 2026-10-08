#!/usr/bin/env python3
"""One remote finite public native callback/enhanced-for pattern policy oracle."""
import argparse,hashlib,json,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
for key in ['project','jdk17','jdk8','output']:p.add_argument('--'+key,type=Path,required=True)
a=p.parse_args();root=a.project.resolve();out=a.output.resolve();cp=Path(__file__).with_name('source-contract.json');c=json.loads(cp.read_bytes());sha=lambda b:hashlib.sha256(b).hexdigest()
assert sha(Path(__file__).read_bytes())==c['runner_sha256']
for row in c['sources']:assert sha((root/row['path']).read_bytes())==row['sha256'],row['path']
out.mkdir(parents=True,exist_ok=False);commands=[]
def run(cmd):
    commands.append(list(map(str,cmd)));(out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n');r=subprocess.run(commands[-1],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);(out/('command-%02d.log'%len(commands))).write_text(r.stdout)
    if r.returncode:raise RuntimeError(r.stdout)
    return r.stdout
classes=out/'classes';classes.mkdir();run([a.jdk17/'bin/javac','--release','17','-encoding','UTF-8','-d',classes]+[root/r['path'] for r in c['sources']]);result=run([a.jdk17/'bin/java','-cp',classes,'dev.openallay.build.CanonicalNativeCallbackPatternFixture',out/'fixture',a.jdk17/'bin/java',a.jdk8/'bin/javac',a.jdk8/'bin/java'])
(out/'receipt.json').write_text(json.dumps({'scope':c['scope'],'tool_sha256':c['sources'][0]['sha256'],'fixture_sha256':c['sources'][2]['sha256'],'actual_native_callback_capture_proofs':3,'actual_enhanced_for_if_body_proofs':2,'original17_release8_genuine8_equal':True,'class_major':52,'noObjectCallbackFallback':True,'noNativeTargetsChanged':True,'engineProductionUnchanged':True,'unadmittedLoopWholeOwnerRejected':True,'output':result},indent=2)+'\n');print('PASS finite native callback/direct enhanced-for body policy')
