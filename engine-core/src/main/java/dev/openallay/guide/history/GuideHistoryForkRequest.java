package dev.openallay.guide.history;

/** One immutable completed-request cutoff in exactly one actor/world/server partition. */
@dev.openallay.value.ValueType(GuideHistoryForkRequest.ValueSchemaProvider.class)
public final class GuideHistoryForkRequest {
    private final GuideHistoryScope scope;
    private final GuideHistoryMutation.ForkSession mutation;
    public GuideHistoryForkRequest(GuideHistoryScope scope, GuideHistoryMutation.ForkSession mutation) {

        java.util.Objects.requireNonNull(scope, "scope");
        java.util.Objects.requireNonNull(mutation, "mutation");

        this.scope = scope;
        this.mutation = mutation;
    }
    public GuideHistoryScope scope() { return scope; }
    public GuideHistoryMutation.ForkSession mutation() { return mutation; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHistoryForkRequest)) return false;
        GuideHistoryForkRequest that = (GuideHistoryForkRequest) other;
        return java.util.Objects.equals(scope, that.scope) && java.util.Objects.equals(mutation, that.mutation);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(scope);
        hash = 31 * hash + java.util.Objects.hashCode(mutation);
        return hash;
    }
    @Override public String toString() { return "GuideHistoryForkRequest[scope=" + scope + ", mutation=" + mutation + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHistoryForkRequest> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHistoryForkRequest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHistoryForkRequest>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHistoryForkRequest.class, "scope", GuideHistoryForkRequest::scope), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryForkRequest.class, "mutation", GuideHistoryForkRequest::mutation)), arguments -> new GuideHistoryForkRequest((GuideHistoryScope) arguments[0], (GuideHistoryMutation.ForkSession) arguments[1]));
        }
    }
}
