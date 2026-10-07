package dev.openallay.guide.ui;

@dev.openallay.value.ValueType(GuideDisplayConfig.ValueSchemaProvider.class)
public final class GuideDisplayConfig {
    private final boolean debugMode;
    private final boolean animationsEnabled;
    private final String assistantName;
    private final GuideUiConfig ui;
    public GuideDisplayConfig(boolean debugMode, boolean animationsEnabled, String assistantName, GuideUiConfig ui) {

        java.util.Objects.requireNonNull(ui, "ui");
        if (assistantName == null) {
            throw new IllegalArgumentException("assistantName must be a string");
        }
        assistantName = dev.openallay.util.Java8Strings.strip(assistantName);
        if (assistantName.isEmpty()) {
            throw new IllegalArgumentException("assistantName must not be blank");
        }
        if (assistantName.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("assistantName must not contain control characters");
        }

        this.debugMode = debugMode;
        this.animationsEnabled = animationsEnabled;
        this.assistantName = assistantName;
        this.ui = ui;
    }
    public boolean debugMode() { return debugMode; }
    public boolean animationsEnabled() { return animationsEnabled; }
    public String assistantName() { return assistantName; }
    public GuideUiConfig ui() { return ui; }
public static final String DEFAULT_ASSISTANT_NAME = "OpenAllay";
public GuideDisplayConfig(boolean debugMode, boolean animationsEnabled, String assistantName) {
        this(debugMode, animationsEnabled, assistantName, GuideUiConfig.defaults());
    }
public static GuideDisplayConfig defaults() {
        return new GuideDisplayConfig(
                false, true, DEFAULT_ASSISTANT_NAME);
    }
public GuideDisplayConfig withAssistantName(String nextAssistantName) {
        return new GuideDisplayConfig(
                debugMode, animationsEnabled, nextAssistantName, ui);
    }
public GuideDisplayConfig withDebugMode(boolean value) {
        return new GuideDisplayConfig(value, animationsEnabled, assistantName, ui);
    }
public GuideDisplayConfig withAnimationsEnabled(boolean value) {
        return new GuideDisplayConfig(debugMode, value, assistantName, ui);
    }
public GuideDisplayConfig withUi(GuideUiConfig value) {
        return new GuideDisplayConfig(debugMode, animationsEnabled, assistantName, value);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideDisplayConfig)) return false;
        GuideDisplayConfig that = (GuideDisplayConfig) other;
        return debugMode == that.debugMode && animationsEnabled == that.animationsEnabled && java.util.Objects.equals(assistantName, that.assistantName) && java.util.Objects.equals(ui, that.ui);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(debugMode);
        hash = 31 * hash + Boolean.hashCode(animationsEnabled);
        hash = 31 * hash + java.util.Objects.hashCode(assistantName);
        hash = 31 * hash + java.util.Objects.hashCode(ui);
        return hash;
    }
    @Override public String toString() { return "GuideDisplayConfig[debugMode=" + debugMode + ", animationsEnabled=" + animationsEnabled + ", assistantName=" + assistantName + ", ui=" + ui + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideDisplayConfig> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideDisplayConfig.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideDisplayConfig>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideDisplayConfig.class, "debugMode", GuideDisplayConfig::debugMode), new dev.openallay.value.ValueSchema.Component<>(GuideDisplayConfig.class, "animationsEnabled", GuideDisplayConfig::animationsEnabled), new dev.openallay.value.ValueSchema.Component<>(GuideDisplayConfig.class, "assistantName", GuideDisplayConfig::assistantName), new dev.openallay.value.ValueSchema.Component<>(GuideDisplayConfig.class, "ui", GuideDisplayConfig::ui)), arguments -> new GuideDisplayConfig((Boolean) arguments[0], (Boolean) arguments[1], (String) arguments[2], (GuideUiConfig) arguments[3]));
        }
    }
}
