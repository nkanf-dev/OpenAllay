package dev.openallay.guide.ui;

import java.util.ArrayList;
import java.util.List;

/** Complete stored recipe facts below the bounded native canvas. No preview values are inferred. */
public final class GuideRecipeDetailFacts {
    public record Line(String key, List<String> arguments) {
        public Line { arguments = List.copyOf(arguments); }
    }
    private GuideRecipeDetailFacts() {}

    public static List<Line> project(GuideRecipeCard recipe) {
        List<Line> lines = new ArrayList<>();
        lines.add(line("screen.openallay.recipe.identity", recipe.id(), recipe.type()));
        if (!recipe.workstation().isBlank()) lines.add(line("screen.openallay.recipe.workstation", recipe.workstation()));
        for (GuideRecipeCard.Output output : recipe.outputs()) {
            lines.add(line("screen.openallay.recipe.output", output.displayName(), output.itemId(), output.count()));
        }
        for (GuideRecipeCard.Ingredient input : recipe.ingredients()) ingredient(lines, input);
        for (GuideRecipeCard.Ingredient catalyst : recipe.catalysts()) ingredient(lines, catalyst);
        for (GuideRecipeCard.Output output : recipe.byproducts()) {
            lines.add(line("screen.openallay.recipe.byproduct", output.displayName(), output.itemId(), output.count()));
        }
        GuideRecipeCard.Processing processing = recipe.processing();
        if (processing.durationTicks() != null) lines.add(line("screen.openallay.native.recipe.duration", processing.durationTicks()));
        if (processing.energy() != null) lines.add(line("screen.openallay.native.recipe.energy", processing.energy()));
        if (processing.temperature() != null) lines.add(line("screen.openallay.native.recipe.temperature", processing.temperature()));
        return List.copyOf(lines);
    }
    private static void ingredient(List<Line> lines, GuideRecipeCard.Ingredient ingredient) {
        lines.add(line(ingredient.consumed() ? "screen.openallay.recipe.ingredient"
                : "screen.openallay.recipe.catalyst", ingredient.key(), ingredient.count()));
        for (GuideRecipeCard.Alternative alternative : ingredient.alternatives()) {
            lines.add(line("screen.openallay.recipe.alternative", alternative.kind(), alternative.id()));
            for (String item : alternative.resolvedItems()) lines.add(line("screen.openallay.recipe.resolved_item", item));
        }
    }
    private static Line line(String key, Object... arguments) {
        return new Line(key, java.util.Arrays.stream(arguments).map(String::valueOf).toList());
    }
}
