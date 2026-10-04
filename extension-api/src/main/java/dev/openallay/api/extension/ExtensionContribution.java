package dev.openallay.api.extension;

import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.Collections;
import java.util.HashSet;
import java.util.ArrayList;

/** Immutable declarations. This external API deliberately omits the legacy record-reflection data graph. */
public final class ExtensionContribution {
    private final List<JavascriptModuleSource> javascriptModules;
    private final List<SkillSource> skills;
    private final List<ResultViewDeclaration> resultViews;
    private final List<JavascriptInvocationParticipant> javascriptInvocationParticipants;
    private final List<JavascriptHostBinding> hostBindings;
    private final List<ExtensionCapability> capabilities;

    public ExtensionContribution(
            List<JavascriptModuleSource> javascriptModules,
            List<SkillSource> skills,
            List<ResultViewDeclaration> resultViews,
            List<JavascriptInvocationParticipant> javascriptInvocationParticipants,
            List<JavascriptHostBinding> hostBindings,
            List<ExtensionCapability> capabilities) {
        this.javascriptModules = ApiValidation.list(javascriptModules, "javascriptModules");
        this.skills = ApiValidation.list(skills, "skills");
        this.resultViews = ApiValidation.list(resultViews, "resultViews");
        this.javascriptInvocationParticipants = ApiValidation.list(javascriptInvocationParticipants, "javascriptInvocationParticipants");
        this.hostBindings = ApiValidation.list(hostBindings, "hostBindings");
        this.capabilities = ApiValidation.list(capabilities, "capabilities");
        List<String> ids = new ArrayList<String>();
        ids.clear();
        for (JavascriptModuleSource value : this.javascriptModules) ids.add(value.id());
        ApiValidation.unique(ids, "javascriptModules ID");
        ids.clear();
        for (ResultViewDeclaration value : this.resultViews) ids.add(value.id());
        ApiValidation.unique(ids, "resultViews ID");
        ids.clear();
        for (JavascriptInvocationParticipant value : this.javascriptInvocationParticipants) ids.add(value.id());
        ApiValidation.unique(ids, "javascriptInvocationParticipants ID");
        ids.clear();
        for (JavascriptHostBinding value : this.hostBindings) ids.add(value.id());
        ApiValidation.unique(ids, "hostBindings ID");
        ids.clear();
        for (ExtensionCapability value : this.capabilities) ids.add(value.id());
        ApiValidation.unique(ids, "capabilities ID");
        ids.clear();
        for (JavascriptModuleSource value : this.javascriptModules) ids.add(value.id());
        for (JavascriptHostBinding value : this.hostBindings) ids.add(value.id());
        ApiValidation.unique(ids, "module/binding ID");
        Set<String> skillNames = new HashSet<String>();
        for (SkillSource value : this.skills)
            if (!skillNames.add(value.directoryName()))
                throw new IllegalArgumentException("Duplicate Skill directory: " + value.directoryName());
    }

    public List<JavascriptModuleSource> javascriptModules() { return javascriptModules; }
    public List<SkillSource> skills() { return skills; }
    public List<ResultViewDeclaration> resultViews() { return resultViews; }
    public List<JavascriptInvocationParticipant> javascriptInvocationParticipants() { return javascriptInvocationParticipants; }
    public List<JavascriptHostBinding> hostBindings() { return hostBindings; }
    public List<ExtensionCapability> capabilities() { return capabilities; }

    public static ExtensionContribution empty() {
        return new ExtensionContribution(Collections.<JavascriptModuleSource>emptyList(),
                Collections.<SkillSource>emptyList(), Collections.<ResultViewDeclaration>emptyList(),
                Collections.<JavascriptInvocationParticipant>emptyList(),
                Collections.<JavascriptHostBinding>emptyList(), Collections.<ExtensionCapability>emptyList());
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionContribution)) return false;
        ExtensionContribution that = (ExtensionContribution) other;
        return Objects.equals(javascriptModules, that.javascriptModules) &&
                Objects.equals(skills, that.skills) &&
                Objects.equals(resultViews, that.resultViews) &&
                Objects.equals(javascriptInvocationParticipants, that.javascriptInvocationParticipants) &&
                Objects.equals(hostBindings, that.hostBindings) &&
                Objects.equals(capabilities, that.capabilities);
    }
    @Override public int hashCode() { return Objects.hash(javascriptModules, skills, resultViews, javascriptInvocationParticipants, hostBindings, capabilities); }
}
