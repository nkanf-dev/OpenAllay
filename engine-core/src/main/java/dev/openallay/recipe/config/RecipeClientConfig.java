package dev.openallay.recipe.config;

import dev.openallay.context.RecipeReference;
import dev.openallay.recipe.RecipeVisibilityPolicy;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Generic stable-ID recipe settings.
 *
 * @param visibility recipe visibility policy
 * @param preferredViewer preferred recipe-viewer source or {@value #AUTO}
 * @param disabledSources source IDs excluded from recipe discovery
 */
@dev.openallay.value.ValueType(RecipeClientConfig.ValueSchemaProvider.class)
public final class RecipeClientConfig {
    private final RecipeVisibilityPolicy visibility;
    private final String preferredViewer;
    private final Set<String> disabledSources;
    public RecipeClientConfig(RecipeVisibilityPolicy visibility, String preferredViewer, Set<String> disabledSources) {

        Objects.requireNonNull(visibility, "visibility");
        if (preferredViewer == null || preferredViewer.isBlank()) {
            throw new IllegalArgumentException("preferredViewer must not be blank");
        }
        if (!AUTO.equals(preferredViewer)) {
            preferredViewer = RecipeReference.requireSourceId(preferredViewer);
        }
        Objects.requireNonNull(disabledSources, "disabledSources");
        TreeSet<String> canonical = new TreeSet<>();
        for (String sourceId : disabledSources) {
            String validated = RecipeReference.requireSourceId(sourceId);
            if (!canonical.add(validated)) {
                throw new IllegalArgumentException("disabledSources must not contain duplicates");
            }
        }
        disabledSources = Collections.unmodifiableSet(canonical);

        this.visibility = visibility;
        this.preferredViewer = preferredViewer;
        this.disabledSources = disabledSources;
    }
    public RecipeVisibilityPolicy visibility() { return visibility; }
    public String preferredViewer() { return preferredViewer; }
    public Set<String> disabledSources() { return disabledSources; }
public static final String AUTO = "auto";
public static RecipeClientConfig defaults() {
        return new RecipeClientConfig(
                RecipeVisibilityPolicy.ALL_KNOWN,
                AUTO,
                Set.of());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeClientConfig)) return false;
        RecipeClientConfig that = (RecipeClientConfig) other;
        return java.util.Objects.equals(visibility, that.visibility) && java.util.Objects.equals(preferredViewer, that.preferredViewer) && java.util.Objects.equals(disabledSources, that.disabledSources);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(visibility);
        hash = 31 * hash + java.util.Objects.hashCode(preferredViewer);
        hash = 31 * hash + java.util.Objects.hashCode(disabledSources);
        return hash;
    }
    @Override public String toString() { return "RecipeClientConfig[visibility=" + visibility + ", preferredViewer=" + preferredViewer + ", disabledSources=" + disabledSources + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeClientConfig> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeClientConfig.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeClientConfig>>asList(new dev.openallay.value.ValueSchema.Component<>(RecipeClientConfig.class, "visibility", RecipeClientConfig::visibility), new dev.openallay.value.ValueSchema.Component<>(RecipeClientConfig.class, "preferredViewer", RecipeClientConfig::preferredViewer), new dev.openallay.value.ValueSchema.Component<>(RecipeClientConfig.class, "disabledSources", RecipeClientConfig::disabledSources)), arguments -> new RecipeClientConfig((RecipeVisibilityPolicy) arguments[0], (String) arguments[1], (Set) arguments[2]));
        }
    }
}
