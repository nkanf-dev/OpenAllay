#!/usr/bin/env python3
"""Inventory real FG3 named metadata plus exact neutral-selected canonical owners.

Run after the empty remote tooling island exports native-inputs.json. No game or
application compiler is invoked. Native javap output is inventory, not canonical
source attribution and never a cross-version name translator.
"""
import argparse
import csv
import hashlib
import io
import json
import re
import subprocess
import zipfile
from pathlib import Path

NATIVE_CLASSES = (
    'net.minecraft.client.Minecraft', 'net.minecraft.server.MinecraftServer',
    'net.minecraft.util.IThreadListener', 'net.minecraft.world.World',
    'net.minecraft.world.WorldServer', 'net.minecraft.world.WorldProvider',
    'net.minecraft.world.gen.ChunkProviderServer', 'net.minecraft.world.chunk.Chunk',
    'net.minecraft.world.chunk.storage.ExtendedBlockStorage',
    'net.minecraft.block.Block', 'net.minecraft.block.state.IBlockState',
    'net.minecraft.block.state.BlockStateContainer', 'net.minecraft.block.properties.IProperty',
    'net.minecraft.util.ResourceLocation', 'net.minecraft.tileentity.TileEntity',
    'net.minecraft.tileentity.TileEntityChest', 'net.minecraft.tileentity.TileEntityLockable',
    'net.minecraft.tileentity.TileEntityLockableLoot', 'net.minecraft.tileentity.TileEntityShulkerBox',
    'net.minecraft.world.LockCode', 'net.minecraft.nbt.NBTBase',
    'net.minecraft.nbt.NBTTagCompound', 'net.minecraft.nbt.NBTTagString',
    'net.minecraft.nbt.JsonToNBT', 'net.minecraft.nbt.NBTException',
    'net.minecraft.item.ItemStack', 'net.minecraft.world.storage.MapStorage',
    'net.minecraft.world.storage.WorldSavedData', 'net.minecraft.client.gui.GuiScreen',
    'net.minecraft.client.gui.GuiButton', 'net.minecraft.client.gui.GuiTextField',
    'net.minecraft.client.gui.ScaledResolution', 'net.minecraft.client.gui.FontRenderer',
    'net.minecraft.client.renderer.GlStateManager', 'net.minecraft.client.renderer.Tessellator',
    'net.minecraft.client.renderer.BufferBuilder', 'net.minecraft.client.renderer.texture.DynamicTexture',
    'net.minecraft.client.renderer.texture.TextureManager', 'net.minecraft.util.ScreenShotHelper',
    'net.minecraft.client.gui.toasts.GuiToast', 'net.minecraft.client.gui.toasts.IToast',
    'net.minecraft.client.entity.EntityPlayerSP', 'net.minecraft.entity.player.EntityPlayerMP',
    'net.minecraft.network.NetHandlerPlayServer', 'net.minecraft.command.ICommand',
    'net.minecraft.command.CommandBase', 'net.minecraftforge.fml.common.registry.ForgeRegistries',
    'net.minecraftforge.registries.IForgeRegistry', 'net.minecraftforge.common.DimensionManager',
    'net.minecraftforge.fluids.FluidRegistry', 'net.minecraftforge.oredict.OreDictionary',
    'net.minecraftforge.fml.client.registry.ClientRegistry',
    'net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper',
    'org.lwjgl.input.Keyboard', 'org.lwjgl.input.Mouse', 'org.lwjgl.opengl.Display')
BYTECODE = {
    'net.minecraft.client.Minecraft': ('addScheduledTask', 'isCallingFromMinecraftThread',
        'setIngameFocus', 'setIngameNotInFocus', 'runTickKeyboard', 'runTickMouse'),
    'net.minecraft.server.MinecraftServer': ('addScheduledTask', 'callFromMainThread',
        'isCallingFromMinecraftThread', 'updateTimeLightAndEntities'),
    'net.minecraft.world.World': ('isOutsideBuildHeight', 'isValid', 'getHeight', 'getActualHeight'),
    'net.minecraft.world.WorldServer': ('addScheduledTask', 'isCallingFromMinecraftThread'),
    'net.minecraft.world.gen.ChunkProviderServer': ('getLoadedChunk',),
    'net.minecraft.world.chunk.Chunk': ('getTileEntity', 'createNewTileEntity', 'getHeight', 'getHeightValue'),
    'net.minecraft.tileentity.TileEntity': ('create', 'readFromNBT', 'writeToNBT', 'getKey'),
    'net.minecraft.world.storage.MapStorage': ('getOrLoadData',),
    'net.minecraft.client.gui.GuiScreen': ('setFocused', 'isFocused', 'handleKeyboardInput', 'handleMouseInput'),
    'net.minecraft.client.gui.GuiTextField': ('setFocused', 'textboxKeyTyped', 'mouseClicked'),
}
LIMIT = 8 * 1024 * 1024


def sha(path):
    d = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(65536), b''):
            d.update(block)
    return d.hexdigest()


def verified(record):
    path = Path(record['path'])
    if not path.is_absolute() or not path.is_file() or sha(path) != record['sha256']:
        raise ValueError('Input hash/path differs: ' + str(path))
    return path


def method_excerpt(text, selectors):
    starts = list(re.finditer(r'(?m)^  (\S[^\n]*\([^\n]*\)[^\n]*;)$', text))
    blocks = {}
    for i, match in enumerate(starts):
        end = starts[i+1].start() if i+1 < len(starts) else text.rfind('\n}')
        name = match.group(1).split('(', 1)[0].rsplit(' ', 1)[-1]
        blocks.setdefault(name, []).append(text[match.start():end])
    return ('\n'.join(body for name in selectors for body in blocks.get(name, []))+'\n',
            sorted(set(selectors)-blocks.keys()))


def mapping_inventory(config_zip, snapshot_zip):
    with zipfile.ZipFile(snapshot_zip) as archive:
        names = {}
        for name in ('fields.csv', 'methods.csv'):
            names.update({row['searge']: row['name'] for row in
                csv.DictReader(io.StringIO(archive.read(name).decode('utf-8')))})
    classes, members, owner = [], [], None
    with zipfile.ZipFile(config_zip) as archive:
        config = json.loads(archive.read('config.json'))
        data = archive.read(config['data']['mappings']).decode('utf-8')
    for line in data.splitlines():
        if not line.strip():
            continue
        cols = line.split()
        if not line[0].isspace():
            if len(cols) != 2:
                raise ValueError('Unexpected pinned TSRG class row')
            owner = cols[1]
            classes.append({'obfuscated': cols[0], 'mcpClass': owner})
        else:
            if owner is None or len(cols) not in (2, 3):
                raise ValueError('Unexpected pinned TSRG member row')
            if owner.replace('/', '.') not in NATIVE_CLASSES:
                continue
            members.append({'mcpOwner': owner, 'obfuscated': cols[0],
                'obfuscatedDescriptor': cols[1] if len(cols)==3 else None,
                'srg': cols[-1], 'mcpMember': names.get(cols[-1], cols[-1])})
    return {'classNamespace': 'MCP class identity from MCPConfig joined.tsrg',
        'memberNamespace': 'SRG -> MCP snapshot20171003-1.12 CSV exact join',
        'officialMojangMappings': False, 'classes': classes, 'members': members}


def collect(args):
    pins = json.loads((Path(__file__).resolve().parents[1] / 'native-builds/forge1122-census/public-input-lock.json').read_text())
    for path, key in ((args.mcp_config, 'mcp_config'), (args.snapshot, 'snapshot')):
        if sha(path) != pins[key]['sha256']:
            raise ValueError('Pinned public mapping hash differs: '+key)
    manifest = json.loads(args.inputs.read_text())
    selected = json.loads(args.selection.read_text())
    if (manifest['minecraft'], manifest['forge'], manifest['forgeGradle'], manifest['gradle'],
            manifest['mappings'], manifest['mcpConfig']) != (
            '1.12.2', '14.23.5.2864', '3.0.197', '5.6.4',
            'snapshot_20171003-1.12', '1.12.2-20200226.224830'):
        raise ValueError('Exact native tuple differs')
    if not manifest['toolJava'].startswith('1.8.'):
        raise ValueError('Expected isolated Java8 tooling daemon')
    inputs = manifest['compileClasspath'] + manifest['pluginArtifacts'] + manifest['mappingOutputs']
    paths = [verified(r) for r in inputs]
    fg = [r for r in manifest['pluginArtifacts'] if r['coordinate']=='net.minecraftforge.gradle:ForgeGradle:3.0.197' and r['classifier']=='']
    if len(fg)!=1 or fg[0]['sha256']!=pins['forgeGradle']['sha256']:
        raise ValueError('Exact ForgeGradle binary differs')
    game = verified(manifest['namedGame'])
    source_archive = verified(manifest['namedSources'])
    cp = [Path(p) for p in manifest['classpath']]
    if set(cp) != {Path(r['path']) for r in manifest['compileClasspath']}:
        raise ValueError('Unrecorded classpath artifact')
    protected = paths + [game, source_archive, args.inputs.resolve(), args.selection.resolve(),
        args.mcp_config.resolve(), args.snapshot.resolve()]
    output = args.output.resolve()
    for path in protected + [Path(r['path']) for r in selected['java']]:
        if path.resolve() == output or output in path.resolve().parents:
            raise ValueError('Output contains immutable input')
    if output.exists():
        raise ValueError('Use a fresh external output directory')
    output.mkdir(parents=True)
    retained = 0
    def write(name, data):
        nonlocal retained
        encoded = data.encode() if isinstance(data, str) else (json.dumps(data, indent=2)+'\n').encode()
        retained += len(encoded)
        if retained > LIMIT:
            raise ValueError('Retained census exceeds 8MiB')
        (output/name).write_bytes(encoded)
    owners, majors = {}, {}
    for artifact in cp:
        with zipfile.ZipFile(artifact) as archive:
            for entry in archive.infolist():
                if entry.filename.endswith('.class') and not entry.filename.startswith('META-INF/versions/'):
                    identity = entry.filename[:-6].replace('/', '.')
                    owners.setdefault(identity, []).append(str(artifact))
                    if identity in NATIVE_CLASSES:
                        data = archive.read(entry)
                        majors.setdefault(identity, []).append(int.from_bytes(data[6:8], 'big'))
    units = []
    imported = set()
    for record in selected['java']:
        source = verified(record)
        text = source.read_text()
        # This is a lexical input census, not javac binding/attribution.
        references = sorted(set(re.findall(r'\b(?:net\.minecraft(?:forge)?|org\.lwjgl)\.[A-Za-z_$][\w.$]*(?:\.\*)?', text)))
        imports = re.findall(r'(?m)^\s*import\s+(?:static\s+)?((?:net\.minecraft(?:forge)?|org\.lwjgl)\.[\w.$*]+)\s*;', text)
        imported.update(imports)
        units.append(dict(record, lexicalNativeReferences=references, nativeImports=[
            {'spelling': name, 'exactClassOwners': owners.get(name, []),
             'kind': 'wildcard' if name.endswith('.*') else 'exact-or-static-member-candidate'}
            for name in imports]))
    write('selected-source-inventory.json', {'kind':'lexical census; no type attribution',
        'sourceSelectionSha256':sha(args.selection), 'units':units,
        'nativeImportCount':len(imported),
        'missingExactImportCandidates': sorted(name for name in imported
            if not name.endswith('.*') and name not in owners)})
    write('mapping-inventory.json', mapping_inventory(args.mcp_config, args.snapshot))
    signatures = []
    for identity in NATIVE_CLASSES:
        record = {'class':identity, 'owners':owners.get(identity, []), 'majorVersions':majors.get(identity, [])}
        signatures.append(record)
        if identity not in owners:
            record['status'] = 'missing'
            continue
        result = subprocess.run([str(args.javap), '-public', '-s', '-classpath',
            ':'.join(map(str, cp)), identity], capture_output=True, text=True, timeout=45)
        if result.returncode or len(result.stdout.encode()) > 300000:
            raise ValueError('Native javap failed/oversized for '+identity+': '+result.stderr[:2048])
        name = identity.replace('.', '_')+'.api.txt'
        write(name, result.stdout)
        record.update(status='captured', api=name, apiSha256=sha(output/name))
        if identity in BYTECODE:
            result = subprocess.run([str(args.javap), '-p', '-c', '-s', '-classpath',
                ':'.join(map(str, cp)), identity], capture_output=True, text=True, timeout=45)
            if result.returncode or len(result.stdout.encode()) > 1500000:
                raise ValueError('Native bytecode failed/oversized for '+identity)
            excerpt, missing = method_excerpt(result.stdout, BYTECODE[identity])
            name = identity.replace('.', '_')+'.bytecode.txt'
            write(name, excerpt)
            record.update(bytecode=name, missingBytecodeSelectors=missing, bytecodeSha256=sha(output/name))
    with zipfile.ZipFile(source_archive) as archive:
        for identity in BYTECODE:
            name = identity.replace('.', '/')+'.java'
            if name in archive.namelist():
                data = archive.read(name).decode('utf-8')
                # Keep exact native source only for bounded semantic seams, never entire copied game trees.
                write(identity.replace('.', '_')+'.native-source.txt', data)
    write('class-owner-conflicts.json', {name:files for name,files in sorted(owners.items()) if len(files)>1})
    write('receipt.json', {'kind':'exact Forge1122 native metadata/source-selection census',
        'nativeInputsSha256':sha(args.inputs), 'selectionSha256':sha(args.selection),
        'mcpConfigSha256':sha(args.mcp_config), 'snapshotSha256':sha(args.snapshot),
        'javap':str(args.javap.resolve()), 'namedGameSha256':sha(game),
        'namedSourcesSha256':sha(source_archive), 'classes':signatures,
        'applicationCompile':False, 'gameLaunch':False,
        'classNamespaceConversion':'no modern Mojang -> legacy MCP cross-version inference',
        'applicationJava':'engine17; SDK/Builder8 unchanged', 'retainedBytesBeforeReceipt':retained})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ('inputs','selection','mcp-config','snapshot','javap','output'):
        parser.add_argument('--'+flag, type=Path, required=True)
    collect(parser.parse_args())

if __name__ == '__main__':
    main()
