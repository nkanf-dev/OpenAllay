package dev.openallay.guide.ui.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import dev.openallay.guide.ui.GuideUiRow;
import dev.openallay.guide.ui.GuideUiView;
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
    void keepsLatestCompletedPreviewSeparateFromLosslessLatestTaskRows() {
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
        assertEquals(cancelled.requestId(), ((GuideUiRow.Assistant) view.rows().getFirst()).requestId());
        assertEquals("cancelled partial", ((GuideUiRow.Assistant) view.rows().getFirst()).semantic().fallbackText());
        assertTrue(view.rows().getLast() instanceof GuideUiRow.Status);
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
        assertTrue(otherView.rows().stream().allMatch(row -> row instanceof GuideUiRow.Assistant assistant
                ? assistant.requestId().equals(other.requests().getFirst().requestId())
                : row instanceof GuideUiRow.Tool tool && tool.requestId().equals(otherActive.requestId())));
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

    @Test
    void interactiveRowsRetainLongReplyToolItemsAndSourceIdentityBeyondPassiveBudgets() {
        String full = java.util.stream.IntStream.range(0, 60)
                .mapToObj(index -> "Guide step " + index + ": " + "complete detail ".repeat(20))
                .collect(java.util.stream.Collectors.joining("\n"));
        var evidence = new dev.openallay.context.EvidenceMetadata(
                dev.openallay.context.DataAuthority.CLIENT_VISIBLE,
                dev.openallay.context.DataCompleteness.PARTIAL, Instant.EPOCH,
                "minecraft:client_player", "minecraft:client", "26.2", "fabric", java.util.Map.of("minecraft:dimension", "minecraft:overworld"));
        var source = new dev.openallay.guide.GuideSource("openallay:run_javascript", evidence);
        GuideToolActivity tool = new GuideToolActivity("items-call", 0, "openallay:run_javascript", GuideToolStatus.SUCCEEDED,
                JsonParser.parseString("""
                        {"status":"success","value":{"viewKind":"ITEM","preview":[
                        {"itemId":"minecraft:diamond","displayName":"Diamond","count":64},
                        {"itemId":"minecraft:oak_log","displayName":"Oak log","count":128}]}}
                        """).getAsJsonObject(), List.of(), List.of(source));
        GuideTimelineEntry.Assistant assistant = new GuideTimelineEntry.Assistant(1, full, semantic(full), false, List.of(source));
        GuideRequestSnapshot request = request("main", GuideRequestStatus.COMPLETED, 1, 3,
                List.of(new GuideTimelineEntry.Tool(0, tool), assistant));
        GuideSnapshot snapshot = snapshot(ACTOR, "main", session("main", request));
        GuideDisplayConfig tiny = config(CONFIG.ui().hud().withContent(1, false, false));
        GuideHudPresenter presenter = new GuideHudPresenter();
        GuideHudView interactive = presenter.projectInteractive(snapshot, tiny);
        assertEquals(GuideUiView.projectRequestRows(request, tiny).stream()
                .filter(row -> !(row instanceof GuideUiRow.User)).toList(), interactive.rows());
        GuideUiRow.Assistant projected = (GuideUiRow.Assistant) interactive.rows().getLast();
        assertEquals(full, projected.text());
        assertSame(assistant.semantic(), projected.semantic());
        assertSame(source, projected.sources().getFirst());
        assertTrue(projected.semantic().fallbackText().length() > GuideHudView.MAX_PREVIEW_CODE_POINTS);
        assertEquals(2, ((dev.openallay.guide.ui.GuideDetailCard.ItemGrid)
                ((GuideUiRow.Tool) interactive.rows().getFirst()).detail().cards().getFirst()).items().size());
        assertEquals(128, ((dev.openallay.guide.ui.GuideDetailCard.ItemGrid)
                ((GuideUiRow.Tool) interactive.rows().getFirst()).detail().cards().getFirst()).items().get(1).count());
        assertSame(interactive, presenter.projectInteractive(snapshot, tiny), "unchanged snapshot does not re-project each frame");
        assertTrue(presenter.project(snapshot, tiny).rows().isEmpty(), "passive content preference does not trim interactive source");
        assertEquals(full, assistant.text());
    }

    @Test
    void toolStartedAndCompletedKeepOneStableInvocationAndUpdateTypedResult() {
        UUID requestId = UUID.randomUUID();
        GuideToolActivity running = new GuideToolActivity("stable-call", 0, "openallay:run_javascript",
                GuideToolStatus.RUNNING, null, List.of(), List.of());
        GuideRequestSnapshot started = request("main", GuideRequestStatus.TOOL_WAIT, 1, null,
                List.of(new GuideTimelineEntry.Tool(0, running)));
        started = new GuideRequestSnapshot(requestId, started.sessionId(), started.topology(), started.userMessage(),
                started.timeline(), started.status(), started.sources(), started.usage(), started.retryAfterMillis(), started.failure(),
                started.createdAt(), started.updatedAt(), started.terminalAt());
        GuideHudPresenter presenter = new GuideHudPresenter();
        var first = (GuideUiRow.Tool) presenter.projectInteractive(snapshot(ACTOR, "main", session("main", started)), CONFIG).rows().getFirst();
        assertEquals(dev.openallay.guide.ui.GuideToolDisplayStatus.RUNNING, first.detail().displayStatus());
        GuideToolActivity completed = new GuideToolActivity("stable-call", 0, "openallay:run_javascript",
                GuideToolStatus.SUCCEEDED, JsonParser.parseString("""
                {"status":"success","value":{"viewKind":"SCALAR","preview":"placed 48 blocks"}}
                """).getAsJsonObject(), List.of(), List.of());
        GuideRequestSnapshot ended = new GuideRequestSnapshot(requestId, "main", GuideTopology.CLIENT_LOCAL,
                "question", List.of(new GuideTimelineEntry.Tool(0, completed)), GuideRequestStatus.COMPLETED,
                List.of(), ModelUsage.empty(), null, null, Instant.EPOCH.plusSeconds(1), Instant.EPOCH.plusSeconds(3), Instant.EPOCH.plusSeconds(3));
        GuideHudView next = presenter.projectInteractive(snapshot(ACTOR, "main", session("main", ended)), CONFIG);
        assertEquals(1, next.rows().size());
        var last = (GuideUiRow.Tool) next.rows().getFirst();
        assertEquals(first.requestId(), last.requestId());
        assertEquals(first.activity().invocationId(), last.activity().invocationId());
        assertEquals(dev.openallay.guide.ui.GuideToolDisplayStatus.SUCCEEDED, last.detail().displayStatus());
        assertEquals("placed 48 blocks", ((dev.openallay.guide.ui.GuideDetailCard.Text) last.detail().cards().getFirst()).lines().getFirst());
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
