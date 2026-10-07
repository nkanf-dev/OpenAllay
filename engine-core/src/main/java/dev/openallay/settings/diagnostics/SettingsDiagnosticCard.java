package dev.openallay.settings.diagnostics;

import java.util.List;
import java.util.Objects;

/** Friendly diagnostics shape; technical identities are intentionally unrepresentable. */
@dev.openallay.value.ValueType(SettingsDiagnosticCard.ValueSchemaProvider.class)
public final class SettingsDiagnosticCard {
    private final Domain domain;
    private final FriendlyStatus friendlyStatus;
    private final String titleKey;
    private final String statusKey;
    private final List<String> noteKeys;
    private final List<Metric> metrics;
    public SettingsDiagnosticCard(Domain domain, FriendlyStatus friendlyStatus, String titleKey, String statusKey, List<String> noteKeys, List<Metric> metrics) {

        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(friendlyStatus, "friendlyStatus");
        titleKey = key(titleKey, "titleKey");
        statusKey = key(statusKey, "statusKey");
        noteKeys = dev.openallay.util.Java8Collections.toList(noteKeys.stream().map(value -> key(value, "noteKey")));
        metrics = dev.openallay.util.Java8Collections.listCopyOf(metrics);

        this.domain = domain;
        this.friendlyStatus = friendlyStatus;
        this.titleKey = titleKey;
        this.statusKey = statusKey;
        this.noteKeys = noteKeys;
        this.metrics = metrics;
    }
    public Domain domain() { return domain; }
    public FriendlyStatus friendlyStatus() { return friendlyStatus; }
    public String titleKey() { return titleKey; }
    public String statusKey() { return statusKey; }
    public List<String> noteKeys() { return noteKeys; }
    public List<Metric> metrics() { return metrics; }
public enum Domain {
        MODELS,
        KNOWLEDGE,
        HISTORY,
        CONTEXT
    }
public enum FriendlyStatus {
        READY,
        WORKING,
        ATTENTION,
        UNAVAILABLE,
        NOT_CONNECTED
    }
@dev.openallay.value.ValueType(Metric.ValueSchemaProvider.class)
public static final class Metric {
    private final String labelKey;
    private final Long value;
    public Metric(String labelKey, Long value) {

            labelKey = key(labelKey, "labelKey");
            if (value != null && value < 0) {
                throw new IllegalArgumentException("diagnostic metric must not be negative");
            }

        this.labelKey = labelKey;
        this.value = value;
    }
    public String labelKey() { return labelKey; }
    public Long value() { return value; }
public Metric(String labelKey, long value) {
            this(labelKey, Long.valueOf(value));
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Metric)) return false;
        Metric that = (Metric) other;
        return java.util.Objects.equals(labelKey, that.labelKey) && java.util.Objects.equals(value, that.value);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(labelKey);
        hash = 31 * hash + java.util.Objects.hashCode(value);
        return hash;
    }
    @Override public String toString() { return "Metric[labelKey=" + labelKey + ", value=" + value + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Metric> schema() {
            return new dev.openallay.value.ValueSchema<>(Metric.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Metric>>asList(new dev.openallay.value.ValueSchema.Component<>(Metric.class, "labelKey", Metric::labelKey), new dev.openallay.value.ValueSchema.Component<>(Metric.class, "value", Metric::value)), arguments -> new Metric((String) arguments[0], (Long) arguments[1]));
        }
    }
}
private static String key(String value, String name) {
        if (value == null || !value.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException(name + " must be a localization key");
        }
        return value;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SettingsDiagnosticCard)) return false;
        SettingsDiagnosticCard that = (SettingsDiagnosticCard) other;
        return java.util.Objects.equals(domain, that.domain) && java.util.Objects.equals(friendlyStatus, that.friendlyStatus) && java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(statusKey, that.statusKey) && java.util.Objects.equals(noteKeys, that.noteKeys) && java.util.Objects.equals(metrics, that.metrics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(domain);
        hash = 31 * hash + java.util.Objects.hashCode(friendlyStatus);
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(statusKey);
        hash = 31 * hash + java.util.Objects.hashCode(noteKeys);
        hash = 31 * hash + java.util.Objects.hashCode(metrics);
        return hash;
    }
    @Override public String toString() { return "SettingsDiagnosticCard[domain=" + domain + ", friendlyStatus=" + friendlyStatus + ", titleKey=" + titleKey + ", statusKey=" + statusKey + ", noteKeys=" + noteKeys + ", metrics=" + metrics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SettingsDiagnosticCard> schema() {
            return new dev.openallay.value.ValueSchema<>(SettingsDiagnosticCard.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SettingsDiagnosticCard>>asList(new dev.openallay.value.ValueSchema.Component<>(SettingsDiagnosticCard.class, "domain", SettingsDiagnosticCard::domain), new dev.openallay.value.ValueSchema.Component<>(SettingsDiagnosticCard.class, "friendlyStatus", SettingsDiagnosticCard::friendlyStatus), new dev.openallay.value.ValueSchema.Component<>(SettingsDiagnosticCard.class, "titleKey", SettingsDiagnosticCard::titleKey), new dev.openallay.value.ValueSchema.Component<>(SettingsDiagnosticCard.class, "statusKey", SettingsDiagnosticCard::statusKey), new dev.openallay.value.ValueSchema.Component<>(SettingsDiagnosticCard.class, "noteKeys", SettingsDiagnosticCard::noteKeys), new dev.openallay.value.ValueSchema.Component<>(SettingsDiagnosticCard.class, "metrics", SettingsDiagnosticCard::metrics)), arguments -> new SettingsDiagnosticCard((Domain) arguments[0], (FriendlyStatus) arguments[1], (String) arguments[2], (String) arguments[3], (List) arguments[4], (List) arguments[5]));
        }
    }
}
