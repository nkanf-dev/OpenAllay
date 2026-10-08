package dev.openallay.world;

import dev.openallay.context.EvidenceMetadata;
import java.util.List;
import java.util.Objects;

@dev.openallay.value.ValueType(BlockObservation.ValueSchemaProvider.class)
public final class BlockObservation {
    private final WorldBounds bounds;
    private final List<WorldBlockSnapshot> blocks;
    private final WorldObservationCoverage coverage;
    private final EvidenceMetadata evidence;
    public BlockObservation(WorldBounds bounds, List<WorldBlockSnapshot> blocks, WorldObservationCoverage coverage, EvidenceMetadata evidence) {

        Objects.requireNonNull(bounds, "bounds");
        blocks = dev.openallay.util.Java8Collections.listCopyOf(blocks);
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(evidence, "evidence");

        this.bounds = bounds;
        this.blocks = blocks;
        this.coverage = coverage;
        this.evidence = evidence;
    }
    public WorldBounds bounds() { return bounds; }
    public List<WorldBlockSnapshot> blocks() { return blocks; }
    public WorldObservationCoverage coverage() { return coverage; }
    public EvidenceMetadata evidence() { return evidence; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BlockObservation)) return false;
        BlockObservation that = (BlockObservation) other;
        return java.util.Objects.equals(bounds, that.bounds) && java.util.Objects.equals(blocks, that.blocks) && java.util.Objects.equals(coverage, that.coverage) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(bounds);
        hash = 31 * hash + java.util.Objects.hashCode(blocks);
        hash = 31 * hash + java.util.Objects.hashCode(coverage);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "BlockObservation[bounds=" + bounds + ", blocks=" + blocks + ", coverage=" + coverage + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<BlockObservation> schema() {
            return new dev.openallay.value.ValueSchema<>(BlockObservation.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<BlockObservation>>asList(new dev.openallay.value.ValueSchema.Component<>(BlockObservation.class, "bounds", BlockObservation::bounds), new dev.openallay.value.ValueSchema.Component<>(BlockObservation.class, "blocks", BlockObservation::blocks), new dev.openallay.value.ValueSchema.Component<>(BlockObservation.class, "coverage", BlockObservation::coverage), new dev.openallay.value.ValueSchema.Component<>(BlockObservation.class, "evidence", BlockObservation::evidence)), arguments -> new BlockObservation((WorldBounds) arguments[0], (List) arguments[1], (WorldObservationCoverage) arguments[2], (EvidenceMetadata) arguments[3]));
        }
    }
}
