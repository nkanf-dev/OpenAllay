#!/usr/bin/env python3
"""Remote ordinary stock Forge14/Java8 packaged acceptance. No agents or replacement loaders."""
import argparse,hashlib,importlib.util,io,json,os,re,shlex,shutil,signal,subprocess,sys,time,zipfile
from pathlib import Path
from types import SimpleNamespace
ROOT=Path(__file__).resolve().parents[1]
PACKET=ROOT/'scripts/forge1122-stock8-acceptance'
PROFILE='1.12.2-forge-14.23.5.2864'
MAIN='net.minecraft.launchwrapper.Launch'
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path);result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n')
def stop_owned(process):
    receipt={'initialExitCode':process.poll(),'signals':[]}
    if process.poll() is None:
        os.killpg(process.pid,signal.SIGTERM);receipt['signals'].append('SIGTERM')
        try:process.wait(timeout=10)
        except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);receipt['signals'].append('SIGKILL');process.wait(timeout=5)
    receipt['finalExitCode']=process.returncode;return receipt

def libraries(metadata,root,launch):
    java=[]
    for library in metadata['libraries']:
        downloads=library.get('downloads',{})
        if set(downloads)=={'classifiers'} and library.get('natives'):continue
        java.append(library)
    return launch.version_libraries({'libraries':java},root,Path('/nonexistent'),allow_gradle=False)

def inspect_product(jar):
    with zipfile.ZipFile(jar) as archive:
        names=archive.namelist()
        if len(names)!=len(set(names)):raise ValueError('Duplicate packaged archive paths')
        manifest=archive.read('META-INF/MANIFEST.MF').decode().replace('\r\n','\n');manifest=re.sub('\n ','',manifest)
        fields=dict(line.split(': ',1) for line in manifest.splitlines() if ': ' in line)
        if fields.get('TweakClass')!='org.spongepowered.asm.launch.MixinTweaker' or fields.get('FMLCorePlugin'):
            raise ValueError('One standard MixinTweaker owner required; no duplicate coreplugin admission')
        if fields.get('ForceLoadAsMod','').lower()!='true':raise ValueError('Genuine Mixin FML reparse admission must be explicit')
        configs=fields.get('MixinConfigs','').split(',')
        if set(configs)!={'openallay.client.mixins.json','openallay.forge.mixins.json','openallay.world.mixins.json'} or len(configs)!=3:
            raise ValueError('One exact manifest-owned three-config registration required')
        if 'META-INF/openallay/distribution.json' not in names:raise ValueError('Normally bundled Builder provenance required')
        forbidden=('net/minecraft/','net/minecraftforge/','cpw/mods/','com/google/common/','com/google/gson/','org/objectweb/asm/')
        class_inventory=[]
        for name in names:
            if name.endswith('.class'):
                data=archive.read(name);major=int.from_bytes(data[6:8],'big')
                if data[:4]!=bytes.fromhex('cafebabe') or major>52:raise ValueError('Physical class not Java8 including MR: '+name)
                if name.startswith(forbidden):raise ValueError('Stock host class replacement forbidden: '+name)
                class_inventory.append({'entry':name,'major':major,'sha256':hashlib.sha256(data).hexdigest()})
            if name.endswith('.mixins.json'):
                config=json.loads(archive.read(name))
                if config.get('compatibilityLevel') not in (None,'JAVA_8'):raise ValueError('Packaged mixin config is not Java8: '+name)
        provenance=json.loads(archive.read('META-INF/openallay/distribution.json'))
        resource=provenance['artifact']['path'];raw=archive.read(resource)
        if hashlib.sha256(raw).hexdigest()!=provenance['artifact']['sha256']:raise ValueError('Bundled Builder checksum differs')
        with zipfile.ZipFile(io.BytesIO(raw)) as nested:
            for name in nested.namelist():
                if name.endswith('.class') and int.from_bytes(nested.read(name)[6:8],'big')!=52:raise ValueError('Bundled Builder must be Java8')
        return {'sha256':sha(jar),'manifest':fields,'classes':class_inventory,'bundledBuilder':provenance,'stockNamespacesPreserved':True}

def guava_method_links(product,host,java):
    """Resolve actual class-file method references against the real official Guava21 JAR."""
    import struct
    def parse(data):
        offset=8;count=int.from_bytes(data[offset:offset+2],'big');offset+=2;pool=[None]*count;index=1
        while index<count:
            tag=data[offset];offset+=1
            if tag==1:
                length=int.from_bytes(data[offset:offset+2],'big');offset+=2;pool[index]=(tag,data[offset:offset+length].decode('utf-8',errors='replace'));offset+=length
            elif tag in (3,4):offset+=4
            elif tag in (5,6):offset+=8;index+=1
            elif tag in (7,8,16,19,20):pool[index]=(tag,int.from_bytes(data[offset:offset+2],'big'));offset+=2
            elif tag in (9,10,11,12,17,18):pool[index]=(tag,*struct.unpack_from('>HH',data,offset));offset+=4
            elif tag==15:offset+=3
            else:raise ValueError('Unknown real class constant tag '+str(tag))
            index+=1
        access,owner,parent=struct.unpack_from('>HHH',data,offset);offset+=6
        text=lambda index:pool[index][1]
        class_name=lambda index:text(pool[index][1]) if index else None
        count=int.from_bytes(data[offset:offset+2],'big');offset+=2
        interfaces=[class_name(int.from_bytes(data[offset+2*i:offset+2*i+2],'big')) for i in range(count)];offset+=2*count
        def skip_attributes(offset):
            count=int.from_bytes(data[offset:offset+2],'big');offset+=2
            for _ in range(count):length=int.from_bytes(data[offset+2:offset+6],'big');offset+=6+length
            return offset
        count=int.from_bytes(data[offset:offset+2],'big');offset+=2
        for _ in range(count):offset=skip_attributes(offset+6)
        count=int.from_bytes(data[offset:offset+2],'big');offset+=2;methods=set()
        for _ in range(count):
            _,name,desc=struct.unpack_from('>HHH',data,offset);methods.add((text(name),text(desc)));offset=skip_attributes(offset+6)
        refs=[]
        for item in pool:
            if item and item[0] in (10,11):
                owner=class_name(item[1]);nt=pool[item[2]];refs.append((owner,text(nt[1]),text(nt[2])))
        return {'parent':class_name(parent),'interfaces':interfaces,'methods':methods,'refs':refs}
    with zipfile.ZipFile(host) as z:classes={name[:-6]:parse(z.read(name)) for name in z.namelist() if name.endswith('.class')}
    rt_path=java.parent.parent/'jre/lib/rt.jar'
    if not rt_path.is_file():rt_path=java.parent.parent/'lib/rt.jar'
    runtime_archive=zipfile.ZipFile(rt_path)
    def resolves(owner,name,desc,seen):
        if owner in seen:return False
        seen.add(owner);record=classes.get(owner)
        if record is None:
            try:record=parse(runtime_archive.read(owner+'.class'));classes[owner]=record
            except KeyError:return False
        if (name,desc) in record['methods']:return True
        if name=='<init>':return False
        return any(resolves(parent,name,desc,seen) for parent in [record['parent']]+record['interfaces'] if parent)
    checked=[];missing=[]
    with zipfile.ZipFile(product) as z:
        for path in z.namelist():
            if not path.endswith('.class'):continue
            for owner,name,descriptor in parse(z.read(path))['refs']:
                if not owner.startswith('com/google/common/'):continue
                fact={'caller':path,'owner':owner,'method':name,'descriptor':descriptor}
                checked.append(fact)
                if not resolves(owner,name,descriptor,set()):missing.append(fact)
    runtime_archive.close()
    return {'hostCoordinate':'com.google.guava:guava:21.0','hostJarSha256':sha(host),'java8RtJarSha256':sha(rt_path),'actualMethodReferences':checked,'missingHostMethods':missing,'accepted':not missing}

def provision(java,runtime,launch):
    frozen=json.loads((PACKET/'official-inputs.json').read_text())
    for name,digest in frozen['metadata'].items():
        if sha(PACKET/name)!=digest:raise ValueError('Frozen official metadata changed')
    install=json.loads((PACKET/'install_profile.json').read_text());version=json.loads((PACKET/'version.json').read_text());vanilla=json.loads((PACKET/'minecraft-1.12.2.json').read_text())
    runtime_root=runtime.safe_root(ROOT/'build/e2e/runtime/forge1122-stock8/minecraft',ROOT)
    runtime.claim_root(runtime_root,'1.12.2')
    actual,files=runtime.prepare_vanilla(runtime_root,{'minecraft_version':'1.12.2','java_version':'8'})
    if actual!=vanilla:raise ValueError('Live Minecraft metadata differs from frozen official inputs')
    pin=frozen['installer'];installer=runtime_root/'.provision/forge-installer.jar'
    runtime.download(pin['url'],installer,pin['sha1'],pin['size'],maximum=8*1024*1024)
    if sha(installer)!=pin['sha256']:raise ValueError('Official installer differs')
    if runtime.inspect_installer(installer,'forge')!=(install,version):raise ValueError('Installer metadata differs')
    with zipfile.ZipFile(installer) as archive:
        for metadata in (install,version):
            for library in metadata['libraries']:
                artifact=library['downloads']['artifact']
                if not artifact['url']:
                    target=runtime.relative_file(runtime_root/'libraries',artifact['path']);content=archive.read('maven/'+artifact['path'])
                    if hashlib.sha1(content).hexdigest()!=artifact['sha1']:raise ValueError('Authentic bundled Forge bytes differ')
                    target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(content)
            runtime.prepare_libraries({'libraries':[row for row in metadata['libraries'] if row['downloads']['artifact']['url']]},runtime_root)
    if not (runtime_root/'versions'/PROFILE/(PROFILE+'.json')).is_file():
        runtime.run_installer([str(java),'-jar',str(installer),'--offline','--installClient',str(runtime_root)],runtime_root,'forge')
    if json.loads((runtime_root/'versions'/PROFILE/(PROFILE+'.json')).read_text())!=version:raise ValueError('Official Forge profile differs')
    for metadata in (install,version,vanilla):runtime.verify_downloaded_libraries(metadata,runtime_root)
    assets=launch.prepare_assets(runtime_root,ROOT/'build/e2e/runtime/assets',repo=ROOT,minecraft_target='1.12.2')
    return runtime_root,assets,vanilla,version

def run_client(args,java,jar,root,assets,vanilla,forge,launch,game,out,phase=''):
    out.mkdir(parents=True,exist_ok=False);mods=game/'mods';mods.mkdir(parents=True,exist_ok=True)
    target=mods/jar.name
    if target.exists() and sha(target)!=sha(jar):raise ValueError('Owned profile product changed')
    if not target.exists():shutil.copyfile(jar,target)
    settings=game/'config/openallay';settings.mkdir(parents=True,exist_ok=True)
    models=launch.fixture_model_config(18765);write(settings/'models.json',models);write(settings/'voice.json',launch.fixture_voice_config());write(settings/'display.json',launch.fixture_display_config())
    write(settings/'unrestricted-javascript.json',{'enabled':False});write(settings/'experimental-commands.json',{'enabled':False})
    (game/'options.txt').write_text('lang:zh_cn\nguiScale:1\nfullscreen:false\nrenderDistance:4\nmaxFps:30\npauseOnLostFocus:false\n')
    cp=libraries(vanilla,root,launch);extra=libraries(forge,root,launch);keys={tuple(n.split(':')[:2]) for n,_ in extra};cp=[(n,p) for n,p in cp if tuple(n.split(':')[:2]) not in keys]+extra
    cp.append(('com.mojang:minecraft:1.12.2:client',root/'versions/1.12.2/1.12.2.jar'))
    launch.extract_natives(launch.native_libraries(vanilla,root),out/'natives')
    values={'auth_player_name':'BuilderProbe','version_name':PROFILE,'game_directory':game,'assets_root':assets,'assets_index_name':vanilla['assetIndex']['id'],'auth_uuid':launch.offline_uuid('BuilderProbe'),'auth_access_token':'0','user_type':'legacy'}
    game_args=[re.sub(r'\$\{([A-Za-z0-9_]+)\}',lambda m:str(values[m[1]]),s) for s in shlex.split(forge['minecraftArguments'])]+['--width','1280','--height','960']
    world='openallay-builder-stock8-'+args.run_id
    scenario={'world':'native-world-sdk','persistence':'native-world-sdk','menus':'ui-manual-regressions'}.get(args.scenario,args.scenario)
    properties={'openallay.e2e.enabled':'true','openallay.e2e.scenario':scenario,'openallay.e2e.question':'OpenAllay E2E UI manual regressions' if args.scenario=='menus' else 'OpenAllay E2E Builder '+scenario.removeprefix('builder-'),'openallay.e2e.report':str(out/'report.json'),'openallay.e2e.trace':str(out/'trace.json'),'openallay.e2e.shutdown':'true','openallay.e2e.timeoutSeconds':'300','openallay.e2e.appliedBindings':'true','openallay.e2e.appliedBindingsReceipt':str(out/'bindings.json')}
    properties['openallay.e2e.resumeWorld' if phase=='reload' else 'openallay.e2e.createWorld']=world
    if phase:properties['openallay.e2e.worldPhase']=phase
    if args.scenario=='menus':properties.update({'openallay.e2e.screenshotRoot':str(out/'frames'),'openallay.e2e.shutdownAfterScreenshots':'true','openallay.e2e.sourceRevision':args.product_source,'openallay.e2e.sourceManifestSha256':sha(args.product_pin)})
    if args.scenario=='boot':properties={'openallay.e2e.enabled':'true','openallay.e2e.scenario':'native-world-sdk','openallay.e2e.question':'Stock Java8 startup and world probe','openallay.e2e.report':str(out/'report.json'),'openallay.e2e.shutdown':'true','openallay.e2e.createWorld':world,'openallay.e2e.timeoutSeconds':'300'}
    command=[str(java),'-Xms256M','-Xmx1536M','-verbose:class','-Djava.library.path='+str(out/'natives')]+['-D'+k+'='+v for k,v in properties.items()]+['-cp',os.pathsep.join(str(p) for _,p in cp),MAIN]+game_args
    if any('javaagent' in part or 'add-opens' in part or 'java.system.class.loader' in part for part in command):raise ValueError('Ordinary Java8 launch forbids runtime overrides')
    env={k:v for k,v in os.environ.items() if k not in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')};env['OPENALLAY_E2E_FIXTURE_KEY']='isolated-stock8-synthetic-key';env['LC_ALL']='C'
    fixture=None;manager=None;write(out/'launch.json',{'command':command,'javaVersion':8,'productSha256':sha(jar),'stockClasspath':[{'coordinate':n,'sha256':sha(p)} for n,p in cp],'oneModsJar':str(target),'noAgents':True,'scenario':scenario})
    if args.scenario=='menus':
        with (out/'wm.log').open('w') as log:manager=subprocess.Popen(['openbox','--sm-disable'],env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
    if scenario.startswith('builder-') or args.scenario=='menus':
        with (out/'model-fixture.log').open('w') as log:fixture=subprocess.Popen([sys.executable,'-B',str(ROOT/'scripts/e2e-model-fixture.py'),'--port','18765'],env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
    receipt={'accepted':False,'scenario':scenario,'phase':phase}
    with (out/'client.log').open('w') as log:
        process=subprocess.Popen(command,cwd=game,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        try:process.wait(timeout=330 if args.scenario!='menus' else 660)
        except subprocess.TimeoutExpired:receipt['failure']='bounded scenario timeout'
        finally:
            receipt['gameExit']=stop_owned(process)
            if fixture:receipt['fixtureExit']=stop_owned(fixture)
            if manager:receipt['windowManagerExit']=stop_owned(manager)
    report=json.loads((out/'report.json').read_text()) if (out/'report.json').exists() else {}
    binding=json.loads((out/'bindings.json').read_text()) if (out/'bindings.json').exists() else {}
    completed=report.get('outcome')=='COMPLETED';native=report.get('nativeAcceptance',{}).get('outcome')=='PASSED'
    text=(out/'client.log').read_text(errors='replace')
    origins={'stockAsm5ClassLoaded':bool(re.search(r'Loaded org\.objectweb\.asm\.ClassReader from .*asm-debug-all-5\.2',text)),
        'hostGuava21ClassLoaded':bool(re.search(r'Loaded com\.google\.common\.[^ ]+ from .*guava-21\.0',text)),
        'genuineMixinStarted':'SpongePowered MIXIN Subsystem Version=0.8.5' in text,
        'openallayDiscovered':bool(re.search(r'openallay@0\.4\.4|openallay[^\n]+0\.4\.4',text)),
        'realLinkageFailure':any(t in text for t in ('NoSuchMethodError','AbstractMethodError','IncompatibleClassChangeError'))}
    write(out/'ordinary-class-origins.json',origins)
    checks=report.get('checks',[]);worldok=completed and (phase in ('persist','reload') or bool(checks) and all(c.get('status')=='PASS' for c in checks))
    clean=receipt['gameExit']['finalExitCode']==0 and receipt['gameExit']['signals']==[]
    receipt.update(reportOutcome=report.get('outcome'),bindingAccepted=binding.get('accepted'),accepted=clean and completed and (native if scenario.startswith('builder-') else worldok if scenario=='native-world-sdk' else bool(list((out/'frames').glob('*.png')))))
    receipt['accepted']=receipt['accepted'] and origins['stockAsm5ClassLoaded'] and origins['hostGuava21ClassLoaded'] and origins['genuineMixinStarted'] and origins['openallayDiscovered'] and not origins['realLinkageFailure']
    if args.scenario!='boot':receipt['accepted']=receipt['accepted'] and binding.get('accepted') is True
    write(out/'acceptance.json',receipt)
    if not receipt['accepted']:raise ValueError('Actual ordinary Java8 acceptance failed; original report/log retained')
    return report

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--product-pin',type=Path,required=True);parser.add_argument('--scenario',choices=['boot','world','persistence','builder-restricted','builder-partial','builder-cancel','builder-undo','builder-legacy-shapes','menus'],required=True);parser.add_argument('--run-id',required=True);args=parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote only; no local game/provision/download')
    if not re.fullmatch('[A-Za-z0-9_-]+',args.run_id):raise ValueError('Fresh safe run ID required')
    sys.path.insert(0,str(ROOT/'scripts'))
    provider=module('provider',ROOT/'scripts/build-forge1122-native.py');runtime=module('runtime',ROOT/'scripts/prepare-ci-minecraft-runtime.py');launch=module('launch',ROOT/'scripts/run-packaged-builder-acceptance.py')
    pin=json.loads(args.product_pin.read_text());expected_keys={'provider','jarSha256','jarName','productSource'}
    if set(pin)!=expected_keys:raise ValueError('Exact final product provider pin required')
    args.product_source=pin['productSource'];evidence=ROOT/'build/e2e/forge1122-stock8'/args.run_id;evidence.mkdir(parents=True,exist_ok=False)
    retained=provider.retained(pin['provider'],evidence/'retained',evidence,'product');jars=[p for p in retained.rglob(pin['jarName']) if sha(p)==pin['jarSha256']]
    if len(jars)!=1:raise ValueError('One exact final single embedded mod JAR required')
    jar=jars[0];write(evidence/'product-inspection.json',inspect_product(jar))
    java=Path(os.environ['JAVA_HOME'])/'bin/java';version=subprocess.check_output([str(java),'-version'],stderr=subprocess.STDOUT,text=True)
    if not re.search(r'version "1\.8\.',version):raise ValueError('Ordinary genuine Java8 runtime required')
    write(evidence/'runtime-selection.json',{'java':str(java),'actualVersion':version,'hiddenRuntime':False})
    # This unshipped pure-JDK fixture checks the verified final product's current native JDBC payload.
    sqlite_proof=evidence/'sqlite-proof';sqlite_proof.mkdir()
    fixture_classes=sqlite_proof/'classes';fixture_classes.mkdir()
    fixture_source=ROOT/'scripts/fixtures/SqliteGameRuntimeJava8Fixture.java'
    javac=java.with_name('javac')
    fixture_env={key:value for key,value in os.environ.items() if key not in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')}
    javac_version=subprocess.check_output([str(javac),'-version'],stderr=subprocess.STDOUT,text=True,env=fixture_env)
    if not re.search(r'javac 1\.8\.',javac_version):raise ValueError('Actual sibling Java8 compiler required for isolated JDBC fixture')
    database=sqlite_proof/'fresh-native.db'
    with (sqlite_proof/'jdbc-java8.log').open('w') as log:
        compiled=subprocess.run([str(javac),'-source','8','-target','8','-proc:none','-d',str(fixture_classes),str(fixture_source)],
            stdout=log,stderr=subprocess.STDOUT,timeout=120,env=fixture_env)
        if compiled.returncode:
            write(sqlite_proof/'receipt.json',{'accepted':False,'productSha256':sha(jar),'fixtureSourceSha256':sha(fixture_source),'compileExitCode':compiled.returncode})
            raise SystemExit(compiled.returncode)
        checked=subprocess.run([str(java),'-cp',str(fixture_classes)+os.pathsep+str(jar),'SqliteGameRuntimeJava8Fixture',str(database)],
            stdout=log,stderr=subprocess.STDOUT,timeout=120,env=fixture_env)
    write(sqlite_proof/'receipt.json',{'accepted':checked.returncode==0,'productSha256':sha(jar),
        'fixtureSourceSha256':sha(fixture_source),'logSha256':sha(sqlite_proof/'jdbc-java8.log'),
        'javac':str(javac),'actualJavacVersion':javac_version,'java':str(java),'actualJavaVersion':version,
        'compileExitCode':compiled.returncode,'runtimeExitCode':checked.returncode,
        'expectedNativeSqliteVersion':'3.50.3','freshDatabase':True,'fixtureShipped':False,'annotationProcessorDiscovery':False})
    if checked.returncode:raise SystemExit(checked.returncode)
    root,assets,vanilla,forge=provision(java,runtime,launch)
    guava=root/'libraries/com/google/guava/guava/21.0/guava-21.0.jar'
    audit=guava_method_links(jar,guava,java);write(evidence/'actual-guava21-method-links.json',audit)
    if not audit['accepted']:raise ValueError('Real packaged Guava method references incompatible with stock21; preserve evidence')
    game=evidence/'game'
    if args.scenario=='persistence':
        before=run_client(args,java,jar,root,assets,vanilla,forge,launch,game,evidence/'persist','persist');after=run_client(args,java,jar,root,assets,vanilla,forge,launch,game,evidence/'reload','reload')
        if before['worldId']!=after['worldId'] or before['actual']!=after['persistedActual'] or after.get('originalImageRestored') is not True:raise ValueError('Actual native UUID/full image persistence differs')
        write(evidence/'persistence-acceptance.json',{'accepted':True,'sameWorldId':before['worldId'],'sameNativeImage':after['persistedActual'],'naturalExits0':True,'gameProfileUploaded':False})
    else:run_client(args,java,jar,root,assets,vanilla,forge,launch,game,evidence/'scenario')
if __name__=='__main__':main()
