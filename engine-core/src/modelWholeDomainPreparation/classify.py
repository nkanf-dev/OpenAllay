#!/usr/bin/env python3
"""Remote whole current model domain API->pattern->switch public attributed pipeline."""
import argparse,base64,difflib,hashlib,json,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
for key in ['source-root','production-classpath-file','jdk17','output']:p.add_argument('--'+key,type=Path,required=True)
a=p.parse_args();root=a.source_root.resolve();source=root/'engine-core/src/main/java';out=a.output.resolve();contract_path=Path(__file__).with_name('source-contract.json');c=json.loads(contract_path.read_bytes());sha=lambda b:hashlib.sha256(b).hexdigest()
assert out!=root and root not in out.parents
assert sha(Path(__file__).read_bytes())==c['runner_sha256']
for row in c['tools']:assert sha((root/row['path']).read_bytes())==row['sha256'],row['path']
actual={str(p.relative_to(source)):p for p in source.rglob('*.java')};assert set(actual)=={r['path'] for r in c['full_sources']}
for row in c['full_sources']:assert sha(actual[row['path']].read_bytes())==row['sha256'],row['path']
selected=sorted(path for path in actual if path.startswith('dev/openallay/model/'));assert selected==c['selected_owners'] and len(selected)==68
cp=a.production_classpath_file.read_text().strip();assert cp
classpath=[]
for entry in cp.split(os.pathsep):
    path=Path(entry);assert path.exists();classpath.append({'path':str(path),'sha256':sha(path.read_bytes()) if path.is_file() else None,'directory':path.is_dir()})
out.mkdir(parents=True,exist_ok=False);commands=[]
def run(command):
    commands.append(list(map(str,command)));(out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n');r=subprocess.run(commands[-1],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    (out/('command-%02d.log'%len(commands))).write_bytes(r.stdout)
    if r.returncode:raise RuntimeError(r.stdout.decode(errors='replace'))
# One remote disposable full-source fork; no local copies or production edits.
staged=out/'staged-full-source'
for name,path in actual.items():
    destination=staged/name;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(path.read_bytes())
tools=out/'tools';tools.mkdir();run([a.jdk17/'bin/javac','--release','17','-encoding','UTF-8','-d',tools]+[root/r['path'] for r in c['tools']])
request=out/'selected.txt';request.write_text('\n'.join(selected)+'\n');stages=[];rejected={}
for kind,clazz in [('api','CanonicalJava8ApiPort'),('pattern','CanonicalPatternPort'),('switch','CanonicalSwitchPort')]:
    stage=out/(kind+'-materialized');run([a.jdk17/'bin/java','-cp',tools,'dev.openallay.build.'+clazz,staged,cp,request,stage]);rows=[]
    for line in (stage/'owner-status.tsv').read_text().splitlines():
        name,status,count,reason=line.split('\t');assert name in selected;row={'path':name,'status':status,'sites':int(count)}
        if status=='REJECTED':
            row['reason']=base64.b64decode(reason).decode();assert not(stage/'post'/name).exists();rejected.setdefault(name,[]).append({'phase':kind,'reason':row['reason']})
        else:
            assert status=='SUPPORTED';before=(stage/'pre'/name).read_bytes();after=(stage/'post'/name).read_bytes();assert before==(staged/name).read_bytes();row.update(pre_sha256=sha(before),post_sha256=sha(after),changed=before!=after)
            # Any phase reject holds the complete original owner; no composed partial target.
            if name not in rejected:(staged/name).write_bytes(after)
        rows.append(row)
    assert len(rows)==len(selected) and {r['path'] for r in rows}==set(selected)
    stages.append({'phase':kind,'owners':rows,'full_stage_source_hashes':{name:sha((staged/name).read_bytes()) for name in sorted(actual)}})
packet=out/'source-packet';packet.mkdir();files=[];statuses=[];patch=''
for name in selected:
    original=actual[name].read_bytes()
    if name in rejected:
        statuses.append({'path':name,'status':'REJECTED','reasons':rejected[name]});continue
    post=(staged/name).read_bytes();statuses.append({'path':name,'status':'SUPPORTED','changed':post!=original})
    if post==original:continue
    logical='engine-core/src/main/java/'+name
    for side,blob in [('pre',original),('post',post)]:
        path=packet/side/logical;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(blob)
    files.append({'path':logical,'pre_sha256':sha(original),'post_sha256':sha(post),'pre_bytes':len(original),'post_bytes':len(post)})
    patch+=''.join(difflib.unified_diff(original.decode().splitlines(True),post.decode().splitlines(True),fromfile='a/'+logical,tofile='b/'+logical))
for row in c['full_sources']:assert sha(actual[row['path']].read_bytes())==row['sha256'],'Actualsource drift'
(packet/'source.patch').write_text(patch);receipt={'scope':c['scope'],'actualProductionSourceCount':len(actual),'selectedCompleteModelOwners':68,'stages':stages,'owners':statuses,'files':files,'patch_sha256':sha(patch.encode()),'contract_sha256':sha(contract_path.read_bytes()),'production_classpath':classpath,'acceptedToolOraclesReexecuted':False,'wholeEngineJava8Accepted':False,'requiredAcceptance':'Root actualwholeowner post/source review andnative modern gate; rejectedwholeowners remainoriginal,no manualpartialapply; nextactual702Java8compile revealsAPI frontier'}
(packet/'manifest.json').write_text(json.dumps(receipt,indent=2)+'\n');print('PASS wholemodel68 API->pattern->switch actualfullproduction reattribution sourcepacket '+str(packet))
