package dev.openallay.client;
import net.minecraft.entity.Entity;
/** Actual native rotation primitives for capture and real development input. */
public final class MinecraftPlayerRotation {
    private MinecraftPlayerRotation() {}
    public static float yaw(Entity entity) { return entity.rotationYaw; }
    public static float pitch(Entity entity) { return entity.rotationPitch; }
    public static void yaw(Entity entity, float yaw) { entity.rotationYaw = yaw; }
    public static void pitch(Entity entity, float pitch) { entity.rotationPitch = pitch; }
}
