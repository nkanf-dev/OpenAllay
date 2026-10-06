package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentState;
import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.image.ImagePayloadResolver;
import dev.openallay.tool.ToolResult;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class GuideServiceManualCompactionTest {
    private static final UUID ACTOR = UUID.fromString("f45156d2-e19e-4dbf-987f-11418eec9938");

    @Test void localControlDoesNotSubmitAQuestionAndNoOpDoesNotChangeHistory() {
        Local local = new Local();
        GuideService service = service(local);
        var future = service.compactSelectedSession();
        assertFalse(future.isDone());
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
        assertTrue(service.snapshot().sessions().getFirst().messages().isEmpty());
        assertEquals(0, local.asks);
        assertEquals("default", local.profile);
        assertEquals("main", local.session);
        Prepared prepared = new Prepared(false);
        local.preparing.complete(new ToolResult.Success<>(prepared));
        var result = success(future.join());
        assertEquals(GuideCompactResult.Status.NOT_NEEDED, result.status());
        assertFalse(prepared.published);
        assertTrue(prepared.closed);
        assertTrue(service.snapshot().sessions().getFirst().checkpoints().isEmpty());
    }

    @Test void controlBusyPreventsOrdinarySubmissionAndActiveRequestRejectsControl() {
        Local local = new Local();
        GuideService service = service(local);
        service.compactSelectedSession();
        assertFailure(service.ask("must stay draft").join(), "agent_busy");
        local.preparing.complete(new ToolResult.Success<>(new Prepared(false)));
        success(service.ask("actual question").join());
        assertFailure(service.compactSelectedSession().join(), "compact_busy");
        assertEquals(1, local.asks);
    }

    @Test void selectedServerIsExplicitlyUnavailableAndNeverCallsMinecraftOrRemoteAsk() {
        Local local = new Local();
        GuideService service = service(local);
        success(service.setModelSelection(GuideModelSelection.server()).join());
        assertFalse(service.compactAvailable());
        assertFailure(service.compactSelectedSession().join(), "compact_unavailable");
        assertNull(local.preparing);
        assertEquals(0, local.asks);
    }

    @Test void completedRealSummaryPublishesOnlyPreparedProjectionNotPlayerHistory() {
        Local local = new Local();
        GuideService service = service(local);
        var future = service.compactSelectedSession();
        Prepared prepared = new Prepared(true);
        local.preparing.complete(new ToolResult.Success<>(prepared));
        GuideCompactResult result = success(future.join());
        assertEquals(GuideCompactResult.Status.COMPACTED, result.status());
        assertTrue(prepared.published);
        assertTrue(prepared.closed);
        assertEquals(List.of(prepared.outcome().checkpoint()), service.snapshot().sessions().getFirst().checkpoints());
        assertTrue(service.snapshot().sessions().getFirst().messages().isEmpty());
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
        assertEquals(0, local.asks);
    }

    @Test void errorsKeepContextAndSelectingAnotherSessionCancelsPreparedPublication() {
        Local local = new Local();
        GuideService service = service(local);
        var failed = service.compactSelectedSession();
        local.preparing.complete(new ToolResult.Failure<>("compact_failed", "summary failed"));
        assertFailure(failed.join(), "compact_failed");
        var stale = service.compactSelectedSession();
        CompletableFuture<ToolResult<GuidePreparedCompaction>> pending = local.preparing;
        success(service.selectSession("other").join());
        assertTrue(local.cancellation.isCancelled());
        assertFailure(stale.join(), "compact_cancelled");
        Prepared prepared = new Prepared(true);
        pending.complete(new ToolResult.Success<>(prepared));
        assertFalse(prepared.published);
        assertTrue(prepared.closed);
        assertTrue(service.snapshot().sessions().stream().allMatch(session -> session.checkpoints().isEmpty()));
    }

    @Test void replacingModelSnapshotOrExplicitStopPreventsLatePublication() {
        Local local = new Local();
        GuideService service = service(local);
        var future = service.compactSelectedSession();
        local.identity = new Object();
        Prepared prepared = new Prepared(true);
        local.preparing.complete(new ToolResult.Success<>(prepared));
        assertFailure(future.join(), "compact_stale");
        assertFalse(prepared.published);
        var stopped = service.compactSelectedSession();
        var pending = local.preparing;
        assertTrue(success(service.cancel().join()));
        assertFailure(stopped.join(), "compact_cancelled");
        Prepared late = new Prepared(true);
        pending.complete(new ToolResult.Success<>(late));
        assertFalse(late.published);
        assertTrue(late.closed);
    }

    @Test void requestlessSummaryUsesRealNumericCallReceiptsWithoutChangingLatestRequest() {
        Local local = new Local();
        GuideService service = service(local);
        var future = service.compactSelectedSession();
        UUID call = UUID.randomUUID();
        local.usage.accept(new AgentEvent.ModelUsageStarted(call, "selected-model"));
        local.usage.accept(new AgentEvent.ModelUsageObserved(call, "selected-model",
                new dev.openallay.model.ModelUsage(20, 4, 0)));
        local.preparing.complete(new ToolResult.Success<>(new Prepared(false)));
        success(future.join());
        assertEquals(1, service.telemetry().sessionUsage().actualCalls());
        assertEquals(20, service.telemetry().sessionUsage().inputTokens());
        assertEquals(4, service.telemetry().sessionUsage().outputTokens());
        assertNull(service.telemetry().requestId());
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
    }

    @Test void cancelledSummaryKeepsItsNumericOwnerUntilActualPrepareCompletion() {
        Local local = new Local();
        GuideService service = service(local);
        var control = service.compactSelectedSession();
        Consumer<AgentEvent> lateUsage = local.usage;
        CompletableFuture<ToolResult<GuidePreparedCompaction>> actual = local.preparing;
        assertTrue(success(service.cancel().join()));
        assertFailure(control.join(), "compact_cancelled");
        assertFailure(service.ask("must wait for true resource release").join(), "agent_busy");
        UUID call = UUID.randomUUID();
        lateUsage.accept(new AgentEvent.ModelUsageStarted(call, "selected-model"));
        lateUsage.accept(new AgentEvent.ModelUsageObserved(call, "selected-model",
                new dev.openallay.model.ModelUsage(20, 4, 0)));
        assertEquals(1, service.telemetry().sessionUsage().actualCalls());
        assertEquals(20, service.telemetry().sessionUsage().inputTokens());
        Prepared discarded = new Prepared(false);
        actual.complete(new ToolResult.Success<>(discarded));
        assertTrue(discarded.closed);
        assertFalse(discarded.published);
        success(service.ask("normal successor").join());
        assertEquals(1, local.asks);
        assertEquals(1, service.telemetry().sessionUsage().actualCalls());
    }

    @Test void durableProjectionMustSaveBeforePublicationAndWriteFailureKeepsPlayerHistory() {
        Local local = new Local();
        History history = new History();
        GuideService service = service(local, history);
        history.completeWrites();
        var future = service.compactSelectedSession();
        Prepared prepared = new Prepared(true);
        local.preparing.complete(new ToolResult.Success<>(prepared));
        history.completeOrdinaryWrites();
        assertFalse(prepared.published);
        assertFalse(future.isDone());
        int index = history.manualWriteIndex();
        assertTrue(index >= 0);
        assertTrue(history.commits.get(index).mutations().stream().noneMatch(mutation ->
                mutation instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertRequest
                        || mutation instanceof dev.openallay.guide.history.GuideHistoryMutation.ReplaceRequestContext));
        history.writes.get(index).completeExceptionally(new IllegalStateException("injected save failure"));
        assertFailure(future.join(), "compact_failed");
        assertFalse(prepared.published);
        assertTrue(prepared.closed);
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
        assertTrue(service.snapshot().sessions().getFirst().checkpoints().isEmpty());
        assertNull(history.durableCheckpoint, "a failed atomic summary commit cannot archive a checkpoint");
    }

    @Test void stoppingWhileSummarySaveIsInFlightRestoresActualSourceBeforeReleasingBusy() {
        Local local = new Local();
        History history = new History();
        GuideService service = service(local, history);
        history.completeWrites();
        var future = service.compactSelectedSession();
        Prepared prepared = new Prepared(true);
        local.preparing.complete(new ToolResult.Success<>(prepared));
        history.completeOrdinaryWrites();
        int index = history.manualWriteIndex();
        assertTrue(index >= 0);
        assertTrue(success(service.cancel().join()));
        assertFalse(future.isDone(), "the save and restore own the idle fence until acknowledgement");
        assertFailure(service.ask("do not overwrite rollback").join(), "agent_busy");
        history.writes.get(index).complete(null);
        var restore = history.commits.getLast();
        var restoredContext = restore.mutations().stream()
                .filter(dev.openallay.guide.history.GuideHistoryMutation.ReplaceContext.class::isInstance)
                .map(dev.openallay.guide.history.GuideHistoryMutation.ReplaceContext.class::cast)
                .findFirst().orElseThrow();
        assertEquals(prepared.source(), restoredContext.messages());
        assertFalse(prepared.published);
        assertFalse(future.isDone());
        history.writes.getLast().complete(null);
        assertFailure(future.join(), "compact_stale");
        assertTrue(prepared.closed);
        assertEquals(0, local.asks);
    }

    @Test void disconnectWaitsForCompensationOfASavedButUnpublishedSummary() throws Exception {
        Local local = new Local();
        History history = new History();
        GuideService service = service(local, history);
        history.completeWrites();
        var compacting = service.compactSelectedSession();
        Prepared prepared = new Prepared(true);
        local.preparing.complete(new ToolResult.Success<>(prepared));
        history.completeOrdinaryWrites();
        int summary = history.manualWriteIndex();
        assertTrue(summary >= 0);
        CompletableFuture<Void> disconnecting = service.disconnect();
        assertFalse(disconnecting.isDone(), "disconnect owns the durable compensation acknowledgement");
        history.writes.get(summary).complete(null);
        assertEquals(prepared.projection(), history.durableContext);
        assertFalse(prepared.published);
        assertFalse(disconnecting.isDone());
        int restore = history.commits.size() - 1;
        var checkpoint = history.commits.get(restore).mutations().stream()
                .filter(dev.openallay.guide.history.GuideHistoryMutation.AppendCheckpoint.class::isInstance)
                .map(dev.openallay.guide.history.GuideHistoryMutation.AppendCheckpoint.class::cast)
                .findFirst().orElseThrow().checkpoint();
        assertEquals(ContextCheckpoint.Status.FAILED, checkpoint.status());
        assertNull(checkpoint.summary());
        assertEquals(SCOPE, history.commits.get(restore).scope());
        history.writes.get(restore).complete(null);
        disconnecting.get(2, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(prepared.source(), history.durableContext,
                "a reopened history reads the predecessor source, not the cancelled summary");
        assertEquals(ContextCheckpoint.Status.FAILED, history.durableCheckpoint.status());
        assertFailure(compacting.join(), "compact_stale");
        assertTrue(prepared.closed);
        assertEquals(0, local.asks);
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
        assertTrue(service.snapshot().sessions().getFirst().checkpoints().isEmpty());
    }

    @Test void clearDuringSummarySaveRemainsAuthoritativeAfterDisconnectAndLateSaveAcknowledgement() throws Exception {
        Local local = new Local(); History history = new History(); GuideService service = service(local, history);
        history.completeWrites();
        service.compactSelectedSession();
        Prepared prepared = new Prepared(true);
        local.preparing.complete(new ToolResult.Success<>(prepared));
        history.completeOrdinaryWrites();
        int summary = history.manualWriteIndex();
        assertTrue(summary >= 0);
        assertTrue(success(service.clearSelectedSession().join()));
        var disconnecting = service.disconnect();
        assertFalse(disconnecting.isDone());
        history.writes.get(summary).complete(null);
        history.completeWrites();
        disconnecting.get(2, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(history.durableContext.isEmpty());
        assertNull(history.durableCheckpoint);
        assertFalse(history.commits.stream().flatMap(commit -> commit.mutations().stream())
                .filter(dev.openallay.guide.history.GuideHistoryMutation.ReplaceContext.class::isInstance)
                .map(dev.openallay.guide.history.GuideHistoryMutation.ReplaceContext.class::cast)
                .anyMatch(context -> context.messages().equals(prepared.source())),
                "disconnect does not grant rollback authority over a newer ClearSession");
        assertFalse(prepared.published);
        assertTrue(prepared.closed);
    }

    @Test void failedLiveRollbackKeepsBusyUntilFallbackCompensationAcknowledgement() {
        Local local = new Local(); History history = new History(); GuideService service = service(local, history);
        history.completeWrites();
        var compacting = service.compactSelectedSession();
        Prepared prepared = new Prepared(true);
        local.preparing.complete(new ToolResult.Success<>(prepared));
        history.completeOrdinaryWrites();
        int summary = history.manualWriteIndex();
        success(service.cancel().join());
        history.writes.get(summary).complete(null);
        history.writes.getLast().completeExceptionally(new IllegalStateException("injected first restore failure"));
        assertFalse(compacting.isDone());
        assertFailure(service.ask("cannot read unpublished summary").join(), "agent_busy");
        assertEquals(prepared.projection(), history.durableContext);
        history.completeWrites();
        assertFailure(compacting.join(), "compact_stale");
        assertEquals(prepared.source(), history.durableContext);
        assertEquals(ContextCheckpoint.Status.FAILED, history.durableCheckpoint.status());
        success(service.ask("safe successor after compensation").join());
    }

    @Test void failedCompensationDoesNotReportDisconnectDurabilitySuccess() throws Exception {
        Local local = new Local();
        History history = new History();
        GuideService service = service(local, history);
        history.completeWrites();
        service.compactSelectedSession();
        local.preparing.complete(new ToolResult.Success<>(new Prepared(true)));
        history.completeOrdinaryWrites();
        int summary = history.manualWriteIndex();
        CompletableFuture<Void> disconnecting = service.disconnect();
        history.writes.get(summary).complete(null);
        assertFalse(disconnecting.isDone());
        history.writes.getLast().completeExceptionally(new IllegalStateException("injected rollback write failure"));
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> disconnecting.get(2, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(disconnecting.isCompletedExceptionally());
        assertThrows(java.util.concurrent.CompletionException.class, disconnecting::join);
    }

    private static GuideService service(Local local) { return service(local, null); }

    private static GuideService service(Local local, History history) {
        GuideRemoteEndpoint remote = new GuideRemoteEndpoint() {
            @Override public boolean serverModelAvailable() { return true; }
            @Override public boolean serverToolsAvailable() { return false; }
            @Override public boolean ask(UUID request, String session, String question, Consumer<AgentEvent> events) {
                fail("Local controls must not be sent to the server model"); return false;
            }
            @Override public boolean cancel(UUID request) { return false; }
            @Override public void disconnect() {}
        };
        return new GuideService(ACTOR, local, remote,
                (capabilities, correlation) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlation)), Runnable::run,
                Clock.systemUTC(), dev.openallay.json.EngineJson.create(), history == null ? null : SCOPE, history);
    }

    private static final dev.openallay.guide.history.GuideHistoryScope SCOPE =
            dev.openallay.guide.history.GuideHistoryScope.derive(ACTOR,
                    dev.openallay.guide.history.GuideHistoryScope.Kind.MULTIPLAYER, "compact.example");

    private static final class History implements dev.openallay.guide.history.GuideHistoryAccess {
        private final List<dev.openallay.guide.history.GuideHistoryCommit> commits = new ArrayList<>();
        private final List<CompletableFuture<Void>> writes = new ArrayList<>();
        private List<ModelMessage> durableContext = List.of();
        private ContextCheckpoint durableCheckpoint;
        @Override public CompletableFuture<Optional<dev.openallay.guide.history.GuideHistoryMetadata>> metadata(
                dev.openallay.guide.history.GuideHistoryScope scope) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        @Override public CompletableFuture<dev.openallay.guide.history.GuideHistoryContextSeed> context(
                dev.openallay.guide.history.GuideHistoryContextRequest request) {
            return CompletableFuture.completedFuture(new dev.openallay.guide.history.GuideHistoryContextSeed(
                    request.sessionId(), List.of(ModelMessage.userText("actual older source")), List.of(), 20));
        }
        @Override public CompletableFuture<Void> commit(dev.openallay.guide.history.GuideHistoryCommit commit) {
            commits.add(commit);
            CompletableFuture<Void> future = new CompletableFuture<>();
            writes.add(future);
            return future.thenRun(() -> {
                for (var mutation : commit.mutations()) {
                    if (mutation instanceof dev.openallay.guide.history.GuideHistoryMutation.ClearSession) {
                        durableContext = List.of();
                        durableCheckpoint = null;
                    } else if (mutation instanceof dev.openallay.guide.history.GuideHistoryMutation.ReplaceContext context) {
                        durableContext = context.messages();
                    } else if (mutation instanceof dev.openallay.guide.history.GuideHistoryMutation.AppendCheckpoint checkpoint) {
                        durableCheckpoint = checkpoint.checkpoint();
                    }
                }
            });
        }
        @Override public CompletableFuture<Void> delete(dev.openallay.guide.history.GuideHistoryDeleteScope scope) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> resetDatabase() { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> flush() { return CompletableFuture.completedFuture(null); }
        @Override public dev.openallay.guide.history.GuideHistoryActivity activity() {
            return new dev.openallay.guide.history.GuideHistoryActivity(0, false);
        }
        private void completeWrites() {
            for (int index = 0; index < writes.size(); index++) writes.get(index).complete(null);
        }
        private void completeOrdinaryWrites() {
            for (int index = 0; index < writes.size(); index++) {
                if (commits.get(index).mutations().stream().noneMatch(
                        dev.openallay.guide.history.GuideHistoryMutation.ReplaceContext.class::isInstance)) {
                    writes.get(index).complete(null);
                }
            }
        }
        private int manualWriteIndex() {
            for (int index = 0; index < commits.size(); index++) {
                if (commits.get(index).mutations().stream().anyMatch(
                        dev.openallay.guide.history.GuideHistoryMutation.ReplaceContext.class::isInstance)) return index;
            }
            return -1;
        }
    }

    private static final class Local implements GuideLocalEndpoint {
        private Object identity = new Object();
        private int asks;
        private String profile;
        private String session;
        private CancellationSignal cancellation;
        private Consumer<AgentEvent> usage;
        private CompletableFuture<ToolResult<GuidePreparedCompaction>> preparing;
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override public Optional<GuideContextSpec> contextSpec(String profile) {
            return Optional.of(new GuideContextSpec(new dev.openallay.agent.context.ContextBudget(10_000, 512),
                    100, "selected-model", new dev.openallay.agent.context.Utf8ContextTokenEstimator()));
        }
        @Override public boolean compactAvailable(String profile) { return true; }
        @Override public Object compactIdentity(String profile) { return identity; }
        @Override public CompletableFuture<ToolResult<GuidePreparedCompaction>> prepareCompaction(
                String profile, UUID actor, String session, UUID control, List<ModelMessage> source,
                CancellationSignal cancellation, ImagePayloadResolver images, Consumer<AgentEvent> events) {
            assertEquals(ACTOR, actor);
            this.profile = profile;
            this.session = session;
            this.cancellation = cancellation;
            this.usage = events;
            preparing = new CompletableFuture<>();
            return preparing;
        }
        @Override public CompletableFuture<AgentResult> ask(UUID actor, String session, UUID request,
                String question, ToolInvocationContext context, Consumer<AgentEvent> events) {
            asks++;
            return new CompletableFuture<>();
        }
        @Override public boolean cancel(UUID actor, String session) { return true; }
        @Override public void clearSession(UUID actor, String session) {}
        @Override public void clearActor(UUID actor) {}
    }

    private static final class Prepared implements GuidePreparedCompaction {
        private final boolean compacted;
        private boolean published;
        private boolean closed;
        private final ContextCheckpoint checkpoint = new ContextCheckpoint(UUID.randomUUID(), 0, 2,
                "a".repeat(64), "selected-model", Instant.EPOCH, ContextCheckpoint.Status.SUCCEEDED,
                "{\"goals\":[]}", null, null, 100);
        private Prepared(boolean compacted) { this.compacted = compacted; }
        @Override public GuideCompactResult outcome() { return new GuideCompactResult(compacted
                ? GuideCompactResult.Status.COMPACTED : GuideCompactResult.Status.NOT_NEEDED,
                400, compacted ? 100 : 400, 1000, compacted ? checkpoint : null); }
        @Override public List<ModelMessage> projection() { return List.of(ModelMessage.userText("derived-summary")); }
        @Override public List<ModelMessage> source() { return List.of(ModelMessage.userText("original-source")); }
        @Override public boolean current() { return !closed; }
        @Override public boolean publish() { published = true; return true; }
        @Override public void close() { closed = true; }
    }

    private static <T> T success(ToolResult<T> result) {
        assertInstanceOf(ToolResult.Success.class, result);
        return ((ToolResult.Success<T>) result).value();
    }
    private static void assertFailure(ToolResult<?> result, String code) {
        assertEquals(code, assertInstanceOf(ToolResult.Failure.class, result).code());
    }
}
