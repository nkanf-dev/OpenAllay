#!/usr/bin/env python3
"""Source-only migration target: one ordinary stock Forge14 Java8 mod JAR from verified components.

No compiler, installer, Java agent, runtime override, source rewriting or header edits.
Consumes actual current major52 artifacts; rejects the old Java17 closure.
"""
import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[1]
SPEC=importlib.util.spec_from_file_location('legacy_pack',ROOT/'scripts/package-legacy-forge-release.py')
legacy=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(legacy)
REQUIRED={'engine','sdk','rhino','commonmark','jtokkit','sqlite','mixin'}
OPTIONAL={'tables','jsr305','checkerqual','errorprone','j2objc','slf4j'}
FORBIDDEN=('java/','net/minecraft/','net/minecraftforge/','org/objectweb/asm/',
    'com/google/gson/','com/google/common/','org/apache/logging/log4j/',
    'dev/openallay/forge1122agent/','dev/openallay/forge36probe/')
BUILDER_PATH='META-INF/openallay/bundled-extensions/openallay-builder.jar'

def scan8(raw,label):
    entries=legacy.archive(raw)
    for name,data in entries.items():
        if name.endswith('.class'):
            legacy.require(len(data)>=10 and data[:4]==b'\xca\xfe\xba\xbe','Invalid physical class '+label+'!'+name)
            legacy.require(int.from_bytes(data[6:8],'big')<=52,'Actual physical Java8 closure exceeded '+label+'!'+name)
        elif name.endswith('.jar'):scan8(data,label+'!'+name)
    return entries

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--inputs',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--receipt',type=Path,required=True);a=p.parse_args()
    request=legacy.load(a.inputs)
    legacy.exact(request,('sourceRoot','sourceRevision','version','native','nativeReceipt','engineReceipt','components','builder','builderLock','engineSource'),'stockForge8 packet request')
    root=Path(request['sourceRoot']).resolve()
    source=subprocess.check_output(['git','-C',str(root),'rev-parse','HEAD'],text=True).strip()
    legacy.require(source==request['sourceRevision'],'Exact current release source required')
    legacy.require(re.search(r'(?m)^version='+re.escape(request['version'])+r'$',(root/'gradle.properties').read_text()) is not None,'Current release version differs')
    native_path=legacy.reference(request['native']);native_raw=native_path.read_bytes();native=scan8(native_raw,'native')
    companion_spec=importlib.util.spec_from_file_location('mixin_companion_audit',ROOT/'scripts/audit-configured-mixin-companions.py')
    companion_audit=importlib.util.module_from_spec(companion_spec);companion_spec.loader.exec_module(companion_audit)
    companion_audit.audit(native_path,a.receipt.with_name('configured-mixin-companions.json'))
    native_receipt=legacy.load(legacy.reference(request['nativeReceipt']))
    legacy.require(native_receipt['nativeRelease']==8 and native_receipt['jarSha256']==legacy.sha(native_raw),
        'Actual normal FG native --release8/reobf receipt required')
    engine_receipt=legacy.load(legacy.reference(request['engineReceipt']))
    legacy.require(engine_receipt['compilerExitCode']==0 and engine_receipt['release']==8 and
        engine_receipt['runtimeClosureJava8Accepted'] is True,'Complete normal engine Java8 compile/closure receipt required')
    legacy.require(re.fullmatch(r'[0-9a-f]{40}',request['engineSource']) is not None,'Exact engine producer source required')
    legacy.require(request['engineSource']==source,'Final current engine source-bound producer required; do not restamp an old JAR')
    roles={r['role']:r for r in request['components']}
    legacy.require(REQUIRED.issubset(roles) and set(roles).issubset(REQUIRED|OPTIONAL) and len(roles)==len(request['components']),'Exact current graph classified Java8 component roles required')
    legacy.require(roles['engine']['sha256']==engine_receipt['engineSha256'],'Complete current engine output identity differs')
    audit_spec=importlib.util.spec_from_file_location('runtime_preflight',ROOT/'scripts/audit-stock8-runtime-closure.py')
    audit=importlib.util.module_from_spec(audit_spec);audit_spec.loader.exec_module(audit)
    audit.audit(request,a.receipt.with_name('whole-runtime-physical-preflight.json'))
    contents={};owners={};services={};licenses={};inputs=[];role_projections=[]
    def merge(entries,role):
        for name,data in entries.items():
            if name.startswith(FORBIDDEN):raise ValueError('Stock game/host/ASM alias forbidden: '+role+'!'+name)
            if name.upper()=='META-INF/MANIFEST.MF' or re.search(r'(?i)^META-INF/[^/]+\.(SF|RSA|DSA)$',name):continue
            if name.startswith('META-INF/services/'):
                services.setdefault(name,set()).update(line.split('#',1)[0].strip() for line in data.decode().splitlines() if line.split('#',1)[0].strip());continue
            if 'LICENSE' in name.upper() or 'NOTICE' in name.upper():
                name='META-INF/licenses/'+role+'/'+name
            if name in contents:
                legacy.require(contents[name]==data,'Conflicting physical source owner '+name)
                if name.endswith('.class'):raise ValueError('Duplicate physical class owner '+name)
            contents[name]=data;owners[name]=role
    merge(native,'native')
    for role,r in roles.items():
        legacy.exact(r,('role','coordinate','path','sha256'),'component')
        path=legacy.reference({'path':r['path'],'sha256':r['sha256']});raw=path.read_bytes()
        if role=='sqlite':
            projection_spec=importlib.util.spec_from_file_location('sqlite_runtime_role',ROOT/'scripts/sqlite-runtime-role.py')
            projection=importlib.util.module_from_spec(projection_spec);projection_spec.loader.exec_module(projection)
            projected,proof=projection.project_sqlite_runtime(path,r['coordinate'])
            projection.audit_projected_entries(projected,proof)
            merge(projected,role);role_projections.append(proof)
        elif role=='mixin':
            projection_spec=importlib.util.spec_from_file_location('mixin_runtime_role',ROOT/'scripts/mixin-launchwrapper-role.py')
            projection=importlib.util.module_from_spec(projection_spec);projection_spec.loader.exec_module(projection)
            projected,proof=projection.project_mixin_launchwrapper(path,r['coordinate'])
            merge(projected,role);role_projections.append(proof)
        else:merge(scan8(raw,role),role)
        inputs.append({'role':role,'coordinate':r['coordinate'],'sha256':r['sha256']})
    builder=legacy.reference(request['builder']).read_bytes();scan8(builder,'builder')
    builder_lock=legacy.load(legacy.reference(request['builderLock']))
    sys.path.insert(0,str(ROOT/'scripts'));spec=importlib.util.spec_from_file_location('bundle_verifier',ROOT/'scripts/verify-bundled-extensions.py');verifier=importlib.util.module_from_spec(spec);spec.loader.exec_module(verifier)
    verifier.verify_universal(builder,builder_lock,additional_target_pairs={('forge','1.12.2'),('forge','1.16.5')})
    provenance={'source':{**builder_lock['source'],'dirty':False,'pinned':True},'project':builder_lock['project'],'version':builder_lock['version'],'extensionId':builder_lock['extensionId'],'openAllayApiVersion':builder_lock['openAllayApiVersion'],'artifact':{'path':BUILDER_PATH,'sha256':legacy.sha(builder)}}
    legacy.require(BUILDER_PATH not in contents,'Bundled Builder already has a competing owner')
    contents['META-INF/openallay/distribution.json']=legacy.encoded(provenance);owners['META-INF/openallay/distribution.json']='builder-provenance'
    contents[BUILDER_PATH]=builder;owners[BUILDER_PATH]='builder'
    legacy.require(all(s in contents for s in ['mcmod.info','pack.mcmeta','openallay.refmap.json',
        'openallay.client.mixins.json','openallay.world.mixins.json','openallay.forge.mixins.json']),
        'Exact Forge14 native descriptors/refmap/bindings required')
    # Forge14's real mcmod.info is a selected source template. Materialize only its declared JSON fields.
    properties={}
    for line in (root/'gradle.properties').read_text().splitlines():
        if line and not line.lstrip().startswith('#') and '=' in line:
            key,value=line.split('=',1);properties[key]=value
    original_mcmod=contents['mcmod.info']
    templates=json.loads(original_mcmod)
    declared={'modid':'mod_id','name':'mod_name','description':'description','version':'version','credits':'credits'}
    legacy.require(isinstance(templates,list) and len(templates)==1,'One genuine Forge14 mcmod.info template required')
    model=dict(templates[0])
    for field,key in declared.items():
        legacy.require(key in properties,'Missing current resource property '+key)
        legacy.require(model[field]=='${'+key+'}' or model[field]==properties[key],'Unknown Forge14 metadata template field '+field)
        model[field]=properties[key]
    legacy.require(model['mcversion']=='1.12.2','Selected Forge14 native Minecraft metadata differs')
    legacy.require(model['authorList']==['${mod_author}'] or model['authorList']==[properties['mod_author']],'Unknown native author template')
    model['authorList']=[properties['mod_author']]
    generated_mcmod=(json.dumps([model],ensure_ascii=False,indent=2)+'\n').encode()
    legacy.require('${' not in generated_mcmod.decode(),'Unresolved native metadata expansion')
    contents['mcmod.info']=generated_mcmod
    metadata_expansion={'resource':'mcmod.info','inputSha256':legacy.sha(original_mcmod),'outputSha256':legacy.sha(generated_mcmod),
        'propertiesFileSha256':legacy.sha((root/'gradle.properties').read_bytes()),'expandedPropertyKeys':sorted(set(declared.values())|{'mod_author'}),
        'minecraftVersion':'1.12.2','originalInputJarUnchanged':True,'jsonEscaping':True}
    descriptor=json.loads(contents['mcmod.info']);legacy.require(len(descriptor)==1 and descriptor[0]['modid']=='openallay' and descriptor[0]['version']==request['version'],
        'Real Forge14 release metadata required, not a filename rename')
    for name in ['openallay.client.mixins.json','openallay.world.mixins.json','openallay.forge.mixins.json']:
        legacy.require(json.loads(contents[name])['compatibilityLevel']=='JAVA_8','Native Mixin compatibility must be Java8')
    for name,lines in services.items():
        for provider in lines:legacy.require(provider.replace('.','/')+'.class' in contents,'Service class absent '+provider)
        contents[name]=('\n'.join(sorted(lines))+'\n').encode()
    legacy.require('org/spongepowered/asm/launch/MixinTweaker.class' in contents,'Genuine normal MixinTweaker payload required')
    legacy.require('dev/openallay/neoforge/OpenAllayForge1122LoadingPlugin.class' not in contents,'Unreleased duplicate coreplugin registration owner refused')
    contents['META-INF/MANIFEST.MF']=(
        'Manifest-Version: 1.0\r\nTweakClass: org.spongepowered.asm.launch.MixinTweaker\r\n'
        'TweakOrder: 0\r\nForceLoadAsMod: true\r\n'
        'MixinConfigs: openallay.client.mixins.json,openallay.world.mixins.json,openallay.forge.mixins.json\r\n\r\n').encode()
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as jar:
        for name,data in sorted(contents.items()):
            item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.external_attr=0o100644<<16;item.compress_type=zipfile.ZIP_DEFLATED;jar.writestr(item,data)
    raw=stream.getvalue();scan8(raw,'final')
    legacy.require(not a.output.exists() and not a.receipt.exists() and a.output.parent.is_dir() and a.receipt.parent.is_dir(),'Fresh final paths required')
    for path in [native_path,legacy.reference(request['engineReceipt']),legacy.reference(request['nativeReceipt']),a.inputs]:
        legacy.require(a.output.resolve()!=path.resolve() and a.receipt.resolve()!=path.resolve(),'Output overwrites input')
    legacy.write_new(a.output,raw)
    result={'target':'forge1122','version':request['version'],'sourceRevision':source,'outputSha256':legacy.sha(raw),
        'entries':legacy.inventory(raw),'owners':owners,'inputs':inputs,'engineReceiptSha256':request['engineReceipt']['sha256'],
        'nativeReceiptSha256':request['nativeReceipt']['sha256'],'builderSha256':legacy.sha(builder),
        'helperBuild':None,'javaRequired':8,'ordinaryModsJar':True,'gameExecuted':False,'runtimeAccepted':False,'bootstrapOwner':'manifest MixinTweaker only','MixinConfigsOwner':'manifest','hostNamespacesReplaced':False,'allPhysicalClassesAtMost52':True,'functionalMrPolicy':'exact fixed SQLite JVM-runtime role projection; other physical entries preserved and Java8 scanned','runtimeRoleProjections':role_projections,'nativeMetadataExpansion':metadata_expansion,'engineProducerSource':request['engineSource'],'builderSource':builder_lock['source']['revision']}
    legacy.write_new(a.receipt,legacy.encoded(result))

if __name__=='__main__':main()
