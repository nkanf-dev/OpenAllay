package dev.openallay.agent.trace;

import com.google.gson.JsonElement;

@dev.openallay.value.ValueType(LiveTraceEvent.ValueSchemaProvider.class)
public final class LiveTraceEvent {
    private final String type;
    private final long elapsedNanos;
    private final JsonElement payload;
    public LiveTraceEvent(String type, long elapsedNanos, JsonElement payload) {

        if (type == null || dev.openallay.util.Java8Strings.isBlank(type) || elapsedNanos < 0) {
            throw new IllegalArgumentException("Trace event type and non-negative time are required");
        }
        payload = payload == null ? null : dev.openallay.json.JsonTrees.copy(payload);

        this.type = type;
        this.elapsedNanos = elapsedNanos;
        this.payload = payload;
    }
    public String type() { return type; }
    public long elapsedNanos() { return elapsedNanos; }

    public JsonElement payload() {
        return payload == null ? null : dev.openallay.json.JsonTrees.copy(payload);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LiveTraceEvent)) return false;
        LiveTraceEvent that = (LiveTraceEvent) other;
        return java.util.Objects.equals(type, that.type) && elapsedNanos == that.elapsedNanos && java.util.Objects.equals(payload, that.payload);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + Long.hashCode(elapsedNanos);
        hash = 31 * hash + java.util.Objects.hashCode(payload);
        return hash;
    }
    @Override public String toString() { return "LiveTraceEvent[type=" + type + ", elapsedNanos=" + elapsedNanos + ", payload=" + payload + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<LiveTraceEvent> schema() {
            return new dev.openallay.value.ValueSchema<>(LiveTraceEvent.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<LiveTraceEvent>>asList(new dev.openallay.value.ValueSchema.Component<>(LiveTraceEvent.class, "type", LiveTraceEvent::type), new dev.openallay.value.ValueSchema.Component<>(LiveTraceEvent.class, "elapsedNanos", LiveTraceEvent::elapsedNanos), new dev.openallay.value.ValueSchema.Component<>(LiveTraceEvent.class, "payload", LiveTraceEvent::payload)), arguments -> new LiveTraceEvent((String) arguments[0], (Long) arguments[1], (JsonElement) arguments[2]));
        }
    }
}
