package dev.openallay.client.gui.settings;

import dev.openallay.recipe.RecipeVisibilityPolicy;
import dev.openallay.recipe.config.RecipeClientConfig;
import dev.openallay.settings.capability.RecipeSettingsView;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Pure recipe Tool child-page projection and current-schema draft edits. */
@dev.openallay.value.ValueType(RecipeSettingsProjection.ValueSchemaProvider.class)
public final class RecipeSettingsProjection {
    private final List<SourceRow> sources;
    private final RecipeClientConfig config;
    private final boolean preferredViewerAvailable;
    private final int retainedUnknownCount;
    public RecipeSettingsProjection(List<SourceRow> sources, RecipeClientConfig config, boolean preferredViewerAvailable, int retainedUnknownCount) {

        sources = List.copyOf(sources);
        Objects.requireNonNull(config, "config");
        if (retainedUnknownCount < 0) {
            throw new IllegalArgumentException("retainedUnknownCount must not be negative");
        }

        this.sources = sources;
        this.config = config;
        this.preferredViewerAvailable = preferredViewerAvailable;
        this.retainedUnknownCount = retainedUnknownCount;
    }
    public List<SourceRow> sources() { return sources; }
    public RecipeClientConfig config() { return config; }
    public boolean preferredViewerAvailable() { return preferredViewerAvailable; }
    public int retainedUnknownCount() { return retainedUnknownCount; }
public static RecipeSettingsProjection from(
            RecipeSettingsView view, RecipeClientConfig draft, boolean debugMode) {
        List<SourceRow> rows = view.sources().stream()
                .map(source -> new SourceRow(
                        source.id(),
                        sourceTitleKey(source.id()),
                        source.available(),
                        !draft.disabledSources().contains(source.id()),
                        source.viewer(),
                        source.exactNavigation(),
                        debugMode ? source.id() : null))
                .toList();
        boolean preferredAvailable = RecipeClientConfig.AUTO.equals(draft.preferredViewer())
                || rows.stream().anyMatch(row -> row.viewer()
                        && row.available()
                        && row.enabled()
                        && row.actionId().equals(draft.preferredViewer()));
        return new RecipeSettingsProjection(
                rows,
                draft,
                preferredAvailable,
                view.unknownDisabledSources().size());
    }
public ToolResult<RecipeClientConfig> toggleSource(String actionId) {
        if (sources.stream().noneMatch(source -> source.actionId().equals(actionId))) {
            return new ToolResult.Failure<>(
                    "recipe_source_unavailable", "This recipe source is unavailable");
        }
        Set<String> disabled = new TreeSet<>(config.disabledSources());
        if (!disabled.remove(actionId)) {
            disabled.add(actionId);
        }
        return new ToolResult.Success<>(new RecipeClientConfig(
                config.visibility(),
                config.preferredViewer(),
                disabled));
    }
public RecipeClientConfig cycleVisibility() {
        RecipeVisibilityPolicy next = config.visibility() == RecipeVisibilityPolicy.ALL_KNOWN
                ? RecipeVisibilityPolicy.UNLOCKED_ONLY
                : RecipeVisibilityPolicy.ALL_KNOWN;
        return new RecipeClientConfig(
                next,
                config.preferredViewer(),
                config.disabledSources());
    }
public RecipeClientConfig cyclePreferredViewer() {
        List<String> options = new ArrayList<>();
        options.add(RecipeClientConfig.AUTO);
        sources.stream()
                .filter(source -> source.viewer() && source.available() && source.enabled())
                .map(SourceRow::actionId)
                .sorted()
                .forEach(options::add);
        if (!options.contains(config.preferredViewer())) {
            options.add(config.preferredViewer());
        }
        int index = options.indexOf(config.preferredViewer());
        String next = options.get((index + 1) % options.size());
        return new RecipeClientConfig(
                config.visibility(),
                next,
                config.disabledSources());
    }
private static String sourceTitleKey(String sourceId) {
        String key = sourceId.replace(':', '_').replace('/', '_').replace('-', '_');
        return "screen.openallay.settings.recipe.source." + key;
    }
@dev.openallay.value.ValueType(SourceRow.ValueSchemaProvider.class)
public static final class SourceRow {
    private final String actionId;
    private final String titleKey;
    private final boolean available;
    private final boolean enabled;
    private final boolean viewer;
    private final boolean exactNavigation;
    private final String debugId;
    public SourceRow(String actionId, String titleKey, boolean available, boolean enabled, boolean viewer, boolean exactNavigation, String debugId) {
        this.actionId = actionId;
        this.titleKey = titleKey;
        this.available = available;
        this.enabled = enabled;
        this.viewer = viewer;
        this.exactNavigation = exactNavigation;
        this.debugId = debugId;
    }
    public String actionId() { return actionId; }
    public String titleKey() { return titleKey; }
    public boolean available() { return available; }
    public boolean enabled() { return enabled; }
    public boolean viewer() { return viewer; }
    public boolean exactNavigation() { return exactNavigation; }
    public String debugId() { return debugId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SourceRow)) return false;
        SourceRow that = (SourceRow) other;
        return java.util.Objects.equals(actionId, that.actionId) && java.util.Objects.equals(titleKey, that.titleKey) && available == that.available && enabled == that.enabled && viewer == that.viewer && exactNavigation == that.exactNavigation && java.util.Objects.equals(debugId, that.debugId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(actionId);
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + Boolean.hashCode(viewer);
        hash = 31 * hash + Boolean.hashCode(exactNavigation);
        hash = 31 * hash + java.util.Objects.hashCode(debugId);
        return hash;
    }
    @Override public String toString() { return "SourceRow[actionId=" + actionId + ", titleKey=" + titleKey + ", available=" + available + ", enabled=" + enabled + ", viewer=" + viewer + ", exactNavigation=" + exactNavigation + ", debugId=" + debugId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SourceRow> schema() {
            return new dev.openallay.value.ValueSchema<>(SourceRow.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SourceRow>>asList(new dev.openallay.value.ValueSchema.Component<>(SourceRow.class, "actionId", SourceRow::actionId), new dev.openallay.value.ValueSchema.Component<>(SourceRow.class, "titleKey", SourceRow::titleKey), new dev.openallay.value.ValueSchema.Component<>(SourceRow.class, "available", SourceRow::available), new dev.openallay.value.ValueSchema.Component<>(SourceRow.class, "enabled", SourceRow::enabled), new dev.openallay.value.ValueSchema.Component<>(SourceRow.class, "viewer", SourceRow::viewer), new dev.openallay.value.ValueSchema.Component<>(SourceRow.class, "exactNavigation", SourceRow::exactNavigation), new dev.openallay.value.ValueSchema.Component<>(SourceRow.class, "debugId", SourceRow::debugId)), arguments -> new SourceRow((String) arguments[0], (String) arguments[1], (Boolean) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4], (Boolean) arguments[5], (String) arguments[6]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeSettingsProjection)) return false;
        RecipeSettingsProjection that = (RecipeSettingsProjection) other;
        return java.util.Objects.equals(sources, that.sources) && java.util.Objects.equals(config, that.config) && preferredViewerAvailable == that.preferredViewerAvailable && retainedUnknownCount == that.retainedUnknownCount;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        hash = 31 * hash + java.util.Objects.hashCode(config);
        hash = 31 * hash + Boolean.hashCode(preferredViewerAvailable);
        hash = 31 * hash + Integer.hashCode(retainedUnknownCount);
        return hash;
    }
    @Override public String toString() { return "RecipeSettingsProjection[sources=" + sources + ", config=" + config + ", preferredViewerAvailable=" + preferredViewerAvailable + ", retainedUnknownCount=" + retainedUnknownCount + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeSettingsProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeSettingsProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeSettingsProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(RecipeSettingsProjection.class, "sources", RecipeSettingsProjection::sources), new dev.openallay.value.ValueSchema.Component<>(RecipeSettingsProjection.class, "config", RecipeSettingsProjection::config), new dev.openallay.value.ValueSchema.Component<>(RecipeSettingsProjection.class, "preferredViewerAvailable", RecipeSettingsProjection::preferredViewerAvailable), new dev.openallay.value.ValueSchema.Component<>(RecipeSettingsProjection.class, "retainedUnknownCount", RecipeSettingsProjection::retainedUnknownCount)), arguments -> new RecipeSettingsProjection((List) arguments[0], (RecipeClientConfig) arguments[1], (Boolean) arguments[2], (Integer) arguments[3]));
        }
    }
}
