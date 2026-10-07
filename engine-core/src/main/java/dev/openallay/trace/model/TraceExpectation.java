package dev.openallay.trace.model;

import com.google.gson.JsonElement;
import java.util.Objects;

@dev.openallay.value.ValueType(TraceExpectation.ValueSchemaProvider.class)
public final class TraceExpectation {
    private final String status;
    private final ExpectationMatch match;
    private final JsonElement value;
    private final String outputType;
    public TraceExpectation(String status, ExpectationMatch match, JsonElement value, String outputType) {

        if (!"success".equals(status) && !"failure".equals(status)) {
            throw new IllegalArgumentException("Expectation status must be success or failure");
        }
        Objects.requireNonNull(match, "match");
        value = value == null ? null : dev.openallay.json.JsonTrees.copy(value);
        if ((match == ExpectationMatch.EXACT || match == ExpectationMatch.CONTAINS)
                && value == null) {
            throw new IllegalArgumentException("Exact and contains expectations require value");
        }
        if (match == ExpectationMatch.SCHEMA
                && (outputType == null || outputType.isBlank())) {
            throw new IllegalArgumentException("Schema expectations require outputType");
        }

        this.status = status;
        this.match = match;
        this.value = value;
        this.outputType = outputType;
    }
    public String status() { return status; }
    public ExpectationMatch match() { return match; }
    public String outputType() { return outputType; }

    public JsonElement value() {
        return value == null ? null : dev.openallay.json.JsonTrees.copy(value);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TraceExpectation)) return false;
        TraceExpectation that = (TraceExpectation) other;
        return java.util.Objects.equals(status, that.status) && java.util.Objects.equals(match, that.match) && java.util.Objects.equals(value, that.value) && java.util.Objects.equals(outputType, that.outputType);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(match);
        hash = 31 * hash + java.util.Objects.hashCode(value);
        hash = 31 * hash + java.util.Objects.hashCode(outputType);
        return hash;
    }
    @Override public String toString() { return "TraceExpectation[status=" + status + ", match=" + match + ", value=" + value + ", outputType=" + outputType + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<TraceExpectation> schema() {
            return new dev.openallay.value.ValueSchema<>(TraceExpectation.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<TraceExpectation>>asList(new dev.openallay.value.ValueSchema.Component<>(TraceExpectation.class, "status", TraceExpectation::status), new dev.openallay.value.ValueSchema.Component<>(TraceExpectation.class, "match", TraceExpectation::match), new dev.openallay.value.ValueSchema.Component<>(TraceExpectation.class, "value", TraceExpectation::value), new dev.openallay.value.ValueSchema.Component<>(TraceExpectation.class, "outputType", TraceExpectation::outputType)), arguments -> new TraceExpectation((String) arguments[0], (ExpectationMatch) arguments[1], (JsonElement) arguments[2], (String) arguments[3]));
        }
    }
}
