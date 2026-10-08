#!/usr/bin/env python3
"""One complete actual native251 language candidate job: real public compiler tools, no feature copies."""
import argparse
import base64
import difflib
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess


def sha(blob):return hashlib.sha256(blob).hexdigest()

def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n')

def apply_var(raw,sites):
    text=raw.decode('utf-8');utf=text.encode('utf-16-le')
    for start,type_name in sorted(sites,reverse=True):
        offset=start*2
        if utf[offset:offset+6]!='var'.encode('utf-16-le'):raise ValueError('Public attributed var span no longer exact')
        utf=utf[:offset]+type_name.encode('utf-16-le')+utf[offset+6:]
    return utf.decode('utf-16-le').encode('utf-8')

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for flag in ['project','native-output','javac','java','output']:p.add_argument('--'+flag,type=Path,required=True)
    a=p.parse_args();project=a.project.resolve();native=a.native_output.resolve();out=a.output.resolve()
    if out.exists() or out==project or project in out.parents:raise ValueError('Fresh external whole-source candidate directory required')
    out.mkdir(parents=True);cp=(native/'classpath.txt').read_text().splitlines();classpath=os.pathsep.join(cp)
    unitrows=[row.split('\t') for row in (native/'units.tsv').read_text().splitlines()]
    if any(len(row)!=4 for row in unitrows):raise ValueError('Exact normal native source owner plan required')
    originals={row[1]:Path(row[3]).read_bytes() for row in unitrows}
    sources=native/'generated-java';working=out/'whole-normalized';shutil.copytree(sources,working)
    expected=set(originals)
    if {p.relative_to(working).as_posix() for p in working.rglob('*.java')}!=expected:raise ValueError('Whole exact native source universe required')
    commands=[]
    def run(command,label,fail=True):
        commands.append([str(c) for c in command]);write(out/'commands.json',commands)
        with (out/(label+'.log')).open('wb') as log:result=subprocess.run(commands[-1],cwd=project,stdout=log,stderr=subprocess.STDOUT,check=False)
        write(out/(label+'.json'),{'exitCode':result.returncode,'command':commands[-1]})
        if fail and result.returncode:raise ValueError('Actual whole-source tool failed: '+label)
        return result.returncode
    def compile_current(label):
        argsfile=out/(label+'.args');argsfile.write_text('\n'.join(json.dumps(str(p)) for p in sorted(working.rglob('*.java')))+'\n')
        target=out/(label+'-classes');target.mkdir()
        return run([a.javac,'--release','17','-proc:none','-encoding','UTF-8','-Xmaxerrs','10000','-classpath',classpath,'-d',target,'@'+str(argsfile)],label)
    compile_current('actual-modern17-whole-native-before-language')
    toolnames=['AttributedVarTypes','CanonicalVarTypePort','CanonicalPatternPort','CanonicalSwitchPort','CanonicalJava8ApiPort','NativeCanonicalSymbolProjector']
    tools=[project/'build-logic/src/main/java/dev/openallay/build'/(name+'.java') for name in toolnames]
    toolclasses=out/'tool-classes';toolclasses.mkdir()
    run([a.javac,'--release','17','-encoding','UTF-8','-d',toolclasses,*tools],'actual-public-tool-compile')
    # Public compiler projection fixture on a real existing native class, not a fake host fixture.
    fixture=out/'projection-fixture';(fixture/'actual/p').mkdir(parents=True)
    actual_text='package p; class Probe { net.minecraft.item.ItemStack value; String text="unchanged literal"; /* net.minecraft.item.ItemStack */ }\n'
    canonical_text='package p; class Probe { net.minecraft.world.item.ItemStack value; String text="unchanged literal"; /* net.minecraft.item.ItemStack */ }\n'
    (fixture/'actual/p/Probe.java').write_text(actual_text)
    (fixture/'original').mkdir();(fixture/'original/Probe.java').write_text(canonical_text)
    fixture_units=fixture/'units.tsv'
    fixture_units.write_text('fixture\tp/Probe.java\tCANONICAL_SEMANTIC\t'+str(fixture/'original/Probe.java')+'\n')
    curated=project/'native-builds/forge1122-census/curated-classes.tsv'
    run([a.java,'-cp',toolclasses,'dev.openallay.build.NativeCanonicalSymbolProjector',fixture/'actual',classpath,fixture_units,curated,fixture/'projected'],
        'public-projection-fixture-actual-symbols')
    if (fixture/'projected/post/p/Probe.java').read_text()!=canonical_text:raise ValueError('Class-only projector changed declaration/literal/comment/member spelling')
    fixture_units.write_text('fixture\tp/Probe.java\tCANONICAL_SEMANTIC\t'+str(fixture/'projected/post/p/Probe.java')+'\n')
    run([a.java,'-cp',native/'namespace-tool','dev.openallay.build.MinecraftClassNamespaceProducerMain',
        '--units',fixture_units,'--symbol-units',native/'symbol-units.tsv','--curated-classes',curated,
        '--curated-classes-sha256',sha(curated.read_bytes()),'--classpath',native/'classpath.txt','--source','17','--preview','false',
        '--metadata-acceptance',native/'metadata-acceptance.properties','--output',fixture/'trusted-forward','--receipt',fixture/'forward-receipt.json'],
        'trusted-forward-projection-fixture')
    if (fixture/'trusted-forward/p/Probe.java').read_text()!=actual_text:raise ValueError('Actual fixture source roundtrip residual differs')
    write(fixture/'proof.json',{'realNativeIdentity':'net.minecraft.item.ItemStack','canonicalIdentity':'net.minecraft.world.item.ItemStack',
        'actualSha256':sha(actual_text.encode()),'canonicalSha256':sha(canonical_text.encode()),
        'classOnlyProjection':True,'trustedForwardRoundtripByteExact':True,'literalCommentDeclarationPreserved':True})
    selected=out/'all-native-owners.txt';selected.write_text('\n'.join(sorted(expected))+'\n')
    run([a.java,'-cp',toolclasses,'dev.openallay.build.CanonicalVarTypePort',working,classpath,selected,out/'var-sites.tsv',out/'var-report.json'],'whole-var-public-attribution')
    sites={}
    for row in (out/'var-sites.tsv').read_text().splitlines():
        name,start,type_b64,variable=row.split('\t');sites.setdefault(name,[]).append((int(start),base64.b64decode(type_b64).decode()))
    for name,edits in sites.items():path=working/name;path.write_bytes(apply_var(path.read_bytes(),edits))
    stages=[]
    for kind,tool in [('pattern','CanonicalPatternPort'),('switch','CanonicalSwitchPort'),('api','CanonicalJava8ApiPort')]:
        output=out/(kind+'-materialized')
        run([a.java,'-cp',toolclasses,'dev.openallay.build.'+tool,working,classpath,selected,output],'whole-'+kind+'-public-attribution')
        statuses=[]
        for row in (output/'owner-status.tsv').read_text().splitlines():
            cells=row.split('\t');name,status,count=cells[:3]
            if name not in expected:raise ValueError('Foreign language source owner')
            statuses.append({'path':name,'state':status,'sites':int(count),'detail':cells[3:]})
            if status=='SUPPORTED':
                before=(output/'pre'/name).read_bytes()
                if (working/name).read_bytes()!=before:raise ValueError('Exact language stage source changed')
                (working/name).write_bytes((output/'post'/name).read_bytes())
            elif status!='REJECTED':raise ValueError('Unknown actual converter owner result')
        if len(statuses)!=len(expected):raise ValueError('Incomplete whole native language frontier')
        stages.append({'stage':kind,'owners':statuses})
    write(out/'stage-results.json',stages)
    compile_current('actual-modern17-whole-native-after-language')
    projection=out/'canonical-projection'
    curated=project/'native-builds/forge1122-census/curated-classes.tsv'
    run([a.java,'-cp',toolclasses,'dev.openallay.build.NativeCanonicalSymbolProjector',working,classpath,native/'units.tsv',curated,projection],
        'actual-class-symbol-project-back')
    rows=(projection/'owner-status.tsv').read_text().splitlines();supported=[];rejected=[]
    for row in rows:
        cells=row.split('\t');(supported if cells[1]=='SUPPORTED' else rejected).append(cells)
    write(out/'projection-results.json',{'supported':supported,'rejected':rejected})
    if rejected:raise ValueError('Whole-owner canonical symbol projection rejected; no candidate patch published')
    # Trusted forward producer round trip. Reuse exact existing metadata acceptance/tool custody classes.
    round_units=out/'round-trip-units.tsv'
    round_units.write_text('\n'.join('\t'.join([r[0],r[1],r[2],str(projection/'post'/r[1])]) for r in unitrows)+'\n')
    namespace_classes=native/'namespace-tool'
    run([a.java,'-cp',namespace_classes,'dev.openallay.build.MinecraftClassNamespaceProducerMain',
        '--units',round_units,'--symbol-units',native/'symbol-units.tsv','--curated-classes',curated,
        '--curated-classes-sha256',sha(curated.read_bytes()),'--classpath',native/'classpath.txt','--source','17','--preview','false',
        '--metadata-acceptance',native/'metadata-acceptance.properties','--output',out/'trusted-forward','--receipt',out/'trusted-forward-receipt.json'],
        'trusted-class-namespace-forward-roundtrip')
    for name in expected:
        if (out/'trusted-forward'/name).read_bytes()!=(working/name).read_bytes():raise ValueError('Residual class-token projection mismatch '+name)
    packet=out/'source-packet';packet.mkdir();patch='';files=[]
    for row in unitrows:
        name=row[1];original=originals[name];candidate=(projection/'post'/name).read_bytes();origin=Path(row[3]).relative_to(project).as_posix()
        if Path(row[3]).read_bytes()!=original:raise ValueError('Original canonical source changed during whole candidate stage')
        if candidate==original:continue
        for side,blob in [('pre',original),('post',candidate)]:
            path=packet/side/origin;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(blob)
        patch+=''.join(difflib.unified_diff(original.decode().splitlines(True),candidate.decode().splitlines(True),fromfile='a/'+origin,tofile='b/'+origin))
        files.append({'path':origin,'preSha256':sha(original),'postSha256':sha(candidate),'mode':row[2]})
    (packet/'source.patch').write_text(patch)
    write(packet/'manifest.json',{'sourceOwners':len(unitrows),'files':files,'patchSha256':sha(patch.encode()),
        'toolPins':[{'path':p.relative_to(project).as_posix(),'sha256':sha(p.read_bytes())} for p in tools],
        'wholeNormalizedModern17BeforeAfterPassed':True,'trustedClassNamespaceRoundTripByteExact':True,
        'gameLaunch':False,'nativeJava8Acceptance':False,'sourceWrites':False,'featureAlgorithmsCopied':False})

if __name__=='__main__':main()
