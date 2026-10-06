package dev.openallay.bridge.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.agent.tool.ToolRuntimeCatalog;
import dev.openallay.bridge.protocol.ClientToolCallPayload;
import dev.openallay.bridge.protocol.ClientToolCancelPayload;
import dev.openallay.bridge.server.PlayerClientToolRouter;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.skill.LoadSkillTool;
import dev.openallay.skill.RetainedSkillContext;
import dev.openallay.skill.SkillCatalogManifest;
import dev.openallay.skill.SkillCatalogSnapshot;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.skill.SkillSource;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Real endpoint/chunk/router path with distinct frozen server/client catalogs; no model or game. */
final class RemoteSkillContextBridgeTest {
    @Test
    void serverProjectionOwnsReuseAndCompactionRequiresFreshClientPlaintext() {
        Bridge bridge = new Bridge(repository("client-pack", "Client instructions.", Map.of()));
        RetainedSkillContext retained = new RetainedSkillContext();
        try (Request request = bridge.open()) {
            request.prepare(List.of(), retained);
            LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
            AgentToolResult first = request.load(input);
            assertEquals("Client instructions.", output(first).content());
            assertEquals(LoadSkillTool.LoadState.COMPLETE, output(first).state());
            assertEquals(1, bridge.calls);
            assertFalse(first.modelValue().getAsString().contains("Server instructions."));
            List<ModelMessage> actual = request.prepare(history("first", input, first), retained);
            AgentToolResult reused = request.load(input);
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(reused).state());
            assertEquals("", output(reused).content());
            assertEquals(1, bridge.calls, "server must not transport an already-retained range");
            CancellationSignal cancelled = new CancellationSignal();
            cancelled.cancel();
            assertTrue(request.tools.execute("openallay__load_skill", dev.openallay.json.EngineJson.create().toJsonTree(input).getAsJsonObject(),
                    ToolInvocationContext.developmentConsole(request.id.toString()), cancelled).isCompletedExceptionally());
            assertEquals(1, bridge.calls);
            assertTrue(request.tools.skillManifest(request.id.toString()).contains("guide / SKILL.md: full"));

            request.prepare(List.of(ModelMessage.userText("A compacted summary mentions guide.")), retained);
            assertEquals("", request.tools.skillManifest(request.id.toString()));
            AgentToolResult fresh = request.load(input);
            assertEquals(LoadSkillTool.LoadState.COMPLETE, output(fresh).state());
            assertEquals("Client instructions.", output(fresh).content());
            assertEquals(2, bridge.calls, "client must not retain a lifetime delivery flag");
            assertEquals("Client instructions.", output(first).content());
            assertFalse(((ModelContent.ToolResult) actual.getLast().content().getFirst()).error());
        }
    }

    @Test
    void clientLocalToServerModelAndBackReusesOnlyTheSameCapturedClientSource() {
        SkillRepository client = repository("client-pack", "Client instructions.", Map.of());
        SkillCatalogSnapshot captured = client.snapshot(Set.of());
        ToolRegistry localTools = new ToolRegistry();
        localTools.register("test", List.of(new LoadSkillTool(captured, "client")));
        AgentToolExecutor local = new dev.openallay.agent.tool.LocalAgentToolExecutor(localTools, dev.openallay.json.EngineJson.create());
        RetainedSkillContext retained = new RetainedSkillContext();
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        local.prepareSystem("System", retained);
        local.prepareContext("local-first", List.of(), retained);
        AgentToolResult first = local.execute("openallay__load_skill", dev.openallay.json.EngineJson.create().toJsonTree(input).getAsJsonObject(),
                ToolInvocationContext.developmentConsole("local-first"), new CancellationSignal()).join();
        assertEquals(LoadSkillTool.LoadState.COMPLETE, output(first).state());
        List<ModelMessage> actual = history("local-first-load", input, first);
        local.prepareContext("local-first", actual, retained);
        local.closeRequestScope("local-first");

        Bridge bridge = new Bridge(client);
        try (Request remote = bridge.open()) {
            remote.prepare(actual, retained);
            assertEquals(output(first).source(), remote.manifest.documents().getFirst().source());
            AgentToolResult reused = remote.load(input);
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(reused).state());
            assertEquals(0, bridge.calls, "server model sees exact retained client-local source without transport");
            List<ModelMessage> remoteActual = new ArrayList<>(actual);
            remoteActual.addAll(history("remote-reuse", input, reused));
            remote.prepare(remoteActual, retained);
            actual = remoteActual;
        }

        ToolRegistry rebuilt = new ToolRegistry();
        rebuilt.register("test", List.of(new LoadSkillTool(client.snapshot(Set.of()), "client")));
        AgentToolExecutor localAgain = new dev.openallay.agent.tool.LocalAgentToolExecutor(rebuilt, dev.openallay.json.EngineJson.create());
        localAgain.prepareSystem("System", retained);
        List<ModelMessage> localProjection = localAgain.refreshContext(actual, retained);
        localAgain.prepareContext("local-again", localProjection, retained);
        AgentToolResult reuseAgain = localAgain.execute("openallay__load_skill", dev.openallay.json.EngineJson.create().toJsonTree(input).getAsJsonObject(),
                ToolInvocationContext.developmentConsole("local-again"), new CancellationSignal()).join();
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(reuseAgain).state());
        assertEquals("", output(reuseAgain).content());
        assertTrue(localAgain.skillManifest("local-again").contains("guide / SKILL.md: full"));
        localAgain.closeRequestScope("local-again");
        assertEquals(0, bridge.calls);
    }

    @Test
    void restoreAndModelSwitchReconcileActualProjectionButNeverCrossActorsOrSessions() {
        Bridge bridge = new Bridge(repository("client-pack", "Client instructions.", Map.of()));
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        List<ModelMessage> saved;
        try (Request first = bridge.open()) {
            first.prepare(List.of(), new RetainedSkillContext());
            saved = history("saved", input, first.load(input));
        }
        assertEquals(1, bridge.calls);
        RetainedSkillContext restored = new RetainedSkillContext();
        try (Request nextModel = bridge.open()) {
            nextModel.prepare(saved, restored);
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(nextModel.load(input)).state());
            assertEquals(1, bridge.calls);
        }
        try (Request otherSession = bridge.open("other", UUID.randomUUID())) {
            otherSession.prepare(List.of(ModelMessage.userText("guide has been loaded")),
                    new RetainedSkillContext());
            assertEquals(LoadSkillTool.LoadState.COMPLETE, output(otherSession.load(input)).state());
            assertEquals(2, bridge.calls);
        }
        try (Request backToFirst = bridge.open()) {
            backToFirst.prepare(saved, restored);
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(backToFirst.load(input)).state());
            assertEquals(2, bridge.calls);
        }
    }

    @Test
    void newRequestUsesActualReloadedClientManifestAndInvalidatesOnlyChangedReferences() {
        SkillRepository client = repository("client-pack", "Client instructions.",
                Map.of("references/a.md", "A original", "references/b.md", "B unchanged"));
        Bridge bridge = new Bridge(client);
        RetainedSkillContext retained = new RetainedSkillContext();
        List<ModelMessage> history = new ArrayList<>();
        LoadSkillTool.Input parent = new LoadSkillTool.Input("guide");
        LoadSkillTool.Input a = new LoadSkillTool.Input("guide", "references/a.md");
        LoadSkillTool.Input b = new LoadSkillTool.Input("guide", "references/b.md");
        SkillCatalogManifest before;
        try (Request first = bridge.open()) {
            first.prepare(history, retained);
            history.addAll(history("parent", parent, first.load(parent)));
            history.addAll(history("a", a, first.load(a)));
            history.addAll(history("b", b, first.load(b)));
            first.prepare(history, retained);
            before = first.manifest;
        }
        assertEquals(3, bridge.calls);
        assertTrue(client.reload(List.of(source("client-pack", "Client instructions.",
                Map.of("references/a.md", "A changed", "references/b.md", "B unchanged",
                        "references/c.md", "C new"))), Set.of()));
        try (Request next = bridge.open()) {
            assertFalse(before.equals(next.manifest));
            List<ModelMessage> projection = next.prepare(history, retained);
            ModelContent.ToolResult originalA = (ModelContent.ToolResult) history.get(3).content().getFirst();
            ModelContent.ToolResult projectedA = (ModelContent.ToolResult) projection.get(3).content().getFirst();
            assertFalse(projectedA.error(), "projection invalidation must not turn real success into error");
            assertTrue(projectedA.value().getAsString().contains("invalidated"));
            assertTrue(originalA.value().getAsString().contains("A original"));
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(next.load(parent)).state());
            assertEquals(List.of("references/a.md", "references/b.md", "references/c.md"),
                    output(next.load(parent)).availableReferences());
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(next.load(b)).state());
            AgentToolResult changed = next.load(a);
            assertEquals("A changed", output(changed).content());
            assertEquals(LoadSkillTool.LoadState.COMPLETE, output(changed).state());
            assertEquals(4, bridge.calls);
        }
    }

    @Test
    void partialRetainedRangesRequireTheirOwnRealPlaintextAndExactCursor() {
        Bridge bridge = new Bridge(repository("client-pack", "x".repeat(17_000), Map.of()));
        RetainedSkillContext retained = new RetainedSkillContext();
        try (Request request = bridge.open()) {
            LoadSkillTool.Input firstInput = new LoadSkillTool.Input("guide");
            AgentToolResult first = request.load(firstInput);
            assertEquals(LoadSkillTool.LoadState.CONTENT, output(first).state());
            assertFalse(output(first).complete());
            LoadSkillTool.Input secondInput = new LoadSkillTool.Input("guide", null, output(first).nextCursor());
            AgentToolResult second = request.load(secondInput);
            List<ModelMessage> both = new ArrayList<>(history("first", firstInput, first));
            both.addAll(history("second", secondInput, second));
            request.prepare(both, retained);
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(request.load(firstInput)).state());
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(request.load(secondInput)).state());
            assertEquals(2, bridge.calls);
            List<ModelMessage> onlySecond = history("second", secondInput, second);
            request.prepare(onlySecond, retained);
            String manifest = request.tools.skillManifest(request.id.toString());
            assertTrue(manifest.contains("partial"));
            assertTrue(manifest.contains("missing_offset=0"));
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(request.load(secondInput)).state());
            assertEquals(LoadSkillTool.LoadState.CONTENT, output(request.load(firstInput)).state());
            assertEquals(3, bridge.calls);

            AgentToolResult invalidCursor = request.load(new LoadSkillTool.Input("guide", null, "bad-cursor"));
            assertTrue(invalidCursor.failure());
            assertEquals("skill_cursor_invalid", invalidCursor.normalized().get("code").getAsString());
            assertEquals(4, bridge.calls);
        }
    }

    @Test
    void closingRequestDropsServerReuseBindingsAndCannotProduceAStaleSuccess() {
        Bridge bridge = new Bridge(repository("client-pack", "Client instructions.", Map.of()));
        Request request = bridge.open();
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        var retained = new RetainedSkillContext();
        request.prepare(history("actual", input, request.load(input)), retained);
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(request.load(input)).state());
        request.close();
        AgentToolResult afterClose = request.load(input);
        assertTrue(afterClose.failure());
        assertEquals("client_tool_unavailable", afterClose.normalized().get("code").getAsString());
        assertEquals("", request.tools.skillManifest(request.id.toString()));
        assertEquals(1, bridge.calls);
    }

    @Test
    void serverPreservesTheFrozenClientManifestAndOriginalPlayerFields() {
        String body = "Client notes: token=quest-token password=castle-password.";
        String provenance = "client-pack token=pack-token password=pack-password";
        SkillRepository client = repository(provenance, body, Map.of());
        SkillCatalogManifest expected = new LoadSkillTool(client.snapshot(Set.of()), "client").catalogManifest();
        Bridge bridge = new Bridge(client);
        try (Request request = bridge.open()) {
            assertEquals(expected, request.manifest);
            AgentToolResult actual = request.load(new LoadSkillTool.Input("guide"));
            assertEquals(body, output(actual).content());
            assertEquals(expected.documents().getFirst().source(), output(actual).source());
            assertEquals(request.manifest.documents().getFirst().fingerprint(), output(actual).fingerprint());
            assertEquals(provenance + ":guide/SKILL.md", output(actual).provenance());
            RetainedSkillContext retained = new RetainedSkillContext();
            request.prepare(history("original-player-fields", new LoadSkillTool.Input("guide"), actual), retained);
            AgentToolResult receipt = request.load(new LoadSkillTool.Input("guide"));
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, output(receipt).state());
            assertEquals("", output(receipt).content());
            assertEquals(output(actual).source(), output(receipt).source());
            assertEquals(output(actual).fingerprint(), output(receipt).fingerprint());
            assertEquals(1, bridge.calls);
        }
    }

    @Test
    void sameBodyFromDifferentActualSourceCannotReuseAndClientFailureKeepsItsTrueStatus() {
        SkillRepository client = repository("client-pack", "Client instructions.", Map.of());
        Bridge bridge = new Bridge(client);
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        RetainedSkillContext retained = new RetainedSkillContext();
        List<ModelMessage> old;
        try (Request request = bridge.open()) {
            old = history("old", input, request.load(input));
            request.prepare(old, retained);
        }
        assertTrue(client.reload(List.of(source("different-pack", "Client instructions.", Map.of())), Set.of()));
        try (Request current = bridge.open()) {
            current.prepare(old, retained);
            assertEquals(LoadSkillTool.LoadState.COMPLETE, output(current.load(input)).state());
            assertEquals(2, bridge.calls);
            LoadSkillTool.Input missing = new LoadSkillTool.Input("missing");
            AgentToolResult failed = current.load(missing);
            assertTrue(failed.failure());
            assertEquals("skill_not_found", failed.normalized().get("code").getAsString());
            List<ModelMessage> actualFailure = history("failed", missing, failed);
            List<ModelMessage> projected = current.prepare(actualFailure, retained);
            assertEquals(actualFailure, projected);
            assertTrue(((ModelContent.ToolResult) projected.getLast().content().getFirst()).error());
        }
    }

    private static LoadSkillTool.Output output(AgentToolResult result) {
        assertFalse(result.failure(), result.normalized().toString());
        return dev.openallay.json.EngineJson.create().fromJson(result.normalized().get("value"), LoadSkillTool.Output.class);
    }

    private static List<ModelMessage> history(String id, LoadSkillTool.Input input, AgentToolResult result) {
        JsonObject arguments = dev.openallay.json.EngineJson.create().toJsonTree(input).getAsJsonObject();
        return List.of(
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        id, "openallay__load_skill", arguments))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        id, result.modelValue(), result.failure()))));
    }

    private static SkillRepository repository(String provenance, String body, Map<String, String> refs) {
        SkillRepository result = new SkillRepository(new SkillParser(), Set.of());
        assertTrue(result.reload(List.of(source(provenance, body, refs)), Set.of()));
        return result;
    }

    private static SkillSource source(String provenance, String body, Map<String, String> refs) {
        Map<String, String> files = new java.util.HashMap<>();
        files.put("guide/SKILL.md", "---\nname: guide\ndescription: Guide the player\n---\n" + body);
        refs.forEach((name, text) -> files.put("guide/" + name, text));
        return new SkillSource(provenance, "guide/SKILL.md", files);
    }

    private static final class Bridge {
        private final Gson gson = dev.openallay.json.EngineJson.create();
        private final UUID actor = UUID.randomUUID();
        private final SkillRepository client;
        private final SkillCatalogSnapshot server = repository("server-pack", "Server instructions.", Map.of())
                .snapshot(Set.of());
        private PlayerClientToolRouter router;
        private ClientToolExecutionEndpoint endpoint;
        private int calls;

        private Bridge(SkillRepository client) {
            this.client = client;
            ToolRegistry trusted = new ToolRegistry();
            trusted.register("test", List.of(new LoadSkillTool(server)));
            router = new PlayerClientToolRouter(trusted, gson, new PlayerClientToolRouter.Transport() {
                @Override
                public boolean call(UUID actorId, ClientToolCallPayload payload) {
                    calls++;
                    endpoint.handle(payload);
                    return true;
                }

                @Override
                public void cancel(UUID actorId, ClientToolCancelPayload payload) {
                    endpoint.cancel(payload);
                }
            });
            endpoint = new ClientToolExecutionEndpoint(
                    (capabilities, correlation, cancellation) -> CompletableFuture.completedFuture(
                            ToolInvocationContext.developmentConsole(correlation)),
                    chunk -> assertTrue(router.receive(owner(chunk.requestId()), chunk)),
                    gson, 137, (java.util.concurrent.Executor) Runnable::run);
        }

        private final Map<UUID, UUID> actors = new java.util.HashMap<>();
        private UUID owner(UUID requestId) { return actors.get(requestId); }
        private Request open() { return open("main", actor); }
        private Request open(String session, UUID requestActor) {
            UUID id = UUID.randomUUID();
            ToolRegistry tools = new ToolRegistry();
            tools.register("test", List.of(new LoadSkillTool(client.snapshot(Set.of()))));
            var opened = assertInstanceOf(ToolResult.Success.class, endpoint.open(id, session,
                    ToolRuntimeCatalog.from(tools.registrations(), Set.of())));
            var local = (ClientToolExecutionEndpoint.OpenedRequest) opened.value();
            var remote = assertInstanceOf(ToolResult.Success.class, router.open(requestActor, id, session,
                    local.clientToolIds(), server, local.skillDocuments()));
            actors.put(id, requestActor);
            return new Request(this, id, requestActor, (AgentToolExecutor) remote.value(), local.skillDocuments());
        }
    }

    private static final class Request implements AutoCloseable {
        private final Bridge bridge;
        private final UUID id;
        private final UUID actor;
        private final AgentToolExecutor tools;
        private final SkillCatalogManifest manifest;

        private Request(Bridge bridge, UUID id, UUID actor, AgentToolExecutor tools, SkillCatalogManifest manifest) {
            this.bridge = bridge;
            this.id = id;
            this.actor = actor;
            this.tools = tools;
            this.manifest = manifest;
        }

        private List<ModelMessage> prepare(List<ModelMessage> messages, RetainedSkillContext retained) {
            tools.prepareSystem("system", retained);
            List<ModelMessage> actual = tools.refreshContext(messages, retained);
            tools.prepareContext(id.toString(), actual, retained);
            return actual;
        }

        private AgentToolResult load(LoadSkillTool.Input input) {
            return tools.execute("openallay__load_skill", bridge.gson.toJsonTree(input).getAsJsonObject(),
                    ToolInvocationContext.developmentConsole(id.toString()), new CancellationSignal()).join();
        }

        @Override
        public void close() {
            bridge.router.close(actor, id);
            tools.closeRequestScope(id.toString());
            bridge.endpoint.close(id);
            bridge.actors.remove(id);
        }
    }
}
