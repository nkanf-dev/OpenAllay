package dev.openallay;

import dev.openallay.capability.CapabilitySettingsCatalog;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.knowledge.KnowledgeRegistry;
import dev.openallay.platform.PlatformService;
import dev.openallay.script.command.CommandCapabilityRuntime;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.skill.SkillRepository;
import dev.openallay.tool.ToolRegistry;

/** Native-free product services. Native composition implements these existing ownership contracts. */
public interface FeatureServices {
    ToolRegistry tools();
    SkillRepository skills();
    PlatformService platform();
    CommandCapabilityRuntime commands();
    OpenAllayExtensionRegistry extensions();
    JavascriptDataModuleRegistry javascriptModules();
    KnowledgeRegistry knowledge();
    CapabilitySettingsCatalog capabilitySettings();
}
