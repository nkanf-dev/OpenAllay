#!/usr/bin/env python3
"""One authentic native harness text-block source candidate; public javac literal value, no FG or game."""
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import subprocess

OWNER='common/src/main/java/dev/openallay/guide/e2e/GuideNativeCommandE2EProbe.java'
TOOL='build-logic/src/main/java/dev/openallay/build/CanonicalTextBlockPort.java'

def sha(blob):return hashlib.sha256(blob).hexdigest()

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for flag in ['project','java','javac','output']:p.add_argument('--'+flag,type=Path,required=True)
    a=p.parse_args();root=a.project.resolve();out=a.output.resolve()
    if out.exists() or out==root or root in out.parents:raise ValueError('Fresh external source candidate output required')
    out.mkdir(parents=True);source=root/OWNER;before=source.read_bytes();tool=root/TOOL;tools=out/'tools';tools.mkdir()
    selected=out/'selected.txt';selected.write_text(OWNER+'\n')
    commands=[]
    def run(command,label):
        commands.append([str(c) for c in command]);(out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
        with (out/(label+'.log')).open('wb') as log:subprocess.run(commands[-1],cwd=root,stdout=log,stderr=subprocess.STDOUT,check=True)
    run([a.javac,'--release','17','-encoding','UTF-8','-d',tools,tool],'actual-textblock-tool-compile')
    run([a.java,'-cp',tools,'dev.openallay.build.CanonicalTextBlockPort',root,selected,out/'actual-literal'],'actual-public-literal-value-conversion')
    raw=(out/'actual-literal/post'/OWNER).read_bytes();text=raw.decode('utf-8')
    marker='String source = '
    start=text.index(marker)+len(marker)
    if text[start]!='"':raise ValueError('Expected authentic converter quoted String source expression')
    end=start+1
    while end<len(text):
        if text[end]=='\\':end+=2;continue
        if text[end]=='"':end+=1;break
        end+=1
    suffix='.formatted(token, token)'
    if text[end:end+len(suffix)]!=suffix:raise ValueError('Exact original harness format argument shape changed')
    quoted=text[start:end]
    after=(text[:start]+'dev.openallay.util.Java8ApiSupport.formatted('+quoted+', token, token)'+text[end+len(suffix):]).encode('utf-8')
    if source.read_bytes()!=before:raise ValueError('Actual canonical source changed during source generation')
    packet=out/'source-packet'
    for side,blob in [('pre',before),('post',after)]:
        path=packet/side/OWNER;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(blob)
    patch=''.join(difflib.unified_diff(before.decode().splitlines(True),after.decode().splitlines(True),fromfile='a/'+OWNER,tofile='b/'+OWNER))
    (packet/'source.patch').write_text(patch)
    (packet/'manifest.json').write_text(json.dumps({'scope':'One actual native command harness source textblock and format call only',
        'files':[{'path':OWNER,'preSha256':sha(before),'postSha256':sha(after),'preBytes':len(before),'postBytes':len(after)}],
        'authenticLiteralPostSha256':sha(raw),'publicASTToolSha256':sha(tool.read_bytes()),'patchSha256':sha(patch.encode()),
        'actualJavaCompilerStringLiteralValueUsed':True,'manualDedent':False,'formatArgumentOrder':['token','token'],
        'formatHelper':'dev.openallay.util.Java8ApiSupport.formatted','nativeCompileAcceptance':False,'gameExecuted':False},indent=2)+'\n')

if __name__=='__main__':main()
