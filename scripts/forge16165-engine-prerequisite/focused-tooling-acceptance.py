#!/usr/bin/env python3
"""Remote-only focused producer/ABI acceptance. No product source compile or game."""
import argparse,hashlib,json,os,re,shutil,subprocess,sys,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'scripts/forge16165-engine-prerequisite'
PROBE=BASE/'probe'
MDK='https://maven.minecraftforge.net/net/minecraftforge/forge/1.16.5-36.2.42/forge-1.16.5-36.2.42-mdk.zip'
MCP='https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/1.16.5-20210115.111550/mcp_config-1.16.5-20210115.111550.zip'
VERSION='https://piston-meta.mojang.com/v1/packages/fba9f7833e858a1257d810d21a3a9e3c967f9077/1.16.5.json'
HASHES={'client':'7931ed6d723eceb1d621d05a76e10ddf643bf468c6ecf1c4ecf377bd72cf8b8c','server':'b3732b0f031abce7083cac8e594427e70d2e19674b8de2c682630c39b64228a7','tsrg':'8e6553b5c9136c83edae0d5eb1094a01b1a9c929b4cddf26e7c58dd664b88be8'}
TOOLS=['MinecraftClassNamespaceProducer','MinecraftClassNamespaceProducerMain','NamespaceMetadataAcceptance']
def digest(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda:stream.read(65536),b''):h.update(block)
    return h.hexdigest()
def write_json(path,value):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n')
def fetch(url,path,limit,sha1=None,sha256=None,size=None):
    req=urllib.request.Request(url,headers={'User-Agent':'OpenAllay-Focused-Tooling'})
    with urllib.request.urlopen(req,timeout=90) as response,path.open('xb') as output:
        count=0
        while block:=response.read(65536):
            count+=len(block)
            if count>limit:raise ValueError('bounded download exceeded '+url)
            output.write(block)
    data=path.read_bytes()
    if size is not None and len(data)!=size:raise ValueError('size mismatch '+url)
    if sha1 and hashlib.sha1(data).hexdigest()!=sha1:raise ValueError('SHA1 mismatch '+url)
    if sha256 and digest(path)!=sha256:raise ValueError('SHA256 mismatch '+url)
    return {'url':url,'bytes':len(data),'sha1':hashlib.sha1(data).hexdigest(),'sha256':digest(path)}
def execute(command,log,cwd=ROOT,env=None):
    result=subprocess.run([str(x) for x in command],cwd=cwd,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    log.parent.mkdir(parents=True,exist_ok=True);log.write_text(result.stdout)
    return result.returncode

def resolution(work,reports):
    archive=work/'mdk.zip';fetch(MDK,archive,2_000_000,sha1='3d95dac7c4f3ec7a0bdafed3f3c5cb6d284cec06')
    with zipfile.ZipFile(archive) as z:
        for name in ['gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties']:
            p=PROBE/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(name))
    (PROBE/'gradlew').chmod(0o755)
    # Checked-in probe properties are the exact normal Gradle8.4 route, not MDK's historical wrapper version.
    (PROBE/'gradle/wrapper/gradle-wrapper.properties').write_text('distributionBase=GRADLE_USER_HOME\ndistributionPath=wrapper/dists\ndistributionUrl=https\\://services.gradle.org/distributions/gradle-8.4-all.zip\nzipStoreBase=GRADLE_USER_HOME\nzipStorePath=wrapper/dists\n')
    env=dict(os.environ);env['JAVA_HOME']=env['JAVA_HOME_17_X64'];env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
    status=execute([PROBE/'gradlew','--max-workers=2','--stacktrace','-p',PROBE,'-PfocusedToolingAcceptance=true','exportFocusedToolingClasspath'],reports/'fg-resolution.log',env=env)
    if status:raise ValueError('real FG classpath resolution failed')
    generated=PROBE/'build/focused-tooling-classpath'
    data=json.loads((generated/'resolution.json').read_text());write_json(reports/'resolution.json',data)
    groups={key:[Path(line) for line in (generated/(key+'.paths.txt')).read_text().splitlines()] for key in ['nativeHost','publicationApi','publicationFull']}
    return data,groups

def mappings(work,reports):
    directory=work/'mappings';directory.mkdir();receipts=[]
    metadata=directory/'version.json';receipts.append(fetch(VERSION,metadata,100_000,sha1='fba9f7833e858a1257d810d21a3a9e3c967f9077'))
    version=json.loads(metadata.read_text())
    for side in ['client','server']:
        item=version['downloads'][side+'_mappings'];receipts.append(fetch(item['url'],directory/(side+'.txt'),7_000_000,sha1=item['sha1'],sha256=HASHES[side],size=item['size']))
    archive=directory/'mcp.zip';receipts.append(fetch(MCP,archive,1_000_000,sha256='80216a0f4ae79d81a5e11ceccaa97582a1f6a2cf079b7703b88a99a26547b342'))
    with zipfile.ZipFile(archive) as z:(directory/'joined.tsrg').write_bytes(z.read('config/joined.tsrg'))
    if digest(directory/'joined.tsrg')!=HASHES['tsrg']:raise ValueError('joined TSRG mismatch')
    write_json(reports/'mapping-receipts.json',receipts)
    return directory

def origin(name,classpath,artifacts):
    entry=name.replace('.','/')+'.class';owners=[]
    bypath={a['path']:a for a in artifacts}
    for path in classpath:
        if not path.is_file() or path.suffix!='.jar':continue
        with zipfile.ZipFile(path) as jar:
            if entry in jar.namelist():
                data=jar.read(entry);a=bypath.get(str(path),{})
                owners.append({'artifact':str(path),'coordinate':a.get('coordinate','EXPLICIT_FILE_INPUT'),'classifier':a.get('classifier'),'extension':a.get('extension'),'artifactSha256':digest(path),'classSha256':hashlib.sha256(data).hexdigest(),'classMajor':int.from_bytes(data[6:8],'big')})
    if len(owners)>1:raise ValueError('competing requested class owners '+name)
    return owners

def declaration_only(text):
    # javap -v supplies attributes; discard every Code block from the declaration report.
    # Requested body snippets are captured separately with the exact member allowlist.
    out=[];discard=False
    for line in text.splitlines(keepends=True):
        if line.startswith('    Code:'):
            discard=True;continue
        if discard:
            if line.strip()=='}' or re.match(r'^  \S',line) or re.match(r'^    (?:Signature|Exceptions|RuntimeVisibleAnnotations|RuntimeInvisibleAnnotations|AnnotationDefault|Deprecated|Synthetic|MethodParameters):',line):
                discard=False
            else:continue
        out.append(line)
    return ''.join(out)

def body_blocks(text,members):
    lines=text.splitlines(keepends=True);blocks={member:[] for member in members};start=None;selected=None
    for index,line in enumerate(lines):
        declaration=bool(re.match(r'^  (?:public|protected|private|static|final|abstract|synchronized|native|strictfp|default|transient|volatile)\b',line))
        if declaration or line=='}\n':
            if start is not None:
                blocks[selected].append(''.join(lines[start:index]));start=None;selected=None
            if declaration:
                matches=[member for member in members if re.search(r'\b'+re.escape(member)+r'\(',line)]
                if len(matches)>1:raise ValueError('ambiguous requested body declaration')
                if matches:start=index;selected=matches[0]
    if start is not None:blocks[selected].append(''.join(lines[start:]))
    return blocks

def export_abi(work,reports,data,groups,request):
    records=[];failures=[];host=groups['nativeHost'];api=groups['publicationApi'];full=groups['publicationFull']
    artifacts=[a for group in data['groups'].values() for a in group['artifacts']]
    for coordinate,expected in request['pins'].items():
        matches=[a for a in artifacts if a['coordinate']==coordinate]
        if len(matches)!=1 or matches[0]['sha256']!=expected:raise ValueError('publisher hash mismatch '+coordinate)
    host_matches=[a for a in data['groups']['nativeHost']['artifacts'] if a['coordinate']==request['expectedHostCoordinate']]
    if not host_matches:raise ValueError('actual normal FG mapped host coordinate missing')
    primary_owner=origin('net.minecraft.client.Minecraft',host,artifacts)
    screen_owner=origin('net.minecraft.client.gui.screen.Screen',host,artifacts)
    if len(primary_owner)!=1 or len(screen_owner)!=1 or primary_owner[0]['artifact']!=screen_owner[0]['artifact']:raise ValueError('primary mapped host class owner mismatch')
    mains=[a for a in host_matches if a['path']==primary_owner[0]['artifact'] and a.get('extension')=='jar' and not a.get('classifier') and not Path(a['path']).name.endswith('-launcher.jar')]
    if len(mains)!=1:raise ValueError('exact actual mapped main jar identity missing')
    main_host=mains[0]
    # Raw publication class identities are established before any hypothetical fg.deobf use.
    # Scanner evidence replaces API REI with full REI; both never share that evidence owner set.
    arch=[p for p in api if 'architectury-forge-' in p.name]
    scanner=host+full+arch
    api_cp=host+api
    # Pins/competing binary owners are preflight-fatal before any declaration/body capture.
    for name in request['publicationClasses']+request['negativeClasses']:origin(name,api_cp,artifacts)
    for name in request['lateNativeClasses']:origin(name,host,artifacts)
    for name in request['conditionalBodies']:origin(name,scanner,artifacts)
    javap=Path(os.environ['JAVA_HOME_17_X64'])/'bin/javap'
    def capture(name,cp,kind,members=(),expected_absent=False):
        owners=origin(name,cp,artifacts);directory=reports/'abi'/kind;directory.mkdir(parents=True,exist_ok=True)
        if expected_absent:
            if owners:raise ValueError('negative publication identity unexpectedly present '+name)
            records.append({'class':name,'kind':kind,'status':'ABSENT_EXPECTED','owners':[]});return
        if not owners:raise ValueError('required requested class absent '+name)
        destination=directory/(name+'.javap.txt')
        status=execute([javap,'-classpath',os.pathsep.join(map(str,cp)),'-protected','-s','-v',name],destination)
        if status:raise ValueError('requested declaration failed '+name)
        declaration=declaration_only(destination.read_text());destination.write_text(declaration)
        declared=re.search(r'(?m)^(?:public |protected |private |abstract |final |static )*(?:class|interface|enum) ([^ <]+)',declaration)
        if not declared or declared.group(1)!=name:raise ValueError('binary declaration identity mismatch '+name)
        # -v supplies access_flags, generic Signature, superclass/interfaces, descriptors and annotations.
        record={'class':name,'kind':kind,'status':'FOUND','owners':owners,'declarationSha256':digest(destination),'rawPublication':kind=='publication'}
        if members:
            temporary=work/(name+'.bodies.txt');status=execute([javap,'-classpath',os.pathsep.join(map(str,cp)),'-p','-s','-c',name],temporary)
            if status:raise ValueError('body capture failed '+name)
            body=body_blocks(temporary.read_text(),members)
            bodyfile=directory/(name+'.requested-bodies.txt');bodyfile.write_text(''.join(block for member in members for block in body[member]))
            record['bodyMembers']={member:{'declarationCount':len(body[member]),'bodyCount':sum('    Code:' in block for block in body[member]),'status':'FOUND_BODY' if any('    Code:' in block for block in body[member]) else 'ABSENT_REQUIRED_BODY'} for member in members};record['bodySha256']=digest(bodyfile)
            missing=[member for member in members if not any('    Code:' in block for block in body[member])]
            if missing:
                records.append(record)
                raise ValueError('required individual body members missing '+name+' '+','.join(missing))
        records.append(record)
    def capture_record(name,cp,kind,members=(),expected_absent=False):
        try:capture(name,cp,kind,members,expected_absent)
        except Exception as error:
            finding={'class':name,'kind':kind,'status':'CRITICAL_ABI_FINDING','error':str(error)}
            records.append(finding);failures.append(finding)
        finally:
            write_json(reports/'abi-partial-index.json',{'source':os.environ['GITHUB_SHA'],'records':records,'criticalFindings':failures,'complete':False,'productCompiled':False,'gameExecuted':False})
    for name in request['publicationClasses']:
        capture_record(name,api_cp,'publication',request['declarationBodies'].get(name,()))
    for name in request['lateNativeClasses']:capture_record(name,host,'late-native',request.get('lateNativeBodies',{}).get(name,()))
    for name in request['negativeClasses']:capture_record(name,api_cp,'negative',expected_absent=True)
    for name,members in request['conditionalBodies'].items():capture_record(name,scanner,'isolated-conditional',members)
    write_json(reports/'abi-index.json',{'source':os.environ['GITHUB_SHA'],'records':records,'criticalFindings':failures,'publicationDeclarations':len(request['publicationClasses']),'lateNativeDeclarations':len(request['lateNativeClasses']),'oldCensusRepeated':False,'normalFgHost':main_host,'sameCoordinateAuxiliaryArtifacts':[a for a in host_matches if a['path']!=main_host['path']],'primaryOwnerChecks':{'Minecraft':primary_owner,'Screen':screen_owner},'rawPublicationPinsVerified':True,'fgDeobfAssumed':False,'gameExecuted':False})
    if failures:raise ValueError('critical requested ABI findings: '+str(len(failures)))

def tool_cases(work,reports,host,mapping):
    home=Path(os.environ['JAVA_HOME_21_X64']);java=home/'bin/java';classes=work/'tool-classes';classes.mkdir()
    sources=[ROOT/'build-logic/src/main/java/dev/openallay/build'/(name+'.java') for name in TOOLS]
    write_json(reports/'tool-source-inputs.json',[{'path':str(p.relative_to(ROOT)),'sha256':digest(p)} for p in sources])
    status=execute([home/'bin/javac','--release','17','-encoding','UTF-8','-d',classes,*sources],reports/'tool-compile.log')
    write_json(reports/'tool-compile-result.json',{'phase':'THREE_HELPER_JAVAC','exit':status,'release':17,'launcher':str(home/'bin/javac'),'sourceGrammarForCases':17,'productCompiled':False})
    if status:raise ValueError('three plain namespace helper sources failed compile')
    majors={str(p.relative_to(classes)):int.from_bytes(p.read_bytes()[6:8],'big') for p in classes.rglob('*.class')}
    if not majors or any(major!=61 for major in majors.values()):raise ValueError('tool bytecode release17 mismatch')
    write_json(reports/'tool-class-metadata.json',{'classes':majors,'productCompiled':False})
    cp=work/'metadata-classpath.txt';cp.write_text('\n'.join(map(str,host))+'\n')
    acceptance=reports/'metadata-acceptance.properties'
    status=execute([java,'-cp',classes,'dev.openallay.build.NamespaceMetadataAcceptance',cp,acceptance],reports/'metadata-fixture.log')
    write_json(reports/'metadata-fixture-result.json',{'phase':'ACTUAL_FG_PUBLIC_ELEMENTS','exit':status,'launcher':str(java),'classpathPlanSha256':digest(cp),'acceptanceReceiptPresent':acceptance.is_file(),'productCompiled':False})
    if status:raise ValueError('actual FG classpath public Elements fixture failed')
    case_records=[]
    cases=json.loads((BASE/'focused-tooling-cases.json').read_text())
    def run_case(spec,overrides=None):
        owned=work/'cases'/spec['name'];owned.mkdir(parents=True);unit=owned/'units.tsv';symbol=owned/'symbols.tsv';symbol.write_text('');rows=[]
        for path,text in spec['files'].items():
            source=owned/'source'/path;source.parent.mkdir(parents=True,exist_ok=True);source.write_text(text)
            rows.append('case\t'+path+'\t'+spec.get('mode','MOJANG_CANONICAL')+'\t'+str(source))
        unit.write_text('\n'.join(rows)+'\n');output=owned/'generated';receipt=owned/'namespace.json'
        command=[java,'-cp',classes,'dev.openallay.build.MinecraftClassNamespaceProducerMain','--units',unit,'--symbol-units',symbol,'--client',mapping/'client.txt','--client-sha256',HASHES['client'],'--server',mapping/'server.txt','--server-sha256',HASHES['server'],'--tsrg',mapping/'joined.tsrg','--tsrg-sha256',HASHES['tsrg'],'--classpath',cp,'--source','17','--preview','false','--metadata-acceptance',acceptance,'--output',output,'--receipt',receipt]
        if overrides:
            for flag,value in overrides.items():command[command.index(flag)+1]=value
        status=execute(command,reports/'cases'/(spec['name']+'.log'))
        success=status==0
        write_json(reports/'cases'/(spec['name']+'.result.json'),{'phase':'SOURCE_IDENTITY_CASE','case':spec['name'],'exit':status,'expectedSuccess':spec['expectSuccess'],'actualSuccess':success,'sourceGrammar':17,'preview':False})
        if success!=spec['expectSuccess']:raise ValueError('focused case expectation mismatch '+spec['name'])
        if success:
            for path,needles in spec.get('expectedTokens',{}).items():
                text=(output/path).read_text()
                for needle in needles:
                    if needle not in text:raise ValueError('case transformed token missing '+spec['name']+' '+needle)
            shutil.copyfile(receipt,reports/'cases'/(spec['name']+'.receipt.json'))
        elif receipt.exists():raise ValueError('failed safe case retained success receipt '+spec['name'])
        case_records.append({'case':spec['name'],'exit':status,'expectedSuccess':spec['expectSuccess'],'status':'PASS'})
        return owned,command,receipt
    for case in cases:run_case(case)
    # Stale namespace receipt must invalidate on safe missing-acceptance failure.
    spec={'name':'stale-receipt','files':{'p/Stale.java':'package p; class Stale {}\n'},'expectSuccess':True}
    owned,command,receipt=run_case(spec);command[command.index('--metadata-acceptance')+1]=owned/'missing-acceptance.properties'
    if execute(command,reports/'cases/stale-receipt-negative.log')==0 or receipt.exists():raise ValueError('stale namespace receipt accepted')
    # Throwaway mapping/source/real tool class protection: request a destructive path, never write it.
    for label,target in [('source',owned/'source/p/Stale.java'),('tool-class',classes/'dev/openallay/build/MinecraftClassNamespaceProducer.class'),('mapping',mapping/'client.txt')]:
        old=digest(target);bad=list(command);bad[bad.index('--metadata-acceptance')+1]=acceptance;bad[bad.index('--receipt')+1]=target
        if execute(bad,reports/'cases'/('protect-'+label+'.log'))==0 or digest(target)!=old:raise ValueError('protected input changed '+label)
        case_records.append({'case':'protect-'+label,'status':'PASS','inputSha256':old})
    # Fixture failed rerun invalidates only its own throwaway receipt; primary receipt retained.
    throwaway=work/'throwaway-acceptance.properties';shutil.copyfile(acceptance,throwaway);empty=work/'empty-classpath.txt';empty.write_text('')
    if execute([java,'-cp',classes,'dev.openallay.build.NamespaceMetadataAcceptance',empty,throwaway],reports/'cases/fixture-stale-negative.log')==0 or throwaway.exists():raise ValueError('stale fixture receipt retained')
    target=classes/'dev/openallay/build/MinecraftClassNamespaceProducer.class';old=digest(target)
    if execute([java,'-cp',classes,'dev.openallay.build.NamespaceMetadataAcceptance',cp,target],reports/'cases/fixture-tool-protect.log')==0 or digest(target)!=old:raise ValueError('fixture tool input changed')
    write_json(reports/'case-results.json',{'sourceGrammar':'17','preview':False,'sameToolingLauncher':str(java),'cases':case_records,'optionalGradleTaskExecuted':False,'nativeProductCompiled':False,'gameExecuted':False})

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--output',required=True,type=Path);args=parser.parse_args()
    reports=args.output.resolve();reports.mkdir(parents=True,exist_ok=False)
    work=ROOT/'build/focused-tooling-work';work.mkdir(parents=True,exist_ok=False)
    request=json.loads((BASE/'focused-tooling-requests.json').read_text())
    result={'source':os.environ.get('GITHUB_SHA'),'purpose':'new-only-tooling-publication-acceptance','productCompiled':False,'gameExecuted':False,'oldCensusRepeated':False,'status':'FAILED'}
    try:
        if not os.environ.get('GITHUB_ACTIONS')=='true':raise ValueError('remote GitHub runner only')
        data,groups=resolution(work,reports)
        mapping=mappings(work,reports)
        # Independent new evidence stays valuable if tool compile/fixture fails; record both outcomes.
        failures=[]
        for label,action in [('publicationAbi',lambda:export_abi(work,reports,data,groups,request)),('namespaceTool',lambda:tool_cases(work,reports,groups['nativeHost'],mapping))]:
            try:action();result[label]='PASS'
            except Exception as error:result[label]='FAILED';failures.append(label+': '+str(error))
        if failures:raise ValueError('; '.join(failures))
        result['status']='PASS'
    except Exception as error:result['error']=str(error)
    finally:write_json(reports/'RESULT.json',result)
    if result['status']!='PASS':raise SystemExit(1)
if __name__=='__main__':main()
