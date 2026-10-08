#!/usr/bin/env python3
"""Remote exact canonical constructor-type delta, output equivalence and genuine owner Java8 oracle."""
import argparse, hashlib, json, os, re, subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
for name in ['source-root','jdk17','jdk8','baseline-packet','output']:p.add_argument('--'+name,type=Path,required=True)
a=p.parse_args();root=a.source_root.resolve();out=a.output.resolve();contract_path=Path(__file__).parent/'source-contract.json';c=json.loads(contract_path.read_text());sha=lambda b:hashlib.sha256(b).hexdigest()
assert sha(Path(__file__).read_bytes())==c['runner_sha256']
for item in [c['converter'],c['probe'],c['fixture']]+c['support']:assert sha((root/item['path']).read_bytes())==item['sha256'],item['path']
assert sha((a.baseline_packet/'manifest.json').read_bytes())==c['baseline_manifest_sha256']
out.mkdir(parents=True,exist_ok=False);commands=[]
def run(cmd):
    commands.append(list(map(str,cmd)));(out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
    r=subprocess.run(commands[-1],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    (out/('command-%02d.log'%len(commands))).write_text(r.stdout)
    if r.returncode:raise RuntimeError(r.stdout)
    return r.stdout
java17,javac17=a.jdk17/'bin/java',a.jdk17/'bin/javac';java8,javac8=a.jdk8/'bin/java',a.jdk8/'bin/javac'
versions={name:run([path,'-version']) for name,path in [('java8',java8),('javac8',javac8),('java17',java17)]}
assert '1.8.' in versions['java8'] and '1.8.' in versions['javac8']
tools=out/'tools';tools.mkdir();run([javac17,'--release','17','-d',tools,root/c['converter']['path'],root/c['probe']['path']]);run([java17,'-cp',tools,'dev.openallay.build.RecordConstructorTypeFixture'])
# Replay source conversion only for complete formerlysupported owners; no accepted runtime repetition.
passes={}
for row in c['equivalence']:
    pre=a.baseline_packet/'pre'/'engine-core/src/main/java'/row['path'];assert sha(pre.read_bytes())==row['pre_sha256']
    for record in row['recordPaths']:passes.setdefault(record.count('.'),{}).setdefault(row['path'],[]).append(record)
equivalent={};previous={row['path']:a.baseline_packet/'pre'/'engine-core/src/main/java'/row['path'] for row in c['equivalence']}
for depth in sorted(passes,reverse=True):
    request=out/('equivalence-%d.tsv'%depth)
    request.write_text(''.join(str(previous[path])+'\t'+path+'\t'+sha(previous[path].read_bytes())+'\t'+','.join(records)+'\n' for path,records in sorted(passes[depth].items())))
    stage=out/('equivalence-%d'%depth);run([java17,'-cp',tools,'dev.openallay.build.RecordValueSourceConverter',request,stage])
    for path in passes[depth]:previous[path]=stage/'candidate'/path
for row in c['equivalence']:
    digest=sha(previous[row['path']].read_bytes());assert digest==row['converterRawPostSha256'],'Changed former supported output: '+row['path'];equivalent[row['path']]=digest
# Actual whole production owner is materialized nestedfirst, then outer; never handwritten.
owner=c['owner'];original=out/'original'/owner['path'];original.parent.mkdir(parents=True);blob=subprocess.run(['git','-C',str(root),'cat-file','blob',owner['git_blob']],check=True,stdout=subprocess.PIPE).stdout
assert sha(blob)==owner['sha256'] and sha((root/owner['path']).read_bytes())==owner['sha256'];original.write_bytes(blob);actual=original
for depth,records in [(1,['SettingsDiagnosticCard.Metric']),(0,['SettingsDiagnosticCard'])]:
    request=out/('owner-%d.tsv'%depth);request.write_text(str(actual)+'\t'+owner['path']+'\t'+sha(actual.read_bytes())+'\t'+','.join(records)+'\n');stage=out/('owner-%d'%depth)
    run([java17,'-cp',tools,'dev.openallay.build.RecordValueSourceConverter',request,stage]);actual=stage/'candidate'/owner['path']
raw=actual.read_bytes();source=raw.decode()
for edit in c['api_lowering']:
    assert edit['old'] in source;source=source.replace(edit['old'],edit['new'])
source=re.sub(r'(?m)^[ \t]+$','',source);post=out/'candidate'/owner['path'];post.parent.mkdir(parents=True);post.write_text(source)
import difflib
(out/'diagnostic-card-java8.patch').write_text(''.join(difflib.unified_diff(blob.decode().splitlines(True),source.splitlines(True),fromfile='a/'+owner['path'],tofile='b/'+owner['path'])))
reports={};majors={}
for flavor in ['modern','release8','java8']:
    classes=out/(flavor+'-classes');classes.mkdir();compiler=javac8 if flavor=='java8' else javac17;target=['-source','8','-target','8'] if flavor=='java8' else ['--release','17' if flavor=='modern' else '8']
    run([compiler]+target+['-encoding','UTF-8','-sourcepath','','-d',classes,original if flavor=='modern' else post,root/c['fixture']['path']]+[root / item['path'] for item in c['support']])
    majors[flavor]={}
    for path in classes.rglob('*.class'):
        b=path.read_bytes();assert b[:4]==b'\xca\xfe\xba\xbe';major=int.from_bytes(b[6:8],'big');majors[flavor][str(path.relative_to(classes))]=major
        if flavor!='modern':assert major==52
    reports[flavor]=run([java17 if flavor=='modern' else java8,'-cp',classes,'dev.openallay.settings.diagnostics.MetricBoxingJava8Fixture'])
assert reports['modern']==reports['release8']==reports['java8'],'Actual boxing owner semantics differ'
receipt={'scope':c['scope'],'original_owner_sha256':sha(blob),'converter_raw_post_sha256':sha(raw),'lowered_post_sha256':sha(post.read_bytes()),'exact_api_lowering':c['api_lowering'],'converter_previous_sha256':c['previous_converter_sha256'],'converter_new_sha256':c['converter']['sha256'],'former_supported_wholeowners_unchanged':equivalent,'genuine_jdk_versions':versions,'class_majors':majors,'exact_original_modern_java8_vectors_equal':True,'vectors':len(reports['modern'].splitlines()),'actual_production_sources':6,'schemas':2,'whole_engine_java8_accepted':False}
(out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print('PASS exact primitive boxing overload detection, 63 formerlysupported wholeowner outputs unchanged, actual whole diagnostic owner original-modern/genuine8 ABI/hash')
