#!/usr/bin/env python3
"""Strict source-owned Forge16 product packing; no compilation or game execution.

Reuses the unchanged prerequisite closure/archive scanner. Builder stays one raw
existing discovery resource. Native input is normal FG reobf output, not a probe.
Python3.11+, stdlib only. Exact current shapes; no internal format version.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import secrets
import stat
import struct
import subprocess
import sys
import tomllib
import zipfile

SCANNER = Path(__file__).with_name('forge16165-engine-prerequisite') / 'pack.py'
SCANNER_SHA256 = '2fd2f895c48bd86568ee72e9d23d90d833fa143888ba0c2f9fddd3ca1329122c'
if hashlib.sha256(SCANNER.read_bytes()).hexdigest()!=SCANNER_SHA256:
    raise RuntimeError('Unchanged accepted closure scanner required')
_spec = importlib.util.spec_from_file_location('forge16_closure_scanner', SCANNER)
closure = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(closure)
require = closure.require
exact = closure.exact
sha = closure.sha
file_sha = closure.file_sha
json_load = closure.json_load
encoded = closure.encoded
GateError = closure.GateError

NATIVE_COORDINATE = 'dev.openallay:openallay-forge-1.16.5-native:0.4.3'
PROVENANCE = 'META-INF/openallay/distribution.json'
BUNDLED = 'META-INF/openallay/bundled-extensions/'
IMMUTABLE_COMPONENT_LOCK_SHA256 = '42d6e579b3e6cf71411f99fe899669c94a3123ff76fcc9b71537988561b8ccb0'
ROLES = {'engine','sdk','rhino','builder','json-proof','maven-proof','gson','guava',
    'failureaccess','listenablefuture','commonmark','tables','jtokkit','sqlite',
    'jsr305','checkerqual','errorprone','j2objc'}
OWNER_ROOTS = {'common':'common/', 'adapter':'adapters/minecraft/',
    'fml':('neoforge/','forge/')}
NATIVE_REQUIRED = {'META-INF/mods.toml','pack.mcmeta','openallay.refmap.json',
    'openallay.client.mixins.json','META-INF/services/dev.openallay.platform.PlatformService',
    'dev/openallay/adapter/minecraft/v26_2/world/material-palette-inputs.json'}
FORBIDDEN_NATIVE = ('dev/openallay/api/extension/','dev/openallay/builder/',
    'dev/openallay/json/','dev/openallay/internal/maven/','dev/latvian/mods/rhino/',
    'dev/openallay/forge36probe/')
def manifest_line(key,value):
    raw=(key+': '+value).encode('utf-8')
    chunks=[]
    while len(raw)>72:
        chunks.append(raw[:72]);raw=b' '+raw[72:]
    chunks.append(raw)
    return b'\r\n'.join(chunks)+b'\r\n'

HOST_CLASSES = {'com/google/gson/Gson.class':'com.google.code.gson:gson:2.8.0',
    'com/google/common/collect/ImmutableList.class':'com.google.guava:guava:21.0',
    'org/apache/logging/log4j/Logger.class':None,
    'org/spongepowered/asm/mixin/Mixin.class':None,
    'net/minecraftforge/fml/common/Mod.class':None,
    'net/minecraft/client/Minecraft.class':None}

def ref(record, label):
    exact(record, ('path','sha256'), label)
    path = Path(record['path'])
    require(path.is_absolute() and path.is_file() and not path.is_symlink(), label + ' ordinary absolute file')
    require(isinstance(record['sha256'],str) and re.fullmatch(r'[0-9a-f]{64}',record['sha256']), label + ' hash')
    require(file_sha(path)==record['sha256'], label + ' bytes differ')
    return path

def protect_outputs(paths, protected):
    resolved = [Path(path).resolve() for path in paths]
    require(len(resolved)==len(set(resolved)), 'Competing output paths')
    for output in resolved:
        for input_path in protected:
            input_path = Path(input_path).resolve()
            require(output != input_path and not output.is_relative_to(input_path)
                and not input_path.is_relative_to(output), 'Output/input overlap: ' + str(output))

def ordinary_output(path):
    path=Path(path).absolute()
    require(path.parent.is_dir() and not path.exists() and not path.is_symlink(),
        'New output in existing directory required: '+str(path))
    return path.parent.resolve()/path.name

def remove_created(record):
    path,device,inode=record
    try:current=path.lstat()
    except FileNotFoundError:return
    require(stat.S_ISREG(current.st_mode) and (current.st_dev,current.st_ino)==(device,inode),
        'Created output custody changed; refusing deletion: '+str(path))
    path.unlink()

def stage_path(final, protected, finals):
    # Validate the exact random staging path before opening/creating any bytes.
    path=final.parent/('.'+final.name+'.stage-'+secrets.token_hex(16))
    protect_outputs([path],protected+finals)
    flags=os.O_WRONLY|os.O_CREAT|os.O_EXCL|getattr(os,'O_NOFOLLOW',0)
    fd=os.open(path,flags,0o600)
    info=os.fstat(fd)
    return path,fd,(path,info.st_dev,info.st_ino)

def stage_bytes(final, data, protected, finals):
    path,fd,record=stage_path(final,protected,finals)
    try:
        with os.fdopen(fd,'wb') as stream:
            require(stream.write(data)==len(data),'Complete staged receipt/report write required')
            stream.flush();os.fsync(stream.fileno())
    except BaseException:
        remove_created(record)
        raise
    return path,record

def publish_staged(pairs):
    # Each link is no-overwrite; two-file visibility is deliberately not atomic.
    created=[]
    try:
        for staged,final,stage_record in pairs:
            current=staged.lstat()
            require(stat.S_ISREG(current.st_mode) and
                (current.st_dev,current.st_ino)==stage_record[1:], 'Staged file custody changed')
            os.link(staged,final)
            created.append((final,current.st_dev,current.st_ino))
    except BaseException as failure:
        cleanup_errors=[]
        for record in reversed(created):
            try:remove_created(record)
            except BaseException as error:cleanup_errors.append(str(error))
        if cleanup_errors:failure.add_note('Rollback preserved changed-custody paths: '+'; '.join(cleanup_errors))
        raise

def source_file(root, origin):
    require(isinstance(origin,str), 'Source origin string')
    closure.safe_name(origin)
    path = root / origin
    require(path.is_file() and not path.is_symlink() and path.resolve().is_relative_to(root), 'Source origin escape/missing')
    return path

def index_records(records, fields, label, key):
    require(isinstance(records,list), label + ' list')
    result = {}
    for record in records:
        exact(record, fields, label)
        require(record[key] not in result, 'Duplicate ' + label + ' ' + record[key])
        result[record[key]] = record
    return result

def class_source(data, name):
    """Read only the SourceFile provenance attribute after scanner validates class identity."""
    closure.class_info(data,name)
    count = struct.unpack_from('>H',data,8)[0]; pool={}; cursor=10; index=1
    def u2():
        nonlocal cursor
        result=struct.unpack_from('>H',data,cursor)[0]; cursor+=2; return result
    def u4():
        nonlocal cursor
        result=struct.unpack_from('>I',data,cursor)[0]; cursor+=4; return result
    try:
        while index<count:
            tag=data[cursor];cursor+=1
            if tag==1:
                size=u2();pool[index]=data[cursor:cursor+size];cursor+=size
            elif tag in (7,8,16,19,20):cursor+=2
            elif tag in (3,4,9,10,11,12,17,18):cursor+=4
            elif tag in (5,6):cursor+=8;index+=1
            elif tag==15:cursor+=3
            else:raise GateError('Unknown class constant')
            require(cursor<=len(data),'Truncated class metadata');index+=1
        cursor+=6
        interfaces=u2();cursor+=2*interfaces
        def attributes():
            nonlocal cursor
            result=[]
            for _ in range(u2()):
                identity=u2();size=u4();payload=data[cursor:cursor+size];cursor+=size
                require(cursor<=len(data),'Truncated class attribute')
                result.append((pool[identity],payload))
            return result
        for _ in range(2):
            for _ in range(u2()):cursor+=6;attributes()
        sources=[value for identity,value in attributes() if identity==b'SourceFile']
        require(cursor==len(data) and len(sources)==1 and len(sources[0])==2,'One exact SourceFile attribute required: '+name)
        value=pool[struct.unpack('>H',sources[0])[0]].decode('utf-8')
        require(re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$]*\.java',value),'SourceFile identifier required')
        return value
    except (IndexError,KeyError,struct.error,UnicodeError) as error:
        raise GateError('Invalid SourceFile metadata: '+name) from error

def reproduce_namespace(text, edits):
    # javac SourcePositions use UTF16 code-unit offsets. Preserve actual source bytes/trivia.
    data=text.encode('utf-16-le')
    records=[]
    for edit in edits:
        exact(edit,('start','end','old','new','role'),'namespace token edit')
        begin,end=edit['start'],edit['end']
        require(type(begin) is int and type(end) is int and 0<=begin<=end<=len(data)//2,
            'Native namespace token offsets')
        require(data[begin*2:end*2].decode('utf-16-le')==edit['old'],'Native namespace original token')
        records.append((begin,end,edit['new'].encode('utf-16-le')))
    ordered=sorted(records)
    require(all(ordered[index-1][1]<=ordered[index][0] for index in range(1,len(ordered))),
        'Overlapping namespace token edits')
    for begin,end,replacement in reversed(ordered):data=data[:begin*2]+replacement+data[end*2:]
    return data.decode('utf-16-le').encode('utf-8')

def checked_json(record,label):
    path=ref(record,label)
    return json_load(path)

def distribution(request, artifacts):
    lock_path=ref(request['distributionLock'],'source-owned private distribution lock')
    require(lock_path==Path(request['sourceRoot']).resolve()/'native-builds/forge16165/extensions.lock.json',
        'Product driver must select its actual canonical private16 lock')
    lock=json_load(lock_path)
    exact(lock,('source','project','version','extensionId','openAllayApiVersion','artifact'),'distribution lock')
    exact(lock['source'],('repository','revision'),'locked Builder source')
    require(lock['source']['repository']=='https://github.com/nkanf-dev/OpenAllay-Extensions.git'
        and lock['project']=='extensions/minecraft-builder'
        and lock['artifact']=='universal/build/libs/openallay-builder-universal-0.4.0.jar',
        'Exact current isolated Builder source/artifact contract')
    require(lock['version']=='0.4.0' and lock['extensionId']=='openallay:builder'
        and lock['openAllayApiVersion']=='0.4.0','Locked Builder identity')
    require(lock['source']['revision']=='e37eb4f325d6917b2a0b32afc47dd139a55acb83',
        'Private16 lock actual retained isolated Builder source')
    closure.safe_name(lock['project']);closure.safe_name(lock['artifact'])
    basename=Path(lock['artifact']).name
    require(re.fullmatch(r'[A-Za-z0-9_.-]+\.jar',basename),'Raw bundled Builder filename')
    resource=BUNDLED+basename
    artifact=artifacts['builder']
    provenance=checked_json(request['distributionProvenance'],'distribution provenance')
    exact(provenance,('source','project','version','extensionId','openAllayApiVersion','artifact'),'distribution provenance')
    exact(provenance['source'],('repository','revision','dirty','pinned'),'distribution source')
    require(provenance['source']=={**lock['source'],'dirty':False,'pinned':True},'Locked clean Builder provenance')
    for field in ('project','version','extensionId','openAllayApiVersion'):
        require(provenance[field]==lock[field],'Distribution identity mismatch: '+field)
    require(provenance['artifact']=={'path':resource,'sha256':artifact['sha256']},'Distribution original Builder bytes')
    proof=checked_json(request['builderSourceProof'],'retained Builder source proof')
    exact(proof,('archiveSha256','retainedSourceRevision','lockSha256','lockedSourceRevision','retainedSourceRoot','lockedSourceRoot','sourceScopes','retainedInputs','lockedInputs'), 'Builder source proof')
    require(proof['archiveSha256']==artifact['sha256'] and proof['lockSha256']==request['distributionLock']['sha256']
        and proof['lockedSourceRevision']==lock['source']['revision'],'Builder source proof identity')
    require(re.fullmatch(r'[0-9a-f]{40}',proof['retainedSourceRevision']),'Retained Builder source revision')
    require(isinstance(proof['sourceScopes'],list) and proof['sourceScopes']
        and len(proof['sourceScopes'])==len(set(proof['sourceScopes'])),'Explicit reviewed Builder source scopes')
    for scope in proof['sourceScopes']:closure.safe_name(scope)
    require(lock['project'] in proof['sourceScopes'] and 'gradle.properties' in proof['sourceScopes']
        and 'settings.gradle' in proof['sourceScopes'] and 'build.gradle' in proof['sourceScopes'],
        'Builder whole project/root build inputs must be source-parity scopes')
    def source_inventory(root_path,revision):
        root=Path(root_path).resolve()
        require(Path(root_path).is_absolute() and root.is_dir(),'Actual Builder source checkout required')
        def git(*args):
            result=subprocess.run(['git','-C',str(root),*args],check=True,capture_output=True)
            return result.stdout
        require(git('rev-parse','HEAD').decode().strip()==revision and
            not git('status','--porcelain','--untracked-files=all','--',*proof['sourceScopes']),
            'Actual committed/clean Builder input source')
        paths=git('ls-files','-z','--',*proof['sourceScopes']).decode().split('\0')
        inventory={name:file_sha(source_file(root,name)) for name in paths if name}
        require(inventory,'Actual Builder source input inventory')
        return inventory
    require(source_inventory(proof['retainedSourceRoot'],proof['retainedSourceRevision'])==proof['retainedInputs']
        and source_inventory(proof['lockedSourceRoot'],proof['lockedSourceRevision'])==proof['lockedInputs'],
        'Independently recomputed Builder committed source inventories')
    require(isinstance(proof['retainedInputs'],dict) and proof['retainedInputs']
        and proof['retainedInputs']==proof['lockedInputs'],'Exact retained/locked Builder source parity required')
    for name,digest in proof['retainedInputs'].items():
        closure.safe_name(name);require(re.fullmatch(r'[0-9a-f]{64}',digest),'Builder source parity hash')
    entries=closure.archive(artifact)
    descriptor=closure.builder_descriptor(entries['META-INF/openallay-extension.json'])
    require(descriptor['id']==lock['extensionId'] and descriptor['version']==lock['version'],'Builder descriptor/lock')
    for name,data in entries.items():
        if name.endswith('.class'):
            require(closure.class_info(data,name)==52 and name.startswith('dev/openallay/builder/'),'Builder Java8/private ownership: '+name)
        require(not name.startswith(('META-INF/versions/','META-INF/jarjar/','META-INF/coremods/'))
            and name not in ('META-INF/mods.toml','META-INF/neoforge.mods.toml','fabric.mod.json','mcmod.info','module-info.class'),
            'No Builder loader/MR payload: '+name)
        require(not name.endswith(('.jar','.zip')),'No nested Builder archive')
        if name.startswith('META-INF/services/'):
            for provider in closure.service(data,name):
                require(provider.startswith('dev.openallay.builder.') and provider.replace('.','/')+'.class' in entries,
                    'Builder service private owner')
    raw=Path(artifact['path']).read_bytes()
    require(sha(raw)==artifact['sha256'],'Builder changed during raw copy')
    return resource,raw,Path(request['distributionProvenance']['path']).read_bytes(),proof

def archived_transport(record, proof, request):
    transport=checked_json(record,'archived normal native proof transport')
    exact(transport,('kind','artifact','run','artifactId','sourceRevision','rootReceipt','originalClosureSpecSha256',
        'derivedGeneratedRoot','derivedClassRoot','originalClosureSpec'),'archived normal build transport')
    require(transport['kind']=='archived-normal-FG-build-derived-views'
        and transport['run']==37460049017 and transport['artifactId']==11412670735
        and transport['sourceRevision']==request['nativeSourceRevision']=='966192594efc73ea389682b5690765db53011f39',
        'Exact passed normal native source/run')
    archive_path=ref(transport['artifact'],'original uploaded native evidence')
    require(transport['artifact']['sha256']=='5655e7891dcd106b255e4986659045a8b10344094b254668d93555bc1913edcb',
        'Original successful native artifact identity')
    root_receipt=checked_json(transport['rootReceipt'],'root verified successful native run receipt')
    require(root_receipt.get('run')==transport['run'] and root_receipt.get('source')==transport['sourceRevision']
        and root_receipt.get('originalStatus')=='success' and root_receipt.get('archiveSha256')==transport['artifact']['sha256']
        and root_receipt.get('artifact',{}).get('id')==transport['artifactId'],'Root original run/artifact custody')
    with zipfile.ZipFile(archive_path) as bundle:
        require(len(bundle.namelist())==len(set(bundle.namelist())),'Duplicate native artifact members')
        def member(name):
            prefix='native-builds/forge16165/build/'
            return bundle.read(prefix+name)
        pairs=[('selection','namespace/source-selection.json'),('namespace','namespace/receipt.json'),
            ('compilerLock','native-tooling/compiler-input-lock.proposed.json'),('apMappings','native-ap/mixins.tsrg')]
        for field,name in pairs:
            require(Path(proof[field]['path']).read_bytes()==member(name),'Transport saved input parity: '+field)
        require(Path(request['nativeMetadata']['path']).read_bytes()==member('native-metadata/build.json'),
            'Original native build metadata preserved')
        require(Path(request['nativeInput']['path']).read_bytes()==member('native-metadata/native-before-reobf.jar')
            and Path(request['nativeReobf']['path']).read_bytes()==member('libs/openallay-forge-1.16.5-native-0.4.3.jar'),
            'Archived original normal FG native pair')
        result=json.loads(bundle.read('build/forge16165-native-report/RESULT.json'),object_pairs_hook=closure.pairs)
        require(result.get('source')==transport['sourceRevision'] and result.get('nativeCompiled') is True
            and result.get('normalApReobf') is True and result.get('engineRebuilt') is False
            and result.get('gameExecuted') is False,'Archived successful normal compile/AP/reobf result')
    original_spec=checked_json(transport['originalClosureSpec'],'original passed compile closure')
    require(transport['originalClosureSpec']['sha256']==transport['originalClosureSpecSha256'],
        'Original native compile closure path/hash binding')
    effective_spec=checked_json(request['closureSpec'],'effective relocated same closure')
    identity=lambda value:(value['sourceRevision'],sorted((a['role'],a['coordinate'],a['sha256']) for a in value['artifacts']))
    require(identity(original_spec)==identity(effective_spec),'Relocated passed closure exact component identities')
    require(proof['generatedSourceRoot']==transport['derivedGeneratedRoot']
        and proof['javacOutputRoot']==transport['derivedClassRoot'],'Honest derived-view root identities')
    return transport

def native_check(request, artifacts, closure_report):
    proof=checked_json(request['nativeProof'],'root-reviewed native ownership proof')
    fields=('sourceRevision','selection','namespace','compilerLock','normalMappings',
        'apMappings','generatedSourceRoot','javacOutputRoot','classes','resources','runtimeOwnership','namespaceAcceptance','mappingInputs')
    exact(proof,tuple(field for field in fields if field!='mappingInputs')+('archivedTransport',)
        if 'archivedTransport' in proof else fields,'native ownership proof')
    transport=archived_transport(proof['archivedTransport'],proof,request) if 'archivedTransport' in proof else None
    require(proof['sourceRevision']==request['nativeSourceRevision'],'Native proof source revision')
    root=Path(request['sourceRoot']).resolve()
    require(root.is_absolute() and root.is_dir(),'Canonical source root')
    git_head=subprocess.run(['git','-C',str(root),'rev-parse','HEAD'],check=True,capture_output=True).stdout.decode().strip()
    git_dirty=subprocess.run(['git','-C',str(root),'status','--porcelain','--untracked-files=all','--',
        'common','adapters/minecraft','neoforge','forge','gradle.properties','LICENSE',
        'native-builds/forge16165/extensions.lock.json'],check=True,capture_output=True).stdout
    require(git_head==proof['sourceRevision'] and not git_dirty,'Actual clean committed canonical native source identity')
    selection=checked_json(proof['selection'],'raw selected source receipt')
    exact(selection,('sourceRevision','tuple','java','resources'),'source selection')
    require(selection['sourceRevision']==proof['sourceRevision'],'Selected native source identity')
    tuple_=selection['tuple']
    require(tuple_.get('minecraft')=='1.16.5' and tuple_.get('forge')=='1.16.5-36.2.42'
        and tuple_.get('forgeGradle')=='6.0.54' and tuple_.get('gradle')=='8.4','Actual native tuple')
    java=index_records(selection['java'],('owner','path','origin','sha256'),'selected Java','path')
    resources=index_records(selection['resources'],('owner','path','origin','sha256'),'selected resource','path')
    for records in (java,resources):
        for path,record in records.items():
            closure.safe_name(path)
            require(record['owner'] in OWNER_ROOTS and record['origin'].startswith(OWNER_ROOTS[record['owner']]),'Canonical source owner')
            require(file_sha(source_file(root,record['origin']))==record['sha256'],'Canonical source bytes: '+record['origin'])
    namespace=checked_json(proof['namespace'],'generated namespace receipt')
    if transport is None:
        ref(proof['namespaceAcceptance'],'exact accepted tooling metadata receipt')
        require(namespace.get('metadataAcceptanceSha256')==proof['namespaceAcceptance']['sha256']
            and all(re.fullmatch(r'[0-9a-f]{64}',namespace.get(field,'')) for field in
                ('jdkIdentity','toolIdentity','metadataClasspathIdentity')), 'Namespace exact tool/JDK/metadata identity')
        mapping_inputs=checked_json(proof['mappingInputs'],'namespace mapping provenance')
        exact(mapping_inputs,('client','server','tsrg'),'namespace mapping inputs')
        for record in mapping_inputs.values():ref(record,'actual namespace mapping file')
        require(namespace.get('mappingHashes')==[mapping_inputs[name]['sha256'] for name in ('client','server','tsrg')],
            'Exact native type mapping inputs')
    else:
        require(all(re.fullmatch(r'[0-9a-f]{64}',namespace.get(field,'')) for field in
            ('metadataAcceptanceSha256','jdkIdentity','toolIdentity','metadataClasspathIdentity')),
            'Archived exact tooling identities; original payload not re-created')
    require(namespace.get('syntaxLevel')=='17' and namespace.get('preview') is False,'Native source grammar17')
    normalized=index_records(namespace['units'],('owner','path','mode','inputHash','outputHash','edits','nativeIdentities'),'normalized Java','path')
    require(set(normalized)==set(java),'One canonical/native namespace unit union')
    input_units=index_records(namespace.get('allInputUnits'),('owner','path','mode','sha256'),'producer raw union','path')
    require(set(input_units)==set(java),'No namespace symbolic/project duplicate owner')
    for path,unit in normalized.items():
        require(isinstance(unit['nativeIdentities'],list),'Namespace native identity audit')
        for identity in unit['nativeIdentities']:
            exact(identity,('role','originalOfficialBinary','actualBinary'),'native identity audit')
            require(isinstance(identity['role'],str) and identity['role'] and
                isinstance(identity['actualBinary'],str) and identity['actualBinary'], 'Actual native identity audit')
        require(unit['owner']==java[path]['owner'] and unit['inputHash']==java[path]['sha256']
            and unit['mode'] in ('MOJANG_CANONICAL','ACTUAL_MCP')
            and input_units[path]=={'owner':unit['owner'],'path':path,'mode':unit['mode'],'sha256':java[path]['sha256']},
            'Explicit native namespace/source ownership')
    generated_root=Path(proof['generatedSourceRoot']).resolve()
    javac_root=Path(proof['javacOutputRoot']).resolve()
    require(Path(proof['generatedSourceRoot']).is_absolute() and generated_root.is_dir()
        and Path(proof['javacOutputRoot']).is_absolute() and javac_root.is_dir()
        and generated_root != root and javac_root != root,'Explicit producer/javac output roots')
    generated_files={str(path.relative_to(generated_root)):path for path in generated_root.rglob('*') if path.is_file()}
    require(set(generated_files)==set(java),'Exact generated native unit tree')
    for path,file in generated_files.items():
        raw=source_file(root,java[path]['origin']).read_bytes()
        require(reproduce_namespace(raw.decode('utf-8'),normalized[path]['edits'])==file.read_bytes(),
            'Generated source must equal recorded canonical token edits: '+path)
        require(not file.is_symlink() and file.resolve().is_relative_to(generated_root)
            and file_sha(file)==normalized[path]['outputHash'],'Generated namespace/source identity: '+path)
    javac_files={str(path.relative_to(javac_root)):path for path in javac_root.rglob('*') if path.is_file()}
    require(javac_files and all(name.endswith('.class') for name in javac_files),'Native javac output class tree only')
    before=closure.archive(request['nativeInput']);after=closure.archive(request['nativeReobf'])
    require(request['nativeInput']['role']=='native-input' and request['nativeReobf']['role']=='native-reobf'
        and request['nativeInput']['coordinate']==NATIVE_COORDINATE
        and request['nativeReobf']['coordinate']==NATIVE_COORDINATE,'Normal product native artifact identities')
    before={n:b for n,b in before.items() if not n.endswith('/')}
    after={n:b for n,b in after.items() if not n.endswith('/')}
    require(set(before)==set(after),'Normal FG changed native entry set')
    classes=index_records(proof['classes'],('entry','owner','sourcePath','sourceSha256','javacSha256'), 'native class proof','entry')
    class_entries={n for n in before if n.endswith('.class')}
    require(set(classes)==class_entries==set(javac_files) and classes,'Exact compiled native class coverage')
    engine_entries=closure.archive(artifacts['engine'])
    retained_names={entry['name'] for entry in closure_report['plannedEntries'] if entry['name'].endswith('.class')}
    owners_seen=set()
    for name,record in classes.items():
        require(name.startswith('dev/openallay/') and not name.startswith(FORBIDDEN_NATIVE)
            and name not in engine_entries and name not in retained_names,'Native/engine/SDK/alias competing owner: '+name)
        require(closure.class_info(before[name],name)==61 and closure.class_info(after[name],name)==61,'Native class major61')
        javac_file=javac_files[name]
        require(not javac_file.is_symlink() and javac_file.resolve().is_relative_to(javac_root)
            and file_sha(javac_file)==sha(before[name])==record['javacSha256'],
            'Native raw archive/actual javac output byte mismatch')
        unit=java.get(record['sourcePath'])
        require(unit is not None and unit['owner']==record['owner'] and unit['sha256']==record['sourceSha256'],'Native class canonical owner')
        require(class_source(before[name],name)==Path(record['sourcePath']).name,'Class SourceFile/logical owner mismatch')
        require(name.rsplit('/',1)[0]==record['sourcePath'].rsplit('/',1)[0],'Class package/source owner mismatch')
        primary=record['sourcePath'][:-5]
        require(name==primary+'.class' or name.startswith(primary+'$'),
            'Class primary/nested binary identity must match source; secondary top-level needs separate AST proof')
        owners_seen.add(record['sourcePath'])
    require(owners_seen==set(java),'Every selected native unit must own compiled classes')
    processed=index_records(proof['resources'],('entry','owner','sourcePath','sourceSha256','processedSha256'), 'native resource proof','entry')
    require(set(processed)==set(resources),'One selected raw/processed resource union')
    product={}
    for line in (root/'gradle.properties').read_text(encoding='utf-8').splitlines():
        if line and not line.startswith('#') and '=' in line:
            key,value=line.split('=',1)
            require(key not in product,'Duplicate product property');product[key]=value
    props={key:product[key] for key in ('version','mod_name','mod_id','mod_author','license','description')}
    props.update(minecraft_version_range='[1.16.5]',forge_native_version='36.2.42',
        forge_loader_version_range='[36,)',java_version='17')
    require(props['version']=='0.4.3' and props['mod_id']=='openallay','Current source-owned product properties')
    for name,record in processed.items():
        unit=resources.get(record['sourcePath'])
        require(record['sourcePath']==name and unit is not None and record['owner']==unit['owner']
            and record['sourceSha256']==unit['sha256'],'Processed resource raw source owner')
        raw=source_file(root,unit['origin']).read_bytes()
        expected=raw
        if name=='META-INF/mods.toml' or name=='pack.mcmeta' or name.endswith('.mixins.json'):
            text=raw.decode('utf-8')
            for key,value in props.items():text=text.replace('${'+key+'}',value)
            require('${' not in text,'Only declared native resource template expansions')
            expected=text.encode('utf-8')
        require(name in before and before[name]==expected,'Processed native resource equals source/declared expansion: '+name)
        require(name in before and sha(before[name])==record['processedSha256'] and before[name]==after[name],
            'Native resource expansion/reobf parity: '+name)
    allowed=set(processed)|set(classes)|{'META-INF/MANIFEST.MF','openallay.refmap.json','LICENSE_OpenAllay'}
    require(set(before)==allowed,'Native archive exact source-owned entry coverage')
    require(NATIVE_REQUIRED<=set(before),'Required genuine product native resources missing')
    require(before['LICENSE_OpenAllay']==after['LICENSE_OpenAllay']==(root/'LICENSE').read_bytes(),
        'Native pre/post-reobf product license original bytes')
    require(before['openallay.refmap.json']==after['openallay.refmap.json'],'Normal reobf changed AP refmap')
    refmap=json.loads(before['openallay.refmap.json'],object_pairs_hook=closure.pairs)
    require(isinstance(refmap,dict) and isinstance(refmap.get('mappings'),dict),'Normal AP refmap mappings')
    for name in before:
        require(not name.startswith(('META-INF/versions/','META-INF/jarjar/','META-INF/coremods/',BUNDLED))
            and name not in (PROVENANCE,'META-INF/neoforge.mods.toml','fabric.mod.json','META-INF/openallay-extension.json',
                'META-INF/accesstransformer.cfg','openallay.accesswidener')
            and not name.endswith(('.jar','.zip')),'Unexpected native loading/resource owner: '+name)
    configs=sorted(name for name in resources if name.endswith('.mixins.json'))
    configured=[]
    for name in configs:
        config=json.loads(before[name],object_pairs_hook=closure.pairs)
        require(config.get('required') is True and config.get('compatibilityLevel')=='JAVA_17'
            and config.get('refmap')=='openallay.refmap.json' and config.get('injectors',{}).get('defaultRequire')==1,
            'Exact native Mixin AP/refmap contract: '+name)
        package=config.get('package')
        require(isinstance(package,str) and package.startswith('dev.openallay.'),'Product-owned Mixin package')
        for scope in ('mixins','client','server'):
            bindings=config.get(scope,[])
            require(isinstance(bindings,list),'Native configured scope list: '+scope)
            for binding in bindings:
                require(isinstance(binding,str) and closure.IDENTIFIER.fullmatch(binding),'Mixin binding identity')
                identity=package+'.'+binding
                entry=identity.replace('.','/')+'.class'
                source_path=identity.replace('.','/')+'.java'
                source_owner=java.get(source_path)
                class_owner=classes.get(entry)
                require(identity not in configured and source_owner is not None and class_owner is not None
                    and class_owner['sourcePath']==source_path
                    and class_owner['owner']==source_owner['owner']
                    and class_owner['sourceSha256']==source_owner['sha256'],
                    'Configured sole selected source/class owner: '+identity)
                configured.append(identity)
    attrs=closure.manifest(before['META-INF/MANIFEST.MF'])
    require(attrs.get('fmlmodtype')=='MOD' and attrs.get('mixinconfigs')==','.join(configs),'Normal product Mixin/Forge manifest')
    require(attrs.get('multi-release','false').lower()=='false' and not set(attrs)&
        {'class-path','main-class','premain-class','agent-class','fmlcoreplugin','fmlat'},'Native archive special loading metadata')
    require(before['META-INF/MANIFEST.MF']==after['META-INF/MANIFEST.MF'],'Normal reobf changed native manifest')
    mods=tomllib.loads(before['META-INF/mods.toml'].decode('utf-8'))
    require(mods.get('modLoader')=='javafml' and mods.get('loaderVersion')=='[36,)'
        and len(mods.get('mods',[]))==1 and mods['mods'][0].get('modId')=='openallay'
        and mods['mods'][0].get('version')=='0.4.3','Actual Forge36 product mod metadata')
    deps=mods.get('dependencies',{})
    require(set(deps)=={'openallay'} and len(deps['openallay'])==2,'Exact native product host dependencies')
    require({item.get('modId') for item in deps['openallay']}=={'forge','minecraft'},'Actual Forge/Minecraft dependencies')
    for item in deps['openallay']:
        exact(item,('modId','mandatory','versionRange','ordering','side'),'product host dependency')
        require(item['mandatory'] is True and item['ordering']=='NONE' and item['side']=='BOTH'
            and item['versionRange']=={'forge':'[36.2.42,)','minecraft':'[1.16.5]'}[item['modId']], 'Exact product target dependency')
    packmeta=json.loads(before['pack.mcmeta'],object_pairs_hook=closure.pairs)
    require(packmeta.get('pack',{}).get('pack_format')==6,'Actual1.16 resource pack format6')
    palette=json.loads(before['dev/openallay/adapter/minecraft/v26_2/world/material-palette-inputs.json'],object_pairs_hook=closure.pairs)
    require(isinstance(palette,dict) and 'lightning_rod_up' not in palette,'Exact target palette excludes unavailable role')
    platform='META-INF/services/dev.openallay.platform.PlatformService'
    require(closure.service(before[platform],platform)==['dev.openallay.neoforge.NeoForgePlatformService']
        and 'dev/openallay/neoforge/NeoForgePlatformService.class' in classes,'One actual selected PlatformService provider')
    metadata=checked_json(request['nativeMetadata'],'fresh normal FG native metadata')
    require(metadata.get('sourceRevision')==proof['sourceRevision'] and metadata.get('minecraft')=='1.16.5'
        and metadata.get('forge')=='36.2.42' and metadata.get('forgeGradle')=='6.0.54' and metadata.get('gradle')=='8.4'
        and metadata.get('nativeCompilerRelease')==17 and metadata.get('sharedClosurePacked') is False
        and metadata.get('gameExecuted') is False,'Native build actual tuple/no product runtime claim')
    for field,artifact in (('before',request['nativeInput']),('reobf',request['nativeReobf'])):
        original=metadata.get(field,{})
        require(original.get('sha256')==artifact['sha256'] and
            (transport is not None or original.get('path')==artifact['path']), 'Native pair receipt original hash/path custody: '+field)
    for field,record in (('selectionSha256',proof['selection']),('namespaceSha256',proof['namespace']),
        ('compilerLockSha256',proof['compilerLock']),('normalMappingsSha256',proof['normalMappings']),('apMappingsSha256',proof['apMappings'])):
        if transport is None or field!='normalMappingsSha256':ref(record,field)
        require(metadata.get(field)==record['sha256'],'Native input metadata hash: '+field)
    require(metadata.get('apRefmapSha256')==sha(before['openallay.refmap.json'])
        and metadata.get('closureSpecSha256')==(transport['originalClosureSpecSha256'] if transport is not None
            else request['closureSpec']['sha256']),'Native AP/effective original closure metadata')
    compiler_lock=checked_json(proof['compilerLock'],'actual native compiler artifact lock')
    exact(compiler_lock,('configurations',),'native compiler artifact lock')
    require(set(compiler_lock['configurations'])=={'compileClasspath','annotationProcessor'},'Actual compile/AP lock configurations')
    runtime=checked_json(proof['runtimeOwnership'],'actual game-owned runtime proof')
    exact(runtime,('minecraft','forge','artifacts','classes'),'runtime host ownership')
    require(runtime['minecraft']=='1.16.5' and runtime['forge']=='36.2.42','Actual runtime tuple')
    runtime_artifacts=index_records(runtime['artifacts'],('role','coordinate','path','sha256'),'actual runtime artifact','role')
    actual_runtime={item['sha256']:(item,closure.archive(item)) for item in runtime_artifacts.values()}
    require(len(actual_runtime)==len(runtime_artifacts),'Unique actual runtime archive hash owners')
    require(sum(len(data) for _,entries in actual_runtime.values() for data in entries.values())<=closure.MAX_EXPANDED,
        'Whole runtime inspection expanded bound')
    runtime_classes=index_records(runtime['classes'],('entry','coordinate','archiveSha256','classSha256'),'runtime host class','entry')
    require(HOST_CLASSES.keys()<=runtime_classes.keys(),'Actual runtime host ownership evidence required')
    for name,coordinate in HOST_CLASSES.items():
        record=runtime_classes[name]
        require(coordinate is None or record['coordinate']==coordinate,'Actual runtime game library coordinate: '+name)
        require(re.fullmatch(r'[0-9a-f]{64}',record['archiveSha256']) and re.fullmatch(r'[0-9a-f]{64}',record['classSha256']), 'Runtime owner hashes')
        require(name not in class_entries and name not in retained_names,'No host replacement in product')
        require(record['archiveSha256'] in actual_runtime,'Runtime owner original archive supplied')
        actual_artifact,actual_entries=actual_runtime[record['archiveSha256']]
        require(actual_artifact['coordinate']==record['coordinate'] and name in actual_entries
            and sha(actual_entries[name])==record['classSha256'],'Actual runtime host declaration bytes')
        require(sum(name in entries for _,entries in actual_runtime.values())==1,'Sole actual runtime class owner')
    return proof,before,after,attrs

def product_scan(request_path):
    request=json_load(request_path)
    exact(request,('sourceRoot','nativeSourceRevision','closureSpec','closurePolicy','closureGate',
        'nativeInput','nativeReobf','nativeMetadata','nativeProof','distributionLock',
        'distributionProvenance','builderSourceProof','retainedResolution'),'product packing request')
    require(isinstance(request['sourceRoot'],str) and Path(request['sourceRoot']).is_absolute()
        and re.fullmatch(r'[0-9a-f]{40}',request['nativeSourceRevision']),'Canonical native source identity')
    spec_path=ref(request['closureSpec'],'retained18 closure spec')
    policy_path=ref(request['closurePolicy'],'retained ownership policy')
    gate_path=ref(request['closureGate'],'accepted closure scan gate')
    closure_report,outputs=closure.scan(spec_path,policy_path)
    require(closure_report==json_load(gate_path) and closure_report['status']=='READY','Exact unchanged retained closure READY gate')
    artifacts={item['role']:item for item in closure_report['artifacts']}
    require(set(artifacts)==ROLES,'Current exact18 effective component closure')
    immutable_path=Path(request['sourceRoot']).resolve()/'native-builds/engine-only/retained-inputs.json'
    require(file_sha(immutable_path)==IMMUTABLE_COMPONENT_LOCK_SHA256,'Immutable17 component source lock changed')
    immutable=json_load(immutable_path)
    immutable_records=index_records(immutable['artifacts'],('role','coordinate','sha256'),'immutable nonengine component','role')
    require(set(immutable_records)==ROLES-{'engine'},'Exact17 unchanged component inputs')
    for role,pin in immutable_records.items():
        require(artifacts[role]['coordinate']==pin['coordinate'] and artifacts[role]['sha256']==pin['sha256'],
            'Effective engine must not replace immutable component: '+role)
    retained=checked_json(request['retainedResolution'],'original18 producer resolution provenance')
    exact(retained,('actualEngineSource','retainedInputSource','retainedArchive','builderArchive',
        'runtimeClasspathConfiguration','artifacts','deploymentAdditions','constituentProofs'),'retained raw producer resolution')
    originals=index_records(retained['artifacts'],('role','coordinate','originalPath','sha256','sourceRevision'),
        'retained original component provenance','role')
    require(set(originals)==set(artifacts) and retained['actualEngineSource']==closure_report['sourceRevision'],
        'Original18 producer identity/role closure')
    require(originals['engine']['sourceRevision']==retained['actualEngineSource']==closure_report['sourceRevision'],
        'Effective engine row/producer/closure source identity')
    for role,artifact in artifacts.items():
        if role!='engine':
            require(originals[role]['sourceRevision']==(
                'e37eb4f325d6917b2a0b32afc47dd139a55acb83' if role=='builder' else
                'cbcf5e66c81d11e4d219fa6cc8da04ab996c7ce0'),
                'Unchanged component producer source must not be relabelled: '+role)
        require(originals[role]['coordinate']==artifact['coordinate'] and originals[role]['sha256']==artifact['sha256'],
            'Retained original component identity: '+role)
    require(originals['builder']['sourceRevision']=='e37eb4f325d6917b2a0b32afc47dd139a55acb83',
        'Accepted isolated retained Builder source identity')
    # Builder disposition in the probe scan was validated, but is not a host product output.
    outputs={name:(data,[owner for owner in owners if owner.get('role')!='builder'])
        for name,(data,owners) in outputs.items()
        if not name.startswith('META-INF/services/') and any(owner.get('role')!='builder' for owner in owners)}
    for record in closure_report['inventory']:
        if record['role']=='builder':record['productDisposition']='retain-only-inside-raw-bundled-Builder'
    builder_resource,raw_builder,provenance,builder_proof=distribution(request,artifacts)
    require(builder_proof['retainedSourceRevision']==originals['builder']['sourceRevision'],
        'Builder source proof must name actual retained isolated source')
    native_proof,before,after,attrs=native_check(request,artifacts,closure_report)
    native_owners=[]
    for name,data in after.items():
        if name=='META-INF/MANIFEST.MF' or name.startswith('META-INF/services/'):continue
        record={'role':'native-reobf','archiveSha256':request['nativeReobf']['sha256'],
            'name':name,'sha256':sha(data),'disposition':'copy-normal-native-reobf'}
        if name in outputs:
            require(name=='LICENSE_OpenAllay' and outputs[name][0]==data,'Native/closure competing sole owner: '+name)
            outputs[name][1].append(record)
        else:outputs[name]=(data,[record])
        native_owners.append(record)
    services={}
    for artifact in sorted(artifacts.values(),key=lambda a:a['role']):
        if artifact['role'] in {'builder','json-proof','maven-proof','gson','guava','failureaccess','listenablefuture'}:continue
        for name,data in closure.archive(artifact).items():
            if name.startswith('META-INF/services/') and not name.endswith('/'):
                services.setdefault(name,[]).append((data,{'role':artifact['role'],'archiveSha256':artifact['sha256'],'name':name,'sha256':sha(data),'disposition':'union-service'}))
    for name,data in after.items():
        if name.startswith('META-INF/services/'):
            services.setdefault(name,[]).append((data,{'role':'native-reobf','archiveSha256':request['nativeReobf']['sha256'],'name':name,'sha256':sha(data),'disposition':'union-service'}))
    for name,members in sorted(services.items()):
        require(name not in outputs,'Service/resource sole-owner conflict')
        providers=[]
        for data,owner in members:
            for provider in closure.service(data,name):
                if provider not in providers:providers.append(provider)
        for provider in providers:
            require(provider.replace('.','/')+'.class' in outputs,'Product service provider sole packed owner: '+provider)
            require(not provider.startswith('dev.openallay.builder.'),'Builder service must stay inside raw payload')
        merged=members[0][0] if len(members)==1 else ('\n'.join(providers)+'\n').encode('utf-8')
        outputs[name]=(merged,[owner for _,owner in members])
    for name,data,role in ((builder_resource,raw_builder,'raw-builder'),(PROVENANCE,provenance,'distribution-provenance')):
        require(name not in outputs,'Competing raw Builder/distribution resource')
        outputs[name]=(data,[{'role':role,'sha256':sha(data),'disposition':'copy-unchanged-raw-resource'}])
    require('META-INF/openallay-extension.json' not in outputs and not any(name.startswith('dev/openallay/builder/') for name in outputs),
        'No flattened Builder descriptor/classes')
    manifest=b''.join(manifest_line(key,value) for key,value in
        [('Manifest-Version','1.0'),('FMLModType','MOD'),('Implementation-Version','0.4.3'),
         ('MixinConfigs',attrs['mixinconfigs'])]+
        ([('Multi-Release','true')] if closure_report['multiRelease'] else []))+b'\r\n'
    outputs['META-INF/MANIFEST.MF']=(manifest,[{'role':'product-packer','sha256':sha(manifest),'disposition':'generate-classic-product-manifest'}])
    # Original immutable engine bytes are inserted after FG reobf; prove every packed constituent disposition.
    for artifact in closure_report['artifacts']:
        for record in closure_report['inventory']:
            if record['role']!=artifact['role'] or not record['disposition'].startswith('copy-') or artifact['role']=='builder':continue
            name=record['target']
            require(name in outputs and sha(outputs[name][0])==record['sha256'],'Unchanged retained product entry: '+name)
    require(sum(len(data) for data,_ in outputs.values())<=closure.MAX_EXPANDED,'Whole product expanded bound')
    result={'status':'READY','nativeSourceRevision':request['nativeSourceRevision'],
        'retainedClosureSourceRevision':closure_report['sourceRevision'],'requestSha256':file_sha(request_path),
        'productPackerSha256':file_sha(__file__),'closureScannerSha256':file_sha(SCANNER),
        'closureGateSha256':request['closureGate']['sha256'],'originalArtifacts':closure_report['artifacts'],
        'nativeInput':request['nativeInput'],'nativeReobf':request['nativeReobf'],
        'nativeProofSha256':request['nativeProof']['sha256'],'builderSourceProofSha256':request['builderSourceProof']['sha256'],
        'distributionLockSha256':request['distributionLock']['sha256'],'builderResource':builder_resource,
        'builderArchiveSha256':artifacts['builder']['sha256'],'multiRelease':closure_report['multiRelease'],
        'gameExecuted':False,'supportAdmitted':False,'nativeClassOwners':native_proof['classes'],
        'entries':[{'name':name,'sha256':sha(data),'bytes':len(data),'major':closure.class_info(data,name) if name.endswith('.class') else None,'owners':owners}
            for name,(data,owners) in sorted(outputs.items())]}
    protected=[request_path,SCANNER,Path(__file__),immutable_path,Path(request['sourceRoot']).resolve()]
    for field in ('closureSpec','closurePolicy','closureGate','nativeMetadata','nativeProof','distributionLock','distributionProvenance','builderSourceProof','retainedResolution'):
        protected.append(request[field]['path'])
    for field in ('nativeInput','nativeReobf'):protected.append(request[field]['path'])
    protected.extend(artifact['path'] for artifact in closure_report['artifacts'])
    protected.append(json_load(spec_path)['resolution']['path'])
    for field in ('selection','namespace','compilerLock','normalMappings','apMappings','runtimeOwnership','namespaceAcceptance','mappingInputs'):
        if field in native_proof:protected.append(native_proof[field]['path'])
    if 'archivedTransport' not in native_proof:
        protected.extend(record['path'] for record in checked_json(native_proof['mappingInputs'],'protected mapping inputs').values())
    else:
        protected.append(native_proof['archivedTransport']['path'])
        transport=checked_json(native_proof['archivedTransport'],'protected archived transport')
        protected.extend([transport['artifact']['path'],transport['rootReceipt']['path'],transport['originalClosureSpec']['path']])
    protected.extend([native_proof['generatedSourceRoot'],native_proof['javacOutputRoot'],
        builder_proof['retainedSourceRoot'],builder_proof['lockedSourceRoot']])
    runtime=checked_json(native_proof['runtimeOwnership'],'protected actual runtime proof')
    protected.extend(item['path'] for item in runtime['artifacts'])
    selection=checked_json(native_proof['selection'],'protected source receipt')
    protected.extend([Path(request['sourceRoot']).resolve()/'gradle.properties',Path(request['sourceRoot']).resolve()/'LICENSE'])
    protected.extend(source_file(Path(request['sourceRoot']).resolve(),record['origin']) for records in (selection['java'],selection['resources']) for record in records)
    return result,outputs,protected

def pack(request_path,gate,gate_sha256,output,receipt):
    output=ordinary_output(output);receipt=ordinary_output(receipt)
    require(file_sha(gate)==gate_sha256,'Product gate SHA mismatch')
    accepted=json_load(gate);actual,entries,protected=product_scan(request_path)
    protected=protected+[gate]
    finals=[output,receipt]
    protect_outputs(finals,protected)
    require(actual==accepted and actual['status']=='READY','Product gate changed/STOP')
    tmp,fd,jar_stage=stage_path(output,protected,finals)
    receipt_stage=None
    try:
        with os.fdopen(fd,'wb') as stream:
            with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_STORED,allowZip64=False) as target:
                for name,(data,_) in sorted(entries.items()):
                    closure.safe_name(name);item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.create_system=3;item.external_attr=0o100644<<16
                    target.writestr(item,data)
            stream.flush();os.fsync(stream.fileno())
        final=closure.archive({'role':'final','coordinate':'final','path':str(tmp),'sha256':file_sha(tmp)})
        require(set(final)==set(entries) and all(final[name]==data for name,(data,_) in entries.items()),'Final source-owned product parity')
        again,_,_=product_scan(request_path)
        require(again==actual and file_sha(gate)==gate_sha256,'Product input changed during packing')
        result={'status':'PACKED','outputSha256':file_sha(tmp),'gateSha256':gate_sha256,'source':actual,
            'originalComponentsPreserved':True,'rawBuilderPreserved':True,'gameExecuted':False,'supportAdmitted':False}
        receipt_tmp,receipt_stage=stage_bytes(receipt,encoded(result),protected,finals+[tmp])
        publish_staged([(tmp,output,jar_stage),(receipt_tmp,receipt,receipt_stage)])
        return result
    finally:
        try:
            if receipt_stage is not None:remove_created(receipt_stage)
        finally:remove_created(jar_stage)

def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='command',required=True)
    scan_parser=sub.add_parser('scan');pack_parser=sub.add_parser('pack')
    for command in (scan_parser,pack_parser):command.add_argument('--request',required=True)
    scan_parser.add_argument('--report',required=True)
    for option in ('gate','gate-sha256','output','receipt'):pack_parser.add_argument('--'+option,required=True)
    args=parser.parse_args()
    try:
        if args.command=='scan':
            result,_,protected=product_scan(args.request)
            report=ordinary_output(args.report)
            protect_outputs([report],protected)
            report_tmp,report_stage=stage_bytes(report,encoded(result),protected,[report])
            try:publish_staged([(report_tmp,report,report_stage)])
            finally:remove_created(report_stage)
            print('READY reportSha256='+file_sha(report));return 0
        result=pack(args.request,args.gate,args.gate_sha256,args.output,args.receipt)
        print('PACKED outputSha256='+result['outputSha256']);return 0
    except (GateError,OSError,KeyError,TypeError,ValueError,struct.error,subprocess.CalledProcessError,zipfile.BadZipFile) as error:
        print('STOP: '+str(error),file=sys.stderr);return 2

if __name__=='__main__':sys.exit(main())
