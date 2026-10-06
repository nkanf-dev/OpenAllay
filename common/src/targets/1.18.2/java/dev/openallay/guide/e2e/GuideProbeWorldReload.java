package dev.openallay.guide.e2e;

import dev.openallay.client.gui.MinecraftClientWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/** Observe the native reload outcome on client ticks; the game retains its load and screen ownership. */
final class GuideProbeWorldReload {
    private static Runnable pendingCancellation;
    private GuideProbeWorldReload() {}
    static void open(Minecraft client, String name, Runnable cancelled) {
        if (!client.isSameThread()) throw new IllegalStateException("World reload requires the client owner thread");
        if (pendingCancellation != null) throw new IllegalStateException("World reload is already pending");
        pendingCancellation = java.util.Objects.requireNonNull(cancelled, "cancelled");
        try { client.loadLevel(name); }
        catch (RuntimeException | Error failure) {
            pendingCancellation = null;
            throw failure;
        }
    }
    static void tick(Minecraft client) {
        if (pendingCancellation == null) return;
        if (!client.isSameThread()) throw new IllegalStateException("World reload observation requires the client owner thread");
        if (client.level != null && client.player != null) {
            pendingCancellation = null;
            return;
        }
        if (MinecraftClientWindow.overlay(client) != null) return;
        var screen = MinecraftClientWindow.screen(client);
        if (screen instanceof TitleScreen || screen instanceof SelectWorldScreen) {
            Runnable cancelled = pendingCancellation;
            pendingCancellation = null;
            cancelled.run();
        }
    }
}
