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
    parser=argparse.ArgumentParser();parser.add_argument('--ui',action='store_true');args_cli=parser.parse_args()
    out=ROOT/('build/e2e/forge16165-product-ui' if args_cli.ui else 'build/e2e/forge16165-product');out.mkdir(parents=True,exist_ok=False)
    aid=11411572027;repo=os.environ['GITHUB_REPOSITORY'];metadata=json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/actions/artifacts/{aid}']))
    expected='ec71b683292a120bed40b34c5496063b243985adda1c92166fb6b5759ed46a46'
    if metadata['expired'] or metadata['workflow_run']['id']!=37460307432 or metadata['workflow_run']['head_sha']!='736e50c9924139b377717a0f734818c25838dcdd' or metadata['digest']!='sha256:'+expected:raise ValueError('Product provider identity')
    archive=out/'product.zip'
    with archive.open('xb') as output:subprocess.run(['gh','api',f'repos/{repo}/actions/artifacts/{aid}/zip'],stdout=output,check=True)
    if sha(archive)!=expected:raise ValueError('Product archive bytes')
    game=out/'game';(game/'mods').mkdir(parents=True)
    with zipfile.ZipFile(archive) as z:
        jars=[n for n in z.namelist() if n.endswith('.jar')];receipts=[n for n in z.namelist() if n.endswith('pack-receipt.json')]
        if len(jars)!=1 or len(receipts)!=1:raise ValueError('Sole product package')
        data=z.read(jars[0]);receipt=json.loads(z.read(receipts[0]))
        if hashlib.sha256(data).hexdigest()!=receipt['outputSha256'] or receipt['outputSha256']!='661ab059ea2b50fa45dec9ffa652f3dfcc0dd6fc390816fd9a8b962391081d38':raise ValueError('Product JAR identity')
        (game/'mods/openallay.jar').write_bytes(data)
    stock=module('product_stock',ROOT/'scripts/forge16165-engine-prerequisite/stock/stock-forge36-prerequisite.py')
    runtime,launch,freeze=stock.load_helpers()
    install=json.loads((stock.PACKET/'install_profile.json').read_text());version=json.loads((stock.PACKET/'version.json').read_text());vanilla=json.loads((stock.PACKET/'minecraft-1.16.5.json').read_text());required=stock.validate_metadata(install,version,vanilla)
    args=type('Inputs',(),{'repo':ROOT,'minecraft_root':ROOT/'build/e2e/runtime/forge16165-stock/minecraft','java':Path(os.environ['JAVA_HOME_17_X64'])/'bin/java','java_release':'17.0.18+8'})()
    root,java,assets=stock.prepare(args,runtime,launch,freeze,install,version,vanilla)
    cp=launch.version_libraries(vanilla,root,Path('/nonexistent'),allow_gradle=False);fml=launch.version_libraries(version,root,Path('/nonexistent'),allow_gradle=False);replace={tuple(n.split(':')[:2]) for n,_ in fml};cp=[(n,p) for n,p in cp if tuple(n.split(':')[:2]) not in replace]+fml
    classpath=stock.inspect_classpath(cp,required,runtime);natives=launch.extract_natives(launch.native_libraries(vanilla,root),out/'natives')
    (game/'options.txt').write_text('renderDistance:4\nmaxFps:30\npauseOnLostFocus:false\nguiScale:2\n' + ('lang:zh_cn\n' if args_cli.ui else ''))
    values={'natives_directory':out/'natives','launcher_name':'OpenAllayProductAcceptance','launcher_version':'native-product','classpath':os.pathsep.join(str(p) for _,p in cp),'auth_player_name':'devGameUser','version_name':stock.PROFILE,'game_directory':game,'assets_root':assets,'assets_index_name':vanilla['assetIndex']['id'],'auth_uuid':launch.offline_uuid('devGameUser'),'auth_access_token':'0','user_type':'legacy','version_type':version['type'],'resolution_width':'1280','resolution_height':'960'}
    features={'has_custom_resolution':True};jvm=launch.expand_arguments(vanilla['arguments']['jvm'],values,features)+stock.FLAGS;gameargs=launch.expand_arguments(vanilla['arguments']['game'],values,features)+version['arguments']['game']
    command=[str(java),'-Xms256M','-Xmx1536M','-Xlog:class+load=info:file='+str(out/'class-load.log')]+jvm+[stock.MAIN]+gameargs
    runtime.write_json(out/'launch.json',{'command':command,'classpath':classpath,'productSha256':receipt['outputSha256'],'nativeBuildRun':37460049017,'packRun':37460307432,'noCompileReplay':True,'noEnginePrerequisiteReplay':True})
    env={k:v for k,v in os.environ.items() if k not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH','DISPLAY']};env['LIBGL_ALWAYS_SOFTWARE']='1';env['ALSOFT_DRIVERS']='null'
    fixture=None;fixture_stream=None
    if args_cli.ui:
        config=game/'config/openallay';config.mkdir(parents=True,exist_ok=True)
        (config/'models.json').write_text(json.dumps({'defaultProfileId':'e2e-fixture','profiles':[{'id':'e2e-fixture','displayName':'OpenAllay E2E Fixture','enabled':True,'protocol':'openai_chat','baseUrl':'http://127.0.0.1:18765/v1/','model':'openallay-e2e-fixture','credentialRef':'env:OPENALLAY_E2E_FIXTURE_KEY','contextWindowTokens':256000,'maxOutputTokens':8192,'connectTimeoutSeconds':10,'requestTimeoutSeconds':120}]},indent=2)+'\n')
        env['OPENALLAY_E2E_FIXTURE_KEY']='isolated-loopback-no-secret'
        flags=['-Dopenallay.e2e.enabled=true','-Dopenallay.e2e.scenario=ui-manual-regressions','-Dopenallay.e2e.question=OpenAllay E2E UI manual regressions','-Dopenallay.e2e.session=e2e','-Dopenallay.e2e.modelMode=client','-Dopenallay.e2e.report='+str(out/'ui-report.json'),'-Dopenallay.e2e.trace='+str(out/'ui-trace.json'),'-Dopenallay.e2e.screenshotRoot='+str(out/'screenshots'),'-Dopenallay.e2e.createWorld=openallay-builder-forge16-native-ui','-Dopenallay.e2e.timeoutSeconds=600','-Dopenallay.e2e.shutdown=true','-Dopenallay.e2e.sourceRevision=966192594efc73ea389682b5690765db53011f39']
        command[1:1]=flags
        fixture_stream=(out/'model-fixture.log').open('w')
        fixture=subprocess.Popen([sys.executable,'-B',str(ROOT/'scripts/e2e-model-fixture.py'),'--port','18765'],stdout=fixture_stream,stderr=subprocess.STDOUT,start_new_session=True,env=env)
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
                try:process.wait(timeout=660 if args_cli.ui else 100)
                except subprocess.TimeoutExpired:pass
                log.flush();text=(out/'client.log').read_text(errors='replace')
                from PIL import ImageGrab
                ImageGrab.grab(xdisplay=env['DISPLAY']).save(out/'actual-product-frame.png')
                result['clientStillRunning']=process.poll() is None;result['bootstrapInitialized']='Initialized OpenAllay on' in text
                result['fatalMarkers']=[line for line in text.splitlines() if any(k in line for k in ['Mixin apply failed','MixinTransformerError','InvalidMixinException','NoClassDefFoundError','Exception in thread','Failed to create mod instance','Exception caught during firing event'])]
                if args_cli.ui:
                    report_path=out/'ui-report.json'
                    report=json.loads(report_path.read_text()) if report_path.exists() else {}
                    result['uiOutcome']=report.get('outcome','NO_REPORT')
                    result['nativeExitCode']=process.poll()
                    result['status']='UI_ACCEPTANCE_PASS' if report.get('outcome')=='COMPLETED' and process.poll()==0 and not result['fatalMarkers'] else 'FAILED'
                    result['cleanShutdownProven']=result['status']=='UI_ACCEPTANCE_PASS'
                else:
                    result['status']='BOOT_BINDINGS_REVIEW' if result['clientStillRunning'] and result['bootstrapInitialized'] and not result['fatalMarkers'] else 'FAILED'
    finally:
        if process is not None:result['ownedClientStop']=stock.stop_owned(process)
        if xvfb is not None:result['ownedDisplayStop']=stock.stop_owned(xvfb)
        if fixture is not None:result['ownedFixtureStop']=stock.stop_owned(fixture)
        if fixture_stream is not None:fixture_stream.close()
        result['collectionStopIntentional']=True;runtime.write_json(out/'receipt.json',result);print(json.dumps(result))
    if result['status'] not in ('BOOT_BINDINGS_REVIEW','UI_ACCEPTANCE_PASS'):raise SystemExit(1)
if __name__=='__main__':main()
