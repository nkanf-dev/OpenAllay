package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentState;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.tool.ToolResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class GuideServiceTest {
    private static final UUID ACTOR = UUID.fromString("30ab22ed-23fb-46f2-82ca-d4a656698eec");

    @Test
    void localRequestsPublishSnapshotsAndIsolateSessionConcurrency() {
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, new FakeRemote(false));
        List<GuideSnapshot> observed = new ArrayList<>();
        GuideSubscription subscription = service.subscribe(observed::add);

        UUID first = success(service.ask("first").join());
        ToolResult.Failure<UUID> busy = failure(service.ask("busy").join());
        assertEquals("agent_busy", busy.code());

        service.selectSession("other").join();
        UUID second = success(service.ask("second").join());
        assertEquals(2, local.pending.size());
        assertNotEquals(first, second);

        local.complete(first, "first answer");
        local.complete(second, "second answer");
        assertEquals(GuideRequestStatus.COMPLETED, request(service, "main", first).status());
        assertEquals(GuideRequestStatus.COMPLETED, request(service, "other", second).status());
        assertFalse(observed.isEmpty());

        int before = observed.size();
        subscription.close();
        service.selectSession("main").join();
        assertEquals(before, observed.size());
    }

    @Test
    void cancelSuppressesLateEventsAndRetryUsesNewIdentity() {
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, new FakeRemote(false));
        UUID request = success(service.ask("retry me").join());

        assertTrue(success(service.cancel().join()));
        assertEquals(GuideRequestStatus.CANCELLED, request(service, "main", request).status());
        local.complete(request, "late");
        assertEquals("", request(service, "main", request).assistantText());

        UUID retried = success(service.retry(request).join());
        assertNotEquals(request, retried);
        assertEquals("retry me", request(service, "main", retried).userMessage());
    }

    @Test
    void queuedStopPublishesCancellationBeforeABlockingEndpointAndAcceptsIntentWhenItReturnsFalse()
            throws Exception {
        QueuedDispatcher dispatcher = new QueuedDispatcher();
        QueuedLocal local = new QueuedLocal(dispatcher);
        GuideService service = queuedService(local, dispatcher);
        List<GuideSnapshot> observed = new ArrayList<>();
        service.subscribe(observed::add);
        CompletableFuture<ToolResult<UUID>> asking = service.ask("stop during delayed handoff");
        dispatcher.runAll();
        UUID request = success(asking.join());
        assertEquals(GuideRequestStatus.MODEL_WAIT, request(service, "main", request).status());
        CompletableFuture<ToolResult<Boolean>> stopping = service.cancel();
        CompletableFuture<Void> processing = CompletableFuture.runAsync(dispatcher::runAll);
        try {
            assertTrue(local.cancelEntered.await(2, java.util.concurrent.TimeUnit.SECONDS));

            assertFalse(stopping.isDone());
            assertEquals(GuideRequestStatus.CANCELLED, request(service, "main", request).status());
            assertEquals(GuideRequestStatus.CANCELLED, observed.getLast().sessions().getFirst()
                    .requests().getFirst().status());
            assertEquals(List.of("main"), local.cancelled);
        } finally {
            local.cancelRelease.countDown();
        }
        processing.get(2, java.util.concurrent.TimeUnit.SECONDS);

        assertTrue(success(stopping.join()));
        assertEquals(GuideRequestStatus.CANCELLED, request(service, "main", request).status());
        GuideRequestSnapshot stopped = request(service, "main", request);
        List<dev.openallay.model.ModelMessage> original = List.of(
                dev.openallay.model.ModelMessage.userText("safe finalized request after stop"));
        local.queue(request, new AgentEvent.ContextFinalized(original, original));
        local.queue(request, new AgentEvent.FinalText("late visible answer"));
        local.queue(request, new AgentEvent.ContextUpdated(
                List.of(dev.openallay.model.ModelMessage.userText("ordinary late context")),
                List.of(dev.openallay.model.ModelMessage.userText("ordinary late original"))));
        dispatcher.runAll();

        assertEquals(stopped, request(service, "main", request));
        CompletableFuture<ToolResult<dev.openallay.guide.export.GuideSessionExportSnapshot>> exporting =
                service.captureSelectedSessionForExport();
        dispatcher.runAll();
        assertEquals(original, success(exporting.join()).requests().getFirst().originalContext());
    }

    @Test
    void queuedPrecompletedEngineHandoffArchivesExactAnswerWithoutReopeningCancelledUi() {
        QueuedDispatcher dispatcher = new QueuedDispatcher();
        QueuedLocal local = new QueuedLocal(dispatcher);
        local.cancelRelease.countDown();
        GuideService service = queuedService(local, dispatcher);
        CompletableFuture<ToolResult<UUID>> asking = service.ask("engine completed before display handoff");
        dispatcher.runAll();
        UUID request = success(asking.join());
        List<dev.openallay.model.ModelMessage> original = List.of(
                dev.openallay.model.ModelMessage.userText("engine completed before display handoff"),
                new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.ASSISTANT, List.of(
                        new dev.openallay.model.ModelContent.Text("exact precompleted engine answer"))));
        List<dev.openallay.model.ModelMessage> active = new ArrayList<>();
        active.add(dev.openallay.model.ModelMessage.userText("earlier session model context"));
        active.addAll(original);
        CompletableFuture<ToolResult<Boolean>> stopping = service.cancel();
        local.queue(request, new AgentEvent.ContextFinalized(active, original));
        local.queue(request, new AgentEvent.StateChanged(AgentState.COMPLETED));
        local.queue(request, new AgentEvent.FinalText("exact precompleted engine answer"));

        dispatcher.runAll();

        assertTrue(success(stopping.join()));
        GuideRequestSnapshot cancelled = request(service, "main", request);
        assertEquals(GuideRequestStatus.CANCELLED, cancelled.status());
        assertEquals("agent_cancelled", cancelled.failure().code());
        assertEquals("", cancelled.assistantText());
        assertTrue(cancelled.timeline().isEmpty());
        assertEquals(List.of("engine completed before display handoff"),
                service.snapshot().sessions().getFirst().messages().stream().map(GuideMessage::text).toList());
        CompletableFuture<ToolResult<dev.openallay.guide.export.GuideSessionExportSnapshot>> exporting =
                service.captureSelectedSessionForExport();
        dispatcher.runAll();
        var archived = success(exporting.join()).requests().getFirst();
        assertEquals(GuideRequestStatus.CANCELLED, archived.status());
        assertEquals(original, archived.originalContext());
        assertEquals("exact precompleted engine answer",
                assertInstanceOf(dev.openallay.model.ModelContent.Text.class,
                        archived.originalContext().getLast().content().getFirst()).text());
        assertTrue(archived.timeline().isEmpty());
        assertEquals(cancelled, request(service, "main", request));
    }

    @Test
    void servicePreservesInterleavedAssistantAndToolTimeline() {
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, new FakeRemote(false));
        UUID requestId = success(service.ask("interleave").join());

        local.completeInterleaved(requestId);

        GuideRequestSnapshot request = request(service, "main", requestId);
        assertEquals(
                List.of(
                        GuideTimelineEntry.Assistant.class,
                        GuideTimelineEntry.Tool.class,
                        GuideTimelineEntry.Assistant.class,
                        GuideTimelineEntry.Tool.class,
                        GuideTimelineEntry.Assistant.class),
                request.timeline().stream().map(Object::getClass).toList());
        assertEquals("final answer", request.assistantText());
        assertEquals("final answer",
                ((GuideTimelineEntry.Assistant) request.timeline().getLast())
                        .semantic().fallbackText());
        assertEquals("final answer", service.snapshot().sessions().getFirst().messages().getLast().text());
    }

    @Test
    void serverModeWorksWithoutClientModelAndNeverFallsBack() {
        FakeRemote remote = new FakeRemote(true);
        GuideService service = service(null, remote);

        ToolResult.Failure<UUID> unconfigured = failure(service.ask("client").join());
        assertEquals("model_not_configured", unconfigured.code());
        assertInstanceOf(ToolResult.Success.class, service.setModelMode(GuideModelMode.SERVER).join());
        UUID request = success(service.ask("server").join());
        assertEquals(GuideTopology.SERVER, request(service, "main", request).topology());

        remote.fail(request, "server_model_failure", "no fallback");
        assertEquals(GuideRequestStatus.FAILED, request(service, "main", request).status());
        assertEquals(GuideModelMode.SERVER, service.snapshot().modelMode());
    }

    @Test
    void serverRequestsWithoutPersistenceReuseActualContextInsteadOfVisibleHistory() {
        FakeRemote remote = new FakeRemote(true);
        GuideService service = service(null, remote);
        success(service.setModelMode(GuideModelMode.SERVER).join());
        UUID first = success(service.ask("first visible question").join());
        List<dev.openallay.model.ModelMessage> actual = List.of(
                dev.openallay.model.ModelMessage.userText("actual retained model context"));
        remote.pending.get(first).accept(new AgentEvent.ContextUpdated(
                actual, List.of(dev.openallay.model.ModelMessage.userText("first request original"))));
        remote.pending.get(first).accept(new AgentEvent.FinalText("visible answer"));
        assertEquals("agent_busy", failure(service.ask("not before real cleanup").join()).code());
        remote.pending.get(first).accept(new AgentEvent.RequestReleased());

        UUID next = success(service.ask("next visible question").join());
        service.selectSession("other").join();
        success(service.setModelMode(GuideModelMode.SERVER).join());
        UUID other = success(service.ask("other session question").join());

        assertEquals(List.of(), remote.contextByRequest.get(first));
        assertEquals(actual, remote.contextByRequest.get(next));
        assertEquals(List.of(), remote.contextByRequest.get(other));
        assertEquals(List.of("first visible question", "visible answer", "next visible question"),
                service.snapshot().sessions().stream()
                        .filter(session -> session.sessionId().equals("main"))
                        .findFirst().orElseThrow().messages().stream().map(GuideMessage::text).toList());
    }

    @Test
    void enhancedLocalTopologyAndCapabilityLossAffectsOnlyFutureRequests() {
        FakeRemote remote = new FakeRemote(true);
        GuideService localService = service(new FakeLocal(), remote);
        UUID local = success(localService.ask("enhanced").join());
        assertEquals(
                GuideTopology.CLIENT_WITH_SERVER_TOOLS,
                request(localService, "main", local).topology());

        GuideService serverService = service(null, remote);
        success(serverService.setModelMode(GuideModelMode.SERVER).join());
        UUID active = success(serverService.ask("server").join());
        remote.available = false;
        serverService.refreshCapabilities().join();

        assertEquals(
                GuideRequestStatus.MODEL_WAIT,
                request(serverService, "main", active).status());
        assertEquals(GuideModelMode.CLIENT, serverService.snapshot().modelMode());
        assertEquals(
                GuideModelSelection.client("default"),
                serverService.snapshot().modelSelection());
    }

    @Test
    void disconnectClearsConnectionScopedStateAndResetsMode() {
        FakeRemote remote = new FakeRemote(true);
        GuideService service = service(new FakeLocal(), remote);
        service.setModelMode(GuideModelMode.SERVER).join();
        UUID active = success(service.ask("active").join());
        service.selectSession("other").join();

        CompletableFuture<Void> disconnected = service.disconnect();
        assertFalse(disconnected.isDone(), "cancel acknowledgement is not the remote endpoint release");
        remote.releaseCancelled(active);
        disconnected.join();

        assertEquals(GuideModelMode.CLIENT, service.snapshot().modelMode());
        assertEquals("main", service.snapshot().selectedSession());
        assertEquals(List.of("main"), service.snapshot().sessions().stream()
                .map(GuideSessionSnapshot::sessionId).toList());
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
        assertEquals(1, remote.cancelled.size());
    }

    @Test
    void capturesTheSelectedSessionForPlayerInitiatedExport() {
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, new FakeRemote(false));
        UUID request = success(service.ask("export this").join());
        List<dev.openallay.model.ModelMessage> original = List.of(
                dev.openallay.model.ModelMessage.userText("actual export request"));
        local.pending.get(request).accept(new AgentEvent.ContextUpdated(
                List.of(dev.openallay.model.ModelMessage.userText("compacted session context")),
                original));
        local.complete(request, "exported answer");

        dev.openallay.guide.export.GuideSessionExportSnapshot exported = success(
                service.captureSelectedSessionForExport().join());

        assertEquals("main", exported.sessionId());
        assertEquals(Instant.EPOCH, exported.capturedAt());
        assertEquals(List.of("export this"), exported.requests().stream()
                .map(dev.openallay.guide.export.GuideSessionExportSnapshot.Request::userMessage)
                .toList());
        assertEquals("exported answer",
                ((dev.openallay.guide.export.GuideSessionExportSnapshot.Entry.Assistant)
                        exported.requests().getFirst().timeline().getFirst()).text());
        assertEquals(original, exported.requests().getFirst().originalContext());
    }

    @Test
    void confirmedSessionCloseCancelsItsActiveRequestAndFencesLateOutput() {
        FakeLocal local = new FakeLocal();
        GuideService service = service(local, new FakeRemote(false));
        UUID request = success(service.ask("active session").join());

        assertTrue(success(service.closeSession("main").join()));
        assertTrue(local.cancelled.contains("main"));
        assertEquals(List.of("main"), service.snapshot().sessions().stream()
                .map(GuideSessionSnapshot::sessionId).toList());
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());

        local.complete(request, "late answer");
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
    }

    private static GuideService queuedService(QueuedLocal local, QueuedDispatcher dispatcher) {
        return new GuideService(ACTOR, local, new FakeRemote(false),
                (capabilities, correlation) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlation)),
                dispatcher, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), dev.openallay.json.EngineJson.create());
    }

    private static GuideService service(FakeLocal local, FakeRemote remote) {
        return new GuideService(
                ACTOR,
                local,
                remote,
                (capabilities, correlation) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlation)),
                Runnable::run,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                dev.openallay.json.EngineJson.create());
    }

    private static GuideRequestSnapshot request(
            GuideService service, String session, UUID request) {
        return service.snapshot().sessions().stream()
                .filter(value -> value.sessionId().equals(session))
                .flatMap(value -> value.requests().stream())
                .filter(value -> value.requestId().equals(request))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static <T> T success(ToolResult<T> result) {
        return ((ToolResult.Success<T>) assertInstanceOf(ToolResult.Success.class, result)).value();
    }

    @SuppressWarnings("unchecked")
    private static <T> ToolResult.Failure<T> failure(ToolResult<T> result) {
        return (ToolResult.Failure<T>) assertInstanceOf(ToolResult.Failure.class, result);
    }

    private static final class QueuedDispatcher implements dev.openallay.client.ClientEventDispatcher {
        private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> tasks =
                new java.util.concurrent.ConcurrentLinkedQueue<>();

        @Override public void execute(Runnable event) { tasks.add(event); }

        private void runAll() {
            Runnable task;
            while ((task = tasks.poll()) != null) task.run();
        }
    }

    private static final class QueuedLocal implements GuideLocalEndpoint {
        private final QueuedDispatcher dispatcher;
        private final Map<UUID, Consumer<AgentEvent>> pending = new java.util.HashMap<>();
        private final List<String> cancelled = new ArrayList<>();
        private final java.util.concurrent.CountDownLatch cancelEntered =
                new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch cancelRelease =
                new java.util.concurrent.CountDownLatch(1);

        private QueuedLocal(QueuedDispatcher dispatcher) { this.dispatcher = dispatcher; }
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override public CompletableFuture<AgentResult> ask(UUID actor, String sessionId,
                UUID requestId, String question, ToolInvocationContext context,
                Consumer<AgentEvent> events) {
            pending.put(requestId, events);
            queue(requestId, new AgentEvent.StateChanged(AgentState.MODEL_WAIT));
            return new CompletableFuture<>();
        }
        @Override public boolean cancel(UUID actor, String sessionId) {
            cancelled.add(sessionId);
            cancelEntered.countDown();
            try {
                if (!cancelRelease.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new AssertionError("blocking cancellation fixture was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            return false;
        }
        @Override public void clearSession(UUID actor, String sessionId) {}
        @Override public void clearActor(UUID actor) {}

        private void queue(UUID requestId, AgentEvent event) {
            Consumer<AgentEvent> captured = pending.get(requestId);
            dispatcher.execute(() -> captured.accept(event));
        }
    }

    private static final class FakeLocal implements GuideLocalEndpoint {
        private final Map<UUID, Consumer<AgentEvent>> pending = new java.util.HashMap<>();
        private final Map<UUID, CompletableFuture<AgentResult>> completions = new java.util.HashMap<>();
        private final Set<String> cancelled = new java.util.HashSet<>();

        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }

        @Override
        public CompletableFuture<AgentResult> ask(
                UUID actor,
                String sessionId,
                UUID requestId,
                String question,
                ToolInvocationContext context,
                Consumer<AgentEvent> events) {
            pending.put(requestId, events);
            events.accept(new AgentEvent.StateChanged(AgentState.MODEL_WAIT));
            CompletableFuture<AgentResult> completed = new CompletableFuture<>();
            completions.put(requestId, completed);
            return completed;
        }

        @Override public boolean cancel(UUID actor, String sessionId) {
            cancelled.add(sessionId);
            return true;
        }
        @Override public void clearSession(UUID actor, String sessionId) {}
        @Override public void clearActor(UUID actor) {}

        void complete(UUID request, String text) {
            Consumer<AgentEvent> events = pending.get(request);
            events.accept(new AgentEvent.StateChanged(AgentState.COMPLETED));
            events.accept(new AgentEvent.FinalText(text));
            completions.get(request).complete(new AgentResult(AgentState.COMPLETED, text, null, null, null));
        }

        void completeInterleaved(UUID request) {
            Consumer<AgentEvent> events = pending.get(request);
            events.accept(new AgentEvent.ModelProgress(new dev.openallay.model.ModelEvent.TextDelta(
                    "I will check.")));
            events.accept(new AgentEvent.ToolStarted("call-1", "openallay:get_recipe"));
            events.accept(new AgentEvent.ToolCompleted(
                    "call-1", "openallay:get_recipe", false, new com.google.gson.JsonObject()));
            events.accept(new AgentEvent.ModelProgress(new dev.openallay.model.ModelEvent.TextDelta(
                    "Now inventory.")));
            events.accept(new AgentEvent.ToolStarted("call-2", "openallay:inspect_inventory"));
            events.accept(new AgentEvent.ToolCompleted(
                    "call-2", "openallay:inspect_inventory", false, new com.google.gson.JsonObject()));
            events.accept(new AgentEvent.FinalText("final answer"));
            completions.get(request).complete(new AgentResult(AgentState.COMPLETED, "final answer", null, null, null));
        }
    }

    private static final class FakeRemote implements GuideRemoteEndpoint {
        private boolean available;
        private final Map<UUID, Consumer<AgentEvent>> pending = new java.util.HashMap<>();
        private final Map<UUID, List<dev.openallay.model.ModelMessage>> contextByRequest =
                new java.util.HashMap<>();
        private final List<UUID> cancelled = new ArrayList<>();

        private FakeRemote(boolean available) { this.available = available; }
        @Override public boolean serverModelAvailable() { return available; }
        @Override public boolean serverToolsAvailable() { return available; }
        @Override
        public boolean ask(
                UUID requestId, String sessionId, String question, Consumer<AgentEvent> events) {
            if (!available) return false;
            pending.put(requestId, events);
            events.accept(new AgentEvent.StateChanged(AgentState.MODEL_WAIT));
            return true;
        }
        @Override
        public boolean askWithContext(
                UUID requestId,
                String sessionId,
                String question,
                List<dev.openallay.model.ModelMessage> history,
                Consumer<AgentEvent> events) {
            contextByRequest.put(requestId, List.copyOf(history));
            return ask(requestId, sessionId, question, events);
        }
        @Override public boolean cancel(UUID requestId) {
            cancelled.add(requestId);
            return pending.containsKey(requestId);
        }
        @Override public void disconnect() { /* Already-produced callbacks drain until explicit release. */ }

        void releaseCancelled(UUID request) {
            Consumer<AgentEvent> events = pending.remove(request);
            events.accept(new AgentEvent.RequestReleased());
        }

        void fail(UUID request, String code, String message) {
            pending.get(request).accept(new AgentEvent.Failed(code, message));
        }
    }
}
