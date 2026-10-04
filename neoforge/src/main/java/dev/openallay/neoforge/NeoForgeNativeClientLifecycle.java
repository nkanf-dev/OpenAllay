package dev.openallay.neoforge;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Native client lifecycle timing; initialization and cleanup behavior stay shared. */
final class NeoForgeNativeClientLifecycle {
    private NeoForgeNativeClientLifecycle() {}

    static void onStarted(Consumer<Minecraft> started) {
        NeoForge.EVENT_BUS.addListener((ClientStartedEvent event) -> started.accept(event.getClient()));
    }

    static void onStopping(Runnable stopping) {
        NeoForge.EVENT_BUS.addListener((ClientStoppingEvent event) -> stopping.run());
    }
}
