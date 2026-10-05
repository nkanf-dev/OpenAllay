package dev.openallay.context.minecraft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.alchemy.Potion;

/** Holder-era potion name contract, shared by 1.21.1/1.21 and 1.20.6/1.20.5. */
final class MinecraftPotionFacts {
    private MinecraftPotionFacts() {}
    static String name(Potion potion) {
        return potion.getName(BuiltInRegistries.POTION.getResourceKey(potion)
                .flatMap(BuiltInRegistries.POTION::getHolder)
                .map(holder -> (net.minecraft.core.Holder<Potion>) holder), "");
    }
}
