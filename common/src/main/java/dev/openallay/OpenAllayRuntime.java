package dev.openallay;

import dev.openallay.capability.CapabilitySettingsCatalog;
import dev.openallay.devmode.DevelopmentToolInspector;
import dev.openallay.extension.OpenAllayExtensionEnvironment;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.integration.patchouli.PatchouliMultiblockStore;
import dev.openallay.knowledge.KnowledgeRegistry;
import dev.openallay.platform.PlatformService;
import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.command.CommandCapabilityRuntime;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.skill.SkillRepository;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.trace.minecraft.TraceReplayService;
import dev.openallay.world.WorldObservationRuntime;
import java.util.Objects;

public record OpenAllayRuntime(
        PlatformService platform,
        ToolRegistry tools,
        KnowledgeRegistry knowledge,
        PatchouliMultiblockStore patchouliMultiblocks,
        JavascriptDataModuleRegistry javascriptModules,
        CommandCapabilityRuntime commands,
        WorldObservationRuntime worldObservations,
        SkillRepository skills,
        DevelopmentToolInspector developmentTools,
        TraceReplayService traceReplay,
        CapabilitySettingsCatalog capabilitySettings,
        OpenAllayExtensionRegistry extensions) implements FeatureServices {
    public OpenAllayRuntime {
        Objects.requireNonNull(capabilitySettings, "capabilitySettings");
        Objects.requireNonNull(javascriptModules, "javascriptModules");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(worldObservations, "worldObservations");
        Objects.requireNonNull(extensions, "extensions");
    }

    public OpenAllayRuntime(
            PlatformService platform,
            ToolRegistry tools,
            KnowledgeRegistry knowledge,
            PatchouliMultiblockStore patchouliMultiblocks,
            JavascriptDataModuleRegistry javascriptModules,
            CommandCapabilityRuntime commands,
            SkillRepository skills,
            DevelopmentToolInspector developmentTools,
            TraceReplayService traceReplay,
            CapabilitySettingsCatalog capabilitySettings) {
        this(
                platform,
                tools,
                knowledge,
                patchouliMultiblocks,
                javascriptModules,
                commands,
                new WorldObservationRuntime(),
                skills,
                developmentTools,
                traceReplay,
                capabilitySettings,
                defaultExtensions(platform, javascriptModules, skills));
    }

    public OpenAllayRuntime(
            PlatformService platform,
            ToolRegistry tools,
            KnowledgeRegistry knowledge,
            PatchouliMultiblockStore patchouliMultiblocks,
            JavascriptDataModuleRegistry javascriptModules,
            SkillRepository skills,
            DevelopmentToolInspector developmentTools,
            TraceReplayService traceReplay,
            CapabilitySettingsCatalog capabilitySettings) {
        this(
                platform,
                tools,
                knowledge,
                patchouliMultiblocks,
                javascriptModules,
                new CommandCapabilityRuntime(),
                new WorldObservationRuntime(),
                skills,
                developmentTools,
                traceReplay,
                capabilitySettings,
                defaultExtensions(platform, javascriptModules, skills));
    }

    public OpenAllayRuntime(
            PlatformService platform,
            ToolRegistry tools,
            KnowledgeRegistry knowledge,
            PatchouliMultiblockStore patchouliMultiblocks,
            SkillRepository skills,
            DevelopmentToolInspector developmentTools,
            TraceReplayService traceReplay,
            CapabilitySettingsCatalog capabilitySettings) {
        this(
                platform,
                tools,
                knowledge,
                patchouliMultiblocks,
                skills,
                developmentTools,
                traceReplay,
                capabilitySettings,
                defaults(platform, skills));
    }

    public OpenAllayRuntime(
            PlatformService platform,
            ToolRegistry tools,
            KnowledgeRegistry knowledge,
            PatchouliMultiblockStore patchouliMultiblocks,
            SkillRepository skills,
            DevelopmentToolInspector developmentTools,
            TraceReplayService traceReplay) {
        this(
                platform,
                tools,
                knowledge,
                patchouliMultiblocks,
                skills,
                developmentTools,
                traceReplay,
                new CapabilitySettingsCatalog(),
                defaults(platform, skills));
    }

    private OpenAllayRuntime(
            PlatformService platform,
            ToolRegistry tools,
            KnowledgeRegistry knowledge,
            PatchouliMultiblockStore patchouliMultiblocks,
            SkillRepository skills,
            DevelopmentToolInspector developmentTools,
            TraceReplayService traceReplay,
            CapabilitySettingsCatalog capabilitySettings,
            Defaults defaults) {
        this(
                platform,
                tools,
                knowledge,
                patchouliMultiblocks,
                defaults.modules(),
                new CommandCapabilityRuntime(),
                new WorldObservationRuntime(),
                skills,
                developmentTools,
                traceReplay,
                capabilitySettings,
                defaults.extensions());
    }

    private static OpenAllayExtensionRegistry defaultExtensions(
            PlatformService platform,
            JavascriptDataModuleRegistry modules,
            SkillRepository skills) {
        return new OpenAllayExtensionRegistry(
                new OpenAllayExtensionEnvironment(
                        platform.platformName(),
                        platform.gameVersion(),
                        OpenAllayConstants.EXTENSION_API_VERSION,
                        java.util.Set.of(OpenAllayConstants.EXTENSION_API_VERSION, "0.4.0")),
                modules,
                JavascriptModuleCatalog.bundled(),
                skills,
                java.util.Set.of());
    }

    private static Defaults defaults(PlatformService platform, SkillRepository skills) {
        JavascriptDataModuleRegistry modules = new JavascriptDataModuleRegistry();
        return new Defaults(modules, defaultExtensions(platform, modules, skills));
    }

    private record Defaults(
            JavascriptDataModuleRegistry modules,
            OpenAllayExtensionRegistry extensions) {}
}
