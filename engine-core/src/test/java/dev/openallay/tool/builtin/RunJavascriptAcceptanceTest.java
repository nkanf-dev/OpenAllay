package dev.openallay.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.openallay.context.EvidenceBearing;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.SourceObservation;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.script.command.CommandCapabilityRuntime;
import dev.openallay.script.command.CommandCatalogSnapshot;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.extension.JavascriptDataModule;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.workspace.AgentResultWorkspaceRegistry;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.testing.JavascriptAgentTestFixtures;
import dev.openallay.world.WorldEntitySnapshot;
import dev.openallay.world.EntityObservation;
import dev.openallay.world.WorldObservationCoverage;
import dev.openallay.world.BlockObservation;
import dev.openallay.world.WorldObservationRequest;
import dev.openallay.world.WorldObservationCoordinator;
import dev.openallay.world.WorldObservationRuntime;
import dev.openallay.tool.ToolResult;
import dev.openallay.trace.replay.ToolResultNormalizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Deterministic acceptance for JavaScript computations and captured Minecraft analysis. */
final class RunJavascriptAcceptanceTest {
    private record DirectModule(int value) {}

    private final AgentResultWorkspaceRegistry workspaces =
            new AgentResultWorkspaceRegistry();
    private final RunJavascriptTool tool = new RunJavascriptTool(
            new RhinoJavascriptRuntime(),
            MinecraftAgentHostGraph::new,
            workspaces,
            new JavascriptResultPresenter());
    private final ToolInvocationContext context =
            JavascriptAgentTestFixtures.context("javascript-acceptance");

    @Test
    void findsHighestDamageSwordInOneInvocation() {
        var result = invoke("""
                return mc.items
                  .filter(item => item.tags.includes("minecraft:swords"))
                  .map(item => ({
                    id: item.id,
                    damage: Number(item.properties["minecraft:attack_damage"])
                  }))
                  .filter(item => Number.isFinite(item.damage))
                  .sort((a, b) => b.damage - a.damage)
                  .slice(0, 1);
                """);

        assertEquals(JavascriptAgentTestFixtures.HIGHEST_DAMAGE_SWORD, result.getAsJsonArray()
                .get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(14, result.getAsJsonArray()
                .get(0).getAsJsonObject().get("damage").getAsInt());
    }

    @Test
    void findsStrongestPoisonItemAndItsProductionRecipeInOneInvocation() {
        var result = invoke("""
                const candidates = mc.items.flatMap(item =>
                  (item.properties["minecraft:effects"] ?? [])
                    .filter(effect => effect.id === "minecraft:poison")
                    .map(effect => ({
                      itemId: item.id,
                      amplifier: Number(effect.amplifier ?? 0),
                      duration: Number(effect.duration ?? 0)
                    })))
                  .sort((a, b) =>
                    (b.amplifier - a.amplifier) || (b.duration - a.duration));
                const best = candidates[0];
                return {
                  best,
                  recipes: mc.recipes
                    .filter(recipe => recipe.outputs.some(output =>
                      output.stack.itemId === best.itemId))
                    .map(recipe => ({
                      recipeId: recipe.id,
                      ingredients: recipe.ingredients.map(ingredient => ({
                        count: ingredient.count,
                        alternatives: ingredient.alternatives.map(value => value.id)
                      }))
                    }))
                };
                """);

        assertEquals(JavascriptAgentTestFixtures.STRONGEST_POISON_ITEM, result.getAsJsonObject()
                .getAsJsonObject("best").get("itemId").getAsString());
        assertEquals(JavascriptAgentTestFixtures.STRONGEST_POISON_ITEM, result.getAsJsonObject()
                .getAsJsonArray("recipes").get(0).getAsJsonObject()
                .get("recipeId").getAsString());
    }

    @Test
    void findsContainerRecipeWithFewestConsumedMaterialUnitsInOneInvocation() {
        var result = invoke("""
                const items = new Map(mc.items.map(item => [item.id, item]));
                return mc.recipes
                  .filter(recipe => recipe.outputs.some(output =>
                    items.get(output.stack.itemId)?.tags.includes("openallay:containers")))
                  .map(recipe => ({
                    recipeId: recipe.id,
                    output: recipe.outputs[0].stack.itemId,
                    materialUnits: recipe.ingredients
                      .filter(ingredient => ingredient.consumed)
                      .reduce((sum, ingredient) => sum + Number(ingredient.count), 0)
                  }))
                  .sort((a, b) => a.materialUnits - b.materialUnits)
                  .slice(0, 1);
                """);

        assertEquals(JavascriptAgentTestFixtures.LEAST_MATERIAL_CONTAINER, result.getAsJsonArray()
                .get(0).getAsJsonObject().get("output").getAsString());
        assertEquals(2, result.getAsJsonArray()
                .get(0).getAsJsonObject().get("materialUnits").getAsInt());
    }

    @Test
    void ordinaryJavascriptComputationPublishesAndNormalizesWithEmptySources() {
        ToolResult.Success<RunJavascriptTool.Output> success = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(context, new RunJavascriptTool.Input("""
                        const squares = [1, 2, 3, 4, 5, 6]
                          .filter(value => value % 2 === 0)
                          .map(value => value * value);
                        return {squares, total: squares.reduce((sum, value) => sum + value, 0)};
                        """, List.of()), new CancellationSignal()).join());

        var canonical = workspaces.open(context.correlationId())
                .open(success.value().handle()).getAsJsonObject();
        assertEquals(56, canonical.get("total").getAsInt());
        assertEquals(List.of(4, 16, 36), dev.openallay.json.JsonReaders.elements(canonical.getAsJsonArray("squares")).stream()
                .map(value -> value.getAsInt()).toList());
        assertTrue(success.value().sources().isEmpty());
        assertTrue(workspaces.open(context.correlationId()).sources(success.value().handle()).isEmpty());
        assertFalse(success.value().modelText().isBlank());
        assertFalse(success.value().modelText().contains("source="));
        assertFalse(EvidenceBearing.class.isAssignableFrom(RunJavascriptTool.Output.class));
        var normalized = new ToolResultNormalizer(new Gson()).normalize(success, RunJavascriptTool.Output.class);
        assertEquals("success", normalized.get("status").getAsString());
        assertTrue(normalized.getAsJsonObject("value").getAsJsonArray("sources").isEmpty());
        assertFalse(normalized.getAsJsonObject("value").has("evidence"));
    }

    @Test
    void authorizedJavaNioComputationWithoutMinecraftDataRunsItsSideEffectExactlyOnce(
            @TempDir Path directory) throws Exception {
        Path file = directory.resolve("values.txt");
        Files.writeString(file, "2\n3\n");
        ToolInvocationContext base = ToolInvocationContext.developmentConsole("javascript-nio");
        ToolInvocationContext authorized = new ToolInvocationContext(
                base.correlationId(), base.capturedAt(), base.caller(), base.player(), base.registries(),
                base.recipes(), base.observableGameState(), base.metrics(), true);
        String source = """
                const Files = Java.type('java.nio.file.Files');
                const Path = Java.type('java.nio.file.Path');
                const StandardOpenOption = Java.type('java.nio.file.StandardOpenOption');
                const file = Path.of(%s);
                Files.writeString(file, "7\\n", StandardOpenOption.APPEND);
                const values = String(Files.readString(file)).trim().split(/\\s+/).map(Number);
                return {count: values.length, sum: values.reduce((sum, value) => sum + value, 0)};
                """.formatted(new Gson().toJson(file.toString()));

        ToolResult.Success<RunJavascriptTool.Output> success = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(authorized, new RunJavascriptTool.Input(source, List.of()),
                        new CancellationSignal()).join());

        assertEquals(3, success.value().preview().getAsJsonObject().get("count").getAsInt());
        assertEquals(12, success.value().preview().getAsJsonObject().get("sum").getAsInt());
        assertEquals("2\n3\n7\n", Files.readString(file), "the successful operation must not be retried");
        assertTrue(success.value().sources().isEmpty());
        var normalized = new ToolResultNormalizer(new Gson()).normalize(success, RunJavascriptTool.Output.class);
        assertEquals("success", normalized.get("status").getAsString());
        assertEquals(1, workspaces.open(authorized.correlationId()).size());
        assertTrue(workspaces.open(authorized.correlationId()).sources(success.value().handle()).isEmpty());
        tool.closeRequestScope(authorized.correlationId());
    }

    @Test
    void pureWorkspaceResultCanBeReopenedAndComputedWithEmptySources() {
        ToolResult.Success<RunJavascriptTool.Output> first = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(context, new RunJavascriptTool.Input(
                        "return [2, 3, 5];", List.of()), new CancellationSignal()).join());
        ToolResult.Success<RunJavascriptTool.Output> second = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(context, new RunJavascriptTool.Input(
                        "return workspace.open('" + first.value().handle() + "').map(value => value * value);",
                        List.of(first.value().handle())), new CancellationSignal()).join());

        assertEquals(List.of(4, 9, 25), dev.openallay.json.JsonReaders.elements(workspaces.open(context.correlationId())
                .open(second.value().handle()).getAsJsonArray()).stream()
                .map(value -> value.getAsInt()).toList());
        assertTrue(first.value().sources().isEmpty());
        assertTrue(second.value().sources().isEmpty());
        assertTrue(workspaces.open(context.correlationId()).sources(second.value().handle()).isEmpty());
    }

    @Test
    void capturesOneDetachedProjectionPerRequestAcrossJavascriptCalls() {
        AtomicInteger captures = new AtomicInteger();
        JavascriptDataModuleRegistry extensions = new JavascriptDataModuleRegistry();
        extensions.register("test", List.of(new JavascriptDataModule() {
            @Override
            public String id() {
                return "test:counter";
            }

            @Override
            public java.lang.reflect.Type valueType() {
                return DirectModule.class;
            }

            @Override
            public Snapshot capture(ToolInvocationContext ignored) {
                captures.incrementAndGet();
                return new Snapshot(
                        new DirectModule(1),
                        List.of(JavascriptAgentTestFixtures.context("evidence")
                                .registries()
                                .orElseThrow()
                                .evidence()));
            }
        }));
        RunJavascriptTool cached = new RunJavascriptTool(
                new RhinoJavascriptRuntime(),
                invocation -> new MinecraftAgentHostGraph(
                        invocation,
                        dev.openallay.knowledge.KnowledgeSnapshot::empty,
                        extensions),
                new AgentResultWorkspaceRegistry(),
                new JavascriptResultPresenter());

        assertInstanceOf(
                ToolResult.Success.class,
                cached.invokeAsync(
                                context,
                                new RunJavascriptTool.Input(
                                        "return mc.extensions['test:counter'];", List.of()),
                                new CancellationSignal())
                        .join());
        assertInstanceOf(
                ToolResult.Success.class,
                cached.invokeAsync(
                                context,
                                new RunJavascriptTool.Input(
                                        "return mc.extensions['test:counter'];", List.of()),
                                new CancellationSignal())
                        .join());

        assertEquals(1, captures.get());
        cached.closeRequestScope(context.correlationId());
        assertInstanceOf(
                ToolResult.Success.class,
                cached.invokeAsync(
                                context,
                                new RunJavascriptTool.Input(
                                        "return mc.extensions['test:counter'];", List.of()),
                                new CancellationSignal())
                        .join());
        assertEquals(2, captures.get());
    }

    @Test
    void unsupportedExtensionDeclarationIsIsolatedFromIndependentDirectRecordModule() {
        JavascriptDataModuleRegistry extensions = new JavascriptDataModuleRegistry();
        extensions.register("test", List.of(
                module("test:good", new DirectModule(7)),
                module("test:unsupported", new Object())));
        RunJavascriptTool isolated = new RunJavascriptTool(
                new RhinoJavascriptRuntime(),
                invocation -> new MinecraftAgentHostGraph(
                        invocation,
                        dev.openallay.knowledge.KnowledgeSnapshot::empty,
                        extensions),
                new AgentResultWorkspaceRegistry(),
                new JavascriptResultPresenter());

        ToolResult.Success<RunJavascriptTool.Output> success = assertInstanceOf(
                ToolResult.Success.class,
                isolated.invokeAsync(
                                context,
                                new RunJavascriptTool.Input(
                                        "return mc.extensions['test:good'].value;",
                                        List.of()),
                                new CancellationSignal())
                        .join());
        assertEquals(7, success.value().preview().getAsInt());

        ToolResult.Success<RunJavascriptTool.Output> diagnostics = assertInstanceOf(
                ToolResult.Success.class,
                isolated.invokeAsync(
                                context,
                                new RunJavascriptTool.Input(
                                        "return mc.extensionDiagnostics;",
                                        List.of()),
                                new CancellationSignal())
                        .join());
        assertEquals(
                "javascript_host_type_unsupported",
                diagnostics.value().preview()
                        .getAsJsonArray()
                        .get(0)
                        .getAsJsonObject()
                        .get("code")
                        .getAsString());
    }

    @Test
    void automaticallyExposesCapturedMinecraftRootsToRhino() {
        ToolResult.Success<RunJavascriptTool.Output> success = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(
                                context,
                                new RunJavascriptTool.Input(
                                        "return {items: mc.items.length, recipes: mc.recipes.length};",
                                        List.of()),
                                new CancellationSignal())
                        .join());
        var canonical = workspaces
                .open(context.correlationId())
                .open(success.value().handle())
                .getAsJsonObject();

        assertEquals(context.registries().orElseThrow().entries().size(), canonical.get("items").getAsInt());
        assertEquals(context.recipes().orElseThrow().recipes().size(), canonical.get("recipes").getAsInt());
        assertEquals(List.of(context.registries().orElseThrow().evidence(),
                        context.recipes().orElseThrow().evidence()),
                success.value().sources().stream().map(SourceObservation::evidence).toList());
    }

    @Test
    void playerAndGameRootsReadTheirDocumentedPositionPathsWithoutPredeclaration() {
        for (String root : List.of("player", "game")) {
            String path = "player".equals(root) ? "mc.player.position" : "mc.game.player.player.position";
            ToolResult.Success<RunJavascriptTool.Output> success = assertInstanceOf(
                    ToolResult.Success.class,
                    tool.invokeAsync(context, new RunJavascriptTool.Input(
                            "return " + path + ";", List.of()), new CancellationSignal()).join());
            var position = workspaces.open(context.correlationId()).open(success.value().handle()).getAsJsonObject();
            var expected = context.player().orElseThrow().position();
            assertEquals(expected.x(), position.get("x").getAsInt());
            assertEquals(expected.y(), position.get("y").getAsInt());
            assertEquals(expected.z(), position.get("z").getAsInt());
            assertFalse(success.value().sources().isEmpty());
        }
    }

    @Test
    void functionReturnIsAnActionableFailureWithoutPublishingAnEmptyFact() {
        ToolResult.Failure<RunJavascriptTool.Output> failure = assertInstanceOf(
                ToolResult.Failure.class,
                tool.invokeAsync(context, new RunJavascriptTool.Input(
                        "var module = require('openallay:crafting'); var count = mc.items.length; return module;",
                        List.of()), new CancellationSignal()).join());
        assertEquals("javascript_result_invalid", failure.code());
        assertTrue(failure.message().contains("Return JSON data from the operation"));
        assertTrue(failure.message().contains("not the function or module itself"));
        assertTrue(failure.message().contains("does not mean the operation is unavailable"));
        assertEquals(0, workspaces.open(context.correlationId()).size());
        ToolResult.Success<RunJavascriptTool.Output> corrected = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(context, new RunJavascriptTool.Input(
                        "var module = require('openallay:crafting'); return {count: mc.items.length};",
                        List.of()), new CancellationSignal()).join());
        assertTrue(corrected.value().preview().getAsJsonObject().get("count").getAsInt() > 0);
        assertFalse(corrected.value().sources().isEmpty());
    }

    @Test
    void reportsOnlySourcesForRootsActuallyReadByThisInvocation() {
        ToolResult.Success<RunJavascriptTool.Output> items = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(
                                context,
                                new RunJavascriptTool.Input("return mc.items.length;", List.of()),
                                new CancellationSignal())
                        .join());
        EvidenceMetadata expected = context.registries().orElseThrow().evidence();
        assertEquals(List.of(new SourceObservation(expected)), items.value().sources());
        assertFalse(items.value().modelText().contains("source="));

        ToolResult.Success<RunJavascriptTool.Output> noRead = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(
                                context,
                                new RunJavascriptTool.Input("return 1;", List.of()),
                                new CancellationSignal())
                        .join());
        assertEquals(1, noRead.value().preview().getAsInt());
        assertTrue(noRead.value().sources().isEmpty());
    }

    @Test
    void schemaDiscoverySucceedsWithoutClaimingCapturedDataSources() {
        ToolResult.Success<RunJavascriptTool.Output> listed = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(
                                context,
                                new RunJavascriptTool.Input("return schema.list();", List.of()),
                                new CancellationSignal())
                        .join());
        assertFalse(workspaces.open(context.correlationId())
                .open(listed.value().handle()).getAsJsonArray().isEmpty());
        assertTrue(listed.value().sources().isEmpty());
        assertFalse(listed.value().modelText().contains("source="));
    }

    @Test
    void requestMetadataAndCapabilityInspectionDoNotManufactureSources() {
        ToolResult.Success<RunJavascriptTool.Output> success = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(context, new RunJavascriptTool.Input("""
                        return {
                          caller: mc.caller.displayName,
                          metrics: mc.metrics,
                          capturedAt: mc.capturedAt,
                          capabilities: mc.capabilities
                        };
                        """, List.of()), new CancellationSignal()).join());

        var stored = workspaces.open(context.correlationId()).open(success.value().handle()).getAsJsonObject();
        assertEquals(context.caller().displayName(), stored.get("caller").getAsString());
        assertTrue(success.value().sources().isEmpty());
    }

    @Test
    void workspaceHandleCarriesOnlyThePriorResultSourcesWhenReopened() {
        ToolResult.Success<RunJavascriptTool.Output> first = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(
                                context,
                                new RunJavascriptTool.Input(
                                        "return mc.items.length;", List.of()),
                                new CancellationSignal())
                        .join());
        ToolResult.Success<RunJavascriptTool.Output> second = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(
                                context,
                                new RunJavascriptTool.Input(
                                        "workspace.open(\"" + first.value().handle() + "\"); "
                                                + "return workspace.open(\"" + first.value().handle() + "\");",
                                        List.of(first.value().handle())),
                                new CancellationSignal())
                        .join());
        assertEquals(first.value().sources(), second.value().sources());
    }

    @Test
    void worldObservationEvidenceIsIncludedOnlyForCallsThatActuallyRun() {
        WorldObservationRuntime world = new WorldObservationRuntime();
        EvidenceMetadata worldEvidence = new EvidenceMetadata(
                dev.openallay.context.DataAuthority.CLIENT_VISIBLE,
                dev.openallay.context.DataCompleteness.PARTIAL,
                Instant.EPOCH,
                "minecraft:client_blocks",
                "minecraft:client_world_observation",
                "26.2",
                "fabric",
                Map.of());
        world.capture(context.correlationId(), new WorldObservationCoordinator() {
            @Override public CompletionStage<BlockObservation> inspect(
                    WorldObservationRequest request, CancellationSignal cancellation) {
                return CompletableFuture.completedFuture(new BlockObservation(
                        request.bounds(), List.of(),
                        new WorldObservationCoverage(request.bounds().volume(), 0, false, List.of()),
                        worldEvidence));
            }
            @Override public CompletionStage<EntityObservation> entities(
                    WorldObservationRequest request, CancellationSignal cancellation) {
                return CompletableFuture.completedFuture(new EntityObservation(
                        request.bounds(), List.of(),
                        new WorldObservationCoverage(request.bounds().volume(), 0, false, List.of()),
                        worldEvidence));
            }
            @Override public CompletionStage<WorldEntitySnapshot> entity(
                    String observationId, CancellationSignal cancellation) {
                throw new AssertionError("unexpected entity lookup");
            }
        });
        RunJavascriptTool observed = new RunJavascriptTool(
                new RhinoJavascriptRuntime(), MinecraftAgentHostGraph::new,
                new AgentResultWorkspaceRegistry(), new JavascriptResultPresenter(),
                new CommandCapabilityRuntime(), world);
        ToolResult.Success<RunJavascriptTool.Output> success = assertInstanceOf(
                ToolResult.Success.class,
                observed.invokeAsync(context, new RunJavascriptTool.Input(
                        "return world.inspect({from:{x:0,y:0,z:0},to:{x:0,y:0,z:0}}).coverage.complete;",
                        List.of()), new CancellationSignal()).join());
        assertEquals(List.of(new SourceObservation(worldEvidence)), success.value().sources());
    }

    @Test
    void usesEnabledCommandsDirectlyWithoutRootPredeclaration() {
        CommandCapabilityRuntime commands = new CommandCapabilityRuntime();
        commands.replace(new CommandCapabilityConfig(
                true));
        UUID actor = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        commands.capture(
                context.correlationId(),
                actor,
                new CommandCatalogSnapshot(Instant.EPOCH, List.of()),
                (expectedActor, command, cancellation) -> {
                    commands.acceptFeedback(expectedActor, "command result: " + command);
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                });
        RunJavascriptTool commandTool = new RunJavascriptTool(
                new RhinoJavascriptRuntime(),
                MinecraftAgentHostGraph::new,
                new AgentResultWorkspaceRegistry(),
                new JavascriptResultPresenter(),
                commands);

        ToolResult<RunJavascriptTool.Output> commandResult = commandTool.invokeAsync(
                        context,
                        new RunJavascriptTool.Input(
                                "return commands.run('/version');", List.of()),
                        new CancellationSignal())
                .join();
        if (commandResult instanceof ToolResult.Failure<RunJavascriptTool.Output> failure) {
            throw new AssertionError(failure.code() + ": " + failure.message());
        }
        ToolResult.Success<RunJavascriptTool.Output> success =
                assertInstanceOf(ToolResult.Success.class, commandResult);

        assertEquals(
                "feedback",
                success.value().preview().getAsJsonObject().get("state").getAsString());
        assertEquals(
                "command result: version",
                success.value().preview().getAsJsonObject()
                        .getAsJsonArray("messages")
                        .get(0)
                        .getAsString());
    }

    @Test
    void disabledCommandsRemainUndefinedAndFailOnlyWhenAccessed() {
        ToolResult.Success<RunJavascriptTool.Output> availability = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(context, new RunJavascriptTool.Input(
                        "return typeof commands;", List.of()), new CancellationSignal()).join());
        assertEquals("undefined", availability.value().preview().getAsString());
        assertTrue(availability.value().sources().isEmpty());

        ToolResult.Failure<RunJavascriptTool.Output> failure = assertInstanceOf(
                ToolResult.Failure.class,
                tool.invokeAsync(
                                context,
                                new RunJavascriptTool.Input("return commands.list();", List.of()),
                                new CancellationSignal())
                        .join());
        assertEquals("javascript_error", failure.code());
    }

    private com.google.gson.JsonElement invoke(String source) {
        ToolResult<RunJavascriptTool.Output> raw = tool.invokeAsync(
                        context,
                        new RunJavascriptTool.Input(source, List.of()),
                        new CancellationSignal())
                .join();
        ToolResult.Success<RunJavascriptTool.Output> success =
                assertInstanceOf(ToolResult.Success.class, raw);
        assertFalse(success.value().sources().isEmpty());
        return workspaces.open(context.correlationId()).open(success.value().handle());
    }

    private static JavascriptDataModule module(String id, Object value) {
        return new JavascriptDataModule() {
            @Override public String id() { return id; }

            @Override public java.lang.reflect.Type valueType() { return value.getClass(); }

            @Override
            public Snapshot capture(ToolInvocationContext ignored) {
                return new Snapshot(
                        value,
                        List.of(JavascriptAgentTestFixtures.context("module-evidence")
                                .registries().orElseThrow().evidence()));
            }
        };
    }

}
