package dev.openallay.client.context;
import net.minecraft.util.math.BlockPos;
public final class MinecraftClientContextFacts {
    private MinecraftClientContextFacts() {}
    public static String blockPosition(BlockPos position) { return position.getX() + ", " + position.getY() + ", " + position.getZ(); }
}
