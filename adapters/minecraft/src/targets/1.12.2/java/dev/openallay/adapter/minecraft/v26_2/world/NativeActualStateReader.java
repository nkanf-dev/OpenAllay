package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
/** Real IBlockAccess for actual-state queries; no mutable World or TE handles exposed. */
final class NativeActualStateReader implements IBlockAccess {
    private final WorldServer level;
    private final BlockPos target;
    private int reads;
    NativeActualStateReader(WorldServer level,BlockPos target) { this.level=level;this.target=target.toImmutable(); }
    private void check(BlockPos pos) {
        if(!level.isCallingFromMinecraftThread())throw new ExtensionException("wrong_owner","Native actual-state reads require the server owner thread");
        if(++reads>256)throw new ExtensionException("unsupported_native_repair","Native actual-state query exceeded its bounded read budget");
        if(Math.abs((long)pos.getX()-target.getX())>2 || Math.abs((long)pos.getY()-target.getY())>2 || Math.abs((long)pos.getZ()-target.getZ())>2)
            throw new ExtensionException("unsupported_native_repair","Native actual-state query exceeded its bounded neighborhood");
        if(!level.isValid(pos) || !level.getWorldBorder().contains(pos))throw new ExtensionException("invalid_bounds","Native actual-state read is outside the active world bounds: "+pos);
        NativeLoadedChunks.require(level,pos);
    }
    @Override public IBlockState getBlockState(BlockPos pos) { check(pos);return NativeLoadedChunks.get(level,pos).getBlockState(pos); }
    @Override public TileEntity getTileEntity(BlockPos pos) {
        check(pos);
        if(NativeLoadedChunks.get(level,pos).getTileEntityMap().containsKey(pos))
            throw new ExtensionException("unsupported_native_repair","Native actual-state query requested opaque live block-entity data");
        return null;
    }
    @Override public int getCombinedLight(BlockPos pos,int light) { check(pos);return level.getCombinedLight(pos,light); }
    @Override public Biome getBiome(BlockPos pos) { check(pos);return level.getBiome(pos); }
    @Override public boolean isAirBlock(BlockPos pos) { IBlockState state=getBlockState(pos);return state.getBlock().isAir(state,this,pos); }
    @Override public int getStrongPower(BlockPos pos,EnumFacing direction) { IBlockState state=getBlockState(pos);return state.getBlock().getStrongPower(state,this,pos,direction); }
    @Override public WorldType getWorldType() { return level.getWorldType(); }
    @Override public boolean isSideSolid(BlockPos pos,EnumFacing side,boolean fallback) {
        IBlockState state=getBlockState(pos);return state.getBlock().isSideSolid(state,this,pos,side);
    }
}
