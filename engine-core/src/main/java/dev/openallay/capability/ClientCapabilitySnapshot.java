package dev.openallay.capability;

import dev.openallay.agent.tool.ToolRuntimeCatalog;
import dev.openallay.context.ContextCapability;
import dev.openallay.skill.SkillCatalogSnapshot;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** One immutable local Tool/Skill authority view captured by future client requests. */
@dev.openallay.value.ValueType(ClientCapabilitySnapshot.ValueSchemaProvider.class)
public final class ClientCapabilitySnapshot {
    private final CapabilityPolicy policy;
    private final ToolRuntimeCatalog localTools;
    private final SkillCatalogSnapshot skills;
    private final Set<ContextCapability> requiredContext;
    public ClientCapabilitySnapshot(CapabilityPolicy policy, ToolRuntimeCatalog localTools, SkillCatalogSnapshot skills, Set<ContextCapability> requiredContext) {

        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(localTools, "localTools");
        Objects.requireNonNull(skills, "skills");
        requiredContext = dev.openallay.util.Java8Collections.setCopyOf(requiredContext);
        Set<ContextCapability> derived = localTools.descriptors().stream()
                .flatMap(descriptor -> descriptor.requiredContext().stream())
                .collect(dev.openallay.util.Java8ApiSupport.toUnmodifiableSet());
        if (!requiredContext.equals(derived)) {
            throw new IllegalArgumentException("requiredContext must match the Tool catalog");
        }

        this.policy = policy;
        this.localTools = localTools;
        this.skills = skills;
        this.requiredContext = requiredContext;
    }
    public CapabilityPolicy policy() { return policy; }
    public ToolRuntimeCatalog localTools() { return localTools; }
    public SkillCatalogSnapshot skills() { return skills; }
    public Set<ContextCapability> requiredContext() { return requiredContext; }
public ClientCapabilitySnapshot forRequest(boolean unrestrictedJavascript) {
        return withRequestSkills(skills.forRequest(unrestrictedJavascript));
    }
public ClientCapabilitySnapshot forRequest(boolean unrestrictedJavascript, boolean commandsAvailable) {
        return withRequestSkills(skills.forRequest(unrestrictedJavascript, commandsAvailable));
    }
public ClientCapabilitySnapshot forRequest(dev.openallay.context.ToolInvocationContext context) {
        return withRequestSkills(skills.forRequest(
                context.unrestrictedJavascript(), commandCapabilityAvailable(context.correlationId())));
    }
public boolean commandCapabilityAvailable(String correlationId) {
        return localTools.find(dev.openallay.tool.builtin.RunJavascriptTool.ID)
                .filter(dev.openallay.tool.builtin.RunJavascriptTool.class::isInstance)
                .map(dev.openallay.tool.builtin.RunJavascriptTool.class::cast)
                .map(tool -> tool.commandCapabilityAvailable(correlationId))
                .orElse(false);
    }
private ClientCapabilitySnapshot withRequestSkills(SkillCatalogSnapshot requestSkills) {
        java.util.List<dev.openallay.tool.RegisteredTool> registrations = dev.openallay.util.Java8Collections.toList(localTools.registrations().stream()
                .filter(registration -> !requestSkills.metadata().isEmpty()
                        || !registration.tool().descriptor().id().equals(ClientCapabilityResolver.LOAD_SKILL_ID))
                .map(registration -> registration.tool() instanceof dev.openallay.skill.LoadSkillTool
                        ? new dev.openallay.tool.RegisteredTool(registration.providerId(),
                                new dev.openallay.skill.LoadSkillTool(requestSkills, "client"))
                        : registration));
        return new ClientCapabilitySnapshot(policy,
                ToolRuntimeCatalog.from(registrations, dev.openallay.util.Java8Collections.setOf()), requestSkills, requiredContext);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ClientCapabilitySnapshot)) return false;
        ClientCapabilitySnapshot that = (ClientCapabilitySnapshot) other;
        return java.util.Objects.equals(policy, that.policy) && java.util.Objects.equals(localTools, that.localTools) && java.util.Objects.equals(skills, that.skills) && java.util.Objects.equals(requiredContext, that.requiredContext);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(policy);
        hash = 31 * hash + java.util.Objects.hashCode(localTools);
        hash = 31 * hash + java.util.Objects.hashCode(skills);
        hash = 31 * hash + java.util.Objects.hashCode(requiredContext);
        return hash;
    }
    @Override public String toString() { return "ClientCapabilitySnapshot[policy=" + policy + ", localTools=" + localTools + ", skills=" + skills + ", requiredContext=" + requiredContext + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ClientCapabilitySnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(ClientCapabilitySnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ClientCapabilitySnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(ClientCapabilitySnapshot.class, "policy", ClientCapabilitySnapshot::policy), new dev.openallay.value.ValueSchema.Component<>(ClientCapabilitySnapshot.class, "localTools", ClientCapabilitySnapshot::localTools), new dev.openallay.value.ValueSchema.Component<>(ClientCapabilitySnapshot.class, "skills", ClientCapabilitySnapshot::skills), new dev.openallay.value.ValueSchema.Component<>(ClientCapabilitySnapshot.class, "requiredContext", ClientCapabilitySnapshot::requiredContext)), arguments -> new ClientCapabilitySnapshot((CapabilityPolicy) arguments[0], (ToolRuntimeCatalog) arguments[1], (SkillCatalogSnapshot) arguments[2], (Set) arguments[3]));
        }
    }
}
