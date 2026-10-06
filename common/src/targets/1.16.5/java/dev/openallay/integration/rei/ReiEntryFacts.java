package dev.openallay.integration.rei;

import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import me.shedaniel.architectury.fluid.FluidStack;
import me.shedaniel.architectury.utils.Fraction;
import me.shedaniel.rei.api.common.entry.EntryStack;

/** Real Architectury 1 fluid ABI. Fractional or out-of-range amounts are not truncated. */
final class ReiEntryFacts {
    private ReiEntryFacts() {}

    static boolean isFluidEntry(EntryStack<?> entry) {
        return entry.getValue() instanceof FluidStack;
    }

    static ReiFluidValue readFluid(EntryStack<?> entry) {
        FluidStack stack = entry.<FluidStack>cast().getValue();
        Fraction fraction = stack.getAmount();
        long amount = exactForgeAmount(fraction.getNumerator(), fraction.getDenominator());
        var tag = stack.getTag();
        return new ReiFluidValue(
                MinecraftNativeRegistries.FLUID.getKey(stack.getFluid()).toString(),
                amount, tag != null && !tag.isEmpty(), amount > 0);
    }

    private static long exactForgeAmount(long numerator, long denominator) {
        if (numerator <= 0 || denominator <= 0 || numerator % denominator != 0) {
            return -1;
        }
        long amount = numerator / denominator;
        // The genuine Forge16 fluid stack owns an int amount; avoid lossy native conversion.
        return amount <= Integer.MAX_VALUE ? amount : -1;
    }
}
