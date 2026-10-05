package dev.openallay.client.observation;

import dev.openallay.client.gui.MinecraftClientWindow;

import dev.openallay.client.gui.OpenAllayKeyMappings;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import dev.openallay.client.gui.GuideInputKey;
import dev.openallay.client.gui.GuideNativeInput;

/** Handles an unconsumed Guide key in a native game menu using its configured key mapping. */
public final class ObservationMenuKeyHandler {
    private static volatile Consumer<Minecraft> opener;
    private ObservationMenuKeyHandler() {}

    public static void configure(Consumer<Minecraft> open) { opener = Objects.requireNonNull(open, "open"); }

    public static void afterUnhandledKey(Minecraft client, int action, GuideInputKey event) {
        if (action != 1 || client.player == null || client.level == null || MinecraftClientWindow.overlay(client) != null) return;
        var screen = MinecraftClientWindow.screen(client);
        if (screen == null || MinecraftClientViewCapture.owns(screen) || dev.openallay.client.gui.GuideNativeWindowState.keyBindingScreen(screen)
                || screen.getFocused() instanceof EditBox || screen.getFocused() instanceof MultiLineEditBox
                || !GuideNativeInput.matches(OpenAllayKeyMappings.OPEN_GUIDE, event)) return;
        Consumer<Minecraft> current = opener;
        if (current != null) current.accept(client);
    }
}
