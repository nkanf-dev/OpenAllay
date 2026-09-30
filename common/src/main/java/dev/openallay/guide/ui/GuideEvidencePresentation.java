package dev.openallay.guide.ui;

import dev.openallay.guide.GuideSource;
import java.time.Instant;
import java.util.Objects;

/** Friendly recorded evidence only. Technical IDs and metadata details are not representable. */
public record GuideEvidencePresentation(
        String sourceKey, String authorityKey, String coverageKey, Instant capturedAt) {
    public GuideEvidencePresentation {
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(authorityKey, "authorityKey");
        Objects.requireNonNull(coverageKey, "coverageKey");
        Objects.requireNonNull(capturedAt, "capturedAt");
    }

    public static GuideEvidencePresentation from(GuideSource source) {
        var evidence = Objects.requireNonNull(source, "source").evidence();
        String sourceName = switch (evidence.sourceId()) {
            case "minecraft:client_player", "minecraft:server_player",
                    "minecraft:player_ui", "openallay:inventory" -> "player";
            case "minecraft:client_registry", "minecraft:registry" -> "registry";
            case "minecraft:recipe_manager", "openallay:recipe_catalog" -> "recipes";
            case "minecraft:client_recipe_book" -> "recipe_book";
            case "viewer:jei" -> "jei";
            case "viewer:rei" -> "rei";
            case "patchouli:resources" -> "resources";
            case "openallay_builder:write-readback", "openallay_builder:undo-readback",
                    "openallay_builder:read-region", "openallay_builder:connection-repair" -> "builder";
            case "minecraft:client_blocks", "minecraft:server_blocks",
                    "minecraft:client_entities", "minecraft:server_entities",
                    "minecraft:client_entity", "minecraft:server_entity" -> "world";
            default -> evidence.sourceId().startsWith("patchouli:") ? "guide"
                    : evidence.sourceId().startsWith("minecraft:world") ? "world"
                    : "unknown";
        };
        return new GuideEvidencePresentation(
                "screen.openallay.evidence.source." + sourceName,
                "screen.openallay.evidence.authority." + switch (evidence.authority()) {
                    case CLIENT_VISIBLE -> "client_visible";
                    case SERVER_AUTHORITATIVE -> "server_authoritative";
                    case RESOURCE_ASSET -> "resource_asset";
                    case INTEGRATION_API -> "integration_api";
                    case DETERMINISTIC_TEST -> "test";
                },
                "screen.openallay.detail.tool.coverage." + switch (evidence.completeness()) {
                    case COMPLETE -> "complete";
                    case PARTIAL -> "partial";
                    case UNKNOWN -> "unknown";
                },
                evidence.capturedAt());
    }
}
