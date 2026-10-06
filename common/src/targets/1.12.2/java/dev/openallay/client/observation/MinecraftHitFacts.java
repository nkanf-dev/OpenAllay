package dev.openallay.client.observation;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityList;
import net.minecraftforge.fluids.FluidRegistry;
/** Actual one-class legacy raytrace discriminator, never aliased modern hit subclasses. */
public final class MinecraftHitFacts {
    private MinecraftHitFacts() {}
    public static RayTraceResult hit(Minecraft client) { return client.objectMouseOver; }
    public static String kind(RayTraceResult hit) { return hit.typeOfHit.name().toLowerCase(java.util.Locale.ROOT); }
    public static Vec3d location(RayTraceResult hit) { return hit.hitVec; }
    private static void block(RayTraceResult hit) {
        if (hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.getBlockPos() == null || hit.sideHit == null) throw new IllegalStateException("Native block hit payload mismatch");
    }
    public static BlockPos blockPosition(RayTraceResult hit) { block(hit); return hit.getBlockPos(); }
    public static EnumFacing direction(RayTraceResult hit) { block(hit); return hit.sideHit; }
    public static boolean inside(RayTraceResult hit) { block(hit); return false; }
    public static Boolean worldBorderHit(Minecraft client, RayTraceResult hit) { block(hit); return null; }
    public static Entity entity(RayTraceResult hit) {
        if (hit.typeOfHit != RayTraceResult.Type.ENTITY || hit.entityHit == null) throw new IllegalStateException("Native entity hit payload mismatch");
        return hit.entityHit;
    }
    public static String entityType(Entity entity) {
        var key = EntityList.getKey(entity);
        if (key == null) throw new IllegalStateException("Native entity has no registered type");
        return key.toString();
    }
    public static String fluid(IBlockState state) {
        var fluid = FluidRegistry.lookupFluidForBlock(state.getBlock());
        return fluid == null ? "" : FluidRegistry.getFluidName(fluid);
    }
    public static String availability() { return "legacy_inside_not_attested;legacy_world_border_hit_provenance_not_attested"; }
}
