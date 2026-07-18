package dev.tomewisp.settings;

import dev.tomewisp.guide.ui.GuideDisplayConfig;
import dev.tomewisp.settings.model.ModelProfileSettingsView;
import dev.tomewisp.settings.capability.CapabilitySettingsView;
import dev.tomewisp.settings.capability.RecipeSettingsView;
import java.util.Objects;

/** Immutable common projection consumed by the native settings screen. */
public record ClientSettingsSnapshot(
        long generation,
        GuideDisplayConfig display,
        ModelProfileSettingsView models,
        CapabilitySettingsView capabilities,
        RecipeSettingsView recipes,
        SettingsOperation operation,
        SettingsNotice notice) {
    public ClientSettingsSnapshot {
        if (generation < 0) {
            throw new IllegalArgumentException("settings generation must not be negative");
        }
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(recipes, "recipes");
        Objects.requireNonNull(operation, "operation");
    }

    public ClientSettingsSnapshot(
            long generation,
            GuideDisplayConfig display,
            ModelProfileSettingsView models,
            SettingsOperation operation,
            SettingsNotice notice) {
        this(
                generation,
                display,
                models,
                CapabilitySettingsView.defaults(),
                RecipeSettingsView.defaults(),
                operation,
                notice);
    }
}
