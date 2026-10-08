package dev.openallay.integration.rei;

import dev.architectury.fluid.FluidStack;
import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import me.shedaniel.rei.api.common.entry.EntryStack;

/** Optional REI entry value ABI only; capture and diagnostics remain in the shared provider. */
final class ReiEntryFacts {
    private ReiEntryFacts() {}

    static boolean isFluidEntry(EntryStack<?> entry) {
        return entry.getValue() instanceof FluidStack;
    }

    static ReiFluidValue readFluid(EntryStack<?> entry) {
        FluidStack stack = entry.<FluidStack>cast().getValue();
        return new ReiFluidValue(
                MinecraftNativeRegistries.FLUID.getKey(stack.getFluid()).toString(),
                stack.getAmount(), !stack.getPatch().isEmpty(), true);
    }
}
