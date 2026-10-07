package dev.openallay.client.gui.settings;

import dev.openallay.model.image.ImageInputCapability;

/** Profile-local manual image-input choice; null means metadata-owned automatic discovery. */
@dev.openallay.value.ValueType(ModelImageSettingsProjection.ValueSchemaProvider.class)
public final class ModelImageSettingsProjection {
    private final ImageInputCapability selected;
    public ModelImageSettingsProjection(ImageInputCapability selected) {
        this.selected = selected;
    }
    public ImageInputCapability selected() { return selected; }
private static final String PREFIX = "screen.openallay.settings.models.image_input.";
public String selectedLabelKey() {
        return PREFIX + (selected == null ? "auto" : selected.encoded());
    }
public String explanationKey() {
        return PREFIX + "description";
    }
public ImageInputCapability next() {
        if (selected == null) return ImageInputCapability.SUPPORTED;
        return switch (selected) {
            case SUPPORTED -> ImageInputCapability.UNSUPPORTED;
            case UNSUPPORTED -> ImageInputCapability.UNKNOWN;
            case UNKNOWN -> null;
        };
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelImageSettingsProjection)) return false;
        ModelImageSettingsProjection that = (ModelImageSettingsProjection) other;
        return java.util.Objects.equals(selected, that.selected);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(selected);
        return hash;
    }
    @Override public String toString() { return "ModelImageSettingsProjection[selected=" + selected + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelImageSettingsProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelImageSettingsProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelImageSettingsProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelImageSettingsProjection.class, "selected", ModelImageSettingsProjection::selected)), arguments -> new ModelImageSettingsProjection((ImageInputCapability) arguments[0]));
        }
    }
}
