package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentState;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.image.FileImageAttachmentStore;
import dev.openallay.model.image.ImageInputCapability;
import dev.openallay.model.image.ImagePayloadResolver;
import dev.openallay.model.image.ImageReference;
import dev.openallay.tool.ToolResult;
import dev.openallay.world.ClientObservationAnchor;
import dev.openallay.world.InputObservationFixtures;
import dev.openallay.world.WorldViewCapture;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Actual GuideService admissions; detached typed sources and managed fixture PNGs only. */
final class GuideServiceInputObservationOccurrenceTest {
    @TempDir Path temporary;

    @Test
    void askDeliversCompleteAnchorAndOrdinaryImagesWithActorScopedCustody() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            ModelMessage input = f.input(101, "inspect this sword");
            UUID request = success(await(f.service.ask(input)));
            assertInput(input, f.endpoint.calls.getFirst().input());
            assertEquals(InputObservationFixtures.ACTOR, f.endpoint.calls.getFirst().actor());
            assertEquals(input.inputObservation(), f.contexts.associated.get(request.toString()));
            assertEquals(input, f.service.userInput(request));
            f.dropImportsAndCheckPins(f.endpoint.calls.getFirst());
            f.complete(request, List.of(input), AgentState.COMPLETED);
            f.assertOriginal(request, List.of(input));
            assertEquals(0, f.store.collect(InputObservationFixtures.ACTOR));
        }
    }

    @Test
    void queuedFollowUpRevisionKeepsItsOwnOccurrenceDespiteEqualSourceImageBytes() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            ModelMessage first = f.input(201, "first source");
            ModelMessage queued = f.input(202, "queued source");
            ModelMessage revised = f.input(203, "revised queued source");
            assertNotEquals(first.inputObservation(), queued.inputObservation());
            assertNotEquals(queued.inputObservation(), revised.inputObservation());
            assertEquals(first.inputObservation().orElseThrow().image().orElseThrow().image(),
                    revised.inputObservation().orElseThrow().image().orElseThrow().image(),
                    "same artifact bytes do not identify the input occurrence");
            UUID active = success(await(f.service.ask(first)));
            UUID receipt = success(await(f.service.followUp(queued)));
            assertInput(queued, f.service.pendingMessages("main").getFirst().message());
            assertTrue(success(await(f.service.editPending(receipt, revised))));
            assertEquals(receipt, f.service.pendingMessages("main").getFirst().id());
            assertInput(revised, f.service.pendingMessages("main").getFirst().message());
            assertEquals(1, f.endpoint.calls.size());
            f.dropImportsAndCheckPins(f.endpoint.calls.getFirst());
            f.endpoint.emit(active, new AgentEvent.ContextUpdated(List.of(first), List.of(first)));
            f.endpoint.emit(active, new AgentEvent.FinalText("done but not released"));
            f.barrier();
            assertEquals(1, f.endpoint.calls.size(), "visible completion must not dispatch a follow-up");
            assertEquals(active, f.session().workingRequestId());
            f.endpoint.release(active, AgentState.COMPLETED);
            f.when(snapshot -> session(snapshot).requests().size() == 2);
            f.barrier();
            Call dispatched = f.endpoint.calls.getLast();
            assertNotEquals(active, dispatched.id());
            assertNotEquals(receipt, dispatched.id());
            assertInput(revised, dispatched.input());
            assertEquals(revised.inputObservation(), f.contexts.associated.get(dispatched.id().toString()));
            assertTrue(f.service.pendingMessages("main").isEmpty());
            f.complete(dispatched.id(), List.of(first, revised), AgentState.COMPLETED);
            f.assertOriginal(active, List.of(first));
            f.assertOriginal(dispatched.id(), List.of(first, revised));
            assertEquals(0, f.store.collect(InputObservationFixtures.ACTOR));
            assertArrayEquals(f.sourceBytes, dispatched.images().read(f.source));
        }
    }

    @Test
    void acceptedSteerRetainsItsDistinctAnchorAndOrdinaryImagesAfterReceiptRemoval() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            ModelMessage initial = f.input(301, "initial goal");
            ModelMessage supplemental = f.input(302, "use this other occurrence");
            UUID request = success(await(f.service.ask(initial)));
            UUID receipt = success(await(f.service.steer(supplemental)));
            Steer captured = f.endpoint.steers.getFirst();
            assertEquals(InputObservationFixtures.ACTOR, captured.actor());
            assertEquals(request, captured.request());
            assertEquals(receipt, captured.receipt());
            assertInput(supplemental, captured.input());
            assertNotEquals(initial.inputObservation().orElseThrow().associationId(),
                    captured.input().inputObservation().orElseThrow().associationId());
            assertEquals(1, f.endpoint.calls.size(), "accepted steer does not start a new ask");
            f.endpoint.emit(request, new AgentEvent.SteerApplied(receipt, captured.input()));
            f.endpoint.emit(request, new AgentEvent.SteerApplied(receipt, captured.input()));
            f.barrier();
            assertTrue(f.service.pendingMessages("main").isEmpty());
            assertEquals(1, f.session().requests().getFirst().timeline().size());
            assertEquals(new GuideTimelineEntry.User(0, receipt, "use this other occurrence"),
                    f.session().requests().getFirst().timeline().getFirst());
            List<ModelMessage> actualContext = List.of(f.endpoint.calls.getFirst().input(), captured.input());
            f.endpoint.emit(request, new AgentEvent.ContextUpdated(actualContext, actualContext));
            f.barrier();
            f.dropImportsAndCheckPins(f.endpoint.calls.getFirst());
            f.complete(request, actualContext, AgentState.COMPLETED);
            f.assertOriginal(request, actualContext);
            assertEquals(1, f.endpoint.calls.size());
            assertEquals(0, f.store.collect(InputObservationFixtures.ACTOR));
        }
    }

    @Test
    void retryResubmitsOriginalOccurrenceRatherThanLaterEqualHashInput() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            ModelMessage original = f.input(401, "retry this precise source");
            ModelMessage later = f.input(402, "later equal-pixel source");
            UUID failed = success(await(f.service.ask(original)));
            f.dropImportsAndCheckPins(f.endpoint.calls.getFirst());
            f.complete(failed, List.of(original), AgentState.FAILED);
            UUID intervening = success(await(f.service.ask(later)));
            f.complete(intervening, List.of(original, later), AgentState.COMPLETED);
            UUID retry = success(await(f.service.retry(failed)));
            assertNotEquals(failed, retry);
            assertNotEquals(intervening, retry);
            Call submitted = f.endpoint.calls.getLast();
            assertEquals(3, f.endpoint.calls.size());
            assertEquals(retry, submitted.id());
            assertInput(original, submitted.input());
            assertEquals(original.inputObservation(), f.contexts.associated.get(retry.toString()));
            assertNotEquals(later.inputObservation(), submitted.input().inputObservation());
            assertEquals(0, f.store.collect(InputObservationFixtures.ACTOR));
            assertArrayEquals(f.sourceBytes, submitted.images().read(f.source));
            assertArrayEquals(f.ordinaryBytes, submitted.images().read(f.ordinary));
            f.complete(retry, List.of(original), AgentState.COMPLETED);
            f.assertOriginal(retry, List.of(original));
        }
    }

    private static void assertInput(ModelMessage expected, ModelMessage actual) {
        assertEquals(expected, actual, "the service must submit the whole typed user input");
        assertEquals(expected.content(), actual.content(), "ordinary image blocks must remain ordinary input");
        ClientObservationAnchor anchor = expected.inputObservation().orElseThrow();
        ClientObservationAnchor delivered = actual.inputObservation().orElseThrow();
        assertEquals(anchor.associationId(), delivered.associationId());
        assertEquals(anchor.capturedAt(), delivered.capturedAt());
        assertEquals(InputObservationFixtures.SOURCE, delivered.capturedAt());
        assertEquals(InputObservationFixtures.ACTOR, delivered.focus().actorId());
        assertEquals(anchor.focus(), delivered.focus(), "full detached focus, including exact item components");
        assertEquals(anchor.image(), delivered.image(), "full source frame, not just its image hash");
        assertEquals(InputObservationFixtures.FRAME_SOURCE, delivered.image().orElseThrow().capturedAt());
        assertEquals(InputObservationFixtures.ACTOR, delivered.image().orElseThrow().actorId());
        assertEquals(anchor.focus().hover().item().components(), delivered.focus().hover().item().components());
        assertEquals(expected.content().stream().filter(ModelContent.Image.class::isInstance).toList(),
                actual.content().stream().filter(ModelContent.Image.class::isInstance).toList());
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(3, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private static <T> T success(ToolResult<T> result) {
        return ((ToolResult.Success<T>) assertInstanceOf(ToolResult.Success.class, result)).value();
    }

    private static GuideSessionSnapshot session(GuideSnapshot snapshot) {
        return snapshot.sessions().stream().filter(value -> value.sessionId().equals("main"))
                .findFirst().orElseThrow();
    }

    private static byte[] png(int color) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            image.setRGB(0, 0, color);
            assertTrue(ImageIO.write(image, "png", bytes));
            return bytes.toByteArray();
        } finally {
            image.flush();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ExecutorService owner = Executors.newSingleThreadExecutor();
        final FileImageAttachmentStore store;
        final Endpoint endpoint = new Endpoint();
        final Contexts contexts = new Contexts();
        final GuideService service;
        final byte[] sourceBytes = png(0xff112233);
        final byte[] ordinaryBytes = png(0xff445566);
        final ImageReference source;
        final ImageReference ordinary;

        Fixture(Path directory) throws Exception {
            store = new FileImageAttachmentStore(directory.resolve("managed-images"));
            service = new GuideService(InputObservationFixtures.ACTOR, endpoint, offline(), contexts,
                    owner::execute, Clock.systemUTC(), new Gson(), null, null, store);
            try {
                source = success(await(service.importImage(sourceBytes)));
                ordinary = success(await(service.importImage(ordinaryBytes)));
            } catch (Exception failure) {
                owner.shutdownNow();
                owner.awaitTermination(3, TimeUnit.SECONDS);
                throw failure;
            }
        }

        ModelMessage input(long occurrence, String text) {
            ClientObservationAnchor template = InputObservationFixtures.anchor(source);
            WorldViewCapture frame = template.image().orElseThrow();
            WorldViewCapture distinctFrame = new WorldViewCapture("source-frame-" + occurrence,
                    frame.capturedAt(), frame.actorId(), frame.dimension(), frame.target(),
                    frame.includedHud(), frame.includedGameUi(), frame.sourceWidth(), frame.sourceHeight(),
                    frame.guiScale(), frame.camera(), frame.screen(), frame.image(), frame.evidence());
            ClientObservationAnchor anchor = new ClientObservationAnchor(new UUID(0, occurrence),
                    template.capturedAt(), template.focus(), Optional.of(distinctFrame));
            return ModelMessage.userInput(text, List.of(ordinary), Optional.of(anchor));
        }

        GuideSessionSnapshot session() { return GuideServiceInputObservationOccurrenceTest.session(service.snapshot()); }

        void barrier() throws Exception { owner.submit(() -> {}).get(3, TimeUnit.SECONDS); }

        void when(Predicate<GuideSnapshot> condition) throws Exception {
            CompletableFuture<Void> reached = new CompletableFuture<>();
            try (GuideSubscription subscription = service.subscribe(snapshot -> {
                if (condition.test(snapshot)) reached.complete(null);
            })) {
                await(reached);
            }
        }

        void dropImportsAndCheckPins(Call submitted) throws Exception {
            assertTrue(success(await(service.releaseImportedImage(source))));
            assertTrue(success(await(service.releaseImportedImage(ordinary))));
            assertEquals(0, store.collect(InputObservationFixtures.ACTOR),
                    "accepted input must pin both managed source and ordinary images without import leases");
            assertArrayEquals(sourceBytes, submitted.images().read(source));
            assertArrayEquals(ordinaryBytes, submitted.images().read(ordinary));
            assertThrows(java.io.IOException.class, () -> store.read(new UUID(0, 999), source),
                    "the managed reference is not an actor-independent read grant");
        }

        void complete(UUID request, List<ModelMessage> actualContext, AgentState state) throws Exception {
            endpoint.emit(request, new AgentEvent.ContextUpdated(actualContext, actualContext));
            endpoint.emit(request, state == AgentState.COMPLETED ? new AgentEvent.FinalText("done")
                    : new AgentEvent.Failed("fixture_failure", "detached endpoint failure"));
            barrier();
            endpoint.release(request, state);
            when(snapshot -> GuideServiceInputObservationOccurrenceTest.session(snapshot).workingRequestId() == null);
            barrier();
        }

        void assertOriginal(UUID request, List<ModelMessage> expected) throws Exception {
            try (var exported = success(await(service.captureSelectedSessionForExport()))) {
                assertEquals(expected, exported.requests().stream().filter(value -> value.requestId().equals(request))
                        .findFirst().orElseThrow().originalContext());
            }
            // Queue an image operation after export.close() so its async lease release has completed.
            success(await(service.readImage(source)));
        }

        @Override public void close() throws Exception {
            try {
                CompletableFuture<Void> disconnected = service.disconnect();
                barrier();
                endpoint.futures.forEach((id, future) -> {
                    if (!future.isDone()) {
                        endpoint.emit(id, new AgentEvent.ContextFinalized(List.of(), List.of()));
                        endpoint.release(id, AgentState.CANCELLED);
                    }
                });
                await(disconnected);
            } finally {
                owner.shutdownNow();
                assertTrue(owner.awaitTermination(3, TimeUnit.SECONDS), "fixture owner must terminate");
            }
        }
    }

    private static final class Contexts implements GuideContextProvider {
        final Map<String, Optional<ClientObservationAnchor>> associated = new ConcurrentHashMap<>();
        public void associateInputObservation(String correlation, Optional<ClientObservationAnchor> anchor) {
            associated.put(correlation, anchor);
        }
        public ToolResult<ToolInvocationContext> capture(Set<ContextCapability> capabilities, String correlation) {
            assertTrue(associated.containsKey(correlation), "association must precede context capture");
            return new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation));
        }
    }

    private record Call(UUID actor, UUID id, ModelMessage input, ImagePayloadResolver images) {}
    private record Steer(UUID actor, UUID request, UUID receipt, ModelMessage input) {}

    private static final class Endpoint implements GuideLocalEndpoint {
        final List<Call> calls = new CopyOnWriteArrayList<>();
        final List<Steer> steers = new CopyOnWriteArrayList<>();
        final Map<UUID, Consumer<AgentEvent>> events = new ConcurrentHashMap<>();
        final Map<UUID, CompletableFuture<AgentResult>> futures = new ConcurrentHashMap<>();
        public Set<ContextCapability> requiredContext() { return Set.of(); }
        public List<GuideClientModelProfile> profiles() {
            return List.of(new GuideClientModelProfile("default", "Fixture", true, true, "fixture", null,
                    ImageInputCapability.SUPPORTED, "test"));
        }
        public CompletableFuture<AgentResult> ask(UUID actor, String session, UUID id, String text,
                ToolInvocationContext context, Consumer<AgentEvent> consumer) {
            throw new AssertionError("typed source input must not use the text endpoint");
        }
        public CompletableFuture<AgentResult> ask(String profile, UUID actor, String session, UUID id,
                ModelMessage input, ImagePayloadResolver images, ToolInvocationContext context,
                Consumer<AgentEvent> consumer) {
            assertEquals(InputObservationFixtures.ACTOR, actor);
            assertEquals("main", session);
            calls.add(new Call(actor, id, input, images));
            events.put(id, consumer);
            CompletableFuture<AgentResult> future = new CompletableFuture<>();
            futures.put(id, future);
            consumer.accept(new AgentEvent.StateChanged(AgentState.MODEL_WAIT));
            return future;
        }
        public ToolResult<Boolean> steer(UUID actor, String session, UUID id, UUID receipt, ModelMessage input) {
            assertEquals("main", session);
            steers.add(new Steer(actor, id, receipt, input));
            return new ToolResult.Success<>(true);
        }
        public boolean cancel(UUID actor, String session) { return true; }
        public void clearSession(UUID actor, String session) {}
        public void clearActor(UUID actor) {}
        void emit(UUID id, AgentEvent event) { events.get(id).accept(event); }
        void release(UUID id, AgentState state) {
            futures.get(id).complete(new AgentResult(state, state == AgentState.COMPLETED ? "done" : null,
                    state == AgentState.COMPLETED ? null : "fixture_failure",
                    state == AgentState.COMPLETED ? null : "detached endpoint ended", null));
            emit(id, new AgentEvent.RequestReleased());
        }
    }

    private static GuideRemoteEndpoint offline() {
        return new GuideRemoteEndpoint() {
            public boolean serverModelAvailable() { return false; }
            public boolean serverToolsAvailable() { return false; }
            public boolean ask(UUID id, String session, String text, Consumer<AgentEvent> events) {
                throw new AssertionError("the fixture must never dispatch remotely");
            }
            public boolean cancel(UUID id) { return false; }
            public void disconnect() {}
        };
    }
}
