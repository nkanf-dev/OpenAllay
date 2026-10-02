package dev.openallay.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.google.gson.Gson;
import dev.openallay.agent.GameGuideAgent;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.bridge.protocol.ServerAgentEventPayload;
import dev.openallay.bridge.protocol.ServerAgentHistoryMessage;
import dev.openallay.bridge.protocol.ServerAgentRequestPayload;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class ServerAgentServiceTest {
    @Test
    void scheduledCancellationKeepsOwnerUntilPartialUsageReceiptIsDelivered() throws Exception {
        Gson gson = new Gson();
        AgentSessionStore sessions = new AgentSessionStore();
        AgentToolExecutor tools = new EmptyTools();
        CompletableFuture<Runnable> queuedReceipt = new CompletableFuture<>();
        CompletableFuture<Void> terminal = new CompletableFuture<>();
        CompletableFuture<Void> released = new CompletableFuture<>();
        CompletableFuture<ModelTurn> raw = new CompletableFuture<>();
        ModelUsage partial = ModelUsage.openAi(17, true, 0, false, 0, false);
        ModelClient provider = (request, sink, cancellation) -> {
            sink.accept(new ModelEvent.UsageUpdate(partial));
            return raw;
        };
        var scheduler = new dev.openallay.model.scheduling.ModelRequestScheduler(provider);
        // Model an asynchronous handoff of a real scheduler/observer receipt, not a fabricated event.
        ModelClient delayedReceipt = new ModelClient() {
            @Override public boolean observesUsage() { return true; }
            @Override public CompletableFuture<ModelTurn> complete(ModelRequest request,
                    Consumer<ModelEvent> sink, CancellationSignal cancellation) {
                return scheduler.complete(request, event -> {
                    if (event instanceof ModelEvent.UsageObserved) {
                        queuedReceipt.complete(() -> sink.accept(event));
                    } else sink.accept(event);
                }, cancellation);
            }
        };
        List<ServerAgentEventPayload> received = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.concurrent.atomic.AtomicInteger closes = new java.util.concurrent.atomic.AtomicInteger();
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        ServerAgentService service = new ServerAgentService(
                (sender, payload) -> new ToolResult.Success<>(new ServerAgentService.RequestRuntime(
                        new GameGuideAgent(delayedReceipt, tools, sessions, gson), tools, closes::incrementAndGet)),
                sessions, (sender, capabilities, id, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(id)),
                (sender, event) -> {
                    assertEquals(actor, sender);
                    assertEquals(requestId, event.requestId());
                    received.add(event);
                    if (event.terminal()) terminal.complete(null);
                    if (event.eventType().equals("request_released")) released.complete(null);
                }, gson, "system", cancellation -> CompletableFuture.completedFuture(null));
        try {
            service.ask(actor, request(requestId, "receipt-gap"));
            org.junit.jupiter.api.Assertions.assertTrue(service.cancel(actor, requestId));
            terminal.get(5, java.util.concurrent.TimeUnit.SECONDS);
            Runnable deliverReceipt = queuedReceipt.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1, received.stream().filter(ServerAgentEventPayload::terminal).count());
            assertEquals(0, received.stream().filter(event -> event.eventType().equals("model_usage_observed")).count());
            org.junit.jupiter.api.Assertions.assertFalse(released.isDone());
            assertEquals(0, closes.get());
            assertEquals(1, service.activeRequests());
            deliverReceipt.run();
            released.get(5, java.util.concurrent.TimeUnit.SECONDS);
            var codec = new dev.openallay.bridge.protocol.ServerAgentEventCodec(gson);
            var receipt = received.stream().filter(event -> event.eventType().equals("model_usage_observed"))
                    .map(event -> (dev.openallay.agent.AgentEvent.ModelUsageObserved) codec.decode(event, requestId))
                    .toList();
            assertEquals(1, receipt.size());
            assertEquals(partial, receipt.getFirst().usage());
            assertEquals("request_released", received.getLast().eventType());
            assertEquals(1, closes.get());
            assertEquals(0, service.activeRequests());
            int sealed = received.size();
            raw.complete(turn("late provider answer"));
            assertEquals(sealed, received.size());
        } finally {
            queuedReceipt.thenAccept(Runnable::run);
            service.disconnect(actor);
        }
    }

    @Test
    void sameSessionIsBusyButDifferentSessionsForOnePlayerRunConcurrently() throws Exception {
        PendingModel model = new PendingModel();
        AgentSessionStore sessions = new AgentSessionStore();
        AgentToolExecutor tools = new EmptyTools();
        List<ServerAgentEventPayload> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.concurrent.CountDownLatch released = new java.util.concurrent.CountDownLatch(2);
        ServerAgentService service = new ServerAgentService(
                new GameGuideAgent(model, tools, sessions, new Gson()),
                tools,
                sessions,
                (actor, capabilities, id, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(id)),
                (actor, event) -> {
                    events.add(event);
                    if (event.eventType().equals("request_released")) released.countDown();
                },
                new Gson(),
                "system");
        UUID actor = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID busyId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        assertInstanceOf(ToolResult.Success.class, service.ask(actor, request(firstId, "main")));
        ToolResult.Failure<?> busy = assertInstanceOf(ToolResult.Failure.class,
                service.ask(actor, request(busyId, "main")));
        assertEquals("agent_busy", busy.code(), "admission rejects the busy session before capture or dispatch");
        assertEquals(1, model.pending.size(), "a rejected request must not reach the model");
        assertEquals(0, events.stream().filter(event -> event.requestId().equals(busyId)).count(),
                "the rejected request has no admitted owner or provider-attempt receipts");
        org.junit.jupiter.api.Assertions.assertFalse(service.hasRequest(actor, busyId),
                "a direct admission failure must not reserve the rejected request nonce");
        assertInstanceOf(ToolResult.Success.class, service.ask(actor, request(otherId, "other")));
        assertEquals(2, model.pending.size());
        assertEquals(2, service.activeRequests());

        model.pending.get("main").complete(turn("main answer"));
        model.pending.get("other").complete(turn("other answer"));
        org.junit.jupiter.api.Assertions.assertTrue(released.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "Each accepted request must release after actual cleanup and numeric receipts");
        assertEquals(0, service.activeRequests());
        assertEquals(Set.of(firstId, otherId), events.stream()
                .filter(event -> event.eventType().equals("request_released"))
                .map(ServerAgentEventPayload::requestId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, events.stream().filter(event -> event.eventType().equals("request_released")).count());
        assertEquals(2, events.stream().filter(event -> event.eventType().equals("model_usage_observed")).count());
        assertEquals(0, events.stream().filter(event -> event.requestId().equals(busyId)
                && event.eventType().equals("model_usage_started")).count());
        assertEquals(2, events.stream().filter(ServerAgentEventPayload::terminal).count());
        assertEquals(0, events.stream().filter(event -> event.eventJson().contains("agent_busy")).count(),
                "synchronous admission failure is returned once, not emitted for a nonexistent owner");
        for (UUID accepted : Set.of(firstId, otherId)) {
            List<ServerAgentEventPayload> owned = events.stream()
                    .filter(event -> event.requestId().equals(accepted)).toList();
            assertEquals("request_released", owned.getLast().eventType());
            assertEquals(1, owned.stream().filter(event -> event.eventType().equals("model_usage_started")).count());
            assertEquals(1, owned.stream().filter(event -> event.eventType().equals("model_usage_observed")).count());
        }
    }

    @Test
    void atomicallyRestoresVisibleHistoryBeforeTheCurrentQuestion() {
        PendingModel model = new PendingModel();
        AgentSessionStore sessions = new AgentSessionStore();
        AgentToolExecutor tools = new EmptyTools();
        ServerAgentService service = new ServerAgentService(
                new GameGuideAgent(model, tools, sessions, new Gson()),
                tools,
                sessions,
                (actor, capabilities, id, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(id)),
                (actor, event) -> {},
                new Gson(),
                "system");
        UUID requestId = UUID.randomUUID();
        ServerAgentRequestPayload request = new ServerAgentRequestPayload(
                requestId,
                "restored",
                "current question",
                true,
                List.of(
                        new ServerAgentHistoryMessage(
                                ServerAgentHistoryMessage.Role.USER, "old question"),
                        new ServerAgentHistoryMessage(
                                ServerAgentHistoryMessage.Role.ASSISTANT, "old answer")));

        service.ask(UUID.randomUUID(), request);

        assertEquals(
                List.of("USER:old question", "ASSISTANT:old answer", "USER:current question"),
                model.requests.get("restored").messages().stream()
                        .map(message -> message.role() + ":" + message.content().stream()
                                .map(content -> ((ModelContent.Text) content).text())
                                .reduce("", String::concat))
                        .toList());
    }

    @Test
    void disconnectCancelsEverySessionAndSuppressesLateEvents() {
        PendingModel model = new PendingModel();
        AgentSessionStore sessions = new AgentSessionStore();
        AgentToolExecutor tools = new EmptyTools();
        List<ServerAgentEventPayload> events = new ArrayList<>();
        ServerAgentService service = new ServerAgentService(
                new GameGuideAgent(model, tools, sessions, new Gson()), tools, sessions,
                (actor, capabilities, id, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(id)),
                (actor, event) -> events.add(event), new Gson(), "system");
        UUID actor = UUID.randomUUID();
        service.ask(actor, request(UUID.randomUUID(), "a"));
        service.ask(actor, request(UUID.randomUUID(), "b"));

        assertEquals(2, service.disconnect(actor));
        assertEquals(0, service.activeRequests());
        int before = events.size();
        model.pending.values().forEach(future -> future.complete(turn("late")));
        assertEquals(before, events.size());
    }

    @Test
    void waitsForEndpointReadinessBeforeCapturingServerContext() {
        PendingModel model = new PendingModel();
        AgentSessionStore sessions = new AgentSessionStore();
        AgentToolExecutor tools = new EmptyTools();
        CompletableFuture<Void> ready = new CompletableFuture<>();
        java.util.concurrent.atomic.AtomicInteger captures = new java.util.concurrent.atomic.AtomicInteger();
        ServerAgentService service = new ServerAgentService(
                new GameGuideAgent(model, tools, sessions, new Gson()),
                tools,
                sessions,
                (actor, capabilities, id, cancellation) -> {
                    captures.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            ToolInvocationContext.developmentConsole(id));
                },
                (actor, event) -> {},
                new Gson(),
                "system",
                cancellation -> ready);

        service.ask(UUID.randomUUID(), request(UUID.randomUUID(), "fresh"));
        assertEquals(0, captures.get());
        ready.complete(null);
        assertEquals(1, captures.get());
    }

    @Test
    void cancelsWhileWaitingForEndpointWithoutCapturingContextLater() {
        PendingModel model = new PendingModel();
        AgentSessionStore sessions = new AgentSessionStore();
        AgentToolExecutor tools = new EmptyTools();
        CompletableFuture<Void> ready = new CompletableFuture<>();
        java.util.concurrent.atomic.AtomicInteger captures = new java.util.concurrent.atomic.AtomicInteger();
        List<ServerAgentEventPayload> events = new ArrayList<>();
        ServerAgentService service = new ServerAgentService(
                new GameGuideAgent(model, tools, sessions, new Gson()), tools, sessions,
                (actor, capabilities, id, cancellation) -> {
                    captures.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            ToolInvocationContext.developmentConsole(id));
                },
                (actor, event) -> events.add(event),
                new Gson(), "system", cancellation -> ready);
        UUID actor = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        service.ask(actor, request(request, "waiting"));

        org.junit.jupiter.api.Assertions.assertTrue(service.cancel(actor, request));
        ready.complete(null);
        assertEquals(0, captures.get());
        assertEquals(0, service.activeRequests());
        assertEquals(1, events.stream().filter(ServerAgentEventPayload::terminal).count());
    }

    private static ServerAgentRequestPayload request(UUID id, String session) {
        return new ServerAgentRequestPayload(id, session, "question", true);
    }

    private static ModelTurn turn(String text) {
        return new ModelTurn("test", "test", List.of(new ModelContent.Text(text)),
                "end_turn", ModelUsage.empty());
    }

    private static final class PendingModel implements ModelClient {
        private final Map<String, CompletableFuture<ModelTurn>> pending = new java.util.HashMap<>();
        private final Map<String, ModelRequest> requests = new java.util.HashMap<>();
        @Override
        public CompletableFuture<ModelTurn> complete(
                ModelRequest request, Consumer<ModelEvent> events, CancellationSignal cancellation) {
            String session = request.sessionKey().substring(request.sessionKey().lastIndexOf(':') + 1);
            requests.put(session, request);
            CompletableFuture<ModelTurn> future = new CompletableFuture<>();
            pending.put(session, future);
            cancellation.onCancel(() -> future.completeExceptionally(new dev.openallay.model.ModelClientException(
                    new dev.openallay.model.ModelFailure("agent_cancelled", "cancelled", null))));
            return future;
        }
    }

    private static final class EmptyTools implements AgentToolExecutor {
        @Override public List<ModelToolDefinition> definitions() { return List.of(); }
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override
        public CompletableFuture<AgentToolResult> execute(
                String name, com.google.gson.JsonObject arguments,
                ToolInvocationContext context, CancellationSignal cancellation) {
            return CompletableFuture.failedFuture(new AssertionError("No tool expected"));
        }
    }
}
