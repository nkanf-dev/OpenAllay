package dev.openallay.client;

import net.minecraft.world.entity.Entity;

/** Exact public Forge36 entity fields; reads/writes preserve the native primitive semantics. */
public final class MinecraftPlayerRotation {
    private MinecraftPlayerRotation() {}
    public static float yaw(Entity entity) { return entity.yRot; }
    public static float pitch(Entity entity) { return entity.xRot; }
    public static void yaw(Entity entity, float yaw) { entity.yRot = yaw; }
    public static void pitch(Entity entity, float pitch) { entity.xRot = pitch; }
}
