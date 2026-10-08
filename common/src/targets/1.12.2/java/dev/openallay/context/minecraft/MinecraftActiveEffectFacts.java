package dev.openallay.context.minecraft;

import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

/** Exact Forge 14 active-effect value and registry identity. */
public final class MinecraftActiveEffectFacts {
    private MinecraftActiveEffectFacts() {}
    public static Potion effect(PotionEffect instance) { return instance.getPotion(); }
    public static String id(PotionEffect instance) {
        net.minecraft.util.ResourceLocation id = MinecraftNativeRegistries.MOB_EFFECT.getKey(effect(instance));
        return id == null ? "unknown" : id.toString();
    }
}
