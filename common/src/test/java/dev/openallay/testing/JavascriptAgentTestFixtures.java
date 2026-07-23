package dev.openallay.testing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.openallay.context.IngredientAlternativeSnapshot;
import dev.openallay.context.IngredientRequirementSnapshot;
import dev.openallay.context.ItemStackSnapshot;
import dev.openallay.context.RecipeEntrySnapshot;
import dev.openallay.context.RecipeLayoutSnapshot;
import dev.openallay.context.RecipeOutputSnapshot;
import dev.openallay.context.RecipeProcessingSnapshot;
import dev.openallay.context.RecipeReference;
import dev.openallay.context.RecipeSnapshot;
import dev.openallay.context.RegistryEntrySnapshot;
import dev.openallay.context.RegistrySnapshot;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.recipe.RecipeUnlockState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Stable captured data used by deterministic and opt-in live Rhino Agent acceptance. */
public final class JavascriptAgentTestFixtures {
    public static final String HIGHEST_DAMAGE_SWORD = "example:obsidian_sword";
    public static final String STRONGEST_POISON_ITEM = "example:concentrated_poison";
    public static final String LEAST_MATERIAL_CONTAINER = "example:small_pouch";

    private JavascriptAgentTestFixtures() {}

    public static ToolInvocationContext context(String correlationId) {
        ToolInvocationContext base = GroundedTestFixtures.fullContext();
        RegistrySnapshot registries = new RegistrySnapshot(
                GroundedTestFixtures.serverEvidence(),
                List.of(
                        item("minecraft:iron_sword", Set.of("minecraft:swords"),
                                Map.of("minecraft:attack_damage", JsonParser.parseString("6"))),
                        item(HIGHEST_DAMAGE_SWORD, Set.of("minecraft:swords"),
                                Map.of("minecraft:attack_damage", JsonParser.parseString("14"))),
                        item("minecraft:honey_bottle", Set.of(), Map.of(
                                "minecraft:effects", effects("minecraft:poison", 0, 100))),
                        item(STRONGEST_POISON_ITEM, Set.of(), Map.of(
                                "minecraft:effects", effects("minecraft:poison", 2, 300))),
                        item("minecraft:chest", Set.of("openallay:containers"), Map.of()),
                        item(LEAST_MATERIAL_CONTAINER, Set.of("openallay:containers"), Map.of())));
        RecipeSnapshot recipes = new RecipeSnapshot(
                GroundedTestFixtures.serverEvidence(),
                List.of(
                        recipe(STRONGEST_POISON_ITEM, STRONGEST_POISON_ITEM, 3),
                        recipe("minecraft:chest", "minecraft:chest", 8),
                        recipe(LEAST_MATERIAL_CONTAINER, LEAST_MATERIAL_CONTAINER, 2)));
        return new ToolInvocationContext(
                correlationId,
                base.capturedAt(),
                base.caller(),
                base.player(),
                Optional.of(registries),
                Optional.of(recipes),
                base.observableGameState(),
                base.metrics());
    }

    private static RegistryEntrySnapshot item(
            String id, Set<String> tags, Map<String, JsonElement> properties) {
        return new RegistryEntrySnapshot(
                id,
                "item",
                id,
                id.substring(0, id.indexOf(':')),
                "minecraft:registry",
                List.of(),
                tags,
                Set.of(),
                properties);
    }

    private static JsonArray effects(String id, int amplifier, int duration) {
        return JsonParser.parseString("""
                [{"id":"%s","amplifier":%d,"duration":%d}]
                """.formatted(id, amplifier, duration)).getAsJsonArray();
    }

    private static RecipeEntrySnapshot recipe(
            String recipeId, String outputId, int units) {
        return new RecipeEntrySnapshot(
                new RecipeReference(
                        "minecraft:recipe_manager",
                        GroundedTestFixtures.RECIPE_GENERATION,
                        recipeId),
                recipeId,
                "minecraft:crafting",
                new RecipeLayoutSnapshot(3, 3, true),
                "minecraft:crafting_table",
                List.of(new IngredientRequirementSnapshot(
                        "materials",
                        units,
                        true,
                        List.of(new IngredientAlternativeSnapshot(
                                "item", "minecraft:stone", List.of("minecraft:stone"))))),
                List.of(),
                List.of(),
                List.of(new RecipeOutputSnapshot(
                        new ItemStackSnapshot(outputId, 1, outputId), 1.0D)),
                List.of(),
                RecipeProcessingSnapshot.unknown(),
                List.of(),
                Map.of(),
                RecipeUnlockState.UNKNOWN,
                GroundedTestFixtures.serverEvidence());
    }
}
