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
    @Test void invalidEditNeverCallsAgentAndFalseIsNotAccepted() {
        var edit = new GuideClientUiState.DraftIntent(GuideClientUiState.DraftMode.FOLLOW_UP, UUID.randomUUID(), true);
        assertEquals(GuideChatLiteScreen.Route.BLOCKED, GuideChatLiteScreen.route(edit, true, true));
        assertFalse(GuideChatLiteScreen.submissionAccepted(true, new ToolResult.Success<>(false)));
        assertTrue(GuideChatLiteScreen.submissionAccepted(true, new ToolResult.Success<>(true)));
        assertFalse(GuideChatLiteScreen.submissionAccepted(true, new ToolResult.Failure<>("pending_missing", "missing")));
        assertTrue(GuideChatLiteScreen.submissionAccepted(false, new ToolResult.Success<>(UUID.randomUUID())));
    }
}
