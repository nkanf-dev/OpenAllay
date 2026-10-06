package dev.openallay.bridge.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import dev.openallay.agent.tool.ModelImageToolOutput;
import dev.openallay.agent.tool.ToolRuntimeCatalog;
import dev.openallay.bridge.protocol.*;
import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.guide.GuideContextProvider;
import dev.openallay.model.image.*;
import dev.openallay.model.ModelMessage;
import dev.openallay.world.ClientObservationAnchor;
import dev.openallay.world.WorldFocusObservation;
import dev.openallay.tool.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/** Native-free engine tests. Manual queues make every custody boundary explicit; no sleeps or game. */
final class ClientBridgeSessionTest {
    @Test
    void queuedInboundCapturesExactScopeAndNativeConnection() {
        Fixture f = new Fixture();
        try {
            int[] changes = {0};
            f.session.onCapabilitiesChanged(() -> changes[0]++);
            Object connection = f.host.connection;
            Runnable replaced = f.session.inboundCallback("capabilities", f.codec.encode(capabilities()),
                    () -> f.host.connection == connection);
            f.host.connection = new Object();
            replaced.run();
            assertEquals(0, changes[0]);

            Object currentConnection = f.host.connection;
            Runnable oldScope = f.session.inboundCallback("capabilities", f.codec.encode(capabilities()),
                    () -> f.host.connection == currentConnection);
            f.session.disconnectState();
            oldScope.run();
            assertEquals(0, changes[0], "unchanged native identity must not revive an old scope");
            f.session.inboundCallback("capabilities", f.codec.encode(capabilities()),
                    () -> f.host.connection == currentConnection).run();
            assertEquals(1, changes[0]);
        } finally { f.close(); }
    }

    @Test
    void clientAdmissionAndInputObservationRejectReplacementAndExactScopeReset() {
        Fixture f = new Fixture();
        try {
            UUID request = f.open(event -> {});
            BooleanSupplier admitted = f.session.clientToolAdmission(request.toString());
            assertTrue(admitted.getAsBoolean());
            assertEquals(Optional.empty(), f.session.clientToolInputObservation(request.toString()));
            Object original = f.host.connection;
            f.host.connection = new Object();
            assertFalse(admitted.getAsBoolean());
            assertThrows(IllegalStateException.class,
                    () -> f.session.clientToolInputObservation(request.toString()));
            f.host.connection = original;
            assertTrue(admitted.getAsBoolean());
            f.session.disconnectState();
            assertFalse(admitted.getAsBoolean(), "an exact scope reset cannot be undone by a native token");
        } finally { f.close(); }
    }

    @Test
    void queuedToolResultRechecksExactRequestAndConnection() {
        for (boolean disconnect : List.of(false, true)) {
            Fixture f = new Fixture();
            try {
                UUID request = f.open(event -> {});
                f.call(request, "test:scope");
                f.tools.drain();
                f.images.drain();
                f.tools.drain();
                assertFalse(f.client.tasks.isEmpty(), "result must wait for native dispatch");
                if (disconnect) f.session.disconnectState();
                else f.host.connection = new Object();
                f.client.drain();
                assertEquals(0, f.host.count("client_tool_result"));
            } finally { f.close(); }
        }
    }

    @Test
    void queuedToolResultCannotEnterReplacementWithSameRequestId() {
        Fixture f = new Fixture();
        try {
            UUID request = f.open(event -> {});
            BooleanSupplier admitted = f.session.clientToolAdmission(request.toString());
            f.call(request, "test:scope");
            f.tools.drain(); f.images.drain(); f.tools.drain();
            f.event(request, "request_released", false);
            assertTrue(f.session.askServer(new ServerAgentRequestPayload(request, "main", "replacement", false),
                    event -> {}));
            assertFalse(admitted.getAsBoolean(), "custody belongs to the original request object");
            assertTrue(f.session.clientToolAdmission(request.toString()).getAsBoolean());
            f.client.drain();
            assertEquals(0, f.host.count("client_tool_result"));
        } finally { f.close(); }
    }

    @Test
    void terminalClosesToolsBeforeCallbackButRetainsLateUsageUntilRelease() {
        Fixture f = new Fixture();
        try {
            List<String> received = new ArrayList<>();
            UUID successor = UUID.randomUUID();
            UUID request = f.open(event -> {
                received.add(event.eventType());
                if (event.terminal() || event.eventType().equals("request_released")) {
                    assertTrue(f.scope.closed.contains(event.requestId().toString()));
                    assertTrue(f.contexts.closed.contains(event.requestId().toString()));
                }
                if (event.eventType().equals("request_released")) {
                    assertTrue(f.session.askServer(new ServerAgentRequestPayload(
                            successor, "main", "successor", false), ignored -> {}));
                }
            });
            f.event(request, "completed", true);
            assertFalse(f.session.clientToolAdmission(request.toString()).getAsBoolean());
            f.event(request, "context_finalized", false);
            f.event(request, "usage", false);
            f.event(request, "request_released", false);
            f.event(request, "usage", false);
            assertEquals(List.of("completed", "context_finalized", "usage", "request_released"), received);
            assertEquals(List.of(request.toString()), f.scope.closed);
            assertTrue(f.session.clientToolAdmission(successor.toString()).getAsBoolean());
            assertEquals(0, f.store.releases);
            assertEquals(0, f.contexts.producerReleases,
                    "endpoint close must not drop request-produced image pins");
        } finally { f.close(); }
    }

    @Test
    void cancellationKeepsOneHandshakeAndClosesNativeWorkWithoutReleasingProducer() {
        Fixture f = new Fixture();
        try {
            List<String> events = new ArrayList<>();
            UUID request = f.open(event -> events.add(event.eventType()));
            BooleanSupplier admitted = f.session.clientToolAdmission(request.toString());
            assertTrue(f.session.cancelServer(request));
            assertFalse(f.session.cancelServer(request));
            assertEquals(1, f.host.count("agent_cancel"));
            assertFalse(admitted.getAsBoolean());
            assertFalse(f.session.steerServer(new ServerAgentSteerPayload(
                    request, UUID.randomUUID(), ServerAgentSteerPayload.Operation.REMOVE, null)));
            f.event(request, "cancelled", true);
            f.event(request, "usage", false);
            f.event(request, "request_released", false);
            assertEquals(List.of("cancelled", "usage", "request_released"), events);
            assertEquals(List.of(request.toString()), f.scope.closed);
            assertEquals(0, f.store.releases);
            assertEquals(0, f.contexts.producerReleases);
        } finally { f.close(); }
    }

    @Test
    void channelGuardDoesNotRetryOrAddANeoForgePolicy() {
        Fixture fabric = new Fixture();
        Fixture neo = new Fixture();
        try {
            fabric.host.available = false;
            assertFalse(fabric.session.askServer(new ServerAgentRequestPayload(
                    UUID.randomUUID(), "main", "blocked", false), ignored -> {}));
            assertTrue(fabric.host.sent.isEmpty());
            fabric.host.available = true;
            UUID blockedCancel = fabric.open(ignored -> {});
            fabric.host.available = false;
            assertFalse(fabric.session.cancelServer(blockedCancel));
            fabric.host.available = true;
            assertFalse(fabric.session.cancelServer(blockedCancel), "no automatic cancel retry");
            assertEquals(0, fabric.host.count("agent_cancel"));

            // Native NeoForge binding always reports true, even without a Fabric channel check.
            UUID unguarded = neo.open(ignored -> {});
            assertTrue(neo.session.cancelServer(unguarded));
            assertEquals(1, neo.host.count("agent_cancel"));
        } finally { fabric.close(); neo.close(); }
    }

    @Test
    void outboundUsesExistingCodecChunkerAndFrozenToolCatalog() {
        Fixture f = new Fixture();
        try {
            ClientObservationAnchor admitted = anchor(f.host.actor);
            ServerAgentRequestPayload input = new ServerAgentRequestPayload(
                    UUID.randomUUID(), "main", ModelMessage.userText("exact canonical input ".repeat(4000))
                            .withInputObservation(admitted), true, List.of(), List.of());
            assertTrue(f.session.askServer(input, event -> {}));
            ServerAgentRequestChunker.Reassembler chunks = new ServerAgentRequestChunker.Reassembler();
            String complete = null;
            int count = 0;
            for (Sent sent : f.host.sent) {
                if (!sent.kind().equals("agent_request_chunk")) continue;
                count++;
                var accepted = chunks.accept(f.host.actor,
                        f.codec.decode(sent.json(), ServerAgentRequestChunkPayload.class));
                if (accepted.isPresent()) complete = accepted.orElseThrow();
            }
            chunks.clearActor(f.host.actor);
            assertTrue(count > 1);
            ServerAgentRequestPayload outbound = f.codec.decode(Objects.requireNonNull(complete),
                    ServerAgentRequestPayload.class);
            assertEquals(input.userInput(), outbound.userInput());
            assertEquals(input.history(), outbound.history());
            assertEquals(List.of("test:scope", "test:visual"), outbound.clientToolIds());
            assertEquals(Optional.of(admitted), f.session.clientToolInputObservation(input.requestId().toString()));
            assertSame(admitted, f.session.clientToolInputObservation(input.requestId().toString()).orElseThrow(),
                    "custody keeps the admitted input object, not a later draft");

            assertTrue(f.session.steerServer(new ServerAgentSteerPayload(input.requestId(), UUID.randomUUID(),
                    ServerAgentSteerPayload.Operation.PUT,
                    new ServerAgentHistoryMessage(ServerAgentHistoryMessage.Role.USER, "steer"))));
            assertTrue(f.session.steerServer(new ServerAgentSteerPayload(input.requestId(), UUID.randomUUID(),
                    ServerAgentSteerPayload.Operation.REMOVE, null)));
            assertEquals(1, f.host.count("agent_steer_chunk"));
            assertEquals(1, f.host.count("agent_steer"));
        } finally { f.close(); }
    }

    @Test
    void sendFailureClosesEndpointAndDoesNotLeaveAdmissionOrRetry() {
        Fixture f = new Fixture();
        try {
            UUID request = UUID.randomUUID();
            f.host.failSend = true;
            assertFalse(f.session.askServer(new ServerAgentRequestPayload(request, "main", "question", false),
                    event -> {}));
            assertFalse(f.session.clientToolAdmission(request.toString()).getAsBoolean());
            assertEquals(List.of(request.toString()), f.scope.closed);
            assertEquals(1, f.host.sendAttempts);
            assertEquals(0, f.store.releases);
        } finally { f.close(); }
    }

    @Test
    void eventChunksReassembleAndReleaseClearsPartialCustody() {
        Fixture f = new Fixture();
        try {
            List<ServerAgentEventPayload> events = new ArrayList<>();
            UUID request = f.open(events::add);
            UUID eventId = UUID.randomUUID();
            var event = new ServerAgentEventPayload(request, "usage", "{\"value\":1}", false);
            var chunks = new ResultChunker().split(eventId, f.codec.encode(event), 32);
            for (int index = chunks.size() - 1; index >= 0; index--) {
                f.chunk(ServerAgentEventChunkPayload.from(request, chunks.get(index)));
            }
            assertEquals(List.of(event), events);

            UUID partialId = UUID.randomUUID();
            var partial = new ResultChunker().split(partialId, f.codec.encode(event), 32);
            f.chunk(ServerAgentEventChunkPayload.from(request, partial.getFirst()));
            f.event(request, "request_released", false);
            assertTrue(f.session.askServer(new ServerAgentRequestPayload(request, "main", "new", false),
                    events::add));
            for (int index = 1; index < partial.size(); index++) {
                f.chunk(ServerAgentEventChunkPayload.from(request, partial.get(index)));
            }
            assertEquals(2, events.size(), "old first chunk must be cleared at release");
            f.chunk(ServerAgentEventChunkPayload.from(request, partial.getFirst()));
            assertEquals(3, events.size());
            assertEquals(event, events.getLast());
        } finally { f.close(); }
    }

    @Test
    void eventChunkCorrelationAndHashChecksRemainStrictAndRecoverable() {
        Fixture f = new Fixture();
        try {
            List<ServerAgentEventPayload> events = new ArrayList<>();
            UUID request = f.open(events::add);
            UUID eventId = UUID.randomUUID();
            var wrong = new ServerAgentEventPayload(UUID.randomUUID(), "usage", "{}", false);
            var wrongChunk = new ResultChunker().split(eventId, f.codec.encode(wrong), 4096).getFirst();
            assertThrows(IllegalArgumentException.class,
                    () -> f.chunk(ServerAgentEventChunkPayload.from(request, wrongChunk)));
            assertTrue(events.isEmpty());
            var event = new ServerAgentEventPayload(request, "usage", "{}", false);
            var valid = new ResultChunker().split(eventId, f.codec.encode(event), 4096).getFirst();
            var corrupted = new ServerAgentEventChunkPayload(request, eventId, valid.index(), valid.total(),
                    "0".repeat(64), valid.base64Data());
            assertThrows(IllegalArgumentException.class, () -> f.chunk(corrupted));
            f.chunk(ServerAgentEventChunkPayload.from(request, valid));
            assertEquals(List.of(event), events);
        } finally { f.close(); }
    }

    @Test
    void imageProducerAdmissionRejectsReplacementCancelTerminalAndDisconnectBeforeRead() {
        for (String boundary : List.of("replacement", "cancel", "terminal", "disconnect")) {
            Fixture f = new Fixture();
            try {
                UUID request = f.open(event -> {});
                f.call(request, "test:visual");
                f.tools.drain();
                assertEquals(1, f.images.tasks.size());
                switch (boundary) {
                    case "replacement" -> f.host.connection = new Object();
                    case "cancel" -> f.session.cancelServer(request);
                    case "terminal" -> f.event(request, "completed", true);
                    case "disconnect" -> f.session.disconnectState();
                    default -> throw new AssertionError(boundary);
                }
                f.images.drain(); f.tools.drain(); f.client.drain();
                assertTrue(f.store.readActors.isEmpty(), boundary);
                assertEquals(0, f.host.count("client_tool_result"), boundary);
                assertEquals(0, f.store.releases, boundary);
                assertEquals(0, f.contexts.producerReleases, boundary);
            } finally { f.close(); }
        }
    }

    @Test
    void imageProducerUsesCapturedActorAndRechecksAfterRead() {
        for (boolean replaceDuringRead : List.of(false, true)) {
            Fixture f = new Fixture();
            try {
                UUID actor = f.host.actor;
                UUID request = f.open(event -> {});
                f.call(request, "test:visual");
                f.tools.drain();
                f.host.actor = UUID.randomUUID();
                if (replaceDuringRead) f.store.afterRead = () -> f.host.connection = new Object();
                f.images.drain(); f.tools.drain(); f.client.drain();
                assertEquals(List.of(actor), f.store.readActors);
                if (replaceDuringRead) assertEquals(0, f.host.count("client_tool_result"));
                else {
                    ResultChunker.Reassembler chunks = new ResultChunker.Reassembler();
                    String complete = null;
                    for (Sent sent : f.host.sent) {
                        if (!sent.kind().equals("client_tool_result")) continue;
                        var accepted = chunks.accept(f.codec.decode(sent.json(),
                                ClientToolResultChunkPayload.class).asRemoteChunk());
                        if (accepted.isPresent()) complete = accepted.orElseThrow();
                    }
                    chunks.clear();
                    ToolExecutionMessage message = f.codec.decode(Objects.requireNonNull(complete),
                            ToolExecutionMessage.class);
                    assertEquals(f.store.reference, message.imageAttachments().getFirst().reference());
                    assertArrayEquals(f.store.bytes, message.imageAttachments().getFirst().bytes());
                }
                assertEquals(0, f.store.releases);
            } finally { f.close(); }
        }
    }

    private static ClientObservationAnchor anchor(UUID actor) {
        Instant at = Instant.parse("2026-10-04T00:00:00Z");
        var evidence = new EvidenceMetadata(DataAuthority.DETERMINISTIC_TEST, DataCompleteness.COMPLETE,
                at, "test:focus", "test:manual", "test", "test", Map.of());
        var empty = new WorldFocusObservation.Item("minecraft:air", 0, "", 0, 0, new JsonObject(), true, "");
        var focus = new WorldFocusObservation(at, actor, "minecraft:overworld",
                new WorldFocusObservation.Camera(0, 0, 0, 0, 0, 70, "first_person", true, false, actor),
                new WorldFocusObservation.Target("none", null, null, null), empty, empty,
                new WorldFocusObservation.Screen("", "", 0, 0, false, false, "gameplay"),
                new WorldFocusObservation.Menu("test:menu", 0, 0, "", false, false, true, 0, empty, ""),
                new WorldFocusObservation.Hover(0, 0, false, "none", -1, -1, null, ""), evidence);
        return ClientObservationAnchor.focus(focus);
    }

    private static CapabilityPayload capabilities() {
        return new CapabilityPayload(List.of(), true, 32768, 1024, 0, "test-model");
    }

    private record Sent(String kind, String json) {}

    private static final class ManualQueue implements Executor, ClientEventDispatcher {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable command) { tasks.addLast(command); }
        void drain() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }
    }

    /** Opaque Object is only an identity token. No native or dynamic API is dispatched. */
    private static final class ManualHost implements ClientBridgeSession.NativeHost {
        UUID actor = UUID.randomUUID();
        Object connection = new Object();
        boolean available = true;
        boolean failSend;
        int sendAttempts;
        final List<Sent> sent = new ArrayList<>();
        @Override public Optional<ClientBridgeSession.Connection> captureConnection() {
            Object captured = connection;
            return captured == null ? Optional.empty()
                    : Optional.of(new ClientBridgeSession.Connection(actor, () -> connection == captured));
        }
        @Override public boolean canSend() { return available; }
        @Override public void send(String kind, String json) {
            sendAttempts++;
            if (failSend) throw new IllegalStateException("native send failed");
            sent.add(new Sent(kind, json));
        }
        long count(String kind) { return sent.stream().filter(packet -> packet.kind().equals(kind)).count(); }
    }

    private record Input() {}
    private record Output(String value) {}
    private record VisualOutput(List<ImageReference> images) implements ModelImageToolOutput {}

    private static final class ScopeTool implements Tool<Input, Output>, RequestScopeParticipant {
        final List<String> closed = new ArrayList<>();
        @Override public ToolDescriptor<Input, Output> descriptor() {
            return new ToolDescriptor<>("test:scope", "Read a fact", Input.class, Output.class, ToolAccess.READ_ONLY);
        }
        @Override public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
            return new ToolResult.Success<>(new Output("fact"));
        }
        @Override public void closeRequestScope(String correlationId) { closed.add(correlationId); }
    }

    private static final class TestContexts implements GuideContextProvider {
        final List<String> closed = new ArrayList<>();
        int producerReleases;
        @Override public ToolResult<ToolInvocationContext> capture(
                Set<dev.openallay.context.ContextCapability> capabilities, String correlationId) {
            return new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlationId));
        }
        @Override public void closeRequest(String correlationId) { closed.add(correlationId); }
        @Override public CompletableFuture<Void> releaseObservationImages(String correlationId) {
            producerReleases++;
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Detached bytes from a fixed PNG; this manual store observes custody, not native image capture. */
    private static final class TestImages implements ImageAttachmentStore {
        final byte[] bytes = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jf1sAAAAASUVORK5CYII=");
        final ImageReference reference;
        final List<UUID> readActors = new ArrayList<>();
        int releases;
        Runnable afterRead = () -> {};
        TestImages() {
            try {
                reference = new ImageReference(HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(bytes)), "image/png", 1, 1, bytes.length);
            } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        }
        @Override public ImageInputLimits limits() { return ImageInputLimits.defaults(); }
        @Override public byte[] read(UUID actor, ImageReference image) {
            assertEquals(reference, image);
            readActors.add(actor);
            afterRead.run();
            return bytes.clone();
        }
        @Override public ImageReference importImage(UUID actor, byte[] image) { throw new UnsupportedOperationException(); }
        @Override public ImageReference importImage(UUID actor, String owner, byte[] image) {
            throw new UnsupportedOperationException();
        }
        @Override public void retain(UUID actor, String owner, List<ImageReference> images) {
            throw new UnsupportedOperationException();
        }
        @Override public void reconcile(UUID actor, String namespace, Map<String, List<ImageReference>> owners) {
            throw new UnsupportedOperationException();
        }
        @Override public void release(UUID actor, String owner) { releases++; }
        @Override public int collect(UUID actor) { throw new UnsupportedOperationException(); }
    }

    private static final class Fixture implements AutoCloseable {
        final BridgeJsonCodec codec = new BridgeJsonCodec();
        final ManualHost host = new ManualHost();
        final ManualQueue client = new ManualQueue();
        final ManualQueue tools = new ManualQueue();
        final ManualQueue images = new ManualQueue();
        final ScopeTool scope = new ScopeTool();
        final TestImages store = new TestImages();
        final TestContexts contexts = new TestContexts();
        final ClientBridgeSession session = new ClientBridgeSession(host, client, images, tools);
        Fixture() {
            ToolRegistry registry = new ToolRegistry();
            registry.register("test", List.of(scope, new Tool<Input, VisualOutput>() {
                @Override public ToolDescriptor<Input, VisualOutput> descriptor() {
                    return new ToolDescriptor<>("test:visual", "Read an image", Input.class,
                            VisualOutput.class, ToolAccess.READ_ONLY);
                }
                @Override public ToolResult<VisualOutput> invoke(ToolInvocationContext context, Input input) {
                    return new ToolResult.Success<>(new VisualOutput(List.of(store.reference)));
                }
            }));
            session.configureClientTools(() -> ToolRuntimeCatalog.from(registry.registrations(), Set.of()),
                    (required, correlation, cancellation) -> CompletableFuture.completedFuture(
                            ToolInvocationContext.developmentConsole(correlation)), dev.openallay.json.EngineJson.create());
            session.configureResultImages(store, contexts);
            session.receive("capabilities", codec.encode(capabilities()));
        }
        UUID open(java.util.function.Consumer<ServerAgentEventPayload> events) {
            UUID request = UUID.randomUUID();
            assertTrue(session.askServer(new ServerAgentRequestPayload(request, "main", "question", false), events));
            return request;
        }
        void call(UUID request, String tool) {
            session.receive("client_tool_call", codec.encode(new ClientToolCallPayload(
                    request, UUID.randomUUID(), "main", tool, "{}")));
        }
        void event(UUID request, String type, boolean terminal) {
            session.receive("agent_event", codec.encode(new ServerAgentEventPayload(request, type, "{}", terminal)));
        }
        void chunk(ServerAgentEventChunkPayload chunk) { session.receive("agent_event_chunk", codec.encode(chunk)); }
        @Override public void close() { session.disconnectState(); }
    }
}
