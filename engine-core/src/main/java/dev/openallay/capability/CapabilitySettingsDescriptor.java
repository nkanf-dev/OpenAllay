package dev.openallay.capability;

import java.util.Objects;
import java.util.regex.Pattern;

/** Code-owned, presentation-only description of one settings capability card. */
@dev.openallay.value.ValueType(CapabilitySettingsDescriptor.ValueSchemaProvider.class)
public final class CapabilitySettingsDescriptor {
    private final String id;
    private final CapabilityKind kind;
    private final String titleKey;
    private final String descriptionKey;
    private final CapabilityChildPage childPage;
    public CapabilitySettingsDescriptor(String id, CapabilityKind kind, String titleKey, String descriptionKey, CapabilityChildPage childPage) {

        id = requireIdentity(id, "capability id");
        Objects.requireNonNull(kind, "kind");
        titleKey = requireLocalizationKey(titleKey, "titleKey");
        descriptionKey = requireLocalizationKey(descriptionKey, "descriptionKey");

        this.id = id;
        this.kind = kind;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
        this.childPage = childPage;
    }
    public String id() { return id; }
    public CapabilityKind kind() { return kind; }
    public String titleKey() { return titleKey; }
    public String descriptionKey() { return descriptionKey; }
    public CapabilityChildPage childPage() { return childPage; }
private static final Pattern IDENTITY =
            Pattern.compile("[a-z0-9][a-z0-9_.-]*(?::[a-z0-9_][a-z0-9_./-]*)?");
private static final Pattern LOCALIZATION_KEY =
            Pattern.compile("[a-z0-9][a-z0-9_.-]*");
public static String requireIdentity(String value, String name) {
        if (value == null || !IDENTITY.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value);
        }
        return value;
    }
private static String requireLocalizationKey(String value, String name) {
        if (value == null || !LOCALIZATION_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value);
        }
        return value;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CapabilitySettingsDescriptor)) return false;
        CapabilitySettingsDescriptor that = (CapabilitySettingsDescriptor) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(descriptionKey, that.descriptionKey) && java.util.Objects.equals(childPage, that.childPage);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(descriptionKey);
        hash = 31 * hash + java.util.Objects.hashCode(childPage);
        return hash;
    }
    @Override public String toString() { return "CapabilitySettingsDescriptor[id=" + id + ", kind=" + kind + ", titleKey=" + titleKey + ", descriptionKey=" + descriptionKey + ", childPage=" + childPage + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CapabilitySettingsDescriptor> schema() {
            return new dev.openallay.value.ValueSchema<>(CapabilitySettingsDescriptor.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CapabilitySettingsDescriptor>>asList(new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsDescriptor.class, "id", CapabilitySettingsDescriptor::id), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsDescriptor.class, "kind", CapabilitySettingsDescriptor::kind), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsDescriptor.class, "titleKey", CapabilitySettingsDescriptor::titleKey), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsDescriptor.class, "descriptionKey", CapabilitySettingsDescriptor::descriptionKey), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsDescriptor.class, "childPage", CapabilitySettingsDescriptor::childPage)), arguments -> new CapabilitySettingsDescriptor((String) arguments[0], (CapabilityKind) arguments[1], (String) arguments[2], (String) arguments[3], (CapabilityChildPage) arguments[4]));
        }
    }
}
