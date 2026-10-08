"""Authenticate the one accepted universal Builder publication; no source compilation."""
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import tempfile
import zipfile

ROOT=Path(__file__).resolve().parents[1]
BUILDER_SHA="bf8cfff9b82f84914aa1c84173d531f2256f537215a66a8d2057adc504632345"
SOURCE="6e977110cbe8e0ca0b39c012f0cdfc10bafffef2"


def require(ok,message):
    if not ok:raise ValueError(message)


def sha(raw):return hashlib.sha256(raw).hexdigest()


def validate_provider(pin,meta,run,proof,repo):
    p=pin['provider']
    require(type(meta['size_in_bytes']) is int and 0<meta['size_in_bytes']<=10*1024*1024, 'Canonical Builder archive size bound')
    require(meta['id']==p['artifactId'] and meta['expired'] is False and meta['digest']=='sha256:'+p['sha256'] and
            meta['workflow_run']['id']==p['runId'] and meta['workflow_run']['head_sha']==p['sourceRevision'], 'Canonical Builder archive identity differs')
    require(run['id']==p['runId'] and run['head_sha']==p['sourceRevision'] and run['status']=='completed' and
            run['event']=='workflow_dispatch' and run['path']=='.github/workflows/minecraft-native.yml' and
            run['repository']['full_name']==repo and run['head_repository']['full_name']==repo, 'Canonical Builder actual producer identity differs')
    require(proof['source']==SOURCE and proof['coreRunnerSource']==p['sourceRevision'] and proof['jarSha256']==BUILDER_SHA and
            proof['retainedSdkSha256']=='53fffa91059247a6f191f6ed77d1e7e74ac78318d0122c9ff503e2fcde18590a' and
            proof['sdkRebuilt'] is False and proof['nativeExecuted'] is False, 'Canonical Builder original source/SDK custody differs')


def canonical_bytes(root=ROOT):
    pin=json.loads((root/'distribution/builder-candidate-provider.json').read_text())
    require(pin=={'provider':{'artifactId':11447976342,'runId':37538595660,'sourceRevision':'89d34cf3b5118a5cc555e4802f094af667ded0d1','sha256':'6fbbd89f669fef2db5c145f9a994534da1eef7ee6c1d7dddab05d8be95d2b41a'},'jarSha256':BUILDER_SHA,'extensionSource':SOURCE}, 'Exact canonical Builder provider pin fields required')
    lock=json.loads((root/'distribution/extensions.lock.json').read_text())
    require(pin['jarSha256']==BUILDER_SHA and pin['extensionSource']==SOURCE==lock['source']['revision'], 'One exact accepted Builder source/provider required')
    cache=root/'build/canonical-builder-provider'/BUILDER_SHA
    artifact=cache/'builder.jar'
    if artifact.is_file():
        raw=artifact.read_bytes();require(sha(raw)==BUILDER_SHA,'Canonical Builder cache bytes differ')
        proof=json.loads((cache/'provider.json').read_text())
        validate_provider(pin,proof['artifact'],proof['run'],proof['originalReceipt'],os.environ['GITHUB_REPOSITORY'])
        require(proof['pin']==pin,'Canonical Builder cache authority changed')
        return raw,pin
    require(os.environ.get('GITHUB_ACTIONS')=='true','Canonical Builder retained provider download runs remotely only')
    repo=os.environ['GITHUB_REPOSITORY'];p=pin['provider']
    api=lambda endpoint:json.loads(subprocess.check_output(['gh','api','repos/'+repo+'/'+endpoint]))
    meta=api('actions/artifacts/'+str(p['artifactId']));run=api('actions/runs/'+str(p['runId']))
    require(type(meta['size_in_bytes']) is int and 0<meta['size_in_bytes']<=10*1024*1024, 'Canonical Builder archive size bound')
    cache.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='canonical-builder-',dir=os.environ['RUNNER_TEMP']) as temporary:
        archive=Path(temporary)/'provider.zip'
        with archive.open('xb') as stream:subprocess.run(['gh','api','repos/'+repo+'/actions/artifacts/'+str(p['artifactId'])+'/zip'],stdout=stream,check=True)
        require(sha(archive.read_bytes())==p['sha256'],'Canonical Builder original archive bytes differ')
        with zipfile.ZipFile(archive) as z:
            require(len(z.namelist())==len(set(z.namelist())) and len(z.infolist())<=100 and sum(i.file_size for i in z.infolist())<=20*1024*1024 and z.testzip() is None,'Ambiguous or oversized canonical provider archive')
            raw_candidates=[];custody=[]
            for info in z.infolist():
                name=info.filename;parts=PurePosixPath(name)
                require(not parts.is_absolute() and '..' not in parts.parts and '\\' not in name and ':' not in name and
                        ((info.external_attr>>16)&0o170000)!=0o120000,'Unsafe canonical Builder provider member')
                if name.endswith('.jar'):
                    raw=z.read(info)
                    if sha(raw)==BUILDER_SHA:raw_candidates.append(raw)
                elif name.endswith('builder-candidate.json'):custody.append(json.loads(z.read(info)))
        require(len(raw_candidates)==1 and len(custody)==1,'Exact sole canonical Builder/provider receipt required')
        raw=raw_candidates[0];proof=custody[0]
        validate_provider(pin,meta,run,proof,repo)
        cache.mkdir(exist_ok=False);artifact.write_bytes(raw);(cache/'provider.json').write_text(json.dumps({'pin':pin,'artifact':meta,'run':run,'originalReceipt':proof},indent=2)+'\n')
    return raw,pin


def stage(root=ROOT):
    from importlib.util import spec_from_file_location,module_from_spec
    raw,pin=canonical_bytes(root)
    spec=spec_from_file_location('canonical_builder_verifier',root/'scripts/verify-bundled-extensions.py')
    verifier=module_from_spec(spec);spec.loader.exec_module(verifier)
    lock=verifier.prepare.load_manifest(root/'distribution/extensions.lock.json')
    verifier.verify_universal(raw,lock,additional_target_pairs=frozenset({('forge','1.12.2'),('forge','1.16.5')}))
    output=root/'build/distribution/bundled-extension-resources';path=output/verifier.resource_path(lock)
    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    value={'source':{**lock['source'],'dirty':False,'pinned':True},
           **{key:lock[key] for key in ('project','version','extensionId','openAllayApiVersion')},
           'artifact':{'path':verifier.resource_path(lock),'sha256':BUILDER_SHA}}
    (output/verifier.PROVENANCE).write_text(json.dumps(value,indent=2,sort_keys=True)+'\n')
    return {'canonicalBuilderSha256':BUILDER_SHA,'provider':pin,'sourceCompiled':False}


if __name__=='__main__':print(json.dumps(stage()))
