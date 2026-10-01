package dev.openallay.knowledge;

import java.util.List;
import java.util.Objects;

/** Counts-only status published by the existing knowledge reload, without document payloads. */
public record KnowledgeSourceSnapshot(
        boolean loaded,
        boolean retained,
        String failureCode,
        List<Source> sources) {
    public enum State { AVAILABLE, PARTIAL, UNAVAILABLE, FAILED }

    public KnowledgeSourceSnapshot {
        sources = List.copyOf(sources);
        if (!loaded && (retained || failureCode != null || !sources.isEmpty())) {
            throw new IllegalArgumentException("Unloaded knowledge has no observed sources");
        }
    }

    public static KnowledgeSourceSnapshot notLoaded() {
        return new KnowledgeSourceSnapshot(false, false, null, List.of());
    }

    public record Source(
            String sourceId, String generation, State state, Integer itemCount, String failureCode) {
        public Source {
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
        }
    }
}
