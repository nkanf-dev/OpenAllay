package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentState;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.image.ImageAttachmentStore;
import dev.openallay.model.image.ImageInputCapability;
import dev.openallay.model.image.ImageInputLimits;
import dev.openallay.model.image.ImageReference;
import dev.openallay.tool.ToolResult;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class GuideObservationImageCustodyTest {
    private static final ImageReference IMAGE = new ImageReference("a".repeat(64), "image/png", 1, 1, 1);
    private static final ImageReference EARLIER = new ImageReference("b".repeat(64), "image/png", 1, 1, 1);

    @Test void nestedOriginalImagesRetainBeforeProducerReleaseAndFifoWaitsForActualRelease() throws Exception {
        Fixture f = new Fixture(); UUID first = f.ask("first"); f.followUp("next");
        f.store.owners.put("producer", List.of(IMAGE, EARLIER));
        f.store.block = IMAGE;
        f.endpoint.emit(first, new AgentEvent.ContextUpdated(original(IMAGE), original(IMAGE, EARLIER)));
        f.owner.runAll(); assertTrue(f.store.entered.await(2, TimeUnit.SECONDS));
        f.endpoint.emit(first, new AgentEvent.FinalText("done")); f.endpoint.finish(first, AgentState.COMPLETED);
        f.owner.runAll();
        assertTrue(f.contexts.closed.contains(first.toString()));
        assertTrue(f.contexts.releases.isEmpty()); assertEquals(1, f.endpoint.calls.size());
        f.store.allow.countDown(); assertTrue(f.contexts.releaseStarted.await(2, TimeUnit.SECONDS)); f.owner.runAll();
        assertEquals(List.of(IMAGE, EARLIER), f.store.sessionImages());
        assertEquals(1, f.endpoint.calls.size(), "FIFO must wait for the real producer release future");
        f.contexts.releaseGate.complete(null); f.owner.until(() -> f.endpoint.calls.size() == 2);
        assertFalse(f.store.owners.containsKey("producer"));
        assertEquals(0, f.store.collect(UUID.randomUUID()), "published images survive collection");
        var exported = f.service.captureSelectedSessionForExport(); f.owner.until(exported::isDone);
        try (var snapshot = success(exported.join())) {
            assertEquals(original(IMAGE, EARLIER), snapshot.requests().getFirst().originalContext());
        }
    }

    @Test void failedMemoryRetainReportsFailureKeepsProducersAndDoesNotStrandTheQueue() throws Exception {
        Fixture f = new Fixture(); UUID first = f.ask("first"); f.followUp("still runs");
        f.store.owners.put("producer", List.of(IMAGE)); f.store.block = IMAGE; f.store.fail = true;
        f.endpoint.emit(first, new AgentEvent.ContextUpdated(original(IMAGE), original(IMAGE)));
        f.owner.runAll(); assertTrue(f.store.entered.await(2, TimeUnit.SECONDS));
        f.endpoint.emit(first, new AgentEvent.FinalText("done")); f.endpoint.finish(first, AgentState.COMPLETED);
        f.owner.runAll(); f.store.allow.countDown(); f.owner.until(() -> f.endpoint.calls.size() == 2);
        assertTrue(f.contexts.releases.isEmpty(), "failed handoff must not claim custody or drop producer pins");
        assertTrue(f.store.owners.containsKey("producer"));
        assertEquals("observation_image_handoff_failed", f.service.snapshot().persistence().failure().code());
        assertNull(f.service.snapshot().sessions().getFirst().requests().getFirst().failure(),
                "custody failure must not invent a failed model answer");
    }

    @Test void cancelledLateFinalizationRetainsTheActualOriginalBeforeRelease() throws Exception {
        Fixture f = new Fixture(); UUID first = f.ask("first");
        f.store.owners.put("producer", List.of(IMAGE));
        var cancel = f.service.cancel(); f.owner.runAll(); assertTrue(success(cancel.join()));
        f.store.block = IMAGE;
        f.endpoint.emit(first, new AgentEvent.ContextFinalized(original(IMAGE), original(IMAGE)));
        f.owner.runAll(); assertTrue(f.store.entered.await(2, TimeUnit.SECONDS));
        f.endpoint.finish(first, AgentState.CANCELLED); f.owner.runAll(); assertTrue(f.contexts.releases.isEmpty());
        f.store.allow.countDown(); assertTrue(f.contexts.releaseStarted.await(2, TimeUnit.SECONDS)); f.owner.runAll();
        assertEquals(List.of(IMAGE), f.store.sessionImages());
        f.contexts.releaseGate.complete(null); f.owner.until(() ->
                f.service.snapshot().sessions().getFirst().workingRequestId() == null);
        assertEquals(GuideRequestStatus.CANCELLED, f.service.snapshot().sessions().getFirst().requests().getFirst().status());
    }

    @Test void closingOldSessionCannotReleaseARecreatedSessionImageOwner() throws Exception {
        Fixture f = new Fixture(); UUID first = f.ask("first"); f.store.block = IMAGE;
        f.endpoint.emit(first, new AgentEvent.ContextUpdated(original(IMAGE), original(IMAGE)));
        f.owner.runAll(); assertTrue(f.store.entered.await(2, TimeUnit.SECONDS));
        var closing = f.service.closeSession("main"); f.owner.runAll(); assertTrue(success(closing.join()));
        UUID next = f.ask("replacement");
        f.endpoint.emit(next, new AgentEvent.ContextUpdated(original(EARLIER), original(EARLIER)));
        f.owner.runAll(); f.store.allow.countDown();
        assertTrue(f.contexts.releaseStarted.await(2, TimeUnit.SECONDS)); f.owner.runAll();
        f.contexts.releaseGate.complete(null); f.owner.runAll();
        f.endpoint.emit(next, new AgentEvent.FinalText("new done")); f.endpoint.finish(next, AgentState.COMPLETED);
        f.owner.until(() -> f.service.snapshot().sessions().getFirst().workingRequestId() == null);
        assertTrue(f.store.owners.values().stream().anyMatch(refs -> refs.contains(EARLIER)));
        assertEquals(next, f.service.snapshot().sessions().getFirst().requests().getFirst().requestId());
    }

    @Test void disconnectWaitsForCustodyAndReportsARealFailedBarrier() throws Exception {
        Fixture f = new Fixture(); UUID first = f.ask("first"); f.store.block = IMAGE; f.store.fail = true;
        f.endpoint.emit(first, new AgentEvent.ContextUpdated(original(IMAGE), original(IMAGE)));
        f.owner.runAll(); assertTrue(f.store.entered.await(2, TimeUnit.SECONDS));
        var disconnected = f.service.disconnect(); f.owner.runAll(); assertFalse(disconnected.isDone());
        f.endpoint.finish(first, AgentState.CANCELLED); f.owner.runAll();
        f.store.allow.countDown(); f.owner.until(disconnected::isDone);
        assertThrows(java.util.concurrent.CompletionException.class, disconnected::join);
        assertTrue(f.contexts.releases.isEmpty());
    }

    @Test void disconnectConsumesAlreadyCapturedToolImagesBeforeClearingTheActor() throws Exception {
        Fixture f = new Fixture(); UUID first = f.ask("first");
        f.store.owners.put("producer", List.of(IMAGE)); f.store.block = IMAGE;
        var disconnected = f.service.disconnect();
        // The model worker has recorded the actual tool result, but its first-hop callbacks
        // and eventual endpoint completion have not reached the Guide dispatcher yet.
        f.owner.execute(() -> f.endpoint.emit(first, new AgentEvent.ContextUpdated(original(IMAGE), original(IMAGE))));
        f.owner.execute(() -> f.endpoint.emit(first, new AgentEvent.ContextFinalized(original(IMAGE), original(IMAGE))));
        f.owner.execute(() -> f.endpoint.finish(first, AgentState.CANCELLED));
        f.owner.runAll(); assertFalse(disconnected.isDone());
        assertTrue(f.store.entered.await(2, TimeUnit.SECONDS));
        assertFalse(f.endpoint.actorCleared, "clearActor must not erase final context before its custody handoff");
        assertTrue(f.contexts.releases.isEmpty());
        f.store.allow.countDown(); assertTrue(f.contexts.releaseStarted.await(2, TimeUnit.SECONDS));
        f.owner.runAll(); assertEquals(List.of(IMAGE), f.store.sessionImages());
        assertFalse(disconnected.isDone(), "the real producer-release future is part of disconnect");
        f.contexts.releaseGate.complete(null); f.owner.until(disconnected::isDone);
        disconnected.join(); assertTrue(f.endpoint.actorCleared);
        assertTrue(f.service.snapshot().sessions().getFirst().requests().isEmpty());
    }

    @Test void cancelledUnpublishedProducedPixelsKeepTheirOnlyOwnerWithoutHangingDisconnect() throws Exception {
        Fixture f = new Fixture(); UUID first = f.ask("first");
        f.store.owners.put("producer", List.of(IMAGE));
        var disconnected = f.service.disconnect(); f.owner.runAll();
        f.endpoint.emit(first, new AgentEvent.ContextFinalized(original(), original()));
        f.endpoint.finish(first, AgentState.CANCELLED); f.owner.until(disconnected::isDone);
        assertThrows(java.util.concurrent.CompletionException.class, disconnected::join);
        assertTrue(f.contexts.releases.isEmpty());
        assertEquals(List.of(IMAGE), f.store.owners.get("producer"));
        assertEquals(0, f.store.collect(UUID.randomUUID()));
        assertArrayEquals(new byte[] {1}, f.store.read(UUID.randomUUID(), IMAGE));
    }

    @Test void disconnectRejectsNewWorkWhileWaitingForRealEndpointFinalization() throws Exception {
        Fixture f = new Fixture(); UUID first = f.ask("first");
        var disconnected = f.service.disconnect(); f.owner.runAll();
        var rejected = f.service.ask("must not be admitted"); f.owner.runAll();
        assertInstanceOf(ToolResult.Failure.class, rejected.join());
        assertEquals(1, f.endpoint.calls.size());
        assertFalse(disconnected.isDone()); assertFalse(f.endpoint.actorCleared);
        f.endpoint.emit(first, new AgentEvent.ContextFinalized(original(), original()));
        f.endpoint.finish(first, AgentState.CANCELLED); f.owner.runAll();
        assertTrue(f.contexts.releaseStarted.await(2, TimeUnit.SECONDS));
        f.contexts.releaseGate.complete(null); f.owner.until(disconnected::isDone);
        assertTrue(f.endpoint.actorCleared);
    }

    private static List<ModelMessage> original(ImageReference... images) {
        return List.of(ModelMessage.userText("first"), new ModelMessage(ModelRole.ASSISTANT,
                List.of(new ModelContent.ToolUse("capture", "openallay__run_javascript", new com.google.gson.JsonObject()))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "capture", new JsonPrimitive("real view"), false, List.of(images)))));
    }
    private static <T> T success(ToolResult<T> result) {
        return ((ToolResult.Success<T>) assertInstanceOf(ToolResult.Success.class, result)).value();
    }
    private static final class Fixture {
        final Owner owner = new Owner(); final Store store = new Store(); final Endpoint endpoint = new Endpoint();
        final Contexts contexts = new Contexts(store);
        final GuideService service = new GuideService(UUID.randomUUID(), endpoint, new GuideRemoteEndpoint() {
            public boolean serverModelAvailable() { return false; }
            public boolean serverToolsAvailable() { return false; }
            public boolean ask(UUID id, String session, String question, Consumer<AgentEvent> events) { return false; }
            public boolean cancel(UUID id) { return true; }
            public void disconnect() {}
        }, contexts, owner::execute, Clock.systemUTC(), new Gson(), null, null, store);
        UUID ask(String text) { var asked = service.ask(text); owner.runAll(); return success(asked.join()); }
        void followUp(String text) { var queued = service.followUp(text); owner.runAll(); success(queued.join()); }
    }
    private static final class Contexts implements GuideContextProvider {
        final Store store; final List<String> closed = new java.util.concurrent.CopyOnWriteArrayList<>();
        final List<String> releases = new java.util.concurrent.CopyOnWriteArrayList<>();
        final CompletableFuture<Void> releaseGate = new CompletableFuture<>();
        final CountDownLatch releaseStarted = new CountDownLatch(1);
        Contexts(Store store) { this.store = store; }
        public ToolResult<ToolInvocationContext> capture(Set<ContextCapability> capabilities, String id) {
            return new ToolResult.Success<>(ToolInvocationContext.developmentConsole(id));
        }
        public void closeRequest(String id) { closed.add(id); }
        public List<ImageReference> observationImageReferences(String id) {
            return store.owners.getOrDefault("producer", List.of());
        }
        public CompletableFuture<Void> releaseObservationImages(String id) {
            releases.add(id); releaseStarted.countDown();
            return releaseGate.thenRun(() -> store.owners.remove("producer"));
        }
    }
    private static final class Endpoint implements GuideLocalEndpoint {
        final List<UUID> calls = new ArrayList<>();
        boolean actorCleared;
        final Map<UUID, Consumer<AgentEvent>> events = new ConcurrentHashMap<>();
        final Map<UUID, CompletableFuture<AgentResult>> futures = new ConcurrentHashMap<>();
        public Set<ContextCapability> requiredContext() { return Set.of(); }
        public List<GuideClientModelProfile> profiles() { return List.of(new GuideClientModelProfile(
                "default", "Default", true, true, "model", null, ImageInputCapability.SUPPORTED, "test")); }
        public CompletableFuture<AgentResult> ask(UUID actor, String session, UUID id, String text,
                ToolInvocationContext context, Consumer<AgentEvent> events) {
            calls.add(id); this.events.put(id, events); var future = new CompletableFuture<AgentResult>();
            futures.put(id, future); return future;
        }
        public boolean cancel(UUID actor, String session) { return true; }
        public void clearSession(UUID actor, String session) {}
        public void clearActor(UUID actor) { actorCleared = true; }
        void emit(UUID id, AgentEvent event) { events.get(id).accept(event); }
        void finish(UUID id, AgentState state) { futures.get(id).complete(new AgentResult(
                state, state == AgentState.COMPLETED ? "done" : null,
                state == AgentState.CANCELLED ? "agent_cancelled" : null,
                state == AgentState.CANCELLED ? "cancelled" : null, null)); }
    }
    private static final class Owner {
        final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        final Semaphore ready = new Semaphore(0);
        void execute(Runnable task) { tasks.add(task); ready.release(); }
        void runAll() { while (ready.tryAcquire()) tasks.remove().run(); }
        void until(BooleanSupplier condition) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!condition.getAsBoolean()) {
                long remaining = deadline - System.nanoTime();
                assertTrue(remaining > 0 && ready.tryAcquire(remaining, TimeUnit.NANOSECONDS), "custody handoff must settle");
                tasks.remove().run(); runAll();
            }
        }
    }
    private static final class Store implements ImageAttachmentStore {
        final Map<String, List<ImageReference>> owners = new ConcurrentHashMap<>();
        final CountDownLatch entered = new CountDownLatch(1); final CountDownLatch allow = new CountDownLatch(1);
        volatile ImageReference block; volatile boolean fail;
        public ImageInputLimits limits() { return ImageInputLimits.defaults(); }
        public ImageReference importImage(UUID actor, byte[] bytes) { throw new UnsupportedOperationException(); }
        public ImageReference importImage(UUID actor, String owner, byte[] bytes) { throw new UnsupportedOperationException(); }
        public byte[] read(UUID actor, ImageReference reference) { return new byte[] {1}; }
        public void retain(UUID actor, String owner, List<ImageReference> references) throws IOException {
            if (owner.contains(":session:") && references.contains(block)) {
                entered.countDown();
                try { if (!allow.await(2, TimeUnit.SECONDS)) throw new IOException("fixture gate timed out"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IOException(failure); }
                if (fail) throw new IOException("injected memory owner retain failure");
            }
            owners.put(owner, List.copyOf(references));
        }
        List<ImageReference> sessionImages() { return owners.entrySet().stream().filter(entry ->
                entry.getKey().contains(":session:")).flatMap(entry -> entry.getValue().stream()).distinct().toList(); }
        public void reconcile(UUID actor, String namespace, Map<String, List<ImageReference>> owners) {}
        public void release(UUID actor, String owner) { owners.remove(owner); }
        public int collect(UUID actor) { return owners.values().stream().anyMatch(refs -> refs.contains(IMAGE)) ? 0 : 1; }
    }
}
