package dev.openallay.integration.rei;

import dev.architectury.fluid.FluidStack;
import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import me.shedaniel.rei.api.common.entry.EntryStack;

/** Pre-component fluid NBT ABI; the provider remains shared. */
final class ReiEntryFacts {
    private ReiEntryFacts() {}

    static boolean isFluidEntry(EntryStack<?> entry) {
        return entry.getValue() instanceof FluidStack;
    }

    static ReiFluidValue readFluid(EntryStack<?> entry) {
        FluidStack stack = entry.<FluidStack>cast().getValue();
        var tag = stack.getTag();
        return new ReiFluidValue(
                MinecraftNativeRegistries.FLUID.getKey(stack.getFluid()).toString(),
                stack.getAmount(), tag != null && !tag.isEmpty(), true);
    }
}
