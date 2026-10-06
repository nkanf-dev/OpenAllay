#!/usr/bin/env python3
"""One affected component startup; consume retained provider-verified pack, never repack."""
import argparse,importlib.util,json,os
from pathlib import Path
from types import SimpleNamespace
ROOT=Path(__file__).resolve().parents[1]
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path);result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--product-pin',type=Path,required=True);parser.add_argument('--applied-bindings',action='store_true');parser.add_argument('--world-sdk',action='store_true');parser.add_argument('--ui-manual',action='store_true');parser.add_argument('--world-persistence',action='store_true');parser.add_argument('--builder-scenario',choices=['restricted','partial','cancel','undo','legacy-shapes']);args=parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote component runtime only')
    provider=module('native_provider',ROOT/'scripts/build-forge1122-native.py')
    report=ROOT/'build/forge1122-component-boot-report';report.mkdir(parents=True,exist_ok=False)
    work=ROOT/'build/forge1122-component-boot-inputs';work.mkdir(exist_ok=False)
    pin=json.loads(args.product_pin.read_text());retained=provider.retained(pin,work/'product',report,'product')
    paths=list(retained.rglob('component-inputs.json'))
    if len(paths)!=1:raise ValueError('One accepted packed component manifest required')
    packed=json.loads(paths[0].read_text());root=paths[0].parent
    for record in packed['artifacts']:
        local=root/Path(record['path']).name
        if provider.sha(local)!=record['sha256']:raise ValueError('Packed provider bytes changed')
        record['path']=str(local)
    packed['provider']=pin
    custody=list(retained.rglob('custody.json'))
    if len(custody)!=1:raise ValueError('Retained component source custody required')
    packed['nativeCompiledSource']=json.loads(custody[0].read_text())['providers']['nativeCompiledSource']
    builder_jar=None;builder_pin=None
    if args.builder_scenario:
        builder_pin=json.loads((ROOT/"native-builds/forge1122-component/builder-candidate-provider.json").read_text())
        candidate=provider.retained(builder_pin["provider"],work/"builder",report,"builder")
        jars=[p for p in candidate.rglob("*.jar") if provider.sha(p)==builder_pin["jarSha256"]]
        if len(jars)!=1:raise ValueError("One exact real Builder candidate JAR required")
        builder_jar=jars[0]
        with __import__("zipfile").ZipFile(builder_jar) as z:
            if any(int.from_bytes(z.read(e)[6:8],"big")!=52 for e in z.namelist() if e.endswith(".class")):
                raise ValueError("SDK Builder candidate must remain Java8")
    local_manifest=work/'component-runtime-inputs.json';provider.write(local_manifest,packed)
    stock=module('stock_runner',ROOT/'scripts/forge1122-runtime-prerequisite/stock-forge1122-prerequisite.py')
    runtime,launch,freeze=stock.load_helpers(ROOT)
    install=json.loads((stock.PACKET/'install_profile.json').read_text());version=json.loads((stock.PACKET/'version.json').read_text());vanilla=json.loads((stock.PACKET/'minecraft-1.12.2.json').read_text());expected=stock.validate_metadata(install,version,vanilla)
    runargs=SimpleNamespace(repo=ROOT,java=Path(os.environ['OPENALLAY_COMPONENT_JAVA17_HOME'])/'bin/java',java_release='17.0.18+8',minecraft_root=ROOT/'build/e2e/runtime/forge1122-stock/minecraft',output=ROOT/'build/e2e/forge1122-component',title_only=True,pack200_bridge=True,launchwrapper_bridge=True,objectholder_bridge=True,objectholder_phase_diagnostic=False,component_inputs=local_manifest,applied_bindings=args.applied_bindings,world_sdk=args.world_sdk,ui_manual=args.ui_manual,creates_disposable_world=args.world_sdk or args.ui_manual or bool(args.builder_scenario),builder_scenario=args.builder_scenario,builder_jar=builder_jar,builder_sha256=builder_pin["jarSha256"] if builder_pin else None,builder_provider=builder_pin["provider"] if builder_pin else None,builder_extension_source=builder_pin["extensionSource"] if builder_pin else None)
    if args.world_persistence:
        runargs.world_sdk=True;runargs.applied_bindings=True;runargs.creates_disposable_world=True
    root,java,assets=stock.prepare(runargs,runtime,launch,freeze,install,version,vanilla)
    if not args.world_persistence:
        raise SystemExit(stock.boot(runargs,root,java,assets,runtime,launch,expected,vanilla,version))
    evidence=runargs.output;evidence.mkdir(parents=True,exist_ok=False)
    game=evidence/"game";runargs.game_directory=game
    runargs.world_name="openallay-builder-forge1122-persist-"+str(os.getpid())
    reports=[]
    for phase in ("persist","reload"):
        runargs.world_phase=phase;runargs.output=evidence/phase
        result=stock.boot(runargs,root,java,assets,runtime,launch,expected,vanilla,version)
        if result:raise SystemExit(result)
        report_data=json.loads((runargs.output/"world-sdk-report.json").read_text())
        process_data=json.loads((runargs.output/"receipt.json").read_text())
        if process_data["termination"]["finalExitCode"]!=0 or process_data["termination"]["signals"]:raise ValueError("Persistence phases require two natural clean exits")
        reports.append(report_data)
    retained=json.loads((game/"config/openallay/e2e/native-world-persistence.json").read_text())
    if reports[0]["worldId"]!=reports[1]["worldId"] or reports[0]["actual"]!=reports[1]["persistedActual"] or retained["worldId"]!=reports[0]["worldId"] or retained["actual"]!=reports[1]["persistedActual"] or reports[1].get("originalImageRestored") is not True:
        raise ValueError("Actual persisted UUID/full native image/restore differs")
    provider.write(evidence/"world-persistence-acceptance.json",{"accepted":True,"sameIsolatedProfile":True,
        "sameWorld":runargs.world_name,"twoNaturalExit0":True,"actualNativeUUID":reports[0]["worldId"],
        "actualFullContainerImage":reports[1]["persistedActual"],"originalImageRestored":True,
        "retainedNativeReceipt":retained,"gameProfileUploaded":False})
if __name__=='__main__':main()
