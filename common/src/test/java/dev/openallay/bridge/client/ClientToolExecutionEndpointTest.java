package dev.openallay.bridge.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import dev.openallay.agent.tool.ToolRuntimeCatalog;
import dev.openallay.bridge.protocol.ClientToolCallPayload;
import dev.openallay.bridge.protocol.ClientToolCancelPayload;
import dev.openallay.bridge.protocol.ClientToolResultChunkPayload;
import dev.openallay.bridge.protocol.ResultChunker;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.script.command.CommandCapabilityRuntime;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.workspace.AgentResultWorkspaceRegistry;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.ToolDescriptor;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.RequestScopeParticipant;
import dev.openallay.tool.builtin.RunJavascriptTool;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class ClientToolExecutionEndpointTest {
    @Test
    void sharesOneRequestCorrelationAcrossCallsAndClosesRequestScopedTools() {
        ScopeTool tool = new ScopeTool();
        ToolRegistry registry = new ToolRegistry();
        registry.register("test", List.of(tool));
        List<String> captured = new ArrayList<>();
        ClientToolExecutionEndpoint endpoint = new ClientToolExecutionEndpoint(
                (capabilities, correlation, cancellation) -> {
                    captured.add(correlation);
                    return CompletableFuture.completedFuture(
                            ToolInvocationContext.developmentConsole(correlation));
                },
                chunk -> {},
                new Gson(),
                128,
                Runnable::run);
        UUID requestId = UUID.randomUUID();
        endpoint.open(
                requestId, "main",
                ToolRuntimeCatalog.from(registry.registrations(), java.util.Set.of()));

        for (int index = 0; index < 2; index++) {
            endpoint.handle(new ClientToolCallPayload(
                    requestId,
                    UUID.randomUUID(),
                    "main",
                    "test:scope",
                    "{}"));
        }
        endpoint.close(requestId);

        assertEquals(List.of(requestId.toString(), requestId.toString()), captured);
        assertEquals(List.of(requestId.toString()), tool.closed);
    }

    @Test
    void freezesOneRequestCatalogAndReturnsACompleteNormalizedResult() {
        ToolRegistry registry = registry();
        List<ClientToolResultChunkPayload> sent = new ArrayList<>();
        ClientToolExecutionEndpoint endpoint = new ClientToolExecutionEndpoint(
                (capabilities, correlation, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(correlation)),
                sent::add,
                new Gson(),
                4,
                Runnable::run);
        UUID requestId = UUID.randomUUID();
        ToolResult<ClientToolExecutionEndpoint.OpenedRequest> opened = endpoint.open(
                requestId, "main", ToolRuntimeCatalog.from(registry.registrations(), java.util.Set.of()));
        @SuppressWarnings("unchecked")
        ToolResult.Success<ClientToolExecutionEndpoint.OpenedRequest> success =
                (ToolResult.Success<ClientToolExecutionEndpoint.OpenedRequest>)
                        assertInstanceOf(ToolResult.Success.class, opened);
        assertEquals(List.of("test:fact"), success.value().clientToolIds());

        UUID invocation = UUID.randomUUID();
        assertInstanceOf(ToolResult.Success.class, endpoint.handle(new ClientToolCallPayload(
                requestId,
                invocation,
                "main",
                "test:fact",
                "{\"value\":42}")));

        String normalized = reassemble(sent);
        assertEquals(
                42,
                JsonParser.parseString(normalized)
                        .getAsJsonObject()
                        .getAsJsonObject("value")
                        .get("value")
                        .getAsInt());
        assertEquals(requestId, sent.getFirst().requestId());
        assertEquals(invocation, sent.getFirst().invocationId());
    }

    @Test
    void rejectsMismatchedOrClosedRequestsWithStructuredResults() {
        ToolRegistry registry = registry();
        List<ClientToolResultChunkPayload> sent = new ArrayList<>();
        ClientToolExecutionEndpoint endpoint = new ClientToolExecutionEndpoint(
                (capabilities, correlation, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(correlation)),
                sent::add,
                new Gson(),
                128,
                Runnable::run);
        UUID requestId = UUID.randomUUID();
        endpoint.open(
                requestId, "main", ToolRuntimeCatalog.from(registry.registrations(), java.util.Set.of()));

        endpoint.handle(new ClientToolCallPayload(
                requestId,
                UUID.randomUUID(),
                "other",
                "test:fact",
                "{\"value\":1}"));
        assertTrue(reassemble(sent).contains("client_tool_rejected"));
        sent.clear();
        endpoint.close(requestId);
        endpoint.handle(new ClientToolCallPayload(
                requestId,
                UUID.randomUUID(),
                "main",
                "test:fact",
                "{\"value\":1}"));
        assertTrue(reassemble(sent).contains("client_tool_unavailable"));
    }

    @Test
    void cancellationSuppressesLateContextCompletion() {
        FactTool tool = new FactTool();
        ToolRegistry registry = new ToolRegistry();
        registry.register("test", List.of(tool));
        CompletableFuture<ToolInvocationContext> context = new CompletableFuture<>();
        List<ClientToolResultChunkPayload> sent = new ArrayList<>();
        ClientToolExecutionEndpoint endpoint = new ClientToolExecutionEndpoint(
                (capabilities, correlation, cancellation) -> context,
                sent::add,
                new Gson(),
                128,
                Runnable::run);
        UUID requestId = UUID.randomUUID();
        UUID invocationId = UUID.randomUUID();
        endpoint.open(
                requestId, "main", ToolRuntimeCatalog.from(registry.registrations(), java.util.Set.of()));
        endpoint.handle(new ClientToolCallPayload(
                requestId,
                invocationId,
                "main",
                "test:fact",
                "{\"value\":7}"));

        assertTrue(endpoint.cancel(new ClientToolCancelPayload(
                requestId, invocationId)));
        assertTrue(context.complete(ToolInvocationContext.developmentConsole(invocationId.toString())));
        assertEquals(0, tool.asyncInvocations);
        assertEquals(0, tool.invocations);
        assertTrue(sent.isEmpty());
        assertFalse(endpoint.cancel(new ClientToolCancelPayload(
                requestId, invocationId)));
    }

    @Test
    void requestCloseCancelsPendingContextBeforeClosingToolsAndBlocksLateInvocation() {
        PendingScopeRequest request = new PendingScopeRequest();
        request.start();

        assertTrue(request.endpoint.close(request.requestId));

        request.assertClosedBeforeLateCapture();
        assertFalse(request.endpoint.close(request.requestId));
        assertEquals(List.of(request.requestId.toString()), request.tool.closed);
    }

    @Test
    void disconnectCancelsPendingContextBeforeClosingToolsAndBlocksLateInvocation() {
        PendingScopeRequest request = new PendingScopeRequest();
        request.start();

        assertEquals(1, request.endpoint.disconnect());

        request.assertClosedBeforeLateCapture();
        assertEquals(0, request.endpoint.disconnect());
        assertEquals(List.of(request.requestId.toString()), request.tool.closed);
    }

    @Test
    void executesAndEncodesAfterCaptureOnTheDedicatedWorker() throws Exception {
        ToolRegistry registry = registry();
        CompletableFuture<ToolInvocationContext> context = new CompletableFuture<>();
        CompletableFuture<String> responseThread = new CompletableFuture<>();
        try (var worker = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "client-tool-test-worker"))) {
            ClientToolExecutionEndpoint endpoint = new ClientToolExecutionEndpoint(
                    (capabilities, correlation, cancellation) -> context,
                    chunk -> responseThread.complete(Thread.currentThread().getName()),
                    new Gson(),
                    128,
                    worker);
            UUID requestId = UUID.randomUUID();
            UUID invocationId = UUID.randomUUID();
            endpoint.open(requestId, "main", ToolRuntimeCatalog.from(
                    registry.registrations(), java.util.Set.of()));
            endpoint.handle(new ClientToolCallPayload(
                    requestId,
                    invocationId,
                    "main",
                    "test:fact",
                    "{\"value\":7}"));

            context.complete(ToolInvocationContext.developmentConsole(invocationId.toString()));

            assertEquals("client-tool-test-worker", responseThread.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void advertisesExperimentalCommandsOnlyForRequestsFrozenWhileEnabled() {
        CommandCapabilityRuntime commands = new CommandCapabilityRuntime();
        ToolRegistry registry = new ToolRegistry();
        registry.register(
                "test",
                List.of(new RunJavascriptTool(
                        new RhinoJavascriptRuntime(),
                        MinecraftAgentHostGraph::new,
                        new AgentResultWorkspaceRegistry(),
                        new JavascriptResultPresenter(),
                        commands)));
        ClientToolExecutionEndpoint endpoint = new ClientToolExecutionEndpoint(
                (capabilities, correlation, cancellation) -> CompletableFuture.completedFuture(
                        ToolInvocationContext.developmentConsole(correlation)),
                chunk -> {},
                new Gson(),
                128,
                Runnable::run);
        ToolRuntimeCatalog catalog =
                ToolRuntimeCatalog.from(registry.registrations(), java.util.Set.of());

        UUID disabledRequest = UUID.randomUUID();
        var disabled = (ToolResult.Success<ClientToolExecutionEndpoint.OpenedRequest>)
                endpoint.open(disabledRequest, "main", catalog);
        assertFalse(disabled.value()
                .clientToolIds()
                .contains(ClientToolExecutionEndpoint.EXPERIMENTAL_COMMANDS_CAPABILITY));

        commands.replace(new CommandCapabilityConfig(
                true));
        UUID enabledRequest = UUID.randomUUID();
        var enabled = (ToolResult.Success<ClientToolExecutionEndpoint.OpenedRequest>)
                endpoint.open(enabledRequest, "main", catalog);
        assertTrue(enabled.value()
                .clientToolIds()
                .contains(ClientToolExecutionEndpoint.EXPERIMENTAL_COMMANDS_CAPABILITY));

        commands.replace(CommandCapabilityConfig.defaults());
        assertTrue(commands.enabledFor(enabledRequest.toString()));
        endpoint.close(disabledRequest);
        endpoint.close(enabledRequest);
    }

    private static ToolRegistry registry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register("test", List.of(new FactTool()));
        return registry;
    }

    private static String reassemble(List<ClientToolResultChunkPayload> chunks) {
        ResultChunker.Reassembler reassembler = new ResultChunker.Reassembler();
        java.util.Optional<String> complete = java.util.Optional.empty();
        for (ClientToolResultChunkPayload chunk : chunks) {
            java.util.Optional<String> accepted = reassembler.accept(chunk.asRemoteChunk());
            if (accepted.isPresent()) complete = accepted;
        }
        return complete.orElseThrow();
    }

    private static final class PendingScopeRequest {
        private final UUID requestId = UUID.randomUUID();
        private final CompletableFuture<ToolInvocationContext> context = new CompletableFuture<>();
        private final ScopeTool tool = new ScopeTool();
        private final List<ClientToolResultChunkPayload> sent = new ArrayList<>();
        private final ClientToolExecutionEndpoint endpoint;
        private CancellationSignal cancellation;

        private PendingScopeRequest() {
            ToolRegistry registry = new ToolRegistry();
            registry.register("test", List.of(tool));
            endpoint = new ClientToolExecutionEndpoint(
                    (capabilities, correlation, signal) -> {
                        cancellation = signal;
                        signal.onCancel(() -> tool.lifecycle.add("cancel"));
                        return context;
                    },
                    sent::add,
                    new Gson(),
                    128,
                    Runnable::run);
            endpoint.open(requestId, "main", ToolRuntimeCatalog.from(
                    registry.registrations(), java.util.Set.of()));
        }

        private void start() {
            assertInstanceOf(ToolResult.Success.class, endpoint.handle(new ClientToolCallPayload(
                    requestId, UUID.randomUUID(),
                    "main", "test:scope", "{}")));
            assertNotNull(cancellation);
            assertFalse(cancellation.isCancelled());
            assertTrue(tool.lifecycle.isEmpty());
            assertTrue(sent.isEmpty());
        }

        private void assertClosedBeforeLateCapture() {
            assertTrue(cancellation.isCancelled());
            assertEquals(List.of("cancel", "close"), tool.lifecycle);
            assertEquals(List.of(requestId.toString()), tool.closed);
            assertEquals(0, endpoint.activeRequests());
            assertTrue(context.complete(ToolInvocationContext.developmentConsole(requestId.toString())));
            assertEquals(0, tool.asyncInvocations,
                    "a late context must not reach even a Tool that ignores cancellation");
            assertEquals(0, tool.invocations);
            assertTrue(sent.isEmpty());
        }
    }

    private static final class FactTool implements Tool<FactTool.Input, FactTool.Output> {
        record Input(int value) {}
        record Output(int value) {}

        private static final ToolDescriptor<Input, Output> DESCRIPTOR = new ToolDescriptor<>(
                "test:fact", "Return a fact", Input.class, Output.class, ToolAccess.READ_ONLY);
        private int asyncInvocations;
        private int invocations;

        @Override
        public ToolDescriptor<Input, Output> descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
            invocations++;
            return new ToolResult.Success<>(new Output(input.value()));
        }

        @Override
        public CompletableFuture<ToolResult<Output>> invokeAsync(
                ToolInvocationContext context, Input input, CancellationSignal cancellation) {
            // Deliberately ignore cancellation to verify that the endpoint blocks dispatch itself.
            asyncInvocations++;
            return CompletableFuture.completedFuture(invoke(context, input));
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
            // Deliberately ignore cancellation to verify that the endpoint blocks dispatch itself.
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
