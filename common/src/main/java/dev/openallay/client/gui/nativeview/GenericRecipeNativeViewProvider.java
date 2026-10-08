package dev.openallay.client.gui.nativeview;

import dev.openallay.platform.minecraft.MinecraftResourceIds;

import dev.openallay.client.gui.GuideGraphics;
import dev.openallay.guide.ui.GuideRecipeCard;
import dev.openallay.guide.ui.GuideUiLayout;
import java.util.ArrayList;
import java.util.List;

import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import dev.openallay.platform.minecraft.MinecraftComponents;



/** Neutral OpenAllay recipe canvas; it deliberately does not imitate a mod screen. */
final class GenericRecipeNativeViewProvider implements NativeDomainViewProvider {
    private static final int PANEL = 0xE0242933;
    private static final int SLOT = 0xFF171A20;
    private static final int BORDER = 0xFF56616E;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFFA9B3BE;
    private static final int ACCENT = 0xFF72D5C4;

    @Override
    public String providerId() {
        return "openallay:generic";
    }

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean supports(NativeDomainViewBinding binding) {
        return binding instanceof NativeDomainViewBinding.Recipe;
    }

    @Override
    public Attempt create(NativeDomainViewBinding binding) {
        NativeDomainViewBinding.Recipe recipe = (NativeDomainViewBinding.Recipe) binding;
        return new Attempt.Ready(new View(recipe.recipe()));
    }

    @dev.openallay.value.ValueType(View.ValueSchemaProvider.class)
private static final class View implements NativeDomainView {
    private final GuideRecipeCard recipe;
    private View(GuideRecipeCard recipe) {
        this.recipe = recipe;
    }
    public GuideRecipeCard recipe() { return recipe; }
@Override
        public String providerId() {
            return "openallay:generic";
        }
@Override
        public NativeDomainViewBinding.Family family() {
            return NativeDomainViewBinding.Family.RECIPE;
        }
@Override
        public void render(RenderContext context) {
            GuideGraphics graphics = context.graphics();
            net.minecraft.client.gui.Font font = context.font();
            GuideUiLayout.Rect bounds = context.bounds();
            graphics.fill(
                    bounds.x(), bounds.y(), bounds.x() + bounds.width(),
                    bounds.y() + bounds.height(), PANEL);
            graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), BORDER);
            String title = dev.openallay.util.Java8Strings.isBlank(recipe.workstation())
                    ? recipe.type() : recipe.workstation();
            graphics.text(font, MinecraftComponents.translatable(
                            "screen.openallay.native.recipe.title", title),
                    bounds.x() + 6, bounds.y() + 5, ACCENT, false);

            int slotY = bounds.y() + 26;
            graphics.text(font, MinecraftComponents.translatable("screen.openallay.native.recipe.inputs"),
                    bounds.x() + 6, slotY - 10, MUTED, false);
            List<GuideRecipeCard.Ingredient> inputs = new ArrayList<>(recipe.ingredients());
            inputs.addAll(recipe.catalysts());
            int outputWidth = 48;
            int arrowWidth = 22;
            int inputWidth = Math.max(22, bounds.width() - outputWidth - arrowWidth - 18);
            int columns = Math.max(1, inputWidth / 20);
            int visibleInputs = Math.min(inputs.size(), columns * 3);
            for (int index = 0; index < visibleInputs; index++) {
                int x = bounds.x() + 6 + index % columns * 20;
                int y = slotY + index / columns * 20;
                GuideRecipeCard.Ingredient ingredient = inputs.get(index);
                renderSlot(graphics, font, ingredientItem(ingredient), ingredient.count(),
                        x, y, context.mouseX(), context.mouseY());
                if (!ingredient.consumed()) {
                    graphics.text(font, "◇", x + 11, y + 10, ACCENT, false);
                }
            }
            if (visibleInputs < inputs.size()) {
                graphics.text(font, "+" + (inputs.size() - visibleInputs),
                        bounds.x() + 7, slotY + 62, MUTED, false);
            }

            int arrowX = bounds.x() + bounds.width() - outputWidth - arrowWidth;
            graphics.text(font, "→", arrowX + 5, slotY + 13, ACCENT, false);
            graphics.text(font, MinecraftComponents.translatable("screen.openallay.native.recipe.outputs"),
                    arrowX + arrowWidth, slotY - 10, MUTED, false);
            int outputX = arrowX + arrowWidth + 4;
            for (int index = 0; index < Math.min(3, recipe.outputs().size()); index++) {
                GuideRecipeCard.Output output = recipe.outputs().get(index);
                renderSlot(graphics, font, output.itemId(), output.count(),
                        outputX, slotY + index * 20,
                        context.mouseX(), context.mouseY());
            }

            int factsY = bounds.y() + bounds.height() - 36;
            String facts = processingFacts(recipe.processing());
            if (!dev.openallay.util.Java8Strings.isBlank(facts)) {
                graphics.text(font, facts, bounds.x() + 6, factsY, MUTED, false);
            }
            if (!recipe.byproducts().isEmpty()) {
                graphics.text(font, MinecraftComponents.translatable(
                                "screen.openallay.native.recipe.byproducts",
                                recipe.byproducts().size()),
                        bounds.x() + 6, factsY + 11, TEXT, false);
            }
        }
private static String processingFacts(GuideRecipeCard.Processing processing) {
            List<String> values = new ArrayList<>();
            if (processing.durationTicks() != null) {
                values.add(MinecraftComponents.getString(MinecraftComponents.translatable(
                        "screen.openallay.native.recipe.duration",
                        processing.durationTicks())));
            }
            if (processing.energy() != null) {
                values.add(MinecraftComponents.getString(MinecraftComponents.translatable(
                        "screen.openallay.native.recipe.energy",
                        processing.energy())));
            }
            if (processing.temperature() != null) {
                values.add(MinecraftComponents.getString(MinecraftComponents.translatable(
                        "screen.openallay.native.recipe.temperature",
                        processing.temperature())));
            }
            return String.join(" · ", values);
        }
private static String ingredientItem(GuideRecipeCard.Ingredient ingredient) {
            GuideRecipeCard.Alternative alternative = ingredient.alternatives().get(0);
            return alternative.resolvedItems().isEmpty()
                    ? alternative.id() : alternative.resolvedItems().get(0);
        }
private static void renderSlot(
                GuideGraphics graphics,
                net.minecraft.client.gui.Font font,
                String itemId,
                long count,
                int x,
                int y,
                int mouseX,
                int mouseY) {
            graphics.fill(x, y, x + 18, y + 18, SLOT);
            graphics.outline(x, y, 18, 18, BORDER);
            net.minecraft.resources.ResourceLocation id = MinecraftResourceIds.tryParse(itemId);
            if (id == null || !MinecraftNativeRegistries.ITEM.containsKey(id)) {
                graphics.text(font, "?", x + 6, y + 5, MUTED, false);
                return;
            }
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(
                    dev.openallay.client.gui.GuideNativeItemLookup.item(id.toString()),
                    (int) Math.min(Integer.MAX_VALUE, Math.max(1, count)));
            graphics.item(stack, x + 1, y + 1);
            graphics.itemDecorations(font, stack, x + 1, y + 1);
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof View)) return false;
        View that = (View) other;
        return java.util.Objects.equals(recipe, that.recipe);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(recipe);
        return hash;
    }
    @Override public String toString() { return "View[recipe=" + recipe + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<View> schema() {
            return new dev.openallay.value.ValueSchema<>(View.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<View>>asList(new dev.openallay.value.ValueSchema.Component<>(View.class, "recipe", View::recipe)), arguments -> new View((GuideRecipeCard) arguments[0]));
        }
    }
}
}
