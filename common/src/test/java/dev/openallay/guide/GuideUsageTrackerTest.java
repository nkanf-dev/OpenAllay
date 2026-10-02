package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentState;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuideUsageTrackerTest {
    @Test
    void pendingStartsPersistUnknownWithoutCountingAndSealedIdsIgnoreDuplicateLateStarts() {
        GuideUsageTracker tracker = tracker();
        UUID call = UUID.randomUUID();
        tracker.accept(new AgentEvent.ModelUsageStarted(call, ""));
        tracker.accept(new AgentEvent.ModelUsageStarted(call, ""));
        assertEquals(1, tracker.requestSnapshot().actualCalls());
        assertNull(tracker.requestSnapshot().estimatedUsd());
        assertTrue(tracker.sessionSnapshot().costIncomplete());
        observe(tracker, call, "", new ModelUsage(10, 2, 0));
        tracker.accept(new AgentEvent.ModelUsageStarted(call, ""));
        assertEquals(1, tracker.sessionSnapshot().actualCalls());
        assertFalse(tracker.sessionSnapshot().incomplete());
    }

    @Test
    void actualCallIdentityOwnsCountingNotUiStatesOrDuplicateStreamCompletion() {
        GuideUsageTracker tracker = tracker();
        UUID call = UUID.randomUUID();
        tracker.accept(new AgentEvent.ModelProgress(new ModelEvent.AttemptStarted(1, null)));
        assertEquals(GuideUsageSnapshot.empty(), tracker.sessionSnapshot());
        observe(tracker, call, null, new ModelUsage(10, 3, 0));
        observe(tracker, call, null, new ModelUsage(10, 3, 0));
        tracker.accept(new AgentEvent.StateChanged(AgentState.MODEL_WAIT));
        tracker.accept(new AgentEvent.ModelProgress(new ModelEvent.UsageUpdate(new ModelUsage(999, 999, 0))));
        tracker.accept(new AgentEvent.ModelProgress(new ModelEvent.MessageComplete("stop")));
        tracker.accept(new AgentEvent.ModelProgress(new ModelEvent.MessageComplete("stop")));
        observe(tracker, UUID.randomUUID(), null, new ModelUsage(20, 4, 0));
        assertEquals(30, tracker.requestSnapshot().inputTokens());
        assertEquals(7, tracker.requestSnapshot().outputTokens());
        assertEquals(2, tracker.requestSnapshot().actualCalls());
        assertEquals(2, tracker.requestSnapshot().reportedCalls());
        assertFalse(tracker.requestSnapshot().incomplete());
    }

    @Test
    void eachRetryIsARealAttemptMissingIsUnknownAndReportedZeroIsKnown() {
        GuideUsageTracker tracker = tracker();
        observe(tracker, UUID.randomUUID(), null, new ModelUsage(50, 10, 0));
        observe(tracker, UUID.randomUUID(), null, ModelUsage.empty());
        observe(tracker, UUID.randomUUID(), null, new ModelUsage(0, 0, 0));
        var sum = tracker.requestSnapshot();
        assertEquals(3, sum.actualCalls());
        assertEquals(2, sum.reportedCalls());
        assertEquals(50, sum.inputTokens());
        assertTrue(sum.incomplete());
        assertNull(sum.cacheHitRate());
        assertNull(sum.estimatedUsd());
        assertTrue(sum.costIncomplete());
    }

    @Test
    void requestResetAndLateCancelledObservationStayWithOriginalOwner() {
        GuideUsageTracker tracker = tracker();
        UUID old = tracker.requestId();
        observe(tracker, UUID.randomUUID(), null, new ModelUsage(10, 2, 0));
        tracker.accept(new AgentEvent.Failed("agent_cancelled", "cancelled"));
        UUID next = UUID.randomUUID();
        tracker.begin(next, GuideModelSelection.client("other"), null);
        assertFalse(tracker.requestSnapshot().known());
        tracker.accept(old, new AgentEvent.ModelUsageObserved(UUID.randomUUID(), "", new ModelUsage(7, 1, 0)));
        assertEquals(17, tracker.sessionSnapshot().inputTokens());
        assertEquals(17, tracker.requestSnapshot(old).inputTokens());
        assertEquals(0, tracker.requestSnapshot(next).inputTokens());
    }

    @Test
    void publishedCacheReadAndWriteRatesUseCanonicalCategoriesAndEachCallsTier() {
        var pricing = new BuiltinModelCatalog.Pricing("USD", "million_tokens", List.of(
                tier(0, "1", "2", "0.1", "1.25"), tier(200_000, "3", "6", "0.3", "3.75")), "reference");
        var first = ModelUsage.anthropic(50_000, true, 10, true, 30_000, true, 20_000, true);
        var second = ModelUsage.openAi(300_000, true, 20, true, 100_000, true);
        assertEquals(0, new BigDecimal("0.70814").compareTo(
                GuideUsageTracker.estimate(first, pricing).add(GuideUsageTracker.estimate(second, pricing))));
        var sum = GuideUsageTracker.project(first, pricing).plus(GuideUsageTracker.project(second, pricing));
        assertEquals(400_000, sum.inputTokens());
        assertEquals(130_000, sum.cacheReadTokens());
        assertEquals(20_000, sum.cacheWriteTokens());
        assertEquals(0, new BigDecimal("0.325").compareTo(sum.cacheHitRate()));
        assertFalse(sum.costIncomplete());
    }

    @Test
    void missingCacheFieldIsUnknownWhileExplicitZeroGivesZeroHitRate() {
        var pricing = new BuiltinModelCatalog.Pricing("USD", "million_tokens", List.of(tier(0, "1", "2", "0.1", "1.25")), "reference");
        var unknown = GuideUsageTracker.project(ModelUsage.openAi(100, true, 5, true, 0, false), pricing);
        assertNull(unknown.cacheHitRate());
        assertTrue(unknown.costIncomplete());
        assertEquals(0, new BigDecimal("0.00001").compareTo(unknown.estimatedUsd()));
        var zero = GuideUsageTracker.project(ModelUsage.openAi(100, true, 5, true, 0, true), pricing);
        assertEquals(0, BigDecimal.ZERO.compareTo(zero.cacheHitRate()));
        assertFalse(zero.costIncomplete());
        assertEquals(0, new BigDecimal("0.00011").compareTo(zero.estimatedUsd()));
    }

    @Test
    void missingPricesKeepKnownAmountRatherThanReplaceWholeSessionWithUnknown() {
        var pricing = new BuiltinModelCatalog.Pricing("USD", "million_tokens", List.of(tier(0, "1", "2", null, null)), "reference");
        var report = ModelUsage.anthropic(100, true, 5, true, 50, true, 25, true);
        var partial = GuideUsageTracker.project(report, pricing);
        assertTrue(partial.costIncomplete());
        assertEquals(0, new BigDecimal("0.00011").compareTo(partial.estimatedUsd()));
        assertNull(GuideUsageTracker.estimate(report, pricing));
        var missing = GuideUsageTracker.project(ModelUsage.empty(), pricing);
        assertNull(missing.estimatedUsd());
        assertEquals(partial.estimatedUsd(), partial.plus(missing).estimatedUsd());
        assertNull(GuideUsageSnapshot.empty().plus(missing).estimatedUsd());
    }

    @Test
    void restoreAndForkReferencesNeverRepriceOrBillInheritedHistoryTwice() {
        var own = new GuideUsageSnapshot(100, 10, 25, 0, 2, 2, false, false, new BigDecimal("0.123456"), false);
        var inherited = new GuideUsageSnapshot(999, 33, 100, 20, 4, 4, false, false, new BigDecimal("9.876543"), false);
        GuideUsageTracker tracker = tracker();
        tracker.restore(own, inherited);
        tracker.begin(UUID.randomUUID(), GuideModelSelection.client("other"), "unknown-model");
        observe(tracker, UUID.randomUUID(), "unknown-model", new ModelUsage(20, 3, 0));
        assertEquals(120, tracker.sessionSnapshot().inputTokens());
        assertEquals(3, tracker.sessionSnapshot().actualCalls());
        assertEquals(own.estimatedUsd(), tracker.sessionSnapshot().estimatedUsd());
        assertTrue(tracker.sessionSnapshot().costIncomplete());
        assertEquals(inherited, tracker.inheritedSnapshot());
    }

    @Test
    void eachReportedModelUsesItsOwnPublishedPriceNotCurrentProfile() {
        var catalog = BuiltinModelCatalog.bundled().catalog();
        var models = catalog.models().stream().filter(entry -> entry.pricing() != null
                && entry.pricing().tiers().getFirst().input() != null
                && entry.pricing().tiers().getFirst().output() != null).limit(2).toList();
        assertEquals(2, models.size());
        GuideUsageTracker tracker = tracker();
        var usage = new ModelUsage(100, 20, 0);
        observe(tracker, UUID.randomUUID(), models.getFirst().id(), usage);
        tracker.begin(UUID.randomUUID(), GuideModelSelection.client("other"), "unpriced");
        observe(tracker, UUID.randomUUID(), models.getLast().id(), usage);
        assertEquals(0, GuideUsageTracker.estimate(usage, models.getFirst().pricing())
                .add(GuideUsageTracker.estimate(usage, models.getLast().pricing()))
                .compareTo(tracker.sessionSnapshot().estimatedUsd()));
    }

    @Test
    void releasedOwnerRejectsNewCallIdsWithoutLosingItsSealedTotals() {
        GuideUsageTracker tracker = tracker();
        UUID owner = tracker.requestId();
        UUID call = UUID.randomUUID();
        assertTrue(tracker.accept(owner, new AgentEvent.ModelUsageStarted(call, "model")));
        assertFalse(tracker.release(owner), "a real started call still owns its late receipt");
        assertTrue(tracker.accept(owner, new AgentEvent.ModelUsageObserved(call, "model", new ModelUsage(7, 2, 0))));
        GuideUsageSnapshot before = tracker.sessionSnapshot();
        assertTrue(tracker.release(owner));
        UUID late = UUID.randomUUID();
        assertFalse(tracker.accept(owner, new AgentEvent.ModelUsageStarted(late, "model")));
        assertFalse(tracker.accept(owner, new AgentEvent.ModelUsageObserved(late, "model", new ModelUsage(999, 99, 0))));
        assertFalse(tracker.accept(owner, new AgentEvent.ModelUsageObserved(call, "model", new ModelUsage(7, 2, 0))));
        assertEquals(before, tracker.sessionSnapshot());
        assertEquals(7, tracker.requestSnapshot(owner).inputTokens());
        assertEquals(1, tracker.requestSnapshot(owner).actualCalls());
    }

    private static BuiltinModelCatalog.Tier tier(int minimum, String input, String output, String read, String write) {
        return new BuiltinModelCatalog.Tier(minimum, decimal(input), decimal(output), decimal(read), decimal(write));
    }
    private static BigDecimal decimal(String text) { return text == null ? null : new BigDecimal(text); }
    private static GuideUsageTracker tracker() {
        GuideUsageTracker tracker = new GuideUsageTracker();
        tracker.begin(UUID.randomUUID(), GuideModelSelection.client("default"), null);
        return tracker;
    }
    private static void observe(GuideUsageTracker tracker, UUID id, String model, ModelUsage usage) {
        tracker.accept(new AgentEvent.ModelUsageObserved(id, model == null ? "" : model, usage));
    }
}
