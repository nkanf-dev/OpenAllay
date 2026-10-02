package dev.openallay.client.gui.hud;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Native API/source boundary guards supplement, but do not replace, dual-loader compilation. */
class GuideHudNativeContractsTest {
    private static Path root() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("repository root unavailable");
        return path;
    }
    private static String source(String relative) throws Exception { return Files.readString(root().resolve(relative)); }
    @Test void renderIsPassiveOpaqueTextAndUsesActual2dExtractionApi() throws Exception {
        String renderer = source("common/src/main/java/dev/openallay/client/gui/hud/GuideHudRenderer.java");
        assertTrue(renderer.contains("GuiGraphicsExtractor"));
        assertTrue(renderer.contains("pushMatrix()"));
        assertTrue(renderer.contains("popMatrix()"));
        assertTrue(renderer.contains("disableScissor()"));
        for (String forbidden : new String[]{"setScreen(", "releaseMouse(", "grabMouse(", "forActor(",
                "new GuideService", "capture(", "Tokenizer", "Files.", "history.page", "ask("}) {
            assertFalse(renderer.contains(forbidden), forbidden);
        }
        assertEquals(0, GuideHudRenderer.backgroundColor(0) >>> 24);
        assertEquals(255, GuideHudRenderer.backgroundColor(1) >>> 24);
        assertEquals(255, GuideHudRenderer.textColor() >>> 24);
    }
    @Test void dualLoaderRegistersOrderedNativeElementsAndNeoBeforeStartup() throws Exception {
        String fabric = source("fabric/src/main/java/dev/openallay/fabric/OpenAllayFabricClient.java");
        String neo = source("neoforge/src/main/java/dev/openallay/neoforge/OpenAllayNeoForgeClient.java");
        String build = source("fabric/build.gradle");
        assertTrue(fabric.contains("HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT"));
        assertTrue(build.contains("fabric-rendering-v1:25.1.6+46a6d00c9c"));
        assertTrue(neo.contains("RegisterGuiLayersEvent"));
        assertTrue(neo.contains("VanillaGuiLayers.CHAT"));
        assertTrue(neo.indexOf("modBus.addListener((RegisterGuiLayersEvent") < neo.indexOf("private static void start("));
        assertTrue(neo.contains("if (current != null) current.extractRenderState(graphics)"));
    }
    @Test void managerBindingOccursBeforeCommandRegistration() throws Exception {
        for (String loader : new String[]{"fabric/src/main/java/dev/openallay/fabric/OpenAllayFabricClient.java",
                "neoforge/src/main/java/dev/openallay/neoforge/OpenAllayNeoForgeClient.java"}) {
            String source = source(loader);
            assertTrue(source.indexOf("new GuideClientUiCoordinator") < source.indexOf("new GuideCommandFacade"));
        }
    }
    @Test void liteInputIsExplicitNativeAndNeverForwardsGameplayMappings() throws Exception {
        String lite = source("common/src/main/java/dev/openallay/client/gui/hud/GuideChatLiteScreen.java");
        assertTrue(lite.contains("extends Screen"));
        assertTrue(lite.contains("MultiLineEditBox.builder()"));
        assertTrue(lite.contains("setInitialFocus() {}"));
        assertTrue(lite.contains("Surface.HUD_INPUT"));
        assertFalse(lite.contains("KeyMapping.set("));
        assertFalse(lite.contains("releaseMouse("));
        assertFalse(lite.contains("grabMouse("));
        var small = GuideChatLiteScreen.Card.calculate(240, 180);
        assertTrue(small.x() >= 0 && small.y() >= 0);
        assertTrue(small.x() + small.width() <= 240);
        assertTrue(small.y() + small.height() <= 180);
    }
}
