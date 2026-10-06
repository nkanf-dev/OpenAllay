package dev.openallay.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.bridge.protocol.RemoteCancelPayload;
import dev.openallay.bridge.protocol.RemoteToolCallPayload;
import dev.openallay.bridge.protocol.RemoteToolResultChunkPayload;
import dev.openallay.bridge.protocol.RemoteToolRequestClosePayload;
import dev.openallay.bridge.protocol.ResultChunker;
import dev.openallay.bridge.server.ExportedToolPolicy;
import dev.openallay.bridge.server.RemoteToolServer;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.ToolDescriptor;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.RequestScopeParticipant;
import dev.openallay.tool.builtin.RunJavascriptTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class RemoteToolBridgeTest {
    @Test
    void derivesIdentityFromSenderAndSuppressesCancelledLateResult() {
        ToolRegistry tools = new ToolRegistry();
        tools.register("test", List.of(new FactTool()));
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        UUID correlation = UUID.randomUUID();
        CompletableFuture<ToolInvocationContext> context = new CompletableFuture<>();
        List<RemoteToolResultChunkPayload> sent = new ArrayList<>();
        RemoteToolServer server = new RemoteToolServer(
                new ExportedToolPolicy(tools, Set.of("test:fact")),
                (actor, capabilities, id, cancellation) -> context,
                (actor, chunk) -> sent.add(chunk),
                new CorrelationRegistry(),
                dev.openallay.json.EngineJson.create(),
                5);
        RemoteToolCallPayload call = new RemoteToolCallPayload(
                correlation, "main", "test:fact", "{\"value\":7}");

        assertInstanceOf(ToolResult.Success.class, server.handle(owner, call));
        assertFalse(server.cancel(attacker, new RemoteCancelPayload(correlation)));
        assertTrue(server.cancel(owner, new RemoteCancelPayload(correlation)));
        context.complete(ToolInvocationContext.developmentConsole(correlation.toString()));
        assertTrue(sent.isEmpty());
    }

    @Test
    void sendsCompleteNormalizedResultOnlyToOwningConnection() {
        ToolRegistry tools = new ToolRegistry();
        tools.register("test", List.of(new FactTool()));
        UUID owner = UUID.randomUUID();
        List<UUID> recipients = new ArrayList<>();
        List<RemoteToolResultChunkPayload> sent = new ArrayList<>();
        RemoteToolServer server = new RemoteToolServer(
                new ExportedToolPolicy(tools, Set.of("test:fact")),
                (actor, capabilities, id, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(id)),
                (actor, chunk) -> { recipients.add(actor); sent.add(chunk); },
                new CorrelationRegistry(), dev.openallay.json.EngineJson.create(), 3);
        UUID correlation = UUID.randomUUID();
        server.handle(owner, new RemoteToolCallPayload(
                correlation, "main", "test:fact", "{\"value\":42}"));

        assertFalse(sent.isEmpty());
        assertTrue(recipients.stream().allMatch(owner::equals));
        ResultChunker.Reassembler reassembler = new ResultChunker.Reassembler();
        String result = null;
        for (RemoteToolResultChunkPayload chunk : sent) {
            var complete = reassembler.accept(chunk);
            if (complete.isPresent()) result = complete.orElseThrow();
        }
        assertTrue(result.contains("42"));
    }

    @Test
    void explicitServerJavascriptInvocationAwaitsRootFreeResultAndClosesOnlyForItsOwner() {
        ToolRegistry tools = new ToolRegistry();
        AsyncJavascriptTool javascript = new AsyncJavascriptTool();
        tools.register("test", List.of(javascript));
        UUID owner = UUID.randomUUID();
        List<RemoteToolResultChunkPayload> sent = new ArrayList<>();
        RemoteToolServer server = new RemoteToolServer(
                new ExportedToolPolicy(tools, Set.of(RunJavascriptTool.ID)),
                (actor, capabilities, id, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(id)),
                (actor, chunk) -> sent.add(chunk),
                new CorrelationRegistry(),
                dev.openallay.json.EngineJson.create(),
                128);
        UUID correlation = UUID.randomUUID();
        RunJavascriptTool.Input input = new RunJavascriptTool.Input(
                "return world.inspect({});", List.of(), "Inspect nearby blocks", "Read the server world");

        assertInstanceOf(ToolResult.Success.class, server.handle(owner, new RemoteToolCallPayload(
                correlation,
                "main",
                RunJavascriptTool.ID,
                dev.openallay.json.EngineJson.create().toJson(input))));
        assertEquals(input, javascript.input);
        assertTrue(sent.isEmpty());

        javascript.pending.complete(new ToolResult.Success<>(
                new AsyncJavascriptTool.Output("server-authoritative")));
        assertFalse(sent.isEmpty());
        assertTrue(sent.stream().allMatch(chunk -> chunk.correlationId().equals(correlation)));
        assertTrue(reassemble(sent).contains("server-authoritative"));
        assertNull(javascript.closedCorrelation);

        server.closeRequest(
                UUID.randomUUID(),
                new RemoteToolRequestClosePayload("main"));
        assertNull(javascript.closedCorrelation);
        server.closeRequest(
                owner,
                new RemoteToolRequestClosePayload("main"));
        assertEquals(owner + "/main", javascript.closedCorrelation);
    }

    private static final class FactTool implements Tool<FactTool.Input, FactTool.Output> {
        record Input(int value) {}
        record Output(int value) {}
        private static final ToolDescriptor<Input, Output> DESCRIPTOR = new ToolDescriptor<>(
                "test:fact", "Return fact", Input.class, Output.class, ToolAccess.READ_ONLY);
        @Override public ToolDescriptor<Input, Output> descriptor() { return DESCRIPTOR; }
        @Override public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
            return new ToolResult.Success<>(new Output(input.value()));
        }
    }

    private static final class AsyncJavascriptTool
            implements Tool<RunJavascriptTool.Input, AsyncJavascriptTool.Output>,
                    RequestScopeParticipant {
        record Output(String route) {}

        private static final ToolDescriptor<RunJavascriptTool.Input, Output> DESCRIPTOR = new ToolDescriptor<>(
                RunJavascriptTool.ID,
                "Run detached JavaScript",
                RunJavascriptTool.Input.class,
                Output.class,
                ToolAccess.EXPERIMENTAL_ACTION);
        private final CompletableFuture<ToolResult<Output>> pending = new CompletableFuture<>();
        private RunJavascriptTool.Input input;
        private String closedCorrelation;

        @Override
        public ToolDescriptor<RunJavascriptTool.Input, Output> descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public ToolResult<Output> invoke(ToolInvocationContext context, RunJavascriptTool.Input input) {
            throw new UnsupportedOperationException("async");
        }

        @Override
        public CompletableFuture<ToolResult<Output>> invokeAsync(
                ToolInvocationContext context,
                RunJavascriptTool.Input input,
                CancellationSignal cancellation) {
            this.input = input;
            return pending;
        }

        @Override
        public void closeRequestScope(String correlationId) {
            closedCorrelation = correlationId;
        }
    }

    private static String reassemble(List<RemoteToolResultChunkPayload> chunks) {
        ResultChunker.Reassembler reassembler = new ResultChunker.Reassembler();
        String result = null;
        for (RemoteToolResultChunkPayload chunk : chunks) {
            var complete = reassembler.accept(chunk);
            if (complete.isPresent()) {
                result = complete.orElseThrow();
            }
        }
        return result;
    }
}
