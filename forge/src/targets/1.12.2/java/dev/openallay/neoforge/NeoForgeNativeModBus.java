package dev.openallay.neoforge;

import net.minecraftforge.fml.common.eventhandler.EventBus;

/** FML14 has one Forge event bus, not a modern per-mod event bus. */
public final class NeoForgeNativeModBus {
    private static EventBus bus;
    private NeoForgeNativeModBus() {}
    public static void install(EventBus value) {
        if (bus != null && bus != value) throw new IllegalStateException("Forge bus already installed");
        bus = java.util.Objects.requireNonNull(value);
    }
    public static EventBus get() { return java.util.Objects.requireNonNull(bus, "Forge bus not installed"); }
}
