package dev.openallay.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class KnowledgeRegistryTest {
    @Test
    void failedProviderDoesNotReplaceLastGoodSnapshot() {
        KnowledgeRegistry registry = new KnowledgeRegistry();
        assertTrue(registry.reload(List.of(provider("guide", document("first")))));
        assertFalse(registry.reload(List.of(new KnowledgeSourceProvider() {
            @Override public String sourceId() { return "broken"; }
            @Override public KnowledgeLoad load() { throw new IllegalStateException("no data"); }
        })));

        assertEquals("first", registry.snapshot().documents().getFirst().title());
        assertEquals("provider_failure", registry.diagnostics().getFirst().code());
        assertEquals("first", registry.search("minecraft:iron_ingot", null)
                .results().getFirst().title());
    }

    @Test
    void searchResultsAndEvidenceComeFromOnePublishedGeneration() {
        KnowledgeRegistry registry = new KnowledgeRegistry();
        KnowledgeDocument first = document("first");
        assertTrue(registry.reload(List.of(provider("guide", first))));

        KnowledgeSearch search = registry.search("minecraft:iron_ingot", null);

        assertEquals("first", search.results().getFirst().title());
        assertEquals(first.evidence(), search.evidence().getFirst());
    }

    @Test
    void rejectsDuplicateIdentityAndExcludesInvisibleDocuments() {
        KnowledgeRegistry registry = new KnowledgeRegistry();
        KnowledgeDocument visible = document("visible");
        KnowledgeDocument hidden = new KnowledgeDocument(
                "guide", "hidden", KnowledgeKind.QUEST, "hidden", "secret", "example",
                Set.of(), Set.of(), null, false, "fixture");
        assertTrue(registry.reload(List.of(provider("guide", visible, hidden))));
        assertEquals(1, registry.snapshot().documents().size());
        assertFalse(registry.reload(List.of(provider("guide", visible, visible))));
        assertEquals(1, registry.snapshot().documents().size());
    }

    @Test
    void supplementalProvidersSurvivePrimaryReloadAndRollbackIndependently() {
        KnowledgeRegistry registry = new KnowledgeRegistry();
        KnowledgeDocument primary = document("guide", "primary", "Primary");
        KnowledgeDocument supplemental = document("notes", "local", "Local");

        assertTrue(registry.reload(List.of(provider("guide", primary))));
        assertTrue(registry.replaceSupplementalProviders(List.of(provider("notes", supplemental))));
        assertEquals(List.of("Primary", "Local"), registry.snapshot().documents().stream()
                .map(KnowledgeDocument::title)
                .toList());

        KnowledgeDocument refreshed = document("guide", "refreshed", "Refreshed");
        assertTrue(registry.reload(List.of(provider("guide", refreshed))));
        assertEquals(List.of("Refreshed", "Local"), registry.snapshot().documents().stream()
                .map(KnowledgeDocument::title)
                .toList());

        assertFalse(registry.replaceSupplementalProviders(List.of(new KnowledgeSourceProvider() {
            @Override public String sourceId() { return "broken"; }
            @Override public KnowledgeLoad load() { throw new IllegalStateException("no data"); }
        })));
        assertEquals(List.of("Refreshed", "Local"), registry.snapshot().documents().stream()
                .map(KnowledgeDocument::title)
                .toList());
    }

    @Test
    void toolOwnedPrimaryProviderPolicySurvivesResourceReload() {
        KnowledgeRegistry registry = new KnowledgeRegistry();
        assertTrue(registry.reload(List.of(
                provider("patchouli", document("patchouli", "entry", "Book")),
                provider("ftbquests", document("ftbquests", "quest", "Quest")))));

        assertTrue(registry.replaceDisabledPrimaryProviders(Set.of("patchouli")));
        assertEquals(List.of("Quest"), registry.snapshot().documents().stream()
                .map(KnowledgeDocument::title)
                .toList());

        assertTrue(registry.reload(List.of(
                provider("patchouli", document("patchouli", "new", "New Book")),
                provider("ftbquests", document("ftbquests", "new", "New Quest")))));
        assertEquals(List.of("New Quest"), registry.snapshot().documents().stream()
                .map(KnowledgeDocument::title)
                .toList());
    }

    @Test
    void sourceStatusDistinguishesNotLoadedKnownEmptyAndPartialUnavailable() {
        KnowledgeRegistry registry = new KnowledgeRegistry();
        assertFalse(registry.sourceSnapshot().loaded());
        assertTrue(registry.reload(List.of(provider("empty"))));
        assertTrue(registry.sourceSnapshot().loaded());
        assertEquals(0, registry.sourceSnapshot().sources().getFirst().itemCount());
        assertEquals(KnowledgeSourceSnapshot.State.AVAILABLE,
                registry.sourceSnapshot().sources().getFirst().state());
        String generation = registry.sourceSnapshot().sources().getFirst().generation();
        assertTrue(registry.reload(List.of(new KnowledgeSourceProvider() {
            @Override public String sourceId() { return "missing_evidence"; }
            @Override public KnowledgeLoad load() { return KnowledgeLoad.of(List.of()); }
        })));
        assertEquals(KnowledgeSourceSnapshot.State.UNAVAILABLE,
                registry.sourceSnapshot().sources().getFirst().state());
        assertEquals(null, registry.sourceSnapshot().sources().getFirst().itemCount());
        assertTrue(registry.reload(List.of(new KnowledgeSourceProvider() {
            @Override public String sourceId() { return "guide"; }
            @Override public KnowledgeLoad load() {
                return new KnowledgeLoad(List.of(document("visible")),
                        List.of(new KnowledgeDiagnostic("guide", "parse_partial", "private body", "fixture")));
            }
        })));
        assertEquals(KnowledgeSourceSnapshot.State.PARTIAL,
                registry.sourceSnapshot().sources().getFirst().state());
        assertFalse(generation.equals(registry.sourceSnapshot().sources().getFirst().generation()));
        assertFalse(registry.sourceSnapshot().toString().contains("private body"));
        assertTrue(registry.reload(List.of(new KnowledgeSourceProvider() {
            @Override public String sourceId() { return "ftbquests"; }
            @Override public KnowledgeLoad load() {
                var evidence = new dev.openallay.context.EvidenceMetadata(
                        dev.openallay.context.DataAuthority.INTEGRATION_API,
                        dev.openallay.context.DataCompleteness.UNKNOWN,
                        java.time.Instant.EPOCH, "ftbquests:api", "ftbquests:bridge",
                        "fixture", "fixture", java.util.Map.of());
                return new KnowledgeLoad(List.of(),
                        List.of(new KnowledgeDiagnostic("ftbquests", "bridge_unavailable", "private", "fixture")),
                        List.of(evidence));
            }
        })));
        assertEquals(KnowledgeSourceSnapshot.State.UNAVAILABLE,
                registry.sourceSnapshot().sources().getFirst().state());
        assertEquals(null, registry.sourceSnapshot().sources().getFirst().itemCount());
        assertEquals(null, registry.sourceSnapshot().sources().getFirst().generation());
    }

    @Test
    void failureStatusRetainsOnlyPublishedCountsAndBlamesTheFailingProvider() {
        KnowledgeRegistry registry = new KnowledgeRegistry();
        assertTrue(registry.reload(List.of(provider("guide", document("first")))));
        String generation = registry.sourceSnapshot().sources().getFirst().generation();
        assertFalse(registry.reload(List.of(provider("empty"), new KnowledgeSourceProvider() {
            @Override public String sourceId() { return "broken"; }
            @Override public KnowledgeLoad load() { throw new IllegalStateException("private failure body"); }
        })));
        assertTrue(registry.sourceSnapshot().retained());
        assertEquals("broken", registry.diagnostics().getFirst().sourceId());
        assertEquals(generation, registry.sourceSnapshot().sources().getFirst().generation());
        assertEquals(1, registry.sourceSnapshot().sources().getFirst().itemCount());
        assertEquals(KnowledgeSourceSnapshot.State.PARTIAL,
                registry.sourceSnapshot().sources().getFirst().state());
        assertEquals(KnowledgeSourceSnapshot.State.FAILED,
                registry.sourceSnapshot().sources().getLast().state());
        assertFalse(registry.sourceSnapshot().toString().contains("private failure body"));
        assertTrue(registry.reload(List.of()));
        assertTrue(registry.sourceSnapshot().loaded());
        assertFalse(registry.sourceSnapshot().retained());
        assertTrue(registry.sourceSnapshot().sources().isEmpty());
    }

    @Test
    void disconnectDropsCapturedDocumentsAndCountsButPreservesConfiguredPolicyAndSupplementalProviders() {
        KnowledgeRegistry registry = new KnowledgeRegistry();
        assertTrue(registry.reload(List.of(
                provider("patchouli", document("patchouli", "a", "World A")),
                provider("ftbquests", document("ftbquests", "a", "Quest A")))));
        assertTrue(registry.replaceSupplementalProviders(List.of(
                provider("notes", document("notes", "saved", "Saved notes")))));
        assertTrue(registry.replaceDisabledPrimaryProviders(Set.of("ftbquests")));
        assertTrue(registry.sourceSnapshot().loaded());
        registry.clearConnectionState();
        assertFalse(registry.sourceSnapshot().loaded());
        assertTrue(registry.sourceSnapshot().sources().isEmpty());
        assertTrue(registry.snapshot().documents().isEmpty());
        assertTrue(registry.search("World A", null).results().isEmpty());
        assertTrue(registry.diagnostics().isEmpty());
        // A policy update cannot re-sample the previous world's retained primary handles.
        assertTrue(registry.replaceDisabledPrimaryProviders(Set.of("ftbquests")));
        assertEquals(List.of("Saved notes"), registry.snapshot().documents().stream()
                .map(KnowledgeDocument::title).toList());
        registry.clearConnectionState();
        assertFalse(registry.sourceSnapshot().loaded());
        // B stays unknown until its normal capture publishes its own providers.
        assertTrue(registry.reload(List.of(
                provider("patchouli", document("patchouli", "b", "World B")),
                provider("ftbquests", document("ftbquests", "b", "Quest B")))));
        assertEquals(List.of("Saved notes", "World B"), registry.snapshot().documents().stream()
                .map(KnowledgeDocument::title).toList());
        assertTrue(registry.sourceSnapshot().loaded());
        assertFalse(registry.sourceSnapshot().toString().contains("World A"));
    }

    private static KnowledgeSourceProvider provider(String id, KnowledgeDocument... documents) {
        return new KnowledgeSourceProvider() {
            @Override public String sourceId() { return id; }
            @Override public KnowledgeLoad load() {
                if (documents.length > 0) return KnowledgeLoad.of(List.of(documents));
                var evidence = new dev.openallay.context.EvidenceMetadata(
                        dev.openallay.context.DataAuthority.INTEGRATION_API,
                        dev.openallay.context.DataCompleteness.COMPLETE,
                        java.time.Instant.EPOCH, "test:" + id, "test:provider", "fixture", "fixture", java.util.Map.of());
                return new KnowledgeLoad(List.of(), List.of(), List.of(evidence));
            }
        };
    }

    private static KnowledgeDocument document(String title) {
        return document("guide", "entry", title);
    }

    private static KnowledgeDocument document(String source, String id, String title) {
        return new KnowledgeDocument(
                source, id, KnowledgeKind.GUIDE_ENTRY, title, "body", "example",
                Set.of("minecraft:iron_ingot"), Set.of(), null, true, "fixture");
    }
}
