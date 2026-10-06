package dev.openallay.client.observation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import dev.openallay.platform.minecraft.MinecraftNativeRegistries;

/** Real native hit payload access; shared capture retains snapshot construction. */
public final class MinecraftHitFacts {
    private MinecraftHitFacts() {}
    public static String availability() { return ""; }
    public static HitResult hit(Minecraft client) { return client.hitResult; }
    public static String kind(HitResult hit) { return hit.getType().name().toLowerCase(java.util.Locale.ROOT); }
    public static Vec3 location(HitResult hit) { return hit.getLocation(); }
    private static BlockHitResult block(HitResult hit) {
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult block)) throw new IllegalStateException("Native block hit payload mismatch");
        return block;
    }
    public static BlockPos blockPosition(HitResult hit) { return block(hit).getBlockPos(); }
    public static Direction direction(HitResult hit) { return block(hit).getDirection(); }
    public static boolean inside(HitResult hit) { return block(hit).isInside(); }
    public static Boolean worldBorderHit(Minecraft client, HitResult hit) { return block(hit).isWorldBorderHit(); }
    public static Entity entity(HitResult hit) {
        if (hit.getType() != HitResult.Type.ENTITY || !(hit instanceof EntityHitResult entity)) throw new IllegalStateException("Native entity hit payload mismatch");
        return entity.getEntity();
    }
    public static String entityType(Entity entity) { return MinecraftNativeRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(); }
    public static String fluid(BlockState state) {
        var fluid = state.getFluidState();
        return fluid.isEmpty() ? "" : MinecraftNativeRegistries.FLUID.getKey(fluid.getType()).toString();
    }
}
