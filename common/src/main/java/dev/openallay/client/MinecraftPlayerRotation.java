package dev.openallay.client;

import net.minecraft.world.entity.Entity;

/** Native entity rotation primitives for capture and real development input. */
public final class MinecraftPlayerRotation {
    private MinecraftPlayerRotation() {}
    public static float yaw(Entity entity) { return entity.getYRot(); }
    public static float pitch(Entity entity) { return entity.getXRot(); }
    public static void yaw(Entity entity, float yaw) { entity.setYRot(yaw); }
    public static void pitch(Entity entity, float pitch) { entity.setXRot(pitch); }
}
