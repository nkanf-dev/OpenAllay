package dev.openallay.client.context;
import net.minecraft.core.BlockPos;
public final class MinecraftClientContextFacts {
    private MinecraftClientContextFacts() {}
    public static String blockPosition(BlockPos position) { return position.toShortString(); }
}
