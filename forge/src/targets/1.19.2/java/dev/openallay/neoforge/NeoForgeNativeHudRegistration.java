package dev.openallay.neoforge;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import dev.openallay.client.gui.GuideGraphics;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

/** Actual float overlay below CHAT_PANEL; custom overlays have no automatic hide-GUI gate. */
final class NeoForgeNativeHudRegistration {
    private NeoForgeNativeHudRegistration() {}
    static void register(Consumer<GuideGraphics> render) {
        NeoForgeNativeModBus.get().addListener((RegisterGuiOverlaysEvent event) -> event.registerBelow(
                VanillaGuiOverlay.CHAT_PANEL.id(), "guide_hud",
                (gui, poseStack, partialTick, width, height) -> {
                    if (!Minecraft.getInstance().options.hideGui) render.accept(GuideGraphics.wrap(poseStack));
                }));
    }
}
