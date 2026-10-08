package dev.openallay.world;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.platform.PlatformService;
import java.time.Instant;
import java.util.Map;

/** Shared factual source metadata for detached client and server world observations. */
final class WorldObservationEvidence {
    private WorldObservationEvidence() {}

    static EvidenceMetadata client(
            PlatformService platform,
            DataCompleteness completeness,
            Instant capturedAt,
            String source,
            String dimension) {
        return create(platform, DataAuthority.CLIENT_VISIBLE, completeness, capturedAt, source,
                "minecraft:client_world_observation", dimension);
    }

    static EvidenceMetadata server(
            PlatformService platform,
            DataCompleteness completeness,
            Instant capturedAt,
            String source,
            String dimension) {
        return create(platform, DataAuthority.SERVER_AUTHORITATIVE, completeness, capturedAt, source,
                "minecraft:server_world_observation", dimension);
    }

    private static EvidenceMetadata create(
            PlatformService platform,
            DataAuthority authority,
            DataCompleteness completeness,
            Instant capturedAt,
            String source,
            String provenance,
            String dimension) {
        return new EvidenceMetadata(
                authority,
                completeness,
                capturedAt,
                source,
                provenance,
                platform.gameVersion(),
                platform.platformName(),
                dev.openallay.util.Java8Collections.mapOf("minecraft:dimension", dimension));
    }
}
