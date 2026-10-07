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
@dev.openallay.value.ValueType(GuideEvidencePresentation.ValueSchemaProvider.class)
public final class GuideEvidencePresentation {
    private final String sourceKey;
    private final String authorityKey;
    private final String coverageKey;
    private final Instant capturedAt;
    public GuideEvidencePresentation(String sourceKey, String authorityKey, String coverageKey, Instant capturedAt) {

        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(authorityKey, "authorityKey");
        Objects.requireNonNull(coverageKey, "coverageKey");
        Objects.requireNonNull(capturedAt, "capturedAt");

        this.sourceKey = sourceKey;
        this.authorityKey = authorityKey;
        this.coverageKey = coverageKey;
        this.capturedAt = capturedAt;
    }
    public String sourceKey() { return sourceKey; }
    public String authorityKey() { return authorityKey; }
    public String coverageKey() { return coverageKey; }
    public Instant capturedAt() { return capturedAt; }
public static List<Group> groups(List<GuideSource> sources) {
        Map<Identity, List<GuideSource>> grouped = new LinkedHashMap<>();
        for (GuideSource source : sources) {
            grouped.computeIfAbsent(Identity.from(source), ignored -> new ArrayList<>()).add(source);
        }
        return grouped.entrySet().stream()
                .map(entry -> new Group(entry.getKey(), entry.getValue()))
                .toList();
    }
@dev.openallay.value.ValueType(Group.ValueSchemaProvider.class)
public static final class Group {
    private final Identity identity;
    private final List<GuideSource> records;
    private final Instant firstCapturedAt;
    private final Instant lastCapturedAt;
    public Group(Identity identity, List<GuideSource> records, Instant firstCapturedAt, Instant lastCapturedAt) {

            Objects.requireNonNull(identity, "identity");
            records = List.copyOf(records);
            if (records.isEmpty()) throw new IllegalArgumentException("source group must not be empty");
            Objects.requireNonNull(firstCapturedAt, "firstCapturedAt");
            Objects.requireNonNull(lastCapturedAt, "lastCapturedAt");
            if (lastCapturedAt.isBefore(firstCapturedAt)) {
                throw new IllegalArgumentException("source observation range is invalid");
            }

        this.identity = identity;
        this.records = records;
        this.firstCapturedAt = firstCapturedAt;
        this.lastCapturedAt = lastCapturedAt;
    }
    public Identity identity() { return identity; }
    public List<GuideSource> records() { return records; }
    public Instant firstCapturedAt() { return firstCapturedAt; }
    public Instant lastCapturedAt() { return lastCapturedAt; }
private Group(Identity identity, List<GuideSource> records) {
            this(identity, records,
                    records.stream().map(source -> new SourceObservation(
                                    source.evidence(), source.lastCapturedAt()).firstCapturedAt())
                            .min(Instant::compareTo).orElseThrow(),
                    records.stream().map(GuideSource::lastCapturedAt)
                            .max(Instant::compareTo).orElseThrow());
        }
public GuideEvidencePresentation presentation() { return from(records.get(0)); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Group)) return false;
        Group that = (Group) other;
        return java.util.Objects.equals(identity, that.identity) && java.util.Objects.equals(records, that.records) && java.util.Objects.equals(firstCapturedAt, that.firstCapturedAt) && java.util.Objects.equals(lastCapturedAt, that.lastCapturedAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(identity);
        hash = 31 * hash + java.util.Objects.hashCode(records);
        hash = 31 * hash + java.util.Objects.hashCode(firstCapturedAt);
        hash = 31 * hash + java.util.Objects.hashCode(lastCapturedAt);
        return hash;
    }
    @Override public String toString() { return "Group[identity=" + identity + ", records=" + records + ", firstCapturedAt=" + firstCapturedAt + ", lastCapturedAt=" + lastCapturedAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Group> schema() {
            return new dev.openallay.value.ValueSchema<>(Group.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Group>>asList(new dev.openallay.value.ValueSchema.Component<>(Group.class, "identity", Group::identity), new dev.openallay.value.ValueSchema.Component<>(Group.class, "records", Group::records), new dev.openallay.value.ValueSchema.Component<>(Group.class, "firstCapturedAt", Group::firstCapturedAt), new dev.openallay.value.ValueSchema.Component<>(Group.class, "lastCapturedAt", Group::lastCapturedAt)), arguments -> new Group((Identity) arguments[0], (List) arguments[1], (Instant) arguments[2], (Instant) arguments[3]));
        }
    }
}
@dev.openallay.value.ValueType(Identity.ValueSchemaProvider.class)
public static final class Identity {
    private final String toolId;
    private final DataAuthority authority;
    private final DataCompleteness completeness;
    private final String sourceId;
    private final String provenance;
    private final String gameVersion;
    private final String loader;
    private final Map<String, String> scope;
    public Identity(String toolId, DataAuthority authority, DataCompleteness completeness, String sourceId, String provenance, String gameVersion, String loader, Map<String, String> scope) {

            scope = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(scope));

        this.toolId = toolId;
        this.authority = authority;
        this.completeness = completeness;
        this.sourceId = sourceId;
        this.provenance = provenance;
        this.gameVersion = gameVersion;
        this.loader = loader;
        this.scope = scope;
    }
    public String toolId() { return toolId; }
    public DataAuthority authority() { return authority; }
    public DataCompleteness completeness() { return completeness; }
    public String sourceId() { return sourceId; }
    public String provenance() { return provenance; }
    public String gameVersion() { return gameVersion; }
    public String loader() { return loader; }
    public Map<String, String> scope() { return scope; }
public static Identity from(GuideSource source) {
            dev.openallay.context.EvidenceMetadata evidence = source.evidence();
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Identity)) return false;
        Identity that = (Identity) other;
        return java.util.Objects.equals(toolId, that.toolId) && java.util.Objects.equals(authority, that.authority) && java.util.Objects.equals(completeness, that.completeness) && java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(provenance, that.provenance) && java.util.Objects.equals(gameVersion, that.gameVersion) && java.util.Objects.equals(loader, that.loader) && java.util.Objects.equals(scope, that.scope);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(toolId);
        hash = 31 * hash + java.util.Objects.hashCode(authority);
        hash = 31 * hash + java.util.Objects.hashCode(completeness);
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(provenance);
        hash = 31 * hash + java.util.Objects.hashCode(gameVersion);
        hash = 31 * hash + java.util.Objects.hashCode(loader);
        hash = 31 * hash + java.util.Objects.hashCode(scope);
        return hash;
    }
    @Override public String toString() { return "Identity[toolId=" + toolId + ", authority=" + authority + ", completeness=" + completeness + ", sourceId=" + sourceId + ", provenance=" + provenance + ", gameVersion=" + gameVersion + ", loader=" + loader + ", scope=" + scope + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Identity> schema() {
            return new dev.openallay.value.ValueSchema<>(Identity.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Identity>>asList(new dev.openallay.value.ValueSchema.Component<>(Identity.class, "toolId", Identity::toolId), new dev.openallay.value.ValueSchema.Component<>(Identity.class, "authority", Identity::authority), new dev.openallay.value.ValueSchema.Component<>(Identity.class, "completeness", Identity::completeness), new dev.openallay.value.ValueSchema.Component<>(Identity.class, "sourceId", Identity::sourceId), new dev.openallay.value.ValueSchema.Component<>(Identity.class, "provenance", Identity::provenance), new dev.openallay.value.ValueSchema.Component<>(Identity.class, "gameVersion", Identity::gameVersion), new dev.openallay.value.ValueSchema.Component<>(Identity.class, "loader", Identity::loader), new dev.openallay.value.ValueSchema.Component<>(Identity.class, "scope", Identity::scope)), arguments -> new Identity((String) arguments[0], (DataAuthority) arguments[1], (DataCompleteness) arguments[2], (String) arguments[3], (String) arguments[4], (String) arguments[5], (String) arguments[6], (Map) arguments[7]));
        }
    }
}
public static GuideEvidencePresentation from(GuideSource source) {
        dev.openallay.context.EvidenceMetadata evidence = Objects.requireNonNull(source, "source").evidence();
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideEvidencePresentation)) return false;
        GuideEvidencePresentation that = (GuideEvidencePresentation) other;
        return java.util.Objects.equals(sourceKey, that.sourceKey) && java.util.Objects.equals(authorityKey, that.authorityKey) && java.util.Objects.equals(coverageKey, that.coverageKey) && java.util.Objects.equals(capturedAt, that.capturedAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceKey);
        hash = 31 * hash + java.util.Objects.hashCode(authorityKey);
        hash = 31 * hash + java.util.Objects.hashCode(coverageKey);
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        return hash;
    }
    @Override public String toString() { return "GuideEvidencePresentation[sourceKey=" + sourceKey + ", authorityKey=" + authorityKey + ", coverageKey=" + coverageKey + ", capturedAt=" + capturedAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideEvidencePresentation> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideEvidencePresentation.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideEvidencePresentation>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideEvidencePresentation.class, "sourceKey", GuideEvidencePresentation::sourceKey), new dev.openallay.value.ValueSchema.Component<>(GuideEvidencePresentation.class, "authorityKey", GuideEvidencePresentation::authorityKey), new dev.openallay.value.ValueSchema.Component<>(GuideEvidencePresentation.class, "coverageKey", GuideEvidencePresentation::coverageKey), new dev.openallay.value.ValueSchema.Component<>(GuideEvidencePresentation.class, "capturedAt", GuideEvidencePresentation::capturedAt)), arguments -> new GuideEvidencePresentation((String) arguments[0], (String) arguments[1], (String) arguments[2], (Instant) arguments[3]));
        }
    }
}
