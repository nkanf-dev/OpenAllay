package dev.openallay.requirement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure, nonrecursive advisory evaluation. Never changes settings, policy, or request authority. */
public final class RequirementEvaluator {
    private RequirementEvaluator() {}

    public static RequirementReport evaluate(
            RequirementSet requirements, RequirementEnvironment environment) {
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(environment, "environment");
        List<RequirementAssessment> result = new ArrayList<>();
        evaluate(result, RequirementKind.CAPABILITY, requirements.capabilities(), environment.capabilities());
        evaluate(result, RequirementKind.EXTENSION, requirements.extensions(), environment.extensions());
        evaluate(result, RequirementKind.SKILL, requirements.skills(), environment.skills());
        return new RequirementReport(result);
    }

    private static void evaluate(List<RequirementAssessment> result, RequirementKind kind,
            Set<String> ids, Map<String, RequirementAvailability> available) {
        for (String id : ids.stream().sorted().toList()) {
            RequirementAvailability value = available.get(id);
            result.add(value == null
                    ? new RequirementAssessment(kind, id, id,
                            kind == RequirementKind.CAPABILITY
                                    ? RequirementStatus.UNKNOWN : RequirementStatus.MISSING, "")
                    : new RequirementAssessment(kind, id, value.name(), value.status(), value.detail()));
        }
    }
}
