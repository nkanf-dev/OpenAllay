package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.guide.ui.GuideUiLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source and geometry contracts only. Actual native mouse callbacks require packaged-client E2E. */
final class OpenAllayScreenInputFocusContractsTest {
    private static String source(String relative) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("repository root unavailable");
        return Files.readString(root.resolve("common/src/main/java/" + relative));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature);
        int open = source.indexOf('{', start);
        int depth = 1;
        int end = open + 1;
        while (depth > 0 && end < source.length()) {
            char value = source.charAt(end++);
            if (value == '{') depth++;
            else if (value == '}') depth--;
        }
        assertEquals(0, depth, signature);
        return source.substring(start, end);
    }

    @Test
    void fullscreenBlursBeforeModalOrContentDispatchAndKeepsNativeDispatchLast() throws Exception {
        String screen = source("dev/openallay/client/gui/OpenAllayScreen.java");
        String click = method(screen, "public boolean guideMouseClicked(");
        assertNativeMouseClickBinding();
        int left = click.indexOf("if (GuideNativeInput.isLeftClick(event))");
        int blur = click.indexOf("if (!composerContains(event.x(), event.y())) GuideNativeFocus.clear(this)");
        int modal = click.indexOf("if (sessionOverlay || overflowOpen)");
        assertTrue(left >= 0 && blur > left && modal > blur);
        assertTrue(click.contains("return true; // A modal's background never routes hidden transcript actions."));
        assertTrue(click.contains("HitKind.MODEL"));
        assertTrue(click.contains("GuideUiClickRoute.resolveDetail("));
        assertTrue(click.contains("closeDetail()"));
        assertTrue(click.contains("hit.action().run()"));
        assertTrue(click.endsWith("return super.guideMouseClicked(event, doubleClick);\n    }"));
        assertNoDraftMutation(click);
        assertFalse(click.contains("setFocused(composer)"), "native input dispatch owns click-to-focus");
    }

    @Test
    void liteBlursBeforeScrollbarOrResultRoutingAndKeepsNativeButtonsAndInput() throws Exception {
        String screen = source("dev/openallay/client/gui/hud/GuideChatLiteScreen.java");
        String click = method(screen, "public boolean guideMouseClicked(");
        assertNativeMouseClickBinding();
        int blur = click.indexOf("if (GuideNativeInput.isLeftClick(event) && !composerContains(event.x(), event.y())) GuideNativeFocus.clear(this)");
        int scrollbar = click.indexOf("scrollbar.contains(event.x(), event.y())");
        assertTrue(blur >= 0 && scrollbar > blur);
        assertTrue(click.contains("draggingScrollbar = true"));
        assertTrue(click.contains("scrollAt(event.y())"));
        assertTrue(click.contains("resultAction(hit.action())"));
        assertTrue(click.endsWith("return super.guideMouseClicked(event, doubleClick);\n    }"));
        assertNoDraftMutation(click);
        assertFalse(click.contains("setFocused(composer)"));
    }

    @Test
    void bothSurfacesUseActualWidgetRectangleAndNativeHoverNotWholeComposerPanel() throws Exception {
        for (String path : new String[] {"dev/openallay/client/gui/OpenAllayScreen.java",
                "dev/openallay/client/gui/hud/GuideChatLiteScreen.java"}) {
            String helper = method(source(path), "private boolean composerContains(");
            assertTrue(helper.contains("if (composer == null) return false"));
            assertTrue(helper.contains("composer.getX(), composer.getY(), composer.getWidth(), composer.getHeight()"));
            assertTrue(helper.contains("input.contains(x, y) && composer.isMouseOver(x, y)"));
            assertFalse(helper.contains("layout.composer()"));
            assertFalse(helper.contains("composerExtras"));
            assertNoDraftMutation(helper);
        }
        for (int[] size : new int[][] {{320, 240}, {427, 320}, {900, 500}}) {
            GuideUiLayout layout = GuideUiLayout.calculate(size[0], size[1], false,
                    90, 60, 48, 54, true, true, true, 4);
            GuideUiLayout.Rect input = layout.composerExtras(true, true, 4).input();
            assertTrue(input.contains(input.x(), input.y()));
            assertTrue(input.contains(input.right() - 0.1, input.bottom() - 0.1));
            assertFalse(input.contains(input.right(), input.y()));
            assertFalse(input.contains(input.x(), input.bottom()));
            GuideUiLayout.Rect send = layout.composerControls().send();
            assertFalse(input.contains(send.x() + send.width() / 2.0, send.y() + send.height() / 2.0));
        }
    }

    @Test
    void voiceDraftFocusCanOnlyRestoreAnUnownedCurrentComposerWithoutPendingEdits() throws Exception {
        String helper = method(source("dev/openallay/client/gui/OpenAllayScreen.java"),
                "public boolean focusComposerAfterVoiceDraft()");
        for (String guard : new String[] {"minecraft == null", "MinecraftClientWindow.screen(minecraft) != this", "composer == null",
                "!composer.active", "!composer.visible", "getFocused() != null", "sessionOverlay",
                "overflowOpen", "modelSelectorOpen", "detailOpen()", "draftIntent().editing()"}) {
            assertTrue(helper.contains(guard), guard);
        }
        assertTrue(helper.indexOf("return false") < helper.indexOf("setFocused(composer)"));
        assertTrue(helper.contains("return getFocused() == composer"));
        assertNoDraftMutation(helper);
        assertFalse(helper.contains("service."));
        assertFalse(helper.contains("GuideNativeFocus.clear(this)"));
        assertFalse(helper.contains("clearFocus()"));
    }

    @Test
    void firstInitializationOwnsTextFocusButNativeResizeRebuildPreservesPriorFocus() throws Exception {
        String screen = source("dev/openallay/client/gui/OpenAllayScreen.java");
        String init = method(screen, "protected void init()");
        assertTrue(init.contains("else if (!presentationInitialized) setInitialFocus(composer)"));
        assertTrue(init.contains("presentationInitialized = true"));
        String nativeInitial = method(screen, "protected void guideInitialFocus()");
        assertFalse(nativeInitial.contains("super.setInitialFocus"));
        assertFalse(nativeInitial.contains("setFocused("));
        String rebuild = method(screen, "private void rebuildPresentationWidgets()");
        assertTrue(rebuild.indexOf("getFocused() == composer") < rebuild.indexOf("rebuildWidgets()"));
        assertTrue(rebuild.contains("getFocused() instanceof AbstractWidget widget ? widget : null"));
        assertTrue(rebuild.indexOf("GuideNativeFocus.clear(this)") > rebuild.indexOf("rebuildWidgets()"));
        assertTrue(rebuild.contains("if (composerFocused) setFocused(composer)"));
        assertTrue(rebuild.contains("else if (previous != null)"));
        assertTrue(rebuild.contains("children().stream()"));
        assertTrue(rebuild.contains("widget.getClass() == previous.getClass()"));
        assertTrue(rebuild.contains("widget.getMessage().equals(previous.getMessage()) && widget.active && widget.visible"));
        assertFalse(rebuild.contains("setFocused(previous)"), "old widgets must not be reattached");
        assertTrue(method(screen, "protected void repositionElements()").contains("rebuildPresentationWidgets()"));
        assertNoDraftMutation(rebuild);
    }

    @Test
    void staleContentAndDetailClosuresAreRemovedBeforeLayoutProjectionOrScrollChanges() throws Exception {
        String screen = source("dev/openallay/client/gui/OpenAllayScreen.java");
        String invalidate = method(screen, "private void invalidateContentHits()");
        assertTrue(invalidate.contains("hits.removeIf(hit -> hit.kind() == HitKind.CONTENT || hit.kind() == HitKind.DETAIL"));
        assertTrue(invalidate.contains("|| hit.kind() == HitKind.COMPOSER"));
        assertFalse(invalidate.contains("hits.clear()"), "native modal/session routes remain distinct");
        for (String signature : new String[] {"protected void resizeGuide(", "private void rebuildPresentationWidgets()",
                "private void applyProjection(", "public boolean guideMouseScrolled(", "private boolean scrollTranscriptKey(",
                "private boolean scrollDetailKey("}) {
            assertTrue(method(screen, signature).contains("invalidateContentHits()"), signature);
        }
        String binding = source("dev/openallay/client/gui/GuideNativeScreen.java");
        assertTrue(method(binding, "public final void resize(").contains("resizeGuide(width, height)"));
        assertTrue(method(screen, "protected void resizeGuide(").contains("resizeGuideWidgets(width, height)"));
        String projection = method(screen, "private void applyProjection(");
        assertTrue(projection.indexOf("invalidateContentHits()") < projection.indexOf("retainedSources"));
        String transcript = method(screen, "private boolean scrollTranscriptKey(");
        assertTrue(transcript.indexOf("invalidateContentHits()") < transcript.indexOf("scroll = Mth.clamp"));
        String detail = method(screen, "private boolean scrollDetailKey(");
        assertTrue(detail.indexOf("invalidateContentHits()") < detail.indexOf("detailScroll = Mth.clamp"));
        assertNoDraftMutation(invalidate);
    }

    @Test
    void pendingEditLookupRejectsPriorSessionIdsAndReturnsFreshRecordForSameId() {
        java.util.UUID id = java.util.UUID.randomUUID();
        var captured = new dev.openallay.guide.GuidePendingMessage(id,
                dev.openallay.guide.GuidePendingMessage.Kind.FOLLOW_UP,
                dev.openallay.model.ModelMessage.userText("prior session text"), java.time.Instant.EPOCH, null);
        var differentSession = new dev.openallay.guide.GuidePendingMessage(java.util.UUID.randomUUID(),
                dev.openallay.guide.GuidePendingMessage.Kind.FOLLOW_UP,
                dev.openallay.model.ModelMessage.userText("current session text"), java.time.Instant.EPOCH, null);
        assertNull(OpenAllayScreen.currentPending(java.util.List.of(differentSession), captured.id()));
        assertNull(OpenAllayScreen.currentPending(java.util.List.of(), captured.id()));
        var fresh = new dev.openallay.guide.GuidePendingMessage(id,
                dev.openallay.guide.GuidePendingMessage.Kind.STEER,
                dev.openallay.model.ModelMessage.userText("fresh current text"), java.time.Instant.EPOCH,
                java.util.UUID.randomUUID());
        assertSame(fresh, OpenAllayScreen.currentPending(java.util.List.of(differentSession, fresh), captured.id()));
        assertEquals(dev.openallay.guide.GuidePendingMessage.Kind.STEER, fresh.kind());
        assertEquals("fresh current text", fresh.text());
        assertEquals("prior session text", captured.text(), "captured closure data remains unchanged and unused");
    }

    @Test
    void pendingEditRevalidatesSelectedSessionMembershipBeforeAnyDraftImageOrIntentWrite() throws Exception {
        String edit = method(source("dev/openallay/client/gui/OpenAllayScreen.java"), "private void editPending(");
        int sync = edit.indexOf("synchronizeComposerSession()");
        int actualSession = edit.indexOf("service.snapshot().selectedSession()");
        int lookup = edit.indexOf("currentPending(service.pendingMessages(currentSession), pending.id())");
        int missing = edit.indexOf("if (pending == null)");
        int draftGuard = edit.indexOf("if (!draft.isBlank() || !composerImages.empty())");
        assertTrue(sync >= 0 && actualSession > sync && lookup > actualSession && missing > lookup && draftGuard > missing);
        assertTrue(edit.contains("view.selectedSession().equals(currentSession)"));
        String refusal = edit.substring(missing, draftGuard);
        assertTrue(refusal.contains("screen.openallay.pending.already_consumed"));
        assertTrue(refusal.contains("return;"));
        assertNoDraftMutation(refusal);
        for (String write : new String[] {"uiState.beginPendingEdit(", "draft = pending.message()",
                "composer.setValue(draft)", "composerImages.restore("}) {
            assertTrue(edit.indexOf(write) > draftGuard, write);
        }
        assertTrue(edit.contains("pending = view.selectedSession().equals(currentSession)"));
    }

    private static void assertNativeMouseClickBinding() throws Exception {
        String binding = source("dev/openallay/client/gui/GuideNativeScreen.java");
        String callback = method(binding, "public final boolean mouseClicked(");
        assertTrue(callback.contains("return guideMouseClicked(GuideNativeInput.capture(event), doubleClick)"));
        String delegate = method(binding, "public boolean guideMouseClicked(");
        assertTrue(delegate.contains("return super.mouseClicked(GuideNativeInput.nativeMouse(event), doubleClick)"));
        assertNoDraftMutation(callback);
        assertNoDraftMutation(delegate);
    }

    private static void assertNoDraftMutation(String text) {
        for (String forbidden : new String[] {"setValue(", "setText(", "resetIntent(", "setMode(",
                "composerImages.", "state.", "uiState.", "voice.", "submit()", "ask("}) {
            assertFalse(text.contains(forbidden), forbidden);
        }
    }
}
