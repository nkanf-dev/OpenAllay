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
    void updatesReplaceUntilCompleteAndEachModelCallAddsOnce() {
        GuideUsageTracker tracker = tracker();
        event(tracker, new ModelEvent.AttemptStarted(1, null));
        event(tracker, new ModelEvent.UsageUpdate(new ModelUsage(10, 1, 0)));
        event(tracker, new ModelEvent.UsageUpdate(new ModelUsage(10, 3, 0)));
        event(tracker, new ModelEvent.MessageComplete("tool_calls"));
        event(tracker, new ModelEvent.MessageComplete("tool_calls"));
        event(tracker, new ModelEvent.UsageUpdate(new ModelUsage(10, 3, 0)));
        assertEquals(10, tracker.requestSnapshot().inputTokens());
        assertEquals(3, tracker.requestSnapshot().outputTokens());
        assertEquals(1, tracker.requestSnapshot().reportedCalls());
        tracker.accept(new AgentEvent.StateChanged(AgentState.MODEL_WAIT));
        event(tracker, new ModelEvent.AttemptStarted(1, null));
        event(tracker, new ModelEvent.UsageUpdate(new ModelUsage(20, 4, 0)));
        event(tracker, new ModelEvent.MessageComplete("stop"));
        tracker.accept(new AgentEvent.FinalText("answer"));
        event(tracker, new ModelEvent.UsageUpdate(new ModelUsage(999, 999, 0)));
        assertEquals(30, tracker.requestSnapshot().inputTokens());
        assertEquals(7, tracker.requestSnapshot().outputTokens());
        assertEquals(2, tracker.requestSnapshot().reportedCalls());
        assertFalse(tracker.requestSnapshot().incomplete());
    }

    @Test
    void requestResetRetainsOnlyObservedSessionTotalsAndCancellationFencesLateEvents() {
        GuideUsageTracker tracker = tracker();
        event(tracker, new ModelEvent.UsageUpdate(new ModelUsage(10, 2, 0)));
        tracker.accept(new AgentEvent.Failed("agent_cancelled", "cancelled"));
        event(tracker, new ModelEvent.MessageComplete("stop"));
        assertEquals(10, tracker.sessionSnapshot().inputTokens());
        tracker.begin(UUID.randomUUID(), GuideModelSelection.client("default"), null);
        assertFalse(tracker.requestSnapshot().known());
        assertEquals(10, tracker.sessionSnapshot().inputTokens());
        event(tracker, new ModelEvent.MessageComplete("stop"));
        tracker.accept(new AgentEvent.FinalText("answer"));
        assertFalse(tracker.requestSnapshot().known());
        assertTrue(tracker.requestSnapshot().incomplete());
        assertTrue(tracker.sessionSnapshot().incomplete());
    }

    @Test
    void missingReportsAreNotZeroButActualZeroIsKnownAndRetriesReplacePendingUsage() {
        GuideUsageTracker tracker = tracker();
        event(tracker, new ModelEvent.AttemptStarted(1, null));
        event(tracker, new ModelEvent.UsageUpdate(new ModelUsage(50, 10, 0)));
        event(tracker, new ModelEvent.AttemptStarted(2, null));
        event(tracker, new ModelEvent.UsageUpdate(ModelUsage.empty()));
        event(tracker, new ModelEvent.MessageComplete("stop"));
        assertTrue(tracker.requestSnapshot().known());
        assertEquals(0, tracker.requestSnapshot().inputTokens());
        assertEquals(1, tracker.requestSnapshot().reportedCalls());
    }

    @Test
    void eachCallSelectsItsOwnPublishedInputTierAndCacheOrMissingRatesRemainUnknown() {
        var pricing = new BuiltinModelCatalog.Pricing("USD", "million_tokens", List.of(
                tier(0, "1", "2"), tier(200_000, "3", "6")), "reference");
        BigDecimal first = GuideUsageTracker.estimate(new ModelUsage(100_000, 10, 0), pricing);
        BigDecimal second = GuideUsageTracker.estimate(new ModelUsage(300_000, 20, 0), pricing);
        assertEquals(0, new BigDecimal("1.00014").compareTo(first.add(second)));
        assertNull(GuideUsageTracker.estimate(new ModelUsage(1, 1, 1), pricing));
        assertNull(GuideUsageTracker.estimate(new ModelUsage(1, 1, 0), null));
        assertNull(GuideUsageTracker.estimate(null, pricing));
        var missingOutput = new BuiltinModelCatalog.Pricing("USD", "million_tokens", List.of(
                new BuiltinModelCatalog.Tier(0, BigDecimal.ONE, null, null, null)), "");
        assertNull(GuideUsageTracker.estimate(new ModelUsage(1, 1, 0), missingOutput));
    }

    @Test
    void trackerSumsEachActualModelCallAtItsOwnTier() {
        var model = BuiltinModelCatalog.bundled().catalog().models().stream()
                .filter(entry -> entry.pricing() != null && entry.pricing().tiers().size() > 1
                        && entry.pricing().tiers().stream().allMatch(tier -> tier.input() != null && tier.output() != null))
                .findFirst().orElseThrow();
        var first = new ModelUsage(100, 10, 0);
        var second = new ModelUsage(model.pricing().tiers().getLast().minInputTokens() + 1L, 20, 0);
        GuideUsageTracker tracker = new GuideUsageTracker();
        tracker.begin(UUID.randomUUID(), GuideModelSelection.client("default"), model.id());
        event(tracker, new ModelEvent.UsageUpdate(first));
        event(tracker, new ModelEvent.MessageComplete("tool_calls"));
        tracker.accept(new AgentEvent.StateChanged(AgentState.MODEL_WAIT));
        event(tracker, new ModelEvent.UsageUpdate(second));
        event(tracker, new ModelEvent.MessageComplete("stop"));
        assertEquals(0, GuideUsageTracker.estimate(first, model.pricing())
                .add(GuideUsageTracker.estimate(second, model.pricing()))
                .compareTo(tracker.requestSnapshot().estimatedUsd()));
    }

    private static BuiltinModelCatalog.Tier tier(int minimum, String input, String output) {
        return new BuiltinModelCatalog.Tier(minimum, new BigDecimal(input), new BigDecimal(output), null, null);
    }
    private static GuideUsageTracker tracker() {
        GuideUsageTracker tracker = new GuideUsageTracker();
        tracker.begin(UUID.randomUUID(), GuideModelSelection.client("default"), null);
        return tracker;
    }
    private static void event(GuideUsageTracker tracker, ModelEvent event) {
        tracker.accept(new AgentEvent.ModelProgress(event));
    }
}
