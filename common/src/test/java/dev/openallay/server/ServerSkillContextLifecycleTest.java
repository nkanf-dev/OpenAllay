package dev.openallay.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentRequest;
import dev.openallay.agent.GameGuideAgent;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.bridge.protocol.ClientToolCallPayload;
import dev.openallay.bridge.protocol.ClientToolCancelPayload;
import dev.openallay.bridge.protocol.ClientToolResultChunkPayload;
import dev.openallay.bridge.protocol.ResultChunker;
import dev.openallay.bridge.server.PlayerClientToolRouter;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import dev.openallay.skill.LoadSkillTool;
import dev.openallay.skill.SkillCatalogSnapshot;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.skill.SkillSource;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import dev.openallay.trace.replay.ToolResultNormalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Server-hosted context proof with in-process Tools and scripted model turns only. */
final class ServerSkillContextLifecycleTest {
    private static final Gson GSON = new Gson();
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final String LOAD_SKILL = "openallay__load_skill";

    @Test
    void secondServerLocalAskUsesRetainedEntryAndReferenceWithoutAnotherRead() {
        SkillRepository repository = repository("Server workflow", "Follow server evidence.",
                Map.of("references/query.md", "Return the visible server facts."));
        PlayerClientToolRouter router = router(repository, unavailableTransport());
        AgentSessionStore sessions = new AgentSessionStore();
        ScriptedModel model = new ScriptedModel();
        model.enqueue(request -> toolTurn("entry", new LoadSkillTool.Input("guide")));
        model.enqueue(request -> {
            assertTrue(resultText(request.messages(), "entry").contains("Follow server evidence."));
            return toolTurn("reference", new LoadSkillTool.Input("guide", "references/query.md"));
        });
        model.enqueue(request -> {
            assertTrue(resultText(request.messages(), "reference")
                    .contains("Return the visible server facts."));
            return textTurn("The server workflow is ready.");
        });
        model.enqueue(request -> {
            assertTrue(resultText(request.messages(), "entry").contains("Follow server evidence."));
            assertTrue(resultText(request.messages(), "reference")
                    .contains("Return the visible server facts."));
            assertFalse(toolResult(request.messages(), "entry").error());
            assertFalse(toolResult(request.messages(), "reference").error());
            return textTurn("The retained workflow can answer this question.");
        });
        List<AgentEvent> firstEvents = new ArrayList<>();
        List<AgentEvent> secondEvents = new ArrayList<>();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        SkillCatalogSnapshot firstSkills = ServerGuideRuntime.requestSkills(repository, false);
        SkillCatalogSnapshot secondSkills = ServerGuideRuntime.requestSkills(repository, false);
        AgentToolExecutor firstTools = opened(router, firstId, List.of(), firstSkills);
        AgentToolExecutor secondTools = opened(router, secondId, List.of(), secondSkills);
        try {
            assertTrue(new GameGuideAgent(model, firstTools, sessions, GSON).ask(
                    request(firstId, "Prepare the workflow.", firstSkills), firstEvents::add)
                    .join().successful());
            List<ModelMessage> retained = lastContext(firstEvents).messages();
            assertTrue(new GameGuideAgent(model, secondTools, sessions, GSON).ask(
                    request(secondId, "Use the same workflow.", secondSkills), secondEvents::add)
                    .join().successful());

            assertEquals(4, model.requests.size());
            assertEquals(2, started(firstEvents));
            assertEquals(0, started(secondEvents));
            assertEquals(retained, model.requests.get(3).messages().subList(0, retained.size()));
            assertTrue(model.scripts.isEmpty());
        } finally {
            router.close(ACTOR, firstId);
            router.close(ACTOR, secondId);
        }
    }

    @Test
    void nextRequestInvalidatesOnlyChangedExactReferenceAndKeepsActiveSnapshotFrozen() {
        SkillRepository repository = repository("Original server metadata", "Entry stays.", Map.of(
                "references/a.md", "Old A instructions.",
                "references/b.md", "B instructions stay."));
        PlayerClientToolRouter router = router(repository, unavailableTransport());
        UUID previousId = UUID.randomUUID();
        UUID currentId = UUID.randomUUID();
        SkillCatalogSnapshot previousSkills = ServerGuideRuntime.requestSkills(repository, false);
        String previousPrompt = ServerGuideRuntime.systemPrompt(previousSkills);
        AgentToolExecutor previous = opened(router, previousId, List.of(), previousSkills);
        List<ModelMessage> messages = new ArrayList<>();
        List<LoadSkillTool.Input> inputs = List.of(new LoadSkillTool.Input("guide"),
                new LoadSkillTool.Input("guide", "references/a.md"),
                new LoadSkillTool.Input("guide", "references/b.md"));
        for (int index = 0; index < inputs.size(); index++) {
            String id = "previous-" + index;
            messages.addAll(exchange(id, inputs.get(index),
                    execute(previous, previousId, inputs.get(index))));
        }
        List<ModelMessage> original = List.copyOf(messages);
        assertTrue(repository.reload(List.of(source("Changed server metadata", "Entry stays.", Map.of(
                "references/a.md", "New A instructions.",
                "references/b.md", "B instructions stay."))), Set.of()));
        SkillCatalogSnapshot currentSkills = ServerGuideRuntime.requestSkills(repository, false);
        String currentPrompt = ServerGuideRuntime.systemPrompt(currentSkills);
        AgentToolExecutor current = opened(router, currentId, List.of(), currentSkills);
        try {
            List<ModelMessage> refreshed = current.refreshContext(original);

            assertEquals(original, previous.refreshContext(original));
            assertSame(original.get(1), refreshed.get(1));
            assertSame(original.get(5), refreshed.get(5));
            ModelContent.ToolResult invalidated = toolResult(refreshed, "previous-1");
            assertFalse(invalidated.error());
            assertTrue(invalidated.value().getAsString().startsWith("skill_instructions: invalidated\n"));
            assertFalse(invalidated.value().getAsString().contains("Old A instructions."));
            assertEquals(original.get(2), refreshed.get(2));
            assertEquals(previousPrompt, ServerGuideRuntime.systemPrompt(previousSkills));
            assertTrue(previousPrompt.contains("Original server metadata"));
            assertFalse(previousPrompt.contains("Changed server metadata"));
            assertTrue(currentPrompt.contains("Changed server metadata"));
            assertFalse(currentPrompt.contains("Original server metadata"));

            current.prepareContext(correlation(currentId), refreshed);
            LoadSkillTool.Output entryReceipt = output(execute(current, currentId, inputs.get(0)));
            LoadSkillTool.Output unchangedReceipt = output(execute(current, currentId, inputs.get(2)));
            LoadSkillTool.Output changed = output(execute(current, currentId, inputs.get(1)));
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, entryReceipt.state());
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, unchangedReceipt.state());
            assertEquals("", entryReceipt.content());
            assertEquals("", unchangedReceipt.content());
            assertEquals(LoadSkillTool.LoadState.COMPLETE, changed.state());
            assertEquals("New A instructions.", changed.content());
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(execute(previous, previousId,
                    new LoadSkillTool.Input("guide", "references/a.md"))).state());
            assertEquals(original, previous.refreshContext(original));
        } finally {
            router.close(ACTOR, previousId);
            router.close(ACTOR, currentId);
        }
    }

    @Test
    void clientPlacedActualPlaintextIsNotInvalidatedByDifferentServerDocuments() {
        SkillRepository server = repository("Server metadata", "Unrelated server instructions.", Map.of());
        SkillRepository client = repository("Client metadata", "Actual client instructions.", Map.of());
        LoadSkillTool actualClientTool = new LoadSkillTool(client.snapshot(Set.of()), "client");
        AtomicReference<PlayerClientToolRouter> routerRef = new AtomicReference<>();
        PlayerClientToolRouter.Transport transport = new PlayerClientToolRouter.Transport() {
            @Override
            public boolean call(UUID actorId, ClientToolCallPayload payload) {
                LoadSkillTool.Input input = GSON.fromJson(payload.argumentsJson(), LoadSkillTool.Input.class);
                ToolResult<LoadSkillTool.Output> result = actualClientTool.invoke(
                        ToolInvocationContext.developmentConsole(payload.requestId().toString()), input);
                JsonObject normalized = new ToolResultNormalizer(GSON).normalize(result, LoadSkillTool.Output.class);
                for (var chunk : new ResultChunker().split(payload.invocationId(), normalized.toString(), 128)) {
                    assertTrue(routerRef.get().receive(actorId,
                            ClientToolResultChunkPayload.from(payload.requestId(), chunk)));
                }
                return true;
            }

            @Override
            public void cancel(UUID actorId, ClientToolCancelPayload payload) {}
        };
        PlayerClientToolRouter router = router(server, transport);
        routerRef.set(router);
        AgentSessionStore sessions = new AgentSessionStore();
        ScriptedModel model = new ScriptedModel();
        model.enqueue(request -> toolTurn("client-entry", new LoadSkillTool.Input("guide")));
        model.enqueue(request -> {
            assertFalse(toolResult(request.messages(), "client-entry").error());
            assertTrue(resultText(request.messages(), "client-entry").contains("Actual client instructions."));
            assertFalse(resultText(request.messages(), "client-entry").contains("Unrelated server instructions."));
            return textTurn("The client workflow is ready.");
        });
        model.enqueue(request -> {
            assertFalse(toolResult(request.messages(), "client-entry").error());
            assertTrue(resultText(request.messages(), "client-entry").contains("Actual client instructions."));
            return textTurn("The real client instructions remain available.");
        });
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        SkillCatalogSnapshot firstSkills = ServerGuideRuntime.requestSkills(server, false);
        AgentToolExecutor first = opened(router, firstId, List.of("openallay:load_skill"), firstSkills,
                actualClientTool.catalogManifest());
        List<AgentEvent> firstEvents = new ArrayList<>();
        List<AgentEvent> secondEvents = new ArrayList<>();
        try {
            assertTrue(new GameGuideAgent(model, first, sessions, GSON).ask(
                    request(firstId, "Read the client workflow.", firstSkills), firstEvents::add)
                    .join().successful());
            List<ModelMessage> retained = lastContext(firstEvents).messages();
            assertTrue(server.reload(List.of(), Set.of()));
            SkillCatalogSnapshot secondSkills = ServerGuideRuntime.requestSkills(server, false);
            AgentToolExecutor second = opened(router, secondId, List.of("openallay:load_skill"), secondSkills,
                    actualClientTool.catalogManifest());
            assertEquals(retained, second.refreshContext(retained));
            second.prepareContext(correlation(secondId), retained);
            assertTrue(new GameGuideAgent(model, second, sessions, GSON).ask(
                    request(secondId, "Reuse the client workflow.", secondSkills), secondEvents::add)
                    .join().successful());
            assertEquals(1, started(firstEvents));
            assertEquals(0, started(secondEvents));
            assertEquals(3, model.requests.size());
            assertEquals(retained, model.requests.get(2).messages().subList(0, retained.size()));
            assertTrue(model.scripts.isEmpty());
        } finally {
            router.close(ACTOR, firstId);
            router.close(ACTOR, secondId);
            actualClientTool.closeRequestScope(firstId.toString());
            actualClientTool.closeRequestScope(secondId.toString());
        }
    }

    private static PlayerClientToolRouter router(
            SkillRepository repository, PlayerClientToolRouter.Transport transport) {
        ToolRegistry tools = new ToolRegistry();
        // This startup Tool's context validator is intentionally captured before later reloads.
        tools.register("openallay", List.of(new LoadSkillTool(repository)));
        return new PlayerClientToolRouter(tools, GSON, transport);
    }

    private static PlayerClientToolRouter.Transport unavailableTransport() {
        return new PlayerClientToolRouter.Transport() {
            @Override
            public boolean call(UUID actorId, ClientToolCallPayload payload) {
                throw new AssertionError("Server-local Skills must not dispatch to the client");
            }

            @Override
            public void cancel(UUID actorId, ClientToolCancelPayload payload) {}
        };
    }

    private static AgentToolExecutor opened(
            PlayerClientToolRouter router, UUID id, List<String> clientIds, SkillCatalogSnapshot skills) {
        return opened(router, id, clientIds, skills, dev.openallay.skill.SkillCatalogManifest.EMPTY);
    }

    private static AgentToolExecutor opened(PlayerClientToolRouter router, UUID id, List<String> clientIds,
            SkillCatalogSnapshot skills, dev.openallay.skill.SkillCatalogManifest clientSkills) {
        ToolResult<AgentToolExecutor> result = router.open(ACTOR, id, "main", clientIds, skills, clientSkills);
        assertInstanceOf(ToolResult.Success.class, result);
        return ((ToolResult.Success<AgentToolExecutor>) result).value();
    }

    private static AgentToolResult execute(AgentToolExecutor tools, UUID id, LoadSkillTool.Input input) {
        AgentToolResult result = tools.execute(LOAD_SKILL, arguments(input),
                ToolInvocationContext.developmentConsole(correlation(id)), new CancellationSignal()).join();
        assertFalse(result.failure(), result.normalized().toString());
        return result;
    }

    private static LoadSkillTool.Output output(AgentToolResult result) {
        return GSON.fromJson(result.normalized().get("value"), LoadSkillTool.Output.class);
    }

    private static List<ModelMessage> exchange(String id, LoadSkillTool.Input input, AgentToolResult result) {
        return List.of(new ModelMessage(ModelRole.ASSISTANT,
                        List.of(new ModelContent.ToolUse(id, LOAD_SKILL, arguments(input)))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(id,
                        new JsonPrimitive(result.normalized().get("modelText").getAsString()), false))));
    }

    private static AgentRequest request(UUID id, String question, SkillCatalogSnapshot skills) {
        return new AgentRequest(id, ACTOR, "main", question, ServerGuideRuntime.systemPrompt(skills),
                ToolInvocationContext.developmentConsole(correlation(id)), false);
    }

    private static String correlation(UUID id) {
        return ACTOR + "/" + id;
    }

    private static JsonObject arguments(LoadSkillTool.Input input) {
        return GSON.toJsonTree(input).getAsJsonObject();
    }

    private static ModelTurn toolTurn(String id, LoadSkillTool.Input input) {
        return new ModelTurn("test", "test", List.of(new ModelContent.ToolUse(id, LOAD_SKILL, arguments(input))),
                "tool_use", ModelUsage.empty());
    }

    private static ModelTurn textTurn(String text) {
        return new ModelTurn("test", "test", List.of(new ModelContent.Text(text)),
                "end_turn", ModelUsage.empty());
    }

    private static ModelContent.ToolResult toolResult(List<ModelMessage> messages, String id) {
        return messages.stream().flatMap(message -> message.content().stream())
                .filter(ModelContent.ToolResult.class::isInstance)
                .map(ModelContent.ToolResult.class::cast)
                .filter(result -> result.toolUseId().equals(id)).findFirst().orElseThrow();
    }

    private static String resultText(List<ModelMessage> messages, String id) {
        return toolResult(messages, id).value().getAsString();
    }

    private static AgentEvent.ContextUpdated lastContext(List<AgentEvent> events) {
        return events.stream().filter(AgentEvent.ContextUpdated.class::isInstance)
                .map(AgentEvent.ContextUpdated.class::cast).reduce((first, second) -> second).orElseThrow();
    }

    private static long started(List<AgentEvent> events) {
        return events.stream().filter(AgentEvent.ToolStarted.class::isInstance)
                .map(AgentEvent.ToolStarted.class::cast)
                .filter(event -> event.toolId().equals("openallay:load_skill")).count();
    }

    private static SkillRepository repository(String description, String body, Map<String, String> references) {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        assertTrue(repository.reload(List.of(source(description, body, references)), Set.of()));
        return repository;
    }

    private static SkillSource source(String description, String body, Map<String, String> references) {
        Map<String, String> files = new java.util.HashMap<>();
        files.put("guide/SKILL.md", """
                ---
                name: guide
                description: %s
                ---
                %s
                """.formatted(description, body));
        references.forEach((name, text) -> files.put("guide/" + name, text));
        return new SkillSource("pack", "guide/SKILL.md", files);
    }

    private static final class ScriptedModel implements ModelClient {
        private final Deque<Function<ModelRequest, ModelTurn>> scripts = new ArrayDeque<>();
        private final List<ModelRequest> requests = new ArrayList<>();

        private void enqueue(Function<ModelRequest, ModelTurn> script) {
            scripts.addLast(script);
        }

        @Override
        public CompletableFuture<ModelTurn> complete(
                ModelRequest request, Consumer<ModelEvent> events, CancellationSignal cancellation) {
            requests.add(request);
            return CompletableFuture.completedFuture(scripts.removeFirst().apply(request));
        }
    }
}
