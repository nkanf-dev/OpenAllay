package dev.openallay.recipe;

import dev.openallay.context.DataCompleteness;
import dev.openallay.context.RecipeEntrySnapshot;
import dev.openallay.context.RecipeReference;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@dev.openallay.value.ValueType(RecipeProviderSnapshot.ValueSchemaProvider.class)
public final class RecipeProviderSnapshot {
    private final String sourceId;
    private final String generation;
    private final RecipeProviderState state;
    private final DataCompleteness completeness;
    private final List<RecipeEntrySnapshot> recipes;
    private final List<RecipeProviderDiagnostic> diagnostics;
    public RecipeProviderSnapshot(String sourceId, String generation, RecipeProviderState state, DataCompleteness completeness, List<RecipeEntrySnapshot> recipes, List<RecipeProviderDiagnostic> diagnostics) {

        sourceId = RecipeReference.requireSourceId(sourceId);
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(completeness, "completeness");
        recipes = dev.openallay.util.Java8Collections.listCopyOf(recipes);
        diagnostics = dev.openallay.util.Java8Collections.listCopyOf(diagnostics);
        if (state == RecipeProviderState.AVAILABLE) {
            RecipeReference.requireGeneration(generation);
        } else if (generation != null) {
            throw new IllegalArgumentException("unavailable provider must not expose a generation");
        }
        if (state != RecipeProviderState.AVAILABLE && !recipes.isEmpty()) {
            throw new IllegalArgumentException("unavailable provider must not expose recipes");
        }
        if (state != RecipeProviderState.AVAILABLE && completeness != DataCompleteness.UNKNOWN) {
            throw new IllegalArgumentException("unavailable provider completeness must be unknown");
        }
        HashSet<String> recipeIds = new HashSet<>();
        for (RecipeEntrySnapshot recipe : recipes) {
            if (!recipe.reference().sourceId().equals(sourceId)
                    || !recipe.reference().generation().equals(generation)) {
                throw new IllegalArgumentException("recipe belongs to another provider generation");
            }
            if (!recipeIds.add(recipe.reference().recipeId())) {
                throw new IllegalArgumentException("provider contains duplicate recipe reference");
            }
        }
        for (RecipeProviderDiagnostic diagnostic : diagnostics) {
            if (!diagnostic.sourceId().equals(sourceId)) {
                throw new IllegalArgumentException("diagnostic belongs to another provider");
            }
        }
        if (state == RecipeProviderState.AVAILABLE
                && !RecipeCanonicalizer.providerGeneration(sourceId, recipes).equals(generation)) {
            throw new IllegalArgumentException("provider generation does not match its records");
        }
            this.sourceId = sourceId;
        this.generation = generation;
        this.state = state;
        this.completeness = completeness;
        this.recipes = recipes;
        this.diagnostics = diagnostics;
    }
    public String sourceId() { return sourceId; }
    public String generation() { return generation; }
    public RecipeProviderState state() { return state; }
    public DataCompleteness completeness() { return completeness; }
    public List<RecipeEntrySnapshot> recipes() { return recipes; }
    public List<RecipeProviderDiagnostic> diagnostics() { return diagnostics; }



    public static RecipeProviderSnapshot available(
            String sourceId,
            DataCompleteness completeness,
            List<RecipeEntrySnapshot> recipes,
            List<RecipeProviderDiagnostic> diagnostics) {
        sourceId = RecipeReference.requireSourceId(sourceId);
        List<RecipeEntrySnapshot> detached = dev.openallay.util.Java8Collections.listCopyOf(recipes);
        for (RecipeEntrySnapshot recipe : detached) {
            if (!recipe.reference().sourceId().equals(sourceId)) {
                throw new IllegalArgumentException("recipe belongs to another provider");
            }
        }
        String generation = RecipeCanonicalizer.providerGeneration(sourceId, detached);
        String finalSourceId = sourceId;
        List<RecipeEntrySnapshot> rebound = detached.stream()
                .map(recipe -> recipe.withReference(new RecipeReference(
                        finalSourceId, generation, recipe.reference().recipeId())))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(java.util.ArrayList::new), java.util.Collections::unmodifiableList));
        return new RecipeProviderSnapshot(
                sourceId,
                generation,
                RecipeProviderState.AVAILABLE,
                completeness,
                rebound,
                diagnostics);
    }

    public static RecipeProviderSnapshot unavailable(String sourceId, String code, String message) {
        return inactive(sourceId, RecipeProviderState.UNAVAILABLE, code, message);
    }

    public static RecipeProviderSnapshot failed(String sourceId, String code, String message) {
        return inactive(sourceId, RecipeProviderState.FAILED, code, message);
    }

    private static RecipeProviderSnapshot inactive(
            String sourceId, RecipeProviderState state, String code, String message) {
        return new RecipeProviderSnapshot(
                sourceId,
                null,
                state,
                DataCompleteness.UNKNOWN,
                dev.openallay.util.Java8Collections.listOf(),
                dev.openallay.util.Java8Collections.listOf(new RecipeProviderDiagnostic(sourceId, code, message)));
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeProviderSnapshot)) return false;
        RecipeProviderSnapshot that = (RecipeProviderSnapshot) other;
        return java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(generation, that.generation) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(completeness, that.completeness) && java.util.Objects.equals(recipes, that.recipes) && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(completeness);
        hash = 31 * hash + java.util.Objects.hashCode(recipes);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "RecipeProviderSnapshot[sourceId=" + sourceId + ", generation=" + generation + ", state=" + state + ", completeness=" + completeness + ", recipes=" + recipes + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeProviderSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeProviderSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeProviderSnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(RecipeProviderSnapshot.class, "sourceId", RecipeProviderSnapshot::sourceId),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeProviderSnapshot.class, "generation", RecipeProviderSnapshot::generation),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeProviderSnapshot.class, "state", RecipeProviderSnapshot::state),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeProviderSnapshot.class, "completeness", RecipeProviderSnapshot::completeness),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeProviderSnapshot.class, "recipes", RecipeProviderSnapshot::recipes),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeProviderSnapshot.class, "diagnostics", RecipeProviderSnapshot::diagnostics)), arguments -> new RecipeProviderSnapshot((String) arguments[0], (String) arguments[1], (RecipeProviderState) arguments[2], (DataCompleteness) arguments[3], (List) arguments[4], (List) arguments[5]));
        }
    }
}
