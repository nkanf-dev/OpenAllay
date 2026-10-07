package dev.openallay.guide.history;

import java.util.Objects;
import java.util.UUID;

/** Typed durable-history deletion scope; no path or foreign actor is supplied by UI text. */
public sealed interface GuideHistoryDeleteScope {
    @dev.openallay.value.ValueType(Partition.ValueSchemaProvider.class)
public static final class Partition implements GuideHistoryDeleteScope {
    private final GuideHistoryScope scope;
    public Partition(GuideHistoryScope scope) {

            Objects.requireNonNull(scope, "scope");

        this.scope = scope;
    }
    public GuideHistoryScope scope() { return scope; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Partition)) return false;
        Partition that = (Partition) other;
        return java.util.Objects.equals(scope, that.scope);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(scope);
        return hash;
    }
    @Override public String toString() { return "Partition[scope=" + scope + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Partition> schema() {
            return new dev.openallay.value.ValueSchema<>(Partition.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Partition>>asList(new dev.openallay.value.ValueSchema.Component<>(Partition.class, "scope", Partition::scope)), arguments -> new Partition((GuideHistoryScope) arguments[0]));
        }
    }
}

    @dev.openallay.value.ValueType(Actor.ValueSchemaProvider.class)
public static final class Actor implements GuideHistoryDeleteScope {
    private final UUID actorId;
    public Actor(UUID actorId) {

            Objects.requireNonNull(actorId, "actorId");

        this.actorId = actorId;
    }
    public UUID actorId() { return actorId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Actor)) return false;
        Actor that = (Actor) other;
        return java.util.Objects.equals(actorId, that.actorId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        return hash;
    }
    @Override public String toString() { return "Actor[actorId=" + actorId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Actor> schema() {
            return new dev.openallay.value.ValueSchema<>(Actor.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Actor>>asList(new dev.openallay.value.ValueSchema.Component<>(Actor.class, "actorId", Actor::actorId)), arguments -> new Actor((UUID) arguments[0]));
        }
    }
}

    static Partition partition(GuideHistoryScope scope) {
        return new Partition(scope);
    }

    static Actor actor(UUID actorId) {
        return new Actor(actorId);
    }
}
