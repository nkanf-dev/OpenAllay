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
                : dev.openallay.json.JsonTrees.copy(canonicalResult);
        observedEffects = List.copyOf(observedEffects);
        Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public JsonElement canonicalResult() {
        return dev.openallay.json.JsonTrees.copy(canonicalResult);
    }
}
