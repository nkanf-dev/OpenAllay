package dev.openallay.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.bridge.protocol.ClientToolCallPayload;
import dev.openallay.bridge.protocol.ClientToolCancelPayload;
import dev.openallay.bridge.protocol.ClientToolResultChunkPayload;
import dev.openallay.bridge.protocol.ResultChunker;
import dev.openallay.bridge.server.PlayerClientToolRouter;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.tool.ModelFacingToolOutput;
import dev.openallay.tool.Tool;
import dev.openallay.trace.replay.ToolResultNormalizer;
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
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class PlayerClientToolRouterTest {
    @Test
    void intersectsTrustedIdsAndRoutesTheFrozenClientToolToItsActor() {
        ToolRegistry registry = registry();
        List<SentCall> calls = new ArrayList<>();
        PlayerClientToolRouter router = new PlayerClientToolRouter(
                registry,
                new Gson(),
                transport(calls, new ArrayList<>()));
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AgentToolExecutor tools = success(router.open(
                actor,
                requestId,
                "main",
                List.of("test:fact", "malicious:invented"), emptySkills()));

        CompletableFuture<AgentToolResult> result = tools.execute(
                "test:fact",
                arguments("value", 4),
                ToolInvocationContext.developmentConsole(requestId.toString()),
                new CancellationSignal());
        assertEquals(1, calls.size());
        assertEquals(actor, calls.getFirst().actorId());
        assertEquals(requestId, calls.getFirst().payload().requestId());
        assertEquals("test:fact", calls.getFirst().payload().toolId());

        JsonObject normalized = new JsonObject();
        normalized.addProperty("status", "success");
        normalized.addProperty("outputType", FactTool.Output.class.getName());
        JsonObject value = new JsonObject();
        value.addProperty("value", 99);
        normalized.add("value", value);
        for (var chunk : new ResultChunker().split(
                calls.getFirst().payload().invocationId(),
                normalized.toString(),
                3)) {
            assertTrue(router.receive(
                    actor, ClientToolResultChunkPayload.from(requestId, chunk)));
        }

        AgentToolResult completed = result.join();
        assertFalse(completed.failure());
        assertEquals("test:fact", completed.toolId());
        assertEquals(99, completed.normalized().getAsJsonObject("value").get("value").getAsInt());
    }

    @Test
    void wrongActorCannotCompleteAndRemoteFailureRemainsAToolResult() {
        ToolRegistry registry = registry();
        List<SentCall> calls = new ArrayList<>();
        PlayerClientToolRouter router = new PlayerClientToolRouter(
                registry, new Gson(), transport(calls, new ArrayList<>()));
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AgentToolExecutor tools = success(router.open(
                actor, requestId, "main", List.of("test:fact"), emptySkills()));
        CompletableFuture<AgentToolResult> result = tools.execute(
                "test__fact",
                arguments("value", 1),
                ToolInvocationContext.developmentConsole(requestId.toString()),
                new CancellationSignal());
        ClientToolCallPayload call = calls.getFirst().payload();
        JsonObject normalized = new JsonObject();
        normalized.addProperty("status", "failure");
        normalized.addProperty("code", "client_tool_unavailable");
        normalized.addProperty("message", "unavailable");
        var chunk = ClientToolResultChunkPayload.from(
                requestId,
                new ResultChunker().split(
                                call.invocationId(), normalized.toString(), 128)
                        .getFirst());

        assertFalse(router.receive(UUID.randomUUID(), chunk));
        assertFalse(result.isDone());
        assertTrue(router.receive(actor, chunk));
        AgentToolResult completed = result.join();
        assertTrue(completed.failure());
        assertEquals("client_tool_unavailable", completed.normalized().get("code").getAsString());
    }

    @Test
    void requestCancellationCancelsOnlyItsInvocationAndSuppressesLateChunks() {
        ToolRegistry registry = registry();
        List<SentCall> calls = new ArrayList<>();
        List<SentCancel> cancels = new ArrayList<>();
        PlayerClientToolRouter router = new PlayerClientToolRouter(
                registry, new Gson(), transport(calls, cancels));
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AgentToolExecutor tools = success(router.open(
                actor, requestId, "main", List.of("test:fact"), emptySkills()));
        CancellationSignal cancellation = new CancellationSignal();
        CompletableFuture<AgentToolResult> result = tools.execute(
                "test:fact",
                arguments("value", 1),
                ToolInvocationContext.developmentConsole(requestId.toString()),
                cancellation);

        assertTrue(cancellation.cancel());
        assertEquals(1, cancels.size());
        assertEquals(requestId, cancels.getFirst().payload().requestId());
        assertTrue(result.isCompletedExceptionally());
        JsonObject late = new JsonObject();
        late.addProperty("status", "failure");
        late.addProperty("code", "late");
        late.addProperty("message", "late");
        assertFalse(router.receive(
                actor,
                ClientToolResultChunkPayload.from(
                        requestId,
                        new ResultChunker().split(
                                        calls.getFirst().payload().invocationId(),
                                        late.toString(),
                                        128)
                                .getFirst())));
    }

    @Test
    void alreadyCancelledRequestNeverDispatchesAClientToolCall() {
        List<SentCall> calls = new ArrayList<>();
        List<SentCancel> cancels = new ArrayList<>();
        PlayerClientToolRouter router = new PlayerClientToolRouter(
                registry(), new Gson(), transport(calls, cancels));
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AgentToolExecutor tools = success(router.open(
                actor, requestId, "main", List.of("test:fact"), emptySkills()));
        CancellationSignal cancellation = new CancellationSignal();
        cancellation.cancel();

        CompletableFuture<AgentToolResult> result = tools.execute(
                "test:fact",
                arguments("value", 1),
                ToolInvocationContext.developmentConsole(requestId.toString()),
                cancellation);

        assertTrue(result.isCompletedExceptionally());
        assertTrue(calls.isEmpty());
        assertTrue(cancels.isEmpty());
    }

    @Test
    void lostClientResultBecomesAToolFailureInsteadOfHangingTheAgent() throws Exception {
        List<SentCall> calls = new ArrayList<>();
        List<SentCancel> cancels = new ArrayList<>();
        PlayerClientToolRouter router = new PlayerClientToolRouter(
                registry(),
                new Gson(),
                transport(calls, cancels),
                Duration.ofMillis(10));
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AgentToolExecutor tools = success(router.open(
                actor, requestId, "main", List.of("test:fact"), emptySkills()));

        AgentToolResult result = tools.execute(
                        "test__fact",
                        arguments("value", 1),
                        ToolInvocationContext.developmentConsole(requestId.toString()),
                        new CancellationSignal())
                .get(1, TimeUnit.SECONDS);

        assertTrue(result.failure());
        assertEquals("client_tool_timeout", result.normalized().get("code").getAsString());
        assertEquals(1, calls.size());
        assertEquals(1, cancels.size());
    }

    @Test
    void cancellationRacingALateChunkCannotRecreatePartialAssembly() {
        List<SentCall> calls = new ArrayList<>();
        PlayerClientToolRouter router = new PlayerClientToolRouter(
                registry(), new Gson(), transport(calls, new ArrayList<>()));
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AgentToolExecutor tools = success(router.open(
                actor, requestId, "main", List.of("test:fact"), emptySkills()));

        for (int attempt = 0; attempt < 100; attempt++) {
            CancellationSignal cancellation = new CancellationSignal();
            tools.execute(
                    "test:fact",
                    arguments("value", attempt),
                    ToolInvocationContext.developmentConsole("client-race-" + attempt),
                    cancellation);
            ClientToolCallPayload call = calls.getLast().payload();
            ClientToolResultChunkPayload firstChunk = ClientToolResultChunkPayload.from(
                    requestId,
                    new ResultChunker()
                            .split(call.invocationId(), "{\"status\":\"failure\"}", 1)
                            .getFirst());

            CompletableFuture.allOf(
                            CompletableFuture.runAsync(cancellation::cancel),
                            CompletableFuture.runAsync(() -> router.receive(actor, firstChunk)))
                    .join();

            assertEquals(0, router.activeResultAssemblies(actor, requestId));
        }
    }

    @Test
    void advertisedJavascriptAlwaysUsesClientPlacementWithoutInspectingArguments() {
        ServerJavascriptTool javascript = new ServerJavascriptTool();
        ToolRegistry registry = new ToolRegistry();
        registry.register("test", List.of(javascript));
        List<SentCall> calls = new ArrayList<>();
        PlayerClientToolRouter router = new PlayerClientToolRouter(
                registry, new Gson(), transport(calls, new ArrayList<>()));
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AgentToolExecutor tools = success(router.open(
                actor, requestId, "main", List.of(RunJavascriptTool.ID), emptySkills()));
        try {
            for (String source : List.of(
                    "return mc.player.position;",
                    "return world.inspect({});",
                    "return commands.run('say no');",
                    "not valid JavaScript")) {
                JsonObject arguments = javascriptArguments(source);
                arguments.addProperty("title", "Use server-authoritative data");
                arguments.addProperty("description", "Inspect the world on the server");
                CompletableFuture<AgentToolResult> result = tools.execute(
                        "openallay__run_javascript",
                        arguments,
                        ToolInvocationContext.developmentConsole(requestId.toString()),
                        new CancellationSignal());

                assertFalse(result.isDone());
                assertEquals(actor, calls.getLast().actorId());
                assertEquals(RunJavascriptTool.ID, calls.getLast().payload().toolId());
                assertEquals(arguments.toString(), calls.getLast().payload().argumentsJson());
            }
            assertEquals(4, calls.size());
            assertEquals(0, javascript.invocations);
        } finally {
            router.close(actor, requestId);
        }
    }

    @Test
    void javascriptWithoutAnAdvertisedClientUsesServerPlacementWithoutRoots() {
        ServerJavascriptTool javascript = new ServerJavascriptTool();
        ToolRegistry registry = new ToolRegistry();
        registry.register("test", List.of(javascript));
        List<SentCall> calls = new ArrayList<>();
        PlayerClientToolRouter router = new PlayerClientToolRouter(
                registry, new Gson(), transport(calls, new ArrayList<>()));
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AgentToolExecutor tools = success(router.open(actor, requestId, "main", List.of(), emptySkills()));
        try {
            AgentToolResult result = tools.execute(
                            "openallay__run_javascript",
                            javascriptArguments("return mc.player.position;"),
                            ToolInvocationContext.developmentConsole(requestId.toString()),
                            new CancellationSignal())
                    .join();

            assertTrue(calls.isEmpty());
            assertEquals(1, javascript.invocations);
            assertFalse(result.failure());
            assertEquals(
                    "server-authoritative",
                    result.normalized().getAsJsonObject("value").get("route").getAsString());
        } finally {
            router.close(actor, requestId);
        }
    }

    @Test
    void unavailableAdvertisedClientDoesNotRetryJavascriptOnTheServer() {
        ServerJavascriptTool javascript = new ServerJavascriptTool();
        ToolRegistry registry = new ToolRegistry();
        registry.register("test", List.of(javascript));
        List<SentCall> calls = new ArrayList<>();
        PlayerClientToolRouter router = new PlayerClientToolRouter(
                registry, new Gson(), new PlayerClientToolRouter.Transport() {
                    @Override
                    public boolean call(UUID actorId, ClientToolCallPayload payload) {
                        calls.add(new SentCall(actorId, payload));
                        return false;
                    }

                    @Override
                    public void cancel(UUID actorId, ClientToolCancelPayload payload) {}
                });
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AgentToolExecutor tools = success(router.open(
                actor, requestId, "main", List.of(RunJavascriptTool.ID), emptySkills()));
        try {
            AgentToolResult result = tools.execute(
                            "openallay__run_javascript",
                            javascriptArguments("return mc.player.position;"),
                            ToolInvocationContext.developmentConsole(requestId.toString()),
                            new CancellationSignal())
                    .join();

            assertEquals(1, calls.size());
            assertEquals(0, javascript.invocations);
            assertTrue(result.failure());
            assertEquals("client_tool_bridge_unavailable", result.normalized().get("code").getAsString());
        } finally {
            router.close(actor, requestId);
        }
    }

    @Test
    void acceptsModelFacingOutputAndRebuildsProjectionFromStructuredValue() {
        Gson gson = new Gson();
        JsonObject normalized = new ToolResultNormalizer(gson).normalize(
                new ToolResult.Success<>(new ModelTextTool.Output(99)),
                ModelTextTool.Output.class);
        assertEquals("Fact: 99", normalized.get("modelText").getAsString());
        AgentToolResult completed = receiveResult(new ModelTextTool(), normalized, true);
        assertFalse(completed.failure());
        assertEquals(normalized, completed.normalized());

        normalized.addProperty("modelText", "untrusted envelope projection");
        completed = receiveResult(new ModelTextTool(), normalized, true);
        assertFalse(completed.failure());
        assertEquals("Fact: 99", completed.normalized().get("modelText").getAsString());

        normalized.remove("modelText");
        completed = receiveResult(new ModelTextTool(), normalized, true);
        assertFalse(completed.failure());
        assertEquals("Fact: 99", completed.normalized().get("modelText").getAsString());
    }

    @Test
    void rejectsMalformedModelTextAndUnknownEnvelopeFields() {
        Gson gson = new Gson();
        JsonObject valid = new ToolResultNormalizer(gson).normalize(
                new ToolResult.Success<>(new ModelTextTool.Output(99)),
                ModelTextTool.Output.class);
        List<com.google.gson.JsonElement> invalidTexts = List.of(
                com.google.gson.JsonNull.INSTANCE,
                new com.google.gson.JsonPrimitive(42),
                new com.google.gson.JsonPrimitive(" "),
                new JsonObject());
        for (var text : invalidTexts) {
            JsonObject invalid = valid.deepCopy();
            invalid.add("modelText", text);
            AgentToolResult completed = receiveResult(new ModelTextTool(), invalid, false);
            assertTrue(completed.failure());
            assertEquals("client_tool_result_invalid", completed.normalized().get("code").getAsString());
        }
        JsonObject extra = valid.deepCopy();
        extra.addProperty("unknown", "field");
        assertTrue(receiveResult(new ModelTextTool(), extra, false).failure());

        JsonObject ordinary = new ToolResultNormalizer(gson).normalize(
                new ToolResult.Success<>(new FactTool.Output(99)), FactTool.Output.class);
        ordinary.addProperty("modelText", "not supported by this output type");
        assertTrue(receiveResult(new FactTool(), ordinary, false).failure());
    }

    private static AgentToolResult receiveResult(
            Tool<?, ?> tool, JsonObject normalized, boolean accepted) {
        ToolRegistry registry = new ToolRegistry();
        registry.register("test", List.of(tool));
        List<SentCall> calls = new ArrayList<>();
        PlayerClientToolRouter router = new PlayerClientToolRouter(
                registry, new Gson(), transport(calls, new ArrayList<>()));
        UUID actor = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID requestId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        AgentToolExecutor tools = success(router.open(
                actor, requestId, "main", List.of(tool.descriptor().id()), emptySkills()));
        try {
            CompletableFuture<AgentToolResult> result = tools.execute(
                    tool.descriptor().id(), arguments("value", 4),
                    ToolInvocationContext.developmentConsole(requestId.toString()),
                    new CancellationSignal());
            var chunks = new ResultChunker().split(
                    calls.getFirst().payload().invocationId(), normalized.toString(), 3);
            for (int index = 0; index < chunks.size(); index++) {
                assertEquals(index < chunks.size() - 1 || accepted, router.receive(
                        actor, ClientToolResultChunkPayload.from(requestId, chunks.get(index))));
            }
            assertTrue(result.isDone());
            return result.join();
        } finally {
            router.close(actor, requestId);
        }
    }

    private static PlayerClientToolRouter.Transport transport(
            List<SentCall> calls, List<SentCancel> cancels) {
        return new PlayerClientToolRouter.Transport() {
            @Override
            public boolean call(UUID actorId, ClientToolCallPayload payload) {
                calls.add(new SentCall(actorId, payload));
                return true;
            }

            @Override
            public void cancel(UUID actorId, ClientToolCancelPayload payload) {
                cancels.add(new SentCancel(actorId, payload));
            }
        };
    }

    private static AgentToolExecutor success(ToolResult<AgentToolExecutor> result) {
        return ((ToolResult.Success<AgentToolExecutor>) assertInstanceOf(
                        ToolResult.Success.class, result))
                .value();
    }

    private static JsonObject arguments(String key, int value) {
        JsonObject result = new JsonObject();
        result.addProperty(key, value);
        return result;
    }

    private static JsonObject javascriptArguments(String source) {
        return new Gson().toJsonTree(new RunJavascriptTool.Input(source, List.of())).getAsJsonObject();
    }

    private static dev.openallay.skill.SkillCatalogSnapshot emptySkills() {
        return new dev.openallay.skill.SkillRepository(
                new dev.openallay.skill.SkillParser(), Set.of()).snapshot(Set.of());
    }

    private static ToolRegistry registry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register("test", List.of(new FactTool()));
        return registry;
    }

    private static final class FactTool implements Tool<FactTool.Input, FactTool.Output> {
        record Input(int value) {}
        record Output(int value) {}

        private static final ToolDescriptor<Input, Output> DESCRIPTOR = new ToolDescriptor<>(
                "test:fact", "Return a fact", Input.class, Output.class, ToolAccess.READ_ONLY);

        @Override public ToolDescriptor<Input, Output> descriptor() { return DESCRIPTOR; }

        @Override
        public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
            return new ToolResult.Success<>(new Output(input.value()));
        }
    }

    private static final class ModelTextTool
            implements Tool<FactTool.Input, ModelTextTool.Output> {
        record Output(int value) implements ModelFacingToolOutput {
            @Override
            public String modelText() {
                return "Fact: " + value;
            }
        }

        @Override
        public ToolDescriptor<FactTool.Input, Output> descriptor() {
            return new ToolDescriptor<>(
                    "test:model_text", "Return a model-facing fact",
                    FactTool.Input.class, Output.class, ToolAccess.READ_ONLY);
        }

        @Override
        public ToolResult<Output> invoke(ToolInvocationContext context, FactTool.Input input) {
            return new ToolResult.Success<>(new Output(input.value()));
        }
    }

    private static final class ServerJavascriptTool
            implements Tool<RunJavascriptTool.Input, ServerJavascriptTool.Output> {
        record Output(String route) {}

        private static final ToolDescriptor<RunJavascriptTool.Input, Output> DESCRIPTOR = new ToolDescriptor<>(
                RunJavascriptTool.ID,
                "Run detached JavaScript",
                RunJavascriptTool.Input.class,
                Output.class,
                ToolAccess.EXPERIMENTAL_ACTION,
                Set.of());
        private int invocations;

        @Override
        public ToolDescriptor<RunJavascriptTool.Input, Output> descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public ToolResult<Output> invoke(ToolInvocationContext context, RunJavascriptTool.Input input) {
            invocations++;
            return new ToolResult.Success<>(new Output("server-authoritative"));
        }
    }

    private record SentCall(UUID actorId, ClientToolCallPayload payload) {}
    private record SentCancel(UUID actorId, ClientToolCancelPayload payload) {}
}
