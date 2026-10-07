package dev.openallay.script;

import com.google.gson.JsonElement;
import dev.openallay.script.result.JavascriptResultShape;
import dev.openallay.script.result.JavascriptSemanticKind;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@dev.openallay.value.ValueType(JavascriptExecution.ValueSchemaProvider.class)
public final class JavascriptExecution {
    private final JsonElement value;
    private final JavascriptResultShape shape;
    private final Duration elapsed;
    private final List<String> modules;
    public JavascriptExecution(JsonElement value, JavascriptResultShape shape, Duration elapsed, List<String> modules) {

        value = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(value, "value"));
        shape = Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(elapsed, "elapsed");
        modules = dev.openallay.util.Java8Collections.listCopyOf(modules);

        this.value = value;
        this.shape = shape;
        this.elapsed = elapsed;
        this.modules = modules;
    }
    public JsonElement value() { return value; }
    public JavascriptResultShape shape() { return shape; }
    public Duration elapsed() { return elapsed; }
    public List<String> modules() { return modules; }
public JavascriptExecution(JsonElement value, Duration elapsed, List<String> modules) {
        this(
                value,
                JavascriptResultShape.ordinary(JavascriptSemanticKind.GENERIC),
                elapsed,
                modules);
    }
public JavascriptExecution(JsonElement value, Duration elapsed) {
        this(value, elapsed, dev.openallay.util.Java8Collections.listOf());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JavascriptExecution)) return false;
        JavascriptExecution that = (JavascriptExecution) other;
        return java.util.Objects.equals(value, that.value) && java.util.Objects.equals(shape, that.shape) && java.util.Objects.equals(elapsed, that.elapsed) && java.util.Objects.equals(modules, that.modules);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(value);
        hash = 31 * hash + java.util.Objects.hashCode(shape);
        hash = 31 * hash + java.util.Objects.hashCode(elapsed);
        hash = 31 * hash + java.util.Objects.hashCode(modules);
        return hash;
    }
    @Override public String toString() { return "JavascriptExecution[value=" + value + ", shape=" + shape + ", elapsed=" + elapsed + ", modules=" + modules + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<JavascriptExecution> schema() {
            return new dev.openallay.value.ValueSchema<>(JavascriptExecution.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<JavascriptExecution>>asList(new dev.openallay.value.ValueSchema.Component<>(JavascriptExecution.class, "value", JavascriptExecution::value), new dev.openallay.value.ValueSchema.Component<>(JavascriptExecution.class, "shape", JavascriptExecution::shape), new dev.openallay.value.ValueSchema.Component<>(JavascriptExecution.class, "elapsed", JavascriptExecution::elapsed), new dev.openallay.value.ValueSchema.Component<>(JavascriptExecution.class, "modules", JavascriptExecution::modules)), arguments -> new JavascriptExecution((JsonElement) arguments[0], (JavascriptResultShape) arguments[1], (Duration) arguments[2], (List) arguments[3]));
        }
    }
}
