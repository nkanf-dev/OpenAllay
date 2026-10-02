package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.guide.*;
import dev.openallay.model.ModelUsage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class OpenAllayScreenForkTest {
    @Test
    void forkActionUsesRequestIdentityAndTerminalBoundaryNotDisplayedProgressPhase() {
        Instant now = Instant.parse("2026-10-02T08:00:00Z");
        UUID terminalId = UUID.randomUUID(); UUID activeId = UUID.randomUUID();
        GuideRequestSnapshot terminal = new GuideRequestSnapshot(terminalId, "main", GuideTopology.CLIENT_LOCAL,
                "completed", List.of(new GuideTimelineEntry.Assistant(0, "intermediate", false, List.of()),
                        new GuideTimelineEntry.Assistant(1, "final", false, List.of())),
                GuideRequestStatus.FAILED, List.of(), ModelUsage.empty(),
                null, new GuideFailure("synthetic_failure", "ended"), now, now, now);
        GuideRequestSnapshot active = GuideRequestSnapshot.start(activeId, "main", GuideTopology.CLIENT_LOCAL,
                "ongoing", now);
        GuideSnapshot snapshot = new GuideSnapshot(UUID.randomUUID(), "main", GuideModelMode.CLIENT,
                true, false, GuidePersistenceSnapshot.disabled(),
                List.of(new GuideSessionSnapshot("main", List.of(), List.of(terminal, active), List.of())), now);
        assertTrue(OpenAllayScreen.forkableRequest(snapshot, "main", terminalId));
        assertFalse(OpenAllayScreen.forkableRequest(snapshot, "main", activeId));
        assertFalse(OpenAllayScreen.forkableRequest(snapshot, "other", terminalId));
        assertFalse(OpenAllayScreen.forkableRequest(snapshot, "main", UUID.randomUUID()));
        var parser = new dev.openallay.guide.semantic.SemanticMessageParser();
        assertFalse(OpenAllayScreen.completedAssistantBoundary(snapshot, "main",
                new dev.openallay.guide.ui.GuideUiRow.Assistant(terminalId, 0, "intermediate",
                        parser.parse("intermediate"), false, List.of())));
        assertTrue(OpenAllayScreen.completedAssistantBoundary(snapshot, "main",
                new dev.openallay.guide.ui.GuideUiRow.Assistant(terminalId, 1, "final",
                        parser.parse("final"), false, List.of())));
        assertFalse(OpenAllayScreen.completedAssistantBoundary(snapshot, "main",
                new dev.openallay.guide.ui.GuideUiRow.Assistant(terminalId, 1, "final",
                        parser.parse("final"), true, List.of())));
        assertFalse(OpenAllayScreen.completedAssistantBoundary(snapshot, "main",
                new dev.openallay.guide.ui.GuideUiRow.Assistant(activeId, 1, "final",
                        parser.parse("final"), false, List.of())));
        assertFalse(OpenAllayScreen.completedAssistantBoundary(snapshot, "other",
                new dev.openallay.guide.ui.GuideUiRow.Assistant(terminalId, 1, "final",
                        parser.parse("final"), false, List.of())));
        assertFalse(OpenAllayScreen.completedAssistantBoundary(snapshot, "main",
                new dev.openallay.guide.ui.GuideUiRow.Assistant(terminalId, 9, "final",
                        parser.parse("final"), false, List.of())));
    }
}
