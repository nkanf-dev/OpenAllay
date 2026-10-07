package dev.openallay.recipe;

import dev.openallay.context.RecipeReference;
import java.util.List;

@dev.openallay.value.ValueType(RecipeCatalogDiagnostic.ValueSchemaProvider.class)
public final class RecipeCatalogDiagnostic {
    private final String code;
    private final String recipeId;
    private final List<RecipeReference> references;
    private final String message;
    public RecipeCatalogDiagnostic(String code, String recipeId, List<RecipeReference> references, String message) {

        if (code == null || !code.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("diagnostic code is invalid");
        }
        recipeId = RecipeReference.requireRecipeId(recipeId);
        references = dev.openallay.util.Java8Collections.listCopyOf(references);
        if (references.size() < 2) {
            throw new IllegalArgumentException("catalog conflict requires multiple references");
        }
        if (message == null || dev.openallay.util.Java8Strings.isBlank(message)) {
            throw new IllegalArgumentException("diagnostic message is required");
        }
            this.code = code;
        this.recipeId = recipeId;
        this.references = references;
        this.message = message;
    }
    public String code() { return code; }
    public String recipeId() { return recipeId; }
    public List<RecipeReference> references() { return references; }
    public String message() { return message; }



    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeCatalogDiagnostic)) return false;
        RecipeCatalogDiagnostic that = (RecipeCatalogDiagnostic) other;
        return java.util.Objects.equals(code, that.code) && java.util.Objects.equals(recipeId, that.recipeId) && java.util.Objects.equals(references, that.references) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(recipeId);
        hash = 31 * hash + java.util.Objects.hashCode(references);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "RecipeCatalogDiagnostic[code=" + code + ", recipeId=" + recipeId + ", references=" + references + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeCatalogDiagnostic> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeCatalogDiagnostic.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeCatalogDiagnostic>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(RecipeCatalogDiagnostic.class, "code", RecipeCatalogDiagnostic::code),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeCatalogDiagnostic.class, "recipeId", RecipeCatalogDiagnostic::recipeId),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeCatalogDiagnostic.class, "references", RecipeCatalogDiagnostic::references),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeCatalogDiagnostic.class, "message", RecipeCatalogDiagnostic::message)), arguments -> new RecipeCatalogDiagnostic((String) arguments[0], (String) arguments[1], (List) arguments[2], (String) arguments[3]));
        }
    }
}
