package dev.openallay.guide.ui;

import java.util.ArrayList;
import java.util.List;

/** Complete stored recipe facts below the bounded native canvas. No preview values are inferred. */
public final class GuideRecipeDetailFacts {
    @dev.openallay.value.ValueType(Line.ValueSchemaProvider.class)
public static final class Line {
    private final String key;
    private final List<String> arguments;
    public Line(String key, List<String> arguments) {
 arguments = dev.openallay.util.Java8Collections.listCopyOf(arguments);
        this.key = key;
        this.arguments = arguments;
    }
    public String key() { return key; }
    public List<String> arguments() { return arguments; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Line)) return false;
        Line that = (Line) other;
        return java.util.Objects.equals(key, that.key) && java.util.Objects.equals(arguments, that.arguments);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + java.util.Objects.hashCode(arguments);
        return hash;
    }
    @Override public String toString() { return "Line[key=" + key + ", arguments=" + arguments + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Line> schema() {
            return new dev.openallay.value.ValueSchema<>(Line.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Line>>asList(new dev.openallay.value.ValueSchema.Component<>(Line.class, "key", Line::key), new dev.openallay.value.ValueSchema.Component<>(Line.class, "arguments", Line::arguments)), arguments -> new Line((String) arguments[0], (List) arguments[1]));
        }
    }
}
    private GuideRecipeDetailFacts() {}

    public static List<Line> project(GuideRecipeCard recipe) {
        List<Line> lines = new ArrayList<>();
        lines.add(line("screen.openallay.recipe.identity", recipe.id(), recipe.type()));
        if (!dev.openallay.util.Java8Strings.isBlank(recipe.workstation())) lines.add(line("screen.openallay.recipe.workstation", recipe.workstation()));
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
        return dev.openallay.util.Java8Collections.listCopyOf(lines);
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
        return new Line(key, dev.openallay.util.Java8Collections.toList(java.util.Arrays.stream(arguments).map(String::valueOf)));
    }
}
