package dev.openallay.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.session.AgentSessionKey;
import dev.openallay.agent.context.ContextBudget;
import dev.openallay.agent.context.ContextCompactor;
import dev.openallay.agent.context.ToolResultContextReducer;
import dev.openallay.agent.context.Utf8ContextTokenEstimator;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.agent.tool.CompositeAgentToolExecutor;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelFailure;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.time.Clock;
import org.junit.jupiter.api.Test;

final class GameGuideAgentTest {
    @Test
    void localEstimateObserverSeesEachExactModelInputWithoutAddingWireEvents() {
        QueueModelClient model = new QueueModelClient();
        model.enqueue(CompletableFuture.completedFuture(toolTurn("estimate_call", 42)));
        model.enqueue(CompletableFuture.completedFuture(textTurn("finished")));
        List<Integer> estimates = new ArrayList<>();
        List<AgentEvent> events = new ArrayList<>();
        AgentRequest request = request(UUID.randomUUID());
        GameGuideAgent agent = new GameGuideAgent(model, new FakeTools(), new AgentSessionStore(),
                new Gson(), null, (observed, tokens) -> {
                    assertEquals(request.requestId(), observed.requestId());
                    estimates.add(tokens);
                });
        assertTrue(agent.ask(request, events::add).join().successful());
        assertEquals(2, estimates.size());
        var estimator = new Utf8ContextTokenEstimator();
        for (int index = 0; index < model.requests.size(); index++) {
            ModelRequest actual = model.requests.get(index);
            assertEquals(estimator.estimate(actual.systemPrompt(), actual.messages(), actual.tools()),
                    estimates.get(index));
        }
        assertFalse(events.stream().anyMatch(AgentEvent.ContextCompacted.class::isInstance));
        QueueModelClient other = new QueueModelClient();
        other.enqueue(CompletableFuture.completedFuture(textTurn("still works")));
        assertTrue(new GameGuideAgent(other, new FakeTools(), new AgentSessionStore(), new Gson(), null,
                (observed, tokens) -> { throw new IllegalStateException("observer unavailable"); })
                .ask(request(UUID.randomUUID()), ignored -> {}).join().successful());
    }

    @Test
    void executesRealToolFlowAndReturnsGroundedFinalText() {
        QueueModelClient model = new QueueModelClient();
        model.enqueue(CompletableFuture.completedFuture(toolTurn("call_1", 42)));
        model.enqueue(CompletableFuture.completedFuture(textTurn("铁锭事实是 42。")));
        FakeTools tools = new FakeTools();
        AgentSessionStore sessions = new AgentSessionStore();
        GameGuideAgent agent = new GameGuideAgent(model, tools, sessions, new Gson());
        List<AgentEvent> events = new ArrayList<>();

        AgentResult result = agent.ask(request(UUID.randomUUID()), events::add).join();

        assertTrue(result.successful());
        assertEquals("铁锭事实是 42。", result.text());
        assertEquals(1, tools.invocations.get());
        assertEquals(2, model.requests.size());
        ModelMessage toolResults = model.requests.get(1).messages().getLast();
        assertTrue(toolResults.content().getFirst() instanceof ModelContent.ToolResult);
        assertTrue(result.trace().events().stream().anyMatch(event -> event.type().equals("tool_result")));
        assertEquals(AgentState.COMPLETED, result.trace().finalState());
        assertTrue(events.stream().anyMatch(AgentEvent.FinalText.class::isInstance));
        AgentEvent.ToolStarted started = events.stream()
                .filter(AgentEvent.ToolStarted.class::isInstance)
                .map(AgentEvent.ToolStarted.class::cast)
                .findFirst()
                .orElseThrow();
        AgentEvent.ToolCompleted completed = events.stream()
                .filter(AgentEvent.ToolCompleted.class::isInstance)
                .map(AgentEvent.ToolCompleted.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("call_1", started.invocationId());
        assertEquals("call_1", completed.invocationId());
    }

    @Test
    void successfulRequestRevokesCancellationBeforeClosingToolsWithoutChangingCompletion() {
        QueueModelClient model = new QueueModelClient();
        model.enqueue(CompletableFuture.completedFuture(toolTurn("call_scope", 42)));
        CompletableFuture<ModelTurn> finalTurn = new CompletableFuture<>();
        model.enqueue(finalTurn);
        AgentSessionStore sessions = new AgentSessionStore();
        AgentRequest request = request(UUID.randomUUID());
        RequestLifetimeTools tools = new RequestLifetimeTools(sessions, request.sessionKey());
        List<AgentEvent> events = new ArrayList<>();

        CompletableFuture<AgentResult> pending = new GameGuideAgent(
                        model, tools, sessions, new Gson())
                .ask(request, events::add);

        assertNotNull(tools.cancellation);
        assertFalse(tools.cancellation.isCancelled());
        assertTrue(tools.lifecycle.isEmpty());
        assertTrue(finalTurn.complete(textTurn("final answer")));
        AgentResult result = pending.join();

        assertTrue(tools.cancellation.isCancelled());
        assertEquals(List.of("cancel", "close"), tools.lifecycle);
        assertEquals(List.of(request.context().correlationId()), tools.closed);
        assertTrue(tools.cancelledAtClose);
        assertNotNull(tools.sessionAtClose);
        assertTrue(tools.sessionAtClose.active(), "the lease must stay owned until tools close");
        assertEquals(request.requestId(), tools.sessionAtClose.requestId());
        assertFalse(sessions.status(request.sessionKey()).active());
        assertTrue(result.successful());
        assertEquals(AgentState.COMPLETED, result.state());
        assertEquals(AgentState.COMPLETED, result.trace().finalState());
        assertEquals("final answer", result.text());
        assertEquals("final answer", result.trace().finalText());
        assertNull(result.errorCode());
        assertNull(result.trace().errorCode());
        assertTrue(events.contains(new AgentEvent.FinalText("final answer")));
        assertFalse(events.stream().anyMatch(AgentEvent.Failed.class::isInstance));
        assertFalse(events.contains(new AgentEvent.StateChanged(AgentState.CANCELLED)));
    }

    @Test
    void failedRequestRevokesCancellationBeforeClosingToolsWithoutReplacingOriginalFailure() {
        QueueModelClient model = new QueueModelClient();
        model.enqueue(CompletableFuture.completedFuture(toolTurn("call_scope", 42)));
        CompletableFuture<ModelTurn> finalTurn = new CompletableFuture<>();
        model.enqueue(finalTurn);
        AgentSessionStore sessions = new AgentSessionStore();
        AgentRequest request = request(UUID.randomUUID());
        RequestLifetimeTools tools = new RequestLifetimeTools(sessions, request.sessionKey());
        List<AgentEvent> events = new ArrayList<>();

        CompletableFuture<AgentResult> pending = new GameGuideAgent(
                        model, tools, sessions, new Gson())
                .ask(request, events::add);

        assertNotNull(tools.cancellation);
        assertFalse(tools.cancellation.isCancelled());
        assertTrue(tools.lifecycle.isEmpty());
        assertTrue(finalTurn.completeExceptionally(new ModelClientException(
                new ModelFailure("provider_unavailable", "Provider is unavailable", null))));
        AgentResult result = pending.join();

        assertTrue(tools.cancellation.isCancelled());
        assertEquals(List.of("cancel", "close"), tools.lifecycle);
        assertEquals(List.of(request.context().correlationId()), tools.closed);
        assertTrue(tools.cancelledAtClose);
        assertNotNull(tools.sessionAtClose);
        assertTrue(tools.sessionAtClose.active(), "the lease must stay owned until tools close");
        assertEquals(request.requestId(), tools.sessionAtClose.requestId());
        assertFalse(sessions.status(request.sessionKey()).active());
        assertFalse(result.successful());
        assertEquals(AgentState.FAILED, result.state());
        assertEquals(AgentState.FAILED, result.trace().finalState());
        assertEquals("provider_unavailable", result.errorCode());
        assertEquals("provider_unavailable", result.trace().errorCode());
        assertEquals("Provider is unavailable", result.errorMessage());
        assertTrue(events.contains(new AgentEvent.Failed(
                "provider_unavailable", "Provider is unavailable")));
        assertFalse(events.stream().anyMatch(AgentEvent.FinalText.class::isInstance));
        assertFalse(events.contains(new AgentEvent.StateChanged(AgentState.CANCELLED)));
    }

    @Test
    void canonicalProviderAliasProducesCanonicalEventsAndCompletes() {
        QueueModelClient model = new QueueModelClient();
        model.enqueue(CompletableFuture.completedFuture(
                toolTurn("call_alias", "test:fact", 42)));
        model.enqueue(CompletableFuture.completedFuture(textTurn("Alias recovered.")));
        List<AgentEvent> events = new ArrayList<>();

        AgentResult result = new GameGuideAgent(
                        model, new FakeTools(), new AgentSessionStore(), new Gson())
                .ask(request(UUID.randomUUID()), events::add)
                .join();

        assertTrue(result.successful());
        AgentEvent.ToolStarted started = events.stream()
                .filter(AgentEvent.ToolStarted.class::isInstance)
                .map(AgentEvent.ToolStarted.class::cast)
                .findFirst()
                .orElseThrow();
        AgentEvent.ToolCompleted completed = events.stream()
                .filter(AgentEvent.ToolCompleted.class::isInstance)
                .map(AgentEvent.ToolCompleted.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("test:fact", started.toolId());
        assertEquals("test:fact", completed.toolId());
    }

    @Test
    void unknownModelToolBecomesAToolResultAndTheModelCanRecover() {
        QueueModelClient model = new QueueModelClient();
        model.enqueue(CompletableFuture.completedFuture(
                toolTurn("call_unknown", "openallay:invented", 42)));
        model.enqueue(CompletableFuture.completedFuture(
                textTurn("That capability is unavailable.")));
        List<AgentEvent> events = new ArrayList<>();

        AgentResult result = new GameGuideAgent(
                        model,
                        new CompositeAgentToolExecutor(List.of(new FakeTools())),
                        new AgentSessionStore(),
                        new Gson())
                .ask(request(UUID.randomUUID()), events::add)
                .join();

        assertTrue(result.successful());
        assertEquals("That capability is unavailable.", result.text());
        ModelContent.ToolResult toolResult = (ModelContent.ToolResult) model.requests.get(1)
                .messages().getLast().content().getFirst();
        assertTrue(toolResult.error());
        assertTrue(toolResult.value().getAsString().contains("code: tool_unavailable"));
        assertTrue(events.stream()
                .filter(AgentEvent.ToolStarted.class::isInstance)
                .map(AgentEvent.ToolStarted.class::cast)
                .allMatch(started -> started.toolId().equals(AgentToolExecutor.UNKNOWN_TOOL_ID)));
    }

    @Test
    void executorExceptionBecomesAToolResultAndTheModelCanRecover() {
        QueueModelClient model = new QueueModelClient();
        model.enqueue(CompletableFuture.completedFuture(toolTurn("call_failure", 42)));
        model.enqueue(CompletableFuture.completedFuture(textTurn("The lookup failed.")));

        AgentResult result = new GameGuideAgent(
                        model, new FailingTools(), new AgentSessionStore(), new Gson())
                .ask(request(UUID.randomUUID()), ignored -> {})
                .join();

        assertTrue(result.successful());
        ModelContent.ToolResult toolResult = (ModelContent.ToolResult) model.requests.get(1)
                .messages().getLast().content().getFirst();
        assertTrue(toolResult.error());
        assertTrue(toolResult.value().getAsString().contains("code: tool_failure"));
    }

    @Test
    void sameTurnToolsStartTogetherAndReturnToModelInOriginalOrder() {
        QueueModelClient model = new QueueModelClient();
        model.enqueue(CompletableFuture.completedFuture(multiToolTurn(
                new ToolCall("call_first", 1), new ToolCall("call_second", 2))));
        model.enqueue(CompletableFuture.completedFuture(textTurn("done")));
        PendingTools tools = new PendingTools();
        GameGuideAgent agent = new GameGuideAgent(
                model, tools, new AgentSessionStore(), new Gson());

        CompletableFuture<AgentResult> result =
                agent.ask(request(UUID.randomUUID()), ignored -> {});

        assertEquals(Set.of(1, 2), tools.pending.keySet(),
                "the second same-turn call must start before the first completes");
        tools.complete(2);
        assertFalse(result.isDone());
        tools.complete(1);
        assertEquals("done", result.join().text());
        List<ModelContent.ToolResult> results = model.requests.get(1).messages().getLast()
                .content().stream()
                .map(ModelContent.ToolResult.class::cast)
                .toList();
        assertEquals(List.of("call_first", "call_second"),
                results.stream().map(ModelContent.ToolResult::toolUseId).toList());
        assertTrue(results.get(0).value().getAsString().contains("fact: 1"));
        assertTrue(results.get(1).value().getAsString().contains("fact: 2"));
    }

    @Test
    void rejectsBusyAndCancelsPendingModelWithoutLateSuccess() {
        QueueModelClient model = new QueueModelClient();
        CompletableFuture<ModelTurn> pending = new CompletableFuture<>();
        model.enqueue(pending);
        AgentSessionStore sessions = new AgentSessionStore();
        GameGuideAgent agent = new GameGuideAgent(model, new FakeTools(), sessions, new Gson());
        UUID actor = UUID.randomUUID();
        List<AgentEvent> firstEvents = new ArrayList<>();
        CompletableFuture<AgentResult> first = agent.ask(request(actor), firstEvents::add);

        AgentResult busy = agent.ask(request(actor), event -> {}).join();
        assertEquals("agent_busy", busy.errorCode());
        assertTrue(sessions.cancel(new AgentSessionKey(actor, "main")));
        AgentResult cancelled = first.join();
        assertEquals(AgentState.CANCELLED, cancelled.state());
        assertFalse(pending.complete(textTurn("late")));
        assertFalse(firstEvents.stream().anyMatch(AgentEvent.FinalText.class::isInstance));
    }

    @Test
    void allowsDifferentSessionsForTheSameActorToRunConcurrently() {
        ConcurrentModelClient model = new ConcurrentModelClient();
        AgentSessionStore sessions = new AgentSessionStore();
        GameGuideAgent agent = new GameGuideAgent(model, new FakeTools(), sessions, new Gson());
        UUID actor = UUID.randomUUID();

        CompletableFuture<AgentResult> first =
                agent.ask(request(actor, "building"), event -> {});
        CompletableFuture<AgentResult> second =
                agent.ask(request(actor, "recipes"), event -> {});

        assertEquals(2, model.pending.size());
        assertEquals(2, model.maxConcurrent.get());
        model.pending.get("building").complete(textTurn("建筑会话"));
        model.pending.get("recipes").complete(textTurn("配方会话"));
        assertEquals("建筑会话", first.join().text());
        assertEquals("配方会话", second.join().text());
    }

    @Test
    void aSessionTranscriptSurvivesSwitchingModelClients() {
        QueueModelClient firstModel = new QueueModelClient();
        firstModel.enqueue(CompletableFuture.completedFuture(textTurn("first answer")));
        QueueModelClient secondModel = new QueueModelClient();
        secondModel.enqueue(CompletableFuture.completedFuture(textTurn("second answer")));
        AgentSessionStore sessions = new AgentSessionStore();
        UUID actor = UUID.randomUUID();

        new GameGuideAgent(firstModel, new FakeTools(), sessions, new Gson())
                .ask(request(actor), ignored -> {})
                .join();
        new GameGuideAgent(secondModel, new FakeTools(), sessions, new Gson())
                .ask(request(actor), ignored -> {})
                .join();

        assertEquals(3, secondModel.requests.getFirst().messages().size());
        assertEquals(
                "first answer",
                ((ModelContent.Text) secondModel.requests.getFirst()
                                .messages().get(1).content().getFirst())
                        .text());
    }

    @Test
    void givesModelOneNoNewInformationResultBeforeRequiringFinalText() {
        QueueModelClient model = new QueueModelClient();
        model.enqueue(CompletableFuture.completedFuture(toolTurn("call_1", 42)));
        model.enqueue(CompletableFuture.completedFuture(toolTurn("call_2", 42)));
        model.enqueue(CompletableFuture.completedFuture(textTurn("I already checked; the result is unchanged.")));
        FakeTools tools = new FakeTools();
        List<AgentEvent> events = new ArrayList<>();
        AgentResult result = new GameGuideAgent(
                        model, tools, new AgentSessionStore(), new Gson())
                .ask(request(UUID.randomUUID()), events::add)
                .join();

        assertEquals(AgentState.COMPLETED, result.state());
        assertEquals("I already checked; the result is unchanged.", result.text());
        assertEquals(1, tools.invocations.get());
        assertEquals(1, events.stream().filter(AgentEvent.ToolStarted.class::isInstance).count());
        ModelContent.ToolResult repeated = (ModelContent.ToolResult) model.requests.get(2)
                .messages().getLast().content().getFirst();
        assertTrue(repeated.value().getAsString().contains("code: no_new_information"));
        assertTrue(repeated.error());
        assertNotNull(result.trace());
    }

    @Test
    void changingJavascriptIntentCannotBypassRepeatedExecutionSuppression() {
        QueueModelClient model = new QueueModelClient();
        model.enqueue(CompletableFuture.completedFuture(javascriptTurn("call-1", "Compare swords")));
        model.enqueue(CompletableFuture.completedFuture(javascriptTurn("call-2", "A different display title")));
        model.enqueue(CompletableFuture.completedFuture(textTurn("done")));
        AtomicInteger executions = new AtomicInteger();
        var javascript = new dev.openallay.tool.builtin.RunJavascriptTool(
                new dev.openallay.script.RhinoJavascriptRuntime(),
                dev.openallay.script.data.MinecraftAgentHostGraph::new,
                new dev.openallay.script.workspace.AgentResultWorkspaceRegistry(),
                new dev.openallay.script.workspace.JavascriptResultPresenter());
        dev.openallay.tool.Tool<dev.openallay.tool.builtin.RunJavascriptTool.Input,
                dev.openallay.tool.builtin.RunJavascriptTool.Output> counted = new dev.openallay.tool.Tool<>() {
            @Override
            public dev.openallay.tool.ToolDescriptor<dev.openallay.tool.builtin.RunJavascriptTool.Input,
                    dev.openallay.tool.builtin.RunJavascriptTool.Output> descriptor() {
                return javascript.descriptor();
            }

            @Override
            public dev.openallay.tool.ToolResult<dev.openallay.tool.builtin.RunJavascriptTool.Output> invoke(
                    ToolInvocationContext context, dev.openallay.tool.builtin.RunJavascriptTool.Input input) {
                throw new AssertionError("JavaScript must execute asynchronously");
            }

            @Override
            public CompletableFuture<dev.openallay.tool.ToolResult<dev.openallay.tool.builtin.RunJavascriptTool.Output>> invokeAsync(
                    ToolInvocationContext context, dev.openallay.tool.builtin.RunJavascriptTool.Input input,
                    CancellationSignal cancellation) {
                executions.incrementAndGet();
                return javascript.invokeAsync(context, input, cancellation);
            }
        };
        var registry = new dev.openallay.tool.ToolRegistry();
        registry.register("test", List.of(counted));
        List<AgentEvent> events = new ArrayList<>();
        AgentResult result = new GameGuideAgent(model,
                new dev.openallay.agent.tool.LocalAgentToolExecutor(registry, new Gson()),
                new AgentSessionStore(), new Gson()).ask(request(UUID.randomUUID()), events::add).join();
        assertTrue(result.successful());
        assertEquals(1, executions.get());
        assertEquals(1, events.stream().filter(AgentEvent.ToolStarted.class::isInstance).count());
        ModelContent.ToolResult repeated = (ModelContent.ToolResult) model.requests.get(2)
                .messages().getLast().content().getFirst();
        assertTrue(repeated.error());
        assertTrue(repeated.value().getAsString().contains("code: no_new_information"));
        AgentEvent.ToolStarted started = (AgentEvent.ToolStarted) events.stream()
                .filter(AgentEvent.ToolStarted.class::isInstance).findFirst().orElseThrow();
        assertEquals("Compare swords", started.arguments().get("title").getAsString());
        javascript.closeRequestScope("agent-test");
    }

    @Test
    void malformedJavascriptIntentCanRecoverWithCorrectedMetadataAndExecuteOnlyOnce() {
        QueueModelClient model = new QueueModelClient();
        JsonObject malformed = new JsonObject();
        malformed.addProperty("source", "return schema.list();");
        malformed.addProperty("title", 7);
        malformed.addProperty("description", "Read declared data");
        model.enqueue(CompletableFuture.completedFuture(new ModelTurn("test", "test-model",
                List.of(new ModelContent.ToolUse("call-invalid", "openallay__run_javascript", malformed)),
                "tool_use", ModelUsage.empty())));
        model.enqueue(CompletableFuture.completedFuture(javascriptTurn("call-corrected", "Inspect catalog")));
        model.enqueue(CompletableFuture.completedFuture(textTurn("done")));
        AtomicInteger captures = new AtomicInteger();
        var javascript = new dev.openallay.tool.builtin.RunJavascriptTool(
                new dev.openallay.script.RhinoJavascriptRuntime(), context -> {
                    captures.incrementAndGet();
                    return new dev.openallay.script.data.MinecraftAgentHostGraph(context);
                }, new dev.openallay.script.workspace.AgentResultWorkspaceRegistry(),
                new dev.openallay.script.workspace.JavascriptResultPresenter());
        var registry = new dev.openallay.tool.ToolRegistry();
        registry.register("test", List.of(javascript));
        List<AgentEvent> events = new ArrayList<>();
        AgentResult result = new GameGuideAgent(model,
                new dev.openallay.agent.tool.LocalAgentToolExecutor(registry, new Gson()),
                new AgentSessionStore(), new Gson()).ask(request(UUID.randomUUID()), events::add).join();
        assertTrue(result.successful());
        assertEquals(1, captures.get());
        List<AgentEvent.ToolCompleted> completed = events.stream().filter(AgentEvent.ToolCompleted.class::isInstance)
                .map(AgentEvent.ToolCompleted.class::cast).toList();
        assertEquals(List.of("call-invalid", "call-corrected"), completed.stream()
                .map(AgentEvent.ToolCompleted::invocationId).toList());
        assertTrue(completed.getFirst().failure());
        assertEquals("invalid_arguments", completed.getFirst().normalized().get("code").getAsString());
        assertFalse(completed.get(1).failure());
        ModelContent.ToolResult invalid = (ModelContent.ToolResult) model.requests.get(1)
                .messages().getLast().content().getFirst();
        assertTrue(invalid.error());
        assertTrue(invalid.value().getAsString().contains("code: invalid_arguments"));
        javascript.closeRequestScope("agent-test");
    }

    @Test
    void javascriptRootSelectorFailuresReachTheModelAndCorrectedBareSelectionSucceeds() {
        QueueModelClient model = new QueueModelClient();
        String source = "return {position: mc.game.player.player.position, unselected: typeof mc.items};";
        model.enqueue(CompletableFuture.completedFuture(javascriptProgramTurn("call-player-path", source, "mc.player")));
        model.enqueue(CompletableFuture.completedFuture(javascriptProgramTurn("call-game-path", source, "mc.game")));
        model.enqueue(CompletableFuture.completedFuture(javascriptProgramTurn("call-game-bare", source, "game")));
        model.enqueue(CompletableFuture.completedFuture(textTurn("The captured position is 1, 64, 2.")));
        AtomicInteger captures = new AtomicInteger();
        var javascript = new dev.openallay.tool.builtin.RunJavascriptTool(
                new dev.openallay.script.RhinoJavascriptRuntime(), context -> {
                    captures.incrementAndGet();
                    return new dev.openallay.script.data.MinecraftAgentHostGraph(context);
                }, new dev.openallay.script.workspace.AgentResultWorkspaceRegistry(),
                new dev.openallay.script.workspace.JavascriptResultPresenter());
        var registry = new dev.openallay.tool.ToolRegistry();
        registry.register("test", List.of(javascript));
        var context = dev.openallay.testing.JavascriptAgentTestFixtures.context("agent-root-recovery");
        AgentRequest request = new AgentRequest(UUID.randomUUID(), UUID.randomUUID(), "main",
                "Read the player position.", AgentSystemPrompt.compose(""), context, false);
        List<AgentEvent> events = new ArrayList<>();
        AgentResult result = new GameGuideAgent(model,
                new dev.openallay.agent.tool.LocalAgentToolExecutor(registry, new Gson()),
                new AgentSessionStore(), new Gson()).ask(request, events::add).join();
        assertTrue(result.successful());
        assertEquals(1, captures.get(), "all calls retain the same detached request graph");
        assertEquals(4, model.requests.size());
        List<AgentEvent.ToolCompleted> completed = events.stream()
                .filter(AgentEvent.ToolCompleted.class::isInstance)
                .map(AgentEvent.ToolCompleted.class::cast).toList();
        assertEquals(List.of("call-player-path", "call-game-path", "call-game-bare"), completed.stream()
                .map(AgentEvent.ToolCompleted::invocationId).toList());
        for (int index = 0; index < 2; index++) {
            String bare = index == 0 ? "player" : "game";
            assertTrue(completed.get(index).failure());
            assertEquals("javascript_root_unavailable", completed.get(index).normalized().get("code").getAsString());
            assertFalse(completed.get(index).normalized().has("value"), "selector failure publishes no empty fact");
            ModelContent.ToolResult failure = (ModelContent.ToolResult) model.requests.get(index + 1)
                    .messages().getLast().content().getFirst();
            assertTrue(failure.error());
            String feedback = failure.value().getAsString();
            assertTrue(feedback.contains("code: javascript_root_unavailable"));
            assertTrue(feedback.contains("Use roots [\"" + bare + "\"] and access mc." + bare));
            assertTrue(feedback.contains("Current declared bare roots:"));
        }
        assertFalse(completed.get(2).failure(), "different corrected execution arguments are not suppressed");
        var preview = completed.get(2).normalized().getAsJsonObject("value").getAsJsonObject("preview");
        assertEquals(1, preview.getAsJsonObject("position").get("x").getAsInt());
        assertEquals(64, preview.getAsJsonObject("position").get("y").getAsInt());
        assertEquals(2, preview.getAsJsonObject("position").get("z").getAsInt());
        assertEquals("undefined", preview.get("unselected").getAsString());
        ModelContent.ToolResult success = (ModelContent.ToolResult) model.requests.get(3)
                .messages().getLast().content().getFirst();
        assertFalse(success.error());
        assertFalse(success.value().getAsString().contains("no_new_information"));
    }

    @Test
    void javascriptModuleReturnFailureReachesTheModelWithJsonRecoveryAndNoEmptyFact() {
        QueueModelClient model = new QueueModelClient();
        model.enqueue(CompletableFuture.completedFuture(javascriptProgramTurn("call-module",
                "var module = require('openallay:crafting'); var count = mc.items.length; return module;", "items")));
        model.enqueue(CompletableFuture.completedFuture(javascriptProgramTurn("call-data",
                "var module = require('openallay:crafting'); return {count: mc.items.length};", "items")));
        model.enqueue(CompletableFuture.completedFuture(textTurn("The captured item count is 6.")));
        var javascript = new dev.openallay.tool.builtin.RunJavascriptTool(
                new dev.openallay.script.RhinoJavascriptRuntime(),
                dev.openallay.script.data.MinecraftAgentHostGraph::new,
                new dev.openallay.script.workspace.AgentResultWorkspaceRegistry(),
                new dev.openallay.script.workspace.JavascriptResultPresenter());
        var registry = new dev.openallay.tool.ToolRegistry();
        registry.register("test", List.of(javascript));
        var context = dev.openallay.testing.JavascriptAgentTestFixtures.context("agent-result-recovery");
        AgentRequest request = new AgentRequest(UUID.randomUUID(), UUID.randomUUID(), "main",
                "Count the captured items.", AgentSystemPrompt.compose(""), context, false);
        List<AgentEvent> events = new ArrayList<>();
        AgentResult result = new GameGuideAgent(model,
                new dev.openallay.agent.tool.LocalAgentToolExecutor(registry, new Gson()),
                new AgentSessionStore(), new Gson()).ask(request, events::add).join();
        assertTrue(result.successful());
        ModelContent.ToolResult failure = (ModelContent.ToolResult) model.requests.get(1)
                .messages().getLast().content().getFirst();
        assertTrue(failure.error());
        String feedback = failure.value().getAsString();
        assertTrue(feedback.contains("code: javascript_result_invalid"));
        assertTrue(feedback.contains("Return JSON data from the operation"));
        assertTrue(feedback.contains("not the function or module itself"));
        assertTrue(feedback.contains("does not mean the operation is unavailable"));
        List<AgentEvent.ToolCompleted> completed = events.stream()
                .filter(AgentEvent.ToolCompleted.class::isInstance)
                .map(AgentEvent.ToolCompleted.class::cast).toList();
        assertEquals(2, completed.size());
        assertTrue(completed.getFirst().failure());
        assertFalse(completed.getFirst().normalized().has("value"));
        assertFalse(completed.get(1).failure());
        assertEquals(6, completed.get(1).normalized().getAsJsonObject("value")
                .getAsJsonObject("preview").get("count").getAsInt());
        ModelContent.ToolResult success = (ModelContent.ToolResult) model.requests.get(2)
                .messages().getLast().content().getFirst();
        assertFalse(success.error());
    }

    private static ModelTurn javascriptProgramTurn(String invocationId, String source, String root) {
        JsonObject input = new JsonObject();
        input.addProperty("source", source);
        input.add("roots", JsonParser.parseString("[\"" + root + "\"]"));
        input.addProperty("title", "Read captured data");
        input.addProperty("description", "Read the requested data without changing the world");
        return new ModelTurn("test", "test-model", List.of(new ModelContent.ToolUse(
                invocationId, "openallay__run_javascript", input)), "tool_use", ModelUsage.empty());
    }

    private static ModelTurn javascriptTurn(String invocationId, String title) {
        JsonObject input = new JsonObject();
        input.addProperty("source", "return schema.list();");
        input.addProperty("title", title);
        input.addProperty("description", "Read the available data catalog: " + title);
        return new ModelTurn("test", "test-model", List.of(new ModelContent.ToolUse(
                invocationId, "openallay__run_javascript", input)), "tool_use", ModelUsage.empty());
    }

    @Test
    void failsIfModelIgnoresNoNewInformationAndRepeatsAgain() {
        QueueModelClient model = new QueueModelClient();
        model.enqueue(CompletableFuture.completedFuture(toolTurn("call_1", 42)));
        model.enqueue(CompletableFuture.completedFuture(toolTurn("call_2", 42)));
        model.enqueue(CompletableFuture.completedFuture(toolTurn("call_3", 42)));
        FakeTools tools = new FakeTools();

        AgentResult result = new GameGuideAgent(
                        model, tools, new AgentSessionStore(), new Gson())
                .ask(request(UUID.randomUUID()), ignored -> {})
                .join();

        assertEquals(AgentState.FAILED, result.state());
        assertEquals("repeated_tool_call", result.errorCode());
        assertEquals(1, tools.invocations.get());
    }

    @Test
    void compactsBeforePrimaryDispatchAndKeepsOnlyTheSuccessfulRuntimeProjection() {
        QueueModelClient model = new QueueModelClient();
        model.enqueue(CompletableFuture.completedFuture(textTurn(summaryJson())));
        model.enqueue(CompletableFuture.completedFuture(textTurn("final")));
        AgentSessionStore sessions = new AgentSessionStore();
        UUID actor = UUID.randomUUID();
        AgentSessionKey key = new AgentSessionKey(actor, "main");
        List<ModelMessage> original = List.of(
                ModelMessage.userText("a".repeat(250)),
                ModelMessage.userText("b".repeat(250)),
                ModelMessage.userText("c".repeat(250)),
                ModelMessage.userText("d".repeat(250)));
        sessions.hydrate(key, original);
        List<AgentEvent> events = new ArrayList<>();

        AgentResult result = new GameGuideAgent(
                        model, new FakeTools(), sessions, new Gson(), compactor(model))
                .ask(request(actor), events::add)
                .join();

        assertTrue(result.successful());
        assertEquals(2, model.requests.size());
        assertEquals("final", result.text());
        assertTrue(events.stream().anyMatch(event -> event.equals(
                new AgentEvent.StateChanged(AgentState.COMPACTING))));
        assertTrue(events.stream().anyMatch(AgentEvent.ContextCompacted.class::isInstance));
        assertEquals(1, sessions.checkpoints(key).size());
        assertEquals(5, sessions.status(key).historyMessages());
        assertTrue(model.requests.get(1).messages().getFirst().content().stream()
                .map(ModelContent.Text.class::cast)
                .anyMatch(text -> text.text().contains("NOT factual evidence")));
    }

    @Test
    void compactionFailureAndCancellationNeverDispatchPrimaryOrReplaceHistory() {
        UUID actor = UUID.randomUUID();
        AgentSessionKey key = new AgentSessionKey(actor, "main");
        AgentSessionStore failedSessions = new AgentSessionStore();
        failedSessions.hydrate(key, largeHistory());
        QueueModelClient malformed = new QueueModelClient();
        malformed.enqueue(CompletableFuture.completedFuture(textTurn("not-json")));

        AgentResult failed = new GameGuideAgent(
                        malformed, new FakeTools(), failedSessions, new Gson(), compactor(malformed))
                .ask(request(actor), ignored -> {})
                .join();

        assertEquals("context_compaction_failed", failed.errorCode());
        assertEquals(1, malformed.requests.size());
        assertEquals(4, failedSessions.status(key).historyMessages());

        AgentSessionStore cancelledSessions = new AgentSessionStore();
        cancelledSessions.hydrate(key, largeHistory());
        QueueModelClient pendingModel = new QueueModelClient();
        CompletableFuture<ModelTurn> pending = new CompletableFuture<>();
        pendingModel.enqueue(pending);
        CompletableFuture<AgentResult> running = new GameGuideAgent(
                        pendingModel, new FakeTools(), cancelledSessions, new Gson(), compactor(pendingModel))
                .ask(request(actor), ignored -> {});
        assertTrue(cancelledSessions.cancel(key));

        assertEquals(AgentState.CANCELLED, running.join().state());
        assertEquals(1, pendingModel.requests.size());
        assertEquals(4, cancelledSessions.status(key).historyMessages());
    }

    @Test
    void reestimatesAfterToolResultsAndFailsLocallyBeforeAnOversizedContinuation() {
        QueueModelClient model = new QueueModelClient();
        model.enqueue(CompletableFuture.completedFuture(toolTurn("call_large", 42)));
        ContextCompactor smallBudget = new ContextCompactor(
                model,
                new Gson(),
                new Utf8ContextTokenEstimator(),
                new ToolResultContextReducer(),
                new ContextBudget(600, 100),
                "test-model",
                Clock.systemUTC());

        AgentResult result = new GameGuideAgent(
                        model,
                        new LargeResultTools(),
                        new AgentSessionStore(),
                        new Gson(),
                        smallBudget)
                .ask(request(UUID.randomUUID()), ignored -> {})
                .join();

        assertEquals("context_compaction_failed", result.errorCode());
        assertEquals(1, model.requests.size(),
                "the oversized continuation must not be sent to the provider");
    }

    private static ContextCompactor compactor(ModelClient model) {
        return new ContextCompactor(
                model, new Gson(), new Utf8ContextTokenEstimator(),
                new ToolResultContextReducer(), new ContextBudget(1_200, 100),
                "test-model", Clock.systemUTC());
    }

    private static List<ModelMessage> largeHistory() {
        return List.of(
                ModelMessage.userText("a".repeat(250)),
                ModelMessage.userText("b".repeat(250)),
                ModelMessage.userText("c".repeat(250)),
                ModelMessage.userText("d".repeat(250)));
    }

    private static String summaryJson() {
        return """
                {"goals":[],"preferences":[],"completedTopics":[],"currentTasks":[],
                 "decisions":[],"unresolvedQuestions":[],"evidenceReferences":[]}
                """;
    }

    private static AgentRequest request(UUID actor) {
        return request(actor, "main");
    }

    private static AgentRequest request(UUID actor, String sessionId) {
        return new AgentRequest(
                UUID.randomUUID(),
                actor,
                sessionId,
                "铁锭怎么做？",
                "Always use tools for dynamic facts.",
                ToolInvocationContext.developmentConsole("agent-test"),
                true);
    }

    private static final class ConcurrentModelClient implements ModelClient {
        private final java.util.Map<String, CompletableFuture<ModelTurn>> pending =
                new java.util.HashMap<>();
        private final AtomicInteger concurrent = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();

        @Override
        public CompletableFuture<ModelTurn> complete(
                ModelRequest request,
                Consumer<ModelEvent> events,
                CancellationSignal cancellation) {
            int active = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(active, Math::max);
            CompletableFuture<ModelTurn> future = new CompletableFuture<>();
            pending.put(request.sessionKey().substring(request.sessionKey().lastIndexOf(':') + 1), future);
            future.whenComplete((ignored, throwable) -> concurrent.decrementAndGet());
            cancellation.onCancel(() -> future.completeExceptionally(new ModelClientException(
                    new ModelFailure("agent_cancelled", "cancelled", null))));
            return future;
        }
    }

    private static ModelTurn toolTurn(String callId, int value) {
        return toolTurn(callId, "test__fact", value);
    }

    private static ModelTurn toolTurn(String callId, String toolName, int value) {
        JsonObject input = new JsonObject();
        input.addProperty("value", value);
        return new ModelTurn(
                "test",
                "test-model",
                List.of(new ModelContent.ToolUse(callId, toolName, input)),
                "tool_use",
                ModelUsage.empty());
    }

    private static ModelTurn textTurn(String text) {
        return new ModelTurn(
                "test",
                "test-model",
                List.of(new ModelContent.Text(text)),
                "end_turn",
                ModelUsage.empty());
    }

    private record ToolCall(String id, int value) {}

    private static ModelTurn multiToolTurn(ToolCall... calls) {
        return new ModelTurn(
                "test",
                "test-model",
                java.util.Arrays.stream(calls).map(call -> {
                    JsonObject input = new JsonObject();
                    input.addProperty("value", call.value());
                    return (ModelContent) new ModelContent.ToolUse(
                            call.id(), "test__fact", input);
                }).toList(),
                "tool_use",
                ModelUsage.empty());
    }

    private static final class QueueModelClient implements ModelClient {
        private final Deque<CompletableFuture<ModelTurn>> turns = new ArrayDeque<>();
        private final List<ModelRequest> requests = new ArrayList<>();

        void enqueue(CompletableFuture<ModelTurn> turn) {
            turns.add(turn);
        }

        @Override
        public CompletableFuture<ModelTurn> complete(
                ModelRequest request,
                Consumer<ModelEvent> events,
                CancellationSignal cancellation) {
            requests.add(request);
            CompletableFuture<ModelTurn> turn = turns.removeFirst();
            cancellation.onCancel(() -> turn.completeExceptionally(new ModelClientException(
                    new ModelFailure("agent_cancelled", "cancelled", null))));
            return turn;
        }
    }

    private static final class FakeTools implements AgentToolExecutor {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public List<ModelToolDefinition> definitions() {
            return List.of(new ModelToolDefinition(
                    "test__fact",
                    "Return a fact",
                    JsonParser.parseString("{\"type\":\"object\"}").getAsJsonObject()));
        }

        @Override
        public Set<ContextCapability> requiredContext() {
            return Set.of();
        }

        @Override
        public Optional<String> canonicalToolId(String modelToolName) {
            return switch (modelToolName) {
                case "test__fact", "test:fact" -> Optional.of("test:fact");
                default -> Optional.empty();
            };
        }

        @Override
        public CompletableFuture<AgentToolResult> execute(
                String modelToolName,
                JsonObject arguments,
                ToolInvocationContext context,
                CancellationSignal cancellation) {
            invocations.incrementAndGet();
            JsonObject value = new JsonObject();
            value.addProperty("fact", arguments.get("value").getAsInt());
            JsonObject normalized = new JsonObject();
            normalized.addProperty("status", "success");
            normalized.addProperty("outputType", "test.Output");
            normalized.add("value", value);
            return CompletableFuture.completedFuture(
                    new AgentToolResult("test:fact", normalized, false));
        }
    }

    private static final class RequestLifetimeTools implements AgentToolExecutor {
        private final FakeTools delegate = new FakeTools();
        private final AgentSessionStore sessions;
        private final AgentSessionKey sessionKey;
        private final List<String> lifecycle = new ArrayList<>();
        private final List<String> closed = new ArrayList<>();
        private CancellationSignal cancellation;
        private boolean cancelledAtClose;
        private AgentSessionStore.Status sessionAtClose;

        private RequestLifetimeTools(AgentSessionStore sessions, AgentSessionKey sessionKey) {
            this.sessions = sessions;
            this.sessionKey = sessionKey;
        }

        @Override
        public List<ModelToolDefinition> definitions() {
            return delegate.definitions();
        }

        @Override
        public Set<ContextCapability> requiredContext() {
            return delegate.requiredContext();
        }

        @Override
        public Optional<String> canonicalToolId(String modelToolName) {
            return delegate.canonicalToolId(modelToolName);
        }

        @Override
        public CompletableFuture<AgentToolResult> execute(
                String modelToolName,
                JsonObject arguments,
                ToolInvocationContext context,
                CancellationSignal cancellation) {
            this.cancellation = cancellation;
            cancellation.onCancel(() -> lifecycle.add("cancel"));
            return delegate.execute(modelToolName, arguments, context, cancellation);
        }

        @Override
        public void closeRequestScope(String correlationId) {
            cancelledAtClose = cancellation.isCancelled();
            sessionAtClose = sessions.status(sessionKey);
            lifecycle.add("close");
            closed.add(correlationId);
        }
    }

    private static final class LargeResultTools implements AgentToolExecutor {
        @Override
        public List<ModelToolDefinition> definitions() {
            return new FakeTools().definitions();
        }

        @Override
        public Set<ContextCapability> requiredContext() {
            return Set.of();
        }

        @Override
        public Optional<String> canonicalToolId(String modelToolName) {
            return Optional.of("test:fact");
        }

        @Override
        public CompletableFuture<AgentToolResult> execute(
                String modelToolName,
                JsonObject arguments,
                ToolInvocationContext context,
                CancellationSignal cancellation) {
            JsonObject normalized = new JsonObject();
            normalized.addProperty("status", "success");
            normalized.addProperty("outputType", "test.Output");
            normalized.add("value", new JsonObject());
            normalized.addProperty("modelText", "x".repeat(4_000));
            return CompletableFuture.completedFuture(
                    new AgentToolResult("test:fact", normalized, false));
        }
    }

    private static final class FailingTools implements AgentToolExecutor {
        @Override
        public List<ModelToolDefinition> definitions() {
            return List.of(new ModelToolDefinition(
                    "test__fact",
                    "Return a fact",
                    JsonParser.parseString("{\"type\":\"object\"}").getAsJsonObject()));
        }

        @Override
        public Set<ContextCapability> requiredContext() {
            return Set.of();
        }

        @Override
        public Optional<String> canonicalToolId(String modelToolName) {
            return "test__fact".equals(modelToolName)
                    ? Optional.of("test:fact")
                    : Optional.empty();
        }

        @Override
        public CompletableFuture<AgentToolResult> execute(
                String modelToolName,
                JsonObject arguments,
                ToolInvocationContext context,
                CancellationSignal cancellation) {
            return CompletableFuture.failedFuture(new IllegalStateException("raw private failure"));
        }
    }

    private static final class PendingTools implements AgentToolExecutor {
        private final java.util.Map<Integer, CompletableFuture<AgentToolResult>> pending =
                new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public List<ModelToolDefinition> definitions() {
            return List.of(new ModelToolDefinition(
                    "test__fact", "Return a fact",
                    JsonParser.parseString("{\"type\":\"object\"}").getAsJsonObject()));
        }

        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }

        @Override
        public Optional<String> canonicalToolId(String modelToolName) {
            return Optional.of("test:fact");
        }

        @Override
        public CompletableFuture<AgentToolResult> execute(
                String modelToolName,
                JsonObject arguments,
                ToolInvocationContext context,
                CancellationSignal cancellation) {
            int value = arguments.get("value").getAsInt();
            CompletableFuture<AgentToolResult> result = new CompletableFuture<>();
            pending.put(value, result);
            return result;
        }

        private void complete(int value) {
            JsonObject fact = new JsonObject();
            fact.addProperty("fact", value);
            JsonObject normalized = new JsonObject();
            normalized.addProperty("status", "success");
            normalized.addProperty("outputType", "test.Output");
            normalized.add("value", fact);
            pending.get(value).complete(new AgentToolResult("test:fact", normalized, false));
        }
    }
}
