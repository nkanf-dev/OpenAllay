package dev.openallay.bridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.openallay.bridge.CorrelationRegistry;
import dev.openallay.bridge.protocol.BridgeProtocol;
import dev.openallay.bridge.protocol.RemoteToolCallPayload;
import dev.openallay.bridge.protocol.RemoteToolRequestClosePayload;
import dev.openallay.bridge.protocol.RemoteToolResultChunkPayload;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.tool.RequestScopeParticipant;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.ToolDescriptor;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class RemoteToolServerTest {
    @Test
    void requestCloseCancelsPendingContextBeforeClosingToolsAndBlocksLateInvocation() {
        PendingRequest request = new PendingRequest();
        request.start();

        request.server.closeRequest(request.actor, new RemoteToolRequestClosePayload(
                BridgeProtocol.VERSION, "main"));

        request.assertClosedBeforeLateCapture();
        request.server.closeRequest(request.actor, new RemoteToolRequestClosePayload(
                BridgeProtocol.VERSION, "main"));
        assertEquals(List.of(request.scope()), request.tool.closed);
    }

    @Test
    void disconnectCancelsPendingContextBeforeClosingToolsAndBlocksLateInvocation() {
        PendingRequest request = new PendingRequest();
        request.start();

        assertEquals(1, request.server.disconnect(request.actor));

        request.assertClosedBeforeLateCapture();
        assertTrue(request.sent.isEmpty());
        assertEquals(0, request.server.disconnect(request.actor));
        assertEquals(List.of(request.scope()), request.tool.closed);
    }

    private static final class PendingRequest {
        private final UUID actor = UUID.randomUUID();
        private final CompletableFuture<ToolInvocationContext> context = new CompletableFuture<>();
        private final ScopeTool tool = new ScopeTool();
        private final List<RemoteToolResultChunkPayload> sent = new ArrayList<>();
        private final RemoteToolServer server;
        private CancellationSignal cancellation;

        private PendingRequest() {
            ToolRegistry tools = new ToolRegistry();
            tools.register("test", List.of(tool));
            server = new RemoteToolServer(
                    new ExportedToolPolicy(tools, Set.of("test:scope")),
                    (actorId, capabilities, correlationId, signal) -> {
                        cancellation = signal;
                        signal.onCancel(() -> tool.lifecycle.add("cancel"));
                        return context;
                    },
                    (actorId, chunk) -> sent.add(chunk),
                    new CorrelationRegistry(),
                    new Gson(),
                    128);
        }

        private void start() {
            assertInstanceOf(ToolResult.Success.class, server.handle(actor, new RemoteToolCallPayload(
                    BridgeProtocol.VERSION, UUID.randomUUID(), "main", "test:scope", "{}")));
            assertNotNull(cancellation);
            assertFalse(cancellation.isCancelled());
            assertTrue(tool.lifecycle.isEmpty());
            assertTrue(sent.isEmpty());
        }

        private String scope() {
            return actor + "/main";
        }

        private void assertClosedBeforeLateCapture() {
            assertTrue(cancellation.isCancelled());
            assertEquals(List.of("cancel", "close"), tool.lifecycle);
            assertEquals(List.of(scope()), tool.closed);
            assertTrue(context.complete(ToolInvocationContext.developmentConsole(scope())));
            assertEquals(0, tool.asyncInvocations,
                    "a late context must not reach even a Tool that ignores cancellation");
            assertEquals(0, tool.invocations);
        }
    }

    private static final class ScopeTool
            implements Tool<ScopeTool.Input, ScopeTool.Output>, RequestScopeParticipant {
        record Input() {}
        record Output(String correlationId) {}

        private static final ToolDescriptor<Input, Output> DESCRIPTOR = new ToolDescriptor<>(
                "test:scope", "Test request scope", Input.class, Output.class, ToolAccess.READ_ONLY);
        private final List<String> lifecycle = new ArrayList<>();
        private final List<String> closed = new ArrayList<>();
        private int asyncInvocations;
        private int invocations;

        @Override
        public ToolDescriptor<Input, Output> descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
            invocations++;
            return new ToolResult.Success<>(new Output(context.correlationId()));
        }

        @Override
        public CompletableFuture<ToolResult<Output>> invokeAsync(
                ToolInvocationContext context, Input input, CancellationSignal cancellation) {
            // Deliberately ignore cancellation to verify that the server blocks dispatch itself.
            asyncInvocations++;
            return CompletableFuture.completedFuture(invoke(context, input));
        }

        @Override
        public void closeRequestScope(String correlationId) {
            lifecycle.add("close");
            closed.add(correlationId);
        }
    }
}
