package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.requirement.RequirementAssessment;
import dev.openallay.requirement.RequirementAvailability;
import dev.openallay.requirement.RequirementEnvironment;
import dev.openallay.requirement.RequirementKind;
import dev.openallay.requirement.RequirementReport;
import dev.openallay.requirement.RequirementSet;
import dev.openallay.requirement.RequirementStatus;
import dev.openallay.settings.requirement.RequirementChange;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RequirementSettingsProjectionTest {
    @Test
    void everyAdvisoryStateAllowsContinueWithoutInventingAnEnableAction() {
        for (RequirementStatus status : RequirementStatus.values()) {
            var report = new RequirementReport(List.of(new RequirementAssessment(
                    RequirementKind.CAPABILITY, "example:need", "Named need", status, "exact detail")));
            var projection = RequirementSettingsProjection.from(report, List.of());

            assertTrue(projection.continueEnabled(), status.name());
            assertEquals(RequirementSettingsProjection.PREFIX + "continue_anyway", projection.continueKey());
            assertFalse(projection.rows().getFirst().canEnable());
            assertEquals("example:need", projection.rows().getFirst().id());
            assertEquals("Named need", projection.rows().getFirst().name());
            assertEquals("exact detail", projection.rows().getFirst().detail());
        }
    }

    @Test
    void onlyAnExactDisabledOwnerCanBeEnabledAndUnrestrictedNeedsItsOwnConfirmation() {
        var report = new RequirementReport(List.of(
                row(RequirementKind.CAPABILITY, "openallay:unrestricted_javascript", RequirementStatus.DISABLED),
                row(RequirementKind.SKILL, "disabled-skill", RequirementStatus.DISABLED),
                row(RequirementKind.CAPABILITY, "example:other", RequirementStatus.DISABLED),
                row(RequirementKind.EXTENSION, "example:disabled", RequirementStatus.DISABLED)));
        var projection = RequirementSettingsProjection.from(report, List.of(
                new RequirementChange(RequirementKind.CAPABILITY, "openallay:unrestricted_javascript", true),
                new RequirementChange(RequirementKind.SKILL, "disabled-skill", false),
                new RequirementChange(RequirementKind.SKILL, "other", false),
                new RequirementChange(RequirementKind.EXTENSION, "example:disabled", false)));

        assertTrue(projection.rows().get(0).canEnable());
        assertTrue(projection.rows().get(0).unrestrictedConsentRequired());
        assertTrue(projection.rows().get(1).canEnable());
        assertFalse(projection.rows().get(1).unrestrictedConsentRequired());
        assertFalse(projection.rows().get(2).canEnable());
        assertFalse(projection.rows().get(3).canEnable());
    }

    @Test
    void missingUnavailableUnknownAndRestartRequiredNeverOfferFakeEnables() {
        for (RequirementStatus status : Arrays.stream(RequirementStatus.values())
                .filter(value -> value != RequirementStatus.DISABLED).toList()) {
            var report = new RequirementReport(List.of(row(
                    RequirementKind.CAPABILITY, "example:absent", status)));
            var projection = RequirementSettingsProjection.from(report, List.of(
                    new RequirementChange(RequirementKind.CAPABILITY, "example:absent", true)));
            assertFalse(projection.rows().getFirst().canEnable(), status.name());
            assertFalse(projection.rows().getFirst().unrestrictedConsentRequired(), status.name());
            assertTrue(projection.continueEnabled());
        }
    }

    @Test
    void detailEvaluationIsDirectDeterministicAndReadOnly() {
        var requirements = new RequirementSet(Set.of("example:z", "example:a"),
                Set.of("example:staged"), Set.of("missing-skill"));
        var environment = new RequirementEnvironment(
                Map.of("example:a", new RequirementAvailability("Available", RequirementStatus.SATISFIED, "")),
                Map.of("example:staged", new RequirementAvailability("Staged", RequirementStatus.RESTART_REQUIRED, "")),
                Map.of());
        var projection = RequirementSettingsProjection.evaluate(requirements, environment);
        assertEquals(List.of("example:a", "example:z", "example:staged", "missing-skill"),
                projection.rows().stream().map(RequirementSettingsProjection.Row::id).toList());
        assertEquals(List.of(RequirementStatus.SATISFIED, RequirementStatus.UNKNOWN,
                RequirementStatus.RESTART_REQUIRED, RequirementStatus.MISSING),
                projection.rows().stream().map(RequirementSettingsProjection.Row::status).toList());
        assertTrue(projection.rows().stream().noneMatch(RequirementSettingsProjection.Row::canEnable));
        assertEquals(1, environment.capabilities().size());
    }

    private static RequirementAssessment row(RequirementKind kind, String id, RequirementStatus status) {
        return new RequirementAssessment(kind, id, id, status, "");
    }
}
