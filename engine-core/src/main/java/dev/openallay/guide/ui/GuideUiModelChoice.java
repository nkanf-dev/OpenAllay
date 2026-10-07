package dev.openallay.guide.ui;

import dev.openallay.guide.GuideModelSelection;

/** Friendly, credential-free model choice for one guide session. */
@dev.openallay.value.ValueType(GuideUiModelChoice.ValueSchemaProvider.class)
public final class GuideUiModelChoice {
    private final GuideModelSelection selection;
    private final String displayName;
    private final ModelOrigin origin;
    private final boolean editable;
    private final boolean available;
    private final boolean selected;
    private final boolean running;
    private final dev.openallay.model.image.ImageInputCapability imageInput;
    private final String imageInputSource;
    public GuideUiModelChoice(GuideModelSelection selection, String displayName, ModelOrigin origin, boolean editable, boolean available, boolean selected, boolean running, dev.openallay.model.image.ImageInputCapability imageInput, String imageInputSource) {

        java.util.Objects.requireNonNull(imageInput, "imageInput");
        java.util.Objects.requireNonNull(selection, "selection");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("model choice display name must not be blank");
        }
        java.util.Objects.requireNonNull(origin, "origin");
        if ((selection.kind() == GuideModelSelection.Kind.SERVER)
                != (origin == ModelOrigin.SERVER)) {
            throw new IllegalArgumentException("model origin must match selection authority");
        }
        if (origin == ModelOrigin.SERVER && editable) {
            throw new IllegalArgumentException("server model choices are read-only");
        }

        this.selection = selection;
        this.displayName = displayName;
        this.origin = origin;
        this.editable = editable;
        this.available = available;
        this.selected = selected;
        this.running = running;
        this.imageInput = imageInput;
        this.imageInputSource = imageInputSource;
    }
    public GuideModelSelection selection() { return selection; }
    public String displayName() { return displayName; }
    public ModelOrigin origin() { return origin; }
    public boolean editable() { return editable; }
    public boolean available() { return available; }
    public boolean selected() { return selected; }
    public boolean running() { return running; }
    public dev.openallay.model.image.ImageInputCapability imageInput() { return imageInput; }
    public String imageInputSource() { return imageInputSource; }
public GuideUiModelChoice(
            GuideModelSelection selection, String displayName, ModelOrigin origin,
            boolean editable, boolean available, boolean selected, boolean running) {
        this(selection, displayName, origin, editable, available, selected, running,
                dev.openallay.model.image.ImageInputCapability.UNKNOWN, null);
    }
public GuideUiModelChoice(
            GuideModelSelection selection,
            String displayName,
            boolean available,
            boolean selected,
            boolean running) {
        this(
                selection,
                displayName,
                selection.kind() == GuideModelSelection.Kind.SERVER
                        ? ModelOrigin.SERVER
                        : ModelOrigin.CLIENT,
                selection.kind() == GuideModelSelection.Kind.CLIENT,
                available,
                selected,
                running);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideUiModelChoice)) return false;
        GuideUiModelChoice that = (GuideUiModelChoice) other;
        return java.util.Objects.equals(selection, that.selection) && java.util.Objects.equals(displayName, that.displayName) && java.util.Objects.equals(origin, that.origin) && editable == that.editable && available == that.available && selected == that.selected && running == that.running && java.util.Objects.equals(imageInput, that.imageInput) && java.util.Objects.equals(imageInputSource, that.imageInputSource);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(selection);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + java.util.Objects.hashCode(origin);
        hash = 31 * hash + Boolean.hashCode(editable);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + Boolean.hashCode(selected);
        hash = 31 * hash + Boolean.hashCode(running);
        hash = 31 * hash + java.util.Objects.hashCode(imageInput);
        hash = 31 * hash + java.util.Objects.hashCode(imageInputSource);
        return hash;
    }
    @Override public String toString() { return "GuideUiModelChoice[selection=" + selection + ", displayName=" + displayName + ", origin=" + origin + ", editable=" + editable + ", available=" + available + ", selected=" + selected + ", running=" + running + ", imageInput=" + imageInput + ", imageInputSource=" + imageInputSource + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideUiModelChoice> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideUiModelChoice.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideUiModelChoice>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideUiModelChoice.class, "selection", GuideUiModelChoice::selection), new dev.openallay.value.ValueSchema.Component<>(GuideUiModelChoice.class, "displayName", GuideUiModelChoice::displayName), new dev.openallay.value.ValueSchema.Component<>(GuideUiModelChoice.class, "origin", GuideUiModelChoice::origin), new dev.openallay.value.ValueSchema.Component<>(GuideUiModelChoice.class, "editable", GuideUiModelChoice::editable), new dev.openallay.value.ValueSchema.Component<>(GuideUiModelChoice.class, "available", GuideUiModelChoice::available), new dev.openallay.value.ValueSchema.Component<>(GuideUiModelChoice.class, "selected", GuideUiModelChoice::selected), new dev.openallay.value.ValueSchema.Component<>(GuideUiModelChoice.class, "running", GuideUiModelChoice::running), new dev.openallay.value.ValueSchema.Component<>(GuideUiModelChoice.class, "imageInput", GuideUiModelChoice::imageInput), new dev.openallay.value.ValueSchema.Component<>(GuideUiModelChoice.class, "imageInputSource", GuideUiModelChoice::imageInputSource)), arguments -> new GuideUiModelChoice((GuideModelSelection) arguments[0], (String) arguments[1], (ModelOrigin) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6], (dev.openallay.model.image.ImageInputCapability) arguments[7], (String) arguments[8]));
        }
    }
}
