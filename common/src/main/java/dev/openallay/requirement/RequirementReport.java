package dev.openallay.requirement;

import java.util.List;

/** Display result. Unmet entries are not reasons to deny installation, reading, or explicit use. */
public record RequirementReport(List<RequirementAssessment> entries) {
    public RequirementReport {
        entries = List.copyOf(entries);
    }

    public boolean allSatisfied() {
        return entries.stream().allMatch(entry -> entry.status() == RequirementStatus.SATISFIED);
    }

    public List<RequirementAssessment> unmet() {
        return entries.stream().filter(entry -> entry.status() != RequirementStatus.SATISFIED).toList();
    }
}
