package dev.openallay.settings.requirement;

import dev.openallay.requirement.RequirementKind;
import dev.openallay.requirement.RequirementReport;
import java.util.List;
import java.util.Objects;

/** Immutable advisory review of the exact privately prepared package. */
@dev.openallay.value.ValueType(RequirementReview.ValueSchemaProvider.class)
public final class RequirementReview {
    private final Token token;
    private final RequirementKind kind;
    private final String id;
    private final String name;
    private final String version;
    private final String sha256;
    private final RequirementReport report;
    private final List<RequirementChange> changes;
    private final boolean catalogRequirementsDiffer;
    public RequirementReview(Token token, RequirementKind kind, String id, String name, String version, String sha256, RequirementReport report, List<RequirementChange> changes, boolean catalogRequirementsDiffer) {

        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(report, "report");
        changes = List.copyOf(changes);

        this.token = token;
        this.kind = kind;
        this.id = id;
        this.name = name;
        this.version = version;
        this.sha256 = sha256;
        this.report = report;
        this.changes = changes;
        this.catalogRequirementsDiffer = catalogRequirementsDiffer;
    }
    public Token token() { return token; }
    public RequirementKind kind() { return kind; }
    public String id() { return id; }
    public String name() { return name; }
    public String version() { return version; }
    public String sha256() { return sha256; }
    public RequirementReport report() { return report; }
    public List<RequirementChange> changes() { return changes; }
    public boolean catalogRequirementsDiffer() { return catalogRequirementsDiffer; }
public RequirementReview(Token token, RequirementKind kind, String id, String name,
            String version, String sha256, RequirementReport report, List<RequirementChange> changes) {
        this(token, kind, id, name, version, sha256, report, changes, false);
    }
public static final class Token {
        public Token() {}
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequirementReview)) return false;
        RequirementReview that = (RequirementReview) other;
        return java.util.Objects.equals(token, that.token) && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(id, that.id) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(version, that.version) && java.util.Objects.equals(sha256, that.sha256) && java.util.Objects.equals(report, that.report) && java.util.Objects.equals(changes, that.changes) && catalogRequirementsDiffer == that.catalogRequirementsDiffer;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(token);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(version);
        hash = 31 * hash + java.util.Objects.hashCode(sha256);
        hash = 31 * hash + java.util.Objects.hashCode(report);
        hash = 31 * hash + java.util.Objects.hashCode(changes);
        hash = 31 * hash + Boolean.hashCode(catalogRequirementsDiffer);
        return hash;
    }
    @Override public String toString() { return "RequirementReview[token=" + token + ", kind=" + kind + ", id=" + id + ", name=" + name + ", version=" + version + ", sha256=" + sha256 + ", report=" + report + ", changes=" + changes + ", catalogRequirementsDiffer=" + catalogRequirementsDiffer + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RequirementReview> schema() {
            return new dev.openallay.value.ValueSchema<>(RequirementReview.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RequirementReview>>asList(new dev.openallay.value.ValueSchema.Component<>(RequirementReview.class, "token", RequirementReview::token), new dev.openallay.value.ValueSchema.Component<>(RequirementReview.class, "kind", RequirementReview::kind), new dev.openallay.value.ValueSchema.Component<>(RequirementReview.class, "id", RequirementReview::id), new dev.openallay.value.ValueSchema.Component<>(RequirementReview.class, "name", RequirementReview::name), new dev.openallay.value.ValueSchema.Component<>(RequirementReview.class, "version", RequirementReview::version), new dev.openallay.value.ValueSchema.Component<>(RequirementReview.class, "sha256", RequirementReview::sha256), new dev.openallay.value.ValueSchema.Component<>(RequirementReview.class, "report", RequirementReview::report), new dev.openallay.value.ValueSchema.Component<>(RequirementReview.class, "changes", RequirementReview::changes), new dev.openallay.value.ValueSchema.Component<>(RequirementReview.class, "catalogRequirementsDiffer", RequirementReview::catalogRequirementsDiffer)), arguments -> new RequirementReview((Token) arguments[0], (RequirementKind) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (String) arguments[5], (RequirementReport) arguments[6], (List) arguments[7], (Boolean) arguments[8]));
        }
    }
}
