package dev.openallay.capability;

import dev.openallay.agent.tool.ToolRuntimeCatalog;
import dev.openallay.context.ContextCapability;
import dev.openallay.skill.SkillCatalogSnapshot;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** One immutable local Tool/Skill authority view captured by future client requests. */
public record ClientCapabilitySnapshot(
        CapabilityPolicy policy,
        ToolRuntimeCatalog localTools,
        SkillCatalogSnapshot skills,
        Set<ContextCapability> requiredContext) {
    /** Builds matching prompt and load_skill catalogs for one frozen invocation context. */
    public ClientCapabilitySnapshot forRequest(boolean unrestrictedJavascript) {
        SkillCatalogSnapshot requestSkills = skills.forRequest(unrestrictedJavascript);
        var registrations = localTools.registrations().stream()
                .filter(registration -> !requestSkills.metadata().isEmpty()
                        || !registration.tool().descriptor().id().equals(ClientCapabilityResolver.LOAD_SKILL_ID))
                .map(registration -> registration.tool() instanceof dev.openallay.skill.LoadSkillTool
                        ? new dev.openallay.tool.RegisteredTool(registration.providerId(),
                                new dev.openallay.skill.LoadSkillTool(requestSkills))
                        : registration).toList();
        return new ClientCapabilitySnapshot(policy,
                ToolRuntimeCatalog.from(registrations, Set.of()), requestSkills, requiredContext);
    }

    public ClientCapabilitySnapshot {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(localTools, "localTools");
        Objects.requireNonNull(skills, "skills");
        requiredContext = Set.copyOf(requiredContext);
        Set<ContextCapability> derived = localTools.descriptors().stream()
                .flatMap(descriptor -> descriptor.requiredContext().stream())
                .collect(Collectors.toUnmodifiableSet());
        if (!requiredContext.equals(derived)) {
            throw new IllegalArgumentException("requiredContext must match the Tool catalog");
        }
    }
}
