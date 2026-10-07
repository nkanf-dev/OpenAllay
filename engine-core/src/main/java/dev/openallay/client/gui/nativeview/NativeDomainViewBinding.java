package dev.openallay.client.gui.nativeview;

import dev.openallay.guide.semantic.RichComponent;
import dev.openallay.guide.ui.GuideRecipeCard;

/** Closed, detached input to a client-thread native view provider. */
public sealed interface NativeDomainViewBinding
        permits NativeDomainViewBinding.Recipe {
    Family family();

    String stableId();

    enum Family {
        RECIPE
    }

    @dev.openallay.value.ValueType(Recipe.ValueSchemaProvider.class)
public static final class Recipe implements NativeDomainViewBinding {
    private final String stableId;
    private final RichComponent.RecipeGrid component;
    private final GuideRecipeCard recipe;
    public Recipe(String stableId, RichComponent.RecipeGrid component, GuideRecipeCard recipe) {

            stableId = requireId(stableId);
            java.util.Objects.requireNonNull(component, "component");
            java.util.Objects.requireNonNull(recipe, "recipe");
            if (!recipe.references().contains(component.recipe())) {
                throw new IllegalArgumentException("native recipe binding reference is not exact");
            }

        this.stableId = stableId;
        this.component = component;
        this.recipe = recipe;
    }
    public String stableId() { return stableId; }
    public RichComponent.RecipeGrid component() { return component; }
    public GuideRecipeCard recipe() { return recipe; }
@Override
        public Family family() {
            return Family.RECIPE;
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Recipe)) return false;
        Recipe that = (Recipe) other;
        return java.util.Objects.equals(stableId, that.stableId) && java.util.Objects.equals(component, that.component) && java.util.Objects.equals(recipe, that.recipe);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(stableId);
        hash = 31 * hash + java.util.Objects.hashCode(component);
        hash = 31 * hash + java.util.Objects.hashCode(recipe);
        return hash;
    }
    @Override public String toString() { return "Recipe[stableId=" + stableId + ", component=" + component + ", recipe=" + recipe + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Recipe> schema() {
            return new dev.openallay.value.ValueSchema<>(Recipe.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Recipe>>asList(new dev.openallay.value.ValueSchema.Component<>(Recipe.class, "stableId", Recipe::stableId), new dev.openallay.value.ValueSchema.Component<>(Recipe.class, "component", Recipe::component), new dev.openallay.value.ValueSchema.Component<>(Recipe.class, "recipe", Recipe::recipe)), arguments -> new Recipe((String) arguments[0], (RichComponent.RecipeGrid) arguments[1], (GuideRecipeCard) arguments[2]));
        }
    }
}

    private static String requireId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("native view stable ID is required");
        }
        return value;
    }
}
