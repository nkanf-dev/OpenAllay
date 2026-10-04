package dev.openallay.settings;

import com.google.gson.Gson;
import dev.openallay.FeatureServices;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.client.ClientModelRuntimeRegistry;
import dev.openallay.guide.GuideFailure;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideDisplayRuntime;
import dev.openallay.model.ProviderModelClients;
import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ModelProfilesConfigLoader;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.ResolvedModelProfile;
import dev.openallay.model.config.CredentialReference;
import dev.openallay.model.config.CredentialResolver;
import dev.openallay.model.config.LocalCredentialStore;
import dev.openallay.model.metadata.ModelMetadataBootstrap;
import dev.openallay.model.metadata.ModelMetadataCache;
import dev.openallay.model.metadata.ModelMetadataUpdate;
import dev.openallay.settings.model.ModelConnectionProbe;
import dev.openallay.settings.model.ModelSettingsBackend;
import dev.openallay.recipe.config.RecipeClientRuntime;
import dev.openallay.settings.capability.CapabilitySettingsBackend;
import dev.openallay.settings.capability.CapabilitySettingsView;
import dev.openallay.settings.capability.RecipeSettingsBackend;
import dev.openallay.settings.capability.RecipeSettingsView;
import dev.openallay.settings.skill.SkillSettingsBackend;
import dev.openallay.settings.extension.ExtensionSettingsBackend;
import dev.openallay.skill.AgentSkillManager;
import dev.openallay.skill.BundledSkillLoader;
import dev.openallay.skill.ManageSkillTool;
import dev.openallay.skill.SkillParser;
import dev.openallay.tool.ToolResult;
import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.script.command.CommandCapabilityConfigStore;
import dev.openallay.script.UnrestrictedJavascriptConfig;
import dev.openallay.script.UnrestrictedJavascriptConfigStore;
import dev.openallay.script.UnrestrictedJavascriptRuntime;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Loader-neutral lifecycle bundle for the model registry and native settings owner. */
public record ClientSettingsRuntime(
        ClientModelRuntimeRegistry models,
        ClientSettingsService settings,
        UnrestrictedJavascriptRuntime unrestrictedJavascript) {
    public ClientSettingsRuntime {
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(unrestrictedJavascript, "unrestrictedJavascript");
    }

    public static ToolResult<ClientSettingsRuntime> create(
            FeatureServices product,
            Path profilesPath,
            Path metadataCachePath,
            Map<String, String> environment,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension,
            Clock clock,
            GuideDisplayConfig display) {
        Path configDirectory = profilesPath.toAbsolutePath().normalize().getParent();
        if (configDirectory == null) {
            return new ToolResult.Failure<>(
                    "settings_unavailable", "Native settings are unavailable");
        }
        Path recipesPath = configDirectory.resolve("tools").resolve("recipes-options.json");
        return create(
                product,
                profilesPath,
                metadataCachePath,
                configDirectory.resolve("capabilities.json"),
                recipesPath,
                new RecipeClientRuntime(recipesPath),
                environment,
                dispatcher,
                extension,
                clock,
                display);
    }

    public static ToolResult<ClientSettingsRuntime> create(
            FeatureServices product,
            Path profilesPath,
            Path metadataCachePath,
            Path capabilitiesPath,
            Path recipesPath,
            RecipeClientRuntime recipeRuntime,
            Map<String, String> environment,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension,
            Clock clock,
            GuideDisplayConfig display) {
        return createInternal(
                product,
                profilesPath,
                metadataCachePath,
                capabilitiesPath,
                recipesPath,
                recipeRuntime,
                environment,
                dispatcher,
                extension,
                clock,
                display,
                unavailableDisplayActions(),
                new ClientSettingsHistoryBinding(),
                null);
    }

    public static ToolResult<ClientSettingsRuntime> create(
            FeatureServices product,
            Path profilesPath,
            Path metadataCachePath,
            Path capabilitiesPath,
            Path recipesPath,
            RecipeClientRuntime recipeRuntime,
            Map<String, String> environment,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension,
            Clock clock,
            GuideDisplayRuntime display,
            ClientSettingsService.HistoryActions historyActions) {
        Objects.requireNonNull(display, "display");
        ClientSettingsService.DisplayActions displayActions =
                new ClientSettingsService.DisplayActions() {
                    @Override
                    public ToolResult<GuideDisplayConfig> saveDisplay(
                            GuideDisplayConfig candidate) {
                        return display.save(candidate);
                    }

                    @Override
                    public ToolResult<GuideDisplayConfig> reloadDisplay() {
                        return display.reload();
                    }
                };
        return createInternal(
                product,
                profilesPath,
                metadataCachePath,
                capabilitiesPath,
                recipesPath,
                recipeRuntime,
                environment,
                dispatcher,
                extension,
                clock,
                display.config(),
                displayActions,
                historyActions,
                display.failure());
    }

    private static ToolResult<ClientSettingsRuntime> createInternal(
            FeatureServices product,
            Path profilesPath,
            Path metadataCachePath,
            Path capabilitiesPath,
            Path recipesPath,
            RecipeClientRuntime recipeRuntime,
            Map<String, String> environment,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension,
            Clock clock,
            GuideDisplayConfig display,
            ClientSettingsService.DisplayActions displayActions,
            ClientSettingsService.HistoryActions historyActions,
            GuideFailure displayFailure) {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(dispatcher, "dispatcher");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(capabilitiesPath, "capabilitiesPath");
        Objects.requireNonNull(recipesPath, "recipesPath");
        Objects.requireNonNull(recipeRuntime, "recipeRuntime");
        Objects.requireNonNull(displayActions, "displayActions");
        Objects.requireNonNull(historyActions, "historyActions");

        Map<String, String> environmentSnapshot = Map.copyOf(environment);
        LocalCredentialStore credentialStore = new LocalCredentialStore(
                profilesPath.toAbsolutePath().normalize().resolveSibling("credentials.sqlite3"),
                clock);
        CredentialResolver credentials = CredentialResolver.composite(
                credentialStore, environmentSnapshot);
        ToolResult<ModelProfilesConfigLoader.Load> loaded = new ModelProfilesConfigLoader()
                .load(
                        profilesPath,
                        credentials,
                        Map.of());
        ModelProfilesConfigLoader.Load initial;
        SettingsNotice startupNotice = null;
        if (loaded instanceof ToolResult.Success<ModelProfilesConfigLoader.Load> success) {
            initial = success.value();
        } else {
            ToolResult.Failure<ModelProfilesConfigLoader.Load> failure =
                    (ToolResult.Failure<ModelProfilesConfigLoader.Load>) loaded;
            initial = unconfigured();
            startupNotice = SettingsNotice.failure(failure.code(), failure.message());
        }
        if (startupNotice == null && displayFailure != null) {
            startupNotice = SettingsNotice.failure(
                    displayFailure.code(), "Display settings are invalid");
        }

        try {
            Gson gson = new Gson();
            Path configDirectory = profilesPath.toAbsolutePath().normalize().getParent();
            if (configDirectory == null) {
                throw new IllegalArgumentException("Model profiles require a configuration directory");
            }
            CommandCapabilityConfigStore commandStore = new CommandCapabilityConfigStore(
                    configDirectory.resolve("experimental-commands.json"));
            ToolResult<CommandCapabilityConfig> loadedCommands = commandStore.reload();
            CommandCapabilityConfig initialCommands;
            if (loadedCommands instanceof ToolResult.Success<CommandCapabilityConfig> success) {
                initialCommands = success.value();
            } else {
                ToolResult.Failure<CommandCapabilityConfig> failure =
                        (ToolResult.Failure<CommandCapabilityConfig>) loadedCommands;
                initialCommands = CommandCapabilityConfig.defaults();
                if (startupNotice == null) {
                    startupNotice = SettingsNotice.failure(failure.code(), failure.message());
                }
            }
            product.commands().replace(initialCommands);
            UnrestrictedJavascriptConfigStore unrestrictedStore = new UnrestrictedJavascriptConfigStore(
                    configDirectory.resolve("unrestricted-javascript.json"));
            ToolResult<UnrestrictedJavascriptConfig> loadedUnrestricted = unrestrictedStore.reload();
            UnrestrictedJavascriptConfig initialUnrestricted = loadedUnrestricted instanceof ToolResult.Success<UnrestrictedJavascriptConfig> s
                    ? s.value() : UnrestrictedJavascriptConfig.defaults();
            UnrestrictedJavascriptRuntime unrestrictedRuntime = new UnrestrictedJavascriptRuntime();
            unrestrictedRuntime.replace(initialUnrestricted);
            if (loadedUnrestricted instanceof ToolResult.Failure<UnrestrictedJavascriptConfig> f && startupNotice == null) {
                startupNotice = SettingsNotice.failure(f.code(), f.message());
            }
            product.skills().setRuntimeDisabledSkills(initialCommands.enabled()
                    ? Set.of()
                    : Set.of("run-game-commands"));
            Set<String> installedSkillMods = installedSkillMods(product);
            SkillSettingsBackend skills = new SkillSettingsBackend(
                    configDirectory.resolve("skills"), product.skills(), installedSkillMods);
            ExtensionSettingsBackend extensions = new ExtensionSettingsBackend(
                    configDirectory,
                    managedModsRoot(configDirectory),
                    product.extensions(),
                    product.javascriptModules());
            if (product.tools().find("openallay:manage_skill").isEmpty()) {
                Set<String> availableTools = product.tools().descriptors().stream()
                        .map(descriptor -> descriptor.id())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                product.tools().register(
                        "openallay:managed-skills",
                        List.of(new ManageSkillTool(new AgentSkillManager(
                                configDirectory.resolve("skills"),
                                product.skills(),
                                new SkillParser(),
                                new BundledSkillLoader().load(),
                                installedSkillMods,
                                availableTools))));
            }
            java.util.concurrent.atomic.AtomicReference<GuideDisplayConfig> activeDisplay =
                    new java.util.concurrent.atomic.AtomicReference<>(display);
            ClientSettingsService.DisplayActions traceDisplayActions = displayActions;
            ClientSettingsService.DisplayActions wiredDisplayActions =
                    new ClientSettingsService.DisplayActions() {
                        @Override
                        public ToolResult<GuideDisplayConfig> saveDisplay(
                                GuideDisplayConfig candidate) {
                            ToolResult<GuideDisplayConfig> result =
                                    traceDisplayActions.saveDisplay(candidate);
                            if (result instanceof ToolResult.Success<GuideDisplayConfig> success) {
                                activeDisplay.set(success.value());
                            }
                            return result;
                        }

                        @Override
                        public ToolResult<GuideDisplayConfig> reloadDisplay() {
                            ToolResult<GuideDisplayConfig> result =
                                    traceDisplayActions.reloadDisplay();
                            if (result instanceof ToolResult.Success<GuideDisplayConfig> success) {
                                activeDisplay.set(success.value());
                            }
                            return result;
                        }
                    };
            ClientModelRuntimeRegistry registry = ClientModelRuntimeRegistry.create(
                    product,
                    initial,
                    gson,
                    dispatcher,
                    extension,
                    configDirectory.resolve("traces"),
                    () -> activeDisplay.get().debugMode());
            CapabilitySettingsBackend capabilities = new CapabilitySettingsBackend(
                    capabilitiesPath, product, registry);
            ClientSettingsService.CommandActions commandActions =
                    new ClientSettingsService.CommandActions() {
                        @Override
                        public ToolResult<CommandCapabilityConfig> save(
                                CommandCapabilityConfig candidate) {
                            return publishCommandConfig(
                                    commandStore.save(candidate),
                                    commandStore,
                                    product,
                                    capabilities);
                        }

                        @Override
                        public ToolResult<CommandCapabilityConfig> reload() {
                            return publishCommandConfig(
                                    commandStore.reload(),
                                    commandStore,
                                    product,
                                    capabilities);
                        }
            };
            RecipeSettingsBackend recipes = new RecipeSettingsBackend(recipesPath, recipeRuntime);
            RecipeSettingsView initialRecipes = recipes.currentView();
            CapabilitySettingsView initialCapabilities = capabilities.currentView();
            ModelConnectionProbe probe = new ModelConnectionProbe(
                    config -> ProviderModelClients.create(config, gson),
                    clock,
                    System::nanoTime);
            ModelSettingsBackend backend = new ModelSettingsBackend(
                    profilesPath,
                    () -> environmentSnapshot,
                    registry,
                    probe,
                    credentialStore);
            backend.collectUnreferencedCredentials(initial.config());
            AtomicReference<ClientSettingsService> serviceReference = new AtomicReference<>();
            AtomicReference<ModelMetadataUpdate> pendingUpdate = new AtomicReference<>();
            ModelMetadataBootstrap metadata = new ModelMetadataBootstrap(
                    new ModelMetadataCache(metadataCachePath),
                    profilesPath,
                    environmentSnapshot,
                    credentials,
                    update -> {
                        ClientSettingsService service = serviceReference.get();
                        if (service == null) {
                            pendingUpdate.set(update);
                        } else {
                            service.acceptMetadataUpdate(update);
                        }
                    },
                    clock);
            ClientSettingsService.MetadataActions metadataActions =
                    new ClientSettingsService.MetadataActions() {
                        @Override
                        public CompletableFuture<Void> refresh() {
                            return metadata.refreshAll();
                        }

                        @Override
                        public CompletableFuture<Void> closeAsync() {
                            return metadata.closeAsync().whenComplete(
                                    (ignored, failure) -> backend.closeCredentials());
                        }
                    };
            ClientSettingsService.ModelState initialState = backend.state(initial);
            ClientSettingsService service = new ClientSettingsService(
                    display,
                    wiredDisplayActions,
                    initialState,
                    backend.presentEnvironmentNames(),
                    backend,
                    metadataActions,
                    initialCapabilities,
                    capabilities,
                    initialRecipes,
                    recipes,
                    skills.currentView(),
                    skills,
                    extensions.currentView(),
                    extensions,
                    initialCommands,
                    commandActions,
                    new ClientSettingsService.UnrestrictedJavascriptActions() {
                        public ToolResult<UnrestrictedJavascriptConfig> save(UnrestrictedJavascriptConfig c) {
                            var result = unrestrictedStore.save(c);
                            if (result instanceof ToolResult.Success<UnrestrictedJavascriptConfig> s) unrestrictedRuntime.replace(s.value());
                            return result;
                        }
                        public ToolResult<UnrestrictedJavascriptConfig> reload() {
                            var result = unrestrictedStore.reload();
                            if (result instanceof ToolResult.Success<UnrestrictedJavascriptConfig> s) unrestrictedRuntime.replace(s.value());
                            return result;
                        }
                    },
                    initialUnrestricted,
                    historyActions,
                    dispatcher,
                    command -> Thread.startVirtualThread(command),
                    startupNotice);
            service.bindKnowledgeSources(product.knowledge()::sourceSnapshot);
            serviceReference.set(service);
            ModelMetadataUpdate early = pendingUpdate.getAndSet(null);
            if (early != null) {
                service.acceptMetadataUpdate(early);
            }
            metadata.start();
            return new ToolResult.Success<>(new ClientSettingsRuntime(registry, service, unrestrictedRuntime));
        } catch (RuntimeException failure) {
            credentialStore.close();
            return new ToolResult.Failure<>(
                    "settings_unavailable", "Native settings are unavailable");
        }
    }

    public CompletableFuture<Void> closeAsync() {
        return settings.closeAsync();
    }

    private static ToolResult<CommandCapabilityConfig> publishCommandConfig(
            ToolResult<CommandCapabilityConfig> loaded,
            CommandCapabilityConfigStore store,
            FeatureServices product,
            CapabilitySettingsBackend capabilities) {
        if (loaded instanceof ToolResult.Failure<CommandCapabilityConfig> failure) {
            return failure;
        }
        CommandCapabilityConfig candidate =
                ((ToolResult.Success<CommandCapabilityConfig>) loaded).value();
        CommandCapabilityConfig prior = new CommandCapabilityConfig(
                product.commands().enabled());
        product.commands().replace(candidate);
        product.skills().setRuntimeDisabledSkills(candidate.enabled()
                ? Set.of()
                : Set.of("run-game-commands"));
        ToolResult<CapabilitySettingsView> published = capabilities.refreshCapabilities();
        if (published instanceof ToolResult.Failure<CapabilitySettingsView> failure) {
            product.commands().replace(prior);
            product.skills().setRuntimeDisabledSkills(prior.enabled()
                    ? Set.of()
                    : Set.of("run-game-commands"));
            store.save(prior);
            capabilities.refreshCapabilities();
            return new ToolResult.Failure<>(failure.code(), failure.message());
        }
        return new ToolResult.Success<>(candidate);
    }

    private static ClientSettingsService.DisplayActions unavailableDisplayActions() {
        return new ClientSettingsService.DisplayActions() {
            @Override
            public ToolResult<GuideDisplayConfig> saveDisplay(GuideDisplayConfig candidate) {
                return new ToolResult.Failure<>(
                        "display_settings_unavailable", "Display settings are unavailable");
            }

            @Override
            public ToolResult<GuideDisplayConfig> reloadDisplay() {
                return new ToolResult.Failure<>(
                        "display_settings_unavailable", "Display settings are unavailable");
            }
        };
    }

    private static ModelProfilesConfigLoader.Load unconfigured() {
        ModelProfileDefinition definition = new ModelProfileDefinition(
                "default",
                "Configure a model",
                false,
                ModelProtocol.OPENAI_CHAT,
                URI.create("https://example.invalid/v1"),
                "configure-model-id",
                CredentialReference.environment("OPENALLAY_API_KEY").encoded(),
                256_000,
                null,
                Duration.ofSeconds(30),
                Duration.ofSeconds(300),
                null);
        ModelProfilesConfig config = new ModelProfilesConfig(
                definition.id(),
                List.of(definition));
        ResolvedModelProfile resolved = new ResolvedModelProfile(
                definition,
                null,
                new GuideFailure("model_disabled", "This model profile is disabled"));
        return new ModelProfilesConfigLoader.Load(
                config, List.of(resolved));
    }

    private static Set<String> installedSkillMods(FeatureServices product) {
        java.util.TreeSet<String> installed = new java.util.TreeSet<>();
        try {
            product.platform().installedMods().stream()
                    .map(dev.openallay.platform.InstalledModMetadata::id).forEach(installed::add);
        } catch (UnsupportedOperationException unavailable) {
            // Minimal/headless platform implementations can still answer known dependency IDs.
        }
        if (product.platform().isModLoaded("ftbquests")) installed.add("ftbquests");
        SkillParser parser = new SkillParser();
        product.skills().externalSources().stream()
                .flatMap(source -> parser.parse(source).metadata().requiredMods().stream())
                .filter(product.platform()::isModLoaded).forEach(installed::add);
        return Set.copyOf(installed);
    }

    private static Path managedModsRoot(Path configDirectory) {
        Path configRoot = configDirectory.toAbsolutePath().normalize().getParent();
        Path gameRoot = configRoot == null ? null : configRoot.getParent();
        if (gameRoot == null) {
            throw new IllegalArgumentException(
                    "OpenAllay configuration directory requires a game directory");
        }
        return gameRoot.resolve("mods");
    }
}
