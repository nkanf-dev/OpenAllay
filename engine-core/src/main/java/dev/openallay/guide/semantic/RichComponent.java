package dev.openallay.guide.semantic;

import dev.openallay.context.RecipeReference;
import java.util.List;

/** Closed set of model-selectable components; behavior is inferred by type, never payload code. */
public interface RichComponent {
    /** Runtime admission for the exact canonical closed variant family. */
    static RichComponent requireKnown(RichComponent value) {
        java.util.Objects.requireNonNull(value, "value");
        Class<?> type = value.getClass();
        if (type == dev.openallay.guide.semantic.RichComponent.ItemRow.class || type == dev.openallay.guide.semantic.RichComponent.RecipeGrid.class || type == dev.openallay.guide.semantic.RichComponent.IngredientCheck.class || type == dev.openallay.guide.semantic.RichComponent.CraftabilitySummary.class || type == dev.openallay.guide.semantic.RichComponent.ProgressSteps.class || type == dev.openallay.guide.semantic.RichComponent.SourceSummary.class || type == dev.openallay.guide.semantic.RichComponent.StatusBadge.class || type == dev.openallay.guide.semantic.RichComponent.ChoiceGroup.class) return value;
        throw new IncompatibleClassChangeError("Unknown RichComponent subtype");
    }

    String nodeId();

    String fallbackText();

    String narration();

    @dev.openallay.value.ValueType(Item.ValueSchemaProvider.class)
public static final class Item {
    private final String itemId;
    private final long count;
    private final String label;
    private final String originInvocationId;
    public Item(String itemId, long count, String label, String originInvocationId) {

            if (!SemanticReferenceValidator.isResourceId(itemId) || count < 0) {
                throw new IllegalArgumentException("component item is invalid");
            }
            label = RichComponentValidation.safeLabel(label);
            originInvocationId = RichComponentValidation.requireOrigin(originInvocationId);

        this.itemId = itemId;
        this.count = count;
        this.label = label;
        this.originInvocationId = originInvocationId;
    }
    public String itemId() { return itemId; }
    public long count() { return count; }
    public String label() { return label; }
    public String originInvocationId() { return originInvocationId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Item)) return false;
        Item that = (Item) other;
        return java.util.Objects.equals(itemId, that.itemId) && count == that.count && java.util.Objects.equals(label, that.label) && java.util.Objects.equals(originInvocationId, that.originInvocationId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(itemId);
        hash = 31 * hash + Long.hashCode(count);
        hash = 31 * hash + java.util.Objects.hashCode(label);
        hash = 31 * hash + java.util.Objects.hashCode(originInvocationId);
        return hash;
    }
    @Override public String toString() { return "Item[itemId=" + itemId + ", count=" + count + ", label=" + label + ", originInvocationId=" + originInvocationId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Item> schema() {
            return new dev.openallay.value.ValueSchema<>(Item.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Item>>asList(new dev.openallay.value.ValueSchema.Component<>(Item.class, "itemId", Item::itemId), new dev.openallay.value.ValueSchema.Component<>(Item.class, "count", Item::count), new dev.openallay.value.ValueSchema.Component<>(Item.class, "label", Item::label), new dev.openallay.value.ValueSchema.Component<>(Item.class, "originInvocationId", Item::originInvocationId)), arguments -> new Item((String) arguments[0], (Long) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(ItemRow.ValueSchemaProvider.class)
public static final class ItemRow implements RichComponent {
    private final String nodeId;
    private final List<Item> items;
    private final String fallbackText;
    private final String narration;
    public ItemRow(String nodeId, List<Item> items, String fallbackText, String narration) {

            RichComponentValidation.common(nodeId, fallbackText, narration);
            items = dev.openallay.util.Java8Collections.listCopyOf(items);
            if (items.isEmpty()) throw new IllegalArgumentException("item row must not be empty");

        this.nodeId = nodeId;
        this.items = items;
        this.fallbackText = fallbackText;
        this.narration = narration;
    }
    public String nodeId() { return nodeId; }
    public List<Item> items() { return items; }
    public String fallbackText() { return fallbackText; }
    public String narration() { return narration; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ItemRow)) return false;
        ItemRow that = (ItemRow) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(items, that.items) && java.util.Objects.equals(fallbackText, that.fallbackText) && java.util.Objects.equals(narration, that.narration);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(items);
        hash = 31 * hash + java.util.Objects.hashCode(fallbackText);
        hash = 31 * hash + java.util.Objects.hashCode(narration);
        return hash;
    }
    @Override public String toString() { return "ItemRow[nodeId=" + nodeId + ", items=" + items + ", fallbackText=" + fallbackText + ", narration=" + narration + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ItemRow> schema() {
            return new dev.openallay.value.ValueSchema<>(ItemRow.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ItemRow>>asList(new dev.openallay.value.ValueSchema.Component<>(ItemRow.class, "nodeId", ItemRow::nodeId), new dev.openallay.value.ValueSchema.Component<>(ItemRow.class, "items", ItemRow::items), new dev.openallay.value.ValueSchema.Component<>(ItemRow.class, "fallbackText", ItemRow::fallbackText), new dev.openallay.value.ValueSchema.Component<>(ItemRow.class, "narration", ItemRow::narration)), arguments -> new ItemRow((String) arguments[0], (List) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(RecipeGrid.ValueSchemaProvider.class)
public static final class RecipeGrid implements RichComponent {
    private final String nodeId;
    private final RecipeReference recipe;
    private final String originInvocationId;
    private final String label;
    private final String fallbackText;
    private final String narration;
    public RecipeGrid(String nodeId, RecipeReference recipe, String originInvocationId, String label, String fallbackText, String narration) {

            RichComponentValidation.common(nodeId, fallbackText, narration);
            java.util.Objects.requireNonNull(recipe, "recipe");
            originInvocationId = RichComponentValidation.requireOrigin(originInvocationId);
            label = RichComponentValidation.safeLabel(label);

        this.nodeId = nodeId;
        this.recipe = recipe;
        this.originInvocationId = originInvocationId;
        this.label = label;
        this.fallbackText = fallbackText;
        this.narration = narration;
    }
    public String nodeId() { return nodeId; }
    public RecipeReference recipe() { return recipe; }
    public String originInvocationId() { return originInvocationId; }
    public String label() { return label; }
    public String fallbackText() { return fallbackText; }
    public String narration() { return narration; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeGrid)) return false;
        RecipeGrid that = (RecipeGrid) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(recipe, that.recipe) && java.util.Objects.equals(originInvocationId, that.originInvocationId) && java.util.Objects.equals(label, that.label) && java.util.Objects.equals(fallbackText, that.fallbackText) && java.util.Objects.equals(narration, that.narration);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(recipe);
        hash = 31 * hash + java.util.Objects.hashCode(originInvocationId);
        hash = 31 * hash + java.util.Objects.hashCode(label);
        hash = 31 * hash + java.util.Objects.hashCode(fallbackText);
        hash = 31 * hash + java.util.Objects.hashCode(narration);
        return hash;
    }
    @Override public String toString() { return "RecipeGrid[nodeId=" + nodeId + ", recipe=" + recipe + ", originInvocationId=" + originInvocationId + ", label=" + label + ", fallbackText=" + fallbackText + ", narration=" + narration + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeGrid> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeGrid.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeGrid>>asList(new dev.openallay.value.ValueSchema.Component<>(RecipeGrid.class, "nodeId", RecipeGrid::nodeId), new dev.openallay.value.ValueSchema.Component<>(RecipeGrid.class, "recipe", RecipeGrid::recipe), new dev.openallay.value.ValueSchema.Component<>(RecipeGrid.class, "originInvocationId", RecipeGrid::originInvocationId), new dev.openallay.value.ValueSchema.Component<>(RecipeGrid.class, "label", RecipeGrid::label), new dev.openallay.value.ValueSchema.Component<>(RecipeGrid.class, "fallbackText", RecipeGrid::fallbackText), new dev.openallay.value.ValueSchema.Component<>(RecipeGrid.class, "narration", RecipeGrid::narration)), arguments -> new RecipeGrid((String) arguments[0], (RecipeReference) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (String) arguments[5]));
        }
    }
}

    @dev.openallay.value.ValueType(Ingredient.ValueSchemaProvider.class)
public static final class Ingredient {
    private final String itemId;
    private final long required;
    private final long available;
    private final String label;
    private final String originInvocationId;
    public Ingredient(String itemId, long required, long available, String label, String originInvocationId) {

            if (!SemanticReferenceValidator.isResourceId(itemId)
                    || required < 0 || available < 0) {
                throw new IllegalArgumentException("ingredient check entry is invalid");
            }
            label = RichComponentValidation.safeLabel(label);
            originInvocationId = RichComponentValidation.requireOrigin(originInvocationId);

        this.itemId = itemId;
        this.required = required;
        this.available = available;
        this.label = label;
        this.originInvocationId = originInvocationId;
    }
    public String itemId() { return itemId; }
    public long required() { return required; }
    public long available() { return available; }
    public String label() { return label; }
    public String originInvocationId() { return originInvocationId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Ingredient)) return false;
        Ingredient that = (Ingredient) other;
        return java.util.Objects.equals(itemId, that.itemId) && required == that.required && available == that.available && java.util.Objects.equals(label, that.label) && java.util.Objects.equals(originInvocationId, that.originInvocationId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(itemId);
        hash = 31 * hash + Long.hashCode(required);
        hash = 31 * hash + Long.hashCode(available);
        hash = 31 * hash + java.util.Objects.hashCode(label);
        hash = 31 * hash + java.util.Objects.hashCode(originInvocationId);
        return hash;
    }
    @Override public String toString() { return "Ingredient[itemId=" + itemId + ", required=" + required + ", available=" + available + ", label=" + label + ", originInvocationId=" + originInvocationId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Ingredient> schema() {
            return new dev.openallay.value.ValueSchema<>(Ingredient.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Ingredient>>asList(new dev.openallay.value.ValueSchema.Component<>(Ingredient.class, "itemId", Ingredient::itemId), new dev.openallay.value.ValueSchema.Component<>(Ingredient.class, "required", Ingredient::required), new dev.openallay.value.ValueSchema.Component<>(Ingredient.class, "available", Ingredient::available), new dev.openallay.value.ValueSchema.Component<>(Ingredient.class, "label", Ingredient::label), new dev.openallay.value.ValueSchema.Component<>(Ingredient.class, "originInvocationId", Ingredient::originInvocationId)), arguments -> new Ingredient((String) arguments[0], (Long) arguments[1], (Long) arguments[2], (String) arguments[3], (String) arguments[4]));
        }
    }
}

    @dev.openallay.value.ValueType(IngredientCheck.ValueSchemaProvider.class)
public static final class IngredientCheck implements RichComponent {
    private final String nodeId;
    private final List<Ingredient> ingredients;
    private final String fallbackText;
    private final String narration;
    public IngredientCheck(String nodeId, List<Ingredient> ingredients, String fallbackText, String narration) {

            RichComponentValidation.common(nodeId, fallbackText, narration);
            ingredients = dev.openallay.util.Java8Collections.listCopyOf(ingredients);
            if (ingredients.isEmpty()) {
                throw new IllegalArgumentException("ingredient check must not be empty");
            }

        this.nodeId = nodeId;
        this.ingredients = ingredients;
        this.fallbackText = fallbackText;
        this.narration = narration;
    }
    public String nodeId() { return nodeId; }
    public List<Ingredient> ingredients() { return ingredients; }
    public String fallbackText() { return fallbackText; }
    public String narration() { return narration; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IngredientCheck)) return false;
        IngredientCheck that = (IngredientCheck) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(ingredients, that.ingredients) && java.util.Objects.equals(fallbackText, that.fallbackText) && java.util.Objects.equals(narration, that.narration);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(ingredients);
        hash = 31 * hash + java.util.Objects.hashCode(fallbackText);
        hash = 31 * hash + java.util.Objects.hashCode(narration);
        return hash;
    }
    @Override public String toString() { return "IngredientCheck[nodeId=" + nodeId + ", ingredients=" + ingredients + ", fallbackText=" + fallbackText + ", narration=" + narration + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<IngredientCheck> schema() {
            return new dev.openallay.value.ValueSchema<>(IngredientCheck.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<IngredientCheck>>asList(new dev.openallay.value.ValueSchema.Component<>(IngredientCheck.class, "nodeId", IngredientCheck::nodeId), new dev.openallay.value.ValueSchema.Component<>(IngredientCheck.class, "ingredients", IngredientCheck::ingredients), new dev.openallay.value.ValueSchema.Component<>(IngredientCheck.class, "fallbackText", IngredientCheck::fallbackText), new dev.openallay.value.ValueSchema.Component<>(IngredientCheck.class, "narration", IngredientCheck::narration)), arguments -> new IngredientCheck((String) arguments[0], (List) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(CraftabilitySummary.ValueSchemaProvider.class)
public static final class CraftabilitySummary implements RichComponent {
    private final String nodeId;
    private final RecipeReference recipe;
    private final String originInvocationId;
    private final boolean craftable;
    private final boolean conclusive;
    private final long requestedCrafts;
    private final long maximumCrafts;
    private final String fallbackText;
    private final String narration;
    public CraftabilitySummary(String nodeId, RecipeReference recipe, String originInvocationId, boolean craftable, boolean conclusive, long requestedCrafts, long maximumCrafts, String fallbackText, String narration) {

            RichComponentValidation.common(nodeId, fallbackText, narration);
            java.util.Objects.requireNonNull(recipe, "recipe");
            originInvocationId = RichComponentValidation.requireOrigin(originInvocationId);
            if (requestedCrafts <= 0 || maximumCrafts < 0) {
                throw new IllegalArgumentException("craftability counts are invalid");
            }

        this.nodeId = nodeId;
        this.recipe = recipe;
        this.originInvocationId = originInvocationId;
        this.craftable = craftable;
        this.conclusive = conclusive;
        this.requestedCrafts = requestedCrafts;
        this.maximumCrafts = maximumCrafts;
        this.fallbackText = fallbackText;
        this.narration = narration;
    }
    public String nodeId() { return nodeId; }
    public RecipeReference recipe() { return recipe; }
    public String originInvocationId() { return originInvocationId; }
    public boolean craftable() { return craftable; }
    public boolean conclusive() { return conclusive; }
    public long requestedCrafts() { return requestedCrafts; }
    public long maximumCrafts() { return maximumCrafts; }
    public String fallbackText() { return fallbackText; }
    public String narration() { return narration; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CraftabilitySummary)) return false;
        CraftabilitySummary that = (CraftabilitySummary) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(recipe, that.recipe) && java.util.Objects.equals(originInvocationId, that.originInvocationId) && craftable == that.craftable && conclusive == that.conclusive && requestedCrafts == that.requestedCrafts && maximumCrafts == that.maximumCrafts && java.util.Objects.equals(fallbackText, that.fallbackText) && java.util.Objects.equals(narration, that.narration);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(recipe);
        hash = 31 * hash + java.util.Objects.hashCode(originInvocationId);
        hash = 31 * hash + Boolean.hashCode(craftable);
        hash = 31 * hash + Boolean.hashCode(conclusive);
        hash = 31 * hash + Long.hashCode(requestedCrafts);
        hash = 31 * hash + Long.hashCode(maximumCrafts);
        hash = 31 * hash + java.util.Objects.hashCode(fallbackText);
        hash = 31 * hash + java.util.Objects.hashCode(narration);
        return hash;
    }
    @Override public String toString() { return "CraftabilitySummary[nodeId=" + nodeId + ", recipe=" + recipe + ", originInvocationId=" + originInvocationId + ", craftable=" + craftable + ", conclusive=" + conclusive + ", requestedCrafts=" + requestedCrafts + ", maximumCrafts=" + maximumCrafts + ", fallbackText=" + fallbackText + ", narration=" + narration + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CraftabilitySummary> schema() {
            return new dev.openallay.value.ValueSchema<>(CraftabilitySummary.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CraftabilitySummary>>asList(new dev.openallay.value.ValueSchema.Component<>(CraftabilitySummary.class, "nodeId", CraftabilitySummary::nodeId), new dev.openallay.value.ValueSchema.Component<>(CraftabilitySummary.class, "recipe", CraftabilitySummary::recipe), new dev.openallay.value.ValueSchema.Component<>(CraftabilitySummary.class, "originInvocationId", CraftabilitySummary::originInvocationId), new dev.openallay.value.ValueSchema.Component<>(CraftabilitySummary.class, "craftable", CraftabilitySummary::craftable), new dev.openallay.value.ValueSchema.Component<>(CraftabilitySummary.class, "conclusive", CraftabilitySummary::conclusive), new dev.openallay.value.ValueSchema.Component<>(CraftabilitySummary.class, "requestedCrafts", CraftabilitySummary::requestedCrafts), new dev.openallay.value.ValueSchema.Component<>(CraftabilitySummary.class, "maximumCrafts", CraftabilitySummary::maximumCrafts), new dev.openallay.value.ValueSchema.Component<>(CraftabilitySummary.class, "fallbackText", CraftabilitySummary::fallbackText), new dev.openallay.value.ValueSchema.Component<>(CraftabilitySummary.class, "narration", CraftabilitySummary::narration)), arguments -> new CraftabilitySummary((String) arguments[0], (RecipeReference) arguments[1], (String) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4], (Long) arguments[5], (Long) arguments[6], (String) arguments[7], (String) arguments[8]));
        }
    }
}

    @dev.openallay.value.ValueType(Step.ValueSchemaProvider.class)
public static final class Step {
    private final String id;
    private final String label;
    private final StepState state;
    public Step(String id, String label, StepState state) {

            id = RichComponentValidation.requireLocalId(id);
            label = RichComponentValidation.requireText(label, "step label");
            RichComponentValidation.rejectActionText(label);
            java.util.Objects.requireNonNull(state, "state");

        this.id = id;
        this.label = label;
        this.state = state;
    }
    public String id() { return id; }
    public String label() { return label; }
    public StepState state() { return state; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Step)) return false;
        Step that = (Step) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(label, that.label) && java.util.Objects.equals(state, that.state);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(label);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        return hash;
    }
    @Override public String toString() { return "Step[id=" + id + ", label=" + label + ", state=" + state + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Step> schema() {
            return new dev.openallay.value.ValueSchema<>(Step.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Step>>asList(new dev.openallay.value.ValueSchema.Component<>(Step.class, "id", Step::id), new dev.openallay.value.ValueSchema.Component<>(Step.class, "label", Step::label), new dev.openallay.value.ValueSchema.Component<>(Step.class, "state", Step::state)), arguments -> new Step((String) arguments[0], (String) arguments[1], (StepState) arguments[2]));
        }
    }
}

    enum StepState { PENDING, ACTIVE, COMPLETE, FAILED }

    @dev.openallay.value.ValueType(ProgressSteps.ValueSchemaProvider.class)
public static final class ProgressSteps implements RichComponent {
    private final String nodeId;
    private final List<Step> steps;
    private final String fallbackText;
    private final String narration;
    public ProgressSteps(String nodeId, List<Step> steps, String fallbackText, String narration) {

            RichComponentValidation.common(nodeId, fallbackText, narration);
            steps = dev.openallay.util.Java8Collections.listCopyOf(steps);
            if (steps.isEmpty() || steps.stream().map(Step::id).distinct().count() != steps.size()) {
                throw new IllegalArgumentException("progress steps are empty or duplicated");
            }

        this.nodeId = nodeId;
        this.steps = steps;
        this.fallbackText = fallbackText;
        this.narration = narration;
    }
    public String nodeId() { return nodeId; }
    public List<Step> steps() { return steps; }
    public String fallbackText() { return fallbackText; }
    public String narration() { return narration; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProgressSteps)) return false;
        ProgressSteps that = (ProgressSteps) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(steps, that.steps) && java.util.Objects.equals(fallbackText, that.fallbackText) && java.util.Objects.equals(narration, that.narration);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(steps);
        hash = 31 * hash + java.util.Objects.hashCode(fallbackText);
        hash = 31 * hash + java.util.Objects.hashCode(narration);
        return hash;
    }
    @Override public String toString() { return "ProgressSteps[nodeId=" + nodeId + ", steps=" + steps + ", fallbackText=" + fallbackText + ", narration=" + narration + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ProgressSteps> schema() {
            return new dev.openallay.value.ValueSchema<>(ProgressSteps.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ProgressSteps>>asList(new dev.openallay.value.ValueSchema.Component<>(ProgressSteps.class, "nodeId", ProgressSteps::nodeId), new dev.openallay.value.ValueSchema.Component<>(ProgressSteps.class, "steps", ProgressSteps::steps), new dev.openallay.value.ValueSchema.Component<>(ProgressSteps.class, "fallbackText", ProgressSteps::fallbackText), new dev.openallay.value.ValueSchema.Component<>(ProgressSteps.class, "narration", ProgressSteps::narration)), arguments -> new ProgressSteps((String) arguments[0], (List) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(Source.ValueSchemaProvider.class)
public static final class Source {
    private final String sourceId;
    private final String label;
    private final String originInvocationId;
    public Source(String sourceId, String label, String originInvocationId) {

            if (!SemanticReferenceValidator.isResourceId(sourceId)) {
                throw new IllegalArgumentException("source ID is invalid");
            }
            label = RichComponentValidation.safeLabel(label);
            originInvocationId = RichComponentValidation.requireOrigin(originInvocationId);

        this.sourceId = sourceId;
        this.label = label;
        this.originInvocationId = originInvocationId;
    }
    public String sourceId() { return sourceId; }
    public String label() { return label; }
    public String originInvocationId() { return originInvocationId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Source)) return false;
        Source that = (Source) other;
        return java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(label, that.label) && java.util.Objects.equals(originInvocationId, that.originInvocationId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(label);
        hash = 31 * hash + java.util.Objects.hashCode(originInvocationId);
        return hash;
    }
    @Override public String toString() { return "Source[sourceId=" + sourceId + ", label=" + label + ", originInvocationId=" + originInvocationId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Source> schema() {
            return new dev.openallay.value.ValueSchema<>(Source.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Source>>asList(new dev.openallay.value.ValueSchema.Component<>(Source.class, "sourceId", Source::sourceId), new dev.openallay.value.ValueSchema.Component<>(Source.class, "label", Source::label), new dev.openallay.value.ValueSchema.Component<>(Source.class, "originInvocationId", Source::originInvocationId)), arguments -> new Source((String) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(SourceSummary.ValueSchemaProvider.class)
public static final class SourceSummary implements RichComponent {
    private final String nodeId;
    private final List<Source> sources;
    private final String fallbackText;
    private final String narration;
    public SourceSummary(String nodeId, List<Source> sources, String fallbackText, String narration) {

            RichComponentValidation.common(nodeId, fallbackText, narration);
            sources = dev.openallay.util.Java8Collections.listCopyOf(sources);
            if (sources.isEmpty()) throw new IllegalArgumentException("source summary is empty");

        this.nodeId = nodeId;
        this.sources = sources;
        this.fallbackText = fallbackText;
        this.narration = narration;
    }
    public String nodeId() { return nodeId; }
    public List<Source> sources() { return sources; }
    public String fallbackText() { return fallbackText; }
    public String narration() { return narration; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SourceSummary)) return false;
        SourceSummary that = (SourceSummary) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(sources, that.sources) && java.util.Objects.equals(fallbackText, that.fallbackText) && java.util.Objects.equals(narration, that.narration);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        hash = 31 * hash + java.util.Objects.hashCode(fallbackText);
        hash = 31 * hash + java.util.Objects.hashCode(narration);
        return hash;
    }
    @Override public String toString() { return "SourceSummary[nodeId=" + nodeId + ", sources=" + sources + ", fallbackText=" + fallbackText + ", narration=" + narration + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SourceSummary> schema() {
            return new dev.openallay.value.ValueSchema<>(SourceSummary.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SourceSummary>>asList(new dev.openallay.value.ValueSchema.Component<>(SourceSummary.class, "nodeId", SourceSummary::nodeId), new dev.openallay.value.ValueSchema.Component<>(SourceSummary.class, "sources", SourceSummary::sources), new dev.openallay.value.ValueSchema.Component<>(SourceSummary.class, "fallbackText", SourceSummary::fallbackText), new dev.openallay.value.ValueSchema.Component<>(SourceSummary.class, "narration", SourceSummary::narration)), arguments -> new SourceSummary((String) arguments[0], (List) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}

    enum BadgeState { INFO, SUCCESS, WARNING, ERROR }

    @dev.openallay.value.ValueType(StatusBadge.ValueSchemaProvider.class)
public static final class StatusBadge implements RichComponent {
    private final String nodeId;
    private final BadgeState state;
    private final String label;
    private final String fallbackText;
    private final String narration;
    public StatusBadge(String nodeId, BadgeState state, String label, String fallbackText, String narration) {

            RichComponentValidation.common(nodeId, fallbackText, narration);
            java.util.Objects.requireNonNull(state, "state");
            label = RichComponentValidation.requireText(label, "badge label");
            RichComponentValidation.rejectActionText(label);

        this.nodeId = nodeId;
        this.state = state;
        this.label = label;
        this.fallbackText = fallbackText;
        this.narration = narration;
    }
    public String nodeId() { return nodeId; }
    public BadgeState state() { return state; }
    public String label() { return label; }
    public String fallbackText() { return fallbackText; }
    public String narration() { return narration; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StatusBadge)) return false;
        StatusBadge that = (StatusBadge) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(label, that.label) && java.util.Objects.equals(fallbackText, that.fallbackText) && java.util.Objects.equals(narration, that.narration);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(label);
        hash = 31 * hash + java.util.Objects.hashCode(fallbackText);
        hash = 31 * hash + java.util.Objects.hashCode(narration);
        return hash;
    }
    @Override public String toString() { return "StatusBadge[nodeId=" + nodeId + ", state=" + state + ", label=" + label + ", fallbackText=" + fallbackText + ", narration=" + narration + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<StatusBadge> schema() {
            return new dev.openallay.value.ValueSchema<>(StatusBadge.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<StatusBadge>>asList(new dev.openallay.value.ValueSchema.Component<>(StatusBadge.class, "nodeId", StatusBadge::nodeId), new dev.openallay.value.ValueSchema.Component<>(StatusBadge.class, "state", StatusBadge::state), new dev.openallay.value.ValueSchema.Component<>(StatusBadge.class, "label", StatusBadge::label), new dev.openallay.value.ValueSchema.Component<>(StatusBadge.class, "fallbackText", StatusBadge::fallbackText), new dev.openallay.value.ValueSchema.Component<>(StatusBadge.class, "narration", StatusBadge::narration)), arguments -> new StatusBadge((String) arguments[0], (BadgeState) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4]));
        }
    }
}

    @dev.openallay.value.ValueType(Choice.ValueSchemaProvider.class)
public static final class Choice {
    private final String id;
    private final String label;
    public Choice(String id, String label) {

            id = RichComponentValidation.requireLocalId(id);
            label = RichComponentValidation.requireText(label, "choice label");
            RichComponentValidation.rejectActionText(label);

        this.id = id;
        this.label = label;
    }
    public String id() { return id; }
    public String label() { return label; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Choice)) return false;
        Choice that = (Choice) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(label, that.label);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(label);
        return hash;
    }
    @Override public String toString() { return "Choice[id=" + id + ", label=" + label + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Choice> schema() {
            return new dev.openallay.value.ValueSchema<>(Choice.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Choice>>asList(new dev.openallay.value.ValueSchema.Component<>(Choice.class, "id", Choice::id), new dev.openallay.value.ValueSchema.Component<>(Choice.class, "label", Choice::label)), arguments -> new Choice((String) arguments[0], (String) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(ChoiceGroup.ValueSchemaProvider.class)
public static final class ChoiceGroup implements RichComponent {
    private final String nodeId;
    private final String prompt;
    private final List<Choice> choices;
    private final String fallbackText;
    private final String narration;
    public ChoiceGroup(String nodeId, String prompt, List<Choice> choices, String fallbackText, String narration) {

            RichComponentValidation.common(nodeId, fallbackText, narration);
            prompt = RichComponentValidation.requireText(prompt, "choice prompt");
            RichComponentValidation.rejectActionText(prompt);
            choices = dev.openallay.util.Java8Collections.listCopyOf(choices);
            if (choices.isEmpty()
                    || choices.stream().map(Choice::id).distinct().count() != choices.size()) {
                throw new IllegalArgumentException("choices are empty or duplicated");
            }

        this.nodeId = nodeId;
        this.prompt = prompt;
        this.choices = choices;
        this.fallbackText = fallbackText;
        this.narration = narration;
    }
    public String nodeId() { return nodeId; }
    public String prompt() { return prompt; }
    public List<Choice> choices() { return choices; }
    public String fallbackText() { return fallbackText; }
    public String narration() { return narration; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChoiceGroup)) return false;
        ChoiceGroup that = (ChoiceGroup) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(prompt, that.prompt) && java.util.Objects.equals(choices, that.choices) && java.util.Objects.equals(fallbackText, that.fallbackText) && java.util.Objects.equals(narration, that.narration);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(prompt);
        hash = 31 * hash + java.util.Objects.hashCode(choices);
        hash = 31 * hash + java.util.Objects.hashCode(fallbackText);
        hash = 31 * hash + java.util.Objects.hashCode(narration);
        return hash;
    }
    @Override public String toString() { return "ChoiceGroup[nodeId=" + nodeId + ", prompt=" + prompt + ", choices=" + choices + ", fallbackText=" + fallbackText + ", narration=" + narration + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ChoiceGroup> schema() {
            return new dev.openallay.value.ValueSchema<>(ChoiceGroup.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ChoiceGroup>>asList(new dev.openallay.value.ValueSchema.Component<>(ChoiceGroup.class, "nodeId", ChoiceGroup::nodeId), new dev.openallay.value.ValueSchema.Component<>(ChoiceGroup.class, "prompt", ChoiceGroup::prompt), new dev.openallay.value.ValueSchema.Component<>(ChoiceGroup.class, "choices", ChoiceGroup::choices), new dev.openallay.value.ValueSchema.Component<>(ChoiceGroup.class, "fallbackText", ChoiceGroup::fallbackText), new dev.openallay.value.ValueSchema.Component<>(ChoiceGroup.class, "narration", ChoiceGroup::narration)), arguments -> new ChoiceGroup((String) arguments[0], (String) arguments[1], (List) arguments[2], (String) arguments[3], (String) arguments[4]));
        }
    }
}












}

/** Package-private Java8 helpers for the canonical variant constructors. */
final class RichComponentValidation {
    private RichComponentValidation() {}
static void common(String nodeId, String fallback, String narration) {
        SemanticIds.require(nodeId);
        requireText(fallback, "component fallback");
        requireText(narration, "component narration");
    }

static String safeLabel(String value) {
        if (value == null) return "";
        String label = dev.openallay.util.Java8Strings.strip(value);
        rejectActionText(label);
        return label;
    }

static String requireText(String value, String label) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

static String requireOrigin(String value) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException("component reference origin is required");
        }
        return value;
    }

static String requireLocalId(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("component-local ID is invalid");
        }
        return value;
    }

static void rejectActionText(String value) {
        String lowered = value.toLowerCase(java.util.Locale.ROOT);
        if (lowered.contains("://") || lowered.startsWith("javascript:")
                || lowered.startsWith("file:")) {
            throw new IllegalArgumentException("component display text cannot name a URL");
        }
    }
}
