package dev.openallay.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BenchmarkSelectorTest {
    @Test
    void distinguishesFixtureMismatchFromMissingCapabilities() {
        BenchmarkCorpus corpus = new BenchmarkCorpus(List.of(
                testCase("world", "javascript-agent", List.of("world")),
                testCase("routing", "server-model-routing", List.of("server-model"))));

        BenchmarkSelector.Selection selection = new BenchmarkSelector().select(
                corpus,
                "javascript-agent",
                Set.of(),
                Set.of(),
                3);

        assertEquals(List.of(), selection.selected());
        assertEquals(List.of(
                new BenchmarkSelector.SkippedCase(
                        "world",
                        "javascript-agent",
                        BenchmarkSelector.SkipReason.MISSING_CAPABILITIES,
                        List.of("world")),
                new BenchmarkSelector.SkippedCase(
                        "routing",
                        "server-model-routing",
                        BenchmarkSelector.SkipReason.FIXTURE_MISMATCH,
                        List.of("server-model"))),
                selection.skipped());
    }

    @Test
    void requestedUnavailableCasesFailInsteadOfShrinkingTheRun() {
        BenchmarkCorpus corpus = new BenchmarkCorpus(List.of(
                testCase("routing", "server-model-routing", List.of("server-model"))));

        assertThrows(IllegalArgumentException.class, () -> new BenchmarkSelector().select(
                corpus,
                "javascript-agent",
                Set.of("server-model"),
                Set.of("routing"),
                1));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkSelector().select(
                corpus,
                "javascript-agent",
                Set.of(),
                Set.of("unknown"),
                1));
    }

    private static BenchmarkCase testCase(
            String id, String fixture, List<String> capabilities) {
        return new BenchmarkCase(
                id,
                "test",
                "test prompt",
                fixture,
                capabilities,
                1,
                2,
                new BenchmarkCase.Verifier(
                        BenchmarkCase.Kind.NON_EMPTY_RESULT, "", null, ""));
    }
}
