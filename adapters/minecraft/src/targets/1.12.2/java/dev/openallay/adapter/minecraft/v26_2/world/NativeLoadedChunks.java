package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
/** getLoadedChunk reads only the loaded map; it never loads or generates. */
final class NativeLoadedChunks {
    private NativeLoadedChunks() {}
    static Chunk get(WorldServer level,BlockPos pos) {
        if(!level.isCallingFromMinecraftThread()) throw new ExtensionException("wrong_owner","Loaded chunk access requires the server owner thread");
        Chunk chunk=level.getChunkProvider().getLoadedChunk(Math.floorDiv(pos.getX(),16),Math.floorDiv(pos.getZ(),16));
        if(chunk==null || !chunk.isLoaded()) throw new ExtensionException("chunk_unavailable","Chunk is not loaded; no implicit generation: "+pos);
        return chunk;
    }
    static void require(WorldServer level,BlockPos pos) { get(level,pos); }
}
