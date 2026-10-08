package dev.openallay.world;

/** Selects real native frame content. WORLD never includes a game Screen or 2D HUD. */
@dev.openallay.value.ValueType(WorldViewRequest.ValueSchemaProvider.class)
public final class WorldViewRequest {
    private final Target target;
    public WorldViewRequest(Target target) {
 java.util.Objects.requireNonNull(target, "target");
        this.target = target;
    }
    public Target target() { return target; }
public enum Target { WORLD, GAME_UI, ASSOCIATED_UI }
public static WorldViewRequest defaults() { return new WorldViewRequest(Target.WORLD); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WorldViewRequest)) return false;
        WorldViewRequest that = (WorldViewRequest) other;
        return java.util.Objects.equals(target, that.target);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(target);
        return hash;
    }
    @Override public String toString() { return "WorldViewRequest[target=" + target + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<WorldViewRequest> schema() {
            return new dev.openallay.value.ValueSchema<>(WorldViewRequest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<WorldViewRequest>>asList(new dev.openallay.value.ValueSchema.Component<>(WorldViewRequest.class, "target", WorldViewRequest::target)), arguments -> new WorldViewRequest((Target) arguments[0]));
        }
    }
}
