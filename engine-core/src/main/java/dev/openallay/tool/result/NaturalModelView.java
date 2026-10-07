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

    public record Choice(JsonElement value, boolean complete, int omittedRows) {}
    private static final class State { private boolean omitted; private int omittedRows; }
}
