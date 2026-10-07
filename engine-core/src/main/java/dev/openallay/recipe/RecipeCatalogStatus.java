package dev.openallay.recipe;

import dev.openallay.context.DataCompleteness;
import dev.openallay.context.RecipeSnapshot;
import java.util.List;

/**
 * Deterministic status projection that exposes source health without duplicating recipes.
 *
 * @param completeness completeness of the aggregated catalog
 * @param recipeCount number of normalized recipes
 * @param semanticGroupCount number of semantic recipe groups
 * @param providers per-provider readiness and coverage
 * @param conflicts normalization diagnostics and conflicts
 */
@dev.openallay.value.ValueType(RecipeCatalogStatus.ValueSchemaProvider.class)
public final class RecipeCatalogStatus {
    private final DataCompleteness completeness;
    private final int recipeCount;
    private final int semanticGroupCount;
    private final List<RecipeProviderStatus> providers;
    private final List<RecipeCatalogDiagnostic> conflicts;
    public RecipeCatalogStatus(DataCompleteness completeness, int recipeCount, int semanticGroupCount, List<RecipeProviderStatus> providers, List<RecipeCatalogDiagnostic> conflicts) {

        java.util.Objects.requireNonNull(completeness, "completeness");
        if (recipeCount < 0 || semanticGroupCount < 0) {
            throw new IllegalArgumentException("recipe catalog counts must not be negative");
        }
        providers = List.copyOf(providers);
        conflicts = List.copyOf(conflicts);

        this.completeness = completeness;
        this.recipeCount = recipeCount;
        this.semanticGroupCount = semanticGroupCount;
        this.providers = providers;
        this.conflicts = conflicts;
    }
    public DataCompleteness completeness() { return completeness; }
    public int recipeCount() { return recipeCount; }
    public int semanticGroupCount() { return semanticGroupCount; }
    public List<RecipeProviderStatus> providers() { return providers; }
    public List<RecipeCatalogDiagnostic> conflicts() { return conflicts; }
public static RecipeCatalogStatus from(RecipeSnapshot snapshot) {
        return new RecipeCatalogStatus(
                snapshot.evidence().completeness(),
                snapshot.recipes().size(),
                snapshot.groups().size(),
                snapshot.providers().stream()
                        .map(RecipeProviderStatus::from)
                        .sorted(java.util.Comparator.comparing(RecipeProviderStatus::sourceId))
                        .toList(),
                snapshot.diagnostics());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeCatalogStatus)) return false;
        RecipeCatalogStatus that = (RecipeCatalogStatus) other;
        return java.util.Objects.equals(completeness, that.completeness) && recipeCount == that.recipeCount && semanticGroupCount == that.semanticGroupCount && java.util.Objects.equals(providers, that.providers) && java.util.Objects.equals(conflicts, that.conflicts);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(completeness);
        hash = 31 * hash + Integer.hashCode(recipeCount);
        hash = 31 * hash + Integer.hashCode(semanticGroupCount);
        hash = 31 * hash + java.util.Objects.hashCode(providers);
        hash = 31 * hash + java.util.Objects.hashCode(conflicts);
        return hash;
    }
    @Override public String toString() { return "RecipeCatalogStatus[completeness=" + completeness + ", recipeCount=" + recipeCount + ", semanticGroupCount=" + semanticGroupCount + ", providers=" + providers + ", conflicts=" + conflicts + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeCatalogStatus> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeCatalogStatus.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeCatalogStatus>>asList(new dev.openallay.value.ValueSchema.Component<>(RecipeCatalogStatus.class, "completeness", RecipeCatalogStatus::completeness), new dev.openallay.value.ValueSchema.Component<>(RecipeCatalogStatus.class, "recipeCount", RecipeCatalogStatus::recipeCount), new dev.openallay.value.ValueSchema.Component<>(RecipeCatalogStatus.class, "semanticGroupCount", RecipeCatalogStatus::semanticGroupCount), new dev.openallay.value.ValueSchema.Component<>(RecipeCatalogStatus.class, "providers", RecipeCatalogStatus::providers), new dev.openallay.value.ValueSchema.Component<>(RecipeCatalogStatus.class, "conflicts", RecipeCatalogStatus::conflicts)), arguments -> new RecipeCatalogStatus((DataCompleteness) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (List) arguments[3], (List) arguments[4]));
        }
    }
}
