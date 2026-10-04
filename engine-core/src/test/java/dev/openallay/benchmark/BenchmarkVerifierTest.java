package dev.openallay.benchmark;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BenchmarkVerifierTest {
    private final BenchmarkVerifier verifier = new BenchmarkVerifier();

    @Test
    void toolEvidenceCannotMakeAnIncorrectFinalAnswerPass() {
        BenchmarkCase testCase = answerContains("example:obsidian_sword");
        BenchmarkOutcome outcome = outcome("""
                {
                  "answer": "The iron sword is strongest.",
                  "toolResults": [
                    {"normalized": {"entries": [{"id": "example:obsidian_sword"}]}}
                  ]
                }
                """);

        assertFalse(verifier.verify(testCase, outcome).passed());
    }

    @Test
    void expectedValueInFinalAnswerPasses() {
        BenchmarkCase testCase = answerContains("example:obsidian_sword");
        BenchmarkOutcome outcome = outcome("""
                {
                  "answer": "The strongest sword is example:obsidian_sword.",
                  "toolResults": []
                }
                """);

        assertTrue(verifier.verify(testCase, outcome).passed());
    }

    private static BenchmarkCase answerContains(String expected) {
        return new BenchmarkCase(
                "answer",
                "data-analysis",
                "Find the strongest sword",
                1,
                4,
                new BenchmarkCase.Verifier(
                        BenchmarkCase.Kind.ANSWER_CONTAINS, "", null, expected));
    }

    private static BenchmarkOutcome outcome(String canonicalResult) {
        return new BenchmarkOutcome(
                JsonParser.parseString(canonicalResult),
                List.of(),
                new BenchmarkMetrics(
                        true,
                        1,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        "completed"));
    }
}
