package dev.openallay.neoforge;

import dev.openallay.platform.minecraft.MinecraftResourceIds;
import java.util.function.Consumer;
import dev.openallay.client.gui.GuideGraphics;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Actual modern before-chat native render layer. */
final class NeoForgeNativeHudRegistration {
    private NeoForgeNativeHudRegistration() {}
    static void register(Consumer<GuideGraphics> render) {
        NeoForgeNativeModBus.get().addListener((RegisterGuiLayersEvent event) -> event.registerBelow(
                VanillaGuiLayers.CHAT, MinecraftResourceIds.fromNamespaceAndPath("openallay", "guide_hud"),
                (graphics, deltaTracker) -> render.accept(GuideGraphics.wrap(graphics))));
    }
}
