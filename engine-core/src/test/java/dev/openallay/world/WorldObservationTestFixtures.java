package dev.openallay.world;

import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.model.CancellationSignal;
import dev.openallay.platform.PlatformService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Detached world reads using the same evidence factory as both Minecraft coordinators. */
final class WorldObservationTestFixtures {
    static final Instant CAPTURED_AT = Instant.parse("2026-10-01T12:00:00Z");
    static final String DIMENSION = "example:dimensions/test_world";
    static final PlatformService PLATFORM = new PlatformService() {
        @Override public String platformName() { return "fabric"; }
        @Override public String gameVersion() { return "26.2"; }
        @Override public boolean isModLoaded(String modId) { return false; }
        @Override public boolean isDevelopmentEnvironment() { return true; }
    };

    private WorldObservationTestFixtures() {}

    static EvidenceMetadata evidence(
            boolean client, DataCompleteness completeness, String source) {
        return client
                ? WorldObservationEvidence.client(
                        PLATFORM, completeness, CAPTURED_AT, source, DIMENSION)
                : WorldObservationEvidence.server(
                        PLATFORM, completeness, CAPTURED_AT, source, DIMENSION);
    }

    static final class Coordinator implements WorldObservationCoordinator {
        final List<EvidenceMetadata> captured = new ArrayList<>();
        private final boolean client;
        private final boolean complete;
        boolean closed;

        Coordinator(boolean client, boolean complete) {
            this.client = client;
            this.complete = complete;
        }

        @Override
        public CompletionStage<BlockObservation> inspect(
                WorldObservationRequest request, CancellationSignal cancellation) {
            cancellation.throwIfCancelled();
            return CompletableFuture.completedFuture(new BlockObservation(
                    request.bounds(),
                    List.of(new WorldBlockSnapshot(
                            "minecraft:oak_log", request.bounds().from(),
                            new WorldPosition(0, 0, 0), Map.of("axis", "y"), "", false)),
                    coverage(request),
                    metadata(completeness(), "blocks")));
        }

        @Override
        public CompletionStage<EntityObservation> entities(
                WorldObservationRequest request, CancellationSignal cancellation) {
            cancellation.throwIfCancelled();
            return CompletableFuture.completedFuture(new EntityObservation(
                    request.bounds(),
                    List.of(new WorldEntitySummary(
                            "entity-1", "minecraft:cow", "Cow", new WorldPosition(1, 0, 0), true)),
                    coverage(request),
                    metadata(client ? DataCompleteness.PARTIAL : completeness(), "entities")));
        }

        @Override
        public CompletionStage<WorldEntitySnapshot> entity(
                String observationId, CancellationSignal cancellation) {
            cancellation.throwIfCancelled();
            return CompletableFuture.completedFuture(new WorldEntitySnapshot(
                    observationId,
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "minecraft:cow", "Cow", new WorldPosition(1, 0, 0), Map.of("health", 10.0),
                    metadata(client ? DataCompleteness.PARTIAL : DataCompleteness.COMPLETE, "entity")));
        }

        @Override
        public void close() {
            closed = true;
        }

        private WorldObservationCoverage coverage(WorldObservationRequest request) {
            return new WorldObservationCoverage(
                    request.bounds().volume(), complete ? request.bounds().volume() : request.bounds().volume() - 1,
                    complete, complete ? List.of() : List.of("chunk:1,0"));
        }

        private DataCompleteness completeness() {
            return complete ? DataCompleteness.COMPLETE : DataCompleteness.PARTIAL;
        }

        private EvidenceMetadata metadata(DataCompleteness completeness, String operation) {
            EvidenceMetadata result = evidence(
                    client, completeness, "minecraft:" + (client ? "client_" : "server_") + operation);
            captured.add(result);
            return result;
        }
    }
}
