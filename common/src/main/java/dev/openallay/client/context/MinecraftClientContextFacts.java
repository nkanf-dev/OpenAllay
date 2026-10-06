package dev.openallay.client.context;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
public final class MinecraftClientContextFacts {
    private MinecraftClientContextFacts() {}
    public static net.minecraft.client.Options options(Minecraft client) { return client.options; }
    public static boolean singleplayer(Minecraft client) { return client.hasSingleplayerServer(); }
    public static boolean connected(Minecraft client) { return client.getConnection()!=null; }
    public static double x(Entity entity) { return entity.getX(); }
    public static double y(Entity entity) { return entity.getY(); }
    public static double z(Entity entity) { return entity.getZ(); }
    public static String direction(Entity entity) { return entity.getDirection().getName(); }
    public static int food(LocalPlayer player) { return player.getFoodData().getFoodLevel(); }
    public static int air(LocalPlayer player) { return player.getAirSupply(); }
    public static int armor(LocalPlayer player) { return player.getArmorValue(); }
    public static java.util.Collection<net.minecraft.world.effect.MobEffectInstance> effects(LocalPlayer player) { return player.getActiveEffects(); }
    public static int inventorySize(LocalPlayer player) { return dev.openallay.context.minecraft.MinecraftPlayerFacts.inventory(player).getContainerSize(); }
    public static ItemStack inventoryItem(LocalPlayer player,int slot) { return dev.openallay.context.minecraft.MinecraftPlayerFacts.inventory(player).getItem(slot); }
    public static long gameTime(Minecraft client) { return client.level.getGameTime(); }
    public static boolean thunder(Minecraft client) { return client.level.isThundering(); }
    public static boolean rain(Minecraft client) { return client.level.isRaining(); }
    public static net.minecraft.world.Difficulty difficulty(Minecraft client) { return client.level.getDifficulty(); }
    public static net.minecraft.world.level.border.WorldBorder border(Minecraft client) { return client.level.getWorldBorder(); }
    public static String spawn(Minecraft client) { return dev.openallay.context.minecraft.MinecraftSpawnFacts.describe(client.level); }
    public static String cameraMode(Minecraft client) { return client.options.getCameraType().name().toLowerCase(java.util.Locale.ROOT); }

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
