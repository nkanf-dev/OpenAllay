package dev.openallay.bridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.bridge.CorrelationRegistry;
import dev.openallay.bridge.protocol.RemoteToolCallPayload;
import dev.openallay.bridge.protocol.RemoteToolRequestClosePayload;
import dev.openallay.bridge.protocol.RemoteToolResultChunkPayload;
import dev.openallay.bridge.protocol.ResultChunker;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.script.command.CommandCapabilityRuntime;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.workspace.AgentResultWorkspaceRegistry;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.testing.GroundedTestFixtures;
import dev.openallay.tool.RequestScopeParticipant;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.ToolDescriptor;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.builtin.RunJavascriptTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class RemoteToolServerTest {
    @Test
    void requestCloseCancelsPendingContextBeforeClosingToolsAndBlocksLateInvocation() {
        PendingRequest request = new PendingRequest();
        request.start();

        request.server.closeRequest(request.actor, new RemoteToolRequestClosePayload(
                "main"));

        request.assertClosedBeforeLateCapture();
        request.server.closeRequest(request.actor, new RemoteToolRequestClosePayload(
                "main"));
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

    @Test
    void oldCompletionCannotRemoveReplacementCorrelationWithSameActorAndId() {
        UUID actor = UUID.randomUUID();
        UUID correlation = UUID.randomUUID();
        ScopeTool tool = new ScopeTool();
        ToolRegistry tools = new ToolRegistry();
        tools.register("test", List.of(tool));
        CorrelationRegistry correlations = new CorrelationRegistry();
        List<CompletableFuture<ToolInvocationContext>> captures = new ArrayList<>();
        List<RemoteToolResultChunkPayload> output = new ArrayList<>();
        RemoteToolServer server = new RemoteToolServer(new ExportedToolPolicy(tools, Set.of("test:scope")),
                (sender, capabilities, scope, cancellation) -> {
                    CompletableFuture<ToolInvocationContext> capture = new CompletableFuture<>();
                    captures.add(capture); return capture;
                }, (sender, chunk) -> output.add(chunk), correlations, dev.openallay.json.EngineJson.create(), 128);
        RemoteToolCallPayload call = new RemoteToolCallPayload(correlation, "main", "test:scope", "{}");
        server.handle(actor, call);
        server.disconnect(actor);
        server.handle(actor, call);
        CancellationSignal replacement = correlations.find(actor, correlation).orElseThrow().cancellation();
        captures.get(0).complete(ToolInvocationContext.developmentConsole(actor + "/main"));
        assertTrue(correlations.find(actor, correlation).orElseThrow().cancellation() == replacement);
        assertTrue(output.isEmpty());
        captures.get(1).complete(ToolInvocationContext.developmentConsole(actor + "/main"));
        assertTrue(correlations.find(actor, correlation).isEmpty());
        assertFalse(output.isEmpty());
    }

    @Test
    void synchronousBindFailureRetiresOnlyItsOriginalCorrelation() {
        UUID actor = UUID.randomUUID(); UUID correlation = UUID.randomUUID();
        ScopeTool tool = new ScopeTool(); ToolRegistry tools = new ToolRegistry();
        tools.register("test", List.of(tool)); CorrelationRegistry correlations = new CorrelationRegistry();
        RemoteToolServer.ResponseSink sink = new RemoteToolServer.ResponseSink() {
            @Override public void send(UUID sender, RemoteToolResultChunkPayload chunk) { throw new AssertionError(); }
            @Override public RemoteToolServer.ResponseSink bind(UUID sender) { throw new IllegalStateException("bind failed"); }
        };
        RemoteToolServer server = new RemoteToolServer(new ExportedToolPolicy(tools, Set.of("test:scope")),
                (sender, capabilities, scope, cancellation) -> { throw new AssertionError("capture ran"); },
                sink, correlations, dev.openallay.json.EngineJson.create(), 128);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> server.handle(actor,
                new RemoteToolCallPayload(correlation, "main", "test:scope", "{}")));
        assertTrue(correlations.find(actor, correlation).isEmpty());
        assertEquals(0, tool.asyncInvocations);
    }

    @Test
    void rootFreeServerJavascriptReadsTheServerSnapshotWithoutScanningSource() throws Exception {
        JavascriptRequest request = new JavascriptRequest();
        try {
            JsonObject result = request.invoke("""
                    const commandText = "commands.run('say no')";
                    if (false) commands.run(commandText);
                    return {
                      recipe: mc.recipes[0].id,
                      commands: typeof commands,
                      java: typeof Java,
                      packages: typeof Packages
                    };
                    """);

            assertEquals("success", result.get("status").getAsString());
            JsonObject preview = result.getAsJsonObject("value").getAsJsonObject("preview");
            assertEquals("minecraft:iron_block", preview.get("recipe").getAsString());
            assertEquals("undefined", preview.get("commands").getAsString());
            assertEquals("undefined", preview.get("java").getAsString());
            assertEquals("undefined", preview.get("packages").getAsString());
            var sources = result.getAsJsonObject("value").getAsJsonArray("sources");
            assertFalse((sources.size() == 0));
            assertTrue(dev.openallay.json.JsonReaders.elements(sources).stream().allMatch(source -> source.getAsJsonObject()
                    .getAsJsonObject("evidence").get("authority").getAsString().equals("SERVER_AUTHORITATIVE")));
            assertEquals(1, request.graphCaptures.get());
        } finally {
            request.close();
        }
    }

    @Test
    void rootFreeCommandAccessFailsAtRuntimeWithoutAServerCommandCapability() throws Exception {
        JavascriptRequest request = new JavascriptRequest();
        try {
            JsonObject result = request.invoke("return commands.run('say no');");

            assertEquals("failure", result.get("status").getAsString());
            assertEquals("javascript_error", result.get("code").getAsString());
            assertTrue(result.get("message").getAsString().contains("commands"));
            assertEquals(1, request.graphCaptures.get(),
                    "the invocation must reach the runtime, not a model-field or source gate");
        } finally {
            request.close();
        }
    }

    @Test
    void rootFreeJavaAccessFailsAtRuntimeWithoutUnrestrictedServerAuthority() throws Exception {
        JavascriptRequest request = new JavascriptRequest();
        try {
            JsonObject result = request.invoke(
                    "return Java.type('java.lang.System').getProperty('java.version');");

            assertEquals("failure", result.get("status").getAsString());
            assertEquals("javascript_error", result.get("code").getAsString());
            assertTrue(result.get("message").getAsString().contains("Java"));
            assertEquals(1, request.graphCaptures.get());
        } finally {
            request.close();
        }
    }

    private static final class JavascriptRequest {
        private final UUID actor = GroundedTestFixtures.PLAYER_ID;
        private final UUID correlation = UUID.randomUUID();
        private final Gson gson = dev.openallay.json.EngineJson.create();
        private final AtomicInteger graphCaptures = new AtomicInteger();
        private final CompletableFuture<JsonObject> result = new CompletableFuture<>();
        private final ResultChunker.Reassembler reassembler = new ResultChunker.Reassembler();
        private final RemoteToolServer server;

        private JavascriptRequest() {
            // A setting alone cannot grant a command bridge. Server requests have no capture.
            CommandCapabilityRuntime commands = new CommandCapabilityRuntime();
            commands.replace(new CommandCapabilityConfig(true));
            RunJavascriptTool javascript = new RunJavascriptTool(
                    new RhinoJavascriptRuntime(),
                    context -> {
                        graphCaptures.incrementAndGet();
                        return new MinecraftAgentHostGraph(context);
                    },
                    new AgentResultWorkspaceRegistry(),
                    new JavascriptResultPresenter(),
                    commands);
            ToolRegistry tools = new ToolRegistry();
            tools.register("test", List.of(javascript));
            server = new RemoteToolServer(
                    new ExportedToolPolicy(tools, Set.of(RunJavascriptTool.ID)),
                    (actorId, capabilities, correlationId, cancellation) -> {
                        assertEquals(actor, actorId);
                        ToolInvocationContext base = GroundedTestFixtures.fullContext();
                        ToolInvocationContext context = new ToolInvocationContext(
                                correlationId, base.capturedAt(), base.caller(), base.player(),
                                base.registries(), base.recipes(), base.observableGameState(), base.metrics());
                        assertFalse(context.unrestrictedJavascript());
                        return CompletableFuture.completedFuture(context);
                    },
                    (actorId, chunk) -> {
                        assertEquals(actor, actorId);
                        assertEquals(correlation, chunk.correlationId());
                        reassembler.accept(chunk).ifPresent(json ->
                                result.complete(dev.openallay.json.JsonTrees.parse(json).getAsJsonObject()));
                    },
                    new CorrelationRegistry(),
                    gson,
                    128);
        }

        private JsonObject invoke(String source) throws Exception {
            RunJavascriptTool.Input input = new RunJavascriptTool.Input(
                    source, List.of(), "Enable commands and Java", "Request unrestricted command access");
            assertInstanceOf(ToolResult.Success.class, server.handle(actor, new RemoteToolCallPayload(
                    correlation, "main", RunJavascriptTool.ID, gson.toJson(input))));
            return result.get(5, TimeUnit.SECONDS);
        }

        private void close() {
            server.closeRequest(actor, new RemoteToolRequestClosePayload("main"));
        }
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
                    dev.openallay.json.EngineJson.create(),
                    128);
        }

        private void start() {
            assertInstanceOf(ToolResult.Success.class, server.handle(actor, new RemoteToolCallPayload(
                    UUID.randomUUID(), "main", "test:scope", "{}")));
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
