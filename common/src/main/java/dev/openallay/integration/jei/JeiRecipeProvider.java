package dev.openallay.integration.jei;

import dev.openallay.platform.minecraft.MinecraftResourceId;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.FluidRequirementSnapshot;
import dev.openallay.context.IngredientAlternativeSnapshot;
import dev.openallay.context.IngredientRequirementSnapshot;
import dev.openallay.context.ItemStackSnapshot;
import dev.openallay.context.RecipeEntrySnapshot;
import dev.openallay.context.RecipeLayoutSnapshot;
import dev.openallay.context.RecipeOutputSnapshot;
import dev.openallay.context.RecipeProcessingSnapshot;
import dev.openallay.context.RecipeReference;
import dev.openallay.platform.PlatformService;
import dev.openallay.recipe.RecipeCanonicalizer;
import dev.openallay.recipe.RecipeKnowledgeProvider;
import dev.openallay.recipe.RecipeProviderDiagnostic;
import dev.openallay.recipe.RecipeProviderSnapshot;
import dev.openallay.recipe.RecipeUnlockState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import net.minecraft.world.item.ItemStack;

final class JeiRecipeProvider implements RecipeKnowledgeProvider {
    private static final String SOURCE_ID = "viewer:jei";
    private static final String TAG_CATEGORY_PREFIX = "minecraft:tag_recipes/";
    private static final String CAPTURE_GENERATION_PLACEHOLDER = "0".repeat(64);

    private final IJeiRuntime runtime;
    private final Instant capturedAt;
    private final PlatformService platform;

    JeiRecipeProvider(IJeiRuntime runtime, Instant capturedAt, PlatformService platform) {
        this.runtime = runtime;
        this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        this.platform = Objects.requireNonNull(platform, "platform");
    }

    @Override
    public String sourceId() {
        return SOURCE_ID;
    }

    @Override
    public RecipeProviderSnapshot capture() {
        if (runtime == null) {
            return RecipeProviderSnapshot.unavailable(
                    SOURCE_ID, "runtime_unavailable", "JEI runtime is not available");
        }

        List<RecipeProviderDiagnostic> diagnostics = new ArrayList<>();
        TreeMap<String, RecipeEntrySnapshot> recipes = new TreeMap<>();
        List<IRecipeCategory<?>> categories = MinecraftJeiRecipeApi.categories(runtime, true).stream()
                .sorted(Comparator.comparing(category -> MinecraftJeiRecipeApi.categoryId(category).toString()))
                .toList();
        for (IRecipeCategory<?> category : categories) {
            if (MinecraftJeiRecipeApi.categoryId(category).toString().startsWith(TAG_CATEGORY_PREFIX)) {
                // JEI exposes item and block tag membership through recipe-shaped layouts. These
                // are viewer metadata, not crafting or processing recipes.
                continue;
            }
            try {
                captureCategory(category, recipes, diagnostics);
            } catch (RuntimeException failure) {
                diagnostics.add(diagnostic(
                        "category_capture_failed",
                        "JEI category could not be detached: "
                                + MinecraftJeiRecipeApi.categoryId(category)));
            }
        }
        DataCompleteness completeness = diagnostics.isEmpty()
                ? DataCompleteness.COMPLETE
                : DataCompleteness.PARTIAL;
        return RecipeProviderSnapshot.available(
                SOURCE_ID, completeness, List.copyOf(recipes.values()), diagnostics);
    }

    private <T> void captureCategory(
            IRecipeCategory<T> category,
            Map<String, RecipeEntrySnapshot> recipes,
            List<RecipeProviderDiagnostic> diagnostics) {
        List<T> categoryRecipes = MinecraftJeiRecipeApi.recipes(runtime, category, true);
        for (T recipe : categoryRecipes) {
            try {
                RecipeEntrySnapshot detached = detach(category, recipe);
                RecipeEntrySnapshot previous = recipes.putIfAbsent(
                        detached.reference().recipeId(), detached);
                if (previous != null && !previous.equals(detached)) {
                    diagnostics.add(diagnostic(
                            "duplicate_reference",
                            "JEI recipes produced the same stable reference: "
                                    + detached.reference().recipeId()));
                }
            } catch (UnsupportedRecipe failure) {
                diagnostics.add(diagnostic(failure.code, failure.getMessage()));
            } catch (RuntimeException failure) {
                diagnostics.add(diagnostic(
                        "recipe_capture_failed",
                        "JEI recipe could not be detached from category "
                                + MinecraftJeiRecipeApi.categoryId(category)));
            }
        }
    }

    private <T> RecipeEntrySnapshot detach(IRecipeCategory<T> category, T recipe) {
        NativeRecipeLayout<T> layout = MinecraftJeiRecipeApi.createLayout(
                        runtime, category, recipe)
                .orElseThrow(() -> new UnsupportedRecipe(
                        "layout_unavailable", "JEI did not provide a recipe layout"));
        CapturedSlots slots = captureSlots(layout.captureSlots(runtime));
        MinecraftResourceId explicitId = MinecraftJeiResourceIds.recipe(category, recipe);
        MinecraftResourceId categoryId = MinecraftResourceId.from(MinecraftJeiRecipeApi.categoryId(category).toString());
        String provisionalId = explicitId == null
                ? "openallay:jei_pending"
                : explicitId.toString();
        RecipeEntrySnapshot provisional = entry(
                "openallay:jei_pending", provisionalId, categoryId, slots);
        String fingerprint = RecipeCanonicalizer.semanticFingerprint(provisional);
        String referenceId = referenceId(categoryId, explicitId, fingerprint);
        String entryId = explicitId == null
                ? "openallay:jei_fallback/" + fingerprint
                : explicitId.toString();
        return entry(referenceId, entryId, categoryId, slots);
    }

    <T> String referenceId(IRecipeCategory<T> category, T recipe) {
        RecipeEntrySnapshot detached = detach(category, recipe);
        return detached.reference().recipeId();
    }

    <T> Optional<String> referenceIdIfSupported(IRecipeCategory<T> category, T recipe) {
        try {
            return Optional.of(referenceId(category, recipe));
        } catch (RuntimeException unsupported) {
            // A partial JEI catalog can contain layouts that OpenAllay intentionally cannot
            // detach. Exact navigation must skip those candidates instead of aborting before
            // reaching the validated reference selected by the player.
            return Optional.empty();
        }
    }

    private CapturedSlots captureSlots(List<JeiIngredientSlot> slots) {
        List<IngredientRequirementSnapshot> ingredients = new ArrayList<>();
        List<IngredientRequirementSnapshot> catalysts = new ArrayList<>();
        List<FluidRequirementSnapshot> fluids = new ArrayList<>();
        List<RecipeOutputSnapshot> outputs = new ArrayList<>();
        int inputIndex = 0;
        int catalystIndex = 0;
        for (JeiIngredientSlot slot : slots) {
            List<JeiIngredientValue> values = slot.values();
            if (values.isEmpty() || slot.role() == JeiIngredientSlot.Role.RENDER_ONLY) {
                continue;
            }
            if (slot.role() == JeiIngredientSlot.Role.OUTPUT) {
                outputs.add(captureOutput(values));
            } else if (allItems(values)) {
                IngredientRequirementSnapshot requirement = captureItemRequirement(
                        slot.role() == JeiIngredientSlot.Role.WORKSTATION
                                ? "catalyst-" + catalystIndex++
                                : "input-" + inputIndex++,
                        values, slot.role() == JeiIngredientSlot.Role.INPUT);
                if (slot.role() == JeiIngredientSlot.Role.WORKSTATION) {
                    catalysts.add(requirement);
                } else if (slot.role() == JeiIngredientSlot.Role.INPUT) {
                    ingredients.add(requirement);
                }
            } else if (slot.role() == JeiIngredientSlot.Role.INPUT && allFluids(values)) {
                fluids.add(captureFluid(values));
            } else {
                throw new UnsupportedRecipe(
                        "unsupported_ingredient", "JEI slot contains an unsupported ingredient kind");
            }
        }
        String workstation = catalysts.stream()
                .flatMap(value -> value.alternatives().stream())
                .map(IngredientAlternativeSnapshot::id)
                .findFirst()
                .orElse(null);
        if (outputs.isEmpty()) {
            throw new UnsupportedRecipe(
                    "missing_output", "JEI recipe has no representable item output");
        }
        return new CapturedSlots(ingredients, catalysts, fluids, outputs, workstation);
    }

    private IngredientRequirementSnapshot captureItemRequirement(
            String key, List<JeiIngredientValue> values, boolean consumed) {
        List<ItemStack> stacks = values.stream()
                .map(value -> ((JeiIngredientValue.Item) value).stack())
                .filter(value -> !value.isEmpty())
                .toList();
        if (stacks.isEmpty()) {
            throw new UnsupportedRecipe("empty_ingredient", "JEI item slot is empty");
        }
        rejectComponents(stacks);
        int count = stacks.get(0).getCount();
        if (count <= 0 || stacks.stream().anyMatch(stack -> stack.getCount() != count)) {
            throw new UnsupportedRecipe(
                    "alternative_count_mismatch", "JEI item alternatives use different counts");
        }
        TreeMap<String, IngredientAlternativeSnapshot> alternatives = new TreeMap<>();
        stacks.forEach(stack -> {
            String id = MinecraftNativeRegistries.ITEM.getKey(stack.getItem()).toString();
            alternatives.put(id, new IngredientAlternativeSnapshot("item", id, List.of(id)));
        });
        return new IngredientRequirementSnapshot(
                key, count, consumed, List.copyOf(alternatives.values()));
    }

    private RecipeOutputSnapshot captureOutput(List<JeiIngredientValue> values) {
        if (!allItems(values)) {
            throw new UnsupportedRecipe(
                    "unsupported_output", "JEI output is not an item stack");
        }
        List<ItemStack> stacks = values.stream()
                .map(value -> ((JeiIngredientValue.Item) value).stack())
                .filter(value -> !value.isEmpty())
                .toList();
        if (stacks.isEmpty()) {
            throw new UnsupportedRecipe("empty_output", "JEI output item is empty");
        }
        rejectComponents(stacks);
        LinkedHashMap<String, ItemStack> unique = new LinkedHashMap<>();
        stacks.forEach(stack -> unique.putIfAbsent(
                MinecraftNativeRegistries.ITEM.getKey(stack.getItem()) + "\u0000" + stack.getCount(), stack));
        if (unique.size() != 1) {
            throw new UnsupportedRecipe(
                    "alternative_output", "JEI output slot contains alternatives");
        }
        ItemStack stack = unique.values().iterator().next();
        String id = MinecraftNativeRegistries.ITEM.getKey(stack.getItem()).toString();
        return new RecipeOutputSnapshot(
                new ItemStackSnapshot(id, stack.getCount(), stack.getHoverName().getString()), 1.0D);
    }

    private FluidRequirementSnapshot captureFluid(List<JeiIngredientValue> values) {
        List<FluidValue> fluids = values.stream().map(value -> fluid((JeiIngredientValue.Fluid) value)).distinct().toList();
        if (fluids.size() != 1) {
            throw new UnsupportedRecipe(
                    "alternative_fluid", "JEI fluid slot contains alternatives");
        }
        FluidValue fluid = fluids.get(0);
        if (fluid.amount <= 0) {
            throw new UnsupportedRecipe("invalid_fluid_amount", "JEI fluid amount is not positive");
        }
        return new FluidRequirementSnapshot(fluid.id, fluid.amount, true);
    }

    private static FluidValue fluid(JeiIngredientValue.Fluid value) {
        long amount = value.amount().orElseThrow(() -> new UnsupportedRecipe(
                "fluid_amount_unavailable", "JEI does not expose the fluid ingredient amount"));
        return new FluidValue(value.id(), amount);
    }

    private static boolean allFluids(List<JeiIngredientValue> values) {
        return values.stream().allMatch(value -> value instanceof JeiIngredientValue.Fluid);
    }

    private static boolean allItems(List<JeiIngredientValue> values) {
        return values.stream().allMatch(value -> value instanceof JeiIngredientValue.Item);
    }

    private static void rejectComponents(List<ItemStack> stacks) {
        if (stacks.stream().anyMatch(stack -> dev.openallay.context.minecraft.MinecraftItemDataFacts.hasCustomData(stack))) {
            throw new UnsupportedRecipe(
                    "item_components_unsupported",
                    "JEI item stack has components that cannot be represented losslessly");
        }
    }

    private RecipeEntrySnapshot entry(
            String referenceId, String entryId, MinecraftResourceId categoryId, CapturedSlots slots) {
        return new RecipeEntrySnapshot(
                new RecipeReference(SOURCE_ID, CAPTURE_GENERATION_PLACEHOLDER, referenceId),
                entryId,
                categoryId.toString(),
                RecipeLayoutSnapshot.unknown(),
                slots.workstation,
                slots.ingredients,
                slots.catalysts,
                slots.fluids,
                slots.outputs,
                List.of(),
                RecipeProcessingSnapshot.unknown(),
                List.of(),
                Map.of(),
                RecipeUnlockState.UNKNOWN,
                evidence(categoryId));
    }

    private EvidenceMetadata evidence(MinecraftResourceId categoryId) {
        return new EvidenceMetadata(
                DataAuthority.INTEGRATION_API,
                DataCompleteness.COMPLETE,
                capturedAt,
                SOURCE_ID,
                SOURCE_ID,
                platform.gameVersion(),
                platform.platformName(),
                Map.of("openallay:category", categoryId.toString()));
    }

    private static String referenceId(
            MinecraftResourceId categoryId, MinecraftResourceId explicitId, String fingerprint) {
        StringBuilder path = new StringBuilder("jei/")
                .append(categoryId.namespace())
                .append('/')
                .append(categoryId.path());
        if (explicitId != null) {
            path.append('/')
                    .append(explicitId.namespace())
                    .append('/')
                    .append(explicitId.path());
        } else {
            path.append("/generated");
        }
        path.append('/').append(fingerprint);
        return "openallay:" + path;
    }

    private static RecipeProviderDiagnostic diagnostic(String code, String message) {
        return new RecipeProviderDiagnostic(SOURCE_ID, code, message);
    }

    private record CapturedSlots(
            List<IngredientRequirementSnapshot> ingredients,
            List<IngredientRequirementSnapshot> catalysts,
            List<FluidRequirementSnapshot> fluids,
            List<RecipeOutputSnapshot> outputs,
            String workstation) {}

    private record FluidValue(String id, long amount) {}

    private static final class UnsupportedRecipe extends RuntimeException {
        private final String code;

        private UnsupportedRecipe(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
