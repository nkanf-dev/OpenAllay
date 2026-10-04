package dev.openallay.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.GameGuideAgent;
import dev.openallay.agent.session.AgentSessionKey;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.bridge.protocol.ClientToolCallPayload;
import dev.openallay.bridge.protocol.ClientToolCancelPayload;
import dev.openallay.bridge.protocol.ServerAgentEventCodec;
import dev.openallay.bridge.protocol.ServerAgentEventPayload;
import dev.openallay.bridge.protocol.ServerAgentHistoryMessage;
import dev.openallay.bridge.protocol.ServerAgentRequestPayload;
import dev.openallay.bridge.server.PlayerClientToolRouter;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Correlated cancellation keeps successor leases and accepted pre-engine context intact. */
final class ServerCancellationCorrelationTest {
    @Test
    void duplicateCancelForOldOwnerCannotCancelNewAskInTheSameSession() throws Exception {
        Gson gson = new Gson();
        UUID actor = UUID.randomUUID();
        UUID oldId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        AgentSessionKey key = new AgentSessionKey(actor, "main");
        AgentSessionStore sessions = new AgentSessionStore();
        NonCooperativeModel model = new NonCooperativeModel();
        CompletableFuture<Void> oldCleanupEntered = new CompletableFuture<>();
        CompletableFuture<Void> releaseOldCleanup = new CompletableFuture<>();
        CompletableFuture<Void> oldOwnerClosed = new CompletableFuture<>();
        CompletableFuture<Void> newOwnerClosed = new CompletableFuture<>();
        CompletableFuture<Void> oldReleased = new CompletableFuture<>();
        CompletableFuture<Void> newReleased = new CompletableFuture<>();
        List<ServerAgentEventPayload> received = new java.util.concurrent.CopyOnWriteArrayList<>();
        Map<String, CancellationSignal> captureSignals = new ConcurrentHashMap<>();
        ServerAgentService service = new ServerAgentService(
                (sender, payload) -> {
                    AgentToolExecutor tools = new EmptyTools() {
                        @Override
                        public void closeRequestScope(String correlationId) {
                            if (payload.requestId().equals(oldId)) {
                                oldCleanupEntered.complete(null);
                                releaseOldCleanup.join();
                            }
                        }
                    };
                    return new ToolResult.Success<>(new ServerAgentService.RequestRuntime(
                            new GameGuideAgent(model, tools, sessions, gson), tools,
                            () -> (payload.requestId().equals(oldId)
                                    ? oldOwnerClosed : newOwnerClosed).complete(null)));
                },
                sessions,
                (sender, capabilities, correlationId, cancellation) -> {
                    captureSignals.put(correlationId, cancellation);
                    return CompletableFuture.completedFuture(
                            ToolInvocationContext.developmentConsole(correlationId));
                },
                (sender, event) -> {
                    assertEquals(actor, sender);
                    received.add(event);
                    if (event.eventType().equals("request_released")) {
                        (event.requestId().equals(oldId) ? oldReleased : newReleased).complete(null);
                    }
                },
                gson,
                "system",
                cancellation -> CompletableFuture.completedFuture(null));
        try {
            assertInstanceOf(ToolResult.Success.class, service.ask(actor,
                    new ServerAgentRequestPayload(oldId, "main", "first question", false)));
            assertEquals(oldId, sessions.status(key).requestId());
            assertTrue(service.cancel(actor, oldId));
            oldCleanupEntered.get(5, TimeUnit.SECONDS);
            assertFalse(sessions.status(key).active());
            assertEquals(1, service.activeRequests(), "old owner stays correlated until cleanup finishes");
            assertFalse(model.raw.get("first question").isDone(), "raw provider ignores cancellation");
            assertTrue(model.signals.get("first question").isCancelled());

            ToolResult.Failure<?> busy = assertInstanceOf(ToolResult.Failure.class, service.ask(actor,
                    new ServerAgentRequestPayload(newId, "main", "retry question", false)));
            assertEquals("agent_busy", busy.code(), "a cancelled owner remains admitted until actual cleanup");
            assertFalse(model.raw.containsKey("retry question"));
            assertFalse(captureSignals.containsKey(actor + "/" + newId));
            assertFalse(service.hasRequest(actor, newId), "busy rejection reserves no successor nonce");
            assertEquals(0, received.stream().filter(event -> event.requestId().equals(newId)).count(),
                    "direct busy rejection emits no fabricated numeric or release events");
            assertFalse(oldReleased.isDone(), "runtime cleanup must finish before RequestReleased");
            releaseOldCleanup.complete(null);
            oldReleased.get(5, TimeUnit.SECONDS);
            assertTrue(oldOwnerClosed.isDone());
            assertEquals(0, service.activeRequests());
            assertEquals("request_released", received.getLast().eventType());

            assertInstanceOf(ToolResult.Success.class, service.ask(actor,
                    new ServerAgentRequestPayload(newId, "main", "retry question", false)));
            assertEquals(newId, sessions.status(key).requestId());
            assertEquals(1, service.activeRequests());
            CancellationSignal retrySignal = model.signals.get("retry question");
            assertFalse(retrySignal.isCancelled());
            assertFalse(captureSignals.get(actor + "/" + newId).isCancelled());

            assertFalse(service.cancel(actor, oldId), "duplicate old cancellation is not accepted");
            assertFalse(retrySignal.isCancelled(), "old owner must not revoke the successor lease");
            assertFalse(captureSignals.get(actor + "/" + newId).isCancelled());
            assertTrue(sessions.status(key).active());
            assertEquals(newId, sessions.status(key).requestId());
            assertFalse(model.raw.get("retry question").isDone());

            assertTrue(oldReleased.isDone(), "the successor is admitted only after the old release fence");
            assertEquals(1, service.activeRequests());
            assertEquals(newId, sessions.status(key).requestId());
            assertFalse(retrySignal.isCancelled());
            long oldEvents = received.stream().filter(event -> event.requestId().equals(oldId)).count();
            model.raw.get("first question").complete(turn("late old answer"));
            assertEquals(oldEvents, received.stream().filter(event -> event.requestId().equals(oldId)).count(),
                    "released old correlation must not reopen when the ignored provider finally answers");
            assertEquals(newId, sessions.status(key).requestId());
            assertFalse(retrySignal.isCancelled());
            model.raw.get("retry question").complete(turn("retry answer"));
            newReleased.get(5, TimeUnit.SECONDS);
            assertTrue(newOwnerClosed.isDone());
            assertEquals(0, service.activeRequests());
            assertFalse(sessions.status(key).active());
            for (UUID id : Set.of(oldId, newId)) {
                List<ServerAgentEventPayload> owned = received.stream()
                        .filter(event -> event.requestId().equals(id)).toList();
                assertEquals("request_released", owned.getLast().eventType());
                assertEquals(1, owned.stream().filter(event -> event.eventType().equals("model_usage_started")).count());
                assertEquals(1, owned.stream().filter(event -> event.eventType().equals("model_usage_observed")).count());
                assertEquals(1, owned.stream().filter(event -> event.eventType().equals("request_released")).count());
            }
        } finally {
            releaseOldCleanup.complete(null);
            model.raw.values().forEach(raw -> raw.complete(turn("cleanup answer")));
            service.disconnect(actor);
        }
    }

    @Test
    void cancelDuringPendingCaptureFinalizesAcceptedContextBeforeFailureExactlyOnce() {
        Gson gson = new Gson();
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AgentSessionStore sessions = new AgentSessionStore();
        CompletableFuture<ToolInvocationContext> pendingCapture = new CompletableFuture<>();
        AtomicReference<CancellationSignal> captureSignal = new AtomicReference<>();
        AtomicInteger captures = new AtomicInteger();
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger requestCloses = new AtomicInteger();
        List<ServerAgentEventPayload> events = new ArrayList<>();
        ModelClient unusedModel = (request, progress, cancellation) -> {
            modelCalls.incrementAndGet();
            return CompletableFuture.failedFuture(new AssertionError("No model dispatch expected"));
        };
        PlayerClientToolRouter router = new PlayerClientToolRouter(
                new ToolRegistry(), gson, new PlayerClientToolRouter.Transport() {
                    @Override
                    public boolean call(UUID sender, ClientToolCallPayload payload) {
                        throw new AssertionError("No client Tool dispatch expected");
                    }

                    @Override
                    public void cancel(UUID sender, ClientToolCancelPayload payload) {}
                });
        ServerAgentService service = new ServerAgentService(
                (sender, payload) -> {
                    ToolResult<AgentToolExecutor> opened = router.open(sender, payload.requestId(),
                            payload.sessionId(), payload.clientToolIds(),
                            new SkillRepository(new SkillParser(), Set.of()).snapshot(Set.of()));
                    assertInstanceOf(ToolResult.Success.class, opened);
                    AgentToolExecutor tools = ((ToolResult.Success<AgentToolExecutor>) opened).value();
                    return new ToolResult.Success<>(new ServerAgentService.RequestRuntime(
                            new GameGuideAgent(unusedModel, tools, sessions, gson), tools, () -> {
                                requestCloses.incrementAndGet();
                                router.close(sender, payload.requestId());
                            }));
                },
                sessions,
                (sender, capabilities, correlationId, cancellation) -> {
                    captures.incrementAndGet();
                    captureSignal.set(cancellation);
                    return pendingCapture;
                },
                (sender, event) -> events.add(event),
                gson,
                "system",
                cancellation -> CompletableFuture.completedFuture(null));
        JsonObject previousInput = new JsonObject();
        previousInput.addProperty("source", "return priorFailure();");
        List<ModelMessage> acceptedHistory = List.of(ModelMessage.userText("Earlier question."),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        "prior-call", "openallay__run_javascript", previousInput))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult("prior-call",
                        new JsonPrimitive("status: failure\ncode: javascript_error\nmessage: priorFailure is undefined"),
                        true))),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("Earlier answer."))));
        String question = "Cancel this accepted question before capture finishes.";
        ServerAgentRequestPayload payload = new ServerAgentRequestPayload(requestId, "pending", question,
                false, acceptedHistory.stream().map(ServerAgentHistoryMessage::from).toList(), List.of());
        try {
            assertInstanceOf(ToolResult.Success.class, service.ask(actor, payload));
            assertEquals(1, captures.get());
            assertEquals(0, modelCalls.get());
            assertEquals(1, service.activeRequests());
            assertEquals(1, router.activeRequests());
            assertFalse(sessions.status(new AgentSessionKey(actor, "pending")).active());
            assertFalse(pendingCapture.isDone());

            assertTrue(service.cancel(actor, requestId));

            assertTrue(captureSignal.get().isCancelled());
            assertEquals(0, modelCalls.get());
            assertEquals(0, service.activeRequests());
            assertEquals(0, router.activeRequests());
            assertEquals(1, requestCloses.get());
            assertEquals(List.of("context_finalized", "failed", "request_released"),
                    events.stream().map(ServerAgentEventPayload::eventType).toList());
            ServerAgentEventCodec codec = new ServerAgentEventCodec(gson);
            AgentEvent.ContextFinalized finalized = assertInstanceOf(AgentEvent.ContextFinalized.class,
                    codec.decode(events.getFirst(), requestId));
            AgentEvent.Failed failed = assertInstanceOf(AgentEvent.Failed.class,
                    codec.decode(events.get(1), requestId));
            List<ModelMessage> expectedOriginal = List.of(ModelMessage.userText(question),
                    new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text(
                            "[OpenAllay request ended: agent_cancelled] Agent request was cancelled"))));
            List<ModelMessage> expectedProjected = new ArrayList<>(acceptedHistory);
            expectedProjected.addAll(expectedOriginal);
            assertEquals(expectedProjected, finalized.messages());
            assertEquals(expectedOriginal, finalized.requestMessages());
            assertEquals("agent_cancelled", failed.code());
            assertFalse(events.getFirst().terminal());
            assertTrue(events.get(1).terminal());
            assertFalse(events.getLast().terminal());

            assertFalse(service.cancel(actor, requestId));
            assertEquals(3, events.size(), "duplicate cancellation must not finalize, fail or release twice");
            assertEquals(1, requestCloses.get());
            pendingCapture.complete(ToolInvocationContext.developmentConsole(actor + "/" + requestId));
            assertEquals(0, modelCalls.get(), "late capture must not dispatch the cancelled request");
            assertEquals(3, events.size());
            assertEquals(1, captures.get());
            assertEquals(1, requestCloses.get());
            assertEquals(0, service.activeRequests());
            assertEquals(0, router.activeRequests());
            assertFalse(sessions.status(new AgentSessionKey(actor, "pending")).active());
        } finally {
            pendingCapture.complete(ToolInvocationContext.developmentConsole("cleanup"));
            service.disconnect(actor);
            router.disconnect(actor);
        }
    }

    private static ModelTurn turn(String text) {
        return new ModelTurn("test", "test", List.of(new ModelContent.Text(text)),
                "end_turn", ModelUsage.empty());
    }

    private static final class NonCooperativeModel implements ModelClient {
        private final Map<String, CompletableFuture<ModelTurn>> raw = new ConcurrentHashMap<>();
        private final Map<String, CancellationSignal> signals = new ConcurrentHashMap<>();

        @Override
        public CompletableFuture<ModelTurn> complete(
                ModelRequest request, Consumer<ModelEvent> events, CancellationSignal cancellation) {
            String question = ((ModelContent.Text) request.messages().getLast().content().getFirst()).text();
            CompletableFuture<ModelTurn> pending = new CompletableFuture<>();
            signals.put(question, cancellation);
            raw.put(question, pending);
            return pending;
        }
    }

    private static class EmptyTools implements AgentToolExecutor {
        @Override public List<ModelToolDefinition> definitions() { return List.of(); }
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }

        @Override
        public CompletableFuture<AgentToolResult> execute(
                String name, JsonObject arguments, ToolInvocationContext context,
                CancellationSignal cancellation) {
            return CompletableFuture.failedFuture(new AssertionError("No Tool expected"));
        }
    }
}
