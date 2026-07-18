package dev.tomewisp.settings;

import dev.tomewisp.guide.ui.GuideDisplayConfig;
import dev.tomewisp.settings.model.ModelProfileSettingsView;
import java.util.Objects;

/** Immutable common projection consumed by the native settings screen. */
public record ClientSettingsSnapshot(
        long generation,
        GuideDisplayConfig display,
        ModelProfileSettingsView models,
        SettingsOperation operation,
        SettingsNotice notice) {
    public ClientSettingsSnapshot {
        if (generation < 0) {
            throw new IllegalArgumentException("settings generation must not be negative");
        }
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(operation, "operation");
    }
}
