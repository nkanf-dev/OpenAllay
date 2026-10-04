package dev.openallay.benchmark;

import java.util.HashSet;
import java.util.List;

public record BenchmarkCorpus(List<BenchmarkCase> cases) {
    public BenchmarkCorpus {
        cases = List.copyOf(cases);
        HashSet<String> ids = new HashSet<>();
        for (BenchmarkCase testCase : cases) {
            if (!ids.add(testCase.id())) {
                throw new IllegalArgumentException(
                        "Duplicate benchmark case " + testCase.id());
            }
        }
    }
}
