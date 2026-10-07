#!/usr/bin/env python3
"""Prepare current legacy products from source and immutable retained inputs.

Remote-only Forge 1.16.5 producer. The final consumer checks package custody.
Plan mode and fixture tests perform no downloads, compiles, or game launches.
"""
from __future__ import annotations
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = dict(artifactId=11406765949, runId=37450040748,
    sourceRevision='7819dbea0601f1ae7ef8b9c03286b6f8a0589bb4',
    sha256='6d1ac947f59d983ea1b88db15d456cec3b7ddffd2b8f26320836a5b04de83802')
PRODUCT16 = dict(artifactId=11435087072, runId=37511014882,
    sourceRevision='ee94284188f21003de589d509e880875cabc5cbc',
    sha256='6adcaac70856c5be46aead86f2b59e4b14713f17dae076c8631729b3b4e063c7')
BUILDER_SOURCE = '6e977110cbe8e0ca0b39c012f0cdfc10bafffef2'
BUILDER_SHA = 'bf8cfff9b82f84914aa1c84173d531f2256f537215a66a8d2057adc504632345'
PRODUCT16_SHA = '31a8f46c9fe0b020e9a853a75a59d57db3d5da63597b81d8491732cfc6ba7bf6'
RESOURCE = 'META-INF/openallay/bundled-extensions/openallay-builder-universal-0.4.0.jar'
PROVENANCE = 'META-INF/openallay/distribution.json'


def require(ok, message):
    if not ok:
        raise ValueError(message)


def pairs(items):
    result = {}
    for key, value in items:
        require(key not in result, 'Duplicate JSON member: '+key)
        result[key] = value
    return result


def load(path):
    return json.loads(Path(path).read_text(), object_pairs_hook=pairs,
        parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value)))


def encoded(value):
    return (json.dumps(value, sort_keys=True, indent=2)+'\n').encode()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(65536), b''):
            h.update(chunk)
    return h.hexdigest()


def ref(path):
    return {'path':str(Path(path).resolve()), 'sha256':sha(path)}


def write(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('xb') as stream:
        stream.write(encoded(value))
    return ref(path)


def git(*args):
    return subprocess.check_output(['git','-C',str(ROOT),*args], text=True).strip()


def source_version(root=None):
    root=ROOT if root is None else root
    matches = re.findall(r'(?m)^version\s*=\s*(\S+)\s*$', (root/'gradle.properties').read_text())
    require(len(matches)==1 and re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z._+\-]*', matches[0]),
        'Exactly one current source product version required')
    return matches[0]


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    sys.modules[name] = result
    spec.loader.exec_module(result)
    return result


def safe_name(name):
    p = PurePosixPath(name)
    require(name and not p.is_absolute() and p.as_posix()==name and
        not any(x in ('','..','.') for x in name.split('/')) and
        not any(c in name for c in '\\:\x00') and not any(ord(c)<32 for c in name),
        'Unsafe archive member: '+name)


def entries(path):
    require(Path(path).is_file() and not Path(path).is_symlink(), 'Ordinary archive required')
    result = {}
    with zipfile.ZipFile(path) as z:
        require(len(z.infolist())<=100000, 'Archive entry bound')
        require(sum(i.file_size for i in z.infolist())<=512*1024*1024, 'Archive expanded bound')
        for i in z.infolist():
            safe_name(i.filename.rstrip('/') if i.is_dir() else i.filename)
            require(((i.external_attr>>16)&0o170000)!=0o120000, 'Archive symlink')
            if not i.is_dir():
                require(i.filename not in result and i.file_size<=64*1024*1024, 'Duplicate/large archive member')
                result[i.filename] = z.read(i)
    return result


def inventory(payload):
    return {name:digest(data) for name,data in sorted(payload.items())}


def physical_inventory(payload):
    result={}
    for name,data in sorted(payload.items()):
        major=None
        if name.endswith('.class'):
            require(len(data)>=8 and data[:4]==b'\xca\xfe\xba\xbe', 'Invalid physical class: '+name)
            major=int.from_bytes(data[6:8],'big')
        result[name]={'sha256':digest(data),'bytes':len(data),'major':major}
    return result


def archive(path, payload):
    with Path(path).open('xb') as out, zipfile.ZipFile(out,'w',compression=zipfile.ZIP_STORED) as z:
        for name, data in sorted(payload.items()):
            safe_name(name)
            i = zipfile.ZipInfo(name,(1980,1,1,0,0,0)); i.create_system=3; i.external_attr=0o100644<<16
            z.writestr(i,data)
    require(entries(path)==payload, 'Output whole-entry parity')
    return ref(path)


def provider_matches(meta, pin, run, repo_id):
    require(set(pin)=={'artifactId','runId','sourceRevision','sha256'}, 'Exact immutable provider shape')
    require(meta.get('id')==pin['artifactId'] and meta.get('expired') is False and
        meta.get('digest')=='sha256:'+pin['sha256'] and
        meta['workflow_run']['id']==pin['runId'] and
        meta['workflow_run']['head_sha']==pin['sourceRevision'], 'Retained provider identity differs')
    require(run['id']==pin['runId'] and run['head_sha']==pin['sourceRevision'] and
        run['repository']['id']==repo_id and run['head_repository']['id']==repo_id and
        run['path']=='.github/workflows/minecraft-native.yml' and run['event']=='workflow_dispatch',
        'Retained provider source/repository/workflow differs')
    # A successful retained stage in a failed historical run is allowed; its
    # native/pack receipts are checked separately. Never relabel that whole run.


def retained(pin, work, label):
    require(os.environ.get('GITHUB_ACTIONS')=='true', 'Remote runner only')
    repo = os.environ['GITHUB_REPOSITORY']
    api = lambda endpoint: json.loads(subprocess.check_output(['gh','api','repos/'+repo+'/'+endpoint]))
    meta = api('actions/artifacts/'+str(pin['artifactId']))
    run = api('actions/runs/'+str(pin['runId']))
    repo_id = api('')['id']
    provider_matches(meta,pin,run,repo_id)
    dest = work/label; transport = work/(label+'.zip')
    write(work/(label+'-provider.json'),{'pin':pin,'artifact':meta,'run':run})
    with transport.open('xb') as stream:
        subprocess.run(['gh','api','repos/'+repo+'/actions/artifacts/'+str(pin['artifactId'])+'/zip'],
            stdout=stream, check=True)
    require(sha(transport)==pin['sha256'], 'Retained archive SHA256 differs: '+label)
    payload = entries(transport)
    dest.mkdir()
    for name,data in payload.items():
        p=dest/name; p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(data)
    return dest


def one(root, pattern):
    paths=list(root.rglob(pattern)); require(len(paths)==1, 'One '+pattern+' required')
    return paths[0]


def exact_jar(root, expected):
    paths=[p for p in root.rglob('*.jar') if sha(p)==expected]
    require(len(paths)==1, 'One exact retained JAR required: '+expected)
    return paths[0]


def execute(command, log, java=None):
    env = {k:v for k,v in os.environ.items() if k not in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')}
    if java:
        env['JAVA_HOME']=str(java); env['PATH']=str(java/'bin')+os.pathsep+env['PATH']
    command=list(map(str,command))
    with Path(log).open('xb') as stream:
        result=subprocess.run(command,cwd=ROOT,env=env,stdout=stream,stderr=subprocess.STDOUT)
    require(result.returncode==0, 'Producer failed; inspect '+str(log))
    return {'command':command,'exitCode':result.returncode,'log':ref(log)}


def home(major):
    key='JAVA_HOME_'+str(major)+'_X64'
    p=Path(os.environ[key]).resolve(); require((p/'bin/java').is_file(), key+' required')
    return p


def canonical(work, version, spec):
    # This is the normal root build; 26.2 configures only its genuine native
    # profile. It is never an alias for either legacy game.
    jars={role:ROOT/module_name/'build/libs'/filename for role,module_name,filename in [
        ('engine','engine-core','openallay-engine-core-'+version+'.jar'),
        ('sdk','extension-api','openallay-extension-api-0.4.0.jar'),
        ('rhino','runtime-rhino','openallay-rhino-2101.2.8-build.91.jar'),
        ('json-proof','runtime-json','openallay-runtime-json-'+version+'.jar')]}
    command=[ROOT/'gradlew','--max-workers=2','--stacktrace','-PminecraftTarget=26.2',
        '-PtestBundledExtensions=false',':engine-core:assemble',':runtime-json:assemble',':extension-api:assemble',':runtime-rhino:assemble']
    receipt=execute(command,work/'canonical.log',home(25))
    engine_payload=entries(jars['engine'])
    require(any(n.endswith('.class') for n in engine_payload), 'Canonical engine classes required')
    native=('net/minecraft/','net/minecraftforge/','net/fabricmc/','net/neoforged/','com/mojang/','org/lwjgl/')
    for name,data in engine_payload.items():
        if name.endswith('.class'):
            require(data[:4]==b'\xca\xfe\xba\xbe' and int.from_bytes(data[6:8],'big')<=61 and
                not name.startswith(native),'Canonical Java17 native-free engine required: '+name)
    receipt.update(sourceRevision=git('rev-parse','HEAD'),version=version,engine=ref(jars['engine']),
        engineEntries=inventory(engine_payload),canonical=True)
    # Source declarations are unchanged for these independent public components.
    # Normal root-produced bytes must equal the retained class/resource inventory,
    # excluding only versioned container metadata and legal resources.
    for role in ('sdk','rhino'):
        previous=next(a for a in spec['artifacts'] if a['role']==role)
        old=entries(previous['path']); new=entries(jars[role])
        old_owned={n:d for n,d in old.items() if n.endswith('.class') or n.startswith('META-INF/services/')}
        new_owned={n:d for n,d in new.items() if n.endswith('.class') or n.startswith('META-INF/services/')}
        require(old_owned==new_owned, 'SDK/Rhino canonical class/API bytes changed: '+role)
    return jars,receipt


def closure(work, version):
    base=retained(BASE,work,'base-closure')
    original=one(base,'closure-input.json'); spec=load(original)
    require(len(spec['artifacts'])==18 and len({r['role'] for r in spec['artifacts']})==18,
        'Exact retained 18-role closure required')
    for row in spec['artifacts']:
        p=original.parent/(row['role']+'.jar')
        require(sha(p)==row['sha256'], 'Retained constituent SHA256 differs: '+row['role'])
        row['path']=str(p)
    pins=load(ROOT/'native-builds/forge1122-component/builder-candidate-provider.json')
    require(pins['extensionSource']==BUILDER_SOURCE and pins['jarSha256']==BUILDER_SHA,
        'One universal accepted Builder source required')
    lock=load(ROOT/'distribution/extensions.lock.json')
    require(lock['source']['revision']==BUILDER_SOURCE and lock['version']==lock['openAllayApiVersion']=='0.4.0',
        'Current universal Builder lock differs')
    kept=retained(pins['provider'],work,'builder')
    builder=exact_jar(kept,BUILDER_SHA)
    current_builder=ROOT/'build/bundled-extensions/openallay-builder-universal-0.4.0.jar'
    if current_builder.is_file():
        require(sha(current_builder)==BUILDER_SHA, 'Central universal Builder bytes differ')
    jars,receipt=canonical(work,version,spec)
    for row in spec['artifacts']:
        if row['role']=='engine':
            row.update(path=str(jars['engine']),sha256=sha(jars['engine']),
                coordinate='dev.openallay:openallay-engine-core:'+version)
        elif row['role']=='builder':
            row.update(path=str(builder),sha256=BUILDER_SHA)
    spec['sourceRevision']=git('rev-parse','HEAD')
    resolution=write(work/'resolution.json',{'sourceRevision':spec['sourceRevision'],
        'runtimeCoordinates':sorted(r['coordinate'] for r in spec['artifacts'] if not r['role'].endswith('-proof'))})
    spec['resolution']=resolution
    spec_ref=write(work/'closure.json',spec)
    return spec,spec_ref,receipt,builder


def native16(work, closure_ref):
    helper=module('release16tooling',ROOT/'scripts/forge16165-engine-prerequisite/focused-tooling-acceptance.py')
    mappings=helper.mappings(work,work)
    mapping_ref=write(work/'mapping-inputs.json',{k:{'path':str(mappings/(k+'.txt' if k!='tsrg' else 'joined.tsrg')),
        'sha256':v} for k,v in helper.HASHES.items()})
    build=ROOT/'native-builds/forge16165'; mdk=work/'mdk.zip'
    helper.fetch(helper.MDK,mdk,2000000,sha1='3d95dac7c4f3ec7a0bdafed3f3c5cb6d284cec06')
    with zipfile.ZipFile(mdk) as z:
        for name in ('gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar'):
            p=build/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(name))
    (build/'gradlew').chmod(0o755)
    lock=work/'compiler-lock.json'
    request=write(work/'native-request.json',{'sourceRoot':str(ROOT),'sourceRevision':git('rev-parse','HEAD'),
        'closureSpec':closure_ref['path'],'sourceSelectionPlan':str(build/'source-selection.json'),
        'mappingInputs':mapping_ref['path'],'metadataAcceptance':str(work/'acceptance.properties'),
        'compilerArtifactLock':str(lock)})
    command=[build/'gradlew','--max-workers=2','--stacktrace','-p',build,'-PnativeBuildInputs='+request['path']]
    first=execute(command+['exportNativeToolingInputs','acceptNamespaceMetadata'],work/'native-inputs.log',home(17))
    proposed=build/'build/native-tooling/compiler-input-lock.proposed.json'
    lock.write_bytes(proposed.read_bytes())
    second=execute(command+['emitNativeBuildMetadata'],work/'native-build.log',home(17))
    metadata=load(build/'build/native-metadata/build.json')
    for name,rel in [('selectionSha256','namespace/source-selection.json'),('namespaceSha256','namespace/receipt.json'),
        ('apRefmapSha256','native-ap/openallay.refmap.json'),('apMappingsSha256','native-ap/mixins.tsrg'),
        ('compilerLockSha256','native-tooling/compiler-input-lock.proposed.json')]:
        require(sha(build/'build'/rel)==metadata[name], 'FG6 producer receipt binding: '+name)
    require(metadata['sourceRevision']==git('rev-parse','HEAD') and metadata['nativeCompilerRelease']==17 and
        metadata['closureSpecSha256']==closure_ref['sha256'] and
        sha(Path(metadata['reobf']['path']))==metadata['reobf']['sha256'],'Current normal FG6 producer required')
    return Path(metadata['reobf']['path']),{'commands':[first,second],'metadata':metadata}


def excluded(name):
    return name in ('META-INF/MANIFEST.MF','module-info.class','mcmod.info','META-INF/mods.toml') or \
        bool(re.fullmatch(r'META-INF/[^/]+\.(?:SF|RSA|DSA|EC)',name,re.I)) or \
        bool(re.fullmatch(r'META-INF/versions/[0-9]+/module-info.class',name))


def replace_owner(payload, previous, current, mapping):
    """Replace complete physical ownership, preserving every unrelated entry."""
    require(set(mapping)==set(previous), 'Owner mapping must cover every original physical input')
    result=dict(payload); changes=[]
    for name,target in mapping.items():
        if target is None:
            continue
        require(target in result, 'Old physical owner missing: '+name)
        if name.startswith('META-INF/services/'):
            require(set(previous[name].decode().splitlines()).issubset(set(result[target].decode().splitlines())), 'Real service contributor differs: '+name)
            old_lines=previous[name].decode().splitlines()
            kept=[x for x in result[target].decode().splitlines() if x not in old_lines]
            lines=kept+current.get(name,b'').decode().splitlines()
            result[target]=('\n'.join(dict.fromkeys(x for x in lines if x and not x.startswith('#')))+'\n').encode()
        else:
            require(result[target]==previous[name], 'Old physical owner differs: '+name)
            del result[target]
    for name,data in current.items():
        target=mapping.get(name)
        if name in mapping and target is None:
            continue
        if name not in mapping:
            if excluded(name):
                continue
            target='META-INF/licenses/engine/'+name.replace('/','_') if re.search(r'(?:LICENSE|NOTICE|COPYING)',name,re.I) else name
        if name.startswith('META-INF/services/') and target in result:
            # Service union was regenerated above from real old/new contributors.
            continue
        require(target not in result, 'Replacement owner collides: '+str(target))
        result[target]=data; changes.append({'input':name,'output':target,'sha256':digest(data)})
    untouched={n:digest(d) for n,d in payload.items() if n not in {p for p in mapping.values() if p is not None}}
    require(all(result[n]==payload[n] for n in untouched), 'Unrelated owner changed')
    return result,{'replaced':changes,'unchanged':untouched}


def native_mapping(previous):
    return {name:(None if excluded(name) else name) for name in previous}


def manifest_version(payload,version):
    old=payload['META-INF/MANIFEST.MF'].decode()
    lines=[]
    for line in old.replace('\r\n','\n').splitlines():
        if not line:break
        if line.startswith(' '):
            require(bool(lines), 'Invalid manifest fold');lines[-1]+=line[1:]
        else:lines.append(line)
    lines=[x for x in lines if not x.lower().startswith('implementation-version:')]
    payload['META-INF/MANIFEST.MF']=('\r\n'.join(lines+['Implementation-Version: '+version])+'\r\n\r\n').encode()


def distribution(payload,builder):
    lock=load(ROOT/'distribution/extensions.lock.json')
    require(sha(builder)==BUILDER_SHA, 'Universal Builder bytes differ')
    payload[RESOURCE]=builder.read_bytes()
    payload[PROVENANCE]=encoded({'source':{**lock['source'],'dirty':False,'pinned':True},
        'project':lock['project'],'version':lock['version'],'extensionId':lock['extensionId'],
        'openAllayApiVersion':lock['openAllayApiVersion'],'artifact':{'path':RESOURCE,'sha256':BUILDER_SHA}})


def prepared16(work,spec,native,builder,version):
    kept=retained(PRODUCT16,work,'accepted-product')
    oldjar=exact_jar(kept,PRODUCT16_SHA); payload=entries(oldjar)
    receipt=load(one(kept,'pack-receipt.json'))
    require(receipt['outputSha256']==PRODUCT16_SHA, 'Accepted16 product receipt binding')
    rows=receipt['source']['entries']
    require({r['name']:r['sha256'] for r in rows}==inventory(payload),'Accepted16 whole-entry custody')
    engine_rows={r['name']:r for r in rows if any(o['role']=='engine' for o in r['owners'])}
    accepted_engine=work/'base-closure'
    oldenginejar=exact_jar(accepted_engine,next(r['sha256'] for r in receipt['source']['originalArtifacts'] if r['role']=='engine'))
    oldengineinputs=entries(oldenginejar)
    oldengine={n:payload[n] for n in engine_rows}
    current=entries(next(r['path'] for r in spec['artifacts'] if r['role']=='engine'))
    mapping={n:n for n in oldengine}
    # Container/legal paths are mapped using the retained input owner, not guessed FQNs.
    for target,row in engine_rows.items():
        owners=[o for o in row['owners'] if o['role']=='engine']; require(len(owners)==1,'Sole engine row')
        name=owners[0]['name']
        if name!=target:
            oldengine[name]=oldengine.pop(target); mapping.pop(target);mapping[name]=target
    for name in current:
        if excluded(name) and name not in mapping:
            mapping[name]=None
    previous={n:(oldengineinputs[n] if n in oldengineinputs else current[n]) for n in mapping}
    for name in oldengineinputs:
        if name not in mapping:
            mapping[name]=None;previous[name]=oldengineinputs[name]
    payload,engine_custody=replace_owner(payload,previous,current,mapping)
    native_pin=dict(artifactId=11435042093,runId=37510699083,
        sourceRevision='19d09199cd7272d92e9e14758e8826a7b3c012df',
        sha256='714f8b1252f9c8891a7d348339677f30d546be0ca99a7f22c3f0d35f5794fa66')
    oldroot=retained(native_pin,work,'old-native16')
    oldnative=entries(exact_jar(oldroot,receipt['source']['nativeReobf']['sha256']))
    fresh=entries(native); mapping=native_mapping(oldnative)
    mapping['META-INF/mods.toml']='META-INF/mods.toml'
    for name in oldnative:
        if name in engine_rows and not name.startswith('META-INF/services/'):
            require(fresh.get(name)==oldnative[name], 'Shared native/engine owner differs')
            mapping[name]=None
    payload,native_custody=replace_owner(payload,oldnative,fresh,mapping)
    require(version in payload['META-INF/mods.toml'].decode(), 'Actual current native mods.toml version required')
    distribution(payload,builder);manifest_version(payload,version)
    product=work/'current-product.jar';archive(product,payload)
    return {'product':product}, {'historicalProvider':PRODUCT16,'engineCustody':engine_custody,'nativeCustody':native_custody}


def prepare(args):
    require(args.target == '1.16.5', 'Exact Forge16 release target required')
    version=source_version()
    require(args.version==version and args.family=='forge-'+args.target, 'Source version/family mismatch')
    suffix='.jar'
    require(args.output.name=='openallay-forge-'+args.target+'-'+version+suffix and
        args.receipt==Path(str(args.output)+'.packaging.json'), 'Exact catalog final output/receipt paths required')
    if args.plan:
        return {'target':args.target,'family':args.family,'version':version,
            'output':str(args.output),'receipt':str(args.receipt),'remoteOnly':True,
            'producer':'normal FG6/17',
            'consumer':'scripts/package-legacy-forge-release.py','gameExecuted':False}
    require(os.environ.get('GITHUB_ACTIONS')=='true','Remote runner only')
    source=git('rev-parse','HEAD');require(source==os.environ['GITHUB_SHA'],'Actual checkout source SHA required')
    require(not git('status','--porcelain','--untracked-files=all','--','engine-core','common','adapters','forge',
        'neoforge','runtime-json','runtime-rhino','extension-api','gradle.properties','scripts/prepare-legacy-forge-release.py'),
        'Clean committed canonical source required')
    require(not args.output.exists() and not args.receipt.exists(), 'Preserve existing final outputs')
    work=ROOT/'build/legacy-release-inputs'/args.target;work.mkdir(parents=True,exist_ok=False)
    spec,closure_ref,engine_receipt,builder=closure(work,version)
    native,native_receipt=native16(work,closure_ref)
    products,custody=prepared16(work,spec,native,builder,version)
    # Physical products are current source-produced components. Historical provider
    # identities remain separate and unchanged in this receipt.
    byrole={r['role']:r for r in spec['artifacts']}
    sqlite=entries(byrole['sqlite']['path'])
    sqlite_payload={n:d for n,d in sqlite.items() if (n.startswith('org/sqlite/') or n=='META-INF/services/java.sql.Driver')}
    shared={label:byrole[role]['sha256']
        for label,role in [('extension-api','sdk'),('runtime-rhino','rhino')]}
    provenance={'releaseSource':{'revision':source,'sourceRoot':str(ROOT),'version':version},
        'engine':engine_receipt,'native':native_receipt,'custody':custody,
        'builder':{'sourceRevision':BUILDER_SOURCE,'sha256':BUILDER_SHA,
            'provider':load(ROOT/'native-builds/forge1122-component/builder-candidate-provider.json')}}
    provider=write(work/'provider-receipt.json',{'version':version,
        'components':{role:{'sha256':sha(path),'entries':physical_inventory(entries(path))} for role,path in products.items()},
        'sqlite':{'artifactSha256':byrole['sqlite']['sha256'],'payloadSha256':digest(json.dumps(inventory(sqlite_payload),sort_keys=True,separators=(',',':')).encode())},
        'sharedRuntimes':shared,'provenance':provenance})
    request={'sourceRoot':str(ROOT),'version':version,'providerReceipt':provider}
    request['product']=ref(products['product'])
    request_ref=write(work/'REQUEST.json',request)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    command=[sys.executable,'-B',ROOT/'scripts/package-legacy-forge-release.py','--target',
        'forge16165','--inputs',request_ref['path'],
        '--output',args.output,'--receipt',args.receipt]
    execute(command,work/'package.log',home(17))
    return {'status':'prepared','sourceRevision':source,'request':request_ref,
        'product':ref(args.output),'receipt':ref(args.receipt),'gameExecuted':False}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--target',choices=['1.16.5'],required=True)
    p.add_argument('--family',required=True);p.add_argument('--version',required=True)
    p.add_argument('--output',type=Path,required=True);p.add_argument('--receipt',type=Path,required=True)
    p.add_argument('--plan',action='store_true')
    args=p.parse_args();args.output=args.output.resolve();args.receipt=args.receipt.resolve()
    print(json.dumps(prepare(args),indent=2))


if __name__=='__main__':
    main()
