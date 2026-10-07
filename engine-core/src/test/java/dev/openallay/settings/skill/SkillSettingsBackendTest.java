package dev.openallay.settings.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.skill.SkillSource;
import dev.openallay.tool.ToolResult;
import dev.openallay.community.CommunityCatalogClient;
import dev.openallay.model.CancellationSignal;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpTransport;
import dev.openallay.skill.FilesystemSkillLoader;
import dev.openallay.skill.install.SkillPackageInstaller;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class SkillSettingsBackendTest {
    @TempDir Path temporaryDirectory;

    @Test
    void savingBundledSkillCreatesValidatedLocalOverride() throws IOException {
        Path root = temporaryDirectory.resolve("skills");
        SkillRepository repository = repository();
        SkillSettingsBackend backend = backend(root, repository);

        SkillSettingsView initial = backend.currentView();
        assertEquals(SkillSource.Origin.BUNDLED, initial.find("guide").orElseThrow().origin());
        assertTrue(initial.find("guide").orElseThrow().createsOverrideOnSave());

        SkillSettingsView saved = success(backend.saveOverride("guide", skill("guide", "local body")));

        SkillSettingsView.Skill guide = saved.find("guide").orElseThrow();
        assertEquals(SkillSource.Origin.LOCAL, guide.origin());
        assertEquals("local body", guide.body());
        assertTrue(guide.overridePresent());
        assertEquals("local body", repository.find("guide").orElseThrow().instructions());
        assertEquals(skill("guide", "local body"), Files.readString(root.resolve("guide/SKILL.md")));
    }

    @Test
    void invalidEditLeavesPriorFileAndPublishedDocumentUnchanged() throws IOException {
        Path root = temporaryDirectory.resolve("skills");
        SkillRepository repository = repository();
        SkillSettingsBackend backend = backend(root, repository);
        success(backend.saveOverride("guide", skill("guide", "valid local")));
        String before = Files.readString(root.resolve("guide/SKILL.md"));

        failure(backend.saveOverride("guide", "not frontmatter"));

        assertEquals(before, Files.readString(root.resolve("guide/SKILL.md")));
        assertEquals("valid local", repository.find("guide").orElseThrow().instructions());
        assertEquals("valid local", backend.currentView().find("guide").orElseThrow().body());
    }

    @Test
    void dependencyRejectedEditRollsBackAndExternalInvalidityRetainsLastValidDiagnostic()
            throws IOException {
        Path root = temporaryDirectory.resolve("skills");
        SkillRepository repository = repository();
        SkillSettingsBackend backend = backend(root, repository);
        success(backend.saveOverride("guide", skill("guide", "valid local")));
        String before = Files.readString(root.resolve("guide/SKILL.md"));

        String unavailableTool = before.replace(
                "allowed-tools: \"\"", "allowed-tools: \"unknown:tool\"");
        ToolResult.Failure<SkillSettingsView> rejected = failure(
                backend.saveOverride("guide", unavailableTool));
        assertEquals("skill_override_dependency_unavailable", rejected.code());
        assertEquals(before, Files.readString(root.resolve("guide/SKILL.md")));

        Files.writeString(root.resolve("guide/SKILL.md"), "externally broken");
        SkillSettingsView retained = success(backend.reloadSkills());
        assertEquals("valid local", retained.find("guide").orElseThrow().body());
        assertEquals(before, retained.find("guide").orElseThrow().markdown());
        assertEquals("skill_validation_failed", retained.diagnostics().getFirst().code());
    }

    @Test
    void deletingLocalOverrideRestoresReadOnlyBundledDocument() {
        Path root = temporaryDirectory.resolve("skills");
        SkillRepository repository = repository();
        SkillSettingsBackend backend = backend(root, repository);
        success(backend.saveOverride("guide", skill("guide", "local")));

        SkillSettingsView restored = success(backend.deleteOverride("guide"));

        SkillSettingsView.Skill guide = restored.find("guide").orElseThrow();
        assertEquals(SkillSource.Origin.BUNDLED, guide.origin());
        assertEquals("bundled body", guide.body());
        assertFalse(guide.overridePresent());
        assertFalse(Files.exists(root.resolve("guide")));
    }

    @Test
    void restartLoadsPersistedOverrideFromExternalMarkdownPackage() {
        Path root = temporaryDirectory.resolve("skills");
        SkillSettingsBackend first = backend(root, repository());
        success(first.saveOverride("guide", skill("guide", "survives restart")));

        SkillRepository restartedRepository = repository();
        SkillSettingsBackend restarted = backend(root, restartedRepository);

        SkillSettingsView.Skill guide = restarted.currentView().find("guide").orElseThrow();
        assertEquals(SkillSource.Origin.LOCAL, guide.origin());
        assertEquals("survives restart", guide.body());
        assertTrue(guide.overridePresent());
        assertEquals("survives restart", restartedRepository.find("guide").orElseThrow().instructions());
    }

    @Test
    void backendListsCachedCommunityAndImportsThroughManagedInstaller() throws Exception {
        Path root = temporaryDirectory.resolve("skills");
        Path cache = temporaryDirectory.resolve("catalogs/skills.json");
        Files.createDirectories(cache.getParent());
        Files.writeString(cache, """
                {"schemaVersion":2,"kind":"skill","generatedAt":"2026-07-25T00:00:00Z",
                 "packages":[{"id":"demo","displayName":"Demo Skill",
                 "description":"A test community Skill.","publisher":"Test Publisher",
                 "version":"1.0.0",
                 "archive":"https://example.test/demo.zip",
                 "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "compatibility":{"minecraft":"26.2","openallayApi":"0.2"},
                 "source":"https://example.test/demo"}]}
                """);
        HttpTransport unused = new HttpTransport() {
            @Override
            public <T> CompletableFuture<T> execute(
                    HttpExchangeRequest request,
                    dev.openallay.net.HttpCancellation cancellation,
                    ResponseDecoder<T> decoder) {
                return CompletableFuture.failedFuture(new IOException("offline"));
            }
        };
        CommunityCatalogClient catalog = new CommunityCatalogClient(
                URI.create("https://example.test/catalog.json"),
                cache,
                unused,
                Duration.ofSeconds(5));
        SkillSettingsBackend backend = new SkillSettingsBackend(
                root,
                repository(),
                new SkillParser(),
                List.of(bundled()),
                Set.of(),
                new FilesystemSkillLoader(),
                catalog,
                new SkillPackageInstaller(root, new SkillParser(), "26.2"),
                "26.2");
        Path imported = temporaryDirectory.resolve("demo");
        Files.createDirectories(imported);
        Files.writeString(imported.resolve("SKILL.md"), skill("demo", "community body"));

        assertTrue(backend.currentCommunityView().available());
        assertFalse(backend.currentCommunityView().packages().getFirst().installed());
        SkillCommunityView after = successCommunity(backend.importLocalPackage(imported));
        assertTrue(after.packages().getFirst().installed());

        ToolResult<SkillCommunityView> failed =
                backend.refreshCommunity(new CancellationSignal()).join();
        assertEquals("catalog_refresh_failed",
                assertInstanceOf(ToolResult.Failure.class, failed).code());
        assertTrue(backend.currentCommunityView().available());
    }

    @Test
    void preparedLocalPackageOnlyRefreshesRepositoryAndViewsAfterCommit() throws Exception {
        Path root = temporaryDirectory.resolve("skills");
        SkillRepository repository = repository();
        SkillSettingsBackend backend = backend(root, repository);
        Path source = temporaryDirectory.resolve("demo");
        Files.createDirectories(source);
        String markdown = skill("demo", "reviewed body").replace("allowed-tools:", """
                metadata:
                  openallay/requires-capabilities: "missing:capability"
                  openallay/requires-extensions: "missing:extension"
                  openallay/requires-skills: "missing-skill"
                allowed-tools:""");
        Files.writeString(source.resolve("SKILL.md"), markdown);
        var cancelled = prepared(backend.prepareLocalPackage(source));
        assertTrue(repository.find("demo").isEmpty());
        assertTrue(backend.currentView().find("demo").isEmpty());
        cancelled.close();
        assertFalse(Files.exists(root.resolve("demo")));
        assertInstanceOf(ToolResult.Failure.class, cancelled.commit());

        var candidate = prepared(backend.prepareLocalPackage(source));
        assertEquals(Set.of("missing:capability"), candidate.requirements().capabilities());
        Files.writeString(source.resolve("SKILL.md"), skill("demo", "changed body"));
        assertInstanceOf(ToolResult.Success.class, candidate.commit());
        assertEquals("reviewed body", repository.find("demo").orElseThrow().instructions());
        assertEquals("reviewed body", backend.currentView().find("demo").orElseThrow().body());
        assertEquals(markdown, Files.readString(root.resolve("demo/SKILL.md")));
        assertInstanceOf(ToolResult.Failure.class, candidate.commit());
    }

    @Test
    void communityDownloadPreparesWithoutPublishingAndCommitRefreshesCatalogState() throws Exception {
        Path root = temporaryDirectory.resolve("skills");
        String markdown = skill("demo", "downloaded body").replace("allowed-tools:", """
                metadata:
                  openallay/requires-extensions: "missing:extension"
                allowed-tools:""");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("SKILL.md"));
            zip.write(markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        byte[] archive = bytes.toByteArray();
        String checksum = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(archive));
        Path cache = temporaryDirectory.resolve("catalog.json");
        Files.writeString(cache, """
                {"schemaVersion":2,"kind":"skill","generatedAt":"2026-07-25T00:00:00Z",
                 "packages":[{"id":"demo","displayName":"Demo Skill",
                 "description":"A test community Skill.","publisher":"Test Publisher",
                 "version":"1.0.0","archive":"https://example.test/demo.zip",
                 "sha256":"%s","compatibility":{"minecraft":"26.2","openallayApi":"0.2"},
                 "source":"https://example.test/demo"}]}
                """.formatted(checksum));
        HttpTransport transport = new HttpTransport() {
            @Override
            public <T> CompletableFuture<T> execute(HttpExchangeRequest request,
                    dev.openallay.net.HttpCancellation cancellation, ResponseDecoder<T> decoder) {
                try {
                    return CompletableFuture.completedFuture(decoder.decode(200,
                            new dev.openallay.net.HttpResponseHeaders(Map.of()),
                            new java.io.ByteArrayInputStream(archive)));
                } catch (IOException failure) {
                    return CompletableFuture.failedFuture(failure);
                }
            }
        };
        SkillRepository repository = repository();
        SkillSettingsBackend backend = new SkillSettingsBackend(root, repository, new SkillParser(),
                List.of(bundled()), Set.of(), new FilesystemSkillLoader(),
                new CommunityCatalogClient(URI.create("https://example.test/catalog.json"), cache,
                        transport, Duration.ofSeconds(5)),
                new SkillPackageInstaller(root, new SkillParser(), transport, "26.2", "0.2"),
                "26.2");
        var candidate = prepared(backend.prepareCommunity("demo", new CancellationSignal()).join());
        assertEquals(Set.of("missing:extension"), candidate.requirements().extensions());
        assertTrue(repository.find("demo").isEmpty());
        assertFalse(backend.currentCommunityView().packages().getFirst().installed());
        assertFalse(Files.exists(root.resolve("demo")));
        assertInstanceOf(ToolResult.Success.class, candidate.commit());
        assertEquals("downloaded body", repository.find("demo").orElseThrow().instructions());
        assertTrue(backend.currentCommunityView().packages().getFirst().installed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.12.2", "1.16.5", "26.2"})
    void catalogUsesCurrentTargetAndSkillApiWhenSelectingLatestCompatiblePackage(
            String minecraftVersion) throws Exception {
        Path root = temporaryDirectory.resolve("skills");
        byte[] archive;
        try (var bytes = new java.io.ByteArrayOutputStream();
                var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("SKILL.md"));
            zip.write(skill("demo", "current target").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            archive = bytes.toByteArray();
        }
        String checksum = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(archive));
        String otherTarget = minecraftVersion.equals("26.2") ? "1.12.2" : "26.2";
        Path cache = temporaryDirectory.resolve("catalog.json");
        writeCatalog(cache, List.of(
                catalogEntry("demo", "1.0.0", minecraftVersion, "0.2", checksum),
                catalogEntry("demo", "1.1.0", minecraftVersion, "0.2", checksum),
                catalogEntry("demo", "1.2.0", "[1.12.2,26.2]", "0.2", checksum),
                catalogEntry("demo", "9.0.0", otherTarget, "0.2", checksum),
                catalogEntry("demo", "10.0.0", minecraftVersion, "0.4.0", checksum),
                catalogEntry("other-target", "1.0.0", otherTarget, "0.2", checksum),
                catalogEntry("other-api", "1.0.0", minecraftVersion, "0.4.0", checksum)));
        java.util.concurrent.atomic.AtomicInteger downloads = new java.util.concurrent.atomic.AtomicInteger();
        HttpTransport transport = new HttpTransport() {
            @Override
            public <T> CompletableFuture<T> execute(HttpExchangeRequest request,
                    dev.openallay.net.HttpCancellation cancellation, ResponseDecoder<T> decoder) {
                downloads.incrementAndGet();
                try {
                    return CompletableFuture.completedFuture(decoder.decode(200,
                            new dev.openallay.net.HttpResponseHeaders(Map.of()),
                            new java.io.ByteArrayInputStream(archive)));
                } catch (IOException failure) {
                    return CompletableFuture.failedFuture(failure);
                }
            }
        };
        SkillSettingsBackend backend = new SkillSettingsBackend(root, repository(), new SkillParser(),
                List.of(bundled()), Set.of(), new FilesystemSkillLoader(),
                new CommunityCatalogClient(URI.create("https://example.test/catalog.json"), cache,
                        transport, Duration.ofSeconds(5)),
                new SkillPackageInstaller(root, new SkillParser(), transport, minecraftVersion, "0.2"),
                minecraftVersion);

        assertEquals(List.of("1.0.0", "1.1.0", "1.2.0"), backend.currentCommunityView().packages().stream()
                .filter(SkillCommunityView.Package::compatible)
                .map(SkillCommunityView.Package::availableVersion).toList());
        try (var candidate = prepared(backend.prepareCommunity("demo", new CancellationSignal()).join())) {
            assertEquals("1.2.0", candidate.version());
        }
        for (String id : List.of("other-target", "other-api")) {
            assertEquals("skill_package_not_found", assertInstanceOf(ToolResult.Failure.class,
                    backend.prepareCommunity(id, new CancellationSignal()).join()).code());
        }
        assertEquals(1, downloads.get());
        assertFalse(Files.exists(root.resolve("demo")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.12.2", "1.16.5", "26.2"})
    void defaultBackendPassesCurrentTargetToInstallerWithoutDownloading(String minecraftVersion)
            throws Exception {
        Path root = temporaryDirectory.resolve("skills");
        Path cache = temporaryDirectory.resolve("catalogs/skills.json");
        writeCatalog(cache, List.of(catalogEntry("demo", "1.0.0", minecraftVersion,
                "0.2", "a".repeat(64))));
        SkillSettingsBackend backend = new SkillSettingsBackend(
                root, repository(), Set.of(), minecraftVersion);
        assertTrue(backend.currentCommunityView().packages().getFirst().compatible());
        CancellationSignal cancelled = new CancellationSignal();
        cancelled.cancel();

        assertEquals("skill_install_cancelled", assertInstanceOf(ToolResult.Failure.class,
                backend.prepareCommunity("demo", cancelled).join()).code());
        assertFalse(Files.exists(root.resolve("demo")));
    }

    private static void writeCatalog(Path cache, List<String> entries) throws IOException {
        Files.createDirectories(cache.getParent());
        Files.writeString(cache, """
                {"schemaVersion":2,"kind":"skill","generatedAt":"2026-07-25T00:00:00Z",
                 "packages":[%s]}
                """.formatted(String.join(",", entries)));
    }

    private static String catalogEntry(
            String id, String version, String minecraftVersion, String api, String checksum) {
        return """
                {"id":"%s","displayName":"Demo Skill","description":"A test Skill.",
                 "publisher":"Test Publisher","version":"%s","archive":"https://example.test/demo.zip",
                 "sha256":"%s","compatibility":{"minecraft":"%s","openallayApi":"%s"},
                 "source":"https://example.test/demo"}
                """.formatted(id, version, checksum, minecraftVersion, api);
    }

    @Test
    void registeredExtensionSkillsSurviveSettingsReloadInstallAndOverrideLifecycle() throws Exception {
        Path root = temporaryDirectory.resolve("skills");
        SkillRepository repository = repository();
        String externalMarkdown = skill("extension-guide", "extension instructions")
                .replace("allowed-tools:", """
                        metadata:
                          openallay/requires-extensions: "missing:extension"
                        allowed-tools:""");
        SkillSource external = new SkillSource("example:extension",
                "extension-guide/SKILL.md", Map.of(
                        "extension-guide/SKILL.md", externalMarkdown,
                        "extension-guide/references/facts.md", "Extension reference facts"),
                SkillSource.Origin.EXTERNAL);
        repository.registerExternal(List.of(external), Set.of());
        SkillSettingsBackend backend = backend(root, repository);

        SkillSettingsView.Skill initial = backend.currentView().find("extension-guide").orElseThrow();
        assertEquals(SkillSource.Origin.EXTERNAL, initial.origin());
        assertTrue(initial.createsOverrideOnSave());
        assertEquals(externalMarkdown, initial.markdown());
        assertEquals(Set.of("missing:extension"), initial.metadata().requirements().extensions());
        assertEquals("Extension reference facts", repository.find("extension-guide").orElseThrow()
                .references().get("references/facts.md"));
        assertInstanceOf(ToolResult.Success.class, backend.reloadSkills());
        assertEquals(externalMarkdown, backend.currentView().find("extension-guide").orElseThrow().markdown());

        Path source = temporaryDirectory.resolve("demo");
        Files.createDirectories(source);
        Files.writeString(source.resolve("SKILL.md"), skill("demo", "installed body"));
        var candidate = prepared(backend.prepareLocalPackage(source));
        assertInstanceOf(ToolResult.Success.class, candidate.commit());
        assertEquals("installed body", backend.currentView().find("demo").orElseThrow().body());
        assertEquals(SkillSource.Origin.EXTERNAL,
                backend.currentView().find("extension-guide").orElseThrow().origin());

        SkillSettingsView overridden = success(backend.saveOverride(
                "extension-guide", skill("extension-guide", "local instructions")));
        assertEquals(SkillSource.Origin.LOCAL, overridden.find("extension-guide").orElseThrow().origin());
        assertFalse(overridden.find("extension-guide").orElseThrow().createsOverrideOnSave());
        assertEquals("local instructions", repository.find("extension-guide").orElseThrow().instructions());
        assertEquals("Extension reference facts", Files.readString(root.resolve(
                "extension-guide/references/facts.md")));
        assertEquals(externalMarkdown, repository.externalSources().getFirst().files()
                .get("extension-guide/SKILL.md"));

        SkillSettingsView restored = success(backend.deleteOverride("extension-guide"));
        assertEquals(SkillSource.Origin.EXTERNAL, restored.find("extension-guide").orElseThrow().origin());
        assertEquals(externalMarkdown, restored.find("extension-guide").orElseThrow().markdown());
        assertEquals("Extension reference facts", repository.find("extension-guide").orElseThrow()
                .references().get("references/facts.md"));
        assertFalse(Files.exists(root.resolve("extension-guide")));

        Files.createDirectories(root.resolve("extension-guide"));
        Files.writeString(root.resolve("extension-guide/SKILL.md"),
                skill("extension-guide", "unavailable local").replace("allowed-tools:", """
                        metadata:
                          openallay/required-mods: "missing-mod"
                        allowed-tools:"""));
        SkillSettingsBackend restarted = backend(root, repository);
        assertEquals(SkillSource.Origin.EXTERNAL,
                restarted.currentView().find("extension-guide").orElseThrow().origin());
        assertEquals(externalMarkdown,
                restarted.currentView().find("extension-guide").orElseThrow().markdown());
    }

    @SuppressWarnings("unchecked")
    private static dev.openallay.settings.requirement.PreparedPackageInstall prepared(
            ToolResult<dev.openallay.settings.requirement.PreparedPackageInstall> result) {
        return ((ToolResult.Success<dev.openallay.settings.requirement.PreparedPackageInstall>)
                assertInstanceOf(ToolResult.Success.class, result)).value();
    }

    private SkillSettingsBackend backend(Path root, SkillRepository repository) {
        return new SkillSettingsBackend(
                root,
                repository,
                new SkillParser(),
                List.of(bundled()),
                Set.of(),
                "26.2");
    }

    private static SkillRepository repository() {
        return new SkillRepository(new SkillParser(), Set.of());
    }

    private static SkillSource bundled() {
        return new SkillSource(
                "openallay:bundled",
                "guide/SKILL.md",
                Map.of(
                        "guide/SKILL.md", skill("guide", "bundled body"),
                        "guide/references/facts.md", "Ground facts."),
                SkillSource.Origin.BUNDLED);
    }

    private static String skill(String name, String body) {
        return """
                ---
                name: %s
                description: Guide answers
                allowed-tools: ""
                ---
                %s
                """.formatted(name, body);
    }

    @SuppressWarnings("unchecked")
    private static SkillSettingsView success(ToolResult<SkillSettingsView> result) {
        return ((ToolResult.Success<SkillSettingsView>)
                        assertInstanceOf(ToolResult.Success.class, result))
                .value();
    }

    @SuppressWarnings("unchecked")
    private static ToolResult.Failure<SkillSettingsView> failure(
            ToolResult<SkillSettingsView> result) {
        return (ToolResult.Failure<SkillSettingsView>)
                assertInstanceOf(ToolResult.Failure.class, result);
    }

    @SuppressWarnings("unchecked")
    private static SkillCommunityView successCommunity(ToolResult<SkillCommunityView> result) {
        return ((ToolResult.Success<SkillCommunityView>)
                assertInstanceOf(ToolResult.Success.class, result)).value();
    }
}
