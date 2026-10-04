package dev.openallay.integration;

import dev.openallay.context.CallerKind;
import dev.openallay.context.CallerSnapshot;
import dev.openallay.context.ContextMetrics;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ItemStackSnapshot;
import dev.openallay.context.RecipeEntrySnapshot;
import dev.openallay.context.RecipeLayoutSnapshot;
import dev.openallay.context.RecipeOutputSnapshot;
import dev.openallay.context.RecipeProcessingSnapshot;
import dev.openallay.context.RecipeReference;
import dev.openallay.context.RecipeSnapshot;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.recipe.RecipeProviderSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RecipeViewerExtensionTestFixtures {
    private RecipeViewerExtensionTestFixtures() {}

    public static EvidenceMetadata evidence(String sourceId) {
        return new EvidenceMetadata(
                DataAuthority.INTEGRATION_API,
                DataCompleteness.COMPLETE,
                Instant.EPOCH,
                sourceId,
                sourceId,
                "26.2",
                "fabric",
                Map.of("openallay:fixture", "detached"));
    }

    public static RecipeProviderSnapshot provider(String sourceId, String id, String category) {
        RecipeEntrySnapshot provisional = new RecipeEntrySnapshot(
                new RecipeReference(sourceId, "0".repeat(64), "test:stable/" + id.substring(5)),
                id,
                category,
                RecipeLayoutSnapshot.unknown(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(new RecipeOutputSnapshot(
                        new ItemStackSnapshot("minecraft:stone", 1, "Stone"), 1.0D)),
                List.of(),
                RecipeProcessingSnapshot.unknown(),
                List.of(),
                Map.of(),
                evidence(sourceId));
        return RecipeProviderSnapshot.available(
                sourceId, DataCompleteness.COMPLETE, List.of(provisional), List.of());
    }

    public static ToolInvocationContext context(RecipeProviderSnapshot... providers) {
        List<RecipeProviderSnapshot> providerList = List.of(providers);
        List<RecipeEntrySnapshot> recipes =
                providerList.stream().flatMap(value -> value.recipes().stream()).toList();
        RecipeSnapshot snapshot = new RecipeSnapshot(
                evidence("openallay:recipe_catalog"),
                recipes,
                providerList,
                List.of(),
                List.of());
        return new ToolInvocationContext(
                "recipe-viewer-extension-test",
                Instant.EPOCH,
                new CallerSnapshot(CallerKind.CONSOLE, null, "Fixture", true),
                Optional.empty(),
                Optional.empty(),
                Optional.of(snapshot),
                Optional.empty(),
                new ContextMetrics(0, recipes.size(), 0, 0, 0));
    }
}
