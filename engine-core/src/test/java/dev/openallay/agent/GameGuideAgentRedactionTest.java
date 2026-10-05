package dev.openallay.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.agent.context.*;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.tool.*;
import dev.openallay.context.*;
import dev.openallay.json.EngineJson;
import dev.openallay.model.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class GameGuideAgentRedactionTest {
    private static final String PLAYER_VALUE = "synthetic-player-value-alpha";
    private static final Gson GSON = EngineJson.withInstant(new Gson());

    @Test
    void preservesPlayerArgumentsResultsNextModelCurrentOriginalHistoryAndUi() {
        List<ModelRequest> requests = new ArrayList<>();
        List<JsonObject> executed = new ArrayList<>();
        List<AgentEvent> events = new ArrayList<>();
        JsonObject input = new JsonObject();
        input.addProperty("source", "return '" + PLAYER_VALUE + "';");
        input.addProperty(PLAYER_VALUE, "ordinary");
        input.addProperty("token", PLAYER_VALUE);
        input.addProperty("password", "player-password");
        input.addProperty("APIkey", "player-api-value");
        ModelClient model = (request, sink, cancellation) -> {
            requests.add(request);
            if (requests.size() == 1) {
                sink.accept(new ModelEvent.ToolUseComplete("call", "test__fact", input));
                sink.accept(new ModelEvent.ReasoningDelta("private-reasoning"));
                return CompletableFuture.completedFuture(new ModelTurn("test", "model", List.of(
                        new ModelContent.Reasoning("private-reasoning", "private-signature"),
                        new ModelContent.ToolUse("call", "test__fact", input)), "tool_use", ModelUsage.empty()));
            }
            return CompletableFuture.completedFuture(new ModelTurn("test", "model",
                    List.of(new ModelContent.Text("answer " + PLAYER_VALUE)), "stop", ModelUsage.empty()));
        };
        JsonObject output = new JsonObject();
        output.addProperty("status", "success");
        JsonObject preview = new JsonObject();
        preview.addProperty(PLAYER_VALUE, PLAYER_VALUE);
        preview.addProperty("fact", 42);
        preview.addProperty("token", PLAYER_VALUE);
        preview.addProperty("password", "player-password");
        preview.addProperty("APIkey", "player-api-value");
        output.add("value", preview);
        String modelText = "observed " + PLAYER_VALUE + "\nordinary fact: 42\ntoken: " + PLAYER_VALUE
                + "\npassword: player-password\nAPIkey: player-api-value";
        output.addProperty("modelText", modelText);
        AgentToolExecutor tools = new AgentToolExecutor() {
            public List<ModelToolDefinition> definitions() {
                return List.of(new ModelToolDefinition("test__fact", "Return a fact", new JsonObject()));
            }
            public Set<ContextCapability> requiredContext() { return Set.of(); }
            public CompletableFuture<AgentToolResult> execute(String name, JsonObject arguments,
                    ToolInvocationContext context, CancellationSignal cancellation) {
                executed.add(arguments);
                // Player data is not rewritten by field-name or value scanning.
                return CompletableFuture.completedFuture(new AgentToolResult("test__fact", output, false));
            }
        };
        AgentSessionStore sessions = new AgentSessionStore();
        GameGuideAgent agent = new GameGuideAgent(model, tools, sessions, GSON, null,
                (request, tokens) -> {});
        UUID actor = UUID.randomUUID();
        AgentRequest request = request(actor, "question token=" + PLAYER_VALUE + "; password=player-password; APIkey=player-api-value");
        AgentResult result = agent.ask(request, events::add).join();
        assertTrue(result.successful());
        assertEquals(2, requests.size());
        assertEquals(List.of(input), executed);
        assertTrue(requests.getFirst().messages().stream().flatMap(message -> message.content().stream())
                .filter(ModelContent.Text.class::isInstance).map(ModelContent.Text.class::cast)
                .anyMatch(text -> text.text().equals(request.userMessage())));
        ModelContent.ToolUse call = requests.getLast().messages().stream()
                .flatMap(message -> message.content().stream())
                .filter(ModelContent.ToolUse.class::isInstance).map(ModelContent.ToolUse.class::cast)
                .findFirst().orElseThrow();
        ModelContent.ToolResult feedback = requests.getLast().messages().stream()
                .flatMap(message -> message.content().stream())
                .filter(ModelContent.ToolResult.class::isInstance).map(ModelContent.ToolResult.class::cast)
                .findFirst().orElseThrow();
        assertEquals(input, call.input());
        assertEquals("call", call.id());
        assertEquals(call.id(), feedback.toolUseId());
        assertFalse(feedback.error());
        assertEquals(modelText, feedback.value().getAsString());
        AgentEvent.ToolStarted started = events.stream().filter(AgentEvent.ToolStarted.class::isInstance)
                .map(AgentEvent.ToolStarted.class::cast).findFirst().orElseThrow();
        AgentEvent.ToolCompleted completed = events.stream().filter(AgentEvent.ToolCompleted.class::isInstance)
                .map(AgentEvent.ToolCompleted.class::cast).findFirst().orElseThrow();
        assertEquals(input, started.arguments());
        assertEquals(started.invocationId(), completed.invocationId());
        assertFalse(completed.failure());
        assertEquals(output, completed.normalized());
        assertEquals("answer " + PLAYER_VALUE, result.text());
        assertTrue(GSON.toJson(result).contains(PLAYER_VALUE));
        assertFalse(GSON.toJson(requests).contains("private-reasoning"));
        assertFalse(GSON.toJson(events).contains("private-reasoning"));
        assertFalse(GSON.toJson(result).contains("private-reasoning"));
        assertFalse(GSON.toJson(result).contains("private-signature"));
        AgentEvent.ContextUpdated finalContext = events.stream().filter(AgentEvent.ContextUpdated.class::isInstance)
                .map(AgentEvent.ContextUpdated.class::cast).reduce((first, last) -> last).orElseThrow();
        assertTrue(GSON.toJson(finalContext.requestMessages()).contains(PLAYER_VALUE));
        assertEquals(ModelContextCodec.safe(requests.getLast().messages()),
                finalContext.requestMessages().subList(0, requests.getLast().messages().size()));
        assertTrue(agent.ask(request(actor, "follow up"), events::add).join().successful());
        assertTrue(GSON.toJson(requests.getLast()).contains(PLAYER_VALUE));
        assertTrue(GSON.toJson(requests.getLast()).contains("player-password"));
        assertTrue(GSON.toJson(requests.getLast()).contains("player-api-value"));
        assertEquals(preview, output.getAsJsonObject("value"), "canonical guest result is not mutated");
    }

    @Test
    void reopenedWorkspaceJsonPreservesPlayerFieldsAfterReconstructionBeforeNextModel() {
        var workspaces = new dev.openallay.script.workspace.AgentResultWorkspaceRegistry();
        var tool = new dev.openallay.tool.builtin.RunJavascriptTool(
                new dev.openallay.script.RhinoJavascriptRuntime(),
                dev.openallay.script.data.MinecraftAgentHostGraph::new, workspaces,
                new dev.openallay.script.workspace.JavascriptResultPresenter());
        ToolInvocationContext base = ToolInvocationContext.developmentConsole("synthetic-workspace");
        ToolInvocationContext context = new ToolInvocationContext(base.correlationId(), base.capturedAt(),
                base.caller(), base.player(), base.registries(), base.recipes(), base.observableGameState(),
                base.metrics(), true);
        var first = tool.invokeAsync(context, new dev.openallay.tool.builtin.RunJavascriptTool.Input(
                "return {observed: '" + PLAYER_VALUE + "', token: '" + PLAYER_VALUE
                        + "', password: 'player-password', APIkey: 'player-api-value', count: 42};",
                List.of()), new CancellationSignal()).join();
        var original = ((dev.openallay.tool.ToolResult.Success<dev.openallay.tool.builtin.RunJavascriptTool.Output>) first).value();
        assertTrue(workspaces.open(context.correlationId()).open(original.handle()).toString().contains(PLAYER_VALUE));
        var registry = new dev.openallay.tool.ToolRegistry();
        registry.register("test", List.of(tool));
        List<ModelRequest> requests = new ArrayList<>();
        JsonObject arguments = new JsonObject();
        arguments.addProperty("source", "const raw = workspace.open('" + original.handle()
                + "'); return Object.fromEntries([[raw.observed, raw.observed], ['token', raw.token],"
                + " ['password', raw.password], ['APIkey', raw.APIkey], ['count', raw.count]]);");
        var handles = new com.google.gson.JsonArray();
        handles.add(original.handle());
        arguments.add("handles", handles);
        ModelClient model = (request, sink, cancellation) -> {
            requests.add(request);
            return CompletableFuture.completedFuture(new ModelTurn("test", "model",
                    requests.size() == 1
                            ? List.of(new ModelContent.ToolUse("reopen", "openallay__run_javascript", arguments))
                            : List.of(new ModelContent.Text("finished")),
                    requests.size() == 1 ? "tool_use" : "stop", ModelUsage.empty()));
        };
        List<AgentEvent> events = new ArrayList<>();
        var agent = new GameGuideAgent(model, new LocalAgentToolExecutor(registry, GSON),
                new AgentSessionStore(), GSON, null, (request, tokens) -> {});
        AgentResult result = agent.ask(new AgentRequest(UUID.randomUUID(), UUID.randomUUID(), "main", "reopen result",
                "Answer using facts", context, false), events::add).join();
        assertTrue(result.successful());
        assertEquals(2, requests.size());
        ModelContent.ToolResult feedback = (ModelContent.ToolResult)
                requests.getLast().messages().getLast().content().getFirst();
        assertEquals("reopen", feedback.toolUseId());
        assertFalse(feedback.error());
        assertTrue(feedback.value().getAsString().contains(PLAYER_VALUE));
        assertTrue(feedback.value().getAsString().contains("player-password"));
        assertTrue(feedback.value().getAsString().contains("player-api-value"));
        assertTrue(GSON.toJson(events).contains(PLAYER_VALUE));
        assertTrue(GSON.toJson(result).contains(PLAYER_VALUE));
        AgentEvent.ToolCompleted completed = events.stream().filter(AgentEvent.ToolCompleted.class::isInstance)
                .map(AgentEvent.ToolCompleted.class::cast).findFirst().orElseThrow();
        assertFalse(completed.failure());
        JsonObject reconstructed = completed.normalized().getAsJsonObject("value").getAsJsonObject("preview");
        assertEquals(42, reconstructed.get("count").getAsInt());
        assertEquals(PLAYER_VALUE, reconstructed.get(PLAYER_VALUE).getAsString());
        assertEquals(PLAYER_VALUE, reconstructed.get("token").getAsString());
        assertEquals("player-password", reconstructed.get("password").getAsString());
        assertEquals("player-api-value", reconstructed.get("APIkey").getAsString());
        assertEquals(PLAYER_VALUE, original.preview().getAsJsonObject().get("observed").getAsString(),
                "canonical workspace schema is untouched");
    }

    @Test
    void failedToolResultPreservesPlayerMessageAndErrorIdentityInContinuationAndOriginalContext() {
        List<ModelRequest> requests = new ArrayList<>();
        ModelClient model = (request, sink, cancellation) -> {
            requests.add(request);
            return CompletableFuture.completedFuture(new ModelTurn("test", "model",
                    requests.size() == 1
                            ? List.of(new ModelContent.ToolUse("failure-call", "test__failure", new JsonObject()))
                            : List.of(new ModelContent.Text("finished")),
                    requests.size() == 1 ? "tool_use" : "stop", ModelUsage.empty()));
        };
        AgentToolExecutor tools = new AgentToolExecutor() {
            public List<ModelToolDefinition> definitions() {
                return List.of(new ModelToolDefinition("test__failure", "Return failure", new JsonObject()));
            }
            public Set<ContextCapability> requiredContext() { return Set.of(); }
            public CompletableFuture<AgentToolResult> execute(String name, JsonObject arguments,
                    ToolInvocationContext context, CancellationSignal cancellation) {
                JsonObject failure = new JsonObject();
                failure.addProperty("status", "failure");
                failure.addProperty("code", "javascript_error");
                failure.addProperty("message", "observed token=" + PLAYER_VALUE + "; password=player-password; APIkey=player-api-value");
                return CompletableFuture.completedFuture(new AgentToolResult("test__failure", failure, true));
            }
        };
        List<AgentEvent> events = new ArrayList<>();
        AgentResult result = new GameGuideAgent(model, tools, new AgentSessionStore(), GSON, null,
                (request, tokens) -> {}).ask(request(UUID.randomUUID(), "question"), events::add).join();
        assertTrue(result.successful());
        assertEquals(2, requests.size());
        ModelContent.ToolResult feedback = (ModelContent.ToolResult)
                requests.getLast().messages().getLast().content().getFirst();
        assertEquals("failure-call", feedback.toolUseId());
        assertTrue(feedback.error());
        assertTrue(feedback.value().getAsString().contains("javascript_error"));
        assertTrue(feedback.value().getAsString().contains("token=" + PLAYER_VALUE));
        assertTrue(feedback.value().getAsString().contains("password=player-password"));
        assertTrue(feedback.value().getAsString().contains("APIkey=player-api-value"));
        AgentEvent.ToolCompleted completed = events.stream().filter(AgentEvent.ToolCompleted.class::isInstance)
                .map(AgentEvent.ToolCompleted.class::cast).findFirst().orElseThrow();
        assertEquals("failure-call", completed.invocationId());
        assertTrue(completed.failure());
        assertEquals("javascript_error", completed.normalized().get("code").getAsString());
        assertEquals("observed token=" + PLAYER_VALUE + "; password=player-password; APIkey=player-api-value",
                completed.normalized().get("message").getAsString());
        AgentEvent.ContextUpdated context = events.stream().filter(AgentEvent.ContextUpdated.class::isInstance)
                .map(AgentEvent.ContextUpdated.class::cast).reduce((first, last) -> last).orElseThrow();
        assertTrue(GSON.toJson(context.requestMessages()).contains(PLAYER_VALUE));
    }

    @Test
    void compactionAndReusedCheckpointPreservePlayerTextButExcludePrivateReasoning() {
        List<ModelRequest> requests = new ArrayList<>();
        String playerText = "token=" + PLAYER_VALUE + "; password=player-password; APIkey=player-api-value";
        String summary = "{\"goals\":[\"" + playerText + "\"],\"preferences\":[],\"completedTopics\":[],"
                + "\"currentTasks\":[],\"decisions\":[],\"unresolvedQuestions\":[],\"evidenceReferences\":[]}";
        ModelClient model = (request, sink, cancellation) -> {
            requests.add(request);
            return CompletableFuture.completedFuture(new ModelTurn("test", "model",
                    List.of(new ModelContent.Text(summary)), "stop", ModelUsage.empty()));
        };
        ContextTokenEstimator estimator = (system, messages, tools) ->
                messages.size() > 1 && !messages.getFirst().content().toString().contains("derived conversation memory")
                        ? 5000 : 100;
        ContextCompactor compactor = new ContextCompactor(model, GSON, estimator,
                new ContextBudget(4000, 100), "model", Clock.systemUTC());
        List<ModelMessage> source = List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(
                new ModelContent.Reasoning("private-reasoning", "signature"),
                new ModelContent.Text("history " + playerText))), ModelMessage.userText("question"));
        ContextCompactor.Result result = compactor.compact("Answer using facts", source, 1, List.of(), false,
                "main", new CancellationSignal()).join();
        assertEquals(1, requests.size());
        String summaryPayload = assertInstanceOf(ModelContent.Text.class,
                requests.getFirst().messages().getLast().content().getFirst()).text();
        var encodedSource = com.google.gson.JsonParser.parseString(summaryPayload).getAsJsonArray();
        String retainedHistory = encodedSource.get(0).getAsJsonObject().getAsJsonArray("content")
                .get(0).getAsJsonObject().get("text").getAsString();
        assertEquals("history " + playerText, retainedHistory);
        assertTrue(retainedHistory.contains("token=" + PLAYER_VALUE));
        assertTrue(retainedHistory.contains("password=player-password"));
        assertTrue(retainedHistory.contains("APIkey=player-api-value"));
        assertFalse(GSON.toJson(requests).contains("private-reasoning"));
        assertFalse(GSON.toJson(requests).contains("signature"));
        assertTrue(result.successful());
        assertEquals(summary, result.checkpoint().summary());
        assertTrue(compactor.matches(result.checkpoint(), ModelContextCodec.safe(source)));
        assertFalse(GSON.toJson(result).contains("private-reasoning"));
        ContextProjection reused = compactor.reuse(result.checkpoint(), "Answer using facts", source, 1, List.of())
                .orElseThrow();
        assertEquals(result.projection().messages(), reused.messages());
        assertTrue(GSON.toJson(reused.messages()).contains(PLAYER_VALUE));
        assertFalse(GSON.toJson(reused.messages()).contains("private-reasoning"));
    }

    private static AgentRequest request(UUID actor, String question) {
        return new AgentRequest(UUID.randomUUID(), actor, "main", question, "Answer using facts",
                ToolInvocationContext.developmentConsole("synthetic-player-fields"), false);
    }
}
