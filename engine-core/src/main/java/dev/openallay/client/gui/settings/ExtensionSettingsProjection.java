package dev.openallay.client.gui.settings;

import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.script.UnrestrictedJavascriptConfig;
import dev.openallay.script.schema.HostSchema;
import dev.openallay.settings.extension.ExtensionSettingsView;
import dev.openallay.requirement.RequirementEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Player-facing projection of Rhino roots, modules, adapters, and effective script access.
 * The command flag includes full access; the command-only setting remains in its settings owner.
 */
@dev.openallay.value.ValueType(ExtensionSettingsProjection.ValueSchemaProvider.class)
public final class ExtensionSettingsProjection {
    private final RuntimeCard runtime;
    private final List<RootCard> roots;
    private final List<ModuleCard> modules;
    private final List<AdapterCard> adapters;
    private final List<ExtensionCard> extensions;
    private final CatalogCard catalog;
    private final boolean experimentalCommands;
    private final boolean unrestrictedJavascript;
    private final boolean debugMode;
    public ExtensionSettingsProjection(RuntimeCard runtime, List<RootCard> roots, List<ModuleCard> modules, List<AdapterCard> adapters, List<ExtensionCard> extensions, CatalogCard catalog, boolean experimentalCommands, boolean unrestrictedJavascript, boolean debugMode) {

        Objects.requireNonNull(runtime, "runtime");
        roots = List.copyOf(roots);
        modules = List.copyOf(modules);
        adapters = List.copyOf(adapters);
        extensions = List.copyOf(extensions);
        Objects.requireNonNull(catalog, "catalog");

        this.runtime = runtime;
        this.roots = roots;
        this.modules = modules;
        this.adapters = adapters;
        this.extensions = extensions;
        this.catalog = catalog;
        this.experimentalCommands = experimentalCommands;
        this.unrestrictedJavascript = unrestrictedJavascript;
        this.debugMode = debugMode;
    }
    public RuntimeCard runtime() { return runtime; }
    public List<RootCard> roots() { return roots; }
    public List<ModuleCard> modules() { return modules; }
    public List<AdapterCard> adapters() { return adapters; }
    public List<ExtensionCard> extensions() { return extensions; }
    public CatalogCard catalog() { return catalog; }
    public boolean experimentalCommands() { return experimentalCommands; }
    public boolean unrestrictedJavascript() { return unrestrictedJavascript; }
    public boolean debugMode() { return debugMode; }
public static ExtensionSettingsProjection from(ExtensionSettingsView view, CommandCapabilityConfig commands, boolean debugMode) {
        return from(view, commands, UnrestrictedJavascriptConfig.defaults(), debugMode);
    }
public static ExtensionSettingsProjection from(
            ExtensionSettingsView view,
            CommandCapabilityConfig commands,
            UnrestrictedJavascriptConfig unrestricted,
            boolean debugMode) {
        return from(view, commands, unrestricted,
                new RequirementEnvironment(Map.of(), Map.of(), Map.of()), debugMode);
    }
public static ExtensionSettingsProjection from(
            ExtensionSettingsView view,
            CommandCapabilityConfig commands,
            UnrestrictedJavascriptConfig unrestricted,
            RequirementEnvironment environment,
            boolean debugMode) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(unrestricted, "unrestricted");
        return new ExtensionSettingsProjection(
                new RuntimeCard(
                        "openallay:run_javascript",
                        "screen.openallay.settings.extensions.runtime.title",
                        "screen.openallay.settings.extensions.runtime.description",
                        List.of("source", "handles", "title", "description"),
                        List.of(
                                "typed result",
                                "workspace handle",
                                "preview",
                                "evidence")),
                view.roots().stream()
                        .map(root -> new RootCard(
                                root.name(),
                                root.availability().name(),
                                root.provider(),
                                root.summary(),
                                root.evidenceOwner(),
                                renderSchema(root.schema(), 0)))
                        .toList(),
                view.bundledModules().stream().map(ModuleCard::new).toList(),
                view.adapters().stream()
                        .map(adapter -> new AdapterCard(
                                adapter.id(),
                                adapter.provider(),
                                adapter.summary(),
                                adapter.available(),
                                adapter.schema() == null
                                        ? ""
                                        : renderSchema(adapter.schema(), 0),
                                adapter.diagnostic()))
                        .toList(),
                view.extensions().stream()
                        .map(extension -> new ExtensionCard(
                                extension.id(),
                                extension.name(),
                                extension.version(),
                                extension.state(),
                                extension.provider(),
                                extension.summary(),
                                extension.loaders(),
                                extension.minecraftVersionRange(),
                                extension.openAllayApiVersionRange(),
                                extension.source(),
                                extension.contributions(),
                                extension.diagnostic(),
                                extension.packageInfo().catalogListed(),
                                extension.packageInfo().availableVersion(),
                                extension.packageInfo().artifact(),
                                extension.packageInfo().sha256(),
                                extension.packageInfo().updateAvailable(),
                                extension.packageInfo().installable(),
                                RequirementSettingsProjection.evaluate(extension.requirements(), environment)))
                        .toList(),
                new CatalogCard(
                        view.catalog().configured(),
                        view.catalog().available(),
                        view.catalog().generatedAt().map(Object::toString).orElse(""),
                        view.catalog().notice().map(ExtensionSettingsView.Notice::code).orElse(""),
                        view.catalog().notice().map(ExtensionSettingsView.Notice::message).orElse("")),
                commands.enabled() || unrestricted.enabled(),
                unrestricted.enabled(),
                debugMode);
    }
public ExtensionSettingsProjection toggleExperimentalCommands() {
        if (unrestrictedJavascript) return this;
        return new ExtensionSettingsProjection(
                runtime,
                roots,
                modules,
                adapters,
                extensions,
                catalog,
                !experimentalCommands,
                unrestrictedJavascript,
                debugMode);
    }
public List<ExtensionCard> installed() {
        return extensions.stream()
                .filter(extension -> extension.state() == ExtensionSettingsView.State.ACTIVE
                        || extension.state()
                                == ExtensionSettingsView.State.RESTART_REQUIRED
                        || extension.state() == ExtensionSettingsView.State.UNAVAILABLE)
                .toList();
    }
public List<ExtensionCard> community() {
        return extensions.stream()
                .filter(ExtensionCard::catalogListed)
                .toList();
    }
public java.util.Optional<ExtensionCard> find(String id) {
        return extensions.stream().filter(extension -> extension.id().equals(id)).findFirst();
    }
public java.util.Optional<ExtensionCard> findInstalled(String id) {
        return installed().stream().filter(extension -> extension.id().equals(id)).findFirst();
    }
public java.util.Optional<ExtensionCard> findCommunity(String id) {
        return community().stream().filter(extension -> extension.id().equals(id)).findFirst();
    }
@dev.openallay.value.ValueType(RuntimeCard.ValueSchemaProvider.class)
public static final class RuntimeCard {
    private final String id;
    private final String titleKey;
    private final String descriptionKey;
    private final List<String> parameters;
    private final List<String> returns;
    public RuntimeCard(String id, String titleKey, String descriptionKey, List<String> parameters, List<String> returns) {

            parameters = List.copyOf(parameters);
            returns = List.copyOf(returns);

        this.id = id;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
        this.parameters = parameters;
        this.returns = returns;
    }
    public String id() { return id; }
    public String titleKey() { return titleKey; }
    public String descriptionKey() { return descriptionKey; }
    public List<String> parameters() { return parameters; }
    public List<String> returns() { return returns; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RuntimeCard)) return false;
        RuntimeCard that = (RuntimeCard) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(descriptionKey, that.descriptionKey) && java.util.Objects.equals(parameters, that.parameters) && java.util.Objects.equals(returns, that.returns);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(descriptionKey);
        hash = 31 * hash + java.util.Objects.hashCode(parameters);
        hash = 31 * hash + java.util.Objects.hashCode(returns);
        return hash;
    }
    @Override public String toString() { return "RuntimeCard[id=" + id + ", titleKey=" + titleKey + ", descriptionKey=" + descriptionKey + ", parameters=" + parameters + ", returns=" + returns + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RuntimeCard> schema() {
            return new dev.openallay.value.ValueSchema<>(RuntimeCard.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RuntimeCard>>asList(new dev.openallay.value.ValueSchema.Component<>(RuntimeCard.class, "id", RuntimeCard::id), new dev.openallay.value.ValueSchema.Component<>(RuntimeCard.class, "titleKey", RuntimeCard::titleKey), new dev.openallay.value.ValueSchema.Component<>(RuntimeCard.class, "descriptionKey", RuntimeCard::descriptionKey), new dev.openallay.value.ValueSchema.Component<>(RuntimeCard.class, "parameters", RuntimeCard::parameters), new dev.openallay.value.ValueSchema.Component<>(RuntimeCard.class, "returns", RuntimeCard::returns)), arguments -> new RuntimeCard((String) arguments[0], (String) arguments[1], (String) arguments[2], (List) arguments[3], (List) arguments[4]));
        }
    }
}
@dev.openallay.value.ValueType(RootCard.ValueSchemaProvider.class)
public static final class RootCard {
    private final String name;
    private final String availability;
    private final String provider;
    private final String summary;
    private final String evidenceOwner;
    private final String schema;
    public RootCard(String name, String availability, String provider, String summary, String evidenceOwner, String schema) {
        this.name = name;
        this.availability = availability;
        this.provider = provider;
        this.summary = summary;
        this.evidenceOwner = evidenceOwner;
        this.schema = schema;
    }
    public String name() { return name; }
    public String availability() { return availability; }
    public String provider() { return provider; }
    public String summary() { return summary; }
    public String evidenceOwner() { return evidenceOwner; }
    public String schema() { return schema; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RootCard)) return false;
        RootCard that = (RootCard) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(availability, that.availability) && java.util.Objects.equals(provider, that.provider) && java.util.Objects.equals(summary, that.summary) && java.util.Objects.equals(evidenceOwner, that.evidenceOwner) && java.util.Objects.equals(schema, that.schema);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(availability);
        hash = 31 * hash + java.util.Objects.hashCode(provider);
        hash = 31 * hash + java.util.Objects.hashCode(summary);
        hash = 31 * hash + java.util.Objects.hashCode(evidenceOwner);
        hash = 31 * hash + java.util.Objects.hashCode(schema);
        return hash;
    }
    @Override public String toString() { return "RootCard[name=" + name + ", availability=" + availability + ", provider=" + provider + ", summary=" + summary + ", evidenceOwner=" + evidenceOwner + ", schema=" + schema + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RootCard> schema() {
            return new dev.openallay.value.ValueSchema<>(RootCard.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RootCard>>asList(new dev.openallay.value.ValueSchema.Component<>(RootCard.class, "name", RootCard::name), new dev.openallay.value.ValueSchema.Component<>(RootCard.class, "availability", RootCard::availability), new dev.openallay.value.ValueSchema.Component<>(RootCard.class, "provider", RootCard::provider), new dev.openallay.value.ValueSchema.Component<>(RootCard.class, "summary", RootCard::summary), new dev.openallay.value.ValueSchema.Component<>(RootCard.class, "evidenceOwner", RootCard::evidenceOwner), new dev.openallay.value.ValueSchema.Component<>(RootCard.class, "schema", RootCard::schema)), arguments -> new RootCard((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (String) arguments[5]));
        }
    }
}
@dev.openallay.value.ValueType(ModuleCard.ValueSchemaProvider.class)
public static final class ModuleCard {
    private final String id;
    public ModuleCard(String id) {
        this.id = id;
    }
    public String id() { return id; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModuleCard)) return false;
        ModuleCard that = (ModuleCard) other;
        return java.util.Objects.equals(id, that.id);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        return hash;
    }
    @Override public String toString() { return "ModuleCard[id=" + id + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModuleCard> schema() {
            return new dev.openallay.value.ValueSchema<>(ModuleCard.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModuleCard>>asList(new dev.openallay.value.ValueSchema.Component<>(ModuleCard.class, "id", ModuleCard::id)), arguments -> new ModuleCard((String) arguments[0]));
        }
    }
}
@dev.openallay.value.ValueType(AdapterCard.ValueSchemaProvider.class)
public static final class AdapterCard {
    private final String id;
    private final String provider;
    private final String summary;
    private final boolean available;
    private final String schema;
    private final String diagnostic;
    public AdapterCard(String id, String provider, String summary, boolean available, String schema, String diagnostic) {
        this.id = id;
        this.provider = provider;
        this.summary = summary;
        this.available = available;
        this.schema = schema;
        this.diagnostic = diagnostic;
    }
    public String id() { return id; }
    public String provider() { return provider; }
    public String summary() { return summary; }
    public boolean available() { return available; }
    public String schema() { return schema; }
    public String diagnostic() { return diagnostic; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AdapterCard)) return false;
        AdapterCard that = (AdapterCard) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(provider, that.provider) && java.util.Objects.equals(summary, that.summary) && available == that.available && java.util.Objects.equals(schema, that.schema) && java.util.Objects.equals(diagnostic, that.diagnostic);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(provider);
        hash = 31 * hash + java.util.Objects.hashCode(summary);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + java.util.Objects.hashCode(schema);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        return hash;
    }
    @Override public String toString() { return "AdapterCard[id=" + id + ", provider=" + provider + ", summary=" + summary + ", available=" + available + ", schema=" + schema + ", diagnostic=" + diagnostic + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<AdapterCard> schema() {
            return new dev.openallay.value.ValueSchema<>(AdapterCard.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<AdapterCard>>asList(new dev.openallay.value.ValueSchema.Component<>(AdapterCard.class, "id", AdapterCard::id), new dev.openallay.value.ValueSchema.Component<>(AdapterCard.class, "provider", AdapterCard::provider), new dev.openallay.value.ValueSchema.Component<>(AdapterCard.class, "summary", AdapterCard::summary), new dev.openallay.value.ValueSchema.Component<>(AdapterCard.class, "available", AdapterCard::available), new dev.openallay.value.ValueSchema.Component<>(AdapterCard.class, "schema", AdapterCard::schema), new dev.openallay.value.ValueSchema.Component<>(AdapterCard.class, "diagnostic", AdapterCard::diagnostic)), arguments -> new AdapterCard((String) arguments[0], (String) arguments[1], (String) arguments[2], (Boolean) arguments[3], (String) arguments[4], (String) arguments[5]));
        }
    }
}
@dev.openallay.value.ValueType(CatalogCard.ValueSchemaProvider.class)
public static final class CatalogCard {
    private final boolean configured;
    private final boolean available;
    private final String generatedAt;
    private final String noticeCode;
    private final String noticeMessage;
    public CatalogCard(boolean configured, boolean available, String generatedAt, String noticeCode, String noticeMessage) {
        this.configured = configured;
        this.available = available;
        this.generatedAt = generatedAt;
        this.noticeCode = noticeCode;
        this.noticeMessage = noticeMessage;
    }
    public boolean configured() { return configured; }
    public boolean available() { return available; }
    public String generatedAt() { return generatedAt; }
    public String noticeCode() { return noticeCode; }
    public String noticeMessage() { return noticeMessage; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CatalogCard)) return false;
        CatalogCard that = (CatalogCard) other;
        return configured == that.configured && available == that.available && java.util.Objects.equals(generatedAt, that.generatedAt) && java.util.Objects.equals(noticeCode, that.noticeCode) && java.util.Objects.equals(noticeMessage, that.noticeMessage);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(configured);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + java.util.Objects.hashCode(generatedAt);
        hash = 31 * hash + java.util.Objects.hashCode(noticeCode);
        hash = 31 * hash + java.util.Objects.hashCode(noticeMessage);
        return hash;
    }
    @Override public String toString() { return "CatalogCard[configured=" + configured + ", available=" + available + ", generatedAt=" + generatedAt + ", noticeCode=" + noticeCode + ", noticeMessage=" + noticeMessage + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CatalogCard> schema() {
            return new dev.openallay.value.ValueSchema<>(CatalogCard.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CatalogCard>>asList(new dev.openallay.value.ValueSchema.Component<>(CatalogCard.class, "configured", CatalogCard::configured), new dev.openallay.value.ValueSchema.Component<>(CatalogCard.class, "available", CatalogCard::available), new dev.openallay.value.ValueSchema.Component<>(CatalogCard.class, "generatedAt", CatalogCard::generatedAt), new dev.openallay.value.ValueSchema.Component<>(CatalogCard.class, "noticeCode", CatalogCard::noticeCode), new dev.openallay.value.ValueSchema.Component<>(CatalogCard.class, "noticeMessage", CatalogCard::noticeMessage)), arguments -> new CatalogCard((Boolean) arguments[0], (Boolean) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4]));
        }
    }
}
@dev.openallay.value.ValueType(ExtensionCard.ValueSchemaProvider.class)
public static final class ExtensionCard {
    private final String id;
    private final String name;
    private final String version;
    private final ExtensionSettingsView.State state;
    private final String provider;
    private final String summary;
    private final List<String> loaders;
    private final String minecraftVersionRange;
    private final String openAllayApiVersionRange;
    private final String source;
    private final ExtensionSettingsView.Contributions contributions;
    private final String diagnostic;
    private final boolean catalogListed;
    private final String availableVersion;
    private final String artifact;
    private final String sha256;
    private final boolean updateAvailable;
    private final boolean installable;
    private final RequirementSettingsProjection requirements;
    public ExtensionCard(String id, String name, String version, ExtensionSettingsView.State state, String provider, String summary, List<String> loaders, String minecraftVersionRange, String openAllayApiVersionRange, String source, ExtensionSettingsView.Contributions contributions, String diagnostic, boolean catalogListed, String availableVersion, String artifact, String sha256, boolean updateAvailable, boolean installable, RequirementSettingsProjection requirements) {

            loaders = List.copyOf(loaders);
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(contributions, "contributions");
            availableVersion = availableVersion == null ? "" : availableVersion;
            artifact = artifact == null ? "" : artifact;
            sha256 = sha256 == null ? "" : sha256;

        this.id = id;
        this.name = name;
        this.version = version;
        this.state = state;
        this.provider = provider;
        this.summary = summary;
        this.loaders = loaders;
        this.minecraftVersionRange = minecraftVersionRange;
        this.openAllayApiVersionRange = openAllayApiVersionRange;
        this.source = source;
        this.contributions = contributions;
        this.diagnostic = diagnostic;
        this.catalogListed = catalogListed;
        this.availableVersion = availableVersion;
        this.artifact = artifact;
        this.sha256 = sha256;
        this.updateAvailable = updateAvailable;
        this.installable = installable;
        this.requirements = requirements;
    }
    public String id() { return id; }
    public String name() { return name; }
    public String version() { return version; }
    public ExtensionSettingsView.State state() { return state; }
    public String provider() { return provider; }
    public String summary() { return summary; }
    public List<String> loaders() { return loaders; }
    public String minecraftVersionRange() { return minecraftVersionRange; }
    public String openAllayApiVersionRange() { return openAllayApiVersionRange; }
    public String source() { return source; }
    public ExtensionSettingsView.Contributions contributions() { return contributions; }
    public String diagnostic() { return diagnostic; }
    public boolean catalogListed() { return catalogListed; }
    public String availableVersion() { return availableVersion; }
    public String artifact() { return artifact; }
    public String sha256() { return sha256; }
    public boolean updateAvailable() { return updateAvailable; }
    public boolean installable() { return installable; }
    public RequirementSettingsProjection requirements() { return requirements; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionCard)) return false;
        ExtensionCard that = (ExtensionCard) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(version, that.version) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(provider, that.provider) && java.util.Objects.equals(summary, that.summary) && java.util.Objects.equals(loaders, that.loaders) && java.util.Objects.equals(minecraftVersionRange, that.minecraftVersionRange) && java.util.Objects.equals(openAllayApiVersionRange, that.openAllayApiVersionRange) && java.util.Objects.equals(source, that.source) && java.util.Objects.equals(contributions, that.contributions) && java.util.Objects.equals(diagnostic, that.diagnostic) && catalogListed == that.catalogListed && java.util.Objects.equals(availableVersion, that.availableVersion) && java.util.Objects.equals(artifact, that.artifact) && java.util.Objects.equals(sha256, that.sha256) && updateAvailable == that.updateAvailable && installable == that.installable && java.util.Objects.equals(requirements, that.requirements);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(version);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(provider);
        hash = 31 * hash + java.util.Objects.hashCode(summary);
        hash = 31 * hash + java.util.Objects.hashCode(loaders);
        hash = 31 * hash + java.util.Objects.hashCode(minecraftVersionRange);
        hash = 31 * hash + java.util.Objects.hashCode(openAllayApiVersionRange);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(contributions);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        hash = 31 * hash + Boolean.hashCode(catalogListed);
        hash = 31 * hash + java.util.Objects.hashCode(availableVersion);
        hash = 31 * hash + java.util.Objects.hashCode(artifact);
        hash = 31 * hash + java.util.Objects.hashCode(sha256);
        hash = 31 * hash + Boolean.hashCode(updateAvailable);
        hash = 31 * hash + Boolean.hashCode(installable);
        hash = 31 * hash + java.util.Objects.hashCode(requirements);
        return hash;
    }
    @Override public String toString() { return "ExtensionCard[id=" + id + ", name=" + name + ", version=" + version + ", state=" + state + ", provider=" + provider + ", summary=" + summary + ", loaders=" + loaders + ", minecraftVersionRange=" + minecraftVersionRange + ", openAllayApiVersionRange=" + openAllayApiVersionRange + ", source=" + source + ", contributions=" + contributions + ", diagnostic=" + diagnostic + ", catalogListed=" + catalogListed + ", availableVersion=" + availableVersion + ", artifact=" + artifact + ", sha256=" + sha256 + ", updateAvailable=" + updateAvailable + ", installable=" + installable + ", requirements=" + requirements + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ExtensionCard> schema() {
            return new dev.openallay.value.ValueSchema<>(ExtensionCard.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ExtensionCard>>asList(new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "id", ExtensionCard::id), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "name", ExtensionCard::name), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "version", ExtensionCard::version), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "state", ExtensionCard::state), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "provider", ExtensionCard::provider), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "summary", ExtensionCard::summary), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "loaders", ExtensionCard::loaders), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "minecraftVersionRange", ExtensionCard::minecraftVersionRange), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "openAllayApiVersionRange", ExtensionCard::openAllayApiVersionRange), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "source", ExtensionCard::source), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "contributions", ExtensionCard::contributions), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "diagnostic", ExtensionCard::diagnostic), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "catalogListed", ExtensionCard::catalogListed), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "availableVersion", ExtensionCard::availableVersion), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "artifact", ExtensionCard::artifact), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "sha256", ExtensionCard::sha256), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "updateAvailable", ExtensionCard::updateAvailable), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "installable", ExtensionCard::installable), new dev.openallay.value.ValueSchema.Component<>(ExtensionCard.class, "requirements", ExtensionCard::requirements)), arguments -> new ExtensionCard((String) arguments[0], (String) arguments[1], (String) arguments[2], (ExtensionSettingsView.State) arguments[3], (String) arguments[4], (String) arguments[5], (List) arguments[6], (String) arguments[7], (String) arguments[8], (String) arguments[9], (ExtensionSettingsView.Contributions) arguments[10], (String) arguments[11], (Boolean) arguments[12], (String) arguments[13], (String) arguments[14], (String) arguments[15], (Boolean) arguments[16], (Boolean) arguments[17], (RequirementSettingsProjection) arguments[18]));
        }
    }
}
private static String renderSchema(HostSchema schema, int depth) {
        if (depth >= 5) {
            return schema.kind();
        }
        Objects.requireNonNull(schema);
        if (schema instanceof HostSchema.Scalar scalar) {
            return scalar.kind();
        } else if (schema instanceof HostSchema.Enumeration enumeration) {
            return enumeration.kind() + "(" + String.join(" | ", enumeration.values()) + ")";
        } else if (schema instanceof HostSchema.Sequence sequence) {
            return "array<" + renderSchema(sequence.elements(), depth + 1) + ">";
        } else if (schema instanceof HostSchema.OptionalValue optional) {
            return renderSchema(optional.value(), depth + 1) + "?";
        } else if (schema instanceof HostSchema.Dictionary dictionary) {
            return "map<string, " + renderSchema(dictionary.values(), depth + 1) + ">";
        } else if (schema instanceof HostSchema.DynamicJson ignored) {
            return "dynamic JSON";
        } else if (schema instanceof HostSchema.DynamicDetached ignored) {
            return "declared extension value";
        } else if (schema instanceof HostSchema.RecordValue record) {
            return renderRecord(record, depth);
        }
        throw new IncompatibleClassChangeError();
    }
private static String renderRecord(HostSchema.RecordValue record, int depth) {
        List<String> fields = new ArrayList<>();
        for (Map.Entry<String, HostSchema> field : record.fields().entrySet()) {
            fields.add(field.getKey() + ": " + renderSchema(field.getValue(), depth + 1));
        }
        fields.sort(String::compareTo);
        return "{ " + String.join(", ", fields) + " }";
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionSettingsProjection)) return false;
        ExtensionSettingsProjection that = (ExtensionSettingsProjection) other;
        return java.util.Objects.equals(runtime, that.runtime) && java.util.Objects.equals(roots, that.roots) && java.util.Objects.equals(modules, that.modules) && java.util.Objects.equals(adapters, that.adapters) && java.util.Objects.equals(extensions, that.extensions) && java.util.Objects.equals(catalog, that.catalog) && experimentalCommands == that.experimentalCommands && unrestrictedJavascript == that.unrestrictedJavascript && debugMode == that.debugMode;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(runtime);
        hash = 31 * hash + java.util.Objects.hashCode(roots);
        hash = 31 * hash + java.util.Objects.hashCode(modules);
        hash = 31 * hash + java.util.Objects.hashCode(adapters);
        hash = 31 * hash + java.util.Objects.hashCode(extensions);
        hash = 31 * hash + java.util.Objects.hashCode(catalog);
        hash = 31 * hash + Boolean.hashCode(experimentalCommands);
        hash = 31 * hash + Boolean.hashCode(unrestrictedJavascript);
        hash = 31 * hash + Boolean.hashCode(debugMode);
        return hash;
    }
    @Override public String toString() { return "ExtensionSettingsProjection[runtime=" + runtime + ", roots=" + roots + ", modules=" + modules + ", adapters=" + adapters + ", extensions=" + extensions + ", catalog=" + catalog + ", experimentalCommands=" + experimentalCommands + ", unrestrictedJavascript=" + unrestrictedJavascript + ", debugMode=" + debugMode + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ExtensionSettingsProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(ExtensionSettingsProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ExtensionSettingsProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(ExtensionSettingsProjection.class, "runtime", ExtensionSettingsProjection::runtime), new dev.openallay.value.ValueSchema.Component<>(ExtensionSettingsProjection.class, "roots", ExtensionSettingsProjection::roots), new dev.openallay.value.ValueSchema.Component<>(ExtensionSettingsProjection.class, "modules", ExtensionSettingsProjection::modules), new dev.openallay.value.ValueSchema.Component<>(ExtensionSettingsProjection.class, "adapters", ExtensionSettingsProjection::adapters), new dev.openallay.value.ValueSchema.Component<>(ExtensionSettingsProjection.class, "extensions", ExtensionSettingsProjection::extensions), new dev.openallay.value.ValueSchema.Component<>(ExtensionSettingsProjection.class, "catalog", ExtensionSettingsProjection::catalog), new dev.openallay.value.ValueSchema.Component<>(ExtensionSettingsProjection.class, "experimentalCommands", ExtensionSettingsProjection::experimentalCommands), new dev.openallay.value.ValueSchema.Component<>(ExtensionSettingsProjection.class, "unrestrictedJavascript", ExtensionSettingsProjection::unrestrictedJavascript), new dev.openallay.value.ValueSchema.Component<>(ExtensionSettingsProjection.class, "debugMode", ExtensionSettingsProjection::debugMode)), arguments -> new ExtensionSettingsProjection((RuntimeCard) arguments[0], (List) arguments[1], (List) arguments[2], (List) arguments[3], (List) arguments[4], (CatalogCard) arguments[5], (Boolean) arguments[6], (Boolean) arguments[7], (Boolean) arguments[8]));
        }
    }
}
