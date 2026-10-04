package dev.openallay.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.image.ImageReference;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.command.CommandCapabilityRuntime;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.workspace.AgentResultWorkspaceRegistry;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.testing.JavascriptAgentTestFixtures;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.builtin.RunJavascriptTool;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

final class WorldViewBridgeTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final ImageReference IMAGE = new ImageReference("a".repeat(64), "image/png", 20, 10, 100);
    private static final EvidenceMetadata EVIDENCE = new EvidenceMetadata(DataAuthority.DETERMINISTIC_TEST,
            DataCompleteness.COMPLETE, Instant.EPOCH, "openallay:test_view", "openallay:test", "26.2", "test", Map.of());
    private static final WorldFocusObservation.Camera CAMERA = new WorldFocusObservation.Camera(
            1, 2, 3, 90, 30, 70, "third_person_back", true, true, ACTOR);
    private static final WorldFocusObservation.Screen SCREEN = new WorldFocusObservation.Screen(
            "test.NativeMenu", "Chest", 20, 10, false, true, "game_ui");

    @Test void defaultCaptureIsWorldAndCollectorSeesImagesNotReturnedByScript() {
        FakeCoordinator coordinator = new FakeCoordinator();
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.capture("view", coordinator);
        List<ImageReference> images = new ArrayList<>();
        var bridge = observations.bridge("view", new CancellationSignal(), ignored -> {}, images::add).orElseThrow();
        var execution = new RhinoJavascriptRuntime().execute(
                "world.capture(); world.capture({target:'GAME_UI'}); return {done:true};",
                Map.of(), Map.of(), Map.of(), new CancellationSignal(), null, bridge);
        assertTrue(execution.value().getAsJsonObject().get("done").getAsBoolean());
        assertEquals(List.of(WorldViewRequest.Target.WORLD, WorldViewRequest.Target.GAME_UI), coordinator.targets);
        assertEquals(List.of(IMAGE, IMAGE), images);
        observations.closeRequest("view");
    }

    @Test void captureRejectsUnknownOptionInsteadOfPretendingToHonorHudFlags() {
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.capture("view", new FakeCoordinator());
        var bridge = observations.bridge("view", new CancellationSignal()).orElseThrow();
        JavascriptExecutionException failure = assertThrows(JavascriptExecutionException.class, () ->
                new RhinoJavascriptRuntime().execute("return world.capture({includeHud:true});",
                        Map.of(), Map.of(), Map.of(), new CancellationSignal(), null, bridge));
        assertEquals("world_observation_invalid", failure.code());
        observations.closeRequest("view");
    }

    @Test void invalidTargetAndArityAreRejected() {
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.capture("view", new FakeCoordinator());
        var bridge = observations.bridge("view", new CancellationSignal()).orElseThrow();
        for (String source : List.of("return world.capture({target:'CHAT'});", "return world.focus(1);")) {
            assertThrows(JavascriptExecutionException.class, () -> new RhinoJavascriptRuntime().execute(source,
                    Map.of(), Map.of(), Map.of(), new CancellationSignal(), null, bridge));
        }
        observations.closeRequest("view");
    }

    @Test void runJavascriptOutputCarriesEveryCaptureOutsideItsCanonicalReturn() {
        var context = JavascriptAgentTestFixtures.context("view-tool");
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.capture(context.correlationId(), new FakeCoordinator());
        RunJavascriptTool tool = new RunJavascriptTool(new RhinoJavascriptRuntime(), MinecraftAgentHostGraph::new,
                new AgentResultWorkspaceRegistry(), new JavascriptResultPresenter(),
                new CommandCapabilityRuntime(), observations);
        ToolResult.Success<RunJavascriptTool.Output> success = assertInstanceOf(ToolResult.Success.class,
                tool.invokeAsync(context, new RunJavascriptTool.Input(
                        "world.capture(); world.capture({target:'GAME_UI'}); return 7;", List.of()),
                        new CancellationSignal()).join());
        assertEquals(List.of(IMAGE, IMAGE), success.value().images());
        assertEquals(7, success.value().preview().getAsInt());
        tool.closeRequestScope(context.correlationId());
        observations.releaseImageProducers(context.correlationId()).join();
    }

    private static final class FakeCoordinator implements WorldObservationCoordinator {
        final List<WorldViewRequest.Target> targets = new ArrayList<>();
        @Override public CompletionStage<WorldViewCapture> capture(WorldViewRequest request, CancellationSignal cancellation) {
            targets.add(request.target());
            return CompletableFuture.completedFuture(new WorldViewCapture("frame-" + targets.size(), Instant.EPOCH,
                    ACTOR, "minecraft:overworld", request.target(), request.target() == WorldViewRequest.Target.GAME_UI,
                    request.target() == WorldViewRequest.Target.GAME_UI, 20, 10, 1, CAMERA, SCREEN, IMAGE, EVIDENCE));
        }
        @Override public CompletionStage<BlockObservation> inspect(WorldObservationRequest request, CancellationSignal cancellation) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        @Override public CompletionStage<EntityObservation> entities(WorldObservationRequest request, CancellationSignal cancellation) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        @Override public CompletionStage<WorldEntitySnapshot> entity(String observationId, CancellationSignal cancellation) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
    }
}
