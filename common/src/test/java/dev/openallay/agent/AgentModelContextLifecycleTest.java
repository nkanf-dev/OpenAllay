package dev.openallay.agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.agent.context.ContextStructure;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.agent.tool.CompositeAgentToolExecutor;
import dev.openallay.agent.tool.LocalAgentToolExecutor;
import dev.openallay.context.CallerKind;
import dev.openallay.context.CallerSnapshot;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ContextMetrics;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelFailure;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.anthropic.AnthropicJsonCodec;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.openai.OpenAiJsonCodec;
import dev.openallay.skill.LoadSkillTool;
import dev.openallay.skill.SkillDocument;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.skill.SkillSource;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.builtin.RunJavascriptTool;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** In-process lifecycle proof. No live provider, JavaScript runtime, or game client is used. */
final class AgentModelContextLifecycleTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000017");
    private static final String LOAD_SKILL = "openallay__load_skill";
    private static final String RUN_JAVASCRIPT = "openallay__run_javascript";
    private static final String FILTER_SOURCE = """
            return mc.game.diagnostics.filter(function (value) {
              return value.name === "biome";
            });
            """.strip();
    private static final String FILTER_ERROR = ".filter is undefined";
    private static final String FILTER_RESULT =
            "status: failure\ncode: javascript_error\nmessage: " + FILTER_ERROR;

    @Test
    void secondQuestionReadsRealRetainedSkillAndDeclaredReferenceThenEmitsItsProgram()
            throws IOException {
        SkillFixture skill = bundledCommandSkill();
        ToolRegistry registry = new ToolRegistry();
        registry.register("openallay", List.of(new LoadSkillTool(skill.repository().snapshot(Set.of()))));
        ScriptedJavascriptTools javascript = new ScriptedJavascriptTools();
        String expectedProgram = commandCatalogProgram(skill.reference()).replace("examplemod", "testmod");
        javascript.enqueue((input, cancellation) -> {
            assertEquals(expectedProgram, input.get("source").getAsString());
            assertFalse(input.has("roots"));
            assertTrue(input.get("title").getAsString().contains("commands"));
            return CompletableFuture.completedFuture(success(
                    RunJavascriptTool.ID, "path: testmod inspect\nkind: literal\nexecutable: true"));
        });
        AgentToolExecutor tools = new CompositeAgentToolExecutor(List.of(
                new LocalAgentToolExecutor(registry, new Gson()), javascript));
        ScriptedModelClient model = new ScriptedModelClient();
        model.enqueue(request -> completed(toolTurn("load_entry", LOAD_SKILL,
                JsonParser.parseString("{\"name\":\"run-game-commands\"}").getAsJsonObject())));
        model.enqueue(request -> {
            assertTrue(modelText(request.messages()).contains(skill.document().instructions()));
            assertTrue(modelText(request.messages()).contains("references/commands.md"));
            return completed(toolTurn("load_reference", LOAD_SKILL, JsonParser.parseString(
                    "{\"name\":\"run-game-commands\",\"reference\":\"references/commands.md\"}")
                    .getAsJsonObject()));
        });
        model.enqueue(request -> {
            assertEquals(skill.reference(), retainedDocument(request.messages(), "references/commands.md"));
            return completed(textTurn("The command workflow is ready."));
        });
        model.enqueue(request -> {
            // The model derives its next program from actual retained instructions, not a
            // test-only summary, metadata receipt, or synthetic durableProjection call.
            assertTrue(modelText(request.messages()).contains(skill.document().instructions()));
            String reference = retainedDocument(request.messages(), "references/commands.md");
            assertEquals(skill.reference(), reference);
            String source = commandCatalogProgram(reference).replace("examplemod", "testmod");
            return completed(toolTurn("query_commands", RUN_JAVASCRIPT,
                    javascriptInput(source, "Inspect testmod commands")));
        });
        model.enqueue(request -> {
            assertEquals(expectedProgram, toolUse(request.messages(), "query_commands").input()
                    .get("source").getAsString());
            assertFalse(toolResult(request.messages(), "query_commands").error());
            return completed(textTurn("The visible command is testmod inspect."));
        });
        AgentSessionStore sessions = new AgentSessionStore();
        GameGuideAgent agent = new GameGuideAgent(model, tools, sessions, new Gson());
        List<AgentEvent> firstEvents = new ArrayList<>();
        List<AgentEvent> secondEvents = new ArrayList<>();
        AgentRequest first = request("load-workflow", "Prepare the command discovery workflow.");
        AgentRequest second = request("reuse-workflow", "Now list the visible testmod commands.");

        var firstResult = agent.ask(first, firstEvents::add).join();
        assertTrue(firstResult.successful(), firstResult::errorMessage);
        List<ModelMessage> firstContext = lastContext(firstEvents).messages();
        var secondResult = agent.ask(second, secondEvents::add).join();
        assertTrue(secondResult.successful(), secondResult::errorMessage);

        assertEquals(5, model.requests.size());
        assertEquals(1, javascript.inputs.size());
        assertEquals(2, started(firstEvents, "openallay:load_skill"));
        assertEquals(0, started(secondEvents, "openallay:load_skill"),
                "retained instructions must not need another load_skill invocation");
        assertEquals(1, started(secondEvents, RunJavascriptTool.ID));
        ModelRequest secondDispatch = model.requests.get(3);
        assertEquals(firstContext, secondDispatch.messages().subList(0, firstContext.size()));
        assertEquals(ModelMessage.userText(second.userMessage()), secondDispatch.messages().getLast());
        assertEquals(firstContext, lastContext(firstEvents).requestMessages());
        AgentEvent.ContextUpdated secondContext = lastContext(secondEvents);
        assertEquals(secondContext.messages().subList(firstContext.size(), secondContext.messages().size()),
                secondContext.requestMessages());
        assertEquals(ModelMessage.userText(second.userMessage()), secondContext.requestMessages().getFirst());
        assertEquals(expectedProgram, toolUse(secondContext.requestMessages(), "query_commands").input()
                .get("source").getAsString());
        assertTrue(modelText(secondContext.messages()).contains(skill.reference()));
        assertStructured(firstEvents, secondEvents, model.requests);
        assertActualProviderBodiesRetainSkill(secondDispatch, skill);
        assertTrue(model.scripts.isEmpty());
        assertFalse(sessions.status(first.sessionKey()).active());
    }

    @Test
    void exactJavascriptErrorSourceAndFlagSurviveNextQuestionAndOriginalRequestEvent() {
        ScriptedJavascriptTools tools = new ScriptedJavascriptTools();
        tools.enqueue((input, cancellation) -> CompletableFuture.completedFuture(
                failure(RunJavascriptTool.ID, "javascript_error", FILTER_ERROR)));
        ScriptedModelClient model = new ScriptedModelClient();
        model.enqueue(request -> completed(toolTurn("filter_call", RUN_JAVASCRIPT,
                javascriptInput(FILTER_SOURCE, "Find biome"))));
        model.enqueue(request -> {
            assertFilterFailure(request.messages());
            return completed(textTurn("The diagnostics lookup failed."));
        });
        model.enqueue(request -> {
            assertFilterFailure(request.messages());
            return completed(textTurn("The failed code called filter on an object, not its values array."));
        });
        AgentSessionStore sessions = new AgentSessionStore();
        GameGuideAgent agent = new GameGuideAgent(model, tools, sessions, new Gson());
        AgentRequest first = request("filter-failure", "Find my biome using diagnostics.");
        List<AgentEvent> firstEvents = new ArrayList<>();
        List<AgentEvent> secondEvents = new ArrayList<>();

        assertTrue(agent.ask(first, firstEvents::add).join().successful());
        AgentEvent.ContextUpdated original = lastContext(firstEvents);
        assertEquals(original.messages(), original.requestMessages());
        assertFilterFailure(original.requestMessages());
        assertEquals(FILTER_ERROR, firstEvents.stream()
                .filter(AgentEvent.ToolCompleted.class::isInstance)
                .map(AgentEvent.ToolCompleted.class::cast)
                .findFirst().orElseThrow().normalized().get("message").getAsString());
        assertTrue(agent.ask(request("filter-followup", "Explain the exact previous failure."),
                secondEvents::add).join().successful());

        assertFilterFailure(model.requests.get(2).messages());
        assertEquals(original.messages(), model.requests.get(2).messages()
                .subList(0, original.messages().size()));
        assertFilterFailure(new ModelContextCodec().decode(
                new ModelContextCodec().encode(model.requests.get(2).messages())));
        assertEquals(1, tools.inputs.size());
        assertStructured(firstEvents, secondEvents, model.requests);
    }

    @Test
    void actualProviderFailureRetainsUnansweredUserAskAndExplicitLabeledNote() {
        ScriptedModelClient model = new ScriptedModelClient();
        model.enqueue(request -> CompletableFuture.failedFuture(new ModelClientException(
                new ModelFailure("provider_unavailable", "Provider is unavailable", null))));
        model.enqueue(request -> {
            assertEquals(ModelMessage.userText("Which visible commands are available?"),
                    request.messages().getFirst());
            assertEquals(new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text(
                    "[OpenAllay request ended: provider_unavailable] Provider is unavailable"))),
                    request.messages().get(1));
            assertEquals(ModelMessage.userText("Try the same question again."), request.messages().getLast());
            return completed(textTurn("No command evidence was collected by the failed request."));
        });
        AgentSessionStore sessions = new AgentSessionStore();
        ScriptedJavascriptTools tools = new ScriptedJavascriptTools();
        GameGuideAgent agent = new GameGuideAgent(model, tools, sessions, new Gson());
        AgentRequest first = request("provider-failure", "Which visible commands are available?");
        List<AgentEvent> firstEvents = new ArrayList<>();
        List<AgentEvent> secondEvents = new ArrayList<>();

        AgentResult failed = agent.ask(first, firstEvents::add).join();

        assertEquals(AgentState.FAILED, failed.state());
        assertEquals("provider_unavailable", failed.errorCode());
        assertEquals("Provider is unavailable", failed.errorMessage());
        AgentEvent.ContextUpdated context = lastContext(firstEvents);
        assertEquals(2, context.messages().size());
        assertEquals(context.messages(), context.requestMessages());
        assertEquals(ModelMessage.userText(first.userMessage()), context.requestMessages().getFirst());
        assertEquals("[OpenAllay request ended: provider_unavailable] Provider is unavailable",
                assertInstanceOf(ModelContent.Text.class,
                        context.requestMessages().getLast().content().getFirst()).text());
        assertFalse(sessions.status(first.sessionKey()).active());
        assertTrue(agent.ask(request("provider-retry", "Try the same question again."),
                secondEvents::add).join().successful());
        assertEquals(0, tools.inputs.size());
        assertStructured(firstEvents, secondEvents, model.requests);
    }

    @Test
    void cancellationAfterCompletedParallelToolsRetainsOrderedPairsWithoutOrphanCalls() {
        PendingJavascriptTools tools = new PendingJavascriptTools();
        ScriptedModelClient model = new ScriptedModelClient();
        model.enqueue(request -> completed(parallelTurn()));
        CompletableFuture<ModelTurn> continuation = new CompletableFuture<>();
        CompletableFuture<ModelRequest> continuationDispatched = new CompletableFuture<>();
        model.enqueue(request -> {
            continuationDispatched.complete(request);
            return continuation;
        });
        model.enqueue(request -> {
            assertParallelPairs(request.messages(), false);
            assertTrue(modelText(request.messages()).contains("[OpenAllay request ended: agent_cancelled]"));
            return completed(textTurn("Both completed reads remain available."));
        });
        AgentSessionStore sessions = new AgentSessionStore();
        GameGuideAgent agent = new GameGuideAgent(model, tools, sessions, new Gson());
        AgentRequest first = request("cancel-after-parallel", "Read both facts.");
        List<AgentEvent> firstEvents = new ArrayList<>();
        List<AgentEvent> secondEvents = new ArrayList<>();

        CompletableFuture<AgentResult> running = agent.ask(first, firstEvents::add);

        assertEquals(Set.of("return 1;", "return 2;"), tools.pending.keySet());
        tools.complete("return 2;", "fact: second");
        assertFalse(running.isDone());
        assertEquals(1, model.requests.size(), "parallel tools must join before a continuation");
        tools.complete("return 1;", "fact: first");
        ModelRequest continued = awaitResult(continuationDispatched);
        assertEquals(2, model.requests.size());
        assertParallelPairs(continued.messages(), false);
        assertFalse(running.isDone());
        assertTrue(sessions.cancel(first.sessionKey()));
        assertEquals(AgentState.CANCELLED, awaitResult(running).state());
        assertParallelPairs(lastFinalized(firstEvents).requestMessages(), false);
        assertTrue(agent.ask(request("parallel-followup", "Reuse the completed reads."),
                secondEvents::add).join().successful());
        assertEquals(2, tools.inputs.size());
        assertParallelPairs(model.requests.get(2).messages(), false);
        assertStructured(firstEvents, secondEvents, model.requests);
    }

    @Test
    void cancellationDuringParallelToolsKeepsCompletedEvidenceAndPairsCancelledResult() {
        PendingJavascriptTools tools = new PendingJavascriptTools();
        ScriptedModelClient model = new ScriptedModelClient();
        model.enqueue(request -> completed(parallelTurn()));
        model.enqueue(request -> {
            assertParallelPairs(request.messages(), true);
            return completed(textTurn("One read completed; the other was cancelled."));
        });
        AgentSessionStore sessions = new AgentSessionStore();
        GameGuideAgent agent = new GameGuideAgent(model, tools, sessions, new Gson());
        AgentRequest first = request("cancel-during-parallel", "Read both facts.");
        List<AgentEvent> firstEvents = new ArrayList<>();
        List<AgentEvent> secondEvents = new ArrayList<>();

        CompletableFuture<AgentResult> running = agent.ask(first, firstEvents::add);
        tools.complete("return 1;", "fact: first");
        assertFalse(running.isDone());
        assertTrue(sessions.cancel(first.sessionKey()));

        assertEquals(AgentState.CANCELLED, awaitResult(running).state());
        assertEquals(1, model.requests.size(), "a cancelled tool turn must not dispatch another model call");
        assertFalse(tools.pending.get("return 2;").isDone(), "the raw peer does not cooperate with cancel");
        assertParallelPairs(lastFinalized(firstEvents).messages(), true);
        assertParallelPairs(lastFinalized(firstEvents).requestMessages(), true);
        int terminalEvents = firstEvents.size();
        tools.complete("return 2;", "late fact must not replace cancellation");
        assertEquals(terminalEvents, firstEvents.size(), "late raw results cannot append terminal context");
        assertTrue(agent.ask(request("partial-parallel-followup", "Which read completed?"),
                secondEvents::add).join().successful());
        assertEquals(2, tools.inputs.size());
        assertStructured(firstEvents, secondEvents, model.requests);
    }

    @Test
    void cancellationInsideFirstLaunchPairsUnstartedAdvertisedCallAndClosesBeforeLeaseRelease() {
        AgentSessionStore sessions = new AgentSessionStore();
        AgentRequest first = request("cancel-midlaunch", "Read both facts.");
        List<AgentSessionStore.Status> statusAtClose = new ArrayList<>();
        List<Boolean> cancellationAtClose = new ArrayList<>();
        List<String> closed = new ArrayList<>();
        ScriptedJavascriptTools tools = new ScriptedJavascriptTools() {
            private CancellationSignal requestCancellation;

            @Override
            public CompletableFuture<AgentToolResult> execute(
                    String name, JsonObject input, ToolInvocationContext context,
                    CancellationSignal cancellation) {
                inputs.add(input.deepCopy());
                requestCancellation = cancellation;
                assertTrue(sessions.cancel(first.sessionKey()));
                return new CompletableFuture<>();
            }

            @Override
            public void closeRequestScope(String correlationId) {
                cancellationAtClose.add(requestCancellation.isCancelled());
                statusAtClose.add(sessions.status(first.sessionKey()));
                closed.add(correlationId);
                throw new IllegalStateException("cleanup must not replace the cancelled outcome");
            }
        };
        ScriptedModelClient model = new ScriptedModelClient();
        model.enqueue(request -> completed(parallelTurn()));
        GameGuideAgent agent = new GameGuideAgent(model, tools, sessions, new Gson());
        List<AgentEvent> events = new ArrayList<>();

        CompletableFuture<AgentResult> running = agent.ask(first, events::add);

        AgentResult result = awaitResult(running);
        assertEquals(AgentState.CANCELLED, result.state());
        assertEquals("agent_cancelled", result.errorCode());
        assertEquals(1, model.requests.size(), "midlaunch cancellation must not dispatch a continuation");
        assertEquals(1, tools.inputs.size(), "the advertised peer is paired but must not start execution");
        assertEquals(1, started(events, RunJavascriptTool.ID));
        List<ModelMessage> retained = lastFinalized(events).requestMessages();
        assertDoesNotThrow(() -> ContextStructure.units(retained));
        for (String id : List.of("parallel_first", "parallel_second")) {
            ModelContent.ToolResult paired = toolResult(retained, id);
            assertTrue(paired.error());
            assertTrue(paired.value().getAsString().startsWith("status: failure\ncode: agent_cancelled\n"));
        }
        assertEquals(List.of(first.context().correlationId()), closed);
        assertEquals(List.of(true), cancellationAtClose);
        assertEquals(1, statusAtClose.size());
        assertFalse(statusAtClose.getFirst().active(), "logical admission is released before async physical cleanup");
        assertEquals(null, statusAtClose.getFirst().requestId());
        assertFalse(sessions.status(first.sessionKey()).active());
        assertTrue(events.contains(new AgentEvent.Failed("agent_cancelled", "Agent request was cancelled")));
        assertStructured(events, List.of(), model.requests);
    }

    private static void assertFilterFailure(List<ModelMessage> messages) {
        ModelContent.ToolUse use = toolUse(messages, "filter_call");
        assertEquals(RUN_JAVASCRIPT, use.name());
        assertEquals(FILTER_SOURCE, use.input().get("source").getAsString());
        assertFalse(use.input().has("durableProjection"));
        ModelContent.ToolResult result = toolResult(messages, "filter_call");
        assertTrue(result.error());
        assertEquals(FILTER_RESULT, result.value().getAsString());
    }

    private static void assertParallelPairs(List<ModelMessage> messages, boolean secondCancelled) {
        assertDoesNotThrow(() -> ContextStructure.units(messages));
        ContextStructure.Unit exchange = ContextStructure.units(messages).stream()
                .filter(ContextStructure.Unit::toolExchange).findFirst().orElseThrow();
        List<ModelContent> uses = exchange.messages().getFirst().content();
        List<ModelContent> results = exchange.messages().getLast().content();
        assertEquals(List.of("parallel_first", "parallel_second"), uses.stream()
                .map(ModelContent.ToolUse.class::cast).map(ModelContent.ToolUse::id).toList());
        assertEquals(List.of("parallel_first", "parallel_second"), results.stream()
                .map(ModelContent.ToolResult.class::cast).map(ModelContent.ToolResult::toolUseId).toList());
        ModelContent.ToolResult first = (ModelContent.ToolResult) results.getFirst();
        ModelContent.ToolResult second = (ModelContent.ToolResult) results.getLast();
        assertFalse(first.error());
        assertEquals("fact: first", first.value().getAsString());
        assertEquals(secondCancelled, second.error());
        if (secondCancelled) {
            assertTrue(second.value().getAsString().startsWith(
                    "status: failure\ncode: agent_cancelled\nmessage: "));
        } else {
            assertEquals("fact: second", second.value().getAsString());
        }
    }

    private static void assertStructured(
            List<AgentEvent> firstEvents, List<AgentEvent> secondEvents, List<ModelRequest> requests) {
        requests.forEach(request -> assertDoesNotThrow(() -> ContextStructure.units(request.messages())));
        for (List<AgentEvent> events : List.of(firstEvents, secondEvents)) {
            for (AgentEvent event : events) {
                if (event instanceof AgentEvent.ContextUpdated context) {
                    assertDoesNotThrow(() -> ContextStructure.units(context.messages()));
                    assertDoesNotThrow(() -> ContextStructure.units(context.requestMessages()));
                    assertEquals(context.messages(), new ModelContextCodec().decode(
                            new ModelContextCodec().encode(context.messages())));
                }
            }
        }
    }

    private static void assertActualProviderBodiesRetainSkill(ModelRequest request, SkillFixture skill) {
        // Only local JSON encoders run. No provider client or transport is constructed.
        JsonObject openAi = JsonParser.parseString(new OpenAiJsonCodec(new Gson())
                .requestBody(config(ModelProtocol.OPENAI_CHAT), request)).getAsJsonObject();
        JsonObject anthropic = JsonParser.parseString(new AnthropicJsonCodec(new Gson())
                .requestBody(config(ModelProtocol.ANTHROPIC_MESSAGES), request)).getAsJsonObject();
        List<String> openAiUses = new ArrayList<>();
        List<String> openAiResults = new ArrayList<>();
        List<String> openAiResultText = new ArrayList<>();
        for (var raw : openAi.getAsJsonArray("messages")) {
            JsonObject message = raw.getAsJsonObject();
            if (message.has("tool_calls")) {
                for (var call : message.getAsJsonArray("tool_calls")) {
                    JsonObject encoded = call.getAsJsonObject();
                    openAiUses.add(encoded.get("id").getAsString());
                    JsonObject input = JsonParser.parseString(encoded.getAsJsonObject("function")
                            .get("arguments").getAsString()).getAsJsonObject();
                    assertFalse(input.has("durableProjection"));
                }
            }
            if (message.has("tool_call_id")) {
                openAiResults.add(message.get("tool_call_id").getAsString());
                openAiResultText.add(message.get("content").getAsString());
            }
        }
        List<String> anthropicUses = new ArrayList<>();
        List<String> anthropicResults = new ArrayList<>();
        List<String> anthropicResultText = new ArrayList<>();
        for (var raw : anthropic.getAsJsonArray("messages")) {
            for (var block : raw.getAsJsonObject().getAsJsonArray("content")) {
                JsonObject content = block.getAsJsonObject();
                switch (content.get("type").getAsString()) {
                    case "tool_use" -> {
                        anthropicUses.add(content.get("id").getAsString());
                        assertFalse(content.getAsJsonObject("input").has("durableProjection"));
                    }
                    case "tool_result" -> {
                        anthropicResults.add(content.get("tool_use_id").getAsString());
                        anthropicResultText.add(content.get("content").getAsString());
                        assertFalse(content.get("is_error").getAsBoolean());
                    }
                    default -> assertEquals("text", content.get("type").getAsString());
                }
            }
        }
        assertEquals(List.of("load_entry", "load_reference"), openAiUses);
        assertEquals(openAiUses, openAiResults);
        assertEquals(openAiUses, anthropicUses);
        assertEquals(anthropicUses, anthropicResults);
        assertTrue(openAiResultText.getFirst().contains(skill.document().instructions()));
        assertTrue(openAiResultText.getLast().endsWith(skill.reference()));
        assertEquals(openAiResultText, anthropicResultText);
    }

    private static ModelConfig config(ModelProtocol protocol) {
        return new ModelConfig(true, protocol, URI.create("https://example.invalid/v1/"),
                "scripted-model", SecretValue.of("not-a-live-credential"), 128_000, 1024,
                Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    @Test
    void logicalCancelSettlesNoncooperativeModelAndAdmitsFencedNextQuestion() {
        AgentSessionStore sessions = new AgentSessionStore();
        CompletableFuture<ModelTurn> oldModel = new CompletableFuture<>();
        List<ModelRequest> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
        ModelClient model = (request, events, cancellation) -> {
            requests.add(request);
            return requests.size() == 1 ? oldModel : completed(textTurn("new answer"));
        };
        GameGuideAgent agent = new GameGuideAgent(model, new CompositeAgentToolExecutor(List.of()),
                sessions, new Gson());
        AgentRequest oldRequest = request("noncooperative-old", "old question");
        List<AgentEvent> oldEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        CompletableFuture<AgentResult> oldResult = agent.ask(oldRequest, oldEvents::add);
        assertTrue(sessions.cancel(oldRequest.sessionKey()));
        AgentRequest next = request("noncooperative-new", "new question");
        List<AgentEvent> nextEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        assertTrue(agent.ask(next, nextEvents::add).join().successful());
        assertEquals(AgentState.CANCELLED, oldResult.join().state());
        assertFalse(oldModel.isDone(), "provider future is intentionally noncooperative");
        AgentEvent.ContextFinalized finalized = oldEvents.stream()
                .filter(AgentEvent.ContextFinalized.class::isInstance)
                .map(AgentEvent.ContextFinalized.class::cast).findFirst().orElseThrow();
        assertTrue(modelText(finalized.requestMessages()).contains("old question"));
        assertTrue(modelText(finalized.requestMessages()).contains("agent_cancelled"));
        List<ModelMessage> newContext = lastContext(nextEvents).messages();
        int beforeLate = oldEvents.size();
        oldModel.complete(textTurn("late old answer"));
        assertEquals(beforeLate, oldEvents.size());
        AgentSessionStore.Lease inspection = ((ToolResult.Success<AgentSessionStore.Lease>) sessions.reserve(
                next.sessionKey(), UUID.randomUUID())).value();
        assertEquals(newContext, inspection.history());
        assertFalse(modelText(inspection.history()).contains("late old answer"));
        sessions.finish(inspection, inspection.history());
    }

    @Test
    void blockedCleanupCannotBlockStopAdmissionOrOverwriteSuccessor() throws Exception {
        AgentSessionStore sessions = new AgentSessionStore();
        CompletableFuture<ModelTurn> pendingModel = new CompletableFuture<>();
        java.util.concurrent.CountDownLatch cleanupStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseCleanup = new java.util.concurrent.CountDownLatch(1);
        AgentToolExecutor tools = new AgentToolExecutor() {
            @Override public List<ModelToolDefinition> definitions() { return List.of(); }
            @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
            @Override public CompletableFuture<AgentToolResult> execute(String name, JsonObject input,
                    ToolInvocationContext context, CancellationSignal cancellation) {
                throw new AssertionError("no tool dispatch expected");
            }
            @Override public void closeRequestScope(String correlation) {
                cleanupStarted.countDown();
                try { releaseCleanup.await(); }
                catch (InterruptedException interrupted) { throw new RuntimeException(interrupted); }
            }
        };
        GameGuideAgent agent = new GameGuideAgent((request, events, cancellation) -> pendingModel,
                tools, sessions, new Gson());
        AgentRequest request = request("blocked-cleanup", "old question");
        CompletableFuture<AgentResult> result = agent.ask(request, ignored -> {});
        try {
            assertTrue(sessions.cancel(request.sessionKey()));
            assertTrue(cleanupStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
            AgentSessionStore.Lease successor = ((ToolResult.Success<AgentSessionStore.Lease>) sessions.reserve(
                    request.sessionKey(), UUID.randomUUID())).value();
            assertTrue(sessions.status(request.sessionKey()).active());
            assertFalse(result.isDone());
            releaseCleanup.countDown();
            assertEquals(AgentState.CANCELLED, result.join().state());
            assertTrue(sessions.status(request.sessionKey()).active());
            assertTrue(sessions.finish(successor, successor.history()));
        } finally {
            releaseCleanup.countDown();
        }
    }

    @Test
    void completedFirstToolAndCancelledLaunchArchiveTruthWithoutOverwritingRetry() {
        AgentSessionStore sessions = new AgentSessionStore();
        AgentRequest oldRequest = request("predeclared-old", "old parallel question");
        ScriptedJavascriptTools tools = new ScriptedJavascriptTools();
        CompletableFuture<AgentToolResult> lateSecond = new CompletableFuture<>();
        tools.enqueue((input, signal) -> CompletableFuture.completedFuture(
                success(RunJavascriptTool.ID, "actual first result")));
        tools.enqueue((input, signal) -> {
            assertTrue(sessions.cancel(oldRequest.sessionKey()));
            return lateSecond;
        });
        ModelTurn advertised = new ModelTurn("scripted", "scripted-model", List.of(
                new ModelContent.ToolUse("first_actual", RUN_JAVASCRIPT, javascriptInput("return 1;", "first")),
                new ModelContent.ToolUse("second_cancelled", RUN_JAVASCRIPT, javascriptInput("return 2;", "second")),
                new ModelContent.ToolUse("third_unlaunched", RUN_JAVASCRIPT, javascriptInput("return 3;", "third"))),
                "tool_use", ModelUsage.empty());
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        ModelClient model = (request, events, signal) -> completed(
                calls.getAndIncrement() == 0 ? advertised : textTurn("retry answer"));
        GameGuideAgent agent = new GameGuideAgent(model, tools, sessions, new Gson());
        List<AgentEvent> oldEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        CompletableFuture<AgentResult> oldResult = agent.ask(oldRequest, oldEvents::add);
        AgentRequest retry = request("predeclared-retry", "retry question");
        List<AgentEvent> retryEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        assertTrue(agent.ask(retry, retryEvents::add).join().successful());
        assertEquals(AgentState.CANCELLED, oldResult.join().state());
        assertEquals(2, tools.inputs.size(), "third advertised call must never execute");
        AgentEvent.ContextFinalized archive = oldEvents.stream().filter(AgentEvent.ContextFinalized.class::isInstance)
                .map(AgentEvent.ContextFinalized.class::cast).findFirst().orElseThrow();
        ContextStructure.units(archive.requestMessages());
        assertEquals("actual first result", toolResult(archive.requestMessages(), "first_actual").value().getAsString());
        assertFalse(toolResult(archive.requestMessages(), "first_actual").error());
        assertTrue(toolResult(archive.requestMessages(), "second_cancelled").error());
        assertTrue(toolResult(archive.requestMessages(), "third_unlaunched").error());
        int beforeLate = oldEvents.size();
        lateSecond.complete(success(RunJavascriptTool.ID, "late second success"));
        assertEquals(beforeLate, oldEvents.size());
        AgentSessionStore.Lease latest = ((ToolResult.Success<AgentSessionStore.Lease>) sessions.reserve(
                retry.sessionKey(), UUID.randomUUID())).value();
        assertEquals(lastContext(retryEvents).messages(), latest.history());
        sessions.finish(latest, latest.history());
    }

    private static SkillFixture bundledCommandSkill() throws IOException {
        String root = "assets/openallay/openallay_skills/run-game-commands/";
        String entry = resource(root + "SKILL.md");
        String reference = resource(root + "references/commands.md");
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of(RunJavascriptTool.ID));
        assertTrue(repository.reload(List.of(new SkillSource(
                "openallay:bundled", "run-game-commands/SKILL.md", Map.of(
                        "run-game-commands/SKILL.md", entry,
                        "run-game-commands/references/commands.md", reference))), Set.of()));
        SkillDocument document = repository.find("run-game-commands").orElseThrow();
        assertEquals(reference, document.references().get("references/commands.md"));
        return new SkillFixture(repository, document, reference);
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = AgentModelContextLifecycleTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing bundled Skill resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String commandCatalogProgram(String reference) {
        int heading = reference.indexOf("## Discover the active registry");
        int start = reference.indexOf("```javascript\n", heading);
        int end = reference.indexOf("\n```", start);
        assertTrue(heading >= 0 && start >= heading && end > start);
        String source = reference.substring(start + "```javascript\n".length(), end);
        assertTrue(source.contains("var catalog = commands.list();"));
        assertTrue(source.contains("return catalog.nodes"));
        assertTrue(source.contains(".filter(function (node)"));
        assertTrue(source.contains(".map(function (node)"));
        return source;
    }

    private static String retainedDocument(List<ModelMessage> messages, String document) {
        String prefix = "document: " + document + "\n";
        String delivered = messages.stream().flatMap(message -> message.content().stream())
                .filter(ModelContent.ToolResult.class::isInstance)
                .map(ModelContent.ToolResult.class::cast)
                .map(result -> result.value().getAsString())
                .filter(text -> text.startsWith("skill_instructions\n") && text.contains(prefix))
                .findFirst().orElseThrow();
        int start = delivered.indexOf("content:\n");
        assertTrue(start >= 0, "an already-loaded receipt is not an instruction document");
        return delivered.substring(start + "content:\n".length());
    }

    private static String modelText(List<ModelMessage> messages) {
        List<String> text = new ArrayList<>();
        for (ModelMessage message : messages) {
            for (ModelContent content : message.content()) {
                if (content instanceof ModelContent.Text value) text.add(value.text());
                else if (content instanceof ModelContent.ToolResult value) text.add(value.value().getAsString());
            }
        }
        return String.join("\n", text);
    }

    private static ModelContent.ToolUse toolUse(List<ModelMessage> messages, String id) {
        return messages.stream().flatMap(message -> message.content().stream())
                .filter(ModelContent.ToolUse.class::isInstance).map(ModelContent.ToolUse.class::cast)
                .filter(use -> use.id().equals(id)).findFirst().orElseThrow();
    }

    private static ModelContent.ToolResult toolResult(List<ModelMessage> messages, String id) {
        return messages.stream().flatMap(message -> message.content().stream())
                .filter(ModelContent.ToolResult.class::isInstance).map(ModelContent.ToolResult.class::cast)
                .filter(result -> result.toolUseId().equals(id)).findFirst().orElseThrow();
    }

    private static <T> T awaitResult(CompletableFuture<T> result) {
        try {
            return result.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("the observed operation must settle without its raw provider future", failure);
        }
    }

    private static AgentEvent.ContextFinalized lastFinalized(List<AgentEvent> events) {
        return events.stream().filter(AgentEvent.ContextFinalized.class::isInstance)
                .map(AgentEvent.ContextFinalized.class::cast).reduce((first, last) -> last).orElseThrow();
    }

    private static AgentEvent.ContextUpdated lastContext(List<AgentEvent> events) {
        return events.stream().filter(AgentEvent.ContextUpdated.class::isInstance)
                .map(AgentEvent.ContextUpdated.class::cast).reduce((first, last) -> last).orElseThrow();
    }

    private static long started(List<AgentEvent> events, String toolId) {
        return events.stream().filter(AgentEvent.ToolStarted.class::isInstance)
                .map(AgentEvent.ToolStarted.class::cast).filter(event -> event.toolId().equals(toolId)).count();
    }

    private static AgentRequest request(String id, String question) {
        UUID requestId = UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
        ToolInvocationContext context = new ToolInvocationContext(
                id, Instant.parse("2026-10-01T00:00:00Z"),
                new CallerSnapshot(CallerKind.CONSOLE, null, "Test console", true),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                new ContextMetrics(0, 0, 0, 0, 0));
        return new AgentRequest(requestId, ACTOR, "model-context-lifecycle", question,
                "Use retained workflow instructions and observed tool results. Do not invent evidence.",
                context, false);
    }

    private static JsonObject javascriptInput(String source, String title) {
        JsonObject input = new JsonObject();
        input.addProperty("source", source);
        input.addProperty("title", title);
        input.addProperty("description", "Read the requested visible data.");
        return input;
    }

    private static ModelTurn parallelTurn() {
        return new ModelTurn("scripted", "scripted-model", List.of(
                new ModelContent.ToolUse("parallel_first", RUN_JAVASCRIPT,
                        javascriptInput("return 1;", "Read first fact")),
                new ModelContent.ToolUse("parallel_second", RUN_JAVASCRIPT,
                        javascriptInput("return 2;", "Read second fact"))),
                "tool_use", ModelUsage.empty());
    }

    private static ModelTurn toolTurn(String id, String name, JsonObject input) {
        return new ModelTurn("scripted", "scripted-model",
                List.of(new ModelContent.ToolUse(id, name, input)), "tool_use", ModelUsage.empty());
    }

    private static ModelTurn textTurn(String text) {
        return new ModelTurn("scripted", "scripted-model", List.of(new ModelContent.Text(text)),
                "end_turn", ModelUsage.empty());
    }

    private static CompletableFuture<ModelTurn> completed(ModelTurn turn) {
        return CompletableFuture.completedFuture(turn);
    }

    private static AgentToolResult success(String toolId, String modelText) {
        JsonObject normalized = new JsonObject();
        normalized.addProperty("status", "success");
        normalized.add("value", new JsonObject());
        normalized.addProperty("modelText", modelText);
        return new AgentToolResult(toolId, normalized, false);
    }

    private static AgentToolResult failure(String toolId, String code, String message) {
        JsonObject normalized = new JsonObject();
        normalized.addProperty("status", "failure");
        normalized.addProperty("code", code);
        normalized.addProperty("message", message);
        return new AgentToolResult(toolId, normalized, true);
    }

    private record SkillFixture(SkillRepository repository, SkillDocument document, String reference) {}

    private static final class ScriptedModelClient implements ModelClient {
        private final Deque<Function<ModelRequest, CompletableFuture<ModelTurn>>> scripts = new ArrayDeque<>();
        private final List<ModelRequest> requests = new ArrayList<>();

        void enqueue(Function<ModelRequest, CompletableFuture<ModelTurn>> script) { scripts.addLast(script); }

        @Override
        public CompletableFuture<ModelTurn> complete(
                ModelRequest request, Consumer<ModelEvent> events, CancellationSignal cancellation) {
            requests.add(request);
            CompletableFuture<ModelTurn> turn = scripts.removeFirst().apply(request);
            cancellation.onCancel(() -> turn.completeExceptionally(new ModelClientException(
                    new ModelFailure("agent_cancelled", "Agent request was cancelled", null))));
            return turn;
        }
    }

    @FunctionalInterface
    private interface JavascriptScript {
        CompletableFuture<AgentToolResult> apply(JsonObject input, CancellationSignal cancellation);
    }

    private static class ScriptedJavascriptTools implements AgentToolExecutor {
        private final Deque<JavascriptScript> scripts = new ArrayDeque<>();
        protected final List<JsonObject> inputs = new ArrayList<>();

        void enqueue(JavascriptScript script) { scripts.addLast(script); }

        @Override
        public List<ModelToolDefinition> definitions() {
            return List.of(new ModelToolDefinition(RUN_JAVASCRIPT, "Read detached game data",
                    JsonParser.parseString("{\"type\":\"object\"}").getAsJsonObject()));
        }

        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }

        @Override
        public Optional<String> canonicalToolId(String name) {
            return name.equals(RUN_JAVASCRIPT) || name.equals(RunJavascriptTool.ID)
                    ? Optional.of(RunJavascriptTool.ID) : Optional.empty();
        }

        @Override
        public CompletableFuture<AgentToolResult> execute(
                String name, JsonObject input, ToolInvocationContext context, CancellationSignal cancellation) {
            inputs.add(input.deepCopy());
            return scripts.removeFirst().apply(input, cancellation);
        }
    }

    private static final class PendingJavascriptTools extends ScriptedJavascriptTools {
        private final Map<String, CompletableFuture<AgentToolResult>> pending = new LinkedHashMap<>();

        @Override
        public CompletableFuture<AgentToolResult> execute(
                String name, JsonObject input, ToolInvocationContext context, CancellationSignal cancellation) {
            inputs.add(input.deepCopy());
            CompletableFuture<AgentToolResult> result = new CompletableFuture<>();
            pending.put(input.get("source").getAsString(), result);
            // Deliberately noncooperative. The Agent must settle its own cancelled result
            // without waiting for this raw future or mutating an already completed peer.
            return result;
        }

        void complete(String source, String text) {
            assertTrue(pending.get(source).complete(success(RunJavascriptTool.ID, text)));
        }
    }
}
