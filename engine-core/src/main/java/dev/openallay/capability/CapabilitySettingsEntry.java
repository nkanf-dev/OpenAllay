package dev.openallay.capability;

/** Immutable catalog card state safe for later player-facing projection. */
@dev.openallay.value.ValueType(CapabilitySettingsEntry.ValueSchemaProvider.class)
public final class CapabilitySettingsEntry {
    private final String ownerId;
    private final String id;
    private final CapabilityKind kind;
    private final String titleKey;
    private final String descriptionKey;
    private final CapabilityChildPage childPage;
    private final boolean available;
    private final boolean enabled;
    public CapabilitySettingsEntry(String ownerId, String id, CapabilityKind kind, String titleKey, String descriptionKey, CapabilityChildPage childPage, boolean available, boolean enabled) {

        ownerId = CapabilitySettingsDescriptor.requireIdentity(ownerId, "owner id");
        CapabilitySettingsDescriptor descriptor = new CapabilitySettingsDescriptor(
                id, kind, titleKey, descriptionKey, childPage);
        id = descriptor.id();
        kind = descriptor.kind();
        titleKey = descriptor.titleKey();
        descriptionKey = descriptor.descriptionKey();

        this.ownerId = ownerId;
        this.id = id;
        this.kind = kind;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
        this.childPage = childPage;
        this.available = available;
        this.enabled = enabled;
    }
    public String ownerId() { return ownerId; }
    public String id() { return id; }
    public CapabilityKind kind() { return kind; }
    public String titleKey() { return titleKey; }
    public String descriptionKey() { return descriptionKey; }
    public CapabilityChildPage childPage() { return childPage; }
    public boolean available() { return available; }
    public boolean enabled() { return enabled; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CapabilitySettingsEntry)) return false;
        CapabilitySettingsEntry that = (CapabilitySettingsEntry) other;
        return java.util.Objects.equals(ownerId, that.ownerId) && java.util.Objects.equals(id, that.id) && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(descriptionKey, that.descriptionKey) && java.util.Objects.equals(childPage, that.childPage) && available == that.available && enabled == that.enabled;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(ownerId);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(descriptionKey);
        hash = 31 * hash + java.util.Objects.hashCode(childPage);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + Boolean.hashCode(enabled);
        return hash;
    }
    @Override public String toString() { return "CapabilitySettingsEntry[ownerId=" + ownerId + ", id=" + id + ", kind=" + kind + ", titleKey=" + titleKey + ", descriptionKey=" + descriptionKey + ", childPage=" + childPage + ", available=" + available + ", enabled=" + enabled + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CapabilitySettingsEntry> schema() {
            return new dev.openallay.value.ValueSchema<>(CapabilitySettingsEntry.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CapabilitySettingsEntry>>asList(new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsEntry.class, "ownerId", CapabilitySettingsEntry::ownerId), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsEntry.class, "id", CapabilitySettingsEntry::id), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsEntry.class, "kind", CapabilitySettingsEntry::kind), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsEntry.class, "titleKey", CapabilitySettingsEntry::titleKey), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsEntry.class, "descriptionKey", CapabilitySettingsEntry::descriptionKey), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsEntry.class, "childPage", CapabilitySettingsEntry::childPage), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsEntry.class, "available", CapabilitySettingsEntry::available), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsEntry.class, "enabled", CapabilitySettingsEntry::enabled)), arguments -> new CapabilitySettingsEntry((String) arguments[0], (String) arguments[1], (CapabilityKind) arguments[2], (String) arguments[3], (String) arguments[4], (CapabilityChildPage) arguments[5], (Boolean) arguments[6], (Boolean) arguments[7]));
        }
    }
}
