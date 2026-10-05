package dev.openallay.client.observation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;

/** Native block-hit border fact at the observation boundary. */
public final class MinecraftHitFacts {
    private MinecraftHitFacts() {}
    public static Boolean worldBorderHit(Minecraft client, BlockHitResult hit) { return hit.isWorldBorderHit(); }
}
