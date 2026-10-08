package dev.openallay.context;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import dev.openallay.recipe.RecipeUnlockState;

@dev.openallay.value.ValueType(RecipeEntrySnapshot.ValueSchemaProvider.class)
public final class RecipeEntrySnapshot {
    private final RecipeReference reference;
    private final String id;
    private final String type;
    private final RecipeLayoutSnapshot layout;
    private final String workstation;
    private final List<IngredientRequirementSnapshot> ingredients;
    private final List<IngredientRequirementSnapshot> catalysts;
    private final List<FluidRequirementSnapshot> fluids;
    private final List<RecipeOutputSnapshot> outputs;
    private final List<RecipeOutputSnapshot> byproducts;
    private final RecipeProcessingSnapshot processing;
    private final List<String> conditions;
    private final Map<String, JsonObject> extensions;
    private final RecipeUnlockState unlockState;
    private final EvidenceMetadata evidence;
    public RecipeEntrySnapshot(RecipeReference reference, String id, String type, RecipeLayoutSnapshot layout, String workstation, List<IngredientRequirementSnapshot> ingredients, List<IngredientRequirementSnapshot> catalysts, List<FluidRequirementSnapshot> fluids, List<RecipeOutputSnapshot> outputs, List<RecipeOutputSnapshot> byproducts, RecipeProcessingSnapshot processing, List<String> conditions, Map<String, JsonObject> extensions, RecipeUnlockState unlockState, EvidenceMetadata evidence) {

        Objects.requireNonNull(reference, "reference");
        id = ContextValidation.identifier(id, "id");
        type = ContextValidation.identifier(type, "type");
        Objects.requireNonNull(layout, "layout");
        if (workstation != null) {
            workstation = ContextValidation.identifier(workstation, "workstation");
        }
        ingredients = copyRequirements(ingredients, "ingredients");
        catalysts = copyRequirements(catalysts, "catalysts");
        fluids = dev.openallay.util.Java8Collections.listCopyOf(fluids);
        outputs = dev.openallay.util.Java8Collections.listCopyOf(outputs);
        byproducts = dev.openallay.util.Java8Collections.listCopyOf(byproducts);
        Objects.requireNonNull(processing, "processing");
        conditions = dev.openallay.util.Java8Collections.listCopyOf(conditions);
        conditions.forEach(value -> ContextValidation.nonBlank(value, "condition"));
        extensions = immutableExtensions(extensions);
        Objects.requireNonNull(unlockState, "unlockState");
        Objects.requireNonNull(evidence, "evidence");
            this.reference = reference;
        this.id = id;
        this.type = type;
        this.layout = layout;
        this.workstation = workstation;
        this.ingredients = ingredients;
        this.catalysts = catalysts;
        this.fluids = fluids;
        this.outputs = outputs;
        this.byproducts = byproducts;
        this.processing = processing;
        this.conditions = conditions;
        this.extensions = extensions;
        this.unlockState = unlockState;
        this.evidence = evidence;
    }
    public RecipeReference reference() { return reference; }
    public String id() { return id; }
    public String type() { return type; }
    public RecipeLayoutSnapshot layout() { return layout; }
    public String workstation() { return workstation; }
    public List<IngredientRequirementSnapshot> ingredients() { return ingredients; }
    public List<IngredientRequirementSnapshot> catalysts() { return catalysts; }
    public List<FluidRequirementSnapshot> fluids() { return fluids; }
    public List<RecipeOutputSnapshot> outputs() { return outputs; }
    public List<RecipeOutputSnapshot> byproducts() { return byproducts; }
    public RecipeProcessingSnapshot processing() { return processing; }
    public List<String> conditions() { return conditions; }
    public RecipeUnlockState unlockState() { return unlockState; }
    public EvidenceMetadata evidence() { return evidence; }



    public RecipeEntrySnapshot(
            RecipeReference reference,
            String id,
            String type,
            RecipeLayoutSnapshot layout,
            String workstation,
            List<IngredientRequirementSnapshot> ingredients,
            List<IngredientRequirementSnapshot> catalysts,
            List<FluidRequirementSnapshot> fluids,
            List<RecipeOutputSnapshot> outputs,
            List<RecipeOutputSnapshot> byproducts,
            RecipeProcessingSnapshot processing,
            List<String> conditions,
            Map<String, JsonObject> extensions,
            EvidenceMetadata evidence) {
        this(
                reference,
                id,
                type,
                layout,
                workstation,
                ingredients,
                catalysts,
                fluids,
                outputs,
                byproducts,
                processing,
                conditions,
                extensions,
                RecipeUnlockState.UNKNOWN,
                evidence);
    }

    public Map<String, JsonObject> extensions() {
        TreeMap<String, JsonObject> copy = new TreeMap<>();
        extensions.forEach((key, value) -> copy.put(key, dev.openallay.json.JsonTrees.copy(value)));
        return Collections.unmodifiableMap(copy);
    }

    public RecipeEntrySnapshot withReference(RecipeReference replacement) {
        return new RecipeEntrySnapshot(
                replacement,
                id,
                type,
                layout,
                workstation,
                ingredients,
                catalysts,
                fluids,
                outputs,
                byproducts,
                processing,
                conditions,
                extensions,
                unlockState,
                evidence);
    }

    private static List<IngredientRequirementSnapshot> copyRequirements(
            List<IngredientRequirementSnapshot> values, String name) {
        List<IngredientRequirementSnapshot> copy = dev.openallay.util.Java8Collections.listCopyOf(values);
        HashSet<String> keys = new HashSet<>();
        for (IngredientRequirementSnapshot value : copy) {
            if (!keys.add(value.key())) {
                throw new IllegalArgumentException(name + " contain duplicate requirement key " + value.key());
            }
        }
        return copy;
    }

    private static Map<String, JsonObject> immutableExtensions(Map<String, JsonObject> values) {
        TreeMap<String, JsonObject> copy = new TreeMap<>();
        Objects.requireNonNull(values, "extensions").forEach((key, value) -> copy.put(
                ContextValidation.identifier(key, "extension key"),
                dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(value, "extension value"))));
        return Collections.unmodifiableMap(copy);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeEntrySnapshot)) return false;
        RecipeEntrySnapshot that = (RecipeEntrySnapshot) other;
        return java.util.Objects.equals(reference, that.reference) && java.util.Objects.equals(id, that.id) && java.util.Objects.equals(type, that.type) && java.util.Objects.equals(layout, that.layout) && java.util.Objects.equals(workstation, that.workstation) && java.util.Objects.equals(ingredients, that.ingredients) && java.util.Objects.equals(catalysts, that.catalysts) && java.util.Objects.equals(fluids, that.fluids) && java.util.Objects.equals(outputs, that.outputs) && java.util.Objects.equals(byproducts, that.byproducts) && java.util.Objects.equals(processing, that.processing) && java.util.Objects.equals(conditions, that.conditions) && java.util.Objects.equals(extensions, that.extensions) && java.util.Objects.equals(unlockState, that.unlockState) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + java.util.Objects.hashCode(layout);
        hash = 31 * hash + java.util.Objects.hashCode(workstation);
        hash = 31 * hash + java.util.Objects.hashCode(ingredients);
        hash = 31 * hash + java.util.Objects.hashCode(catalysts);
        hash = 31 * hash + java.util.Objects.hashCode(fluids);
        hash = 31 * hash + java.util.Objects.hashCode(outputs);
        hash = 31 * hash + java.util.Objects.hashCode(byproducts);
        hash = 31 * hash + java.util.Objects.hashCode(processing);
        hash = 31 * hash + java.util.Objects.hashCode(conditions);
        hash = 31 * hash + java.util.Objects.hashCode(extensions);
        hash = 31 * hash + java.util.Objects.hashCode(unlockState);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "RecipeEntrySnapshot[reference=" + reference + ", id=" + id + ", type=" + type + ", layout=" + layout + ", workstation=" + workstation + ", ingredients=" + ingredients + ", catalysts=" + catalysts + ", fluids=" + fluids + ", outputs=" + outputs + ", byproducts=" + byproducts + ", processing=" + processing + ", conditions=" + conditions + ", extensions=" + extensions + ", unlockState=" + unlockState + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeEntrySnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeEntrySnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeEntrySnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "reference", RecipeEntrySnapshot::reference),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "id", RecipeEntrySnapshot::id),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "type", RecipeEntrySnapshot::type),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "layout", RecipeEntrySnapshot::layout),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "workstation", RecipeEntrySnapshot::workstation),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "ingredients", RecipeEntrySnapshot::ingredients),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "catalysts", RecipeEntrySnapshot::catalysts),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "fluids", RecipeEntrySnapshot::fluids),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "outputs", RecipeEntrySnapshot::outputs),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "byproducts", RecipeEntrySnapshot::byproducts),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "processing", RecipeEntrySnapshot::processing),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "conditions", RecipeEntrySnapshot::conditions),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "extensions", RecipeEntrySnapshot::extensions),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "unlockState", RecipeEntrySnapshot::unlockState),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeEntrySnapshot.class, "evidence", RecipeEntrySnapshot::evidence)), arguments -> new RecipeEntrySnapshot((RecipeReference) arguments[0], (String) arguments[1], (String) arguments[2], (RecipeLayoutSnapshot) arguments[3], (String) arguments[4], (List) arguments[5], (List) arguments[6], (List) arguments[7], (List) arguments[8], (List) arguments[9], (RecipeProcessingSnapshot) arguments[10], (List) arguments[11], (Map) arguments[12], (RecipeUnlockState) arguments[13], (EvidenceMetadata) arguments[14]));
        }
    }
}
