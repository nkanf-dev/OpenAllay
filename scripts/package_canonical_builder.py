#!/usr/bin/env python3
"""Replace only the raw Builder resource and its hash field; no source compilation."""
import argparse
import base64
import hashlib
from importlib.util import spec_from_file_location,module_from_spec
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import zipfile

from canonical_builder_provider import canonical_bytes, BUILDER_SHA, SOURCE
ROOT=Path(__file__).resolve().parents[1]
ORIGINALS='distribution/builder-package-originals.json'
ORIGINALS_SHA="665ad67c0f303e1559173d4e53419b23338c67f1277079244d9eeaad97cb076d"
RESOURCE='META-INF/openallay/bundled-extensions/openallay-builder-universal-0.4.0.jar'
PROVENANCE='META-INF/openallay/distribution.json'
OLD_BUILDER_SHA='198213578128d6739936bf5527c5d0d5fb99692eceaee571836e342cb2cc0ff5'
FIELDS={'kind','outcome','sourceSha','sourceRunId','sourceRunAttempt','version','family','artifactSha256',
        'engineManifestSha256','commands','originalGroup','originalReceiptBase64','originalJarSha256',
        'originalReceiptSha256','canonicalBuilderProvider','outsideReplacementEntriesSha256'}


def require(ok,message):
    if not ok:raise ValueError(message)


def sha(raw):return hashlib.sha256(raw).hexdigest()


def decode(raw):
    def pairs(items):
        result={}
        for key,value in items:
            require(key not in result, 'Duplicate package-only custody JSON member')
            result[key]=value
        return result
    return json.loads(raw,object_pairs_hook=pairs,parse_constant=lambda value:(_ for _ in ()).throw(ValueError(value)))


def originals(root=ROOT):
    raw=(root/ORIGINALS).read_bytes();require(sha(raw)==ORIGINALS_SHA,'Frozen original provider selection changed')
    value=decode(raw)
    require(set(value)=={'version','groups'} and value['version']=='0.4.4' and len(value['groups'])==20,'Exact original20 current-version groups required')
    return {row['target']:row for row in value['groups']}


def archive(raw):
    result={}
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        require(len(z.infolist())<=20000 and sum(row.file_size for row in z.infolist())<=512*1024*1024,'Product archive bounds exceeded')
        for row in z.infolist():
            name=row.filename;parts=PurePosixPath(name)
            require(not parts.is_absolute() and '..' not in parts.parts and '\\' not in name and ':' not in name and
                    ((row.external_attr>>16)&0o170000)!=0o120000,'Unsafe product archive member')
            require(name not in result,'Duplicate product archive entry')
            result[name]=z.read(row)
    return result


def provenance(raw):
    value=decode(raw)
    require(set(value)=={'source','project','version','extensionId','openAllayApiVersion','artifact'},'Exact original distribution provenance required')
    require(value['source']=={'repository':'https://github.com/nkanf-dev/OpenAllay-Extensions.git','revision':SOURCE,'dirty':False,'pinned':True} and
            value['project']=='extensions/minecraft-builder' and value['version']==value['openAllayApiVersion']=='0.4.0' and value['extensionId']=='openallay:builder',
            'Original pinned Builder source/SDK provenance differs')
    require(value['artifact']=={'path':RESOURCE,'sha256':OLD_BUILDER_SHA},'Exact original modern Builder identity required')
    return value


def replacement(original,builder):
    require(sha(builder)==BUILDER_SHA,'Canonical raw Builder differs')
    entries=archive(original)
    require(sha(entries[RESOURCE])==OLD_BUILDER_SHA,'Original product Builder bytes differ')
    previous=provenance(entries[PROVENANCE]);current={**previous,'artifact':{**previous['artifact'],'sha256':BUILDER_SHA}}
    updated=dict(entries);updated[RESOURCE]=builder;updated[PROVENANCE]=(json.dumps(current,indent=2,sort_keys=True)+'\n').encode()
    out=io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(original)) as source,zipfile.ZipFile(out,'w') as target:
        target.comment=source.comment
        for info in source.infolist():target.writestr(info,updated[info.filename])
    raw=out.getvalue();verify_replacement(original,raw,builder)
    return raw


def verify_replacement(original,current,builder):
    old,new=archive(original),archive(current)
    require(set(old)==set(new),'Package-only replacement cannot add or delete entries')
    require(sha(old[RESOURCE])==OLD_BUILDER_SHA and new[RESOURCE]==builder and sha(builder)==BUILDER_SHA,'Exact original/canonical Builder bytes required')
    previous=provenance(old[PROVENANCE]);expected={**previous,'artifact':{**previous['artifact'],'sha256':BUILDER_SHA}}
    require(new[PROVENANCE]==(json.dumps(expected,indent=2,sort_keys=True)+'\n').encode(),'Package-only provenance changed beyond the exact canonical Builder hash encoding')
    names=set(old)-{RESOURCE,PROVENANCE}
    require(all(old[name]==new[name] for name in names),'Package-only replacement changed an engine/native/class/legal/service/resource entry')
    ledger={name:sha(old[name]) for name in sorted(names)}
    return sha(json.dumps(ledger,sort_keys=True,separators=(',',':')).encode())


def command(target):
    artifact=originals(ROOT)[target]['artifactName']
    return [{'command':['python3','-B','scripts/package_canonical_builder.py','--target',target,'--original-directory','build/builder-package-original-cache/'+artifact,'--output','release-group'],
             'runtime':'package-only'}]


def authenticate_original(root,family,original_directory):
    group=originals(root)[family['buildTarget']]
    require(family['packagingRecipe']=='nested-mod','Only the33 nested products may replace Builder resources')
    receipt_path=original_directory/'build-receipts'/(family['id']+'.json');receipt_raw=receipt_path.read_bytes();receipt=decode(receipt_raw)
    row=next(row for row in group['familyArtifacts'] if row['id']==family['id'])
    require(sha(receipt_raw)==row['receiptSha256'],'Original compile receipt bytes differ from frozen approval')
    require(receipt['kind']=='compile-package' and receipt['outcome']=='passed' and receipt['family']==family and receipt['version']=='0.4.4' and
            receipt['sourceSha']==group['sourceSha'] and receipt['sourceRunId']==str(group['runId']) and receipt['sourceRunAttempt']==str(group['runAttempt']),
            'Original actual compile/source/run/family custody differs')
    filename=family['filenameTemplate'].replace('{version}','0.4.4');raw=(original_directory/filename).read_bytes()
    require(sha(raw)==row['artifactSha256']==receipt['artifactSha256'],'Original product bytes differ from frozen approval')
    engine=(original_directory/'build-receipts/engine-manifest.json').read_bytes()
    require(sha(engine)==group['engineManifestSha256']==receipt['engineManifestSha256'],'Original engine-manifest custody differs')
    return group,receipt_raw,raw,engine


def verify_receipt(root,family,path,receipt,original_directory,expected_source,builder=None):
    require(type(receipt) is dict and set(receipt)==FIELDS and receipt['kind']=='package-only' and receipt['outcome']=='passed','Exact truthful package-only receipt required')
    require(receipt['family']==family and receipt['version']=='0.4.4' and receipt['sourceSha']==expected_source and
            type(receipt['sourceRunId']) is str and type(receipt['sourceRunAttempt']) is str and
            re.fullmatch(r'[1-9][0-9]*',receipt['sourceRunId']) and re.fullmatch(r'[1-9][0-9]*',receipt['sourceRunAttempt']),
            'New actual package-only source/run/family differs')
    require(receipt['commands']==command(family['buildTarget']),'Package-only receipt cannot invent compile/game commands')
    group,oldreceipt,original,engine=authenticate_original(root,family,original_directory)
    require(receipt['originalGroup']==group and base64.b64decode(receipt['originalReceiptBase64'],validate=True)==oldreceipt and
            receipt['originalReceiptSha256']==sha(oldreceipt) and receipt['originalJarSha256']==sha(original) and receipt['engineManifestSha256']==sha(engine),
            'Package-only original compile chain changed')
    pin=decode((root/'distribution/builder-candidate-provider.json').read_text())
    require(receipt['canonicalBuilderProvider']==pin,'Canonical Builder provider/source differs')
    current=path.read_bytes();require(receipt['artifactSha256']==sha(current),'New package-only artifact bytes differ')
    if builder is None:builder=archive(current)[RESOURCE]
    require(receipt['outsideReplacementEntriesSha256']==verify_replacement(original,current,builder),'Outside-two-entry byte custody differs')
    return decode(oldreceipt)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--target',required=True);parser.add_argument('--original-directory',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    require(os.environ.get('GITHUB_ACTIONS')=='true','Package-only producer runs remotely only')
    source=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    require(source==os.environ['GITHUB_SHA'] and not subprocess.check_output(['git','status','--porcelain','--untracked-files=no'],cwd=ROOT,text=True).strip(),'Clean exact new packaging source required')
    run,attempt=os.environ['GITHUB_RUN_ID'],os.environ['GITHUB_RUN_ATTEMPT'];require(re.fullmatch(r'[1-9][0-9]*',run) and re.fullmatch(r'[1-9][0-9]*',attempt),'Actual packaging run required')
    builder,pin=canonical_bytes(ROOT)
    data=decode((ROOT/'gradle/minecraft-artifacts.json').read_text());families=[family for family in data['acceptedFamilies'] if family['buildTarget']==args.target]
    require(families and all(f['packagingRecipe']=='nested-mod' for f in families),'Exact nested current group required')
    require(not args.output.exists(),'Preserve previous derived stage');(args.output/'build-receipts').mkdir(parents=True)
    checksums=[]
    for family in families:
        group,oldreceipt,original,engine=authenticate_original(ROOT,family,args.original_directory)
        raw=replacement(original,builder);filename=family['filenameTemplate'].replace('{version}','0.4.4');path=args.output/filename;path.write_bytes(raw)
        receipt={'kind':'package-only','outcome':'passed','sourceSha':source,'sourceRunId':run,'sourceRunAttempt':attempt,'version':'0.4.4','family':family,
                 'artifactSha256':sha(raw),'engineManifestSha256':sha(engine),'commands':command(args.target),'originalGroup':group,
                 'originalReceiptBase64':base64.b64encode(oldreceipt).decode(),'originalJarSha256':sha(original),'originalReceiptSha256':sha(oldreceipt),
                 'canonicalBuilderProvider':pin,'outsideReplacementEntriesSha256':verify_replacement(original,raw,builder)}
        verify_receipt(ROOT,family,path,receipt,args.original_directory,source,builder)
        (args.output/'build-receipts'/(family['id']+'.json')).write_text(json.dumps(receipt,indent=2,sort_keys=True)+'\n')
        (args.output/'build-receipts/engine-manifest.json').write_bytes(engine)
        checksums.append(sha(raw)+'  '+filename+'\n')
    (args.output/'SHA256SUMS').write_text(''.join(checksums))
    print(json.dumps({'packageOnly':True,'families':len(families),'sourceSha':source,'originalSourceSha':group['sourceSha'],'nativeOrEngineCompiled':False}))


if __name__=='__main__':main()
