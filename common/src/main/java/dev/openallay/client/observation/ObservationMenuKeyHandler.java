package dev.openallay.client.observation;

import dev.openallay.client.gui.MinecraftClientWindow;

import dev.openallay.client.gui.OpenAllayKeyMappings;
import java.util.Objects;
import java.util.function.Consumer;

import dev.openallay.client.gui.GuideInputKey;
import dev.openallay.client.gui.GuideNativeInput;

/** Handles an unconsumed Guide key in a native game menu using its configured key mapping. */
public final class ObservationMenuKeyHandler {
    private static volatile Consumer<net.minecraft.client.Minecraft> opener;
    private ObservationMenuKeyHandler() {}

    public static void configure(Consumer<net.minecraft.client.Minecraft> open) {
        opener = Objects.requireNonNull(open, "open");
        GuideNativeMenuKeyObservation.configure();
    }

    public static void afterUnhandledKey(net.minecraft.client.Minecraft client, int action, GuideInputKey event) {
        if (action != 1 || !dev.openallay.client.context.MinecraftClientContextFacts.active(client) || dev.openallay.client.context.MinecraftFocusNativeFacts.overlay(client) != null) return;
        net.minecraft.client.gui.screens.Screen screen = MinecraftClientWindow.screen(client);
        if (screen == null || MinecraftClientViewCapture.owns(screen) || dev.openallay.client.gui.GuideNativeWindowState.keyBindingScreen(screen)
                || dev.openallay.client.gui.GuideTextInputFocus.isTextFocused(screen)
                || !GuideNativeInput.matches(OpenAllayKeyMappings.OPEN_GUIDE, event)) return;
        Consumer<net.minecraft.client.Minecraft> current = opener;
        if (current != null) current.accept(client);
    }
}
