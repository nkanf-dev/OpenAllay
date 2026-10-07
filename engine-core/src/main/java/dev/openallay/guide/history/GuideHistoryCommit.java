package dev.openallay.guide.history;

import java.util.List;

/** One ordered atomic mutation batch for exactly one durable partition. */
@dev.openallay.value.ValueType(GuideHistoryCommit.ValueSchemaProvider.class)
public final class GuideHistoryCommit {
    private final GuideHistoryScope scope;
    private final List<GuideHistoryMutation> mutations;
    public GuideHistoryCommit(GuideHistoryScope scope, List<GuideHistoryMutation> mutations) {

        java.util.Objects.requireNonNull(scope, "scope");
        mutations = dev.openallay.util.Java8Collections.listCopyOf(mutations);
        if (mutations.isEmpty()) {
            throw new IllegalArgumentException("history commit must not be empty");
        }

        this.scope = scope;
        this.mutations = mutations;
    }
    public GuideHistoryScope scope() { return scope; }
    public List<GuideHistoryMutation> mutations() { return mutations; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHistoryCommit)) return false;
        GuideHistoryCommit that = (GuideHistoryCommit) other;
        return java.util.Objects.equals(scope, that.scope) && java.util.Objects.equals(mutations, that.mutations);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(scope);
        hash = 31 * hash + java.util.Objects.hashCode(mutations);
        return hash;
    }
    @Override public String toString() { return "GuideHistoryCommit[scope=" + scope + ", mutations=" + mutations + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHistoryCommit> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHistoryCommit.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHistoryCommit>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHistoryCommit.class, "scope", GuideHistoryCommit::scope), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryCommit.class, "mutations", GuideHistoryCommit::mutations)), arguments -> new GuideHistoryCommit((GuideHistoryScope) arguments[0], (List) arguments[1]));
        }
    }
}
