package dev.openallay.fabric;

import dev.openallay.client.gui.GuideClientUiCoordinator;
import dev.openallay.client.gui.GuideGraphics;
import net.minecraft.client.gui.GuiGraphics;

/** Native CHAT-layer binding before Fabric supplied ordered HUD registration. */
public final class FabricNativeHudRegistration {
    private static volatile GuideClientUiCoordinator ui;
    private FabricNativeHudRegistration() {}

    static void register(GuideClientUiCoordinator coordinator) { ui = coordinator; }

    public static void beforeChat(GuiGraphics graphics) {
        GuideClientUiCoordinator current = ui;
        if (current != null) current.extractRenderState(GuideGraphics.wrap(graphics));
    }
}
