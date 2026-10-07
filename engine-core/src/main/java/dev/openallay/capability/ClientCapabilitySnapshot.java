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
    /** Selects eligible full-access guidance without capturing a native execution route. */
    public ClientCapabilitySnapshot forRequest(boolean unrestrictedJavascript) {
        return withRequestSkills(skills.forRequest(unrestrictedJavascript));
    }

    /** Reserves the complete eligible guidance before a request captures its optional routes. */
    public ClientCapabilitySnapshot forRequest(boolean unrestrictedJavascript, boolean commandsAvailable) {
        return withRequestSkills(skills.forRequest(unrestrictedJavascript, commandsAvailable));
    }

    /**
     * Selects command guidance from the exact route that Rhino will bind. Full access enables
     * capture, but cannot manufacture a missing route or re-enable a user-disabled Tool/Skill.
     */
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
        java.util.List<dev.openallay.tool.RegisteredTool> registrations = localTools.registrations().stream()
                .filter(registration -> !requestSkills.metadata().isEmpty()
                        || !registration.tool().descriptor().id().equals(ClientCapabilityResolver.LOAD_SKILL_ID))
                .map(registration -> registration.tool() instanceof dev.openallay.skill.LoadSkillTool
                        ? new dev.openallay.tool.RegisteredTool(registration.providerId(),
                                new dev.openallay.skill.LoadSkillTool(requestSkills, "client"))
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
