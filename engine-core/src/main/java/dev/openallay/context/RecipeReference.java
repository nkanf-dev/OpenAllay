package dev.openallay.context;

@dev.openallay.value.ValueType(RecipeReference.ValueSchemaProvider.class)
public final class RecipeReference {
    private final String sourceId;
    private final String generation;
    private final String recipeId;
    public RecipeReference(String sourceId, String generation, String recipeId) {

        sourceId = requireSourceId(sourceId);
        generation = requireGeneration(generation);
        recipeId = requireRecipeId(recipeId);
            this.sourceId = sourceId;
        this.generation = generation;
        this.recipeId = recipeId;
    }
    public String sourceId() { return sourceId; }
    public String generation() { return generation; }
    public String recipeId() { return recipeId; }



    public static String requireSourceId(String value) {
        return ContextValidation.identifier(value, "sourceId");
    }

    public static String requireRecipeId(String value) {
        return ContextValidation.identifier(value, "recipeId");
    }

    public static String requireGeneration(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("generation must be a lowercase SHA-256 digest");
        }
        return value;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeReference)) return false;
        RecipeReference that = (RecipeReference) other;
        return java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(generation, that.generation) && java.util.Objects.equals(recipeId, that.recipeId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(recipeId);
        return hash;
    }
    @Override public String toString() { return "RecipeReference[sourceId=" + sourceId + ", generation=" + generation + ", recipeId=" + recipeId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeReference> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeReference.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeReference>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(RecipeReference.class, "sourceId", RecipeReference::sourceId),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeReference.class, "generation", RecipeReference::generation),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeReference.class, "recipeId", RecipeReference::recipeId)), arguments -> new RecipeReference((String) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}
