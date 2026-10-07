package dev.openallay.settings.skill;

import dev.openallay.OpenAllayConstants;
import dev.openallay.community.CommunityCatalogClient;
import dev.openallay.community.CommunityCatalogManifest;
import dev.openallay.model.CancellationSignal;
import dev.openallay.skill.BundledSkillLoader;
import dev.openallay.skill.FilesystemSkillLoader;
import dev.openallay.skill.SkillDocument;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.skill.SkillSettingsStore;
import dev.openallay.skill.SkillSource;
import dev.openallay.skill.install.SkillPackageInstaller;
import dev.openallay.settings.ClientSettingsService;
import dev.openallay.settings.requirement.PreparedPackageInstall;
import dev.openallay.settings.requirement.RefreshingPreparedPackageInstall;
import dev.openallay.skill.install.PreparedSkillInstall;
import dev.openallay.tool.ToolResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Player-owned Skill document editing outside the Agent tool surface. */
public final class SkillSettingsBackend implements ClientSettingsService.SkillActions {
    public static final URI DEFAULT_CATALOG_URI = URI.create(
            "https://raw.githubusercontent.com/nkanf-dev/OpenAllay-Skills/main/catalog.json");
    private final SkillRepository repository;
    private final SkillParser parser;
    private final List<SkillSource> bundledSources;
    private final Path localRoot;
    private final Set<String> installedMods;
    private final FilesystemSkillLoader localLoader;
    private final SkillSettingsStore store;
    private final CommunityCatalogClient communityCatalog;
    private final SkillPackageInstaller installer;
    private final String minecraftVersion;
    private volatile SkillSettingsView current = SkillSettingsView.empty();
    private volatile SkillCommunityView community = SkillCommunityView.unavailable();

    public SkillSettingsBackend(
            Path localRoot,
            SkillRepository repository,
            Set<String> installedMods,
            String minecraftVersion) {
        this(
                localRoot,
                repository,
                new SkillParser(),
                new BundledSkillLoader().load(),
                installedMods,
                new FilesystemSkillLoader(),
                defaultCatalog(localRoot),
                new SkillPackageInstaller(localRoot, new SkillParser(), minecraftVersion, installedMods),
                minecraftVersion);
    }

    public SkillSettingsBackend(
            Path localRoot,
            SkillRepository repository,
            SkillParser parser,
            Collection<SkillSource> bundledSources,
            Set<String> installedMods,
            String minecraftVersion) {
        this(
                localRoot,
                repository,
                parser,
                bundledSources,
                installedMods,
                new FilesystemSkillLoader(),
                null,
                new SkillPackageInstaller(localRoot, parser, minecraftVersion, installedMods),
                minecraftVersion);
    }

    SkillSettingsBackend(
            Path localRoot,
            SkillRepository repository,
            SkillParser parser,
            Collection<SkillSource> bundledSources,
            Set<String> installedMods,
            FilesystemSkillLoader localLoader,
            String minecraftVersion) {
        this(
                localRoot,
                repository,
                parser,
                bundledSources,
                installedMods,
                localLoader,
                null,
                new SkillPackageInstaller(localRoot, parser, minecraftVersion, installedMods),
                minecraftVersion);
    }

    SkillSettingsBackend(
            Path localRoot,
            SkillRepository repository,
            SkillParser parser,
            Collection<SkillSource> bundledSources,
            Set<String> installedMods,
            FilesystemSkillLoader localLoader,
            CommunityCatalogClient communityCatalog,
            SkillPackageInstaller installer,
            String minecraftVersion) {
        this.localRoot = Objects.requireNonNull(localRoot, "localRoot")
                .toAbsolutePath()
                .normalize();
        this.repository = Objects.requireNonNull(repository, "repository");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.bundledSources = dev.openallay.util.Java8Collections.listCopyOf(bundledSources);
        this.installedMods = dev.openallay.util.Java8Collections.setCopyOf(installedMods);
        this.localLoader = Objects.requireNonNull(localLoader, "localLoader");
        this.store = new SkillSettingsStore(this.localRoot, parser);
        this.communityCatalog = communityCatalog;
        this.installer = Objects.requireNonNull(installer, "installer");
        if (minecraftVersion == null || dev.openallay.util.Java8Strings.isBlank(minecraftVersion)) {
            throw new IllegalArgumentException("minecraftVersion must not be blank");
        }
        this.minecraftVersion = minecraftVersion;
        reloadInternal();
        community = buildCommunity(Optional.empty());
    }

    @Override
    public SkillSettingsView currentView() {
        return current;
    }

    public SkillCommunityView currentCommunityView() {
        return community;
    }

    @Override
    public SkillCommunityView communityView() {
        return community;
    }

    @Override
    public CompletableFuture<ToolResult<SkillCommunityView>> refreshCommunity(
            CancellationSignal cancellation) {
        if (communityCatalog == null) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "catalog_unavailable", "The Skill community catalog is not configured"));
        }
        return communityCatalog.refresh(cancellation).thenApply(result -> {
            if (result instanceof ToolResult.Success<CommunityCatalogManifest>) {
                community = buildCommunity(Optional.empty());
                return new ToolResult.Success<>(community);
            }
            ToolResult.Failure<CommunityCatalogManifest> failure =
                    (ToolResult.Failure<CommunityCatalogManifest>) result;
            community = buildCommunity(Optional.of(
                    new SkillCommunityView.Notice(failure.code(), failure.message())));
            return new ToolResult.Failure<>(failure.code(), failure.message());
        });
    }

    @Override
    public CompletableFuture<ToolResult<PreparedPackageInstall>> prepareCommunity(
            String id, CancellationSignal cancellation) {
        if (communityCatalog == null) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "catalog_unavailable", "The Skill community catalog is not configured"));
        }
        CommunityCatalogManifest.PackageEntry entry = communityCatalog.current()
                .flatMap(catalog -> catalog.packages().stream()
                        .filter(candidate -> candidate.id().equals(id))
                        .filter(this::compatible)
                        .max((left, right) -> compareVersions(left.version(), right.version())))
                .orElse(null);
        if (entry == null) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "skill_package_not_found", "The selected community Skill is unavailable"));
        }
        return installer.prepare(entry, cancellation).thenApply(this::refreshing);
    }

    @Override
    public ToolResult<PreparedPackageInstall> prepareLocalPackage(Path source) {
        return refreshing(installer.prepareLocal(source));
    }

    private ToolResult<PreparedPackageInstall> refreshing(ToolResult<PreparedSkillInstall> result) {
        final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.skill.install.PreparedSkillInstall> value; ToolResult.Failure<PreparedSkillInstall> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = result) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern0_holder.bound = (ToolResult.Failure<PreparedSkillInstall>) $oaPattern0_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern0_holder.bound.code(), $oaPattern0_holder.bound.message());
        }
        PreparedSkillInstall candidate = ((ToolResult.Success<PreparedSkillInstall>) result).value();
        return new ToolResult.Success<>(new RefreshingPreparedPackageInstall(candidate, this, () -> {
            reloadInternal();
            community = buildCommunity(Optional.empty());
        }));
    }

    @Override
    public CompletableFuture<ToolResult<SkillCommunityView>> installCommunity(
            String id, CancellationSignal cancellation) {
        return prepareCommunity(id, cancellation).thenApply(this::commitPrepared);
    }

    @Override
    public ToolResult<SkillCommunityView> importLocalPackage(Path source) {
        return commitPrepared(prepareLocalPackage(source));
    }

    private ToolResult<SkillCommunityView> commitPrepared(ToolResult<PreparedPackageInstall> result) {
        final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<dev.openallay.settings.requirement.PreparedPackageInstall> value; ToolResult.Failure<PreparedPackageInstall> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = result) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern1_holder.bound = (ToolResult.Failure<PreparedPackageInstall>) $oaPattern1_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern1_holder.bound.code(), $oaPattern1_holder.bound.message());
        }
        try (PreparedPackageInstall candidate = ((ToolResult.Success<PreparedPackageInstall>) result).value()) {
            ToolResult<Boolean> committed = candidate.commit();
            final class $oaPattern2_Holder { dev.openallay.tool.ToolResult<java.lang.Boolean> value; ToolResult.Failure<Boolean> bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = committed) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern2_holder.bound = (ToolResult.Failure<Boolean>) $oaPattern2_holder.value) != null))) {
                return new ToolResult.Failure<>($oaPattern2_holder.bound.code(), $oaPattern2_holder.bound.message());
            }
            return new ToolResult.Success<>(community);
        }
    }

    @Override
    public synchronized ToolResult<SkillSettingsView> reloadSkills() {
        try {
            reloadInternal();
            return new ToolResult.Success<>(current);
        } catch (RuntimeException failure) {
            return failure("skill_reload_failed", "Unable to reload Skills", failure);
        }
    }

    /** Saves a local override; a bundled selection is copied before its entry is replaced. */
    @Override
    public synchronized ToolResult<SkillSettingsView> saveOverride(String name, String markdown) {
        SkillSettingsView.Skill selected = current.find(name).orElse(null);
        if (selected == null) {
            return new ToolResult.Failure<>("skill_not_found", "The selected Skill is unavailable");
        }
        if (markdown == null || dev.openallay.util.Java8Strings.isBlank(markdown)) {
            return new ToolResult.Failure<>("skill_override_invalid", "Skill Markdown must not be blank");
        }

        FilesystemSkillLoader.LoadResult local = localLoader.load(localRoot);
        SkillSource previousLocal = sourceNamed(local.sources(), name);
        boolean hadOverride = store.hasOverride(name);
        if (hadOverride && previousLocal == null) {
            return new ToolResult.Failure<>(
                    "skill_override_invalid",
                    "The existing local override is invalid; delete it before creating a replacement");
        }
        SkillSource base = previousLocal != null ? previousLocal : bundledNamed(name);
        if (base == null) {
            return new ToolResult.Failure<>(
                    "skill_override_unavailable", "The selected Skill cannot be overridden");
        }

        SkillDocument candidate;
        try {
            candidate = parser.parse(candidateSource(base, markdown));
        } catch (RuntimeException failure) {
            return failure("skill_override_invalid", "The Skill override is invalid", failure);
        }

        String previousMarkdown = previousLocal == null ? null : entryMarkdown(previousLocal);
        boolean created = false;
        try {
            if (!hadOverride) {
                SkillSource bundled = bundledNamed(name);
                if (bundled == null) {
                    return new ToolResult.Failure<>(
                            "skill_override_unavailable", "The selected Skill cannot be overridden");
                }
                // The existing copy writer accepts bundled-origin immutable packages. Extension
                // sources receive that copy-only label; their registered source is never changed.
                SkillSource copySource = bundled.origin() == SkillSource.Origin.EXTERNAL
                        ? new SkillSource(bundled.provenance(), bundled.entryPath(), bundled.files(),
                                SkillSource.Origin.BUNDLED)
                        : bundled;
                store.createOverride(copySource);
                created = true;
            }
            store.editOverride(name, markdown);
            reloadInternal();
            SkillDocument published = repository.find(name).orElse(null);
            if (!sameContent(candidate, published)
                    || published.metadata().origin() != SkillSource.Origin.LOCAL) {
                rollback(name, created, previousMarkdown);
                reloadInternal();
                return new ToolResult.Failure<>(
                        "skill_override_dependency_unavailable",
                        "The Skill override requires unavailable Tools or mods");
            }
            return new ToolResult.Success<>(current);
        } catch (RuntimeException failure) {
            try {
                rollback(name, created, previousMarkdown);
                reloadInternal();
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            return failure("skill_override_save_failed", "Unable to save the Skill override", failure);
        }
    }

    @Override
    public synchronized ToolResult<SkillSettingsView> deleteOverride(String name) {
        try {
            if (!store.hasOverride(name)) {
                return new ToolResult.Failure<>(
                        "skill_override_not_found", "The selected Skill has no local override");
            }
            store.deleteOverride(name);
            reloadInternal();
            return new ToolResult.Success<>(current);
        } catch (RuntimeException failure) {
            return failure("skill_override_delete_failed", "Unable to delete the Skill override", failure);
        }
    }

    private void reloadInternal() {
        FilesystemSkillLoader.LoadResult local = localLoader.load(localRoot);
        repository.reload(bundledSources, local, installedMods);
        current = buildView(local);
    }

    private SkillCommunityView buildCommunity(Optional<SkillCommunityView.Notice> notice) {
        if (communityCatalog == null || communityCatalog.current().isEmpty()) {
            return new SkillCommunityView(false, Optional.empty(), dev.openallay.util.Java8Collections.listOf(), notice);
        }
        CommunityCatalogManifest catalog = communityCatalog.current().orElseThrow();
        List<SkillCommunityView.Package> packages = dev.openallay.util.Java8Collections.toList(catalog.packages().stream().map(entry -> {
            Optional<String> installedVersion = current.find(entry.id())
                    .flatMap(skill -> Optional.ofNullable(
                            skill.metadata().attributes().get("openallay/version")));
            boolean installed = current.find(entry.id()).isPresent();
            boolean compatible = compatible(entry);
            return SkillCommunityView.Package.from(
                    entry, installed, installedVersion, compatible);
        }));
        return new SkillCommunityView(
                true, Optional.of(catalog.generatedAt()), packages, notice);
    }

    private boolean compatible(CommunityCatalogManifest.PackageEntry entry) {
        return entry.compatibility().supports(minecraftVersion, OpenAllayConstants.SKILL_API_VERSION);
    }

    private static CommunityCatalogClient defaultCatalog(Path localRoot) {
        Path root = Objects.requireNonNull(localRoot, "localRoot").toAbsolutePath().normalize();
        Path config = root.getParent();
        if (config == null) {
            throw new IllegalArgumentException("Skill root requires a configuration directory");
        }
        return new CommunityCatalogClient(
                DEFAULT_CATALOG_URI,
                config.resolve("catalogs/skills.json"),
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
                comparison = new java.math.BigInteger(a).compareTo(new java.math.BigInteger(b));
            } else {
                comparison = a.compareTo(b);
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private SkillSettingsView buildView(FilesystemSkillLoader.LoadResult local) {
        Map<String, List<ParsedSource>> parsedSources = new LinkedHashMap<>();
        for (SkillSource source : bundledSources) {
            addParsed(parsedSources, source);
        }
        for (SkillSource source : repository.externalSources()) {
            addParsed(parsedSources, source);
        }
        for (SkillSource source : local.sources()) {
            addParsed(parsedSources, source);
        }

        List<SkillSettingsView.Skill> skills = new ArrayList<>();
        for (dev.openallay.skill.SkillMetadata metadata : repository.metadata()) {
            SkillDocument document = repository.find(metadata.name()).orElseThrow();
            String markdown = parsedSources.getOrDefault(metadata.name(), dev.openallay.util.Java8Collections.listOf()).stream()
                    .filter(parsed -> parsed.document().metadata().origin() == metadata.origin())
                    .filter(parsed -> sameContent(parsed.document(), document))
                    .map(parsed -> entryMarkdown(parsed.source()))
                    .findFirst().orElse(null);
            if (markdown == null) {
                SkillSettingsView.Skill previous = current.find(metadata.name()).orElse(null);
                if (previous != null
                        && previous.origin() == metadata.origin()
                        && previous.body().equals(document.instructions())) {
                    markdown = previous.markdown();
                }
            }
            if (markdown == null) {
                throw new IllegalStateException("Validated Skill source is unavailable: " + metadata.name());
            }
            skills.add(new SkillSettingsView.Skill(
                    metadata,
                    document.instructions(),
                    markdown,
                    store.hasOverride(metadata.name())));
        }
        return new SkillSettingsView(skills, repository.diagnostics());
    }

    private void addParsed(Map<String, List<ParsedSource>> parsedSources, SkillSource source) {
        try {
            SkillDocument document = parser.parse(source);
            parsedSources.computeIfAbsent(document.metadata().name(), ignored -> new ArrayList<>())
                    .add(new ParsedSource(source, document));
        } catch (RuntimeException ignored) {
            // Repository diagnostics own invalid-package reporting and last-valid retention.
        }
    }

    private SkillSource bundledNamed(String name) {
        SkillSource bundled = sourceNamed(bundledSources, name);
        return bundled == null ? sourceNamed(repository.externalSources(), name) : bundled;
    }

    private SkillSource sourceNamed(Collection<SkillSource> sources, String name) {
        for (SkillSource source : sources) {
            try {
                if (parser.parse(source).metadata().name().equals(name)) {
                    return source;
                }
            } catch (RuntimeException ignored) {
                // Invalid local packages are isolated by the loader/repository contract.
            }
        }
        return null;
    }

    private SkillSource candidateSource(SkillSource base, String markdown) {
        Map<String, String> files = new LinkedHashMap<>(base.files());
        files.put(base.entryPath(), markdown);
        return new SkillSource(
                "local-candidate:" + base.directoryName(),
                base.entryPath(),
                files,
                SkillSource.Origin.LOCAL);
    }

    private void rollback(String name, boolean created, String previousMarkdown) {
        if (created) {
            if (store.hasOverride(name)) {
                store.deleteOverride(name);
            }
            return;
        }
        if (previousMarkdown != null) {
            store.editOverride(name, previousMarkdown);
        }
    }

    private static boolean sameContent(SkillDocument expected, SkillDocument actual) {
        if (actual == null) {
            return false;
        }
        dev.openallay.skill.SkillMetadata left = expected.metadata();
        dev.openallay.skill.SkillMetadata right = actual.metadata();
        return left.name().equals(right.name())
                && left.description().equals(right.description())
                && left.license().equals(right.license())
                && left.compatibility().equals(right.compatibility())
                && left.attributes().equals(right.attributes())
                && left.requiredMods().equals(right.requiredMods())
                && left.allowedTools().equals(right.allowedTools())
                && left.references().equals(right.references())
                && expected.instructions().equals(actual.instructions())
                && expected.references().equals(actual.references());
    }

    private static String entryMarkdown(SkillSource source) {
        String markdown = source.files().get(source.entryPath());
        if (markdown == null || dev.openallay.util.Java8Strings.isBlank(markdown)) {
            throw new IllegalStateException("Skill entry Markdown is unavailable");
        }
        return markdown;
    }

    private static <T> ToolResult.Failure<T> failure(
            String code, String message, RuntimeException failure) {
        String detail = failure.getMessage();
        return new ToolResult.Failure<>(
                code,
                detail == null || dev.openallay.util.Java8Strings.isBlank(detail) ? message : message + ": " + detail);
    }

    @dev.openallay.value.ValueType(ParsedSource.ValueSchemaProvider.class)
private static final class ParsedSource {
    private final SkillSource source;
    private final SkillDocument document;
    private ParsedSource(SkillSource source, SkillDocument document) {
        this.source = source;
        this.document = document;
    }
    public SkillSource source() { return source; }
    public SkillDocument document() { return document; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ParsedSource)) return false;
        ParsedSource that = (ParsedSource) other;
        return java.util.Objects.equals(source, that.source) && java.util.Objects.equals(document, that.document);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(document);
        return hash;
    }
    @Override public String toString() { return "ParsedSource[source=" + source + ", document=" + document + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ParsedSource> schema() {
            return new dev.openallay.value.ValueSchema<>(ParsedSource.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ParsedSource>>asList(new dev.openallay.value.ValueSchema.Component<>(ParsedSource.class, "source", ParsedSource::source), new dev.openallay.value.ValueSchema.Component<>(ParsedSource.class, "document", ParsedSource::document)), arguments -> new ParsedSource((SkillSource) arguments[0], (SkillDocument) arguments[1]));
        }
    }
}
}
