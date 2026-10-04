package dev.openallay.fabric;

import dev.openallay.client.gui.GuideClientUiCoordinator;
import dev.openallay.client.gui.GuideGraphics;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;

/** Native layered HUD registration before CHAT, retaining its inherited visibility rule. */
final class FabricNativeHudRegistration {
    private FabricNativeHudRegistration() {}
    static void register(GuideClientUiCoordinator ui) {
        HudLayerRegistrationCallback.EVENT.register(layers -> layers.attachLayerBefore(
                IdentifiedLayer.CHAT, MinecraftResourceIds.fromNamespaceAndPath("openallay", "guide_hud"),
                (graphics, deltaTracker) -> ui.extractRenderState(GuideGraphics.wrap(graphics))));
    }
}
