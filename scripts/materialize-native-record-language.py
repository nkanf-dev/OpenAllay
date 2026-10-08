#!/usr/bin/env python3
"""One actual selected-native record language candidate packet; existing accepted converter only."""
import argparse
import base64
import difflib
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]

def sha(blob):return hashlib.sha256(blob).hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for flag in ['project','javac','java','output']:parser.add_argument('--'+flag,type=Path,required=True)
    parser.add_argument('--units',type=Path)
    parser.add_argument('--expected-selection',type=Path,help='Retained normal FG source-selection receipt when available')
    a=parser.parse_args();project=a.project.resolve();output=a.output.resolve()
    if output==project or project in output.parents or output.exists():raise ValueError('Fresh external candidate output required')
    output.mkdir(parents=True)
    if a.units is None:
        helper_path=project/'scripts/forge1122_source_selection.py'
        spec=importlib.util.spec_from_file_location('native_selection',helper_path)
        helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
        owners,receipt=helper.selected_native_units(project)
        if a.expected_selection:
            actual=json.loads(a.expected_selection.read_text())
            expected={(row['owner'],row['logicalPath'],row['sha256']) for row in actual['java']}
            projected={(row['owner'],row['logicalPath'],row['sha256']) for row in owners}
            if projected!=expected:raise ValueError('Projection differs from actual retained native source receipt')
            receipt['actualSelectionReceiptSha256']=sha(a.expected_selection.read_bytes())
        a.units=output/'original-units.tsv'
        a.units.write_text('\n'.join('\t'.join([row['owner'],row['logicalPath'],row['mode'],row['path']]) for row in owners)+'\n')
        (output/'source-selection-projection.json').write_text(json.dumps(receipt,indent=2)+'\n')
    selected=[]
    for row in a.units.read_text().splitlines():
        cells=row.split('\t')
        if len(cells)!=4:raise ValueError('Exact original selected source units required')
        path=Path(cells[3]).resolve()
        if not path.is_relative_to(project):raise ValueError('Foreign source owner')
        blob=path.read_bytes();selected.append({'path':path.relative_to(project).as_posix(),'sha256':sha(blob),'bytes':len(blob)})
    if len({r['path'] for r in selected})!=len(selected):raise ValueError('Duplicate physical source owner')
    tools=output/'tool-classes';tools.mkdir();commands=[]
    tool_paths=[project/'build-logic/src/main/java/dev/openallay/build'/name for name in
        ['RecordValueSourceConverter.java','CanonicalRecordBatchMaterializer.java','NativeRecordOwnerRequest.java']]
    def run(command,label):
        commands.append([str(c) for c in command]);(output/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
        with (output/(label+'.log')).open('wb') as log:subprocess.run(commands[-1],cwd=project,stdout=log,stderr=subprocess.STDOUT,check=True)
    run([a.javac,'--release','17','-encoding','UTF-8','-d',tools,*tool_paths],'tool-compile')
    request=output/'records.tsv'
    run([a.java,'-cp',tools,'dev.openallay.build.NativeRecordOwnerRequest',project,a.units,request],'exact-record-discovery')
    run([a.java,'-cp',tools,'dev.openallay.build.CanonicalRecordBatchMaterializer',project,request,output/'materialized'],'existing-record-materializer')
    packet=output/'source-packet';packet.mkdir();patch='';statuses=[];files=[]
    for line in (output/'materialized/owner-status.tsv').read_text().splitlines():
        cells=line.split('\t');name,state,count=cells[:3]
        if state=='REJECTED':
            statuses.append({'path':name,'state':state,'selectedRecordCount':int(count),'reason':base64.b64decode(cells[4]).decode()});continue
        if state!='SUPPORTED':raise ValueError('Unknown actual converter classification')
        before=(output/'materialized/pre'/name).read_bytes();after=(output/'materialized/post'/name).read_bytes()
        if (project/name).read_bytes()!=before:raise ValueError('Original native owner changed')
        for side,blob in [('pre',before),('post',after)]:
            path=packet/side/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(blob)
        patch+=''.join(difflib.unified_diff(before.decode().splitlines(True),after.decode().splitlines(True),fromfile='a/'+name,tofile='b/'+name))
        files.append({'path':name,'preSha256':sha(before),'postSha256':sha(after),'preBytes':len(before),'postBytes':len(after)})
        statuses.append({'path':name,'state':state,'selectedRecordCount':int(count),'convertedRecordCount':int(cells[3])})
    for r in selected:
        if sha((project/r['path']).read_bytes())!=r['sha256']:raise ValueError('Exact native source closure changed')
    (packet/'source.patch').write_text(patch)
    (packet/'manifest.json').write_text(json.dumps({'scope':'whole exact selected native original owners, record language candidate only',
        'selectedSources':selected,'sourceUnitCount':len(selected),'files':files,'ownerStatuses':statuses,
        'toolPins':[{'path':p.relative_to(project).as_posix(),'sha256':sha(p.read_bytes())} for p in tool_paths],
        'patchSha256':sha(patch.encode()),'runtimeAcceptance':False,'sourceRewrite':False,'featureAlgorithmsCopied':False,
        'pending':'Actual native Java8 compile/API/type attribution; rejected owners unchanged'},indent=2)+'\n')

if __name__=='__main__':main()
