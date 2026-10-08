#!/usr/bin/env python3
"""Project genuine canonical OpenAllay JSON into Minecraft1.12 UTF8 .lang resources."""
import hashlib,json,re
LOCALES=('en_us','zh_cn')
def unique(pairs):
    result={}
    for key,value in pairs:
        if key in result:raise ValueError('Duplicate canonical language key '+key)
        result[key]=value
    return result
def project_legacy_languages(contents,owners):
    proofs=[]
    for locale in LOCALES:
        source='assets/openallay/lang/'+locale+'.json';target='assets/openallay/lang/'+locale+'.lang'
        if source not in contents or target in contents:raise ValueError('One canonical JSON language owner required; no competing legacy resource')
        raw=contents[source];values=json.loads(raw.decode('utf-8'),object_pairs_hook=unique)
        if not isinstance(values,dict) or not values:raise ValueError('Canonical language object required')
        lines=[]
        for key,value in values.items():
            if not isinstance(key,str) or not isinstance(value,str):raise ValueError('Canonical language values must be strings')
            if not key or key.startswith('#') or '=' in key or any(c in key+value for c in ('\n','\r','\x00')):
                raise ValueError('Canonical language entry cannot be represented by native one-line .lang grammar: '+key)
            if re.search(r'%(\d+\$)?[\d.]*[df]',value):raise ValueError('Native1.12 numeric-format substitution needs explicit source acceptance: '+key)
            # Vanilla .lang uses raw UTF8 lines and the first equals; no invented properties escape/unescape.
            lines.append(key+'='+value)
        projected=('\n'.join(lines)+'\n').encode('utf-8')
        readback={line.split('=',1)[0]:line.split('=',1)[1] for line in projected.decode('utf-8').splitlines() if line and not line.startswith('#')}
        if readback!=values:raise ValueError('Legacy language all-key roundtrip differs')
        contents[target]=projected;owners[target]='derived-legacy-language:'+owners[source]
        proofs.append({'locale':locale,'input':source,'inputOwner':owners[source],'inputSha256':hashlib.sha256(raw).hexdigest(),
            'output':target,'outputSha256':hashlib.sha256(projected).hexdigest(),'keyCount':len(values),'allKeysRoundtripExact':True,
            'originalJsonPreserved':True,'grammar':'raw UTF8 key=value split at first equals; no invented escape processing'})
    return proofs
