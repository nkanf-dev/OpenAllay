package dev.openallay.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.SourceObservation;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.command.CommandCapabilityRuntime;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.workspace.AgentResultWorkspaceRegistry;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.testing.JavascriptAgentTestFixtures;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.builtin.RunJavascriptTool;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class WorldObservationToolTest {
    @ParameterizedTest
    @MethodSource("captureModes")
    void correctWorldReadsAutomaticallyProduceCapturedSourceSummaries(boolean client, boolean complete) {
        var context = JavascriptAgentTestFixtures.context("world-factory-tool");
        WorldObservationTestFixtures.Coordinator coordinator =
                new WorldObservationTestFixtures.Coordinator(client, complete);
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.capture(context.correlationId(), coordinator);
        AgentResultWorkspaceRegistry workspaces = new AgentResultWorkspaceRegistry();
        RunJavascriptTool tool = new RunJavascriptTool(
                new RhinoJavascriptRuntime(), MinecraftAgentHostGraph::new, workspaces,
                new JavascriptResultPresenter(), new CommandCapabilityRuntime(), observations);

        ToolResult.Success<RunJavascriptTool.Output> success = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(context, new RunJavascriptTool.Input(
                        """
                        const bounds = {from: {x: 0, y: 0, z: 0}, to: {x: 16, y: 0, z: 0}};
                        const blocks = world.inspect(bounds);
                        const entities = world.entities(bounds);
                        const detail = world.entity(entities.entities[0].observationId);
                        return {
                          block: blocks.blocks[0].id,
                          entity: detail.type,
                          complete: blocks.coverage.complete,
                          requestedPositions: blocks.coverage.requestedPositions,
                          loadedPositions: blocks.coverage.loadedPositions,
                          unavailableSections: blocks.coverage.unavailableSections,
                          dimension: blocks.evidence.details["minecraft:dimension"]
                        };
                        """,
                        List.of()), new CancellationSignal()).join());

        var stored = workspaces.open(context.correlationId()).open(success.value().handle()).getAsJsonObject();
        assertEquals("minecraft:oak_log", stored.get("block").getAsString());
        assertEquals("minecraft:cow", stored.get("entity").getAsString());
        assertEquals(complete, stored.get("complete").getAsBoolean());
        assertEquals(17, stored.get("requestedPositions").getAsLong());
        assertEquals(complete ? 17 : 16, stored.get("loadedPositions").getAsLong());
        assertEquals(complete ? List.of() : List.of("chunk:1,0"),
                dev.openallay.json.JsonReaders.elements(stored.getAsJsonArray("unavailableSections")).stream()
                        .map(value -> value.getAsString()).toList());
        assertEquals(WorldObservationTestFixtures.DIMENSION, stored.get("dimension").getAsString());
        assertEquals(coordinator.captured.stream().map(SourceObservation::new).toList(), success.value().sources());
        assertEquals(3, success.value().sources().size());
        assertEquals(complete ? DataCompleteness.COMPLETE : DataCompleteness.PARTIAL,
                success.value().sources().get(0).evidence().completeness());
        assertEquals(client ? DataCompleteness.PARTIAL
                        : complete ? DataCompleteness.COMPLETE : DataCompleteness.PARTIAL,
                success.value().sources().get(1).evidence().completeness());
        assertEquals(client ? DataCompleteness.PARTIAL : DataCompleteness.COMPLETE,
                success.value().sources().get(2).evidence().completeness());
        assertEquals(success.value().sources(), workspaces.open(context.correlationId())
                .sources(success.value().handle()));
        for (SourceObservation source : success.value().sources()) {
            assertEquals(client ? DataAuthority.CLIENT_VISIBLE : DataAuthority.SERVER_AUTHORITATIVE,
                    source.evidence().authority());
            assertEquals(WorldObservationTestFixtures.DIMENSION,
                    source.evidence().details().get("minecraft:dimension"));
            assertEquals(WorldObservationTestFixtures.CAPTURED_AT, source.lastCapturedAt());

        }
        tool.closeRequestScope(context.correlationId());
        assertTrue(coordinator.closed);
        assertTrue(observations.bridge(context.correlationId(), new CancellationSignal()).isEmpty());
    }

    @Test
    void havingAWorldBindingDoesNotForgeEvidenceForAProgramThatDoesNotReadIt() {
        var context = JavascriptAgentTestFixtures.context("world-factory-unused");
        WorldObservationTestFixtures.Coordinator coordinator =
                new WorldObservationTestFixtures.Coordinator(true, true);
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.capture(context.correlationId(), coordinator);
        AgentResultWorkspaceRegistry workspaces = new AgentResultWorkspaceRegistry();
        RunJavascriptTool tool = new RunJavascriptTool(
                new RhinoJavascriptRuntime(), MinecraftAgentHostGraph::new, workspaces,
                new JavascriptResultPresenter(), new CommandCapabilityRuntime(), observations);

        ToolResult.Success<RunJavascriptTool.Output> success = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(context, new RunJavascriptTool.Input("return 1;", List.of()),
                        new CancellationSignal()).join());

        assertEquals(1, success.value().preview().getAsInt());
        assertFalse(success.value().modelText().contains("input coverage:"));
        assertFalse(success.value().modelText().contains("evidence:"));
        assertTrue(success.value().sources().isEmpty());
        assertTrue(coordinator.captured.isEmpty());
        assertEquals(1, workspaces.open(context.correlationId()).size());
        assertTrue(workspaces.open(context.correlationId()).sources(success.value().handle()).isEmpty());
        tool.closeRequestScope(context.correlationId());
    }

    @Test
    void mixedWorldAndRecipeReadsKeepEachSourceAuthorityAndCompleteness() {
        var context = JavascriptAgentTestFixtures.context("world-factory-mixed");
        WorldObservationTestFixtures.Coordinator coordinator =
                new WorldObservationTestFixtures.Coordinator(true, false);
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.capture(context.correlationId(), coordinator);
        AgentResultWorkspaceRegistry workspaces = new AgentResultWorkspaceRegistry();
        RunJavascriptTool tool = new RunJavascriptTool(
                new RhinoJavascriptRuntime(), MinecraftAgentHostGraph::new, workspaces,
                new JavascriptResultPresenter(), new CommandCapabilityRuntime(), observations);

        ToolResult.Success<RunJavascriptTool.Output> success = assertInstanceOf(
                ToolResult.Success.class,
                tool.invokeAsync(context, new RunJavascriptTool.Input("""
                        const blocks = world.inspect({from: {x: 0, y: 0, z: 0}, to: {x: 16, y: 0, z: 0}});
                        return {block: blocks.blocks[0].id, recipes: mc.recipes.length};
                        """, List.of()), new CancellationSignal()).join());

        assertEquals("minecraft:oak_log", success.value().preview().getAsJsonObject().get("block").getAsString());
        assertEquals(context.recipes().orElseThrow().recipes().size(),
                success.value().preview().getAsJsonObject().get("recipes").getAsInt());
        assertEquals(List.of(new SourceObservation(coordinator.captured.get(0)),
                        new SourceObservation(context.recipes().orElseThrow().evidence())),
                success.value().sources());
        assertEquals(DataAuthority.CLIENT_VISIBLE, success.value().sources().get(0).evidence().authority());
        assertEquals(DataCompleteness.PARTIAL, success.value().sources().get(0).evidence().completeness());
        assertEquals(DataAuthority.SERVER_AUTHORITATIVE, success.value().sources().get(1).evidence().authority());
        assertEquals(DataCompleteness.COMPLETE, success.value().sources().get(1).evidence().completeness());
        assertTrue(success.value().modelText().contains(
                "input coverage: CLIENT_VISIBLE PARTIAL; SERVER_AUTHORITATIVE COMPLETE"));
        assertFalse(success.value().modelText().contains("evidence:"));
        assertFalse(success.value().modelText().contains("provenance="));
        assertEquals(success.value().sources(), workspaces.open(context.correlationId())
                .sources(success.value().handle()));
        tool.closeRequestScope(context.correlationId());
    }

    private static Stream<Arguments> captureModes() {
        return Stream.of(
                Arguments.of(true, true), Arguments.of(true, false),
                Arguments.of(false, true), Arguments.of(false, false));
    }
}
