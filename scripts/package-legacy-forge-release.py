#!/usr/bin/env python3
"""Release-owned stock Forge player packaging, stdlib only.

Forge 1.16.5 product compilation belongs to prepare-legacy-forge-release.py.
The offline verifier consumes the packaging sidecar and checks complete custody.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import tomllib
import zipfile

VERSION = '0.4.4'


def require(condition, message):
    if not condition: raise ValueError(message)


def sha(data): return hashlib.sha256(data).hexdigest()

def encoded(value): return (json.dumps(value, sort_keys=True, indent=2) + '\n').encode()

def exact(value, fields, label):
    require(isinstance(value, dict) and set(value) == set(fields), 'Exact ' + label + ' fields required')


def pairs(items):
    result = {}
    for key, value in items:
        require(key not in result, 'Duplicate JSON key: ' + key); result[key] = value
    return result


def load(path): return json.loads(Path(path).read_text(), object_pairs_hook=pairs)


def safe_name(name):
    require(isinstance(name, str) and name and '\\' not in name and '\x00' not in name,
            'Unsafe archive name')
    path = PurePosixPath(name)
    require(not path.is_absolute() and all(p not in ('', '.', '..') for p in name.split('/')),
            'Archive escape: ' + name)


def archive(raw):
    result = {}; folded = set()
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        for item in z.infolist():
            if item.is_dir(): continue
            safe_name(item.filename)
            require(item.filename.casefold() not in folded, 'Duplicate/case-colliding archive entry')
            require((item.external_attr >> 16) & 0o170000 != 0o120000, 'Archive symlink refused')
            folded.add(item.filename.casefold()); result[item.filename] = z.read(item)
    require(result, 'Empty archive refused')
    return result


def inventory(raw):
    result = {}
    for name, data in archive(raw).items():
        item = {'sha256': sha(data), 'bytes': len(data), 'major': None}
        if name.endswith('.class'):
            require(len(data) >= 10 and data[:4] == b'\xca\xfe\xba\xbe', 'Invalid physical class: ' + name)
            item['major'] = int.from_bytes(data[6:8], 'big')
            require(45 <= item['major'] <= 61, 'Physical class exceeds Java17: ' + name)
        result[name] = item
    return result


def check_inventory(raw, expected):
    require(inventory(raw) == expected, 'Complete physical entry custody differs')


def reference(record):
    exact(record, ('path', 'sha256'), 'file reference')
    path = Path(record['path'])
    require(path.is_absolute() and path.is_file() and not path.is_symlink(), 'Ordinary absolute input file required')
    require(sha(path.read_bytes()) == record['sha256'], 'Input checksum differs: ' + str(path))
    return path


def write_new(path, data):
    path = Path(path)
    with path.open('xb') as stream:
        stream.write(data); stream.flush(); os.fsync(stream.fileno())


def require_remote(environment):
    require(environment.get('GITHUB_ACTIONS') == 'true', 'Remote packaging only')


def mod_version(raw, target, version):
    entries = archive(raw)
    require(target == 'forge16165', 'Exact Forge16 target required')
    metadata = tomllib.loads(entries['META-INF/mods.toml'].decode())
    mods = [m for m in metadata.get('mods', []) if m.get('modId') == 'openallay']
    require(len(mods) == 1 and mods[0].get('version') == version,
            'Actual OpenAllay loader metadata must equal release version')


def product_scan(raw, role):
    entries=archive(raw); inventory(raw)
    require(any(name.endswith('.class') for name in entries), 'Real product classes required')
    for name, data in entries.items():
        require(not name.startswith(('java/','net/minecraft/','net/minecraftforge/','dev/openallay/forge36probe/')),
                'Product cannot supply host/game alias: ' + name)
        require(not re.search(r'META-INF/[^/]+\.(SF|RSA|DSA)$',name,re.I), 'Stale product signature')
        if name.startswith('META-INF/services/'):
            for provider in data.decode().splitlines():
                provider=provider.split('#',1)[0].strip()
                if provider: require(provider.replace('.','/')+'.class' in entries, 'Service provider absent: ' + provider)
    if role == 'product':
        require(any('LICENSE' in name.upper() for name in entries), 'Product license required')
        require(any(name.startswith('META-INF/openallay/bundled-extensions/') and name.endswith('.jar') for name in entries), 'Raw bundled Builder required')
    return entries


def provider_check(proof, version, payloads):
    require(proof.get('version')==version and isinstance(proof.get('provenance'),dict)
            and proof['provenance'], 'Release-owned provider/native provenance required')
    require(set(proof['components'])==set(payloads), 'Exact provider component roles')
    for role, raw in payloads.items():
        record=proof['components'][role]
        require(record['sha256']==sha(raw), 'Provider component checksum differs: '+role)
        check_inventory(raw,record['entries']);product_scan(raw,role)
    require(set(proof['sharedRuntimes'])=={'extension-api','runtime-rhino'}, 'Shared runtime provider identities required')
    for digest in [*proof['sharedRuntimes'].values(),*proof['sqlite'].values()]:
        require(isinstance(digest,str) and re.fullmatch(r'[0-9a-f]{64}',digest), 'Provider payload digest required')


def provenance_check(proof, root, version):
    value=proof['provenance']
    exact(value,('releaseSource','engine','native','custody','builder'),'release provenance')
    source=value['releaseSource']
    exact(source,('revision','sourceRoot','version'),'release source provenance')
    require(source['version']==version and re.fullmatch(r'[0-9a-f]{40}',source['revision'])
            and Path(source['sourceRoot']).is_absolute(), 'Actual release revision/version required')
    actual=subprocess.run(['git','-C',str(root),'rev-parse','HEAD'],capture_output=True,text=True,check=True).stdout.strip()
    require(actual==source['revision'], 'Packaging must verify actual release source revision')
    engine=value['engine']
    require(engine['canonical'] is True and engine['exitCode']==0 and engine['version']==version
            and engine['sourceRevision']==source['revision'] and engine['engineEntries'],
            'Current canonical engine/source compile proof required')
    require(engine['engine']['sha256'] and re.fullmatch(r'[0-9a-f]{64}',engine['engine']['sha256']),
            'Actual canonical engine archive hash required')
    native=value['native']
    require(native['commands'] and all(record['exitCode']==0 and record['command'] for record in native['commands'])
            and isinstance(native['metadata'],dict) and native['metadata'],
            'Actual successful native producer commands/normal Forge metadata required')
    custody=value['custody']
    historical=custody['historicalProvider']
    require(historical['runId']>0 and historical['artifactId']>0
            and re.fullmatch(r'[0-9a-f]{64}',historical['sha256'])
            and re.fullmatch(r'[0-9a-f]{40}',historical['sourceRevision']),
            'Immutable historical provider identity required')
    for role in ('engineCustody','nativeCustody'):
        record=custody[role]
        require(isinstance(record['replaced'],list) and isinstance(record['unchanged'],dict),
                'Complete changed/unchanged owner custody required: '+role)
    builder=value['builder']
    require(re.fullmatch(r'[0-9a-f]{40}',builder['sourceRevision'])
            and re.fullmatch(r'[0-9a-f]{64}',builder['sha256']) and builder['provider'],
            'Pinned raw Builder actual producer required')


def build_packet(request, target):
    version=request['version'];root=Path(request['sourceRoot'])
    require(version==VERSION and re.search(r'(?m)^version\s*=\s*'+re.escape(version)+r'\s*$',(root/'gradle.properties').read_text()), 'Actual current source release version required')
    proof=load(reference(request['providerReceipt']));provenance_check(proof,root,version)
    require(target == 'forge16165', 'Exact Forge16 target required')
    exact(request,('sourceRoot','version','product','providerReceipt'),'Forge16 request')
    raw=reference(request['product']).read_bytes();provider_check(proof,version,{'product':raw})
    mod_version(raw,target,version)
    return raw,{'provider':proof,'helperBuild':None}


def validate_family(family, target, root):
    require(target == 'forge16165', 'Exact Forge16 target identity')
    identity = 'forge-1.16.5'
    if isinstance(family,dict):
        catalog=load(Path(root)/'gradle/minecraft-artifacts.json')
        rows=[record for record in catalog['acceptedFamilies'] if record['id']==identity]
        require(len(rows)==1 and family==rows[0], 'Exact source catalog Forge16 family required')
        require(family['buildTarget']=='1.16.5' and family['supportedTargets']==['1.16.5']
                and family['loader']=='forge' and family['artifactKind']=='jar'
                and family['packagingRecipe']=='forge-flat'
                and family['publicationChannels']==['github','modrinth'],
                'Actual Forge16 target/recipe/kind/channels differ')
    else:
        require(family in (target,identity), 'Exact internal target identity required')
    return identity


def verify_release(path, family, release_version, root):
    """Offline strict custody check; canonical engine/Builder parity belongs to caller."""
    path=Path(path);raw=path.read_bytes();receipt=load(str(path)+'.packaging.json')
    require(re.search(r'(?m)^version\s*=\s*'+re.escape(release_version)+r'\s*$',(Path(root)/'gradle.properties').read_text()), 'Actual release source version differs')
    require(receipt['version']==release_version==VERSION and receipt['outputSha256']==sha(raw), 'Release/package identity differs')
    check_inventory(raw,receipt['entries']);target=receipt['target']
    validate_family(family,target,root)
    require(receipt['helperBuild'] is None, 'Forge16 packaging has no player helper build')
    payloads={'product':raw};mod_version(raw,target,release_version);core=raw
    provider_check(receipt['provider'],release_version,payloads)
    provenance_check(receipt['provider'],root,release_version)
    return {'coreBytes':core,'sqlite':receipt['provider']['sqlite'],'sharedRuntimes':receipt['provider']['sharedRuntimes']}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--target',choices=('forge16165',),required=True)
    p.add_argument('--inputs',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--receipt',type=Path,required=True)
    a=p.parse_args();require_remote(os.environ);request=load(a.inputs)
    outputs=[a.output.absolute(),a.receipt.absolute()]
    require(len(set(outputs))==2 and str(a.receipt)==str(a.output)+'.packaging.json', 'Output sidecar path must be output+.packaging.json')
    require(all(not p.exists() and not p.is_symlink() and p.parent.is_dir() for p in outputs),'New output files in existing directories required')
    require(all(not p.resolve().is_relative_to(Path(request['sourceRoot']).resolve()) or 'build' in p.parts for p in outputs), 'Do not replace source files')
    raw,details=build_packet(request,a.target)
    result={'target':a.target,'version':request['version'],'outputSha256':sha(raw),'entries':inventory(raw),**details,
            'productClassesRecompiled':False,'gameExecuted':False,'playerConfigurationIncluded':False}
    # Validate private staged bytes before exclusive publication. Each final is
    # linked without overwrite. Roll back only files whose inode we created.
    stage=a.output.parent/('.'+a.output.name+'.stage-'+os.urandom(12).hex())
    stage_receipt=Path(str(stage)+'.packaging.json');created=[]
    try:
        write_new(stage,raw);write_new(stage_receipt,encoded(result))
        verify_release(stage,a.target,request['version'],request['sourceRoot'])
        for source,destination in [(stage,a.output),(stage_receipt,a.receipt)]:
            os.link(source,destination);st=source.stat();created.append((destination,st.st_dev,st.st_ino))
    except BaseException:
        for path,device,inode in reversed(created):
            st=path.lstat()
            if (st.st_dev,st.st_ino)==(device,inode) and not path.is_symlink():path.unlink()
        raise
    finally:
        for path in (stage,stage_receipt):
            if path.is_file() and not path.is_symlink():path.unlink()
    print('PACKED '+a.target+' '+sha(raw))

if __name__=='__main__':
    try: main()
    except (ValueError,OSError,KeyError,subprocess.CalledProcessError,zipfile.BadZipFile) as error:
        print('STOP: '+str(error),file=sys.stderr);sys.exit(2)
