package dev.openallay.guide.history;

/** One immutable completed-request cutoff in exactly one actor/world/server partition. */
public record GuideHistoryForkRequest(
        GuideHistoryScope scope, GuideHistoryMutation.ForkSession mutation) {
    public GuideHistoryForkRequest {
        java.util.Objects.requireNonNull(scope, "scope");
        java.util.Objects.requireNonNull(mutation, "mutation");
    }
}
