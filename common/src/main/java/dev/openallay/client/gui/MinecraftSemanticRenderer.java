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


import dev.openallay.platform.minecraft.MinecraftComponents;



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
 hits = dev.openallay.util.Java8Collections.listCopyOf(hits);
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
                net.minecraft.client.gui.Font font,
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
            net.minecraft.client.gui.Font font,
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
            net.minecraft.client.gui.Font font,
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
            net.minecraft.client.gui.Font font,
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
            switch ((line.kind())) {
case RULE:
{
graphics.fill(left, current + line.height() / 2,
                        x + width, current + line.height() / 2 + 1, MUTED);
break;
}
case COMPONENT:
{
renderComponent(
                        graphics, font, line.component(), left, current,
                        Math.max(20, width - line.indent()), line.height(), mouseX, mouseY, hits,
                        animationsEnabled, presentationTicks, recipeGridRenderer);
break;
}
case TABLE:
{
renderTable(graphics, font, line.table(), left, current, hits);
break;
}
default:
{
{
                    if (line.kind() == SemanticLayout.Kind.CODE) {
                        graphics.fill(left - 2, current - 1, x + width, current + line.height(), PANEL);
                    } else if (line.kind() == SemanticLayout.Kind.QUOTE) {
                        graphics.fill(left - 4, current, left - 2, current + line.height(), ACCENT);
                    }
                    net.minecraft.network.chat.Component rendered = MinecraftComponents.empty();
                    int runX = left;
                    for (SemanticLayout.Run run : line.runs()) {
                        net.minecraft.network.chat.Component value = MinecraftComponents.literal(run.text());
                        {
net.minecraft.network.chat.Component $oaSwitch4_exit_result;
$oaSwitch4_exit: {
switch ((run.style())) {
case NORMAL:
{
$oaSwitch4_exit_result = value; break $oaSwitch4_exit;
}
case EMPHASIS:
{
$oaSwitch4_exit_result = MinecraftComponents.style(value, net.minecraft.ChatFormatting.ITALIC); break $oaSwitch4_exit;
}
case STRONG:
{
$oaSwitch4_exit_result = MinecraftComponents.style(value, net.minecraft.ChatFormatting.BOLD); break $oaSwitch4_exit;
}
case CODE:
{
$oaSwitch4_exit_result = MinecraftComponents.style(value, net.minecraft.ChatFormatting.GRAY); break $oaSwitch4_exit;
}
case REFERENCE:
{
$oaSwitch4_exit_result = MinecraftComponents.style(value, net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.UNDERLINE); break $oaSwitch4_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
value = $oaSwitch4_exit_result;
}
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
break;
}
}

            current = lineTop + line.height();
        }
        return new Result(current, hits);
    }

    private void renderTable(
            GuideGraphics graphics,
            net.minecraft.client.gui.Font font,
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
            net.minecraft.client.gui.Font font,
            List<SemanticLayout.Run> runs,
            int x,
            int y,
            int color,
            List<Hit> hits) {
        int runX = x;
        for (SemanticLayout.Run run : runs) {
            net.minecraft.network.chat.Component rendered = MinecraftComponents.literal(run.text());
            {
net.minecraft.network.chat.Component $oaSwitch3_exit_result;
$oaSwitch3_exit: {
switch ((run.style())) {
case NORMAL:
{
$oaSwitch3_exit_result = rendered; break $oaSwitch3_exit;
}
case EMPHASIS:
{
$oaSwitch3_exit_result = MinecraftComponents.style(rendered, net.minecraft.ChatFormatting.ITALIC); break $oaSwitch3_exit;
}
case STRONG:
{
$oaSwitch3_exit_result = MinecraftComponents.style(rendered, net.minecraft.ChatFormatting.BOLD); break $oaSwitch3_exit;
}
case CODE:
{
$oaSwitch3_exit_result = MinecraftComponents.style(rendered, net.minecraft.ChatFormatting.GRAY); break $oaSwitch3_exit;
}
case REFERENCE:
{
$oaSwitch3_exit_result = MinecraftComponents.style(rendered,
                        net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.UNDERLINE); break $oaSwitch3_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
rendered = $oaSwitch3_exit_result;
}
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
        {
int $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((alignment)) {
case RIGHT:
{
$oaSwitch0_exit_result = remaining; break $oaSwitch0_exit;
}
case CENTER:
{
$oaSwitch0_exit_result = remaining / 2; break $oaSwitch0_exit;
}
case NONE:
case LEFT:
{
$oaSwitch0_exit_result = 0; break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch0_exit_result;
}
    }

    public static Intent intent(SemanticReference reference) {
        if (reference == null) return null;
        {
dev.openallay.client.gui.MinecraftSemanticRenderer.Intent $oaSwitch5_exit_result;
$oaSwitch5_exit: {
switch ((reference.kind())) {
case ITEM:
case BLOCK:
{
$oaSwitch5_exit_result = new Intent.BrowseRecipes(reference.target()); break $oaSwitch5_exit;
}
case RECIPE:
{
{
                if (!reference.grounded()) { $oaSwitch5_exit_result = null; break $oaSwitch5_exit; }
                try {
                    { $oaSwitch5_exit_result = new Intent.ExactRecipe(RecipeSemanticHandle.decode(reference.target())); break $oaSwitch5_exit; }
                } catch (IllegalArgumentException malformed) {
                    { $oaSwitch5_exit_result = null; break $oaSwitch5_exit; }
                }
            }
}
case SOURCE:
{
$oaSwitch5_exit_result = reference.grounded()
                    ? new Intent.Source(reference.target(), reference.originInvocationId()) : null; break $oaSwitch5_exit;
}
case EVIDENCE:
{
$oaSwitch5_exit_result = reference.grounded()
                    ? new Intent.Evidence(reference.target(), reference.originInvocationId()) : null; break $oaSwitch5_exit;
}
case FLUID:
case ENTITY:
case BIOME:
case DIMENSION:
case TAG:
case KEY:
{
$oaSwitch5_exit_result = null; break $oaSwitch5_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch5_exit_result;
}
    }

    private void renderComponent(
            GuideGraphics graphics,
            net.minecraft.client.gui.Font font,
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
        final class $oaPattern0_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ItemRow bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ItemRow && (($oaPattern0_holder.bound = (RichComponent.ItemRow) $oaPattern0_holder.value) != null))) {
            int rowY = y;
            for (RichComponent.Item item : $oaPattern0_holder.bound.items()) {
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
        } else {
final class $oaPattern1_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.RecipeGrid bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.RecipeGrid && (($oaPattern1_holder.bound = (RichComponent.RecipeGrid) $oaPattern1_holder.value) != null))) {
            if (recipeGridRenderer.render(
                    graphics,
                    font,
                    $oaPattern1_holder.bound,
                    new GuideUiLayout.Rect(x - 2, y - 1, width + 2, height),
                    mouseX,
                    mouseY,
                    presentationTicks)) {
                action(graphics, font, MinecraftComponents.translatable(
                                "screen.openallay.semantic.action.open_recipe"),
                        x + 4, y + height - 11,
                        new Intent.ExactRecipe($oaPattern1_holder.bound.recipe()), hits);
                return;
            }
            graphics.text(font, dev.openallay.util.Java8Strings.isBlank($oaPattern1_holder.bound.label())
                            ? MinecraftComponents.translatable("screen.openallay.semantic.recipe")
                            : MinecraftComponents.literal($oaPattern1_holder.bound.label()),
                    x + 4, y + 4, ACCENT, false);
            graphics.text(font, MinecraftComponents.translatable(
                            "screen.openallay.semantic.recipe_verified"),
                    x + 4, y + 18, MUTED, false);
            action(graphics, font, MinecraftComponents.translatable(
                            "screen.openallay.semantic.action.open_recipe"), x + 4, y + 34,
                    new Intent.ExactRecipe($oaPattern1_holder.bound.recipe()), hits);
        } else {
final class $oaPattern2_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.IngredientCheck bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.IngredientCheck && (($oaPattern2_holder.bound = (RichComponent.IngredientCheck) $oaPattern2_holder.value) != null))) {
            int rowY = y;
            for (RichComponent.Ingredient ingredient : $oaPattern2_holder.bound.ingredients()) {
                renderItem(graphics, font, ingredient.itemId(), ingredient.label(),
                        ingredient.required(), x + 2, rowY, mouseX, mouseY);
                String count = ingredient.available() + "/" + ingredient.required();
                graphics.text(font, count, x + width - GuideNativeFont.width(font, count) - 5, rowY + 5,
                        ingredient.available() >= ingredient.required() ? SUCCESS : ERROR, false);
                rowY += 22;
            }
        } else {
final class $oaPattern3_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.CraftabilitySummary bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.CraftabilitySummary && (($oaPattern3_holder.bound = (RichComponent.CraftabilitySummary) $oaPattern3_holder.value) != null))) {
            graphics.text(font, MinecraftComponents.translatable($oaPattern3_holder.bound.craftable()
                            ? "screen.openallay.semantic.craftable"
                            : "screen.openallay.semantic.not_craftable"),
                    x + 4, y + 4, $oaPattern3_holder.bound.craftable() ? SUCCESS : ERROR, false);
            graphics.text(font, MinecraftComponents.translatable(
                            "screen.openallay.semantic.maximum_crafts", $oaPattern3_holder.bound.maximumCrafts()),
                    x + 4, y + 16, TEXT, false);
            graphics.text(font, MinecraftComponents.translatable($oaPattern3_holder.bound.conclusive()
                            ? "screen.openallay.semantic.conclusive"
                            : "screen.openallay.semantic.incomplete"),
                    x + 4, y + 28, MUTED, false);
        } else {
final class $oaPattern4_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ProgressSteps bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ProgressSteps && (($oaPattern4_holder.bound = (RichComponent.ProgressSteps) $oaPattern4_holder.value) != null))) {
            int rowY = y + 2;
            for (RichComponent.Step step : $oaPattern4_holder.bound.steps()) {
                String marker = progressMarker(
                        step.state(), animationsEnabled, presentationTicks);
                graphics.text(font, marker + " " + step.label(), x + 4, rowY,
                        step.state() == RichComponent.StepState.FAILED ? ERROR : TEXT, false);
                rowY += 10;
            }
        } else {
final class $oaPattern5_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.SourceSummary bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.SourceSummary && (($oaPattern5_holder.bound = (RichComponent.SourceSummary) $oaPattern5_holder.value) != null))) {
            int rowY = y + 2;
            for (RichComponent.Source source : $oaPattern5_holder.bound.sources()) {
                graphics.text(font, MinecraftComponents.translatable(
                                "screen.openallay.semantic.source", source.label()),
                        x + 4, rowY, ACCENT, false);
                hits.add(new Hit(new GuideUiLayout.Rect(x + 2, rowY - 1, width - 4, 11),
                        new Intent.Source(source.sourceId(), source.originInvocationId())));
                rowY += 10;
            }
        } else {
final class $oaPattern6_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.StatusBadge bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.StatusBadge && (($oaPattern6_holder.bound = (RichComponent.StatusBadge) $oaPattern6_holder.value) != null))) {
            {
final dev.openallay.client.gui.GuideGraphics $oaSwitch2_exit_result_prior0 = graphics;
final net.minecraft.client.gui.Font $oaSwitch2_exit_result_prior1 = font;
final java.lang.String $oaSwitch2_exit_result_prior2 = $oaPattern6_holder.bound.label();
final int $oaSwitch2_exit_result_prior3 = x + 5;
final int $oaSwitch2_exit_result_prior4 = y + 4;
int $oaSwitch2_exit_result;
$oaSwitch2_exit: {
switch (($oaPattern6_holder.bound.state())) {
case INFO:
{
$oaSwitch2_exit_result = ACCENT; break $oaSwitch2_exit;
}
case SUCCESS:
{
$oaSwitch2_exit_result = SUCCESS; break $oaSwitch2_exit;
}
case WARNING:
{
$oaSwitch2_exit_result = 0xFFFFD479; break $oaSwitch2_exit;
}
case ERROR:
{
$oaSwitch2_exit_result = ERROR; break $oaSwitch2_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
$oaSwitch2_exit_result_prior0.text(
                    $oaSwitch2_exit_result_prior1, $oaSwitch2_exit_result_prior2, $oaSwitch2_exit_result_prior3, $oaSwitch2_exit_result_prior4,
                    $oaSwitch2_exit_result, false);
}
        } else {
final class $oaPattern7_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ChoiceGroup bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ChoiceGroup && (($oaPattern7_holder.bound = (RichComponent.ChoiceGroup) $oaPattern7_holder.value) != null))) {
            graphics.text(font, $oaPattern7_holder.bound.prompt(), x + 4, y + 2, TEXT, false);
            int rowY = y + 14;
            for (RichComponent.Choice choice : $oaPattern7_holder.bound.choices()) {
                action(graphics, font, MinecraftComponents.literal(choice.label()), x + 4, rowY,
                        new Intent.Choice($oaPattern7_holder.bound.nodeId(), choice.id()), hits);
                rowY += 11;
            }
        }
}
}
}
}
}
}
}
    }

    static String progressMarker(
            RichComponent.StepState state,
            boolean animationsEnabled,
            long presentationTicks) {
        {
java.lang.String $oaSwitch1_exit_result;
$oaSwitch1_exit: {
switch ((state)) {
case PENDING:
{
$oaSwitch1_exit_result = "○"; break $oaSwitch1_exit;
}
case ACTIVE:
{
$oaSwitch1_exit_result = animationsEnabled && (presentationTicks / 8) % 2 == 0
                    ? "▷" : "▶"; break $oaSwitch1_exit;
}
case COMPLETE:
{
$oaSwitch1_exit_result = "✓"; break $oaSwitch1_exit;
}
case FAILED:
{
$oaSwitch1_exit_result = "!"; break $oaSwitch1_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch1_exit_result;
}
    }

    private void renderItem(
            GuideGraphics graphics,
            net.minecraft.client.gui.Font font,
            String itemId,
            String label,
            long count,
            int x,
            int y,
            int mouseX,
            int mouseY) {
        MinecraftSemanticResolver.ItemPresentation item = resolver.item(itemId, label, count);
        net.minecraft.world.item.ItemStack stack = item.stack();
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
            net.minecraft.client.gui.Font font,
            net.minecraft.network.chat.Component label,
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
        final class $oaPattern8_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ItemRow bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ItemRow && (($oaPattern8_holder.bound = (RichComponent.ItemRow) $oaPattern8_holder.value) != null))) {
            return Math.max(22, 22 * $oaPattern8_holder.bound.items().size());
        } else {
final class $oaPattern9_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.RecipeGrid bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.RecipeGrid && (($oaPattern9_holder.bound = (RichComponent.RecipeGrid) $oaPattern9_holder.value) != null))) {
            return 136;
        } else {
final class $oaPattern10_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.IngredientCheck bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.IngredientCheck && (($oaPattern10_holder.bound = (RichComponent.IngredientCheck) $oaPattern10_holder.value) != null))) {
            return Math.max(22, 22 * $oaPattern10_holder.bound.ingredients().size());
        } else {
final class $oaPattern11_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.CraftabilitySummary bound; }
final $oaPattern11_Holder $oaPattern11_holder = new $oaPattern11_Holder();
if ((($oaPattern11_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.CraftabilitySummary && (($oaPattern11_holder.bound = (RichComponent.CraftabilitySummary) $oaPattern11_holder.value) != null))) {
            return 40;
        } else {
final class $oaPattern12_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ProgressSteps bound; }
final $oaPattern12_Holder $oaPattern12_holder = new $oaPattern12_Holder();
if ((($oaPattern12_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ProgressSteps && (($oaPattern12_holder.bound = (RichComponent.ProgressSteps) $oaPattern12_holder.value) != null))) {
            return 12 * ($oaPattern12_holder.bound.steps().size() + 1);
        } else {
final class $oaPattern13_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.SourceSummary bound; }
final $oaPattern13_Holder $oaPattern13_holder = new $oaPattern13_Holder();
if ((($oaPattern13_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.SourceSummary && (($oaPattern13_holder.bound = (RichComponent.SourceSummary) $oaPattern13_holder.value) != null))) {
            return 12 * ($oaPattern13_holder.bound.sources().size() + 1);
        } else {
final class $oaPattern14_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.StatusBadge bound; }
final $oaPattern14_Holder $oaPattern14_holder = new $oaPattern14_Holder();
if ((($oaPattern14_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.StatusBadge && (($oaPattern14_holder.bound = (RichComponent.StatusBadge) $oaPattern14_holder.value) != null))) {
            return 16;
        } else {
final class $oaPattern15_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ChoiceGroup bound; }
final $oaPattern15_Holder $oaPattern15_holder = new $oaPattern15_Holder();
if ((($oaPattern15_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ChoiceGroup && (($oaPattern15_holder.bound = (RichComponent.ChoiceGroup) $oaPattern15_holder.value) != null))) {
            return 12 * ($oaPattern15_holder.bound.choices().size() + 1);
        }
}
}
}
}
}
}
}
        throw new IncompatibleClassChangeError();
    }
}
