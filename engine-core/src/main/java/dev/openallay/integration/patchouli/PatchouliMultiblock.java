package dev.openallay.integration.patchouli;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@dev.openallay.value.ValueType(PatchouliMultiblock.ValueSchemaProvider.class)
public final class PatchouliMultiblock {
    private final String id;
    private final List<Block> blocks;
    private final String provenance;
    private final EvidenceMetadata evidence;
    public PatchouliMultiblock(String id, List<Block> blocks, String provenance, EvidenceMetadata evidence) {

        blocks = List.copyOf(blocks);
        java.util.Objects.requireNonNull(evidence, "evidence");

        this.id = id;
        this.blocks = blocks;
        this.provenance = provenance;
        this.evidence = evidence;
    }
    public String id() { return id; }
    public List<Block> blocks() { return blocks; }
    public String provenance() { return provenance; }
    public EvidenceMetadata evidence() { return evidence; }
public PatchouliMultiblock(String id, List<Block> blocks, String provenance) {
        this(
                id,
                blocks,
                provenance,
                new EvidenceMetadata(
                        DataAuthority.DETERMINISTIC_TEST,
                        DataCompleteness.COMPLETE,
                        Instant.EPOCH,
                        "openallay:test_fixture",
                        "openallay:patchouli_fixture",
                        "test",
                        "common-test",
                        Map.of("openallay:fixture_provenance", provenance)));
    }
@dev.openallay.value.ValueType(Block.ValueSchemaProvider.class)
public static final class Block {
    private final int x;
    private final int y;
    private final int z;
    private final String state;
    public Block(int x, int y, int z, String state) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.state = state;
    }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public String state() { return state; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Block)) return false;
        Block that = (Block) other;
        return x == that.x && y == that.y && z == that.z && java.util.Objects.equals(state, that.state);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(x);
        hash = 31 * hash + Integer.hashCode(y);
        hash = 31 * hash + Integer.hashCode(z);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        return hash;
    }
    @Override public String toString() { return "Block[x=" + x + ", y=" + y + ", z=" + z + ", state=" + state + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Block> schema() {
            return new dev.openallay.value.ValueSchema<>(Block.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Block>>asList(new dev.openallay.value.ValueSchema.Component<>(Block.class, "x", Block::x), new dev.openallay.value.ValueSchema.Component<>(Block.class, "y", Block::y), new dev.openallay.value.ValueSchema.Component<>(Block.class, "z", Block::z), new dev.openallay.value.ValueSchema.Component<>(Block.class, "state", Block::state)), arguments -> new Block((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (String) arguments[3]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PatchouliMultiblock)) return false;
        PatchouliMultiblock that = (PatchouliMultiblock) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(blocks, that.blocks) && java.util.Objects.equals(provenance, that.provenance) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(blocks);
        hash = 31 * hash + java.util.Objects.hashCode(provenance);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "PatchouliMultiblock[id=" + id + ", blocks=" + blocks + ", provenance=" + provenance + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PatchouliMultiblock> schema() {
            return new dev.openallay.value.ValueSchema<>(PatchouliMultiblock.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PatchouliMultiblock>>asList(new dev.openallay.value.ValueSchema.Component<>(PatchouliMultiblock.class, "id", PatchouliMultiblock::id), new dev.openallay.value.ValueSchema.Component<>(PatchouliMultiblock.class, "blocks", PatchouliMultiblock::blocks), new dev.openallay.value.ValueSchema.Component<>(PatchouliMultiblock.class, "provenance", PatchouliMultiblock::provenance), new dev.openallay.value.ValueSchema.Component<>(PatchouliMultiblock.class, "evidence", PatchouliMultiblock::evidence)), arguments -> new PatchouliMultiblock((String) arguments[0], (List) arguments[1], (String) arguments[2], (EvidenceMetadata) arguments[3]));
        }
    }
}
