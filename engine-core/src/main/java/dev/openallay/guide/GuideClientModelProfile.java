package dev.openallay.guide;

/** Redacted client-profile summary exposed to GuideService and player UI. */
@dev.openallay.value.ValueType(GuideClientModelProfile.ValueSchemaProvider.class)
public final class GuideClientModelProfile {
    private final String id;
    private final String displayName;
    private final boolean enabled;
    private final boolean available;
    private final String modelIdentifier;
    private final GuideFailure failure;
    private final dev.openallay.model.image.ImageInputCapability imageInputCapability;
    private final String imageInputSource;
    public GuideClientModelProfile(String id, String displayName, boolean enabled, boolean available, String modelIdentifier, GuideFailure failure, dev.openallay.model.image.ImageInputCapability imageInputCapability, String imageInputSource) {

        java.util.Objects.requireNonNull(imageInputCapability, "imageInputCapability");
        if (id == null || !id.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid model profile id");
        }
        if (displayName == null || displayName.isBlank()
                || modelIdentifier == null || modelIdentifier.isBlank()) {
            throw new IllegalArgumentException("model profile labels must not be blank");
        }
        if (available == (failure != null)) {
            throw new IllegalArgumentException(
                    "available profile must not have a failure and unavailable profile must have one");
        }
        if (available && !enabled) {
            throw new IllegalArgumentException("disabled profile cannot be available");
        }

        this.id = id;
        this.displayName = displayName;
        this.enabled = enabled;
        this.available = available;
        this.modelIdentifier = modelIdentifier;
        this.failure = failure;
        this.imageInputCapability = imageInputCapability;
        this.imageInputSource = imageInputSource;
    }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public boolean enabled() { return enabled; }
    public boolean available() { return available; }
    public String modelIdentifier() { return modelIdentifier; }
    public GuideFailure failure() { return failure; }
    public dev.openallay.model.image.ImageInputCapability imageInputCapability() { return imageInputCapability; }
    public String imageInputSource() { return imageInputSource; }
public GuideClientModelProfile(
            String id, String displayName, boolean enabled, boolean available,
            String modelIdentifier, GuideFailure failure) {
        this(id, displayName, enabled, available, modelIdentifier, failure,
                dev.openallay.model.image.ImageInputCapability.UNKNOWN, null);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideClientModelProfile)) return false;
        GuideClientModelProfile that = (GuideClientModelProfile) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(displayName, that.displayName) && enabled == that.enabled && available == that.available && java.util.Objects.equals(modelIdentifier, that.modelIdentifier) && java.util.Objects.equals(failure, that.failure) && java.util.Objects.equals(imageInputCapability, that.imageInputCapability) && java.util.Objects.equals(imageInputSource, that.imageInputSource);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + java.util.Objects.hashCode(modelIdentifier);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        hash = 31 * hash + java.util.Objects.hashCode(imageInputCapability);
        hash = 31 * hash + java.util.Objects.hashCode(imageInputSource);
        return hash;
    }
    @Override public String toString() { return "GuideClientModelProfile[id=" + id + ", displayName=" + displayName + ", enabled=" + enabled + ", available=" + available + ", modelIdentifier=" + modelIdentifier + ", failure=" + failure + ", imageInputCapability=" + imageInputCapability + ", imageInputSource=" + imageInputSource + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideClientModelProfile> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideClientModelProfile.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideClientModelProfile>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideClientModelProfile.class, "id", GuideClientModelProfile::id), new dev.openallay.value.ValueSchema.Component<>(GuideClientModelProfile.class, "displayName", GuideClientModelProfile::displayName), new dev.openallay.value.ValueSchema.Component<>(GuideClientModelProfile.class, "enabled", GuideClientModelProfile::enabled), new dev.openallay.value.ValueSchema.Component<>(GuideClientModelProfile.class, "available", GuideClientModelProfile::available), new dev.openallay.value.ValueSchema.Component<>(GuideClientModelProfile.class, "modelIdentifier", GuideClientModelProfile::modelIdentifier), new dev.openallay.value.ValueSchema.Component<>(GuideClientModelProfile.class, "failure", GuideClientModelProfile::failure), new dev.openallay.value.ValueSchema.Component<>(GuideClientModelProfile.class, "imageInputCapability", GuideClientModelProfile::imageInputCapability), new dev.openallay.value.ValueSchema.Component<>(GuideClientModelProfile.class, "imageInputSource", GuideClientModelProfile::imageInputSource)), arguments -> new GuideClientModelProfile((String) arguments[0], (String) arguments[1], (Boolean) arguments[2], (Boolean) arguments[3], (String) arguments[4], (GuideFailure) arguments[5], (dev.openallay.model.image.ImageInputCapability) arguments[6], (String) arguments[7]));
        }
    }
}
