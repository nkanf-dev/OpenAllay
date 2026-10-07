package dev.openallay.recipe;

import dev.openallay.context.DataCompleteness;
import java.util.List;

/** Lightweight tool-facing state for one captured recipe source. */
@dev.openallay.value.ValueType(RecipeProviderStatus.ValueSchemaProvider.class)
public final class RecipeProviderStatus {
    private final String sourceId;
    private final String generation;
    private final RecipeProviderState state;
    private final DataCompleteness completeness;
    private final int recipeCount;
    private final List<RecipeProviderDiagnostic> diagnostics;
    public RecipeProviderStatus(String sourceId, String generation, RecipeProviderState state, DataCompleteness completeness, int recipeCount, List<RecipeProviderDiagnostic> diagnostics) {

        sourceId = dev.openallay.context.RecipeReference.requireSourceId(sourceId);
        java.util.Objects.requireNonNull(state, "state");
        java.util.Objects.requireNonNull(completeness, "completeness");
        if (state == RecipeProviderState.AVAILABLE) {
            dev.openallay.context.RecipeReference.requireGeneration(generation);
        } else if (generation != null) {
            throw new IllegalArgumentException("unavailable provider status has a generation");
        }
        if (recipeCount < 0) {
            throw new IllegalArgumentException("provider recipe count must not be negative");
        }
        diagnostics = List.copyOf(diagnostics);

        this.sourceId = sourceId;
        this.generation = generation;
        this.state = state;
        this.completeness = completeness;
        this.recipeCount = recipeCount;
        this.diagnostics = diagnostics;
    }
    public String sourceId() { return sourceId; }
    public String generation() { return generation; }
    public RecipeProviderState state() { return state; }
    public DataCompleteness completeness() { return completeness; }
    public int recipeCount() { return recipeCount; }
    public List<RecipeProviderDiagnostic> diagnostics() { return diagnostics; }
public static RecipeProviderStatus from(RecipeProviderSnapshot snapshot) {
        return new RecipeProviderStatus(
                snapshot.sourceId(),
                snapshot.generation(),
                snapshot.state(),
                snapshot.completeness(),
                snapshot.recipes().size(),
                snapshot.diagnostics());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeProviderStatus)) return false;
        RecipeProviderStatus that = (RecipeProviderStatus) other;
        return java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(generation, that.generation) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(completeness, that.completeness) && recipeCount == that.recipeCount && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(completeness);
        hash = 31 * hash + Integer.hashCode(recipeCount);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "RecipeProviderStatus[sourceId=" + sourceId + ", generation=" + generation + ", state=" + state + ", completeness=" + completeness + ", recipeCount=" + recipeCount + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeProviderStatus> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeProviderStatus.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeProviderStatus>>asList(new dev.openallay.value.ValueSchema.Component<>(RecipeProviderStatus.class, "sourceId", RecipeProviderStatus::sourceId), new dev.openallay.value.ValueSchema.Component<>(RecipeProviderStatus.class, "generation", RecipeProviderStatus::generation), new dev.openallay.value.ValueSchema.Component<>(RecipeProviderStatus.class, "state", RecipeProviderStatus::state), new dev.openallay.value.ValueSchema.Component<>(RecipeProviderStatus.class, "completeness", RecipeProviderStatus::completeness), new dev.openallay.value.ValueSchema.Component<>(RecipeProviderStatus.class, "recipeCount", RecipeProviderStatus::recipeCount), new dev.openallay.value.ValueSchema.Component<>(RecipeProviderStatus.class, "diagnostics", RecipeProviderStatus::diagnostics)), arguments -> new RecipeProviderStatus((String) arguments[0], (String) arguments[1], (RecipeProviderState) arguments[2], (DataCompleteness) arguments[3], (Integer) arguments[4], (List) arguments[5]));
        }
    }
}
