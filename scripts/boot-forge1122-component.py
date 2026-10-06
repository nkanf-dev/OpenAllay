#!/usr/bin/env python3
"""One affected component startup; consume retained provider-verified pack, never repack."""
import argparse,importlib.util,json,os
from pathlib import Path
from types import SimpleNamespace
ROOT=Path(__file__).resolve().parents[1]
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path);result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--product-pin',type=Path,required=True);args=parser.parse_args()
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
    local_manifest=work/'component-runtime-inputs.json';provider.write(local_manifest,packed)
    stock=module('stock_runner',ROOT/'scripts/forge1122-runtime-prerequisite/stock-forge1122-prerequisite.py')
    runtime,launch,freeze=stock.load_helpers(ROOT)
    install=json.loads((stock.PACKET/'install_profile.json').read_text());version=json.loads((stock.PACKET/'version.json').read_text());vanilla=json.loads((stock.PACKET/'minecraft-1.12.2.json').read_text());expected=stock.validate_metadata(install,version,vanilla)
    runargs=SimpleNamespace(repo=ROOT,java=Path(os.environ['OPENALLAY_COMPONENT_JAVA17_HOME'])/'bin/java',java_release='17.0.18+8',minecraft_root=ROOT/'build/e2e/runtime/forge1122-stock/minecraft',output=ROOT/'build/e2e/forge1122-component',title_only=True,pack200_bridge=True,launchwrapper_bridge=True,objectholder_bridge=True,objectholder_phase_diagnostic=False,component_inputs=local_manifest)
    root,java,assets=stock.prepare(runargs,runtime,launch,freeze,install,version,vanilla)
    raise SystemExit(stock.boot(runargs,root,java,assets,runtime,launch,expected,vanilla,version))
if __name__=='__main__':main()
