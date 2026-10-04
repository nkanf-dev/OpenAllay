package dev.openallay.adapter.minecraft.v26_2.world;

import com.google.gson.JsonObject;
import dev.openallay.api.extension.ExtensionException;
import dev.openallay.api.extension.ExtensionInvocation;
import dev.openallay.api.extension.WorldSession;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Live handles never leave this native adapter. Capture and validation run on their owners. */
final class NativeWorldSession implements WorldSession {
    private final Minecraft client;
    private final IntegratedServer server;
    private final ClientPacketListener connection;
    private final LocalPlayer clientPlayer;
    private final Object clientLevel;
    private final UUID actor;
    private final String dimension;
    private ServerPlayer player;
    private ServerLevel level;
    private String worldId;
    private Path artifacts;
    private final SessionIdentity identity;
    private final Runnable requireWorldWrite;
    private OwnerThreadBridge bridge;
    private OwnerThreadBridge.Owner clientOwner;
    private OwnerThreadBridge.Owner serverOwner;
    private Boolean terrainHeightmapSafe;
    // Owner-action-local pending/live BE section masks. No live chunk or proof is cached.
    private java.util.Map<Long,java.util.Set<Integer>> blockEntitySections;

    private NativeWorldSession(Minecraft client, ExtensionInvocation invocation) {
        requireWorldWrite = () -> {
            bridge.checkActive();
            invocation.requireCapability("openallay_builder:world_write");
        };
        invocation.requireActive();
        if (!client.isSameThread()) throw new ExtensionException("wrong_owner", "Capture requires the client owner thread");
        this.client = client;
        if (invocation.callerKind() != ExtensionInvocation.CallerKind.PLAYER || invocation.callerUuid() == null
                || invocation.playerDimension().isEmpty())
            throw new ExtensionException("player_required", "An exact local player invocation is required");
        actor = invocation.callerUuid();
        dimension = invocation.playerDimension().orElseThrow();
        server = client.getSingleplayerServer();
        connection = client.getConnection();
        clientPlayer = client.player;
        clientLevel = client.level;
        artifacts = client.gameDirectory.toPath().resolve("config/openallay-builder");
        if (server == null) throw new ExtensionException("unsupported_topology", "World access requires the active integrated server; a remote server is not an authoritative local backend");
        if (connection == null || clientPlayer == null || clientLevel == null) throw stale();
        identity = new SessionIdentity(connection, clientPlayer, clientLevel, server, actor, dimension);
        validateClient();
    }

    static NativeWorldSession capture(Minecraft client, OwnerThreadBridge bridge, ExtensionInvocation invocation) {
        OwnerThreadBridge.Owner captureOwner = new OwnerThreadBridge.Owner(client, client::isSameThread, () -> {});
        NativeWorldSession session = bridge.call(captureOwner, () -> new NativeWorldSession(client, invocation));
        session.bridge = bridge;
        session.clientOwner = new OwnerThreadBridge.Owner(client, client::isSameThread, session::validateClient);
        session.serverOwner = new OwnerThreadBridge.Owner(session.server, session.server::isSameThread, session::validateServer);
        // Recheck the captured client before admitting server capture. Neither owner waits.
        bridge.callAfter(session.clientOwner,
                new OwnerThreadBridge.Owner(session.server, session.server::isSameThread, () -> {}), () -> {
            session.player = session.server.getPlayerList().getPlayer(session.actor);
            if (session.player == null) throw stale();
            session.level = NativeServerPlayerLevel.get(session.player);
            session.identity.bindServer(session.player, session.level);
            session.validateServer();
            return null;
        });
        return session;
    }

    private void validateClient() {
        if (!client.isSameThread()) throw new ExtensionException("wrong_owner", "Client validation requires its owner thread");
        if (client.player == null || !connection.getConnection().isConnected()) throw stale();
        identity.requireClient(client.getConnection(), client.player, client.level, client.getSingleplayerServer(),
                client.player.getUUID(), NativeWorldResourceIds.keyId(client.player.level().dimension()).toString());
    }

    private void validateServer() {
        if (!server.isSameThread()) throw new ExtensionException("wrong_owner", "Server validation requires its owner thread");
        if (server.isStopped() || server.isShutdown() || player == null || level == null
                || player.isRemoved() || !player.connection.isAcceptingMessages()
                || !connection.getConnection().isConnected()) throw stale();
        identity.requireServer(server.getPlayerList().getPlayer(actor), NativeServerPlayerLevel.get(player), player.getUUID(),
                NativeWorldResourceIds.keyId(NativeServerPlayerLevel.get(player).dimension()).toString());
    }

    @Override public <T> T call(java.util.concurrent.Callable<T> action) {
        return bridge.callAfter(clientOwner,serverOwner,() -> {
            blockEntitySections=new java.util.HashMap<>();
            try { return action.call(); }
            finally { blockEntitySections=null; NativeBlockCodec.releasePalette(); }
        });
    }

    @Override public long sliceDeadline() { requireOwnerAction(); return System.nanoTime() + 4_000_000L; }
    @Override public long dispatches() { bridge.checkActive(); return bridge.dispatches(); }

    private void requireOwnerAction() {
        bridge.checkActive();
        validateServer();
        if (blockEntitySections == null)
            throw new ExtensionException("owner_action_required", "Native operations require an admitted owner action");
    }

    @Override public void validatePosition(int x, int y, int z) { validatePosition(new BlockPos(x,y,z)); }
    private void validatePosition(BlockPos position) {
        requireOwnerAction();
        if (level.isOutsideBuildHeight(position)) throw new ExtensionException("invalid_bounds", "Position is outside the active dimension build height: " + position);
        if (!level.getWorldBorder().isWithinBounds(position)) throw new ExtensionException("invalid_bounds", "Position is outside the world border: " + position);
        if (!level.hasChunkAt(position)) throw new ExtensionException("chunk_unavailable", "Chunk is not loaded; no implicit chunk generation: " + position);
    }

    @Override public String context() {
        return call(() -> {
            JsonObject result = new JsonObject();
            result.addProperty("topology", "integrated-server");
            result.addProperty("dimension", dimension);
            result.addProperty("minY", level.getMinY());
            result.addProperty("maxY", Math.addExact(level.getMaxY(), 1));
            result.addProperty("version", NativeWorldVersionFacts.name());
            result.addProperty("dataVersion", NativeWorldVersionFacts.dataVersion());
            JsonObject who = new JsonObject();
            who.addProperty("uuid", actor.toString());
            who.addProperty("x", player.getX()); who.addProperty("y", player.getY()); who.addProperty("z", player.getZ());
            who.addProperty("yaw", player.getYRot());
            result.add("player", who);
            result.add("materialPalette", NativeBlockCodec.materialPalette());
            return result.toString();
        });
    }

    @Override public String read(int x, int y, int z) { return read(new BlockPos(x,y,z)); }
    private String read(BlockPos pos) {
        validatePosition(pos);
        try { return NativeBlockCodec.read(level,pos); }
        finally { if(blockEntitySections!=null)blockEntitySections.clear(); }
    }
    @Override public String readNonAir(int x, int y, int z) { return readNonAir(new BlockPos(x,y,z)); }
    private String readNonAir(BlockPos pos) {
        validatePosition(pos);
        var state = level.getBlockState(pos);
        if (state == net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() && level.getBlockEntity(pos) == null) return null;
        try { return NativeBlockCodec.read(level,pos); }
        finally {
            // Native/modded BE serializers may have side effects. A proof mask may
            // be reused only across read-only palette checks, not arbitrary hooks.
            if(blockEntitySections!=null)blockEntitySections.clear();
        }
    }
    @Override public boolean canonicalAir(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ)
            throw new ExtensionException("invalid_bounds", "A canonical-air tile must have ordered inclusive bounds");
        BlockPos min=new BlockPos(minX,minY,minZ);
        BlockPos max=new BlockPos(maxX,maxY,maxZ);
        validatePosition(min);validatePosition(max);
        int chunkX=Math.floorDiv(minX,16),chunkZ=Math.floorDiv(minZ,16);
        int sectionY=Math.floorDiv(minY,16);
        if(chunkX!=Math.floorDiv(maxX,16) || chunkZ!=Math.floorDiv(maxZ,16)
                || sectionY!=Math.floorDiv(maxY,16))
            throw new IllegalArgumentException("Canonical-air proof requires one clipped section");
        // getChunkNow never requests or generates a missing chunk.
        var chunk=level.getChunkSource().getChunkNow(chunkX,chunkZ);
        if(chunk==null)throw new ExtensionException("chunk_unavailable","Chunk is not loaded; no implicit generation: "+min);
        var section=chunk.getSection(chunk.getSectionIndex(minY));
        if(!canonicalAir(section))return false;
        long key=NativeChunkCoordinates.pack(chunkX,chunkZ);
        java.util.Set<Integer> occupied=blockEntitySections.computeIfAbsent(key,ignored -> {
            java.util.Set<Integer> sections=new java.util.HashSet<>();
            for(BlockPos pos:chunk.getBlockEntitiesPos())sections.add(Math.floorDiv(pos.getY(),16));
            return sections;
        });
        return !occupied.contains(sectionY);
    }
    static boolean canonicalAir(net.minecraft.world.level.chunk.LevelChunkSection section) {
        // Palette may include stale unused values: those only cause a safe slow fallback.
        // hasOnlyAir/isAir would collapse cave, void and modded air and are not proofs.
        return !section.maybeHas(state -> state!=net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
    }
    @Override public String terrainState(int x, int y, int z) { return terrainState(new BlockPos(x,y,z)); }
    private String terrainState(BlockPos pos) {
        validatePosition(pos);
        return NativeBlockCodec.terrainState(level,pos);
    }
    @Override public int terrainTop(int x,int z,int minY,int maxY) {
        if (minY >= maxY) throw new ExtensionException("invalid_bounds", "Terrain height bounds must be a nonempty half-open range");
        BlockPos bottom = new BlockPos(x,minY,z);
        validatePosition(bottom);
        validatePosition(new BlockPos(x,maxY-1,z));
        if (terrainHeightmapSafe == null) {
            terrainHeightmapSafe = true;
            // WORLD_SURFACE tests native isAir, while terrain.js excludes only three IDs.
            // Inspect every possible state once per binding, not every column/default state.
            for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
                if (!heightmapCovers(block,id)) { terrainHeightmapSafe = false; break; }
            }
        }
        if (!terrainHeightmapSafe) return maxY-1;
        var chunk = level.getChunkAt(bottom); // Already validated loaded; no implicit generation.
        var type = net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE;
        // Never prime a missing map by scanning a whole chunk inside this bounded action.
        if (!chunk.hasPrimedHeightmap(type)) return maxY-1;
        return Math.min(maxY-1,chunk.getHeight(type,x & 15,z & 15));
    }
    private static final java.util.Set<String> TERRAIN_AIR = java.util.Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    static boolean heightmapCovers(net.minecraft.world.level.block.Block block,String id) {
        for (var state : block.getStateDefinition().getPossibleStates()) {
            if (state.isAir() && !TERRAIN_AIR.contains(id)) return false;
        }
        return true;
    }
    @Override public String preview(int x, int y, int z, String state) { return preview(new BlockPos(x,y,z), state); }
    private String preview(BlockPos pos,String state) {
        validatePosition(pos);
        try { return NativeBlockCodec.preview(level,pos,state); }
        finally { blockEntitySections.clear(); }
    }
    @Override public WriteOutcome write(int x, int y, int z, String state) { return write(new BlockPos(x,y,z), state); }
    private WriteOutcome write(BlockPos pos,String state) {
        requireWorldWrite.run();
        validatePosition(pos);
        try { return write(pos,state,NativeBlockCodec.read(level,pos)); }
        finally { blockEntitySections.clear(); }
    }
    @Override public WriteOutcome write(int x, int y, int z, String state, String before) { return write(new BlockPos(x,y,z), state, before); }
    private WriteOutcome write(BlockPos pos,String state,String before) {
        requireWorldWrite.run();
        validatePosition(pos);
        try {
            NativeBlockCodec.VerifiedWrite result = NativeBlockCodec.writeVerified(level,pos,state, () -> { validatePosition(pos); requireWorldWrite.run(); });
            return new WriteOutcome(result.actual(),result.changed(),null);
        } catch (RuntimeException failure) {
            // This action passed optimistic-before validation. Retain actual outcome even
            // when a native replacement hook fails or cancellation arrives mid-write.
            try {
                String actual = NativeBlockCodec.read(level,pos);
                return new WriteOutcome(actual,!NativeBlockCodec.sameImage(before,actual),OwnerThreadBridge.propagate(failure));
            } catch (RuntimeException unreadable) {
                failure.addSuppressed(unreadable);
                throw OwnerThreadBridge.propagate(failure); // Durable intent stays uncertain, never marked unapplied.
            }
        } finally { if (blockEntitySections != null) blockEntitySections.clear(); }
    }
    @Override public String transform(String state,int degrees,String mirror) { requireOwnerAction(); return NativeBlockCodec.transform(state,degrees,mirror); }
    @Override public String repairedState(int x, int y, int z) { return repairedState(new BlockPos(x,y,z)); }
    private String repairedState(BlockPos pos) {
        RepairOutcome result=repair(pos);return result==null?null:result.intended();
    }
    @Override public RepairOutcome repair(int x, int y, int z) { return repair(new BlockPos(x,y,z)); }
    private RepairOutcome repair(BlockPos pos) {
        requireWorldWrite.run();
        validatePosition(pos);
        for(net.minecraft.core.Direction direction:net.minecraft.core.Direction.values()) {
            BlockPos neighbour=pos.relative(direction);
            if(!level.isOutsideBuildHeight(neighbour))validatePosition(neighbour);
        }
        NativeBlockCodec.Snapshot before=NativeBlockCodec.snapshot(level,pos);
        try {
            requireWorldWrite.run();
            validatePosition(pos);
            var current=before.state();
            var updated=net.minecraft.world.level.block.Block.updateFromNeighbourShapes(current,level,pos);
            if(updated==current)return null;
            // Keep the original pre-hook image for optimistic conflict checks. For a
            // changed state, retain the exact post-hook BE payload as the old repair
            // adapter did; an in-hook target mutation still fails the before gate.
            JsonObject state=com.google.gson.JsonParser.parseString(NativeBlockCodec.read(level,pos)).getAsJsonObject();
            state.addProperty("id",net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(updated.getBlock()).toString());
            state.add("properties",NativeBlockStateProperties.encode(updated));
            if(updated.getBlock()!=current.getBlock())state.remove("blockEntity");
            return new RepairOutcome(before.json(),state.toString());
        } finally {if(blockEntitySections!=null)blockEntitySections.clear();}
    }
    @Override public void notifyNeighbours(int x, int y, int z) { notifyNeighbours(new BlockPos(x,y,z)); }
    private void notifyNeighbours(BlockPos pos) {
        requireWorldWrite.run();
        validatePosition(pos);
        try {
            var block = level.getBlockState(pos).getBlock();
            level.updateNeighborsAt(pos,block);
            validatePosition(pos);
            requireWorldWrite.run();
            level.updateNeighbourForOutputSignal(pos,block);
        } finally { blockEntitySections.clear(); }
    }

    @Override public String dimension() { return call(() -> dimension); }
    @Override public String worldId() {
        bridge.checkWorker();
        requireWorldWrite.run();
        return call(() -> {
            requireWorldWrite.run();
            if (worldId == null)
                worldId = NativeWorldIdentity.getOrCreate(server.overworld()).id();
            return worldId;
        });
    }
    @Override public java.util.Optional<String> existingWorldId() {
        bridge.checkWorker();
        // Native get reads/caches existing SavedData; it never invokes the constructor or setDirty.
        return call(() -> {
            if (worldId != null) return java.util.Optional.of(worldId);
            NativeWorldIdentity existing = NativeWorldIdentity.getExisting(server.overworld());
            if (existing == null) return java.util.Optional.empty();
            worldId = existing.id();
            return java.util.Optional.of(worldId);
        });
    }
    @Override public Path artifacts() { return call(() -> artifacts); }
    @Override public boolean isOwnerThread() { return client.isSameThread() || server.isSameThread(); }
    @Override public void close() { bridge.close(); }
    private static ExtensionException stale() { return new ExtensionException("stale_session", "The exact player, connection, server or dimension binding is no longer active"); }
}
