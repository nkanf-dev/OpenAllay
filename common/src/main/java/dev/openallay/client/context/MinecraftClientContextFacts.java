package dev.openallay.client.context;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
public final class MinecraftClientContextFacts {
    private MinecraftClientContextFacts() {}
    public static String blockPosition(BlockPos position) { return position.toShortString(); }
    public static boolean ownerThread(Minecraft client) { return client.isSameThread(); }
    public static boolean active(Minecraft client) { return client.player!=null && client.level!=null; }
    public static Object world(Minecraft client) { return client.level; }
    public static String dimension(Minecraft client) { return dev.openallay.platform.minecraft.MinecraftResourceIds.keyId(client.level.dimension()).toString(); }
    public static void execute(Minecraft client,Runnable action) { client.execute(action); }
    public static java.util.UUID uuid(Entity entity) { return entity.getUUID(); }
    public static int entityId(Entity entity) { return entity.getId(); }
    public static boolean alive(Entity entity) { return entity.isAlive(); }
    public static BlockPos position(Entity entity) { return entity.blockPosition(); }
    public static String name(Entity entity) { return entity.getName().getString(); }
    public static ItemStack mainHand(LocalPlayer player) { return player.getMainHandItem(); }
    public static ItemStack offHand(LocalPlayer player) { return player.getOffhandItem(); }
}
