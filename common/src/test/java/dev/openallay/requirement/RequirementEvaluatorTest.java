package dev.openallay.requirement;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RequirementEvaluatorTest {
    @Test
    void reportsAllAdvisoryStatesAndRetainsUnknownNames() {
        RequirementSet requirements = new RequirementSet(
                Set.of("future:unknown", "core:on", "core:off", "core:unavailable"),
                Set.of("sample:missing", "sample:staged"), Set.of("guide", "missing-guide"));
        RequirementEnvironment environment = new RequirementEnvironment(Map.of(
                "core:on", new RequirementAvailability("Enabled", RequirementStatus.SATISFIED, ""),
                "core:off", new RequirementAvailability("Disabled", RequirementStatus.DISABLED, "setting off"),
                "core:unavailable", new RequirementAvailability("Unavailable", RequirementStatus.UNAVAILABLE, "unsupported")),
                Map.of("sample:staged", new RequirementAvailability("Staged", RequirementStatus.RESTART_REQUIRED, "restart")),
                Map.of("guide", new RequirementAvailability("Guide", RequirementStatus.SATISFIED, "")));

        RequirementReport report = RequirementEvaluator.evaluate(requirements, environment);

        assertFalse(report.allSatisfied());
        assertEquals(8, report.entries().size());
        assertEquals(6, report.unmet().size());
        assertEquals(List.of(RequirementStatus.DISABLED, RequirementStatus.SATISFIED,
                        RequirementStatus.UNAVAILABLE, RequirementStatus.UNKNOWN,
                        RequirementStatus.MISSING, RequirementStatus.RESTART_REQUIRED,
                        RequirementStatus.SATISFIED, RequirementStatus.MISSING),
                report.entries().stream().map(RequirementAssessment::status).toList());
        assertEquals("future:unknown", report.entries().get(3).name());
        assertEquals("setting off", report.entries().getFirst().detail());
        assertThrows(UnsupportedOperationException.class, () -> report.entries().clear());
    }

    @Test
    void usesAFrozenEnvironmentAndNeverChangesItOrWalksDependencies() {
        Map<String, RequirementAvailability> mutable = new HashMap<>();
        mutable.put("core:off", new RequirementAvailability("Disabled", RequirementStatus.DISABLED, ""));
        RequirementEnvironment environment = new RequirementEnvironment(mutable, Map.of(), Map.of());
        mutable.put("core:off", new RequirementAvailability("Enabled", RequirementStatus.SATISFIED, ""));
        RequirementSet declarations = new RequirementSet(Set.of("core:off"), Set.of(), Set.of());

        RequirementReport first = RequirementEvaluator.evaluate(declarations, environment);
        assertEquals(first, RequirementEvaluator.evaluate(declarations, environment));
        assertEquals(RequirementStatus.DISABLED, first.entries().getFirst().status());
        assertEquals(RequirementStatus.DISABLED, environment.capabilities().get("core:off").status());
        assertTrue(RequirementEvaluator.evaluate(RequirementSet.EMPTY, environment).allSatisfied());
    }
}
