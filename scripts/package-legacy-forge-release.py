#!/usr/bin/env python3
"""Release-owned stock Forge player packaging, stdlib only.

Only tiny runtime helpers compile here, remotely. Product compilation belongs to
prepare-legacy-forge-release.py. Offline verifier consumes the packaging sidecar;
no provider receipts or test configuration enter the player ZIP.
"""
import argparse
import hashlib
import io
import json
import lzma
import os
from pathlib import Path, PurePosixPath
import re
import shlex
import struct
import subprocess
import sys
import tempfile
import tomllib
import zipfile

VERSION = '0.4.4'
STOCK_PROFILE = '1.12.2-forge-14.23.5.2864'
MAIN = 'net.minecraft.launchwrapper.Launch'
GAME_ARGUMENTS = '--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory} --assetsDir ${assets_root} --assetIndex ${assets_index_name} --uuid ${auth_uuid} --accessToken ${auth_access_token} --userType ${user_type} --tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker --versionType Forge'
FORGE_SHA = 'ff578d670d2c720a72f8fff31ea3d6868595c7e980ecdecba3254f307ef2c2a9'
WRAPPER_SHA = '57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f'
ASM_SHA = '254b82bec9da4f8efbc8b1f93ab2b87f7465227a82b36cf3d05d9e77a0e8dd2e'
LZMA_SHA = 'ceebaefd4abca814aa0160e71e62c507d63733b7da1773c1268e04ac9a720882'
PACK_SHA = '637960a65a320b359561f86016c467e6cfd6d31c0507c1f76effb46e85ea4db6'
UNPACK_TOOL_SHA = '5b6fdf439e95f7344d11cb377c23441799de52de3d5578176341ce7d178376c2'
SOURCES = {'bridge/DimensionEnumBridge.java': '462eab9dcf961efdf2d6122fb60613e6455ce5cce123902641897337a4dc1b5c', 'bridge/Pack200Bridge.java': 'e0540a9643c0a7739757575b3c7636e57cd742a34c462dcf54790a4a3a190f78', 'bridge/DimensionEnumPhaseCapture.java': 'bc424f8481235913fd667e2e5cde4e1279f93033e67c3f2b6109b21dbd121ce5', 'bridge/ObjectHolderBridge.java': '059982fcc7265008e8413bb3a82042500200a4d1acfe6dd0530296b69338dc9f', 'bridge/LaunchWrapperJava17Bridge.java': '4f5fe06acf0ddef31d7eff0219d9612f2d7563bb449c012489b243f040f3717d', 'bridge/CapabilityBridge.java': 'b295a16e7dc56b298dd582cb6bd4969cdb9300c6e8abebef9db1da3a1aae0378', 'bridge/dimension17/DimensionEnumRuntime.java': 'af5c830af0c9318dd845264c15a25ffa4778d8e6dc863bf80b17945059ed8509', 'bridge/dimension17/DimensionEnumInstaller.java': '2198a489e91ae7d3c0134c1835488b016c2d6b16f88642e7db4891c78c24d07f', 'bridge/pack200/DimensionConstructorFailure.java': 'c175f8c9c89158950a56667104937e1685ab1a5a38771d35431ff761ce04adce', 'bridge/pack200/ObjectHolderRuntime.java': '7449f3b8b96a65c440391ef9b0786752671c2eaafdadaabab02226d7f04b46bc', 'bridge/pack200/CapabilityRuntime.java': '6550ad4f6d309c32b939b0a517877a7660e52482d58bd95e8faa60002cf9c037', 'bridge/pack200/Pack200Runtime.java': '4647f5aa9d93642c1784fad9cc8fc724df5c97f329f7ebf3da9845a353ded9e4'}
FIXTURE_HOOK = 'hook.add(new LdcInsnNode(Type.getObjectType(OWNER)));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"fixture","(Ljava/lang/Class;)V",false));'


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


def jar_bytes(entries):
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(entries.items()):
            safe_name(name); item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            item.external_attr = 0o100644 << 16
            z.writestr(item, data, compress_type=zipfile.ZIP_DEFLATED)
    return out.getvalue()


def require_remote(environment):
    require(environment.get('GITHUB_ACTIONS') == 'true', 'Remote packaging only')


def mod_version(raw, target, version):
    entries = archive(raw)
    if target == 'forge1122':
        metadata = json.loads(entries['mcmod.info'], object_pairs_hook=pairs)
        mods = [m for m in metadata if m.get('modid') == 'openallay']
    else:
        metadata = tomllib.loads(entries['META-INF/mods.toml'].decode())
        mods = [m for m in metadata.get('mods', []) if m.get('modId') == 'openallay']
    require(len(mods) == 1 and mods[0].get('version') == version,
            'Actual OpenAllay loader metadata must equal release version')


def gate_dimension_fixture(text):
    require(text.count(FIXTURE_HOOK) == 1, 'Exact accepted dimension fixture seam required')
    # Guard lives in our bootstrap helper, not a new branch/frame in the game.
    # Constructor/values transformations and actual phase hash remain unchanged.
    return text.replace(FIXTURE_HOOK, FIXTURE_HOOK.replace('"fixture"', '"fixtureIfEnabled"'))


def gate_dimension_runtime(text):
    seam = 'public static synchronized void fixture(Class<?> type)throws Exception {'
    require(text.count(seam) == 1, 'Exact accepted real enum fixture owner required')
    return text.replace(seam,
        'public static void fixtureIfEnabled(Class<?> type)throws Exception {'
        'if(Boolean.getBoolean("openallay.e2e.enabled"))fixture(type);}' + seam)


def library_path(artifact):
    return 'libraries/dev/openallay/legacy/' + artifact + '/' + VERSION + '/' + artifact + '-' + VERSION + '.jar'


def library_record(artifact, raw):
    path = library_path(artifact).removeprefix('libraries/')
    return {'name': 'dev.openallay.legacy:' + artifact + ':' + VERSION,
            'downloads': {'artifact': {'path': path, 'sha1': hashlib.sha1(raw).hexdigest(),
                                      'size': len(raw), 'url': ''}}}


def player_profile(stock, profile_id, libraries):
    require(re.fullmatch(r'[A-Za-z0-9_.-]+', profile_id) is not None and profile_id != STOCK_PROFILE,
            'New safe profile identifier required')
    require(stock.get('id') == STOCK_PROFILE and stock.get('mainClass') == MAIN
            and stock.get('inheritsFrom') == '1.12.2', 'Exact installed stock Forge14 profile required')
    game = stock.get('minecraftArguments')
    require(game == GAME_ARGUMENTS, 'Authentic stock game arguments differ')
    game_tokens = shlex.split(game) + ['--tweakClass', 'org.spongepowered.asm.launch.MixinTweaker']
    jvm = ['-Djava.library.path=${natives_directory}', '-Dminecraft.launcher.brand=${launcher_name}',
           '-Dminecraft.launcher.version=${launcher_version}',
           '-javaagent:${game_directory}/openallay-runtime/agent.jar=${game_directory}/openallay-runtime',
           '-Dopenallay.component.receipt=${game_directory}/logs/openallay-component.log',
           '-cp', '${classpath}']
    return {'id': profile_id, 'inheritsFrom': STOCK_PROFILE, 'type': 'release', 'mainClass': MAIN,
            'javaVersion': {'component': 'java-runtime-gamma', 'majorVersion': 17},
            'minecraftArguments': game + ' --tweakClass org.spongepowered.asm.launch.MixinTweaker',
            'arguments': {'game': game_tokens, 'jvm': jvm}, 'libraries': libraries}


# Public source, not a launcher: installs only release files and an inherited version.
# The launcher owns account selection, credentials, asset rules and native extraction.
INSTALLER = r'''#!/usr/bin/env python3
"""Install a new OpenAllay profile into an existing authenticated launcher root."""
import argparse, hashlib, json, os, re
from pathlib import Path

def sha(raw): return hashlib.sha256(raw).hexdigest()
def safe(root, relative):
    parts=relative.split('/')
    if not relative or '\\' in relative or any(x in ('','.','..') for x in parts): raise ValueError('Unsafe install path')
    path=root.joinpath(*parts)
    # Reject all symlink ancestors, including the target itself.
    current=path
    while current != root:
        if current.is_symlink(): raise ValueError('Symlink install path refused')
        current=current.parent
    if root.is_symlink() or not path.resolve().is_relative_to(root.resolve()): raise ValueError('Install root escape')
    return path

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--minecraft-root',type=Path,required=True)
    p.add_argument('--game-directory',type=Path,required=True)
    p.add_argument('--profile-id',default='openallay-0.4.4-forge-1.12.2')
    p.add_argument('--install',action='store_true',help='Opt in; default only checks prerequisites')
    a=p.parse_args(); packet=Path(__file__).resolve().parent
    root=a.minecraft_root.absolute(); game=a.game_directory.absolute()
    if not root.is_dir() or root.is_symlink() or game.is_symlink(): raise ValueError('Ordinary launcher/game roots required')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+',a.profile_id): raise ValueError('Unsafe new profile ID')
    if a.profile_id=='1.12.2-forge-14.23.5.2864': raise ValueError('Do not replace stock Forge')
    stock=root/'versions/1.12.2-forge-14.23.5.2864/1.12.2-forge-14.23.5.2864.json'
    parent=json.loads(stock.read_text())
    if parent.get('id')!='1.12.2-forge-14.23.5.2864' or parent.get('mainClass')!='net.minecraft.launchwrapper.'+'Launch' or parent.get('inheritsFrom')!='1.12.2': raise ValueError('Install stock Forge14.23.5.2864 first')
    pins={
      'libraries/net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864.jar':'__FORGE_SHA__',
      'libraries/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar':'__WRAPPER_SHA__',
      'libraries/org/ow2/asm/asm-debug-all/5.2/asm-debug-all-5.2.jar':'__ASM_SHA__'}
    for relative,digest in pins.items():
        if sha(safe(root,relative).read_bytes())!=digest: raise ValueError('Installed stock library differs: '+relative)
    client=safe(root,'versions/1.12.2/1.12.2.jar')
    if hashlib.sha1(client.read_bytes()).hexdigest()!='0f275bc1547d01fa5f56ba34bdc87d981ee12daf': raise ValueError('Original Minecraft client differs')
    profile=json.loads((packet/'profile-template.json').read_text()); profile['id']=a.profile_id
    if profile['minecraftArguments'] != parent.get('minecraftArguments','')+' --tweakClass org.spongepowered.asm.launch.MixinTweaker': raise ValueError('Stock authentication/game arguments differ')
    sums={}
    for line in (packet/'SHA256SUMS').read_text().splitlines():
        digest,relative=line.split('  ',1)
        if sha(safe(packet,relative).read_bytes())!=digest: raise ValueError('Packet checksum differs: '+relative)
        sums[relative]=digest
    copies=[]; reused=[]
    for relative in sums:
        if relative.startswith('libraries/'):
            target=safe(root,relative)
            if target.exists():
                if not target.is_file() or target.is_symlink() or sha(target.read_bytes())!=sums[relative]: raise FileExistsError('Shared library bytes differ: '+str(target))
                reused.append({'path':relative,'sha256':sums[relative]})
            else: copies.append((safe(packet,relative),target))
        elif relative.startswith(('mods/','openallay-runtime/')): copies.append((safe(packet,relative),safe(game,relative)))
    version_dir=safe(root,'versions/'+a.profile_id)
    if version_dir.exists(): raise FileExistsError('Profile already exists; choose a new profile ID')
    for _,target in copies:
        if target.exists() or target.is_symlink(): raise FileExistsError('Never overwrite installed files: '+str(target))
    print('Stock libraries verified. New profile: '+a.profile_id)
    print('Game directory: '+str(game))
    for record in reused: print('REUSED '+record['sha256']+'  '+record['path'])
    if not a.install: print('No files written. Add --install to opt in.'); return {'reused':reused,'installed':False}
    created=[]; dirs=[]
    try:
        for source,target in copies:
            target.parent.mkdir(parents=True,exist_ok=True)
            with target.open('xb') as stream: stream.write(source.read_bytes()); stream.flush(); os.fsync(stream.fileno())
            st=target.stat(); created.append((target,st.st_dev,st.st_ino))
        safe(game,'logs').mkdir(parents=True,exist_ok=True)
        version_dir.mkdir(parents=True,exist_ok=False); dirs.append(version_dir)
        # Atomic, no-overwrite profile publication. This installer never edits launcher account/profile files.
        stage=version_dir/'.profile-new'
        with stage.open('xb') as stream: stream.write((json.dumps(profile,indent=2)+'\n').encode()); stream.flush(); os.fsync(stream.fileno())
        st=stage.stat();stage_record=(stage,st.st_dev,st.st_ino);created.append(stage_record);final=version_dir/(a.profile_id+'.json')
        os.link(stage,final);created.append((final,st.st_dev,st.st_ino));stage.unlink();created.remove(stage_record)
    except BaseException:
        for file,device,inode in reversed(created):
            current=file.lstat()
            if current.st_dev==device and current.st_ino==inode and file.is_file() and not file.is_symlink(): file.unlink()
        for directory in reversed(dirs):
            try: directory.rmdir()
            except OSError: pass
        raise
    print('Installed. Select the new version in your launcher, Java17 and the printed game directory. Sign in normally.')
    return {'reused':reused,'installed':True}
if __name__=='__main__': main()
'''
INSTALLER = INSTALLER.replace('__FORGE_SHA__', FORGE_SHA).replace('__WRAPPER_SHA__', WRAPPER_SHA).replace('__ASM_SHA__', ASM_SHA)

PUBLIC_AGENT = r'''package dev.openallay.runtime.forge1122;
import java.io.File;import java.lang.instrument.Instrumentation;import java.nio.file.*;
import java.security.MessageDigest;import java.util.*;import java.util.regex.Pattern;
/** Player setup only. All authenticated transformations remain in their pinned owners. */
public final class PlayerRuntimeAgent {
    static String sha(Path p)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)))s.append(String.format("%02x",b&255));return s.toString();}
    static Path stock(String name,String hash)throws Exception{
        Path found=null;for(String item:System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator),-1)){
            Path p=Paths.get(item);if(p.getFileName().toString().equals(name)){
                if(found!=null||!hash.equals(sha(p)))throw new IllegalStateException("Exact unique stock library required: "+name);found=p.toRealPath();}}
        if(found==null)throw new IllegalStateException("Missing installed stock library: "+name);return found;
    }
    public static void premain(String directory,Instrumentation instrumentation)throws Exception{
        if(!"17".equals(System.getProperty("java.specification.version")))throw new IllegalStateException("OpenAllay needs Java17");
        if(directory==null)throw new IllegalArgumentException("Player runtime directory required");
        Path home=Paths.get(directory).toRealPath();
        Properties config=new Properties();try(java.io.InputStream in=Files.newInputStream(home.resolve("runtime.properties"))){config.load(in);}
        for(String name:new String[]{"agent.jar","dimension.jar","binpatches.jar"})if(!config.getProperty(name).equals(sha(home.resolve(name))))throw new IllegalStateException("Player helper bytes differ: "+name);
        Path forge=stock("forge-1.12.2-14.23.5.2864.jar","__FORGE_SHA__");
        Path wrapper=stock("launchwrapper-1.12.jar","__WRAPPER_SHA__");
        stock("asm-debug-all-5.2.jar","__ASM_SHA__");
        Path client=stock("1.12.2.jar","__CLIENT_SHA__");
        Path logs=home.getParent().resolve("logs").resolve("openallay-runtime");Files.createDirectories(logs);
        Path session=Files.createTempDirectory(logs,"session-");
        String[] receipts={"bridge.receipt","pack200.transformReceipt","pack200.runtimeReceipt","objectholder.fields","objectholder.writes","objectholder.metadata","objectholder.invalid","objectholder.transformReceipt","capability.writes","capability.transformReceipt","dimension.bootstrapReceipt","dimension.transformReceipt","dimension.fixtureReceipt"};
        for(String property:receipts)System.setProperty("openallay."+property,session.resolve(property+".log").toString());
        System.setProperty("openallay.objectholder.rejected",session.resolve("rejected-holder").toString());
        System.setProperty("openallay.capability.rejected",session.resolve("rejected-capability").toString());
        System.setProperty("openallay.pack200.enabled","true");System.setProperty("openallay.objectholder.enabled","true");System.setProperty("openallay.capability.enabled","true");
        System.setProperty("openallay.pack200.forge",forge.toString());System.setProperty("openallay.pack200.jar",home.resolve("binpatches.jar").toString());System.setProperty("openallay.pack200.jarSha256",config.getProperty("binpatches.jar"));
        System.setProperty("openallay.objectholder.client",client.toString());System.setProperty("openallay.dimension.helper",home.resolve("dimension.jar").toString());System.setProperty("openallay.dimension.valuesField","$VALUES");
        // No fallback, exception swallowing, replacement classloader, Unsafe or test enable flag.
        LaunchWrapperJava17Bridge.premain(wrapper.toString(),instrumentation);
    }
}
'''
PUBLIC_AGENT = PUBLIC_AGENT.replace('__FORGE_SHA__', FORGE_SHA).replace('__WRAPPER_SHA__', WRAPPER_SHA).replace('__ASM_SHA__', ASM_SHA)


def run(command, log):
    with Path(log).open('xb') as stream:
        subprocess.run([str(x) for x in command], check=True, stdout=stream, stderr=subprocess.STDOUT,
                       env={k:v for k,v in os.environ.items() if k not in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')})


def classes(directory):
    return {p.relative_to(directory).as_posix(): p.read_bytes() for p in Path(directory).rglob('*.class')}


def compile_helpers(request, work):
    root = Path(request['sourceRoot']); packet = root/'scripts/forge1122-runtime-prerequisite'
    sources = {}
    for name, digest in SOURCES.items():
        path = packet/name; require(sha(path.read_bytes()) == digest, 'Pinned accepted helper source differs: ' + name)
        sources[name] = path
    forge=reference(request['forge']); asm=reference(request['stockAsm']); wrapper=reference(request['launchwrapper'])
    require(sha(forge.read_bytes()) == FORGE_SHA and sha(asm.read_bytes()) == ASM_SHA
            and sha(wrapper.read_bytes()) == WRAPPER_SHA, 'Actual stock helper compiler dependencies differ')
    java17=Path(request['java17Home'])/'bin'; java8=Path(request['java8Home'])/'bin'
    info=subprocess.run([str(java8/'java'),'-version'],capture_output=True,text=True,check=True)
    info=info.stdout+info.stderr
    require('1.8.0_482' in info and 'Temurin' in info and '1.8.0_482-b08' in info,
            'Genuine pinned Temurin8u482-b08 required')
    require(sha((java8/'unpack200').read_bytes()) == UNPACK_TOOL_SHA, 'Exact accepted genuine unpack200 executable required')
    info17=subprocess.run([str(java17/'java'),'-version'],capture_output=True,text=True,check=True)
    require('17.0.18' in info17.stdout+info17.stderr and 'Temurin' in info17.stdout+info17.stderr, 'Accepted helper compiler Java17 required')
    accepted=load(reference(request['acceptedPack200']))
    require(accepted['forgeSha256']==FORGE_SHA and accepted['lzmaSha256']==LZMA_SHA
            and accepted['packedSha256']==PACK_SHA and accepted['genuineUnpack200ExecutableSha256']==UNPACK_TOOL_SHA,
            'Accepted genuine Pack200 custody required')
    with zipfile.ZipFile(forge) as z: compressed=z.read('binpatches.pack.lzma')
    require(sha(compressed)==LZMA_SHA, 'Genuine Forge LZMA resource differs')
    packed=lzma.decompress(compressed); require(sha(packed)==PACK_SHA, 'Genuine Forge packed resource differs')
    packed_path=work/'patches.pack'; write_new(packed_path,packed)
    patches=work/'binpatches.jar'; run([java8/'unpack200',packed_path,patches],work/'unpack.log')
    entries=archive(patches.read_bytes())
    require({name:sha(data) for name,data in entries.items()}==accepted['entries'], 'Every genuine unpacked physical patch entry must match accepted stock phase')
    require(any(n.startswith('binpatch/client/') for n in entries), 'Client binpatches missing')
    dimension_dir=work/'dimension-classes'; dimension_dir.mkdir()
    derived_runtime=work/'DimensionEnumRuntime.java';write_new(derived_runtime,gate_dimension_runtime(sources['bridge/dimension17/DimensionEnumRuntime.java'].read_text()).encode())
    run([java17/'javac','--release','17','-d',dimension_dir,derived_runtime],work/'dimension-compile.log')
    agent_dir=work/'agent-classes'; agent_dir.mkdir()
    run([java17/'javac','--release','17','-d',agent_dir,sources['bridge/dimension17/DimensionEnumInstaller.java']],work/'installer-compile.log')
    derived=work/'DimensionEnumBridge.java'; write_new(derived,gate_dimension_fixture(sources['bridge/DimensionEnumBridge.java'].read_text()).encode())
    agent_sources=[p for n,p in sources.items() if n.startswith('bridge/') and n.count('/')==1 and n!='bridge/DimensionEnumBridge.java']+[derived]
    run([java17/'javac','--release','8','-cp',str(asm)+os.pathsep+str(agent_dir),'-d',agent_dir,*agent_sources],work/'agent-compile.log')
    # Official client SHA256 is source-owned metadata, not a renamed/mapped game alias.
    client_download=load(packet/'minecraft-1.12.2.json')['downloads']['client']
    require(client_download['sha1']=='0f275bc1547d01fa5f56ba34bdc87d981ee12daf', 'Official client source metadata differs')
    # Agent authenticates SHA1 in pinned ObjectHolderBridge. Public wrapper discovers
    # client by filename, then the original exact source guard authenticates its SHA1.
    public=PUBLIC_AGENT.replace('Path client=stock("1.12.2.jar","__CLIENT_SHA__");',
        'Path client=null;for(String item:System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator),-1)){Path p=Paths.get(item);if(p.getFileName().toString().equals("1.12.2.jar")){if(client!=null)throw new IllegalStateException("Unique original client required");client=p.toRealPath();}}if(client==null)throw new IllegalStateException("Original client missing");')
    player=work/'PlayerRuntimeAgent.java';write_new(player,public.encode())
    run([java17/'javac','--release','17','-cp',agent_dir,'-d',agent_dir,player],work/'player-compile.log')
    helper_dir=work/'helper-classes';helper_dir.mkdir()
    run([java17/'javac','--release','8','-cp',forge,'-d',helper_dir,*[p for n,p in sources.items() if n.startswith('bridge/pack200/')]],work/'helper-compile.log')
    agent=classes(agent_dir);agent['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nPremain-Class: dev.openallay.runtime.forge1122.PlayerRuntimeAgent\r\n\r\n'
    results={'agent':jar_bytes(agent),'dimension':jar_bytes(classes(dimension_dir)),
             'patches':patches.read_bytes(),'runtimeHelper':jar_bytes(classes(helper_dir))}
    return results, {'sourcePins': SOURCES, 'dimensionDerivedSourceSha256':sha(derived.read_bytes()),
                     'dimensionRuntimeDerivedSourceSha256':sha(derived_runtime.read_bytes()),
                     'productionFixtureOptIn': True, 'acceptedPack200': accepted,
                     'helperEntries':{key:inventory(raw) for key,raw in results.items()}}


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
    if role in ('featureCore','product'):
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


def build_packet(request, target, work, guide):
    version=request['version'];root=Path(request['sourceRoot'])
    require(version==VERSION and re.search(r'(?m)^version\s*=\s*'+re.escape(version)+r'\s*$',(root/'gradle.properties').read_text()), 'Actual current source release version required')
    proof=load(reference(request['providerReceipt']));provenance_check(proof,root,version)
    if target=='forge16165':
        exact(request,('sourceRoot','version','product','providerReceipt'),'Forge16 request')
        raw=reference(request['product']).read_bytes();provider_check(proof,version,{'product':raw})
        mod_version(raw,target,version)
        return raw,{'provider':proof,'helperBuild':None}
    exact(request,('sourceRoot','version','components','providerReceipt','forge','launchwrapper','stockAsm','java17Home','java8Home','acceptedPack200'),'Forge12 request')
    exact(request['components'],('featureCore','lifecycleFacade','privateMixin'),'product component roles')
    payloads={role:reference(record).read_bytes() for role,record in request['components'].items()}
    provider_check(proof,version,payloads);mod_version(payloads['lifecycleFacade'],target,version)
    require('FMLCorePlugin: dev.openallay.forge1122.bootstrap.OpenAllayFeatureBoundary' in archive(payloads['featureCore'])['META-INF/MANIFEST.MF'].decode(), 'Actual FML coremod manifest required')
    require('dev/openallay/forge1122/facade/OpenAllayLifecycleFacade.class' in archive(payloads['lifecycleFacade']), 'Actual Java8 discovery facade required')
    helpers,custody=compile_helpers(request,work)
    entries={'mods/openallay-feature-core.jar':payloads['featureCore'],
             'mods/openallay-lifecycle-facade.jar':payloads['lifecycleFacade'],
             library_path('private-mixin'):payloads['privateMixin'],
             library_path('runtime-helper'):helpers['runtimeHelper'],
             'openallay-runtime/agent.jar':helpers['agent'],
             'openallay-runtime/dimension.jar':helpers['dimension'],
             'openallay-runtime/binpatches.jar':helpers['patches']}
    entries['openallay-runtime/runtime.properties']=('agent.jar='+sha(helpers['agent'])+'\ndimension.jar='+sha(helpers['dimension'])+'\nbinpatches.jar='+sha(helpers['patches'])+'\n').encode()
    stock=load(root/'scripts/forge1122-runtime-prerequisite/version.json')
    profile=player_profile(stock,'openallay-'+version+'-forge-1.12.2',[
        library_record('private-mixin',payloads['privateMixin']),library_record('runtime-helper',helpers['runtimeHelper'])])
    entries['profile-template.json']=encoded(profile)
    entries['install-openallay.py']=INSTALLER.encode()
    entries['INSTALL.md']=guide
    entries['JVM-ARGUMENTS.txt']=('\n'.join(profile['arguments']['jvm'])+'\n').encode()
    entries['GAME-ARGUMENTS.txt']=('\n'.join(profile['arguments']['game'])+'\n').encode()
    entries['SHA256SUMS']=(''.join(sha(data)+'  '+name+'\n' for name,data in sorted(entries.items()))).encode()
    return jar_bytes(entries),{'provider':proof,'helperBuild':custody}


def validate_family(family, target, root):
    require(target in ('forge1122','forge16165'), 'Exact legacy target identity')
    identity={'forge1122':'forge-1.12.2','forge16165':'forge-1.16.5'}[target]
    if isinstance(family,dict):
        catalog=load(Path(root)/'gradle/minecraft-artifacts.json')
        rows=[record for record in catalog['acceptedFamilies'] if record['id']==identity]
        require(len(rows)==1 and family==rows[0], 'Exact source catalog legacy family required')
        minecraft={'forge1122':'1.12.2','forge16165':'1.16.5'}[target]
        require(family['buildTarget']==minecraft and family['supportedTargets']==[minecraft]
                and family['loader']=='forge' and family['artifactKind']==('zip' if target=='forge1122' else 'jar')
                and family['packagingRecipe']==('forge-install' if target=='forge1122' else 'forge-flat')
                and family['publicationChannels']==(['github'] if target=='forge1122' else ['github','modrinth']),
                'Actual legacy target/recipe/kind/channels differ')
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
    if target=='forge16165':
        payloads={'product':raw};mod_version(raw,target,release_version);core=raw
    else:
        entries=archive(raw)
        allowed={'mods/openallay-feature-core.jar','mods/openallay-lifecycle-facade.jar',
                 library_path('private-mixin'),library_path('runtime-helper'),
                 'openallay-runtime/agent.jar','openallay-runtime/dimension.jar',
                 'openallay-runtime/binpatches.jar','openallay-runtime/runtime.properties',
                 'profile-template.json','install-openallay.py','INSTALL.md',
                 'JVM-ARGUMENTS.txt','GAME-ARGUMENTS.txt','SHA256SUMS'}
        require(set(entries)==allowed, 'Exact config/game-free public packet layout required')
        payloads={'featureCore':entries['mods/openallay-feature-core.jar'],
                  'lifecycleFacade':entries['mods/openallay-lifecycle-facade.jar'],
                  'privateMixin':entries[library_path('private-mixin')]}
        mod_version(payloads['lifecycleFacade'],target,release_version);core=payloads['featureCore']
        require(entries['install-openallay.py']==INSTALLER.encode(), 'Source-owned public installer differs')
        helper=receipt['helperBuild'];require(helper['sourcePins']==SOURCES and helper['productionFixtureOptIn'] is True, 'Actual authenticated production helper custody required')
        packet=Path(root)/'scripts/forge1122-runtime-prerequisite'
        for name,digest in SOURCES.items():
            require(sha((packet/name).read_bytes())==digest, 'Actual source helper pin differs: '+name)
        require(sha(gate_dimension_fixture((packet/'bridge/DimensionEnumBridge.java').read_text()).encode())==helper['dimensionDerivedSourceSha256']
            and sha(gate_dimension_runtime((packet/'bridge/dimension17/DimensionEnumRuntime.java').read_text()).encode())==helper['dimensionRuntimeDerivedSourceSha256'], 'Exact production fixture opt-in source delta differs')
        for role,name in [('agent','openallay-runtime/agent.jar'),('dimension','openallay-runtime/dimension.jar'),('patches','openallay-runtime/binpatches.jar'),('runtimeHelper',library_path('runtime-helper'))]:
            check_inventory(entries[name],helper['helperEntries'][role])
        require({n:sha(d) for n,d in archive(entries['openallay-runtime/binpatches.jar']).items()}==helper['acceptedPack200']['entries'], 'Genuine full patch entry custody differs')
        sums={}
        for line in entries['SHA256SUMS'].decode().splitlines():
            digest,name=line.split('  ',1);require(name in entries and name not in sums and sha(entries[name])==digest, 'Public SHA256SUMS differs');sums[name]=digest
        require(set(sums)==set(entries)-{'SHA256SUMS'}, 'Public checksum coverage must be complete')
        profile=json.loads(entries['profile-template.json'],object_pairs_hook=pairs)
        require(profile==player_profile(load(Path(root)/'scripts/forge1122-runtime-prerequisite/version.json'),profile['id'],[
            library_record('private-mixin',payloads['privateMixin']),library_record('runtime-helper',entries[library_path('runtime-helper')])]), 'Exact player profile/classpath/auth arguments differ')
        require(entries['JVM-ARGUMENTS.txt']==('\n'.join(profile['arguments']['jvm'])+'\n').encode()
            and entries['GAME-ARGUMENTS.txt']==('\n'.join(profile['arguments']['game'])+'\n').encode(), 'Exact launcher argument templates differ')
        properties=('agent.jar='+sha(entries['openallay-runtime/agent.jar'])+'\ndimension.jar='+sha(entries['openallay-runtime/dimension.jar'])+'\nbinpatches.jar='+sha(entries['openallay-runtime/binpatches.jar'])+'\n').encode()
        require(entries['openallay-runtime/runtime.properties']==properties, 'Actual player helper runtime hash binding differs')
    provider_check(receipt['provider'],release_version,payloads)
    provenance_check(receipt['provider'],root,release_version)
    return {'coreBytes':core,'sqlite':receipt['provider']['sqlite'],'sharedRuntimes':receipt['provider']['sharedRuntimes']}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--target',choices=('forge1122','forge16165'),required=True)
    p.add_argument('--inputs',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--receipt',type=Path,required=True);p.add_argument('--work',type=Path)
    a=p.parse_args();require_remote(os.environ);request=load(a.inputs)
    outputs=[a.output.absolute(),a.receipt.absolute()]
    require(len(set(outputs))==2 and str(a.receipt)==str(a.output)+'.packaging.json', 'Output sidecar path must be output+.packaging.json')
    require(all(not p.exists() and not p.is_symlink() and p.parent.is_dir() for p in outputs),'New output files in existing directories required')
    require(all(not p.resolve().is_relative_to(Path(request['sourceRoot']).resolve()) or 'build' in p.parts for p in outputs), 'Do not replace source files')
    if a.target=='forge1122':
        require(a.work is not None and not a.work.exists() and a.work.parent.is_dir(), 'Fresh explicit helper work directory required')
        a.work.mkdir();work=a.work
    else:work=None
    raw,details=build_packet(request,a.target,work,(Path(request['sourceRoot'])/'docs/forge-runtime-installation.md').read_bytes())
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
