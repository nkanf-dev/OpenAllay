package dev.openallay.benchmark;

import java.util.List;

/**
 * Evidence-only post-run classification for failed benchmark attempts.
 *
 * <p>{@code attempts}: failed-attempt audits in report order</p>
 */
@dev.openallay.value.ValueType(BenchmarkTraceAudit.ValueSchemaProvider.class)
public final class BenchmarkTraceAudit {
    private final List<AttemptAudit> attempts;
    public BenchmarkTraceAudit(List<AttemptAudit> attempts) {

        attempts = dev.openallay.util.Java8Collections.listCopyOf(attempts);

        this.attempts = attempts;
    }
    public List<AttemptAudit> attempts() { return attempts; }
@dev.openallay.value.ValueType(AttemptAudit.ValueSchemaProvider.class)
public static final class AttemptAudit {
    private final String caseId;
    private final int attempt;
    private final BenchmarkReport.FailureKind failureKind;
    private final Disposition disposition;
    private final RootCauseDomain domain;
    private final String diagnostic;
    private final List<Evidence> evidence;
    public AttemptAudit(String caseId, int attempt, BenchmarkReport.FailureKind failureKind, Disposition disposition, RootCauseDomain domain, String diagnostic, List<Evidence> evidence) {

            if (caseId == null || dev.openallay.util.Java8Strings.isBlank(caseId) || attempt < 1) {
                throw new IllegalArgumentException("caseId and positive attempt are required");
            }
            java.util.Objects.requireNonNull(failureKind, "failureKind");
            java.util.Objects.requireNonNull(disposition, "disposition");
            java.util.Objects.requireNonNull(domain, "domain");
            diagnostic = diagnostic == null ? "" : diagnostic;
            evidence = dev.openallay.util.Java8Collections.listCopyOf(evidence);
            if ((disposition == Disposition.CONFIRMED)
                    != (domain != RootCauseDomain.UNRESOLVED)) {
                throw new IllegalArgumentException(
                        "confirmed audits require one domain; unresolved audits require UNRESOLVED");
            }

        this.caseId = caseId;
        this.attempt = attempt;
        this.failureKind = failureKind;
        this.disposition = disposition;
        this.domain = domain;
        this.diagnostic = diagnostic;
        this.evidence = evidence;
    }
    public String caseId() { return caseId; }
    public int attempt() { return attempt; }
    public BenchmarkReport.FailureKind failureKind() { return failureKind; }
    public Disposition disposition() { return disposition; }
    public RootCauseDomain domain() { return domain; }
    public String diagnostic() { return diagnostic; }
    public List<Evidence> evidence() { return evidence; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AttemptAudit)) return false;
        AttemptAudit that = (AttemptAudit) other;
        return java.util.Objects.equals(caseId, that.caseId) && attempt == that.attempt && java.util.Objects.equals(failureKind, that.failureKind) && java.util.Objects.equals(disposition, that.disposition) && java.util.Objects.equals(domain, that.domain) && java.util.Objects.equals(diagnostic, that.diagnostic) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(caseId);
        hash = 31 * hash + Integer.hashCode(attempt);
        hash = 31 * hash + java.util.Objects.hashCode(failureKind);
        hash = 31 * hash + java.util.Objects.hashCode(disposition);
        hash = 31 * hash + java.util.Objects.hashCode(domain);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "AttemptAudit[caseId=" + caseId + ", attempt=" + attempt + ", failureKind=" + failureKind + ", disposition=" + disposition + ", domain=" + domain + ", diagnostic=" + diagnostic + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<AttemptAudit> schema() {
            return new dev.openallay.value.ValueSchema<>(AttemptAudit.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<AttemptAudit>>asList(new dev.openallay.value.ValueSchema.Component<>(AttemptAudit.class, "caseId", AttemptAudit::caseId), new dev.openallay.value.ValueSchema.Component<>(AttemptAudit.class, "attempt", AttemptAudit::attempt), new dev.openallay.value.ValueSchema.Component<>(AttemptAudit.class, "failureKind", AttemptAudit::failureKind), new dev.openallay.value.ValueSchema.Component<>(AttemptAudit.class, "disposition", AttemptAudit::disposition), new dev.openallay.value.ValueSchema.Component<>(AttemptAudit.class, "domain", AttemptAudit::domain), new dev.openallay.value.ValueSchema.Component<>(AttemptAudit.class, "diagnostic", AttemptAudit::diagnostic), new dev.openallay.value.ValueSchema.Component<>(AttemptAudit.class, "evidence", AttemptAudit::evidence)), arguments -> new AttemptAudit((String) arguments[0], (Integer) arguments[1], (BenchmarkReport.FailureKind) arguments[2], (Disposition) arguments[3], (RootCauseDomain) arguments[4], (String) arguments[5], (List) arguments[6]));
        }
    }
}
@dev.openallay.value.ValueType(Evidence.ValueSchemaProvider.class)
public static final class Evidence {
    private final EvidenceSource source;
    private final String code;
    private final RootCauseDomain domain;
    private final int eventIndex;
    private final String toolId;
    public Evidence(EvidenceSource source, String code, RootCauseDomain domain, int eventIndex, String toolId) {

            java.util.Objects.requireNonNull(source, "source");
            if (code == null || dev.openallay.util.Java8Strings.isBlank(code)) {
                throw new IllegalArgumentException("evidence code must not be blank");
            }
            java.util.Objects.requireNonNull(domain, "domain");
            if (domain == RootCauseDomain.UNRESOLVED) {
                throw new IllegalArgumentException("evidence requires a concrete domain");
            }
            if (eventIndex < -1) {
                throw new IllegalArgumentException("eventIndex must be -1 or non-negative");
            }
            toolId = toolId == null ? "" : toolId;

        this.source = source;
        this.code = code;
        this.domain = domain;
        this.eventIndex = eventIndex;
        this.toolId = toolId;
    }
    public EvidenceSource source() { return source; }
    public String code() { return code; }
    public RootCauseDomain domain() { return domain; }
    public int eventIndex() { return eventIndex; }
    public String toolId() { return toolId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Evidence)) return false;
        Evidence that = (Evidence) other;
        return java.util.Objects.equals(source, that.source) && java.util.Objects.equals(code, that.code) && java.util.Objects.equals(domain, that.domain) && eventIndex == that.eventIndex && java.util.Objects.equals(toolId, that.toolId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(domain);
        hash = 31 * hash + Integer.hashCode(eventIndex);
        hash = 31 * hash + java.util.Objects.hashCode(toolId);
        return hash;
    }
    @Override public String toString() { return "Evidence[source=" + source + ", code=" + code + ", domain=" + domain + ", eventIndex=" + eventIndex + ", toolId=" + toolId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Evidence> schema() {
            return new dev.openallay.value.ValueSchema<>(Evidence.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Evidence>>asList(new dev.openallay.value.ValueSchema.Component<>(Evidence.class, "source", Evidence::source), new dev.openallay.value.ValueSchema.Component<>(Evidence.class, "code", Evidence::code), new dev.openallay.value.ValueSchema.Component<>(Evidence.class, "domain", Evidence::domain), new dev.openallay.value.ValueSchema.Component<>(Evidence.class, "eventIndex", Evidence::eventIndex), new dev.openallay.value.ValueSchema.Component<>(Evidence.class, "toolId", Evidence::toolId)), arguments -> new Evidence((EvidenceSource) arguments[0], (String) arguments[1], (RootCauseDomain) arguments[2], (Integer) arguments[3], (String) arguments[4]));
        }
    }
}
public enum Disposition {
        CONFIRMED,
        UNRESOLVED
    }
public enum RootCauseDomain {
        PROMPT,
        SKILL,
        SCHEMA,
        EXTENSION,
        COMMAND,
        WORLD,
        PROVIDER,
        FIXTURE,
        UNRESOLVED
    }
public enum EvidenceSource {
        TERMINAL,
        TRACE_FAILURE,
        TOOL_RESULT
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BenchmarkTraceAudit)) return false;
        BenchmarkTraceAudit that = (BenchmarkTraceAudit) other;
        return java.util.Objects.equals(attempts, that.attempts);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(attempts);
        return hash;
    }
    @Override public String toString() { return "BenchmarkTraceAudit[attempts=" + attempts + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<BenchmarkTraceAudit> schema() {
            return new dev.openallay.value.ValueSchema<>(BenchmarkTraceAudit.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<BenchmarkTraceAudit>>asList(new dev.openallay.value.ValueSchema.Component<>(BenchmarkTraceAudit.class, "attempts", BenchmarkTraceAudit::attempts)), arguments -> new BenchmarkTraceAudit((List) arguments[0]));
        }
    }
}
