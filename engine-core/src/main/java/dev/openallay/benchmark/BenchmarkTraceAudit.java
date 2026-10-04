package dev.openallay.benchmark;

import java.util.List;

/**
 * Evidence-only post-run classification for failed benchmark attempts.
 *
 * @param attempts failed-attempt audits in report order
 */
public record BenchmarkTraceAudit(
        List<AttemptAudit> attempts) {
    public BenchmarkTraceAudit {
        attempts = List.copyOf(attempts);
    }

    public record AttemptAudit(
            String caseId,
            int attempt,
            BenchmarkReport.FailureKind failureKind,
            Disposition disposition,
            RootCauseDomain domain,
            String diagnostic,
            List<Evidence> evidence) {
        public AttemptAudit {
            if (caseId == null || caseId.isBlank() || attempt < 1) {
                throw new IllegalArgumentException("caseId and positive attempt are required");
            }
            java.util.Objects.requireNonNull(failureKind, "failureKind");
            java.util.Objects.requireNonNull(disposition, "disposition");
            java.util.Objects.requireNonNull(domain, "domain");
            diagnostic = diagnostic == null ? "" : diagnostic;
            evidence = List.copyOf(evidence);
            if ((disposition == Disposition.CONFIRMED)
                    != (domain != RootCauseDomain.UNRESOLVED)) {
                throw new IllegalArgumentException(
                        "confirmed audits require one domain; unresolved audits require UNRESOLVED");
            }
        }
    }

    public record Evidence(
            EvidenceSource source,
            String code,
            RootCauseDomain domain,
            int eventIndex,
            String toolId) {
        public Evidence {
            java.util.Objects.requireNonNull(source, "source");
            if (code == null || code.isBlank()) {
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
}
