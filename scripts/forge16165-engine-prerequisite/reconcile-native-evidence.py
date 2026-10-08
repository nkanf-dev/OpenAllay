#!/usr/bin/env python3
"""Reconcile original Forge36 native evidence offline. Never launches or compiles code."""
import argparse,hashlib,json
from pathlib import Path

def read(path):return json.loads(path.read_text())
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def one(root,name):
    values=list(root.rglob(name))
    if len(values)!=1:raise ValueError('Ambiguous original evidence: '+name)
    return values[0]
def require(value,message):
    if not value:raise ValueError(message)
def main():
    cli=argparse.ArgumentParser(description=__doc__)
    cli.add_argument('--prefix',required=True,type=Path)
    cli.add_argument('--tail',required=True,type=Path)
    cli.add_argument('--output',required=True,type=Path)
    args=cli.parse_args()
    require(not args.output.exists(),'Preserve an existing evidence receipt')
    prefix_file=one(args.prefix,'probe-receipt.json');tail_file=one(args.tail,'probe-receipt.json')
    require(sha(prefix_file)=='c667924ae750814a2b41495ffd447c50af0f67131e27b47a172ae0bce30c12ac','Original prefix receipt bytes differ')
    require(sha(tail_file)=='3f747bb7d22f80aca02dbb635a9a206fe9ff8ecd1368daa2cb535510f93f4d52','Original pending receipt bytes differ')
    prefix,tail=read(prefix_file),read(tail_file)
    first=['identity','engine-logging','bound-engine-json','json-trees-readers','engine-rhino-record-schema']
    pending=['identity','rhino-default-interface-java-adapter','existing-tool-envelope-copy','builder-descriptor-only']
    require(prefix['status']=='FAIL' and prefix['failureStage']==pending[1] and [s['stage'] for s in prefix['stages']]==first+[pending[1]],'Original failure chronology differs')
    require(all(s['status']=='PASS' for s in prefix['stages'][:5]) and prefix['stages'][5]['status']=='FAIL','Prefix operations did not pass')
    require(tail['status']=='PENDING_STAGES_PASS' and [s['stage'] for s in tail['stages']]==pending and all(s['status']=='PASS' for s in tail['stages']),'Pending native operations did not pass')
    packs=[read(one(root,'pack-receipt.json')) for root in [args.prefix,args.tail]]
    specs=[p['closure'] for p in packs]
    require(all(s['status']=='READY' and not s['errors'] for s in specs),'Complete input ownership did not pass')
    require({a['role']:a['sha256'] for a in specs[0]['artifacts']}=={a['role']:a['sha256'] for a in specs[1]['artifacts']},'Engine/runtime/component inputs changed')
    def payload(pack):return {e['name']:e['sha256'] for e in pack['entries'] if not e['name'].startswith('dev/openallay/forge36probe/')}
    require(payload(packs[0])==payload(packs[1]),'Non-probe final class/resource payload changed')
    identities=[p['stages'][0]['details'] for p in [prefix,tail]]
    launches=[read(one(root,'launch.json')) for root in [args.prefix,args.tail]]
    logs=[one(root,'client.log') for root in [args.prefix,args.tail]]
    for i,identity in enumerate(identities):
        require(identity['pid']==str(read(one([args.prefix,args.tail][i],'collector-receipt.json'))['clientPid']),'Native receipt PID differs')
        require(identity['javaVersion']=='17.0.18+8' and identity['thread']=='Render thread','Native Java/thread differs')
        cp={(x['path'],x['sha256']):x['coordinate'] for x in launches[i]['classpath']}
        for name,coordinate in [('com.google.gson.Gson','com.google.code.gson:gson:2.8.0'),('com.google.common.collect.ImmutableList','com.google.guava:guava:21.0'),('org.apache.logging.log4j.Logger','org.apache.logging.log4j:log4j-api:2.15.0')]:
            origin=identity[name]
            require(origin['originKind']=='official-host-file' and cp.get((origin['codeSource'],origin['archiveSha256']))==coordinate,'Original native host origin differs')
        probe=identity['dev.openallay.forge36probe.Probe']
        packed={e['name']:e for e in packs[i]['entries']}
        for name in ['dev.openallay.forge36probe.Probe','dev.openallay.script.RhinoJavascriptRuntime','dev.openallay.json.EngineJson','dev.latvian.mods.rhino.Context','dev.openallay.api.extension.OpenAllayExtension','dev.openallay.OpenAllayConstants','dev.openallay.logging.OpenAllayLogger']:
            origin=identity[name];proof=origin['modEntryProof'];entry=name.replace('.','/')+'.class'
            require(origin['originKind']=='forge-modjar' and origin['rawCodeSourceURL']=='modjar://openallay_engine_probe' and origin['loaderIdentity']==probe['loaderIdentity'] and origin['codeSource']==probe['codeSource'] and origin['archiveSha256']==packs[i]['outputSha256'],'Native sole mod identity differs')
            require(proof['classResourceSha256']==proof['archiveEntrySha256']==packed[entry]['sha256'],'Native class resource/JAR entry differs')
    log=logs[0].read_text(errors='replace')
    require(sha(logs[0])=='9d43647babe35f194afc075bc783941e48cb0631f9177ab89d61c8bc6d4fc5b9','Original prefix client log differs')
    for marker in ['OA36 ENGINE_LOGGING_INFO engine=OpenAllay value=brace-ok','OA36 ENGINE_LOGGING_WARN engine=OpenAllay value=brace-ok','OA36 ENGINE_LOGGING_ERROR engine=OpenAllay value=brace-ok','java.lang.IllegalStateException: OA36 ENGINE_LOGGING_THROWABLE']:
        require(marker in log,'Original logging output missing')
    stack=next((line for line in log.partition('java.lang.IllegalStateException: OA36 ENGINE_LOGGING_THROWABLE')[2].splitlines()[:8] if line.strip()),'')
    require('at dev.openallay.forge36probe.Probe$Client.run(' in stack,'Original logging exception stack missing')
    builder=tail['stages'][-1]['details']['dev.openallay.builder.BuilderExtension'];probe=identities[1]['dev.openallay.forge36probe.Probe'];entry='dev/openallay/builder/BuilderExtension.class'
    require(builder['classMajor']==52 and builder['archiveSha256']==packs[1]['outputSha256'] and builder['loaderIdentity']==probe['loaderIdentity'] and builder['codeSource']==probe['codeSource'] and builder['modEntryProof']['archiveEntrySha256']=={e['name']:e['sha256'] for e in packs[1]['entries']}[entry],'Builder descriptor input identity differs')
    outcome={'outcome':'ENGINE_PREREQUISITE_PASS','evidenceKind':'split-original-native-executions','fullNativeSupport':False,
        'prefix':{'run':37434990163,'source':'0170a2cb8cc4fd2a7eb2fe9ee2502b8f1d094eff','originalRunOutcome':'FAIL','receiptSha256':sha(prefix_file),'fatJarSha256':packs[0]['outputSha256'],'acceptedStages':first},
        'pending':{'run':37436299667,'source':'447257c951c4c845f50f1a522b3b27fdbe1a7bef','originalRunOutcome':'FAIL (collector scope error)','probeOutcome':tail['status'],'receiptSha256':sha(tail_file),'fatJarSha256':packs[1]['outputSha256'],'executedStages':pending},
        'scope':'Same engine/runtime/resources and official host inputs; changed probe belongs to fresh JavaAdapter stage, not retained successful prefix',
        'next':'Typed Forge1.16.5 native adapters required; Builder descriptor is not target admission'}
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(outcome,indent=2)+'\n');print(json.dumps(outcome))
if __name__=='__main__':main()
