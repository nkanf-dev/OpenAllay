package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.openallay.agent.AgentEvent;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.guide.history.GuideHistoryAccess;
import dev.openallay.guide.history.GuideHistoryActivity;
import dev.openallay.guide.history.GuideHistoryCommit;
import dev.openallay.guide.history.GuideHistoryCursor;
import dev.openallay.guide.history.GuideHistoryDeleteScope;
import dev.openallay.guide.history.GuideHistoryMetadata;
import dev.openallay.guide.history.GuideHistoryPage;
import dev.openallay.guide.history.GuideHistoryPageRequest;
import dev.openallay.guide.history.GuideHistoryScope;
import dev.openallay.model.ModelUsage;
import dev.openallay.tool.ToolResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class GuideServiceHistoryWindowTest {
    private static final UUID ACTOR = UUID.fromString("fa7cf22f-6632-4fb6-b31a-bff27de35000");
    private static final GuideHistoryScope SCOPE = GuideHistoryScope.derive(
            ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, "window.example");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-18T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void cancelDuringRestoredContextLoadPreservesDurablePredecessorSeedAndNextHydration() {
        WindowHistory history = new WindowHistory(metadata());
        List<dev.openallay.model.ModelMessage> predecessor = List.of(
                dev.openallay.model.ModelMessage.userText("earlier player goal"),
                new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.ASSISTANT,
                        List.of(new dev.openallay.model.ModelContent.Text("earlier safe result"))));
        history.durableSeed = predecessor;
        ContextLocal local = new ContextLocal();
        GuideService service = new GuideService(ACTOR, local, new NoRemote(),
                (capabilities, correlation) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlation)),
                Runnable::run, CLOCK, new Gson(), SCOPE, history);
        UUID cancelled = success(service.ask("cancel before source is ready").join());
        assertEquals(1, history.contexts.size());
        assertEquals(GuideRequestStatus.CONTEXT_LOADING,
                service.snapshot().sessions().getFirst().requests().getFirst().status());
        assertTrue(success(service.cancel().join()));
        assertEquals(predecessor, history.durableSeed);
        assertTrue(history.commits.stream().flatMap(commit -> commit.mutations().stream())
                .noneMatch(dev.openallay.guide.history.GuideHistoryMutation.ReplaceContext.class::isInstance));
        assertTrue(history.commits.stream().flatMap(commit -> commit.mutations().stream())
                .filter(dev.openallay.guide.history.GuideHistoryMutation.ReplaceRequestContext.class::isInstance)
                .map(dev.openallay.guide.history.GuideHistoryMutation.ReplaceRequestContext.class::cast)
                .anyMatch(row -> row.requestId().equals(cancelled)
                        && row.messages().getFirst().equals(
                                dev.openallay.model.ModelMessage.userText("cancel before source is ready"))));
        assertTrue(local.questions.isEmpty());
        history.contexts.getFirst().complete(new dev.openallay.guide.history.GuideHistoryContextSeed(
                "main", predecessor, List.of(), 1));
        assertTrue(local.questions.isEmpty(), "late source callback cannot revive cancelled work");
        UUID next = success(service.ask("normal next request").join());
        assertEquals(2, history.contexts.size());
        history.contexts.getLast().complete(new dev.openallay.guide.history.GuideHistoryContextSeed(
                "main", history.durableSeed, List.of(), 1));
        assertEquals(predecessor, local.hydrated);
        assertEquals(List.of("normal next request"), local.questions);
        assertEquals(next, service.snapshot().sessions().getFirst().workingRequestId());
    }

    @SuppressWarnings("unchecked")
    private static <T> T success(ToolResult<T> result) {
        return ((ToolResult.Success<T>) assertInstanceOf(ToolResult.Success.class, result)).value();
    }

    private static final class ContextLocal implements GuideLocalEndpoint {
        private List<dev.openallay.model.ModelMessage> hydrated = List.of();
        private final List<String> questions = new ArrayList<>();
        @Override public java.util.Set<dev.openallay.context.ContextCapability> requiredContext() {
            return java.util.Set.of();
        }
        @Override public java.util.Optional<GuideContextSpec> contextSpec(String profile) {
            return java.util.Optional.of(new GuideContextSpec(
                    new dev.openallay.agent.context.ContextBudget(8_192, 1_024), 256, "test/context"));
        }
        @Override public void hydrateContext(UUID actor, String session,
                List<dev.openallay.model.ModelMessage> messages,
                List<dev.openallay.agent.context.ContextCheckpoint> checkpoints) { hydrated = messages; }
        @Override public CompletableFuture<dev.openallay.agent.AgentResult> ask(UUID actor, String session,
                UUID requestId, String question, ToolInvocationContext context, Consumer<AgentEvent> events) {
            questions.add(question);
            events.accept(new AgentEvent.StateChanged(dev.openallay.agent.AgentState.MODEL_WAIT));
            return new CompletableFuture<>();
        }
        @Override public boolean cancel(UUID actor, String session) { return true; }
        @Override public void clearSession(UUID actor, String session) {}
        @Override public void clearActor(UUID actor) {}
    }

    @Test
    void restoredNumericTotalsAreAvailableWithoutPagesAndSurviveSelectionChanges() {
        var usage = new GuideUsageSnapshot(3_000, 200, 900, 100, 7, 7, false, false,
                new java.math.BigDecimal("0.123456789"), false);
        var inherited = new GuideUsageSnapshot(8_000, 400, 1_000, 0, 9, 9, false, false,
                new java.math.BigDecimal("0.654321"), false);
        var base = metadata();
        var row = base.sessions().getFirst();
        WindowHistory history = new WindowHistory(new GuideHistoryMetadata(SCOPE, "main",
                List.of(new GuideHistoryMetadata.Session(row.sessionId(), row.ordinal(), row.modelSelection(),
                        row.requestCount(), row.first(), row.last(), usage, inherited)), CLOCK.instant()));
        GuideService service = service(history);
        assertEquals(usage, service.telemetry().sessionUsage());
        assertEquals(inherited, service.telemetry().inheritedUsage());
        assertTrue(history.pages.isEmpty());
        service.selectSession("other").join();
        service.selectSession("main").join();
        assertEquals(usage, service.telemetry().sessionUsage());
        assertEquals(0, usage.estimatedUsd().compareTo(service.telemetry().sessionUsage().estimatedUsd()));
        assertTrue(history.pages.isEmpty());
    }

    @Test
    void startupLoadsOnlyMetadataAndPublishesCountsWithoutBodies() {
        WindowHistory history = new WindowHistory(metadata());
        GuideService service = service(history);

        GuideSessionSnapshot session = service.snapshot().sessions().getFirst();
        assertEquals(3, session.historyWindow().totalRequests());
        assertTrue(session.requests().isEmpty());
        assertTrue(history.pages.isEmpty());
        assertEquals(1, history.metadataLoads);
    }

    @Test
    void coalescesIdenticalPagesAndSuppressesSupersededLateCompletion() {
        WindowHistory history = new WindowHistory(metadata());
        GuideService service = service(history);
        GuideHistoryCursor last = metadata().sessions().getFirst().last();

        CompletableFuture<ToolResult<GuideHistoryPage>> first = service.requestHistoryWindow(
                "main", GuideHistoryPageRequest.Direction.NEWEST, null, 2);
        CompletableFuture<ToolResult<GuideHistoryPage>> duplicate = service.requestHistoryWindow(
                "main", GuideHistoryPageRequest.Direction.NEWEST, null, 2);
        assertEquals(1, history.pages.size());

        CompletableFuture<ToolResult<GuideHistoryPage>> replacement = service.requestHistoryWindow(
                "main", GuideHistoryPageRequest.Direction.BEFORE, last, 1);
        assertFailure(first.join(), "history_page_superseded");
        assertFailure(duplicate.join(), "history_page_superseded");
        assertEquals(2, history.pages.size());

        history.pages.getFirst().complete(page(List.of(request(1), request(2)), true, false));
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());

        GuideHistoryPage accepted = page(List.of(request(1)), false, true);
        history.pages.getLast().complete(accepted);
        assertInstanceOf(ToolResult.Success.class, replacement.join());
        GuideSessionSnapshot session = service.snapshot().sessions().getFirst();
        assertEquals(List.of(request(1).requestId()),
                session.requests().stream().map(GuideRequestSnapshot::requestId).toList());
        assertEquals(GuideHistoryPageState.IDLE, session.historyWindow().state());
    }

    @Test
    void disconnectInvalidatesOutstandingPageWaiters() {
        WindowHistory history = new WindowHistory(metadata());
        GuideService service = service(history);
        CompletableFuture<ToolResult<GuideHistoryPage>> page = service.requestHistoryWindow(
                "main", GuideHistoryPageRequest.Direction.NEWEST, null, 2);

        service.disconnect().join();

        assertFailure(page.join(), "history_page_cancelled");
        history.pages.getFirst().complete(page(List.of(request(1)), false, true));
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
    }

    private static GuideService service(WindowHistory history) {
        return new GuideService(
                ACTOR, null, new NoRemote(),
                (capabilities, correlation) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlation)),
                Runnable::run, CLOCK, new Gson(), SCOPE, history);
    }

    private static GuideHistoryMetadata metadata() {
        GuideHistoryCursor first = new GuideHistoryCursor(0, request(0).requestId());
        GuideHistoryCursor last = new GuideHistoryCursor(2, request(2).requestId());
        return new GuideHistoryMetadata(
                SCOPE, "main",
                List.of(new GuideHistoryMetadata.Session(
                        "main", 0, GuideModelSelection.client("default"), 3, first, last)),
                CLOCK.instant());
    }

    private static GuideHistoryPage page(
            List<GuideRequestSnapshot> requests, boolean earlier, boolean later) {
        if (requests.isEmpty()) return new GuideHistoryPage("main", List.of(), null, null, earlier, later);
        long firstSequence = requests.getFirst().userMessage().charAt(1) - '0';
        long lastSequence = requests.getLast().userMessage().charAt(1) - '0';
        return new GuideHistoryPage(
                "main", requests,
                new GuideHistoryCursor(firstSequence, requests.getFirst().requestId()),
                new GuideHistoryCursor(lastSequence, requests.getLast().requestId()),
                earlier, later);
    }

    private static GuideRequestSnapshot request(int sequence) {
        UUID id = UUID.nameUUIDFromBytes(("request-" + sequence).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Instant at = CLOCK.instant().minusSeconds(10 - sequence);
        return new GuideRequestSnapshot(
                id, "main", GuideTopology.CLIENT_LOCAL, "q" + sequence,
                List.of(new GuideTimelineEntry.Assistant(0, "a" + sequence, false, List.of())),
                GuideRequestStatus.COMPLETED, List.of(), ModelUsage.empty(), null, null,
                at, at, at, GuideModelSelection.client("default"));
    }

    private static void assertFailure(ToolResult<?> result, String code) {
        assertEquals(code,
                ((ToolResult.Failure<?>) assertInstanceOf(ToolResult.Failure.class, result)).code());
    }

    private static final class WindowHistory implements GuideHistoryAccess {
        private final GuideHistoryMetadata metadata;
        private final List<CompletableFuture<GuideHistoryPage>> pages = new ArrayList<>();
        private final List<CompletableFuture<dev.openallay.guide.history.GuideHistoryContextSeed>> contexts = new ArrayList<>();
        private final List<GuideHistoryCommit> commits = new ArrayList<>();
        private List<dev.openallay.model.ModelMessage> durableSeed = List.of();
        private int metadataLoads;

        private WindowHistory(GuideHistoryMetadata metadata) { this.metadata = metadata; }
        @Override public CompletableFuture<java.util.Optional<GuideHistoryMetadata>> metadata(
                GuideHistoryScope scope) {
            metadataLoads++;
            return CompletableFuture.completedFuture(java.util.Optional.of(metadata));
        }
        @Override public CompletableFuture<GuideHistoryPage> page(GuideHistoryPageRequest request) {
            CompletableFuture<GuideHistoryPage> result = new CompletableFuture<>();
            pages.add(result);
            return result;
        }
        @Override public CompletableFuture<dev.openallay.guide.history.GuideHistoryContextSeed> context(
                dev.openallay.guide.history.GuideHistoryContextRequest request) {
            CompletableFuture<dev.openallay.guide.history.GuideHistoryContextSeed> result = new CompletableFuture<>();
            contexts.add(result);
            return result;
        }
        @Override public CompletableFuture<Void> commit(GuideHistoryCommit commit) {
            commits.add(commit);
            for (var mutation : commit.mutations()) {
                if (mutation instanceof dev.openallay.guide.history.GuideHistoryMutation.ReplaceContext replaced) {
                    durableSeed = replaced.messages();
                }
            }
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> delete(GuideHistoryDeleteScope scope) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> resetDatabase() {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> flush() {
            return CompletableFuture.completedFuture(null);
        }
        @Override public GuideHistoryActivity activity() { return new GuideHistoryActivity(0, false); }
    }

    private static final class NoRemote implements GuideRemoteEndpoint {
        @Override public boolean serverModelAvailable() { return false; }
        @Override public boolean serverToolsAvailable() { return false; }
        @Override public boolean ask(
                UUID requestId, String sessionId, String question, Consumer<AgentEvent> events) {
            return false;
        }
        @Override public boolean cancel(UUID requestId) { return false; }
        @Override public void disconnect() {}
    }
}
