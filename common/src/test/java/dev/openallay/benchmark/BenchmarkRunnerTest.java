package dev.openallay.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BenchmarkRunnerTest {
    @Test
    void reportsCompletionProbabilityAndRoundsWithoutWallClockScoring() {
        BenchmarkCase testCase = new BenchmarkCase(
                "swords",
                "data-analysis",
                "Find the strongest sword",
                3,
                4,
                new BenchmarkCase.Verifier(
                        BenchmarkCase.Kind.JSON_PATH_EQUALS,
                        "winner.id",
                        JsonParser.parseString("\"minecraft:netherite_sword\""),
                        ""));

        BenchmarkReport report = new BenchmarkRunner(new BenchmarkVerifier()).run(
                List.of(testCase),
                (ignored, attempt) -> new BenchmarkOutcome(
                        JsonParser.parseString(attempt == 2
                                ? "{\"winner\":{\"id\":\"minecraft:iron_sword\"}}"
                                : "{\"winner\":{\"id\":\"minecraft:netherite_sword\"}}"),
                        List.of(),
                        metrics(true, attempt + 1, attempt + 2)));

        assertEquals(java.util.Set.of("cases"),
                new com.google.gson.Gson().toJsonTree(report).getAsJsonObject().keySet());
        BenchmarkReport.CaseReport result = report.cases().getFirst();
        assertEquals(2, result.successes());
        assertEquals(2.0 / 3.0, result.successProbability(), 0.0001);
        assertEquals(3.0, result.averageModelTurns(), 0.0001);
        assertEquals(3.0, result.medianModelTurns(), 0.0001);
        assertEquals(4.0, result.averageToolCalls(), 0.0001);
        assertEquals(4.0, result.medianToolCalls(), 0.0001);
        assertEquals(
                java.util.List.of(
                        BenchmarkReport.FailureKind.NONE,
                        BenchmarkReport.FailureKind.VERIFICATION,
                        BenchmarkReport.FailureKind.NONE),
                result.attemptReports().stream()
                        .map(BenchmarkReport.AttemptReport::failureKind)
                        .toList());
        assertFalse(java.util.Arrays.stream(BenchmarkMetrics.class.getRecordComponents())
                .anyMatch(component -> component.getName().toLowerCase().contains("time")));
    }

    @Test
    void failsAnOtherwiseSuccessfulAttemptWhenTurnBudgetIsExceeded() {
        BenchmarkCase testCase = new BenchmarkCase(
                "bounded",
                "core",
                "answer",
                1,
                2,
                new BenchmarkCase.Verifier(
                        BenchmarkCase.Kind.NON_EMPTY_RESULT, "", null, ""));

        BenchmarkReport.CaseReport report =
                new BenchmarkRunner(new BenchmarkVerifier()).run(
                                List.of(testCase),
                                (ignored, attempt) -> new BenchmarkOutcome(
                                        JsonParser.parseString("{\"ok\":true}"),
                                        List.of(),
                                        metrics(true, 3, 0)))
                        .cases()
                        .getFirst();

        assertEquals(0, report.successes());
        assertEquals(
                BenchmarkReport.FailureKind.MODEL_TURN_BUDGET,
                report.attemptReports().getFirst().failureKind());
        assertEquals("model turn budget exceeded", report.diagnostics().getFirst());
    }

    @Test
    void preservesRuntimeTerminalFailureInsteadOfMisreportingTurnBudget() {
        BenchmarkCase testCase = new BenchmarkCase(
                "provider",
                "core",
                "answer",
                1,
                3,
                new BenchmarkCase.Verifier(
                        BenchmarkCase.Kind.NON_EMPTY_RESULT, "", null, ""));

        BenchmarkReport.AttemptReport attempt =
                new BenchmarkRunner(new BenchmarkVerifier()).run(
                                List.of(testCase),
                                (ignored, number) -> new BenchmarkOutcome(
                                        JsonParser.parseString("{\"partial\":true}"),
                                        List.of(),
                                        new BenchmarkMetrics(
                                                false,
                                                1,
                                                0,
                                                0,
                                                0,
                                                0,
                                                0,
                                                0,
                                                0,
                                                "model_transport_unavailable")))
                        .cases()
                        .getFirst()
                        .attemptReports()
                        .getFirst();

        assertFalse(attempt.success());
        assertEquals(
                BenchmarkReport.FailureKind.RUNTIME_TERMINAL,
                attempt.failureKind());
        assertEquals(
                "runtime terminal: model_transport_unavailable",
                attempt.diagnostic());
    }

    private static BenchmarkMetrics metrics(
            boolean success, int modelTurns, int toolCalls) {
        return new BenchmarkMetrics(
                success,
                modelTurns,
                toolCalls,
                Math.min(toolCalls, 1),
                0,
                0,
                0,
                0,
                0,
                success ? "completed" : "failed");
    }
}
