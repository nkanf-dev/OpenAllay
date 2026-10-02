package dev.openallay.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.GameGuideAgent;
import dev.openallay.agent.session.AgentSessionKey;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.bridge.protocol.ServerAgentEventCodec;
import dev.openallay.bridge.protocol.ServerAgentEventPayload;
import dev.openallay.bridge.protocol.ServerAgentHistoryMessage;
import dev.openallay.bridge.protocol.ServerAgentRequestPayload;
import dev.openallay.bridge.protocol.ServerAgentSteerPayload;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** All boundaries use explicit futures; no provider, clock polling, or game runtime is needed. */
final class ServerSteerBridgeTest {
    private final Gson gson = new Gson();
    private final ServerAgentEventCodec codec = new ServerAgentEventCodec(gson);

    @Test
    void preCaptureInboxOverwritesAndRemovesOnlyMatchingOwnedEntries() throws Exception {
        AgentSessionStore sessions = new AgentSessionStore();
        PendingModel model = new PendingModel();
        AgentToolExecutor tools = new EmptyTools();
        CompletableFuture<Void> ready = new CompletableFuture<>();
        List<ServerAgentEventPayload> events = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> released = new CompletableFuture<>();
        ServerAgentService service = new ServerAgentService(
                new GameGuideAgent(model, tools, sessions, gson), tools, sessions,
                (actor, capabilities, id, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(id)),
                (actor, event) -> {
                    events.add(event);
                    if (event.eventType().equals("request_released")) released.complete(null);
                }, gson, "system", cancellation -> ready);
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID removedId = UUID.randomUUID();
        service.ask(actor, new ServerAgentRequestPayload(requestId, "main", "question", false));

        assertTrue(service.steer(actor, put(requestId, messageId, "original")));
        assertTrue(service.steer(actor, put(requestId, messageId, "edited")));
        assertTrue(service.steer(actor, put(requestId, removedId, "remove me")));
        assertFalse(service.steer(UUID.randomUUID(), put(requestId, messageId, "wrong actor")));
        assertFalse(service.steer(actor, put(UUID.randomUUID(), messageId, "wrong request")));
        assertTrue(service.steer(actor, remove(requestId, removedId)));
        assertFalse(service.steer(actor, remove(requestId, removedId)));
        assertEquals(0, model.requests.size());

        ready.complete(null);

        assertEquals(List.of(ModelMessage.userText("question"), ModelMessage.userText("edited")),
                model.requests.getFirst().messages());
        List<AgentEvent.SteerApplied> applied = events.stream()
                .filter(event -> event.eventType().equals("steer_applied"))
                .map(event -> assertInstanceOf(AgentEvent.SteerApplied.class, codec.decode(event, requestId)))
                .toList();
        assertEquals(List.of(new AgentEvent.SteerApplied(messageId, ModelMessage.userText("edited"))), applied);
        assertFalse(service.steer(actor, put(requestId, messageId, "too late")));
        assertInstanceOf(AgentEvent.SteerRejected.class, codec.decode(events.getLast(), requestId));
        assertFalse(service.steer(actor, remove(requestId, messageId)));
        model.pending.getFirst().complete(turn("answer"));
        released.get(5, TimeUnit.SECONDS);
        assertEquals("request_released", events.getLast().eventType());
        assertEquals(0, service.activeRequests());
    }

    @Test
    void releaseFollowsTerminalRuntimeCloseAndSessionLeaseRelease() throws Exception {
        AgentSessionStore sessions = new AgentSessionStore();
        PendingModel model = new PendingModel();
        AgentToolExecutor tools = new EmptyTools();
        AtomicBoolean closed = new AtomicBoolean();
        List<String> observed = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> released = new CompletableFuture<>();
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AgentSessionKey key = new AgentSessionKey(actor, "main");
        ServerAgentService service = new ServerAgentService(
                (sender, request) -> new ToolResult.Success<>(new ServerAgentService.RequestRuntime(
                        new GameGuideAgent(model, tools, sessions, gson), tools, () -> closed.set(true))),
                sessions,
                (sender, capabilities, id, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(id)),
                (sender, event) -> {
                    observed.add(event.eventType());
                    if (event.terminal()) {
                        assertFalse(closed.get(), "terminal is not runtime release");
                        assertFalse(sessions.status(key).active());
                    }
                    if (event.eventType().equals("request_released")) {
                        assertTrue(closed.get());
                        assertFalse(sessions.status(key).active());
                        assertFalse(event.terminal());
                        released.complete(null);
                    }
                }, gson, "system", cancellation -> CompletableFuture.completedFuture(null));
        service.ask(actor, new ServerAgentRequestPayload(requestId, "main", "question", false));
        model.pending.getFirst().complete(turn("answer"));
        released.get(5, TimeUnit.SECONDS);
        assertEquals(List.of("final_text", "request_released"),
                observed.subList(observed.size() - 2, observed.size()));
        assertEquals(0, service.activeRequests());
        assertFalse(service.steer(actor, put(requestId, UUID.randomUUID(), "released")));
        assertEquals("steer_rejected", observed.getLast());
    }

    @Test
    void cancelledDetachedEngineDoesNotReleaseWhileCleanupIsStillRunning() throws Exception {
        AgentSessionStore sessions = new AgentSessionStore();
        PendingModel model = new PendingModel();
        CompletableFuture<Void> cleanupEntered = new CompletableFuture<>();
        CompletableFuture<Void> cleanupGate = new CompletableFuture<>();
        CompletableFuture<Void> released = new CompletableFuture<>();
        AtomicBoolean closed = new AtomicBoolean();
        List<ServerAgentEventPayload> events = new CopyOnWriteArrayList<>();
        AgentToolExecutor tools = new EmptyTools() {
            @Override public void closeRequestScope(String correlationId) {
                cleanupEntered.complete(null);
                cleanupGate.join();
            }
        };
        ServerAgentService service = new ServerAgentService(
                (sender, request) -> new ToolResult.Success<>(new ServerAgentService.RequestRuntime(
                        new GameGuideAgent(model, tools, sessions, gson), tools, () -> closed.set(true))),
                sessions,
                (sender, capabilities, id, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(id)),
                (sender, event) -> {
                    events.add(event);
                    if (event.eventType().equals("request_released")) released.complete(null);
                }, gson, "system", cancellation -> CompletableFuture.completedFuture(null));
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        try {
            service.ask(actor, new ServerAgentRequestPayload(requestId, "main", "question", false));
            assertTrue(service.cancel(actor, requestId));
            cleanupEntered.get(5, TimeUnit.SECONDS);
            assertEquals(1, service.activeRequests());
            assertFalse(closed.get());
            assertFalse(released.isDone());
            assertFalse(model.pending.getFirst().isDone(), "raw provider is noncooperative");
            assertFalse(service.cancel(actor, requestId));
            assertFalse(service.steer(actor, put(requestId, UUID.randomUUID(), "cancelled")));
            cleanupGate.complete(null);
            released.get(5, TimeUnit.SECONDS);
            assertTrue(closed.get());
            assertEquals(0, service.activeRequests());
            assertEquals(List.of("failed", "request_released"), events.stream()
                    .filter(event -> event.terminal() || event.eventType().equals("request_released"))
                    .map(ServerAgentEventPayload::eventType).toList());
        } finally {
            cleanupGate.complete(null);
            model.pending.forEach(future -> future.complete(turn("cleanup")));
            service.disconnect(actor);
        }
    }

    @Test
    void synchronousCaptureCancellationStillPublishesTerminalBeforeRelease() {
        AgentSessionStore sessions = new AgentSessionStore();
        PendingModel model = new PendingModel();
        AgentToolExecutor tools = new EmptyTools();
        CompletableFuture<ToolInvocationContext> capture = new CompletableFuture<>();
        List<String> events = new ArrayList<>();
        ServerAgentService service = new ServerAgentService(
                new GameGuideAgent(model, tools, sessions, gson), tools, sessions,
                (sender, capabilities, id, cancellation) -> {
                    cancellation.onCancel(() -> capture.complete(null));
                    return capture;
                }, (sender, event) -> events.add(event.eventType()), gson, "system");
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        service.ask(actor, new ServerAgentRequestPayload(requestId, "main", "question", false));
        assertTrue(service.cancel(actor, requestId));
        assertEquals(List.of("context_finalized", "failed", "request_released"), events);
        assertEquals(0, model.requests.size());
    }

    @Test
    void stopDoesNotWaitForSynchronousEnginePreparationBeforeRevokingItsLease() throws Exception {
        AgentSessionStore sessions = new AgentSessionStore();
        PendingModel model = new PendingModel();
        CompletableFuture<Void> ready = new CompletableFuture<>();
        CompletableFuture<Void> preparing = new CompletableFuture<>();
        CompletableFuture<Void> preparationGate = new CompletableFuture<>();
        CompletableFuture<Void> released = new CompletableFuture<>();
        AgentToolExecutor tools = new EmptyTools() {
            @Override public void prepareSystem(String prompt,
                    dev.openallay.skill.RetainedSkillContext retainedSkills) {
                preparing.complete(null);
                preparationGate.join();
            }
        };
        ServerAgentService service = new ServerAgentService(
                new GameGuideAgent(model, tools, sessions, gson), tools, sessions,
                (sender, capabilities, id, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(id)),
                (sender, event) -> {
                    if (event.eventType().equals("request_released")) released.complete(null);
                }, gson, "system", cancellation -> ready);
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            service.ask(actor, new ServerAgentRequestPayload(requestId, "main", "question", false));
            CompletableFuture<Void> dispatch = CompletableFuture.runAsync(() -> ready.complete(null), worker);
            preparing.get(5, TimeUnit.SECONDS);
            CompletableFuture<Boolean> stop = CompletableFuture.supplyAsync(() -> service.cancel(actor, requestId));
            assertTrue(stop.get(5, TimeUnit.SECONDS), "Stop must not wait behind preparation");
            assertFalse(sessions.status(new AgentSessionKey(actor, "main")).active());
            assertFalse(released.isDone(), "the actual engine still owns its unfinished work");
            preparationGate.complete(null);
            dispatch.get(5, TimeUnit.SECONDS);
            released.get(5, TimeUnit.SECONDS);
            assertTrue(model.requests.isEmpty(), "cancelled preparation must not dispatch the provider");
        } finally {
            preparationGate.complete(null);
            service.disconnect(actor);
            worker.shutdownNow();
        }
    }

    @Test
    void removedImageImportCannotApplyAndItsActualWorkOutlivesTerminalUntilRelease() throws Exception {
        AgentSessionStore sessions = new AgentSessionStore();
        PendingModel model = new PendingModel();
        AgentToolExecutor tools = new EmptyTools();
        CompletableFuture<Void> importEntered = new CompletableFuture<>();
        CompletableFuture<Void> importGate = new CompletableFuture<>();
        CompletableFuture<Void> released = new CompletableFuture<>();
        AtomicBoolean closed = new AtomicBoolean();
        List<ServerAgentEventPayload> events = new CopyOnWriteArrayList<>();
        ServerAgentService service = new ServerAgentService(
                (sender, request) -> new ToolResult.Success<>(new ServerAgentService.RequestRuntime(
                        new GameGuideAgent(model, tools, sessions, gson), tools, "system", payload -> {
                            importEntered.complete(null);
                            importGate.join();
                            return payload.message().toModelMessage();
                        }, () -> closed.set(true))), sessions,
                (sender, capabilities, id, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(id)),
                (sender, event) -> {
                    events.add(event);
                    if (event.eventType().equals("request_released")) released.complete(null);
                }, gson, "system", cancellation -> CompletableFuture.completedFuture(null));
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        byte[] bytes = {1, 2, 3};
        var reference = new dev.openallay.model.image.ImageReference(
                sha256(bytes), "image/png", 1, 1, bytes.length);
        ModelMessage message = new ModelMessage(dev.openallay.model.ModelRole.USER,
                List.of(new ModelContent.Text("look"), new ModelContent.Image(reference)));
        ServerAgentSteerPayload payload = new ServerAgentSteerPayload(requestId, messageId,
                ServerAgentSteerPayload.Operation.PUT, ServerAgentHistoryMessage.from(message),
                List.of(dev.openallay.bridge.protocol.ServerAgentImageAttachment.from(reference, bytes)));
        try {
            service.ask(actor, new ServerAgentRequestPayload(requestId, "main", "question", false));
            assertTrue(service.steer(actor, payload));
            importEntered.get(5, TimeUnit.SECONDS);
            assertTrue(service.steer(actor, remove(requestId, messageId)));
            model.pending.getFirst().complete(turn("answer"));
            assertTrue(events.stream().anyMatch(ServerAgentEventPayload::terminal));
            assertFalse(released.isDone(), "image preparation is still actual request work");
            assertFalse(closed.get());
            assertEquals(1, service.activeRequests());
            importGate.complete(null);
            released.get(5, TimeUnit.SECONDS);
            assertTrue(closed.get());
            assertEquals(0, service.activeRequests());
            assertFalse(events.stream().anyMatch(event -> event.eventType().equals("steer_applied")));
            assertFalse(events.stream().anyMatch(event -> event.eventType().equals("steer_rejected")),
                    "removed jobs must not mutate a later UI instruction");
        } finally {
            importGate.complete(null);
            service.disconnect(actor);
        }
    }

    @Test
    void terminalWithSteerPendingRetainsLateActualCallUsageUntilItsFrontierSeals() throws Exception {
        AgentSessionStore sessions = new AgentSessionStore();
        AgentToolExecutor tools = new EmptyTools();
        CompletableFuture<ModelTurn> providerTurn = new CompletableFuture<>();
        CompletableFuture<Runnable> queuedReceipt = new CompletableFuture<>();
        CompletableFuture<Void> terminal = new CompletableFuture<>();
        var scheduler = new dev.openallay.model.scheduling.ModelRequestScheduler(
                (request, events, cancellation) -> providerTurn);
        // Delay only the handoff of a real observer receipt. A client that already emits
        // numeric lifecycle events must declare ownership instead of being observed twice.
        ModelClient model = new ModelClient() {
            @Override public boolean observesUsage() { return true; }
            @Override public CompletableFuture<ModelTurn> complete(ModelRequest request,
                    Consumer<ModelEvent> events, CancellationSignal cancellation) {
                return scheduler.complete(request, event -> {
                    if (event instanceof ModelEvent.UsageObserved) {
                        queuedReceipt.complete(() -> events.accept(event));
                    } else events.accept(event);
                }, cancellation);
            }
        };
        CompletableFuture<Void> released = new CompletableFuture<>();
        List<ServerAgentEventPayload> events = new CopyOnWriteArrayList<>();
        AtomicBoolean closed = new AtomicBoolean();
        ServerAgentService service = new ServerAgentService(
                (sender, request) -> new ToolResult.Success<>(new ServerAgentService.RequestRuntime(
                        new GameGuideAgent(model, tools, sessions, gson), tools, () -> closed.set(true))), sessions,
                (sender, capabilities, id, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(id)), (sender, event) -> {
                    events.add(event);
                    if (event.terminal()) terminal.complete(null);
                    if (event.eventType().equals("request_released")) released.complete(null);
                }, gson, "system", cancellation -> CompletableFuture.completedFuture(null));
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        try {
            service.ask(actor, new ServerAgentRequestPayload(requestId, "main", "question", false));
            assertTrue(service.steer(actor, put(requestId, UUID.randomUUID(), "next instruction")));
            ModelUsage usage = new ModelUsage(4, 2, 0);
            providerTurn.complete(new ModelTurn("test", "test", List.of(new ModelContent.Text("answer")),
                    "end_turn", usage));
            terminal.get(5, TimeUnit.SECONDS);
            Runnable deliverReceipt = queuedReceipt.get(5, TimeUnit.SECONDS);
            assertFalse(released.isDone());
            assertFalse(closed.get());
            assertEquals(1, service.activeRequests());
            assertEquals(1, events.stream().filter(event -> event.eventType().equals("model_usage_started")).count());
            assertEquals(0, events.stream().filter(event -> event.eventType().equals("model_usage_observed")).count());
            deliverReceipt.run();
            released.get(5, TimeUnit.SECONDS);
            assertTrue(closed.get());
            assertEquals(0, service.activeRequests());
            int terminalIndex = java.util.stream.IntStream.range(0, events.size())
                    .filter(index -> events.get(index).terminal()).findFirst().orElseThrow();
            assertEquals(List.of("final_text", "model_usage_observed", "request_released"),
                    events.subList(terminalIndex, events.size()).stream().map(ServerAgentEventPayload::eventType).toList());
            var codec = new dev.openallay.bridge.protocol.ServerAgentEventCodec(gson);
            var started = events.stream().filter(event -> event.eventType().equals("model_usage_started"))
                    .map(event -> (AgentEvent.ModelUsageStarted) codec.decode(event, requestId)).findFirst().orElseThrow();
            var observed = events.stream().filter(event -> event.eventType().equals("model_usage_observed"))
                    .map(event -> (AgentEvent.ModelUsageObserved) codec.decode(event, requestId)).toList();
            assertEquals(1, observed.size());
            assertEquals(started.callId(), observed.getFirst().callId());
            assertEquals(usage, observed.getFirst().usage());
        } finally {
            queuedReceipt.thenAccept(Runnable::run);
            service.disconnect(actor);
        }
    }

    @Test
    void frozenImageHookImportsOnlyItsActorAndExtendsTheCurrentRequestResolver() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        byte[] bytes = {1, 2, 3};
        var reference = new dev.openallay.model.image.ImageReference(
                sha256(bytes), "image/png", 1, 1, bytes.length);
        ModelMessage imageMessage = new ModelMessage(dev.openallay.model.ModelRole.USER,
                List.of(new ModelContent.Text("inspect"), new ModelContent.Image(reference)));
        MemoryImages store = new MemoryImages(actor, reference, bytes);
        AgentSessionStore sessions = new AgentSessionStore();
        AgentToolExecutor tools = new EmptyTools();
        CompletableFuture<Void> ready = new CompletableFuture<>();
        CompletableFuture<Void> imported = new CompletableFuture<>();
        CompletableFuture<Void> dispatch = new CompletableFuture<>();
        CompletableFuture<Void> importBarrier = new CompletableFuture<>();
        CompletableFuture<Void> barrierReleased = new CompletableFuture<>();
        CompletableFuture<Void> released = new CompletableFuture<>();
        CompletableFuture<ModelTurn> provider = new CompletableFuture<>();
        ModelClient model = (request, events, cancellation) -> {
            try {
                org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, request.images().read(reference));
                assertEquals(List.of(ModelMessage.userText("question"), imageMessage), request.messages());
                dispatch.complete(null);
            } catch (Exception failure) {
                dispatch.completeExceptionally(failure);
            }
            return provider;
        };
        ServerAgentService service = new ServerAgentService(
                (sender, request) -> new ToolResult.Success<>(new ServerAgentService.RequestRuntime(
                        new GameGuideAgent(model, tools, sessions, gson), tools, "system", payload -> {
                            try {
                                ModelMessage input = ServerGuideRuntime.importSteerImages(actor, requestId,
                                        payload.message().toModelMessage(), payload.imageAttachments(), store,
                                        dev.openallay.model.image.ImageInputCapability.SUPPORTED);
                                imported.complete(null);
                                return input;
                            } catch (java.io.IOException failure) {
                                throw new java.io.UncheckedIOException(failure);
                            }
                        }, () -> {
                            try { store.release(actor, "server-steer:" + request.requestId()); }
                            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                        })), sessions,
                (sender, capabilities, id, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(id)), (sender, event) -> {
                    if (event.eventType().equals("request_released")) {
                        if (event.requestId().equals(requestId)) released.complete(null);
                        else barrierReleased.complete(null);
                    }
                }, gson, "system", cancellation -> {
                    if (store.requestRetained.isDone()) importBarrier.complete(null);
                    return ready;
                }, store, dev.openallay.model.image.ImageInputCapability.SUPPORTED);
        try {
            service.ask(actor, new ServerAgentRequestPayload(requestId, "main", "question", false));
            assertTrue(service.steer(actor, new ServerAgentSteerPayload(requestId, messageId,
                    ServerAgentSteerPayload.Operation.PUT, ServerAgentHistoryMessage.from(imageMessage),
                    List.of(dev.openallay.bridge.protocol.ServerAgentImageAttachment.from(reference, bytes)))));
            imported.get(5, TimeUnit.SECONDS);
            store.requestRetained.get(5, TimeUnit.SECONDS);
            // A normal typed initial ask provides a bounded, observable barrier on the same
            // S2b worker. It does not dispatch a provider while the original import is pending.
            UUID barrierId = UUID.randomUUID();
            service.ask(actor, new ServerAgentRequestPayload(barrierId, "barrier", imageMessage,
                    false, List.of(), List.of(dev.openallay.bridge.protocol.ServerAgentImageAttachment.from(reference, bytes))));
            importBarrier.get(5, TimeUnit.SECONDS);
            assertTrue(service.cancel(actor, barrierId));
            ready.complete(null);
            dispatch.get(5, TimeUnit.SECONDS);
            provider.complete(turn("answer"));
            released.get(5, TimeUnit.SECONDS);
            barrierReleased.get(5, TimeUnit.SECONDS);
            assertFalse(store.owners.containsKey("server-steer:" + requestId));
            assertTrue(store.owners.keySet().stream().anyMatch(owner -> owner.startsWith("server-session:")),
                    "applied image remains retained by the actual session after request release");
            assertFalse(store.owners.keySet().stream().anyMatch(owner -> owner.startsWith("server-request:")));
        } finally {
            provider.complete(turn("cleanup"));
            ready.complete(null);
            service.disconnectAsync(actor).get(5, TimeUnit.SECONDS);
        }
    }

    private static final class MemoryImages implements dev.openallay.model.image.ImageAttachmentStore {
        private final UUID actor;
        private final dev.openallay.model.image.ImageReference reference;
        private final byte[] bytes;
        private final java.util.Map<String, List<dev.openallay.model.image.ImageReference>> owners =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final CompletableFuture<Void> requestRetained = new CompletableFuture<>();
        private boolean imported;
        private MemoryImages(UUID actor, dev.openallay.model.image.ImageReference reference, byte[] bytes) {
            this.actor = actor;
            this.reference = reference;
            this.bytes = bytes;
        }
        private void check(UUID sender) throws java.io.IOException {
            if (!actor.equals(sender)) throw new java.io.IOException("wrong actor");
        }
        @Override public dev.openallay.model.image.ImageInputLimits limits() {
            return dev.openallay.model.image.ImageInputLimits.defaults();
        }
        @Override public synchronized dev.openallay.model.image.ImageReference importImage(UUID sender, byte[] input)
                throws java.io.IOException {
            check(sender);
            org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, input);
            imported = true;
            return reference;
        }
        @Override public synchronized dev.openallay.model.image.ImageReference importImage(UUID sender, String owner,
                byte[] input) throws java.io.IOException {
            var value = importImage(sender, input);
            owners.put(owner, List.of(value));
            return value;
        }
        @Override public synchronized byte[] read(UUID sender, dev.openallay.model.image.ImageReference image)
                throws java.io.IOException {
            check(sender);
            if (!imported || !reference.equals(image)) throw new java.io.IOException("image unavailable");
            return bytes;
        }
        @Override public synchronized void retain(UUID sender, String owner,
                List<dev.openallay.model.image.ImageReference> references) throws java.io.IOException {
            check(sender);
            for (var image : references) read(sender, image);
            if (references.isEmpty()) owners.remove(owner);
            else owners.put(owner, List.copyOf(references));
            if (owner.startsWith("server-request:")) requestRetained.complete(null);
        }
        @Override public void reconcile(UUID sender, String namespace,
                java.util.Map<String, List<dev.openallay.model.image.ImageReference>> values) throws java.io.IOException {
            check(sender);
        }
        @Override public void release(UUID sender, String owner) throws java.io.IOException {
            check(sender);
            owners.remove(owner);
        }
        @Override public int collect(UUID sender) throws java.io.IOException { check(sender); return 0; }
    }

    private static ServerAgentSteerPayload put(UUID requestId, UUID messageId, String text) {
        return new ServerAgentSteerPayload(requestId, messageId, ServerAgentSteerPayload.Operation.PUT,
                ServerAgentHistoryMessage.from(ModelMessage.userText(text)));
    }

    private static ServerAgentSteerPayload remove(UUID requestId, UUID messageId) {
        return new ServerAgentSteerPayload(requestId, messageId, ServerAgentSteerPayload.Operation.REMOVE, null);
    }

    private static ModelTurn turn(String text) {
        return new ModelTurn("test", "test", List.of(new ModelContent.Text(text)),
                "end_turn", ModelUsage.empty());
    }

    private static final class PendingModel implements ModelClient {
        private final List<ModelRequest> requests = new ArrayList<>();
        private final List<CompletableFuture<ModelTurn>> pending = new ArrayList<>();
        @Override public CompletableFuture<ModelTurn> complete(
                ModelRequest request, Consumer<ModelEvent> progress, CancellationSignal cancellation) {
            requests.add(request);
            CompletableFuture<ModelTurn> future = new CompletableFuture<>();
            pending.add(future);
            return future;
        }
    }

    private static class EmptyTools implements AgentToolExecutor {
        @Override public List<ModelToolDefinition> definitions() { return List.of(); }
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override public CompletableFuture<AgentToolResult> execute(String name, com.google.gson.JsonObject arguments,
                ToolInvocationContext context, CancellationSignal cancellation) {
            return CompletableFuture.failedFuture(new AssertionError("No Tool expected"));
        }
    }
    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
    }


}
