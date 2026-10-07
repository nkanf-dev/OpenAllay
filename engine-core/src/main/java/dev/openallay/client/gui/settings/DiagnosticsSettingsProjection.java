package dev.openallay.client.gui.settings;

import dev.openallay.settings.diagnostics.SettingsDiagnosticCard;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Diagnostics page with friendly cards and a distinctly typed Debug Mode section. */
@dev.openallay.value.ValueType(DiagnosticsSettingsProjection.ValueSchemaProvider.class)
public final class DiagnosticsSettingsProjection {
    private final String titleKey;
    private final String narrationKey;
    private final List<CardRow> cards;
    private final Optional<DebugSection> debug;
    public DiagnosticsSettingsProjection(String titleKey, String narrationKey, List<CardRow> cards, Optional<DebugSection> debug) {

        cards = List.copyOf(cards);
        debug = Objects.requireNonNull(debug, "debug");

        this.titleKey = titleKey;
        this.narrationKey = narrationKey;
        this.cards = cards;
        this.debug = debug;
    }
    public String titleKey() { return titleKey; }
    public String narrationKey() { return narrationKey; }
    public List<CardRow> cards() { return cards; }
    public Optional<DebugSection> debug() { return debug; }
public static DiagnosticsSettingsProjection from(SettingsDiagnosticsSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<CardRow> cards = snapshot.cards().stream()
                .map(card -> new CardRow(
                        card.domain(),
                        card.titleKey(),
                        card.statusKey(),
                        card.statusKey(),
                        icon(card.friendlyStatus()),
                        card.noteKeys(),
                        card.metrics()))
                .toList();
        return new DiagnosticsSettingsProjection(
                "screen.openallay.settings.diagnostics.title",
                "screen.openallay.settings.diagnostics.narration",
                cards,
                snapshot.debug().map(debug -> new DebugSection(
                        "screen.openallay.settings.diagnostics.debug.title",
                        "screen.openallay.settings.diagnostics.debug.narration",
                        debug)));
    }
private static String icon(SettingsDiagnosticCard.FriendlyStatus status) {
        return switch (status) {
            case READY -> "✓";
            case WORKING -> "…";
            case ATTENTION -> "!";
            case UNAVAILABLE -> "×";
            case NOT_CONNECTED -> "○";
        };
    }
@dev.openallay.value.ValueType(CardRow.ValueSchemaProvider.class)
public static final class CardRow {
    private final SettingsDiagnosticCard.Domain domain;
    private final String titleKey;
    private final String statusKey;
    private final String statusTextKey;
    private final String statusIcon;
    private final List<String> noteKeys;
    private final List<SettingsDiagnosticCard.Metric> metrics;
    public CardRow(SettingsDiagnosticCard.Domain domain, String titleKey, String statusKey, String statusTextKey, String statusIcon, List<String> noteKeys, List<SettingsDiagnosticCard.Metric> metrics) {

            Objects.requireNonNull(domain, "domain");
            requireKey(titleKey, "titleKey");
            requireKey(statusKey, "statusKey");
            requireKey(statusTextKey, "statusTextKey");
            if (statusIcon == null || statusIcon.isBlank()) {
                throw new IllegalArgumentException("statusIcon is required");
            }
            noteKeys = List.copyOf(noteKeys);
            metrics = List.copyOf(metrics);

        this.domain = domain;
        this.titleKey = titleKey;
        this.statusKey = statusKey;
        this.statusTextKey = statusTextKey;
        this.statusIcon = statusIcon;
        this.noteKeys = noteKeys;
        this.metrics = metrics;
    }
    public SettingsDiagnosticCard.Domain domain() { return domain; }
    public String titleKey() { return titleKey; }
    public String statusKey() { return statusKey; }
    public String statusTextKey() { return statusTextKey; }
    public String statusIcon() { return statusIcon; }
    public List<String> noteKeys() { return noteKeys; }
    public List<SettingsDiagnosticCard.Metric> metrics() { return metrics; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CardRow)) return false;
        CardRow that = (CardRow) other;
        return java.util.Objects.equals(domain, that.domain) && java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(statusKey, that.statusKey) && java.util.Objects.equals(statusTextKey, that.statusTextKey) && java.util.Objects.equals(statusIcon, that.statusIcon) && java.util.Objects.equals(noteKeys, that.noteKeys) && java.util.Objects.equals(metrics, that.metrics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(domain);
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(statusKey);
        hash = 31 * hash + java.util.Objects.hashCode(statusTextKey);
        hash = 31 * hash + java.util.Objects.hashCode(statusIcon);
        hash = 31 * hash + java.util.Objects.hashCode(noteKeys);
        hash = 31 * hash + java.util.Objects.hashCode(metrics);
        return hash;
    }
    @Override public String toString() { return "CardRow[domain=" + domain + ", titleKey=" + titleKey + ", statusKey=" + statusKey + ", statusTextKey=" + statusTextKey + ", statusIcon=" + statusIcon + ", noteKeys=" + noteKeys + ", metrics=" + metrics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CardRow> schema() {
            return new dev.openallay.value.ValueSchema<>(CardRow.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CardRow>>asList(new dev.openallay.value.ValueSchema.Component<>(CardRow.class, "domain", CardRow::domain), new dev.openallay.value.ValueSchema.Component<>(CardRow.class, "titleKey", CardRow::titleKey), new dev.openallay.value.ValueSchema.Component<>(CardRow.class, "statusKey", CardRow::statusKey), new dev.openallay.value.ValueSchema.Component<>(CardRow.class, "statusTextKey", CardRow::statusTextKey), new dev.openallay.value.ValueSchema.Component<>(CardRow.class, "statusIcon", CardRow::statusIcon), new dev.openallay.value.ValueSchema.Component<>(CardRow.class, "noteKeys", CardRow::noteKeys), new dev.openallay.value.ValueSchema.Component<>(CardRow.class, "metrics", CardRow::metrics)), arguments -> new CardRow((SettingsDiagnosticCard.Domain) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (List) arguments[5], (List) arguments[6]));
        }
    }
}
@dev.openallay.value.ValueType(DebugSection.ValueSchemaProvider.class)
public static final class DebugSection {
    private final String titleKey;
    private final String narrationKey;
    private final SettingsDiagnosticsSnapshot.DebugSettingsDiagnostics diagnostics;
    public DebugSection(String titleKey, String narrationKey, SettingsDiagnosticsSnapshot.DebugSettingsDiagnostics diagnostics) {

            requireKey(titleKey, "titleKey");
            requireKey(narrationKey, "narrationKey");
            Objects.requireNonNull(diagnostics, "diagnostics");

        this.titleKey = titleKey;
        this.narrationKey = narrationKey;
        this.diagnostics = diagnostics;
    }
    public String titleKey() { return titleKey; }
    public String narrationKey() { return narrationKey; }
    public SettingsDiagnosticsSnapshot.DebugSettingsDiagnostics diagnostics() { return diagnostics; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DebugSection)) return false;
        DebugSection that = (DebugSection) other;
        return java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(narrationKey, that.narrationKey) && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(narrationKey);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "DebugSection[titleKey=" + titleKey + ", narrationKey=" + narrationKey + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DebugSection> schema() {
            return new dev.openallay.value.ValueSchema<>(DebugSection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DebugSection>>asList(new dev.openallay.value.ValueSchema.Component<>(DebugSection.class, "titleKey", DebugSection::titleKey), new dev.openallay.value.ValueSchema.Component<>(DebugSection.class, "narrationKey", DebugSection::narrationKey), new dev.openallay.value.ValueSchema.Component<>(DebugSection.class, "diagnostics", DebugSection::diagnostics)), arguments -> new DebugSection((String) arguments[0], (String) arguments[1], (SettingsDiagnosticsSnapshot.DebugSettingsDiagnostics) arguments[2]));
        }
    }
}
private static void requireKey(String value, String name) {
        if (value == null || !value.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException(name + " must be a localization key");
        }
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiagnosticsSettingsProjection)) return false;
        DiagnosticsSettingsProjection that = (DiagnosticsSettingsProjection) other;
        return java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(narrationKey, that.narrationKey) && java.util.Objects.equals(cards, that.cards) && java.util.Objects.equals(debug, that.debug);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(narrationKey);
        hash = 31 * hash + java.util.Objects.hashCode(cards);
        hash = 31 * hash + java.util.Objects.hashCode(debug);
        return hash;
    }
    @Override public String toString() { return "DiagnosticsSettingsProjection[titleKey=" + titleKey + ", narrationKey=" + narrationKey + ", cards=" + cards + ", debug=" + debug + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DiagnosticsSettingsProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(DiagnosticsSettingsProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DiagnosticsSettingsProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(DiagnosticsSettingsProjection.class, "titleKey", DiagnosticsSettingsProjection::titleKey), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsSettingsProjection.class, "narrationKey", DiagnosticsSettingsProjection::narrationKey), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsSettingsProjection.class, "cards", DiagnosticsSettingsProjection::cards), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsSettingsProjection.class, "debug", DiagnosticsSettingsProjection::debug)), arguments -> new DiagnosticsSettingsProjection((String) arguments[0], (String) arguments[1], (List) arguments[2], (Optional) arguments[3]));
        }
    }
}
