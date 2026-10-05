package dev.openallay.context.minecraft;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.IngredientAlternativeSnapshot;
import dev.openallay.context.IngredientRequirementSnapshot;
import dev.openallay.context.ItemStackSnapshot;
import dev.openallay.context.RecipeEntrySnapshot;
import dev.openallay.context.RecipeLayoutSnapshot;
import dev.openallay.context.RecipeOutputSnapshot;
import dev.openallay.context.RecipeProcessingSnapshot;
import dev.openallay.context.RecipeReference;
import dev.openallay.recipe.RecipeUnlockState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** One recipe detachment algorithm for every native family and both capture authorities. */
public final class MinecraftRecipeSnapshots {
    private static final String CAPTURE_GENERATION_PLACEHOLDER = "0".repeat(64);
    private MinecraftRecipeSnapshots() {}

    public static List<RecipeEntrySnapshot> capture(
            List<MinecraftRecipeInput> inputs, String source,
            RecipeUnlockState unlockState, EvidenceMetadata evidence) {
        Map<String, RecipeEntrySnapshot> recipes = new TreeMap<>();
        for (MinecraftRecipeInput input : inputs) {
            List<IngredientRequirementSnapshot> ingredients = new ArrayList<>();
            for (int index = 0; index < input.ingredients().size(); index++) {
                List<IngredientAlternativeSnapshot> alternatives = MinecraftIngredientItems
                        .items(input.ingredients().get(index))
                        .map(holder -> {
                            String id = dev.openallay.platform.minecraft.MinecraftResourceIds.keyId(
                                    holder.unwrapKey().orElseThrow(
                                            () -> new IllegalStateException("Unbound recipe item")));
                            return new IngredientAlternativeSnapshot("item", id, List.of(id));
                        }).toList();
                if (alternatives.isEmpty()) continue;
                ingredients.add(new IngredientRequirementSnapshot("input-" + index, 1, true, alternatives));
            }
            List<RecipeOutputSnapshot> outputs = input.outputs().stream()
                    .map(value -> new RecipeOutputSnapshot(stack(value), 1.0D)).toList();
            RecipeLayoutSnapshot layout = input.shaped()
                    ? new RecipeLayoutSnapshot(input.width(), input.height(), true)
                    : input.crafting() ? new RecipeLayoutSnapshot(0, 0, false)
                    : RecipeLayoutSnapshot.unknown();
            recipes.putIfAbsent(input.id(), new RecipeEntrySnapshot(
                    new RecipeReference(source, CAPTURE_GENERATION_PLACEHOLDER, input.id()),
                    input.id(), input.type(), layout, input.workstation(), ingredients,
                    List.of(), List.of(), outputs, List.of(), RecipeProcessingSnapshot.unknown(),
                    List.of(), Map.of(), unlockState, evidence));
        }
        return List.copyOf(recipes.values());
    }

    private static ItemStackSnapshot stack(ItemStack stack) {
        if (stack.isEmpty()) return ItemStackSnapshot.empty();
        return new ItemStackSnapshot(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getCount(), stack.getHoverName().getString());
    }
}
