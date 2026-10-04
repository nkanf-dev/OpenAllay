package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentState;
import dev.openallay.agent.context.ContextBudget;
import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.guide.history.GuideHistoryAccess;
import dev.openallay.guide.history.GuideHistoryActivity;
import dev.openallay.guide.history.GuideHistoryCommit;
import dev.openallay.guide.history.GuideHistoryContextRequest;
import dev.openallay.guide.history.GuideHistoryContextSeed;
import dev.openallay.guide.history.GuideHistoryCursor;
import dev.openallay.guide.history.GuideHistoryDeleteScope;
import dev.openallay.guide.history.GuideHistoryException;
import dev.openallay.guide.history.GuideHistoryMetadata;
import dev.openallay.guide.history.GuideHistoryMutation;
import dev.openallay.guide.history.GuideHistoryPage;
import dev.openallay.guide.history.GuideHistoryPageRequest;
import dev.openallay.guide.history.GuideHistoryScope;
import dev.openallay.model.ModelMessage;
import dev.openallay.tool.ToolResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class GuideServiceHistoryTest {
    private static final UUID ACTOR =
            UUID.fromString("e86bc174-e814-4fb7-a7ca-8ac52158fcad");
    private static final GuideHistoryScope SCOPE = GuideHistoryScope.derive(
            ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, "history.example");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-18T07:00:00Z"), ZoneOffset.UTC);

    @Test
    void unsupportedLayoutKeepsItsCodeForHistoryAndExportWithoutSavingReplacementData() {
        FakeHistory history = new FakeHistory();
        GuideService service = service(new FakeLocal(), history);
        var failure = new GuideHistoryException("history_layout_unsupported",
                "Guide history uses a different layout; the original database was not changed");
        history.pageFailure = failure;
        history.metadata.completeExceptionally(failure);

        assertEquals(GuidePersistenceSnapshot.State.UNAVAILABLE, service.snapshot().persistence().state());
        assertEquals("history_layout_unsupported", service.snapshot().persistence().failure().code());
        assertFailure(service.captureSelectedSessionForExport().join(), "history_layout_unsupported");
        assertTrue(history.commits.isEmpty(), "unsupported history must never be replaced by an empty live session");
    }

    @Test
    void loadsMetadataBeforeAcceptingRequestsAndHydratesActualContextOnlyOnRetry() {
        FakeHistory history = new FakeHistory();
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, history);

        assertEquals(GuidePersistenceSnapshot.State.LOADING,
                service.snapshot().persistence().state());
        assertFailure(service.ask("too early").join(), "history_loading");
        assertTrue(local.pending.isEmpty());

        GuideRequestSnapshot interrupted = interruptedRequest();
        recover(history, interrupted, GuideModelSelection.client("default"));

        assertEquals(GuidePersistenceSnapshot.State.AVAILABLE,
                service.snapshot().persistence().state());
        assertEquals(1, service.snapshot().sessions().getFirst().historyWindow().totalRequests());
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
        assertTrue(local.hydratedMessages.isEmpty());
        assertTrue(history.contextRequests.isEmpty());

        loadRecoveredPage(service);
        assertEquals(GuideRequestStatus.INTERRUPTED,
                service.snapshot().sessions().getFirst().requests().getFirst().status());
        assertTrue(local.hydratedMessages.isEmpty());
        UUID retry = success(service.retry(interrupted.requestId()).join());

        assertNotEquals(interrupted.requestId(), retry);
        assertTrue(local.pending.containsKey(retry));
        assertEquals(List.of(checkpoint()), local.hydratedCheckpoints);
        assertEquals(List.of(ModelMessage.userText("durable actual context")), local.hydratedMessages);
        assertEquals(1, history.contextRequests.size());
        assertEquals(SCOPE, history.contextRequests.getFirst().scope());
        assertEquals("main", history.contextRequests.getFirst().sessionId());
    }

    @Test
    void restoresTheSessionSelectionWithoutRebindingCapturedRequests() {
        FakeHistory history = new FakeHistory();
        GuideService service = service(new FakeLocal(), history);
        GuideRequestSnapshot interrupted = interruptedRequest();
        recover(history, interrupted, GuideModelSelection.client("removed-profile"));
        loadRecoveredPage(service);

        GuideSessionSnapshot restored = service.snapshot().sessions().getFirst();

        assertEquals(GuideModelSelection.client("removed-profile"), restored.modelSelection());
        assertEquals(GuideModelSelection.client("default"),
                restored.requests().getFirst().modelSelection());
        assertFailure(service.retry(interrupted.requestId()).join(), "model_not_configured");
        assertTrue(history.contextRequests.isEmpty());
    }

    @Test
    void recoveredServerSelectionReturnsToTheLocalDefaultForTheNewConnection() {
        FakeHistory history = new FakeHistory();
        GuideService service = service(new FakeLocal(), history, new FakeRemote(true));
        GuideRequestSnapshot interrupted = interruptedRequest(GuideTopology.SERVER);
        recover(history, interrupted, GuideModelSelection.server());
        loadRecoveredPage(service);

        GuideSessionSnapshot restored = service.snapshot().sessions().getFirst();

        assertEquals(GuideModelSelection.client("default"), restored.modelSelection());
        assertEquals(GuideModelSelection.server(),
                restored.requests().getFirst().modelSelection());
        assertEquals(GuideTopology.SERVER, restored.requests().getFirst().topology());
    }

    @Test
    void numericReceiptsRemainBillableBeforeCleanupButReleasedRequestCannotInventNewCalls() {
        QueuedDispatcher dispatcher = new QueuedDispatcher();
        QueuedLocal local = new QueuedLocal(dispatcher);
        FakeHistory history = new FakeHistory();
        GuideService service = queuedService(local, history, dispatcher);
        history.metadata.complete(Optional.empty()); dispatcher.runAll();
        var asking = service.ask("actual numeric owner"); dispatcher.runAll();
        UUID request = success(asking.join());
        service.cancel(); dispatcher.runAll();
        UUID actual = UUID.randomUUID();
        local.queue(request, new AgentEvent.ModelUsageStarted(actual, "test-model"));
        local.queue(request, new AgentEvent.ModelUsageObserved(actual, "test-model",
                new dev.openallay.model.ModelUsage(7, 2, 0)));
        dispatcher.runAll();
        assertEquals(7, service.telemetry().sessionUsage().inputTokens());
        assertEquals(1, service.telemetry().sessionUsage().actualCalls());
        assertEquals(GuideRequestStatus.CANCELLED, service.snapshot().sessions().getFirst().requests().getFirst().status());
        local.releaseCancelled(request); dispatcher.runAll();
        GuideSnapshot released = service.snapshot();
        int committed = history.commits.size();
        UUID invented = UUID.randomUUID();
        local.queue(request, new AgentEvent.ModelUsageStarted(invented, "test-model"));
        local.queue(request, new AgentEvent.ModelUsageObserved(invented, "test-model",
                new dev.openallay.model.ModelUsage(999, 99, 0)));
        local.queue(request, new AgentEvent.ModelUsageObserved(actual, "test-model",
                new dev.openallay.model.ModelUsage(7, 2, 0)));
        dispatcher.runAll();
        assertSame(released, service.snapshot());
        assertEquals(committed, history.commits.size());
        assertEquals(7, service.telemetry().sessionUsage().inputTokens());
        assertEquals(1, service.telemetry().sessionUsage().actualCalls());
        assertEquals(GuideRequestStatus.CANCELLED, service.snapshot().sessions().getFirst().requests().getFirst().status());
    }

    @Test
    void persistsSanitizedEventProjectionsAndIgnoresStaleCompletions() {
        FakeHistory history = new FakeHistory();
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, history);
        history.metadata.complete(Optional.empty());

        UUID request = success(service.ask("persist me").join());
        local.compact(request);
        local.tool(request);

        assertTrue(history.commits.size() >= 3);
        GuideHistoryMutation.UpsertTimelineEntry persistedEntry = history.commits.getLast()
                .mutations().stream()
                .filter(GuideHistoryMutation.UpsertTimelineEntry.class::isInstance)
                .map(GuideHistoryMutation.UpsertTimelineEntry.class::cast)
                .findFirst().orElseThrow();
        GuideToolActivity persistedTool = assertInstanceOf(
                GuideTimelineEntry.Tool.class, persistedEntry.entry()).activity();
        assertEquals(request, persistedEntry.requestId());
        assertNull(persistedTool.normalized());
        assertFalse(persistedTool.presentationMessages().isEmpty());
        assertEquals(List.of(new GuideHistoryMutation.AppendCheckpoint("main", checkpoint())),
                history.commits.stream().flatMap(commit -> commit.mutations().stream())
                        .filter(GuideHistoryMutation.AppendCheckpoint.class::isInstance)
                        .map(GuideHistoryMutation.AppendCheckpoint.class::cast).toList());

        int latestIndex = history.commitCompletions.size() - 1;
        history.commitCompletions.get(latestIndex).complete(null);
        long committed = service.snapshot().persistence().committedGeneration();
        history.commitCompletions.getFirst().complete(null);

        assertEquals(committed, service.snapshot().persistence().committedGeneration());
        assertEquals(GuidePersistenceSnapshot.State.AVAILABLE,
                service.snapshot().persistence().state());
    }

    @Test
    void persistsActiveAndOriginalRequestContextAsIndependentMutations() {
        FakeHistory history = new FakeHistory();
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, history);
        history.metadata.complete(Optional.empty());
        UUID request = success(service.ask("original turn").join());
        List<ModelMessage> active = List.of(ModelMessage.userText("compacted active context"));
        JsonObject arguments = new JsonObject();
        arguments.addProperty("source", "return mc.items.find(item => item.id === 'minecraft:apple');");
        JsonObject toolFailure = new JsonObject();
        toolFailure.addProperty("code", "stale_reference");
        List<ModelMessage> original = List.of(
                ModelMessage.userText("original turn"),
                new ModelMessage(dev.openallay.model.ModelRole.ASSISTANT, List.of(
                        new dev.openallay.model.ModelContent.ToolUse(
                                "original-call", "openallay:run_javascript", arguments))),
                new ModelMessage(dev.openallay.model.ModelRole.USER, List.of(
                        new dev.openallay.model.ModelContent.ToolResult(
                                "original-call", toolFailure, true))));

        local.updateContext(request, active, original);

        List<GuideHistoryMutation> mutations = history.commits.getLast().mutations();
        GuideHistoryMutation.ReplaceContext context = mutations.stream()
                .filter(GuideHistoryMutation.ReplaceContext.class::isInstance)
                .map(GuideHistoryMutation.ReplaceContext.class::cast)
                .findFirst().orElseThrow();
        GuideHistoryMutation.ReplaceRequestContext requestContext = mutations.stream()
                .filter(GuideHistoryMutation.ReplaceRequestContext.class::isInstance)
                .map(GuideHistoryMutation.ReplaceRequestContext.class::cast)
                .findFirst().orElseThrow();
        assertEquals("main", context.sessionId());
        assertEquals(active, context.messages());
        assertEquals(request, requestContext.requestId());
        assertEquals(original, requestContext.messages());
        assertNotEquals(context.messages(), requestContext.messages());
        assertEquals(List.of("original turn"), service.snapshot().sessions().getFirst()
                .messages().stream().map(GuideMessage::text).toList());
    }

    @Test
    void acceptsFinalActualContextWhileCompletingBeforeTheTerminalFinalText() {
        FakeHistory history = new FakeHistory();
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, history);
        history.metadata.complete(Optional.empty());
        UUID request = success(service.ask("finish turn").join());
        local.pending.get(request).accept(new AgentEvent.StateChanged(AgentState.COMPLETED));
        assertEquals(GuideRequestStatus.COMPLETING,
                service.snapshot().sessions().getFirst().requests().getFirst().status());
        List<ModelMessage> finalContext = List.of(ModelMessage.userText("final actual context"));
        int before = history.commits.size();

        local.updateContext(request, finalContext, finalContext);
        local.complete(request, "final answer");

        assertEquals(before + 2, history.commits.size());
        assertEquals(List.of(finalContext), history.commits.stream()
                .flatMap(commit -> commit.mutations().stream())
                .filter(GuideHistoryMutation.ReplaceContext.class::isInstance)
                .map(GuideHistoryMutation.ReplaceContext.class::cast)
                .map(GuideHistoryMutation.ReplaceContext::messages).toList());
        assertEquals(GuideRequestStatus.COMPLETED,
                service.snapshot().sessions().getFirst().requests().getFirst().status());
        assertEquals("final answer", service.snapshot().sessions().getFirst().messages().getLast().text());
    }

    @Test
    void terminalRequestsIgnoreLateContextAndCheckpointEvents() {
        for (GuideRequestStatus terminal : List.of(
                GuideRequestStatus.CANCELLED, GuideRequestStatus.COMPLETED, GuideRequestStatus.FAILED)) {
            FakeHistory history = new FakeHistory();
            FakeLocal local = new FakeLocal();
            GuideService service = service(local, history);
            history.metadata.complete(Optional.empty());
            UUID request = success(service.ask("keep accepted context").join());
            List<ModelMessage> accepted = List.of(ModelMessage.userText("accepted actual context"));
            local.updateContext(request, accepted, accepted);
            switch (terminal) {
                case CANCELLED -> assertInstanceOf(ToolResult.Success.class, service.cancel().join());
                case COMPLETED -> local.complete(request, "accepted answer");
                case FAILED -> local.pending.get(request).accept(
                        new AgentEvent.Failed("provider_error", "accepted failure"));
                default -> throw new AssertionError("nonterminal fixture");
            }
            history.completeAllCommits();
            GuideSnapshot before = service.snapshot();
            int commitsBefore = history.commits.size();

            local.updateContext(request,
                    List.of(ModelMessage.userText("late active context")),
                    List.of(ModelMessage.userText("late original context")));
            local.compact(request);

            assertSame(before, service.snapshot());
            assertEquals(terminal, service.snapshot().sessions().getFirst().requests().getFirst().status());
            assertTrue(service.snapshot().sessions().getFirst().checkpoints().isEmpty());
            assertEquals(commitsBefore, history.commits.size());
        }
    }

    @Test
    void cancelledRequestAcceptsOneFinalizedContextHandoffWithoutASuccessor() {
        QueuedDispatcher dispatcher = new QueuedDispatcher();
        QueuedLocal local = new QueuedLocal(dispatcher);
        FakeHistory history = new FakeHistory();
        GuideService service = queuedService(local, history, dispatcher);
        history.metadata.complete(Optional.empty());
        dispatcher.runAll();
        CompletableFuture<ToolResult<UUID>> asking = service.ask("cancel after tool pair");
        dispatcher.runAll();
        UUID request = success(asking.join());
        CompletableFuture<ToolResult<Boolean>> cancelling = service.cancel();
        dispatcher.runAll();
        assertEquals(Boolean.TRUE, assertInstanceOf(ToolResult.Success.class, cancelling.join()).value());
        GuideRequestSnapshot cancelled = service.snapshot().sessions().getFirst().requests().getFirst();
        List<ModelMessage> original = finalizedToolPair("cancelled-call");
        List<ModelMessage> active = new ArrayList<>();
        active.add(ModelMessage.userText("prior active session context"));
        active.addAll(original);
        int before = history.commits.size();

        local.queue(request, new AgentEvent.ContextFinalized(active, original));
        dispatcher.runAll();

        assertEquals(before + 1, history.commits.size());
        assertEquals(cancelled, service.snapshot().sessions().getFirst().requests().getFirst());
        assertEquals(GuideRequestStatus.CANCELLED, cancelled.status());
        assertEquals(List.of(active), history.commits.getLast().mutations().stream()
                .filter(GuideHistoryMutation.ReplaceContext.class::isInstance)
                .map(GuideHistoryMutation.ReplaceContext.class::cast)
                .map(GuideHistoryMutation.ReplaceContext::messages).toList());
        assertEquals(List.of(original), history.commits.getLast().mutations().stream()
                .filter(GuideHistoryMutation.ReplaceRequestContext.class::isInstance)
                .map(GuideHistoryMutation.ReplaceRequestContext.class::cast)
                .map(GuideHistoryMutation.ReplaceRequestContext::messages).toList());
        GuideSnapshot accepted = service.snapshot();

        local.queue(request, new AgentEvent.ContextFinalized(
                List.of(ModelMessage.userText("duplicate active")),
                List.of(ModelMessage.userText("duplicate original"))));
        dispatcher.runAll();

        assertSame(accepted, service.snapshot());
        assertEquals(before + 1, history.commits.size());
    }

    @Test
    void finalizedCancelledArchiveCannotOverrideAnImmediateSuccessorEvenWhenItFails() {
        for (boolean successorFails : List.of(false, true)) {
            QueuedDispatcher dispatcher = new QueuedDispatcher();
            QueuedLocal local = new QueuedLocal(dispatcher);
            FakeHistory history = new FakeHistory();
            GuideService service = queuedService(local, history, dispatcher);
            history.metadata.complete(Optional.empty());
            dispatcher.runAll();
            CompletableFuture<ToolResult<UUID>> asking = service.ask("old cancelled turn");
            dispatcher.runAll();
            UUID old = success(asking.join());
            service.cancel();
            CompletableFuture<ToolResult<UUID>> premature = service.retry(old);
            dispatcher.runAll();
            assertFailure(premature.join(), "agent_busy");
            local.releaseCancelled(old);
            dispatcher.runAll();
            CompletableFuture<ToolResult<UUID>> retrying = service.retry(old);
            dispatcher.runAll();
            UUID successor = success(retrying.join());
            List<ModelMessage> current = List.of(ModelMessage.userText("successor active context"));
            List<ModelMessage> successorOriginal = List.of(ModelMessage.userText("successor original turn"));
            local.queue(successor, new AgentEvent.ContextUpdated(current, successorOriginal));
            if (successorFails) {
                local.queue(successor, new AgentEvent.Failed("provider_error", "successor failed"));
            }
            dispatcher.runAll();
            GuideRequestSnapshot successorBefore = service.snapshot().sessions().getFirst().requests().getLast();
            List<ModelMessage> oldOriginal = finalizedToolPair("old-completed-call");
            List<ModelMessage> oldActive = new ArrayList<>();
            oldActive.add(ModelMessage.userText("obsolete active prefix"));
            oldActive.addAll(oldOriginal);
            int before = history.commits.size();

            local.queue(old, new AgentEvent.ContextFinalized(oldActive, oldOriginal));
            dispatcher.runAll();

            assertEquals(before + 1, history.commits.size());
            assertEquals(successorBefore, service.snapshot().sessions().getFirst().requests().getLast());
            List<GuideHistoryMutation> mutations = history.commits.getLast().mutations();
            assertTrue(mutations.stream().noneMatch(GuideHistoryMutation.ReplaceContext.class::isInstance));
            GuideHistoryMutation.ReplaceRequestContext archived = mutations.stream()
                    .filter(GuideHistoryMutation.ReplaceRequestContext.class::isInstance)
                    .map(GuideHistoryMutation.ReplaceRequestContext.class::cast)
                    .findFirst().orElseThrow();
            assertEquals(old, archived.requestId());
            assertEquals(oldOriginal, archived.messages());
            List<ModelMessage> latestActive = history.commits.stream()
                    .flatMap(commit -> commit.mutations().stream())
                    .filter(GuideHistoryMutation.ReplaceContext.class::isInstance)
                    .map(GuideHistoryMutation.ReplaceContext.class::cast)
                    .reduce((earlier, latest) -> latest).orElseThrow().messages();
            assertEquals(current, latestActive);
            CompletableFuture<ToolResult<dev.openallay.guide.export.GuideSessionExportSnapshot>> exporting =
                    service.captureSelectedSessionForExport();
            dispatcher.runAll();
            var exported = assertInstanceOf(ToolResult.Success.class, exporting.join());
            var snapshot = (dev.openallay.guide.export.GuideSessionExportSnapshot) exported.value();
            assertEquals(oldOriginal, snapshot.requests().getFirst().originalContext());
            assertEquals(successorOriginal, snapshot.requests().getLast().originalContext());
        }
    }

    @Test
    void cancelledFinalizationSurvivesPageEvictionWithoutRestoringItsViewportRequest() {
        for (boolean hasSuccessor : List.of(false, true)) {
            QueuedDispatcher dispatcher = new QueuedDispatcher();
            QueuedLocal local = new QueuedLocal(dispatcher);
            FakeHistory history = new FakeHistory();
            GuideService service = queuedService(local, history, dispatcher);
            GuideRequestSnapshot historical = interruptedRequest();
            recover(history, historical, GuideModelSelection.client("default"));
            dispatcher.runAll();
            CompletableFuture<ToolResult<UUID>> asking = service.ask("cancel outside history window");
            dispatcher.runAll();
            UUID cancelled = success(asking.join());
            service.cancel();
            dispatcher.runAll();
            local.releaseCancelled(cancelled);
            dispatcher.runAll();
            UUID successor = null;
            List<ModelMessage> newerActive = List.of(ModelMessage.userText("newer retained active context"));
            if (hasSuccessor) {
                CompletableFuture<ToolResult<UUID>> next = service.ask("newer visible turn");
                dispatcher.runAll();
                successor = success(next.join());
                local.queue(successor, new AgentEvent.ContextUpdated(newerActive,
                        List.of(ModelMessage.userText("newer original turn"))));
                dispatcher.runAll();
            }
            CompletableFuture<ToolResult<GuideHistoryPage>> paging = service.requestHistoryWindow(
                    "main", GuideHistoryPageRequest.Direction.NEWEST, null, 1);
            dispatcher.runAll();
            assertInstanceOf(ToolResult.Success.class, paging.join());
            List<UUID> visible = service.snapshot().sessions().getFirst().requests().stream()
                    .map(GuideRequestSnapshot::requestId).toList();
            assertEquals(hasSuccessor ? List.of(historical.requestId(), successor)
                    : List.of(historical.requestId()), visible);
            assertFalse(visible.contains(cancelled));
            GuideSnapshot pageSnapshot = service.snapshot();
            int before = history.commits.size();
            local.queue(cancelled, new AgentEvent.ContextUpdated(
                    List.of(ModelMessage.userText("ordinary evicted late active")),
                    List.of(ModelMessage.userText("ordinary evicted late original"))));
            local.queue(cancelled, new AgentEvent.ContextCompacted(checkpoint()));
            dispatcher.runAll();
            assertSame(pageSnapshot, service.snapshot());
            assertEquals(before, history.commits.size());
            List<ModelMessage> original = finalizedToolPair("evicted-completed-call");
            List<ModelMessage> active = new ArrayList<>();
            active.add(ModelMessage.userText("cancelled session active prefix"));
            active.addAll(original);

            local.queue(cancelled, new AgentEvent.ContextFinalized(active, original));
            dispatcher.runAll();

            assertEquals(before + 1, history.commits.size());
            assertEquals(visible, service.snapshot().sessions().getFirst().requests().stream()
                    .map(GuideRequestSnapshot::requestId).toList());
            assertEquals(pageSnapshot.sessions().getFirst().requests(),
                    service.snapshot().sessions().getFirst().requests());
            List<GuideHistoryMutation> mutations = history.commits.getLast().mutations();
            GuideHistoryMutation.ReplaceRequestContext archived = mutations.stream()
                    .filter(GuideHistoryMutation.ReplaceRequestContext.class::isInstance)
                    .map(GuideHistoryMutation.ReplaceRequestContext.class::cast)
                    .findFirst().orElseThrow();
            assertEquals(cancelled, archived.requestId());
            assertEquals(original, archived.messages());
            List<GuideHistoryMutation.ReplaceContext> activeChanges = mutations.stream()
                    .filter(GuideHistoryMutation.ReplaceContext.class::isInstance)
                    .map(GuideHistoryMutation.ReplaceContext.class::cast).toList();
            if (hasSuccessor) {
                assertTrue(activeChanges.isEmpty());
                assertEquals(newerActive, history.commits.stream()
                        .flatMap(commit -> commit.mutations().stream())
                        .filter(GuideHistoryMutation.ReplaceContext.class::isInstance)
                        .map(GuideHistoryMutation.ReplaceContext.class::cast)
                        .reduce((earlier, latest) -> latest).orElseThrow().messages());
            } else {
                assertEquals(List.of(active), activeChanges.stream()
                        .map(GuideHistoryMutation.ReplaceContext::messages).toList());
            }
            GuideSnapshot finalized = service.snapshot();
            local.queue(cancelled, new AgentEvent.ContextFinalized(
                    List.of(ModelMessage.userText("duplicate evicted active")),
                    List.of(ModelMessage.userText("duplicate evicted original"))));
            dispatcher.runAll();
            assertSame(finalized, service.snapshot());
            assertEquals(before + 1, history.commits.size());
        }
    }

    @Test
    void finalizedContextIsIgnoredForActiveCompletedAndFailedRequests() {
        for (String state : List.of("active", "completed", "failed")) {
            QueuedDispatcher dispatcher = new QueuedDispatcher();
            QueuedLocal local = new QueuedLocal(dispatcher);
            FakeHistory history = new FakeHistory();
            GuideService service = queuedService(local, history, dispatcher);
            history.metadata.complete(Optional.empty());
            dispatcher.runAll();
            CompletableFuture<ToolResult<UUID>> asking = service.ask("not cancelled");
            dispatcher.runAll();
            UUID request = success(asking.join());
            if (state.equals("completed")) local.queue(request, new AgentEvent.FinalText("done"));
            if (state.equals("failed")) local.queue(request, new AgentEvent.Failed("provider_error", "failed"));
            dispatcher.runAll();
            GuideSnapshot before = service.snapshot();
            int commits = history.commits.size();

            local.queue(request, new AgentEvent.ContextFinalized(
                    finalizedToolPair("ignored-call"), finalizedToolPair("ignored-call")));
            dispatcher.runAll();

            assertSame(before, service.snapshot());
            assertEquals(commits, history.commits.size());
        }
    }

    @Test
    void removedOrDisconnectedCancelledRequestsDropFinalizedContextWithoutWrites() throws Exception {
        for (String removal : List.of("clear", "close", "delete", "disconnect")) {
            QueuedDispatcher dispatcher = new QueuedDispatcher();
            QueuedLocal local = new QueuedLocal(dispatcher);
            FakeHistory history = new FakeHistory();
            GuideService service = queuedService(local, history, dispatcher);
            history.metadata.complete(Optional.empty());
            dispatcher.runAll();
            CompletableFuture<ToolResult<UUID>> asking = service.ask("removed cancelled turn");
            dispatcher.runAll();
            UUID request = success(asking.join());
            service.cancel();
            dispatcher.runAll();
            local.releaseCancelled(request);
            dispatcher.runAll();
            history.completeAllCommits();
            dispatcher.runAll();
            CompletableFuture<Void> disconnecting = null;
            switch (removal) {
                case "clear" -> service.clearSelectedSession();
                case "close" -> service.closeSession("main");
                case "delete" -> service.deleteCurrentHistory();
                case "disconnect" -> disconnecting = service.disconnect();
                default -> throw new AssertionError("unknown removal fixture");
            }
            dispatcher.runAll();
            if (removal.equals("delete")) history.completeDeletion();
            history.completeAllCommits();
            dispatcher.runAll();
            if (disconnecting != null) dispatcher.runUntil(disconnecting::isDone);
            GuideSnapshot before = service.snapshot();
            int commits = history.commits.size();

            local.queue(request, new AgentEvent.ContextFinalized(
                    finalizedToolPair("removed-call"), finalizedToolPair("removed-call")));
            dispatcher.runAll();

            assertSame(before, service.snapshot());
            assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
            assertEquals(commits, history.commits.size());
        }
    }

    @Test
    void failedContextsRemainOwnedUntilASuccessfulCumulativeSuccessorCommit() {
        FakeHistory history = new FakeHistory(false);
        FakeLocal local = new FakeLocal(true);
        GuideService service = service(local, history);
        history.metadata.complete(Optional.empty());
        UUID request = success(service.ask("synthetic failed context turn").join());
        history.completeAllCommits();
        List<ModelMessage> active = List.of(ModelMessage.userText("synthetic active context 雪"));
        List<ModelMessage> original = finalizedToolPair("synthetic-original-call");
        int contextIndex = history.commits.size();
        local.updateContext(request, active, original);
        local.complete(request, "synthetic terminal answer");

        assertEquals(contextIndex + 1, history.commits.size(),
                "later events must wait for the current writer acknowledgment");
        history.commitCompletions.get(contextIndex).completeExceptionally(
                new GuideHistoryException("history_write_failed", "synthetic failure"));

        assertEquals(GuidePersistenceSnapshot.State.UNAVAILABLE,
                service.snapshot().persistence().state());
        assertEquals("history_write_failed", service.snapshot().persistence().failure().code());
        assertEquals(contextIndex + 2, history.commits.size());
        List<GuideHistoryMutation> recovering = history.commits.getLast().mutations();
        assertEquals(List.of(active), recovering.stream()
                .filter(GuideHistoryMutation.ReplaceContext.class::isInstance)
                .map(GuideHistoryMutation.ReplaceContext.class::cast)
                .map(GuideHistoryMutation.ReplaceContext::messages).toList());
        assertEquals(List.of(original), recovering.stream()
                .filter(GuideHistoryMutation.ReplaceRequestContext.class::isInstance)
                .map(GuideHistoryMutation.ReplaceRequestContext.class::cast)
                .map(GuideHistoryMutation.ReplaceRequestContext::messages).toList());
        assertEquals(GuideRequestStatus.COMPLETED, recovering.stream()
                .filter(GuideHistoryMutation.UpsertRequest.class::isInstance)
                .map(GuideHistoryMutation.UpsertRequest.class::cast)
                .filter(mutation -> mutation.request().requestId().equals(request))
                .findFirst().orElseThrow().request().status());
        assertTrue(service.snapshot().persistence().committedGeneration()
                < service.snapshot().persistence().submittedGeneration());

        history.commitCompletions.getLast().complete(null);

        assertEquals(GuidePersistenceSnapshot.State.AVAILABLE,
                service.snapshot().persistence().state());
        assertEquals(service.snapshot().persistence().submittedGeneration(),
                service.snapshot().persistence().committedGeneration());
        assertEquals(1, local.pending.size(), "durability retry must not replay the provider");
        int acknowledged = history.commits.size();
        service.selectSession("main").join();
        assertEquals(acknowledged, history.commits.size(),
                "an acknowledged context must not be rewritten for an unchanged publish");
    }

    @Test
    void idleWriteFailureRetainsContextForAnExplicitLaterPublishWithoutFalseAvailability() {
        FakeHistory history = new FakeHistory(false);
        FakeLocal local = new FakeLocal(true);
        GuideService service = service(local, history);
        history.metadata.complete(Optional.empty());
        UUID request = success(service.ask("synthetic idle failure").join());
        history.completeAllCommits();
        List<ModelMessage> original = finalizedToolPair("synthetic-idle-call");
        local.updateContext(request, original, original);
        int failedIndex = history.commits.size() - 1;
        history.commitCompletions.get(failedIndex).completeExceptionally(
                new GuideHistoryException("history_write_failed", "synthetic idle failure"));
        assertEquals(failedIndex + 1, history.commits.size(), "do not spin retrying a failed store");
        assertEquals(GuidePersistenceSnapshot.State.UNAVAILABLE,
                service.snapshot().persistence().state());

        service.selectSession("main").join();

        assertEquals(failedIndex + 2, history.commits.size());
        assertEquals(GuidePersistenceSnapshot.State.UNAVAILABLE,
                service.snapshot().persistence().state(),
                "a new submitted write is not proof that the failed data is durable");
        assertEquals(List.of(original), history.commits.getLast().mutations().stream()
                .filter(GuideHistoryMutation.ReplaceRequestContext.class::isInstance)
                .map(GuideHistoryMutation.ReplaceRequestContext.class::cast)
                .map(GuideHistoryMutation.ReplaceRequestContext::messages).toList());
        history.completeAllCommits();
        assertEquals(GuidePersistenceSnapshot.State.AVAILABLE,
                service.snapshot().persistence().state());
    }

    @Test
    void queuedChangesCoalesceByUnitAndRetryIncludesUnacknowledgedInitialRows() {
        FakeHistory history = new FakeHistory(false);
        FakeLocal local = new FakeLocal(true);
        GuideService service = service(local, history);
        history.metadata.complete(Optional.empty());
        UUID request = success(service.ask("synthetic queued stream").join());
        List<ModelMessage> latest = List.of();
        for (int index = 0; index < 128; index++) {
            latest = List.of(ModelMessage.userText("synthetic latest snapshot " + index));
            local.updateContext(request, latest, latest);
        }
        local.compact(request);
        local.tool(request);
        local.complete(request, "synthetic completed stream");
        assertEquals(1, history.commits.size());

        history.commitCompletions.getFirst().completeExceptionally(
                new GuideHistoryException("history_write_failed", "synthetic initial failure"));

        assertEquals(2, history.commits.size());
        List<GuideHistoryMutation> retry = history.commits.getLast().mutations();
        assertEquals(1, retry.stream().filter(GuideHistoryMutation.UpsertSession.class::isInstance).count());
        assertEquals(1, retry.stream().filter(GuideHistoryMutation.UpsertRequest.class::isInstance).count());
        assertEquals(List.of(new GuideHistoryMutation.AppendCheckpoint("main", checkpoint())), retry.stream()
                .filter(GuideHistoryMutation.AppendCheckpoint.class::isInstance)
                .map(GuideHistoryMutation.AppendCheckpoint.class::cast).toList());
        assertEquals(1, retry.stream().filter(GuideHistoryMutation.ReplaceContext.class::isInstance).count());
        assertEquals(1, retry.stream().filter(GuideHistoryMutation.ReplaceRequestContext.class::isInstance).count());
        assertEquals(List.of(latest), retry.stream()
                .filter(GuideHistoryMutation.ReplaceRequestContext.class::isInstance)
                .map(GuideHistoryMutation.ReplaceRequestContext.class::cast)
                .map(GuideHistoryMutation.ReplaceRequestContext::messages).toList());
        assertEquals(1, retry.stream().filter(GuideHistoryMutation.UpsertTimelineEntry.class::isInstance)
                .map(GuideHistoryMutation.UpsertTimelineEntry.class::cast)
                .filter(mutation -> mutation.entry() instanceof GuideTimelineEntry.Tool).count());
        history.completeAllCommits();
        assertEquals(GuidePersistenceSnapshot.State.AVAILABLE,
                service.snapshot().persistence().state());
    }

    @Test
    void clearAndCloseRemainOrderedAfterAFailedInFlightContextWithoutResurrectingIt() {
        for (boolean close : List.of(false, true)) {
            FakeHistory history = new FakeHistory(false);
            FakeLocal local = new FakeLocal(true);
            GuideService service = service(local, history);
            history.metadata.complete(Optional.empty());
            UUID oldRequest = success(service.ask("synthetic deleted old turn").join());
            history.completeAllCommits();
            List<ModelMessage> oldContext = finalizedToolPair("synthetic-deleted-call");
            local.updateContext(oldRequest, oldContext, oldContext);
            int failedIndex = history.commits.size() - 1;
            local.complete(oldRequest, "synthetic old terminal");
            if (close) service.closeSession("main").join();
            else service.clearSelectedSession().join();
            UUID newerRequest = success(service.ask("synthetic recreated turn").join());
            List<ModelMessage> newerContext = finalizedToolPair("synthetic-recreated-call");
            local.updateContext(newerRequest, newerContext, newerContext);
            local.compact(newerRequest);
            local.complete(newerRequest, "synthetic new terminal");
            assertFailure(service.deleteActorHistory().join(), "history_delete_busy");
            assertTrue(history.deletes.isEmpty());

            history.commitCompletions.get(failedIndex).completeExceptionally(
                    new GuideHistoryException("history_write_failed", "synthetic barrier failure"));

            List<GuideHistoryMutation> retry = history.commits.getLast().mutations();
            assertEquals(1, retry.stream().filter(mutation -> close
                    ? mutation instanceof GuideHistoryMutation.DeleteSession
                    : mutation instanceof GuideHistoryMutation.ClearSession).count());
            assertTrue(retry.stream().noneMatch(mutation ->
                    mutation instanceof GuideHistoryMutation.UpsertRequest row
                            && row.request().requestId().equals(oldRequest)
                    || mutation instanceof GuideHistoryMutation.ReplaceRequestContext context
                            && context.requestId().equals(oldRequest)));
            assertEquals(List.of(newerContext), retry.stream()
                    .filter(GuideHistoryMutation.ReplaceContext.class::isInstance)
                    .map(GuideHistoryMutation.ReplaceContext.class::cast)
                    .map(GuideHistoryMutation.ReplaceContext::messages).toList());
            int barrier = -1;
            int newRow = -1;
            for (int index = 0; index < retry.size(); index++) {
                if (retry.get(index) instanceof GuideHistoryMutation.DeleteSession
                        || retry.get(index) instanceof GuideHistoryMutation.ClearSession) barrier = index;
                if (retry.get(index) instanceof GuideHistoryMutation.UpsertRequest row
                        && row.request().requestId().equals(newerRequest)) newRow = index;
            }
            assertTrue(barrier >= 0 && newRow > barrier);
            history.completeAllCommits();
            assertEquals(GuidePersistenceSnapshot.State.AVAILABLE,
                    service.snapshot().persistence().state());
            int committed = history.commits.size();
            service.selectSession("main").join();
            assertEquals(committed, history.commits.size());
            assertEquals(List.of(newerRequest), service.snapshot().sessions().getFirst().requests()
                    .stream().map(GuideRequestSnapshot::requestId).toList());
        }
    }

    @Test
    void acknowledgmentOfAnOlderBatchCannotMarkQueuedFinalDataAvailable() {
        FakeHistory history = new FakeHistory(false);
        FakeLocal local = new FakeLocal(true);
        GuideService service = service(local, history);
        history.metadata.complete(Optional.empty());
        UUID request = success(service.ask("synthetic ordered acknowledgments").join());
        CompletableFuture<Void> first = history.commitCompletions.getFirst();
        local.complete(request, "synthetic final delta");
        long submitted = service.snapshot().persistence().submittedGeneration();
        assertEquals(1, history.commits.size());

        first.complete(null);

        assertEquals(2, history.commits.size());
        assertEquals(GuidePersistenceSnapshot.State.SAVING, service.snapshot().persistence().state());
        assertEquals(submitted, service.snapshot().persistence().submittedGeneration());
        assertTrue(service.snapshot().persistence().committedGeneration() < submitted);
        GuidePersistenceSnapshot waiting = service.snapshot().persistence();
        first.completeExceptionally(new GuideHistoryException("history_write_failed", "stale failure"));
        assertEquals(waiting, service.snapshot().persistence());
        history.commitCompletions.getLast().complete(null);
        assertEquals(GuidePersistenceSnapshot.available(submitted), service.snapshot().persistence());
    }

    @Test
    void disconnectDrainsQueuedCancellationBeforeFlushingWithoutCapturingEmptyMemory() {
        FakeHistory history = new FakeHistory(false);
        FakeLocal local = new FakeLocal(true);
        GuideService service = service(local, history);
        history.metadata.complete(Optional.empty());
        UUID request = success(service.ask("synthetic disconnect while writing").join());
        List<ModelMessage> original = finalizedToolPair("synthetic-disconnect-call");
        local.updateContext(request, original, original);

        CompletableFuture<Void> disconnecting = service.disconnect();

        assertFalse(disconnecting.isDone());
        assertEquals(1, history.commits.size());
        assertEquals(GuideRequestStatus.CANCELLED,
                service.snapshot().sessions().getFirst().requests().getFirst().status());
        local.releaseCancelled(request);
        history.completeAllCommits();
        disconnecting.join();
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
        assertEquals(2, history.commits.size());
        List<GuideHistoryMutation> finalWrite = history.commits.getLast().mutations();
        assertEquals(GuideRequestStatus.CANCELLED, finalWrite.stream()
                .filter(GuideHistoryMutation.UpsertRequest.class::isInstance)
                .map(GuideHistoryMutation.UpsertRequest.class::cast)
                .filter(mutation -> mutation.request().requestId().equals(request))
                .findFirst().orElseThrow().request().status());
        assertEquals(List.of(original), finalWrite.stream()
                .filter(GuideHistoryMutation.ReplaceRequestContext.class::isInstance)
                .map(GuideHistoryMutation.ReplaceRequestContext.class::cast)
                .map(GuideHistoryMutation.ReplaceRequestContext::messages).toList());
        assertTrue(history.commits.stream().flatMap(commit -> commit.mutations().stream())
                .noneMatch(mutation -> mutation instanceof GuideHistoryMutation.ClearSession
                        || mutation instanceof GuideHistoryMutation.DeleteSession));
        assertEquals(1, history.flushCalls);
    }

    @Test
    void commitFailureKeepsInMemoryRequestAndMarksItUnsaved() {
        FakeHistory history = new FakeHistory(false);
        FakeLocal local = new FakeLocal(true);
        GuideService service = service(local, history);
        history.metadata.complete(Optional.empty());

        UUID request = success(service.ask("unsaved").join());
        CompletableFuture<Void> latest = history.commitCompletions.getLast();
        latest.completeExceptionally(new GuideHistoryException(
                "history_write_failed", "injected"));

        assertEquals(request, service.snapshot().sessions().getFirst().requests().getFirst().requestId());
        assertEquals(GuidePersistenceSnapshot.State.UNAVAILABLE,
                service.snapshot().persistence().state());
        assertEquals("history_write_failed", service.snapshot().persistence().failure().code());
    }

    @Test
    void disconnectPersistsCancellationAndNeverWritesEmptyReplacement() {
        FakeHistory history = new FakeHistory();
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, history);
        history.metadata.complete(Optional.empty());
        UUID request = success(service.ask("disconnect").join());
        history.completeAllCommits();
        int before = history.commits.size();

        CompletableFuture<Void> disconnect = service.disconnect();
        assertFalse(disconnect.isDone(), "disconnect must await the actual endpoint's finalization");
        local.releaseCancelled(request);
        history.completeAllCommits();
        disconnect.join();

        assertEquals(before + 1, history.commits.size());
        GuideRequestSnapshot durable = history.commits.getLast().mutations().stream()
                .filter(GuideHistoryMutation.UpsertRequest.class::isInstance)
                .map(GuideHistoryMutation.UpsertRequest.class::cast)
                .map(GuideHistoryMutation.UpsertRequest::request)
                .findFirst().orElseThrow();
        assertEquals(request, durable.requestId());
        assertEquals(GuideRequestStatus.CANCELLED, durable.status());
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
    }

    @Test
    void loadFailureKeepsAgentUsableWithExplicitUnsavedState() {
        FakeHistory history = new FakeHistory();
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, history);
        history.metadata.completeExceptionally(new GuideHistoryException(
                "history_load_failed", "injected"));

        assertEquals(GuidePersistenceSnapshot.State.UNAVAILABLE,
                service.snapshot().persistence().state());
        assertInstanceOf(ToolResult.Success.class, service.ask("memory only").join());
    }

    @Test
    void sendsRecoveredActualContextToTheSelectedServerModel() {
        FakeHistory history = new FakeHistory();
        FakeRemote remote = new FakeRemote(true);
        GuideService service = service(new FakeLocal(), history, remote);
        GuideRequestSnapshot interrupted = interruptedRequest();
        recover(history, interrupted, GuideModelSelection.client("default"));
        successMode(service.setModelMode(GuideModelMode.SERVER).join());

        success(service.ask("continue remotely").join());

        assertEquals(List.of(ModelMessage.userText("durable actual context")), remote.history);
        assertEquals(1, history.contextRequests.size());
        assertTrue(history.pageRequests.isEmpty());
    }

    @Test
    void laterRequestsReuseLiveLocalContextWhileDifferentSessionsHydrateIndependently() {
        FakeHistory history = new FakeHistory();
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, history);
        history.contextSeeds.put("main", new GuideHistoryContextSeed(
                "main", List.of(ModelMessage.userText("main persisted context")), List.of(), 10));
        history.contextSeeds.put("other", new GuideHistoryContextSeed(
                "other", List.of(ModelMessage.userText("other persisted context")), List.of(), 10));
        history.metadata.complete(Optional.empty());
        UUID first = success(service.ask("first").join());
        local.complete(first, "done");

        success(service.ask("same connection").join());
        service.selectSession("other").join();
        UUID other = success(service.ask("other session").join());
        local.complete(other, "other done");
        success(service.ask("same other connection").join());

        assertEquals(List.of("main", "other"), history.contextRequests.stream()
                .map(GuideHistoryContextRequest::sessionId).toList());
        assertEquals(List.of(ModelMessage.userText("main persisted context"),
                ModelMessage.userText("other persisted context")), local.hydratedMessages);
        assertEquals(4, local.pending.size());
    }

    @Test
    void mismatchedMetadataScopeIsUnavailableAndNeverHydratesAnotherPartition() {
        FakeHistory history = new FakeHistory();
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, history);
        GuideHistoryScope other = GuideHistoryScope.derive(
                ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, "other.example");
        history.metadata.complete(Optional.of(new GuideHistoryMetadata(
                other, "main", List.of(new GuideHistoryMetadata.Session(
                        "main", 0, GuideModelSelection.client("default"), 0, null, null)),
                CLOCK.instant())));

        assertEquals(GuidePersistenceSnapshot.State.UNAVAILABLE,
                service.snapshot().persistence().state());
        assertEquals("history_scope_mismatch", service.snapshot().persistence().failure().code());
        assertTrue(local.hydratedMessages.isEmpty());
        assertTrue(history.contextRequests.isEmpty());
        UUID memoryOnly = success(service.ask("memory only").join());
        assertTrue(local.pending.containsKey(memoryOnly));
        assertTrue(history.commits.isEmpty());
    }

    @Test
    void lateMetadataAfterDisconnectCannotRestoreRequestsOrHydrateContext() {
        FakeHistory history = new FakeHistory();
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, history);
        service.disconnect().join();

        recover(history, interruptedRequest(), GuideModelSelection.client("default"));

        assertEquals(List.of("main"), service.snapshot().sessions().stream()
                .map(GuideSessionSnapshot::sessionId).toList());
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
        assertTrue(local.hydratedMessages.isEmpty());
        assertTrue(history.contextRequests.isEmpty());
        assertTrue(history.commits.isEmpty());
    }

    @Test
    void actorDeleteRejectsAnActiveRequestInAnySessionWithoutCancellingIt() {
        FakeHistory history = new FakeHistory();
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, history);
        history.metadata.complete(Optional.empty());
        service.selectSession("other").join();
        history.completeAllCommits();
        UUID active = success(service.ask("stay active").join());
        history.completeAllCommits();

        assertFailure(service.deleteActorHistory().join(), "history_delete_busy");

        assertEquals(active, service.snapshot().sessions().stream()
                .flatMap(session -> session.requests().stream())
                .filter(request -> !request.terminal())
                .findFirst().orElseThrow().requestId());
        assertTrue(history.deletes.isEmpty());
        assertTrue(local.pending.containsKey(active));
    }

    @Test
    void successfulPartitionDeleteBlocksStateChangesAndResetsMemoryWithoutCommitting() {
        FakeHistory history = new FakeHistory();
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, history);
        history.metadata.complete(Optional.empty());
        service.selectSession("other").join();
        history.completeAllCommits();
        int commitsBefore = history.commits.size();

        CompletableFuture<ToolResult<Boolean>> deleting = service.deleteCurrentHistory();

        assertEquals(List.of(GuideHistoryDeleteScope.partition(SCOPE)), history.deletes);
        assertFailure(service.ask("during delete").join(), "history_delete_busy");
        assertFailure(service.selectSession("blocked").join(), "history_delete_busy");
        assertFalse(deleting.isDone());
        history.completeDeletion();

        assertInstanceOf(ToolResult.Success.class, deleting.join());
        assertEquals(List.of("main"), service.snapshot().sessions().stream()
                .map(GuideSessionSnapshot::sessionId).toList());
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
        assertEquals(GuidePersistenceSnapshot.State.AVAILABLE,
                service.snapshot().persistence().state());
        assertEquals(commitsBefore, history.commits.size());
        assertEquals(List.of(ACTOR), local.clearedActors);
    }

    @Test
    void actorDeleteIsBoundToTheCurrentActorAndFailureRetainsExactSnapshot() {
        FakeHistory history = new FakeHistory();
        GuideService service = service(new FakeLocal(), history);
        history.metadata.complete(Optional.empty());
        service.selectSession("retained").join();
        history.completeAllCommits();
        GuideSnapshot before = service.snapshot();

        CompletableFuture<ToolResult<Boolean>> deleting = service.deleteActorHistory();
        assertEquals(List.of(GuideHistoryDeleteScope.actor(ACTOR)), history.deletes);
        history.failDeletion();

        assertFailure(deleting.join(), "history_delete_failed");
        assertSame(before, service.snapshot());
        assertInstanceOf(ToolResult.Success.class, service.selectSession("after-failure").join());
    }

    @Test
    void pendingWriteAndUnavailablePersistenceRejectDeletionWithoutRepositoryCall() {
        FakeHistory pending = new FakeHistory(false);
        GuideService service = service(new FakeLocal(), pending);
        pending.metadata.complete(Optional.empty());
        service.selectSession("pending").join();

        assertFailure(service.deleteCurrentHistory().join(), "history_delete_busy");
        assertTrue(pending.deletes.isEmpty());

        FakeHistory unavailable = new FakeHistory();
        GuideService unavailableService = service(new FakeLocal(), unavailable);
        assertFailure(unavailableService.deleteCurrentHistory().join(), "history_loading");
        unavailable.metadata.completeExceptionally(new GuideHistoryException(
                "history_load_failed", "injected"));
        assertFailure(unavailableService.deleteCurrentHistory().join(), "history_unavailable");
    }

    @Test
    void explicitDatabaseResetCanRecoverAnUnsupportedPreReleaseSchema() {
        FakeHistory history = new FakeHistory();
        GuideService service = service(new FakeLocal(), history);
        history.metadata.completeExceptionally(new GuideHistoryException(
                "history_schema_unsupported", "future schema"));
        assertEquals(GuidePersistenceSnapshot.State.UNAVAILABLE,
                service.snapshot().persistence().state());

        CompletableFuture<ToolResult<Boolean>> resetting = service.resetHistoryDatabase();

        assertEquals(1, history.resetCalls);
        history.completeDeletion();
        assertInstanceOf(ToolResult.Success.class, resetting.join());
        assertEquals(GuidePersistenceSnapshot.State.AVAILABLE,
                service.snapshot().persistence().state());
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
    }

    @Test
    void disconnectDuringDeleteLeavesDisconnectedMemoryCleanAndWaitsForTransaction() throws Exception {
        FakeHistory history = new FakeHistory();
        GuideService service = service(new FakeLocal(), history);
        history.metadata.complete(Optional.empty());
        service.selectSession("other").join();
        history.completeAllCommits();
        int writesBeforeDelete = history.commits.size();
        CompletableFuture<ToolResult<Boolean>> deleting = service.deleteCurrentHistory();

        CompletableFuture<Void> disconnect = service.disconnect();
        assertFalse(disconnect.isDone());
        history.completeDeletion();

        assertInstanceOf(ToolResult.Success.class, deleting.join());
        disconnect.get(2, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(List.of("main"), service.snapshot().sessions().stream()
                .map(GuideSessionSnapshot::sessionId).toList());
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
        assertEquals(writesBeforeDelete, history.commits.size(),
                "closing cleanup must not write an empty replacement after the actual delete transaction");
    }

    private static GuideService queuedService(
            QueuedLocal local, FakeHistory history, QueuedDispatcher dispatcher) {
        return new GuideService(ACTOR, local, new FakeRemote(false),
                (capabilities, correlation) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlation)),
                dispatcher, CLOCK, new Gson(), SCOPE, history);
    }

    private static List<ModelMessage> finalizedToolPair(String callId) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("source", "return mc.items.length;");
        JsonObject result = new JsonObject();
        result.addProperty("count", 3);
        return List.of(ModelMessage.userText("completed tool before stop"),
                new ModelMessage(dev.openallay.model.ModelRole.ASSISTANT, List.of(
                        new dev.openallay.model.ModelContent.ToolUse(
                                callId, "openallay:run_javascript", arguments))),
                new ModelMessage(dev.openallay.model.ModelRole.USER, List.of(
                        new dev.openallay.model.ModelContent.ToolResult(callId, result, false))),
                new ModelMessage(dev.openallay.model.ModelRole.ASSISTANT, List.of(
                        new dev.openallay.model.ModelContent.Text("request ended: agent_cancelled"))));
    }

    private static GuideService service(FakeLocal local, FakeHistory history) {
        return service(local, history, new FakeRemote(false));
    }

    private static GuideService service(
            FakeLocal local, FakeHistory history, FakeRemote remote) {
        return new GuideService(
                ACTOR,
                local,
                remote,
                (capabilities, correlation) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlation)),
                Runnable::run,
                CLOCK,
                new Gson(),
                SCOPE,
                history);
    }

    private static void recover(
            FakeHistory history, GuideRequestSnapshot request, GuideModelSelection selection) {
        GuideHistoryCursor cursor = new GuideHistoryCursor(0, request.requestId());
        history.recoveredPage = new GuideHistoryPage(
                "main", List.of(request), cursor, cursor, false, false);
        history.contextSeeds.put("main", new GuideHistoryContextSeed(
                "main", List.of(ModelMessage.userText("durable actual context")),
                List.of(checkpoint()), 10));
        history.metadata.complete(Optional.of(new GuideHistoryMetadata(
                SCOPE, "main", List.of(new GuideHistoryMetadata.Session(
                        "main", 0, selection, 1, cursor, cursor)), request.updatedAt())));
    }

    private static void loadRecoveredPage(GuideService service) {
        assertInstanceOf(ToolResult.Success.class, service.requestHistoryWindow(
                "main", GuideHistoryPageRequest.Direction.NEWEST, null, 1).join());
    }

    private static GuideContextSpec contextSpec() {
        return new GuideContextSpec(new ContextBudget(8_192, 1_024), 256, "test-model");
    }

    private static GuideRequestSnapshot interruptedRequest() {
        return interruptedRequest(GuideTopology.CLIENT_LOCAL);
    }

    private static GuideRequestSnapshot interruptedRequest(GuideTopology topology) {
        Instant terminal = Instant.parse("2026-07-18T06:59:00Z");
        return new GuideRequestSnapshot(
                UUID.fromString("3bfe1fc2-489d-46c4-a17d-a8706dd02863"),
                "main",
                topology,
                "retry after restart",
                List.of(new GuideTimelineEntry.Assistant(0, "partial", false, List.of())),
                GuideRequestStatus.INTERRUPTED,
                List.of(),
                dev.openallay.model.ModelUsage.empty(),
                null,
                new GuideFailure("request_interrupted", "interrupted"),
                terminal.minusSeconds(1),
                terminal,
                terminal);
    }

    private static ContextCheckpoint checkpoint() {
        return new ContextCheckpoint(
                UUID.fromString("b68c674f-b7fd-4c37-bf1d-284139216889"),
                0, 1, "a".repeat(64), "test-model", CLOCK.instant(),
                ContextCheckpoint.Status.SUCCEEDED,
                "{\"goals\":[],\"preferences\":[],\"completedTopics\":[],"
                        + "\"currentTasks\":[],\"decisions\":[],"
                        + "\"unresolvedQuestions\":[],\"evidenceReferences\":[]}",
                null, null, 512);
    }

    private static UUID success(ToolResult<UUID> result) {
        return ((ToolResult.Success<UUID>) assertInstanceOf(ToolResult.Success.class, result)).value();
    }

    private static GuideModelMode successMode(ToolResult<GuideModelMode> result) {
        return ((ToolResult.Success<GuideModelMode>)
                        assertInstanceOf(ToolResult.Success.class, result))
                .value();
    }

    private static void assertFailure(ToolResult<?> result, String code) {
        assertEquals(code,
                ((ToolResult.Failure<?>) assertInstanceOf(ToolResult.Failure.class, result)).code());
    }

    private static final class QueuedDispatcher implements dev.openallay.client.ClientEventDispatcher {
        private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> tasks = new java.util.concurrent.ConcurrentLinkedQueue<>();
        private final java.util.concurrent.Semaphore available = new java.util.concurrent.Semaphore(0);

        @Override public void execute(Runnable event) { tasks.add(event); available.release(); }

        private void runAll() {
            while (available.tryAcquire()) tasks.remove().run();
        }

        private void runUntil(java.util.function.BooleanSupplier settled) throws Exception {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while (!settled.getAsBoolean()) {
                long remaining = deadline - System.nanoTime();
                assertTrue(remaining > 0 && available.tryAcquire(remaining, java.util.concurrent.TimeUnit.NANOSECONDS),
                        "actual final owner-thread cleanup must arrive");
                tasks.remove().run();
                runAll();
            }
        }
    }

    private static final class QueuedLocal implements GuideLocalEndpoint {
        private final QueuedDispatcher dispatcher;
        private final Map<UUID, Consumer<AgentEvent>> pending = new java.util.LinkedHashMap<>();
        private final Map<UUID, CompletableFuture<AgentResult>> completions = new java.util.LinkedHashMap<>();
        private final Set<String> contextSessions = new java.util.HashSet<>();

        private QueuedLocal(QueuedDispatcher dispatcher) { this.dispatcher = dispatcher; }
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override public Optional<GuideContextSpec> contextSpec(String profileId) {
            return Optional.of(GuideServiceHistoryTest.contextSpec());
        }
        @Override public boolean hasContext(UUID actor, String sessionId) {
            return contextSessions.contains(sessionId);
        }
        @Override public void hydrateContext(UUID actor, String sessionId,
                List<ModelMessage> messages, List<ContextCheckpoint> checkpoints) {}
        @Override public CompletableFuture<AgentResult> ask(UUID actor, String sessionId,
                UUID requestId, String question, ToolInvocationContext context,
                Consumer<AgentEvent> events) {
            pending.put(requestId, events);
            contextSessions.add(sessionId);
            queue(requestId, new AgentEvent.StateChanged(AgentState.MODEL_WAIT));
            CompletableFuture<AgentResult> completion = new CompletableFuture<>();
            completions.put(requestId, completion);
            return completion;
        }
        @Override public boolean cancel(UUID actor, String sessionId) { return false; }
        @Override public void clearSession(UUID actor, String sessionId) { contextSessions.remove(sessionId); }
        @Override public void clearActor(UUID actor) { contextSessions.clear(); }

        private void queue(UUID requestId, AgentEvent event) {
            Consumer<AgentEvent> captured = pending.get(requestId);
            dispatcher.execute(() -> captured.accept(event));
        }

        /** Cleanup has finished; an already-produced archive callback may still arrive late. */
        private void releaseCancelled(UUID requestId) {
            dispatcher.execute(() -> completions.get(requestId).complete(new AgentResult(
                    AgentState.CANCELLED, null, "agent_cancelled", "Agent request was cancelled", null)));
        }
    }

    private static final class FakeHistory implements GuideHistoryAccess {
        private final boolean autoAcknowledge;
        private final CompletableFuture<Optional<GuideHistoryMetadata>> metadata = new CompletableFuture<>();
        private final List<GuideHistoryCommit> commits = new ArrayList<>();
        private final List<CompletableFuture<Void>> commitCompletions = new ArrayList<>();
        private final List<GuideHistoryPageRequest> pageRequests = new ArrayList<>();
        private final List<GuideHistoryContextRequest> contextRequests = new ArrayList<>();
        private final Map<String, GuideHistoryContextSeed> contextSeeds = new java.util.LinkedHashMap<>();
        private final List<GuideHistoryDeleteScope> deletes = new ArrayList<>();
        private final List<CompletableFuture<Void>> deleteCompletions = new ArrayList<>();
        private GuideHistoryPage recoveredPage;
        private GuideHistoryException pageFailure;
        private int resetCalls;
        private int flushCalls;

        private FakeHistory() { this(true); }

        private FakeHistory(boolean autoAcknowledge) { this.autoAcknowledge = autoAcknowledge; }

        @Override
        public CompletableFuture<Optional<GuideHistoryMetadata>> metadata(GuideHistoryScope scope) {
            assertEquals(SCOPE, scope);
            return metadata;
        }

        @Override
        public CompletableFuture<GuideHistoryPage> page(GuideHistoryPageRequest request) {
            assertEquals(SCOPE, request.scope());
            pageRequests.add(request);
            if (pageFailure != null) return CompletableFuture.failedFuture(pageFailure);
            return CompletableFuture.completedFuture(recoveredPage == null
                    ? new GuideHistoryPage(request.sessionId(), List.of(), null, null, false, false)
                    : recoveredPage);
        }

        @Override
        public CompletableFuture<GuideHistoryContextSeed> context(GuideHistoryContextRequest request) {
            assertEquals(SCOPE, request.scope());
            contextRequests.add(request);
            return CompletableFuture.completedFuture(contextSeeds.getOrDefault(request.sessionId(),
                    new GuideHistoryContextSeed(request.sessionId(), List.of(), List.of(), 0)));
        }

        @Override
        public CompletableFuture<Void> commit(GuideHistoryCommit commit) {
            assertEquals(SCOPE, commit.scope());
            commits.add(commit);
            CompletableFuture<Void> completion = new CompletableFuture<>();
            commitCompletions.add(completion);
            if (autoAcknowledge) completion.complete(null);
            return completion;
        }

        @Override
        public CompletableFuture<Void> delete(GuideHistoryDeleteScope scope) {
            deletes.add(scope);
            CompletableFuture<Void> completion = new CompletableFuture<>();
            deleteCompletions.add(completion);
            return completion;
        }

        @Override
        public CompletableFuture<Void> resetDatabase() {
            resetCalls++;
            CompletableFuture<Void> completion = new CompletableFuture<>();
            deleteCompletions.add(completion);
            return completion;
        }

        @Override
        public CompletableFuture<Void> flush() {
            flushCalls++;
            if (!deleteCompletions.isEmpty() && !deleteCompletions.getLast().isDone()) {
                return deleteCompletions.getLast().handle((ignored, failure) -> null);
            }
            return CompletableFuture.allOf(commitCompletions.toArray(CompletableFuture[]::new))
                    .handle((ignored, failure) -> null);
        }

        @Override
        public GuideHistoryActivity activity() {
            int pending = (int) commitCompletions.stream().filter(value -> !value.isDone()).count();
            boolean deleting = deleteCompletions.stream().anyMatch(value -> !value.isDone());
            return new GuideHistoryActivity(pending, deleting);
        }

        private void completeAllCommits() {
            for (int index = 0; index < commitCompletions.size(); index++) {
                commitCompletions.get(index).complete(null);
            }
        }

        private void completeDeletion() {
            deleteCompletions.getLast().complete(null);
        }

        private void failDeletion() {
            deleteCompletions.getLast().completeExceptionally(new GuideHistoryException(
                    "history_delete_failed", "injected"));
        }
    }

    private static final class FakeLocal implements GuideLocalEndpoint {
        private final boolean alwaysHasContext;
        private final java.util.Map<UUID, Consumer<AgentEvent>> pending = new java.util.LinkedHashMap<>();
        private final Map<UUID, CompletableFuture<AgentResult>> completions = new java.util.LinkedHashMap<>();
        private final List<ModelMessage> hydratedMessages = new ArrayList<>();
        private final List<ContextCheckpoint> hydratedCheckpoints = new ArrayList<>();
        private final List<UUID> clearedActors = new ArrayList<>();
        private final Set<String> contextSessions = new java.util.HashSet<>();

        private FakeLocal() { this(false); }
        private FakeLocal(boolean alwaysHasContext) { this.alwaysHasContext = alwaysHasContext; }

        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override public Optional<GuideContextSpec> contextSpec(String profileId) {
            return Optional.of(GuideServiceHistoryTest.contextSpec());
        }
        @Override public boolean hasContext(UUID actor, String sessionId) {
            return alwaysHasContext || contextSessions.contains(sessionId);
        }
        @Override
        public CompletableFuture<AgentResult> ask(
                UUID actor, String sessionId, UUID requestId, String question,
                ToolInvocationContext context, Consumer<AgentEvent> events) {
            pending.put(requestId, events);
            contextSessions.add(sessionId);
            events.accept(new AgentEvent.StateChanged(AgentState.MODEL_WAIT));
            CompletableFuture<AgentResult> completion = new CompletableFuture<>();
            completions.put(requestId, completion);
            return completion;
        }
        @Override public boolean cancel(UUID actor, String sessionId) { return true; }
        @Override public void clearSession(UUID actor, String sessionId) {
            contextSessions.remove(sessionId);
        }
        @Override public void clearActor(UUID actor) {
            clearedActors.add(actor);
            contextSessions.clear();
        }

        @Override
        public void hydrateContext(
                UUID actor,
                String sessionId,
                List<ModelMessage> messages,
                List<ContextCheckpoint> checkpoints) {
            if (!messages.isEmpty()) contextSessions.add(sessionId);
            hydratedMessages.addAll(messages);
            hydratedCheckpoints.addAll(checkpoints);
        }

        /** The cancelled endpoint has actually finished its work and resource cleanup. */
        private void releaseCancelled(UUID request) {
            completions.get(request).complete(new AgentResult(
                    AgentState.CANCELLED, null, "agent_cancelled", "Agent request was cancelled", null));
        }

        private void updateContext(
                UUID request, List<ModelMessage> active, List<ModelMessage> original) {
            pending.get(request).accept(new AgentEvent.ContextUpdated(active, original));
        }

        private void complete(UUID request, String text) {
            pending.get(request).accept(new AgentEvent.FinalText(text));
            completions.get(request).complete(new AgentResult(AgentState.COMPLETED, text, null, null, null));
        }

        private void compact(UUID request) {
            pending.get(request).accept(new AgentEvent.ContextCompacted(checkpoint()));
        }

        private void tool(UUID request) {
            Consumer<AgentEvent> events = pending.get(request);
            events.accept(new AgentEvent.ToolStarted("call-1", "openallay:unknown"));
            JsonObject normalized = new JsonObject();
            normalized.addProperty("status", "success");
            JsonObject value = new JsonObject();
            value.addProperty("visible", "projection");
            normalized.add("value", value);
            events.accept(new AgentEvent.ToolCompleted(
                    "call-1", "openallay:unknown", false, normalized));
        }
    }

    private static final class FakeRemote implements GuideRemoteEndpoint {
        private final boolean serverModel;
        private List<ModelMessage> history = List.of();

        private FakeRemote(boolean serverModel) {
            this.serverModel = serverModel;
        }

        @Override public boolean serverModelAvailable() { return serverModel; }
        @Override public Optional<GuideContextSpec> contextSpec() {
            return Optional.of(GuideServiceHistoryTest.contextSpec());
        }
        @Override public boolean serverToolsAvailable() { return false; }
        @Override public boolean ask(
                UUID requestId, String sessionId, String question, Consumer<AgentEvent> events) {
            return false;
        }
        @Override
        public boolean askWithContext(
                UUID requestId,
                String sessionId,
                String question,
                List<ModelMessage> history,
                Consumer<AgentEvent> events) {
            this.history = List.copyOf(history);
            return true;
        }
        @Override public boolean cancel(UUID requestId) { return false; }
        @Override public void disconnect() {}
    }
}
