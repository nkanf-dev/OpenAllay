package dev.openallay.neoforge;

import dev.openallay.client.gui.GuideGraphics;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

/** Real Chat event paints once before native chat translation. Canceled chat suppresses HUD. */
final class NeoForgeNativeHudRegistration {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private NeoForgeNativeHudRegistration() {}
    static void register(Consumer<GuideGraphics> render) {
        if (!REGISTERED.compareAndSet(false, true)) return;
        // Invoked inside client setup enqueueWork. Default listener does not receive canceled events.
        MinecraftForge.EVENT_BUS.addListener((RenderGameOverlayEvent.Chat event) -> {
            if (!Minecraft.getInstance().options.hideGui && !event.isCanceled()) {
                render.accept(GuideGraphics.wrap(event.getMatrixStack()));
            }
        });
    }
}
