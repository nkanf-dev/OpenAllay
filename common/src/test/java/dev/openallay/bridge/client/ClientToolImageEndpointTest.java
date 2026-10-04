package dev.openallay.bridge.client;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import dev.openallay.agent.tool.ModelImageToolOutput;
import dev.openallay.agent.tool.ToolRuntimeCatalog;
import dev.openallay.bridge.protocol.*;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.image.*;
import dev.openallay.tool.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientToolImageEndpointTest {
    @Test
    void resultWaitsForRootCustodyAndSendsActualBinaryThenCloseIsExactlyOnce(@TempDir Path directory) throws Exception {
        Fixture fixture = new Fixture(directory);
        CompletableFuture<List<ServerAgentImageAttachment>> custody = new CompletableFuture<>();
        AtomicInteger closes = new AtomicInteger();
        fixture.endpoint.configureResultImages(new ClientToolExecutionEndpoint.ResultImages() {
            public CompletableFuture<List<ServerAgentImageAttachment>> prepare(UUID request, UUID invocation,
                    String session, List<ImageReference> refs, CancellationSignal cancellation) {
                assertEquals(fixture.request, request); assertEquals("main", session);
                assertEquals(List.of(fixture.ref), refs); return custody;
            }
            public void close(UUID request) { assertEquals(fixture.request, request); closes.incrementAndGet(); }
        });
        fixture.open(); fixture.invoke();
        assertTrue(fixture.sent.isEmpty());
        custody.complete(List.of(ServerAgentImageAttachment.from(fixture.ref, fixture.bytes)));
        ToolExecutionMessage message = fixture.message();
        assertArrayEquals(fixture.bytes, message.imageAttachments().getFirst().bytes());
        assertEquals(List.of(fixture.ref), new Gson().fromJson(message.result().get("value"), Output.class).images());
        assertTrue(fixture.endpoint.close(fixture.request));
        assertFalse(fixture.endpoint.close(fixture.request));
        assertEquals(1, closes.get());
    }

    @Test
    void cancellationAndDisconnectSuppressLatePreparedImages(@TempDir Path directory) throws Exception {
        for (boolean disconnect : List.of(false, true)) {
            Fixture fixture = new Fixture(directory.resolve(Boolean.toString(disconnect)));
            CompletableFuture<List<ServerAgentImageAttachment>> custody = new CompletableFuture<>();
            fixture.endpoint.configureResultImages((request, invocation, session, refs, cancellation) -> custody);
            fixture.open(); fixture.invoke();
            if (disconnect) fixture.endpoint.disconnect();
            else assertTrue(fixture.endpoint.cancel(new ClientToolCancelPayload(fixture.request, fixture.invocation)));
            custody.complete(List.of(ServerAgentImageAttachment.from(fixture.ref, fixture.bytes)));
            assertTrue(fixture.sent.isEmpty());
        }
    }

    @Test
    void missingCustodyFailsInsteadOfSendingAnImageDescriptorWithoutPixels(@TempDir Path directory) throws Exception {
        Fixture fixture = new Fixture(directory); fixture.open(); fixture.invoke();
        ToolExecutionMessage message = fixture.message();
        assertEquals("failure", message.result().get("status").getAsString());
        assertEquals("client_tool_image_failed", message.result().get("code").getAsString());
        assertTrue(message.imageAttachments().isEmpty());
        fixture.endpoint.disconnect();
    }

    record Input() {}
    record Output(List<ImageReference> images) implements ModelImageToolOutput {}
    private static final class Fixture {
        final UUID actor = UUID.randomUUID();
        final UUID request = UUID.randomUUID();
        final UUID invocation = UUID.randomUUID();
        final byte[] bytes;
        final ImageReference ref;
        final List<ClientToolResultChunkPayload> sent = new ArrayList<>();
        final ToolRegistry tools = new ToolRegistry();
        final ClientToolExecutionEndpoint endpoint;
        Fixture(Path directory) throws Exception {
            var png = new java.awt.image.BufferedImage(2, 3, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var output = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(png, "png", output);
            bytes = output.toByteArray(); ref = new FileImageAttachmentStore(directory).importImage(actor, "producer", bytes);
            tools.register("test", List.of(new Tool<Input, Output>() {
                public ToolDescriptor<Input, Output> descriptor() {
                    return new ToolDescriptor<>("test:visual", "Typed visual", Input.class, Output.class, ToolAccess.READ_ONLY);
                }
                public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
                    return new ToolResult.Success<>(new Output(List.of(ref)));
                }
            }));
            endpoint = new ClientToolExecutionEndpoint((required, correlation, cancellation) ->
                    CompletableFuture.completedFuture(ToolInvocationContext.developmentConsole(correlation)),
                    sent::add, new Gson(), 97, Runnable::run);
        }
        void open() { endpoint.open(request, "main", ToolRuntimeCatalog.from(tools.registrations(), Set.of())); }
        void invoke() { endpoint.handle(new ClientToolCallPayload(request, invocation, "main", "test:visual", "{}")); }
        ToolExecutionMessage message() {
            ResultChunker.Reassembler reassembler = new ResultChunker.Reassembler();
            String complete = null;
            for (var chunk : sent) {
                var accepted = reassembler.accept(chunk.asRemoteChunk()); if (accepted.isPresent()) complete = accepted.get();
            }
            return new BridgeJsonCodec().decode(Objects.requireNonNull(complete), ToolExecutionMessage.class);
        }
    }
}
