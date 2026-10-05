package dev.openallay.neoforge;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import dev.openallay.client.gui.GuideGraphics;
import net.minecraftforge.client.gui.ForgeIngameGui;
import net.minecraftforge.client.gui.OverlayRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Actual Forge 1.18.2 overlay registration before the chat panel. */
final class NeoForgeNativeHudRegistration {
    private NeoForgeNativeHudRegistration() {}
    static void register(Consumer<GuideGraphics> render) {
        NeoForgeNativeModBus.get().addListener((FMLClientSetupEvent event) ->
                OverlayRegistry.registerOverlayBelow(ForgeIngameGui.CHAT_PANEL_ELEMENT, "guide_hud",
                        (gui, poseStack, partialTick, width, height) -> {
                            if (!Minecraft.getInstance().options.hideGui) render.accept(GuideGraphics.wrap(poseStack));
                        }));
    }
}
