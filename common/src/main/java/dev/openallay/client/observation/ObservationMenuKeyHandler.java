package dev.openallay.client.observation;

import dev.openallay.client.gui.MinecraftClientWindow;

import dev.openallay.client.gui.OpenAllayKeyMappings;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;

/** Handles an unconsumed Guide key in a native game menu using its configured key mapping. */
public final class ObservationMenuKeyHandler {
    private static volatile Consumer<Minecraft> opener;
    private ObservationMenuKeyHandler() {}

    public static void configure(Consumer<Minecraft> open) { opener = Objects.requireNonNull(open, "open"); }

    public static void afterUnhandledKey(Minecraft client, int action, KeyEvent event) {
        if (action != 1 || client.player == null || client.level == null || MinecraftClientWindow.overlay(client) != null) return;
        var screen = MinecraftClientWindow.screen(client);
        if (screen == null || MinecraftClientViewCapture.owns(screen) || screen instanceof KeyBindsScreen
                || screen.getFocused() instanceof EditBox || screen.getFocused() instanceof MultiLineEditBox
                || !OpenAllayKeyMappings.OPEN_GUIDE.matches(event)) return;
        Consumer<Minecraft> current = opener;
        if (current != null) current.accept(client);
    }
}
