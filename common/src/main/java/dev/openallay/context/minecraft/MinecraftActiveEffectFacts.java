package dev.openallay.context.minecraft;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/** The native active-effect representation changed from values to holders in 1.20.5. */
public final class MinecraftActiveEffectFacts {
    private MinecraftActiveEffectFacts() {}
    public static MobEffect effect(MobEffectInstance instance) { return instance.getEffect().value(); }
    public static String id(MobEffectInstance instance) {
        return instance.getEffect().unwrapKey()
                .map(dev.openallay.platform.minecraft.MinecraftResourceIds::keyId).orElse("unknown");
    }
}
