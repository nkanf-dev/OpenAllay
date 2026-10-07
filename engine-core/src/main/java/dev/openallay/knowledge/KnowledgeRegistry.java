package dev.openallay.knowledge;

import dev.openallay.knowledge.search.KnowledgeIndex;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class KnowledgeRegistry {
    private volatile PublishedKnowledge published = published(KnowledgeSnapshot.empty());
    private volatile List<KnowledgeDiagnostic> diagnostics = List.of();
    private volatile KnowledgeSourceSnapshot sourceSnapshot = KnowledgeSourceSnapshot.notLoaded();
    private long sourceGeneration;
    private List<KnowledgeSourceProvider> primaryProviders = List.of();
    private List<KnowledgeSourceProvider> supplementalProviders = List.of();
    private Set<String> disabledPrimaryProviderIds = Set.of();

    public synchronized boolean reload(List<? extends KnowledgeSourceProvider> providers) {
        List<KnowledgeSourceProvider> candidate = List.copyOf(providers);
        if (!load(enabledPrimary(candidate, disabledPrimaryProviderIds), supplementalProviders)) {
            return false;
        }
        primaryProviders = candidate;
        return true;
    }

    /**
     * Replaces settings-owned providers without losing the current resource-owned providers. A
     * rejected candidate retains both the prior provider set and the last valid snapshot.
     */
    public synchronized boolean replaceSupplementalProviders(
            List<? extends KnowledgeSourceProvider> providers) {
        return replaceProviderConfiguration(disabledPrimaryProviderIds, providers);
    }

    /** Applies Tool-owned enablement to resource/integration providers by their stable source ID. */
    public synchronized boolean replaceDisabledPrimaryProviders(Set<String> sourceIds) {
        return replaceProviderConfiguration(sourceIds, supplementalProviders);
    }

    /** Atomically validates and publishes both Tool-owned provider selections. */
    public synchronized boolean replaceProviderConfiguration(
            Set<String> sourceIds,
            List<? extends KnowledgeSourceProvider> supplemental) {
        Set<String> candidate = Set.copyOf(sourceIds);
        if (candidate.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("Disabled knowledge source IDs must not be blank");
        }
        List<KnowledgeSourceProvider> supplementalCandidate = List.copyOf(supplemental);
        if (!load(enabledPrimary(primaryProviders, candidate), supplementalCandidate)) {
            return false;
        }
        disabledPrimaryProviderIds = candidate;
        supplementalProviders = supplementalCandidate;
        return true;
    }

    private static List<KnowledgeSourceProvider> enabledPrimary(
            List<KnowledgeSourceProvider> providers, Set<String> disabled) {
        return providers.stream()
                .filter(provider -> !disabled.contains(provider.sourceId()))
                .toList();
    }

    private boolean load(
            List<? extends KnowledgeSourceProvider> primary,
            List<? extends KnowledgeSourceProvider> supplemental) {
        List<KnowledgeSourceProvider> providers = new ArrayList<>(primary.size() + supplemental.size());
        providers.addAll(primary);
        providers.addAll(supplemental);
        List<KnowledgeDocument> documents = new ArrayList<>();
        List<KnowledgeDiagnostic> nextDiagnostics = new ArrayList<>();
        List<dev.openallay.context.EvidenceMetadata> evidence = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        String failureCode = "provider_failure";
        String activeSource = "knowledge";
        List<KnowledgeSourceSnapshot.Source> sourceStates = new ArrayList<>();
        String generation = "knowledge-" + (sourceGeneration + 1);
        try {
            for (KnowledgeSourceProvider provider : providers) {
                activeSource = provider.sourceId();
                KnowledgeLoad load = provider.load();
                sourceStates.add(sourceState(provider.sourceId(), load, generation));
                nextDiagnostics.addAll(load.diagnostics());
                evidence.addAll(load.evidence());
                for (KnowledgeDocument document : load.documents()) {
                    if (!document.sourceId().equals(provider.sourceId())
                            && !document.sourceId().startsWith(provider.sourceId() + ":")) {
                        throw new IllegalArgumentException("Provider " + provider.sourceId()
                                + " emitted document for " + document.sourceId());
                    }
                    if (!keys.add(document.key())) {
                        throw new IllegalArgumentException("Duplicate knowledge document " + document.key());
                    }
                    if (document.visible()) {
                        documents.add(document);
                    }
                }
            }
            documents.sort(java.util.Comparator.comparing(KnowledgeDocument::key));
            KnowledgeSnapshot nextSnapshot = new KnowledgeSnapshot(
                    documents, Instant.now(), evidence.stream().distinct().toList());
            failureCode = "index_failure";
            PublishedKnowledge next = published(nextSnapshot);
            published = next;
            diagnostics = List.copyOf(nextDiagnostics);
            sourceGeneration++;
            sourceSnapshot = new KnowledgeSourceSnapshot(true, false, null, sourceStates);
            return true;
        } catch (Exception failure) {
            String source = activeSource;
            List<KnowledgeSourceSnapshot.Source> retained = new ArrayList<>();
            for (KnowledgeSourceSnapshot.Source prior : sourceSnapshot.sources()) {
                if (prior.generation() != null) {
                    retained.add(new KnowledgeSourceSnapshot.Source(
                            prior.sourceId(), prior.generation(), KnowledgeSourceSnapshot.State.PARTIAL,
                            prior.itemCount(), failureCode));
                }
            }
            boolean hasRetained = sourceGeneration > 0;
            if (retained.stream().noneMatch(value -> value.sourceId().equals(source))) {
                retained.add(new KnowledgeSourceSnapshot.Source(
                        source, null, KnowledgeSourceSnapshot.State.FAILED, null, failureCode));
            }
            sourceSnapshot = new KnowledgeSourceSnapshot(true, hasRetained, failureCode, retained);
            diagnostics = List.of(new KnowledgeDiagnostic(
                    source,
                    failureCode,
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(),
                    source));
            return false;
        }
    }

    private static KnowledgeSourceSnapshot.Source sourceState(
            String sourceId, KnowledgeLoad load, String generation) {
        int count = (int) load.documents().stream().filter(KnowledgeDocument::visible).count();
        boolean unknown = load.evidence().isEmpty() || load.evidence().stream().anyMatch(value ->
                value.completeness() == dev.openallay.context.DataCompleteness.UNKNOWN);
        boolean partial = unknown || !load.diagnostics().isEmpty() || load.evidence().stream().anyMatch(value ->
                value.completeness() != dev.openallay.context.DataCompleteness.COMPLETE);
        KnowledgeSourceSnapshot.State state = unknown && count == 0
                ? KnowledgeSourceSnapshot.State.UNAVAILABLE
                : partial ? KnowledgeSourceSnapshot.State.PARTIAL : KnowledgeSourceSnapshot.State.AVAILABLE;
        String code = load.diagnostics().isEmpty()
                ? (partial ? "knowledge_incomplete" : null) : load.diagnostics().get(0).code();
        return new KnowledgeSourceSnapshot.Source(
                sourceId, state == KnowledgeSourceSnapshot.State.UNAVAILABLE ? null : generation,
                state, unknown && count == 0 ? null : count, code);
    }

    /** Drops connection-captured data/handles without loading providers or changing saved policy. */
    public synchronized void clearConnectionState() {
        published = published(KnowledgeSnapshot.empty());
        diagnostics = List.of();
        sourceSnapshot = KnowledgeSourceSnapshot.notLoaded();
        sourceGeneration = 0;
        primaryProviders = List.of();
        // Supplemental configuration and explicit primary-source deny choices survive reconnect.
    }

    /** Reading this immutable status does not load providers or inspect Game state. */
    public KnowledgeSourceSnapshot sourceSnapshot() {
        return sourceSnapshot;
    }

    public KnowledgeSnapshot snapshot() {
        return published.snapshot();
    }

    public KnowledgeSearch search(String query, Integer limit) {
        PublishedKnowledge current = published;
        return new KnowledgeSearch(
                current.index().search(query, limit),
                current.snapshot().evidence());
    }

    public List<KnowledgeDiagnostic> diagnostics() {
        return diagnostics;
    }

    private static PublishedKnowledge published(KnowledgeSnapshot snapshot) {
        return new PublishedKnowledge(snapshot, new KnowledgeIndex(snapshot));
    }

    @dev.openallay.value.ValueType(PublishedKnowledge.ValueSchemaProvider.class)
private static final class PublishedKnowledge {
    private final KnowledgeSnapshot snapshot;
    private final KnowledgeIndex index;
    private PublishedKnowledge(KnowledgeSnapshot snapshot, KnowledgeIndex index) {
        this.snapshot = snapshot;
        this.index = index;
    }
    public KnowledgeSnapshot snapshot() { return snapshot; }
    public KnowledgeIndex index() { return index; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PublishedKnowledge)) return false;
        PublishedKnowledge that = (PublishedKnowledge) other;
        return java.util.Objects.equals(snapshot, that.snapshot) && java.util.Objects.equals(index, that.index);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(snapshot);
        hash = 31 * hash + java.util.Objects.hashCode(index);
        return hash;
    }
    @Override public String toString() { return "PublishedKnowledge[snapshot=" + snapshot + ", index=" + index + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PublishedKnowledge> schema() {
            return new dev.openallay.value.ValueSchema<>(PublishedKnowledge.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PublishedKnowledge>>asList(new dev.openallay.value.ValueSchema.Component<>(PublishedKnowledge.class, "snapshot", PublishedKnowledge::snapshot), new dev.openallay.value.ValueSchema.Component<>(PublishedKnowledge.class, "index", PublishedKnowledge::index)), arguments -> new PublishedKnowledge((KnowledgeSnapshot) arguments[0], (KnowledgeIndex) arguments[1]));
        }
    }
}
}
