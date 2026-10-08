#!/usr/bin/env python3
"""One complete physical inventory before flat merge; collect all roles/errors, no JVM."""
import argparse,hashlib,importlib.util,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def load(name,path):
    spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
def audit(request,output):
    rows=[];errors=[]
    for item in request['components']:
        row={'role':item['role'],'coordinate':item['coordinate'],'expectedSha256':item['sha256'],'path':item['path'],'classes':[],'services':[],'physicalAbove52':[]}
        try:
            path=Path(item['path']);raw=path.read_bytes();digest=hashlib.sha256(raw).hexdigest();row['actualSha256']=digest
            if digest!=item['sha256']:raise ValueError('Actual input hash differs')
            with zipfile.ZipFile(path) as z:
                names=[i.filename for i in z.infolist() if not i.is_dir()]
                if len(names)!=len(set(names)):raise ValueError('Duplicate archive entry')
                for name in names:
                    blob=z.read(name)
                    if name.endswith('.class'):
                        if len(blob)<8 or blob[:4]!=bytes.fromhex('cafebabe'):errors.append({'role':item['role'],'entry':name,'error':'Malformed class'});continue
                        fact={'entry':name,'major':int.from_bytes(blob[6:8],'big'),'sha256':hashlib.sha256(blob).hexdigest()};row['classes'].append(fact)
                        if fact['major']>52:row['physicalAbove52'].append(fact)
                    elif name.startswith('META-INF/services/'):row['services'].append({'entry':name,'sha256':hashlib.sha256(blob).hexdigest(),'providers':blob.decode().splitlines()})
            if item['role']=='sqlite':
                entries,proof=load('sqlite',ROOT/'scripts/sqlite-runtime-role.py').project_sqlite_runtime(path,item['coordinate']);row['roleProjection']=proof
            elif item['role']=='mixin':
                entries,proof=load('mixin',ROOT/'scripts/mixin-launchwrapper-role.py').project_mixin_launchwrapper(path,item['coordinate']);row['roleProjection']=proof
            elif row['physicalAbove52']:errors.extend({'role':item['role'],'entry':f['entry'],'major':f['major'],'error':'Unapproved physical class above Java8'} for f in row['physicalAbove52'])
        except Exception as failure:errors.append({'role':item['role'],'error':str(failure)})
        rows.append(row)
    if len(rows)!=11 or len({r['role'] for r in rows})!=11:errors.append({'error':'Actual resolved runtime must have exact eleven single-owner roles'})
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps({'artifacts':rows,'errors':errors,'accepted':not errors,'scope':'complete actual resolved eleven-JAR preflight with explicit consumer projections'},indent=2)+'\n')
    if errors:raise ValueError('Whole runtime preflight found '+str(len(errors))+' failures; all original roles recorded')
def main():
    p=argparse.ArgumentParser();p.add_argument('--inputs',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();audit(json.loads(a.inputs.read_text()),a.output)
if __name__=='__main__':main()
