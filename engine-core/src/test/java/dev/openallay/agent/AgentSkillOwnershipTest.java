package dev.openallay.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.tool.LocalAgentToolExecutor;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.*;
import dev.openallay.skill.*;
import dev.openallay.tool.ToolRegistry;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class AgentSkillOwnershipTest {
    private static final Gson GSON = new Gson();
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000077");

    @Test
    void forcedRepeatedCallsRemainRealSuccessfulReuseAcrossThreeRebuiltRuntimes() {
        SkillRepository repository = repository("Use the retained workflow.");
        AgentSessionStore sessions = new AgentSessionStore();
        for (int ask = 0; ask < 3; ask++) {
            int current = ask;
            Scripted model = new Scripted();
            model.add(request -> {
                if (current > 0) assertTrue(request.systemPrompt().contains("guide / SKILL.md: full"));
                return call("ask-" + current + "-a");
            });
            model.add(request -> call("ask-" + current + "-b"));
            model.add(request -> call("ask-" + current + "-c"));
            model.add(request -> text("Done."));
            List<AgentEvent> events = new ArrayList<>();
            assertTrue(agent(repository, sessions, model)
                    .ask(request("main", "System"), events::add).join().successful());
            List<AgentEvent.ToolCompleted> completed = events.stream()
                    .filter(AgentEvent.ToolCompleted.class::isInstance).map(AgentEvent.ToolCompleted.class::cast).toList();
            assertEquals(3, completed.size(), "Do not hide the model's real repeated Tool calls");
            assertTrue(completed.stream().noneMatch(AgentEvent.ToolCompleted::failure));
            assertEquals(current == 0 ? 1 : 0, completed.stream()
                    .filter(event -> output(event).content().length() > 0).count());
            assertTrue(model.requests.getLast().systemPrompt().contains("guide / SKILL.md: full"));
            assertEquals(1, occurrenceCount(model.requests.getLast().messages(), "Use the retained workflow."));
        }
    }

    @Test
    void playerNamedFieldsRemainInOneActualDocumentIdentityAcrossThreeAsksAndRestore() {
        String playerValue = "synthetic-player-value-only";
        String body = "APIkey=\"example\"; token=" + playerValue + "; password=player-password; use workflow.";
        SkillRepository repository = repository(body);
        String fingerprint = new LoadSkillTool(repository.snapshot(Set.of()))
                .catalogManifest().documents().getFirst().fingerprint();
        AgentSessionStore sessions = new AgentSessionStore();
        List<ModelMessage> restored = List.of();
        for (int ask = 0; ask < 3; ask++) {
            Scripted model = new Scripted();
            model.add(request -> call("document-" + model.requests.size() + "-" + UUID.randomUUID()));
            model.add(request -> text("Done."));
            List<AgentEvent> events = new ArrayList<>();
            assertTrue(agent(repository, sessions, model).ask(request("fields", "System"), events::add)
                    .join().successful());
            var completed = events.stream().filter(AgentEvent.ToolCompleted.class::isInstance)
                    .map(AgentEvent.ToolCompleted.class::cast).findFirst().orElseThrow();
            LoadSkillTool.Output actual = output(completed);
            assertFalse(completed.failure());
            assertEquals(ask == 0 ? LoadSkillTool.LoadState.COMPLETE : LoadSkillTool.LoadState.ALREADY_LOADED,
                    actual.state());
            assertEquals(ask == 0 ? body : "", actual.content());
            assertEquals(fingerprint, actual.fingerprint());
            String wire = GSON.toJson(model.requests);
            assertTrue(wire.contains(playerValue));
            assertTrue(wire.contains("example"));
            assertTrue(wire.contains("player-password"));
            assertEquals(1, occurrenceCount(model.requests.getLast().messages(), body));
            assertTrue(model.requests.getLast().systemPrompt().contains("guide / SKILL.md: full"));
            restored = model.requests.getLast().messages();
        }
        AgentSessionStore fresh = new AgentSessionStore();
        fresh.hydrate(new dev.openallay.agent.session.AgentSessionKey(ACTOR, "fields"), restored);
        Scripted model = new Scripted();
        model.add(request -> call("restored-fields"));
        model.add(request -> text("Done."));
        List<AgentEvent> events = new ArrayList<>();
        assertTrue(agent(repository, fresh, model).ask(request("fields", "System"), events::add)
                .join().successful());
        var completed = events.stream().filter(AgentEvent.ToolCompleted.class::isInstance)
                .map(AgentEvent.ToolCompleted.class::cast).findFirst().orElseThrow();
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(completed).state());
        assertEquals("", output(completed).content());
        assertEquals(fingerprint, output(completed).fingerprint());
        assertEquals(1, occurrenceCount(model.requests.getLast().messages(), body));
        assertTrue(GSON.toJson(model.requests).contains(playerValue));
        assertTrue(GSON.toJson(model.requests).contains("player-password"));
    }

    @Test
    void aModelThatUsesTheManifestNeedsNoToolCallsOnTheSecondAsk() {
        SkillRepository repository = repository("Use visible workflow.");
        AgentSessionStore sessions = new AgentSessionStore();
        Scripted first = new Scripted();
        first.add(request -> call("first"));
        first.add(request -> text("Ready."));
        assertTrue(agent(repository, sessions, first)
                .ask(request("manifest", "System"), ignored -> {}).join().successful());
        Scripted second = new Scripted();
        second.add(request -> {
            assertTrue(request.systemPrompt().contains("guide / SKILL.md: full"));
            assertEquals(1, occurrenceCount(request.messages(), "Use visible workflow."));
            return text("Use the retained workflow.");
        });
        List<AgentEvent> events = new ArrayList<>();
        assertTrue(agent(repository, sessions, second)
                .ask(request("manifest", "System"), events::add).join().successful());
        assertTrue(events.stream().noneMatch(AgentEvent.ToolStarted.class::isInstance));
    }

    @Test
    void inlineSystemSkillWithMarkdownHeadingsReturnsOnlyReuseReceiptsAcrossAsks() {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        String body = "Inline workflow.\n\n## API\nUse the safe host.";
        assertTrue(repository.reload(List.of(new SkillSource("pack", "unrestricted-javascript/SKILL.md",
                Map.of("unrestricted-javascript/SKILL.md", "---\nname: unrestricted-javascript\n"
                        + "description: Java guidance\n---\n" + body))), Set.of()));
        AgentSessionStore sessions = new AgentSessionStore();
        for (int ask = 0; ask < 2; ask++) {
            Scripted model = new Scripted();
            model.add(request -> {
                JsonObject input = new JsonObject(); input.addProperty("name", "unrestricted-javascript");
                return new ModelTurn("test", "test", List.of(new ModelContent.ToolUse(UUID.randomUUID().toString(),
                        "openallay__load_skill", input)), "tool_use", ModelUsage.empty());
            });
            model.add(request -> {
                assertTrue(request.systemPrompt().contains("unrestricted-javascript / SKILL.md: full"));
                assertFalse(GSON.toJson(request.messages()).contains("invalidated"));
                assertEquals(0, occurrenceCount(request.messages(), body));
                return text("Done.");
            });
            ToolRegistry registry = new ToolRegistry();
            registry.register("openallay", List.of(new LoadSkillTool(repository.snapshot(Set.of()).forRequest(true))));
            UUID id = UUID.randomUUID();
            AgentRequest request = new AgentRequest(id, ACTOR, "inline", "Use it.",
                    "## UNRESTRICTED JAVASCRIPT GUIDANCE\n" + body + "\n\n## EXECUTION\nExecute.",
                    enabled(id.toString()), false);
            List<AgentEvent> events = new ArrayList<>();
            assertTrue(new GameGuideAgent(model, new LocalAgentToolExecutor(registry, GSON), sessions, GSON)
                    .ask(request, events::add).join().successful());
            var complete = events.stream().filter(AgentEvent.ToolCompleted.class::isInstance)
                    .map(AgentEvent.ToolCompleted.class::cast).findFirst().orElseThrow();
            assertFalse(complete.failure());
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(complete).state());
            assertEquals("", output(complete).content());
        }
    }

    @Test
    void catalogRefreshChangesOnlyActiveProjectionNotOriginalRequestDelta() {
        SkillRepository repository = repository("Old workflow.");
        AgentSessionStore sessions = new AgentSessionStore();
        Scripted first = new Scripted();
        first.add(request -> call("original"));
        first.add(request -> text("Done."));
        List<AgentEvent> original = new ArrayList<>();
        assertTrue(agent(repository, sessions, first)
                .ask(request("history", "System"), original::add).join().successful());
        var prior = original.stream().filter(AgentEvent.ContextFinalized.class::isInstance)
                .map(AgentEvent.ContextFinalized.class::cast).findFirst().orElseThrow();
        assertTrue(GSON.toJson(prior.requestMessages()).contains("Old workflow."));
        repository.reload(List.of(source("New workflow.")), Set.of());
        Scripted second = new Scripted();
        second.add(request -> {
            assertFalse(GSON.toJson(request.messages()).contains("Old workflow."));
            var old = request.messages().stream().flatMap(message -> message.content().stream())
                    .filter(ModelContent.ToolResult.class::isInstance).map(ModelContent.ToolResult.class::cast)
                    .filter(result -> result.toolUseId().equals("original")).findFirst().orElseThrow();
            assertFalse(old.error(), "Projection invalidation must not invent a historical Tool error");
            return text("Use current guidance if needed.");
        });
        assertTrue(agent(repository, sessions, second)
                .ask(request("history", "System"), ignored -> {}).join().successful());
        assertTrue(GSON.toJson(prior.requestMessages()).contains("Old workflow."));
    }

    private static GameGuideAgent agent(SkillRepository repository, AgentSessionStore sessions,
            Scripted model) {
        ToolRegistry registry = new ToolRegistry();
        registry.register("openallay", List.of(new LoadSkillTool(repository.snapshot(Set.of()))));
        return new GameGuideAgent(model, new LocalAgentToolExecutor(registry, GSON), sessions,
                GSON, null, (request, tokens) -> {});
    }
    private static ToolInvocationContext enabled(String id) {
        ToolInvocationContext ordinary = ToolInvocationContext.developmentConsole(id);
        return new ToolInvocationContext(ordinary.correlationId(), ordinary.capturedAt(), ordinary.caller(),
                ordinary.player(), ordinary.registries(), ordinary.recipes(), ordinary.observableGameState(),
                ordinary.metrics(), true);
    }

    private static AgentRequest request(String session, String system) {
        UUID id = UUID.randomUUID();
        return new AgentRequest(id, ACTOR, session, "Use workflow.", system,
                ToolInvocationContext.developmentConsole(id.toString()), false);
    }
    private static LoadSkillTool.Output output(AgentEvent.ToolCompleted event) {
        return GSON.fromJson(event.normalized().get("value"), LoadSkillTool.Output.class);
    }
    private static int occurrenceCount(List<ModelMessage> messages, String body) {
        return (int) messages.stream().flatMap(message -> message.content().stream())
                .filter(ModelContent.ToolResult.class::isInstance).map(ModelContent.ToolResult.class::cast)
                .filter(result -> result.value().isJsonPrimitive() && result.value().getAsString().contains(body)).count();
    }
    private static ModelTurn call(String id) {
        JsonObject input = new JsonObject(); input.addProperty("name", "guide");
        return new ModelTurn("test", "test", List.of(new ModelContent.ToolUse(id, "openallay__load_skill", input)),
                "tool_use", ModelUsage.empty());
    }
    private static ModelTurn text(String text) {
        return new ModelTurn("test", "test", List.of(new ModelContent.Text(text)), "end_turn", ModelUsage.empty());
    }
    private static SkillRepository repository(String body) {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        assertTrue(repository.reload(List.of(source(body)), Set.of())); return repository;
    }
    private static SkillSource source(String body) {
        return new SkillSource("pack", "guide/SKILL.md", Map.of("guide/SKILL.md",
                "---\nname: guide\ndescription: Workflow\n---\n" + body));
    }
    private static final class Scripted implements ModelClient {
        private final Deque<Function<ModelRequest, ModelTurn>> scripts = new ArrayDeque<>();
        private final List<ModelRequest> requests = new ArrayList<>();
        void add(Function<ModelRequest, ModelTurn> script) { scripts.addLast(script); }
        @Override public CompletableFuture<ModelTurn> complete(ModelRequest request,
                Consumer<ModelEvent> events, CancellationSignal cancellation) {
            requests.add(request); return CompletableFuture.completedFuture(scripts.removeFirst().apply(request));
        }
    }
}
