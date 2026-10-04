package dev.openallay.settings;

import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.settings.model.ModelProfileSettingsView;
import dev.openallay.settings.model.ServerModelSettingsView;
import dev.openallay.settings.capability.CapabilitySettingsView;
import dev.openallay.settings.capability.RecipeSettingsView;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsSnapshot;
import dev.openallay.settings.extension.ExtensionSettingsView;
import dev.openallay.settings.history.HistorySettingsView;
import dev.openallay.settings.skill.SkillCommunityView;
import dev.openallay.settings.skill.SkillSettingsView;
import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.script.UnrestrictedJavascriptConfig;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable common projection consumed by the native settings screen. */
public record ClientSettingsSnapshot(
        long generation,
        GuideDisplayConfig display,
        ModelProfileSettingsView models,
        ServerModelSettingsView serverModel,
        CapabilitySettingsView capabilities,
        RecipeSettingsView recipes,
        SkillSettingsView skills,
        SkillCommunityView skillCommunity,
        ExtensionSettingsView extensions,
        CommandCapabilityConfig experimentalCommands,
        UnrestrictedJavascriptConfig unrestrictedJavascript,
        HistorySettingsView history,
        SettingsDiagnosticsSnapshot diagnostics,
        SettingsOperation operation,
        SettingsNotice notice,
        Optional<dev.openallay.settings.requirement.RequirementReview> requirementReview) {
    public ClientSettingsSnapshot {
        if (generation < 0) {
            throw new IllegalArgumentException("settings generation must not be negative");
        }
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(serverModel, "serverModel");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(recipes, "recipes");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(skillCommunity, "skillCommunity");
        Objects.requireNonNull(extensions, "extensions");
        Objects.requireNonNull(experimentalCommands, "experimentalCommands");
        Objects.requireNonNull(unrestrictedJavascript, "unrestrictedJavascript");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(operation, "operation");
        requirementReview = Objects.requireNonNull(requirementReview, "requirementReview");
    }

    public ClientSettingsSnapshot(
            long generation, GuideDisplayConfig display, ModelProfileSettingsView models,
            ServerModelSettingsView serverModel, CapabilitySettingsView capabilities,
            RecipeSettingsView recipes, SkillSettingsView skills, SkillCommunityView skillCommunity,
            ExtensionSettingsView extensions, CommandCapabilityConfig experimentalCommands,
            UnrestrictedJavascriptConfig unrestrictedJavascript, HistorySettingsView history,
            SettingsDiagnosticsSnapshot diagnostics, SettingsOperation operation, SettingsNotice notice) {
        this(generation, display, models, serverModel, capabilities, recipes, skills,
                skillCommunity, extensions, experimentalCommands, unrestrictedJavascript,
                history, diagnostics, operation, notice, Optional.empty());
    }

    public ClientSettingsSnapshot(
            long generation,
            GuideDisplayConfig display,
            ModelProfileSettingsView models,
            CapabilitySettingsView capabilities,
            RecipeSettingsView recipes,
            SkillSettingsView skills,
            ExtensionSettingsView extensions,
            CommandCapabilityConfig experimentalCommands,
        UnrestrictedJavascriptConfig unrestrictedJavascript,
            HistorySettingsView history,
            SettingsDiagnosticsSnapshot diagnostics,
            SettingsOperation operation,
            SettingsNotice notice) {
        this(
                generation,
                display,
                models,
                ServerModelSettingsView.unavailable(),
                capabilities,
                recipes,
                skills,
                SkillCommunityView.unavailable(),
                extensions,
                experimentalCommands,
                unrestrictedJavascript,
                history,
                diagnostics,
                operation,
                notice);
    }

    public ClientSettingsSnapshot(
            long generation,
            GuideDisplayConfig display,
            ModelProfileSettingsView models,
            CapabilitySettingsView capabilities,
            RecipeSettingsView recipes,
            SettingsOperation operation,
            SettingsNotice notice) {
        this(
                generation,
                display,
                models,
                ServerModelSettingsView.unavailable(),
                capabilities,
                recipes,
                SkillSettingsView.empty(),
                SkillCommunityView.unavailable(),
                ExtensionSettingsView.defaults(),
                CommandCapabilityConfig.defaults(),
                UnrestrictedJavascriptConfig.defaults(),
                HistorySettingsView.disconnected(),
                new SettingsDiagnosticsSnapshot(List.of(), Optional.empty()),
                operation,
                notice);
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
                ServerModelSettingsView.unavailable(),
                CapabilitySettingsView.defaults(),
                RecipeSettingsView.defaults(),
                SkillSettingsView.empty(),
                SkillCommunityView.unavailable(),
                ExtensionSettingsView.defaults(),
                CommandCapabilityConfig.defaults(),
                UnrestrictedJavascriptConfig.defaults(),
                HistorySettingsView.disconnected(),
                new SettingsDiagnosticsSnapshot(List.of(), Optional.empty()),
                operation,
                notice);
    }
}
