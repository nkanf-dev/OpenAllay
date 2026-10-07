package dev.openallay.context.game;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.PlayerSnapshot;
import dev.openallay.platform.InstalledModMetadata;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One detached snapshot of state a player can already see in menus, HUD/F3, or their own UI.
 * It intentionally has no spatial-search, external-container, command-string, or write surface.
 */
@dev.openallay.value.ValueType(ObservableGameStateSnapshot.ValueSchemaProvider.class)
public final class ObservableGameStateSnapshot {
    private final Instant capturedAt;
    private final RuntimeState runtime;
    private final ModsState mods;
    private final OptionsState options;
    private final PacksState packs;
    private final ShaderState shaders;
    private final DiagnosticsState diagnostics;
    private final PlayerUiState player;
    private final WorldQueriesState worldQueries;
    public ObservableGameStateSnapshot(Instant capturedAt, RuntimeState runtime, ModsState mods, OptionsState options, PacksState packs, ShaderState shaders, DiagnosticsState diagnostics, PlayerUiState player, WorldQueriesState worldQueries) {

        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(mods, "mods");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(packs, "packs");
        Objects.requireNonNull(shaders, "shaders");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(worldQueries, "worldQueries");

        this.capturedAt = capturedAt;
        this.runtime = runtime;
        this.mods = mods;
        this.options = options;
        this.packs = packs;
        this.shaders = shaders;
        this.diagnostics = diagnostics;
        this.player = player;
        this.worldQueries = worldQueries;
    }
    public Instant capturedAt() { return capturedAt; }
    public RuntimeState runtime() { return runtime; }
    public ModsState mods() { return mods; }
    public OptionsState options() { return options; }
    public PacksState packs() { return packs; }
    public ShaderState shaders() { return shaders; }
    public DiagnosticsState diagnostics() { return diagnostics; }
    public PlayerUiState player() { return player; }
    public WorldQueriesState worldQueries() { return worldQueries; }
@dev.openallay.value.ValueType(SectionDiagnostic.ValueSchemaProvider.class)
public static final class SectionDiagnostic {
    private final String code;
    private final String message;
    public SectionDiagnostic(String code, String message) {

            code = require(code, "code");
            message = require(message, "message");

        this.code = code;
        this.message = message;
    }
    public String code() { return code; }
    public String message() { return message; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SectionDiagnostic)) return false;
        SectionDiagnostic that = (SectionDiagnostic) other;
        return java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "SectionDiagnostic[code=" + code + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SectionDiagnostic> schema() {
            return new dev.openallay.value.ValueSchema<>(SectionDiagnostic.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SectionDiagnostic>>asList(new dev.openallay.value.ValueSchema.Component<>(SectionDiagnostic.class, "code", SectionDiagnostic::code), new dev.openallay.value.ValueSchema.Component<>(SectionDiagnostic.class, "message", SectionDiagnostic::message)), arguments -> new SectionDiagnostic((String) arguments[0], (String) arguments[1]));
        }
    }
}
@dev.openallay.value.ValueType(RuntimeState.ValueSchemaProvider.class)
public static final class RuntimeState {
    private final String gameVersion;
    private final String loader;
    private final boolean developmentEnvironment;
    private final String connectionKind;
    private final EvidenceMetadata evidence;
    private final List<SectionDiagnostic> diagnostics;
    public RuntimeState(String gameVersion, String loader, boolean developmentEnvironment, String connectionKind, EvidenceMetadata evidence, List<SectionDiagnostic> diagnostics) {

            gameVersion = require(gameVersion, "gameVersion");
            loader = require(loader, "loader");
            connectionKind = require(connectionKind, "connectionKind");
            Objects.requireNonNull(evidence, "evidence");
            diagnostics = dev.openallay.util.Java8Collections.listCopyOf(diagnostics);

        this.gameVersion = gameVersion;
        this.loader = loader;
        this.developmentEnvironment = developmentEnvironment;
        this.connectionKind = connectionKind;
        this.evidence = evidence;
        this.diagnostics = diagnostics;
    }
    public String gameVersion() { return gameVersion; }
    public String loader() { return loader; }
    public boolean developmentEnvironment() { return developmentEnvironment; }
    public String connectionKind() { return connectionKind; }
    public EvidenceMetadata evidence() { return evidence; }
    public List<SectionDiagnostic> diagnostics() { return diagnostics; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RuntimeState)) return false;
        RuntimeState that = (RuntimeState) other;
        return java.util.Objects.equals(gameVersion, that.gameVersion) && java.util.Objects.equals(loader, that.loader) && developmentEnvironment == that.developmentEnvironment && java.util.Objects.equals(connectionKind, that.connectionKind) && java.util.Objects.equals(evidence, that.evidence) && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(gameVersion);
        hash = 31 * hash + java.util.Objects.hashCode(loader);
        hash = 31 * hash + Boolean.hashCode(developmentEnvironment);
        hash = 31 * hash + java.util.Objects.hashCode(connectionKind);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "RuntimeState[gameVersion=" + gameVersion + ", loader=" + loader + ", developmentEnvironment=" + developmentEnvironment + ", connectionKind=" + connectionKind + ", evidence=" + evidence + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RuntimeState> schema() {
            return new dev.openallay.value.ValueSchema<>(RuntimeState.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RuntimeState>>asList(new dev.openallay.value.ValueSchema.Component<>(RuntimeState.class, "gameVersion", RuntimeState::gameVersion), new dev.openallay.value.ValueSchema.Component<>(RuntimeState.class, "loader", RuntimeState::loader), new dev.openallay.value.ValueSchema.Component<>(RuntimeState.class, "developmentEnvironment", RuntimeState::developmentEnvironment), new dev.openallay.value.ValueSchema.Component<>(RuntimeState.class, "connectionKind", RuntimeState::connectionKind), new dev.openallay.value.ValueSchema.Component<>(RuntimeState.class, "evidence", RuntimeState::evidence), new dev.openallay.value.ValueSchema.Component<>(RuntimeState.class, "diagnostics", RuntimeState::diagnostics)), arguments -> new RuntimeState((String) arguments[0], (String) arguments[1], (Boolean) arguments[2], (String) arguments[3], (EvidenceMetadata) arguments[4], (List) arguments[5]));
        }
    }
}
@dev.openallay.value.ValueType(ModsState.ValueSchemaProvider.class)
public static final class ModsState {
    private final List<InstalledModMetadata> installed;
    private final EvidenceMetadata evidence;
    private final List<SectionDiagnostic> diagnostics;
    public ModsState(List<InstalledModMetadata> installed, EvidenceMetadata evidence, List<SectionDiagnostic> diagnostics) {

            installed = dev.openallay.util.Java8Collections.listCopyOf(installed);
            Objects.requireNonNull(evidence, "evidence");
            diagnostics = dev.openallay.util.Java8Collections.listCopyOf(diagnostics);

        this.installed = installed;
        this.evidence = evidence;
        this.diagnostics = diagnostics;
    }
    public List<InstalledModMetadata> installed() { return installed; }
    public EvidenceMetadata evidence() { return evidence; }
    public List<SectionDiagnostic> diagnostics() { return diagnostics; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModsState)) return false;
        ModsState that = (ModsState) other;
        return java.util.Objects.equals(installed, that.installed) && java.util.Objects.equals(evidence, that.evidence) && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(installed);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "ModsState[installed=" + installed + ", evidence=" + evidence + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModsState> schema() {
            return new dev.openallay.value.ValueSchema<>(ModsState.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModsState>>asList(new dev.openallay.value.ValueSchema.Component<>(ModsState.class, "installed", ModsState::installed), new dev.openallay.value.ValueSchema.Component<>(ModsState.class, "evidence", ModsState::evidence), new dev.openallay.value.ValueSchema.Component<>(ModsState.class, "diagnostics", ModsState::diagnostics)), arguments -> new ModsState((List) arguments[0], (EvidenceMetadata) arguments[1], (List) arguments[2]));
        }
    }
}
@dev.openallay.value.ValueType(OptionValue.ValueSchemaProvider.class)
public static final class OptionValue {
    private final String group;
    private final String key;
    private final String displayName;
    private final String value;
    public OptionValue(String group, String key, String displayName, String value) {

            group = require(group, "group");
            key = require(key, "key");
            displayName = require(displayName, "displayName");
            value = Objects.requireNonNull(value, "value");

        this.group = group;
        this.key = key;
        this.displayName = displayName;
        this.value = value;
    }
    public String group() { return group; }
    public String key() { return key; }
    public String displayName() { return displayName; }
    public String value() { return value; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OptionValue)) return false;
        OptionValue that = (OptionValue) other;
        return java.util.Objects.equals(group, that.group) && java.util.Objects.equals(key, that.key) && java.util.Objects.equals(displayName, that.displayName) && java.util.Objects.equals(value, that.value);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(group);
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + java.util.Objects.hashCode(value);
        return hash;
    }
    @Override public String toString() { return "OptionValue[group=" + group + ", key=" + key + ", displayName=" + displayName + ", value=" + value + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<OptionValue> schema() {
            return new dev.openallay.value.ValueSchema<>(OptionValue.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<OptionValue>>asList(new dev.openallay.value.ValueSchema.Component<>(OptionValue.class, "group", OptionValue::group), new dev.openallay.value.ValueSchema.Component<>(OptionValue.class, "key", OptionValue::key), new dev.openallay.value.ValueSchema.Component<>(OptionValue.class, "displayName", OptionValue::displayName), new dev.openallay.value.ValueSchema.Component<>(OptionValue.class, "value", OptionValue::value)), arguments -> new OptionValue((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}
@dev.openallay.value.ValueType(OptionsState.ValueSchemaProvider.class)
public static final class OptionsState {
    private final List<OptionValue> values;
    private final EvidenceMetadata evidence;
    private final List<SectionDiagnostic> diagnostics;
    public OptionsState(List<OptionValue> values, EvidenceMetadata evidence, List<SectionDiagnostic> diagnostics) {

            values = dev.openallay.util.Java8Collections.listCopyOf(values);
            Objects.requireNonNull(evidence, "evidence");
            diagnostics = dev.openallay.util.Java8Collections.listCopyOf(diagnostics);

        this.values = values;
        this.evidence = evidence;
        this.diagnostics = diagnostics;
    }
    public List<OptionValue> values() { return values; }
    public EvidenceMetadata evidence() { return evidence; }
    public List<SectionDiagnostic> diagnostics() { return diagnostics; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OptionsState)) return false;
        OptionsState that = (OptionsState) other;
        return java.util.Objects.equals(values, that.values) && java.util.Objects.equals(evidence, that.evidence) && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(values);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "OptionsState[values=" + values + ", evidence=" + evidence + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<OptionsState> schema() {
            return new dev.openallay.value.ValueSchema<>(OptionsState.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<OptionsState>>asList(new dev.openallay.value.ValueSchema.Component<>(OptionsState.class, "values", OptionsState::values), new dev.openallay.value.ValueSchema.Component<>(OptionsState.class, "evidence", OptionsState::evidence), new dev.openallay.value.ValueSchema.Component<>(OptionsState.class, "diagnostics", OptionsState::diagnostics)), arguments -> new OptionsState((List) arguments[0], (EvidenceMetadata) arguments[1], (List) arguments[2]));
        }
    }
}
@dev.openallay.value.ValueType(PackInfo.ValueSchemaProvider.class)
public static final class PackInfo {
    private final String id;
    private final String title;
    private final String description;
    private final boolean selected;
    private final boolean required;
    private final String compatibility;
    private final String source;
    public PackInfo(String id, String title, String description, boolean selected, boolean required, String compatibility, String source) {

            id = require(id, "id");
            title = require(title, "title");
            description = description == null ? "" : description;
            compatibility = require(compatibility, "compatibility");
            source = require(source, "source");

        this.id = id;
        this.title = title;
        this.description = description;
        this.selected = selected;
        this.required = required;
        this.compatibility = compatibility;
        this.source = source;
    }
    public String id() { return id; }
    public String title() { return title; }
    public String description() { return description; }
    public boolean selected() { return selected; }
    public boolean required() { return required; }
    public String compatibility() { return compatibility; }
    public String source() { return source; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PackInfo)) return false;
        PackInfo that = (PackInfo) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(title, that.title) && java.util.Objects.equals(description, that.description) && selected == that.selected && required == that.required && java.util.Objects.equals(compatibility, that.compatibility) && java.util.Objects.equals(source, that.source);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + Boolean.hashCode(selected);
        hash = 31 * hash + Boolean.hashCode(required);
        hash = 31 * hash + java.util.Objects.hashCode(compatibility);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        return hash;
    }
    @Override public String toString() { return "PackInfo[id=" + id + ", title=" + title + ", description=" + description + ", selected=" + selected + ", required=" + required + ", compatibility=" + compatibility + ", source=" + source + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PackInfo> schema() {
            return new dev.openallay.value.ValueSchema<>(PackInfo.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PackInfo>>asList(new dev.openallay.value.ValueSchema.Component<>(PackInfo.class, "id", PackInfo::id), new dev.openallay.value.ValueSchema.Component<>(PackInfo.class, "title", PackInfo::title), new dev.openallay.value.ValueSchema.Component<>(PackInfo.class, "description", PackInfo::description), new dev.openallay.value.ValueSchema.Component<>(PackInfo.class, "selected", PackInfo::selected), new dev.openallay.value.ValueSchema.Component<>(PackInfo.class, "required", PackInfo::required), new dev.openallay.value.ValueSchema.Component<>(PackInfo.class, "compatibility", PackInfo::compatibility), new dev.openallay.value.ValueSchema.Component<>(PackInfo.class, "source", PackInfo::source)), arguments -> new PackInfo((String) arguments[0], (String) arguments[1], (String) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4], (String) arguments[5], (String) arguments[6]));
        }
    }
}
@dev.openallay.value.ValueType(PacksState.ValueSchemaProvider.class)
public static final class PacksState {
    private final List<PackInfo> resourcePacks;
    private final List<String> visibleDataPacks;
    private final EvidenceMetadata evidence;
    private final List<SectionDiagnostic> diagnostics;
    public PacksState(List<PackInfo> resourcePacks, List<String> visibleDataPacks, EvidenceMetadata evidence, List<SectionDiagnostic> diagnostics) {

            resourcePacks = dev.openallay.util.Java8Collections.listCopyOf(resourcePacks);
            visibleDataPacks = dev.openallay.util.Java8Collections.listCopyOf(visibleDataPacks);
            Objects.requireNonNull(evidence, "evidence");
            diagnostics = dev.openallay.util.Java8Collections.listCopyOf(diagnostics);

        this.resourcePacks = resourcePacks;
        this.visibleDataPacks = visibleDataPacks;
        this.evidence = evidence;
        this.diagnostics = diagnostics;
    }
    public List<PackInfo> resourcePacks() { return resourcePacks; }
    public List<String> visibleDataPacks() { return visibleDataPacks; }
    public EvidenceMetadata evidence() { return evidence; }
    public List<SectionDiagnostic> diagnostics() { return diagnostics; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PacksState)) return false;
        PacksState that = (PacksState) other;
        return java.util.Objects.equals(resourcePacks, that.resourcePacks) && java.util.Objects.equals(visibleDataPacks, that.visibleDataPacks) && java.util.Objects.equals(evidence, that.evidence) && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(resourcePacks);
        hash = 31 * hash + java.util.Objects.hashCode(visibleDataPacks);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "PacksState[resourcePacks=" + resourcePacks + ", visibleDataPacks=" + visibleDataPacks + ", evidence=" + evidence + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PacksState> schema() {
            return new dev.openallay.value.ValueSchema<>(PacksState.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PacksState>>asList(new dev.openallay.value.ValueSchema.Component<>(PacksState.class, "resourcePacks", PacksState::resourcePacks), new dev.openallay.value.ValueSchema.Component<>(PacksState.class, "visibleDataPacks", PacksState::visibleDataPacks), new dev.openallay.value.ValueSchema.Component<>(PacksState.class, "evidence", PacksState::evidence), new dev.openallay.value.ValueSchema.Component<>(PacksState.class, "diagnostics", PacksState::diagnostics)), arguments -> new PacksState((List) arguments[0], (List) arguments[1], (EvidenceMetadata) arguments[2], (List) arguments[3]));
        }
    }
}
@dev.openallay.value.ValueType(ShaderState.ValueSchemaProvider.class)
public static final class ShaderState {
    private final boolean integrationAvailable;
    private final String provider;
    private final String selectedPack;
    private final Map<String, String> publicOptions;
    private final EvidenceMetadata evidence;
    private final List<SectionDiagnostic> diagnostics;
    public ShaderState(boolean integrationAvailable, String provider, String selectedPack, Map<String, String> publicOptions, EvidenceMetadata evidence, List<SectionDiagnostic> diagnostics) {

            provider = require(provider, "provider");
            selectedPack = selectedPack == null ? "" : selectedPack;
            publicOptions = dev.openallay.util.Java8Collections.mapCopyOf(publicOptions);
            Objects.requireNonNull(evidence, "evidence");
            diagnostics = dev.openallay.util.Java8Collections.listCopyOf(diagnostics);

        this.integrationAvailable = integrationAvailable;
        this.provider = provider;
        this.selectedPack = selectedPack;
        this.publicOptions = publicOptions;
        this.evidence = evidence;
        this.diagnostics = diagnostics;
    }
    public boolean integrationAvailable() { return integrationAvailable; }
    public String provider() { return provider; }
    public String selectedPack() { return selectedPack; }
    public Map<String, String> publicOptions() { return publicOptions; }
    public EvidenceMetadata evidence() { return evidence; }
    public List<SectionDiagnostic> diagnostics() { return diagnostics; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ShaderState)) return false;
        ShaderState that = (ShaderState) other;
        return integrationAvailable == that.integrationAvailable && java.util.Objects.equals(provider, that.provider) && java.util.Objects.equals(selectedPack, that.selectedPack) && java.util.Objects.equals(publicOptions, that.publicOptions) && java.util.Objects.equals(evidence, that.evidence) && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(integrationAvailable);
        hash = 31 * hash + java.util.Objects.hashCode(provider);
        hash = 31 * hash + java.util.Objects.hashCode(selectedPack);
        hash = 31 * hash + java.util.Objects.hashCode(publicOptions);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "ShaderState[integrationAvailable=" + integrationAvailable + ", provider=" + provider + ", selectedPack=" + selectedPack + ", publicOptions=" + publicOptions + ", evidence=" + evidence + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ShaderState> schema() {
            return new dev.openallay.value.ValueSchema<>(ShaderState.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ShaderState>>asList(new dev.openallay.value.ValueSchema.Component<>(ShaderState.class, "integrationAvailable", ShaderState::integrationAvailable), new dev.openallay.value.ValueSchema.Component<>(ShaderState.class, "provider", ShaderState::provider), new dev.openallay.value.ValueSchema.Component<>(ShaderState.class, "selectedPack", ShaderState::selectedPack), new dev.openallay.value.ValueSchema.Component<>(ShaderState.class, "publicOptions", ShaderState::publicOptions), new dev.openallay.value.ValueSchema.Component<>(ShaderState.class, "evidence", ShaderState::evidence), new dev.openallay.value.ValueSchema.Component<>(ShaderState.class, "diagnostics", ShaderState::diagnostics)), arguments -> new ShaderState((Boolean) arguments[0], (String) arguments[1], (String) arguments[2], (Map) arguments[3], (EvidenceMetadata) arguments[4], (List) arguments[5]));
        }
    }
}
@dev.openallay.value.ValueType(DiagnosticValue.ValueSchemaProvider.class)
public static final class DiagnosticValue {
    private final String category;
    private final String key;
    private final String value;
    public DiagnosticValue(String category, String key, String value) {

            category = require(category, "category");
            key = require(key, "key");
            value = Objects.requireNonNull(value, "value");

        this.category = category;
        this.key = key;
        this.value = value;
    }
    public String category() { return category; }
    public String key() { return key; }
    public String value() { return value; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiagnosticValue)) return false;
        DiagnosticValue that = (DiagnosticValue) other;
        return java.util.Objects.equals(category, that.category) && java.util.Objects.equals(key, that.key) && java.util.Objects.equals(value, that.value);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(category);
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + java.util.Objects.hashCode(value);
        return hash;
    }
    @Override public String toString() { return "DiagnosticValue[category=" + category + ", key=" + key + ", value=" + value + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DiagnosticValue> schema() {
            return new dev.openallay.value.ValueSchema<>(DiagnosticValue.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DiagnosticValue>>asList(new dev.openallay.value.ValueSchema.Component<>(DiagnosticValue.class, "category", DiagnosticValue::category), new dev.openallay.value.ValueSchema.Component<>(DiagnosticValue.class, "key", DiagnosticValue::key), new dev.openallay.value.ValueSchema.Component<>(DiagnosticValue.class, "value", DiagnosticValue::value)), arguments -> new DiagnosticValue((String) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}
@dev.openallay.value.ValueType(DiagnosticsState.ValueSchemaProvider.class)
public static final class DiagnosticsState {
    private final List<DiagnosticValue> values;
    private final EvidenceMetadata evidence;
    private final List<SectionDiagnostic> diagnostics;
    public DiagnosticsState(List<DiagnosticValue> values, EvidenceMetadata evidence, List<SectionDiagnostic> diagnostics) {

            values = dev.openallay.util.Java8Collections.listCopyOf(values);
            Objects.requireNonNull(evidence, "evidence");
            diagnostics = dev.openallay.util.Java8Collections.listCopyOf(diagnostics);

        this.values = values;
        this.evidence = evidence;
        this.diagnostics = diagnostics;
    }
    public List<DiagnosticValue> values() { return values; }
    public EvidenceMetadata evidence() { return evidence; }
    public List<SectionDiagnostic> diagnostics() { return diagnostics; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiagnosticsState)) return false;
        DiagnosticsState that = (DiagnosticsState) other;
        return java.util.Objects.equals(values, that.values) && java.util.Objects.equals(evidence, that.evidence) && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(values);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "DiagnosticsState[values=" + values + ", evidence=" + evidence + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DiagnosticsState> schema() {
            return new dev.openallay.value.ValueSchema<>(DiagnosticsState.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DiagnosticsState>>asList(new dev.openallay.value.ValueSchema.Component<>(DiagnosticsState.class, "values", DiagnosticsState::values), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsState.class, "evidence", DiagnosticsState::evidence), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsState.class, "diagnostics", DiagnosticsState::diagnostics)), arguments -> new DiagnosticsState((List) arguments[0], (EvidenceMetadata) arguments[1], (List) arguments[2]));
        }
    }
}
@dev.openallay.value.ValueType(PlayerUiState.ValueSchemaProvider.class)
public static final class PlayerUiState {
    private final PlayerSnapshot player;
    private final String openScreen;
    private final String openScreenTitle;
    private final EvidenceMetadata evidence;
    private final List<SectionDiagnostic> diagnostics;
    private final java.util.Optional<dev.openallay.world.WorldFocusObservation> focus;
    public PlayerUiState(PlayerSnapshot player, String openScreen, String openScreenTitle, EvidenceMetadata evidence, List<SectionDiagnostic> diagnostics, java.util.Optional<dev.openallay.world.WorldFocusObservation> focus) {

            openScreen = require(openScreen, "openScreen");
            openScreenTitle = openScreenTitle == null ? "" : openScreenTitle;
            Objects.requireNonNull(evidence, "evidence");
            diagnostics = dev.openallay.util.Java8Collections.listCopyOf(diagnostics);
            focus = Objects.requireNonNull(focus, "focus");

        this.player = player;
        this.openScreen = openScreen;
        this.openScreenTitle = openScreenTitle;
        this.evidence = evidence;
        this.diagnostics = diagnostics;
        this.focus = focus;
    }
    public PlayerSnapshot player() { return player; }
    public String openScreen() { return openScreen; }
    public String openScreenTitle() { return openScreenTitle; }
    public EvidenceMetadata evidence() { return evidence; }
    public List<SectionDiagnostic> diagnostics() { return diagnostics; }
    public java.util.Optional<dev.openallay.world.WorldFocusObservation> focus() { return focus; }
public PlayerUiState(PlayerSnapshot player, String openScreen, String openScreenTitle,
                EvidenceMetadata evidence, List<SectionDiagnostic> diagnostics) {
            this(player, openScreen, openScreenTitle, evidence, diagnostics, java.util.Optional.empty());
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PlayerUiState)) return false;
        PlayerUiState that = (PlayerUiState) other;
        return java.util.Objects.equals(player, that.player) && java.util.Objects.equals(openScreen, that.openScreen) && java.util.Objects.equals(openScreenTitle, that.openScreenTitle) && java.util.Objects.equals(evidence, that.evidence) && java.util.Objects.equals(diagnostics, that.diagnostics) && java.util.Objects.equals(focus, that.focus);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(player);
        hash = 31 * hash + java.util.Objects.hashCode(openScreen);
        hash = 31 * hash + java.util.Objects.hashCode(openScreenTitle);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        hash = 31 * hash + java.util.Objects.hashCode(focus);
        return hash;
    }
    @Override public String toString() { return "PlayerUiState[player=" + player + ", openScreen=" + openScreen + ", openScreenTitle=" + openScreenTitle + ", evidence=" + evidence + ", diagnostics=" + diagnostics + ", focus=" + focus + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PlayerUiState> schema() {
            return new dev.openallay.value.ValueSchema<>(PlayerUiState.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PlayerUiState>>asList(new dev.openallay.value.ValueSchema.Component<>(PlayerUiState.class, "player", PlayerUiState::player), new dev.openallay.value.ValueSchema.Component<>(PlayerUiState.class, "openScreen", PlayerUiState::openScreen), new dev.openallay.value.ValueSchema.Component<>(PlayerUiState.class, "openScreenTitle", PlayerUiState::openScreenTitle), new dev.openallay.value.ValueSchema.Component<>(PlayerUiState.class, "evidence", PlayerUiState::evidence), new dev.openallay.value.ValueSchema.Component<>(PlayerUiState.class, "diagnostics", PlayerUiState::diagnostics), new dev.openallay.value.ValueSchema.Component<>(PlayerUiState.class, "focus", PlayerUiState::focus)), arguments -> new PlayerUiState((PlayerSnapshot) arguments[0], (String) arguments[1], (String) arguments[2], (EvidenceMetadata) arguments[3], (List) arguments[4], (java.util.Optional) arguments[5]));
        }
    }
}
@dev.openallay.value.ValueType(QueryValue.ValueSchemaProvider.class)
public static final class QueryValue {
    private final String operation;
    private final String value;
    private final boolean authoritative;
    private final String authorityNote;
    public QueryValue(String operation, String value, boolean authoritative, String authorityNote) {

            operation = require(operation, "operation");
            value = Objects.requireNonNull(value, "value");
            authorityNote = require(authorityNote, "authorityNote");

        this.operation = operation;
        this.value = value;
        this.authoritative = authoritative;
        this.authorityNote = authorityNote;
    }
    public String operation() { return operation; }
    public String value() { return value; }
    public boolean authoritative() { return authoritative; }
    public String authorityNote() { return authorityNote; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof QueryValue)) return false;
        QueryValue that = (QueryValue) other;
        return java.util.Objects.equals(operation, that.operation) && java.util.Objects.equals(value, that.value) && authoritative == that.authoritative && java.util.Objects.equals(authorityNote, that.authorityNote);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(operation);
        hash = 31 * hash + java.util.Objects.hashCode(value);
        hash = 31 * hash + Boolean.hashCode(authoritative);
        hash = 31 * hash + java.util.Objects.hashCode(authorityNote);
        return hash;
    }
    @Override public String toString() { return "QueryValue[operation=" + operation + ", value=" + value + ", authoritative=" + authoritative + ", authorityNote=" + authorityNote + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<QueryValue> schema() {
            return new dev.openallay.value.ValueSchema<>(QueryValue.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<QueryValue>>asList(new dev.openallay.value.ValueSchema.Component<>(QueryValue.class, "operation", QueryValue::operation), new dev.openallay.value.ValueSchema.Component<>(QueryValue.class, "value", QueryValue::value), new dev.openallay.value.ValueSchema.Component<>(QueryValue.class, "authoritative", QueryValue::authoritative), new dev.openallay.value.ValueSchema.Component<>(QueryValue.class, "authorityNote", QueryValue::authorityNote)), arguments -> new QueryValue((String) arguments[0], (String) arguments[1], (Boolean) arguments[2], (String) arguments[3]));
        }
    }
}
@dev.openallay.value.ValueType(WorldQueriesState.ValueSchemaProvider.class)
public static final class WorldQueriesState {
    private final Map<String, QueryValue> values;
    private final EvidenceMetadata evidence;
    private final List<SectionDiagnostic> diagnostics;
    public WorldQueriesState(Map<String, QueryValue> values, EvidenceMetadata evidence, List<SectionDiagnostic> diagnostics) {

            values = dev.openallay.util.Java8Collections.mapCopyOf(values);
            Objects.requireNonNull(evidence, "evidence");
            diagnostics = dev.openallay.util.Java8Collections.listCopyOf(diagnostics);

        this.values = values;
        this.evidence = evidence;
        this.diagnostics = diagnostics;
    }
    public Map<String, QueryValue> values() { return values; }
    public EvidenceMetadata evidence() { return evidence; }
    public List<SectionDiagnostic> diagnostics() { return diagnostics; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WorldQueriesState)) return false;
        WorldQueriesState that = (WorldQueriesState) other;
        return java.util.Objects.equals(values, that.values) && java.util.Objects.equals(evidence, that.evidence) && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(values);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "WorldQueriesState[values=" + values + ", evidence=" + evidence + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<WorldQueriesState> schema() {
            return new dev.openallay.value.ValueSchema<>(WorldQueriesState.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<WorldQueriesState>>asList(new dev.openallay.value.ValueSchema.Component<>(WorldQueriesState.class, "values", WorldQueriesState::values), new dev.openallay.value.ValueSchema.Component<>(WorldQueriesState.class, "evidence", WorldQueriesState::evidence), new dev.openallay.value.ValueSchema.Component<>(WorldQueriesState.class, "diagnostics", WorldQueriesState::diagnostics)), arguments -> new WorldQueriesState((Map) arguments[0], (EvidenceMetadata) arguments[1], (List) arguments[2]));
        }
    }
}
private static String require(String value, String name) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ObservableGameStateSnapshot)) return false;
        ObservableGameStateSnapshot that = (ObservableGameStateSnapshot) other;
        return java.util.Objects.equals(capturedAt, that.capturedAt) && java.util.Objects.equals(runtime, that.runtime) && java.util.Objects.equals(mods, that.mods) && java.util.Objects.equals(options, that.options) && java.util.Objects.equals(packs, that.packs) && java.util.Objects.equals(shaders, that.shaders) && java.util.Objects.equals(diagnostics, that.diagnostics) && java.util.Objects.equals(player, that.player) && java.util.Objects.equals(worldQueries, that.worldQueries);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        hash = 31 * hash + java.util.Objects.hashCode(runtime);
        hash = 31 * hash + java.util.Objects.hashCode(mods);
        hash = 31 * hash + java.util.Objects.hashCode(options);
        hash = 31 * hash + java.util.Objects.hashCode(packs);
        hash = 31 * hash + java.util.Objects.hashCode(shaders);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        hash = 31 * hash + java.util.Objects.hashCode(player);
        hash = 31 * hash + java.util.Objects.hashCode(worldQueries);
        return hash;
    }
    @Override public String toString() { return "ObservableGameStateSnapshot[capturedAt=" + capturedAt + ", runtime=" + runtime + ", mods=" + mods + ", options=" + options + ", packs=" + packs + ", shaders=" + shaders + ", diagnostics=" + diagnostics + ", player=" + player + ", worldQueries=" + worldQueries + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ObservableGameStateSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(ObservableGameStateSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ObservableGameStateSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(ObservableGameStateSnapshot.class, "capturedAt", ObservableGameStateSnapshot::capturedAt), new dev.openallay.value.ValueSchema.Component<>(ObservableGameStateSnapshot.class, "runtime", ObservableGameStateSnapshot::runtime), new dev.openallay.value.ValueSchema.Component<>(ObservableGameStateSnapshot.class, "mods", ObservableGameStateSnapshot::mods), new dev.openallay.value.ValueSchema.Component<>(ObservableGameStateSnapshot.class, "options", ObservableGameStateSnapshot::options), new dev.openallay.value.ValueSchema.Component<>(ObservableGameStateSnapshot.class, "packs", ObservableGameStateSnapshot::packs), new dev.openallay.value.ValueSchema.Component<>(ObservableGameStateSnapshot.class, "shaders", ObservableGameStateSnapshot::shaders), new dev.openallay.value.ValueSchema.Component<>(ObservableGameStateSnapshot.class, "diagnostics", ObservableGameStateSnapshot::diagnostics), new dev.openallay.value.ValueSchema.Component<>(ObservableGameStateSnapshot.class, "player", ObservableGameStateSnapshot::player), new dev.openallay.value.ValueSchema.Component<>(ObservableGameStateSnapshot.class, "worldQueries", ObservableGameStateSnapshot::worldQueries)), arguments -> new ObservableGameStateSnapshot((Instant) arguments[0], (RuntimeState) arguments[1], (ModsState) arguments[2], (OptionsState) arguments[3], (PacksState) arguments[4], (ShaderState) arguments[5], (DiagnosticsState) arguments[6], (PlayerUiState) arguments[7], (WorldQueriesState) arguments[8]));
        }
    }
}
