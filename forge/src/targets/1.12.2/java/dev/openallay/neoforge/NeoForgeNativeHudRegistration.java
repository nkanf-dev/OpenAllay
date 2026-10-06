package dev.openallay.neoforge;

import dev.openallay.client.gui.GuideGraphics;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Actual pre-chat1.12 overlay; render composition remains in the shared HUD owner. */
public final class NeoForgeNativeHudRegistration {
    private NeoForgeNativeHudRegistration() {}
    static void register(Consumer<GuideGraphics> render) {
        MinecraftForge.EVENT_BUS.register(new HudListener(render));
    }
    public static final class HudListener {
        private final Consumer<GuideGraphics> render;
        HudListener(Consumer<GuideGraphics> render) { this.render = render; }
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void render(RenderGameOverlayEvent.Pre event) {
            if (event.getType() != RenderGameOverlayEvent.ElementType.CHAT
                    || Minecraft.getMinecraft().gameSettings.hideGUI) return;
            GuideGraphics graphics = GuideGraphics.wrap();
            graphics.paint(() -> render.accept(graphics));
        }
    }
}
