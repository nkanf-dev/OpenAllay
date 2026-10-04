package dev.openallay.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import dev.openallay.agent.AgentState;
import dev.openallay.agent.trace.LiveAgentTrace;
import dev.openallay.agent.trace.LiveTraceEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class BenchmarkTraceAuditorTest {
    private final BenchmarkTraceAuditor auditor = new BenchmarkTraceAuditor();

    @Test
    void confirmsProviderFailureFromStableTerminalCode() {
        BenchmarkTraceAudit.AttemptAudit audit = audit(
                failedAttempt("model_context_rejected"),
                trace(
                        AgentState.FAILED,
                        "model_context_rejected",
                        event("failure", failure("model_context_rejected"))));

        assertEquals(BenchmarkTraceAudit.Disposition.CONFIRMED, audit.disposition());
        assertEquals(BenchmarkTraceAudit.RootCauseDomain.PROVIDER, audit.domain());
        assertEquals(
                List.of("model_context_rejected"),
                audit.evidence().stream()
                        .map(BenchmarkTraceAudit.Evidence::code)
                        .distinct()
                        .toList());
    }

    @Test
    void confirmsToolDomainFromNormalizedFailureCode() {
        BenchmarkTraceAudit.AttemptAudit audit = audit(
                verificationFailure(),
                trace(
                        AgentState.COMPLETED,
                        null,
                        toolFailure(
                                "openallay:run_javascript",
                                "javascript_schema_unavailable")));

        assertEquals(BenchmarkTraceAudit.Disposition.CONFIRMED, audit.disposition());
        assertEquals(BenchmarkTraceAudit.RootCauseDomain.SCHEMA, audit.domain());
        assertEquals("openallay:run_javascript", audit.evidence().getFirst().toolId());
    }

    @Test
    void leavesVerificationOnlyFailureUnresolvedInsteadOfGuessingPrompt() {
        BenchmarkTraceAudit.AttemptAudit audit = audit(
                verificationFailure(),
                trace(AgentState.COMPLETED, null, event("model_turn", new JsonObject())));

        assertEquals(BenchmarkTraceAudit.Disposition.UNRESOLVED, audit.disposition());
        assertEquals(BenchmarkTraceAudit.RootCauseDomain.UNRESOLVED, audit.domain());
        assertEquals(List.of(), audit.evidence());
    }

    @Test
    void leavesConflictingDirectDomainsUnresolvedAndRetainsBothSignals() {
        BenchmarkTraceAudit.AttemptAudit audit = audit(
                verificationFailure(),
                trace(
                        AgentState.COMPLETED,
                        null,
                        toolFailure("openallay:load_skill", "skill_not_found"),
                        toolFailure("openallay:run_javascript", "command_invalid")));

        assertEquals(BenchmarkTraceAudit.Disposition.UNRESOLVED, audit.disposition());
        assertEquals(BenchmarkTraceAudit.RootCauseDomain.UNRESOLVED, audit.domain());
        assertEquals(
                List.of(
                        BenchmarkTraceAudit.RootCauseDomain.SKILL,
                        BenchmarkTraceAudit.RootCauseDomain.COMMAND),
                audit.evidence().stream()
                        .map(BenchmarkTraceAudit.Evidence::domain)
                        .toList());
    }

    @Test
    void confirmsHarnessFailureWithoutInventingAMissingTrace() {
        BenchmarkTraceAudit.AttemptAudit audit =
                audit(failedAttempt("benchmark_harness_failure"), null);

        assertEquals(BenchmarkTraceAudit.Disposition.CONFIRMED, audit.disposition());
        assertEquals(BenchmarkTraceAudit.RootCauseDomain.FIXTURE, audit.domain());
        assertEquals(
                BenchmarkTraceAudit.EvidenceSource.TERMINAL,
                audit.evidence().getFirst().source());
    }

    @Test
    void rejectsAReportWithoutAnExactlyCorrelatedTraceReference() {
        BenchmarkReport.AttemptReport attempt = verificationFailure();
        BenchmarkReport report = new BenchmarkReport(
                List.of(new BenchmarkReport.CaseReport(
                        "case-a",
                        1,
                        0,
                        0.0D,
                        attempt.metrics().modelTurns(),
                        attempt.metrics().modelTurns(),
                        attempt.metrics().toolCalls(),
                        attempt.metrics().toolCalls(),
                        List.of(attempt))));

        assertThrows(
                IllegalArgumentException.class,
                () -> auditor.audit(report, List.of()));
    }

    private BenchmarkTraceAudit.AttemptAudit audit(
            BenchmarkReport.AttemptReport attempt, LiveAgentTrace trace) {
        BenchmarkReport report = new BenchmarkReport(
                List.of(new BenchmarkReport.CaseReport(
                        "case-a",
                        1,
                        0,
                        0.0D,
                        attempt.metrics().modelTurns(),
                        attempt.metrics().modelTurns(),
                        attempt.metrics().toolCalls(),
                        attempt.metrics().toolCalls(),
                        List.of(attempt))));
        return auditor.audit(
                        report,
                        List.of(new BenchmarkTraceAuditor.TraceRef(
                                "case-a", 1, trace)))
                .attempts()
                .getFirst();
    }

    private static BenchmarkReport.AttemptReport failedAttempt(String code) {
        return new BenchmarkReport.AttemptReport(
                1,
                false,
                BenchmarkReport.FailureKind.RUNTIME_TERMINAL,
                "runtime terminal: " + code,
                metrics(false, code));
    }

    private static BenchmarkReport.AttemptReport verificationFailure() {
        return new BenchmarkReport.AttemptReport(
                1,
                false,
                BenchmarkReport.FailureKind.VERIFICATION,
                "expected final answer evidence was absent",
                metrics(false, "completed"));
    }

    private static BenchmarkMetrics metrics(boolean success, String terminalCode) {
        return new BenchmarkMetrics(
                success, 1, 1, 1, 0, 0, 0, 1, 0, terminalCode);
    }

    private static LiveAgentTrace trace(
            AgentState state, String errorCode, LiveTraceEvent... events) {
        return new LiveAgentTrace(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "benchmark",
                Instant.EPOCH,
                Instant.EPOCH,
                state,
                List.of(events),
                state == AgentState.COMPLETED ? "answer" : null,
                errorCode);
    }

    private static LiveTraceEvent toolFailure(String toolId, String code) {
        JsonObject payload = new JsonObject();
        payload.addProperty("toolId", toolId);
        payload.addProperty("failure", true);
        payload.add("result", failure(code));
        return event("tool_result", payload);
    }

    private static JsonObject failure(String code) {
        JsonObject value = new JsonObject();
        value.addProperty("status", "failure");
        value.addProperty("code", code);
        value.addProperty("message", "failed");
        return value;
    }

    private static LiveTraceEvent event(String type, JsonObject payload) {
        return new LiveTraceEvent(type, 0L, payload);
    }
}
