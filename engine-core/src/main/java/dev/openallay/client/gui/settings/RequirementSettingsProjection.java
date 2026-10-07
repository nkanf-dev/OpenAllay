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
@dev.openallay.value.ValueType(RequirementSettingsProjection.ValueSchemaProvider.class)
public final class RequirementSettingsProjection {
    private final List<Row> rows;
    public RequirementSettingsProjection(List<Row> rows) {

        rows = dev.openallay.util.Java8Collections.listCopyOf(rows);

        this.rows = rows;
    }
    public List<Row> rows() { return rows; }
public static final String PREFIX = "screen.openallay.settings.requirements.";
public static RequirementSettingsProjection evaluate(
            RequirementSet requirements, RequirementEnvironment environment) {
        return from(RequirementEvaluator.evaluate(requirements, environment), dev.openallay.util.Java8Collections.listOf());
    }
public static RequirementSettingsProjection from(
            RequirementReport report, List<RequirementChange> changes) {
        Objects.requireNonNull(report, "report");
        List<RequirementChange> allowed = dev.openallay.util.Java8Collections.listCopyOf(changes);
        return new RequirementSettingsProjection(dev.openallay.util.Java8Collections.toList(report.entries().stream()
                .map(entry -> row(entry, allowed))));
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
@dev.openallay.value.ValueType(Row.ValueSchemaProvider.class)
public static final class Row {
    private final RequirementKind kind;
    private final String id;
    private final String name;
    private final RequirementStatus status;
    private final String detail;
    private final boolean canEnable;
    private final boolean unrestrictedConsentRequired;
    public Row(RequirementKind kind, String id, String name, RequirementStatus status, String detail, boolean canEnable, boolean unrestrictedConsentRequired) {
        this.kind = kind;
        this.id = id;
        this.name = name;
        this.status = status;
        this.detail = detail;
        this.canEnable = canEnable;
        this.unrestrictedConsentRequired = unrestrictedConsentRequired;
    }
    public RequirementKind kind() { return kind; }
    public String id() { return id; }
    public String name() { return name; }
    public RequirementStatus status() { return status; }
    public String detail() { return detail; }
    public boolean canEnable() { return canEnable; }
    public boolean unrestrictedConsentRequired() { return unrestrictedConsentRequired; }
public String kindKey() {
            return PREFIX + "kind." + kind.name().toLowerCase(Locale.ROOT);
        }
public String statusKey() {
            return PREFIX + "state." + status.name().toLowerCase(Locale.ROOT);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Row)) return false;
        Row that = (Row) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(id, that.id) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(detail, that.detail) && canEnable == that.canEnable && unrestrictedConsentRequired == that.unrestrictedConsentRequired;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(detail);
        hash = 31 * hash + Boolean.hashCode(canEnable);
        hash = 31 * hash + Boolean.hashCode(unrestrictedConsentRequired);
        return hash;
    }
    @Override public String toString() { return "Row[kind=" + kind + ", id=" + id + ", name=" + name + ", status=" + status + ", detail=" + detail + ", canEnable=" + canEnable + ", unrestrictedConsentRequired=" + unrestrictedConsentRequired + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Row> schema() {
            return new dev.openallay.value.ValueSchema<>(Row.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Row>>asList(new dev.openallay.value.ValueSchema.Component<>(Row.class, "kind", Row::kind), new dev.openallay.value.ValueSchema.Component<>(Row.class, "id", Row::id), new dev.openallay.value.ValueSchema.Component<>(Row.class, "name", Row::name), new dev.openallay.value.ValueSchema.Component<>(Row.class, "status", Row::status), new dev.openallay.value.ValueSchema.Component<>(Row.class, "detail", Row::detail), new dev.openallay.value.ValueSchema.Component<>(Row.class, "canEnable", Row::canEnable), new dev.openallay.value.ValueSchema.Component<>(Row.class, "unrestrictedConsentRequired", Row::unrestrictedConsentRequired)), arguments -> new Row((RequirementKind) arguments[0], (String) arguments[1], (String) arguments[2], (RequirementStatus) arguments[3], (String) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequirementSettingsProjection)) return false;
        RequirementSettingsProjection that = (RequirementSettingsProjection) other;
        return java.util.Objects.equals(rows, that.rows);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(rows);
        return hash;
    }
    @Override public String toString() { return "RequirementSettingsProjection[rows=" + rows + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RequirementSettingsProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(RequirementSettingsProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RequirementSettingsProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(RequirementSettingsProjection.class, "rows", RequirementSettingsProjection::rows)), arguments -> new RequirementSettingsProjection((List) arguments[0]));
        }
    }
}
