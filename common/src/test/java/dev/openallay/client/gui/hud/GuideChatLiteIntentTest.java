package dev.openallay.client.gui.hud;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.client.gui.GuideClientUiState;
import dev.openallay.tool.ToolResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuideChatLiteIntentTest {
    @Test void sameActiveDraftModeRoutesToActualSteerOrFollowUp() {
        assertEquals(GuideChatLiteScreen.Route.FOLLOW_UP, GuideChatLiteScreen.route(GuideClientUiState.DraftIntent.defaults(), true, false));
        var steer = new GuideClientUiState.DraftIntent(GuideClientUiState.DraftMode.STEER, null, false);
        assertEquals(GuideChatLiteScreen.Route.STEER, GuideChatLiteScreen.route(steer, true, false));
        assertEquals(GuideChatLiteScreen.Route.ASK, GuideChatLiteScreen.route(steer, false, false));
    }
    @Test void pendingTargetNeverBecomesANewAskEvenWhenTaskEnded() {
        var edit = new GuideClientUiState.DraftIntent(GuideClientUiState.DraftMode.STEER, UUID.randomUUID(), false);
        assertEquals(GuideChatLiteScreen.Route.EDIT_PENDING, GuideChatLiteScreen.route(edit, true, true));
        assertEquals(GuideChatLiteScreen.Route.EDIT_PENDING, GuideChatLiteScreen.route(edit, false, true));
        assertEquals(GuideChatLiteScreen.Route.BLOCKED, GuideChatLiteScreen.route(edit, false, false));
    }
    @Test void pendingEditCompactIsLiteralAndNeverInvokesLocalCompactOrClearsText() {
        var edit = new GuideClientUiState.DraftIntent(GuideClientUiState.DraftMode.FOLLOW_UP, UUID.randomUUID(), false);
        var text = new java.util.concurrent.atomic.AtomicReference<>("/compact");
        var commandCalls = new java.util.concurrent.atomic.AtomicInteger();
        var dispatch = GuideChatLiteScreen.dispatchDraft(text.get(), edit,
                ordinaryText -> dev.openallay.guide.composer.SlashCommandDispatcher.dispatch(ordinaryText, () -> {
                    commandCalls.incrementAndGet();
                    return java.util.concurrent.CompletableFuture.completedFuture(
                            new ToolResult.Failure<>("compact_failed", "unused"));
                }, completion -> text.set("")));
        assertEquals(0, commandCalls.get());
        assertEquals("/compact", text.get());
        assertEquals("/compact", dispatch.normalizedText());
        assertFalse(dispatch.handled());
        assertEquals(GuideChatLiteScreen.Route.EDIT_PENDING, GuideChatLiteScreen.route(edit, false, true));
    }
    @Test void invalidEditCompactCannotRunACommandOrNormalizeLiteralText() {
        var target = UUID.randomUUID();
        var edit = new GuideClientUiState.DraftIntent(GuideClientUiState.DraftMode.STEER, target, true);
        var text = new java.util.concurrent.atomic.AtomicReference<>("//compact");
        var dispatch = GuideChatLiteScreen.dispatchDraft(text.get(), edit, ordinaryText -> {
            text.set("");
            fail("An invalid edit must never call local command dispatch");
            return null;
        });
        assertEquals("//compact", text.get());
        assertEquals("//compact", dispatch.normalizedText());
        assertEquals(target, edit.pendingId());
        assertTrue(edit.editInvalid());
        assertEquals(GuideChatLiteScreen.Route.BLOCKED, GuideChatLiteScreen.route(edit, false, false));
        assertFalse(dispatch.handled());
    }
    @Test void ordinaryDraftStillUsesExistingSlashCommandAndEscapeRules() {
        var commandCalls = new java.util.concurrent.atomic.AtomicInteger();
        var completionCalls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Function<String, dev.openallay.guide.composer.SlashCommandDispatcher.Dispatch> ordinary =
                text -> dev.openallay.guide.composer.SlashCommandDispatcher.dispatch(text, () -> {
                    commandCalls.incrementAndGet();
                    return java.util.concurrent.CompletableFuture.completedFuture(
                            new ToolResult.Failure<>("compact_failed", "test"));
                }, completion -> completionCalls.incrementAndGet());
        var command = GuideChatLiteScreen.dispatchDraft("/compact", GuideClientUiState.DraftIntent.defaults(), ordinary);
        assertTrue(command.handled());
        assertEquals(1, commandCalls.get());
        assertEquals(1, completionCalls.get());
        var escaped = GuideChatLiteScreen.dispatchDraft("//compact", GuideClientUiState.DraftIntent.defaults(), ordinary);
        assertFalse(escaped.handled());
        assertEquals("/compact", escaped.normalizedText());
        assertEquals(1, commandCalls.get());
        assertEquals(1, completionCalls.get());
    }

    @Test void invalidEditNeverCallsAgentAndFalseIsNotAccepted() {
        var edit = new GuideClientUiState.DraftIntent(GuideClientUiState.DraftMode.FOLLOW_UP, UUID.randomUUID(), true);
        assertEquals(GuideChatLiteScreen.Route.BLOCKED, GuideChatLiteScreen.route(edit, true, true));
        assertFalse(GuideChatLiteScreen.submissionAccepted(true, new ToolResult.Success<>(false)));
        assertTrue(GuideChatLiteScreen.submissionAccepted(true, new ToolResult.Success<>(true)));
        assertFalse(GuideChatLiteScreen.submissionAccepted(true, new ToolResult.Failure<>("pending_missing", "missing")));
        assertTrue(GuideChatLiteScreen.submissionAccepted(false, new ToolResult.Success<>(UUID.randomUUID())));
    }
}
