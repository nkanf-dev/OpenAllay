package dev.openallay.script.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.knowledge.KnowledgeSnapshot;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.extension.JavascriptDataModule;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.schema.HostSchema;
import dev.openallay.testing.JavascriptAgentTestFixtures;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class MinecraftAgentHostGraphTest {
    @Test
    void readsOriginalRegistryReferencesWithoutResolvingUnusedRoots() {
        var context = JavascriptAgentTestFixtures.context("direct-host-graph");
        AtomicInteger knowledgeCaptures = new AtomicInteger();
        AtomicInteger extensionCaptures = new AtomicInteger();
        JavascriptDataModuleRegistry extensions = new JavascriptDataModuleRegistry();
        extensions.register("test", List.of(new JavascriptDataModule() {
            @Override public String id() { return "test:direct"; }

            @Override public java.lang.reflect.Type valueType() { return ModuleRecord.class; }

            @Override
            public Snapshot capture(dev.openallay.context.ToolInvocationContext ignored) {
                extensionCaptures.incrementAndGet();
                return new Snapshot(
                        new ModuleRecord("retained"),
                        List.of(context.registries().orElseThrow().evidence()));
            }
        }));
        MinecraftAgentHostGraph graph = new MinecraftAgentHostGraph(
                context,
                () -> {
                    knowledgeCaptures.incrementAndGet();
                    return KnowledgeSnapshot.empty();
                },
                extensions);

        var selected = graph.open();
        assertEquals(List.of(), evidence(selected));
        assertEquals(0, knowledgeCaptures.get());
        assertEquals(0, extensionCaptures.get());
        @SuppressWarnings("unchecked")
        List<dev.openallay.context.RegistryEntrySnapshot> items =
                (List<dev.openallay.context.RegistryEntrySnapshot>) selected.get("items");
        var expected = context.registries().orElseThrow().entries().stream()
                .filter(entry -> entry.kind().equals("item"))
                .findFirst().orElseThrow();
        assertSame(expected, items.getFirst());
        assertEquals(List.of(context.registries().orElseThrow().evidence()), evidence(selected));
        assertEquals(0, knowledgeCaptures.get());
        assertEquals(0, extensionCaptures.get());
        selected.get("items");
        selected.get("registryEntries");
        assertEquals(1, selected.sources().size());


        var next = graph.open();
        assertEquals(List.of(), evidence(next));
        next.get("recipes");
        assertTrue(evidence(next).contains(context.recipes().orElseThrow().evidence()));
        assertEquals(List.of(context.recipes().orElseThrow().evidence()), evidence(next));

        var all = graph.open();
        int entries = 0;
        for (String root : all.keySet()) {
            assertTrue(!root.isBlank());
            entries++;
        }
        assertTrue(entries > 0);
        assertEquals(List.of(), evidence(all));
        all.get("knowledge");
        all.get("knowledge");
        assertEquals(1, knowledgeCaptures.get());
        assertTrue(evidence(all).stream().anyMatch(value -> value.sourceId().equals("openallay:knowledge_registry")));
        all.get("extensions");
        all.get("extensions");
        assertEquals(1, extensionCaptures.get());
        assertTrue(evidence(all).contains(context.registries().orElseThrow().evidence()));
    }

    @Test
    void metadataAndCapabilityRootsDoNotCreateEvidence() {
        var context = JavascriptAgentTestFixtures.context("metadata-roots");
        MinecraftAgentHostGraph graph = new MinecraftAgentHostGraph(context);
        var selection = graph.open();
        selection.get("capabilities");
        selection.get("extensionCatalog");
        assertTrue(selection.sources().isEmpty());
        assertTrue(selection.sources().isEmpty());
    }

    @Test
    void exposesCompleteCatalogMetadataAndUnifiedRegistryRows() {
        var context = JavascriptAgentTestFixtures.context("catalog-metadata");
        MinecraftAgentHostGraph graph = new MinecraftAgentHostGraph(context);
        Map<String, Object> selected = graph.open();

        MinecraftAgentHostGraph.RegistryCatalog registries =
                assertInstanceOf(
                        MinecraftAgentHostGraph.RegistryCatalog.class,
                        selected.get("registries"));
        assertEquals(context.registries().orElseThrow().entries().size(), registries.entryCount());
        assertEquals(6, ((List<?>) selected.get("registryEntries")).size());

        MinecraftAgentHostGraph.RecipeCatalogView recipes =
                assertInstanceOf(
                        MinecraftAgentHostGraph.RecipeCatalogView.class,
                        selected.get("recipeCatalog"));
        assertEquals(context.recipes().orElseThrow().recipes().size(), recipes.recipeCount());
        assertEquals(context.recipes().orElseThrow().providers(), List.of());
        assertEquals(context.recipes().orElseThrow().groups(), recipes.groups());
        assertEquals(context.recipes().orElseThrow().diagnostics(), recipes.diagnostics());

        MinecraftAgentHostGraph.KnowledgeCatalog knowledge =
                assertInstanceOf(
                        MinecraftAgentHostGraph.KnowledgeCatalog.class,
                        selected.get("knowledgeCatalog"));
        assertEquals(0, knowledge.documentCount());
        assertFalse(knowledge.evidence().isEmpty());

        assertSame(context.observableGameState().orElseThrow(), selected.get("game"));
    }

    @Test
    void declaresUnavailableRootsAndExtensionSchemasWithoutCapturingValues() {
        AtomicInteger captures = new AtomicInteger();
        JavascriptDataModuleRegistry extensions = new JavascriptDataModuleRegistry();
        extensions.register("test-provider", List.of(new JavascriptDataModule() {
            @Override public String id() { return "test:declared"; }

            @Override public java.lang.reflect.Type valueType() { return ModuleRecord.class; }

            @Override public String summary() { return "Declared test module"; }

            @Override
            public Snapshot capture(dev.openallay.context.ToolInvocationContext ignored) {
                captures.incrementAndGet();
                return new Snapshot(
                        new ModuleRecord("value"),
                        List.of(dev.openallay.testing.GroundedTestFixtures.serverEvidence()));
            }
        }));
        var sparse = dev.openallay.context.ToolInvocationContext.developmentConsole("sparse");
        var descriptorOnly =
                MinecraftAgentHostGraph.describeRequest(sparse, extensions);
        assertEquals(0, captures.get());
        MinecraftAgentHostGraph graph = new MinecraftAgentHostGraph(
                sparse, KnowledgeSnapshot::empty, extensions);

        var player = descriptorOnly.describe("player").orElseThrow();
        assertEquals(
                dev.openallay.script.schema.HostRootDescriptor.Availability.UNAVAILABLE,
                player.availability());
        assertEquals(
                "list",
                graph.schemaCatalog()
                        .describe("game.mods.installed")
                        .orElseThrow()
                        .schema()
                        .kind());
        assertEquals(0, captures.get());

        @SuppressWarnings("unchecked")
        List<JavascriptDataModuleRegistry.Descriptor> catalog =
                (List<JavascriptDataModuleRegistry.Descriptor>)
                        graph.open().get("extensionCatalog");
        assertEquals("test-provider", catalog.getFirst().provider());
        assertInstanceOf(HostSchema.RecordValue.class, catalog.getFirst().schema());
        assertEquals(0, captures.get());
    }

    @Test
    void exposesAllCapturedRootsWithoutDeclarationsAndOnlyFailsMissingDataOnRead() {
        var context = JavascriptAgentTestFixtures.context("all-lazy-roots");
        MinecraftAgentHostGraph graph = new MinecraftAgentHostGraph(context);
        var data = graph.open();
        assertTrue(data.containsKey("items"));
        assertTrue(data.containsKey("player"));
        assertTrue(data.sources().isEmpty());
        assertSame(context.player().orElseThrow(), data.get("player"));
        assertSame(context.observableGameState().orElseThrow(), data.get("game"));
        assertFalse(data.containsKey("unknown"));
        assertEquals(null, data.get("unknown"));
    }

    @Test
    void declaredUnavailableDataNeverBecomesUndefinedOrAnEmptyDataset() {
        MinecraftAgentHostGraph graph = new MinecraftAgentHostGraph(
                dev.openallay.context.ToolInvocationContext.developmentConsole("missing-snapshots"));
        var data = graph.open();
        assertTrue(data.containsKey("player"));
        assertTrue(data.sources().isEmpty());
        for (String root : List.of("player", "game", "items", "recipes")) {
            JavascriptExecutionException failure = assertThrows(
                    JavascriptExecutionException.class, () -> data.get(root));
            assertEquals("javascript_root_unavailable", failure.code());
            assertTrue(failure.getMessage().contains("mc." + root + " was not captured"));
            assertTrue(failure.getMessage().contains("Unavailable data is not an empty dataset"));
            assertFalse(graph.schemaCatalog().availableRootNames().contains(root));
        }
        assertTrue(data.sources().isEmpty());
    }

    @Test
    void declaredOnlyCatalogUsesRequestScopedAvailabilityWithoutAContext() {
        var catalog = MinecraftAgentHostGraph.declaredOnlyCatalog();

        assertEquals(
                dev.openallay.script.schema.HostRootDescriptor.Availability.REQUEST_SCOPED,
                catalog.describe("game.mods.installed").orElseThrow().availability());
        assertEquals(
                "list",
                catalog.describe("recipeCatalog.providers").orElseThrow().schema().kind());
    }

    private static List<dev.openallay.context.EvidenceMetadata> evidence(
            MinecraftAgentHostGraph.InvocationData data) {
        return data.sources().stream().map(dev.openallay.context.SourceObservation::evidence).toList();
    }

    private record ModuleRecord(String value) {}
}
