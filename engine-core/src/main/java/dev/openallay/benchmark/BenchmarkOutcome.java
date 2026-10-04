package dev.openallay.benchmark;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import java.util.List;
import java.util.Objects;

public record BenchmarkOutcome(
        JsonElement canonicalResult,
        List<String> observedEffects,
        BenchmarkMetrics metrics) {
    public BenchmarkOutcome {
        canonicalResult = canonicalResult == null
                ? JsonNull.INSTANCE
                : canonicalResult.deepCopy();
        observedEffects = List.copyOf(observedEffects);
        Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public JsonElement canonicalResult() {
        return canonicalResult.deepCopy();
    }
}
