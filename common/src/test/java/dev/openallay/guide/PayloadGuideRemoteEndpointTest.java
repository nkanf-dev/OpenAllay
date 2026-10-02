package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.openallay.agent.AgentEvent;
import dev.openallay.bridge.protocol.CapabilityPayload;
import dev.openallay.bridge.protocol.ServerAgentEventPayload;
import dev.openallay.bridge.protocol.ServerAgentRequestPayload;
import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class PayloadGuideRemoteEndpointTest {
    @Test
    void sendsActualModelContextWithServerRequests() {
        FakePort port = new FakePort();
        PayloadGuideRemoteEndpoint endpoint = new PayloadGuideRemoteEndpoint(port, new Gson());
        UUID request = UUID.randomUUID();
        List<dev.openallay.model.ModelMessage> actual = List.of(
                dev.openallay.model.ModelMessage.userText("old question"),
                new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.ASSISTANT,
                        List.of(new dev.openallay.model.ModelContent.Text("old answer"))));

        assertTrue(endpoint.askWithContext(request, "main", "follow up", actual, ignored -> {}));

        assertEquals(request, port.request.requestId());
        assertEquals(
                List.of("USER:old question", "ASSISTANT:old answer"),
                port.request.history().stream()
                        .map(message -> message.role() + ":" + message.content().getFirst().text())
                        .toList());
    }

    @Test
    void sendsRealToolInputsAndPlaintextFailureInsteadOfDisplaySurrogates() {
        FakePort port = new FakePort();
        PayloadGuideRemoteEndpoint endpoint = new PayloadGuideRemoteEndpoint(port, new Gson());
        com.google.gson.JsonObject input = new com.google.gson.JsonObject();
        input.addProperty("source", "return mc.items.filter(x => x.id); ");
        List<dev.openallay.model.ModelMessage> actual = List.of(
                new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.ASSISTANT,
                        List.of(new dev.openallay.model.ModelContent.ToolUse("actual", "openallay__run_javascript", input))),
                new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.USER,
                        List.of(new dev.openallay.model.ModelContent.ToolResult("actual",
                                new com.google.gson.JsonPrimitive("code: javascript_error\nmessage: .filter is undefined"), true))));
        assertTrue(endpoint.askWithContext(UUID.randomUUID(), "main", "follow up", actual, ignored -> {}));
        assertEquals(actual, port.request.history().stream().map(
                dev.openallay.bridge.protocol.ServerAgentHistoryMessage::toModelMessage).toList());
        dev.openallay.agent.context.ContextStructure.units(actual);
    }

    @Test
    void malformedRemoteEventFailsOnlyItsRequestAndCancelsTransport() {
        FakePort port = new FakePort();
        PayloadGuideRemoteEndpoint endpoint = new PayloadGuideRemoteEndpoint(port, new Gson());
        UUID request = UUID.randomUUID();
        List<AgentEvent> events = new ArrayList<>();

        assertTrue(endpoint.ask(request, "main", "question", events::add));
        port.events.accept(new ServerAgentEventPayload(
                request, "future_event", "{}", false));

        AgentEvent.Failed failed = assertInstanceOf(AgentEvent.Failed.class, events.getFirst());
        assertEquals("server_protocol_error", failed.code());
        assertEquals(List.of(request), port.cancelled);
    }

    @Test
    void sendsPutEditAndRemoveWithTheSameRequestAndMessageCorrelation() {
        FakePort port = new FakePort();
        PayloadGuideRemoteEndpoint endpoint = new PayloadGuideRemoteEndpoint(port, new Gson());
        UUID requestId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        assertTrue(endpoint.ask(requestId, "main", "question", ignored -> {}));
        assertTrue(endpoint.steer(requestId, messageId,
                dev.openallay.model.ModelMessage.userText("first")));
        assertTrue(endpoint.editSteer(requestId, messageId,
                dev.openallay.model.ModelMessage.userText("edited")));
        assertTrue(endpoint.cancelSteer(requestId, messageId));
        assertEquals(List.of(
                dev.openallay.bridge.protocol.ServerAgentSteerPayload.Operation.PUT,
                dev.openallay.bridge.protocol.ServerAgentSteerPayload.Operation.PUT,
                dev.openallay.bridge.protocol.ServerAgentSteerPayload.Operation.REMOVE),
                port.steers.stream().map(
                        dev.openallay.bridge.protocol.ServerAgentSteerPayload::operation).toList());
        for (var payload : port.steers) {
            assertEquals(requestId, payload.requestId());
            assertEquals(messageId, payload.messageId());
        }
        assertEquals(dev.openallay.model.ModelMessage.userText("edited"),
                port.steers.get(1).message().toModelMessage());
        org.junit.jupiter.api.Assertions.assertNull(port.steers.getLast().message());
    }

    @Test
    void keepsDeliveringThroughTerminalUntilExplicitRelease() {
        FakePort port = new FakePort();
        PayloadGuideRemoteEndpoint endpoint = new PayloadGuideRemoteEndpoint(port, new Gson());
        UUID requestId = UUID.randomUUID();
        List<AgentEvent> actual = new ArrayList<>();
        endpoint.ask(requestId, "main", "question", actual::add);
        var codec = new dev.openallay.bridge.protocol.ServerAgentEventCodec(new Gson());
        port.events.accept(codec.encode(requestId, new AgentEvent.FinalText("done")));
        port.events.accept(codec.encode(requestId, new AgentEvent.RequestReleased()));
        assertEquals(List.of(new AgentEvent.FinalText("done"), new AgentEvent.RequestReleased()), actual);
    }

    @Test
    void typedSteerReadsOnlyOnWorkerAndRemoveFencesTheQueuedRead() {
        FakePort port = new FakePort();
        List<Runnable> work = new ArrayList<>();
        PayloadGuideRemoteEndpoint endpoint = new PayloadGuideRemoteEndpoint(port, new Gson(), work::add, Runnable::run);
        UUID requestId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        endpoint.ask(requestId, "main", "question", ignored -> {});
        byte[] bytes = {1, 2, 3};
        var reference = new dev.openallay.model.image.ImageReference(
                sha256(bytes), "image/png", 1, 1, bytes.length);
        var message = new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.USER,
                List.of(new dev.openallay.model.ModelContent.Text("look"),
                        new dev.openallay.model.ModelContent.Image(reference)));
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        assertTrue(endpoint.steer(requestId, messageId, message, image -> {
            reads.incrementAndGet();
            return bytes;
        }));
        assertEquals(0, reads.get(), "owner/caller must not read image files");
        assertTrue(endpoint.cancelSteer(requestId, messageId));
        work.forEach(Runnable::run);
        assertEquals(0, reads.get(), "a queued removed instruction cannot resurrect");
        assertEquals(List.of(dev.openallay.bridge.protocol.ServerAgentSteerPayload.Operation.REMOVE),
                port.steers.stream().map(dev.openallay.bridge.protocol.ServerAgentSteerPayload::operation).toList());
    }

    @Test
    void typedSteerEditsKeepOnlyCurrentPayloadAndReadFailuresRejectThatMessage() {
        FakePort port = new FakePort();
        List<Runnable> work = new ArrayList<>();
        PayloadGuideRemoteEndpoint endpoint = new PayloadGuideRemoteEndpoint(port, new Gson(), work::add, Runnable::run);
        UUID requestId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        List<AgentEvent> actual = new ArrayList<>();
        endpoint.ask(requestId, "main", "question", actual::add);
        byte[] bytes = {1, 2, 3};
        var reference = new dev.openallay.model.image.ImageReference(
                sha256(bytes), "image/png", 1, 1, bytes.length);
        var original = new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.USER,
                List.of(new dev.openallay.model.ModelContent.Text("original"),
                        new dev.openallay.model.ModelContent.Image(reference)));
        var edited = new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.USER,
                List.of(new dev.openallay.model.ModelContent.Text("edited"),
                        new dev.openallay.model.ModelContent.Image(reference)));
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        dev.openallay.model.image.ImagePayloadResolver resolver = image -> {
            reads.incrementAndGet();
            return bytes;
        };
        assertTrue(endpoint.steer(requestId, messageId, original, resolver));
        assertTrue(endpoint.editSteer(requestId, messageId, edited, resolver));
        work.forEach(Runnable::run);
        assertEquals(1, reads.get());
        assertEquals(1, port.steers.size());
        assertEquals(edited, port.steers.getFirst().message().toModelMessage());
        assertEquals(reference, port.steers.getFirst().imageAttachments().getFirst().reference());
        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes,
                port.steers.getFirst().imageAttachments().getFirst().bytes());
        work.clear();
        UUID failedId = UUID.randomUUID();
        assertTrue(endpoint.steer(requestId, failedId, edited, image -> {
            throw new java.io.IOException("missing scoped bytes");
        }));
        work.forEach(Runnable::run);
        assertEquals(List.of(new AgentEvent.SteerRejected(failedId)), actual);
        assertEquals(1, port.steers.size());
    }

    @Test
    void terminalReleaseWaitsForAnActualTypedSteerReadAndCannotCloseItsResolverEarly() throws Exception {
        FakePort port = new FakePort();
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.CompletableFuture<Void> reading = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<Void> readGate = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<Void> released = new java.util.concurrent.CompletableFuture<>();
        List<AgentEvent> actual = new java.util.concurrent.CopyOnWriteArrayList<>();
        PayloadGuideRemoteEndpoint endpoint = new PayloadGuideRemoteEndpoint(port, new Gson(), worker, Runnable::run);
        UUID requestId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        endpoint.ask(requestId, "main", "question", event -> {
            actual.add(event);
            if (event instanceof AgentEvent.RequestReleased) released.complete(null);
        });
        byte[] bytes = {1, 2, 3};
        var reference = new dev.openallay.model.image.ImageReference(
                sha256(bytes), "image/png", 1, 1, bytes.length);
        var input = new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.USER,
                List.of(new dev.openallay.model.ModelContent.Image(reference)));
        try {
            assertTrue(endpoint.steer(requestId, messageId, input, image -> {
                reading.complete(null);
                readGate.join();
                return bytes;
            }));
            reading.get(5, java.util.concurrent.TimeUnit.SECONDS);
            var codec = new dev.openallay.bridge.protocol.ServerAgentEventCodec(new Gson());
            port.events.accept(codec.encode(requestId, new AgentEvent.FinalText("done")));
            port.events.accept(codec.encode(requestId, new AgentEvent.RequestReleased()));
            org.junit.jupiter.api.Assertions.assertFalse(released.isDone());
            assertEquals(List.of(new AgentEvent.FinalText("done")), actual);
            readGate.complete(null);
            released.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(List.of(new AgentEvent.FinalText("done"), new AgentEvent.RequestReleased()), actual);
            assertTrue(port.steers.isEmpty(), "a late read cannot send into the released request");
        } finally {
            readGate.complete(null);
            worker.shutdownNow();
        }
    }

    private static final class FakePort implements PayloadGuideRemoteEndpoint.Port {
        private Consumer<ServerAgentEventPayload> events;
        private ServerAgentRequestPayload request;
        private final List<UUID> cancelled = new ArrayList<>();
        private final List<dev.openallay.bridge.protocol.ServerAgentSteerPayload> steers = new ArrayList<>();
        @Override public CapabilityPayload capabilities() {
            return new CapabilityPayload(
                    List.of(), true,
                    256_000, 8_192, 2_000, "test/model");
        }
        @Override public boolean ask(
                ServerAgentRequestPayload request, Consumer<ServerAgentEventPayload> events) {
            this.request = request;
            this.events = events;
            return true;
        }
        @Override public boolean steer(dev.openallay.bridge.protocol.ServerAgentSteerPayload payload) {
            steers.add(payload);
            return true;
        }
        @Override public boolean cancel(UUID requestId) {
            cancelled.add(requestId);
            return true;
        }
        @Override public void disconnect() {}
    }
    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
    }


}
