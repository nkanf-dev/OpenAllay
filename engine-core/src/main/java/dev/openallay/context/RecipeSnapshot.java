package dev.openallay.context;

import java.util.List;
import dev.openallay.recipe.RecipeCatalogDiagnostic;
import dev.openallay.recipe.RecipeProviderSnapshot;
import dev.openallay.recipe.RecipeSemanticGroup;

@dev.openallay.value.ValueType(RecipeSnapshot.ValueSchemaProvider.class)
public final class RecipeSnapshot {
    private final EvidenceMetadata evidence;
    private final List<RecipeEntrySnapshot> recipes;
    private final List<RecipeProviderSnapshot> providers;
    private final List<RecipeSemanticGroup> groups;
    private final List<RecipeCatalogDiagnostic> diagnostics;
    public RecipeSnapshot(EvidenceMetadata evidence, List<RecipeEntrySnapshot> recipes, List<RecipeProviderSnapshot> providers, List<RecipeSemanticGroup> groups, List<RecipeCatalogDiagnostic> diagnostics) {

        java.util.Objects.requireNonNull(evidence, "evidence");
        recipes = dev.openallay.util.Java8Collections.listCopyOf(recipes);
        providers = dev.openallay.util.Java8Collections.listCopyOf(providers);
        groups = dev.openallay.util.Java8Collections.listCopyOf(groups);
        diagnostics = dev.openallay.util.Java8Collections.listCopyOf(diagnostics);
            this.evidence = evidence;
        this.recipes = recipes;
        this.providers = providers;
        this.groups = groups;
        this.diagnostics = diagnostics;
    }
    public EvidenceMetadata evidence() { return evidence; }
    public List<RecipeEntrySnapshot> recipes() { return recipes; }
    public List<RecipeProviderSnapshot> providers() { return providers; }
    public List<RecipeSemanticGroup> groups() { return groups; }
    public List<RecipeCatalogDiagnostic> diagnostics() { return diagnostics; }



    public RecipeSnapshot(
            EvidenceMetadata evidence,
            List<RecipeEntrySnapshot> recipes,
            List<RecipeProviderSnapshot> providers) {
        this(evidence, recipes, providers, dev.openallay.util.Java8Collections.listOf(), dev.openallay.util.Java8Collections.listOf());
    }

    public RecipeSnapshot(EvidenceMetadata evidence, List<RecipeEntrySnapshot> recipes) {
        this(evidence, recipes, dev.openallay.util.Java8Collections.listOf(), dev.openallay.util.Java8Collections.listOf(), dev.openallay.util.Java8Collections.listOf());
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeSnapshot)) return false;
        RecipeSnapshot that = (RecipeSnapshot) other;
        return java.util.Objects.equals(evidence, that.evidence) && java.util.Objects.equals(recipes, that.recipes) && java.util.Objects.equals(providers, that.providers) && java.util.Objects.equals(groups, that.groups) && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        hash = 31 * hash + java.util.Objects.hashCode(recipes);
        hash = 31 * hash + java.util.Objects.hashCode(providers);
        hash = 31 * hash + java.util.Objects.hashCode(groups);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "RecipeSnapshot[evidence=" + evidence + ", recipes=" + recipes + ", providers=" + providers + ", groups=" + groups + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeSnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(RecipeSnapshot.class, "evidence", RecipeSnapshot::evidence),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeSnapshot.class, "recipes", RecipeSnapshot::recipes),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeSnapshot.class, "providers", RecipeSnapshot::providers),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeSnapshot.class, "groups", RecipeSnapshot::groups),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeSnapshot.class, "diagnostics", RecipeSnapshot::diagnostics)), arguments -> new RecipeSnapshot((EvidenceMetadata) arguments[0], (List) arguments[1], (List) arguments[2], (List) arguments[3], (List) arguments[4]));
        }
    }
}
