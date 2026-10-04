package dev.openallay.neoforge;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/** Native lifecycle before separate client events: first pre-tick and orderly game shutdown. */
final class NeoForgeNativeClientLifecycle {
    private NeoForgeNativeClientLifecycle() {}

    static void onStarted(Consumer<Minecraft> started) {
        AtomicBoolean first = new AtomicBoolean(true);
        // The newer ClientStartedEvent also fires immediately before the first tick,
        // while the loading overlay/resource reload can still be active.
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> {
            if (first.compareAndSet(true, false)) started.accept(Minecraft.getInstance());
        });
    }

    static void onStopping(Runnable stopping) {
        // This class is installed only on the physical client. The native event
        // fires once with its GL context valid; integrated-server stop does not fire it.
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> stopping.run());
    }
}
