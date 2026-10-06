#!/usr/bin/env python3
"""Bundle one provider-verified raw universal Builder; never rebuild or alter product classes."""
import importlib.util,json,os,hashlib,zipfile,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path);result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result

def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote bundling only')
    provider=module('native_provider',ROOT/'scripts/build-forge1122-native.py')
    report=ROOT/'build/forge1122-component-report';report.mkdir(parents=True,exist_ok=False)
    work=ROOT/'build/forge1122-component-inputs';work.mkdir(exist_ok=False)
    productpin=json.loads((ROOT/'native-builds/forge1122-component/accepted-product-provider.json').read_text())
    kept=provider.retained(productpin,work/'product',report,'product')
    manifest=next(kept.rglob('component-inputs.json'));product=manifest.parent;component=json.loads(manifest.read_text())
    for record in component['artifacts']:
        if provider.sha(product/Path(record['path']).name)!=record['sha256']:raise ValueError('Accepted component bytes differ')
    pin=json.loads((ROOT/'native-builds/forge1122-component/builder-candidate-provider.json').read_text())
    candidate=provider.retained(pin['provider'],work/'candidate',report,'candidate')
    jars=[p for p in candidate.rglob('*.jar') if provider.sha(p)==pin['jarSha256']]
    if len(jars)!=1:raise ValueError('One exact tested Builder candidate required')
    data=jars[0].read_bytes();lock=json.loads((ROOT/'native-builds/forge1122-component/builder-bundle.lock.json').read_text())
    if lock['source']['revision']!=pin['extensionSource'] or lock['openAllayApiVersion']!='0.4.0':raise ValueError('Exact tested Builder source/API lock differs')
    sys=__import__('sys');sys.path.insert(0,str(ROOT/'scripts'))
    verifier=module('normal_builder_verifier',ROOT/'scripts/verify-bundled-extensions.py')
    verifier.verify_universal(data,lock)
    with zipfile.ZipFile(jars[0]) as z:
        descriptor=json.loads(z.read('META-INF/openallay-extension.json'))
        if descriptor['support']['validatedTargetIds']!=[] or len(descriptor['support']['targets'])!=50 or descriptor['support']['minimumJavaVersion']!=8:raise ValueError('Tested candidate declaration changed')
        entry_hashes={i.filename:hashlib.sha256(z.read(i)).hexdigest() for i in z.infolist() if not i.is_dir()}
    resource='META-INF/openallay/bundled-extensions/openallay-builder-universal-0.4.0.jar'
    provenance={'source':{**lock['source'],'dirty':False,'pinned':True},'project':lock['project'],'version':lock['version'],
        'extensionId':lock['extensionId'],'openAllayApiVersion':lock['openAllayApiVersion'],'artifact':{'path':resource,'sha256':pin['jarSha256']}}
    verifier.verify_provenance(provenance,lock,pin['jarSha256'],False)
    additions={resource:data,'META-INF/openallay/distribution.json':(json.dumps(provenance,indent=2)+'\n').encode()}
    core=product/'openallay-feature-core.jar';out=ROOT/'build/forge1122-component-product';out.mkdir()
    unchanged={};prior_bundle=[]
    with zipfile.ZipFile(core) as source,zipfile.ZipFile(out/core.name,'w') as target:
        for info in source.infolist():
            content=source.read(info)
            if info.filename in additions:
                # Refuse hidden replacement of an independently bundled package/provenance.
                if content!=additions[info.filename]:raise ValueError('Core already bundles different provenance/package')
                prior_bundle.append(info.filename);continue
            if info.filename.startswith('META-INF/openallay/bundled-extensions/') and info.filename.endswith('.jar'):raise ValueError('Unexpected preexisting bundled Extension')
            target.writestr(info,content);unchanged[info.filename]=hashlib.sha256(content).hexdigest()
        for name,content in additions.items():target.writestr(name,content)
    with zipfile.ZipFile(out/core.name) as target:
        for name,digest in unchanged.items():
            if hashlib.sha256(target.read(name)).hexdigest()!=digest:raise ValueError('Existing product entry changed: '+name)
        if target.read(resource)!=data:raise ValueError('Bundled raw candidate bytes changed')
    for name in ('openallay-lifecycle-facade.jar','openallay-private-mixin.jar'):
        shutil.copyfile(product/name,out/name)
    for name in ('builder-retained.jar','builder-provenance.json'):
        if(product/name).exists():shutil.copyfile(product/name,out/name)
    records=[{'path':str(out/name),'sha256':provider.sha(out/name)} for name in ('openallay-feature-core.jar','openallay-lifecycle-facade.jar','openallay-private-mixin.jar')]
    provider.write(out/'component-inputs.json',{**component,'artifacts':records,'builderBundled':True,'builderProvider':pin,'builderSource':pin['extensionSource'],'builderRawResource':resource,'nativeCompiledSourceUnchanged':True})
    custody=json.loads(next(kept.rglob('custody.json')).read_text());custody['bundledBuilder']={'provider':pin,'lock':lock,'provenance':provenance,'rawJarSha256':pin['jarSha256'],'descriptorChanged':False}
    provider.write(report/'custody.json',custody)
    provider.write(report/'builder-bundle-custody.json',{'acceptedProduct':productpin,'candidateProvider':pin,'sourceLock':lock,'provenance':provenance,
        'oldBundleResources':prior_bundle,'newResourceHashes':{n:hashlib.sha256(b).hexdigest() for n,b in additions.items()},
        'candidateEntryHashes':entry_hashes,'unchangedProductEntries':unchanged,'classBytesChanged':False,'candidateValidatedTargetsUnchanged':[],
        'normalRuntimeContract':'BundledUniversalExtensions distribution.json -> checksum cache -> UniversalExtensionDiscovery',
        'configExternalCandidateInstalled':False})
    provider.write(report/'RESULT.json',{'status':'passed','builderBundled':True,'rawCandidateBytesUnchanged':True,'allProductClassesUnchanged':True,
        'nativeEngineSdkRhinoRebuilt':False,'privateMixinFacadeCopiedUnchanged':True,'testedExtensionSource':pin['extensionSource']})
if __name__=='__main__':main()
