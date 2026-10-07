package dev.openallay.client.gui.settings;

import dev.openallay.capability.CapabilityChildPage;
import dev.openallay.capability.CapabilityKind;
import dev.openallay.capability.CapabilityPolicy;
import dev.openallay.capability.CapabilitySettingsEntry;
import dev.openallay.settings.capability.CapabilitySettingsView;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Pure friendly-card projection and deny-policy draft edits for the native screen. */
@dev.openallay.value.ValueType(CapabilitySettingsProjection.ValueSchemaProvider.class)
public final class CapabilitySettingsProjection {
    private final List<Card> cards;
    private final CapabilityPolicy policy;
    private final int retainedUnknownCount;
    public CapabilitySettingsProjection(List<Card> cards, CapabilityPolicy policy, int retainedUnknownCount) {

        cards = List.copyOf(cards);
        Objects.requireNonNull(policy, "policy");
        if (retainedUnknownCount < 0) {
            throw new IllegalArgumentException("retainedUnknownCount must not be negative");
        }

        this.cards = cards;
        this.policy = policy;
        this.retainedUnknownCount = retainedUnknownCount;
    }
    public List<Card> cards() { return cards; }
    public CapabilityPolicy policy() { return policy; }
    public int retainedUnknownCount() { return retainedUnknownCount; }
public static CapabilitySettingsProjection from(
            CapabilitySettingsView view, CapabilityPolicy draft, boolean debugMode) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(draft, "draft");
        List<Card> cards = new ArrayList<>();
        for (CapabilitySettingsEntry entry : view.catalog().entries()) {
            boolean enabled = switch (entry.kind()) {
                case TOOL -> !draft.disabledTools().contains(entry.id());
                case SKILL -> !draft.disabledSkills().contains(entry.id());
                case KNOWLEDGE_SOURCE -> entry.enabled();
            };
            boolean toggleable = entry.childPage() == null
                    && (entry.kind() == CapabilityKind.TOOL
                            || entry.kind() == CapabilityKind.SKILL);
            cards.add(new Card(
                    entry.id(),
                    entry.kind(),
                    entry.titleKey(),
                    entry.descriptionKey(),
                    statusKey(entry.available(), enabled),
                    entry.available(),
                    enabled,
                    toggleable,
                    entry.childPage(),
                    debugMode ? entry.id() : null));
        }
        return new CapabilitySettingsProjection(
                cards,
                draft,
                view.unknownDisabledTools().size() + view.unknownDisabledSkills().size());
    }
public ToolResult<CapabilityPolicy> toggle(String actionId) {
        Card card = cards.stream()
                .filter(value -> value.actionId().equals(actionId))
                .findFirst()
                .orElse(null);
        if (card == null || !card.toggleable()) {
            return new ToolResult.Failure<>(
                    "capability_not_toggleable", "This capability cannot be toggled here");
        }
        Set<String> tools = new TreeSet<>(policy.disabledTools());
        Set<String> skills = new TreeSet<>(policy.disabledSkills());
        Set<String> target = card.kind() == CapabilityKind.TOOL ? tools : skills;
        if (!target.remove(card.actionId())) {
            target.add(card.actionId());
        }
        return new ToolResult.Success<>(new CapabilityPolicy(
                tools, skills));
    }
public List<Card> cards(CapabilityKind kind) {
        Objects.requireNonNull(kind, "kind");
        return cards.stream().filter(card -> card.kind() == kind).toList();
    }
public CapabilityChildPage route(String actionId) {
        return cards.stream()
                .filter(value -> value.actionId().equals(actionId))
                .map(Card::childPage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
private static String statusKey(boolean available, boolean enabled) {
        if (!available) {
            return "screen.openallay.settings.capability.unavailable";
        }
        return enabled
                ? "screen.openallay.settings.capability.enabled"
                : "screen.openallay.settings.capability.disabled";
    }
@dev.openallay.value.ValueType(Card.ValueSchemaProvider.class)
public static final class Card {
    private final String actionId;
    private final CapabilityKind kind;
    private final String titleKey;
    private final String descriptionKey;
    private final String statusKey;
    private final boolean available;
    private final boolean enabled;
    private final boolean toggleable;
    private final CapabilityChildPage childPage;
    private final String debugId;
    public Card(String actionId, CapabilityKind kind, String titleKey, String descriptionKey, String statusKey, boolean available, boolean enabled, boolean toggleable, CapabilityChildPage childPage, String debugId) {
        this.actionId = actionId;
        this.kind = kind;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
        this.statusKey = statusKey;
        this.available = available;
        this.enabled = enabled;
        this.toggleable = toggleable;
        this.childPage = childPage;
        this.debugId = debugId;
    }
    public String actionId() { return actionId; }
    public CapabilityKind kind() { return kind; }
    public String titleKey() { return titleKey; }
    public String descriptionKey() { return descriptionKey; }
    public String statusKey() { return statusKey; }
    public boolean available() { return available; }
    public boolean enabled() { return enabled; }
    public boolean toggleable() { return toggleable; }
    public CapabilityChildPage childPage() { return childPage; }
    public String debugId() { return debugId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Card)) return false;
        Card that = (Card) other;
        return java.util.Objects.equals(actionId, that.actionId) && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(descriptionKey, that.descriptionKey) && java.util.Objects.equals(statusKey, that.statusKey) && available == that.available && enabled == that.enabled && toggleable == that.toggleable && java.util.Objects.equals(childPage, that.childPage) && java.util.Objects.equals(debugId, that.debugId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(actionId);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(descriptionKey);
        hash = 31 * hash + java.util.Objects.hashCode(statusKey);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + Boolean.hashCode(toggleable);
        hash = 31 * hash + java.util.Objects.hashCode(childPage);
        hash = 31 * hash + java.util.Objects.hashCode(debugId);
        return hash;
    }
    @Override public String toString() { return "Card[actionId=" + actionId + ", kind=" + kind + ", titleKey=" + titleKey + ", descriptionKey=" + descriptionKey + ", statusKey=" + statusKey + ", available=" + available + ", enabled=" + enabled + ", toggleable=" + toggleable + ", childPage=" + childPage + ", debugId=" + debugId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Card> schema() {
            return new dev.openallay.value.ValueSchema<>(Card.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Card>>asList(new dev.openallay.value.ValueSchema.Component<>(Card.class, "actionId", Card::actionId), new dev.openallay.value.ValueSchema.Component<>(Card.class, "kind", Card::kind), new dev.openallay.value.ValueSchema.Component<>(Card.class, "titleKey", Card::titleKey), new dev.openallay.value.ValueSchema.Component<>(Card.class, "descriptionKey", Card::descriptionKey), new dev.openallay.value.ValueSchema.Component<>(Card.class, "statusKey", Card::statusKey), new dev.openallay.value.ValueSchema.Component<>(Card.class, "available", Card::available), new dev.openallay.value.ValueSchema.Component<>(Card.class, "enabled", Card::enabled), new dev.openallay.value.ValueSchema.Component<>(Card.class, "toggleable", Card::toggleable), new dev.openallay.value.ValueSchema.Component<>(Card.class, "childPage", Card::childPage), new dev.openallay.value.ValueSchema.Component<>(Card.class, "debugId", Card::debugId)), arguments -> new Card((String) arguments[0], (CapabilityKind) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6], (Boolean) arguments[7], (CapabilityChildPage) arguments[8], (String) arguments[9]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CapabilitySettingsProjection)) return false;
        CapabilitySettingsProjection that = (CapabilitySettingsProjection) other;
        return java.util.Objects.equals(cards, that.cards) && java.util.Objects.equals(policy, that.policy) && retainedUnknownCount == that.retainedUnknownCount;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(cards);
        hash = 31 * hash + java.util.Objects.hashCode(policy);
        hash = 31 * hash + Integer.hashCode(retainedUnknownCount);
        return hash;
    }
    @Override public String toString() { return "CapabilitySettingsProjection[cards=" + cards + ", policy=" + policy + ", retainedUnknownCount=" + retainedUnknownCount + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CapabilitySettingsProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(CapabilitySettingsProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CapabilitySettingsProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsProjection.class, "cards", CapabilitySettingsProjection::cards), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsProjection.class, "policy", CapabilitySettingsProjection::policy), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsProjection.class, "retainedUnknownCount", CapabilitySettingsProjection::retainedUnknownCount)), arguments -> new CapabilitySettingsProjection((List) arguments[0], (CapabilityPolicy) arguments[1], (Integer) arguments[2]));
        }
    }
}
