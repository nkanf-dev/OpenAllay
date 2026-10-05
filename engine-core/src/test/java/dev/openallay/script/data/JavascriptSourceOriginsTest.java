package dev.openallay.script.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.RecipeEntrySnapshot;
import dev.openallay.context.RecipeSnapshot;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.knowledge.KnowledgeDocument;
import dev.openallay.knowledge.KnowledgeKind;
import dev.openallay.knowledge.KnowledgeSnapshot;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.workspace.AgentResultWorkspaceRegistry;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.testing.JavascriptAgentTestFixtures;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.builtin.RunJavascriptTool;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class JavascriptSourceOriginsTest {
    @Test
    void recipeCountAndIdsRetainDistinctCatalogAndProviderOriginsWithoutPerRowCopies() {
        var base = JavascriptAgentTestFixtures.context("mixed-recipe-origins");
        EvidenceMetadata catalog = evidence(DataAuthority.CLIENT_VISIBLE, "test:catalog", 0);
        EvidenceMetadata firstServer = evidence(DataAuthority.SERVER_AUTHORITATIVE, "test:server", 1);
        EvidenceMetadata integration = evidence(DataAuthority.INTEGRATION_API, "test:viewer", 2);
        RecipeEntrySnapshot sample = base.recipes().orElseThrow().recipes().getFirst();
        List<RecipeEntrySnapshot> recipes = new ArrayList<>();
        recipes.add(withEvidence(sample, firstServer));
        recipes.add(withEvidence(sample, integration));
        for (int second = 3; second <= 1000; second++) {
            recipes.add(withEvidence(sample, evidence(DataAuthority.SERVER_AUTHORITATIVE, "test:server", second)));
        }
        var context = new ToolInvocationContext(base.correlationId(), base.capturedAt(), base.caller(),
                base.player(), base.registries(), Optional.of(new RecipeSnapshot(catalog, recipes)),
                base.observableGameState(), base.metrics());
        var tool = tool(KnowledgeSnapshot.empty());
        try {
            var success = assertInstanceOf(ToolResult.Success.class,
                    tool.invokeAsync(context, new RunJavascriptTool.Input(
                            "return {count: mc.recipes.length, ids: mc.recipes.slice(0,2).map(recipe => recipe.id)};",
                            List.of()), new CancellationSignal()).join());
            var output = (RunJavascriptTool.Output) success.value();
            assertEquals(1000, output.preview().getAsJsonObject().get("count").getAsInt());
            assertEquals(2, output.preview().getAsJsonObject().getAsJsonArray("ids").size());
            assertEquals(List.of(catalog, firstServer, integration), output.sources().stream()
                    .map(source -> source.evidence()).toList());
            assertSame(firstServer, output.sources().get(1).evidence());
            assertEquals(Instant.EPOCH.plusSeconds(1000), output.sources().get(1).lastCapturedAt());
            assertTrue(output.modelText().contains("input coverage:"));
        } finally {
            tool.closeRequestScope(context.correlationId());
        }
    }

    @Test
    void documentIdsRetainActualDocumentOriginsAlongsideTheCatalogOrigin() {
        var context = JavascriptAgentTestFixtures.context("mixed-document-origins");
        EvidenceMetadata catalog = evidence(DataAuthority.CLIENT_VISIBLE, "test:catalog", 0);
        EvidenceMetadata server = evidence(DataAuthority.SERVER_AUTHORITATIVE, "test:server", 1);
        EvidenceMetadata integration = evidence(DataAuthority.INTEGRATION_API, "test:guide", 2);
        var knowledge = new KnowledgeSnapshot(List.of(document("first", server), document("second", integration),
                document("third", evidence(DataAuthority.INTEGRATION_API, "test:guide", 9))),
                Instant.EPOCH, List.of(catalog));
        var tool = tool(knowledge);
        try {
            var success = assertInstanceOf(ToolResult.Success.class,
                    tool.invokeAsync(context, new RunJavascriptTool.Input(
                            "return mc.knowledge.map(document => document.documentId);", List.of()),
                            new CancellationSignal()).join());
            var output = (RunJavascriptTool.Output) success.value();
            assertEquals(List.of("first", "second", "third"), dev.openallay.json.JsonReaders.elements(output.preview().getAsJsonArray()).stream()
                    .map(value -> value.getAsString()).toList());
            assertEquals(List.of(catalog, server, integration), output.sources().stream()
                    .map(source -> source.evidence()).toList());
            assertEquals(Instant.EPOCH.plusSeconds(9), output.sources().get(2).lastCapturedAt());
        } finally {
            tool.closeRequestScope(context.correlationId());
        }
    }

    private static RunJavascriptTool tool(KnowledgeSnapshot knowledge) {
        return new RunJavascriptTool(new RhinoJavascriptRuntime(),
                invocation -> new MinecraftAgentHostGraph(invocation, () -> knowledge),
                new AgentResultWorkspaceRegistry(), new JavascriptResultPresenter());
    }

    private static EvidenceMetadata evidence(DataAuthority authority, String source, long second) {
        return new EvidenceMetadata(authority, DataCompleteness.COMPLETE, Instant.EPOCH.plusSeconds(second),
                source, "test:captured", "26.2", "fabric", Map.of("minecraft:dimension", "minecraft:overworld"));
    }

    private static KnowledgeDocument document(String id, EvidenceMetadata evidence) {
        return new KnowledgeDocument(evidence.sourceId(), id, KnowledgeKind.GUIDE_ENTRY, id, "Captured text",
                "test", Set.of(), Set.of(), null, true, evidence.provenance(), evidence);
    }

    private static RecipeEntrySnapshot withEvidence(RecipeEntrySnapshot value, EvidenceMetadata evidence) {
        return new RecipeEntrySnapshot(value.reference(), value.id(), value.type(), value.layout(), value.workstation(),
                value.ingredients(), value.catalysts(), value.fluids(), value.outputs(), value.byproducts(),
                value.processing(), value.conditions(), value.extensions(), value.unlockState(), evidence);
    }
}
