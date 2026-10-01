package dev.openallay.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class WorldObservationEvidenceTest {
    @ParameterizedTest
    @MethodSource("sourcesAndCompleteness")
    void constructsValidImmutableMetadataForEveryProductionSource(
            boolean client, String source, DataCompleteness completeness) {
        EvidenceMetadata evidence = WorldObservationTestFixtures.evidence(client, completeness, source);

        assertEquals(client ? DataAuthority.CLIENT_VISIBLE : DataAuthority.SERVER_AUTHORITATIVE,
                evidence.authority());
        assertEquals(completeness, evidence.completeness());
        assertEquals(WorldObservationTestFixtures.CAPTURED_AT, evidence.capturedAt());
        assertEquals(source, evidence.sourceId());
        assertEquals(client ? "minecraft:client_world_observation" : "minecraft:server_world_observation",
                evidence.provenance());
        assertEquals("26.2", evidence.gameVersion());
        assertEquals("fabric", evidence.loader());
        assertEquals(Map.of("minecraft:dimension", WorldObservationTestFixtures.DIMENSION),
                evidence.details());
        assertThrows(UnsupportedOperationException.class,
                () -> evidence.details().put("minecraft:dimension", "minecraft:the_nether"));
    }

    @Test
    void keepsEvidenceValidationStrictRatherThanAcceptingTheFormerInvalidKey() {
        assertThrows(IllegalArgumentException.class, () -> new EvidenceMetadata(
                DataAuthority.CLIENT_VISIBLE, DataCompleteness.COMPLETE,
                WorldObservationTestFixtures.CAPTURED_AT,
                "minecraft:client_blocks", "minecraft:client_world_observation", "26.2", "fabric",
                Map.of("dimension", WorldObservationTestFixtures.DIMENSION)));
    }

    @Test
    void publicWorldRecordsStillRequireEvidence() {
        WorldBounds bounds = new WorldBounds(new WorldPosition(0, 0, 0), new WorldPosition(0, 0, 0));
        WorldObservationCoverage coverage = new WorldObservationCoverage(1, 1, true, List.of());

        assertThrows(NullPointerException.class,
                () -> new BlockObservation(bounds, List.of(), coverage, null));
        assertThrows(NullPointerException.class,
                () -> new EntityObservation(bounds, List.of(), coverage, null));
        assertThrows(NullPointerException.class, () -> new WorldEntitySnapshot(
                "entity-1", UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "minecraft:cow", "Cow", bounds.from(), Map.of(), null));
    }

    private static Stream<Arguments> sourcesAndCompleteness() {
        List<Arguments> cases = new ArrayList<>();
        for (boolean client : List.of(true, false)) {
            for (String operation : List.of("blocks", "entities", "entity")) {
                for (DataCompleteness completeness : List.of(
                        DataCompleteness.COMPLETE, DataCompleteness.PARTIAL)) {
                    cases.add(Arguments.of(
                            client, "minecraft:" + (client ? "client_" : "server_") + operation,
                            completeness));
                }
            }
        }
        return cases.stream();
    }
}
