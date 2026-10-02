package dev.openallay.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.OpenAllayRuntime;
import dev.openallay.devmode.DevelopmentToolInspector;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideDisplayRuntime;
import dev.openallay.knowledge.KnowledgeRegistry;
import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.catalog.ModelCatalog;
import dev.openallay.model.catalog.ModelCatalogRequest;
import dev.openallay.platform.PlatformService;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientSettingsRuntimeTest {
    @Test
    void missingFilesUseDisabledMemoryDefaultWithoutMaterializingConfiguration(
            @TempDir Path directory) throws Exception {
        Path profiles = directory.resolve("models.json");
        Path metadata = directory.resolve("model-metadata.json");

        ToolResult<ClientSettingsRuntime> created = ClientSettingsRuntime.create(
                runtime(),
                profiles,
                metadata,
                Map.of(),
                Runnable::run,
                null,
                Clock.systemUTC(),
                GuideDisplayConfig.defaults());

        if (!(created instanceof ToolResult.Success<ClientSettingsRuntime> success)) {
            throw new AssertionError("expected native settings runtime creation to succeed");
        }
        ClientSettingsRuntime settings = success.value();
        assertEquals("default", settings.settings().snapshot()
                .models().config().defaultProfileId());
        assertFalse(settings.settings().snapshot().models().profiles().getFirst().available());
        assertEquals(null, settings.settings().snapshot().models().profiles().getFirst()
                .definition().maxOutputTokens());
        assertEquals("model_not_configured", settings.settings().snapshot().notice().code());
        assertFalse(Files.exists(profiles));
        settings.closeAsync().join();
    }

    @Test
    void offlineBuiltinContextWorksThroughNativeSettingsStartupAndUntouchedSave(@TempDir Path directory)
            throws Exception {
        Path profiles = directory.resolve("models.json");
        var definition = new ModelProfileDefinition("main", "Main", true, ModelProtocol.OPENAI_CHAT,
                URI.create("https://arbitrary.example/v1/"), "gpt-6-luna", "env:KEY", null, 8192,
                Duration.ofSeconds(30), Duration.ofSeconds(300), null);
        var config = new ModelProfilesConfig("main", List.of(definition));
        Files.writeString(profiles, new dev.openallay.model.config.ModelProfilesConfigWriter().encode(config));
        ClientSettingsRuntime settings = success(ClientSettingsRuntime.create(runtime(), profiles,
                directory.resolve("model-metadata.json"), Map.of("KEY", "private-sentinel"),
                Runnable::run, null, Clock.systemUTC(), GuideDisplayConfig.defaults()));
        try {
            var view = settings.settings().snapshot().models().profiles().getFirst();
            assertTrue(view.available());
            assertEquals(1_050_000, view.effectiveContextWindowTokens());
            assertEquals(null, view.definition().contextWindowTokens());
            assertEquals(1_050_000, settings.models().contextSpec("main").orElseThrow()
                    .budget().contextWindowTokens());
            assertInstanceOf(ToolResult.Success.class, settings.settings().saveModels(config).join());
            assertFalse(Files.readString(profiles).contains("contextWindowTokens"));
            assertFalse(settings.settings().snapshot().toString().contains("private-sentinel"));
            assertFalse(Files.exists(directory.resolve("model-metadata.json")));
        } finally { settings.closeAsync().join(); }
    }

    @Test
    void publicUiFixtureCreatesNativeRuntimeWithDisabledManualAndAutomaticProfiles(
            @TempDir Path directory) throws Exception {
        Path profiles = directory.resolve("models.json");
        Files.writeString(profiles, """
                {
                  "defaultProfileId":"e2e-fixture",
                  "profiles":[
                    {"id":"e2e-fixture","displayName":"Offline UI fixture","enabled":true,
                     "protocol":"openai_chat","baseUrl":"http://127.0.0.1:18765/v1/",
                     "model":"openallay-e2e-fixture","credentialRef":"env:OPENALLAY_E2E_FIXTURE_KEY",
                     "contextWindowTokens":256000,"maxOutputTokens":8192,
                     "connectTimeoutSeconds":10,"requestTimeoutSeconds":120},
                    {"id":"luna-manual","displayName":"Luna · manual 1M","enabled":false,
                     "protocol":"openai_chat","baseUrl":"https://api.openai.com/v1/",
                     "model":"gpt-6-luna","credentialRef":"env:OPENALLAY_UI_UNUSED_KEY",
                     "contextWindowTokens":1000000,"maxOutputTokens":8192,
                     "connectTimeoutSeconds":10,"requestTimeoutSeconds":120},
                    {"id":"reference-auto","displayName":"Reference · automatic","enabled":false,
                     "protocol":"openai_chat","baseUrl":"https://api.openai.com/v1/",
                     "model":"gpt-4.1","credentialRef":"env:OPENALLAY_UI_UNUSED_KEY",
                     "contextWindowTokens":null,"maxOutputTokens":8192,
                     "connectTimeoutSeconds":10,"requestTimeoutSeconds":120}
                  ]
                }
                """);
        ToolResult<ClientSettingsRuntime> created = ClientSettingsRuntime.create(runtime(), profiles,
                directory.resolve("model-metadata.json"),
                Map.of("OPENALLAY_E2E_FIXTURE_KEY", "offline-fixture-stub"), Runnable::run, null,
                Clock.systemUTC(), GuideDisplayConfig.defaults());
        if (created instanceof ToolResult.Failure<ClientSettingsRuntime> failure) {
            throw new AssertionError("public fixture native runtime failed: "
                    + failure.code() + " / " + failure.message());
        }
        ClientSettingsRuntime settings = success(created);
        try {
            assertEquals("e2e-fixture", settings.models().defaultProfileId());
            assertTrue(settings.models().contextSpec("e2e-fixture").isPresent());
            assertEquals(256_000, settings.models().contextSpec("e2e-fixture").orElseThrow()
                    .budget().contextWindowTokens());
            var views = settings.settings().snapshot().models().profiles();
            assertEquals(3, views.size());
            assertTrue(views.get(0).available());
            assertEquals("model_disabled", views.get(1).failure().code());
            assertEquals("model_disabled", views.get(2).failure().code());
            assertEquals(1_000_000, views.get(1).definition().contextWindowTokens());
            assertEquals(null, views.get(2).definition().contextWindowTokens());
            assertFalse(settings.settings().snapshot().toString().contains("offline-fixture-stub"));
        } finally {
            settings.closeAsync().join();
        }
    }

    @Test
    void sharedDisplayRuntimePersistsDebugModeForSettingsAndGuide(@TempDir Path directory)
            throws Exception {
        Path displayPath = directory.resolve("display.json");
        GuideDisplayRuntime display = new GuideDisplayRuntime(displayPath);
        ClientSettingsHistoryBinding history = new ClientSettingsHistoryBinding();
        ToolResult<ClientSettingsRuntime> created = ClientSettingsRuntime.create(
                runtime(),
                directory.resolve("models.json"),
                directory.resolve("model-metadata.json"),
                directory.resolve("capabilities.json"),
                directory.resolve("recipes.json"),
                new dev.openallay.recipe.config.RecipeClientRuntime(
                        directory.resolve("recipes.json")),
                Map.of(),
                Runnable::run,
                null,
                Clock.systemUTC(),
                display,
                history);
        if (!(created instanceof ToolResult.Success<ClientSettingsRuntime> success)) {
            throw new AssertionError("expected native settings runtime creation to succeed");
        }
        ClientSettingsRuntime settings = success.value();

        assertInstanceOf(ToolResult.Success.class, settings.settings()
                .saveDisplay(new GuideDisplayConfig(
                        true, true,
                GuideDisplayConfig.DEFAULT_ASSISTANT_NAME)).join());

        assertTrue(display.config().debugMode());
        assertTrue(settings.settings().snapshot().display().debugMode());
        assertTrue(Files.exists(displayPath));
        assertTrue(new GuideDisplayRuntime(displayPath).config().debugMode());
        settings.closeAsync().join();
    }

    @Test
    void playerCredentialSaveRotatesLocalSecretAndSurvivesRestart(@TempDir Path directory)
            throws Exception {
        Path profiles = directory.resolve("models.json");
        Path credentials = directory.resolve("credentials.sqlite3");
        ClientSettingsRuntime first = success(ClientSettingsRuntime.create(
                runtime(),
                profiles,
                directory.resolve("model-metadata.json"),
                Map.of(),
                Runnable::run,
                null,
                Clock.systemUTC(),
                GuideDisplayConfig.defaults()));
        ModelProfileDefinition definition = new ModelProfileDefinition(
                "main",
                "Main",
                true,
                ModelProtocol.OPENAI_CHAT,
                java.net.URI.create("https://provider.example/v1"),
                "vendor/model",
                "env:UNSET_PLACEHOLDER",
                256_000,
                4_096,
                Duration.ofSeconds(30),
                Duration.ofSeconds(300),
                null);
        ModelProfilesConfig config = new ModelProfilesConfig(
                "main", List.of(definition));

        assertInstanceOf(ToolResult.Success.class, first.settings()
                .saveModels(config, "main", SecretValue.of("first-secret-value")).join());
        assertInstanceOf(ToolResult.Success.class, first.settings()
                .saveModels(
                        first.settings().snapshot().models().config(),
                        "main",
                        SecretValue.of("second-secret-value"))
                .join());

        String encoded = Files.readString(profiles);
        assertFalse(encoded.contains("schemaVersion"));
        assertTrue(encoded.contains("\"credentialRef\":\"local:"));
        assertFalse(encoded.contains("first-secret-value"));
        assertFalse(encoded.contains("second-secret-value"));
        assertFalse(first.settings().snapshot().toString().contains("second-secret-value"));
        try (var connection = java.sql.DriverManager.getConnection(
                        "jdbc:sqlite:" + credentials);
                var result = connection.createStatement().executeQuery(
                        "select count(*) from credentials")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
        first.closeAsync().join();

        ClientSettingsRuntime restarted = success(ClientSettingsRuntime.create(
                runtime(),
                profiles,
                directory.resolve("model-metadata.json"),
                Map.of(),
                Runnable::run,
                null,
                Clock.systemUTC(),
                GuideDisplayConfig.defaults()));
        assertTrue(restarted.settings().snapshot().models().profiles().getFirst().available());
        assertTrue(restarted.settings().snapshot().models().profiles().getFirst()
                .credentialPresent());
        restarted.closeAsync().join();
    }

    @Test
    void catalogUsesTypedKeyBeforeSavedCredentialAndReportsActualStoredPresence(
            @TempDir Path directory) throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"data\":[{\"id\":\"mimo-v2.5-pro\"}]}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ClientSettingsRuntime settings = success(ClientSettingsRuntime.create(
                runtime(),
                directory.resolve("models.json"),
                directory.resolve("model-metadata.json"),
                Map.of(),
                Runnable::run,
                null,
                Clock.systemUTC(),
                GuideDisplayConfig.defaults()));
        try {
            URI baseUri = URI.create(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            ModelProfileDefinition profile = new ModelProfileDefinition(
                    "main",
                    "Main",
                    true,
                    ModelProtocol.OPENAI_CHAT,
                    baseUri,
                    "typed-model",
                    "env:UNSET_PLACEHOLDER",
                    256_000,
                    4_096,
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(5),
                    null);
            ModelProfilesConfig config = new ModelProfilesConfig(
                    "main", List.of(profile));
            assertInstanceOf(ToolResult.Success.class, settings.settings()
                    .saveModels(config, "main", SecretValue.of("saved-catalog-key")).join());
            ModelProfileDefinition saved = settings.settings().snapshot()
                    .models().config().profiles().getFirst();
            assertTrue(settings.settings().snapshot().models().profiles().getFirst()
                    .credentialPresent());
            ModelCatalogRequest request = new ModelCatalogRequest(
                    saved.id(),
                    saved.protocol(),
                    saved.baseUri(),
                    saved.credentialRef(),
                    saved.connectTimeout(),
                    saved.requestTimeout());

            ToolResult<ModelCatalog> stored = settings.settings()
                    .fetchModelCatalog(request, null).join();
            assertEquals(List.of("mimo-v2.5-pro"), successValue(stored).modelIds());
            assertEquals("Bearer saved-catalog-key", authorization.get());

            ToolResult<ModelCatalog> typed = settings.settings()
                    .fetchModelCatalog(request, SecretValue.of("typed-catalog-key")).join();
            assertEquals(List.of("mimo-v2.5-pro"), successValue(typed).modelIds());
            assertEquals("Bearer typed-catalog-key", authorization.get());
            assertFalse(typed.toString().contains("typed-catalog-key"));

            ModelProfileDefinition missing = new ModelProfileDefinition(
                    saved.id(),
                    saved.displayName(),
                    saved.enabled(),
                    saved.protocol(),
                    saved.baseUri(),
                    saved.model(),
                    dev.openallay.model.config.CredentialReference.local(UUID.randomUUID()).encoded(),
                    saved.contextWindowTokens(),
                    saved.maxOutputTokens(),
                    saved.connectTimeout(),
                    saved.requestTimeout(),
                    saved.metadata());
            assertInstanceOf(ToolResult.Success.class, settings.settings()
                    .saveModels(new ModelProfilesConfig(
                            "main", List.of(missing)))
                    .join());
            assertFalse(settings.settings().snapshot().models().profiles().getFirst()
                    .credentialPresent());
            authorization.set(null);
            ToolResult<ModelCatalog> absent = settings.settings().fetchModelCatalog(
                    new ModelCatalogRequest(
                            missing.id(), missing.protocol(), missing.baseUri(),
                            missing.credentialRef(), missing.connectTimeout(), missing.requestTimeout()),
                    null).join();
            assertEquals("model_catalog_credential_missing",
                    assertInstanceOf(ToolResult.Failure.class, absent).code());
            assertEquals(null, authorization.get());
        } finally {
            settings.closeAsync().join();
            server.stop(0);
        }
    }

    @Test
    void commandEnablePreservesIndependentSkillDenyPolicyAndCapturedAuthority(@TempDir Path directory) {
        ToolRegistry tools = new ToolRegistry();
        tools.register("test:tools", List.of(new dev.openallay.tool.Tool<String, String>() {
            public dev.openallay.tool.ToolDescriptor<String, String> descriptor() {
                return new dev.openallay.tool.ToolDescriptor<>("openallay:run_javascript",
                        "Test JavaScript", String.class, String.class, dev.openallay.tool.ToolAccess.READ_ONLY);
            }
            public ToolResult<String> invoke(dev.openallay.context.ToolInvocationContext context, String value) {
                return new ToolResult.Success<>(value);
            }
        }));
        SkillRepository skills = new SkillRepository(new SkillParser(), List.of("openallay:run_javascript"));
        tools.register("test:skills", List.of(new dev.openallay.skill.LoadSkillTool(skills)));
        OpenAllayRuntime product = new OpenAllayRuntime(new FakePlatform(), tools, new KnowledgeRegistry(),
                new dev.openallay.integration.patchouli.PatchouliMultiblockStore(), skills,
                new DevelopmentToolInspector(tools), null);
        ClientSettingsRuntime settings = success(ClientSettingsRuntime.create(product,
                directory.resolve("models.json"), directory.resolve("model-metadata.json"),
                Map.of(), Runnable::run, null, Clock.systemUTC(), GuideDisplayConfig.defaults()));
        try {
            assertTrue(skills.find("inspect-game-state").isPresent());
            var policy = new dev.openallay.capability.CapabilityPolicy(java.util.Set.of(), java.util.Set.of("inspect-game-state"));
            assertInstanceOf(ToolResult.Success.class, settings.settings().saveCapabilities(policy).join());
            var frozen = settings.models().capabilities();
            assertFalse(frozen.skills().find("inspect-game-state").isPresent());

            assertInstanceOf(ToolResult.Success.class, settings.settings().saveExperimentalCommands(true).join());

            assertEquals(policy, settings.settings().snapshot().capabilities().policy());
            assertEquals(policy, settings.models().capabilities().policy());
            assertFalse(settings.models().capabilities().skills().find("inspect-game-state").isPresent());
            assertFalse(frozen.skills().find("inspect-game-state").isPresent());
            assertFalse(frozen.skills().find("run-game-commands").isPresent());
            assertTrue(settings.models().capabilities().skills().find("run-game-commands").isPresent());
        } finally {
            settings.closeAsync().join();
        }
    }

    @Test
    void settingsStartupKeepsExtensionSkillsWithOtherInstalledLegacyMods(@TempDir Path directory) {
        ToolRegistry tools = new ToolRegistry();
        SkillRepository skills = new SkillRepository(new SkillParser(), List.of());
        var source = new dev.openallay.skill.SkillSource("example:extension", "extension-guide/SKILL.md",
                Map.of("extension-guide/SKILL.md", """
                        ---
                        name: extension-guide
                        description: Extension guide
                        metadata:
                          openallay/required-mods: example_loaded_mod
                        ---
                        Read [details](references/details.md).
                        """, "extension-guide/references/details.md", "Original extension reference"),
                dev.openallay.skill.SkillSource.Origin.EXTERNAL);
        skills.registerExternal(List.of(source), java.util.Set.of("example_loaded_mod"));
        tools.register("test:skills", List.of(new dev.openallay.skill.LoadSkillTool(skills)));
        PlatformService platform = new PlatformService() {
            public String platformName() { return "test"; }
            public String gameVersion() { return "test"; }
            public boolean isModLoaded(String id) { return id.equals("example_loaded_mod"); }
            public boolean isDevelopmentEnvironment() { return true; }
            public List<dev.openallay.platform.InstalledModMetadata> installedMods() {
                return List.of(new dev.openallay.platform.InstalledModMetadata("example_loaded_mod",
                        "Example", "1.0", "", List.of(), List.of(), Map.of(), "client", List.of()));
            }
        };
        OpenAllayRuntime product = new OpenAllayRuntime(platform, tools, new KnowledgeRegistry(),
                new dev.openallay.integration.patchouli.PatchouliMultiblockStore(), skills,
                new DevelopmentToolInspector(tools), null);
        ClientSettingsRuntime settings = success(ClientSettingsRuntime.create(product,
                directory.resolve("models.json"), directory.resolve("model-metadata.json"),
                Map.of(), Runnable::run, null, Clock.systemUTC(), GuideDisplayConfig.defaults()));
        try {
            assertTrue(settings.settings().snapshot().skills().find("extension-guide").isPresent());
            assertEquals("Original extension reference", skills.find("extension-guide").orElseThrow()
                    .references().get("references/details.md"));
            assertInstanceOf(ToolResult.Success.class, settings.settings().reloadSkills(true).join());
            assertTrue(settings.settings().snapshot().skills().find("extension-guide").isPresent());
            assertEquals("Original extension reference", skills.find("extension-guide").orElseThrow()
                    .references().get("references/details.md"));
        } finally {
            settings.closeAsync().join();
        }
    }

    @Test
    void nativeRuntimeObservesPublishedSourcesAndLateBoundHistoryWithoutProviderCallsOnRefresh(
            @TempDir Path directory) throws Exception {
        OpenAllayRuntime product = runtime();
        ClientSettingsHistoryBinding binding = new ClientSettingsHistoryBinding();
        GuideDisplayRuntime display = new GuideDisplayRuntime(directory.resolve("display.json"));
        ClientSettingsRuntime settings = success(ClientSettingsRuntime.create(product,
                directory.resolve("models.json"), directory.resolve("model-metadata.json"),
                directory.resolve("capabilities.json"), directory.resolve("recipes.json"),
                new dev.openallay.recipe.config.RecipeClientRuntime(directory.resolve("recipes.json")),
                Map.of(), Runnable::run, null, Clock.systemUTC(), display, binding));
        java.util.concurrent.atomic.AtomicInteger loads = new java.util.concurrent.atomic.AtomicInteger();
        try {
            settings.settings().saveDisplay(new GuideDisplayConfig(true, true, GuideDisplayConfig.DEFAULT_ASSISTANT_NAME)).join();
            assertFalse(settings.settings().snapshot().diagnostics().debug().orElseThrow().sourcesKnown());
            dev.openallay.knowledge.KnowledgeSourceProvider provider = new dev.openallay.knowledge.KnowledgeSourceProvider() {
                @Override public String sourceId() { return "patchouli"; }
                @Override public dev.openallay.knowledge.KnowledgeLoad load() {
                    loads.incrementAndGet();
                    var evidence = new dev.openallay.context.EvidenceMetadata(
                            dev.openallay.context.DataAuthority.RESOURCE_ASSET,
                            dev.openallay.context.DataCompleteness.COMPLETE,
                            java.time.Instant.EPOCH, "patchouli:resources", "patchouli:parser",
                            "fixture", "fixture", Map.of());
                    return new dev.openallay.knowledge.KnowledgeLoad(List.of(), List.of(), List.of(evidence));
                }
            };
            assertTrue(product.knowledge().reload(List.of(provider)));
            settings.settings().refreshRuntimeState();
            var sources = settings.settings().snapshot().diagnostics().debug().orElseThrow();
            assertTrue(sources.sourcesKnown());
            assertEquals(1, sources.sources().size());
            assertEquals(0, sources.sources().getFirst().itemCount());
            assertEquals(dev.openallay.settings.diagnostics.SettingsDiagnosticsAggregator.SourceState.AVAILABLE,
                    sources.sources().getFirst().state());
            long generation = settings.settings().snapshot().generation();
            settings.settings().refreshRuntimeState();
            assertEquals(1, loads.get());
            assertEquals(generation, settings.settings().snapshot().generation());
            assertTrue(settings.settings().snapshot().diagnostics().debug().orElseThrow().guide().isEmpty());

            UUID actor = UUID.randomUUID();
            var scope = dev.openallay.guide.history.GuideHistoryScope.derive(
                    actor, dev.openallay.guide.history.GuideHistoryScope.Kind.SINGLEPLAYER, "fixture-world");
            dev.openallay.guide.GuideLocalEndpoint local = new dev.openallay.guide.GuideLocalEndpoint() {
                private dev.openallay.guide.GuideContextEstimate estimate;
                @Override public java.util.Set<dev.openallay.context.ContextCapability> requiredContext() {
                    return java.util.Set.of();
                }
                @Override public List<dev.openallay.guide.GuideClientModelProfile> profiles() {
                    return List.of(new dev.openallay.guide.GuideClientModelProfile(
                            defaultProfileId(), "Client model", true, true, "test-model", null));
                }
                @Override public java.util.Optional<dev.openallay.guide.GuideContextSpec> contextSpec(String profileId) {
                    return java.util.Optional.of(new dev.openallay.guide.GuideContextSpec(
                            new dev.openallay.agent.context.ContextBudget(64_000, 4_096), 1_000, "test-model"));
                }
                @Override public java.util.Optional<dev.openallay.guide.GuideContextEstimate> contextEstimate(
                        String profileId, UUID actorId, String sessionId) {
                    return java.util.Optional.ofNullable(estimate);
                }
                @Override public java.util.concurrent.CompletableFuture<dev.openallay.agent.AgentResult> ask(
                        UUID actorId, String sessionId, UUID requestId, String question,
                        dev.openallay.context.ToolInvocationContext context,
                        java.util.function.Consumer<dev.openallay.agent.AgentEvent> events) {
                    var captured = contextSpec(defaultProfileId()).orElseThrow();
                    estimate = new dev.openallay.guide.GuideContextEstimate(
                            requestId, 777, captured.budget(), captured.canonicalModelId());
                    events.accept(new dev.openallay.agent.AgentEvent.FinalText("done"));
                    return java.util.concurrent.CompletableFuture.completedFuture(new dev.openallay.agent.AgentResult(
                            dev.openallay.agent.AgentState.COMPLETED, "done", null, null, null));
                }
                @Override public boolean cancel(UUID actorId, String sessionId) { return false; }
                @Override public void clearSession(UUID actorId, String sessionId) { estimate = null; }
                @Override public void clearActor(UUID actorId) { estimate = null; }
            };
            dev.openallay.guide.GuideRemoteEndpoint remote = new dev.openallay.guide.GuideRemoteEndpoint() {
                @Override public boolean serverModelAvailable() { return false; }
                @Override public boolean serverToolsAvailable() { return false; }
                @Override public boolean ask(UUID requestId, String sessionId, String question,
                        java.util.function.Consumer<dev.openallay.agent.AgentEvent> events) { return false; }
                @Override public boolean cancel(UUID requestId) { return false; }
                @Override public void disconnect() {}
            };
            dev.openallay.guide.history.GuideHistoryAccess history = new dev.openallay.guide.history.GuideHistoryAccess() {
                @Override public java.util.concurrent.CompletableFuture<java.util.Optional<dev.openallay.guide.history.GuideHistoryMetadata>> metadata(
                        dev.openallay.guide.history.GuideHistoryScope ignored) {
                    return java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty());
                }
                @Override public java.util.concurrent.CompletableFuture<dev.openallay.guide.history.GuideHistoryContextSeed> context(
                        dev.openallay.guide.history.GuideHistoryContextRequest request) {
                    return java.util.concurrent.CompletableFuture.completedFuture(
                            new dev.openallay.guide.history.GuideHistoryContextSeed(request.sessionId(), List.of(), List.of(), 0));
                }
                @Override public java.util.concurrent.CompletableFuture<Void> commit(
                        dev.openallay.guide.history.GuideHistoryCommit commit) {
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
                @Override public java.util.concurrent.CompletableFuture<Void> delete(
                        dev.openallay.guide.history.GuideHistoryDeleteScope ignored) {
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
                @Override public java.util.concurrent.CompletableFuture<Void> resetDatabase() {
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
                @Override public java.util.concurrent.CompletableFuture<Void> flush() {
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
                @Override public dev.openallay.guide.history.GuideHistoryActivity activity() {
                    return dev.openallay.guide.history.GuideHistoryActivity.idle();
                }
            };
            dev.openallay.guide.GuideContextProvider contexts = new dev.openallay.guide.GuideContextProvider() {
                @Override public ToolResult<dev.openallay.context.ToolInvocationContext> capture(
                        java.util.Set<dev.openallay.context.ContextCapability> capabilities, String correlation) {
                    product.knowledge().reload(List.of(provider));
                    return new ToolResult.Success<>(
                            dev.openallay.context.ToolInvocationContext.developmentConsole(correlation));
                }
                @Override public void clearConnectionState() { product.knowledge().clearConnectionState(); }
            };
            var manager = new dev.openallay.guide.GuideServiceManager(local, remote, contexts,
                    Runnable::run, Clock.systemUTC(), new com.google.gson.Gson(), history, ignored -> scope);
            binding.bind(manager);
            var guide = manager.forActor(actor);
            settings.settings().refreshRuntimeState();
            assertFalse(settings.settings().snapshot().diagnostics().debug().orElseThrow().sourcesKnown());
            assertEquals(null, settings.settings().snapshot().diagnostics().debug().orElseThrow()
                    .guide().orElseThrow().context().estimatedProjectionTokens());
            assertInstanceOf(ToolResult.Success.class, guide.ask("normal small request").join());
            settings.settings().refreshRuntimeState();
            var diagnostics = settings.settings().snapshot().diagnostics().debug().orElseThrow().guide().orElseThrow();
            assertEquals(0, diagnostics.activeRequestCount());
            assertEquals(0, diagnostics.pendingWrites());
            assertEquals(0, diagnostics.context().checkpointCount());
            assertEquals(777L, diagnostics.context().estimatedProjectionTokens());
            guide.selectSession("other").join();
            settings.settings().refreshRuntimeState();
            assertEquals(null, settings.settings().snapshot().diagnostics().debug().orElseThrow()
                    .guide().orElseThrow().context().estimatedProjectionTokens());
            manager.disconnect().join();
            settings.settings().refreshRuntimeState();
            assertTrue(settings.settings().snapshot().diagnostics().debug().orElseThrow().guide().isEmpty());
            assertFalse(settings.settings().snapshot().diagnostics().debug().orElseThrow().sourcesKnown());
            assertTrue(product.knowledge().snapshot().documents().isEmpty());
            manager.forActor(actor);
            settings.settings().refreshRuntimeState();
            assertFalse(settings.settings().snapshot().diagnostics().debug().orElseThrow().sourcesKnown());
            assertEquals(2, loads.get());
        } finally {
            settings.closeAsync().join();
        }
    }

    @SuppressWarnings("unchecked")
    private static ClientSettingsRuntime success(ToolResult<ClientSettingsRuntime> result) {
        return ((ToolResult.Success<ClientSettingsRuntime>)
                assertInstanceOf(ToolResult.Success.class, result)).value();
    }

    @SuppressWarnings("unchecked")
    private static <T> T successValue(ToolResult<T> result) {
        return ((ToolResult.Success<T>) assertInstanceOf(ToolResult.Success.class, result)).value();
    }

    private static OpenAllayRuntime runtime() {
        ToolRegistry tools = new ToolRegistry();
        return new OpenAllayRuntime(
                new FakePlatform(),
                tools,
                new KnowledgeRegistry(),
                new dev.openallay.integration.patchouli.PatchouliMultiblockStore(),
                new SkillRepository(new SkillParser(), List.of()),
                new DevelopmentToolInspector(tools),
                null);
    }

    private static final class FakePlatform implements PlatformService {
        @Override
        public String platformName() {
            return "test";
        }

        @Override
        public boolean isModLoaded(String modId) {
            return false;
        }

        @Override
        public boolean isDevelopmentEnvironment() {
            return true;
        }

        @Override
        public String gameVersion() {
            return "test";
        }
    }
}
