package dev.openallay.settings.requirement;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.capability.CapabilityCatalogSnapshot;
import dev.openallay.capability.CapabilityKind;
import dev.openallay.capability.CapabilityPolicy;
import dev.openallay.capability.CapabilitySettingsEntry;
import dev.openallay.requirement.RequirementEvaluator;
import dev.openallay.requirement.RequirementKind;
import dev.openallay.requirement.RequirementSet;
import dev.openallay.requirement.RequirementStatus;
import dev.openallay.script.UnrestrictedJavascriptConfig;
import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.settings.capability.CapabilitySettingsView;
import dev.openallay.settings.extension.ExtensionSettingsView;
import dev.openallay.settings.skill.SkillSettingsView;
import dev.openallay.skill.SkillMetadata;
import dev.openallay.skill.SkillSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RequirementSettingsEnvironmentTest {
    @Test
    void reportUsesInstalledStateNotCatalogPresenceAndNeverEnablesMissingOrUnavailable() {
        CapabilitySettingsView capabilities = new CapabilitySettingsView(
                new CapabilityPolicy(1, Set.of("test:disabled"), Set.of("disabled-skill")),
                new CapabilityCatalogSnapshot(List.of(
                        capability("test:disabled", true, false),
                        capability("test:unavailable", false, true),
                        capability("test:ready", true, true))), Set.of(), Set.of());
        SkillSettingsView skills = new SkillSettingsView(List.of(skill("disabled-skill"),
                skill("ready-skill")), List.of());
        ExtensionSettingsView extensions = new ExtensionSettingsView(List.of(), List.of(), List.of(),
                List.of(extension("test:active", ExtensionSettingsView.State.ACTIVE),
                        extension("test:pending", ExtensionSettingsView.State.RESTART_REQUIRED),
                        extension("test:community", ExtensionSettingsView.State.COMMUNITY),
                        extension("test:broken", ExtensionSettingsView.State.INCOMPATIBLE)));
        var environment = RequirementSettingsEnvironment.from(capabilities, skills, extensions,
                CommandCapabilityConfig.defaults(), UnrestrictedJavascriptConfig.defaults());
        var requirements = new RequirementSet(
                Set.of("test:disabled", "test:ready", "test:unavailable", "test:unknown"),
                Set.of("test:active", "test:pending", "test:community", "test:broken", "test:missing"),
                Set.of("disabled-skill", "ready-skill", "missing-skill"));
        var report = RequirementEvaluator.evaluate(requirements, environment);
        Map<String, RequirementStatus> statuses = report.entries().stream().collect(
                java.util.stream.Collectors.toMap(entry -> entry.id(), entry -> entry.status()));
        assertEquals(RequirementStatus.SATISFIED, statuses.get("test:active"));
        assertEquals(RequirementStatus.RESTART_REQUIRED, statuses.get("test:pending"));
        assertEquals(RequirementStatus.MISSING, statuses.get("test:community"));
        assertEquals(RequirementStatus.UNAVAILABLE, statuses.get("test:broken"));
        assertEquals(RequirementStatus.MISSING, statuses.get("test:missing"));
        assertEquals(RequirementStatus.UNKNOWN, statuses.get("test:unknown"));
        assertEquals(RequirementStatus.DISABLED, statuses.get("test:disabled"));
        assertEquals(RequirementStatus.UNAVAILABLE, statuses.get("test:unavailable"));
        assertEquals(RequirementStatus.SATISFIED, statuses.get("test:ready"));
        assertEquals(RequirementStatus.DISABLED, statuses.get("disabled-skill"));
        assertEquals(RequirementStatus.SATISFIED, statuses.get("ready-skill"));
        assertEquals(RequirementStatus.MISSING, statuses.get("missing-skill"));
        assertEquals(List.of(
                new RequirementChange(RequirementKind.CAPABILITY, "test:disabled", false),
                new RequirementChange(RequirementKind.SKILL, "disabled-skill", false)),
                RequirementSettingsEnvironment.changes(report, capabilities));
    }

    @Test
    void builtInAuthorizationGuidanceCannotBeEnabledThroughAnUnrelatedSkillDenyChange() {
        var environment = RequirementSettingsEnvironment.from(CapabilitySettingsView.defaults(),
                new SkillSettingsView(List.of(skill("run-game-commands"),
                        skill("unrestricted-javascript")), List.of()),
                ExtensionSettingsView.defaults(), CommandCapabilityConfig.defaults(),
                UnrestrictedJavascriptConfig.defaults());
        var report = RequirementEvaluator.evaluate(new RequirementSet(
                Set.of(RequirementSettingsEnvironment.UNRESTRICTED_JAVASCRIPT,
                        RequirementSettingsEnvironment.EXPERIMENTAL_COMMANDS),
                Set.of(), Set.of("run-game-commands", "unrestricted-javascript")), environment);
        assertEquals(RequirementStatus.UNAVAILABLE, environment.skills().get("run-game-commands").status());
        assertEquals(RequirementStatus.UNAVAILABLE, environment.skills().get("unrestricted-javascript").status());
        var changes = RequirementSettingsEnvironment.changes(report, CapabilitySettingsView.defaults());
        assertEquals(2, changes.size());
        assertTrue(changes.stream().allMatch(change -> change.kind() == RequirementKind.CAPABILITY));
        assertTrue(changes.stream().filter(change -> change.unrestrictedConsentRequired())
                .allMatch(change -> change.id().equals(RequirementSettingsEnvironment.UNRESTRICTED_JAVASCRIPT)));
    }

    private static CapabilitySettingsEntry capability(String id, boolean available, boolean enabled) {
        return new CapabilitySettingsEntry("test:owner", id, CapabilityKind.TOOL,
                "settings.test.title", "settings.test.description", null, available, enabled);
    }

    private static SkillSettingsView.Skill skill(String id) {
        return new SkillSettingsView.Skill(new SkillMetadata(id, "Test", Optional.empty(), Optional.empty(),
                Map.of(), Set.of(), Set.of(), List.of(), "local:" + id, SkillSource.Origin.LOCAL),
                "Instructions", "Markdown", false);
    }

    private static ExtensionSettingsView.Extension extension(String id, ExtensionSettingsView.State state) {
        return new ExtensionSettingsView.Extension(id, id, "1.0", "Test", "Test", state,
                List.of("fabric"), "[26.2,26.3)", "[0.2,0.3)", "local",
                new ExtensionSettingsView.Contributions(List.of(), List.of(), List.of(), List.of(), List.of()), "");
    }
}
