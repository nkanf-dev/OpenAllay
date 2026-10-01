package dev.openallay.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.agent.context.*;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.tool.*;
import dev.openallay.context.*;
import dev.openallay.model.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class GameGuideAgentRedactionTest {
    private static final String SECRET = "opaqueSavedCredentialAlpha";
    private static final Gson GSON = new Gson();

    @Test
    void scrubsArgumentsResultsNextModelCurrentOriginalHistoryUiAndFailures() {
        KnownSecretRedactor redactor = new KnownSecretRedactor(Set.of(SECRET));
        List<ModelRequest> requests = new ArrayList<>();
        List<JsonObject> executed = new ArrayList<>();
        List<AgentEvent> events = new ArrayList<>();
        JsonObject input = new JsonObject();
        input.addProperty("source", "return '" + SECRET + "';");
        input.addProperty(SECRET, "ordinary");
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
                    List.of(new ModelContent.Text("answer " + SECRET)), "stop", ModelUsage.empty()));
        };
        JsonObject output = new JsonObject();
        output.addProperty("status", "success");
        JsonObject preview = new JsonObject();
        preview.addProperty(SECRET, SECRET);
        preview.addProperty("fact", 42);
        output.add("value", preview);
        output.addProperty("modelText", "observed " + SECRET + "\nordinary fact: 42");
        AgentToolExecutor tools = new AgentToolExecutor() {
            public List<ModelToolDefinition> definitions() {
                return List.of(new ModelToolDefinition("test__fact", "Return a fact", new JsonObject()));
            }
            public Set<ContextCapability> requiredContext() { return Set.of(); }
            public CompletableFuture<AgentToolResult> execute(String name, JsonObject arguments,
                    ToolInvocationContext context, CancellationSignal cancellation) {
                executed.add(arguments);
                // Models may re-open raw canonical workspace data or reconstruct its JSON. Every
                // returned projection must go through the same outbound result boundary again.
                return CompletableFuture.completedFuture(new AgentToolResult("test__fact", output, false));
            }
        };
        AgentSessionStore sessions = new AgentSessionStore();
        GameGuideAgent agent = new GameGuideAgent(model, tools, sessions, GSON, null,
                (request, tokens) -> {}, redactor);
        UUID actor = UUID.randomUUID();
        AgentRequest request = request(actor, "question " + SECRET);
        AgentResult result = agent.ask(request, events::add).join();
        assertTrue(result.successful());
        assertEquals(2, requests.size());
        assertFalse(GSON.toJson(requests).contains(SECRET));
        assertFalse(GSON.toJson(executed).contains(SECRET));
        assertFalse(GSON.toJson(events).contains(SECRET));
        assertFalse(GSON.toJson(result).contains(SECRET));
        assertFalse(GSON.toJson(events).contains("private-reasoning"));
        assertFalse(GSON.toJson(result).contains("private-signature"));
        assertTrue(requests.getLast().messages().stream().flatMap(message -> message.content().stream())
                .filter(ModelContent.ToolResult.class::isInstance).map(ModelContent.ToolResult.class::cast)
                .anyMatch(value -> value.value().getAsString().contains("ordinary fact: 42")));
        AgentEvent.ContextUpdated finalContext = events.stream().filter(AgentEvent.ContextUpdated.class::isInstance)
                .map(AgentEvent.ContextUpdated.class::cast).reduce((first, last) -> last).orElseThrow();
        assertFalse(GSON.toJson(finalContext.requestMessages()).contains(SECRET));
        agent.ask(request(actor, "follow up"), events::add).join();
        assertFalse(GSON.toJson(requests.getLast()).contains(SECRET));
        assertTrue(output.toString().contains(SECRET), "canonical guest result is not mutated");
    }

    @Test
    void reopenedRawWorkspaceJsonIsRedactedAfterReconstructionBeforeNextModel() {
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
                "return {observed: '" + SECRET + "', count: 42};", List.of()), new CancellationSignal()).join();
        var original = ((dev.openallay.tool.ToolResult.Success<dev.openallay.tool.builtin.RunJavascriptTool.Output>) first).value();
        assertTrue(workspaces.open(context.correlationId()).open(original.handle()).toString().contains(SECRET));
        var registry = new dev.openallay.tool.ToolRegistry();
        registry.register("test", List.of(tool));
        List<ModelRequest> requests = new ArrayList<>();
        JsonObject arguments = new JsonObject();
        arguments.addProperty("source", "const raw = workspace.open('" + original.handle()
                + "'); return Object.fromEntries([[raw.observed, raw.observed], ['count', raw.count]]);");
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
                new AgentSessionStore(), GSON, null, (request, tokens) -> {},
                new KnownSecretRedactor(Set.of(SECRET)));
        AgentResult result = agent.ask(new AgentRequest(UUID.randomUUID(), UUID.randomUUID(), "main", "reopen result",
                "Answer using facts", context, false), events::add).join();
        assertTrue(result.successful());
        assertEquals(2, requests.size());
        assertFalse(GSON.toJson(requests).contains(SECRET));
        assertFalse(GSON.toJson(events).contains(SECRET));
        assertFalse(GSON.toJson(result).contains(SECRET));
        AgentEvent.ToolCompleted completed = events.stream().filter(AgentEvent.ToolCompleted.class::isInstance)
                .map(AgentEvent.ToolCompleted.class::cast).findFirst().orElseThrow();
        assertFalse(completed.failure());
        assertEquals(42, completed.normalized().getAsJsonObject("value")
                .getAsJsonObject("preview").get("count").getAsInt());
        assertTrue(original.preview().toString().contains(SECRET), "canonical workspace schema is untouched");
    }

    @Test
    void failedToolResultIsRedactedBeforeContinuationAndOriginalContext() {
        KnownSecretRedactor redactor = new KnownSecretRedactor(Set.of(SECRET));
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
                failure.addProperty("message", "observed " + SECRET);
                return CompletableFuture.completedFuture(new AgentToolResult("test__failure", failure, true));
            }
        };
        List<AgentEvent> events = new ArrayList<>();
        AgentResult result = new GameGuideAgent(model, tools, new AgentSessionStore(), GSON, null,
                (request, tokens) -> {}, redactor).ask(request(UUID.randomUUID(), "question"), events::add).join();
        assertTrue(result.successful());
        assertFalse(GSON.toJson(requests).contains(SECRET));
        assertFalse(GSON.toJson(events).contains(SECRET));
        ModelContent.ToolResult feedback = (ModelContent.ToolResult)
                requests.getLast().messages().getLast().content().getFirst();
        assertTrue(feedback.error());
        assertTrue(feedback.value().getAsString().contains("javascript_error"));
    }

    @Test
    void compactionAndReusedCheckpointNeverSendKnownCredentialsOrReasoning() {
        KnownSecretRedactor redactor = new KnownSecretRedactor(Set.of(SECRET));
        List<ModelRequest> requests = new ArrayList<>();
        String summary = "{\"goals\":[\"" + SECRET + "\"],\"preferences\":[],\"completedTopics\":[],"
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
                new ContextBudget(4000, 100), "model", Clock.systemUTC(), redactor);
        List<ModelMessage> source = List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(
                new ModelContent.Reasoning("private-reasoning", "signature"),
                new ModelContent.Text("history " + SECRET))), ModelMessage.userText("question"));
        ContextCompactor.Result result = compactor.compact("Answer using facts", source, 1, List.of(), false,
                "main", new CancellationSignal()).join();
        assertEquals(1, requests.size());
        assertFalse(GSON.toJson(requests).contains(SECRET));
        assertFalse(GSON.toJson(requests).contains("private-reasoning"));
        assertFalse(GSON.toJson(result).contains(SECRET));
        assertTrue(result.successful());
        assertTrue(compactor.reuse(result.checkpoint(), "Answer using facts", source, 1, List.of()).isPresent());
    }

    private static AgentRequest request(UUID actor, String question) {
        return new AgentRequest(UUID.randomUUID(), actor, "main", question, "Answer using facts",
                ToolInvocationContext.developmentConsole("synthetic-redaction"), false);
    }
}
