package dev.openallay.client.context;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
public final class MinecraftClientContextFacts {
    private MinecraftClientContextFacts() {}
    public static String blockPosition(BlockPos position) { return position.getX()+", "+position.getY()+", "+position.getZ(); }
    public static boolean ownerThread(Minecraft client) { return client.isCallingFromMinecraftThread(); }
    public static boolean active(Minecraft client) { return client.player!=null && client.world!=null; }
    public static Object world(Minecraft client) { return client.world; }
    public static String dimension(Minecraft client) { return "forge:dimension/"+client.world.provider.getDimension(); }
    public static void execute(Minecraft client,Runnable action) { client.addScheduledTask(action); }
    public static java.util.UUID uuid(Entity entity) { return entity.getUniqueID(); }
    public static int entityId(Entity entity) { return entity.getEntityId(); }
    public static boolean alive(Entity entity) { return entity.isEntityAlive(); }
    public static BlockPos position(Entity entity) { return entity.getPosition(); }
    public static String name(Entity entity) { return entity.getDisplayName().getUnformattedText(); }
    public static ItemStack mainHand(EntityPlayerSP player) { return player.getHeldItemMainhand(); }
    public static ItemStack offHand(EntityPlayerSP player) { return player.getHeldItemOffhand(); }
}
