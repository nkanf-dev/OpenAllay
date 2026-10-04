package dev.openallay.integration;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.RecipeEntrySnapshot;
import dev.openallay.context.RecipeSnapshot;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.recipe.RecipeProviderDiagnostic;
import dev.openallay.recipe.RecipeProviderSnapshot;
import dev.openallay.recipe.RecipeProviderState;
import dev.openallay.script.extension.JavascriptDataModule;
import dev.openallay.script.host.HostAccessException;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Projects one optional recipe viewer from the already-detached request recipe catalog.
 *
 * <p>Viewer APIs are called by the existing owning-thread recipe capture. This module only reads
 * the resulting immutable OpenAllay snapshots on the Agent worker.
 */
public final class RecipeViewerExtensionDataModule implements JavascriptDataModule {
    private final String id;
    private final String sourceId;
    private final String summary;
    private final boolean focusSupported;
    private final boolean navigationSupported;
    private final boolean exactNavigationSupported;

    public RecipeViewerExtensionDataModule(
            String id,
            String sourceId,
            String summary,
            boolean focusSupported,
            boolean navigationSupported,
            boolean exactNavigationSupported) {
        this.id = require(id, "id");
        this.sourceId = require(sourceId, "sourceId");
        this.summary = require(summary, "summary");
        this.focusSupported = focusSupported;
        this.navigationSupported = navigationSupported;
        this.exactNavigationSupported = exactNavigationSupported;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Type valueType() {
        return RecipeViewerExtensionSnapshot.class;
    }

    @Override
    public String summary() {
        return summary;
    }

    @Override
    public Snapshot capture(ToolInvocationContext context) {
        Objects.requireNonNull(context, "context");
        RecipeSnapshot catalog = context.recipes().orElseThrow(() -> new HostAccessException(
                "recipe_catalog_unavailable",
                "Recipe viewer data requires a captured recipe catalog"));
        RecipeProviderSnapshot provider = catalog.providers().stream()
                .filter(candidate -> candidate.sourceId().equals(sourceId))
                .findFirst()
                .orElse(null);

        LinkedHashSet<EvidenceMetadata> evidence = new LinkedHashSet<>();
        evidence.add(catalog.evidence());
        if (provider == null) {
            return new Snapshot(
                    unavailableProjection(),
                    List.copyOf(evidence));
        }

        TreeSet<String> categories = new TreeSet<>();
        List<RecipeViewerExtensionSnapshot.Recipe> recipes = provider.recipes().stream()
                .sorted(Comparator.comparing(recipe -> recipe.reference().recipeId()))
                .peek(recipe -> {
                    categories.add(recipe.type());
                    evidence.add(recipe.evidence());
                })
                .map(RecipeViewerExtensionDataModule::project)
                .toList();
        List<RecipeViewerExtensionSnapshot.Diagnostic> diagnostics =
                provider.diagnostics().stream()
                        .map(RecipeViewerExtensionDataModule::project)
                        .toList();
        RecipeViewerExtensionSnapshot value = new RecipeViewerExtensionSnapshot(
                sourceId,
                provider.state() == RecipeProviderState.AVAILABLE,
                provider.state(),
                provider.completeness(),
                provider.generation(),
                recipes.size(),
                List.copyOf(categories),
                recipes,
                focusSupported,
                navigationSupported,
                exactNavigationSupported,
                diagnostics);
        return new Snapshot(value, List.copyOf(evidence));
    }

    private RecipeViewerExtensionSnapshot unavailableProjection() {
        return new RecipeViewerExtensionSnapshot(
                sourceId,
                false,
                RecipeProviderState.UNAVAILABLE,
                dev.openallay.context.DataCompleteness.UNKNOWN,
                null,
                0,
                List.of(),
                List.of(),
                focusSupported,
                navigationSupported,
                exactNavigationSupported,
                List.of(new RecipeViewerExtensionSnapshot.Diagnostic(
                        "provider_not_captured",
                        "The recipe viewer did not contribute a provider snapshot to this request")));
    }

    private static RecipeViewerExtensionSnapshot.Recipe project(RecipeEntrySnapshot recipe) {
        return new RecipeViewerExtensionSnapshot.Recipe(
                recipe.reference().sourceId(),
                recipe.reference().generation(),
                recipe.reference().recipeId(),
                recipe.id(),
                recipe.type());
    }

    private static RecipeViewerExtensionSnapshot.Diagnostic project(
            RecipeProviderDiagnostic diagnostic) {
        return new RecipeViewerExtensionSnapshot.Diagnostic(
                diagnostic.code(), diagnostic.message());
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
