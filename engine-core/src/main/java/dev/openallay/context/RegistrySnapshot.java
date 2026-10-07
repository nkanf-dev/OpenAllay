package dev.openallay.context;

import java.util.List;

@dev.openallay.value.ValueType(RegistrySnapshot.ValueSchemaProvider.class)
public final class RegistrySnapshot {
    private final EvidenceMetadata evidence;
    private final List<RegistryEntrySnapshot> entries;
    public RegistrySnapshot(EvidenceMetadata evidence, List<RegistryEntrySnapshot> entries) {

        java.util.Objects.requireNonNull(evidence, "evidence");
        entries = dev.openallay.util.Java8Collections.listCopyOf(entries);

        this.evidence = evidence;
        this.entries = entries;
    }
    public EvidenceMetadata evidence() { return evidence; }
    public List<RegistryEntrySnapshot> entries() { return entries; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RegistrySnapshot)) return false;
        RegistrySnapshot that = (RegistrySnapshot) other;
        return java.util.Objects.equals(evidence, that.evidence) && java.util.Objects.equals(entries, that.entries);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        hash = 31 * hash + java.util.Objects.hashCode(entries);
        return hash;
    }
    @Override public String toString() { return "RegistrySnapshot[evidence=" + evidence + ", entries=" + entries + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RegistrySnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(RegistrySnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RegistrySnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(RegistrySnapshot.class, "evidence", RegistrySnapshot::evidence), new dev.openallay.value.ValueSchema.Component<>(RegistrySnapshot.class, "entries", RegistrySnapshot::entries)), arguments -> new RegistrySnapshot((EvidenceMetadata) arguments[0], (List) arguments[1]));
        }
    }
}
