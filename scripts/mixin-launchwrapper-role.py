#!/usr/bin/env python3
"""Pinned genuine Mixin LaunchWrapper consumer projection; original publication remains intact."""
import hashlib,json,struct,zipfile
from pathlib import Path
POLICY=Path(__file__).with_name('mixin-launchwrapper-role-policy.json')
COORDINATE='org.spongepowered:mixin:0.8.5'
UPSTREAM_SHA256='ca15a907e4d1f6b38cefed51d7df96a83347dcfb0bc8ced53111519e991d887d'
EXCLUDED={'org/spongepowered/asm/service/modlauncher/ModLauncherClassTracker.class', 'org/spongepowered/asm/service/modlauncher/Blackboard$Key.class', 'module-info.class', 'org/spongepowered/asm/launch/platform/container/ContainerHandleModLauncherEx$SecureJarResource.class', 'org/spongepowered/asm/service/modlauncher/MixinServiceModLauncher.class', 'org/spongepowered/asm/launch/platform/container/ContainerHandleModLauncherEx.class', 'org/spongepowered/asm/launch/MixinLaunchPlugin.class', 'org/spongepowered/asm/launch/platform/container/ContainerHandleModLauncher$Resource.class', 'org/spongepowered/asm/launch/MixinTransformationServiceAbstract.class', 'org/spongepowered/asm/service/modlauncher/MixinServiceModLauncherBootstrap.class', 'org/spongepowered/asm/launch/Phases.class', 'org/spongepowered/asm/service/modlauncher/ModLauncherClassProvider.class', 'org/spongepowered/asm/service/modlauncher/LoggerAdapterLog4j2.class', 'META-INF/services/cpw.mods.modlauncher.api.ITransformationService', 'org/spongepowered/asm/launch/platform/MixinPlatformAgentMinecraftForge.class', 'org/spongepowered/asm/service/modlauncher/MixinTransformationHandler.class', 'org/spongepowered/asm/launch/MixinLaunchPluginLegacy.class', 'org/spongepowered/asm/launch/MixinTransformationService.class', 'org/spongepowered/asm/service/modlauncher/Blackboard.class', 'org/spongepowered/asm/service/modlauncher/ModLauncherAuditTrail.class', 'org/spongepowered/asm/launch/platform/container/ContainerHandleModLauncher.class', 'org/spongepowered/asm/launch/IClassProcessor.class', 'META-INF/services/cpw.mods.modlauncher.serviceapi.ILaunchPluginService', 'org/spongepowered/asm/launch/MixinTransformationServiceLegacy.class'}
SERVICE_PROVIDERS={'META-INF/services/org.spongepowered.asm.service.IGlobalPropertyService': ['org.spongepowered.asm.service.mojang.Blackboard'], 'META-INF/services/org.spongepowered.asm.service.IMixinService': ['org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper'], 'META-INF/services/org.spongepowered.asm.service.IMixinServiceBootstrap': ['org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapperBootstrap']}

def sha(blob):return hashlib.sha256(blob).hexdigest()
def class_refs(data):
    offset=8;count=int.from_bytes(data[offset:offset+2],'big');offset+=2;pool=[None]*count;index=1
    while index<count:
        tag=data[offset];offset+=1
        if tag==1:
            size=int.from_bytes(data[offset:offset+2],'big');offset+=2;pool[index]=(tag,data[offset:offset+size].decode('utf-8',errors='replace'));offset+=size
        elif tag in (3,4):offset+=4
        elif tag in (5,6):offset+=8;index+=1
        elif tag in (7,8,16,19,20):pool[index]=(tag,int.from_bytes(data[offset:offset+2],'big'));offset+=2
        elif tag in (9,10,11,12,17,18):offset+=4
        elif tag==15:offset+=3
        else:raise ValueError('Unknown authentic class constant tag '+str(tag))
        index+=1
    return [pool[p[1]][1] for p in pool if p and p[0]==7]
def project_mixin_launchwrapper(archive,coordinate,policy_path=POLICY):
    if coordinate!=COORDINATE:raise ValueError('Exact genuine unclassified Mixin runtime coordinate required')
    path=Path(archive);raw=path.read_bytes();policy=json.loads(Path(policy_path).read_text())
    if sha(raw)!=UPSTREAM_SHA256 or policy['coordinate']!=COORDINATE or policy['upstream']['sha256']!=UPSTREAM_SHA256 or len(raw)!=policy['upstream']['bytes']:raise ValueError('Original Mixin publication identity differs')
    expected={r['path']:r for r in policy['allEntries']};omitted={r['path']:r for r in policy['excludedEntries']};services={r['path']:r for r in policy['serviceRewrites']}
    if set(omitted)!=EXCLUDED or {n:r['gameProviders'] for n,r in services.items()}!=SERVICE_PROVIDERS or len(expected)!=len(policy['allEntries']):raise ValueError('Fixed consumer-role policy shape differs')
    with zipfile.ZipFile(path) as z:
        names=[i.filename for i in z.infolist() if not i.is_dir()]
        if len(names)!=len(set(names)) or set(names)!=set(expected):raise ValueError('Unknown/missing Mixin publication entry')
        entries={name:z.read(name) for name in names}
    for name,blob in entries.items():
        row=expected[name]
        if len(blob)!=row['bytes'] or sha(blob)!=row['sha256']:raise ValueError('Exact Mixin entry changed '+name)
    result={};cut_receipts=[];rewrite_receipts=[];preserved=[];classes=[]
    excluded_classes={n[:-6] for n in omitted if n.endswith('.class')}
    for name,blob in entries.items():
        if name in omitted:
            if sha(blob)!=omitted[name]['sha256']:raise ValueError('Role entry prehash differs')
            cut_receipts.append(omitted[name]);continue
        if name in services:
            row=services[name]
            providers=[line.strip() for line in blob.decode().splitlines() if line.strip() and not line.lstrip().startswith('#')]
            if providers!=row['originalProviders'] or sha(blob)!=row['preSha256']:raise ValueError('Service provider preimage differs')
            after=('\n'.join(row['gameProviders'])+'\n').encode()
            if sha(after)!=row['postSha256'] or len(after)!=row['postBytes']:raise ValueError('Fixed LaunchWrapper service result differs')
            result[name]=after;rewrite_receipts.append(row);continue
        result[name]=blob;preserved.append({'path':name,'sha256':sha(blob),'bytes':len(blob)})
        if name.endswith('.class'):
            if blob[:4]!=bytes.fromhex('cafebabe') or int.from_bytes(blob[6:8],'big')>52:raise ValueError('Unclassified runtime class above Java8 '+name)
            refs=class_refs(blob)
            if any(ref in excluded_classes or ref.startswith(('cpw/mods/modlauncher/','cpw/mods/jarhandling/')) for ref in refs):raise ValueError('Retained runtime hardlink to excluded consumer '+name)
            classes.append(name)
    if len(classes)!=591:raise ValueError('Complete preserved Mixin core/LaunchWrapper class closure differs')
    for name,blob in result.items():
        if name.startswith('META-INF/services/'):
            for provider in blob.decode().splitlines():
                provider=provider.split('#',1)[0].strip()
                if provider and provider.replace('.','/')+'.class' not in result:raise ValueError('Retained advertised provider absent '+provider)
    receipt={'scope':'exact Mixin0.8.5 ordinary LaunchWrapper consumer role projection','coordinate':COORDINATE,
        'originalPublication':{'sha256':UPSTREAM_SHA256,'bytes':len(raw),'retainedUnchanged':True,'otherConsumers':'ModLauncher and JPMS retain original whole publication'},
        'sourceAuthority':policy['sourceAuthority'],'excludedEntries':cut_receipts,'rewrittenServices':rewrite_receipts,
        'preservedEntries':preserved,'preservedRuntimeClasses':len(classes),'classBytecodeEdited':False,'retainedClassHardlinksToExcludedConsumer':0,
        'originalClassMajorCounts':policy['originalClassMajorCounts'],'allFiveHighClasses':policy['hostedHighClasses'],'gameRuntimeAccepted':False}
    return result,receipt
