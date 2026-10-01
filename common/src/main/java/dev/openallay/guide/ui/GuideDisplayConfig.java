package dev.openallay.guide.ui;

public record GuideDisplayConfig(
        boolean debugMode,
        boolean animationsEnabled,
        String assistantName) {
    public static final String DEFAULT_ASSISTANT_NAME = "OpenAllay";

    public GuideDisplayConfig {
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
                debugMode, animationsEnabled, nextAssistantName);
    }
}
