package dev.openallay.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Selects corpus cases against one honest fixture capability boundary. */
public final class BenchmarkSelector {
    public Selection select(
            BenchmarkCorpus corpus,
            String fixtureId,
            Set<String> capabilities,
            Set<String> requestedCaseIds,
            int attempts) {
        Objects.requireNonNull(corpus, "corpus");
        if (fixtureId == null || fixtureId.isBlank()) {
            throw new IllegalArgumentException("fixtureId must not be blank");
        }
        Set<String> availableCapabilities = Set.copyOf(capabilities);
        Set<String> requested = Set.copyOf(requestedCaseIds);
        if (attempts <= 0) {
            throw new IllegalArgumentException("attempts must be positive");
        }

        Set<String> known = corpus.cases().stream()
                .map(BenchmarkCase::id)
                .collect(Collectors.toSet());
        TreeSet<String> unknown = new TreeSet<>(requested);
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown benchmark cases: " + unknown);
        }

        ArrayList<BenchmarkCase> selected = new ArrayList<>();
        ArrayList<SkippedCase> skipped = new ArrayList<>();
        for (BenchmarkCase testCase : corpus.cases()) {
            if (!requested.isEmpty() && !requested.contains(testCase.id())) {
                continue;
            }
            List<String> missing = testCase.requiredCapabilities().stream()
                    .filter(capability -> !availableCapabilities.contains(capability))
                    .sorted()
                    .toList();
            if (!testCase.fixture().equals(fixtureId)) {
                skipped.add(new SkippedCase(
                        testCase.id(),
                        testCase.fixture(),
                        SkipReason.FIXTURE_MISMATCH,
                        missing));
            } else if (!missing.isEmpty()) {
                skipped.add(new SkippedCase(
                        testCase.id(),
                        testCase.fixture(),
                        SkipReason.MISSING_CAPABILITIES,
                        missing));
            } else {
                selected.add(testCase.withAttempts(attempts));
            }
        }

        if (!requested.isEmpty() && !skipped.isEmpty()) {
            throw new IllegalArgumentException(
                    "Requested cases are unavailable to fixture " + fixtureId + ": " + skipped);
        }
        return new Selection(selected, skipped);
    }

    public enum SkipReason {
        FIXTURE_MISMATCH,
        MISSING_CAPABILITIES
    }

    public record SkippedCase(
            String caseId,
            String requiredFixture,
            SkipReason reason,
            List<String> missingCapabilities) {
        public SkippedCase {
            if (caseId == null || caseId.isBlank()) {
                throw new IllegalArgumentException("caseId must not be blank");
            }
            if (requiredFixture == null || requiredFixture.isBlank()) {
                throw new IllegalArgumentException("requiredFixture must not be blank");
            }
            Objects.requireNonNull(reason, "reason");
            missingCapabilities = List.copyOf(missingCapabilities);
        }
    }

    public record Selection(
            List<BenchmarkCase> selected,
            List<SkippedCase> skipped) {
        public Selection {
            selected = List.copyOf(selected);
            skipped = List.copyOf(skipped);
        }
    }
}
