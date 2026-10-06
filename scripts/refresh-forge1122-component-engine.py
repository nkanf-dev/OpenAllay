#!/usr/bin/env python3
"""Replace only engine-owned entries under accepted physical ownership custody."""
import importlib.util,json,os,hashlib,zipfile,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path);r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r);return r

def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote only')
    provider=module('native_provider',ROOT/'scripts/build-forge1122-native.py')
    newpin=json.loads((ROOT/'native-builds/forge1122-component/engine-refresh-provider.json').read_text())
    if set(newpin)!={'provider','engineJarSha256'}:raise ValueError('Exact new engine provider/JAR pin')
    report=ROOT/'build/forge1122-component-report';report.mkdir(parents=True,exist_ok=False)
    work=ROOT/'build/forge1122-component-inputs';work.mkdir(exist_ok=False)
    productpin=json.loads((ROOT/'native-builds/forge1122-component/accepted-product-provider.json').read_text())
    kept=provider.retained(productpin,work/'product',report,'product')
    manifest=next(kept.rglob('component-inputs.json'));product=manifest.parent;component=json.loads(manifest.read_text())
    for r in component['artifacts']:
        if provider.sha(product/Path(r['path']).name)!=r['sha256']:raise ValueError('Accepted component byte custody differs')
    custody=json.loads(next(kept.rglob('custody.json')).read_text())
    # Original whole-entry ownership provider persists independently through native/core bootstrap refreshes.
    ownershippin=json.loads((ROOT/'native-builds/forge1122-component/engine-entry-ownership-provider.json').read_text())
    ownership_root=provider.retained(ownershippin,work/'ownership',report,'ownership')
    ownership=json.loads(next(ownership_root.rglob('entry-ownership.json')).read_text())
    before_root=provider.retained(custody['providers']['engine'],work/'previous-engine',report,'previous-engine')
    after_root=provider.retained(newpin['provider'],work/'new-engine',report,'new-engine')
    def closure_engine(root,expected):
        manifest=next(root.rglob('closure-input.json'));spec=json.loads(manifest.read_text());row=next(r for r in spec['artifacts'] if r['role']=='engine');jar=manifest.parent/'engine.jar'
        if row['sha256']!=expected or provider.sha(jar)!=expected:raise ValueError('Exact effective engine closure differs')
        return jar
    oldengine=closure_engine(before_root,custody['providers']['engineJarSha256']);newengine=closure_engine(after_root,newpin['engineJarSha256'])
    def entries(path):
        with zipfile.ZipFile(path) as z:return {e.filename:z.read(e) for e in z.infolist() if not e.is_dir()}
    oldentries=entries(oldengine);newentries=entries(newengine)
    rows={r['input']:r for r in ownership['inventory'] if r['owner']=='engine'}
    if set(rows)!=set(oldentries):raise ValueError('Original engine ownership inventory does not cover exact previous engine')
    core=product/'openallay-feature-core.jar';out=ROOT/'build/forge1122-component-product';out.mkdir()
    remove=set();services={};shared={};changes=[]
    with zipfile.ZipFile(core) as z:
        original={e.filename:(e,z.read(e)) for e in z.infolist()}
        for name,row in rows.items():
            digest=hashlib.sha256(oldentries[name]).hexdigest()
            if row['sha256']!=digest:raise ValueError('Previous engine inventory/source differs: '+name)
            if row['disposition']=='copied':
                output=row['output']
                if output not in original or hashlib.sha256(original[output][1]).hexdigest()!=digest:raise ValueError('Current engine physical entry differs: '+output)
                remove.add(output)
            elif row['disposition']=='identical-byte-duplicate':shared[name]=row
            elif row['disposition']=='merged-real-service-declarations':services[name]=row
            elif row['disposition']!='excluded-original-container-metadata':raise ValueError('Unknown accepted engine disposition')
        for name,row in shared.items():
            if name not in newentries or hashlib.sha256(newentries[name]).hexdigest()!=row['sha256']:raise ValueError('Changed engine shared owner collision: '+name)
        for name,row in services.items():
            output=row['output'];others=[]
            for contributor in ownership['inventory']:
                if contributor['output']==output and contributor['owner']!='engine' and contributor['disposition']=='merged-real-service-declarations':
                    # Reconstruct each real contributor from provider-bound immutable base closure.
                    base=work/'base'
                    if not base.exists():provider.retained(custody['providers']['base'],base,report,'base')
                    rootmanifest=next(base.rglob('closure-input.json'));jar=rootmanifest.parent/(contributor['owner']+'.jar')
                    with zipfile.ZipFile(jar) as archive:data=archive.read(contributor['input'])
                    if hashlib.sha256(data).hexdigest()!=contributor['sha256']:raise ValueError('Real service contributor changed')
                    others.extend(data.decode().splitlines())
            current=original[output][1].decode().splitlines();oldlines=oldentries[name].decode().splitlines()
            expected=list(dict.fromkeys(l for l in others+oldlines if l.strip() and not l.startswith('#')))
            if set(current)!=set(expected):raise ValueError('Current merged service custody differs')
            newlines=newentries.get(name,b'').decode().splitlines();data=('\n'.join(dict.fromkeys(l for l in others+newlines if l.strip() and not l.startswith('#')))+'\n').encode();original[output]=(original[output][0],data)
    added={}
    for name,data in newentries.items():
        if name in shared or name in services:continue
        if name in rows:
            row=rows[name]
            if row['disposition']=='excluded-original-container-metadata':continue
            output=row['output']
        else:
            if name=='META-INF/MANIFEST.MF' or name=='module-info.class' or name.startswith('META-INF/versions/') and name.endswith('/module-info.class') or name in ('mcmod.info','META-INF/mods.toml') or name.endswith(('.SF','.RSA','.DSA')):continue
            output='META-INF/licenses/engine/'+name.replace('/','_') if not name.endswith('.class') and ('LICENSE' in name.upper() or 'NOTICE' in name.upper()) else name
        if output in original and output not in remove:raise ValueError('New engine output collides with immutable other owner: '+output)
        if output in added:raise ValueError('New engine duplicate physical output')
        added[output]=data;changes.append({'input':name,'output':output,'sha256':hashlib.sha256(data).hexdigest()})
    # Preserve existing effective MR manifest; refuse an MR shape requiring unsupported container regeneration.
    oldmr=any(n.startswith('META-INF/versions/') for n in original);newmr=any(n.startswith('META-INF/versions/') for n in added)
    if newmr and not oldmr:raise ValueError('New functional MR entries require explicit accepted manifest update')
    unchanged={}
    with zipfile.ZipFile(out/core.name,'w') as z:
        for name,(info,data) in original.items():
            if name in remove:continue
            z.writestr(info,data)
            if name not in {r['output'] for r in services.values()}:unchanged[name]=hashlib.sha256(data).hexdigest()
        for name,data in added.items():z.writestr(name,data)
    with zipfile.ZipFile(out/core.name) as z:
        for name,digest in unchanged.items():
            if hashlib.sha256(z.read(name)).hexdigest()!=digest:raise ValueError('Nonengine physical entry changed')
    for name in ('openallay-lifecycle-facade.jar','openallay-private-mixin.jar','builder-retained.jar','builder-provenance.json'):
        if (product/name).exists():shutil.copyfile(product/name,out/name)
    custody['providers']['engine']=newpin['provider'];custody['providers']['engineJarSha256']=newpin['engineJarSha256']
    provider.write(report/'custody.json',custody)
    records=[{'path':str(out/n),'sha256':provider.sha(out/n)} for n in ('openallay-feature-core.jar','openallay-lifecycle-facade.jar','openallay-private-mixin.jar')]
    provider.write(out/'component-inputs.json',{**component,'artifacts':records,'engineEntriesReplacedOnly':True,'engineProvider':newpin['provider'],'engineJarSha256':newpin['engineJarSha256'],'nativeProviderUnchanged':True})
    provider.write(report/'engine-entry-custody.json',{'oldEngineOwnershipProvider':ownershippin,'newEngine':newpin,'newEntries':changes,'unchangedEntries':unchanged,'nativePrivateMixinFacadeBootUnchanged':True,'engineCompileReplayed':False})
    provider.write(report/'RESULT.json',{'status':'passed','engineEntriesReplacedOnly':True,'immutableNonengineEntryCount':len(unchanged),'newEngineSource':newpin['provider']['sourceRevision'],'nativeRebuilt':False,'privateMixinFacadeCopiedUnchanged':True})
if __name__=='__main__':main()
