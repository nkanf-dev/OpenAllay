#!/usr/bin/env python3
"""PACK ONLY: derive honest scan views from one successful saved Forge16 build.
No javac/Gradle/AP/reobf/game. Runtime jars/effective closure/Builder source are explicit supplied inputs.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import zipfile

P=Path(__file__).with_name('forge16165-product-pack.py')
spec=importlib.util.spec_from_file_location('product_pack',P)
pack=importlib.util.module_from_spec(spec);spec.loader.exec_module(pack)
C=pack.closure
PREFIX='native-builds/forge16165/build/'
NATIVE_SHA='5655e7891dcd106b255e4986659045a8b10344094b254668d93555bc1913edcb'
SOURCE='966192594efc73ea389682b5690765db53011f39'

def reference(path):return {'path':str(Path(path).resolve()),'sha256':C.file_sha(path)}
def write(path,data):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    C.write_new(path,data);return reference(path)
def json_write(path,value):return write(path,C.encoded(value))

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for arg in ('inputs','work','output','receipt'):parser.add_argument('--'+arg,required=True)
    args=parser.parse_args();inputs=C.json_load(args.inputs)
    C.exact(inputs,('sourceRoot','nativeArtifact','rootReceipt','closureSpec','closurePolicy','retainedResolution',
        'runtimeOwnership','builderSourceProof','originalClosureSpec'),'postprocess inputs')
    source=Path(inputs['sourceRoot']).resolve();work=Path(args.work).absolute()
    protected=[source,P,Path(__file__),args.inputs]
    for key in inputs:
        if key!='sourceRoot':protected.append(pack.ref(inputs[key],key))
    pack.protect_outputs([work],protected)
    C.require(not work.exists() and work.parent.is_dir(),'New isolated postprocess directory required')
    C.require(inputs['nativeArtifact']['sha256']==NATIVE_SHA,'Exact successful native artifact')
    head=subprocess.run(['git','-C',str(source),'rev-parse','HEAD'],check=True,capture_output=True).stdout.decode().strip()
    C.require(head==SOURCE,'Use exact successful native source checkout')
    # All archived present receipts and native pair hashes are checked before derived files are written.
    with zipfile.ZipFile(inputs['nativeArtifact']['path']) as z:
        C.require(len(z.namelist())==len(set(z.namelist())),'Unique saved artifact members')
        saved={name:z.read(PREFIX+name) for name in ('namespace/source-selection.json','namespace/receipt.json',
            'native-metadata/build.json','native-ap/mixins.tsrg','native-ap/openallay.refmap.json',
            'native-tooling/compiler-input-lock.proposed.json','native-metadata/native-before-reobf.jar',
            'libs/openallay-forge-1.16.5-native-0.4.3.jar')}
        result=json.loads(z.read('build/forge16165-native-report/RESULT.json'),object_pairs_hook=C.pairs)
    metadata=json.loads(saved['native-metadata/build.json'],object_pairs_hook=C.pairs)
    selection=json.loads(saved['namespace/source-selection.json'],object_pairs_hook=C.pairs)
    namespace=json.loads(saved['namespace/receipt.json'],object_pairs_hook=C.pairs)
    C.require(result['source']==metadata['sourceRevision']==selection['sourceRevision']==SOURCE
        and result['nativeCompiled'] is True and result['normalApReobf'] is True and result['gameExecuted'] is False,
        'Successful original normal native build')
    bindings={'selectionSha256':'namespace/source-selection.json','namespaceSha256':'namespace/receipt.json',
        'compilerLockSha256':'native-tooling/compiler-input-lock.proposed.json','apMappingsSha256':'native-ap/mixins.tsrg',
        'apRefmapSha256':'native-ap/openallay.refmap.json'}
    for field,name in bindings.items():C.require(C.sha(saved[name])==metadata[field],'Saved receipt identity: '+field)
    C.require(C.sha(saved['native-metadata/native-before-reobf.jar'])==metadata['before']['sha256']
        and C.sha(saved['libs/openallay-forge-1.16.5-native-0.4.3.jar'])==metadata['reobf']['sha256'],'Saved pair identity')
    effective=C.json_load(pack.ref(inputs['closureSpec'],'effective closure'))
    original_effective=C.json_load(pack.ref(inputs['retainedResolution'],'effective original producer'))
    C.require(effective['sourceRevision']==original_effective['actualEngineSource'],'Effective engine source proof')
    # Saved native closure differs only in relocated paths, never component identity.
    C.require(inputs['originalClosureSpec']['sha256']==metadata['closureSpecSha256'],
        'Exact original successful native compile closure bytes')
    original_spec=C.json_load(inputs['originalClosureSpec']['path'])
    identity=lambda value:(value['sourceRevision'],sorted((a['role'],a['coordinate'],a['sha256']) for a in value['artifacts']))
    C.require(identity(original_spec)==identity(effective),'Relocated closure preserves original eighteen identities')
    work.mkdir()
    refs={name:write(work/'saved'/name,data) for name,data in saved.items()}
    artifact=lambda role,name:{'role':role,'coordinate':pack.NATIVE_COORDINATE,**refs[name]}
    before=artifact('native-input','native-metadata/native-before-reobf.jar')
    after=artifact('native-reobf','libs/openallay-forge-1.16.5-native-0.4.3.jar')
    raw=C.archive(before);normal=C.archive(after)
    C.require(set(raw)==set(normal),'Saved normal FG equal entry sets')
    generated=work/'DERIVED-canonical-token-view';classroot=work/'DERIVED-raw-native-class-view'
    unitmap={unit['path']:unit for unit in namespace['units']}
    origins={unit['path']:unit for unit in selection['java']}
    C.require(set(unitmap)==set(origins),'Saved canonical namespace unit union')
    for path,origin in origins.items():
        canonical=pack.source_file(source,origin['origin']).read_bytes();unit=unitmap[path]
        C.require(C.sha(canonical)==origin['sha256']==unit['inputHash'],'Canonical input bytes')
        derived=pack.reproduce_namespace(canonical.decode('utf-8'),unit['edits'])
        C.require(C.sha(derived)==unit['outputHash'],'Recorded generated token identity')
        write(generated/path,derived)
    classes=[]
    for name,data in sorted(raw.items()):
        if not name.endswith('.class'):continue
        C.require(C.class_info(data,name)==61,'Raw native major61')
        path=name.rsplit('/',1)[0]+'/'+pack.class_source(data,name)
        C.require(path in origins and (name==path[:-5]+'.class' or name.startswith(path[:-5]+'$')),
            'Raw native class selected source ownership')
        origin=origins[path];write(classroot/name,data)
        classes.append({'entry':name,'owner':origin['owner'],'sourcePath':path,
            'sourceSha256':origin['sha256'],'javacSha256':C.sha(data)})
    resources=[{'entry':r['path'],'owner':r['owner'],'sourcePath':r['path'],'sourceSha256':r['sha256'],
        'processedSha256':C.sha(raw[r['path']])} for r in selection['resources']]
    lockpath=source/'native-builds/forge16165/extensions.lock.json';lock=C.json_load(lockpath)
    builder=next(a for a in effective['artifacts'] if a['role']=='builder')
    provenance={'source':{**lock['source'],'dirty':False,'pinned':True},
        **{key:lock[key] for key in ('project','version','extensionId','openAllayApiVersion')},
        'artifact':{'path':pack.BUNDLED+Path(lock['artifact']).name,'sha256':builder['sha256']}}
    provenance_ref=json_write(work/'distribution.json',provenance)
    transport={'kind':'archived-normal-FG-build-derived-views','artifact':inputs['nativeArtifact'],
        'run':37460049017,'artifactId':11412670735,'sourceRevision':SOURCE,'rootReceipt':inputs['rootReceipt'],
        'originalClosureSpecSha256':metadata['closureSpecSha256'],
        'derivedGeneratedRoot':str(generated),'derivedClassRoot':str(classroot),'originalClosureSpec':inputs['originalClosureSpec']}
    transport_ref=json_write(work/'archived-transport.json',transport)
    # Absent original byte inputs are honest hash-only references anchored by successful saved receipts.
    hash_only=lambda label,digest:{'path':str(work/('NOT-ARCHIVED-'+label)),'sha256':digest}
    proof={'sourceRevision':SOURCE,'selection':refs['namespace/source-selection.json'],
        'namespace':refs['namespace/receipt.json'],'compilerLock':refs['native-tooling/compiler-input-lock.proposed.json'],
        'normalMappings':hash_only('normalMappings',metadata['normalMappingsSha256']),
        'apMappings':refs['native-ap/mixins.tsrg'],'generatedSourceRoot':str(generated),'javacOutputRoot':str(classroot),
        'classes':classes,'resources':resources,'runtimeOwnership':inputs['runtimeOwnership'],
        'namespaceAcceptance':hash_only('namespaceAcceptance',namespace['metadataAcceptanceSha256']),
        'archivedTransport':transport_ref}
    proof_ref=json_write(work/'native-proof.json',proof)
    closure_report,_=C.scan(inputs['closureSpec']['path'],inputs['closurePolicy']['path'])
    C.require(closure_report['status']=='READY','Effective original engine/component closure READY')
    closure_gate=json_write(work/'closure-ready.json',closure_report)
    request={'sourceRoot':str(source),'nativeSourceRevision':SOURCE,'closureSpec':inputs['closureSpec'],
        'closurePolicy':inputs['closurePolicy'],'closureGate':closure_gate,'nativeInput':before,'nativeReobf':after,
        'nativeMetadata':refs['native-metadata/build.json'],'nativeProof':proof_ref,
        'distributionLock':reference(lockpath),'distributionProvenance':provenance_ref,
        'builderSourceProof':inputs['builderSourceProof'],'retainedResolution':inputs['retainedResolution']}
    request_path=work/'product-request.json';json_write(request_path,request)
    report,_,protected=pack.product_scan(request_path)
    gatepath=work/'product-ready.json';pack.protect_outputs([gatepath],protected)
    json_write(gatepath,report)
    packed=pack.pack(request_path,gatepath,C.file_sha(gatepath),args.output,args.receipt)
    print('PACKED original native compile reused '+packed['outputSha256'])

if __name__=='__main__':main()
