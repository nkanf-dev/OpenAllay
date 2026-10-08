package dev.openallay.client;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.world.World;
/** Actual world owned by the captured legacy local player. */
public final class MinecraftLocalPlayerLevel {
    private MinecraftLocalPlayerLevel() {}
    public static World get(EntityPlayerSP player) { return player.world; }
}
