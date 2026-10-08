package dev.openallay.benchmark;

import java.util.HashSet;
import java.util.List;

@dev.openallay.value.ValueType(BenchmarkCorpus.ValueSchemaProvider.class)
public final class BenchmarkCorpus {
    private final List<BenchmarkCase> cases;
    public BenchmarkCorpus(List<BenchmarkCase> cases) {

        cases = dev.openallay.util.Java8Collections.listCopyOf(cases);
        HashSet<String> ids = new HashSet<>();
        for (BenchmarkCase testCase : cases) {
            if (!ids.add(testCase.id())) {
                throw new IllegalArgumentException(
                        "Duplicate benchmark case " + testCase.id());
            }
        }

        this.cases = cases;
    }
    public List<BenchmarkCase> cases() { return cases; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BenchmarkCorpus)) return false;
        BenchmarkCorpus that = (BenchmarkCorpus) other;
        return java.util.Objects.equals(cases, that.cases);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(cases);
        return hash;
    }
    @Override public String toString() { return "BenchmarkCorpus[cases=" + cases + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<BenchmarkCorpus> schema() {
            return new dev.openallay.value.ValueSchema<>(BenchmarkCorpus.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<BenchmarkCorpus>>asList(new dev.openallay.value.ValueSchema.Component<>(BenchmarkCorpus.class, "cases", BenchmarkCorpus::cases)), arguments -> new BenchmarkCorpus((List) arguments[0]));
        }
    }
}
