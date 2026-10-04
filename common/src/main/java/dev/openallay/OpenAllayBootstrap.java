package dev.openallay;

import com.google.gson.Gson;
import dev.openallay.capability.CapabilityKind;
import dev.openallay.capability.CapabilitySettingsCatalog;
import dev.openallay.capability.CapabilitySettingsDescriptor;
import dev.openallay.context.minecraft.MinecraftContextCapture;
import dev.openallay.devmode.DevelopmentToolInspector;
import dev.openallay.extension.OpenAllayExtension;
import dev.openallay.extension.OpenAllayExtensionEnvironment;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.knowledge.KnowledgeRegistry;
import dev.openallay.integration.patchouli.PatchouliMultiblockStore;
import dev.openallay.platform.PlatformService;
import dev.openallay.platform.PlatformServices;
import dev.openallay.skill.BundledSkillLoader;
import dev.openallay.skill.LoadSkillTool;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.Tool;
import dev.openallay.tool.builtin.RunJavascriptTool;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.workspace.AgentResultWorkspaceRegistry;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.command.CommandCapabilityRuntime;
import dev.openallay.world.WorldObservationRuntime;
import dev.openallay.trace.json.TraceParser;
import dev.openallay.trace.minecraft.TraceReplayService;
import dev.openallay.trace.minecraft.TraceRepository;
import dev.openallay.trace.replay.AgentTraceReplayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class OpenAllayBootstrap {
    private static OpenAllayRuntime runtime;
    private static final List<OpenAllayExtension> pendingExtensions = new ArrayList<>();
    private static dev.openallay.extension.universal.UniversalExtensionDiscovery universalExtensions;
    private static final Set<String> implementedExtensionApis = Set.of(
            OpenAllayConstants.EXTENSION_API_VERSION, "0.3.0");

    private OpenAllayBootstrap() {}

    /**
     * Ordinary Fabric/NeoForge mod entrypoints call this during loader initialization.
     *
     * <p>Legacy loader-mod registration stays loader-owned. Universal packages use the
     * separate startup-only SDK entrypoint, not runtime hot loading.
     */
    public static synchronized void registerExtension(OpenAllayExtension extension) {
        java.util.Objects.requireNonNull(extension, "extension");
        if (runtime == null) {
            pendingExtensions.add(extension);
            return;
        }
        OpenAllayExtensionRegistry.Registration registration =
                runtime.extensions().register(extension);
        if (registration.state() != dev.openallay.extension.OpenAllayExtensionState.ACTIVE) {
            OpenAllayConstants.LOGGER.warn(
                    "Extension registration rejected: {} ({})",
                    registration.extensionId(),
                    registration.diagnostic());
        }
    }

    public static synchronized OpenAllayRuntime initialize() {
        if (runtime != null) {
            return runtime;
        }

        PlatformService platform = PlatformServices.load();
        Gson gson = new Gson();
        AgentResultWorkspaceRegistry javascriptWorkspaces =
                new AgentResultWorkspaceRegistry();
        KnowledgeRegistry knowledge = new KnowledgeRegistry();
        JavascriptDataModuleRegistry javascriptModules =
                new JavascriptDataModuleRegistry();
        JavascriptModuleCatalog javascriptModuleCatalog =
                JavascriptModuleCatalog.bundled();
        CommandCapabilityRuntime commands = new CommandCapabilityRuntime();
        WorldObservationRuntime worldObservations = new WorldObservationRuntime();
        ToolRegistry tools = new ToolRegistry();
        PatchouliMultiblockStore patchouliMultiblocks = new PatchouliMultiblockStore();
        SkillRepository skills = new SkillRepository(
                new SkillParser(), List.of(RunJavascriptTool.ID));
        Set<String> installedMods = installedMods(platform);
        OpenAllayExtensionRegistry extensions = new OpenAllayExtensionRegistry(
                new OpenAllayExtensionEnvironment(
                        platform.platformName(),
                        platform.gameVersion(),
                        OpenAllayConstants.EXTENSION_API_VERSION,
                        implementedExtensionApis),
                javascriptModules,
                javascriptModuleCatalog,
                skills,
                installedMods);
        tools.register(
                "openallay:builtins",
                builtinTools(
                        platform,
                        gson,
                        javascriptWorkspaces,
                        knowledge,
                        javascriptModules,
                        commands,
                        worldObservations,
                        javascriptModuleCatalog,
                        extensions));
        java.util.Set<String> installedSkillMods = new java.util.HashSet<>();
        if (platform.isModLoaded("ftbquests")) {
            installedSkillMods.add("ftbquests");
        }
        if (!skills.reload(new BundledSkillLoader().load(), installedSkillMods)) {
            OpenAllayConstants.LOGGER.warn("Bundled Skill validation failed: {}", skills.diagnostics());
        }
        skills.setRuntimeDisabledSkills(Set.of("run-game-commands"));
        tools.register("openallay:skills", List.of(new LoadSkillTool(skills)));
        for (OpenAllayExtension extension : List.copyOf(pendingExtensions)) {
            OpenAllayExtensionRegistry.Registration registration = extensions.register(extension);
            if (registration.state() != dev.openallay.extension.OpenAllayExtensionState.ACTIVE) {
                OpenAllayConstants.LOGGER.warn(
                        "Extension registration rejected: {} ({})",
                        registration.extensionId(),
                        registration.diagnostic());
            }
        }
        pendingExtensions.clear();
        var worldAccess = platform.minecraftWorldAccess();
        var environment = universalEnvironment(platform, worldAccess.isPresent());
        var host = dev.openallay.extension.universal.UniversalExtensionBridge.host(environment,
                worldAccess.orElseGet(() -> invocation -> {
                    invocation.requireActive();
                    throw new dev.openallay.api.extension.ExtensionException(
                            "world_backend_unavailable", "A native world backend is unavailable on this host");
                }));
        universalExtensions = new dev.openallay.extension.universal.UniversalExtensionDiscovery(
                platform.extensionDirectory(), extensions, host,
                dev.openallay.api.extension.OpenAllayExtension.class.getClassLoader());
        for (var result : universalExtensions.discover()) {
            if (result.state() != dev.openallay.extension.OpenAllayExtensionState.ACTIVE) {
                OpenAllayConstants.LOGGER.warn("Universal Extension registration rejected: {} ({})",
                        result.extensionId(), result.diagnostic());
            }
        }
        TraceReplayService traceReplay = new TraceReplayService(
                new TraceRepository(new TraceParser()),
                new MinecraftContextCapture(gson),
                new AgentTraceReplayer(tools, gson));
        CapabilitySettingsCatalog capabilitySettings = capabilitySettings(tools, skills);
        runtime = new OpenAllayRuntime(
                platform,
                tools,
                knowledge,
                patchouliMultiblocks,
                javascriptModules,
                commands,
                worldObservations,
                skills,
                new DevelopmentToolInspector(tools),
                traceReplay,
                capabilitySettings,
                extensions);
        OpenAllayConstants.LOGGER.info(
                "Initialized OpenAllay on {} with {} tool(s)",
                platform.platformName(),
                tools.descriptors().size());
        return runtime;
    }

    /** Loader display names and public support IDs are distinct; normalize at the adapter seam. */
    static dev.openallay.api.extension.ExtensionEnvironment universalEnvironment(PlatformService platform) {
        return universalEnvironment(platform, platform.minecraftWorldAccess().isPresent());
    }

    private static dev.openallay.api.extension.ExtensionEnvironment universalEnvironment(
            PlatformService platform, boolean nativeWorldAvailable) {
        Set<String> features = new java.util.HashSet<>(Set.of(
                "openallay:javascript_host", "openallay:skills", "openallay:semantic_results"));
        if (nativeWorldAvailable) features.add("minecraft:world-access");
        return new dev.openallay.api.extension.ExtensionEnvironment(
                platform.platformName().toLowerCase(java.util.Locale.ROOT),
                platform.gameVersion(), platform.productVersion(), implementedExtensionApis,
                Runtime.version().feature(), features);
    }

    /** Loader shutdown keeps package classes alive until admitted Extension hooks unwind. */
    public static java.util.concurrent.CompletableFuture<Void> shutdownExtensions() {
        OpenAllayRuntime captured;
        dev.openallay.extension.universal.UniversalExtensionDiscovery discovery;
        synchronized (OpenAllayBootstrap.class) {
            captured = runtime;
            discovery = universalExtensions;
        }
        if (captured == null) return java.util.concurrent.CompletableFuture.completedFuture(null);
        return captured.extensions().shutdown().thenRun(() -> {
            if (discovery != null) discovery.close();
        });
    }

    static CapabilitySettingsCatalog capabilitySettings(
            ToolRegistry tools, SkillRepository skills) {
        CapabilitySettingsCatalog catalog = new CapabilitySettingsCatalog();
        List<CapabilitySettingsDescriptor> descriptors = new ArrayList<>();
        descriptors.add(descriptor("patchouli", CapabilityKind.KNOWLEDGE_SOURCE, "source"));
        descriptors.add(descriptor("ftbquests", CapabilityKind.KNOWLEDGE_SOURCE, "source"));
        tools.registrations().stream()
                .filter(registration -> !registration.tool().descriptor().id()
                        .equals("openallay:load_skill"))
                .map(registration -> descriptor(
                        registration.tool().descriptor().id(), CapabilityKind.TOOL, "tool"))
                .forEach(descriptors::add);
        skills.metadata().stream()
                .map(metadata -> descriptor(
                        metadata.name(), CapabilityKind.SKILL, "skill"))
                .forEach(descriptors::add);
        catalog.register("openallay:core", descriptors);
        return catalog;
    }

    private static CapabilitySettingsDescriptor descriptor(
            String id,
            CapabilityKind kind,
            String keyKind) {
        String keyId = id.replace(':', '_').replace('/', '_').replace('-', '_');
        String prefix = "settings.openallay.capability." + keyKind + "." + keyId;
        return new CapabilitySettingsDescriptor(
                id, kind, prefix + ".title", prefix + ".description", null);
    }

    static List<Tool<?, ?>> builtinTools(PlatformService platform) {
        return builtinTools(platform, new Gson(), new AgentResultWorkspaceRegistry());
    }

    static List<Tool<?, ?>> builtinTools(
            PlatformService platform,
            Gson gson,
            AgentResultWorkspaceRegistry javascriptWorkspaces) {
        return builtinTools(
                platform, gson, javascriptWorkspaces, new KnowledgeRegistry());
    }

    static List<Tool<?, ?>> builtinTools(
            PlatformService platform,
            Gson gson,
            AgentResultWorkspaceRegistry javascriptWorkspaces,
            KnowledgeRegistry knowledge) {
        return builtinTools(
                platform,
                gson,
                javascriptWorkspaces,
                knowledge,
                new JavascriptDataModuleRegistry(),
                new CommandCapabilityRuntime(),
                new WorldObservationRuntime());
    }

    static List<Tool<?, ?>> builtinTools(
            PlatformService platform,
            Gson gson,
            AgentResultWorkspaceRegistry javascriptWorkspaces,
            KnowledgeRegistry knowledge,
            JavascriptDataModuleRegistry javascriptModules) {
        return builtinTools(
                platform,
                gson,
                javascriptWorkspaces,
                knowledge,
                javascriptModules,
                new CommandCapabilityRuntime(),
                new WorldObservationRuntime());
    }

    static List<Tool<?, ?>> builtinTools(
            PlatformService platform,
            Gson gson,
            AgentResultWorkspaceRegistry javascriptWorkspaces,
            KnowledgeRegistry knowledge,
            JavascriptDataModuleRegistry javascriptModules,
            CommandCapabilityRuntime commands) {
        return builtinTools(
                platform,
                gson,
                javascriptWorkspaces,
                knowledge,
                javascriptModules,
                commands,
                new WorldObservationRuntime(),
                JavascriptModuleCatalog.bundled());
    }

    static List<Tool<?, ?>> builtinTools(
            PlatformService platform,
            Gson gson,
            AgentResultWorkspaceRegistry javascriptWorkspaces,
            KnowledgeRegistry knowledge,
            JavascriptDataModuleRegistry javascriptModules,
            CommandCapabilityRuntime commands,
            WorldObservationRuntime worldObservations) {
        return builtinTools(
                platform,
                gson,
                javascriptWorkspaces,
                knowledge,
                javascriptModules,
                commands,
                worldObservations,
                JavascriptModuleCatalog.bundled());
    }

    static List<Tool<?, ?>> builtinTools(
            PlatformService platform,
            Gson gson,
            AgentResultWorkspaceRegistry javascriptWorkspaces,
            KnowledgeRegistry knowledge,
            JavascriptDataModuleRegistry javascriptModules,
            CommandCapabilityRuntime commands,
            JavascriptModuleCatalog javascriptModuleCatalog) {
        return builtinTools(
                platform,
                gson,
                javascriptWorkspaces,
                knowledge,
                javascriptModules,
                commands,
                new WorldObservationRuntime(),
                javascriptModuleCatalog);
    }

    static List<Tool<?, ?>> builtinTools(
            PlatformService platform,
            Gson gson,
            AgentResultWorkspaceRegistry javascriptWorkspaces,
            KnowledgeRegistry knowledge,
            JavascriptDataModuleRegistry javascriptModules,
            CommandCapabilityRuntime commands,
            WorldObservationRuntime worldObservations,
            JavascriptModuleCatalog javascriptModuleCatalog) {
        return builtinTools(platform, gson, javascriptWorkspaces, knowledge, javascriptModules,
                commands, worldObservations, javascriptModuleCatalog, null);
    }

    static List<Tool<?, ?>> builtinTools(
            PlatformService platform,
            Gson gson,
            AgentResultWorkspaceRegistry javascriptWorkspaces,
            KnowledgeRegistry knowledge,
            JavascriptDataModuleRegistry javascriptModules,
            CommandCapabilityRuntime commands,
            WorldObservationRuntime worldObservations,
            JavascriptModuleCatalog javascriptModuleCatalog,
            OpenAllayExtensionRegistry extensions) {
        return List.of(new RunJavascriptTool(
                new RhinoJavascriptRuntime(
                        RhinoJavascriptRuntime.DEFAULT_TIMEOUT,
                        dev.openallay.script.JavascriptRuntimeLimits.DEFAULT,
                        javascriptModuleCatalog),
                context -> new MinecraftAgentHostGraph(
                        context, knowledge::snapshot, javascriptModules),
                javascriptWorkspaces,
                new JavascriptResultPresenter(),
                commands,
                worldObservations,
                extensions));
    }

    private static Set<String> installedMods(PlatformService platform) {
        try {
            return platform.installedMods().stream()
                    .map(dev.openallay.platform.InstalledModMetadata::id)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (UnsupportedOperationException unavailable) {
            return Set.of();
        }
    }
}
