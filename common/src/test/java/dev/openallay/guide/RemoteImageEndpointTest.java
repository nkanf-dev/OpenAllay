package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.openallay.agent.AgentEvent;
import dev.openallay.bridge.protocol.CapabilityPayload;
import dev.openallay.bridge.protocol.ServerAgentEventCodec;
import dev.openallay.bridge.protocol.ServerAgentEventPayload;
import dev.openallay.bridge.protocol.ServerAgentImageAttachment;
import dev.openallay.bridge.protocol.ServerAgentRequestPayload;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.image.FileImageAttachmentStore;
import dev.openallay.model.image.ImageInputCapability;
import dev.openallay.model.image.ImageReference;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RemoteImageEndpointTest {
    private static final byte[] BYTES = encodedImage("png", 1, 1);
    private static final ImageReference REFERENCE = new ImageReference(
            sha256(BYTES), "image/png", 1, 1, BYTES.length);
    private static final ModelMessage IMAGE = image(REFERENCE);

    @Test
    void associatedOnlyFrameIsUploadedForHistoryAndSteerWithoutOrdinaryImageBlocks() throws Exception {
        var anchor = dev.openallay.world.InputObservationFixtures.anchor(REFERENCE);
        var input = ModelMessage.userInput("", List.of(), java.util.Optional.of(anchor));
        Port port = new Port(ImageInputCapability.SUPPORTED);
        var endpoint = new PayloadGuideRemoteEndpoint(port, new Gson(), Runnable::run, Runnable::run);
        UUID requestId = UUID.randomUUID();
        try {
            assertTrue(endpoint.askWithContext(requestId, "main", ModelMessage.userText("continue"),
                    reference -> BYTES, List.of(input), ignored -> {}));
            var request = port.sent.get(5, TimeUnit.SECONDS);
            assertEquals(List.of(REFERENCE), request.imageAttachments().stream()
                    .map(ServerAgentImageAttachment::reference).toList());
            assertEquals(input, request.history().getFirst().toModelMessage());
            UUID messageId = UUID.randomUUID();
            assertTrue(endpoint.steer(requestId, messageId, input, reference -> BYTES));
            var steer = port.steerSent.get(5, TimeUnit.SECONDS);
            assertEquals(input, steer.message().toModelMessage());
            assertEquals(List.of(REFERENCE), steer.imageAttachments().stream()
                    .map(ServerAgentImageAttachment::reference).toList());
            assertArrayEquals(BYTES, steer.imageAttachments().getFirst().bytes());
        } finally { endpoint.disconnect(); }
    }

    @Test
    void uploadsUniqueActualImageClosureOffCallerThread(@TempDir Path directory) throws Exception {
        terminalImageRequestKeepsItsIdUntilTheActualReleaseEvent();
        UUID actor = UUID.randomUUID();
        var store = new FileImageAttachmentStore(directory.resolve("images"));
        byte[] png = encodedImage("png", 2, 1);
        byte[] jpeg = encodedImage("jpeg", 3, 2);
        var first = store.importImage(actor, "fixture", png);
        var second = store.importImage(actor, "fixture", jpeg);
        var input = new ModelMessage(ModelRole.USER,
                List.of(new ModelContent.Image(second), new ModelContent.Image(first)));
        var history = List.of(image(first), new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("Look at the pictures"))));
        Port port = new Port(ImageInputCapability.SUPPORTED);
        var endpoint = new PayloadGuideRemoteEndpoint(port, new Gson());
        Thread caller = Thread.currentThread();
        var readThreads = new CopyOnWriteArrayList<Thread>();
        var reads = new CopyOnWriteArrayList<ImageReference>();
        try {
            assertTrue(endpoint.askWithContext(UUID.randomUUID(), "main", input, reference -> {
                readThreads.add(Thread.currentThread());
                reads.add(reference);
                return store.read(actor, reference);
            }, history, ignored -> {}));

            var request = port.sent.get(5, TimeUnit.SECONDS);
            assertEquals(List.of(first, second), reads, "each unique reference is read once");
            assertTrue(readThreads.stream().allMatch(thread -> thread != caller));
            assertEquals(input, request.userInput().toModelMessage());
            assertEquals(history, request.history().stream().map(message -> message.toModelMessage()).toList());
            assertEquals(List.of(first, second), request.imageAttachments().stream()
                    .map(ServerAgentImageAttachment::reference).toList());
            assertArrayEquals(png, request.imageAttachments().getFirst().bytes());
            assertArrayEquals(jpeg, request.imageAttachments().getLast().bytes());
            assertEquals("image/png", first.mimeType());
            assertEquals("image/jpeg", second.mimeType());
            assertEquals(2, ImageIO.read(new ByteArrayInputStream(png)).getWidth());
            assertEquals(3, ImageIO.read(new ByteArrayInputStream(jpeg)).getWidth());
            assertThrows(IOException.class, () -> store.read(UUID.randomUUID(), first));
        } finally {
            endpoint.disconnect();
            store.release(actor, "fixture");
            assertEquals(2, store.collect(actor));
        }
    }

    @Test
    void unknownAndUnsupportedModelsRejectBeforeReadingEvenForHistoryOnlyImages() {
        for (var capability : List.of(ImageInputCapability.UNKNOWN, ImageInputCapability.UNSUPPORTED)) {
            Port port = new Port(capability);
            var endpoint = new PayloadGuideRemoteEndpoint(port, new Gson());
            var failure = assertThrows(GuideModelProfileException.class, () -> endpoint.askWithContext(
                    UUID.randomUUID(), "main", ModelMessage.userText("follow up"), reference -> {
                        throw new AssertionError("unsupported requests must not read images");
                    }, List.of(IMAGE), ignored -> {}));
            assertEquals(capability == ImageInputCapability.UNKNOWN
                    ? "image_input_unknown" : "image_input_unsupported", failure.code());
            assertFalse(port.sent.isDone());
            assertEquals(0, port.cancelCalls.get());
        }
    }

    @Test
    void missingPayloadFailsItsAcceptedRequestAndNeverSendsATextFallback() throws Exception {
        Port port = new Port(ImageInputCapability.SUPPORTED);
        var endpoint = new PayloadGuideRemoteEndpoint(port, new Gson());
        var received = new CopyOnWriteArrayList<AgentEvent>();
        var released = new CompletableFuture<Void>();
        assertTrue(endpoint.ask(UUID.randomUUID(), "main", IMAGE, reference -> {
            throw new IOException("missing");
        }, event -> {
            received.add(event);
            if (event instanceof AgentEvent.RequestReleased) released.complete(null);
        }));
        released.get(5, TimeUnit.SECONDS);
        assertEquals(2, received.size());
        assertEquals("image_unavailable", assertInstanceOf(AgentEvent.Failed.class, received.getFirst()).code());
        assertInstanceOf(AgentEvent.RequestReleased.class, received.getLast());
        assertFalse(port.sent.isDone());
        assertEquals(0, port.cancelCalls.get());
    }

    @Test
    void cancelDuringReadRevokesLateUpload() throws Exception {
        disconnectDuringReadWaitsForReleaseAndNeverUploadsLateBytes();
        Port port = new Port(ImageInputCapability.SUPPORTED);
        var endpoint = new PayloadGuideRemoteEndpoint(port, new Gson());
        UUID requestId = UUID.randomUUID();
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch finishRead = new CountDownLatch(1);
        var released = new CompletableFuture<Void>();
        var received = new CopyOnWriteArrayList<AgentEvent>();
        try {
            assertTrue(endpoint.ask(requestId, "main", IMAGE, reference -> {
                reading.countDown();
                await(finishRead);
                return BYTES;
            }, event -> {
                received.add(event);
                if (event instanceof AgentEvent.RequestReleased) released.complete(null);
            }));
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            assertTrue(endpoint.cancel(requestId));
            assertFalse(released.isDone(), "release must wait for the in-progress read");
            assertFalse(endpoint.ask(requestId, "main", IMAGE, reference -> BYTES, ignored -> {}));
            assertTrue(received.isEmpty());
            finishRead.countDown();
            released.get(5, TimeUnit.SECONDS);
            assertEquals(2, received.size());
            assertEquals("agent_cancelled", assertInstanceOf(AgentEvent.Failed.class, received.getFirst()).code());
            assertInstanceOf(AgentEvent.RequestReleased.class, received.getLast());
            assertFalse(port.sent.isDone(), "late read bytes must not escape the cancelled owner");
            assertEquals(0, port.cancelCalls.get(), "no server request was sent");
        } finally {
            finishRead.countDown();
            endpoint.disconnect();
        }
    }

    private void disconnectDuringReadWaitsForReleaseAndNeverUploadsLateBytes() throws Exception {
        Port port = new Port(ImageInputCapability.SUPPORTED);
        var endpoint = new PayloadGuideRemoteEndpoint(port, new Gson());
        UUID requestId = UUID.randomUUID();
        var reading = new CountDownLatch(1);
        var finishRead = new CountDownLatch(1);
        var released = new CompletableFuture<Void>();
        var received = new CopyOnWriteArrayList<AgentEvent>();
        try {
            assertTrue(endpoint.ask(requestId, "main", IMAGE, reference -> {
                reading.countDown();
                await(finishRead);
                return BYTES;
            }, event -> {
                received.add(event);
                if (event instanceof AgentEvent.RequestReleased) released.complete(null);
            }));
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            endpoint.disconnect();
            assertEquals(1, port.disconnectCalls.get());
            assertFalse(released.isDone());
            assertFalse(endpoint.ask(requestId, "main", IMAGE, reference -> BYTES, ignored -> {}));
            finishRead.countDown();
            released.get(5, TimeUnit.SECONDS);
            assertEquals("server_disconnected", assertInstanceOf(AgentEvent.Failed.class, received.getFirst()).code());
            assertInstanceOf(AgentEvent.RequestReleased.class, received.getLast());
            assertEquals(2, received.size());
            assertFalse(port.sent.isDone());
        } finally {
            finishRead.countDown();
        }
    }

    private void terminalImageRequestKeepsItsIdUntilTheActualReleaseEvent() throws Exception {
        Port port = new Port(ImageInputCapability.SUPPORTED);
        var endpoint = new PayloadGuideRemoteEndpoint(port, new Gson());
        UUID requestId = UUID.randomUUID();
        var received = new CopyOnWriteArrayList<AgentEvent>();
        var codec = new ServerAgentEventCodec(new Gson());
        try {
            assertTrue(endpoint.ask(requestId, "main", IMAGE, reference -> BYTES, received::add));
            Consumer<ServerAgentEventPayload> events = port.events.get(5, TimeUnit.SECONDS);
            UUID call = UUID.randomUUID();
            ModelUsage usage = new ModelUsage(10, 2, 3, 0, 7, true, true, true, true, true);
            events.accept(codec.encode(requestId, new AgentEvent.ModelUsageStarted(call, "test/model")));
            events.accept(codec.encode(requestId, new AgentEvent.FinalText("done")));
            assertFalse(endpoint.ask(requestId, "main", IMAGE, reference -> BYTES, ignored -> {}));
            events.accept(codec.encode(requestId, new AgentEvent.ModelUsageObserved(call, "test/model", usage)));
            events.accept(codec.encode(requestId, new AgentEvent.RequestReleased()));
            assertEquals(4, received.size());
            assertEquals(usage, assertInstanceOf(AgentEvent.ModelUsageObserved.class, received.get(2)).usage());
            assertInstanceOf(AgentEvent.RequestReleased.class, received.getLast());
            assertTrue(endpoint.ask(requestId, "main", ModelMessage.userText("again"),
                    reference -> { throw new AssertionError("text request has no images"); }, ignored -> {}));
        } finally {
            endpoint.disconnect();
        }
    }

    private static ModelMessage image(ImageReference reference) {
        return new ModelMessage(ModelRole.USER, List.of(new ModelContent.Image(reference)));
    }

    private static byte[] encodedImage(String format, int width, int height) {
        BufferedImage image = new BufferedImage(width, height,
                format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, output)) throw new AssertionError("No image writer");
            return output.toByteArray();
        } catch (IOException failure) {
            throw new AssertionError(failure);
        } finally {
            image.flush();
        }
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IOException("read timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException(interrupted);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class Port implements PayloadGuideRemoteEndpoint.Port {
        private final ImageInputCapability capability;
        private final CompletableFuture<ServerAgentRequestPayload> sent = new CompletableFuture<>();
        private final CompletableFuture<dev.openallay.bridge.protocol.ServerAgentSteerPayload> steerSent = new CompletableFuture<>();
        private final CompletableFuture<Consumer<ServerAgentEventPayload>> events = new CompletableFuture<>();
        private final AtomicInteger cancelCalls = new AtomicInteger();
        private final AtomicInteger disconnectCalls = new AtomicInteger();
        private Port(ImageInputCapability capability) { this.capability = capability; }
        @Override public CapabilityPayload capabilities() {
            return new CapabilityPayload(List.of(), true, 100_000, 8_192, 2_000,
                    "test/model", capability, "explicit");
        }
        @Override public boolean ask(ServerAgentRequestPayload request, Consumer<ServerAgentEventPayload> sink) {
            sent.complete(request);
            events.complete(sink);
            return true;
        }
        @Override public boolean steer(dev.openallay.bridge.protocol.ServerAgentSteerPayload payload) {
            steerSent.complete(payload); return true;
        }
        @Override public boolean cancel(UUID requestId) { cancelCalls.incrementAndGet(); return false; }
        @Override public void disconnect() { disconnectCalls.incrementAndGet(); }
    }
}
