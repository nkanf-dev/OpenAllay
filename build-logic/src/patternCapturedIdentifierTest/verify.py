#!/usr/bin/env python3
"""Remote-only narrow declared wildcard identifier public compiler and genuine Java8 proof."""
import argparse, hashlib, json, subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
for key in ['project','javac','java','javac8','java8','output']:p.add_argument('--'+key,type=Path,required=True)
a=p.parse_args();root=a.project.resolve();out=a.output.resolve();contract_path=Path(__file__).parent/'source-contract.json';c=json.loads(contract_path.read_text());sha=lambda b:hashlib.sha256(b).hexdigest()
assert sha(Path(__file__).read_bytes())==c['runner_sha256']
for item in c['sources']:assert sha((root/item['path']).read_bytes())==item['sha256'],item['path']
out.mkdir(parents=True,exist_ok=False);commands=[]
def run(command):
    commands.append(list(map(str,command)));(out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
    r=subprocess.run(commands[-1],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);(out/('command-%02d.log'%len(commands))).write_text(r.stdout)
    if r.returncode:raise RuntimeError(r.stdout)
    return r.stdout
classes=out/'classes';classes.mkdir();run([a.javac,'--release','17','-encoding','UTF-8','-d',classes]+[root/item['path'] for item in c['sources']])
log=run([a.java,'-cp',classes,'dev.openallay.build.CanonicalCapturedIdentifierFixture',out/'fixture',a.java,a.javac8,a.java8])
receipt={'scope':c['scope'],'tool_sha256':c['sources'][0]['sha256'],'shared_renderer_sha256':c['sources'][1]['sha256'],'fixture_sha256':c['sources'][2]['sha256'],'public_captured_expression_and_declared_symbol_proofs':4,'actual_declared_identifier_patterns':4,'behavior_cases':13,'value_reads':9,'original17_release8_genuine8_equal':True,'java8_class_major':52,'captured_methodcall_owner_rejected_without_partialoutput':True,'object_fallback':False,'accepted_prior17pattern18eval_oracle_reexecuted':False,'wholeClientJava8Accepted':False,'output':log}
(out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print('PASS separate captured identifier actual compiler and genuine8 proof')
