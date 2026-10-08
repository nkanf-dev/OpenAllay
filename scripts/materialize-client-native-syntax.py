#!/usr/bin/env python3
"""Remote public AST completeclient syntax materialization; no downloads/native state."""
import argparse,base64,difflib,hashlib,json,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
for key in ['project','javac','java','output']:p.add_argument('--'+key,type=Path,required=True)
a=p.parse_args();root=a.project.resolve();out=a.output.resolve();cp=root/'engine-core/src/clientNativeSyntaxTest/source-contract.json';c=json.loads(cp.read_text());sha=lambda b:hashlib.sha256(b).hexdigest()
assert sha(Path(__file__).read_bytes())==c['driver_sha256'];assert sha((root/c['tool']['path']).read_bytes())==c['tool']['sha256']
for row in c['owners']:assert sha((root/row['path']).read_bytes())==row['sha256'],row['path']
out.mkdir(parents=True,exist_ok=False);commands=[]
def run(cmd):
    commands.append(list(map(str,cmd)));(out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
    r=subprocess.run(commands[-1],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);(out/('command-%02d.log'%len(commands))).write_text(r.stdout)
    if r.returncode:raise RuntimeError(r.stdout)
classes=out/'classes';classes.mkdir();run([a.javac,'--release','17','-encoding','UTF-8','-d',classes,root/c['tool']['path']])
run([a.java,'-cp',classes,'dev.openallay.build.ClientNativeSyntaxMaterializer',root,c['owners'][0]['sha256'],c['owners'][1]['sha256'],out/'materialized',c['helper_base64']])
packet=out/'source-packet';packet.mkdir();rows=[];patch=''
for line in (out/'materialized/owner-hashes.tsv').read_text().splitlines():
    name,pre,post,size=line.split('\t');before=(out/'materialized/pre'/name).read_bytes();after=(out/'materialized/post'/name).read_bytes()
    assert sha(before)==pre and sha(after)==post and len(after)==int(size)
    for side,blob in [('pre',before),('post',after)]:
        path=packet/side/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(blob)
    rows.append({'path':name,'pre_sha256':pre,'post_sha256':post,'post_bytes':len(after)})
    patch+=''.join(difflib.unified_diff(before.decode().splitlines(True),after.decode().splitlines(True),fromfile='a/'+name,tofile='b/'+name))
assert len(rows)==2
literals=[]
for line in (out/'materialized/literal-hashes.tsv').read_text().splitlines():
    name,digest,size,chars=line.split('\t');blob=(out/'materialized/literal-values'/(name+'.txt')).read_bytes();assert sha(blob)==digest and len(blob)==int(size)
    literals.append({'name':name,'sha256':digest,'utf8_bytes':int(size),'utf16_chars':int(chars),'original_public_AST_equals_lowered_literal_expression':True})
(packet/'source.patch').write_text(patch);(packet/'manifest.json').write_text(json.dumps({'scope':c['scope'],'files':rows,'patch_sha256':sha(patch.encode()),'sourceContractSha256':sha(cp.read_bytes()),'literalValues':literals,'realCompleteOwnerCount':2,'privateHelperMovedToOnlyActualCaller':True,'recipePublicFinalSchemaABIUnchanged':True,'noProductionAlgorithmChanges':True,'requiredAcceptance':'Root sourcecustody review thenactualnative modern tests; lateractual702Java8diagnostic handles remainingAPIs/fullclosure','wholeClientJava8Accepted':False},indent=2)+'\n');print('PASS actualcompleteclient syntax sourcepacket '+str(packet))
