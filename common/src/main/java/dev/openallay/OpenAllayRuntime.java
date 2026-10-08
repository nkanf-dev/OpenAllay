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

@dev.openallay.value.ValueType(OpenAllayRuntime.ValueSchemaProvider.class)
public final class OpenAllayRuntime implements FeatureServices {
    private final PlatformService platform;
    private final ToolRegistry tools;
    private final KnowledgeRegistry knowledge;
    private final PatchouliMultiblockStore patchouliMultiblocks;
    private final JavascriptDataModuleRegistry javascriptModules;
    private final CommandCapabilityRuntime commands;
    private final WorldObservationRuntime worldObservations;
    private final SkillRepository skills;
    private final DevelopmentToolInspector developmentTools;
    private final TraceReplayService traceReplay;
    private final CapabilitySettingsCatalog capabilitySettings;
    private final OpenAllayExtensionRegistry extensions;
    public OpenAllayRuntime(PlatformService platform, ToolRegistry tools, KnowledgeRegistry knowledge, PatchouliMultiblockStore patchouliMultiblocks, JavascriptDataModuleRegistry javascriptModules, CommandCapabilityRuntime commands, WorldObservationRuntime worldObservations, SkillRepository skills, DevelopmentToolInspector developmentTools, TraceReplayService traceReplay, CapabilitySettingsCatalog capabilitySettings, OpenAllayExtensionRegistry extensions) {

        Objects.requireNonNull(capabilitySettings, "capabilitySettings");
        Objects.requireNonNull(javascriptModules, "javascriptModules");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(worldObservations, "worldObservations");
        Objects.requireNonNull(extensions, "extensions");

        this.platform = platform;
        this.tools = tools;
        this.knowledge = knowledge;
        this.patchouliMultiblocks = patchouliMultiblocks;
        this.javascriptModules = javascriptModules;
        this.commands = commands;
        this.worldObservations = worldObservations;
        this.skills = skills;
        this.developmentTools = developmentTools;
        this.traceReplay = traceReplay;
        this.capabilitySettings = capabilitySettings;
        this.extensions = extensions;
    }
    public PlatformService platform() { return platform; }
    public ToolRegistry tools() { return tools; }
    public KnowledgeRegistry knowledge() { return knowledge; }
    public PatchouliMultiblockStore patchouliMultiblocks() { return patchouliMultiblocks; }
    public JavascriptDataModuleRegistry javascriptModules() { return javascriptModules; }
    public CommandCapabilityRuntime commands() { return commands; }
    public WorldObservationRuntime worldObservations() { return worldObservations; }
    public SkillRepository skills() { return skills; }
    public DevelopmentToolInspector developmentTools() { return developmentTools; }
    public TraceReplayService traceReplay() { return traceReplay; }
    public CapabilitySettingsCatalog capabilitySettings() { return capabilitySettings; }
    public OpenAllayExtensionRegistry extensions() { return extensions; }
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
                        dev.openallay.util.Java8Collections.setOf(OpenAllayConstants.EXTENSION_API_VERSION, "0.4.0")),
                modules,
                JavascriptModuleCatalog.bundled(),
                skills,
                dev.openallay.util.Java8Collections.setOf());
    }
private static Defaults defaults(PlatformService platform, SkillRepository skills) {
        JavascriptDataModuleRegistry modules = new JavascriptDataModuleRegistry();
        return new Defaults(modules, defaultExtensions(platform, modules, skills));
    }
@dev.openallay.value.ValueType(Defaults.ValueSchemaProvider.class)
private static final class Defaults {
    private final JavascriptDataModuleRegistry modules;
    private final OpenAllayExtensionRegistry extensions;
    private Defaults(JavascriptDataModuleRegistry modules, OpenAllayExtensionRegistry extensions) {
        this.modules = modules;
        this.extensions = extensions;
    }
    public JavascriptDataModuleRegistry modules() { return modules; }
    public OpenAllayExtensionRegistry extensions() { return extensions; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Defaults)) return false;
        Defaults that = (Defaults) other;
        return java.util.Objects.equals(modules, that.modules) && java.util.Objects.equals(extensions, that.extensions);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(modules);
        hash = 31 * hash + java.util.Objects.hashCode(extensions);
        return hash;
    }
    @Override public String toString() { return "Defaults[modules=" + modules + ", extensions=" + extensions + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Defaults> schema() {
            return new dev.openallay.value.ValueSchema<>(Defaults.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Defaults>>asList(new dev.openallay.value.ValueSchema.Component<>(Defaults.class, "modules", Defaults::modules), new dev.openallay.value.ValueSchema.Component<>(Defaults.class, "extensions", Defaults::extensions)), arguments -> new Defaults((JavascriptDataModuleRegistry) arguments[0], (OpenAllayExtensionRegistry) arguments[1]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OpenAllayRuntime)) return false;
        OpenAllayRuntime that = (OpenAllayRuntime) other;
        return java.util.Objects.equals(platform, that.platform) && java.util.Objects.equals(tools, that.tools) && java.util.Objects.equals(knowledge, that.knowledge) && java.util.Objects.equals(patchouliMultiblocks, that.patchouliMultiblocks) && java.util.Objects.equals(javascriptModules, that.javascriptModules) && java.util.Objects.equals(commands, that.commands) && java.util.Objects.equals(worldObservations, that.worldObservations) && java.util.Objects.equals(skills, that.skills) && java.util.Objects.equals(developmentTools, that.developmentTools) && java.util.Objects.equals(traceReplay, that.traceReplay) && java.util.Objects.equals(capabilitySettings, that.capabilitySettings) && java.util.Objects.equals(extensions, that.extensions);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(platform);
        hash = 31 * hash + java.util.Objects.hashCode(tools);
        hash = 31 * hash + java.util.Objects.hashCode(knowledge);
        hash = 31 * hash + java.util.Objects.hashCode(patchouliMultiblocks);
        hash = 31 * hash + java.util.Objects.hashCode(javascriptModules);
        hash = 31 * hash + java.util.Objects.hashCode(commands);
        hash = 31 * hash + java.util.Objects.hashCode(worldObservations);
        hash = 31 * hash + java.util.Objects.hashCode(skills);
        hash = 31 * hash + java.util.Objects.hashCode(developmentTools);
        hash = 31 * hash + java.util.Objects.hashCode(traceReplay);
        hash = 31 * hash + java.util.Objects.hashCode(capabilitySettings);
        hash = 31 * hash + java.util.Objects.hashCode(extensions);
        return hash;
    }
    @Override public String toString() { return "OpenAllayRuntime[platform=" + platform + ", tools=" + tools + ", knowledge=" + knowledge + ", patchouliMultiblocks=" + patchouliMultiblocks + ", javascriptModules=" + javascriptModules + ", commands=" + commands + ", worldObservations=" + worldObservations + ", skills=" + skills + ", developmentTools=" + developmentTools + ", traceReplay=" + traceReplay + ", capabilitySettings=" + capabilitySettings + ", extensions=" + extensions + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<OpenAllayRuntime> schema() {
            return new dev.openallay.value.ValueSchema<>(OpenAllayRuntime.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<OpenAllayRuntime>>asList(new dev.openallay.value.ValueSchema.Component<>(OpenAllayRuntime.class, "platform", OpenAllayRuntime::platform), new dev.openallay.value.ValueSchema.Component<>(OpenAllayRuntime.class, "tools", OpenAllayRuntime::tools), new dev.openallay.value.ValueSchema.Component<>(OpenAllayRuntime.class, "knowledge", OpenAllayRuntime::knowledge), new dev.openallay.value.ValueSchema.Component<>(OpenAllayRuntime.class, "patchouliMultiblocks", OpenAllayRuntime::patchouliMultiblocks), new dev.openallay.value.ValueSchema.Component<>(OpenAllayRuntime.class, "javascriptModules", OpenAllayRuntime::javascriptModules), new dev.openallay.value.ValueSchema.Component<>(OpenAllayRuntime.class, "commands", OpenAllayRuntime::commands), new dev.openallay.value.ValueSchema.Component<>(OpenAllayRuntime.class, "worldObservations", OpenAllayRuntime::worldObservations), new dev.openallay.value.ValueSchema.Component<>(OpenAllayRuntime.class, "skills", OpenAllayRuntime::skills), new dev.openallay.value.ValueSchema.Component<>(OpenAllayRuntime.class, "developmentTools", OpenAllayRuntime::developmentTools), new dev.openallay.value.ValueSchema.Component<>(OpenAllayRuntime.class, "traceReplay", OpenAllayRuntime::traceReplay), new dev.openallay.value.ValueSchema.Component<>(OpenAllayRuntime.class, "capabilitySettings", OpenAllayRuntime::capabilitySettings), new dev.openallay.value.ValueSchema.Component<>(OpenAllayRuntime.class, "extensions", OpenAllayRuntime::extensions)), arguments -> new OpenAllayRuntime((PlatformService) arguments[0], (ToolRegistry) arguments[1], (KnowledgeRegistry) arguments[2], (PatchouliMultiblockStore) arguments[3], (JavascriptDataModuleRegistry) arguments[4], (CommandCapabilityRuntime) arguments[5], (WorldObservationRuntime) arguments[6], (SkillRepository) arguments[7], (DevelopmentToolInspector) arguments[8], (TraceReplayService) arguments[9], (CapabilitySettingsCatalog) arguments[10], (OpenAllayExtensionRegistry) arguments[11]));
        }
    }
}
