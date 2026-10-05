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
        assertTrue(renderer.contains("GuideGraphics"));
        assertTrue(renderer.contains("graphics.pushPose()"));
        assertTrue(renderer.contains("graphics.popPose()"));
        String graphics = source("common/src/main/java/dev/openallay/client/gui/GuideGraphics.java");
        assertTrue(graphics.contains("GuiGraphicsExtractor"));
        assertTrue(graphics.contains("graphics.pose().pushMatrix()"));
        assertTrue(graphics.contains("graphics.pose().popMatrix()"));
        assertFalse(renderer.contains(".pose()"));
        assertTrue(renderer.contains("disableScissor()"));
        for (String forbidden : new String[]{"setScreen(", "releaseMouse(", "grabMouse(", "forActor(",
                "new GuideService", "capture(", "Tokenizer", "Files.", "history.page", "ask("}) {
            assertFalse(renderer.contains(forbidden), forbidden);
        }
        assertEquals(0, GuideHudRenderer.backgroundColor(0) >>> 24);
        assertEquals(255, GuideHudRenderer.backgroundColor(1) >>> 24);
        assertEquals(255, GuideHudRenderer.textColor() >>> 24);
    }
    @Test void compactViewportUsesTypedNativeCardsAndFullScrollRatherThanPreviewLineCaps() throws Exception {
        String lite = source("common/src/main/java/dev/openallay/client/gui/hud/GuideChatLiteScreen.java");
        String results = source("common/src/main/java/dev/openallay/client/gui/hud/GuideHudResultRenderer.java");
        assertTrue(lite.contains("presenter.projectInteractive("));
        assertTrue(lite.contains("results.scroll().wheel(scrollY)"));
        String keyInput = source("common/src/main/java/dev/openallay/client/gui/GuideKeyInput.java");
        assertTrue(keyInput.contains("InputConstants.KEY_PAGEUP -> GuideKeyIntent.PAGE_UP"));
        assertTrue(keyInput.contains("InputConstants.KEY_PAGEDOWN -> GuideKeyIntent.PAGE_DOWN"));
        assertTrue(lite.contains("case PAGE_UP -> { scrollResults(() -> results.scroll().page(-1))"));
        assertTrue(lite.contains("case PAGE_DOWN -> { scrollResults(() -> results.scroll().page(1))"));
        assertTrue(lite.contains("scrollbarThumbHeight()"));
        assertTrue(lite.contains("recipes.openExact(value.reference())"));
        assertFalse(lite.contains("Math.min(6, lines.size())"));
        assertFalse(lite.contains("font.split(Component.literal(preview)"));
        assertTrue(results.contains("new MinecraftSemanticRenderer(new MinecraftSemanticResolver())"));
        assertTrue(results.contains("GuideHudToolCards.project(tool"));
        assertTrue(results.contains("new NativeDomainViewBinding.Recipe("));
        assertTrue(results.contains("nativeViews.endFrame()"));
        assertTrue(results.contains("nativeViews.close()"));
        assertTrue(results.contains("cachedFont != font"));
        assertTrue(results.contains("cachedLanguage != language"));
        assertTrue(results.contains("cachedPresentation"));
        assertTrue(results.contains("++extractedFrame"));
        assertTrue(results.contains("boolean painted = nativeRecipe("));
        assertTrue(results.contains("if (painted && bounds.x() < viewport.right()"));
        assertTrue(results.contains("paintedRecipes.contains(line.nodeId())"));
        assertTrue(results.contains("List.copyOf(paintedNodes), lastPaintedText"));
        assertFalse(results.contains("lastPaintedText = row.layout().narration()"));
        for (String forbidden : new String[]{"new GuideService", "Files.", "requestHistoryWindow(", "ModelProvider"}) assertFalse(results.contains(forbidden));
    }

    @Test void dualLoaderRegistersOrderedNativeElementsAndNeoBeforeStartup() throws Exception {
        String fabric = source("fabric/src/main/java/dev/openallay/fabric/OpenAllayFabricClient.java");
        String neo = source("neoforge/src/main/java/dev/openallay/neoforge/OpenAllayNeoForgeClient.java");
        String build = source("fabric/build.gradle");
        assertTrue(fabric.contains("FabricNativeHudRegistration.register(ui)"));
        String registration = source("fabric/src/main/java/dev/openallay/fabric/FabricNativeHudRegistration.java");
        assertTrue(registration.contains("HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT"));
        String legacyRegistration = source("fabric/src/targets/1.21.5/java/dev/openallay/fabric/FabricNativeHudRegistration.java");
        assertTrue(legacyRegistration.contains("HudLayerRegistrationCallback.EVENT.register"));
        assertTrue(legacyRegistration.contains("IdentifiedLayer.CHAT"));
        String olderRegistration = source("fabric/src/targets/1.21.3/java/dev/openallay/fabric/FabricNativeHudRegistration.java");
        String olderLayer = source("fabric/src/targets/1.21.3/java/dev/openallay/fabric/mixin/FabricGuideHudLayerMixin.java");
        assertTrue(olderRegistration.contains("current.extractRenderState(GuideGraphics.wrap(graphics))"));
        assertTrue(olderLayer.contains("@Inject(method = \"renderChat\", at = @At(\"HEAD\"))"));
        assertFalse(olderRegistration.contains("HudRenderCallback"));
        assertTrue(build.contains("fabric-rendering-v1:${fabric_rendering_version}"));
        assertTrue(source("gradle/minecraft-targets/26.2.properties")
                .contains("fabric_rendering_version=25.1.6+46a6d00c9c"));
        assertTrue(neo.contains("RegisterGuiLayersEvent"));
        assertTrue(neo.contains("VanillaGuiLayers.CHAT"));
        assertTrue(neo.indexOf("modBus.addListener((RegisterGuiLayersEvent") < neo.indexOf("private static void start("));
        assertTrue(neo.contains("if (current != null) current.extractRenderState(dev.openallay.client.gui.GuideGraphics.wrap(graphics))"));
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
        assertTrue(lite.contains("extends dev.openallay.client.gui.GuideNativeScreen"));
        String screenBinding = source("common/src/main/java/dev/openallay/client/gui/GuideNativeScreen.java");
        assertTrue(screenBinding.contains("extends Screen"));
        assertTrue(screenBinding.contains("return guideKeyPressed(GuideNativeInput.capture(event))"));
        assertTrue(lite.contains("GuideNativeMultilineText.create("));
        String editor = source("common/src/main/java/dev/openallay/client/gui/GuideNativeMultilineText.java");
        assertTrue(editor.contains("MultiLineEditBox.builder()"));
        assertTrue(editor.contains("editor.setValue(value, bypassLineLimit)"));
        String legacyEditor = source("common/src/targets/1.21.5/java/dev/openallay/client/gui/GuideNativeMultilineText.java");
        assertTrue(legacyEditor.contains("new MultiLineEditBox(font, x, y, width, height, placeholder, narration)"));
        assertTrue(legacyEditor.contains("editor.setValue(value)"));
        assertFalse(lite.contains("MultiLineEditBox.builder()"));
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
        String coordinator = source("engine-core/src/main/java/dev/openallay/client/presentation/GuidePresentationCoordinator.java");
        String facade = source("common/src/main/java/dev/openallay/client/gui/GuideClientUiCoordinator.java");
        String host = source("common/src/main/java/dev/openallay/client/gui/NativeGuidePresentationHost.java");
        String factorySource = source("common/src/main/java/dev/openallay/client/voice/VoiceClientRuntimes.java");
        assertTrue(facade.contains("drafts -> VoiceClientRuntimes.create(configDirectory, drafts, dispatcher::execute)"));
        assertTrue(factorySource.contains("return new VoiceClientRuntime(configDirectory, drafts, clientDispatcher,"));
        assertTrue(factorySource.contains("Map.copyOf(credentialEnvironment), new OpenAlCapture()"));
        assertTrue(host.contains("withVoiceActions(voice)"));
        assertTrue(host.contains(".withNotifications(view.notifications()).withVoice(view.voice())"));
        assertTrue(coordinator.contains("state.selectSession(bound.snapshot().selectedSession())"));
        assertTrue(coordinator.contains("bound.snapshot().actorId(), state.ownerId(), state.generation()"));
        assertTrue(coordinator.contains("bound.presentationSessionOwner(state.selectedSession())"));
        assertTrue(coordinator.contains("target.uiOwnerId(), target.uiGeneration()"));
        assertTrue(coordinator.contains("target.sessionId(), target.draftRevision()"));
        assertTrue(coordinator.contains("bound.presentationGeneration().equals(target.connectionGeneration())"));
        assertTrue(coordinator.contains("bound.presentationSessionOwner(target.sessionId()).filter(target.sessionOwner()::equals).isPresent()"));
        assertTrue(coordinator.contains("state.insertTranscript(captured, text, observationForVoice(target).orElse(null))"));
        assertTrue(coordinator.contains("state.leaseObservation(state.captureObservation(state.selectedSession()))"));
        assertTrue(coordinator.contains("finally { releaseVoiceObservation(target); }"));
        assertTrue(coordinator.contains("voiceObservations.remove(target)"));
        assertTrue(coordinator.contains("if (lease != null) lease.close()"));
        assertTrue(coordinator.contains("service == bound && voiceAdmissionAllowed(target)"));
        assertTrue(coordinator.contains("service.followUp(target.sessionId(), target.sessionOwner(), input, fence)"));
        int factory = coordinator.indexOf("voices.apply(new VoiceRuntime.DraftPort()");
        int factoryEnd = coordinator.indexOf("settingsBinding =", factory);
        assertTrue(factory >= 0 && factoryEnd > factory);
        String draftPort = coordinator.substring(factory, factoryEnd);
        for (String forbidden : new String[]{".ask(", ".steer(", ".followUp(", "forActor(", "new GuideService"}) {
            assertFalse(draftPort.contains(forbidden), forbidden);
            assertFalse(factorySource.contains(forbidden), forbidden);
        }
        assertTrue(host.contains("OpenAllayKeyMappings.VOICE_PTT.isDown()"));
        assertTrue(coordinator.contains("boolean physicalDown = input.pttDown()"));
        assertTrue(coordinator.contains("gameplay && physicalDown && !pttDown && feedback && voice.input().enabled()"));
        assertTrue(coordinator.contains("voice.input().pressPtt()"));
        assertTrue(coordinator.contains("pttDown && !physicalDown"));
        assertTrue(coordinator.contains("voice.input().release()"));
        String ptt = coordinator.substring(coordinator.indexOf("if (gameplay && physicalDown"),
                coordinator.indexOf("} else if (pttDown && !physicalDown)"));
        assertTrue(ptt.indexOf("services.forActor(facts.actorId())") < ptt.indexOf("voice.input().pressPtt()"));
    }
    @Test void voiceFeedbackVisibilityCancelsCaptureAndFencesTheDraftBeforeCleanup() throws Exception {
        String coordinator = source("engine-core/src/main/java/dev/openallay/client/presentation/GuidePresentationCoordinator.java");
        String host = source("common/src/main/java/dev/openallay/client/gui/NativeGuidePresentationHost.java");
        assertTrue(host.contains("MinecraftClientWindow.overlay(minecraft) != null, MinecraftClientWindow.hudHidden(minecraft), minecraft.isWindowActive()"));
        assertTrue(host.contains("screen instanceof OpenAllayScreen ? Surface.GUIDE"));
        assertTrue(host.contains("screen instanceof GuideChatLiteScreen ? Surface.HUD_INPUT"));
        assertTrue(coordinator.contains("!facts.overlayPresent() && !facts.hudHidden()"));
        for (String surface : new String[]{"GAMEPLAY", "GUIDE", "HUD_INPUT"}) {
            assertTrue(coordinator.contains("facts.surface() == GuidePresentationHost.Surface." + surface));
        }
        assertTrue(coordinator.contains("setFeedbackVisible(feedback)"));
        assertTrue(coordinator.contains("voice.input().tick(current.windowActive(), current.connected(), physicalDown, feedback)"));
        String closeState = coordinator.substring(coordinator.indexOf("private void closeState()"),
                coordinator.indexOf("private static void close("));
        int stateClosed = closeState.indexOf("state.close()");
        int voiceCancelled = closeState.indexOf("voice.input().cancel(");
        assertTrue(stateClosed >= 0 && voiceCancelled > stateClosed);
        assertTrue(voiceCancelled < closeState.indexOf("state = null"));
        assertTrue(closeState.contains("closeVoiceObservations()"));
        String runtime = source("engine-core/src/main/java/dev/openallay/client/voice/VoiceRuntime.java");
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
        String presentation = source("engine-core/src/main/java/dev/openallay/client/voice/VoiceStatusPresentation.java");
        assertTrue(presentation.contains("default -> notice(\"failed\", \"retry\", true)"));
        assertFalse(presentation.contains("PREFIX + code"));
    }
}
