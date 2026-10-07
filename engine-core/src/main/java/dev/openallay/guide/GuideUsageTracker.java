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
        {
final java.lang.Object $oaPattern0_value = event;
final boolean $oaPattern0_match = $oaPattern0_value instanceof AgentEvent.ModelUsageStarted;
AgentEvent.ModelUsageStarted $oaPattern0_bound = $oaPattern0_match ? (AgentEvent.ModelUsageStarted) $oaPattern0_value : null;
if ($oaPattern0_match) {
            if (calls.contains($oaPattern0_bound.callId())) return false;
            return pendingCalls.putIfAbsent($oaPattern0_bound.callId(), owner) == null;
        }
}
        final java.lang.Object $oaPattern1_value = event;
final boolean $oaPattern1_match = $oaPattern1_value instanceof AgentEvent.ModelUsageObserved;
AgentEvent.ModelUsageObserved $oaPattern1_bound = $oaPattern1_match ? (AgentEvent.ModelUsageObserved) $oaPattern1_value : null;
if (!($oaPattern1_match)) return false;
        UUID startedOwner = pendingCalls.get($oaPattern1_bound.callId());
        if (startedOwner != null && !startedOwner.equals(owner) || !calls.add($oaPattern1_bound.callId())) return false;
        String model = $oaPattern1_bound.modelIdentifier() == null || $oaPattern1_bound.modelIdentifier().isBlank()
                ? requestModels.get(owner) : $oaPattern1_bound.modelIdentifier();
        GuideUsageSnapshot delta = project($oaPattern1_bound.usage(), pricing(model));
        requests.merge(owner, delta, GuideUsageSnapshot::plus);
        pendingCalls.remove($oaPattern1_bound.callId());
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
        dev.openallay.model.metadata.BuiltinModelCatalog.Load bundled = BuiltinModelCatalog.bundled();
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
            for (dev.openallay.model.metadata.BuiltinModelCatalog.Tier candidate : pricing.tiers()) {
                if (usage.inputTokens() >= candidate.minInputTokens()) tier = candidate;
            }
        } else if (pricing.tiers().size() == 1 && pricing.tiers().get(0).minInputTokens() == 0) {
            tier = pricing.tiers().get(0);
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

    @dev.openallay.value.ValueType(Component.ValueSchemaProvider.class)
private static final class Component {
    private final long tokens;
    private final boolean known;
    private final BigDecimal rate;
    private Component(long tokens, boolean known, BigDecimal rate) {
        this.tokens = tokens;
        this.known = known;
        this.rate = rate;
    }
    public long tokens() { return tokens; }
    public boolean known() { return known; }
    public BigDecimal rate() { return rate; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Component)) return false;
        Component that = (Component) other;
        return tokens == that.tokens && known == that.known && java.util.Objects.equals(rate, that.rate);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(tokens);
        hash = 31 * hash + Boolean.hashCode(known);
        hash = 31 * hash + java.util.Objects.hashCode(rate);
        return hash;
    }
    @Override public String toString() { return "Component[tokens=" + tokens + ", known=" + known + ", rate=" + rate + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Component> schema() {
            return new dev.openallay.value.ValueSchema<>(Component.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Component>>asList(new dev.openallay.value.ValueSchema.Component<>(Component.class, "tokens", Component::tokens), new dev.openallay.value.ValueSchema.Component<>(Component.class, "known", Component::known), new dev.openallay.value.ValueSchema.Component<>(Component.class, "rate", Component::rate)), arguments -> new Component((Long) arguments[0], (Boolean) arguments[1], (BigDecimal) arguments[2]));
        }
    }
}
    @dev.openallay.value.ValueType(Quote.ValueSchemaProvider.class)
private static final class Quote {
    private final BigDecimal amount;
    private final boolean incomplete;
    private Quote(BigDecimal amount, boolean incomplete) {
        this.amount = amount;
        this.incomplete = incomplete;
    }
    public BigDecimal amount() { return amount; }
    public boolean incomplete() { return incomplete; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Quote)) return false;
        Quote that = (Quote) other;
        return java.util.Objects.equals(amount, that.amount) && incomplete == that.incomplete;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(amount);
        hash = 31 * hash + Boolean.hashCode(incomplete);
        return hash;
    }
    @Override public String toString() { return "Quote[amount=" + amount + ", incomplete=" + incomplete + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Quote> schema() {
            return new dev.openallay.value.ValueSchema<>(Quote.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Quote>>asList(new dev.openallay.value.ValueSchema.Component<>(Quote.class, "amount", Quote::amount), new dev.openallay.value.ValueSchema.Component<>(Quote.class, "incomplete", Quote::incomplete)), arguments -> new Quote((BigDecimal) arguments[0], (Boolean) arguments[1]));
        }
    }
}
}
