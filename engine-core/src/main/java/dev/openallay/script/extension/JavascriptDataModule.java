package dev.openallay.script.extension;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ToolInvocationContext;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Trusted Java-side projector for optional extension data already detached into a request context.
 *
 * <p>This method runs on the Agent worker and therefore must only inspect immutable values from
 * {@link ToolInvocationContext}. It must not call Minecraft or mod APIs, use reflection to reach
 * live objects, or retain thread-owned state. Loader integrations that need live state capture and
 * detach it on the owning Minecraft thread before constructing the request context. Rhino receives
 * the returned detached record/collection graph through OpenAllay's component-only host adapter;
 * generic methods, classes, and reflection authority are never exposed.
 */
public interface JavascriptDataModule {
    String id();

    /** Stable detached value type used for discovery without capturing the module. */
    Type valueType();

    /** Player-facing summary of the data contributed by this module. */
    default String summary() {
        return id();
    }

    Snapshot capture(ToolInvocationContext context);

    @dev.openallay.value.ValueType(Snapshot.ValueSchemaProvider.class)
public static final class Snapshot {
    private final Object value;
    private final List<EvidenceMetadata> evidence;
    public Snapshot(Object value, List<EvidenceMetadata> evidence) {

            value = java.util.Objects.requireNonNull(value, "value");
            evidence = List.copyOf(evidence);
            if (evidence.isEmpty()) {
                throw new IllegalArgumentException("JavaScript module snapshot requires evidence");
            }

        this.value = value;
        this.evidence = evidence;
    }
    public Object value() { return value; }
    public List<EvidenceMetadata> evidence() { return evidence; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Snapshot)) return false;
        Snapshot that = (Snapshot) other;
        return java.util.Objects.equals(value, that.value) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(value);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "Snapshot[value=" + value + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Snapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(Snapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Snapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(Snapshot.class, "value", Snapshot::value), new dev.openallay.value.ValueSchema.Component<>(Snapshot.class, "evidence", Snapshot::evidence)), arguments -> new Snapshot((Object) arguments[0], (List) arguments[1]));
        }
    }
}
}
