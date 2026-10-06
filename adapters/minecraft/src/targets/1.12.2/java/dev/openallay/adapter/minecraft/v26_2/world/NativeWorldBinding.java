package dev.openallay.adapter.minecraft.v26_2.world;

import com.google.gson.JsonObject;
import dev.openallay.api.extension.ExtensionException;
import dev.openallay.api.extension.ExtensionInvocation;
import dev.openallay.api.extension.WorldSession;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.init.Blocks;
/** Real MCP 1.12 native custody; no native aliases, fake worlds or implicit chunk acquisition. */
final class NativeWorldBinding implements WorldBinding {
    private final Minecraft client;
    private final IntegratedServer server;
    private final NetHandlerPlayClient connection;
    private final EntityPlayerSP clientPlayer;
    private final World clientLevel;
    private final UUID actor;
    private final String dimension;
    private final Path artifacts;
    private final SessionIdentity identity;
    private EntityPlayerMP player;
    private WorldServer level;
    private NativeWorldBinding(Minecraft client,ExtensionInvocation invocation) {
        invocation.requireActive();
        if(!client.isCallingFromMinecraftThread())throw new ExtensionException("wrong_owner","Capture requires the client owner thread");
        this.client=client;
        if(invocation.callerKind()!=ExtensionInvocation.CallerKind.PLAYER || invocation.callerUuid()==null || invocation.playerDimension().isEmpty())
            throw new ExtensionException("player_required","An exact local player invocation is required");
        actor=invocation.callerUuid();dimension=invocation.playerDimension().orElseThrow();
        server=client.getIntegratedServer();connection=client.getConnection();clientPlayer=client.player;clientLevel=client.world;
        artifacts=client.mcDataDir.toPath().resolve("config/openallay-builder");
        if(server==null)throw new ExtensionException("unsupported_topology","World access requires the active integrated server; a remote server is not an authoritative local backend");
        if(connection==null || clientPlayer==null || clientLevel==null)throw stale();
        identity=new SessionIdentity(connection,clientPlayer,clientLevel,server,actor,dimension);
        validateClient();
    }
    static OwnerThreadBridge.Owner captureOwner() {
        Minecraft client=Minecraft.getMinecraft();
        return new OwnerThreadBridge.Owner(client::addScheduledTask,client::isCallingFromMinecraftThread,()->{});
    }
    static WorldBinding captureClient(ExtensionInvocation invocation) { return new NativeWorldBinding(Minecraft.getMinecraft(),invocation); }
    static boolean isAnyOwnerThread() {
        Minecraft client=Minecraft.getMinecraft();IntegratedServer server=client.getIntegratedServer();
        return client.isCallingFromMinecraftThread() || server!=null && server.isCallingFromMinecraftThread();
    }
    @Override public OwnerThreadBridge.Owner clientOwner() { return new OwnerThreadBridge.Owner(client::addScheduledTask,client::isCallingFromMinecraftThread,this::validateClient); }
    @Override public OwnerThreadBridge.Owner serverOwner() { return new OwnerThreadBridge.Owner(server::addScheduledTask,server::isCallingFromMinecraftThread,this::validateServer); }
    @Override public OwnerThreadBridge.Owner serverCaptureOwner() { return new OwnerThreadBridge.Owner(server::addScheduledTask,server::isCallingFromMinecraftThread,()->{}); }
    @Override public void captureServer() {
        player=server.getPlayerList().getPlayerByUUID(actor);
        if(player==null)throw stale();
        level=player.getServerWorld();identity.bindServer(player,level);validateServer();
    }
    private void validateClient() {
        if(!client.isCallingFromMinecraftThread())throw new ExtensionException("wrong_owner","Client validation requires its owner thread");
        if(client.player==null || !connection.getNetworkManager().isChannelOpen())throw stale();
        identity.requireClient(client.getConnection(),client.player,client.world,client.getIntegratedServer(),
                client.player.getUniqueID(),NativeDimensionIdentity.id(client.player.world));
    }
    @Override public void validateServer() {
        if(!server.isCallingFromMinecraftThread())throw new ExtensionException("wrong_owner","Server validation requires its owner thread");
        if(server.isServerStopped() || !server.isServerRunning() || player==null || level==null || player.isDead
                || player.connection==null || !player.connection.getNetworkManager().isChannelOpen()
                || !connection.getNetworkManager().isChannelOpen())throw stale();
        identity.requireServer(server.getPlayerList().getPlayerByUUID(actor),player.getServerWorld(),player.getUniqueID(),NativeDimensionIdentity.id(player.getServerWorld()));
    }
    @Override public void beginAction() {}
    @Override public void endAction() { NativeBlockCodec.releasePalette(); }
    @Override public void invalidateProofs() {}
    @Override public void validatePosition(int x,int y,int z) {
        BlockPos pos=new BlockPos(x,y,z);
        if(!level.isValid(pos) || !level.getWorldBorder().contains(pos))throw new ExtensionException("invalid_bounds","Position is outside the active world bounds: "+pos);
        NativeLoadedChunks.require(level,pos);
    }
    @Override public String context() {
        JsonObject result=new JsonObject();
        result.addProperty("topology","integrated-server");result.addProperty("dimension",dimension);
        result.addProperty("minY",0);result.addProperty("maxY",256);
        result.addProperty("version",server.getMinecraftVersion());result.addProperty("dataVersion",NativeWorldVersionFacts.dataVersion(server));
        JsonObject who=new JsonObject();who.addProperty("uuid",actor.toString());
        who.addProperty("x",player.posX);who.addProperty("y",player.posY);who.addProperty("z",player.posZ);who.addProperty("yaw",player.rotationYaw);
        result.add("player",who);result.add("materialPalette",NativeBlockCodec.materialPalette());return result.toString();
    }
    @Override public String read(int x,int y,int z) { return NativeBlockCodec.read(level,new BlockPos(x,y,z)); }
    @Override public boolean canonicalAirAt(int x,int y,int z) {
        BlockPos pos=new BlockPos(x,y,z);
        return NativeLoadedChunks.get(level,pos).getBlockState(pos)==Blocks.AIR.getDefaultState()
                && NativeBlockEntityLifecycle.live(level,pos)==null;
    }
    @Override public boolean canonicalAir(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        // Null storage is a canonical-air proof in ordinary worlds. Native isEmpty
        // merges modded air and cannot be used. Non-null storage takes the bounded slow path.
        if(level.getWorldType()==net.minecraft.world.WorldType.DEBUG_ALL_BLOCK_STATES)return false;
        var chunk=NativeLoadedChunks.get(level,new BlockPos(minX,minY,minZ));
        if(chunk.getBlockStorageArray()[Math.floorDiv(minY,16)]!=net.minecraft.world.chunk.Chunk.NULL_BLOCK_STORAGE)return false;
        for(BlockPos pos:chunk.getTileEntityMap().keySet())
            if(Math.floorDiv(pos.getY(),16)==Math.floorDiv(minY,16))return false;
        return true;
    }
    @Override public String terrainState(int x,int y,int z) { return NativeBlockCodec.terrainState(level,new BlockPos(x,y,z)); }
    @Override public int terrainTop(int x,int z,int minY,int maxY) {
        // 1.12 heightMap is an opacity map, not the terrain non-air predicate.
        // Use the existing bounded scan-start fallback, never a guessed surface height.
        return maxY-1;
    }
    @Override public String preview(int x,int y,int z,String state) { return NativeBlockCodec.preview(level,new BlockPos(x,y,z),state); }
    @Override public Image write(int x,int y,int z,String state,Runnable requireActive) {
        NativeBlockCodec.VerifiedWrite result=NativeBlockCodec.writeVerified(level,new BlockPos(x,y,z),state,requireActive);
        return new Image(result.actual(),result.changed());
    }
    @Override public boolean sameImage(String before,String actual) { return NativeBlockCodec.sameImage(before,actual); }
    @Override public String transform(String state,int degrees,String mirror) { return NativeBlockCodec.transform(state,degrees,mirror); }
    @Override public WorldSession.RepairOutcome repair(int x,int y,int z,Runnable requireActive) {
        // 1.12 getActualState is a read/render-derived state, not a persisted modern
        // neighbor-shape update. Propagation is performed by real notifyNeighbours.
        requireActive.run();
        throw new ExtensionException("unsupported_native_repair","This native target has no persisted neighbor-shape repair operation; use native neighbor notification");
    }
    @Override public void notifyNeighbours(int x,int y,int z,Runnable requireActive) {
        BlockPos pos=new BlockPos(x,y,z);
        validatePosition(x,y,z);
        for(net.minecraft.util.EnumFacing direction:net.minecraft.util.EnumFacing.values()) {
            BlockPos neighbour=pos.offset(direction);
            if(!level.isOutsideBuildHeight(neighbour))validatePosition(neighbour.getX(),neighbour.getY(),neighbour.getZ());
            BlockPos second=neighbour.offset(direction);
            if(!level.isOutsideBuildHeight(second))validatePosition(second.getX(),second.getY(),second.getZ());
        }
        requireActive.run();
        var block=NativeLoadedChunks.get(level,pos).getBlockState(pos).getBlock();
        level.notifyNeighborsOfStateChange(pos,block,false);
        requireActive.run();
        level.updateComparatorOutputLevel(pos,block);
    }
    @Override public String dimension() { return dimension; }
    @Override public String createWorldId() { return NativeWorldIdentity.getOrCreate(server.getWorld(0)).id(); }
    @Override public Optional<String> existingWorldId() {
        NativeWorldIdentity existing=NativeWorldIdentity.getExisting(server.getWorld(0));
        return existing==null?Optional.empty():Optional.of(existing.id());
    }
    @Override public Path artifacts() { return artifacts; }
    @Override public boolean isOwnerThread() { return client.isCallingFromMinecraftThread() || server.isCallingFromMinecraftThread(); }
    private static ExtensionException stale() { return new ExtensionException("stale_session","The exact player, connection, server or dimension binding is no longer active"); }
}
