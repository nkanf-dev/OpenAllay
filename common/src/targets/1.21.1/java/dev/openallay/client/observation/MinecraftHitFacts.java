package dev.openallay.client.observation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;

/** Older native hit objects do not carry world-border occlusion provenance. */
public final class MinecraftHitFacts {
    private MinecraftHitFacts() {}
    public static Boolean worldBorderHit(Minecraft client, BlockHitResult hit) {
        // A hit point on a border plane is only geometric contact, not proof of native border occlusion.
        return null;
    }
}
