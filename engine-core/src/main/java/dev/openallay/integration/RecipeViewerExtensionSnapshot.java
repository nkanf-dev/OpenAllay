package dev.openallay.integration;

import dev.openallay.context.DataCompleteness;
import dev.openallay.recipe.RecipeProviderState;
import java.util.List;

/**
 * Detached recipe-viewer capability projected into the request-scoped Extension root.
 *
 * <p>The projection contains stable OpenAllay references only. It deliberately cannot retain a
 * JEI runtime, REI registry, recipe widget, focus object, or other client-thread-owned value.
 */
@dev.openallay.value.ValueType(RecipeViewerExtensionSnapshot.ValueSchemaProvider.class)
public final class RecipeViewerExtensionSnapshot {
    private final String provider;
    private final boolean available;
    private final RecipeProviderState state;
    private final DataCompleteness completeness;
    private final String generation;
    private final int recipeCount;
    private final List<String> categories;
    private final List<Recipe> recipes;
    private final boolean focusSupported;
    private final boolean navigationSupported;
    private final boolean exactNavigationSupported;
    private final List<Diagnostic> diagnostics;
    public RecipeViewerExtensionSnapshot(String provider, boolean available, RecipeProviderState state, DataCompleteness completeness, String generation, int recipeCount, List<String> categories, List<Recipe> recipes, boolean focusSupported, boolean navigationSupported, boolean exactNavigationSupported, List<Diagnostic> diagnostics) {

        provider = require(provider, "provider");
        java.util.Objects.requireNonNull(state, "state");
        java.util.Objects.requireNonNull(completeness, "completeness");
        if (available != (state == RecipeProviderState.AVAILABLE)) {
            throw new IllegalArgumentException("availability must match provider state");
        }
        if (available && (generation == null || generation.isBlank())) {
            throw new IllegalArgumentException("available provider requires a generation");
        }
        if (!available && generation != null) {
            throw new IllegalArgumentException("unavailable provider cannot expose a generation");
        }
        if (recipeCount < 0) {
            throw new IllegalArgumentException("recipeCount must be non-negative");
        }
        categories = List.copyOf(categories);
        recipes = List.copyOf(recipes);
        diagnostics = List.copyOf(diagnostics);
        if (recipeCount != recipes.size()) {
            throw new IllegalArgumentException("recipeCount must match recipes");
        }

        this.provider = provider;
        this.available = available;
        this.state = state;
        this.completeness = completeness;
        this.generation = generation;
        this.recipeCount = recipeCount;
        this.categories = categories;
        this.recipes = recipes;
        this.focusSupported = focusSupported;
        this.navigationSupported = navigationSupported;
        this.exactNavigationSupported = exactNavigationSupported;
        this.diagnostics = diagnostics;
    }
    public String provider() { return provider; }
    public boolean available() { return available; }
    public RecipeProviderState state() { return state; }
    public DataCompleteness completeness() { return completeness; }
    public String generation() { return generation; }
    public int recipeCount() { return recipeCount; }
    public List<String> categories() { return categories; }
    public List<Recipe> recipes() { return recipes; }
    public boolean focusSupported() { return focusSupported; }
    public boolean navigationSupported() { return navigationSupported; }
    public boolean exactNavigationSupported() { return exactNavigationSupported; }
    public List<Diagnostic> diagnostics() { return diagnostics; }
@dev.openallay.value.ValueType(Recipe.ValueSchemaProvider.class)
public static final class Recipe {
    private final String sourceId;
    private final String generation;
    private final String referenceId;
    private final String recipeId;
    private final String category;
    public Recipe(String sourceId, String generation, String referenceId, String recipeId, String category) {

            sourceId = require(sourceId, "sourceId");
            generation = require(generation, "generation");
            referenceId = require(referenceId, "referenceId");
            recipeId = require(recipeId, "recipeId");
            category = require(category, "category");

        this.sourceId = sourceId;
        this.generation = generation;
        this.referenceId = referenceId;
        this.recipeId = recipeId;
        this.category = category;
    }
    public String sourceId() { return sourceId; }
    public String generation() { return generation; }
    public String referenceId() { return referenceId; }
    public String recipeId() { return recipeId; }
    public String category() { return category; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Recipe)) return false;
        Recipe that = (Recipe) other;
        return java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(generation, that.generation) && java.util.Objects.equals(referenceId, that.referenceId) && java.util.Objects.equals(recipeId, that.recipeId) && java.util.Objects.equals(category, that.category);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(referenceId);
        hash = 31 * hash + java.util.Objects.hashCode(recipeId);
        hash = 31 * hash + java.util.Objects.hashCode(category);
        return hash;
    }
    @Override public String toString() { return "Recipe[sourceId=" + sourceId + ", generation=" + generation + ", referenceId=" + referenceId + ", recipeId=" + recipeId + ", category=" + category + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Recipe> schema() {
            return new dev.openallay.value.ValueSchema<>(Recipe.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Recipe>>asList(new dev.openallay.value.ValueSchema.Component<>(Recipe.class, "sourceId", Recipe::sourceId), new dev.openallay.value.ValueSchema.Component<>(Recipe.class, "generation", Recipe::generation), new dev.openallay.value.ValueSchema.Component<>(Recipe.class, "referenceId", Recipe::referenceId), new dev.openallay.value.ValueSchema.Component<>(Recipe.class, "recipeId", Recipe::recipeId), new dev.openallay.value.ValueSchema.Component<>(Recipe.class, "category", Recipe::category)), arguments -> new Recipe((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4]));
        }
    }
}
@dev.openallay.value.ValueType(Diagnostic.ValueSchemaProvider.class)
public static final class Diagnostic {
    private final String code;
    private final String message;
    public Diagnostic(String code, String message) {

            code = require(code, "code");
            message = require(message, "message");

        this.code = code;
        this.message = message;
    }
    public String code() { return code; }
    public String message() { return message; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Diagnostic)) return false;
        Diagnostic that = (Diagnostic) other;
        return java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "Diagnostic[code=" + code + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Diagnostic> schema() {
            return new dev.openallay.value.ValueSchema<>(Diagnostic.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Diagnostic>>asList(new dev.openallay.value.ValueSchema.Component<>(Diagnostic.class, "code", Diagnostic::code), new dev.openallay.value.ValueSchema.Component<>(Diagnostic.class, "message", Diagnostic::message)), arguments -> new Diagnostic((String) arguments[0], (String) arguments[1]));
        }
    }
}
private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeViewerExtensionSnapshot)) return false;
        RecipeViewerExtensionSnapshot that = (RecipeViewerExtensionSnapshot) other;
        return java.util.Objects.equals(provider, that.provider) && available == that.available && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(completeness, that.completeness) && java.util.Objects.equals(generation, that.generation) && recipeCount == that.recipeCount && java.util.Objects.equals(categories, that.categories) && java.util.Objects.equals(recipes, that.recipes) && focusSupported == that.focusSupported && navigationSupported == that.navigationSupported && exactNavigationSupported == that.exactNavigationSupported && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(provider);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(completeness);
        hash = 31 * hash + java.util.Objects.hashCode(generation);
        hash = 31 * hash + Integer.hashCode(recipeCount);
        hash = 31 * hash + java.util.Objects.hashCode(categories);
        hash = 31 * hash + java.util.Objects.hashCode(recipes);
        hash = 31 * hash + Boolean.hashCode(focusSupported);
        hash = 31 * hash + Boolean.hashCode(navigationSupported);
        hash = 31 * hash + Boolean.hashCode(exactNavigationSupported);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "RecipeViewerExtensionSnapshot[provider=" + provider + ", available=" + available + ", state=" + state + ", completeness=" + completeness + ", generation=" + generation + ", recipeCount=" + recipeCount + ", categories=" + categories + ", recipes=" + recipes + ", focusSupported=" + focusSupported + ", navigationSupported=" + navigationSupported + ", exactNavigationSupported=" + exactNavigationSupported + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeViewerExtensionSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeViewerExtensionSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeViewerExtensionSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(RecipeViewerExtensionSnapshot.class, "provider", RecipeViewerExtensionSnapshot::provider), new dev.openallay.value.ValueSchema.Component<>(RecipeViewerExtensionSnapshot.class, "available", RecipeViewerExtensionSnapshot::available), new dev.openallay.value.ValueSchema.Component<>(RecipeViewerExtensionSnapshot.class, "state", RecipeViewerExtensionSnapshot::state), new dev.openallay.value.ValueSchema.Component<>(RecipeViewerExtensionSnapshot.class, "completeness", RecipeViewerExtensionSnapshot::completeness), new dev.openallay.value.ValueSchema.Component<>(RecipeViewerExtensionSnapshot.class, "generation", RecipeViewerExtensionSnapshot::generation), new dev.openallay.value.ValueSchema.Component<>(RecipeViewerExtensionSnapshot.class, "recipeCount", RecipeViewerExtensionSnapshot::recipeCount), new dev.openallay.value.ValueSchema.Component<>(RecipeViewerExtensionSnapshot.class, "categories", RecipeViewerExtensionSnapshot::categories), new dev.openallay.value.ValueSchema.Component<>(RecipeViewerExtensionSnapshot.class, "recipes", RecipeViewerExtensionSnapshot::recipes), new dev.openallay.value.ValueSchema.Component<>(RecipeViewerExtensionSnapshot.class, "focusSupported", RecipeViewerExtensionSnapshot::focusSupported), new dev.openallay.value.ValueSchema.Component<>(RecipeViewerExtensionSnapshot.class, "navigationSupported", RecipeViewerExtensionSnapshot::navigationSupported), new dev.openallay.value.ValueSchema.Component<>(RecipeViewerExtensionSnapshot.class, "exactNavigationSupported", RecipeViewerExtensionSnapshot::exactNavigationSupported), new dev.openallay.value.ValueSchema.Component<>(RecipeViewerExtensionSnapshot.class, "diagnostics", RecipeViewerExtensionSnapshot::diagnostics)), arguments -> new RecipeViewerExtensionSnapshot((String) arguments[0], (Boolean) arguments[1], (RecipeProviderState) arguments[2], (DataCompleteness) arguments[3], (String) arguments[4], (Integer) arguments[5], (List) arguments[6], (List) arguments[7], (Boolean) arguments[8], (Boolean) arguments[9], (Boolean) arguments[10], (List) arguments[11]));
        }
    }
}
