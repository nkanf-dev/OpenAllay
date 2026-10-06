#!/usr/bin/env python3
"""Capture only additional native seam API/source from the existing FG named input export."""
import argparse
import importlib.util
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

CLASSES = (
    'net.minecraft.client.renderer.vertex.DefaultVertexFormats','org.lwjgl.opengl.GL11',
    'net.minecraft.client.settings.GameSettings','net.minecraft.client.settings.KeyBinding',
    'net.minecraft.util.text.ITextComponent',
    'net.minecraft.util.text.TextFormatting',
    'net.minecraft.client.gui.Gui','net.minecraft.client.renderer.RenderItem','net.minecraftforge.fml.client.config.GuiUtils',
    'net.minecraft.client.renderer.texture.AbstractTexture',
    'net.minecraft.client.renderer.texture.ITextureObject',
    'net.minecraftforge.fml.common.Mod','net.minecraftforge.fml.common.Loader',
    'net.minecraftforge.fml.common.ModContainer','net.minecraftforge.fml.common.ModMetadata',
    'net.minecraftforge.fml.common.FMLCommonHandler','net.minecraftforge.fml.common.versioning.ArtifactVersion',
    'net.minecraftforge.fml.common.event.FMLPreInitializationEvent',
    'net.minecraftforge.fml.common.event.FMLInitializationEvent',
    'net.minecraftforge.fml.common.event.FMLPostInitializationEvent',
    'net.minecraftforge.fml.common.event.FMLServerStartingEvent',
    'net.minecraftforge.fml.common.event.FMLServerStartedEvent',
    'net.minecraftforge.fml.common.event.FMLServerStoppedEvent',
    'net.minecraftforge.fml.common.eventhandler.EventBus','net.minecraftforge.fml.common.eventhandler.SubscribeEvent',
    'net.minecraftforge.fml.common.gameevent.TickEvent$ClientTickEvent',
    'net.minecraftforge.fml.common.gameevent.TickEvent$Phase',
    'net.minecraftforge.fml.common.gameevent.PlayerEvent$PlayerLoggedInEvent',
    'net.minecraftforge.fml.common.gameevent.PlayerEvent$PlayerLoggedOutEvent',
    'net.minecraftforge.fml.common.network.NetworkRegistry',
    'net.minecraftforge.fml.common.network.FMLNetworkEvent$ClientDisconnectionFromServerEvent',
    'net.minecraftforge.fml.common.network.FMLNetworkEvent',
    'net.minecraftforge.fml.common.network.simpleimpl.IMessage',
    'net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler',
    'net.minecraftforge.fml.common.network.simpleimpl.MessageContext',
    'net.minecraftforge.fml.common.network.internal.FMLProxyPacket',
    'net.minecraftforge.client.event.ClientChatReceivedEvent',
    'net.minecraftforge.client.event.RenderGameOverlayEvent$Pre',
    'net.minecraftforge.client.event.RenderGameOverlayEvent','net.minecraftforge.client.event.RenderGameOverlayEvent$ElementType',
    'net.minecraftforge.client.ClientCommandHandler',
    'net.minecraft.client.resources.IReloadableResourceManager',
    'net.minecraft.client.resources.IResourceManagerReloadListener','net.minecraft.client.resources.IResourceManager',
    'net.minecraft.network.PacketBuffer','net.minecraft.command.ICommandSender',
    'net.minecraft.entity.Entity','net.minecraft.server.management.PlayerList',
    'net.minecraft.client.network.NetHandlerPlayClient','net.minecraft.network.NetworkManager',
    'net.minecraft.world.border.WorldBorder','net.minecraft.world.WorldType',
    'net.minecraft.util.math.BlockPos','net.minecraft.util.math.ChunkPos',
    'net.minecraft.block.state.IBlockBehaviors','net.minecraft.block.BlockChest',
    'net.minecraft.block.BlockShulkerBox','net.minecraft.util.datafix.DataFixer',
    'net.minecraft.world.IBlockAccess','net.minecraft.block.BlockFence','net.minecraft.block.BlockPane','net.minecraft.block.BlockStairs',
    'net.minecraft.entity.player.InventoryPlayer',
    'net.minecraft.entity.player.EntityPlayer',
    'net.minecraft.entity.EntityLivingBase',
    'net.minecraft.server.management.PlayerInteractionManager',
    'net.minecraft.world.GameType',
    'net.minecraft.world.EnumDifficulty',
    'net.minecraft.world.storage.ISaveHandler',

)
BYTECODE = {
    'net.minecraft.client.renderer.RenderItem': ('renderItemAndEffectIntoGUI','renderItemOverlayIntoGUI'),
    'net.minecraftforge.fml.client.config.GuiUtils': ('drawHoveringText','preItemToolTip','postItemToolTip'),
}
BODIES = ('net.minecraft.world.IBlockAccess','net.minecraft.block.BlockFence','net.minecraft.block.BlockPane','net.minecraft.block.BlockStairs','net.minecraft.util.datafix.DataFixer', 'net.minecraft.tileentity.TileEntityLockableLoot',
    'net.minecraft.tileentity.TileEntityChest','net.minecraft.tileentity.TileEntityShulkerBox')

def sha(path):
    d=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(65536),b''): d.update(b)
    return d.hexdigest()

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for flag in ('inputs','javap','output'): p.add_argument('--'+flag,type=Path,required=True)
    args=p.parse_args(); inputs=json.loads(args.inputs.read_text())
    cp=[Path(s) for s in inputs['classpath']]
    for r in inputs['compileClasspath']:
        if sha(Path(r['path']))!=r['sha256']: raise ValueError('Actual input hash differs')
    if args.output.exists(): raise ValueError('Fresh supplement output required')
    args.output.mkdir(parents=True)
    spec=importlib.util.spec_from_file_location('native_census',Path(__file__).with_name('collect-forge1122-native-census.py'))
    collector=importlib.util.module_from_spec(spec);spec.loader.exec_module(collector)
    records=[]; total=0
    for cls in CLASSES:
        r={'class':cls};records.append(r)
        result=subprocess.run([str(args.javap),'-public','-s','-classpath',':'.join(map(str,cp)),cls],
            capture_output=True,text=True,timeout=45)
        if result.returncode:
            r.update(status='missing',error=result.stderr[:2048]);continue
        encoded=result.stdout.encode();total+=len(encoded)
        if total>2*1024*1024: raise ValueError('Supplement API exceeds2MiB')
        name=cls.replace('.','_')+'.api.txt';(args.output/name).write_bytes(encoded)
        r.update(status='captured',api=name,apiSha256=sha(args.output/name))
        if cls in BYTECODE:
            body=subprocess.run([str(args.javap),'-p','-c','-s','-classpath',':'.join(map(str,cp)),cls],
                capture_output=True,text=True,timeout=45)
            if body.returncode or len(body.stdout.encode())>1500000:
                raise ValueError('Bounded requested render bytecode failed: '+cls)
            excerpt,missing=collector.method_excerpt(body.stdout,BYTECODE[cls])
            encoded=excerpt.encode();total+=len(encoded)
            if total>2*1024*1024:raise ValueError('Supplement exceeds2MiB')
            name=cls.replace('.','_')+'.bytecode.txt';(args.output/name).write_bytes(encoded)
            r.update(bytecode=name,bytecodeSha256=sha(args.output/name),missingBodySelectors=missing)
    source=Path(inputs['namedSources']['path'])
    if sha(source)!=inputs['namedSources']['sha256']:raise ValueError('Actual native source hash differs')
    with zipfile.ZipFile(source) as z:
        for cls in BODIES:
            data=z.read(cls.replace('.','/')+'.java');total+=len(data)
            if total>2*1024*1024: raise ValueError('Supplement source exceeds2MiB')
            (args.output/(cls.replace('.','_')+'.native-source.txt')).write_bytes(data)
    (args.output/'receipt.json').write_text(json.dumps({'kind':'additional exact native seam evidence only',
        'nativeInputsSha256':sha(args.inputs),'oldClassesReexported':False,
        'additionalClasses':records,'containerNativeSourceBodies':list(BODIES),'retainedBytes':total},indent=2)+'\n')
if __name__=='__main__':main()
