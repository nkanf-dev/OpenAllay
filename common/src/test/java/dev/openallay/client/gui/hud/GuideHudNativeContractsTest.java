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
    @Test void voiceKeyAndFactoryUseExplicitAdmissionAndTheCurrentDraftOwner() throws Exception {
        String keys = source("common/src/main/java/dev/openallay/client/gui/OpenAllayKeyMappings.java");
        assertTrue(keys.contains("VOICE_PTT = unbound(\"key.openallay.voice_ptt\")"));
        assertTrue(keys.contains("INTERACT_HUD, VOICE_PTT"));
        String coordinator = source("common/src/main/java/dev/openallay/client/gui/GuideClientUiCoordinator.java");
        assertTrue(coordinator.contains("VoiceClientRuntime.create(configDirectory, new VoiceRuntime.DraftPort()"));
        assertTrue(coordinator.contains("withVoiceActions(voice.settings())"));
        assertTrue(coordinator.contains(".withNotifications(notifications).withVoice(voice.input())"));
        assertTrue(coordinator.contains("state.selectSession(bound.snapshot().selectedSession())"));
        assertTrue(coordinator.contains("state.ownerId(), state.selectedSession()"));
        assertTrue(coordinator.contains("state.generation(), target.sessionId(), target.draftRevision()"));
        assertTrue(coordinator.contains("state.insertTranscript(captured, text)"));
        int factory = coordinator.indexOf("voice = VoiceClientRuntime.create(");
        int factoryEnd = coordinator.indexOf("settingsBinding =", factory);
        assertTrue(factory >= 0 && factoryEnd > factory);
        String draftPort = coordinator.substring(factory, factoryEnd);
        for (String forbidden : new String[]{".ask(", ".steer(", ".followUp(", "forActor(", "new GuideService"}) {
            assertFalse(draftPort.contains(forbidden), forbidden);
        }
        assertTrue(coordinator.contains("OpenAllayKeyMappings.VOICE_PTT.isDown()"));
        assertTrue(coordinator.contains("gameplay && physicalDown && !pttDown && feedback && voice.input().enabled()"));
        assertTrue(coordinator.contains("voice.input().pressPtt()"));
        assertTrue(coordinator.contains("pttDown && !physicalDown"));
        assertTrue(coordinator.contains("voice.input().release()"));
    }
    @Test void voiceFeedbackVisibilityCancelsCaptureAndFencesTheDraftBeforeCleanup() throws Exception {
        String coordinator = source("common/src/main/java/dev/openallay/client/gui/GuideClientUiCoordinator.java");
        assertTrue(coordinator.contains("minecraft.gui.overlay() == null && !minecraft.gui.hud.isHidden()"));
        assertTrue(coordinator.contains("activeScreen instanceof OpenAllayScreen"));
        assertTrue(coordinator.contains("activeScreen instanceof GuideChatLiteScreen"));
        assertTrue(coordinator.contains("setFeedbackVisible(feedback)"));
        assertTrue(coordinator.contains("voice.input().tick(minecraft.isWindowActive()"));
        assertTrue(coordinator.contains("physicalDown, feedback)"));
        String closeState = coordinator.substring(coordinator.indexOf("private void closeState()"));
        int stateClosed = closeState.indexOf("state.close()");
        int voiceCancelled = closeState.indexOf("voice.input().cancel(");
        assertTrue(stateClosed >= 0 && voiceCancelled > stateClosed);
        assertTrue(voiceCancelled < closeState.indexOf("state = null"));
        String runtime = source("common/src/main/java/dev/openallay/client/voice/VoiceRuntime.java");
        assertTrue(runtime.contains("if (!visible && active) cancel(CancelReason.FEEDBACK_HIDDEN)"));
        assertTrue(runtime.contains("if (!visibleFeedback) cancel = CancelReason.FEEDBACK_HIDDEN"));
        assertTrue(runtime.contains("else if (!connected) cancel = CancelReason.DISCONNECTED"));
        assertTrue(runtime.contains("else if (!focused) cancel = CancelReason.FOCUS_LOST"));
        assertTrue(runtime.contains("cancel = CancelReason.KEY_LOST"));
        String lite = source("common/src/main/java/dev/openallay/client/gui/hud/GuideChatLiteScreen.java");
        assertTrue(lite.contains("voice.cancel(VoiceRuntime.CancelReason.SCREEN_CLOSED)"));
    }
    @Test void voiceStatusExtractionIsPassiveAndUsesClosedPlayerFacingPresentation() throws Exception {
        String indicator = source("common/src/main/java/dev/openallay/client/gui/hud/GuideVoiceIndicator.java");
        assertTrue(indicator.contains("if (!status.indicatorVisible()) return"));
        assertTrue(indicator.contains("VoiceStatusPresentation.describe(status)"));
        assertTrue(indicator.contains("feedback.translationKey()"));
        assertTrue(indicator.contains("feedback.actionTranslationKey()"));
        assertTrue(indicator.contains("Component.translatable(key)"));
        for (String forbidden : new String[]{"status.code()", "status.source()", "Component.literal(",
                "setScreen(", "forActor(", "capture(", "Files.", ".ask(", ".press(", ".pressPtt("}) {
            assertFalse(indicator.contains(forbidden), forbidden);
        }
        String lite = source("common/src/main/java/dev/openallay/client/gui/hud/GuideChatLiteScreen.java");
        assertTrue(lite.contains("GuideVoiceIndicator.extract(graphics, minecraft, voice)"));
        String presentation = source("common/src/main/java/dev/openallay/client/voice/VoiceStatusPresentation.java");
        assertTrue(presentation.contains("default -> notice(\"failed\", \"retry\", true)"));
        assertFalse(presentation.contains("PREFIX + code"));
    }
}
