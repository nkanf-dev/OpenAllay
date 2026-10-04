package dev.openallay.guide.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compact player facts only. Complete typed results and sources remain in Tool detail. */
public final class GuideToolSummaryPresenter {
    public static final int MAX_CAPSULES = 3;

    public record Summary(String id, String title, String titleKey, String description,
            GuideToolDisplayStatus status, List<Capsule> capsules) {
        public Summary {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(titleKey, "titleKey");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(status, "status");
            capsules = List.copyOf(capsules);
        }
        public boolean hasDescription() { return !description.isBlank(); }
    }

    public sealed interface Capsule permits Item, Recipe {
        String id();
        String originInvocationId();
        GuideItemView item();
    }
    public record Item(String id, String originInvocationId, GuideItemView item) implements Capsule {}
    public record Recipe(String id, String originInvocationId, GuideItemView item,
            GuideRecipeCard recipe) implements Capsule {}

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
                switch (card) {
                    case GuideDetailCard.ItemGrid grid -> {
                        for (int itemIndex = 0; itemIndex < grid.items().size() && capsules.size() < MAX_CAPSULES; itemIndex++) {
                            capsules.add(new Item(cardId + ":item:" + itemIndex, origin, grid.items().get(itemIndex)));
                        }
                    }
                    case GuideDetailCard.Recipe value -> {
                        GuideRecipeCard recipe = value.recipe();
                        if (!recipe.outputs().isEmpty()) {
                            GuideRecipeCard.Output output = recipe.outputs().getFirst();
                            capsules.add(new Recipe(cardId + ":recipe", origin,
                                    new GuideItemView(output.itemId(), output.displayName(), output.count()), recipe));
                        }
                    }
                    default -> { /* Other complete card families belong only in the detail drawer. */ }
                }
            }
        }
        return new Summary(id, detail.intent().title(), detail.titleKey(), detail.intent().description(),
                detail.displayStatus(), capsules);
    }
}
