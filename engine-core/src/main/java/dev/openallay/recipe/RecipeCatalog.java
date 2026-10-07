package dev.openallay.recipe;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.IngredientRequirementSnapshot;
import dev.openallay.context.RecipeEntrySnapshot;
import dev.openallay.context.RecipeOutputSnapshot;
import dev.openallay.context.RecipeReference;
import dev.openallay.context.RecipeSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, deterministic query projection over one captured recipe snapshot. */
public final class RecipeCatalog {
    @dev.openallay.value.ValueType(Query.ValueSchemaProvider.class)
public static final class Query {
    private final String recipeId;
    private final String outputItem;
    private final String inputItem;
    private final String recipeType;
    public Query(String recipeId, String outputItem, String inputItem, String recipeType) {

            recipeId = normalize(recipeId);
            outputItem = normalize(outputItem);
            inputItem = normalize(inputItem);
            recipeType = normalize(recipeType);

        this.recipeId = recipeId;
        this.outputItem = outputItem;
        this.inputItem = inputItem;
        this.recipeType = recipeType;
    }
    public String recipeId() { return recipeId; }
    public String outputItem() { return outputItem; }
    public String inputItem() { return inputItem; }
    public String recipeType() { return recipeType; }
public boolean isEmpty() {
            return recipeId == null && outputItem == null && inputItem == null && recipeType == null;
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Query)) return false;
        Query that = (Query) other;
        return java.util.Objects.equals(recipeId, that.recipeId) && java.util.Objects.equals(outputItem, that.outputItem) && java.util.Objects.equals(inputItem, that.inputItem) && java.util.Objects.equals(recipeType, that.recipeType);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(recipeId);
        hash = 31 * hash + java.util.Objects.hashCode(outputItem);
        hash = 31 * hash + java.util.Objects.hashCode(inputItem);
        hash = 31 * hash + java.util.Objects.hashCode(recipeType);
        return hash;
    }
    @Override public String toString() { return "Query[recipeId=" + recipeId + ", outputItem=" + outputItem + ", inputItem=" + inputItem + ", recipeType=" + recipeType + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Query> schema() {
            return new dev.openallay.value.ValueSchema<>(Query.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Query>>asList(new dev.openallay.value.ValueSchema.Component<>(Query.class, "recipeId", Query::recipeId), new dev.openallay.value.ValueSchema.Component<>(Query.class, "outputItem", Query::outputItem), new dev.openallay.value.ValueSchema.Component<>(Query.class, "inputItem", Query::inputItem), new dev.openallay.value.ValueSchema.Component<>(Query.class, "recipeType", Query::recipeType)), arguments -> new Query((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(Summary.ValueSchemaProvider.class)
public static final class Summary {
    private final RecipeReference reference;
    private final String id;
    private final String type;
    private final List<RecipeOutputSnapshot> outputs;
    private final String workstation;
    private final EvidenceMetadata evidence;
    private final List<RecipeReference> references;
    private final List<EvidenceMetadata> evidenceRecords;
    public Summary(RecipeReference reference, String id, String type, List<RecipeOutputSnapshot> outputs, String workstation, EvidenceMetadata evidence, List<RecipeReference> references, List<EvidenceMetadata> evidenceRecords) {

            Objects.requireNonNull(reference, "reference");
            outputs = dev.openallay.util.Java8Collections.listCopyOf(outputs);
            Objects.requireNonNull(evidence, "evidence");
            references = dev.openallay.util.Java8Collections.listCopyOf(references);
            evidenceRecords = dev.openallay.util.Java8Collections.listCopyOf(evidenceRecords);
            if (references.isEmpty() || references.size() != evidenceRecords.size()) {
                throw new IllegalArgumentException("recipe summary references and evidence are incomplete");
            }

        this.reference = reference;
        this.id = id;
        this.type = type;
        this.outputs = outputs;
        this.workstation = workstation;
        this.evidence = evidence;
        this.references = references;
        this.evidenceRecords = evidenceRecords;
    }
    public RecipeReference reference() { return reference; }
    public String id() { return id; }
    public String type() { return type; }
    public List<RecipeOutputSnapshot> outputs() { return outputs; }
    public String workstation() { return workstation; }
    public EvidenceMetadata evidence() { return evidence; }
    public List<RecipeReference> references() { return references; }
    public List<EvidenceMetadata> evidenceRecords() { return evidenceRecords; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Summary)) return false;
        Summary that = (Summary) other;
        return java.util.Objects.equals(reference, that.reference) && java.util.Objects.equals(id, that.id) && java.util.Objects.equals(type, that.type) && java.util.Objects.equals(outputs, that.outputs) && java.util.Objects.equals(workstation, that.workstation) && java.util.Objects.equals(evidence, that.evidence) && java.util.Objects.equals(references, that.references) && java.util.Objects.equals(evidenceRecords, that.evidenceRecords);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + java.util.Objects.hashCode(outputs);
        hash = 31 * hash + java.util.Objects.hashCode(workstation);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        hash = 31 * hash + java.util.Objects.hashCode(references);
        hash = 31 * hash + java.util.Objects.hashCode(evidenceRecords);
        return hash;
    }
    @Override public String toString() { return "Summary[reference=" + reference + ", id=" + id + ", type=" + type + ", outputs=" + outputs + ", workstation=" + workstation + ", evidence=" + evidence + ", references=" + references + ", evidenceRecords=" + evidenceRecords + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Summary> schema() {
            return new dev.openallay.value.ValueSchema<>(Summary.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Summary>>asList(new dev.openallay.value.ValueSchema.Component<>(Summary.class, "reference", Summary::reference), new dev.openallay.value.ValueSchema.Component<>(Summary.class, "id", Summary::id), new dev.openallay.value.ValueSchema.Component<>(Summary.class, "type", Summary::type), new dev.openallay.value.ValueSchema.Component<>(Summary.class, "outputs", Summary::outputs), new dev.openallay.value.ValueSchema.Component<>(Summary.class, "workstation", Summary::workstation), new dev.openallay.value.ValueSchema.Component<>(Summary.class, "evidence", Summary::evidence), new dev.openallay.value.ValueSchema.Component<>(Summary.class, "references", Summary::references), new dev.openallay.value.ValueSchema.Component<>(Summary.class, "evidenceRecords", Summary::evidenceRecords)), arguments -> new Summary((RecipeReference) arguments[0], (String) arguments[1], (String) arguments[2], (List) arguments[3], (String) arguments[4], (EvidenceMetadata) arguments[5], (List) arguments[6], (List) arguments[7]));
        }
    }
}

    public enum UsageRole {
        INPUT,
        CATALYST,
        OUTPUT,
        BYPRODUCT
    }

    @dev.openallay.value.ValueType(Usage.ValueSchemaProvider.class)
public static final class Usage {
    private final RecipeReference reference;
    private final UsageRole role;
    private final EvidenceMetadata evidence;
    public Usage(RecipeReference reference, UsageRole role, EvidenceMetadata evidence) {

            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(evidence, "evidence");

        this.reference = reference;
        this.role = role;
        this.evidence = evidence;
    }
    public RecipeReference reference() { return reference; }
    public UsageRole role() { return role; }
    public EvidenceMetadata evidence() { return evidence; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Usage)) return false;
        Usage that = (Usage) other;
        return java.util.Objects.equals(reference, that.reference) && java.util.Objects.equals(role, that.role) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        hash = 31 * hash + java.util.Objects.hashCode(role);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "Usage[reference=" + reference + ", role=" + role + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Usage> schema() {
            return new dev.openallay.value.ValueSchema<>(Usage.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Usage>>asList(new dev.openallay.value.ValueSchema.Component<>(Usage.class, "reference", Usage::reference), new dev.openallay.value.ValueSchema.Component<>(Usage.class, "role", Usage::role), new dev.openallay.value.ValueSchema.Component<>(Usage.class, "evidence", Usage::evidence)), arguments -> new Usage((RecipeReference) arguments[0], (UsageRole) arguments[1], (EvidenceMetadata) arguments[2]));
        }
    }
}

    private static final Comparator<RecipeEntrySnapshot> RECIPE_ORDER = Comparator
            .comparing((RecipeEntrySnapshot value) -> value.reference().sourceId())
            .thenComparing(value -> value.reference().recipeId());
    private static final Comparator<Usage> USAGE_ORDER = Comparator
            .comparing((Usage value) -> value.reference().sourceId())
            .thenComparing(value -> value.reference().recipeId())
            .thenComparing(Usage::role);

    private final List<RecipeEntrySnapshot> recipes;
    private final List<RecipeSemanticGroup> groups;

    public RecipeCatalog(RecipeSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        recipes = dev.openallay.util.Java8Collections.toList(snapshot.recipes().stream().sorted(RECIPE_ORDER));
        groups = snapshot.groups();
    }

    public List<Summary> search(Query query) {
        Objects.requireNonNull(query, "query");
        if (query.isEmpty()) {
            throw new IllegalArgumentException("at least one recipe search criterion is required");
        }
        if (!groups.isEmpty()) {
            return dev.openallay.util.Java8Collections.toList(groups.stream()
                    .filter(group -> matches(group, query))
                    .map(RecipeCatalog::summary));
        }
        return dev.openallay.util.Java8Collections.toList(recipes.stream()
                .filter(recipe -> query.recipeId() == null
                        || recipe.id().equals(query.recipeId())
                        || recipe.reference().recipeId().equals(query.recipeId()))
                .filter(recipe -> query.recipeType() == null || recipe.type().equals(query.recipeType()))
                .filter(recipe -> query.outputItem() == null
                        || matchesOutputs(recipe.outputs(), query.outputItem())
                        || matchesOutputs(recipe.byproducts(), query.outputItem()))
                .filter(recipe -> query.inputItem() == null
                        || matchesRequirements(recipe.ingredients(), query.inputItem())
                        || matchesRequirements(recipe.catalysts(), query.inputItem()))
                .map(RecipeCatalog::summary));
    }

    public Optional<RecipeEntrySnapshot> get(RecipeReference reference) {
        Objects.requireNonNull(reference, "reference");
        return recipes.stream().filter(recipe -> recipe.reference().equals(reference)).findFirst();
    }

    public List<Usage> usages(String itemId) {
        Objects.requireNonNull(itemId, "itemId");
        List<Usage> result = new ArrayList<>();
        for (RecipeEntrySnapshot recipe : recipes) {
            addUsage(result, recipe, UsageRole.INPUT, matchesRequirements(recipe.ingredients(), itemId));
            addUsage(result, recipe, UsageRole.CATALYST, matchesRequirements(recipe.catalysts(), itemId));
            addUsage(result, recipe, UsageRole.OUTPUT, matchesOutputs(recipe.outputs(), itemId));
            addUsage(result, recipe, UsageRole.BYPRODUCT, matchesOutputs(recipe.byproducts(), itemId));
        }
        return dev.openallay.util.Java8Collections.toList(result.stream().sorted(USAGE_ORDER));
    }

    private static Summary summary(RecipeEntrySnapshot recipe) {
        return new Summary(
                recipe.reference(),
                recipe.id(),
                recipe.type(),
                recipe.outputs(),
                recipe.workstation(),
                recipe.evidence(),
                dev.openallay.util.Java8Collections.listOf(recipe.reference()),
                dev.openallay.util.Java8Collections.listOf(recipe.evidence()));
    }

    private static Summary summary(RecipeSemanticGroup group) {
        RecipeEntrySnapshot recipe = group.representative();
        return new Summary(
                recipe.reference(),
                recipe.id(),
                recipe.type(),
                recipe.outputs(),
                recipe.workstation(),
                recipe.evidence(),
                group.references(),
                group.evidence());
    }

    private static boolean matches(RecipeSemanticGroup group, Query query) {
        RecipeEntrySnapshot recipe = group.representative();
        return (query.recipeId() == null
                        || recipe.id().equals(query.recipeId())
                        || group.references().stream().anyMatch(reference ->
                                reference.recipeId().equals(query.recipeId())))
                && (query.recipeType() == null || recipe.type().equals(query.recipeType()))
                && (query.outputItem() == null
                        || matchesOutputs(recipe.outputs(), query.outputItem())
                        || matchesOutputs(recipe.byproducts(), query.outputItem()))
                && (query.inputItem() == null
                        || matchesRequirements(recipe.ingredients(), query.inputItem())
                        || matchesRequirements(recipe.catalysts(), query.inputItem()));
    }

    private static boolean matchesRequirements(
            List<IngredientRequirementSnapshot> requirements, String itemId) {
        return requirements.stream().flatMap(value -> value.alternatives().stream()).anyMatch(alternative ->
                alternative.id().equals(itemId) || alternative.resolvedItems().contains(itemId));
    }

    private static boolean matchesOutputs(List<RecipeOutputSnapshot> outputs, String itemId) {
        return outputs.stream().anyMatch(output -> output.stack().itemId().equals(itemId));
    }

    private static void addUsage(
            List<Usage> result,
            RecipeEntrySnapshot recipe,
            UsageRole role,
            boolean matches) {
        if (matches) {
            result.add(new Usage(recipe.reference(), role, recipe.evidence()));
        }
    }

    private static String normalize(String value) {
        return value == null || dev.openallay.util.Java8Strings.isBlank(value) ? null : value;
    }
}
