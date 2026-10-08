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
import dev.openallay.platform.minecraft.MinecraftNativeRegistries;


/** One recipe detachment algorithm for every native family and both capture authorities. */
public final class MinecraftRecipeSnapshots {
    private static final String CAPTURE_GENERATION_PLACEHOLDER = dev.openallay.util.Java8Strings.repeat("0", 64);
    private MinecraftRecipeSnapshots() {}

    public static List<RecipeEntrySnapshot> capture(
            List<MinecraftRecipeInput> inputs, String source,
            RecipeUnlockState unlockState, EvidenceMetadata evidence) {
        Map<String, RecipeEntrySnapshot> recipes = new TreeMap<>();
        for (MinecraftRecipeInput input : inputs) {
            List<IngredientRequirementSnapshot> ingredients = new ArrayList<>();
            for (int index = 0; index < input.ingredients().size(); index++) {
                List<IngredientAlternativeSnapshot> alternatives = dev.openallay.util.Java8Collections.toList(MinecraftIngredientItems
                        .items(input.ingredients().get(index))
                        .map(item -> {
                            String id = java.util.Objects.requireNonNull(MinecraftNativeRegistries.ITEM.getKey(item),
                                    "Unbound recipe item").toString();
                            return new IngredientAlternativeSnapshot("item", id, dev.openallay.util.Java8Collections.listOf(id));
                        }));
                if (alternatives.isEmpty()) continue;
                ingredients.add(new IngredientRequirementSnapshot("input-" + index, 1, true, alternatives));
            }
            List<RecipeOutputSnapshot> outputs = dev.openallay.util.Java8Collections.toList(input.outputs().stream()
                    .map(value -> new RecipeOutputSnapshot(stack(value), 1.0D)));
            RecipeLayoutSnapshot layout = input.shaped()
                    ? new RecipeLayoutSnapshot(input.width(), input.height(), true)
                    : input.crafting() ? new RecipeLayoutSnapshot(0, 0, false)
                    : RecipeLayoutSnapshot.unknown();
            recipes.putIfAbsent(input.id(), new RecipeEntrySnapshot(
                    new RecipeReference(source, CAPTURE_GENERATION_PLACEHOLDER, input.id()),
                    input.id(), input.type(), layout, input.workstation(), ingredients,
                    dev.openallay.util.Java8Collections.listOf(), dev.openallay.util.Java8Collections.listOf(), outputs, dev.openallay.util.Java8Collections.listOf(), RecipeProcessingSnapshot.unknown(),
                    dev.openallay.util.Java8Collections.listOf(), dev.openallay.util.Java8Collections.mapOf(), unlockState, evidence));
        }
        return dev.openallay.util.Java8Collections.listCopyOf(recipes.values());
    }

    private static ItemStackSnapshot stack(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return ItemStackSnapshot.empty();
        return new ItemStackSnapshot(
                MinecraftNativeRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getCount(), MinecraftServerCaptureFacts.itemName(stack));
    }
}
