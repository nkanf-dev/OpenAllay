package dev.openallay.client.gui.settings;

import dev.openallay.settings.SettingsOperation;
import dev.openallay.settings.history.HistorySettingsView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Friendly actor-scoped history page; destructive intents carry no raw identity. */
@dev.openallay.value.ValueType(HistorySettingsProjection.ValueSchemaProvider.class)
public final class HistorySettingsProjection {
    private final String titleKey;
    private final String scopeLabelKey;
    private final String statusKey;
    private final String narrationKey;
    private final List<ActionRow> actions;
    public HistorySettingsProjection(String titleKey, String scopeLabelKey, String statusKey, String narrationKey, List<ActionRow> actions) {

        actions = List.copyOf(actions);

        this.titleKey = titleKey;
        this.scopeLabelKey = scopeLabelKey;
        this.statusKey = statusKey;
        this.narrationKey = narrationKey;
        this.actions = actions;
    }
    public String titleKey() { return titleKey; }
    public String scopeLabelKey() { return scopeLabelKey; }
    public String statusKey() { return statusKey; }
    public String narrationKey() { return narrationKey; }
    public List<ActionRow> actions() { return actions; }
public enum Action {
        DELETE_CURRENT,
        DELETE_ACTOR,
        RESET_DATABASE
    }
public static HistorySettingsProjection from(
            HistorySettingsView history,
            boolean debugMode,
            SettingsOperation operation) {
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(operation, "operation");
        boolean idle = operation.kind() == SettingsOperation.Kind.IDLE;
        List<ActionRow> actions = new ArrayList<>();
        actions.add(row(
                Action.DELETE_CURRENT,
                idle && history.currentDeleteAvailable(),
                false));
        actions.add(row(
                Action.DELETE_ACTOR,
                idle && history.actorDeleteAvailable(),
                false));
        if (debugMode) {
            actions.add(row(
                    Action.RESET_DATABASE,
                    idle && history.databaseResetAvailable(),
                    true));
        }
        return new HistorySettingsProjection(
                "screen.openallay.settings.history.title",
                switch (history.connectionKind()) {
                    case NONE -> "screen.openallay.settings.history.scope.none";
                    case SINGLEPLAYER_WORLD -> "screen.openallay.settings.history.scope.world";
                    case MULTIPLAYER_SERVER -> "screen.openallay.settings.history.scope.server";
                },
                "screen.openallay.settings.history.status."
                        + history.health().name().toLowerCase(Locale.ROOT),
                "screen.openallay.settings.history.narration",
                actions);
    }
private static ActionRow row(Action action, boolean enabled, boolean second) {
        String suffix = switch (action) {
            case DELETE_CURRENT -> "delete_current";
            case DELETE_ACTOR -> "delete_actor";
            case RESET_DATABASE -> "reset_database";
        };
        return new ActionRow(
                action,
                "screen.openallay.settings.history.action." + suffix,
                "screen.openallay.settings.history.action." + suffix + ".description",
                enabled,
                second);
    }
@dev.openallay.value.ValueType(ActionRow.ValueSchemaProvider.class)
public static final class ActionRow {
    private final Action action;
    private final String labelKey;
    private final String descriptionKey;
    private final boolean enabled;
    private final boolean requiresSecondConfirmation;
    public ActionRow(Action action, String labelKey, String descriptionKey, boolean enabled, boolean requiresSecondConfirmation) {

            Objects.requireNonNull(action, "action");
            if (labelKey == null || labelKey.isBlank()
                    || descriptionKey == null || descriptionKey.isBlank()) {
                throw new IllegalArgumentException("history action localization is required");
            }

        this.action = action;
        this.labelKey = labelKey;
        this.descriptionKey = descriptionKey;
        this.enabled = enabled;
        this.requiresSecondConfirmation = requiresSecondConfirmation;
    }
    public Action action() { return action; }
    public String labelKey() { return labelKey; }
    public String descriptionKey() { return descriptionKey; }
    public boolean enabled() { return enabled; }
    public boolean requiresSecondConfirmation() { return requiresSecondConfirmation; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ActionRow)) return false;
        ActionRow that = (ActionRow) other;
        return java.util.Objects.equals(action, that.action) && java.util.Objects.equals(labelKey, that.labelKey) && java.util.Objects.equals(descriptionKey, that.descriptionKey) && enabled == that.enabled && requiresSecondConfirmation == that.requiresSecondConfirmation;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(action);
        hash = 31 * hash + java.util.Objects.hashCode(labelKey);
        hash = 31 * hash + java.util.Objects.hashCode(descriptionKey);
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + Boolean.hashCode(requiresSecondConfirmation);
        return hash;
    }
    @Override public String toString() { return "ActionRow[action=" + action + ", labelKey=" + labelKey + ", descriptionKey=" + descriptionKey + ", enabled=" + enabled + ", requiresSecondConfirmation=" + requiresSecondConfirmation + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ActionRow> schema() {
            return new dev.openallay.value.ValueSchema<>(ActionRow.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ActionRow>>asList(new dev.openallay.value.ValueSchema.Component<>(ActionRow.class, "action", ActionRow::action), new dev.openallay.value.ValueSchema.Component<>(ActionRow.class, "labelKey", ActionRow::labelKey), new dev.openallay.value.ValueSchema.Component<>(ActionRow.class, "descriptionKey", ActionRow::descriptionKey), new dev.openallay.value.ValueSchema.Component<>(ActionRow.class, "enabled", ActionRow::enabled), new dev.openallay.value.ValueSchema.Component<>(ActionRow.class, "requiresSecondConfirmation", ActionRow::requiresSecondConfirmation)), arguments -> new ActionRow((Action) arguments[0], (String) arguments[1], (String) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HistorySettingsProjection)) return false;
        HistorySettingsProjection that = (HistorySettingsProjection) other;
        return java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(scopeLabelKey, that.scopeLabelKey) && java.util.Objects.equals(statusKey, that.statusKey) && java.util.Objects.equals(narrationKey, that.narrationKey) && java.util.Objects.equals(actions, that.actions);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(scopeLabelKey);
        hash = 31 * hash + java.util.Objects.hashCode(statusKey);
        hash = 31 * hash + java.util.Objects.hashCode(narrationKey);
        hash = 31 * hash + java.util.Objects.hashCode(actions);
        return hash;
    }
    @Override public String toString() { return "HistorySettingsProjection[titleKey=" + titleKey + ", scopeLabelKey=" + scopeLabelKey + ", statusKey=" + statusKey + ", narrationKey=" + narrationKey + ", actions=" + actions + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<HistorySettingsProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(HistorySettingsProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<HistorySettingsProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(HistorySettingsProjection.class, "titleKey", HistorySettingsProjection::titleKey), new dev.openallay.value.ValueSchema.Component<>(HistorySettingsProjection.class, "scopeLabelKey", HistorySettingsProjection::scopeLabelKey), new dev.openallay.value.ValueSchema.Component<>(HistorySettingsProjection.class, "statusKey", HistorySettingsProjection::statusKey), new dev.openallay.value.ValueSchema.Component<>(HistorySettingsProjection.class, "narrationKey", HistorySettingsProjection::narrationKey), new dev.openallay.value.ValueSchema.Component<>(HistorySettingsProjection.class, "actions", HistorySettingsProjection::actions)), arguments -> new HistorySettingsProjection((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (List) arguments[4]));
        }
    }
}
