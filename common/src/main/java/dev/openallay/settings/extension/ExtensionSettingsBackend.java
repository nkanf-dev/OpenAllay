package dev.openallay.settings.extension;

import dev.openallay.extension.OpenAllayExtensionDescriptor;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.extension.ExtensionCapability;
import dev.openallay.extension.ExtensionCapabilityPolicy;
import dev.openallay.extension.ExtensionCapabilityPolicyStore;
import dev.openallay.extension.catalog.ExtensionCatalogClient;
import dev.openallay.extension.catalog.ExtensionCatalogCodec;
import dev.openallay.extension.catalog.ExtensionCatalogEntry;
import dev.openallay.extension.catalog.ExtensionCatalogManifest;
import dev.openallay.extension.install.ExtensionPackageInstaller;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.settings.ClientSettingsService;
import dev.openallay.settings.requirement.PreparedPackageInstall;
import dev.openallay.settings.requirement.RefreshingPreparedPackageInstall;
import dev.openallay.extension.install.PreparedExtensionInstall;
import dev.openallay.tool.ToolResult;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/** Non-UI owner for last-valid catalogs and restart-required Extension staging. */
public final class ExtensionSettingsBackend implements ClientSettingsService.ExtensionActions {
    public static final URI DEFAULT_CATALOG_URI = URI.create(
            "https://raw.githubusercontent.com/nkanf-dev/OpenAllay-Extensions/main/catalog.json");

    private final OpenAllayExtensionRegistry registry;
    private final JavascriptDataModuleRegistry dataModules;
    private final ExtensionCatalogCodec codec;
    private final ExtensionPackageInstaller installer;
    private final ExtensionCatalogClient catalogClient;
    private final ExtensionCapabilityPolicyStore capabilityStore;
    private ExtensionCatalogManifest catalog =
            new ExtensionCatalogManifest(
                    ExtensionCatalogManifest.SCHEMA_VERSION,
                    "extension",
                    java.time.Instant.EPOCH,
                    List.of());
    private final Map<String, StagedPackage> staged = new TreeMap<>();
    private Optional<ExtensionSettingsView.Notice> notice = Optional.empty();

    /**
     * Creates the production backend.
     *
     * @param configDirectory OpenAllay's own configuration directory
     * @param managedModsRoot the loader-visible mods directory; managed package names are stable
     */
    public ExtensionSettingsBackend(
            Path configDirectory,
            Path managedModsRoot,
            OpenAllayExtensionRegistry registry,
            JavascriptDataModuleRegistry dataModules) {
        this(
                registry,
                dataModules,
                new ExtensionCatalogCodec(),
                new ExtensionPackageInstaller(registry.environment(), managedModsRoot),
                defaultCatalog(configDirectory),
                new ExtensionCapabilityPolicyStore(configDirectory.resolve("extension-capabilities.json")));
    }

    /** Constructor retained for deterministic backend/installer contract tests. */
    public ExtensionSettingsBackend(
            OpenAllayExtensionRegistry registry,
            JavascriptDataModuleRegistry dataModules,
            ExtensionCatalogCodec codec,
            ExtensionPackageInstaller installer) {
        this(registry, dataModules, codec, installer, null);
    }

    ExtensionSettingsBackend(
            OpenAllayExtensionRegistry registry,
            JavascriptDataModuleRegistry dataModules,
            ExtensionCatalogCodec codec,
            ExtensionPackageInstaller installer,
            ExtensionCatalogClient catalogClient) {
        this(registry, dataModules, codec, installer, catalogClient, null);
    }

    ExtensionSettingsBackend(
            OpenAllayExtensionRegistry registry,
            JavascriptDataModuleRegistry dataModules,
            ExtensionCatalogCodec codec,
            ExtensionPackageInstaller installer,
            ExtensionCatalogClient catalogClient,
            ExtensionCapabilityPolicyStore capabilityStore) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.dataModules = Objects.requireNonNull(dataModules, "dataModules");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.installer = Objects.requireNonNull(installer, "installer");
        this.catalogClient = catalogClient;
        this.capabilityStore = capabilityStore;
        if (capabilityStore != null) {
            ToolResult<ExtensionCapabilityPolicy> loaded = capabilityStore.load();
            registry.replaceCapabilityPolicy(declaredPolicy(capabilityStore.current()));
            if (loaded instanceof ToolResult.Failure<ExtensionCapabilityPolicy> failure) {
                notice = Optional.of(new ExtensionSettingsView.Notice(failure.code(), failure.message()));
            }
        }
        if (catalogClient != null && catalogClient.current().isPresent()) {
            catalog = catalogClient.current().orElseThrow();
        }
    }

    @Override
    public synchronized ToolResult<ExtensionSettingsView> saveCapability(
            String extensionId, String capabilityId, boolean enabled) {
        var declarations = registry.capabilityDescriptors();
        if (!declarations.containsKey(extensionId)
                || declarations.get(extensionId).stream()
                        .noneMatch(capability -> capability.id().equals(capabilityId))) {
            return new ToolResult.Failure<>(
                    "unknown_extension_capability",
                    "The Extension capability is not declared by an active Extension");
        }
        if (capabilityStore == null) {
            return new ToolResult.Failure<>(
                    "settings_unavailable", "Extension capability settings are unavailable");
        }
        ExtensionCapabilityPolicy candidate =
                declaredPolicy(registry.capabilityPolicy()).withGrant(extensionId, capabilityId, enabled);
        ToolResult<ExtensionCapabilityPolicy> saved = capabilityStore.save(candidate);
        if (saved instanceof ToolResult.Failure<ExtensionCapabilityPolicy> failure) {
            return new ToolResult.Failure<>(failure.code(), failure.message());
        }
        registry.replaceCapabilityPolicy(((ToolResult.Success<ExtensionCapabilityPolicy>) saved).value());
        notice = Optional.empty();
        return new ToolResult.Success<>(currentView());
    }

    private ExtensionCapabilityPolicy declaredPolicy(ExtensionCapabilityPolicy policy) {
        Map<String, Set<String>> grants = new TreeMap<>();
        registry.capabilityDescriptors().forEach((extensionId, capabilities) -> {
            Set<String> enabled = capabilities.stream()
                    .map(ExtensionCapability::id)
                    .filter(capabilityId -> policy.allows(extensionId, capabilityId))
                    .collect(java.util.stream.Collectors.toSet());
            if (!enabled.isEmpty()) grants.put(extensionId, enabled);
        });
        return new ExtensionCapabilityPolicy(grants);
    }

    @Override
    public synchronized ExtensionSettingsView currentView() {
        ExtensionSettingsView base = ExtensionSettingsView.from(dataModules, registry);
        Map<String, ExtensionSettingsView.Extension> extensions = new TreeMap<>();
        base.extensions().forEach(extension -> extensions.put(extension.id(), extension));

        Map<String, ExtensionCatalogEntry> latest = latestEntries();
        latest.forEach((id, entry) -> {
            ExtensionSettingsView.Extension installed = extensions.get(id);
            StagedPackage pending = staged.get(id);
            if (pending != null) {
                extensions.put(id, staged(installed, pending));
            } else if (installed != null) {
                extensions.put(
                        id,
                        installed(
                                installed,
                                entry,
                                registry.environment()
                                        .incompatibility(entry.descriptor())
                                        .isEmpty()));
            } else {
                extensions.put(id, community(entry));
            }
        });
        staged.forEach((id, stagedPackage) -> extensions.put(
                id, staged(extensions.get(id), stagedPackage)));

        ExtensionSettingsView.Catalog catalogView = new ExtensionSettingsView.Catalog(
                catalogClient != null || hasCatalog(),
                hasCatalog(),
                hasCatalog() ? Optional.of(catalog.generatedAt()) : Optional.empty(),
                notice);
        return base.withCommunity(List.copyOf(extensions.values()), catalogView);
    }

    /**
     * Test/development injection. Production refreshes use the HTTPS catalog client.
     *
     * <p>A decoding failure retains the prior generation.
     */
    public synchronized ToolResult<ExtensionSettingsView> replaceCatalog(String json) {
        try {
            catalog = codec.decode(json);
            notice = Optional.empty();
            return new ToolResult.Success<>(currentView());
        } catch (RuntimeException failure) {
            notice = Optional.of(new ExtensionSettingsView.Notice(
                    "catalog_refresh_failed",
                    "The Extension community catalog could not be validated"));
            return new ToolResult.Failure<>(
                    "catalog_refresh_failed",
                    "The Extension community catalog could not be validated");
        }
    }

    @Override
    public CompletableFuture<ToolResult<ExtensionSettingsView>> refreshCommunity(
            CancellationSignal cancellation) {
        if (catalogClient == null) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "catalog_unavailable",
                    "The Extension community catalog is not configured"));
        }
        return catalogClient.refresh(cancellation).thenApply(result -> {
            synchronized (this) {
                if (result instanceof ToolResult.Success<ExtensionCatalogManifest> success) {
                    catalog = success.value();
                    notice = Optional.empty();
                    return new ToolResult.Success<>(currentView());
                }
                ToolResult.Failure<ExtensionCatalogManifest> failure =
                        (ToolResult.Failure<ExtensionCatalogManifest>) result;
                notice = Optional.of(
                        new ExtensionSettingsView.Notice(failure.code(), failure.message()));
                return new ToolResult.Failure<ExtensionSettingsView>(
                        failure.code(), failure.message());
            }
        });
    }

    @Override
    public ToolResult<PreparedPackageInstall> prepareLocalPackage(String extensionId, Path source) {
        ExtensionCatalogEntry entry;
        synchronized (this) {
            entry = entry(extensionId);
        }
        return entry == null ? prepareLocalPackage(source)
                : refreshing(Optional.of(entry), installer.prepareLocal(entry, source));
    }

    @Override
    public ToolResult<PreparedPackageInstall> prepareLocalPackage(Path source) {
        return refreshing(Optional.empty(), installer.prepareLocal(source));
    }

    @Override
    public CompletableFuture<ToolResult<PreparedPackageInstall>> prepareCommunity(
            String extensionId, CancellationSignal cancellation) {
        ExtensionCatalogEntry entry;
        synchronized (this) {
            entry = entry(extensionId);
        }
        if (entry == null) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "extension_package_not_found", "The selected community Extension is unavailable"));
        }
        return installer.prepareDownload(entry, cancellation)
                .thenApply(result -> refreshing(Optional.of(entry), result));
    }

    private ToolResult<PreparedPackageInstall> refreshing(
            Optional<ExtensionCatalogEntry> entry, ToolResult<PreparedExtensionInstall> result) {
        if (result instanceof ToolResult.Failure<PreparedExtensionInstall> failure) {
            return new ToolResult.Failure<>(failure.code(), failure.message());
        }
        PreparedExtensionInstall candidate = ((ToolResult.Success<PreparedExtensionInstall>) result).value();
        return new ToolResult.Success<>(new RefreshingPreparedPackageInstall(candidate, this, () -> {
            var descriptor = candidate.manifest().descriptor();
            staged.put(descriptor.id(), new StagedPackage(descriptor, entry, candidate.sha256()));
            notice = Optional.empty();
        }));
    }

    @Override
    public ToolResult<ExtensionSettingsView> importLocalPackage(String extensionId, Path source) {
        return commitPrepared(prepareLocalPackage(extensionId, source));
    }

    @Override
    public ToolResult<ExtensionSettingsView> importLocalPackage(Path source) {
        return commitPrepared(prepareLocalPackage(source));
    }

    @Override
    public CompletableFuture<ToolResult<ExtensionSettingsView>> installCommunity(
            String extensionId, CancellationSignal cancellation) {
        return prepareCommunity(extensionId, cancellation).thenApply(this::commitPrepared);
    }

    private ToolResult<ExtensionSettingsView> commitPrepared(ToolResult<PreparedPackageInstall> result) {
        if (result instanceof ToolResult.Failure<PreparedPackageInstall> failure) {
            return new ToolResult.Failure<>(failure.code(), failure.message());
        }
        try (PreparedPackageInstall candidate = ((ToolResult.Success<PreparedPackageInstall>) result).value()) {
            ToolResult<Boolean> committed = candidate.commit();
            if (committed instanceof ToolResult.Failure<Boolean> failure) {
                return new ToolResult.Failure<>(failure.code(), failure.message());
            }
            return new ToolResult.Success<>(currentView());
        }
    }

    private Map<String, ExtensionCatalogEntry> latestEntries() {
        Map<String, ExtensionCatalogEntry> latest = new TreeMap<>();
        for (ExtensionCatalogEntry entry : catalog.extensions()) {
            ExtensionCatalogEntry prior = latest.get(entry.id());
            if (prior == null || compareVersions(entry.version(), prior.version()) > 0) {
                latest.put(entry.id(), entry);
            }
        }
        return latest;
    }

    private ExtensionCatalogEntry entry(String id) {
        return latestEntries().get(id);
    }

    private ExtensionSettingsView.Extension community(ExtensionCatalogEntry entry) {
        String incompatibility = registry.environment().incompatibility(entry.descriptor());
        return extension(
                entry,
                incompatibility.isEmpty()
                        ? ExtensionSettingsView.State.COMMUNITY
                        : ExtensionSettingsView.State.INCOMPATIBLE,
                incompatibility,
                "",
                incompatibility.isEmpty(),
                false,
                emptyContributions());
    }

    private ExtensionSettingsView.Extension installed(
            ExtensionSettingsView.Extension installed,
            ExtensionCatalogEntry available,
            boolean compatible) {
        boolean update = compatible
                && compareVersions(available.version(), installed.version()) > 0;
        return new ExtensionSettingsView.Extension(
                installed.id(),
                installed.name(),
                installed.version(),
                installed.provider(),
                installed.summary(),
                installed.state(),
                installed.loaders(),
                installed.minecraftVersionRange(),
                installed.openAllayApiVersionRange(),
                installed.source(),
                installed.contributions(),
                installed.diagnostic(),
                packageInfo(available, update, update),
                installed.requirements(),
                installed.capabilities());
    }

    private ExtensionSettingsView.Extension staged(
            ExtensionSettingsView.Extension installed, StagedPackage staged) {
        ExtensionSettingsView.Contributions contributions = installed == null
                ? emptyContributions()
                : installed.contributions();
        OpenAllayExtensionDescriptor descriptor = staged.descriptor();
        String displayVersion =
                installed == null ? descriptor.version() : installed.version();
        return new ExtensionSettingsView.Extension(
                descriptor.id(),
                descriptor.name(),
                displayVersion,
                descriptor.provider(),
                descriptor.summary(),
                ExtensionSettingsView.State.RESTART_REQUIRED,
                descriptor.loaders().stream().toList(),
                descriptor.minecraftVersionRange(),
                descriptor.openAllayApiVersionRange(),
                descriptor.source(),
                contributions,
                "restart_required",
                staged.catalogEntry()
                        .map(entry -> packageInfo(entry, false, false))
                        .orElseGet(() -> ExtensionSettingsView.PackageInfo.local(
                                descriptor.version(), staged.sha256())),
                descriptor.requirements(),
                installed == null ? List.of() : installed.capabilities());
    }

    private ExtensionSettingsView.Extension extension(
            ExtensionCatalogEntry entry,
            ExtensionSettingsView.State state,
            String diagnostic,
            String installedVersion,
            boolean installable,
            boolean updateAvailable,
            ExtensionSettingsView.Contributions contributions) {
        String displayVersion =
                installedVersion.isBlank() ? entry.version() : installedVersion;
        return new ExtensionSettingsView.Extension(
                entry.id(),
                entry.name(),
                displayVersion,
                entry.provider(),
                entry.summary(),
                state,
                entry.loaders().stream().toList(),
                entry.minecraftVersionRange(),
                entry.openAllayApiVersionRange(),
                entry.source(),
                contributions,
                diagnostic,
                packageInfo(entry, updateAvailable, installable),
                entry.requirements());
    }

    private ExtensionSettingsView.PackageInfo packageInfo(
            ExtensionCatalogEntry entry, boolean updateAvailable, boolean installable) {
        return entry.artifactFor(registry.environment().loader())
                .map(artifact -> new ExtensionSettingsView.PackageInfo(
                        true,
                        entry.version(),
                        artifact.artifact().toString(),
                        artifact.sha256(),
                        updateAvailable,
                        installable))
                .orElseGet(() ->
                        ExtensionSettingsView.PackageInfo.catalogOnly(entry.version()));
    }

    private static ExtensionSettingsView.Contributions emptyContributions() {
        return new ExtensionSettingsView.Contributions(
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private boolean hasCatalog() {
        return !catalog.generatedAt().equals(java.time.Instant.EPOCH);
    }

    private static ExtensionCatalogClient defaultCatalog(Path configDirectory) {
        Path config = Objects.requireNonNull(configDirectory, "configDirectory")
                .toAbsolutePath()
                .normalize();
        return new ExtensionCatalogClient(
                DEFAULT_CATALOG_URI,
                config.resolve("catalogs").resolve("extensions.json"),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30));
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[.-]");
        String[] rightParts = right.split("[.-]");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            String a = index < leftParts.length ? leftParts[index] : "0";
            String b = index < rightParts.length ? rightParts[index] : "0";
            int comparison;
            if (a.chars().allMatch(Character::isDigit)
                    && b.chars().allMatch(Character::isDigit)) {
                comparison =
                        new java.math.BigInteger(a).compareTo(new java.math.BigInteger(b));
            } else {
                comparison = a.compareTo(b);
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private record StagedPackage(
            OpenAllayExtensionDescriptor descriptor,
            Optional<ExtensionCatalogEntry> catalogEntry,
            String sha256) {
        private StagedPackage {
            Objects.requireNonNull(descriptor, "descriptor");
            catalogEntry = Objects.requireNonNull(catalogEntry, "catalogEntry");
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "A staged Extension requires a SHA-256 digest");
            }
        }
    }
}
