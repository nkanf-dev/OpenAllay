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
        return dev.openallay.util.Java8Collections.toList(grouped.entrySet().stream()
                .map(entry -> new Group(entry.getKey(), entry.getValue())));
    }
@dev.openallay.value.ValueType(Group.ValueSchemaProvider.class)
public static final class Group {
    private final Identity identity;
    private final List<GuideSource> records;
    private final Instant firstCapturedAt;
    private final Instant lastCapturedAt;
    public Group(Identity identity, List<GuideSource> records, Instant firstCapturedAt, Instant lastCapturedAt) {

            Objects.requireNonNull(identity, "identity");
            records = dev.openallay.util.Java8Collections.listCopyOf(records);
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
            scope.keySet().removeIf(key -> {
boolean $oaSwitch1_exit_result;
$oaSwitch1_exit: {
switch ((key)) {
case "minecraft:position":
case "minecraft:x":
case "minecraft:y":
case "minecraft:z":
case "openallay_builder:position":
case "openallay_builder:x":
case "openallay_builder:y":
case "openallay_builder:z":
case "openallay_builder:count":
{
$oaSwitch1_exit_result = true; break $oaSwitch1_exit;
}
default:
{
$oaSwitch1_exit_result = false; break $oaSwitch1_exit;
}
}
}
return $oaSwitch1_exit_result;
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
        java.lang.String $oaSwitch3_exit_result;
$oaSwitch3_exit: {
switch ((evidence.sourceId())) {
case "minecraft:client_player":
case "minecraft:server_player":
case "minecraft:player_ui":
case "openallay:inventory":
{
$oaSwitch3_exit_result = "player"; break $oaSwitch3_exit;
}
case "minecraft:client_registry":
case "minecraft:registry":
{
$oaSwitch3_exit_result = "registry"; break $oaSwitch3_exit;
}
case "minecraft:recipe_manager":
case "openallay:recipe_catalog":
{
$oaSwitch3_exit_result = "recipes"; break $oaSwitch3_exit;
}
case "minecraft:client_recipe_book":
{
$oaSwitch3_exit_result = "recipe_book"; break $oaSwitch3_exit;
}
case "viewer:jei":
{
$oaSwitch3_exit_result = "jei"; break $oaSwitch3_exit;
}
case "viewer:rei":
{
$oaSwitch3_exit_result = "rei"; break $oaSwitch3_exit;
}
case "patchouli:resources":
{
$oaSwitch3_exit_result = "resources"; break $oaSwitch3_exit;
}
case "openallay_builder:write-readback":
case "openallay_builder:undo-readback":
case "openallay_builder:read":
case "openallay_builder:read-region":
case "openallay_builder:connection-repair":
case "openallay_builder:scan-ground":
{
$oaSwitch3_exit_result = "builder"; break $oaSwitch3_exit;
}
case "minecraft:client_blocks":
case "minecraft:server_blocks":
case "minecraft:client_entities":
case "minecraft:server_entities":
case "minecraft:client_entity":
case "minecraft:server_entity":
{
$oaSwitch3_exit_result = "world"; break $oaSwitch3_exit;
}
default:
{
$oaSwitch3_exit_result = evidence.sourceId().startsWith("patchouli:") ? "guide"
                    : evidence.sourceId().startsWith("minecraft:world") ? "world"
                    : "unknown"; break $oaSwitch3_exit;
}
}
}
String sourceName = $oaSwitch3_exit_result;
        {
final java.lang.String $oaSwitch2_exit_result_prior1 = "screen.openallay.evidence.source." + sourceName;
final java.lang.String $oaSwitch2_exit_result_prior0 = "screen.openallay.evidence.authority.";
java.lang.String $oaSwitch2_exit_result;
$oaSwitch2_exit: {
switch ((evidence.authority())) {
case CLIENT_VISIBLE:
{
$oaSwitch2_exit_result = "client_visible"; break $oaSwitch2_exit;
}
case SERVER_AUTHORITATIVE:
{
$oaSwitch2_exit_result = "server_authoritative"; break $oaSwitch2_exit;
}
case RESOURCE_ASSET:
{
$oaSwitch2_exit_result = "resource_asset"; break $oaSwitch2_exit;
}
case INTEGRATION_API:
{
$oaSwitch2_exit_result = "integration_api"; break $oaSwitch2_exit;
}
case DETERMINISTIC_TEST:
{
$oaSwitch2_exit_result = "test"; break $oaSwitch2_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
{
final java.lang.String $oaSwitch0_exit_result_prior1 = $oaSwitch2_exit_result_prior1;
final java.lang.String $oaSwitch0_exit_result_prior2 = $oaSwitch2_exit_result_prior0 + $oaSwitch2_exit_result;
final java.lang.String $oaSwitch0_exit_result_prior0 = "screen.openallay.detail.tool.coverage.";
java.lang.String $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((evidence.completeness())) {
case COMPLETE:
{
$oaSwitch0_exit_result = "complete"; break $oaSwitch0_exit;
}
case PARTIAL:
{
$oaSwitch0_exit_result = "partial"; break $oaSwitch0_exit;
}
case UNKNOWN:
{
$oaSwitch0_exit_result = "unknown"; break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return new GuideEvidencePresentation(
                $oaSwitch0_exit_result_prior1,
                $oaSwitch0_exit_result_prior2,
                $oaSwitch0_exit_result_prior0 + $oaSwitch0_exit_result,
                evidence.capturedAt());
}
}
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
