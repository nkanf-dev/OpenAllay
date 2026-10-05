package dev.openallay.guide.ui;

import dev.openallay.guide.GuideSource;
import dev.openallay.context.SourceObservation;
import java.time.Instant;
import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;

/** Friendly source labels and lossless, optional source-detail groups. */
public record GuideEvidencePresentation(
        String sourceKey, String authorityKey, String coverageKey, Instant capturedAt) {
    public GuideEvidencePresentation {
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(authorityKey, "authorityKey");
        Objects.requireNonNull(coverageKey, "coverageKey");
        Objects.requireNonNull(capturedAt, "capturedAt");
    }

    /** One UI disclosure per source identity. All recorded observations remain inspectable. */
    public static List<Group> groups(List<GuideSource> sources) {
        Map<Identity, List<GuideSource>> grouped = new LinkedHashMap<>();
        for (GuideSource source : sources) {
            grouped.computeIfAbsent(Identity.from(source), ignored -> new ArrayList<>()).add(source);
        }
        return grouped.entrySet().stream()
                .map(entry -> new Group(entry.getKey(), entry.getValue()))
                .toList();
    }

    public record Group(
            Identity identity, List<GuideSource> records,
            Instant firstCapturedAt, Instant lastCapturedAt) {
        public Group {
            Objects.requireNonNull(identity, "identity");
            records = List.copyOf(records);
            if (records.isEmpty()) throw new IllegalArgumentException("source group must not be empty");
            Objects.requireNonNull(firstCapturedAt, "firstCapturedAt");
            Objects.requireNonNull(lastCapturedAt, "lastCapturedAt");
            if (lastCapturedAt.isBefore(firstCapturedAt)) {
                throw new IllegalArgumentException("source observation range is invalid");
            }
        }

        private Group(Identity identity, List<GuideSource> records) {
            this(identity, records,
                    records.stream().map(source -> new SourceObservation(
                                    source.evidence(), source.lastCapturedAt()).firstCapturedAt())
                            .min(Instant::compareTo).orElseThrow(),
                    records.stream().map(GuideSource::lastCapturedAt)
                            .max(Instant::compareTo).orElseThrow());
        }

        public GuideEvidencePresentation presentation() { return from(records.get(0)); }
    }

    public record Identity(
            String toolId, DataAuthority authority, DataCompleteness completeness,
            String sourceId, String provenance, String gameVersion, String loader,
            Map<String, String> scope) {
        public Identity {
            scope = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(scope));
        }

        public static Identity from(GuideSource source) {
            var evidence = source.evidence();
            Map<String, String> scope = new java.util.TreeMap<>(new SourceObservation(
                    evidence, source.lastCapturedAt()).identityDetails());
            // Position and operation size are retained in the individual UI records below.
            // Capture ranges use the same validated extent rule as core source aggregation.
            // Unknown detail keys remain in identity, so unrelated scopes cannot silently merge.
            scope.keySet().removeIf(key -> switch (key) {
                case "minecraft:position", "minecraft:x", "minecraft:y", "minecraft:z",
                        "openallay_builder:position", "openallay_builder:x", "openallay_builder:y",
                        "openallay_builder:z", "openallay_builder:count" -> true;
                default -> false;
            });
            return new Identity(source.toolId(), evidence.authority(), evidence.completeness(),
                    evidence.sourceId(), evidence.provenance(), evidence.gameVersion(),
                    evidence.loader(), scope);
        }
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
                    "openallay_builder:read", "openallay_builder:read-region",
                    "openallay_builder:connection-repair", "openallay_builder:scan-ground" -> "builder";
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
