package dev.openallay.neoforge;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import dev.openallay.client.lifecycle.MinecraftClientShutdownCallbacks;

/** Native lifecycle before separate client events: first pre-tick and orderly game shutdown. */
final class NeoForgeNativeClientLifecycle {
    private NeoForgeNativeClientLifecycle() {}

    static void onStarted(Consumer<Minecraft> started) {
        AtomicBoolean first = new AtomicBoolean(true);
        // The newer ClientStartedEvent also fires immediately before the first tick,
        // while the loading overlay/resource reload can still be active.
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.START && first.compareAndSet(true, false)) started.accept(Minecraft.getInstance());
        });
    }

    static void onStopping(Runnable stopping) {
        MinecraftClientShutdownCallbacks.install(stopping);
    }
}
