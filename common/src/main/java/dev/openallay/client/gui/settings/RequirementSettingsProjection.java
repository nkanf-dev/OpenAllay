package dev.openallay.client.gui.settings;

import dev.openallay.requirement.RequirementAssessment;
import dev.openallay.requirement.RequirementEnvironment;
import dev.openallay.requirement.RequirementEvaluator;
import dev.openallay.requirement.RequirementKind;
import dev.openallay.requirement.RequirementReport;
import dev.openallay.requirement.RequirementSet;
import dev.openallay.requirement.RequirementStatus;
import dev.openallay.settings.requirement.RequirementChange;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Read-only display. An unmet advisory row never disables Continue anyway. */
public record RequirementSettingsProjection(List<Row> rows) {
    public static final String PREFIX = "screen.openallay.settings.requirements.";

    public RequirementSettingsProjection {
        rows = List.copyOf(rows);
    }

    public static RequirementSettingsProjection evaluate(
            RequirementSet requirements, RequirementEnvironment environment) {
        return from(RequirementEvaluator.evaluate(requirements, environment), List.of());
    }

    public static RequirementSettingsProjection from(
            RequirementReport report, List<RequirementChange> changes) {
        Objects.requireNonNull(report, "report");
        List<RequirementChange> allowed = List.copyOf(changes);
        return new RequirementSettingsProjection(report.entries().stream()
                .map(entry -> row(entry, allowed)).toList());
    }

    private static Row row(RequirementAssessment entry, List<RequirementChange> changes) {
        // Only a real disabled owner can be enabled. Missing providers and staged JARs
        // are not settings, even if a stale action was supplied with the report.
        RequirementChange change = entry.status() == RequirementStatus.DISABLED
                        && entry.kind() != RequirementKind.EXTENSION
                ? changes.stream().filter(value -> value.kind() == entry.kind()
                        && value.id().equals(entry.id())).findFirst().orElse(null)
                : null;
        return new Row(entry.kind(), entry.id(), entry.name(), entry.status(), entry.detail(),
                change != null, change != null && change.unrestrictedConsentRequired());
    }

    public boolean continueEnabled() {
        return true;
    }

    public String continueKey() {
        return PREFIX + "continue_anyway";
    }

    public record Row(
            RequirementKind kind,
            String id,
            String name,
            RequirementStatus status,
            String detail,
            boolean canEnable,
            boolean unrestrictedConsentRequired) {
        public String kindKey() {
            return PREFIX + "kind." + kind.name().toLowerCase(Locale.ROOT);
        }

        public String statusKey() {
            return PREFIX + "state." + status.name().toLowerCase(Locale.ROOT);
        }
    }
}
