#!/usr/bin/env python3
"""Replace only provider-verified native entries in accepted feature core; no compilation."""
import importlib.util,json,os,hashlib,zipfile,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path);r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r);return r

def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote only')
    provider=module('native_provider',ROOT/'scripts/build-forge1122-native.py')
    newpin=json.loads((ROOT/'native-builds/forge1122-component/native-refresh-provider.json').read_text())
    if set(newpin)!={'provider','nativeJarSha256','nativeCompiledSource'}:raise ValueError('Exact new native source/provider shape')
    report=ROOT/'build/forge1122-component-report';report.mkdir(parents=True,exist_ok=False)
    work=ROOT/'build/forge1122-component-inputs';work.mkdir(exist_ok=False)
    productpin=json.loads((ROOT/'native-builds/forge1122-component/accepted-product-provider.json').read_text())
    kept=provider.retained(productpin,work/'accepted-product',report,'accepted-product')
    manifest=next(kept.rglob('component-inputs.json'));product=manifest.parent;old=json.loads(manifest.read_text())
    for record in old['artifacts']:
        if provider.sha(product/Path(record['path']).name)!=record['sha256']:raise ValueError('Accepted component differs')
    custody=json.loads(next(kept.rglob('custody.json')).read_text())
    previous=provider.retained(custody['providers']['native'],work/'previous-native',report,'previous-native')
    actual=provider.retained(newpin['provider'],work/'new-native',report,'new-native')
    oldsha=custody['providers']['nativeJarSha256']
    def exact_jar(root,digest):
        matches=[p for p in root.rglob('*.jar') if provider.sha(p)==digest]
        if len(matches)!=1:raise ValueError('One exact native JAR required')
        return matches[0]
    oldnative=exact_jar(previous,oldsha);newnative=exact_jar(actual,newpin['nativeJarSha256'])
    receipts=list(actual.rglob('native-build-receipt.json'))
    if len(receipts)!=1:raise ValueError('One normal native compile/AP/reobf receipt required')
    receipt=json.loads(receipts[0].read_text())
    if receipt.get('jarSha256')!=newpin['nativeJarSha256'] or newpin['provider']['sourceRevision']!=newpin['nativeCompiledSource']:
        raise ValueError('Actual new native receipt/source differs')
    def native_entries(path):
        with zipfile.ZipFile(path) as z:return {i.filename:z.read(i) for i in z.infolist() if not i.is_dir() and i.filename not in ('META-INF/MANIFEST.MF','mcmod.info','META-INF/mods.toml','module-info.class') and not i.filename.endswith(('.SF','.RSA','.DSA'))}
    before=native_entries(oldnative);after=native_entries(newnative)
    core=product/'openallay-feature-core.jar';out=ROOT/'build/forge1122-component-product';out.mkdir()
    unchanged={};changed=[]
    with zipfile.ZipFile(core) as original,zipfile.ZipFile(out/core.name,'w') as destination:
        names=set(original.namelist())
        for name,data in before.items():
            if hashlib.sha256(original.read(name)).hexdigest()!=hashlib.sha256(data).hexdigest():raise ValueError('Original native entry custody differs: '+name)
        for entry in original.infolist():
            if entry.filename in before:continue
            data=original.read(entry);destination.writestr(entry,data);unchanged[entry.filename]=hashlib.sha256(data).hexdigest()
        for name,data in after.items():
            if name in names and name not in before:raise ValueError('New native collides with immutable owner: '+name)
            destination.writestr(name,data);changed.append({'entry':name,'sha256':hashlib.sha256(data).hexdigest()})
    with zipfile.ZipFile(out/core.name) as z:
        for name,digest in unchanged.items():
            if hashlib.sha256(z.read(name)).hexdigest()!=digest:raise ValueError('Non-native component entry changed')
    for name in ('openallay-lifecycle-facade.jar','openallay-private-mixin.jar','builder-retained.jar','builder-provenance.json'):
        if(product/name).exists():shutil.copyfile(product/name,out/name)
    records=[{'path':str(out/name),'sha256':provider.sha(out/name)} for name in ('openallay-feature-core.jar','openallay-lifecycle-facade.jar','openallay-private-mixin.jar')]
    custody['providers']['native']=newpin['provider'];custody['providers']['nativeJarSha256']=newpin['nativeJarSha256'];custody['providers']['nativeCompiledSource']=newpin['nativeCompiledSource']
    provider.write(report/'custody.json',custody)
    provider.write(out/'component-inputs.json',{**old,'artifacts':records,'nativeCompiledSource':newpin['nativeCompiledSource'],'nativeEntriesReplacedOnly':True,'privateMixinCopiedUnchanged':True,'facadeCopiedUnchanged':True})
    provider.write(report/'native-entry-custody.json',{'beforeProvider':productpin,'newNative':newpin,'actualNativeReceipt':receipt,'replaced':changed,'unchangedEntries':unchanged,'engineRebuilt':False,'nativeCompileReplayed':False,'privateMixinRebuilt':False})
    provider.write(report/'RESULT.json',{'status':'passed','nativeEntriesReplacedOnly':True,'immutableEntryCount':len(unchanged),'newNativeEntryCount':len(changed),'newNativeSource':newpin['nativeCompiledSource'],'oldContainerNotRestamped':True})
if __name__=='__main__':main()
