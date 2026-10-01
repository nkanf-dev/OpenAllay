package dev.openallay.guide;

import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentState;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import java.math.BigDecimal;
import java.util.UUID;

/** Client-dispatcher-owned counters. Each model call is sealed once, not once per stream delta. */
final class GuideUsageTracker {
    private final Totals session = new Totals();
    private Totals request = new Totals();
    private UUID requestId;
    private GuideModelSelection selection;
    private String modelIdentifier;
    private BuiltinModelCatalog.Pricing pricing;
    private ModelUsage pending;
    private boolean sealed;
    private boolean attempted;
    private boolean terminal;

    void begin(UUID id, GuideModelSelection selected, String model) {
        requestId = id;
        selection = selected;
        modelIdentifier = model;
        pricing = model == null ? null : BuiltinModelCatalog.bundled().catalog()
                .match(model).map(match -> match.entry().pricing()).orElse(null);
        request = new Totals();
        pending = null;
        sealed = false;
        attempted = false;
        terminal = false;
    }

    UUID requestId() { return requestId; }
    GuideModelSelection selection() { return selection; }
    String modelIdentifier() { return modelIdentifier; }

    void accept(AgentEvent event) {
        if (requestId == null || terminal) return;
        if (event instanceof AgentEvent.StateChanged state && state.state() == AgentState.MODEL_WAIT) {
            if (sealed) startCall();
        } else if (event instanceof AgentEvent.ModelProgress progress) {
            switch (progress.event()) {
                case ModelEvent.AttemptStarted ignored -> {
                    if (sealed) startCall();
                    // Retries replace the unsealed report instead of billing failed attempts twice.
                    pending = null;
                    attempted = true;
                }
                case ModelEvent.UsageUpdate update -> {
                    if (!sealed) {
                        pending = update.usage();
                        attempted = true;
                    }
                }
                case ModelEvent.MessageComplete ignored -> seal();
                default -> { }
            }
        } else if (event instanceof AgentEvent.FinalText || event instanceof AgentEvent.Failed) {
            if (!sealed && attempted) seal();
            terminal = true;
        }
    }

    private void startCall() {
        sealed = false;
        attempted = false;
        pending = null;
    }

    private void seal() {
        if (sealed) return;
        BigDecimal cost = estimate(pending, pricing);
        request.add(pending, cost);
        session.add(pending, cost);
        pending = null;
        sealed = true;
    }

    GuideUsageSnapshot requestSnapshot() { return request.snapshot(!terminal && !sealed); }
    GuideUsageSnapshot sessionSnapshot() { return session.snapshot(!terminal && !sealed); }

    /** Published reference price only. Cache accounting differs by protocol and is not inferred. */
    static BigDecimal estimate(ModelUsage usage, BuiltinModelCatalog.Pricing pricing) {
        if (usage == null || usage.cacheReadTokens() != 0 || pricing == null
                || !"USD".equals(pricing.currency()) || !"million_tokens".equals(pricing.unit())) return null;
        BuiltinModelCatalog.Tier selected = null;
        for (var tier : pricing.tiers()) {
            if (usage.inputTokens() >= tier.minInputTokens()) selected = tier;
        }
        if (selected == null || selected.input() == null || selected.output() == null) return null;
        return selected.input().multiply(BigDecimal.valueOf(usage.inputTokens()))
                .add(selected.output().multiply(BigDecimal.valueOf(usage.outputTokens())))
                .movePointLeft(6);
    }

    private static final class Totals {
        private long input;
        private long output;
        private int reported;
        private boolean missing;
        private boolean costMissing;
        private BigDecimal cost = BigDecimal.ZERO;

        void add(ModelUsage usage, BigDecimal estimate) {
            if (usage == null) {
                missing = true;
                costMissing = true;
                return;
            }
            input = Math.addExact(input, usage.inputTokens());
            output = Math.addExact(output, usage.outputTokens());
            reported++;
            if (estimate == null) costMissing = true;
            else cost = cost.add(estimate);
        }

        GuideUsageSnapshot snapshot(boolean inFlight) {
            return new GuideUsageSnapshot(input, output, reported, missing || inFlight,
                    reported == 0 || costMissing || inFlight ? null : cost);
        }
    }
}
