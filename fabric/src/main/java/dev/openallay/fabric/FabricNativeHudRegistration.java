package dev.openallay.fabric;

import dev.openallay.client.gui.GuideClientUiCoordinator;
import dev.openallay.client.gui.GuideGraphics;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/** Native ordered HUD registration; UI state and painting stay shared. */
final class FabricNativeHudRegistration {
    private FabricNativeHudRegistration() {}
    static void register(GuideClientUiCoordinator ui) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                MinecraftResourceIds.fromNamespaceAndPath("openallay", "guide_hud"),
                (graphics, deltaTracker) -> ui.extractRenderState(GuideGraphics.wrap(graphics)));
    }
}
