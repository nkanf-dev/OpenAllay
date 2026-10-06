package dev.openallay.trace.replay;

import com.google.gson.JsonElement;

public record ReplayStepReport(
        int index,
        String type,
        String tool,
        boolean passed,
        long elapsedNanos,
        JsonElement actual,
        JsonElement expected,
        String assistantMessage,
        String error) {
    public ReplayStepReport {
        if (index < 0 || elapsedNanos < 0) {
            throw new IllegalArgumentException("Replay step index and elapsed time must be non-negative");
        }
        actual = actual == null ? null : dev.openallay.json.JsonTrees.copy(actual);
        expected = expected == null ? null : dev.openallay.json.JsonTrees.copy(expected);
    }

    @Override
    public JsonElement actual() {
        return actual == null ? null : dev.openallay.json.JsonTrees.copy(actual);
    }

    @Override
    public JsonElement expected() {
        return expected == null ? null : dev.openallay.json.JsonTrees.copy(expected);
    }
}
