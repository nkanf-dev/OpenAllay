package dev.openallay.client.context;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
public final class MinecraftClientContextFacts {
    private MinecraftClientContextFacts() {}
    public static net.minecraft.client.settings.GameSettings options(Minecraft client) { return client.gameSettings; }
    public static boolean singleplayer(Minecraft client) { return client.isSingleplayer(); }
    public static boolean connected(Minecraft client) { return client.getConnection()!=null; }
    public static double x(Entity entity) { return entity.posX; }
    public static double y(Entity entity) { return entity.posY; }
    public static double z(Entity entity) { return entity.posZ; }
    public static String direction(Entity entity) { return entity.getHorizontalFacing().getName(); }
    public static int food(EntityPlayerSP player) { return player.getFoodStats().getFoodLevel(); }
    public static int air(EntityPlayerSP player) { return player.getAir(); }
    public static int armor(EntityPlayerSP player) { return player.getTotalArmorValue(); }
    public static java.util.Collection<net.minecraft.potion.PotionEffect> effects(EntityPlayerSP player) { return player.getActivePotionEffects(); }
    public static int inventorySize(EntityPlayerSP player) { return player.inventory.getSizeInventory(); }
    public static ItemStack inventoryItem(EntityPlayerSP player,int slot) { return player.inventory.getStackInSlot(slot); }
    public static long gameTime(Minecraft client) { return client.world.getTotalWorldTime(); }
    public static boolean thunder(Minecraft client) { return client.world.isThundering(); }
    public static boolean rain(Minecraft client) { return client.world.isRaining(); }
    public static net.minecraft.world.EnumDifficulty difficulty(Minecraft client) { return client.world.getDifficulty(); }
    public static net.minecraft.world.border.WorldBorder border(Minecraft client) { return client.world.getWorldBorder(); }
    public static String spawn(Minecraft client) { return dev.openallay.context.minecraft.MinecraftSpawnFacts.describe(client.world); }
    public static String cameraMode(Minecraft client) { return client.gameSettings.thirdPersonView==0 ? "first_person" : client.gameSettings.thirdPersonView==1 ? "third_person_back" : "third_person_front"; }

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
