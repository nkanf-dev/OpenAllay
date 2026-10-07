package dev.openallay.capability;

import java.util.List;

@dev.openallay.value.ValueType(CapabilityCatalogSnapshot.ValueSchemaProvider.class)
public final class CapabilityCatalogSnapshot {
    private final List<CapabilitySettingsEntry> entries;
    public CapabilityCatalogSnapshot(List<CapabilitySettingsEntry> entries) {

        entries = List.copyOf(entries);

        this.entries = entries;
    }
    public List<CapabilitySettingsEntry> entries() { return entries; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CapabilityCatalogSnapshot)) return false;
        CapabilityCatalogSnapshot that = (CapabilityCatalogSnapshot) other;
        return java.util.Objects.equals(entries, that.entries);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(entries);
        return hash;
    }
    @Override public String toString() { return "CapabilityCatalogSnapshot[entries=" + entries + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CapabilityCatalogSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(CapabilityCatalogSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CapabilityCatalogSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(CapabilityCatalogSnapshot.class, "entries", CapabilityCatalogSnapshot::entries)), arguments -> new CapabilityCatalogSnapshot((List) arguments[0]));
        }
    }
}
