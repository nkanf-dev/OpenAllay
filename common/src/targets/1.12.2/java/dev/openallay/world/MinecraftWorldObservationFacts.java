package dev.openallay.world;
import java.util.List;
import java.util.Collection;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
/** Real MCP observation facts; no modern state/type aliases or capture algorithm. */
public final class MinecraftWorldObservationFacts {
    private MinecraftWorldObservationFacts() {}
    public static World clientLevel(Minecraft client) { return client.world; }
    public static boolean loaded(World level,BlockPos pos) {
        final class $oaPattern0_Holder { net.minecraft.world.World value; WorldServer bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if((($oaPattern0_holder.value = level) instanceof net.minecraft.world.WorldServer && (($oaPattern0_holder.bound = (WorldServer) $oaPattern0_holder.value) != null))) {
            net.minecraft.world.chunk.Chunk chunk=$oaPattern0_holder.bound.getChunkProvider().getLoadedChunk(Math.floorDiv(pos.getX(),16),Math.floorDiv(pos.getZ(),16));
            return chunk!=null && chunk.isLoaded();
        }
        return level.isBlockLoaded(pos);
    }
    public static List<Entity> entities(World level,AxisAlignedBB box,Predicate<Entity> filter) {
        return level.getEntitiesInAABBexcluding(null,box,entity->filter.test(entity));
    }
    public static boolean air(World level,IBlockState state,BlockPos pos) { return state.getBlock().isAir(state,level,pos); }
    public static boolean hasBlockEntity(World level,BlockPos pos) {
        if(!loaded(level,pos))throw new IllegalStateException("Block entity observation requires a loaded chunk");
        // IMMEDIATE/CHECK lookups can create/remove TEs. Observe the real loaded map.
        return level.getChunkFromBlockCoords(pos).getTileEntityMap().containsKey(pos);
    }
    public static String dimension(World level) { return "forge:dimension/"+level.provider.getDimension(); }
    public static double motionX(Entity entity) { return entity.motionX; }
    public static double motionY(Entity entity) { return entity.motionY; }
    public static double motionZ(Entity entity) { return entity.motionZ; }
    public static String pose(Entity entity) { return null; } // No native Pose enum in this game.
    public static float width(Entity entity) { return entity.width; }
    public static float height(Entity entity) { return entity.height; }
    public static int armor(EntityLivingBase living) { return living.getTotalArmorValue(); }
    public static Collection<net.minecraft.potion.PotionEffect> effects(EntityLivingBase living) { return living.getActivePotionEffects(); }
}
