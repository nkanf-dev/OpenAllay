package dev.openallay.guide.ui;

import dev.openallay.context.RecipeReference;
import java.util.List;

/** Safe first-class projection for recipe results rendered by the native screen. */
@dev.openallay.value.ValueType(GuideRecipeCard.ValueSchemaProvider.class)
public final class GuideRecipeCard {
    private final RecipeReference reference;
    private final List<RecipeReference> references;
    private final String id;
    private final String type;
    private final String workstation;
    private final List<Output> outputs;
    private final List<Ingredient> ingredients;
    private final List<Ingredient> catalysts;
    private final List<Output> byproducts;
    private final Processing processing;
    public GuideRecipeCard(RecipeReference reference, List<RecipeReference> references, String id, String type, String workstation, List<Output> outputs, List<Ingredient> ingredients, List<Ingredient> catalysts, List<Output> byproducts, Processing processing) {

        java.util.Objects.requireNonNull(reference, "reference");
        references = List.copyOf(references);
        if (references.isEmpty() || !references.contains(reference)) {
            throw new IllegalArgumentException("recipe card references are incomplete");
        }
        if (id == null || id.isBlank() || type == null || type.isBlank()) {
            throw new IllegalArgumentException("recipe card identity is invalid");
        }
        workstation = workstation == null ? "" : workstation;
        outputs = List.copyOf(outputs);
        ingredients = List.copyOf(ingredients);
        catalysts = List.copyOf(catalysts);
        byproducts = List.copyOf(byproducts);
        processing = java.util.Objects.requireNonNull(processing, "processing");

        this.reference = reference;
        this.references = references;
        this.id = id;
        this.type = type;
        this.workstation = workstation;
        this.outputs = outputs;
        this.ingredients = ingredients;
        this.catalysts = catalysts;
        this.byproducts = byproducts;
        this.processing = processing;
    }
    public RecipeReference reference() { return reference; }
    public List<RecipeReference> references() { return references; }
    public String id() { return id; }
    public String type() { return type; }
    public String workstation() { return workstation; }
    public List<Output> outputs() { return outputs; }
    public List<Ingredient> ingredients() { return ingredients; }
    public List<Ingredient> catalysts() { return catalysts; }
    public List<Output> byproducts() { return byproducts; }
    public Processing processing() { return processing; }
public GuideRecipeCard(
            RecipeReference reference,
            List<RecipeReference> references,
            String id,
            String type,
            String workstation,
            List<Output> outputs) {
        this(reference, references, id, type, workstation, outputs,
                List.of(), List.of(), List.of(), Processing.unknown());
    }
@dev.openallay.value.ValueType(Output.ValueSchemaProvider.class)
public static final class Output {
    private final String itemId;
    private final int count;
    private final String displayName;
    public Output(String itemId, int count, String displayName) {

            if (itemId == null || itemId.isBlank() || count <= 0) {
                throw new IllegalArgumentException("recipe card output is invalid");
            }
            displayName = displayName == null || displayName.isBlank() ? itemId : displayName;

        this.itemId = itemId;
        this.count = count;
        this.displayName = displayName;
    }
    public String itemId() { return itemId; }
    public int count() { return count; }
    public String displayName() { return displayName; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Output)) return false;
        Output that = (Output) other;
        return java.util.Objects.equals(itemId, that.itemId) && count == that.count && java.util.Objects.equals(displayName, that.displayName);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(itemId);
        hash = 31 * hash + Integer.hashCode(count);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        return hash;
    }
    @Override public String toString() { return "Output[itemId=" + itemId + ", count=" + count + ", displayName=" + displayName + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Output> schema() {
            return new dev.openallay.value.ValueSchema<>(Output.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Output>>asList(new dev.openallay.value.ValueSchema.Component<>(Output.class, "itemId", Output::itemId), new dev.openallay.value.ValueSchema.Component<>(Output.class, "count", Output::count), new dev.openallay.value.ValueSchema.Component<>(Output.class, "displayName", Output::displayName)), arguments -> new Output((String) arguments[0], (Integer) arguments[1], (String) arguments[2]));
        }
    }
}
@dev.openallay.value.ValueType(Ingredient.ValueSchemaProvider.class)
public static final class Ingredient {
    private final String key;
    private final long count;
    private final boolean consumed;
    private final List<Alternative> alternatives;
    public Ingredient(String key, long count, boolean consumed, List<Alternative> alternatives) {

            if (key == null || key.isBlank() || count <= 0) {
                throw new IllegalArgumentException("recipe ingredient is invalid");
            }
            alternatives = List.copyOf(alternatives);
            if (alternatives.isEmpty()) {
                throw new IllegalArgumentException("recipe ingredient has no alternatives");
            }

        this.key = key;
        this.count = count;
        this.consumed = consumed;
        this.alternatives = alternatives;
    }
    public String key() { return key; }
    public long count() { return count; }
    public boolean consumed() { return consumed; }
    public List<Alternative> alternatives() { return alternatives; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Ingredient)) return false;
        Ingredient that = (Ingredient) other;
        return java.util.Objects.equals(key, that.key) && count == that.count && consumed == that.consumed && java.util.Objects.equals(alternatives, that.alternatives);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + Long.hashCode(count);
        hash = 31 * hash + Boolean.hashCode(consumed);
        hash = 31 * hash + java.util.Objects.hashCode(alternatives);
        return hash;
    }
    @Override public String toString() { return "Ingredient[key=" + key + ", count=" + count + ", consumed=" + consumed + ", alternatives=" + alternatives + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Ingredient> schema() {
            return new dev.openallay.value.ValueSchema<>(Ingredient.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Ingredient>>asList(new dev.openallay.value.ValueSchema.Component<>(Ingredient.class, "key", Ingredient::key), new dev.openallay.value.ValueSchema.Component<>(Ingredient.class, "count", Ingredient::count), new dev.openallay.value.ValueSchema.Component<>(Ingredient.class, "consumed", Ingredient::consumed), new dev.openallay.value.ValueSchema.Component<>(Ingredient.class, "alternatives", Ingredient::alternatives)), arguments -> new Ingredient((String) arguments[0], (Long) arguments[1], (Boolean) arguments[2], (List) arguments[3]));
        }
    }
}
@dev.openallay.value.ValueType(Alternative.ValueSchemaProvider.class)
public static final class Alternative {
    private final String kind;
    private final String id;
    private final List<String> resolvedItems;
    public Alternative(String kind, String id, List<String> resolvedItems) {

            if (kind == null || kind.isBlank() || id == null || id.isBlank()) {
                throw new IllegalArgumentException("recipe alternative is invalid");
            }
            resolvedItems = List.copyOf(resolvedItems);

        this.kind = kind;
        this.id = id;
        this.resolvedItems = resolvedItems;
    }
    public String kind() { return kind; }
    public String id() { return id; }
    public List<String> resolvedItems() { return resolvedItems; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Alternative)) return false;
        Alternative that = (Alternative) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(id, that.id) && java.util.Objects.equals(resolvedItems, that.resolvedItems);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(resolvedItems);
        return hash;
    }
    @Override public String toString() { return "Alternative[kind=" + kind + ", id=" + id + ", resolvedItems=" + resolvedItems + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Alternative> schema() {
            return new dev.openallay.value.ValueSchema<>(Alternative.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Alternative>>asList(new dev.openallay.value.ValueSchema.Component<>(Alternative.class, "kind", Alternative::kind), new dev.openallay.value.ValueSchema.Component<>(Alternative.class, "id", Alternative::id), new dev.openallay.value.ValueSchema.Component<>(Alternative.class, "resolvedItems", Alternative::resolvedItems)), arguments -> new Alternative((String) arguments[0], (String) arguments[1], (List) arguments[2]));
        }
    }
}
@dev.openallay.value.ValueType(Processing.ValueSchemaProvider.class)
public static final class Processing {
    private final Long durationTicks;
    private final Long energy;
    private final Double temperature;
    public Processing(Long durationTicks, Long energy, Double temperature) {

            if ((durationTicks != null && durationTicks < 0)
                    || (energy != null && energy < 0)
                    || (temperature != null && !Double.isFinite(temperature))) {
                throw new IllegalArgumentException("recipe processing metadata is invalid");
            }

        this.durationTicks = durationTicks;
        this.energy = energy;
        this.temperature = temperature;
    }
    public Long durationTicks() { return durationTicks; }
    public Long energy() { return energy; }
    public Double temperature() { return temperature; }
public static Processing unknown() {
            return new Processing(null, null, null);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Processing)) return false;
        Processing that = (Processing) other;
        return java.util.Objects.equals(durationTicks, that.durationTicks) && java.util.Objects.equals(energy, that.energy) && java.util.Objects.equals(temperature, that.temperature);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(durationTicks);
        hash = 31 * hash + java.util.Objects.hashCode(energy);
        hash = 31 * hash + java.util.Objects.hashCode(temperature);
        return hash;
    }
    @Override public String toString() { return "Processing[durationTicks=" + durationTicks + ", energy=" + energy + ", temperature=" + temperature + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Processing> schema() {
            return new dev.openallay.value.ValueSchema<>(Processing.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Processing>>asList(new dev.openallay.value.ValueSchema.Component<>(Processing.class, "durationTicks", Processing::durationTicks), new dev.openallay.value.ValueSchema.Component<>(Processing.class, "energy", Processing::energy), new dev.openallay.value.ValueSchema.Component<>(Processing.class, "temperature", Processing::temperature)), arguments -> new Processing((Long) arguments[0], (Long) arguments[1], (Double) arguments[2]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideRecipeCard)) return false;
        GuideRecipeCard that = (GuideRecipeCard) other;
        return java.util.Objects.equals(reference, that.reference) && java.util.Objects.equals(references, that.references) && java.util.Objects.equals(id, that.id) && java.util.Objects.equals(type, that.type) && java.util.Objects.equals(workstation, that.workstation) && java.util.Objects.equals(outputs, that.outputs) && java.util.Objects.equals(ingredients, that.ingredients) && java.util.Objects.equals(catalysts, that.catalysts) && java.util.Objects.equals(byproducts, that.byproducts) && java.util.Objects.equals(processing, that.processing);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        hash = 31 * hash + java.util.Objects.hashCode(references);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + java.util.Objects.hashCode(workstation);
        hash = 31 * hash + java.util.Objects.hashCode(outputs);
        hash = 31 * hash + java.util.Objects.hashCode(ingredients);
        hash = 31 * hash + java.util.Objects.hashCode(catalysts);
        hash = 31 * hash + java.util.Objects.hashCode(byproducts);
        hash = 31 * hash + java.util.Objects.hashCode(processing);
        return hash;
    }
    @Override public String toString() { return "GuideRecipeCard[reference=" + reference + ", references=" + references + ", id=" + id + ", type=" + type + ", workstation=" + workstation + ", outputs=" + outputs + ", ingredients=" + ingredients + ", catalysts=" + catalysts + ", byproducts=" + byproducts + ", processing=" + processing + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideRecipeCard> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideRecipeCard.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideRecipeCard>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideRecipeCard.class, "reference", GuideRecipeCard::reference), new dev.openallay.value.ValueSchema.Component<>(GuideRecipeCard.class, "references", GuideRecipeCard::references), new dev.openallay.value.ValueSchema.Component<>(GuideRecipeCard.class, "id", GuideRecipeCard::id), new dev.openallay.value.ValueSchema.Component<>(GuideRecipeCard.class, "type", GuideRecipeCard::type), new dev.openallay.value.ValueSchema.Component<>(GuideRecipeCard.class, "workstation", GuideRecipeCard::workstation), new dev.openallay.value.ValueSchema.Component<>(GuideRecipeCard.class, "outputs", GuideRecipeCard::outputs), new dev.openallay.value.ValueSchema.Component<>(GuideRecipeCard.class, "ingredients", GuideRecipeCard::ingredients), new dev.openallay.value.ValueSchema.Component<>(GuideRecipeCard.class, "catalysts", GuideRecipeCard::catalysts), new dev.openallay.value.ValueSchema.Component<>(GuideRecipeCard.class, "byproducts", GuideRecipeCard::byproducts), new dev.openallay.value.ValueSchema.Component<>(GuideRecipeCard.class, "processing", GuideRecipeCard::processing)), arguments -> new GuideRecipeCard((RecipeReference) arguments[0], (List) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (List) arguments[5], (List) arguments[6], (List) arguments[7], (List) arguments[8], (Processing) arguments[9]));
        }
    }
}
