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
@dev.openallay.value.ValueType(ClientSettingsSnapshot.ValueSchemaProvider.class)
public final class ClientSettingsSnapshot {
    private final long generation;
    private final GuideDisplayConfig display;
    private final ModelProfileSettingsView models;
    private final ServerModelSettingsView serverModel;
    private final CapabilitySettingsView capabilities;
    private final RecipeSettingsView recipes;
    private final SkillSettingsView skills;
    private final SkillCommunityView skillCommunity;
    private final ExtensionSettingsView extensions;
    private final CommandCapabilityConfig experimentalCommands;
    private final UnrestrictedJavascriptConfig unrestrictedJavascript;
    private final HistorySettingsView history;
    private final SettingsDiagnosticsSnapshot diagnostics;
    private final SettingsOperation operation;
    private final SettingsNotice notice;
    private final Optional<dev.openallay.settings.requirement.RequirementReview> requirementReview;
    public ClientSettingsSnapshot(long generation, GuideDisplayConfig display, ModelProfileSettingsView models, ServerModelSettingsView serverModel, CapabilitySettingsView capabilities, RecipeSettingsView recipes, SkillSettingsView skills, SkillCommunityView skillCommunity, ExtensionSettingsView extensions, CommandCapabilityConfig experimentalCommands, UnrestrictedJavascriptConfig unrestrictedJavascript, HistorySettingsView history, SettingsDiagnosticsSnapshot diagnostics, SettingsOperation operation, SettingsNotice notice, Optional<dev.openallay.settings.requirement.RequirementReview> requirementReview) {

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

        this.generation = generation;
        this.display = display;
        this.models = models;
        this.serverModel = serverModel;
        this.capabilities = capabilities;
        this.recipes = recipes;
        this.skills = skills;
        this.skillCommunity = skillCommunity;
        this.extensions = extensions;
        this.experimentalCommands = experimentalCommands;
        this.unrestrictedJavascript = unrestrictedJavascript;
        this.history = history;
        this.diagnostics = diagnostics;
        this.operation = operation;
        this.notice = notice;
        this.requirementReview = requirementReview;
    }
    public long generation() { return generation; }
    public GuideDisplayConfig display() { return display; }
    public ModelProfileSettingsView models() { return models; }
    public ServerModelSettingsView serverModel() { return serverModel; }
    public CapabilitySettingsView capabilities() { return capabilities; }
    public RecipeSettingsView recipes() { return recipes; }
    public SkillSettingsView skills() { return skills; }
    public SkillCommunityView skillCommunity() { return skillCommunity; }
    public ExtensionSettingsView extensions() { return extensions; }
    public CommandCapabilityConfig experimentalCommands() { return experimentalCommands; }
    public UnrestrictedJavascriptConfig unrestrictedJavascript() { return unrestrictedJavascript; }
    public HistorySettingsView history() { return history; }
    public SettingsDiagnosticsSnapshot diagnostics() { return diagnostics; }
    public SettingsOperation operation() { return operation; }
    public SettingsNotice notice() { return notice; }
    public Optional<dev.openallay.settings.requirement.RequirementReview> requirementReview() { return requirementReview; }
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
                new SettingsDiagnosticsSnapshot(dev.openallay.util.Java8Collections.listOf(), Optional.empty()),
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
                new SettingsDiagnosticsSnapshot(dev.openallay.util.Java8Collections.listOf(), Optional.empty()),
                operation,
                notice);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ClientSettingsSnapshot)) return false;
        ClientSettingsSnapshot that = (ClientSettingsSnapshot) other;
        return generation == that.generation && java.util.Objects.equals(display, that.display) && java.util.Objects.equals(models, that.models) && java.util.Objects.equals(serverModel, that.serverModel) && java.util.Objects.equals(capabilities, that.capabilities) && java.util.Objects.equals(recipes, that.recipes) && java.util.Objects.equals(skills, that.skills) && java.util.Objects.equals(skillCommunity, that.skillCommunity) && java.util.Objects.equals(extensions, that.extensions) && java.util.Objects.equals(experimentalCommands, that.experimentalCommands) && java.util.Objects.equals(unrestrictedJavascript, that.unrestrictedJavascript) && java.util.Objects.equals(history, that.history) && java.util.Objects.equals(diagnostics, that.diagnostics) && java.util.Objects.equals(operation, that.operation) && java.util.Objects.equals(notice, that.notice) && java.util.Objects.equals(requirementReview, that.requirementReview);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(display);
        hash = 31 * hash + java.util.Objects.hashCode(models);
        hash = 31 * hash + java.util.Objects.hashCode(serverModel);
        hash = 31 * hash + java.util.Objects.hashCode(capabilities);
        hash = 31 * hash + java.util.Objects.hashCode(recipes);
        hash = 31 * hash + java.util.Objects.hashCode(skills);
        hash = 31 * hash + java.util.Objects.hashCode(skillCommunity);
        hash = 31 * hash + java.util.Objects.hashCode(extensions);
        hash = 31 * hash + java.util.Objects.hashCode(experimentalCommands);
        hash = 31 * hash + java.util.Objects.hashCode(unrestrictedJavascript);
        hash = 31 * hash + java.util.Objects.hashCode(history);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        hash = 31 * hash + java.util.Objects.hashCode(operation);
        hash = 31 * hash + java.util.Objects.hashCode(notice);
        hash = 31 * hash + java.util.Objects.hashCode(requirementReview);
        return hash;
    }
    @Override public String toString() { return "ClientSettingsSnapshot[generation=" + generation + ", display=" + display + ", models=" + models + ", serverModel=" + serverModel + ", capabilities=" + capabilities + ", recipes=" + recipes + ", skills=" + skills + ", skillCommunity=" + skillCommunity + ", extensions=" + extensions + ", experimentalCommands=" + experimentalCommands + ", unrestrictedJavascript=" + unrestrictedJavascript + ", history=" + history + ", diagnostics=" + diagnostics + ", operation=" + operation + ", notice=" + notice + ", requirementReview=" + requirementReview + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ClientSettingsSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(ClientSettingsSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ClientSettingsSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "generation", ClientSettingsSnapshot::generation), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "display", ClientSettingsSnapshot::display), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "models", ClientSettingsSnapshot::models), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "serverModel", ClientSettingsSnapshot::serverModel), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "capabilities", ClientSettingsSnapshot::capabilities), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "recipes", ClientSettingsSnapshot::recipes), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "skills", ClientSettingsSnapshot::skills), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "skillCommunity", ClientSettingsSnapshot::skillCommunity), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "extensions", ClientSettingsSnapshot::extensions), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "experimentalCommands", ClientSettingsSnapshot::experimentalCommands), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "unrestrictedJavascript", ClientSettingsSnapshot::unrestrictedJavascript), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "history", ClientSettingsSnapshot::history), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "diagnostics", ClientSettingsSnapshot::diagnostics), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "operation", ClientSettingsSnapshot::operation), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "notice", ClientSettingsSnapshot::notice), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsSnapshot.class, "requirementReview", ClientSettingsSnapshot::requirementReview)), arguments -> new ClientSettingsSnapshot((Long) arguments[0], (GuideDisplayConfig) arguments[1], (ModelProfileSettingsView) arguments[2], (ServerModelSettingsView) arguments[3], (CapabilitySettingsView) arguments[4], (RecipeSettingsView) arguments[5], (SkillSettingsView) arguments[6], (SkillCommunityView) arguments[7], (ExtensionSettingsView) arguments[8], (CommandCapabilityConfig) arguments[9], (UnrestrictedJavascriptConfig) arguments[10], (HistorySettingsView) arguments[11], (SettingsDiagnosticsSnapshot) arguments[12], (SettingsOperation) arguments[13], (SettingsNotice) arguments[14], (Optional) arguments[15]));
        }
    }
}
