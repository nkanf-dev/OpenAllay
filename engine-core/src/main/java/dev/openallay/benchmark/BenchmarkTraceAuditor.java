package dev.openallay.benchmark;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.agent.trace.LiveAgentTrace;
import dev.openallay.agent.trace.LiveTraceEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Classifies only stable, directly observed failure codes.
 *
 * <p>A verification miss, turn-budget excess, or otherwise ambiguous trace stays unresolved.
 * This audit is evidence for a later engineering decision, not a model-behavior guess.
 */
public final class BenchmarkTraceAuditor {
    public BenchmarkTraceAudit audit(
            BenchmarkReport report, List<TraceRef> traces) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(traces, "traces");
        Map<AttemptKey, LiveAgentTrace> indexed = new HashMap<>();
        for (TraceRef trace : traces) {
            AttemptKey key = new AttemptKey(trace.caseId(), trace.attempt());
            if (indexed.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Duplicate benchmark trace reference: "
                                + trace.caseId() + "#" + trace.attempt());
            }
            indexed.put(key, trace.trace());
        }
        HashSet<AttemptKey> expected = new HashSet<>();
        for (BenchmarkReport.CaseReport caseReport : report.cases()) {
            for (BenchmarkReport.AttemptReport attempt : caseReport.attemptReports()) {
                expected.add(new AttemptKey(caseReport.caseId(), attempt.attempt()));
            }
        }
        if (!indexed.keySet().equals(expected)) {
            throw new IllegalArgumentException(
                    "Benchmark report and retained trace references must match exactly");
        }
        ArrayList<BenchmarkTraceAudit.AttemptAudit> attempts = new ArrayList<>();
        for (BenchmarkReport.CaseReport caseReport : report.cases()) {
            for (BenchmarkReport.AttemptReport attempt : caseReport.attemptReports()) {
                if (attempt.success()) {
                    continue;
                }
                AttemptKey key = new AttemptKey(caseReport.caseId(), attempt.attempt());
                attempts.add(classify(
                        caseReport.caseId(),
                        attempt,
                        indexed.get(key)));
            }
        }
        return new BenchmarkTraceAudit(attempts);
    }

    private static BenchmarkTraceAudit.AttemptAudit classify(
            String caseId,
            BenchmarkReport.AttemptReport attempt,
            LiveAgentTrace trace) {
        ArrayList<BenchmarkTraceAudit.Evidence> evidence = new ArrayList<>();
        addCode(
                evidence,
                BenchmarkTraceAudit.EvidenceSource.TERMINAL,
                attempt.metrics().terminalCode(),
                -1,
                "");
        if (trace != null) {
            List<LiveTraceEvent> events = trace.events();
            for (int index = 0; index < events.size(); index++) {
                LiveTraceEvent event = events.get(index);
                if (event.payload() == null || !event.payload().isJsonObject()) {
                    continue;
                }
                JsonObject payload = event.payload().getAsJsonObject();
                if ("failure".equals(event.type())) {
                    addCode(
                            evidence,
                            BenchmarkTraceAudit.EvidenceSource.TRACE_FAILURE,
                            string(payload, "code"),
                            index,
                            "");
                } else if ("tool_result".equals(event.type())
                        && bool(payload, "failure")) {
                    String toolId = string(payload, "toolId");
                    int eventIndex = index;
                    collectCodes(
                            payload.get("result"),
                            code -> addCode(
                                    evidence,
                                    BenchmarkTraceAudit.EvidenceSource.TOOL_RESULT,
                                    code,
                                    eventIndex,
                                    toolId));
                }
            }
        }

        List<BenchmarkTraceAudit.RootCauseDomain> domains = evidence.stream()
                .map(BenchmarkTraceAudit.Evidence::domain)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf));
        BenchmarkTraceAudit.Disposition disposition =
                domains.size() == 1
                        ? BenchmarkTraceAudit.Disposition.CONFIRMED
                        : BenchmarkTraceAudit.Disposition.UNRESOLVED;
        BenchmarkTraceAudit.RootCauseDomain domain =
                domains.size() == 1
                        ? domains.get(0)
                        : BenchmarkTraceAudit.RootCauseDomain.UNRESOLVED;
        String diagnostic = domains.isEmpty()
                ? "No stable failure-domain evidence; inspect the retained raw trace"
                : domains.size() == 1
                        ? "Confirmed from stable failure code"
                        : "Conflicting failure domains; inspect the retained raw trace";
        return new BenchmarkTraceAudit.AttemptAudit(
                caseId,
                attempt.attempt(),
                attempt.failureKind(),
                disposition,
                domain,
                diagnostic,
                evidence);
    }

    private static void collectCodes(JsonElement element, Consumer<String> codes) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry :
                    element.getAsJsonObject().entrySet()) {
                if ("code".equals(entry.getKey())
                        && entry.getValue().isJsonPrimitive()
                        && entry.getValue().getAsJsonPrimitive().isString()) {
                    codes.accept(entry.getValue().getAsString());
                } else {
                    collectCodes(entry.getValue(), codes);
                }
            }
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> collectCodes(value, codes));
        }
    }

    private static void addCode(
            List<BenchmarkTraceAudit.Evidence> evidence,
            BenchmarkTraceAudit.EvidenceSource source,
            String code,
            int eventIndex,
            String toolId) {
        BenchmarkTraceAudit.RootCauseDomain domain = domain(code);
        if (domain == BenchmarkTraceAudit.RootCauseDomain.UNRESOLVED) {
            return;
        }
        BenchmarkTraceAudit.Evidence candidate = new BenchmarkTraceAudit.Evidence(
                source, code, domain, eventIndex, toolId);
        if (!evidence.contains(candidate)) {
            evidence.add(candidate);
        }
    }

    private static BenchmarkTraceAudit.RootCauseDomain domain(String code) {
        if (code == null || code.isBlank() || "completed".equals(code)) {
            return BenchmarkTraceAudit.RootCauseDomain.UNRESOLVED;
        }
        if (code.startsWith("benchmark_") || code.startsWith("fixture_")) {
            return BenchmarkTraceAudit.RootCauseDomain.FIXTURE;
        }
        if (code.startsWith("model_")) {
            return BenchmarkTraceAudit.RootCauseDomain.PROVIDER;
        }
        if (code.startsWith("skill_")
                || code.startsWith("invalid_skill_")
                || code.startsWith("bundled_skill_")) {
            return BenchmarkTraceAudit.RootCauseDomain.SKILL;
        }
        if (code.startsWith("javascript_schema_")
                || code.startsWith("javascript_host_type_")
                || code.equals("context_result_malformed")) {
            return BenchmarkTraceAudit.RootCauseDomain.SCHEMA;
        }
        if (code.startsWith("extension_")
                || code.startsWith("javascript_module_")
                || code.equals("module_capture_failed")) {
            return BenchmarkTraceAudit.RootCauseDomain.EXTENSION;
        }
        if (code.startsWith("command_")) {
            return BenchmarkTraceAudit.RootCauseDomain.COMMAND;
        }
        if (code.startsWith("world_")) {
            return BenchmarkTraceAudit.RootCauseDomain.WORLD;
        }
        return BenchmarkTraceAudit.RootCauseDomain.UNRESOLVED;
    }

    private static boolean bool(JsonObject object, String field) {
        return object.has(field)
                && object.get(field).isJsonPrimitive()
                && object.get(field).getAsBoolean();
    }

    private static String string(JsonObject object, String field) {
        return object.has(field)
                        && object.get(field).isJsonPrimitive()
                        && object.get(field).getAsJsonPrimitive().isString()
                ? object.get(field).getAsString()
                : "";
    }

    public record TraceRef(String caseId, int attempt, LiveAgentTrace trace) {
        public TraceRef {
            if (caseId == null || caseId.isBlank() || attempt < 1) {
                throw new IllegalArgumentException("caseId and positive attempt are required");
            }
        }
    }

    private record AttemptKey(String caseId, int attempt) {}
}
