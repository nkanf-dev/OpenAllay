package dev.openallay.guide.ui;

public record GuideDisplayConfig(
        boolean debugMode,
        boolean animationsEnabled,
        String assistantName,
        GuideUiConfig ui) {
    public static final String DEFAULT_ASSISTANT_NAME = "OpenAllay";

    public GuideDisplayConfig(boolean debugMode, boolean animationsEnabled, String assistantName) {
        this(debugMode, animationsEnabled, assistantName, GuideUiConfig.defaults());
    }

    public GuideDisplayConfig {
        java.util.Objects.requireNonNull(ui, "ui");
        if (assistantName == null) {
            throw new IllegalArgumentException("assistantName must be a string");
        }
        assistantName = assistantName.strip();
        if (assistantName.isEmpty()) {
            throw new IllegalArgumentException("assistantName must not be blank");
        }
        if (assistantName.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("assistantName must not contain control characters");
        }
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
}
