package dev.openallay.neoforge;

import net.minecraftforge.eventbus.api.IEventBus;

/** Installed by the native entrypoint; no bus crosses an engine API. */
public final class NeoForgeNativeModBus {
    private static IEventBus bus;
    private NeoForgeNativeModBus() {}
    public static void install(IEventBus modBus) {
        if (bus != null && bus != modBus) throw new IllegalStateException("Mod bus already installed");
        bus = java.util.Objects.requireNonNull(modBus, "modBus");
    }
    public static IEventBus get() { return java.util.Objects.requireNonNull(bus, "Mod bus not installed"); }
}
