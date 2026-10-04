package dev.openallay.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.JavascriptExecution;
import dev.openallay.script.RhinoJavascriptRuntime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void publishesStrictPublicWorldRecordsWithTheProductionEvidenceFactory(boolean client) {
        WorldObservationTestFixtures.Coordinator coordinator =
                new WorldObservationTestFixtures.Coordinator(client, false);
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.capture("factory-request", coordinator);
        List<EvidenceMetadata> captured = new java.util.ArrayList<>();
        var bridge = observations.bridge("factory-request", new CancellationSignal(), captured::add)
                .orElseThrow();

        JavascriptExecution result = new RhinoJavascriptRuntime().execute(
                """
                const bounds = {from: {x: 0, y: 0, z: 0}, to: {x: 16, y: 0, z: 0}};
                const blocks = world.inspect(bounds);
                const entities = world.entities(bounds);
                const entity = world.entity(entities.entities[0].observationId);
                return {blocks, entities, entity};
                """,
                Map.of(), Map.of(), Map.of(), new CancellationSignal(), null, bridge);

        var output = result.value().getAsJsonObject();
        var blocks = output.getAsJsonObject("blocks");
        var entities = output.getAsJsonObject("entities");
        var entity = output.getAsJsonObject("entity");
        assertEquals(Set.of("bounds", "blocks", "coverage", "evidence"), blocks.keySet());
        assertEquals(Set.of("bounds", "entities", "coverage", "evidence"), entities.keySet());
        assertEquals(Set.of("observationId", "uuid", "type", "name", "position", "data", "evidence"),
                entity.keySet());
        assertEquals("minecraft:oak_log", blocks.getAsJsonArray("blocks")
                .get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(1, blocks.getAsJsonArray("blocks").size());
        assertFalse(blocks.getAsJsonObject("coverage").get("complete").getAsBoolean());
        assertEquals(17, blocks.getAsJsonObject("coverage").get("requestedPositions").getAsInt());
        assertEquals(16, blocks.getAsJsonObject("coverage").get("loadedPositions").getAsInt());
        assertEquals("chunk:1,0", blocks.getAsJsonObject("coverage")
                .getAsJsonArray("unavailableSections").get(0).getAsString());
        assertEquals("minecraft:cow", entity.get("type").getAsString());
        assertEquals(coordinator.captured, captured);
        assertEquals(List.of(
                        "minecraft:" + (client ? "client_" : "server_") + "blocks",
                        "minecraft:" + (client ? "client_" : "server_") + "entities",
                        "minecraft:" + (client ? "client_" : "server_") + "entity"),
                captured.stream().map(EvidenceMetadata::sourceId).toList());
        for (String record : List.of("blocks", "entities", "entity")) {
            var metadata = output.getAsJsonObject(record).getAsJsonObject("evidence");
            assertEquals(Set.of("authority", "completeness", "capturedAt", "sourceId", "provenance",
                            "gameVersion", "loader", "details"), metadata.keySet());
            assertEquals(WorldObservationTestFixtures.DIMENSION,
                    metadata.getAsJsonObject("details").get("minecraft:dimension").getAsString());
            assertEquals(WorldObservationTestFixtures.CAPTURED_AT.toString(),
                    metadata.get("capturedAt").getAsString());
        }
        observations.closeRequest("factory-request");
        assertTrue(coordinator.closed);
        assertTrue(observations.bridge("factory-request", new CancellationSignal()).isEmpty());
    }

    @Test
    void cancellationDuringAWorldReadDoesNotPublishEvidence() {
        CancellationSignal cancellation = new CancellationSignal();
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.capture("cancelled-request", new RecordingCoordinator() {
            @Override
            public CompletionStage<BlockObservation> inspect(
                    WorldObservationRequest request, CancellationSignal signal) {
                signal.cancel();
                return new CompletableFuture<>();
            }
        });
        List<EvidenceMetadata> captured = new java.util.ArrayList<>();
        var bridge = observations.bridge("cancelled-request", cancellation, captured::add).orElseThrow();

        ModelClientException failure = assertThrows(ModelClientException.class,
                () -> new RhinoJavascriptRuntime().execute(
                        "return world.inspect({from:{x:0,y:0,z:0},to:{x:0,y:0,z:0}});",
                        Map.of(), Map.of(), Map.of(), cancellation, null, bridge));

        assertEquals("agent_cancelled", failure.failure().code());
        assertTrue(captured.isEmpty());
        observations.closeRequest("cancelled-request");
    }

    @Test
    void invalidMetadataAfterAReadIsNotSilentlyPublishedAsAnEmptyObservation() {
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.capture("invalid-metadata-request", new RecordingCoordinator() {
            @Override
            public CompletionStage<BlockObservation> inspect(
                    WorldObservationRequest request, CancellationSignal cancellation) {
                try {
                    new BlockObservation(request.bounds(), List.of(),
                            new WorldObservationCoverage(request.bounds().volume(), 0, false, List.of()),
                            null);
                    throw new AssertionError("missing evidence was accepted");
                } catch (NullPointerException missingEvidence) {
                    return CompletableFuture.failedFuture(missingEvidence);
                }
            }
        });
        List<EvidenceMetadata> captured = new java.util.ArrayList<>();
        var bridge = observations.bridge("invalid-metadata-request", new CancellationSignal(), captured::add)
                .orElseThrow();

        JavascriptExecutionException failure = assertThrows(JavascriptExecutionException.class,
                () -> new RhinoJavascriptRuntime().execute(
                        "return world.inspect({from:{x:0,y:0,z:0},to:{x:0,y:0,z:0}});",
                        Map.of(), Map.of(), Map.of(), new CancellationSignal(), null, bridge));

        assertEquals("world_observation_failed", failure.code());
        assertTrue(captured.isEmpty());
        observations.closeRequest("invalid-metadata-request");
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

    private static class RecordingCoordinator implements WorldObservationCoordinator {
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
