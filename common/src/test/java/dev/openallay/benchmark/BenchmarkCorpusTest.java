package dev.openallay.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class BenchmarkCorpusTest {
    @Test
    void bundledCorpusIsStrictGeneralizableAndCoversPlatformCapabilities() {
        var input = getClass().getClassLoader()
                .getResourceAsStream("data/openallay/benchmarks/core.json");
        BenchmarkCorpus corpus = new BenchmarkCorpusCodec().decode(
                new InputStreamReader(input, StandardCharsets.UTF_8));

        assertEquals(Set.of("cases"),
                new com.google.gson.Gson().toJsonTree(corpus).getAsJsonObject().keySet());
        assertTrue(corpus.cases().size() >= 10);
        Set<String> categories = corpus.cases().stream()
                .map(BenchmarkCase::category)
                .collect(Collectors.toSet());
        assertTrue(categories.containsAll(Set.of(
                "core-context",
                "data-analysis",
                "recipes",
                "schema",
                "extensions",
                "commands",
                "world",
                "model-routing")));
        for (BenchmarkCase testCase : corpus.cases()) {
            assertFalse(testCase.prompt().contains("/give "));
            assertFalse(testCase.prompt().contains("return {"));
            assertFalse(testCase.requiredCapabilities().isEmpty());
        }
        assertTrue(corpus.cases().stream()
                .filter(testCase ->
                        testCase.verifier().kind() != BenchmarkCase.Kind.EFFECT_CONTAINS)
                .allMatch(testCase ->
                        testCase.verifier().kind() == BenchmarkCase.Kind.ANSWER_CONTAINS));
        assertEquals(
                "server-model-routing",
                corpus.cases().stream()
                        .filter(testCase -> testCase.id().equals("server-model-routing"))
                        .findFirst()
                        .orElseThrow()
                        .fixture());
        assertTrue(corpus.cases().stream()
                .filter(testCase -> !testCase.id().equals("server-model-routing"))
                .allMatch(testCase -> testCase.fixture().equals("javascript-agent")));
    }

    @Test
    void applicabilityUsesDeclaredFixtureCapabilitiesInsteadOfCaseIds() {
        BenchmarkCase testCase = new BenchmarkCase(
                "world",
                "world",
                "Inspect nearby blocks",
                "fixture-a",
                java.util.List.of("registries", "world"),
                1,
                3,
                new BenchmarkCase.Verifier(
                        BenchmarkCase.Kind.NON_EMPTY_RESULT, "", null, ""));

        assertTrue(testCase.applicableTo(
                "fixture-a", Set.of("registries", "world", "extensions")));
        assertFalse(testCase.applicableTo("fixture-a", Set.of("registries")));
        assertFalse(testCase.applicableTo(
                "fixture-b", Set.of("registries", "world")));
    }

    @Test
    void rejectsUnknownOrMissingFieldsAndInvalidVerifiers() {
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkCorpusCodec().decode(
                new java.io.StringReader("""
                        {"cases":[],"answer":"cheat"}
                        """)));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkCorpusCodec().decode(
                new java.io.StringReader("""
                        {}
                        """)));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkCorpusCodec().decode(
                new java.io.StringReader("""
                        {
                          "cases": [{
                            "id": "weak",
                            "category": "core",
                            "prompt": "answer",
                            "fixture": "fixture",
                            "requiredCapabilities": ["game"],
                            "attempts": 1,
                            "maxModelTurns": 1,
                            "verifier": {
                              "kind": "RESULT_CONTAINS",
                              "contains": "target"
                            }
                          }]
                        }
                        """)));
    }
}
