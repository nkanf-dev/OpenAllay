#!/usr/bin/env python3
"""First genuine product client boot/binding check. No compile or prior probe replay."""
import argparse,hashlib,importlib.util,json,os,select,shutil,subprocess,sys,time,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote only')
    parser=argparse.ArgumentParser();parser.add_argument('--ui',action='store_true');parser.add_argument('--world',action='store_true');parser.add_argument('--persistence',action='store_true');parser.add_argument('--builder-call',action='store_true');parser.add_argument('--builder-scenario',choices=['restricted','partial','cancel','undo'],default='restricted');args_cli=parser.parse_args()
    if args_cli.persistence:args_cli.world=True
    out=ROOT/('build/e2e/forge16165-product-builder' if args_cli.builder_call else 'build/e2e/forge16165-product-world' if args_cli.world else 'build/e2e/forge16165-product-ui' if args_cli.ui else 'build/e2e/forge16165-product');out.mkdir(parents=True,exist_ok=False)
    aid=11416338261;repo=os.environ['GITHUB_REPOSITORY'];metadata=json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/actions/artifacts/{aid}']))
    expected='04877130ebdf4c873ab7278223d1b23031cf9e0bd621baf3bc6cba7b14799ac7'
    if metadata['expired'] or metadata['workflow_run']['id']!=37470983514 or metadata['workflow_run']['head_sha']!='80307399ffa70769a3bc9e4e755bf32df0e5f58e' or metadata['digest']!='sha256:'+expected:raise ValueError('Product provider identity')
    archive=out/'product.zip'
    with archive.open('xb') as output:subprocess.run(['gh','api',f'repos/{repo}/actions/artifacts/{aid}/zip'],stdout=output,check=True)
    if sha(archive)!=expected:raise ValueError('Product archive bytes')
    game=out/'game';(game/'mods').mkdir(parents=True)
    with zipfile.ZipFile(archive) as z:
        jars=[n for n in z.namelist() if n.endswith('.jar')];receipts=[n for n in z.namelist() if n.endswith('pack-receipt.json')]
        if len(jars)!=1 or len(receipts)!=1:raise ValueError('Sole product package')
        data=z.read(jars[0]);receipt=json.loads(z.read(receipts[0]))
        if hashlib.sha256(data).hexdigest()!=receipt['outputSha256'] or receipt['outputSha256']!='87318b4361f30f1fdf324eaff093a70b07ec048ea7e93995cafdcbe81b62f53e':raise ValueError('Product JAR identity')
        (game/'mods/openallay.jar').write_bytes(data)
    stock=module('product_stock',ROOT/'scripts/forge16165-engine-prerequisite/stock/stock-forge36-prerequisite.py')
    runtime,launch,freeze=stock.load_helpers()
    install=json.loads((stock.PACKET/'install_profile.json').read_text());version=json.loads((stock.PACKET/'version.json').read_text());vanilla=json.loads((stock.PACKET/'minecraft-1.16.5.json').read_text());required=stock.validate_metadata(install,version,vanilla)
    args=type('Inputs',(),{'repo':ROOT,'minecraft_root':ROOT/'build/e2e/runtime/forge16165-stock/minecraft','java':Path(os.environ['JAVA_HOME_17_X64'])/'bin/java','java_release':'17.0.18+8'})()
    root,java,assets=stock.prepare(args,runtime,launch,freeze,install,version,vanilla)
    cp=launch.version_libraries(vanilla,root,Path('/nonexistent'),allow_gradle=False);fml=launch.version_libraries(version,root,Path('/nonexistent'),allow_gradle=False);replace={tuple(n.split(':')[:2]) for n,_ in fml};cp=[(n,p) for n,p in cp if tuple(n.split(':')[:2]) not in replace]+fml
    classpath=stock.inspect_classpath(cp,required,runtime);natives=launch.extract_natives(launch.native_libraries(vanilla,root),out/'natives')
    (game/'options.txt').write_text('renderDistance:4\nmaxFps:30\npauseOnLostFocus:false\n' + ('guiScale:1\nlang:zh_cn\n' if args_cli.ui else 'guiScale:2\n'))
    values={'natives_directory':out/'natives','launcher_name':'OpenAllayProductAcceptance','launcher_version':'native-product','classpath':os.pathsep.join(str(p) for _,p in cp),'auth_player_name':'devGameUser','version_name':stock.PROFILE,'game_directory':game,'assets_root':assets,'assets_index_name':vanilla['assetIndex']['id'],'auth_uuid':launch.offline_uuid('devGameUser'),'auth_access_token':'0','user_type':'legacy','version_type':version['type'],'resolution_width':'1280','resolution_height':'960'}
    features={'has_custom_resolution':True};jvm=launch.expand_arguments(vanilla['arguments']['jvm'],values,features)+stock.FLAGS;gameargs=launch.expand_arguments(vanilla['arguments']['game'],values,features)+version['arguments']['game']
    command=[str(java),'-Xms256M','-Xmx1536M','-Xlog:class+load=info:file='+str(out/'class-load.log')]+jvm+[stock.MAIN]+gameargs
    runtime.write_json(out/'launch.json',{'command':command,'classpath':classpath,'productSha256':receipt['outputSha256'],'nativeBuildRun':37470608150,'packRun':37470983514,'noCompileReplay':True,'noEnginePrerequisiteReplay':True})
    env={k:v for k,v in os.environ.items() if k not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH','DISPLAY']};env['LIBGL_ALWAYS_SOFTWARE']='1';env['ALSOFT_DRIVERS']='null'
    fixture=None;fixture_stream=None
    if args_cli.ui or args_cli.builder_call:
        config=game/'config/openallay';config.mkdir(parents=True,exist_ok=True)
        (config/'models.json').write_text(json.dumps({'defaultProfileId':'e2e-fixture','profiles':[{'id':'e2e-fixture','displayName':'OpenAllay E2E Fixture','enabled':True,'protocol':'openai_chat','baseUrl':'http://127.0.0.1:18765/v1/','model':'openallay-e2e-fixture','credentialRef':'env:OPENALLAY_E2E_FIXTURE_KEY','contextWindowTokens':256000,'maxOutputTokens':8192,'connectTimeoutSeconds':10,'requestTimeoutSeconds':120}]},indent=2)+'\n')
        env['OPENALLAY_E2E_FIXTURE_KEY']='isolated-loopback-no-secret'
        flags=['-Dopenallay.e2e.enabled=true','-Dopenallay.e2e.scenario=ui-manual-regressions','-Dopenallay.e2e.question=OpenAllay E2E UI manual regressions','-Dopenallay.e2e.session=e2e','-Dopenallay.e2e.modelMode=client','-Dopenallay.e2e.report='+str(out/'ui-report.json'),'-Dopenallay.e2e.trace='+str(out/'ui-trace.json'),'-Dopenallay.e2e.screenshotRoot='+str(out/'screenshots'),'-Dopenallay.e2e.createWorld=openallay-builder-forge16-native-ui','-Dopenallay.e2e.timeoutSeconds=600','-Dopenallay.e2e.shutdown=true','-Dopenallay.e2e.sourceRevision=108f61a6a64b9ae26c525a57d9b3f4873a59b2fd']
        if args_cli.builder_call:
            builder_archive=out/'builder-candidate.zip'
            aid_builder=11417805653
            info=json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/actions/artifacts/{aid_builder}']))
            digest='c675991790369c508acde001abd82e53486b820a4f3a2053cb16a4d832280e09'
            if info['expired'] or info['workflow_run']['id']!=37472367504 or info['digest']!='sha256:'+digest:raise ValueError('Builder candidate provider')
            with builder_archive.open('xb') as output:subprocess.run(['gh','api',info['archive_download_url']],stdout=output,check=True)
            if sha(builder_archive)!=digest:raise ValueError('Builder candidate archive checksum')
            with zipfile.ZipFile(builder_archive) as z:
                paths=[n for n in z.namelist() if n.endswith('builder-candidate.jar')]
                if len(paths)!=1:raise ValueError('One candidate Builder')
                candidate=z.read(paths[0])
                if hashlib.sha256(candidate).hexdigest()!='cbdeba2e7f9090241a3475fe958d819d728565dd916c121fbfac2782707eff32':raise ValueError('Candidate Builder bytes')
            extensions=config/'extensions';extensions.mkdir()
            (extensions/'openallay-builder-candidate.jar').write_bytes(candidate)
            flags=['-Dopenallay.e2e.enabled=true','-Dopenallay.e2e.scenario=builder-'+args_cli.builder_scenario,'-Dopenallay.e2e.question=OpenAllay E2E Builder '+args_cli.builder_scenario,'-Dopenallay.e2e.session=e2e','-Dopenallay.e2e.modelMode=client','-Dopenallay.e2e.report='+str(out/'builder-report.json'),'-Dopenallay.e2e.trace='+str(out/'builder-trace.json'),'-Dopenallay.e2e.createWorld=openallay-builder-forge16-candidate','-Dopenallay.e2e.timeoutSeconds=300','-Dopenallay.e2e.shutdown=true']
        command[1:1]=flags
        fixture_stream=(out/'model-fixture.log').open('w')
        fixture=subprocess.Popen([sys.executable,'-B',str(ROOT/'scripts/e2e-model-fixture.py'),'--port','18765'],stdout=fixture_stream,stderr=subprocess.STDOUT,start_new_session=True,env=env)
    if args_cli.world:
        command[1:1]=['-Dopenallay.e2e.enabled=true','-Dopenallay.e2e.scenario=native-world-sdk','-Dopenallay.e2e.question=Native world SDK acceptance without model','-Dopenallay.e2e.report='+str(out/'world-report.json'),'-Dopenallay.e2e.trace='+str(out/'world-trace.json'),'-Dopenallay.e2e.createWorld=openallay-builder-forge16-world-sdk','-Dopenallay.e2e.timeoutSeconds=300','-Dopenallay.e2e.shutdown=true']
    if args_cli.persistence:command.insert(1,'-Dopenallay.e2e.worldPhase=persist')
    process=None;xvfb=None;result={'status':'FAILED','productSha256':receipt['outputSha256'],'source':os.environ['GITHUB_SHA'],'gameLaunched':False,'fullNativeSupport':False,'cleanShutdownProven':False}
    try:
        with (out/'xvfb.log').open('w') as xlog:
            xvfb=subprocess.Popen(['Xvfb','-displayfd','1','-screen','0','1280x960x24','-nolisten','tcp'],stdout=subprocess.PIPE,stderr=xlog,env=env,start_new_session=True)
            ready,_,_=select.select([xvfb.stdout],[],[],15)
            if not ready:raise ValueError('Owned display startup failed')
            display=xvfb.stdout.readline().decode().strip()
            if not display.isdecimal():raise ValueError('Display identity')
            env['DISPLAY']=':'+display
            with (out/'client.log').open('w') as log:
                process=subprocess.Popen(command,cwd=game,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True);result['gameLaunched']=True;result['pid']=process.pid
                try:process.wait(timeout=360 if args_cli.builder_call else 660 if args_cli.ui else 360 if args_cli.world else 100)
                except subprocess.TimeoutExpired:pass
                log.flush();text=(out/'client.log').read_text(errors='replace')
                if args_cli.persistence:
                    first_report=json.loads((out/'world-report.json').read_text())
                    if first_report.get('outcome')!='COMPLETED' or process.poll()!=0:raise ValueError('Native persistence first phase failed; original report retained')
                    result['persistPhase']={'pid':process.pid,'exitCode':process.poll(),'report':first_report}
                    reload_command=[arg.replace('-Dopenallay.e2e.worldPhase=persist','-Dopenallay.e2e.worldPhase=reload').replace('-Dopenallay.e2e.createWorld=','-Dopenallay.e2e.resumeWorld=').replace(str(out/'world-report.json'),str(out/'world-reload-report.json')).replace(str(out/'world-trace.json'),str(out/'world-reload-trace.json')) for arg in command]
                    runtime.write_json(out/'reload-launch.json',{'command':reload_command,'sameGameProfile':str(game),'world':first_report.get('world'),'firstWorldId':first_report.get('worldId'),'productSha256':receipt['outputSha256'],'compileReplayed':False})
                    with (out/'reload-client.log').open('w') as reload_log:
                        process=subprocess.Popen(reload_command,cwd=game,env=env,stdout=reload_log,stderr=subprocess.STDOUT,start_new_session=True)
                        result['reloadPid']=process.pid
                        try:process.wait(timeout=360)
                        except subprocess.TimeoutExpired:pass
                        reload_log.flush()
                    text+='\n'+(out/'reload-client.log').read_text(errors='replace')
                from PIL import ImageGrab
                ImageGrab.grab(xdisplay=env['DISPLAY']).save(out/'actual-product-frame.png')
                result['clientStillRunning']=process.poll() is None;result['bootstrapInitialized']='Initialized OpenAllay on' in text
                result['fatalMarkers']=[line for line in text.splitlines() if any(k in line for k in ['Mixin apply failed','MixinTransformerError','InvalidMixinException','NoClassDefFoundError','Exception in thread','Failed to create mod instance','Exception caught during firing event'])]
                if args_cli.ui or args_cli.world or args_cli.builder_call:
                    report_path=out/('builder-report.json' if args_cli.builder_call else 'world-reload-report.json' if args_cli.persistence else 'world-report.json' if args_cli.world else 'ui-report.json')
                    report=json.loads(report_path.read_text()) if report_path.exists() else {}
                    result['uiOutcome']=report.get('outcome','NO_REPORT')
                    result['nativeExitCode']=process.poll()
                    result['status']=('BUILDER_INVOCATION_PASS' if args_cli.builder_call else 'WORLD_SDK_PASS' if args_cli.world else 'UI_ACCEPTANCE_PASS') if report.get('outcome')=='COMPLETED' and (not args_cli.builder_call or report.get('nativeAcceptance',{}).get('outcome')=='PASSED') and process.poll()==0 and not result['fatalMarkers'] else 'FAILED'
                    result['cleanShutdownProven']=result['status'] in ('UI_ACCEPTANCE_PASS','WORLD_SDK_PASS','BUILDER_INVOCATION_PASS')
                else:
                    result['status']='BOOT_BINDINGS_REVIEW' if result['clientStillRunning'] and result['bootstrapInitialized'] and not result['fatalMarkers'] else 'FAILED'
    finally:
        if process is not None:result['ownedClientStop']=stock.stop_owned(process)
        if xvfb is not None:result['ownedDisplayStop']=stock.stop_owned(xvfb)
        if fixture is not None:result['ownedFixtureStop']=stock.stop_owned(fixture)
        if fixture_stream is not None:fixture_stream.close()
        result['collectionStopIntentional']=True;runtime.write_json(out/'receipt.json',result);print(json.dumps(result))
    if result['status'] not in ('BOOT_BINDINGS_REVIEW','UI_ACCEPTANCE_PASS','WORLD_SDK_PASS','BUILDER_INVOCATION_PASS'):raise SystemExit(1)
if __name__=='__main__':main()
