package dev.openallay.benchmark;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import java.util.List;
import java.util.Objects;

@dev.openallay.value.ValueType(BenchmarkOutcome.ValueSchemaProvider.class)
public final class BenchmarkOutcome {
    private final JsonElement canonicalResult;
    private final List<String> observedEffects;
    private final BenchmarkMetrics metrics;
    public BenchmarkOutcome(JsonElement canonicalResult, List<String> observedEffects, BenchmarkMetrics metrics) {

        canonicalResult = canonicalResult == null
                ? JsonNull.INSTANCE
                : dev.openallay.json.JsonTrees.copy(canonicalResult);
        observedEffects = List.copyOf(observedEffects);
        Objects.requireNonNull(metrics, "metrics");

        this.canonicalResult = canonicalResult;
        this.observedEffects = observedEffects;
        this.metrics = metrics;
    }
    public List<String> observedEffects() { return observedEffects; }
    public BenchmarkMetrics metrics() { return metrics; }

    public JsonElement canonicalResult() {
        return dev.openallay.json.JsonTrees.copy(canonicalResult);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BenchmarkOutcome)) return false;
        BenchmarkOutcome that = (BenchmarkOutcome) other;
        return java.util.Objects.equals(canonicalResult, that.canonicalResult) && java.util.Objects.equals(observedEffects, that.observedEffects) && java.util.Objects.equals(metrics, that.metrics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(canonicalResult);
        hash = 31 * hash + java.util.Objects.hashCode(observedEffects);
        hash = 31 * hash + java.util.Objects.hashCode(metrics);
        return hash;
    }
    @Override public String toString() { return "BenchmarkOutcome[canonicalResult=" + canonicalResult + ", observedEffects=" + observedEffects + ", metrics=" + metrics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<BenchmarkOutcome> schema() {
            return new dev.openallay.value.ValueSchema<>(BenchmarkOutcome.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<BenchmarkOutcome>>asList(new dev.openallay.value.ValueSchema.Component<>(BenchmarkOutcome.class, "canonicalResult", BenchmarkOutcome::canonicalResult), new dev.openallay.value.ValueSchema.Component<>(BenchmarkOutcome.class, "observedEffects", BenchmarkOutcome::observedEffects), new dev.openallay.value.ValueSchema.Component<>(BenchmarkOutcome.class, "metrics", BenchmarkOutcome::metrics)), arguments -> new BenchmarkOutcome((JsonElement) arguments[0], (List) arguments[1], (BenchmarkMetrics) arguments[2]));
        }
    }
}
