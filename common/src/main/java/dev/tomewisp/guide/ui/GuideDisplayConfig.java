package dev.tomewisp.guide.ui;

public record GuideDisplayConfig(int schemaVersion, boolean debugMode) {
    public static final int SCHEMA_VERSION = 1;

    public GuideDisplayConfig {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported display config schema version " + schemaVersion);
        }
    }

    public static GuideDisplayConfig defaults() {
        return new GuideDisplayConfig(SCHEMA_VERSION, false);
    }
}
