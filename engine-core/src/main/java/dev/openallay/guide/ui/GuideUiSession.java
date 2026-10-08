package dev.openallay.guide.ui;

@dev.openallay.value.ValueType(GuideUiSession.ValueSchemaProvider.class)
public final class GuideUiSession {
    private final String id;
    private final boolean selected;
    private final boolean running;
    private final int requestCount;
    public GuideUiSession(String id, boolean selected, boolean running, int requestCount) {
        this.id = id;
        this.selected = selected;
        this.running = running;
        this.requestCount = requestCount;
    }
    public String id() { return id; }
    public boolean selected() { return selected; }
    public boolean running() { return running; }
    public int requestCount() { return requestCount; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideUiSession)) return false;
        GuideUiSession that = (GuideUiSession) other;
        return java.util.Objects.equals(id, that.id) && selected == that.selected && running == that.running && requestCount == that.requestCount;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + Boolean.hashCode(selected);
        hash = 31 * hash + Boolean.hashCode(running);
        hash = 31 * hash + Integer.hashCode(requestCount);
        return hash;
    }
    @Override public String toString() { return "GuideUiSession[id=" + id + ", selected=" + selected + ", running=" + running + ", requestCount=" + requestCount + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideUiSession> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideUiSession.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideUiSession>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideUiSession.class, "id", GuideUiSession::id), new dev.openallay.value.ValueSchema.Component<>(GuideUiSession.class, "selected", GuideUiSession::selected), new dev.openallay.value.ValueSchema.Component<>(GuideUiSession.class, "running", GuideUiSession::running), new dev.openallay.value.ValueSchema.Component<>(GuideUiSession.class, "requestCount", GuideUiSession::requestCount)), arguments -> new GuideUiSession((String) arguments[0], (Boolean) arguments[1], (Boolean) arguments[2], (Integer) arguments[3]));
        }
    }
}
