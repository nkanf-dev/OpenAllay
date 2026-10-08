package dev.openallay.recipe;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.RecipeEntrySnapshot;
import dev.openallay.context.RecipeReference;
import dev.openallay.context.RecipeSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

public final class RecipeCatalogMerger {
    private static final Comparator<RecipeEntrySnapshot> RECIPE_ORDER = Comparator
            .comparingInt((RecipeEntrySnapshot value) -> authorityRank(value.evidence().authority()))
            .thenComparing(value -> value.reference().sourceId())
            .thenComparing(value -> value.reference().recipeId());

    public RecipeSnapshot merge(
            EvidenceMetadata evidence,
            RecipeVisibilityPolicy visibility,
            List<RecipeProviderSnapshot> providers) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(visibility, "visibility");
        List<RecipeProviderSnapshot> sourceSnapshots = dev.openallay.util.Java8Collections.listCopyOf(providers);
        HashSet<String> sourceIds = new HashSet<>();
        sourceSnapshots.forEach(provider -> {
            if (!sourceIds.add(provider.sourceId())) {
                throw new IllegalArgumentException("duplicate recipe provider " + provider.sourceId());
            }
        });

        List<RecipeEntrySnapshot> recipes = dev.openallay.util.Java8Collections.toList(sourceSnapshots.stream()
                .filter(provider -> provider.state() == RecipeProviderState.AVAILABLE)
                .flatMap(provider -> provider.recipes().stream())
                .filter(recipe -> visibility == RecipeVisibilityPolicy.ALL_KNOWN
                        || recipe.unlockState() == RecipeUnlockState.UNLOCKED)
                .sorted(RECIPE_ORDER));

        TreeMap<String, List<RecipeEntrySnapshot>> byFingerprint = new TreeMap<>();
        recipes.forEach(recipe -> byFingerprint
                .computeIfAbsent(RecipeCanonicalizer.semanticFingerprint(recipe), ignored -> new ArrayList<>())
                .add(recipe));
        List<RecipeSemanticGroup> groups = dev.openallay.util.Java8Collections.toList(byFingerprint.entrySet().stream()
                .map(entry -> semanticGroup(entry.getKey(), entry.getValue())));

        TreeMap<String, List<RecipeEntrySnapshot>> byId = new TreeMap<>();
        recipes.forEach(recipe -> byId.computeIfAbsent(recipe.id(), ignored -> new ArrayList<>())
                .add(recipe));
        List<RecipeCatalogDiagnostic> diagnostics = dev.openallay.util.Java8Collections.toList(byId.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .map(RecipeCanonicalizer::semanticFingerprint)
                        .distinct()
                        .count() > 1)
                .map(entry -> new RecipeCatalogDiagnostic(
                        "recipe_id_conflict",
                        entry.getKey(),
                        dev.openallay.util.Java8Collections.toList(entry.getValue().stream()
                                .sorted(RECIPE_ORDER)
                                .map(RecipeEntrySnapshot::reference)),
                        "Recipe id resolves to different normalized contents")));

        return new RecipeSnapshot(
                withCompleteness(evidence, completeness(sourceSnapshots)),
                recipes,
                sourceSnapshots,
                groups,
                diagnostics);
    }

    private static RecipeSemanticGroup semanticGroup(
            String fingerprint, List<RecipeEntrySnapshot> variants) {
        List<RecipeEntrySnapshot> ordered = dev.openallay.util.Java8Collections.toList(variants.stream().sorted(RECIPE_ORDER));
        return new RecipeSemanticGroup(
                fingerprint,
                ordered.get(0),
                dev.openallay.util.Java8Collections.toList(ordered.stream().map(RecipeEntrySnapshot::reference)),
                dev.openallay.util.Java8Collections.toList(ordered.stream().map(RecipeEntrySnapshot::evidence)));
    }

    private static DataCompleteness completeness(List<RecipeProviderSnapshot> providers) {
        if (providers.isEmpty()
                || providers.stream().noneMatch(provider ->
                        provider.state() == RecipeProviderState.AVAILABLE)) {
            return DataCompleteness.UNKNOWN;
        }
        boolean complete = providers.stream().allMatch(provider ->
                provider.state() == RecipeProviderState.AVAILABLE
                        && provider.completeness() == DataCompleteness.COMPLETE);
        return complete ? DataCompleteness.COMPLETE : DataCompleteness.PARTIAL;
    }

    private static EvidenceMetadata withCompleteness(
            EvidenceMetadata evidence, DataCompleteness completeness) {
        return new EvidenceMetadata(
                evidence.authority(),
                completeness,
                evidence.capturedAt(),
                evidence.sourceId(),
                evidence.provenance(),
                evidence.gameVersion(),
                evidence.loader(),
                evidence.details());
    }

    private static int authorityRank(DataAuthority authority) {
        {
int $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((authority)) {
case SERVER_AUTHORITATIVE:
{
$oaSwitch0_exit_result = 0; break $oaSwitch0_exit;
}
case CLIENT_VISIBLE:
{
$oaSwitch0_exit_result = 1; break $oaSwitch0_exit;
}
case INTEGRATION_API:
{
$oaSwitch0_exit_result = 2; break $oaSwitch0_exit;
}
case RESOURCE_ASSET:
{
$oaSwitch0_exit_result = 3; break $oaSwitch0_exit;
}
case DETERMINISTIC_TEST:
{
$oaSwitch0_exit_result = 4; break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch0_exit_result;
}
    }
}
