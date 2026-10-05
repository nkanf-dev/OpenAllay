package dev.openallay.settings.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.extension.OpenAllayExtension;
import dev.openallay.extension.OpenAllayExtensionContribution;
import dev.openallay.extension.OpenAllayExtensionDescriptor;
import dev.openallay.extension.OpenAllayExtensionEnvironment;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.extension.catalog.ExtensionCatalogCodec;
import dev.openallay.extension.install.ExtensionPackageInstaller;
import dev.openallay.extension.install.ExtensionPackageManifest;
import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.tool.ToolResult;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExtensionSettingsBackendTest {
    @TempDir
    Path temporary;

    @Test
    void productionCatalogUsesThePublishedExtensionRepository() {
        assertEquals(
                "https://raw.githubusercontent.com/nkanf-dev/OpenAllay-Extensions/main/catalog.json",
                ExtensionSettingsBackend.DEFAULT_CATALOG_URI.toString());
    }

    @Test
    void productionBackendIgnoresRetiredGrantFileAndPreservesUserData() throws Exception {
        for (String contents : List.of("", "not valid JSON", "{\"grants\":{}}")) {
            JavascriptDataModuleRegistry modules = new JavascriptDataModuleRegistry();
            OpenAllayExtensionRegistry registry = activeRegistry(modules, new java.util.concurrent.atomic.AtomicInteger());
            Path config = temporary.resolve("config-" + contents.length());
            Path retiredFile = config.resolve("extension-capabilities.json");
            if (!contents.isEmpty()) {
                Files.createDirectories(config);
                Files.writeString(retiredFile, contents);
            }

            ExtensionSettingsBackend backend = new ExtensionSettingsBackend(
                    config, temporary.resolve("mods"), registry, modules);
            ExtensionSettingsView.Extension active = extension(backend.currentView(), "sample:extension");

            assertEquals(ExtensionSettingsView.State.ACTIVE, active.state());
            assertEquals(List.of("sample:native"), active.contributions().hostBindings());
            assertTrue(backend.currentView().catalog().notice().isEmpty());
            if (contents.isEmpty()) assertTrue(Files.notExists(retiredFile));
            else assertEquals(contents, Files.readString(retiredFile));
        }
    }

    @Test
    void activeExtensionNativeActionsNeedNoAdditionalSettingsGrant() throws Exception {
        JavascriptDataModuleRegistry modules = new JavascriptDataModuleRegistry();
        var operations = new java.util.concurrent.atomic.AtomicInteger();
        OpenAllayExtensionRegistry registry = activeRegistry(modules, operations);
        Path config = temporary.resolve("active-config");
        ExtensionSettingsBackend backend = new ExtensionSettingsBackend(
                config, temporary.resolve("mods"), registry, modules);

        assertEquals(ExtensionSettingsView.State.ACTIVE,
                extension(backend.currentView(), "sample:extension").state());
        try (var invocation = registry.prepareJavascriptInvocation(
                dev.openallay.context.ToolInvocationContext.developmentConsole("native-action"),
                new dev.openallay.model.CancellationSignal())) {
            invocation.open(ignored -> {});
            assertEquals(1, invocation.invokeHostMethod("sample:native", "build", List.of()).getAsInt());
        }
        assertEquals(1, operations.get());
        assertTrue(Files.notExists(config.resolve("extension-capabilities.json")));
        assertEquals(0, registry.activeJavascriptInvocations());
    }

    private static OpenAllayExtensionRegistry activeRegistry(
            JavascriptDataModuleRegistry modules, java.util.concurrent.atomic.AtomicInteger operations) {
        OpenAllayExtensionRegistry registry = new OpenAllayExtensionRegistry(
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0"), modules,
                new JavascriptModuleCatalog(Map.of()), new SkillRepository(new SkillParser(), List.of()), Set.of());
        assertEquals(dev.openallay.extension.OpenAllayExtensionState.ACTIVE, registry.register(new OpenAllayExtension() {
            @Override public OpenAllayExtensionDescriptor descriptor() {
                return new OpenAllayExtensionDescriptor("sample:extension", "Sample", "1.0.0", "Provider",
                        "Native actions", Set.of("fabric"), "[26.2,26.3)", "[0.2,0.3)", "bundled");
            }
            @Override public OpenAllayExtensionContribution contribution() {
                return new OpenAllayExtensionContribution(List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(new dev.openallay.extension.JavascriptHostBinding("sample:native", List.of(
                                new dev.openallay.extension.JavascriptHostMethod("build", List.of(),
                                        dev.openallay.extension.JavascriptHostValueType.INTEGER,
                                        (context, arguments) -> {
                                            context.requireActive();
                                            return new com.google.gson.JsonPrimitive(operations.incrementAndGet());
                                        })))));
            }
        }).state());
        return registry;
    }

    @Test
    void invalidCatalogRetainsPriorCommunityGenerationAndCompatibilityState() {
        JavascriptDataModuleRegistry dataModules = new JavascriptDataModuleRegistry();
        OpenAllayExtensionEnvironment environment =
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0");
        OpenAllayExtensionRegistry registry = new OpenAllayExtensionRegistry(
                environment,
                dataModules,
                new JavascriptModuleCatalog(Map.of()),
                new SkillRepository(new SkillParser(), List.of()),
                Set.of());
        ExtensionSettingsBackend backend = new ExtensionSettingsBackend(
                registry,
                dataModules,
                new ExtensionCatalogCodec(),
                new ExtensionPackageInstaller(environment, temporary.resolve("pending")));

        ToolResult<ExtensionSettingsView> accepted = backend.replaceCatalog("""
                {
                  "schemaVersion": 2,
                  "kind": "extension",
                  "generatedAt": "2026-07-25T00:00:00Z",
                  "extensions": [
                    {
                      "id": "community:compatible",
                      "name": "Compatible",
                      "version": "1.0.0",
                      "provider": "Community",
                      "summary": "Compatible package",
                      "minecraftVersionRange": "[26.2,26.3)",
                      "openAllayApiVersionRange": "[0.2,0.3)",
                      "artifacts": [{
                        "loader": "fabric",
                        "artifact": "https://example.invalid/compatible.jar",
                        "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "modIds": ["compatible_extension"]
                      }],
                      "source": "community"
                    },
                    {
                      "id": "community:neoforge",
                      "name": "NeoForge only",
                      "version": "1.0.0",
                      "provider": "Community",
                      "summary": "Incompatible package",
                      "minecraftVersionRange": "[26.2,26.3)",
                      "openAllayApiVersionRange": "[0.2,0.3)",
                      "artifacts": [{
                        "loader": "neoforge",
                        "artifact": "https://example.invalid/neoforge.jar",
                        "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        "modIds": ["neoforge_extension"]
                      }],
                      "source": "community"
                    }
                  ]
                }
                """);
        ToolResult<ExtensionSettingsView> rejected = backend.replaceCatalog("{}");

        assertInstanceOf(ToolResult.Success.class, accepted);
        assertEquals(
                List.of("community:compatible", "community:neoforge", "openallay:core"),
                backend.currentView().extensions().stream()
                        .map(ExtensionSettingsView.Extension::id)
                        .toList());
        assertEquals(
                ExtensionSettingsView.State.COMMUNITY,
                extension(backend.currentView(), "community:compatible").state());
        assertEquals(
                ExtensionSettingsView.State.INCOMPATIBLE,
                extension(backend.currentView(), "community:neoforge").state());
        ExtensionSettingsView.PackageInfo incompatiblePackage =
                extension(backend.currentView(), "community:neoforge").packageInfo();
        assertTrue(incompatiblePackage.catalogListed());
        assertEquals("1.0.0", incompatiblePackage.availableVersion());
        assertEquals("", incompatiblePackage.artifact());
        assertEquals("", incompatiblePackage.sha256());
        assertTrue(!incompatiblePackage.installable());
        ToolResult.Failure<ExtensionSettingsView> failure =
                assertInstanceOf(ToolResult.Failure.class, rejected);
        assertEquals("catalog_refresh_failed", failure.code());
        assertEquals(3, backend.currentView().extensions().size());
    }

    @Test
    void activeExtensionExposesUpdateAndLocalImportStagesRestartRequired() throws Exception {
        JavascriptDataModuleRegistry dataModules = new JavascriptDataModuleRegistry();
        OpenAllayExtensionEnvironment environment =
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0");
        OpenAllayExtensionRegistry registry = new OpenAllayExtensionRegistry(
                environment,
                dataModules,
                new JavascriptModuleCatalog(Map.of()),
                new SkillRepository(new SkillParser(), List.of()),
                Set.of());
        registry.register(new OpenAllayExtension() {
            @Override
            public OpenAllayExtensionDescriptor descriptor() {
                return new OpenAllayExtensionDescriptor(
                        "sample:extension",
                        "Sample",
                        "1.0.0",
                        "Provider",
                        "Sample Extension",
                        Set.of("fabric"),
                        "[26.2,26.3)",
                        "[0.2,0.3)",
                        "bundled");
            }

            @Override
            public OpenAllayExtensionContribution contribution() {
                return OpenAllayExtensionContribution.empty();
            }
        });
        ExtensionSettingsBackend backend = new ExtensionSettingsBackend(
                registry,
                dataModules,
                new ExtensionCatalogCodec(),
                new ExtensionPackageInstaller(environment, temporary.resolve("mods")));
        byte[] jar = fabricJar("sample_extension");
        Path local = temporary.resolve("sample.jar");
        Files.write(local, jar);
        String catalog = catalog("2.0.0", sha256(jar));

        assertInstanceOf(ToolResult.Success.class, backend.replaceCatalog(catalog));
        ExtensionSettingsView.Extension update =
                extension(backend.currentView(), "sample:extension");
        assertEquals(ExtensionSettingsView.State.ACTIVE, update.state());
        assertTrue(update.packageInfo().updateAvailable());
        assertTrue(update.packageInfo().installable());

        assertInstanceOf(
                ToolResult.Success.class,
                backend.importLocalPackage("sample:extension", local));
        ExtensionSettingsView.Extension staged =
                extension(backend.currentView(), "sample:extension");
        assertEquals(ExtensionSettingsView.State.RESTART_REQUIRED, staged.state());
        assertEquals("1.0.0", staged.version());
        assertEquals("2.0.0", staged.packageInfo().availableVersion());
        assertTrue(Files.isRegularFile(
                temporary.resolve("mods").resolve(
                        "openallay-extension-sample_extension.jar")));
    }

    @Test
    void localImportIsAvailableWhenCommunityCatalogIsEmpty() throws Exception {
        JavascriptDataModuleRegistry dataModules = new JavascriptDataModuleRegistry();
        OpenAllayExtensionEnvironment environment =
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0");
        OpenAllayExtensionRegistry registry = new OpenAllayExtensionRegistry(
                environment,
                dataModules,
                new JavascriptModuleCatalog(Map.of()),
                new SkillRepository(new SkillParser(), List.of()),
                Set.of());
        ExtensionSettingsBackend backend = new ExtensionSettingsBackend(
                registry,
                dataModules,
                new ExtensionCatalogCodec(),
                new ExtensionPackageInstaller(environment, temporary.resolve("local-mods")));
        Path local = temporary.resolve("local.jar");
        Files.write(local, fabricJar("sample_extension"));

        assertInstanceOf(ToolResult.Success.class, backend.importLocalPackage(local));

        ExtensionSettingsView.Extension staged =
                extension(backend.currentView(), "sample:extension");
        assertEquals(ExtensionSettingsView.State.RESTART_REQUIRED, staged.state());
        assertEquals("2.0.0", staged.version());
        assertEquals("community", staged.source());
        assertTrue(!staged.packageInfo().catalogListed());
        assertEquals(64, staged.packageInfo().sha256().length());
        assertTrue(staged.contributions().hostBindings().isEmpty());
        assertTrue(registry.snapshot().extensions().isEmpty());
    }

    @Test
    void preparedPackageExposesActualRequirementsWithoutMutatingViewsUntilCommit() throws Exception {
        JavascriptDataModuleRegistry dataModules = new JavascriptDataModuleRegistry();
        OpenAllayExtensionEnvironment environment =
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0");
        OpenAllayExtensionRegistry registry = new OpenAllayExtensionRegistry(
                environment, dataModules, new JavascriptModuleCatalog(Map.of()),
                new SkillRepository(new SkillParser(), List.of()), Set.of());
        ExtensionSettingsBackend backend = new ExtensionSettingsBackend(registry, dataModules,
                new ExtensionCatalogCodec(), new ExtensionPackageInstaller(environment, temporary.resolve("mods")));
        Path source = temporary.resolve("sample.jar");
        byte[] jar = fabricJar("sample_extension", true);
        Files.write(source, jar);
        String catalog = catalog("2.0.0", sha256(jar)).replace("\"source\": \"community\"", """
                "source": "community",
                "requirements": {"skills": ["catalog-skill"]}
                """);
        assertInstanceOf(ToolResult.Success.class, backend.replaceCatalog(catalog));
        assertEquals(Set.of("catalog-skill"), extension(backend.currentView(), "sample:extension")
                .requirements().skills());
        var cancelled = prepared(backend.prepareLocalPackage("sample:extension", source));
        assertEquals(ExtensionSettingsView.State.COMMUNITY,
                extension(backend.currentView(), "sample:extension").state());
        cancelled.close();
        assertInstanceOf(ToolResult.Failure.class, cancelled.commit());

        var candidate = prepared(backend.prepareLocalPackage("sample:extension", source));
        assertTrue(candidate.catalogRequirementsDiffer());
        assertEquals(Set.of("package-skill"), candidate.requirements().skills());
        assertInstanceOf(ToolResult.Success.class, candidate.commit());
        ExtensionSettingsView.Extension staged = extension(backend.currentView(), "sample:extension");
        assertEquals(ExtensionSettingsView.State.RESTART_REQUIRED, staged.state());
        assertEquals(Set.of("package-skill"), staged.requirements().skills());
        assertTrue(registry.snapshot().extensions().isEmpty());
        assertInstanceOf(ToolResult.Failure.class, candidate.commit());
    }

    @Test
    void installedRequirementsSurviveCatalogPreviewAndLocalPendingReplacement() throws Exception {
        JavascriptDataModuleRegistry dataModules = new JavascriptDataModuleRegistry();
        OpenAllayExtensionEnvironment environment =
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0");
        OpenAllayExtensionRegistry registry = new OpenAllayExtensionRegistry(
                environment, dataModules, new JavascriptModuleCatalog(Map.of()),
                new SkillRepository(new SkillParser(), List.of()), Set.of());
        registry.register(new OpenAllayExtension() {
            @Override public OpenAllayExtensionDescriptor descriptor() {
                return new OpenAllayExtensionDescriptor("sample:extension", "Sample", "1.0.0",
                        "Provider", "Sample Extension", Set.of("fabric"), "[26.2,26.3)", "[0.2,0.3)",
                        "community", new dev.openallay.requirement.RequirementSet(
                                Set.of(), Set.of(), Set.of("installed-skill")));
            }
            @Override public OpenAllayExtensionContribution contribution() {
                return OpenAllayExtensionContribution.empty();
            }
        });
        ExtensionSettingsBackend backend = new ExtensionSettingsBackend(registry, dataModules,
                new ExtensionCatalogCodec(), new ExtensionPackageInstaller(environment, temporary.resolve("mods")));
        assertEquals(Set.of("installed-skill"), extension(backend.currentView(), "sample:extension")
                .requirements().skills());
        Path source = temporary.resolve("sample.jar");
        byte[] jar = fabricJar("sample_extension", true);
        Files.write(source, jar);
        assertInstanceOf(ToolResult.Success.class, backend.importLocalPackage(source));
        ExtensionSettingsView.Extension pending = extension(backend.currentView(), "sample:extension");
        assertEquals(ExtensionSettingsView.State.RESTART_REQUIRED, pending.state());
        assertEquals(Set.of("package-skill"), pending.requirements().skills());
        assertEquals("1.0.0", pending.version());
        assertEquals(Set.of("installed-skill"), registry.snapshot().extensions().getFirst()
                .descriptor().requirements().skills());
    }

    @Test
    void communityPreparationDoesNotMarkArtifactStagedUntilCommit() throws Exception {
        JavascriptDataModuleRegistry dataModules = new JavascriptDataModuleRegistry();
        OpenAllayExtensionEnvironment environment =
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0");
        OpenAllayExtensionRegistry registry = new OpenAllayExtensionRegistry(
                environment, dataModules, new JavascriptModuleCatalog(Map.of()),
                new SkillRepository(new SkillParser(), List.of()), Set.of());
        byte[] jar = fabricJar("sample_extension", true);
        dev.openallay.net.HttpTransport transport = new dev.openallay.net.HttpTransport() {
            @Override
            public <T> java.util.concurrent.CompletableFuture<T> execute(
                    dev.openallay.net.HttpExchangeRequest request,
                    dev.openallay.net.HttpCancellation cancellation, ResponseDecoder<T> decoder) {
                try {
                    return java.util.concurrent.CompletableFuture.completedFuture(decoder.decode(200,
                            new dev.openallay.net.HttpResponseHeaders(Map.of()),
                            new java.io.ByteArrayInputStream(jar)));
                } catch (java.io.IOException failure) {
                    return java.util.concurrent.CompletableFuture.failedFuture(failure);
                }
            }
        };
        Path mods = temporary.resolve("mods");
        ExtensionSettingsBackend backend = new ExtensionSettingsBackend(registry, dataModules,
                new ExtensionCatalogCodec(), new ExtensionPackageInstaller(environment, mods, transport));
        assertInstanceOf(ToolResult.Success.class, backend.replaceCatalog(catalog("2.0.0", sha256(jar))));
        var candidate = prepared(backend.prepareCommunity("sample:extension",
                new dev.openallay.model.CancellationSignal()).join());
        assertEquals(Set.of("package-skill"), candidate.requirements().skills());
        assertEquals(ExtensionSettingsView.State.COMMUNITY,
                extension(backend.currentView(), "sample:extension").state());
        assertTrue(Files.notExists(mods.resolve("openallay-extension-sample_extension.jar")));
        assertInstanceOf(ToolResult.Success.class, candidate.commit());
        assertEquals(ExtensionSettingsView.State.RESTART_REQUIRED,
                extension(backend.currentView(), "sample:extension").state());
        assertEquals(Set.of("package-skill"), extension(backend.currentView(), "sample:extension")
                .requirements().skills());
    }

    @SuppressWarnings("unchecked")
    private static dev.openallay.settings.requirement.PreparedPackageInstall prepared(
            ToolResult<dev.openallay.settings.requirement.PreparedPackageInstall> result) {
        return ((ToolResult.Success<dev.openallay.settings.requirement.PreparedPackageInstall>)
                assertInstanceOf(ToolResult.Success.class, result)).value();
    }

    private static ExtensionSettingsView.Extension extension(
            ExtensionSettingsView view, String id) {
        return view.extensions().stream()
                .filter(extension -> extension.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static String catalog(String version, String checksum) {
        return """
                {
                  "schemaVersion": 2,
                  "kind": "extension",
                  "generatedAt": "2026-07-25T00:00:00Z",
                  "extensions": [{
                    "id": "sample:extension",
                    "name": "Sample",
                    "version": "%s",
                    "provider": "Provider",
                    "summary": "Sample Extension",
                    "minecraftVersionRange": "[26.2,26.3)",
                    "openAllayApiVersionRange": "[0.2,0.3)",
                    "artifacts": [{
                      "loader": "fabric",
                      "artifact": "https://example.invalid/sample.jar",
                      "sha256": "%s",
                      "modIds": ["sample_extension"]
                    }],
                    "source": "community"
                  }]
                }
                """.formatted(version, checksum);
    }

    private static byte[] fabricJar(String modId) throws Exception {
        return fabricJar(modId, false);
    }

    private static byte[] fabricJar(String modId, boolean requirements) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(output)) {
            jar.putNextEntry(new JarEntry(ExtensionPackageManifest.JAR_PATH));
            String manifest = ("""
                            {
                              "schemaVersion": 1,
                              "id": "sample:extension",
                              "name": "Sample",
                              "version": "2.0.0",
                              "provider": "Provider",
                              "summary": "Sample Extension",
                              "loaders": ["fabric"],
                              "minecraftVersionRange": "[26.2,26.3)",
                              "openAllayApiVersionRange": "[0.2,0.3)",
                              "modIds": ["%s"],
                              "source": "community"
                            }
                            """)
                    .formatted(modId);
            if (requirements) {
                manifest = manifest.replace("\"source\": \"community\"", """
                        "source": "community",
                        "requirements": {"skills": ["package-skill"]}
                        """);
            }
            jar.write(manifest.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("fabric.mod.json"));
            jar.write(("{\"schemaVersion\":1,\"id\":\"" + modId
                            + "\",\"version\":\"2.0.0\",\"name\":\"Sample\"}")
                    .getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return output.toByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
