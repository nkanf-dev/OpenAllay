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
@dev.openallay.value.ValueType(ClientSettingsRuntime.ValueSchemaProvider.class)
public final class ClientSettingsRuntime {
    private final ClientModelRuntimeRegistry models;
    private final ClientSettingsService settings;
    private final UnrestrictedJavascriptRuntime unrestrictedJavascript;
    public ClientSettingsRuntime(ClientModelRuntimeRegistry models, ClientSettingsService settings, UnrestrictedJavascriptRuntime unrestrictedJavascript) {

        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(unrestrictedJavascript, "unrestrictedJavascript");

        this.models = models;
        this.settings = settings;
        this.unrestrictedJavascript = unrestrictedJavascript;
    }
    public ClientModelRuntimeRegistry models() { return models; }
    public ClientSettingsService settings() { return settings; }
    public UnrestrictedJavascriptRuntime unrestrictedJavascript() { return unrestrictedJavascript; }
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

        Map<String, String> environmentSnapshot = dev.openallay.util.Java8Collections.mapCopyOf(environment);
        LocalCredentialStore credentialStore = new LocalCredentialStore(
                profilesPath.toAbsolutePath().normalize().resolveSibling("credentials.sqlite3"),
                clock);
        CredentialResolver credentials = CredentialResolver.composite(
                credentialStore, environmentSnapshot);
        ToolResult<ModelProfilesConfigLoader.Load> loaded = new ModelProfilesConfigLoader()
                .load(
                        profilesPath,
                        credentials,
                        dev.openallay.util.Java8Collections.mapOf());
        ModelProfilesConfigLoader.Load initial;
        SettingsNotice startupNotice = null;
        final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.model.config.ModelProfilesConfigLoader.Load> value; ToolResult.Success<ModelProfilesConfigLoader.Load> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = loaded) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern0_holder.bound = (ToolResult.Success<ModelProfilesConfigLoader.Load>) $oaPattern0_holder.value) != null))) {
            initial = $oaPattern0_holder.bound.value();
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
            Gson gson = dev.openallay.json.EngineJson.create();
            Path configDirectory = profilesPath.toAbsolutePath().normalize().getParent();
            if (configDirectory == null) {
                throw new IllegalArgumentException("Model profiles require a configuration directory");
            }
            CommandCapabilityConfigStore commandStore = new CommandCapabilityConfigStore(
                    configDirectory.resolve("experimental-commands.json"));
            ToolResult<CommandCapabilityConfig> loadedCommands = commandStore.reload();
            CommandCapabilityConfig initialCommands;
            final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<dev.openallay.script.command.CommandCapabilityConfig> value; ToolResult.Success<CommandCapabilityConfig> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = loadedCommands) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern1_holder.bound = (ToolResult.Success<CommandCapabilityConfig>) $oaPattern1_holder.value) != null))) {
                initialCommands = $oaPattern1_holder.bound.value();
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
            final class $oaPattern2_Holder { dev.openallay.tool.ToolResult<dev.openallay.script.UnrestrictedJavascriptConfig> value; ToolResult.Success<UnrestrictedJavascriptConfig> bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
UnrestrictedJavascriptConfig initialUnrestricted = (($oaPattern2_holder.value = loadedUnrestricted) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern2_holder.bound = (ToolResult.Success<UnrestrictedJavascriptConfig>) $oaPattern2_holder.value) != null))
                    ? $oaPattern2_holder.bound.value() : UnrestrictedJavascriptConfig.defaults();
            UnrestrictedJavascriptRuntime unrestrictedRuntime = new UnrestrictedJavascriptRuntime();
            unrestrictedRuntime.replace(initialUnrestricted);
            final class $oaPattern3_Holder { dev.openallay.tool.ToolResult<dev.openallay.script.UnrestrictedJavascriptConfig> value; ToolResult.Failure<UnrestrictedJavascriptConfig> bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = loadedUnrestricted) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern3_holder.bound = (ToolResult.Failure<UnrestrictedJavascriptConfig>) $oaPattern3_holder.value) != null)) && startupNotice == null) {
                startupNotice = SettingsNotice.failure($oaPattern3_holder.bound.code(), $oaPattern3_holder.bound.message());
            }
            updateCommandGuidance(product, unrestrictedRuntime);
            Set<String> installedSkillMods = installedSkillMods(product);
            SkillSettingsBackend skills = new SkillSettingsBackend(
                    configDirectory.resolve("skills"), product.skills(), installedSkillMods,
                    product.platform().gameVersion());
            ExtensionSettingsBackend extensions = new ExtensionSettingsBackend(
                    configDirectory,
                    managedModsRoot(configDirectory),
                    product.extensions(),
                    product.javascriptModules());
            if (dev.openallay.util.Java8ApiSupport.isEmpty(product.tools().find("openallay:manage_skill"))) {
                Set<String> availableTools = product.tools().descriptors().stream()
                        .map(descriptor -> descriptor.id())
                        .collect(dev.openallay.util.Java8ApiSupport.toUnmodifiableSet());
                product.tools().register(
                        "openallay:managed-skills",
                        dev.openallay.util.Java8Collections.listOf(new ManageSkillTool(new AgentSkillManager(
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
                            final class $oaPattern4_Holder { dev.openallay.tool.ToolResult<dev.openallay.guide.ui.GuideDisplayConfig> value; ToolResult.Success<GuideDisplayConfig> bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern4_holder.bound = (ToolResult.Success<GuideDisplayConfig>) $oaPattern4_holder.value) != null))) {
                                activeDisplay.set($oaPattern4_holder.bound.value());
                            }
                            return result;
                        }

                        @Override
                        public ToolResult<GuideDisplayConfig> reloadDisplay() {
                            ToolResult<GuideDisplayConfig> result =
                                    traceDisplayActions.reloadDisplay();
                            final class $oaPattern5_Holder { dev.openallay.tool.ToolResult<dev.openallay.guide.ui.GuideDisplayConfig> value; ToolResult.Success<GuideDisplayConfig> bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern5_holder.bound = (ToolResult.Success<GuideDisplayConfig>) $oaPattern5_holder.value) != null))) {
                                activeDisplay.set($oaPattern5_holder.bound.value());
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
                                    capabilities,
                                    unrestrictedRuntime);
                        }

                        @Override
                        public ToolResult<CommandCapabilityConfig> reload() {
                            return publishCommandConfig(
                                    commandStore.reload(),
                                    commandStore,
                                    product,
                                    capabilities,
                                    unrestrictedRuntime);
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
                            return publishUnrestrictedConfig(unrestrictedStore.save(c), unrestrictedStore,
                                    unrestrictedRuntime, product, capabilities);
                        }
                        public ToolResult<UnrestrictedJavascriptConfig> reload() {
                            return publishUnrestrictedConfig(unrestrictedStore.reload(), unrestrictedStore,
                                    unrestrictedRuntime, product, capabilities);
                        }
                    },
                    initialUnrestricted,
                    historyActions,
                    dispatcher,
                    command -> dev.openallay.concurrent.NamedThreads.startDaemon("openallay-settings-connection-test", command),
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
            CapabilitySettingsBackend capabilities,
            UnrestrictedJavascriptRuntime unrestrictedRuntime) {
        final class $oaPattern6_Holder { dev.openallay.tool.ToolResult<dev.openallay.script.command.CommandCapabilityConfig> value; ToolResult.Failure<CommandCapabilityConfig> bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = loaded) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern6_holder.bound = (ToolResult.Failure<CommandCapabilityConfig>) $oaPattern6_holder.value) != null))) {
            return $oaPattern6_holder.bound;
        }
        CommandCapabilityConfig candidate =
                ((ToolResult.Success<CommandCapabilityConfig>) loaded).value();
        CommandCapabilityConfig prior = new CommandCapabilityConfig(
                product.commands().enabled());
        product.commands().replace(candidate);
        updateCommandGuidance(product, unrestrictedRuntime);
        ToolResult<CapabilitySettingsView> published = capabilities.refreshCapabilities();
        final class $oaPattern7_Holder { dev.openallay.tool.ToolResult<dev.openallay.settings.capability.CapabilitySettingsView> value; ToolResult.Failure<CapabilitySettingsView> bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = published) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern7_holder.bound = (ToolResult.Failure<CapabilitySettingsView>) $oaPattern7_holder.value) != null))) {
            product.commands().replace(prior);
            updateCommandGuidance(product, unrestrictedRuntime);
            store.save(prior);
            capabilities.refreshCapabilities();
            return new ToolResult.Failure<>($oaPattern7_holder.bound.code(), $oaPattern7_holder.bound.message());
        }
        return new ToolResult.Success<>(candidate);
    }
private static ToolResult<UnrestrictedJavascriptConfig> publishUnrestrictedConfig(
            ToolResult<UnrestrictedJavascriptConfig> loaded,
            UnrestrictedJavascriptConfigStore store,
            UnrestrictedJavascriptRuntime unrestrictedRuntime,
            FeatureServices product,
            CapabilitySettingsBackend capabilities) {
        final class $oaPattern8_Holder { dev.openallay.tool.ToolResult<dev.openallay.script.UnrestrictedJavascriptConfig> value; ToolResult.Failure<UnrestrictedJavascriptConfig> bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = loaded) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern8_holder.bound = (ToolResult.Failure<UnrestrictedJavascriptConfig>) $oaPattern8_holder.value) != null))) {
            return $oaPattern8_holder.bound;
        }
        UnrestrictedJavascriptConfig candidate =
                ((ToolResult.Success<UnrestrictedJavascriptConfig>) loaded).value();
        UnrestrictedJavascriptConfig prior = new UnrestrictedJavascriptConfig(unrestrictedRuntime.enabled());
        unrestrictedRuntime.replace(candidate);
        updateCommandGuidance(product, unrestrictedRuntime);
        ToolResult<CapabilitySettingsView> published = capabilities.refreshCapabilities();
        final class $oaPattern9_Holder { dev.openallay.tool.ToolResult<dev.openallay.settings.capability.CapabilitySettingsView> value; ToolResult.Failure<CapabilitySettingsView> bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = published) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern9_holder.bound = (ToolResult.Failure<CapabilitySettingsView>) $oaPattern9_holder.value) != null))) {
            unrestrictedRuntime.replace(prior);
            updateCommandGuidance(product, unrestrictedRuntime);
            store.save(prior);
            capabilities.refreshCapabilities();
            return new ToolResult.Failure<>($oaPattern9_holder.bound.code(), $oaPattern9_holder.bound.message());
        }
        return new ToolResult.Success<>(candidate);
    }
private static void updateCommandGuidance(
            FeatureServices product, UnrestrictedJavascriptRuntime unrestrictedRuntime) {
        product.skills().setRuntimeDisabledSkills(unrestrictedRuntime.enabled() || product.commands().enabled()
                ? dev.openallay.util.Java8Collections.setOf()
                : dev.openallay.util.Java8Collections.setOf(dev.openallay.skill.SkillCatalogSnapshot.GAME_COMMANDS));
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
                true,
                ModelProtocol.OPENAI_CHAT,
                URI.create("https://example.invalid/v1"),
                "configure-model-id",
                CredentialReference.environment("OPENALLAY_API_KEY").encoded(),
                null,
                null,
                Duration.ofSeconds(30),
                Duration.ofSeconds(300),
                null);
        ModelProfilesConfig config = new ModelProfilesConfig(
                definition.id(),
                dev.openallay.util.Java8Collections.listOf(definition));
        ResolvedModelProfile resolved = new ResolvedModelProfile(
                definition,
                null,
                new GuideFailure("invalid_model_config", "Configure a model"));
        return new ModelProfilesConfigLoader.Load(
                config, dev.openallay.util.Java8Collections.listOf(resolved));
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
        return dev.openallay.util.Java8Collections.setCopyOf(installed);
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ClientSettingsRuntime)) return false;
        ClientSettingsRuntime that = (ClientSettingsRuntime) other;
        return java.util.Objects.equals(models, that.models) && java.util.Objects.equals(settings, that.settings) && java.util.Objects.equals(unrestrictedJavascript, that.unrestrictedJavascript);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(models);
        hash = 31 * hash + java.util.Objects.hashCode(settings);
        hash = 31 * hash + java.util.Objects.hashCode(unrestrictedJavascript);
        return hash;
    }
    @Override public String toString() { return "ClientSettingsRuntime[models=" + models + ", settings=" + settings + ", unrestrictedJavascript=" + unrestrictedJavascript + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ClientSettingsRuntime> schema() {
            return new dev.openallay.value.ValueSchema<>(ClientSettingsRuntime.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ClientSettingsRuntime>>asList(new dev.openallay.value.ValueSchema.Component<>(ClientSettingsRuntime.class, "models", ClientSettingsRuntime::models), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsRuntime.class, "settings", ClientSettingsRuntime::settings), new dev.openallay.value.ValueSchema.Component<>(ClientSettingsRuntime.class, "unrestrictedJavascript", ClientSettingsRuntime::unrestrictedJavascript)), arguments -> new ClientSettingsRuntime((ClientModelRuntimeRegistry) arguments[0], (ClientSettingsService) arguments[1], (UnrestrictedJavascriptRuntime) arguments[2]));
        }
    }
}
