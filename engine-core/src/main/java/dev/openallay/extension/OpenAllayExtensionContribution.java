package dev.openallay.extension;

import dev.openallay.script.extension.JavascriptDataModule;
import dev.openallay.script.result.JavascriptResultViewProvider;
import dev.openallay.skill.SkillSource;
import java.util.List;

/** Immutable declarations owned by one Extension. */
@dev.openallay.value.ValueType(OpenAllayExtensionContribution.ValueSchemaProvider.class)
public final class OpenAllayExtensionContribution {
    private final List<JavascriptDataModule> dataModules;
    private final List<JavascriptModuleSource> javascriptModules;
    private final List<SkillSource> skills;
    private final List<JavascriptResultViewProvider> resultViews;
    private final List<JavascriptInvocationParticipant> javascriptInvocationParticipants;
    private final List<JavascriptHostBinding> hostBindings;
    public OpenAllayExtensionContribution(List<JavascriptDataModule> dataModules, List<JavascriptModuleSource> javascriptModules, List<SkillSource> skills, List<JavascriptResultViewProvider> resultViews, List<JavascriptInvocationParticipant> javascriptInvocationParticipants, List<JavascriptHostBinding> hostBindings) {

        dataModules = List.copyOf(dataModules);
        javascriptModules = List.copyOf(javascriptModules);
        skills = List.copyOf(skills);
        resultViews = List.copyOf(resultViews);
        javascriptInvocationParticipants = List.copyOf(javascriptInvocationParticipants);
        hostBindings = List.copyOf(hostBindings);

        this.dataModules = dataModules;
        this.javascriptModules = javascriptModules;
        this.skills = skills;
        this.resultViews = resultViews;
        this.javascriptInvocationParticipants = javascriptInvocationParticipants;
        this.hostBindings = hostBindings;
    }
    public List<JavascriptDataModule> dataModules() { return dataModules; }
    public List<JavascriptModuleSource> javascriptModules() { return javascriptModules; }
    public List<SkillSource> skills() { return skills; }
    public List<JavascriptResultViewProvider> resultViews() { return resultViews; }
    public List<JavascriptInvocationParticipant> javascriptInvocationParticipants() { return javascriptInvocationParticipants; }
    public List<JavascriptHostBinding> hostBindings() { return hostBindings; }
public OpenAllayExtensionContribution(
            List<JavascriptDataModule> dataModules,
            List<JavascriptModuleSource> javascriptModules,
            List<SkillSource> skills,
            List<JavascriptResultViewProvider> resultViews) {
        this(dataModules, javascriptModules, skills, resultViews, List.of(), List.of());
    }
public OpenAllayExtensionContribution(
            List<JavascriptDataModule> dataModules,
            List<JavascriptModuleSource> javascriptModules,
            List<SkillSource> skills,
            List<JavascriptResultViewProvider> resultViews,
            List<JavascriptInvocationParticipant> javascriptInvocationParticipants) {
        this(dataModules, javascriptModules, skills, resultViews,
                javascriptInvocationParticipants, List.of());
    }
public static OpenAllayExtensionContribution empty() {
        return new OpenAllayExtensionContribution(List.of(), List.of(), List.of(), List.of());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OpenAllayExtensionContribution)) return false;
        OpenAllayExtensionContribution that = (OpenAllayExtensionContribution) other;
        return java.util.Objects.equals(dataModules, that.dataModules) && java.util.Objects.equals(javascriptModules, that.javascriptModules) && java.util.Objects.equals(skills, that.skills) && java.util.Objects.equals(resultViews, that.resultViews) && java.util.Objects.equals(javascriptInvocationParticipants, that.javascriptInvocationParticipants) && java.util.Objects.equals(hostBindings, that.hostBindings);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(dataModules);
        hash = 31 * hash + java.util.Objects.hashCode(javascriptModules);
        hash = 31 * hash + java.util.Objects.hashCode(skills);
        hash = 31 * hash + java.util.Objects.hashCode(resultViews);
        hash = 31 * hash + java.util.Objects.hashCode(javascriptInvocationParticipants);
        hash = 31 * hash + java.util.Objects.hashCode(hostBindings);
        return hash;
    }
    @Override public String toString() { return "OpenAllayExtensionContribution[dataModules=" + dataModules + ", javascriptModules=" + javascriptModules + ", skills=" + skills + ", resultViews=" + resultViews + ", javascriptInvocationParticipants=" + javascriptInvocationParticipants + ", hostBindings=" + hostBindings + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<OpenAllayExtensionContribution> schema() {
            return new dev.openallay.value.ValueSchema<>(OpenAllayExtensionContribution.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<OpenAllayExtensionContribution>>asList(new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionContribution.class, "dataModules", OpenAllayExtensionContribution::dataModules), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionContribution.class, "javascriptModules", OpenAllayExtensionContribution::javascriptModules), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionContribution.class, "skills", OpenAllayExtensionContribution::skills), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionContribution.class, "resultViews", OpenAllayExtensionContribution::resultViews), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionContribution.class, "javascriptInvocationParticipants", OpenAllayExtensionContribution::javascriptInvocationParticipants), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionContribution.class, "hostBindings", OpenAllayExtensionContribution::hostBindings)), arguments -> new OpenAllayExtensionContribution((List) arguments[0], (List) arguments[1], (List) arguments[2], (List) arguments[3], (List) arguments[4], (List) arguments[5]));
        }
    }
}
