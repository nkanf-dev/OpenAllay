package dev.openallay.requirement;

import java.util.List;

/** Display result. Unmet entries are not reasons to deny installation, reading, or explicit use. */
@dev.openallay.value.ValueType(RequirementReport.ValueSchemaProvider.class)
public final class RequirementReport {
    private final List<RequirementAssessment> entries;
    public RequirementReport(List<RequirementAssessment> entries) {

        entries = List.copyOf(entries);

        this.entries = entries;
    }
    public List<RequirementAssessment> entries() { return entries; }
public boolean allSatisfied() {
        return entries.stream().allMatch(entry -> entry.status() == RequirementStatus.SATISFIED);
    }
public List<RequirementAssessment> unmet() {
        return entries.stream().filter(entry -> entry.status() != RequirementStatus.SATISFIED).toList();
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequirementReport)) return false;
        RequirementReport that = (RequirementReport) other;
        return java.util.Objects.equals(entries, that.entries);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(entries);
        return hash;
    }
    @Override public String toString() { return "RequirementReport[entries=" + entries + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RequirementReport> schema() {
            return new dev.openallay.value.ValueSchema<>(RequirementReport.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RequirementReport>>asList(new dev.openallay.value.ValueSchema.Component<>(RequirementReport.class, "entries", RequirementReport::entries)), arguments -> new RequirementReport((List) arguments[0]));
        }
    }
}
