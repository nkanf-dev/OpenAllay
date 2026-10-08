package dev.openallay.settings.extension;

import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.requirement.RequirementSet;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.schema.HostRootDescriptor;
import dev.openallay.script.schema.HostSchema;
import dev.openallay.script.schema.HostSchemaCatalog;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Descriptor-only extension state for Settings.
 *
 * <p>Constructing this view never captures a game root or an extension value.
 */
@dev.openallay.value.ValueType(ExtensionSettingsView.ValueSchemaProvider.class)
public final class ExtensionSettingsView {
    private final List<Root> roots;
    private final List<String> bundledModules;
    private final List<Adapter> adapters;
    private final List<Extension> extensions;
    private final Catalog catalog;
    public ExtensionSettingsView(List<Root> roots, List<String> bundledModules, List<Adapter> adapters, List<Extension> extensions, Catalog catalog) {

        roots = dev.openallay.util.Java8Collections.listCopyOf(roots);
        bundledModules = dev.openallay.util.Java8Collections.listCopyOf(bundledModules);
        adapters = dev.openallay.util.Java8Collections.listCopyOf(adapters);
        extensions = dev.openallay.util.Java8Collections.toList(dev.openallay.util.Java8Collections.listCopyOf(extensions).stream()
                .sorted(Comparator.comparing(Extension::id)));
        Objects.requireNonNull(catalog, "catalog");

        this.roots = roots;
        this.bundledModules = bundledModules;
        this.adapters = adapters;
        this.extensions = extensions;
        this.catalog = catalog;
    }
    public List<Root> roots() { return roots; }
    public List<String> bundledModules() { return bundledModules; }
    public List<Adapter> adapters() { return adapters; }
    public List<Extension> extensions() { return extensions; }
    public Catalog catalog() { return catalog; }
public ExtensionSettingsView(
            List<Root> roots,
            List<String> bundledModules,
            List<Adapter> adapters,
            List<Extension> extensions) {
        this(roots, bundledModules, adapters, extensions, Catalog.unavailable());
    }
public static ExtensionSettingsView from(JavascriptDataModuleRegistry registry) {
        return from(registry, null);
    }
public static ExtensionSettingsView from(
            JavascriptDataModuleRegistry registry,
            OpenAllayExtensionRegistry extensionRegistry) {
        Objects.requireNonNull(registry, "registry");
        HostSchemaCatalog catalog = MinecraftAgentHostGraph.declaredOnlyCatalog();
        List<Root> roots = dev.openallay.util.Java8Collections.toList(catalog.list().stream()
                .map(summary -> Root.from(catalog, summary))
                .sorted(Comparator.comparing(Root::name)));
        List<String> modules = dev.openallay.util.Java8Collections.toList(JavascriptModuleCatalog.bundledIds().stream().sorted());
        List<Adapter> adapters = dev.openallay.util.Java8Collections.toList(registry.descriptors().stream()
                .map(Adapter::from)
                .sorted(Comparator.comparing(Adapter::id)));
        List<Extension> extensions = new java.util.ArrayList<>();
        extensions.add(core(roots, modules, adapters));
        if (extensionRegistry != null) {
            extensionRegistry.snapshot().extensions().stream()
                    .map(Extension::from)
                    .forEach(extensions::add);
        }
        return new ExtensionSettingsView(
                roots, modules, adapters, extensions, Catalog.unavailable());
    }
public static ExtensionSettingsView defaults() {
        return from(new JavascriptDataModuleRegistry());
    }
@dev.openallay.value.ValueType(Root.ValueSchemaProvider.class)
public static final class Root {
    private final String name;
    private final HostRootDescriptor.Availability availability;
    private final String provider;
    private final String summary;
    private final String evidenceOwner;
    private final HostSchema schema;
    public Root(String name, HostRootDescriptor.Availability availability, String provider, String summary, String evidenceOwner, HostSchema schema) {

            name = require(name, "name");
            Objects.requireNonNull(availability, "availability");
            provider = require(provider, "provider");
            summary = require(summary, "summary");
            evidenceOwner = require(evidenceOwner, "evidenceOwner");
            Objects.requireNonNull(schema, "schema");

        this.name = name;
        this.availability = availability;
        this.provider = provider;
        this.summary = summary;
        this.evidenceOwner = evidenceOwner;
        this.schema = schema;
    }
    public String name() { return name; }
    public HostRootDescriptor.Availability availability() { return availability; }
    public String provider() { return provider; }
    public String summary() { return summary; }
    public String evidenceOwner() { return evidenceOwner; }
    public HostSchema schema() { return schema; }
private static Root from(
                HostSchemaCatalog catalog, HostSchemaCatalog.RootSummary summary) {
            HostSchema schema = catalog.describe(summary.name())
                    .orElseThrow(() -> new java.util.NoSuchElementException("No value present"))
                    .schema();
            return new Root(
                    summary.name(),
                    summary.availability(),
                    summary.provider(),
                    summary.summary(),
                    summary.evidenceOwner(),
                    schema);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Root)) return false;
        Root that = (Root) other;
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
    @Override public String toString() { return "Root[name=" + name + ", availability=" + availability + ", provider=" + provider + ", summary=" + summary + ", evidenceOwner=" + evidenceOwner + ", schema=" + schema + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Root> schema() {
            return new dev.openallay.value.ValueSchema<>(Root.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Root>>asList(new dev.openallay.value.ValueSchema.Component<>(Root.class, "name", Root::name), new dev.openallay.value.ValueSchema.Component<>(Root.class, "availability", Root::availability), new dev.openallay.value.ValueSchema.Component<>(Root.class, "provider", Root::provider), new dev.openallay.value.ValueSchema.Component<>(Root.class, "summary", Root::summary), new dev.openallay.value.ValueSchema.Component<>(Root.class, "evidenceOwner", Root::evidenceOwner), new dev.openallay.value.ValueSchema.Component<>(Root.class, "schema", Root::schema)), arguments -> new Root((String) arguments[0], (HostRootDescriptor.Availability) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (HostSchema) arguments[5]));
        }
    }
}
@dev.openallay.value.ValueType(Adapter.ValueSchemaProvider.class)
public static final class Adapter {
    private final String id;
    private final String provider;
    private final String summary;
    private final boolean available;
    private final HostSchema schema;
    private final String diagnostic;
    public Adapter(String id, String provider, String summary, boolean available, HostSchema schema, String diagnostic) {

            id = require(id, "id");
            provider = require(provider, "provider");
            summary = require(summary, "summary");
            diagnostic = diagnostic == null ? "" : diagnostic;

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
    public HostSchema schema() { return schema; }
    public String diagnostic() { return diagnostic; }
private static Adapter from(JavascriptDataModuleRegistry.Descriptor descriptor) {
            return new Adapter(
                    descriptor.module(),
                    descriptor.provider(),
                    descriptor.summary(),
                    descriptor.available(),
                    descriptor.schema(),
                    descriptor.diagnostic());
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Adapter)) return false;
        Adapter that = (Adapter) other;
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
    @Override public String toString() { return "Adapter[id=" + id + ", provider=" + provider + ", summary=" + summary + ", available=" + available + ", schema=" + schema + ", diagnostic=" + diagnostic + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Adapter> schema() {
            return new dev.openallay.value.ValueSchema<>(Adapter.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Adapter>>asList(new dev.openallay.value.ValueSchema.Component<>(Adapter.class, "id", Adapter::id), new dev.openallay.value.ValueSchema.Component<>(Adapter.class, "provider", Adapter::provider), new dev.openallay.value.ValueSchema.Component<>(Adapter.class, "summary", Adapter::summary), new dev.openallay.value.ValueSchema.Component<>(Adapter.class, "available", Adapter::available), new dev.openallay.value.ValueSchema.Component<>(Adapter.class, "schema", Adapter::schema), new dev.openallay.value.ValueSchema.Component<>(Adapter.class, "diagnostic", Adapter::diagnostic)), arguments -> new Adapter((String) arguments[0], (String) arguments[1], (String) arguments[2], (Boolean) arguments[3], (HostSchema) arguments[4], (String) arguments[5]));
        }
    }
}
public enum State {
        ACTIVE,
        RESTART_REQUIRED,
        INCOMPATIBLE,
        UNAVAILABLE,
        COMMUNITY
    }
@dev.openallay.value.ValueType(Notice.ValueSchemaProvider.class)
public static final class Notice {
    private final String code;
    private final String message;
    public Notice(String code, String message) {

            code = require(code, "code");
            message = require(message, "message");

        this.code = code;
        this.message = message;
    }
    public String code() { return code; }
    public String message() { return message; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Notice)) return false;
        Notice that = (Notice) other;
        return java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "Notice[code=" + code + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Notice> schema() {
            return new dev.openallay.value.ValueSchema<>(Notice.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Notice>>asList(new dev.openallay.value.ValueSchema.Component<>(Notice.class, "code", Notice::code), new dev.openallay.value.ValueSchema.Component<>(Notice.class, "message", Notice::message)), arguments -> new Notice((String) arguments[0], (String) arguments[1]));
        }
    }
}
@dev.openallay.value.ValueType(Catalog.ValueSchemaProvider.class)
public static final class Catalog {
    private final boolean configured;
    private final boolean available;
    private final Optional<Instant> generatedAt;
    private final Optional<Notice> notice;
    public Catalog(boolean configured, boolean available, Optional<Instant> generatedAt, Optional<Notice> notice) {

            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
            notice = Objects.requireNonNull(notice, "notice");
            if (!configured && (available || generatedAt.isPresent())) {
                throw new IllegalArgumentException(
                        "An unconfigured Extension catalog cannot be available");
            }
            if (available != generatedAt.isPresent()) {
                throw new IllegalArgumentException(
                        "Extension catalog availability must match its generation");
            }

        this.configured = configured;
        this.available = available;
        this.generatedAt = generatedAt;
        this.notice = notice;
    }
    public boolean configured() { return configured; }
    public boolean available() { return available; }
    public Optional<Instant> generatedAt() { return generatedAt; }
    public Optional<Notice> notice() { return notice; }
public static Catalog unavailable() {
            return new Catalog(false, false, Optional.empty(), Optional.empty());
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Catalog)) return false;
        Catalog that = (Catalog) other;
        return configured == that.configured && available == that.available && java.util.Objects.equals(generatedAt, that.generatedAt) && java.util.Objects.equals(notice, that.notice);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(configured);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + java.util.Objects.hashCode(generatedAt);
        hash = 31 * hash + java.util.Objects.hashCode(notice);
        return hash;
    }
    @Override public String toString() { return "Catalog[configured=" + configured + ", available=" + available + ", generatedAt=" + generatedAt + ", notice=" + notice + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Catalog> schema() {
            return new dev.openallay.value.ValueSchema<>(Catalog.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Catalog>>asList(new dev.openallay.value.ValueSchema.Component<>(Catalog.class, "configured", Catalog::configured), new dev.openallay.value.ValueSchema.Component<>(Catalog.class, "available", Catalog::available), new dev.openallay.value.ValueSchema.Component<>(Catalog.class, "generatedAt", Catalog::generatedAt), new dev.openallay.value.ValueSchema.Component<>(Catalog.class, "notice", Catalog::notice)), arguments -> new Catalog((Boolean) arguments[0], (Boolean) arguments[1], (Optional) arguments[2], (Optional) arguments[3]));
        }
    }
}
@dev.openallay.value.ValueType(PackageInfo.ValueSchemaProvider.class)
public static final class PackageInfo {
    private final boolean catalogListed;
    private final String availableVersion;
    private final String artifact;
    private final String sha256;
    private final boolean updateAvailable;
    private final boolean installable;
    public PackageInfo(boolean catalogListed, String availableVersion, String artifact, String sha256, boolean updateAvailable, boolean installable) {

            availableVersion = availableVersion == null ? "" : availableVersion;
            artifact = artifact == null ? "" : artifact;
            sha256 = sha256 == null ? "" : sha256;
            if (!catalogListed
                    && (!artifact.isEmpty() || updateAvailable || installable)) {
                throw new IllegalArgumentException(
                        "Local Extensions cannot expose catalog actions or artifacts");
            }
            if (catalogListed && dev.openallay.util.Java8Strings.isBlank(availableVersion)) {
                throw new IllegalArgumentException(
                        "Catalog Extensions require an available version");
            }
            if (catalogListed && dev.openallay.util.Java8Strings.isBlank(artifact) != dev.openallay.util.Java8Strings.isBlank(sha256)) {
                throw new IllegalArgumentException(
                        "Catalog Extension artifact metadata must be complete");
            }
            if (catalogListed && installable && dev.openallay.util.Java8Strings.isBlank(artifact)) {
                throw new IllegalArgumentException(
                        "Installable catalog Extensions require an artifact");
            }
            if (updateAvailable && !installable) {
                throw new IllegalArgumentException(
                        "An available Extension update must be installable");
            }
            if (!sha256.isEmpty() && !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Extension package SHA-256 must be lowercase hexadecimal");
            }
            if (!catalogListed
                    && (dev.openallay.util.Java8Strings.isBlank(availableVersion) != dev.openallay.util.Java8Strings.isBlank(sha256))) {
                throw new IllegalArgumentException(
                        "Local Extension package metadata must be complete");
            }

        this.catalogListed = catalogListed;
        this.availableVersion = availableVersion;
        this.artifact = artifact;
        this.sha256 = sha256;
        this.updateAvailable = updateAvailable;
        this.installable = installable;
    }
    public boolean catalogListed() { return catalogListed; }
    public String availableVersion() { return availableVersion; }
    public String artifact() { return artifact; }
    public String sha256() { return sha256; }
    public boolean updateAvailable() { return updateAvailable; }
    public boolean installable() { return installable; }
public static PackageInfo none() {
            return new PackageInfo(false, "", "", "", false, false);
        }
public static PackageInfo local(String version, String sha256) {
            return new PackageInfo(false, version, "", sha256, false, false);
        }
public static PackageInfo catalogOnly(String version) {
            return new PackageInfo(true, version, "", "", false, false);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PackageInfo)) return false;
        PackageInfo that = (PackageInfo) other;
        return catalogListed == that.catalogListed && java.util.Objects.equals(availableVersion, that.availableVersion) && java.util.Objects.equals(artifact, that.artifact) && java.util.Objects.equals(sha256, that.sha256) && updateAvailable == that.updateAvailable && installable == that.installable;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(catalogListed);
        hash = 31 * hash + java.util.Objects.hashCode(availableVersion);
        hash = 31 * hash + java.util.Objects.hashCode(artifact);
        hash = 31 * hash + java.util.Objects.hashCode(sha256);
        hash = 31 * hash + Boolean.hashCode(updateAvailable);
        hash = 31 * hash + Boolean.hashCode(installable);
        return hash;
    }
    @Override public String toString() { return "PackageInfo[catalogListed=" + catalogListed + ", availableVersion=" + availableVersion + ", artifact=" + artifact + ", sha256=" + sha256 + ", updateAvailable=" + updateAvailable + ", installable=" + installable + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PackageInfo> schema() {
            return new dev.openallay.value.ValueSchema<>(PackageInfo.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PackageInfo>>asList(new dev.openallay.value.ValueSchema.Component<>(PackageInfo.class, "catalogListed", PackageInfo::catalogListed), new dev.openallay.value.ValueSchema.Component<>(PackageInfo.class, "availableVersion", PackageInfo::availableVersion), new dev.openallay.value.ValueSchema.Component<>(PackageInfo.class, "artifact", PackageInfo::artifact), new dev.openallay.value.ValueSchema.Component<>(PackageInfo.class, "sha256", PackageInfo::sha256), new dev.openallay.value.ValueSchema.Component<>(PackageInfo.class, "updateAvailable", PackageInfo::updateAvailable), new dev.openallay.value.ValueSchema.Component<>(PackageInfo.class, "installable", PackageInfo::installable)), arguments -> new PackageInfo((Boolean) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (Boolean) arguments[4], (Boolean) arguments[5]));
        }
    }
}
@dev.openallay.value.ValueType(Contributions.ValueSchemaProvider.class)
public static final class Contributions {
    private final List<String> roots;
    private final List<String> dataModules;
    private final List<String> javascriptModules;
    private final List<String> skills;
    private final List<String> resultViews;
    private final List<String> hostBindings;
    public Contributions(List<String> roots, List<String> dataModules, List<String> javascriptModules, List<String> skills, List<String> resultViews, List<String> hostBindings) {

            roots = sorted(roots);
            dataModules = sorted(dataModules);
            javascriptModules = sorted(javascriptModules);
            skills = sorted(skills);
            resultViews = sorted(resultViews);
            hostBindings = sorted(hostBindings);

        this.roots = roots;
        this.dataModules = dataModules;
        this.javascriptModules = javascriptModules;
        this.skills = skills;
        this.resultViews = resultViews;
        this.hostBindings = hostBindings;
    }
    public List<String> roots() { return roots; }
    public List<String> dataModules() { return dataModules; }
    public List<String> javascriptModules() { return javascriptModules; }
    public List<String> skills() { return skills; }
    public List<String> resultViews() { return resultViews; }
    public List<String> hostBindings() { return hostBindings; }
public Contributions(
                List<String> roots, List<String> dataModules, List<String> javascriptModules,
                List<String> skills, List<String> resultViews) {
            this(roots, dataModules, javascriptModules, skills, resultViews, dev.openallay.util.Java8Collections.listOf());
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Contributions)) return false;
        Contributions that = (Contributions) other;
        return java.util.Objects.equals(roots, that.roots) && java.util.Objects.equals(dataModules, that.dataModules) && java.util.Objects.equals(javascriptModules, that.javascriptModules) && java.util.Objects.equals(skills, that.skills) && java.util.Objects.equals(resultViews, that.resultViews) && java.util.Objects.equals(hostBindings, that.hostBindings);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(roots);
        hash = 31 * hash + java.util.Objects.hashCode(dataModules);
        hash = 31 * hash + java.util.Objects.hashCode(javascriptModules);
        hash = 31 * hash + java.util.Objects.hashCode(skills);
        hash = 31 * hash + java.util.Objects.hashCode(resultViews);
        hash = 31 * hash + java.util.Objects.hashCode(hostBindings);
        return hash;
    }
    @Override public String toString() { return "Contributions[roots=" + roots + ", dataModules=" + dataModules + ", javascriptModules=" + javascriptModules + ", skills=" + skills + ", resultViews=" + resultViews + ", hostBindings=" + hostBindings + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Contributions> schema() {
            return new dev.openallay.value.ValueSchema<>(Contributions.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Contributions>>asList(new dev.openallay.value.ValueSchema.Component<>(Contributions.class, "roots", Contributions::roots), new dev.openallay.value.ValueSchema.Component<>(Contributions.class, "dataModules", Contributions::dataModules), new dev.openallay.value.ValueSchema.Component<>(Contributions.class, "javascriptModules", Contributions::javascriptModules), new dev.openallay.value.ValueSchema.Component<>(Contributions.class, "skills", Contributions::skills), new dev.openallay.value.ValueSchema.Component<>(Contributions.class, "resultViews", Contributions::resultViews), new dev.openallay.value.ValueSchema.Component<>(Contributions.class, "hostBindings", Contributions::hostBindings)), arguments -> new Contributions((List) arguments[0], (List) arguments[1], (List) arguments[2], (List) arguments[3], (List) arguments[4], (List) arguments[5]));
        }
    }
}
@dev.openallay.value.ValueType(Extension.ValueSchemaProvider.class)
public static final class Extension {
    private final String id;
    private final String name;
    private final String version;
    private final String provider;
    private final String summary;
    private final State state;
    private final List<String> loaders;
    private final String minecraftVersionRange;
    private final String openAllayApiVersionRange;
    private final String source;
    private final Contributions contributions;
    private final String diagnostic;
    private final PackageInfo packageInfo;
    private final RequirementSet requirements;
    public Extension(String id, String name, String version, String provider, String summary, State state, List<String> loaders, String minecraftVersionRange, String openAllayApiVersionRange, String source, Contributions contributions, String diagnostic, PackageInfo packageInfo, RequirementSet requirements) {

            id = require(id, "id");
            name = require(name, "name");
            version = require(version, "version");
            provider = require(provider, "provider");
            summary = require(summary, "summary");
            Objects.requireNonNull(state, "state");
            loaders = sorted(loaders);
            minecraftVersionRange = require(minecraftVersionRange, "minecraftVersionRange");
            openAllayApiVersionRange = require(
                    openAllayApiVersionRange, "openAllayApiVersionRange");
            source = require(source, "source");
            Objects.requireNonNull(contributions, "contributions");
            diagnostic = diagnostic == null ? "" : diagnostic;
            Objects.requireNonNull(packageInfo, "packageInfo");
            Objects.requireNonNull(requirements, "requirements");

        this.id = id;
        this.name = name;
        this.version = version;
        this.provider = provider;
        this.summary = summary;
        this.state = state;
        this.loaders = loaders;
        this.minecraftVersionRange = minecraftVersionRange;
        this.openAllayApiVersionRange = openAllayApiVersionRange;
        this.source = source;
        this.contributions = contributions;
        this.diagnostic = diagnostic;
        this.packageInfo = packageInfo;
        this.requirements = requirements;
    }
    public String id() { return id; }
    public String name() { return name; }
    public String version() { return version; }
    public String provider() { return provider; }
    public String summary() { return summary; }
    public State state() { return state; }
    public List<String> loaders() { return loaders; }
    public String minecraftVersionRange() { return minecraftVersionRange; }
    public String openAllayApiVersionRange() { return openAllayApiVersionRange; }
    public String source() { return source; }
    public Contributions contributions() { return contributions; }
    public String diagnostic() { return diagnostic; }
    public PackageInfo packageInfo() { return packageInfo; }
    public RequirementSet requirements() { return requirements; }
public Extension(
                String id, String name, String version, String provider, String summary,
                State state, List<String> loaders, String minecraftVersionRange,
                String openAllayApiVersionRange, String source, Contributions contributions,
                String diagnostic, PackageInfo packageInfo) {
            this(id, name, version, provider, summary, state, loaders, minecraftVersionRange,
                    openAllayApiVersionRange, source, contributions, diagnostic, packageInfo,
                    RequirementSet.EMPTY);
        }
public Extension(
                String id,
                String name,
                String version,
                String provider,
                String summary,
                State state,
                List<String> loaders,
                String minecraftVersionRange,
                String openAllayApiVersionRange,
                String source,
                Contributions contributions,
                String diagnostic) {
            this(
                    id,
                    name,
                    version,
                    provider,
                    summary,
                    state,
                    loaders,
                    minecraftVersionRange,
                    openAllayApiVersionRange,
                    source,
                    contributions,
                    diagnostic,
                    PackageInfo.none());
        }
private static Extension from(
                OpenAllayExtensionRegistry.ExtensionView extension) {
            dev.openallay.extension.OpenAllayExtensionDescriptor descriptor = extension.descriptor();
            return new Extension(
                    descriptor.id(),
                    descriptor.name(),
                    descriptor.version(),
                    descriptor.provider(),
                    descriptor.summary(),
                    State.ACTIVE,
                    dev.openallay.util.Java8Collections.toList(descriptor.loaders().stream()),
                    descriptor.minecraftVersionRange(),
                    descriptor.openAllayApiVersionRange(),
                    descriptor.source(),
                    new Contributions(
                            dev.openallay.util.Java8Collections.listOf(),
                            extension.dataModules(),
                            extension.javascriptModules(),
                            extension.skills(),
                            extension.resultViews(),
                            extension.hostBindings()),
                    extension.diagnostic(),
                    PackageInfo.none(),
                    descriptor.requirements());
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Extension)) return false;
        Extension that = (Extension) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(version, that.version) && java.util.Objects.equals(provider, that.provider) && java.util.Objects.equals(summary, that.summary) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(loaders, that.loaders) && java.util.Objects.equals(minecraftVersionRange, that.minecraftVersionRange) && java.util.Objects.equals(openAllayApiVersionRange, that.openAllayApiVersionRange) && java.util.Objects.equals(source, that.source) && java.util.Objects.equals(contributions, that.contributions) && java.util.Objects.equals(diagnostic, that.diagnostic) && java.util.Objects.equals(packageInfo, that.packageInfo) && java.util.Objects.equals(requirements, that.requirements);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(version);
        hash = 31 * hash + java.util.Objects.hashCode(provider);
        hash = 31 * hash + java.util.Objects.hashCode(summary);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(loaders);
        hash = 31 * hash + java.util.Objects.hashCode(minecraftVersionRange);
        hash = 31 * hash + java.util.Objects.hashCode(openAllayApiVersionRange);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(contributions);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        hash = 31 * hash + java.util.Objects.hashCode(packageInfo);
        hash = 31 * hash + java.util.Objects.hashCode(requirements);
        return hash;
    }
    @Override public String toString() { return "Extension[id=" + id + ", name=" + name + ", version=" + version + ", provider=" + provider + ", summary=" + summary + ", state=" + state + ", loaders=" + loaders + ", minecraftVersionRange=" + minecraftVersionRange + ", openAllayApiVersionRange=" + openAllayApiVersionRange + ", source=" + source + ", contributions=" + contributions + ", diagnostic=" + diagnostic + ", packageInfo=" + packageInfo + ", requirements=" + requirements + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Extension> schema() {
            return new dev.openallay.value.ValueSchema<>(Extension.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Extension>>asList(new dev.openallay.value.ValueSchema.Component<>(Extension.class, "id", Extension::id), new dev.openallay.value.ValueSchema.Component<>(Extension.class, "name", Extension::name), new dev.openallay.value.ValueSchema.Component<>(Extension.class, "version", Extension::version), new dev.openallay.value.ValueSchema.Component<>(Extension.class, "provider", Extension::provider), new dev.openallay.value.ValueSchema.Component<>(Extension.class, "summary", Extension::summary), new dev.openallay.value.ValueSchema.Component<>(Extension.class, "state", Extension::state), new dev.openallay.value.ValueSchema.Component<>(Extension.class, "loaders", Extension::loaders), new dev.openallay.value.ValueSchema.Component<>(Extension.class, "minecraftVersionRange", Extension::minecraftVersionRange), new dev.openallay.value.ValueSchema.Component<>(Extension.class, "openAllayApiVersionRange", Extension::openAllayApiVersionRange), new dev.openallay.value.ValueSchema.Component<>(Extension.class, "source", Extension::source), new dev.openallay.value.ValueSchema.Component<>(Extension.class, "contributions", Extension::contributions), new dev.openallay.value.ValueSchema.Component<>(Extension.class, "diagnostic", Extension::diagnostic), new dev.openallay.value.ValueSchema.Component<>(Extension.class, "packageInfo", Extension::packageInfo), new dev.openallay.value.ValueSchema.Component<>(Extension.class, "requirements", Extension::requirements)), arguments -> new Extension((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (State) arguments[5], (List) arguments[6], (String) arguments[7], (String) arguments[8], (String) arguments[9], (Contributions) arguments[10], (String) arguments[11], (PackageInfo) arguments[12], (RequirementSet) arguments[13]));
        }
    }
}
ExtensionSettingsView withExtensions(List<Extension> replacements) {
        return new ExtensionSettingsView(
                roots, bundledModules, adapters, replacements, catalog);
    }
ExtensionSettingsView withCommunity(List<Extension> replacements, Catalog replacement) {
        return new ExtensionSettingsView(
                roots, bundledModules, adapters, replacements, replacement);
    }
private static Extension core(
            List<Root> roots, List<String> modules, List<Adapter> adapters) {
        return new Extension(
                "openallay:core",
                "OpenAllay Core",
                dev.openallay.OpenAllayConstants.EXTENSION_API_VERSION,
                "OpenAllay",
                "Built-in JavaScript runtime, host roots, and typed result views.",
                State.ACTIVE,
                dev.openallay.util.Java8Collections.listOf("fabric", "neoforge"),
                "[26.2,26.3)",
                "[0.2,0.3)",
                "bundled",
                new Contributions(
                        dev.openallay.util.Java8Collections.toList(roots.stream().map(Root::name)),
                        dev.openallay.util.Java8Collections.toList(adapters.stream().map(Adapter::id)),
                        modules,
                        dev.openallay.util.Java8Collections.listOf(),
                        dev.openallay.util.Java8Collections.listOf("openallay:recipe", "openallay:item", "openallay:table")),
                "",
                PackageInfo.none());
    }
private static List<String> sorted(List<String> values) {
        return dev.openallay.util.Java8Collections.toList(dev.openallay.util.Java8Collections.listCopyOf(values).stream().sorted());
    }
private static String require(String value, String name) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionSettingsView)) return false;
        ExtensionSettingsView that = (ExtensionSettingsView) other;
        return java.util.Objects.equals(roots, that.roots) && java.util.Objects.equals(bundledModules, that.bundledModules) && java.util.Objects.equals(adapters, that.adapters) && java.util.Objects.equals(extensions, that.extensions) && java.util.Objects.equals(catalog, that.catalog);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(roots);
        hash = 31 * hash + java.util.Objects.hashCode(bundledModules);
        hash = 31 * hash + java.util.Objects.hashCode(adapters);
        hash = 31 * hash + java.util.Objects.hashCode(extensions);
        hash = 31 * hash + java.util.Objects.hashCode(catalog);
        return hash;
    }
    @Override public String toString() { return "ExtensionSettingsView[roots=" + roots + ", bundledModules=" + bundledModules + ", adapters=" + adapters + ", extensions=" + extensions + ", catalog=" + catalog + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ExtensionSettingsView> schema() {
            return new dev.openallay.value.ValueSchema<>(ExtensionSettingsView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ExtensionSettingsView>>asList(new dev.openallay.value.ValueSchema.Component<>(ExtensionSettingsView.class, "roots", ExtensionSettingsView::roots), new dev.openallay.value.ValueSchema.Component<>(ExtensionSettingsView.class, "bundledModules", ExtensionSettingsView::bundledModules), new dev.openallay.value.ValueSchema.Component<>(ExtensionSettingsView.class, "adapters", ExtensionSettingsView::adapters), new dev.openallay.value.ValueSchema.Component<>(ExtensionSettingsView.class, "extensions", ExtensionSettingsView::extensions), new dev.openallay.value.ValueSchema.Component<>(ExtensionSettingsView.class, "catalog", ExtensionSettingsView::catalog)), arguments -> new ExtensionSettingsView((List) arguments[0], (List) arguments[1], (List) arguments[2], (List) arguments[3], (Catalog) arguments[4]));
        }
    }
}
