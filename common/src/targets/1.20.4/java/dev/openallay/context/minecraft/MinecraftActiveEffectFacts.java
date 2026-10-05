package dev.openallay.context.minecraft;

import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/** Pre-component active effects expose MobEffect directly, shared through 1.20.1. */
public final class MinecraftActiveEffectFacts {
    private MinecraftActiveEffectFacts() {}
    public static MobEffect effect(MobEffectInstance instance) { return instance.getEffect(); }
    public static String id(MobEffectInstance instance) {
        var id = MinecraftNativeRegistries.MOB_EFFECT.getKey(instance.getEffect());
        return id == null ? "unknown" : id.toString();
    }
}
