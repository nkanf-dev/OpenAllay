package dev.openallay.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import dev.openallay.bridge.protocol.ServerAgentImageAttachment;
import dev.openallay.bridge.protocol.ServerAgentRequestPayload;
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
import dev.openallay.model.ObservingModelClient;
import dev.openallay.model.image.FileImageAttachmentStore;
import dev.openallay.model.image.ImageAttachmentStore;
import dev.openallay.model.image.ImageInputCapability;
import dev.openallay.model.image.ImageInputLimits;
import dev.openallay.model.image.ImageReference;
import dev.openallay.tool.ToolResult;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ServerAgentImageServiceTest {
    private static final ModelUsage USAGE = new ModelUsage(10, 2, 3, 0, 7,
            true, true, true, true, true);

    @Test
    void imageOnlyRequestResolvesForSenderAndNeverForAnotherActor(@TempDir Path directory) throws Exception {
        for (String format : List.of("png", "jpeg")) {
            byte[] bytes = encodedImage(format, 2, 1);
            var reference = reference(bytes, format.equals("png") ? "image/png" : "image/jpeg", 2, 1);
            UUID sender = UUID.randomUUID();
            Path root = directory.resolve(format);
            var store = new FileImageAttachmentStore(root);
            var observed = new CompletableFuture<ModelRequest>();
            var response = new CompletableFuture<ModelTurn>();
            var fixture = fixture(store, ImageInputCapability.SUPPORTED, (request, events, cancellation) -> {
                observed.complete(request);
                return response;
            });
            var payload = payload(reference, bytes);
            try {
                assertInstanceOf(ToolResult.Success.class, fixture.service.ask(sender, payload));
                ModelRequest request = observed.get(5, TimeUnit.SECONDS);
                assertEquals(payload.userInput().toModelMessage(), request.messages().getLast());
                assertArrayEquals(bytes, request.images().read(reference));
                assertEquals(2, ImageIO.read(new ByteArrayInputStream(request.images().read(reference))).getWidth());
                assertEquals(reference, store.importImage(sender, bytes));
                assertThrows(IOException.class, () -> store.read(UUID.randomUUID(), reference));
                var outside = new ImageReference("a".repeat(64), reference.mimeType(), 2, 1, bytes.length);
                assertThrows(IOException.class, () -> request.images().read(outside));
                assertFalse(fixture.service.cancel(UUID.randomUUID(), payload.requestId()));
                response.complete(turn("done"));
                fixture.released(payload.requestId()).get(5, TimeUnit.SECONDS);
                assertEquals(1, fixture.closes.get());
                assertEquals(0, fixture.service.activeRequests());
                assertFalse(fixture.service.hasRequest(sender, payload.requestId()));
                assertEquals("request_released", fixture.events.getLast().eventType());
                var receipt = assertInstanceOf(AgentEvent.ModelUsageObserved.class,
                        fixture.decode(fixture.events.stream()
                                .filter(event -> event.eventType().equals("model_usage_observed")).findFirst().orElseThrow()));
                assertEquals(USAGE, receipt.usage());
                assertEquals(1, fixture.events.stream().filter(ServerAgentEventPayload::terminal).count());
                assertThrows(IOException.class, () -> request.images().read(reference));
                assertArrayEquals(bytes, store.read(sender, reference), "session history retains the image");
            } finally {
                response.complete(turn("cleanup"));
                fixture.service.disconnectAsync(sender).get(5, TimeUnit.SECONDS);
            }
            assertFalse(Files.exists(root.resolve(sender.toString()).resolve(reference.sha256())));
            assertThrows(IOException.class, () -> store.read(sender, reference));
        }
    }

    @Test
    void validWireHashWithIncorrectImageDimensionsFailsBeforeModelDispatch(@TempDir Path directory) throws Exception {
        hashAndFullPngAndJpegDecodeFailuresReleaseBeforeAnyModelDispatch(directory.resolve("decode"));
        byte[] bytes = encodedImage("png", 1, 1);
        var reference = reference(bytes, "image/png", 2, 1);
        UUID sender = UUID.randomUUID();
        var observed = new CompletableFuture<ModelRequest>();
        var fixture = fixture(new FileImageAttachmentStore(directory.resolve("images")),
                ImageInputCapability.SUPPORTED, (request, events, cancellation) -> {
                    observed.complete(request);
                    return new CompletableFuture<>();
                });
        var request = payload(reference, bytes);
        try {
            assertInstanceOf(ToolResult.Success.class, fixture.service.ask(sender, request));
            fixture.released(request.requestId()).get(5, TimeUnit.SECONDS);
            assertEquals("image_attachment_failed", fixture.failure.get(5, TimeUnit.SECONDS).code());
            assertFalse(observed.isDone());
            assertEquals(1, fixture.closes.get());
            assertEquals(0, fixture.service.activeRequests());
            assertEquals(0, fixture.events.stream().filter(event -> event.eventType().startsWith("model_usage_")).count());
            assertEquals("request_released", fixture.events.getLast().eventType());
        } finally {
            fixture.service.disconnectAsync(sender).get(5, TimeUnit.SECONDS);
        }
    }

    private void hashAndFullPngAndJpegDecodeFailuresReleaseBeforeAnyModelDispatch(@TempDir Path directory) throws Exception {
        int index = 0;
        for (String format : List.of("png", "jpeg")) {
            byte[] full = encodedImage(format, 3, 2);
            String mime = format.equals("png") ? "image/png" : "image/jpeg";
            byte[] truncated = Arrays.copyOf(full, full.length / 2);
            var invalid = List.of(
                    ServerAgentImageAttachment.from(new ImageReference("a".repeat(64), mime, 3, 2, full.length), full),
                    ServerAgentImageAttachment.from(reference(truncated, mime, 3, 2), truncated),
                    ServerAgentImageAttachment.from(reference(full,
                            format.equals("png") ? "image/jpeg" : "image/png", 3, 2), full));
            for (var attachment : invalid) {
                UUID sender = UUID.randomUUID();
                Path root = directory.resolve("invalid-" + index++);
                var store = new FileImageAttachmentStore(root);
                var fixture = fixture(store, ImageInputCapability.SUPPORTED,
                        (request, events, cancellation) -> { throw new AssertionError("No model dispatch expected"); });
                var request = new ServerAgentRequestPayload(UUID.randomUUID(), "main", image(attachment.reference()),
                        true, List.of(), List.of(attachment));
                try {
                    assertInstanceOf(ToolResult.Success.class, fixture.service.ask(sender, request));
                    fixture.released(request.requestId()).get(5, TimeUnit.SECONDS);
                    assertEquals("image_attachment_failed", fixture.failure.get(5, TimeUnit.SECONDS).code());
                    assertEquals(1, fixture.closes.get());
                    assertEquals(0, fixture.service.activeRequests());
                    assertEquals(0, fixture.events.stream()
                            .filter(event -> event.eventType().startsWith("model_usage_")).count());
                    assertFalse(Files.exists(root.resolve(sender.toString()).resolve(sha256(full))));
                } finally {
                    fixture.service.disconnectAsync(sender).get(5, TimeUnit.SECONDS);
                }
            }
        }
    }

    @Test
    void unsupportedAndUnknownModelsRejectCurrentOrRetainedImagesBeforeStoreReads(@TempDir Path directory) throws Exception {
        byte[] bytes = encodedImage("png", 1, 1);
        var reference = reference(bytes, "image/png", 1, 1);
        UUID sender = UUID.randomUUID();
        for (var capability : List.of(ImageInputCapability.UNKNOWN, ImageInputCapability.UNSUPPORTED)) {
            var store = new DelegatingStore(new FileImageAttachmentStore(directory.resolve(capability.name()))) {
                @Override public ImageReference importImage(UUID actor, String owner, byte[] data) {
                    throw new AssertionError("Unsupported input cannot import bytes");
                }
                @Override public byte[] read(UUID actor, ImageReference image) {
                    throw new AssertionError("Unsupported input cannot read bytes");
                }
            };
            var fixture = fixture(store, capability,
                    (request, events, cancellation) -> { throw new AssertionError("No model dispatch expected"); });
            try {
                var current = assertInstanceOf(ToolResult.Failure.class,
                        fixture.service.ask(sender, payload(reference, bytes)));
                assertEquals(capability == ImageInputCapability.UNKNOWN
                        ? "image_input_unknown" : "image_input_unsupported", current.code());
                fixture.sessions.hydrate(new AgentSessionKey(sender, "main"), List.of(image(reference)));
                var retained = assertInstanceOf(ToolResult.Failure.class,
                        fixture.service.ask(sender, new ServerAgentRequestPayload(
                                UUID.randomUUID(), "main", "follow up", true)));
                assertEquals(current.code(), retained.code());
                assertEquals(0, fixture.service.activeRequests());
                assertEquals(0, fixture.closes.get(), "preflight does not create a runtime");
                assertTrue(fixture.events.isEmpty());
            } finally {
                fixture.service.disconnectAsync(sender).get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void reusedRequestIdWaitsForThePreviousActorScopedPinRelease(@TempDir Path directory) throws Exception {
        cancelDuringImportWaitsForCleanupAndRevokesLateImageDispatch(directory.resolve("cancel-import"));
        disconnectDuringImportCannotPublishOrRetainLateActorBytes(directory.resolve("disconnect-import"));
        cancelledImageRequestWaitsForActualNumericReceiptBeforeRelease(directory.resolve("receipt"));
        byte[] bytes = encodedImage("png", 1, 1);
        var reference = reference(bytes, "image/png", 1, 1);
        UUID sender = UUID.randomUUID();
        Path root = directory.resolve("images");
        var store = new BlockingReleaseStore(new FileImageAttachmentStore(root));
        var observed = new CompletableFuture<ModelRequest>();
        var fixture = fixture(store, ImageInputCapability.SUPPORTED, (request, events, cancellation) -> {
            observed.complete(request);
            return new CompletableFuture<>();
        });
        var request = payload(reference, bytes);
        CompletableFuture<Integer> detached = null;
        try {
            assertInstanceOf(ToolResult.Success.class, fixture.service.ask(sender, request));
            observed.get(5, TimeUnit.SECONDS);
            detached = fixture.service.disconnectAsync(sender);
            assertTrue(store.releasing.await(5, TimeUnit.SECONDS));
            assertFalse(detached.isDone(), "disconnect must await actual actor-scoped cleanup");
            assertTrue(fixture.service.hasRequest(sender, request.requestId()));
            assertFalse(fixture.service.hasRequest(UUID.randomUUID(), request.requestId()));
            assertEquals("duplicate_request", assertInstanceOf(ToolResult.Failure.class,
                    fixture.service.ask(sender, request)).code());
            assertEquals("duplicate_request", assertInstanceOf(ToolResult.Failure.class,
                    fixture.service.ask(UUID.randomUUID(), request)).code());
            store.continueRelease.countDown();
            assertEquals(1, detached.get(5, TimeUnit.SECONDS));
            assertTrue(store.collected.await(5, TimeUnit.SECONDS));
            assertFalse(fixture.service.hasRequest(sender, request.requestId()));
            assertFalse(Files.exists(root.resolve(sender.toString()).resolve(reference.sha256())));
            assertInstanceOf(ToolResult.Success.class, fixture.service.ask(sender, request));
        } finally {
            store.continueRelease.countDown();
            if (detached != null) detached.get(5, TimeUnit.SECONDS);
            fixture.service.disconnectAsync(sender).get(5, TimeUnit.SECONDS);
        }
        assertEquals(2, fixture.closes.get());
        assertFalse(Files.exists(root.resolve(sender.toString()).resolve(reference.sha256())));
    }

    private void cancelDuringImportWaitsForCleanupAndRevokesLateImageDispatch(@TempDir Path directory) throws Exception {
        byte[] bytes = encodedImage("png", 1, 1);
        var reference = reference(bytes, "image/png", 1, 1);
        UUID sender = UUID.randomUUID();
        Path root = directory.resolve("images");
        var store = new BlockingImportStore(new FileImageAttachmentStore(root));
        var fixture = fixture(store, ImageInputCapability.SUPPORTED,
                (request, events, cancellation) -> { throw new AssertionError("Cancelled upload must not dispatch"); });
        var request = payload(reference, bytes);
        Thread caller = Thread.currentThread();
        try {
            assertInstanceOf(ToolResult.Success.class, fixture.service.ask(sender, request));
            assertTrue(store.importing.await(5, TimeUnit.SECONDS));
            assertTrue(store.importThread.get(5, TimeUnit.SECONDS) != caller);
            assertTrue(fixture.service.cancel(sender, request.requestId()));
            assertFalse(fixture.released(request.requestId()).isDone());
            assertEquals(0, fixture.closes.get());
            assertEquals("duplicate_request", assertInstanceOf(ToolResult.Failure.class,
                    fixture.service.ask(sender, request)).code());
            store.continueImport.countDown();
            fixture.released(request.requestId()).get(5, TimeUnit.SECONDS);
            assertTrue(store.collected.await(5, TimeUnit.SECONDS));
            assertEquals("agent_cancelled", fixture.failure.get(5, TimeUnit.SECONDS).code());
            assertEquals(1, fixture.closes.get());
            assertEquals(0, fixture.service.activeRequests());
            assertEquals(0, fixture.events.stream().filter(event -> event.eventType().startsWith("model_usage_")).count());
            assertEquals("request_released", fixture.events.getLast().eventType());
            assertFalse(Files.exists(root.resolve(sender.toString()).resolve(reference.sha256())));
            assertThrows(IOException.class, () -> store.read(sender, reference));
        } finally {
            store.continueImport.countDown();
            fixture.service.disconnectAsync(sender).get(5, TimeUnit.SECONDS);
        }
    }

    private void disconnectDuringImportCannotPublishOrRetainLateActorBytes(@TempDir Path directory) throws Exception {
        byte[] bytes = encodedImage("jpeg", 2, 1);
        var reference = reference(bytes, "image/jpeg", 2, 1);
        UUID sender = UUID.randomUUID();
        Path root = directory.resolve("images");
        var store = new BlockingImportStore(new FileImageAttachmentStore(root));
        var fixture = fixture(store, ImageInputCapability.SUPPORTED,
                (request, events, cancellation) -> { throw new AssertionError("Detached upload must not dispatch"); });
        var request = payload(reference, bytes);
        CompletableFuture<Integer> detached = null;
        try {
            assertInstanceOf(ToolResult.Success.class, fixture.service.ask(sender, request));
            assertTrue(store.importing.await(5, TimeUnit.SECONDS));
            detached = fixture.service.disconnectAsync(sender);
            assertFalse(detached.isDone());
            assertEquals(0, fixture.closes.get());
            assertEquals("duplicate_request", assertInstanceOf(ToolResult.Failure.class,
                    fixture.service.ask(sender, request)).code());
            store.continueImport.countDown();
            assertEquals(1, detached.get(5, TimeUnit.SECONDS));
            assertTrue(store.collected.await(5, TimeUnit.SECONDS));
            assertEquals(1, fixture.closes.get());
            assertFalse(fixture.service.hasRequest(sender, request.requestId()));
            assertTrue(fixture.events.isEmpty(), "detached requests must not publish late events");
            assertFalse(Files.exists(root.resolve(sender.toString()).resolve(reference.sha256())));
            assertThrows(IOException.class, () -> store.read(sender, reference));
        } finally {
            store.continueImport.countDown();
            if (detached != null) detached.get(5, TimeUnit.SECONDS);
            fixture.service.disconnectAsync(sender).get(5, TimeUnit.SECONDS);
        }
    }

    private void cancelledImageRequestWaitsForActualNumericReceiptBeforeRelease(@TempDir Path directory) throws Exception {
        byte[] bytes = encodedImage("png", 1, 1);
        var reference = reference(bytes, "image/png", 1, 1);
        UUID sender = UUID.randomUUID();
        Path root = directory.resolve("images");
        var observed = new CompletableFuture<ModelRequest>();
        var raw = new CompletableFuture<ModelTurn>();
        var queuedReceipt = new CompletableFuture<Runnable>();
        ModelUsage partial = new ModelUsage(17, 0, 3, 0, 14, true, false, true, true, true);
        ModelClient provider = (request, events, cancellation) -> {
            observed.complete(request);
            events.accept(new ModelEvent.UsageUpdate(partial));
            return raw;
        };
        ModelClient actualObserver = ObservingModelClient.observe(provider, "test/model");
        ModelClient delayedReceipt = new ModelClient() {
            @Override public boolean observesUsage() { return true; }
            @Override public CompletableFuture<ModelTurn> complete(ModelRequest request,
                    Consumer<ModelEvent> events, CancellationSignal cancellation) {
                return actualObserver.complete(request, event -> {
                    if (event instanceof ModelEvent.UsageObserved) {
                        queuedReceipt.complete(() -> events.accept(event));
                    } else events.accept(event);
                }, cancellation);
            }
        };
        var fixture = fixture(new FileImageAttachmentStore(root), ImageInputCapability.SUPPORTED, delayedReceipt);
        var request = payload(reference, bytes);
        try {
            assertInstanceOf(ToolResult.Success.class, fixture.service.ask(sender, request));
            ModelRequest captured = observed.get(5, TimeUnit.SECONDS);
            assertArrayEquals(bytes, captured.images().read(reference));
            assertTrue(fixture.service.cancel(sender, request.requestId()));
            assertEquals("agent_cancelled", fixture.failure.get(5, TimeUnit.SECONDS).code());
            Runnable deliverReceipt = queuedReceipt.get(5, TimeUnit.SECONDS);
            assertFalse(fixture.released(request.requestId()).isDone());
            assertEquals(0, fixture.closes.get());
            assertTrue(fixture.service.hasRequest(sender, request.requestId()));
            assertEquals(1, fixture.events.stream().filter(ServerAgentEventPayload::terminal).count());
            assertEquals(0, fixture.events.stream()
                    .filter(event -> event.eventType().equals("model_usage_observed")).count());
            raw.complete(turn("late provider answer"));
            deliverReceipt.run();
            fixture.released(request.requestId()).get(5, TimeUnit.SECONDS);
            var receipts = fixture.events.stream().filter(event -> event.eventType().equals("model_usage_observed"))
                    .map(fixture::decode).map(AgentEvent.ModelUsageObserved.class::cast).toList();
            assertEquals(1, receipts.size());
            assertEquals(partial, receipts.getFirst().usage());
            assertEquals("test/model", receipts.getFirst().modelIdentifier());
            var started = fixture.events.stream().filter(event -> event.eventType().equals("model_usage_started"))
                    .map(fixture::decode).map(AgentEvent.ModelUsageStarted.class::cast).toList();
            assertEquals(1, started.size());
            assertEquals(started.getFirst().callId(), receipts.getFirst().callId());
            assertEquals(1, fixture.closes.get());
            assertEquals(0, fixture.service.activeRequests());
            assertEquals("request_released", fixture.events.getLast().eventType());
            assertEquals(1, fixture.events.stream().filter(ServerAgentEventPayload::terminal).count());
            assertFalse(fixture.events.stream().anyMatch(event -> event.eventJson().contains("late provider answer")));
            assertThrows(IOException.class, () -> captured.images().read(reference));
        } finally {
            queuedReceipt.thenAccept(Runnable::run);
            raw.complete(turn("cleanup"));
            fixture.service.disconnectAsync(sender).get(5, TimeUnit.SECONDS);
        }
        assertFalse(Files.exists(root.resolve(sender.toString()).resolve(reference.sha256())));
    }

    @Test
    void clientResultImportPinsAndAllowsImagesOnlyAfterWorkerAcceptance(@TempDir Path directory) throws Exception {
        byte[] bytes = encodedImage("png", 2, 1);
        ImageReference ref = reference(bytes, "image/png", 2, 1);
        BlockingImportStore store = new BlockingImportStore(new FileImageAttachmentStore(directory));
        UUID actor = UUID.randomUUID();
        CompletableFuture<ModelRequest> observed = new CompletableFuture<>();
        CompletableFuture<ModelTurn> response = new CompletableFuture<>();
        Fixture fixture = fixture(store, ImageInputCapability.SUPPORTED, (request, events, cancellation) -> {
            observed.complete(request); return response;
        });
        ServerAgentRequestPayload payload = new ServerAgentRequestPayload(UUID.randomUUID(), "main", "observe", true);
        try {
            assertInstanceOf(ToolResult.Success.class, fixture.service.ask(actor, payload));
            ModelRequest request = observed.get(5, TimeUnit.SECONDS);
            assertThrows(IOException.class, () -> request.images().read(ref));
            CompletableFuture<Void> prepared = fixture.service.prepareClientToolImages(actor, payload.requestId(), "main",
                    List.of(ref), List.of(ServerAgentImageAttachment.from(ref, bytes)), () -> true);
            assertTrue(store.importing.await(5, TimeUnit.SECONDS));
            assertFalse(prepared.isDone());
            assertFalse(store.importThread.get(5, TimeUnit.SECONDS) == Thread.currentThread());
            assertThrows(IOException.class, () -> request.images().read(ref));
            store.continueImport.countDown();
            prepared.get(5, TimeUnit.SECONDS);
            assertArrayEquals(bytes, request.images().read(ref));
            assertEquals(0, store.collect(actor), "Request owner must pin pixels before next model turn");
            assertThrows(java.util.concurrent.CompletionException.class, () -> fixture.service.prepareClientToolImages(
                    UUID.randomUUID(), payload.requestId(), "main", List.of(ref),
                    List.of(ServerAgentImageAttachment.from(ref, bytes)), () -> true).join());
            assertThrows(java.util.concurrent.CompletionException.class, () -> fixture.service.prepareClientToolImages(
                    actor, payload.requestId(), "other", List.of(ref),
                    List.of(ServerAgentImageAttachment.from(ref, bytes)), () -> true).join());
        } finally {
            store.continueImport.countDown(); response.complete(turn("done"));
            fixture.released(payload.requestId()).get(5, TimeUnit.SECONDS);
            fixture.service.disconnectAsync(actor).get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void revokedClientInvocationDuringImportNeverGrantsLateResolverAccess(@TempDir Path directory) throws Exception {
        byte[] bytes = encodedImage("png", 1, 1);
        ImageReference ref = reference(bytes, "image/png", 1, 1);
        BlockingImportStore store = new BlockingImportStore(new FileImageAttachmentStore(directory));
        UUID actor = UUID.randomUUID();
        CompletableFuture<ModelRequest> observed = new CompletableFuture<>();
        CompletableFuture<ModelTurn> response = new CompletableFuture<>();
        Fixture fixture = fixture(store, ImageInputCapability.SUPPORTED, (request, events, cancellation) -> {
            observed.complete(request); return response;
        });
        ServerAgentRequestPayload payload = new ServerAgentRequestPayload(UUID.randomUUID(), "main", "observe", true);
        AtomicBoolean current = new AtomicBoolean(true);
        try {
            fixture.service.ask(actor, payload);
            ModelRequest request = observed.get(5, TimeUnit.SECONDS);
            CompletableFuture<Void> prepared = fixture.service.prepareClientToolImages(actor, payload.requestId(), "main",
                    List.of(ref), List.of(ServerAgentImageAttachment.from(ref, bytes)), current::get);
            assertTrue(store.importing.await(5, TimeUnit.SECONDS));
            current.set(false);
            store.continueImport.countDown();
            assertThrows(java.util.concurrent.ExecutionException.class, () -> prepared.get(5, TimeUnit.SECONDS));
            assertThrows(IOException.class, () -> request.images().read(ref));
        } finally {
            store.continueImport.countDown(); response.complete(turn("done"));
            fixture.released(payload.requestId()).get(5, TimeUnit.SECONDS);
            fixture.service.disconnectAsync(actor).get(5, TimeUnit.SECONDS);
        }
        assertThrows(IOException.class, () -> store.read(actor, ref));
    }

    private static Fixture fixture(ImageAttachmentStore store, ImageInputCapability capability, ModelClient model) {
        return new Fixture(store, capability, model);
    }

    private static ServerAgentRequestPayload payload(ImageReference reference, byte[] bytes) {
        return new ServerAgentRequestPayload(UUID.randomUUID(), "main", image(reference), true,
                List.of(), List.of(ServerAgentImageAttachment.from(reference, bytes)));
    }

    private static ModelMessage image(ImageReference reference) {
        return new ModelMessage(ModelRole.USER, List.of(new ModelContent.Image(reference)));
    }

    private static ImageReference reference(byte[] bytes, String mime, int width, int height) {
        return new ImageReference(sha256(bytes), mime, width, height, bytes.length);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
    }

    private static byte[] encodedImage(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height,
                format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            assertTrue(ImageIO.write(image, format, output));
            return output.toByteArray();
        } finally {
            image.flush();
        }
    }

    private static ModelTurn turn(String text) {
        return new ModelTurn("test", "test/model", List.of(new ModelContent.Text(text)), "end_turn", USAGE);
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IOException("image operation timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException(interrupted);
        }
    }

    private static final class Fixture {
        private final Gson gson = dev.openallay.json.EngineJson.create();
        private final AgentSessionStore sessions = new AgentSessionStore();
        private final List<ServerAgentEventPayload> events = new CopyOnWriteArrayList<>();
        private final Map<UUID, CompletableFuture<Void>> releases = new ConcurrentHashMap<>();
        private final CompletableFuture<AgentEvent.Failed> failure = new CompletableFuture<>();
        private final AtomicInteger closes = new AtomicInteger();
        private final ServerAgentService service;

        private Fixture(ImageAttachmentStore store, ImageInputCapability capability, ModelClient model) {
            AgentToolExecutor tools = new EmptyTools();
            service = new ServerAgentService(
                    (actor, payload) -> new ToolResult.Success<>(new ServerAgentService.RequestRuntime(
                            new GameGuideAgent(model, tools, sessions, gson), tools, "system", closes::incrementAndGet)),
                    sessions,
                    (actor, capabilities, id, cancellation) -> CompletableFuture.completedFuture(
                            ToolInvocationContext.developmentConsole(id)),
                    new ServerGuideEvents() {
                        @Override public void send(UUID actor, ServerAgentEventPayload event) {
                            events.add(event);
                            if (event.eventType().equals("failed")) {
                                failure.complete((AgentEvent.Failed) decode(event));
                            }
                            if (event.eventType().equals("request_released")) {
                                assertPendingReleaseCorrelation(actor, event.requestId());
                            }
                        }
                        @Override public void send(UUID actor, ServerAgentEventPayload event, Runnable retired) {
                            try {
                                ServerGuideEvents.super.send(actor, event, retired);
                                if (event.eventType().equals("request_released")) {
                                    assertReleasedCorrelation(actor, event.requestId());
                                    released(event.requestId()).complete(null);
                                }
                            } catch (RuntimeException | Error invalid) {
                                released(event.requestId()).completeExceptionally(invalid);
                                throw invalid;
                            }
                        }
                    }, gson, "system", cancellation -> CompletableFuture.completedFuture(null), store, capability);
        }

        private void assertPendingReleaseCorrelation(UUID actor, UUID requestId) {
            assertTrue(service.hasRequest(actor, requestId),
                    "The release handoff retains actor-scoped custody until retirement");
            assertFalse(service.ownsRequest(actor, requestId),
                    "Pending release cannot grant active request authority");
            assertFalse(service.hasRequest(UUID.randomUUID(), requestId),
                    "Pending release cannot grant another actor correlation access");
        }

        private void assertReleasedCorrelation(UUID actor, UUID requestId) {
            assertFalse(service.hasRequest(actor, requestId),
                    "Retirement must remove the actor-scoped request correlation");
        }

        private CompletableFuture<Void> released(UUID requestId) {
            return releases.computeIfAbsent(requestId, ignored -> new CompletableFuture<>());
        }

        private AgentEvent decode(ServerAgentEventPayload event) {
            return new ServerAgentEventCodec(gson).decode(event, event.requestId());
        }
    }

    private static class DelegatingStore implements ImageAttachmentStore {
        protected final FileImageAttachmentStore delegate;
        protected final CountDownLatch collected = new CountDownLatch(1);
        private DelegatingStore(FileImageAttachmentStore delegate) { this.delegate = delegate; }
        @Override public ImageInputLimits limits() { return delegate.limits(); }
        @Override public ImageReference importImage(UUID actor, byte[] bytes) throws IOException {
            return delegate.importImage(actor, bytes);
        }
        @Override public ImageReference importImage(UUID actor, String owner, byte[] bytes) throws IOException {
            return delegate.importImage(actor, owner, bytes);
        }
        @Override public byte[] read(UUID actor, ImageReference reference) throws IOException {
            return delegate.read(actor, reference);
        }
        @Override public void retain(UUID actor, String owner, List<ImageReference> references) throws IOException {
            delegate.retain(actor, owner, references);
        }
        @Override public void reconcile(UUID actor, String namespace, Map<String, List<ImageReference>> owners)
                throws IOException {
            delegate.reconcile(actor, namespace, owners);
        }
        @Override public void release(UUID actor, String owner) throws IOException { delegate.release(actor, owner); }
        @Override public int collect(UUID actor) throws IOException {
            int removed = delegate.collect(actor);
            collected.countDown();
            return removed;
        }
    }

    private static final class BlockingReleaseStore extends DelegatingStore {
        private final CountDownLatch releasing = new CountDownLatch(1);
        private final CountDownLatch continueRelease = new CountDownLatch(1);
        private final AtomicBoolean blocked = new AtomicBoolean();
        private BlockingReleaseStore(FileImageAttachmentStore delegate) { super(delegate); }
        @Override public void release(UUID actor, String owner) throws IOException {
            if (owner.startsWith("server-request:") && blocked.compareAndSet(false, true)) {
                releasing.countDown();
                await(continueRelease);
            }
            super.release(actor, owner);
        }
    }

    private static final class BlockingImportStore extends DelegatingStore {
        private final CountDownLatch importing = new CountDownLatch(1);
        private final CountDownLatch continueImport = new CountDownLatch(1);
        private final CompletableFuture<Thread> importThread = new CompletableFuture<>();
        private BlockingImportStore(FileImageAttachmentStore delegate) { super(delegate); }
        @Override public ImageReference importImage(UUID actor, String owner, byte[] bytes) throws IOException {
            importThread.complete(Thread.currentThread());
            importing.countDown();
            await(continueImport);
            return super.importImage(actor, owner, bytes);
        }
    }

    private static final class EmptyTools implements AgentToolExecutor {
        @Override public List<ModelToolDefinition> definitions() { return List.of(); }
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override public CompletableFuture<AgentToolResult> execute(String name, com.google.gson.JsonObject input,
                ToolInvocationContext context, CancellationSignal cancellation) {
            return CompletableFuture.failedFuture(new AssertionError("No tool expected"));
        }
    }
}
