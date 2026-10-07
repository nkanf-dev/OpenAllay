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
public record ExtensionSettingsView(
        List<Root> roots,
        List<String> bundledModules,
        List<Adapter> adapters,
        List<Extension> extensions,
        Catalog catalog) {
    public ExtensionSettingsView {
        roots = List.copyOf(roots);
        bundledModules = List.copyOf(bundledModules);
        adapters = List.copyOf(adapters);
        extensions = List.copyOf(extensions).stream()
                .sorted(Comparator.comparing(Extension::id))
                .toList();
        Objects.requireNonNull(catalog, "catalog");
    }

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
        List<Root> roots = catalog.list().stream()
                .map(summary -> Root.from(catalog, summary))
                .sorted(Comparator.comparing(Root::name))
                .toList();
        List<String> modules = JavascriptModuleCatalog.bundledIds().stream().sorted().toList();
        List<Adapter> adapters = registry.descriptors().stream()
                .map(Adapter::from)
                .sorted(Comparator.comparing(Adapter::id))
                .toList();
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

    public record Root(
            String name,
            HostRootDescriptor.Availability availability,
            String provider,
            String summary,
            String evidenceOwner,
            HostSchema schema) {
        public Root {
            name = require(name, "name");
            Objects.requireNonNull(availability, "availability");
            provider = require(provider, "provider");
            summary = require(summary, "summary");
            evidenceOwner = require(evidenceOwner, "evidenceOwner");
            Objects.requireNonNull(schema, "schema");
        }

        private static Root from(
                HostSchemaCatalog catalog, HostSchemaCatalog.RootSummary summary) {
            HostSchema schema = catalog.describe(summary.name())
                    .orElseThrow()
                    .schema();
            return new Root(
                    summary.name(),
                    summary.availability(),
                    summary.provider(),
                    summary.summary(),
                    summary.evidenceOwner(),
                    schema);
        }
    }

    public record Adapter(
            String id,
            String provider,
            String summary,
            boolean available,
            HostSchema schema,
            String diagnostic) {
        public Adapter {
            id = require(id, "id");
            provider = require(provider, "provider");
            summary = require(summary, "summary");
            diagnostic = diagnostic == null ? "" : diagnostic;
        }

        private static Adapter from(JavascriptDataModuleRegistry.Descriptor descriptor) {
            return new Adapter(
                    descriptor.module(),
                    descriptor.provider(),
                    descriptor.summary(),
                    descriptor.available(),
                    descriptor.schema(),
                    descriptor.diagnostic());
        }
    }

    public enum State {
        ACTIVE,
        RESTART_REQUIRED,
        INCOMPATIBLE,
        UNAVAILABLE,
        COMMUNITY
    }

    public record Notice(String code, String message) {
        public Notice {
            code = require(code, "code");
            message = require(message, "message");
        }
    }

    public record Catalog(
            boolean configured,
            boolean available,
            Optional<Instant> generatedAt,
            Optional<Notice> notice) {
        public Catalog {
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
        }

        public static Catalog unavailable() {
            return new Catalog(false, false, Optional.empty(), Optional.empty());
        }
    }

    public record PackageInfo(
            boolean catalogListed,
            String availableVersion,
            String artifact,
            String sha256,
            boolean updateAvailable,
            boolean installable) {
        public PackageInfo {
            availableVersion = availableVersion == null ? "" : availableVersion;
            artifact = artifact == null ? "" : artifact;
            sha256 = sha256 == null ? "" : sha256;
            if (!catalogListed
                    && (!artifact.isEmpty() || updateAvailable || installable)) {
                throw new IllegalArgumentException(
                        "Local Extensions cannot expose catalog actions or artifacts");
            }
            if (catalogListed && availableVersion.isBlank()) {
                throw new IllegalArgumentException(
                        "Catalog Extensions require an available version");
            }
            if (catalogListed && artifact.isBlank() != sha256.isBlank()) {
                throw new IllegalArgumentException(
                        "Catalog Extension artifact metadata must be complete");
            }
            if (catalogListed && installable && artifact.isBlank()) {
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
                    && (availableVersion.isBlank() != sha256.isBlank())) {
                throw new IllegalArgumentException(
                        "Local Extension package metadata must be complete");
            }
        }

        public static PackageInfo none() {
            return new PackageInfo(false, "", "", "", false, false);
        }

        public static PackageInfo local(String version, String sha256) {
            return new PackageInfo(false, version, "", sha256, false, false);
        }

        public static PackageInfo catalogOnly(String version) {
            return new PackageInfo(true, version, "", "", false, false);
        }
    }

    public record Contributions(
            List<String> roots,
            List<String> dataModules,
            List<String> javascriptModules,
            List<String> skills,
            List<String> resultViews,
            List<String> hostBindings) {
        public Contributions(
                List<String> roots, List<String> dataModules, List<String> javascriptModules,
                List<String> skills, List<String> resultViews) {
            this(roots, dataModules, javascriptModules, skills, resultViews, List.of());
        }

        public Contributions {
            roots = sorted(roots);
            dataModules = sorted(dataModules);
            javascriptModules = sorted(javascriptModules);
            skills = sorted(skills);
            resultViews = sorted(resultViews);
            hostBindings = sorted(hostBindings);
        }
    }

    public record Extension(
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
            String diagnostic,
            PackageInfo packageInfo,
            RequirementSet requirements) {
        public Extension {
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
        }

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
                    descriptor.loaders().stream().toList(),
                    descriptor.minecraftVersionRange(),
                    descriptor.openAllayApiVersionRange(),
                    descriptor.source(),
                    new Contributions(
                            List.of(),
                            extension.dataModules(),
                            extension.javascriptModules(),
                            extension.skills(),
                            extension.resultViews(),
                            extension.hostBindings()),
                    extension.diagnostic(),
                    PackageInfo.none(),
                    descriptor.requirements());
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
                List.of("fabric", "neoforge"),
                "[26.2,26.3)",
                "[0.2,0.3)",
                "bundled",
                new Contributions(
                        roots.stream().map(Root::name).toList(),
                        adapters.stream().map(Adapter::id).toList(),
                        modules,
                        List.of(),
                        List.of("openallay:recipe", "openallay:item", "openallay:table")),
                "",
                PackageInfo.none());
    }

    private static List<String> sorted(List<String> values) {
        return List.copyOf(values).stream().sorted().toList();
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
