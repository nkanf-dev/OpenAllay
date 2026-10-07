package dev.openallay.guide.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compact player facts only. Complete typed results and sources remain in Tool detail. */
public final class GuideToolSummaryPresenter {
    public static final int MAX_CAPSULES = 3;

    @dev.openallay.value.ValueType(Summary.ValueSchemaProvider.class)
public static final class Summary {
    private final String id;
    private final String title;
    private final String titleKey;
    private final String description;
    private final GuideToolDisplayStatus status;
    private final List<Capsule> capsules;
    public Summary(String id, String title, String titleKey, String description, GuideToolDisplayStatus status, List<Capsule> capsules) {

            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(titleKey, "titleKey");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(status, "status");
            capsules = dev.openallay.util.Java8Collections.listCopyOf(capsules);

        this.id = id;
        this.title = title;
        this.titleKey = titleKey;
        this.description = description;
        this.status = status;
        this.capsules = capsules;
    }
    public String id() { return id; }
    public String title() { return title; }
    public String titleKey() { return titleKey; }
    public String description() { return description; }
    public GuideToolDisplayStatus status() { return status; }
    public List<Capsule> capsules() { return capsules; }
public boolean hasDescription() { return !dev.openallay.util.Java8Strings.isBlank(description); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Summary)) return false;
        Summary that = (Summary) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(title, that.title) && java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(capsules, that.capsules);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(capsules);
        return hash;
    }
    @Override public String toString() { return "Summary[id=" + id + ", title=" + title + ", titleKey=" + titleKey + ", description=" + description + ", status=" + status + ", capsules=" + capsules + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Summary> schema() {
            return new dev.openallay.value.ValueSchema<>(Summary.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Summary>>asList(new dev.openallay.value.ValueSchema.Component<>(Summary.class, "id", Summary::id), new dev.openallay.value.ValueSchema.Component<>(Summary.class, "title", Summary::title), new dev.openallay.value.ValueSchema.Component<>(Summary.class, "titleKey", Summary::titleKey), new dev.openallay.value.ValueSchema.Component<>(Summary.class, "description", Summary::description), new dev.openallay.value.ValueSchema.Component<>(Summary.class, "status", Summary::status), new dev.openallay.value.ValueSchema.Component<>(Summary.class, "capsules", Summary::capsules)), arguments -> new Summary((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (GuideToolDisplayStatus) arguments[4], (List) arguments[5]));
        }
    }
}

    public sealed interface Capsule permits Item, Recipe {
        String id();
        String originInvocationId();
        GuideItemView item();
    }
    @dev.openallay.value.ValueType(Item.ValueSchemaProvider.class)
public static final class Item implements Capsule {
    private final String id;
    private final String originInvocationId;
    private final GuideItemView item;
    public Item(String id, String originInvocationId, GuideItemView item) {
        this.id = id;
        this.originInvocationId = originInvocationId;
        this.item = item;
    }
    public String id() { return id; }
    public String originInvocationId() { return originInvocationId; }
    public GuideItemView item() { return item; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Item)) return false;
        Item that = (Item) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(originInvocationId, that.originInvocationId) && java.util.Objects.equals(item, that.item);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(originInvocationId);
        hash = 31 * hash + java.util.Objects.hashCode(item);
        return hash;
    }
    @Override public String toString() { return "Item[id=" + id + ", originInvocationId=" + originInvocationId + ", item=" + item + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Item> schema() {
            return new dev.openallay.value.ValueSchema<>(Item.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Item>>asList(new dev.openallay.value.ValueSchema.Component<>(Item.class, "id", Item::id), new dev.openallay.value.ValueSchema.Component<>(Item.class, "originInvocationId", Item::originInvocationId), new dev.openallay.value.ValueSchema.Component<>(Item.class, "item", Item::item)), arguments -> new Item((String) arguments[0], (String) arguments[1], (GuideItemView) arguments[2]));
        }
    }
}
    @dev.openallay.value.ValueType(Recipe.ValueSchemaProvider.class)
public static final class Recipe implements Capsule {
    private final String id;
    private final String originInvocationId;
    private final GuideItemView item;
    private final GuideRecipeCard recipe;
    public Recipe(String id, String originInvocationId, GuideItemView item, GuideRecipeCard recipe) {
        this.id = id;
        this.originInvocationId = originInvocationId;
        this.item = item;
        this.recipe = recipe;
    }
    public String id() { return id; }
    public String originInvocationId() { return originInvocationId; }
    public GuideItemView item() { return item; }
    public GuideRecipeCard recipe() { return recipe; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Recipe)) return false;
        Recipe that = (Recipe) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(originInvocationId, that.originInvocationId) && java.util.Objects.equals(item, that.item) && java.util.Objects.equals(recipe, that.recipe);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(originInvocationId);
        hash = 31 * hash + java.util.Objects.hashCode(item);
        hash = 31 * hash + java.util.Objects.hashCode(recipe);
        return hash;
    }
    @Override public String toString() { return "Recipe[id=" + id + ", originInvocationId=" + originInvocationId + ", item=" + item + ", recipe=" + recipe + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Recipe> schema() {
            return new dev.openallay.value.ValueSchema<>(Recipe.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Recipe>>asList(new dev.openallay.value.ValueSchema.Component<>(Recipe.class, "id", Recipe::id), new dev.openallay.value.ValueSchema.Component<>(Recipe.class, "originInvocationId", Recipe::originInvocationId), new dev.openallay.value.ValueSchema.Component<>(Recipe.class, "item", Recipe::item), new dev.openallay.value.ValueSchema.Component<>(Recipe.class, "recipe", Recipe::recipe)), arguments -> new Recipe((String) arguments[0], (String) arguments[1], (GuideItemView) arguments[2], (GuideRecipeCard) arguments[3]));
        }
    }
}

    private GuideToolSummaryPresenter() {}

    public static Summary project(GuideUiRow.Tool tool) {
        Objects.requireNonNull(tool, "tool");
        String origin = tool.activity().invocationId();
        String id = "tool:" + tool.requestId() + ":" + origin;
        GuideToolDetailView detail = tool.detail();
        List<Capsule> capsules = new ArrayList<>();
        // Never infer a result capsule from narration, arbitrary fields, or a private JSON envelope.
        if (detail.failure().isEmpty() && detail.displayStatus() == GuideToolDisplayStatus.SUCCEEDED) {
            for (int cardIndex = 0; cardIndex < detail.cards().size() && capsules.size() < MAX_CAPSULES; cardIndex++) {
                GuideDetailCard card = detail.cards().get(cardIndex);
                String cardId = id + ":card:" + cardIndex;
                Objects.requireNonNull(card);
                {
final java.lang.Object $oaPattern0_value = card;
final boolean $oaPattern0_match = $oaPattern0_value instanceof GuideDetailCard.ItemGrid;
GuideDetailCard.ItemGrid $oaPattern0_bound = $oaPattern0_match ? (GuideDetailCard.ItemGrid) $oaPattern0_value : null;
if ($oaPattern0_match) {
                    for (int itemIndex = 0; itemIndex < $oaPattern0_bound.items().size() && capsules.size() < MAX_CAPSULES; itemIndex++) {
                        capsules.add(new Item(cardId + ":item:" + itemIndex, origin, $oaPattern0_bound.items().get(itemIndex)));
                    }
                } else {
final java.lang.Object $oaPattern1_value = card;
final boolean $oaPattern1_match = $oaPattern1_value instanceof GuideDetailCard.Recipe;
GuideDetailCard.Recipe $oaPattern1_bound = $oaPattern1_match ? (GuideDetailCard.Recipe) $oaPattern1_value : null;
if ($oaPattern1_match) {
                    GuideRecipeCard recipe = $oaPattern1_bound.recipe();
                    if (!recipe.outputs().isEmpty()) {
                        GuideRecipeCard.Output output = recipe.outputs().get(0);
                        capsules.add(new Recipe(cardId + ":recipe", origin,
                                new GuideItemView(output.itemId(), output.displayName(), output.count()), recipe));
                    }
                } else {
                    /* Other complete card families belong only in the detail drawer. */
                }
}
}
            }
        }
        return new Summary(id, detail.intent().title(), detail.titleKey(), detail.intent().description(),
                detail.displayStatus(), capsules);
    }
}
