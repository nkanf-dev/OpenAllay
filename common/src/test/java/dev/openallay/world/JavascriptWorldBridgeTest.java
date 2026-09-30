package dev.openallay.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.JavascriptExecution;
import dev.openallay.script.RhinoJavascriptRuntime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

final class JavascriptWorldBridgeTest {
    @Test
    void exposesNormalizedFocusedBlocksAndEntityDrillDown() {
        RecordingCoordinator coordinator = new RecordingCoordinator();
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.capture("request", coordinator);
        var bridge = observations.bridge("request", new CancellationSignal()).orElseThrow();

        JavascriptExecution result = new RhinoJavascriptRuntime().execute(
                """
                const slice = world.inspect(
                  {from: {x: 4, y: 8, z: 3}, to: {x: 2, y: 8, z: 1}},
                  {includeAir: false});
                const entities = world.entities(
                  {from: {x: 0, y: 0, z: 0}, to: {x: 8, y: 8, z: 8}},
                  {type: "minecraft:cow"});
                const detail = world.entity(entities.entities[0].observationId);
                return {
                  block: slice.blocks[0].id,
                  entity: detail.type,
                  complete: slice.coverage.complete
                };
                """,
                Map.of(),
                Map.of(),
                Map.of(),
                new CancellationSignal(),
                null,
                bridge);

        assertEquals("minecraft:oak_log", result.value().getAsJsonObject()
                .get("block").getAsString());
        assertEquals("minecraft:cow", result.value().getAsJsonObject()
                .get("entity").getAsString());
        assertTrue(result.value().getAsJsonObject().get("complete").getAsBoolean());
        assertEquals(new WorldBounds(
                        new WorldPosition(2, 8, 1),
                        new WorldPosition(4, 8, 3)),
                coordinator.blocks.bounds());
        assertEquals("minecraft:cow", coordinator.entities.entityType());
    }

    @Test
    void bridgeReportsEvidenceOnlyForWorldCallsActuallyMade() {
        RecordingCoordinator coordinator = new RecordingCoordinator();
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.capture("request", coordinator);
        List<EvidenceMetadata> captured = new java.util.ArrayList<>();
        var bridge = observations.bridge("request", new CancellationSignal(), captured::add)
                .orElseThrow();

        new RhinoJavascriptRuntime().execute(
                "return world.inspect({from:{x:0,y:0,z:0},to:{x:0,y:0,z:0}}).blocks.length;",
                Map.of(), Map.of(), Map.of(), new CancellationSignal(), null, bridge);

        assertEquals(List.of(evidence()), captured);
    }

    @Test
    void runtimeDropsRequestScopedCoordinator() {
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.capture("request", new RecordingCoordinator());
        assertTrue(observations.bridge("request", new CancellationSignal()).isPresent());

        observations.closeRequest("request");

        assertTrue(observations.bridge("request", new CancellationSignal()).isEmpty());
    }

    @Test
    void repeatedCaptureKeepsTheRequestSnapshotAndClosesTheUnusedCandidate() {
        RecordingCoordinator first = new RecordingCoordinator();
        RecordingCoordinator second = new RecordingCoordinator();
        WorldObservationRuntime observations = new WorldObservationRuntime();

        observations.capture("request", first);
        observations.capture("request", second);

        assertTrue(second.closed);
        assertTrue(observations.bridge("request", new CancellationSignal()).isPresent());
        observations.closeRequest("request");
        assertTrue(first.closed);
    }

    private static final class RecordingCoordinator implements WorldObservationCoordinator {
        private WorldObservationRequest blocks;
        private WorldObservationRequest entities;
        private boolean closed;

        @Override
        public CompletionStage<BlockObservation> inspect(
                WorldObservationRequest request, CancellationSignal cancellation) {
            blocks = request;
            return CompletableFuture.completedFuture(new BlockObservation(
                    request.bounds(),
                    List.of(new WorldBlockSnapshot(
                            "minecraft:oak_log",
                            new WorldPosition(2, 8, 1),
                            new WorldPosition(0, 0, 0),
                            Map.of("axis", "y"),
                            "",
                            false)),
                    new WorldObservationCoverage(
                            request.bounds().volume(),
                            request.bounds().volume(),
                            true,
                            List.of()),
                    evidence()));
        }

        @Override
        public CompletionStage<EntityObservation> entities(
                WorldObservationRequest request, CancellationSignal cancellation) {
            entities = request;
            return CompletableFuture.completedFuture(new EntityObservation(
                    request.bounds(),
                    List.of(new WorldEntitySummary(
                            "entity-1",
                            "minecraft:cow",
                            "Cow",
                            new WorldPosition(1, 2, 3),
                            true)),
                    new WorldObservationCoverage(
                            request.bounds().volume(),
                            request.bounds().volume(),
                            true,
                            List.of()),
                    evidence()));
        }

        @Override
        public CompletionStage<WorldEntitySnapshot> entity(
                String observationId, CancellationSignal cancellation) {
            return CompletableFuture.completedFuture(new WorldEntitySnapshot(
                    observationId,
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "minecraft:cow",
                    "Cow",
                    new WorldPosition(1, 2, 3),
                    Map.of("health", 10.0),
                    evidence()));
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static EvidenceMetadata evidence() {
        return new EvidenceMetadata(
                DataAuthority.DETERMINISTIC_TEST,
                DataCompleteness.COMPLETE,
                Instant.EPOCH,
                "openallay:test_world",
                "openallay:test",
                "26.2",
                "test",
                Map.of());
    }
}
