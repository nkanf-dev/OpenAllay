package dev.openallay.guide.ui.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.openallay.guide.GuideModelMode;
import dev.openallay.guide.GuideRequestPhase;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideSessionSnapshot;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.GuideToolActivity;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.guide.GuideTopology;
import dev.openallay.guide.semantic.SemanticBlock;
import dev.openallay.guide.semantic.SemanticDocument;
import dev.openallay.guide.semantic.SemanticInline;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.model.ModelUsage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuideHudPresenterTest {
    private static final UUID ACTOR = UUID.fromString("30ab22ed-23fb-46f2-82ca-d4a656698eec");
    private static final GuideDisplayConfig CONFIG = config(
            GuideUiConfig.Hud.defaults().withEnabled(true));

    @Test
    void selectsLatestCompletedSemanticAssistantWithoutProjectingToolsOrFailures() {
        GuideHudPresenter presenter = new GuideHudPresenter();
        GuideToolActivity tool = new GuideToolActivity("private-call", 0, "openallay:inspect_inventory",
                GuideToolStatus.SUCCEEDED,
                JsonParser.parseString("{\"privateToolResult\":\"not a reply\"}").getAsJsonObject(),
                List.of(), List.of());
        GuideRequestSnapshot completed = request("main", GuideRequestStatus.COMPLETED, 1, 8,
                List.of(new GuideTimelineEntry.Assistant(0, "raw markdown should not be parsed",
                                semantic("first explanation"), false, List.of()),
                        new GuideTimelineEntry.Tool(1, tool),
                        new GuideTimelineEntry.Assistant(2, "different raw text",
                                semantic("visible semantic reply"), false, List.of())));
        GuideRequestSnapshot older = completed("main", 2, 4, "older answer");
        GuideRequestSnapshot failed = request("main", GuideRequestStatus.FAILED, 9, 10,
                List.of(assistant(0, "failure partial", false)));
        GuideRequestSnapshot cancelled = request("main", GuideRequestStatus.CANCELLED, 11, 12,
                List.of(assistant(0, "cancelled partial", false)));
        GuideHudView view = presenter.project(snapshot(ACTOR, "main",
                session("main", completed, older, failed, cancelled)), CONFIG);

        assertEquals("visible semantic reply", view.latestReply());
        assertEquals("", view.streamingPreview());
        assertNull(view.progress());
        assertEquals(0, view.otherRunningTasks());
        assertFalse(view.toString().contains("privateToolResult"));
        assertFalse(view.toString().contains("raw markdown"));
    }

    @Test
    void completionCacheUsesTerminalTimeAndDoesNotRegressOnHistoricalPages() {
        GuideHudPresenter presenter = new GuideHudPresenter();
        GuideRequestSnapshot newest = completed("main", 1, 20, "newest reply");
        GuideRequestSnapshot older = completed("main", 10, 15, "older page reply");
        assertEquals("newest reply", presenter.project(snapshot(ACTOR, "main",
                session("main", newest)), CONFIG).latestReply());
        assertEquals("newest reply", presenter.project(snapshot(ACTOR, "main",
                session("main", older)), CONFIG).latestReply());
        assertEquals("newest reply", presenter.project(snapshot(ACTOR, "main",
                session("main")), CONFIG).latestReply());
        assertEquals("newer completion", presenter.project(snapshot(ACTOR, "main",
                session("main", completed("main", 2, 25, "newer completion"))), CONFIG).latestReply());
    }

    @Test
    void selectedSessionControlsReplyAndProgressOtherTasksRemainOnlyACount() {
        GuideHudPresenter presenter = new GuideHudPresenter();
        GuideRequestSnapshot mainActive = request("main", GuideRequestStatus.MODEL_WAIT, 4, null,
                List.of(assistant(0, "main streaming", true)));
        GuideRequestSnapshot otherActive = request("other", GuideRequestStatus.TOOL_WAIT, 6, null,
                List.of());
        GuideSessionSnapshot main = session("main", completed("main", 1, 3, "main completed"), mainActive);
        GuideSessionSnapshot other = session("other", completed("other", 2, 5, "other completed"), otherActive);

        GuideHudView mainView = presenter.project(snapshot(ACTOR, "main", main, other), CONFIG);
        assertEquals("main", mainView.selectedSession());
        assertEquals("main completed", mainView.latestReply());
        assertEquals(GuideRequestPhase.MODEL_WAIT, mainView.progress().phase());
        assertEquals(1, mainView.otherRunningTasks());
        assertEquals("", mainView.streamingPreview(), "streaming is opt-in");

        GuideHudView otherView = presenter.project(snapshot(ACTOR, "other", main, other), CONFIG);
        assertEquals("other completed", otherView.latestReply());
        assertEquals(GuideRequestPhase.TOOL_WAIT, otherView.progress().phase());
        assertEquals(1, otherView.otherRunningTasks());
        assertFalse(otherView.toString().contains("main streaming"));
    }

    @Test
    void workingRequestIdWinsOverPageOrderForSelectedProgress() {
        GuideRequestSnapshot working = request("main", GuideRequestStatus.TOOL_WAIT, 1, null, List.of());
        GuideRequestSnapshot newer = request("main", GuideRequestStatus.MODEL_WAIT, 9, null, List.of());
        GuideSessionSnapshot session = new GuideSessionSnapshot("main", List.of(), List.of(working, newer));
        session = new GuideSessionSnapshot(session.sessionId(), session.messages(), session.requests(),
                session.checkpoints(), session.modelSelection(), session.historyWindow(), List.of(),
                working.requestId());

        GuideHudView view = new GuideHudPresenter().project(snapshot(ACTOR, "main", session), CONFIG);

        assertEquals(GuideRequestPhase.TOOL_WAIT, view.progress().phase());
        assertEquals(1, view.otherRunningTasks());
    }

    @Test
    void contentTogglesApplyWithoutDroppingCompletedReplyCache() {
        GuideHudPresenter presenter = new GuideHudPresenter();
        GuideSnapshot snapshot = snapshot(ACTOR, "main", session("main",
                completed("main", 1, 3, "saved completed"),
                request("main", GuideRequestStatus.MODEL_WAIT, 4, null,
                        List.of(assistant(0, "stream semantic", true)))));
        GuideDisplayConfig streamingOnly = config(CONFIG.ui().hud().withContent(3, false, true));
        GuideHudView streaming = presenter.project(snapshot, streamingOnly);
        assertEquals("", streaming.latestReply());
        assertEquals("stream semantic", streaming.streamingPreview());
        assertEquals("saved completed", presenter.project(snapshot(ACTOR, "main", session("main")),
                CONFIG).latestReply());
    }

    @Test
    void presenterAlsoSupportsExplicitLiteProjectionWhenHudIsDisabled() {
        GuideDisplayConfig disabled = config(CONFIG.ui().hud().withEnabled(false));
        GuideHudView view = new GuideHudPresenter().project(snapshot(ACTOR, "main",
                session("main", completed("main", 1, 2, "Lite reply"))), disabled);
        assertFalse(view.hud().enabled());
        assertEquals("Lite reply", view.latestReply());
    }

    @Test
    void idleSelectionReportsOtherTasksWithoutBorrowingTheirReplies() {
        GuideHudView view = new GuideHudPresenter().project(snapshot(ACTOR, "idle",
                session("idle"), session("other",
                        request("other", GuideRequestStatus.MODEL_WAIT, 1, null,
                                List.of(assistant(0, "other stream", true))))), CONFIG);
        assertNull(view.progress());
        assertEquals("", view.latestReply());
        assertEquals("", view.streamingPreview());
        assertEquals(1, view.otherRunningTasks());
        assertTrue(view.hasContent());
    }

    @Test
    void actorChangesRemovedSessionsAndClearDiscardCachedReplies() {
        GuideHudPresenter presenter = new GuideHudPresenter();
        GuideSnapshot initial = snapshot(ACTOR, "main",
                session("main", completed("main", 1, 5, "old actor reply")));
        presenter.project(initial, CONFIG);
        assertEquals("", presenter.project(snapshot(UUID.randomUUID(), "main", session("main")),
                CONFIG).latestReply());
        presenter.project(initial, CONFIG);
        presenter.project(snapshot(ACTOR, "other", session("other")), CONFIG);
        assertEquals("", presenter.project(snapshot(ACTOR, "main", session("main")), CONFIG).latestReply());
        presenter.project(initial, CONFIG);
        presenter.clear();
        assertEquals("", presenter.project(snapshot(ACTOR, "main", session("main")), CONFIG).latestReply());
    }

    @Test
    void previewCapsUnicodeCodePointsAndKeepsSurrogatesAndCombiningClustersIntact() {
        assertEquals("short reply", GuideHudPresenter.preview("short reply"));
        assertEquals("a".repeat(512), GuideHudPresenter.preview("a".repeat(512)));
        assertEquals("😀".repeat(511) + "…", GuideHudPresenter.preview("😀".repeat(10000)));
        String clusters = GuideHudPresenter.preview("e\u0301".repeat(10000));
        assertEquals("e\u0301".repeat(255) + "…", clusters);
        assertTrue(clusters.codePointCount(0, clusters.length()) <= 512);
        assertEquals("x".repeat(510) + "…",
                GuideHudPresenter.preview("x".repeat(510) + "e\u0301" + "y".repeat(50)));
    }

    private static GuideDisplayConfig config(GuideUiConfig.Hud hud) {
        return GuideDisplayConfig.defaults().withUi(GuideUiConfig.defaults().withHud(hud));
    }

    private static SemanticDocument semantic(String text) {
        return SemanticDocument.of(List.of(new SemanticBlock.Paragraph("a".repeat(64),
                List.of(new SemanticInline.Text("b".repeat(64), text)))), List.of());
    }

    private static GuideTimelineEntry.Assistant assistant(int ordinal, String text, boolean streaming) {
        return new GuideTimelineEntry.Assistant(ordinal, "raw text differs", semantic(text), streaming, List.of());
    }

    private static GuideRequestSnapshot completed(String session, long created, long terminal, String text) {
        return request(session, GuideRequestStatus.COMPLETED, created, terminal,
                List.of(assistant(0, text, false)));
    }

    private static GuideRequestSnapshot request(String session, GuideRequestStatus status, long created,
            Number terminal, List<GuideTimelineEntry> timeline) {
        Instant started = Instant.EPOCH.plusSeconds(created);
        Instant ended = terminal == null ? null : Instant.EPOCH.plusSeconds(terminal.longValue());
        return new GuideRequestSnapshot(UUID.randomUUID(), session, GuideTopology.CLIENT_LOCAL,
                "question", timeline, status, List.of(), ModelUsage.empty(), null, null,
                started, ended == null ? started : ended, ended);
    }

    private static GuideSessionSnapshot session(String id, GuideRequestSnapshot... requests) {
        return new GuideSessionSnapshot(id, List.of(), List.of(requests));
    }

    private static GuideSnapshot snapshot(UUID actor, String selected, GuideSessionSnapshot... sessions) {
        return new GuideSnapshot(actor, selected, GuideModelMode.CLIENT, true, false,
                List.of(sessions), Instant.EPOCH.plusSeconds(100));
    }
}
