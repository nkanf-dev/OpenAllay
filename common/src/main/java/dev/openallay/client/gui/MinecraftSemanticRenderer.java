package dev.openallay.client.gui;

import dev.openallay.context.RecipeReference;
import dev.openallay.guide.semantic.RecipeSemanticHandle;
import dev.openallay.guide.semantic.RichComponent;
import dev.openallay.guide.semantic.SemanticBlock;
import dev.openallay.guide.semantic.SemanticReference;
import dev.openallay.guide.semantic.SemanticReferenceKind;
import dev.openallay.guide.ui.GuideUiLayout;
import dev.openallay.guide.ui.SemanticLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Native renderer for safe semantic layouts. It emits typed intents, never callbacks from text. */
public final class MinecraftSemanticRenderer {
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFFA9B3BE;
    private static final int ACCENT = 0xFF72D5C4;
    private static final int PANEL = 0xA0242933;
    private static final int SUCCESS = 0xFF7FC8A9;
    private static final int ERROR = 0xFFFF7D7D;

    public sealed interface Intent permits Intent.BrowseRecipes, Intent.BrowseUsages,
            Intent.ExactRecipe, Intent.Source, Intent.Evidence, Intent.Choice {
        @dev.openallay.value.ValueType(BrowseRecipes.ValueSchemaProvider.class)
public static final class BrowseRecipes implements Intent {
    private final String itemId;
    public BrowseRecipes(String itemId) {
        this.itemId = itemId;
    }
    public String itemId() { return itemId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BrowseRecipes)) return false;
        BrowseRecipes that = (BrowseRecipes) other;
        return java.util.Objects.equals(itemId, that.itemId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(itemId);
        return hash;
    }
    @Override public String toString() { return "BrowseRecipes[itemId=" + itemId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<BrowseRecipes> schema() {
            return new dev.openallay.value.ValueSchema<>(BrowseRecipes.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<BrowseRecipes>>asList(new dev.openallay.value.ValueSchema.Component<>(BrowseRecipes.class, "itemId", BrowseRecipes::itemId)), arguments -> new BrowseRecipes((String) arguments[0]));
        }
    }
}
        @dev.openallay.value.ValueType(BrowseUsages.ValueSchemaProvider.class)
public static final class BrowseUsages implements Intent {
    private final String itemId;
    public BrowseUsages(String itemId) {
        this.itemId = itemId;
    }
    public String itemId() { return itemId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BrowseUsages)) return false;
        BrowseUsages that = (BrowseUsages) other;
        return java.util.Objects.equals(itemId, that.itemId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(itemId);
        return hash;
    }
    @Override public String toString() { return "BrowseUsages[itemId=" + itemId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<BrowseUsages> schema() {
            return new dev.openallay.value.ValueSchema<>(BrowseUsages.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<BrowseUsages>>asList(new dev.openallay.value.ValueSchema.Component<>(BrowseUsages.class, "itemId", BrowseUsages::itemId)), arguments -> new BrowseUsages((String) arguments[0]));
        }
    }
}
        @dev.openallay.value.ValueType(ExactRecipe.ValueSchemaProvider.class)
public static final class ExactRecipe implements Intent {
    private final RecipeReference reference;
    public ExactRecipe(RecipeReference reference) {
        this.reference = reference;
    }
    public RecipeReference reference() { return reference; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExactRecipe)) return false;
        ExactRecipe that = (ExactRecipe) other;
        return java.util.Objects.equals(reference, that.reference);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        return hash;
    }
    @Override public String toString() { return "ExactRecipe[reference=" + reference + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ExactRecipe> schema() {
            return new dev.openallay.value.ValueSchema<>(ExactRecipe.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ExactRecipe>>asList(new dev.openallay.value.ValueSchema.Component<>(ExactRecipe.class, "reference", ExactRecipe::reference)), arguments -> new ExactRecipe((RecipeReference) arguments[0]));
        }
    }
}
        @dev.openallay.value.ValueType(Source.ValueSchemaProvider.class)
public static final class Source implements Intent {
    private final String sourceId;
    private final String originInvocationId;
    public Source(String sourceId, String originInvocationId) {
        this.sourceId = sourceId;
        this.originInvocationId = originInvocationId;
    }
    public String sourceId() { return sourceId; }
    public String originInvocationId() { return originInvocationId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Source)) return false;
        Source that = (Source) other;
        return java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(originInvocationId, that.originInvocationId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(originInvocationId);
        return hash;
    }
    @Override public String toString() { return "Source[sourceId=" + sourceId + ", originInvocationId=" + originInvocationId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Source> schema() {
            return new dev.openallay.value.ValueSchema<>(Source.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Source>>asList(new dev.openallay.value.ValueSchema.Component<>(Source.class, "sourceId", Source::sourceId), new dev.openallay.value.ValueSchema.Component<>(Source.class, "originInvocationId", Source::originInvocationId)), arguments -> new Source((String) arguments[0], (String) arguments[1]));
        }
    }
}
        @dev.openallay.value.ValueType(Evidence.ValueSchemaProvider.class)
public static final class Evidence implements Intent {
    private final String evidenceId;
    private final String originInvocationId;
    public Evidence(String evidenceId, String originInvocationId) {
        this.evidenceId = evidenceId;
        this.originInvocationId = originInvocationId;
    }
    public String evidenceId() { return evidenceId; }
    public String originInvocationId() { return originInvocationId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Evidence)) return false;
        Evidence that = (Evidence) other;
        return java.util.Objects.equals(evidenceId, that.evidenceId) && java.util.Objects.equals(originInvocationId, that.originInvocationId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(evidenceId);
        hash = 31 * hash + java.util.Objects.hashCode(originInvocationId);
        return hash;
    }
    @Override public String toString() { return "Evidence[evidenceId=" + evidenceId + ", originInvocationId=" + originInvocationId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Evidence> schema() {
            return new dev.openallay.value.ValueSchema<>(Evidence.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Evidence>>asList(new dev.openallay.value.ValueSchema.Component<>(Evidence.class, "evidenceId", Evidence::evidenceId), new dev.openallay.value.ValueSchema.Component<>(Evidence.class, "originInvocationId", Evidence::originInvocationId)), arguments -> new Evidence((String) arguments[0], (String) arguments[1]));
        }
    }
}
        @dev.openallay.value.ValueType(Choice.ValueSchemaProvider.class)
public static final class Choice implements Intent {
    private final String componentNodeId;
    private final String choiceId;
    public Choice(String componentNodeId, String choiceId) {
        this.componentNodeId = componentNodeId;
        this.choiceId = choiceId;
    }
    public String componentNodeId() { return componentNodeId; }
    public String choiceId() { return choiceId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Choice)) return false;
        Choice that = (Choice) other;
        return java.util.Objects.equals(componentNodeId, that.componentNodeId) && java.util.Objects.equals(choiceId, that.choiceId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(componentNodeId);
        hash = 31 * hash + java.util.Objects.hashCode(choiceId);
        return hash;
    }
    @Override public String toString() { return "Choice[componentNodeId=" + componentNodeId + ", choiceId=" + choiceId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Choice> schema() {
            return new dev.openallay.value.ValueSchema<>(Choice.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Choice>>asList(new dev.openallay.value.ValueSchema.Component<>(Choice.class, "componentNodeId", Choice::componentNodeId), new dev.openallay.value.ValueSchema.Component<>(Choice.class, "choiceId", Choice::choiceId)), arguments -> new Choice((String) arguments[0], (String) arguments[1]));
        }
    }
}
    }

    @dev.openallay.value.ValueType(Hit.ValueSchemaProvider.class)
public static final class Hit {
    private final GuideUiLayout.Rect bounds;
    private final Intent intent;
    public Hit(GuideUiLayout.Rect bounds, Intent intent) {
        this.bounds = bounds;
        this.intent = intent;
    }
    public GuideUiLayout.Rect bounds() { return bounds; }
    public Intent intent() { return intent; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Hit)) return false;
        Hit that = (Hit) other;
        return java.util.Objects.equals(bounds, that.bounds) && java.util.Objects.equals(intent, that.intent);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(bounds);
        hash = 31 * hash + java.util.Objects.hashCode(intent);
        return hash;
    }
    @Override public String toString() { return "Hit[bounds=" + bounds + ", intent=" + intent + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Hit> schema() {
            return new dev.openallay.value.ValueSchema<>(Hit.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Hit>>asList(new dev.openallay.value.ValueSchema.Component<>(Hit.class, "bounds", Hit::bounds), new dev.openallay.value.ValueSchema.Component<>(Hit.class, "intent", Hit::intent)), arguments -> new Hit((GuideUiLayout.Rect) arguments[0], (Intent) arguments[1]));
        }
    }
}
    @dev.openallay.value.ValueType(Result.ValueSchemaProvider.class)
public static final class Result {
    private final int bottom;
    private final List<Hit> hits;
    public Result(int bottom, List<Hit> hits) {
 hits = List.copyOf(hits);
        this.bottom = bottom;
        this.hits = hits;
    }
    public int bottom() { return bottom; }
    public List<Hit> hits() { return hits; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Result)) return false;
        Result that = (Result) other;
        return bottom == that.bottom && java.util.Objects.equals(hits, that.hits);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(bottom);
        hash = 31 * hash + java.util.Objects.hashCode(hits);
        return hash;
    }
    @Override public String toString() { return "Result[bottom=" + bottom + ", hits=" + hits + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Result> schema() {
            return new dev.openallay.value.ValueSchema<>(Result.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Result>>asList(new dev.openallay.value.ValueSchema.Component<>(Result.class, "bottom", Result::bottom), new dev.openallay.value.ValueSchema.Component<>(Result.class, "hits", Result::hits)), arguments -> new Result((Integer) arguments[0], (List) arguments[1]));
        }
    }
}

    @FunctionalInterface
    public interface RecipeGridRenderer {
        boolean render(
                GuideGraphics graphics,
                Font font,
                RichComponent.RecipeGrid component,
                GuideUiLayout.Rect bounds,
                int mouseX,
                int mouseY,
                long presentationTicks);
    }

    private static final RecipeGridRenderer NO_NATIVE_RECIPES =
            (graphics, font, component, bounds, mouseX, mouseY, ticks) -> false;

    private final MinecraftSemanticResolver resolver;

    public MinecraftSemanticRenderer(MinecraftSemanticResolver resolver) {
        this.resolver = java.util.Objects.requireNonNull(resolver, "resolver");
    }

    public Result render(
            GuideGraphics graphics,
            Font font,
            SemanticLayout layout,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY) {
        return render(graphics, font, layout, x, y, width, mouseX, mouseY, false, 0);
    }

    public Result render(
            GuideGraphics graphics,
            Font font,
            SemanticLayout layout,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY,
            boolean animationsEnabled,
            long presentationTicks) {
        return render(
                graphics, font, layout, x, y, width, mouseX, mouseY,
                animationsEnabled, presentationTicks, NO_NATIVE_RECIPES);
    }

    public Result render(
            GuideGraphics graphics,
            Font font,
            SemanticLayout layout,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY,
            boolean animationsEnabled,
            long presentationTicks,
            RecipeGridRenderer recipeGridRenderer) {
        java.util.Objects.requireNonNull(recipeGridRenderer, "recipeGridRenderer");
        ArrayList<Hit> hits = new ArrayList<>();
        int current = y;
        for (SemanticLayout.Line line : layout.lines()) {
            int lineTop = current;
            int left = x + line.indent();
            switch (line.kind()) {
                case RULE -> graphics.fill(left, current + line.height() / 2,
                        x + width, current + line.height() / 2 + 1, MUTED);
                case COMPONENT -> renderComponent(
                        graphics, font, line.component(), left, current,
                        Math.max(20, width - line.indent()), line.height(), mouseX, mouseY, hits,
                        animationsEnabled, presentationTicks, recipeGridRenderer);
                case TABLE -> renderTable(graphics, font, line.table(), left, current, hits);
                default -> {
                    if (line.kind() == SemanticLayout.Kind.CODE) {
                        graphics.fill(left - 2, current - 1, x + width, current + line.height(), PANEL);
                    } else if (line.kind() == SemanticLayout.Kind.QUOTE) {
                        graphics.fill(left - 4, current, left - 2, current + line.height(), ACCENT);
                    }
                    Component rendered = MinecraftComponents.empty();
                    int runX = left;
                    for (SemanticLayout.Run run : line.runs()) {
                        Component value = MinecraftComponents.literal(run.text());
                        value = switch (run.style()) {
                            case NORMAL -> value;
                            case EMPHASIS -> MinecraftComponents.style(value, ChatFormatting.ITALIC);
                            case STRONG -> MinecraftComponents.style(value, ChatFormatting.BOLD);
                            case CODE -> MinecraftComponents.style(value, ChatFormatting.GRAY);
                            case REFERENCE -> MinecraftComponents.style(value, ChatFormatting.AQUA, ChatFormatting.UNDERLINE);
                        };
                        MinecraftComponents.append(rendered, value);
                        int runWidth = GuideNativeFont.width(font, value);
                        Intent intent = intent(run.reference());
                        if (intent != null) {
                            hits.add(new Hit(
                                    new GuideUiLayout.Rect(runX, current - 1, runWidth, line.height()),
                                    intent));
                        }
                        runX += runWidth;
                    }
                    graphics.text(font, rendered, left, current,
                            line.kind() == SemanticLayout.Kind.HEADING ? ACCENT : TEXT, false);
                }
            }
            current = lineTop + line.height();
        }
        return new Result(current, hits);
    }

    private void renderTable(
            GuideGraphics graphics,
            Font font,
            SemanticLayout.TableBox table,
            int x,
            int y,
            List<Hit> hits) {
        if (table.mode() == SemanticLayout.TableBox.Mode.GRID) {
            graphics.fill(x, y, x + table.width(), y + table.height(), PANEL);
            for (SemanticLayout.TableRow row : table.rows()) {
                if (row.header()) {
                    graphics.fill(
                            x, y + row.y(), x + table.width(), y + row.y() + row.height(),
                            0xFF29443F);
                }
                for (SemanticLayout.TableCell cell : row.cells()) {
                    graphics.outline(
                            x + cell.x(), y + cell.y(), cell.width(), cell.height(),
                            0xFF46515F);
                    int available = Math.max(1, cell.width() - 9);
                    for (SemanticLayout.CellLine value : cell.valueLines()) {
                        int textX = x + cell.x() + 4 + alignedOffset(
                                cell.alignment(), available, value.width());
                        int textY = y + cell.y() + 3 + value.y();
                        renderTableRuns(
                                graphics, font, value.runs(), textX, textY,
                                row.header() ? ACCENT : TEXT, hits);
                    }
                }
            }
            return;
        }

        for (SemanticLayout.TableRow row : table.rows()) {
            graphics.fill(x, y + row.y(), x + table.width(), y + row.y() + row.height(), PANEL);
            graphics.outline(x, y + row.y(), table.width(), row.height(), 0xFF46515F);
            for (SemanticLayout.TableCell cell : row.cells()) {
                int labelY = y + cell.y();
                for (SemanticLayout.CellLine label : cell.labelLines()) {
                    renderTableRuns(
                            graphics, font, label.runs(), x + cell.x(), labelY + label.y(),
                            ACCENT, hits);
                }
                int valueY = labelY + cell.labelLines().size() * table.lineHeight();
                for (SemanticLayout.CellLine value : cell.valueLines()) {
                    int textX = x + cell.x() + alignedOffset(
                            cell.alignment(), cell.width(), value.width());
                    renderTableRuns(
                            graphics, font, value.runs(), textX, valueY + value.y(),
                            TEXT, hits);
                }
            }
        }
    }

    private static void renderTableRuns(
            GuideGraphics graphics,
            Font font,
            List<SemanticLayout.Run> runs,
            int x,
            int y,
            int color,
            List<Hit> hits) {
        int runX = x;
        for (SemanticLayout.Run run : runs) {
            Component rendered = MinecraftComponents.literal(run.text());
            rendered = switch (run.style()) {
                case NORMAL -> rendered;
                case EMPHASIS -> MinecraftComponents.style(rendered, ChatFormatting.ITALIC);
                case STRONG -> MinecraftComponents.style(rendered, ChatFormatting.BOLD);
                case CODE -> MinecraftComponents.style(rendered, ChatFormatting.GRAY);
                case REFERENCE -> MinecraftComponents.style(rendered,
                        ChatFormatting.AQUA, ChatFormatting.UNDERLINE);
            };
            graphics.text(font, rendered, runX, y, color, false);
            int runWidth = GuideNativeFont.width(font, rendered);
            Intent intent = intent(run.reference());
            if (intent != null) {
                hits.add(new Hit(new GuideUiLayout.Rect(runX, y - 1, runWidth, 10), intent));
            }
            runX += runWidth;
        }
    }

    static int alignedOffset(
            SemanticBlock.Alignment alignment, int availableWidth, int contentWidth) {
        int remaining = Math.max(0, availableWidth - contentWidth);
        return switch (alignment) {
            case RIGHT -> remaining;
            case CENTER -> remaining / 2;
            case NONE, LEFT -> 0;
        };
    }

    public static Intent intent(SemanticReference reference) {
        if (reference == null) return null;
        return switch (reference.kind()) {
            case ITEM, BLOCK -> new Intent.BrowseRecipes(reference.target());
            case RECIPE -> {
                if (!reference.grounded()) yield null;
                try {
                    yield new Intent.ExactRecipe(RecipeSemanticHandle.decode(reference.target()));
                } catch (IllegalArgumentException malformed) {
                    yield null;
                }
            }
            case SOURCE -> reference.grounded()
                    ? new Intent.Source(reference.target(), reference.originInvocationId()) : null;
            case EVIDENCE -> reference.grounded()
                    ? new Intent.Evidence(reference.target(), reference.originInvocationId()) : null;
            case FLUID, ENTITY, BIOME, DIMENSION, TAG, KEY -> null;
        };
    }

    private void renderComponent(
            GuideGraphics graphics,
            Font font,
            RichComponent component,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY,
            List<Hit> hits,
            boolean animationsEnabled,
            long presentationTicks,
            RecipeGridRenderer recipeGridRenderer) {
        graphics.fill(x - 2, y - 1, x + width, y + height, PANEL);
        java.util.Objects.requireNonNull(component);
        if (component instanceof RichComponent.ItemRow value) {
            int rowY = y;
            for (RichComponent.Item item : value.items()) {
                renderItem(graphics, font, item.itemId(), item.label(), item.count(),
                        x + 2, rowY, mouseX, mouseY);
                int actionX = Math.min(x + width - 54, x + 120);
                action(graphics, font, MinecraftComponents.translatable(
                                "screen.openallay.semantic.action.recipes"), actionX, rowY + 4,
                        new Intent.BrowseRecipes(item.itemId()), hits);
                action(graphics, font, MinecraftComponents.translatable(
                                "screen.openallay.semantic.action.usages"), actionX + 28, rowY + 4,
                        new Intent.BrowseUsages(item.itemId()), hits);
                rowY += 22;
            }
        } else if (component instanceof RichComponent.RecipeGrid value) {
            if (recipeGridRenderer.render(
                    graphics,
                    font,
                    value,
                    new GuideUiLayout.Rect(x - 2, y - 1, width + 2, height),
                    mouseX,
                    mouseY,
                    presentationTicks)) {
                action(graphics, font, MinecraftComponents.translatable(
                                "screen.openallay.semantic.action.open_recipe"),
                        x + 4, y + height - 11,
                        new Intent.ExactRecipe(value.recipe()), hits);
                return;
            }
            graphics.text(font, value.label().isBlank()
                            ? MinecraftComponents.translatable("screen.openallay.semantic.recipe")
                            : MinecraftComponents.literal(value.label()),
                    x + 4, y + 4, ACCENT, false);
            graphics.text(font, MinecraftComponents.translatable(
                            "screen.openallay.semantic.recipe_verified"),
                    x + 4, y + 18, MUTED, false);
            action(graphics, font, MinecraftComponents.translatable(
                            "screen.openallay.semantic.action.open_recipe"), x + 4, y + 34,
                    new Intent.ExactRecipe(value.recipe()), hits);
        } else if (component instanceof RichComponent.IngredientCheck value) {
            int rowY = y;
            for (RichComponent.Ingredient ingredient : value.ingredients()) {
                renderItem(graphics, font, ingredient.itemId(), ingredient.label(),
                        ingredient.required(), x + 2, rowY, mouseX, mouseY);
                String count = ingredient.available() + "/" + ingredient.required();
                graphics.text(font, count, x + width - GuideNativeFont.width(font, count) - 5, rowY + 5,
                        ingredient.available() >= ingredient.required() ? SUCCESS : ERROR, false);
                rowY += 22;
            }
        } else if (component instanceof RichComponent.CraftabilitySummary value) {
            graphics.text(font, MinecraftComponents.translatable(value.craftable()
                            ? "screen.openallay.semantic.craftable"
                            : "screen.openallay.semantic.not_craftable"),
                    x + 4, y + 4, value.craftable() ? SUCCESS : ERROR, false);
            graphics.text(font, MinecraftComponents.translatable(
                            "screen.openallay.semantic.maximum_crafts", value.maximumCrafts()),
                    x + 4, y + 16, TEXT, false);
            graphics.text(font, MinecraftComponents.translatable(value.conclusive()
                            ? "screen.openallay.semantic.conclusive"
                            : "screen.openallay.semantic.incomplete"),
                    x + 4, y + 28, MUTED, false);
        } else if (component instanceof RichComponent.ProgressSteps value) {
            int rowY = y + 2;
            for (RichComponent.Step step : value.steps()) {
                String marker = progressMarker(
                        step.state(), animationsEnabled, presentationTicks);
                graphics.text(font, marker + " " + step.label(), x + 4, rowY,
                        step.state() == RichComponent.StepState.FAILED ? ERROR : TEXT, false);
                rowY += 10;
            }
        } else if (component instanceof RichComponent.SourceSummary value) {
            int rowY = y + 2;
            for (RichComponent.Source source : value.sources()) {
                graphics.text(font, MinecraftComponents.translatable(
                                "screen.openallay.semantic.source", source.label()),
                        x + 4, rowY, ACCENT, false);
                hits.add(new Hit(new GuideUiLayout.Rect(x + 2, rowY - 1, width - 4, 11),
                        new Intent.Source(source.sourceId(), source.originInvocationId())));
                rowY += 10;
            }
        } else if (component instanceof RichComponent.StatusBadge value) {
            graphics.text(
                    font, value.label(), x + 5, y + 4,
                    switch (value.state()) {
                        case INFO -> ACCENT;
                        case SUCCESS -> SUCCESS;
                        case WARNING -> 0xFFFFD479;
                        case ERROR -> ERROR;
                    }, false);
        } else if (component instanceof RichComponent.ChoiceGroup value) {
            graphics.text(font, value.prompt(), x + 4, y + 2, TEXT, false);
            int rowY = y + 14;
            for (RichComponent.Choice choice : value.choices()) {
                action(graphics, font, MinecraftComponents.literal(choice.label()), x + 4, rowY,
                        new Intent.Choice(value.nodeId(), choice.id()), hits);
                rowY += 11;
            }
        }
    }

    static String progressMarker(
            RichComponent.StepState state,
            boolean animationsEnabled,
            long presentationTicks) {
        return switch (state) {
            case PENDING -> "○";
            case ACTIVE -> animationsEnabled && (presentationTicks / 8) % 2 == 0
                    ? "▷" : "▶";
            case COMPLETE -> "✓";
            case FAILED -> "!";
        };
    }

    private void renderItem(
            GuideGraphics graphics,
            Font font,
            String itemId,
            String label,
            long count,
            int x,
            int y,
            int mouseX,
            int mouseY) {
        MinecraftSemanticResolver.ItemPresentation item = resolver.item(itemId, label, count);
        ItemStack stack = item.stack();
        if (item.resolved()) {
            graphics.item(stack, x, y);
            graphics.itemDecorations(font, stack, x, y);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }
        } else {
            graphics.text(font, "?", x + 5, y + 4, MUTED, false);
        }
        graphics.text(font, item.label() + (count > 1 ? " ×" + count : ""),
                x + 20, y + 5, TEXT, false);
    }

    private static void action(
            GuideGraphics graphics,
            Font font,
            Component label,
            int x,
            int y,
            Intent intent,
            List<Hit> hits) {
        int width = GuideNativeFont.width(font, label) + 6;
        graphics.fill(x, y - 2, x + width, y + 9, 0xFF29443F);
        graphics.text(font, label, x + 3, y, ACCENT, false);
        hits.add(new Hit(new GuideUiLayout.Rect(x, y - 2, width, 11), intent));
    }

    private static int componentHeight(RichComponent component) {
        java.util.Objects.requireNonNull(component);
        if (component instanceof RichComponent.ItemRow value) {
            return Math.max(22, 22 * value.items().size());
        } else if (component instanceof RichComponent.RecipeGrid ignored) {
            return 136;
        } else if (component instanceof RichComponent.IngredientCheck value) {
            return Math.max(22, 22 * value.ingredients().size());
        } else if (component instanceof RichComponent.CraftabilitySummary ignored) {
            return 40;
        } else if (component instanceof RichComponent.ProgressSteps value) {
            return 12 * (value.steps().size() + 1);
        } else if (component instanceof RichComponent.SourceSummary value) {
            return 12 * (value.sources().size() + 1);
        } else if (component instanceof RichComponent.StatusBadge ignored) {
            return 16;
        } else if (component instanceof RichComponent.ChoiceGroup value) {
            return 12 * (value.choices().size() + 1);
        }
        throw new IncompatibleClassChangeError();
    }
}
