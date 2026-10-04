package dev.openallay.script;

import com.google.gson.JsonElement;
import dev.openallay.script.result.JavascriptResultShape;
import dev.openallay.script.result.JavascriptSemanticKind;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record JavascriptExecution(
        JsonElement value,
        JavascriptResultShape shape,
        Duration elapsed,
        List<String> modules) {
    public JavascriptExecution {
        value = Objects.requireNonNull(value, "value").deepCopy();
        shape = Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(elapsed, "elapsed");
        modules = List.copyOf(modules);
    }

    public JavascriptExecution(JsonElement value, Duration elapsed, List<String> modules) {
        this(
                value,
                JavascriptResultShape.ordinary(JavascriptSemanticKind.GENERIC),
                elapsed,
                modules);
    }

    public JavascriptExecution(JsonElement value, Duration elapsed) {
        this(value, elapsed, List.of());
    }
}
