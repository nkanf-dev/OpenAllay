package dev.openallay.knowledge;

import java.util.List;
import java.util.Objects;

/** Counts-only status published by the existing knowledge reload, without document payloads. */
@dev.openallay.value.ValueType(KnowledgeSourceSnapshot.ValueSchemaProvider.class)
public final class KnowledgeSourceSnapshot {
    private final boolean loaded;
    private final boolean retained;
    private final String failureCode;
    private final List<Source> sources;
    public KnowledgeSourceSnapshot(boolean loaded, boolean retained, String failureCode, List<Source> sources) {

        sources = List.copyOf(sources);
        if (!loaded && (retained || failureCode != null || !sources.isEmpty())) {
            throw new IllegalArgumentException("Unloaded knowledge has no observed sources");
        }

        this.loaded = loaded;
        this.retained = retained;
        this.failureCode = failureCode;
        this.sources = sources;
    }
    public boolean loaded() { return loaded; }
    public boolean retained() { return retained; }
    public String failureCode() { return failureCode; }
    public List<Source> sources() { return sources; }
public enum State { AVAILABLE, PARTIAL, UNAVAILABLE, FAILED }
public static KnowledgeSourceSnapshot notLoaded() {
        return new KnowledgeSourceSnapshot(false, false, null, List.of());
    }
@dev.openallay.value.ValueType(Source.ValueSchemaProvider.class)
public static final class Source {
    private final String sourceId;
    private final String generation;
    private final State state;
    private final Integer itemCount;
    private final String failureCode;
    public Source(String sourceId, String generation, State state, Integer itemCount, String failureCode) {

            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(state, "state");
            if (itemCount != null && itemCount < 0) {
                throw new IllegalArgumentException("Source count must not be negative");
            }
            boolean generated = state == State.AVAILABLE || state == State.PARTIAL;
            if (generated != (generation != null) || generated && itemCount == null
                    || (state == State.AVAILABLE) != (failureCode == null)) {
                throw new IllegalArgumentException("Source observation state is inconsistent");
            }

        this.sourceId = sourceId;
        this.generation = generation;
        this.state = state;
        this.itemCount = itemCount;
        this.failureCode = failureCode;
    }
    public String sourceId() { return sourceId; }
    public String generation() { return generation; }
    public State state() { return state; }
    public Integer itemCount() { return itemCount; }
    public String failureCode() { return failureCode; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Source)) return false;
        Source that = (Source) other;
        return java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(generation, that.generation) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(itemCount, that.itemCount) && java.util.Objects.equals(failureCode, that.failureCode);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(itemCount);
        hash = 31 * hash + java.util.Objects.hashCode(failureCode);
        return hash;
    }
    @Override public String toString() { return "Source[sourceId=" + sourceId + ", generation=" + generation + ", state=" + state + ", itemCount=" + itemCount + ", failureCode=" + failureCode + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Source> schema() {
            return new dev.openallay.value.ValueSchema<>(Source.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Source>>asList(new dev.openallay.value.ValueSchema.Component<>(Source.class, "sourceId", Source::sourceId), new dev.openallay.value.ValueSchema.Component<>(Source.class, "generation", Source::generation), new dev.openallay.value.ValueSchema.Component<>(Source.class, "state", Source::state), new dev.openallay.value.ValueSchema.Component<>(Source.class, "itemCount", Source::itemCount), new dev.openallay.value.ValueSchema.Component<>(Source.class, "failureCode", Source::failureCode)), arguments -> new Source((String) arguments[0], (String) arguments[1], (State) arguments[2], (Integer) arguments[3], (String) arguments[4]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof KnowledgeSourceSnapshot)) return false;
        KnowledgeSourceSnapshot that = (KnowledgeSourceSnapshot) other;
        return loaded == that.loaded && retained == that.retained && java.util.Objects.equals(failureCode, that.failureCode) && java.util.Objects.equals(sources, that.sources);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(loaded);
        hash = 31 * hash + Boolean.hashCode(retained);
        hash = 31 * hash + java.util.Objects.hashCode(failureCode);
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        return hash;
    }
    @Override public String toString() { return "KnowledgeSourceSnapshot[loaded=" + loaded + ", retained=" + retained + ", failureCode=" + failureCode + ", sources=" + sources + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<KnowledgeSourceSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(KnowledgeSourceSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<KnowledgeSourceSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(KnowledgeSourceSnapshot.class, "loaded", KnowledgeSourceSnapshot::loaded), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSourceSnapshot.class, "retained", KnowledgeSourceSnapshot::retained), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSourceSnapshot.class, "failureCode", KnowledgeSourceSnapshot::failureCode), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSourceSnapshot.class, "sources", KnowledgeSourceSnapshot::sources)), arguments -> new KnowledgeSourceSnapshot((Boolean) arguments[0], (Boolean) arguments[1], (String) arguments[2], (List) arguments[3]));
        }
    }
}
