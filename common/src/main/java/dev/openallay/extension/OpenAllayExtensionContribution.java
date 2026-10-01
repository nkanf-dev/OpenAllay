package dev.openallay.extension;

import dev.openallay.script.extension.JavascriptDataModule;
import dev.openallay.script.result.JavascriptResultViewProvider;
import dev.openallay.skill.SkillSource;
import java.util.List;

/** Immutable declarations owned by one Extension. */
public record OpenAllayExtensionContribution(
        List<JavascriptDataModule> dataModules,
        List<JavascriptModuleSource> javascriptModules,
        List<SkillSource> skills,
        List<JavascriptResultViewProvider> resultViews,
        List<JavascriptInvocationParticipant> javascriptInvocationParticipants,
        List<JavascriptHostBinding> hostBindings,
        List<ExtensionCapability> capabilities) {
    public OpenAllayExtensionContribution {
        dataModules = List.copyOf(dataModules);
        javascriptModules = List.copyOf(javascriptModules);
        skills = List.copyOf(skills);
        resultViews = List.copyOf(resultViews);
        javascriptInvocationParticipants = List.copyOf(javascriptInvocationParticipants);
        hostBindings = List.copyOf(hostBindings);
        capabilities = List.copyOf(capabilities);
    }

    /** Keeps the original Extension API constructor binary- and source-compatible. */
    public OpenAllayExtensionContribution(
            List<JavascriptDataModule> dataModules,
            List<JavascriptModuleSource> javascriptModules,
            List<SkillSource> skills,
            List<JavascriptResultViewProvider> resultViews) {
        this(dataModules, javascriptModules, skills, resultViews, List.of(), List.of(), List.of());
    }

    /** Retains the lifecycle-participant Extension API constructor for 0.2.x binaries. */
    public OpenAllayExtensionContribution(
            List<JavascriptDataModule> dataModules,
            List<JavascriptModuleSource> javascriptModules,
            List<SkillSource> skills,
            List<JavascriptResultViewProvider> resultViews,
            List<JavascriptInvocationParticipant> javascriptInvocationParticipants) {
        this(dataModules, javascriptModules, skills, resultViews,
                javascriptInvocationParticipants, List.of(), List.of());
    }

    public static OpenAllayExtensionContribution empty() {
        return new OpenAllayExtensionContribution(List.of(), List.of(), List.of(), List.of());
    }
}
