package dev.openallay.client.gui.settings;

import dev.openallay.guide.ui.GuideDisplayConfig;
import java.util.Objects;

/** Friendly General-page projection for implemented presentation settings only. */
@dev.openallay.value.ValueType(GeneralSettingsProjection.ValueSchemaProvider.class)
public final class GeneralSettingsProjection {
    private final String assistantName;
    private final boolean debugMode;
    private final boolean animationsEnabled;
    private final String titleKey;
    private final String assistantNameLabelKey;
    private final String assistantNameDescriptionKey;
    private final String debugLabelKey;
    private final String debugDescriptionKey;
    private final String debugStatusKey;
    private final String animationsLabelKey;
    private final String animationsDescriptionKey;
    private final String animationsStatusKey;
    private final String narrationKey;
    private final GuideDisplayConfig display;
    public GeneralSettingsProjection(String assistantName, boolean debugMode, boolean animationsEnabled, String titleKey, String assistantNameLabelKey, String assistantNameDescriptionKey, String debugLabelKey, String debugDescriptionKey, String debugStatusKey, String animationsLabelKey, String animationsDescriptionKey, String animationsStatusKey, String narrationKey, GuideDisplayConfig display) {
        this.assistantName = assistantName;
        this.debugMode = debugMode;
        this.animationsEnabled = animationsEnabled;
        this.titleKey = titleKey;
        this.assistantNameLabelKey = assistantNameLabelKey;
        this.assistantNameDescriptionKey = assistantNameDescriptionKey;
        this.debugLabelKey = debugLabelKey;
        this.debugDescriptionKey = debugDescriptionKey;
        this.debugStatusKey = debugStatusKey;
        this.animationsLabelKey = animationsLabelKey;
        this.animationsDescriptionKey = animationsDescriptionKey;
        this.animationsStatusKey = animationsStatusKey;
        this.narrationKey = narrationKey;
        this.display = display;
    }
    public String assistantName() { return assistantName; }
    public boolean debugMode() { return debugMode; }
    public boolean animationsEnabled() { return animationsEnabled; }
    public String titleKey() { return titleKey; }
    public String assistantNameLabelKey() { return assistantNameLabelKey; }
    public String assistantNameDescriptionKey() { return assistantNameDescriptionKey; }
    public String debugLabelKey() { return debugLabelKey; }
    public String debugDescriptionKey() { return debugDescriptionKey; }
    public String debugStatusKey() { return debugStatusKey; }
    public String animationsLabelKey() { return animationsLabelKey; }
    public String animationsDescriptionKey() { return animationsDescriptionKey; }
    public String animationsStatusKey() { return animationsStatusKey; }
    public String narrationKey() { return narrationKey; }
    public GuideDisplayConfig display() { return display; }
public static GeneralSettingsProjection from(GuideDisplayConfig display) {
        Objects.requireNonNull(display, "display");
        return new GeneralSettingsProjection(
                display.assistantName(),
                display.debugMode(),
                display.animationsEnabled(),
                "screen.openallay.settings.general.title",
                "screen.openallay.settings.general.assistant_name.label",
                "screen.openallay.settings.general.assistant_name.description",
                "screen.openallay.settings.general.debug.label",
                "screen.openallay.settings.general.debug.description",
                display.debugMode()
                        ? "screen.openallay.settings.general.debug.enabled"
                        : "screen.openallay.settings.general.debug.disabled",
                "screen.openallay.settings.general.animations.label",
                "screen.openallay.settings.general.animations.description",
                display.animationsEnabled()
                        ? "screen.openallay.settings.general.animations.enabled"
                        : "screen.openallay.settings.general.animations.disabled",
                "screen.openallay.settings.general.narration", display);
    }
public GuideDisplayConfig toggleDebug() {
        return display.withDebugMode(!debugMode);
    }
public GuideDisplayConfig toggleAnimations() {
        return display.withAnimationsEnabled(!animationsEnabled);
    }
public GuideDisplayConfig renameAssistant(String nextAssistantName) {
        return display.withAssistantName(nextAssistantName);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GeneralSettingsProjection)) return false;
        GeneralSettingsProjection that = (GeneralSettingsProjection) other;
        return java.util.Objects.equals(assistantName, that.assistantName) && debugMode == that.debugMode && animationsEnabled == that.animationsEnabled && java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(assistantNameLabelKey, that.assistantNameLabelKey) && java.util.Objects.equals(assistantNameDescriptionKey, that.assistantNameDescriptionKey) && java.util.Objects.equals(debugLabelKey, that.debugLabelKey) && java.util.Objects.equals(debugDescriptionKey, that.debugDescriptionKey) && java.util.Objects.equals(debugStatusKey, that.debugStatusKey) && java.util.Objects.equals(animationsLabelKey, that.animationsLabelKey) && java.util.Objects.equals(animationsDescriptionKey, that.animationsDescriptionKey) && java.util.Objects.equals(animationsStatusKey, that.animationsStatusKey) && java.util.Objects.equals(narrationKey, that.narrationKey) && java.util.Objects.equals(display, that.display);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(assistantName);
        hash = 31 * hash + Boolean.hashCode(debugMode);
        hash = 31 * hash + Boolean.hashCode(animationsEnabled);
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(assistantNameLabelKey);
        hash = 31 * hash + java.util.Objects.hashCode(assistantNameDescriptionKey);
        hash = 31 * hash + java.util.Objects.hashCode(debugLabelKey);
        hash = 31 * hash + java.util.Objects.hashCode(debugDescriptionKey);
        hash = 31 * hash + java.util.Objects.hashCode(debugStatusKey);
        hash = 31 * hash + java.util.Objects.hashCode(animationsLabelKey);
        hash = 31 * hash + java.util.Objects.hashCode(animationsDescriptionKey);
        hash = 31 * hash + java.util.Objects.hashCode(animationsStatusKey);
        hash = 31 * hash + java.util.Objects.hashCode(narrationKey);
        hash = 31 * hash + java.util.Objects.hashCode(display);
        return hash;
    }
    @Override public String toString() { return "GeneralSettingsProjection[assistantName=" + assistantName + ", debugMode=" + debugMode + ", animationsEnabled=" + animationsEnabled + ", titleKey=" + titleKey + ", assistantNameLabelKey=" + assistantNameLabelKey + ", assistantNameDescriptionKey=" + assistantNameDescriptionKey + ", debugLabelKey=" + debugLabelKey + ", debugDescriptionKey=" + debugDescriptionKey + ", debugStatusKey=" + debugStatusKey + ", animationsLabelKey=" + animationsLabelKey + ", animationsDescriptionKey=" + animationsDescriptionKey + ", animationsStatusKey=" + animationsStatusKey + ", narrationKey=" + narrationKey + ", display=" + display + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GeneralSettingsProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(GeneralSettingsProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GeneralSettingsProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(GeneralSettingsProjection.class, "assistantName", GeneralSettingsProjection::assistantName), new dev.openallay.value.ValueSchema.Component<>(GeneralSettingsProjection.class, "debugMode", GeneralSettingsProjection::debugMode), new dev.openallay.value.ValueSchema.Component<>(GeneralSettingsProjection.class, "animationsEnabled", GeneralSettingsProjection::animationsEnabled), new dev.openallay.value.ValueSchema.Component<>(GeneralSettingsProjection.class, "titleKey", GeneralSettingsProjection::titleKey), new dev.openallay.value.ValueSchema.Component<>(GeneralSettingsProjection.class, "assistantNameLabelKey", GeneralSettingsProjection::assistantNameLabelKey), new dev.openallay.value.ValueSchema.Component<>(GeneralSettingsProjection.class, "assistantNameDescriptionKey", GeneralSettingsProjection::assistantNameDescriptionKey), new dev.openallay.value.ValueSchema.Component<>(GeneralSettingsProjection.class, "debugLabelKey", GeneralSettingsProjection::debugLabelKey), new dev.openallay.value.ValueSchema.Component<>(GeneralSettingsProjection.class, "debugDescriptionKey", GeneralSettingsProjection::debugDescriptionKey), new dev.openallay.value.ValueSchema.Component<>(GeneralSettingsProjection.class, "debugStatusKey", GeneralSettingsProjection::debugStatusKey), new dev.openallay.value.ValueSchema.Component<>(GeneralSettingsProjection.class, "animationsLabelKey", GeneralSettingsProjection::animationsLabelKey), new dev.openallay.value.ValueSchema.Component<>(GeneralSettingsProjection.class, "animationsDescriptionKey", GeneralSettingsProjection::animationsDescriptionKey), new dev.openallay.value.ValueSchema.Component<>(GeneralSettingsProjection.class, "animationsStatusKey", GeneralSettingsProjection::animationsStatusKey), new dev.openallay.value.ValueSchema.Component<>(GeneralSettingsProjection.class, "narrationKey", GeneralSettingsProjection::narrationKey), new dev.openallay.value.ValueSchema.Component<>(GeneralSettingsProjection.class, "display", GeneralSettingsProjection::display)), arguments -> new GeneralSettingsProjection((String) arguments[0], (Boolean) arguments[1], (Boolean) arguments[2], (String) arguments[3], (String) arguments[4], (String) arguments[5], (String) arguments[6], (String) arguments[7], (String) arguments[8], (String) arguments[9], (String) arguments[10], (String) arguments[11], (String) arguments[12], (GuideDisplayConfig) arguments[13]));
        }
    }
}
