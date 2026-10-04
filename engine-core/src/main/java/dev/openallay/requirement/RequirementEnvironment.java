package dev.openallay.requirement;

import java.util.Map;

/** Detached snapshot supplied by the settings UI; holds no mutation or runtime authority. */
public record RequirementEnvironment(
        Map<String, RequirementAvailability> capabilities,
        Map<String, RequirementAvailability> extensions,
        Map<String, RequirementAvailability> skills) {
    public RequirementEnvironment {
        capabilities = Map.copyOf(capabilities);
        extensions = Map.copyOf(extensions);
        skills = Map.copyOf(skills);
    }
}
