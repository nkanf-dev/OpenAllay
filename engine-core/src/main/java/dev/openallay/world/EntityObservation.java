package dev.openallay.world;

import dev.openallay.context.EvidenceMetadata;
import java.util.List;
import java.util.Objects;

@dev.openallay.value.ValueType(EntityObservation.ValueSchemaProvider.class)
public final class EntityObservation {
    private final WorldBounds bounds;
    private final List<WorldEntitySummary> entities;
    private final WorldObservationCoverage coverage;
    private final EvidenceMetadata evidence;
    public EntityObservation(WorldBounds bounds, List<WorldEntitySummary> entities, WorldObservationCoverage coverage, EvidenceMetadata evidence) {

        Objects.requireNonNull(bounds, "bounds");
        entities = List.copyOf(entities);
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(evidence, "evidence");

        this.bounds = bounds;
        this.entities = entities;
        this.coverage = coverage;
        this.evidence = evidence;
    }
    public WorldBounds bounds() { return bounds; }
    public List<WorldEntitySummary> entities() { return entities; }
    public WorldObservationCoverage coverage() { return coverage; }
    public EvidenceMetadata evidence() { return evidence; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EntityObservation)) return false;
        EntityObservation that = (EntityObservation) other;
        return java.util.Objects.equals(bounds, that.bounds) && java.util.Objects.equals(entities, that.entities) && java.util.Objects.equals(coverage, that.coverage) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(bounds);
        hash = 31 * hash + java.util.Objects.hashCode(entities);
        hash = 31 * hash + java.util.Objects.hashCode(coverage);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "EntityObservation[bounds=" + bounds + ", entities=" + entities + ", coverage=" + coverage + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<EntityObservation> schema() {
            return new dev.openallay.value.ValueSchema<>(EntityObservation.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<EntityObservation>>asList(new dev.openallay.value.ValueSchema.Component<>(EntityObservation.class, "bounds", EntityObservation::bounds), new dev.openallay.value.ValueSchema.Component<>(EntityObservation.class, "entities", EntityObservation::entities), new dev.openallay.value.ValueSchema.Component<>(EntityObservation.class, "coverage", EntityObservation::coverage), new dev.openallay.value.ValueSchema.Component<>(EntityObservation.class, "evidence", EntityObservation::evidence)), arguments -> new EntityObservation((WorldBounds) arguments[0], (List) arguments[1], (WorldObservationCoverage) arguments[2], (EvidenceMetadata) arguments[3]));
        }
    }
}
