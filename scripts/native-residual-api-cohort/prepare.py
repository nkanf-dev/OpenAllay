#!/usr/bin/env python3
"""One source-only residual API stage: full251 actual native type universe, 18 current owners."""
import argparse,hashlib,json,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
for name in ['project','native-output','javac','java','true-javac8','true-java8','output']:p.add_argument('--'+name,type=Path,required=True)
a=p.parse_args();project=a.project.resolve();contract_path=Path(__file__).with_name('source-contract.json');c=json.loads(contract_path.read_bytes());sha=lambda b:hashlib.sha256(b).hexdigest();assert sha(Path(__file__).read_bytes())==c['runner_sha256']
for i in c['pins']:assert sha((project/i['path']).read_bytes())==i['sha256'],'Actual API/helper/fixture/producer differs '+i['path']
a.output.mkdir(parents=True,exist_ok=False)
def run(command,label):
 r=subprocess.run(list(map(str,command)),cwd=project,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(a.output/(label+'.log')).write_bytes(r.stdout+r.stderr)
 if r.returncode:raise RuntimeError(label+' '+(r.stdout+r.stderr).decode(errors='replace'))
 return r
# Small composition-only public symbol oracle, accepted helper-family/runtime proofs are not replayed.
classes=a.output/'fixture-classes';classes.mkdir()
run([a.javac,'--release','17','-d',classes,project/'build-logic/src/main/java/dev/openallay/build/CanonicalJava8ApiPort.java',project/'scripts/api-language-port/CanonicalNativeResidualApiFixture.java'],'api-symbol-fixture-compile')
run([a.java,'-cp',classes,'dev.openallay.build.CanonicalNativeResidualApiFixture',a.output/'api-symbol-fixture',a.java,a.true_javac8,a.true_java8,project/'engine-core/src/main/java/dev/openallay/util'],'api-symbol-fixture-three-tiers')
facts=a.output/'fact-classes';facts.mkdir();support=[project/'engine-core/src/main/java/dev/openallay/util/Java8ApiSupport.java',project/'engine-core/src/main/java/dev/openallay/util/Java8Collections.java',project/'scripts/api-language-port/RuntimeFeatureJava8Fixture.java']
run([a.javac,'--release','8','-d',facts]+support,'runtime-fact-compile')
run([a.java,'-cp',facts,'dev.openallay.util.RuntimeFeatureJava8Fixture'],'runtime-fact-modern')
run([a.true_java8,'-cp',facts,'dev.openallay.util.RuntimeFeatureJava8Fixture'],'runtime-fact-true8')
# Resolve exact logical/native owners from the normal FG-selected complete source universe, never filename aliases.
rows=[line.split('\t') for line in (a.native_output/'units.tsv').read_text().splitlines()];assert len(rows)==c['expected_source_count']==251 and all(len(row)==4 for row in rows)
selected=[];matches={name:[] for name in c['selected_simple_names']}
for row in rows:
 name=Path(row[1]).name
 if name in matches:matches[name].append(row)
for name,owners in matches.items():
 assert len(owners)==1,'Exact selected native logical owner differs '+name
 selected.append(owners[0][1])
request=a.output/'selected-residual-owners.txt';request.write_text('\n'.join(sorted(selected))+'\n')
(a.output/'selected-source-custody.json').write_text(json.dumps([{'logicalPath':row[1],'physicalPath':row[3],'sha256':sha(Path(row[3]).read_bytes())} for owners in matches.values() for row in owners],indent=2)+'\n')
run(['python3',project/'scripts/materialize-native-language-cohort.py','--project',project,'--native-output',a.native_output,'--javac',a.javac,'--java',a.java,'--output',a.output/'actual-api-cohort','--selected-owners',request,'--api-only'],'one-full-native-api-cohort')
print('PREPARED one residual native API18 batch with whole251 type/projection roundtrip; runtime/game acceptance pending')
