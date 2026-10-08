#!/usr/bin/env python3
"""Read-only projection of exact neutral Forge1122 owners for parse-only language conversion.

The normal Gradle source-selection receipt remains authoritative. This projection
uses the same ordered roots, exact target exclusions, profiles, and configured
Mixin ownership. It invokes no FG, dependency resolution, compiler or game.
"""
import hashlib
import json
from pathlib import Path
import re


def sha(data):return hashlib.sha256(data).hexdigest()

def selected_native_units(project):
    project=Path(project).resolve()
    ancestors=json.loads((project/'native-builds/forge16165/source-selection.json').read_text())['sourceFamilies']+['1.16.5','1.12.2']
    roots={};java={};resources={}
    for owner,module in [('common','common'),('adapter','adapters/minecraft'),('fml','neoforge')]:
        for kind,selected in [('java',java),('resources',resources)]:
            ordered=[project/module/('src/main/'+kind)]+[project/module/('src/targets/'+v+'/'+kind) for v in ancestors]
            if owner=='fml':ordered += [project/'forge'/('src/targets/'+v+'/'+kind) for v in ancestors]
            roots[owner+':'+kind]=[str(path) for path in ordered]
            for root in ordered:
                if not root.exists():continue
                for path in sorted(root.rglob('*')):
                    if not path.is_file() or kind=='java' and path.suffix!='.java':continue
                    name=path.relative_to(root).as_posix()
                    if owner=='fml' and kind=='resources' and name=='META-INF/neoforge.mods.toml':continue
                    selected[(owner,name)]=path
    raw=dict(java);profiles=[]
    for name in ['source-retirements.json','core-compile-profile.json']:
        record=json.loads((project/'native-builds/forge1122-census'/name).read_text());profiles.append({'path':name,'sha256':sha((project/'native-builds/forge1122-census'/name).read_bytes())})
        for item in record['java']:
            path=raw.get((item['owner'],item['logicalPath']))
            if path is None or path.relative_to(project).as_posix()!=item['origin'] or sha(path.read_bytes())!=item['sha256']:
                raise ValueError('Exact profile source owner/hash differs: '+item['logicalPath'])
            java.pop((item['owner'],item['logicalPath']),None)
    configured=set();packages=set();configs=[]
    for (owner,name),path in resources.items():
        if name.endswith('.mixins.json'):
            config=json.loads(path.read_text());packages.add(config['package']);configs.append({'path':path.relative_to(project).as_posix(),'sha256':sha(path.read_bytes())})
            for scope in ['client','server','mixins']:
                for name in config.get(scope,[]):
                    identity=config['package']+'.'+name
                    if identity in configured:raise ValueError('Competing configured Mixin owner '+identity)
                    configured.add(identity)
    fqns={}
    for key,path in java.items():
        text=path.read_text();match=re.search(r'(?m)^\s*package\s+([\w.$]+)\s*;',text);package=match.group(1) if match else ''
        identity=(package+'.' if package else '')+path.stem
        if identity in fqns:raise ValueError('Competing native source FQN '+identity)
        if (package.replace('.','/')+'/' if package else '')+path.name!=key[1]:raise ValueError('Misplaced Java source '+str(path))
        fqns[identity]=(key,path,text,package)
    for identity in configured:
        owner=fqns.get(identity)
        if owner is None or not re.search(r'(?m)^\s*@(?:org\.spongepowered\.asm\.mixin\.)?Mixin\s*\(',owner[2]):
            raise ValueError('Configured actual Mixin source missing '+identity)
    for identity,(key,path,text,package) in list(fqns.items()):
        if any(package==reserved or package.startswith(reserved+'.') for reserved in packages) and identity not in configured:
            java.pop(key,None)
    old=json.loads((project/'native-builds/forge16165/source-selection.json').read_text())
    actual=set(old['actualMcpUnits'])
    rows=[]
    for (owner,logical),path in sorted(java.items()):
        relative=path.relative_to(project).as_posix()
        rows.append({'owner':owner,'logicalPath':logical,'path':str(path),'origin':relative,
            'sha256':sha(path.read_bytes()),'bytes':path.stat().st_size,
            'mode':'ACTUAL_MCP' if '/src/targets/1.12.2/' in relative or relative in actual else 'CANONICAL_SEMANTIC'})
    return rows,{'kind':'read-only parse-source projection; normal Gradle receipt authoritative','roots':roots,
        'profiles':profiles,'mixinConfigs':configs,'neutralSelectorSha256':sha((project/'gradle/minecraft-source-selection.gradle').read_bytes()),
        'curatedClassesSha256':sha((project/'native-builds/forge1122-census/curated-classes.tsv').read_bytes()),'java':rows}
