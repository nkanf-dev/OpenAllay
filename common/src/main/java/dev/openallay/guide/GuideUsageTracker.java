package dev.openallay.guide;

import dev.openallay.agent.AgentEvent;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client-dispatcher-owned numeric projection. Actual call IDs, not stream/UI states, own counting. */
final class GuideUsageTracker {
    private final Map<UUID, GuideUsageSnapshot> requests = new LinkedHashMap<>();
    private final Map<UUID, String> requestModels = new LinkedHashMap<>();
    private final Set<UUID> calls = new HashSet<>();
    private final Map<UUID, UUID> pendingCalls = new LinkedHashMap<>();
    private final Set<UUID> controls = new HashSet<>();
    private GuideUsageSnapshot session = GuideUsageSnapshot.empty();
    private GuideUsageSnapshot inherited = GuideUsageSnapshot.empty();
    private GuideUsageSnapshot control = GuideUsageSnapshot.empty();
    private UUID requestId;
    private GuideModelSelection selection;
    private String modelIdentifier;

    void begin(UUID id, GuideModelSelection selected, String model) {
        requestId = id;
        selection = selected;
        modelIdentifier = model;
        requests.put(id, GuideUsageSnapshot.empty());
        requestModels.put(id, model);
    }

    void restore(GuideUsageSnapshot own, GuideUsageSnapshot reference) {
        restore(own, reference, GuideUsageSnapshot.empty());
    }

    void restore(GuideUsageSnapshot own, GuideUsageSnapshot reference, GuideUsageSnapshot standalone) {
        session = java.util.Objects.requireNonNull(own, "own usage");
        inherited = java.util.Objects.requireNonNull(reference, "inherited usage");
        control = java.util.Objects.requireNonNull(standalone, "control usage");
    }

    void registerControl(UUID owner, GuideModelSelection selected, String model) {
        java.util.Objects.requireNonNull(owner, "control owner");
        java.util.Objects.requireNonNull(selected, "selection");
        controls.add(owner);
        requests.put(owner, GuideUsageSnapshot.empty());
        requestModels.put(owner, model);
    }

    void finishControl(UUID owner) {
        if (controls.contains(owner) && !pendingCalls.containsValue(owner)) {
            controls.remove(owner);
            requestModels.remove(owner);
            requests.remove(owner);
        }
    }

    GuideUsageSnapshot controlSnapshot() {
        int pending = (int) pendingCalls.values().stream().filter(controls::contains).count();
        return pending == 0 ? control : control.pending(pending);
    }

    UUID requestId() { return requestId; }
    GuideModelSelection selection() { return selection; }
    String modelIdentifier() { return modelIdentifier; }

    /** A released callback owner cannot receive a new billed call. Existing totals remain. */
    boolean release(UUID owner) {
        if (pendingCalls.containsValue(owner)) return false;
        requestModels.remove(owner);
        return true;
    }

    void accept(AgentEvent event) { accept(requestId, event); }

    boolean accept(UUID owner, AgentEvent event) {
        if (owner == null || !requestModels.containsKey(owner)) return false;
        if (event instanceof AgentEvent.ModelUsageStarted started) {
            if (calls.contains(started.callId())) return false;
            return pendingCalls.putIfAbsent(started.callId(), owner) == null;
        }
        if (!(event instanceof AgentEvent.ModelUsageObserved observed)) return false;
        UUID startedOwner = pendingCalls.get(observed.callId());
        if (startedOwner != null && !startedOwner.equals(owner) || !calls.add(observed.callId())) return false;
        String model = observed.modelIdentifier() == null || observed.modelIdentifier().isBlank()
                ? requestModels.get(owner) : observed.modelIdentifier();
        GuideUsageSnapshot delta = project(observed.usage(), pricing(model));
        requests.merge(owner, delta, GuideUsageSnapshot::plus);
        pendingCalls.remove(observed.callId());
        session = session.plus(delta);
        if (controls.contains(owner)) control = control.plus(delta);
        return true;
    }

    GuideUsageSnapshot requestSnapshot() { return requestSnapshot(requestId); }
    GuideUsageSnapshot requestSnapshot(UUID owner) {
        GuideUsageSnapshot value = requests.getOrDefault(owner, GuideUsageSnapshot.empty());
        int count = (int) pendingCalls.values().stream().filter(owner::equals).count();
        return count == 0 ? value : value.pending(count);
    }
    GuideUsageSnapshot sessionSnapshot() {
        return pendingCalls.isEmpty() ? session : session.pending(pendingCalls.size());
    }
    GuideUsageSnapshot inheritedSnapshot() { return inherited; }

    private static BuiltinModelCatalog.Pricing pricing(String model) {
        var bundled = BuiltinModelCatalog.bundled();
        return model == null || bundled.catalog() == null ? null : bundled.catalog().match(model)
                .map(match -> match.entry().pricing()).orElse(null);
    }

    static GuideUsageSnapshot project(ModelUsage usage, BuiltinModelCatalog.Pricing pricing) {
        ModelUsage report = usage == null ? ModelUsage.empty() : usage;
        Quote quote = quote(report, pricing);
        return new GuideUsageSnapshot(report.inputTokens(), report.outputTokens(),
                report.cacheReadTokens(), report.cacheWriteTokens(), 1, report.reported() ? 1 : 0,
                !report.complete(), !report.inputKnown() || !report.cacheReadKnown(),
                quote.amount(), quote.incomplete());
    }

    /** Full published-price estimate, or null when any required usage/rate is missing. */
    static BigDecimal estimate(ModelUsage usage, BuiltinModelCatalog.Pricing pricing) {
        Quote quote = quote(usage == null ? ModelUsage.empty() : usage, pricing);
        return quote.incomplete() ? null : quote.amount();
    }

    private static Quote quote(ModelUsage usage, BuiltinModelCatalog.Pricing pricing) {
        if (pricing == null || !"USD".equals(pricing.currency()) || !"million_tokens".equals(pricing.unit())) {
            return new Quote(null, true);
        }
        // Without complete canonical input, a threshold tier cannot safely be selected.
        BuiltinModelCatalog.Tier tier = null;
        if (usage.inputKnown()) {
            for (var candidate : pricing.tiers()) {
                if (usage.inputTokens() >= candidate.minInputTokens()) tier = candidate;
            }
        } else if (pricing.tiers().size() == 1 && pricing.tiers().getFirst().minInputTokens() == 0) {
            tier = pricing.tiers().getFirst();
        }
        if (tier == null) return new Quote(null, true);
        Component[] parts = {
                new Component(usage.uncachedInputTokens(), usage.uncachedInputKnown(), tier.input()),
                new Component(usage.outputTokens(), usage.outputKnown(), tier.output()),
                new Component(usage.cacheReadTokens(), usage.cacheReadKnown(), tier.cacheRead()),
                new Component(usage.cacheWriteTokens(), usage.cacheWriteKnown(), tier.cacheWrite())
        };
        BigDecimal amount = BigDecimal.ZERO;
        boolean known = false;
        boolean incomplete = !usage.complete();
        for (Component part : parts) {
            if (!part.known()) {
                incomplete = true;
            } else if (part.tokens() == 0) {
                known |= usage.inputKnown() || usage.outputKnown() || usage.uncachedInputKnown();
                // A protocol-implied zero cache category alone is not a reported zero-cost call.
            } else if (part.rate() == null) {
                incomplete = true;
            } else {
                known = true;
                amount = amount.add(part.rate().multiply(BigDecimal.valueOf(part.tokens())));
            }
        }
        return new Quote(known && (!incomplete || amount.signum() > 0) ? amount.movePointLeft(6) : null, incomplete);
    }

    private record Component(long tokens, boolean known, BigDecimal rate) {}
    private record Quote(BigDecimal amount, boolean incomplete) {}
}
