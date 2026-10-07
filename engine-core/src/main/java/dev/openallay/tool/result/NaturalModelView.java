package dev.openallay.tool.result;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/** Producer-owned control-plane choice, independent of available context capacity. */
public final class NaturalModelView {
    private NaturalModelView() {}

    public static Choice artifact(JsonElement canonical) {
        State state = new State();
        JsonElement selected = select(canonical == null ? JsonNull.INSTANCE : canonical, state, 0);
        return new Choice(selected, !state.omitted, state.omittedRows);
    }

    private static JsonElement select(JsonElement value, State state, int depth) {
        if (depth > 64) { state.omitted = true; return new com.google.gson.JsonPrimitive("…"); }
        if (value.isJsonNull() || value.isJsonPrimitive()) return value;
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            // Scalar findings (IDs, counts, labels, and nulls) already form a chosen answer
            // shape. Preserve them; the caller's actual projection budget can reduce a
            // large vector later. Only container-valued arrays choose a representative row.
            boolean scalarFindings = true;
            for (JsonElement element : array) {
                if (!element.isJsonNull() && !element.isJsonPrimitive()) {
                    scalarFindings = false;
                    break;
                }
            }
            if (scalarFindings) return array;
            JsonArray sample = new JsonArray();
            if (!(array.size() == 0)) sample.add(select(array.get(0), state, depth + 1));
            if (array.size() > 1) {
                state.omitted = true;
                state.omittedRows = (int) Math.min(Integer.MAX_VALUE,
                        (long) state.omittedRows + array.size() - 1);
            }
            return sample;
        }
        JsonObject result = new JsonObject();
        for (java.util.Map.Entry<java.lang.String, com.google.gson.JsonElement> entry : value.getAsJsonObject().entrySet())
            result.add(entry.getKey(), select(entry.getValue(), state, depth + 1));
        return result;
    }

    @dev.openallay.value.ValueType(Choice.ValueSchemaProvider.class)
public static final class Choice {
    private final JsonElement value;
    private final boolean complete;
    private final int omittedRows;
    public Choice(JsonElement value, boolean complete, int omittedRows) {
        this.value = value;
        this.complete = complete;
        this.omittedRows = omittedRows;
    }
    public JsonElement value() { return value; }
    public boolean complete() { return complete; }
    public int omittedRows() { return omittedRows; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Choice)) return false;
        Choice that = (Choice) other;
        return java.util.Objects.equals(value, that.value) && complete == that.complete && omittedRows == that.omittedRows;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(value);
        hash = 31 * hash + Boolean.hashCode(complete);
        hash = 31 * hash + Integer.hashCode(omittedRows);
        return hash;
    }
    @Override public String toString() { return "Choice[value=" + value + ", complete=" + complete + ", omittedRows=" + omittedRows + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Choice> schema() {
            return new dev.openallay.value.ValueSchema<>(Choice.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Choice>>asList(new dev.openallay.value.ValueSchema.Component<>(Choice.class, "value", Choice::value), new dev.openallay.value.ValueSchema.Component<>(Choice.class, "complete", Choice::complete), new dev.openallay.value.ValueSchema.Component<>(Choice.class, "omittedRows", Choice::omittedRows)), arguments -> new Choice((JsonElement) arguments[0], (Boolean) arguments[1], (Integer) arguments[2]));
        }
    }
}
    private static final class State { private boolean omitted; private int omittedRows; }
}
