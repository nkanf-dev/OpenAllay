package dev.openallay.model.http;

@dev.openallay.value.ValueType(SseEvent.ValueSchemaProvider.class)
public final class SseEvent {
    private final String event;
    private final String data;
    public SseEvent(String event, String data) {
        this.event = event;
        this.data = data;
    }
    public String event() { return event; }
    public String data() { return data; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SseEvent)) return false;
        SseEvent that = (SseEvent) other;
        return java.util.Objects.equals(event, that.event) && java.util.Objects.equals(data, that.data);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(event);
        hash = 31 * hash + java.util.Objects.hashCode(data);
        return hash;
    }
    @Override public String toString() { return "SseEvent[event=" + event + ", data=" + data + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SseEvent> schema() {
            return new dev.openallay.value.ValueSchema<>(SseEvent.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SseEvent>>asList(new dev.openallay.value.ValueSchema.Component<>(SseEvent.class, "event", SseEvent::event), new dev.openallay.value.ValueSchema.Component<>(SseEvent.class, "data", SseEvent::data)), arguments -> new SseEvent((String) arguments[0], (String) arguments[1]));
        }
    }
}
