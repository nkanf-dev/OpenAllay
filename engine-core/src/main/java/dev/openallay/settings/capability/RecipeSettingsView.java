package dev.openallay.settings.capability;

import dev.openallay.context.RecipeReference;
import dev.openallay.recipe.config.RecipeClientConfig;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Immutable recipe Tool child-settings projection. */
@dev.openallay.value.ValueType(RecipeSettingsView.ValueSchemaProvider.class)
public final class RecipeSettingsView {
    private final RecipeClientConfig config;
    private final List<Source> sources;
    private final Set<String> unknownDisabledSources;
    private final boolean preferredViewerAvailable;
    public RecipeSettingsView(RecipeClientConfig config, List<Source> sources, Set<String> unknownDisabledSources, boolean preferredViewerAvailable) {

        java.util.Objects.requireNonNull(config, "config");
        sources = dev.openallay.util.Java8Collections.toList(dev.openallay.util.Java8Collections.listCopyOf(sources).stream()
                .sorted(Comparator.comparing(Source::id)));
        unknownDisabledSources = Collections.unmodifiableSet(
                new TreeSet<>(unknownDisabledSources));

        this.config = config;
        this.sources = sources;
        this.unknownDisabledSources = unknownDisabledSources;
        this.preferredViewerAvailable = preferredViewerAvailable;
    }
    public RecipeClientConfig config() { return config; }
    public List<Source> sources() { return sources; }
    public Set<String> unknownDisabledSources() { return unknownDisabledSources; }
    public boolean preferredViewerAvailable() { return preferredViewerAvailable; }
public static RecipeSettingsView defaults() {
        return new RecipeSettingsView(
                RecipeClientConfig.defaults(), dev.openallay.util.Java8Collections.listOf(), dev.openallay.util.Java8Collections.setOf(), true);
    }
@dev.openallay.value.ValueType(Source.ValueSchemaProvider.class)
public static final class Source {
    private final String id;
    private final boolean available;
    private final boolean enabled;
    private final boolean viewer;
    private final boolean exactNavigation;
    public Source(String id, boolean available, boolean enabled, boolean viewer, boolean exactNavigation) {

            id = RecipeReference.requireSourceId(id);
            if (exactNavigation && !viewer) {
                throw new IllegalArgumentException("exact navigation requires a viewer source");
            }

        this.id = id;
        this.available = available;
        this.enabled = enabled;
        this.viewer = viewer;
        this.exactNavigation = exactNavigation;
    }
    public String id() { return id; }
    public boolean available() { return available; }
    public boolean enabled() { return enabled; }
    public boolean viewer() { return viewer; }
    public boolean exactNavigation() { return exactNavigation; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Source)) return false;
        Source that = (Source) other;
        return java.util.Objects.equals(id, that.id) && available == that.available && enabled == that.enabled && viewer == that.viewer && exactNavigation == that.exactNavigation;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + Boolean.hashCode(viewer);
        hash = 31 * hash + Boolean.hashCode(exactNavigation);
        return hash;
    }
    @Override public String toString() { return "Source[id=" + id + ", available=" + available + ", enabled=" + enabled + ", viewer=" + viewer + ", exactNavigation=" + exactNavigation + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Source> schema() {
            return new dev.openallay.value.ValueSchema<>(Source.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Source>>asList(new dev.openallay.value.ValueSchema.Component<>(Source.class, "id", Source::id), new dev.openallay.value.ValueSchema.Component<>(Source.class, "available", Source::available), new dev.openallay.value.ValueSchema.Component<>(Source.class, "enabled", Source::enabled), new dev.openallay.value.ValueSchema.Component<>(Source.class, "viewer", Source::viewer), new dev.openallay.value.ValueSchema.Component<>(Source.class, "exactNavigation", Source::exactNavigation)), arguments -> new Source((String) arguments[0], (Boolean) arguments[1], (Boolean) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeSettingsView)) return false;
        RecipeSettingsView that = (RecipeSettingsView) other;
        return java.util.Objects.equals(config, that.config) && java.util.Objects.equals(sources, that.sources) && java.util.Objects.equals(unknownDisabledSources, that.unknownDisabledSources) && preferredViewerAvailable == that.preferredViewerAvailable;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(config);
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        hash = 31 * hash + java.util.Objects.hashCode(unknownDisabledSources);
        hash = 31 * hash + Boolean.hashCode(preferredViewerAvailable);
        return hash;
    }
    @Override public String toString() { return "RecipeSettingsView[config=" + config + ", sources=" + sources + ", unknownDisabledSources=" + unknownDisabledSources + ", preferredViewerAvailable=" + preferredViewerAvailable + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeSettingsView> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeSettingsView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeSettingsView>>asList(new dev.openallay.value.ValueSchema.Component<>(RecipeSettingsView.class, "config", RecipeSettingsView::config), new dev.openallay.value.ValueSchema.Component<>(RecipeSettingsView.class, "sources", RecipeSettingsView::sources), new dev.openallay.value.ValueSchema.Component<>(RecipeSettingsView.class, "unknownDisabledSources", RecipeSettingsView::unknownDisabledSources), new dev.openallay.value.ValueSchema.Component<>(RecipeSettingsView.class, "preferredViewerAvailable", RecipeSettingsView::preferredViewerAvailable)), arguments -> new RecipeSettingsView((RecipeClientConfig) arguments[0], (List) arguments[1], (Set) arguments[2], (Boolean) arguments[3]));
        }
    }
}
