package dev.openallay.world;
import java.util.List;
import java.util.Collection;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
/** Typed native observation facts; capture, yielding and actor custody stay canonical. */
public final class MinecraftWorldObservationFacts {
    private MinecraftWorldObservationFacts() {}
    public static Level clientLevel(Minecraft client) { return client.level; }
    public static boolean loaded(Level level,BlockPos pos) { return level.hasChunkAt(pos); }
    public static List<Entity> entities(Level level,AABB box,Predicate<Entity> filter) { return level.getEntities((Entity)null,box,filter); }
    public static boolean air(Level level,BlockState state,BlockPos pos) { return state.isAir(); }
    public static boolean hasBlockEntity(Level level,BlockPos pos) { return level.getBlockEntity(pos)!=null; }
    public static String dimension(Level level) { return dev.openallay.platform.minecraft.MinecraftResourceIds.keyId(level.dimension()).toString(); }
    public static double motionX(Entity entity) { return entity.getDeltaMovement().x(); }
    public static double motionY(Entity entity) { return entity.getDeltaMovement().y(); }
    public static double motionZ(Entity entity) { return entity.getDeltaMovement().z(); }
    public static String pose(Entity entity) { return entity.getPose().name().toLowerCase(java.util.Locale.ROOT); }
    public static float width(Entity entity) { return entity.getBbWidth(); }
    public static float height(Entity entity) { return entity.getBbHeight(); }
    public static int armor(LivingEntity living) { return living.getArmorValue(); }
    public static Collection<net.minecraft.world.effect.MobEffectInstance> effects(LivingEntity living) { return living.getActiveEffects(); }
}
