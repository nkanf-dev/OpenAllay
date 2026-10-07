package dev.openallay.bridge;

import dev.openallay.model.CancellationSignal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CorrelationRegistry {
    @dev.openallay.value.ValueType(Entry.ValueSchemaProvider.class)
public static final class Entry {
    private final UUID actorId;
    private final CancellationSignal cancellation;
    public Entry(UUID actorId, CancellationSignal cancellation) {
        this.actorId = actorId;
        this.cancellation = cancellation;
    }
    public UUID actorId() { return actorId; }
    public CancellationSignal cancellation() { return cancellation; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Entry)) return false;
        Entry that = (Entry) other;
        return java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(cancellation, that.cancellation);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(cancellation);
        return hash;
    }
    @Override public String toString() { return "Entry[actorId=" + actorId + ", cancellation=" + cancellation + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Entry> schema() {
            return new dev.openallay.value.ValueSchema<>(Entry.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Entry>>asList(new dev.openallay.value.ValueSchema.Component<>(Entry.class, "actorId", Entry::actorId), new dev.openallay.value.ValueSchema.Component<>(Entry.class, "cancellation", Entry::cancellation)), arguments -> new Entry((UUID) arguments[0], (CancellationSignal) arguments[1]));
        }
    }
}

    private final Map<UUID, Entry> entries = new HashMap<>();

    public synchronized boolean register(UUID actorId, UUID correlationId, CancellationSignal cancellation) {
        return entries.putIfAbsent(correlationId, new Entry(actorId, cancellation)) == null;
    }

    public synchronized Optional<Entry> find(UUID actorId, UUID correlationId) {
        Entry entry = entries.get(correlationId);
        return entry != null && entry.actorId().equals(actorId) ? Optional.of(entry) : Optional.empty();
    }

    public synchronized boolean complete(UUID actorId, UUID correlationId) {
        Entry entry = entries.get(correlationId);
        if (entry == null || !entry.actorId().equals(actorId)) {
            return false;
        }
        entries.remove(correlationId);
        return true;
    }

    public synchronized boolean cancel(UUID actorId, UUID correlationId) {
        Optional<Entry> entry = find(actorId, correlationId);
        if (entry.isEmpty()) {
            return false;
        }
        entries.remove(correlationId);
        entry.orElseThrow().cancellation().cancel();
        return true;
    }

    public synchronized int cancelActor(UUID actorId) {
        java.util.List<java.util.UUID> owned = entries.entrySet().stream()
                .filter(entry -> entry.getValue().actorId().equals(actorId))
                .map(Map.Entry::getKey)
                .toList();
        owned.forEach(id -> cancel(actorId, id));
        return owned.size();
    }
}
