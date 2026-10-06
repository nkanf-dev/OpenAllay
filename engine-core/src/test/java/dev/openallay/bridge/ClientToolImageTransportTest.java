package dev.openallay.bridge;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.agent.tool.ModelImageToolOutput;
import dev.openallay.bridge.protocol.*;
import dev.openallay.bridge.server.PlayerClientToolRouter;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.image.*;
import dev.openallay.tool.*;
import dev.openallay.trace.replay.ToolResultNormalizer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientToolImageTransportTest {
    @Test
    void typedBinaryResultWaitsForAdmissionAndPreservesImageOccurrences(@TempDir Path directory) throws Exception {
        byte[] bytes = png();
        ImageAttachmentStore images = new FileImageAttachmentStore(directory);
        UUID actor = UUID.randomUUID();
        ImageReference ref = images.importImage(actor, "fixture", bytes);
        Fixture fixture = new Fixture(actor);
        CompletableFuture<Void> admitted = new CompletableFuture<>();
        AtomicReference<List<ImageReference>> prepared = new AtomicReference<>();
        fixture.router.configureResultPreparation((actualActor, request, session, refs, attachments, current) -> {
            assertEquals(actor, actualActor);
            assertEquals("main", session);
            assertTrue(current.getAsBoolean());
            assertArrayEquals(bytes, attachments.getFirst().bytes());
            prepared.set(refs);
            return admitted;
        }, BridgeProtocol.MAX_OPENAI_REQUEST_BYTES);
        fixture.open();
        CompletableFuture<AgentToolResult> result = fixture.invoke(new CancellationSignal());
        List<ClientToolResultChunkPayload> chunks = fixture.chunks(List.of(ref, ref),
                List.of(ServerAgentImageAttachment.from(ref, bytes)));
        Collections.reverse(chunks);
        for (var chunk : chunks) assertTrue(fixture.router.receive(actor, chunk));
        assertEquals(List.of(ref, ref), prepared.get());
        assertFalse(result.isDone(), "Future must not complete before import/pin/allowlist admission");
        admitted.complete(null);
        assertEquals(List.of(ref, ref), result.join().images());
        assertArrayEquals(bytes, images.read(actor, result.join().images().getFirst()));
        assertFalse(fixture.router.receive(actor, chunks.getFirst()));
        fixture.close();
    }

    @Test
    void cancelDuringAdmissionRevokesTokenAndSuppressesLateSuccessfulImport(@TempDir Path directory) throws Exception {
        UUID actor = UUID.randomUUID();
        ImageReference ref = new FileImageAttachmentStore(directory).importImage(actor, "fixture", png());
        Fixture fixture = new Fixture(actor);
        CompletableFuture<Void> admitted = new CompletableFuture<>();
        AtomicReference<java.util.function.BooleanSupplier> token = new AtomicReference<>();
        fixture.router.configureResultPreparation((a, r, s, refs, attachments, current) -> {
            token.set(current); return admitted;
        }, BridgeProtocol.MAX_OPENAI_REQUEST_BYTES);
        fixture.open();
        CancellationSignal cancellation = new CancellationSignal();
        var result = fixture.invoke(cancellation);
        var chunks = fixture.chunks(List.of(ref), List.of(ServerAgentImageAttachment.from(ref, png())));
        for (var chunk : chunks) assertTrue(fixture.router.receive(actor, chunk));
        assertTrue(token.get().getAsBoolean());
        cancellation.cancel();
        assertFalse(token.get().getAsBoolean());
        admitted.complete(null);
        assertTrue(result.isCompletedExceptionally());
        assertEquals(0, fixture.router.activeResultAssemblies(actor, fixture.request));
        assertFalse(fixture.router.receive(actor, chunks.getFirst()));
        fixture.close();
    }

    @Test
    void unknownActorRequestInvocationAndRevokedOwnerCannotAllocateAssembly(@TempDir Path directory) throws Exception {
        UUID actor = UUID.randomUUID();
        ImageReference ref = new FileImageAttachmentStore(directory).importImage(actor, "fixture", png());
        Fixture fixture = new Fixture(actor);
        fixture.router.configureResultPreparation((a, r, s, refs, attachments, current) ->
                CompletableFuture.completedFuture(null), BridgeProtocol.MAX_OPENAI_REQUEST_BYTES, (a, r) -> false);
        fixture.open();
        var result = fixture.invoke(new CancellationSignal());
        var chunk = fixture.chunks(List.of(ref), List.of(ServerAgentImageAttachment.from(ref, png()))).getFirst();
        assertFalse(fixture.router.receive(UUID.randomUUID(), chunk));
        assertFalse(fixture.router.receive(actor, new ClientToolResultChunkPayload(UUID.randomUUID(),
                chunk.invocationId(), chunk.index(), chunk.total(), chunk.contentHash(), chunk.base64Data())));
        assertFalse(fixture.router.receive(actor, new ClientToolResultChunkPayload(fixture.request,
                UUID.randomUUID(), chunk.index(), chunk.total(), chunk.contentHash(), chunk.base64Data())));
        assertFalse(fixture.router.receive(actor, chunk));
        assertEquals(0, fixture.router.activeResultAssemblies(actor, fixture.request));
        assertFalse(result.isDone());
        fixture.close();
    }

    @Test
    void missingExtraOrInvalidNativeBytesFailWithoutModelImageReferences(@TempDir Path directory) throws Exception {
        UUID actor = UUID.randomUUID();
        ImageReference ref = new FileImageAttachmentStore(directory).importImage(actor, "fixture", png());
        for (int mode = 0; mode < 3; mode++) {
            Fixture fixture = new Fixture(actor);
            fixture.router.configureResultPreparation((a, r, s, refs, attachments, current) -> {
                try {
                    for (var attachment : attachments) {
                        var imported = new FileImageAttachmentStore(directory.resolve(r.toString()))
                                .importImage(a, "request", attachment.bytes());
                        if (!imported.equals(attachment.reference())) throw new java.io.IOException("Wrong dimensions");
                    }
                    return CompletableFuture.completedFuture(null);
                } catch (Exception invalid) { return CompletableFuture.failedFuture(invalid); }
            }, BridgeProtocol.MAX_OPENAI_REQUEST_BYTES);
            fixture.open();
            var result = fixture.invoke(new CancellationSignal());
            ImageReference wrong = new ImageReference(ref.sha256(), ref.mimeType(), 7, 7, ref.byteSize());
            List<ImageReference> refs = mode == 1 ? List.of() : List.of(mode == 2 ? wrong : ref);
            List<ServerAgentImageAttachment> attachments = mode == 0 ? List.of()
                    : List.of(ServerAgentImageAttachment.from(mode == 2 ? wrong : ref, png()));
            for (var chunk : fixture.chunks(refs, attachments)) assertTrue(fixture.router.receive(actor, chunk));
            assertTrue(result.join().failure());
            assertTrue(result.join().images().isEmpty());
            fixture.close();
        }
    }

    @Test
    void envelopeExactShapeAndChunkLimits() {
        JsonObject failure = new JsonObject();
        failure.addProperty("status", "failure"); failure.addProperty("code", "x"); failure.addProperty("message", "x");
        BridgeJsonCodec codec = new BridgeJsonCodec();
        String json = codec.encode(new ToolExecutionMessage(failure, List.of()));
        assertEquals(failure, codec.decode(json, ToolExecutionMessage.class).result());
        JsonObject explicitNull = new JsonObject();
        explicitNull.addProperty("status", "success");
        explicitNull.add("modelText", com.google.gson.JsonNull.INSTANCE);
        JsonObject nullableValue = new JsonObject();
        nullableValue.add("field", com.google.gson.JsonNull.INSTANCE);
        explicitNull.add("value", nullableValue);
        assertEquals(explicitNull, codec.decode(codec.encode(new ToolExecutionMessage(
                explicitNull, List.of())), ToolExecutionMessage.class).result(),
                "Transport must preserve null fields for exact validation, not erase them");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                json.substring(0, json.length() - 1) + ",\"extra\":true}", ToolExecutionMessage.class));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                "{\"result\":{},\"result\":{},\"imageAttachments\":[]}", ToolExecutionMessage.class));
        assertThrows(IllegalArgumentException.class, () -> new ClientToolResultChunkPayload(UUID.randomUUID(),
                UUID.randomUUID(), 0, BridgeProtocol.MAX_REQUEST_CHUNKS + 1, "0".repeat(64), ""));
        assertThrows(IllegalArgumentException.class, () -> new ClientToolResultChunkPayload(UUID.randomUUID(),
                UUID.randomUUID(), 0, 1, "0".repeat(64), Base64.getEncoder().encodeToString(
                        new byte[BridgeProtocol.TRANSPORT_CHUNK_BYTES + 1])));
        ResultChunker.Reassembler bounded = new ResultChunker.Reassembler(Duration.ofMinutes(1), 5, 3);
        var chunks = new ResultChunker().split(UUID.randomUUID(), "abcdef", 3);
        bounded.accept(chunks.getFirst());
        assertThrows(IllegalArgumentException.class, () -> bounded.accept(chunks.getLast()));
        assertEquals(0, bounded.activeAssemblies());
    }

    private static byte[] png() throws Exception {
        var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x12ab34);
        var output = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(image, "png", output));
        return output.toByteArray();
    }

    private static final class Fixture {
        final UUID actor;
        final UUID request = UUID.randomUUID();
        final AtomicReference<ClientToolCallPayload> call = new AtomicReference<>();
        final PlayerClientToolRouter router;
        AgentToolExecutor tools;
        Fixture(UUID actor) {
            this.actor = actor;
            ToolRegistry registry = new ToolRegistry(); registry.register("test", List.of(new VisualTool()));
            router = new PlayerClientToolRouter(registry, dev.openallay.json.EngineJson.create(), new PlayerClientToolRouter.Transport() {
                public boolean call(UUID a, ClientToolCallPayload payload) { call.set(payload); return true; }
                public void cancel(UUID a, ClientToolCancelPayload payload) {}
            }, Duration.ofMinutes(1), Runnable::run);
        }
        void open() {
            var skills = new dev.openallay.skill.SkillRepository(new dev.openallay.skill.SkillParser(), Set.of()).snapshot(Set.of());
            tools = ((ToolResult.Success<AgentToolExecutor>) router.open(actor, request, "main",
                    List.of("test:visual"), skills)).value();
        }
        CompletableFuture<AgentToolResult> invoke(CancellationSignal cancellation) {
            return tools.execute("test:visual", new JsonObject(),
                    ToolInvocationContext.developmentConsole(request.toString()), cancellation);
        }
        List<ClientToolResultChunkPayload> chunks(List<ImageReference> refs, List<ServerAgentImageAttachment> attachments) {
            JsonObject normalized = new ToolResultNormalizer(dev.openallay.json.EngineJson.create()).normalize(
                    new ToolResult.Success<>(new VisualTool.Output(refs)), VisualTool.Output.class);
            String json = new BridgeJsonCodec().encode(new ToolExecutionMessage(normalized, attachments));
            return new ArrayList<>(new ResultChunker().split(call.get().invocationId(), json, 97).stream()
                    .map(chunk -> ClientToolResultChunkPayload.from(request, chunk)).toList());
        }
        void close() { router.close(actor, request); }
    }

    private static final class VisualTool implements Tool<VisualTool.Input, VisualTool.Output> {
        record Input() {}
        record Output(List<ImageReference> images) implements ModelImageToolOutput {}
        public ToolDescriptor<Input, Output> descriptor() {
            return new ToolDescriptor<>("test:visual", "Synthetic typed visual Tool", Input.class, Output.class, ToolAccess.READ_ONLY);
        }
        public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
            return new ToolResult.Success<>(new Output(List.of()));
        }
    }
}
