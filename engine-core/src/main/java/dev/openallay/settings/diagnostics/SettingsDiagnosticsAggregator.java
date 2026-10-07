package dev.openallay.settings.diagnostics;

import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.capability.CapabilityKind;
import dev.openallay.guide.GuideFailure;
import dev.openallay.guide.GuideHistoryPageState;
import dev.openallay.guide.GuideHistoryWindowSnapshot;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideSessionSnapshot;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.history.GuideHistoryActivity;
import dev.openallay.guide.ui.SemanticLayoutCache;
import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.settings.capability.CapabilitySettingsView;
import dev.openallay.settings.capability.RecipeSettingsView;
import dev.openallay.settings.diagnostics.SettingsDiagnosticCard.Domain;
import dev.openallay.settings.diagnostics.SettingsDiagnosticCard.FriendlyStatus;
import dev.openallay.settings.diagnostics.SettingsDiagnosticCard.Metric;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsSnapshot.DebugCapabilities;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsSnapshot.DebugContext;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsSnapshot.DebugGuide;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsSnapshot.DebugHistory;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsSnapshot.DebugModelProfile;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsSnapshot.DebugRequest;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsSnapshot.DebugSettingsDiagnostics;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsSnapshot.DebugSource;
import dev.openallay.settings.model.ModelConnectionResult;
import dev.openallay.settings.model.ModelProfileSettingsView;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/** Pure projection from immutable domain snapshots into friendly and redacted diagnostics. */
public final class SettingsDiagnosticsAggregator {
    public enum HistoryScopeKind {
        NONE,
        SINGLEPLAYER_WORLD,
        MULTIPLAYER_SERVER
    }

    public enum SourceState {
        AVAILABLE,
        PARTIAL,
        UNAVAILABLE,
        FAILED
    }

    @dev.openallay.value.ValueType(SourceStatus.ValueSchemaProvider.class)
public static final class SourceStatus {
    private final String sourceId;
    private final String generation;
    private final SourceState state;
    private final Integer itemCount;
    private final String failureCode;
    public SourceStatus(String sourceId, String generation, SourceState state, Integer itemCount, String failureCode) {

            sourceId = safeIdentifier(sourceId);
            Objects.requireNonNull(state, "state");
            if (itemCount != null && itemCount < 0) {
                throw new IllegalArgumentException("source item count must not be negative");
            }
            if ((state == SourceState.AVAILABLE || state == SourceState.PARTIAL)
                    && (generation == null || generation.isBlank())) {
                throw new IllegalArgumentException("available source requires a generation");
            }
            if ((state == SourceState.UNAVAILABLE || state == SourceState.FAILED)
                    && generation != null) {
                throw new IllegalArgumentException("unavailable source cannot expose a generation");
            }
            generation = generation == null ? null : safeIdentifier(generation);
            failureCode = failureCode == null ? null : safeCode(failureCode);
            if (state == SourceState.AVAILABLE && failureCode != null
                    || state != SourceState.AVAILABLE && failureCode == null) {
                throw new IllegalArgumentException(
                        "source state and failure diagnostic do not agree");
            }

        this.sourceId = sourceId;
        this.generation = generation;
        this.state = state;
        this.itemCount = itemCount;
        this.failureCode = failureCode;
    }
    public String sourceId() { return sourceId; }
    public String generation() { return generation; }
    public SourceState state() { return state; }
    public Integer itemCount() { return itemCount; }
    public String failureCode() { return failureCode; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SourceStatus)) return false;
        SourceStatus that = (SourceStatus) other;
        return java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(generation, that.generation) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(itemCount, that.itemCount) && java.util.Objects.equals(failureCode, that.failureCode);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(itemCount);
        hash = 31 * hash + java.util.Objects.hashCode(failureCode);
        return hash;
    }
    @Override public String toString() { return "SourceStatus[sourceId=" + sourceId + ", generation=" + generation + ", state=" + state + ", itemCount=" + itemCount + ", failureCode=" + failureCode + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SourceStatus> schema() {
            return new dev.openallay.value.ValueSchema<>(SourceStatus.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SourceStatus>>asList(new dev.openallay.value.ValueSchema.Component<>(SourceStatus.class, "sourceId", SourceStatus::sourceId), new dev.openallay.value.ValueSchema.Component<>(SourceStatus.class, "generation", SourceStatus::generation), new dev.openallay.value.ValueSchema.Component<>(SourceStatus.class, "state", SourceStatus::state), new dev.openallay.value.ValueSchema.Component<>(SourceStatus.class, "itemCount", SourceStatus::itemCount), new dev.openallay.value.ValueSchema.Component<>(SourceStatus.class, "failureCode", SourceStatus::failureCode)), arguments -> new SourceStatus((String) arguments[0], (String) arguments[1], (SourceState) arguments[2], (Integer) arguments[3], (String) arguments[4]));
        }
    }
}

    @dev.openallay.value.ValueType(DiagnosticsInputs.ValueSchemaProvider.class)
public static final class DiagnosticsInputs {
    private final long settingsGeneration;
    private final ModelProfileSettingsView models;
    private final CapabilitySettingsView capabilities;
    private final RecipeSettingsView recipes;
    private final Optional<GuideSnapshot> guide;
    private final GuideHistoryActivity historyActivity;
    private final HistoryScopeKind historyScopeKind;
    private final List<SourceStatus> sources;
    private final boolean sourcesKnown;
    private final boolean sourcesRetained;
    private final Long estimatedContextTokens;
    public DiagnosticsInputs(long settingsGeneration, ModelProfileSettingsView models, CapabilitySettingsView capabilities, RecipeSettingsView recipes, Optional<GuideSnapshot> guide, GuideHistoryActivity historyActivity, HistoryScopeKind historyScopeKind, List<SourceStatus> sources, boolean sourcesKnown, boolean sourcesRetained, Long estimatedContextTokens) {

            if (settingsGeneration < 0) {
                throw new IllegalArgumentException("diagnostic generation is invalid");
            }
            Objects.requireNonNull(models, "models");
            Objects.requireNonNull(capabilities, "capabilities");
            Objects.requireNonNull(recipes, "recipes");
            guide = Objects.requireNonNull(guide, "guide");
            Objects.requireNonNull(historyActivity, "historyActivity");
            Objects.requireNonNull(historyScopeKind, "historyScopeKind");
            sources = List.copyOf(sources);
            if (estimatedContextTokens != null && estimatedContextTokens < 0) {
                throw new IllegalArgumentException("Context estimate must not be negative");
            }
            if (!sourcesKnown && (!sources.isEmpty() || sourcesRetained)) {
                throw new IllegalArgumentException("Unknown sources cannot expose observed source state");
            }
            if (guide.isEmpty() != (historyScopeKind == HistoryScopeKind.NONE)) {
                throw new IllegalArgumentException("history scope kind must match Guide availability");
            }

        this.settingsGeneration = settingsGeneration;
        this.models = models;
        this.capabilities = capabilities;
        this.recipes = recipes;
        this.guide = guide;
        this.historyActivity = historyActivity;
        this.historyScopeKind = historyScopeKind;
        this.sources = sources;
        this.sourcesKnown = sourcesKnown;
        this.sourcesRetained = sourcesRetained;
        this.estimatedContextTokens = estimatedContextTokens;
    }
    public long settingsGeneration() { return settingsGeneration; }
    public ModelProfileSettingsView models() { return models; }
    public CapabilitySettingsView capabilities() { return capabilities; }
    public RecipeSettingsView recipes() { return recipes; }
    public Optional<GuideSnapshot> guide() { return guide; }
    public GuideHistoryActivity historyActivity() { return historyActivity; }
    public HistoryScopeKind historyScopeKind() { return historyScopeKind; }
    public List<SourceStatus> sources() { return sources; }
    public boolean sourcesKnown() { return sourcesKnown; }
    public boolean sourcesRetained() { return sourcesRetained; }
    public Long estimatedContextTokens() { return estimatedContextTokens; }
public DiagnosticsInputs(
                long settingsGeneration, ModelProfileSettingsView models,
                CapabilitySettingsView capabilities, RecipeSettingsView recipes,
                Optional<GuideSnapshot> guide, GuideHistoryActivity historyActivity,
                HistoryScopeKind historyScopeKind, List<SourceStatus> sources) {
            this(settingsGeneration, models, capabilities, recipes, guide, historyActivity,
                    historyScopeKind, sources, true, false, null);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiagnosticsInputs)) return false;
        DiagnosticsInputs that = (DiagnosticsInputs) other;
        return settingsGeneration == that.settingsGeneration && java.util.Objects.equals(models, that.models) && java.util.Objects.equals(capabilities, that.capabilities) && java.util.Objects.equals(recipes, that.recipes) && java.util.Objects.equals(guide, that.guide) && java.util.Objects.equals(historyActivity, that.historyActivity) && java.util.Objects.equals(historyScopeKind, that.historyScopeKind) && java.util.Objects.equals(sources, that.sources) && sourcesKnown == that.sourcesKnown && sourcesRetained == that.sourcesRetained && java.util.Objects.equals(estimatedContextTokens, that.estimatedContextTokens);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(settingsGeneration);
        hash = 31 * hash + java.util.Objects.hashCode(models);
        hash = 31 * hash + java.util.Objects.hashCode(capabilities);
        hash = 31 * hash + java.util.Objects.hashCode(recipes);
        hash = 31 * hash + java.util.Objects.hashCode(guide);
        hash = 31 * hash + java.util.Objects.hashCode(historyActivity);
        hash = 31 * hash + java.util.Objects.hashCode(historyScopeKind);
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        hash = 31 * hash + Boolean.hashCode(sourcesKnown);
        hash = 31 * hash + Boolean.hashCode(sourcesRetained);
        hash = 31 * hash + java.util.Objects.hashCode(estimatedContextTokens);
        return hash;
    }
    @Override public String toString() { return "DiagnosticsInputs[settingsGeneration=" + settingsGeneration + ", models=" + models + ", capabilities=" + capabilities + ", recipes=" + recipes + ", guide=" + guide + ", historyActivity=" + historyActivity + ", historyScopeKind=" + historyScopeKind + ", sources=" + sources + ", sourcesKnown=" + sourcesKnown + ", sourcesRetained=" + sourcesRetained + ", estimatedContextTokens=" + estimatedContextTokens + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DiagnosticsInputs> schema() {
            return new dev.openallay.value.ValueSchema<>(DiagnosticsInputs.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DiagnosticsInputs>>asList(new dev.openallay.value.ValueSchema.Component<>(DiagnosticsInputs.class, "settingsGeneration", DiagnosticsInputs::settingsGeneration), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsInputs.class, "models", DiagnosticsInputs::models), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsInputs.class, "capabilities", DiagnosticsInputs::capabilities), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsInputs.class, "recipes", DiagnosticsInputs::recipes), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsInputs.class, "guide", DiagnosticsInputs::guide), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsInputs.class, "historyActivity", DiagnosticsInputs::historyActivity), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsInputs.class, "historyScopeKind", DiagnosticsInputs::historyScopeKind), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsInputs.class, "sources", DiagnosticsInputs::sources), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsInputs.class, "sourcesKnown", DiagnosticsInputs::sourcesKnown), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsInputs.class, "sourcesRetained", DiagnosticsInputs::sourcesRetained), new dev.openallay.value.ValueSchema.Component<>(DiagnosticsInputs.class, "estimatedContextTokens", DiagnosticsInputs::estimatedContextTokens)), arguments -> new DiagnosticsInputs((Long) arguments[0], (ModelProfileSettingsView) arguments[1], (CapabilitySettingsView) arguments[2], (RecipeSettingsView) arguments[3], (Optional) arguments[4], (GuideHistoryActivity) arguments[5], (HistoryScopeKind) arguments[6], (List) arguments[7], (Boolean) arguments[8], (Boolean) arguments[9], (Long) arguments[10]));
        }
    }
}

    public SettingsDiagnosticsSnapshot snapshot(
            boolean debugMode, DiagnosticsInputs inputs) {
        Objects.requireNonNull(inputs, "inputs");
        GuideSummary guide = summarizeGuide(inputs.guide());
        List<SettingsDiagnosticCard> cards = List.of(
                modelCard(inputs.models()),
                knowledgeCard(inputs),
                historyCard(inputs, guide),
                contextCard(inputs, guide));
        Optional<DebugSettingsDiagnostics> debug = debugMode
                ? Optional.of(debug(inputs, guide))
                : Optional.empty();
        return new SettingsDiagnosticsSnapshot(cards, debug);
    }

    private static SettingsDiagnosticCard modelCard(ModelProfileSettingsView models) {
        int total = models.profiles().size();
        int available = count(models.profiles().stream().map(
                ModelProfileSettingsView.Profile::available).toList());
        int credentials = count(models.profiles().stream().map(
                ModelProfileSettingsView.Profile::credentialPresent).toList());
        FriendlyStatus status = total == 0 || available == 0
                ? FriendlyStatus.UNAVAILABLE
                : available < total ? FriendlyStatus.ATTENTION : FriendlyStatus.READY;
        return card(Domain.MODELS, status, List.of(
                metric("screen.openallay.settings.diagnostics.metric.configured", total),
                metric("screen.openallay.settings.diagnostics.metric.available", available),
                metric("screen.openallay.settings.diagnostics.metric.credentials", credentials)));
    }

    private static SettingsDiagnosticCard knowledgeCard(DiagnosticsInputs inputs) {
        CapabilitySettingsView capabilities = inputs.capabilities();
        List<SourceStatus> sources = inputs.sources();
        int catalog = capabilities.catalog().entries().size();
        int enabled = (int) capabilities.catalog().entries().stream()
                .filter(entry -> entry.available() && entry.enabled()).count();
        int availableSources = (int) sources.stream()
                .filter(source -> source.state() == SourceState.AVAILABLE
                        || source.state() == SourceState.PARTIAL)
                .count();
        int total = catalog + sources.size();
        boolean degraded = sources.stream().anyMatch(source -> source.state() != SourceState.AVAILABLE);
        FriendlyStatus status = total == 0 && inputs.sourcesKnown()
                ? FriendlyStatus.UNAVAILABLE
                : inputs.sourcesKnown() && !degraded && enabled + availableSources == total
                        ? FriendlyStatus.READY : FriendlyStatus.ATTENTION;
        List<String> notes = !inputs.sourcesKnown()
                ? List.of("screen.openallay.settings.diagnostics.knowledge.not_observed")
                : inputs.sourcesRetained()
                        ? List.of("screen.openallay.settings.diagnostics.knowledge.retained") : List.of();
        return card(Domain.KNOWLEDGE, status, notes, List.of(
                metric("screen.openallay.settings.diagnostics.metric.registered", catalog),
                metric("screen.openallay.settings.diagnostics.metric.enabled", enabled),
                optionalMetric("screen.openallay.settings.diagnostics.metric.sources",
                        inputs.sourcesKnown() ? Long.valueOf(sources.size()) : null),
                optionalMetric("screen.openallay.settings.diagnostics.metric.available_sources",
                        inputs.sourcesKnown() ? Long.valueOf(availableSources) : null)));
    }

    private static SettingsDiagnosticCard historyCard(
            DiagnosticsInputs inputs, GuideSummary guide) {
        FriendlyStatus status;
        if (inputs.guide().isEmpty()) {
            status = FriendlyStatus.NOT_CONNECTED;
        } else {
            GuideSnapshot snapshot = inputs.guide().orElseThrow();
            status = switch (snapshot.persistence().state()) {
                case UNAVAILABLE -> FriendlyStatus.UNAVAILABLE;
                case LOADING, SAVING -> FriendlyStatus.WORKING;
                case DISABLED -> FriendlyStatus.ATTENTION;
                case AVAILABLE -> selectedSession(snapshot)
                                .map(GuideSessionSnapshot::historyWindow)
                                .map(window -> switch (window.state()) {
                                    case LOADING -> FriendlyStatus.WORKING;
                                    case FAILED -> FriendlyStatus.ATTENTION;
                                    case IDLE -> inputs.historyActivity().idleForDeletion()
                                                    && guide.activeRequests() == 0
                                            ? FriendlyStatus.READY : FriendlyStatus.WORKING;
                                })
                                .orElse(FriendlyStatus.READY);
            };
        }
        List<String> notes = new java.util.ArrayList<>();
        if (inputs.guide().isPresent()) {
            notes.add("screen.openallay.settings.diagnostics.history.on_demand");
            selectedSession(inputs.guide().orElseThrow()).ifPresent(session -> {
                if (session.historyWindow().state()
                        == GuideHistoryPageState.LOADING) {
                    notes.add("screen.openallay.settings.diagnostics.history.page_loading");
                } else if (session.historyWindow().state()
                        == GuideHistoryPageState.FAILED) {
                    notes.add("screen.openallay.settings.diagnostics.history.page_failed");
                }
            });
        }
        return card(Domain.HISTORY, status, notes, List.of(
                optionalMetric("screen.openallay.settings.diagnostics.metric.pending_writes",
                        inputs.guide().isPresent() ? Long.valueOf(inputs.historyActivity().pendingWrites()) : null),
                optionalMetric("screen.openallay.settings.diagnostics.metric.active_requests",
                        inputs.guide().isPresent() ? Long.valueOf(guide.activeRequests()) : null)));
    }

    private static SettingsDiagnosticCard contextCard(DiagnosticsInputs inputs, GuideSummary guide) {
        Optional<GuideSnapshot> guideSnapshot = inputs.guide();
        FriendlyStatus status = guideSnapshot.isEmpty()
                ? FriendlyStatus.NOT_CONNECTED
                : guide.failedCheckpoints() > 0
                        ? FriendlyStatus.ATTENTION
                        : guide.activeRequests() > 0
                                ? FriendlyStatus.WORKING
                                : FriendlyStatus.READY;
        return card(Domain.CONTEXT, status,
                guideSnapshot.isPresent()
                        ? List.of("screen.openallay.settings.diagnostics.context.retained") : List.of(),
                List.of(
                        optionalMetric("screen.openallay.settings.diagnostics.metric.checkpoints",
                                guideSnapshot.isPresent() ? Long.valueOf(guide.checkpointCount()) : null),
                        optionalMetric("screen.openallay.settings.diagnostics.metric.checkpoint_failures",
                                guideSnapshot.isPresent() ? Long.valueOf(guide.failedCheckpoints()) : null),
                        optionalMetric("screen.openallay.settings.diagnostics.metric.estimated_tokens",
                                inputs.estimatedContextTokens())));
    }

    private static DebugSettingsDiagnostics debug(
            DiagnosticsInputs inputs, GuideSummary summary) {
        List<DebugModelProfile> models = inputs.models().profiles().stream()
                .map(profile -> debugModel(profile))
                .toList();
        int catalog = inputs.capabilities().catalog().entries().size();
        DebugCapabilities capabilities = new DebugCapabilities(
                catalog,
                (int) inputs.capabilities().catalog().entries().stream()
                        .filter(entry -> entry.available()).count(),
                (int) inputs.capabilities().catalog().entries().stream()
                        .filter(entry -> entry.available() && entry.enabled()).count(),
                (int) inputs.capabilities().catalog().entries().stream()
                        .filter(entry -> entry.kind() == CapabilityKind.KNOWLEDGE_SOURCE).count(),
                (int) inputs.capabilities().catalog().entries().stream()
                        .filter(entry -> entry.kind() == CapabilityKind.TOOL).count(),
                (int) inputs.capabilities().catalog().entries().stream()
                        .filter(entry -> entry.kind() == CapabilityKind.SKILL).count(),
                inputs.recipes().sources().size(),
                (int) inputs.recipes().sources().stream()
                        .filter(source -> source.available() && source.enabled()).count(),
                inputs.capabilities().unknownDisabledTools().size()
                        + inputs.capabilities().unknownDisabledSkills().size()
                        + inputs.recipes().unknownDisabledSources().size());
        List<DebugSource> sources = inputs.sources().stream()
                .map(source -> new DebugSource(
                        source.sourceId(),
                        source.generation(),
                        source.state(),
                        source.itemCount(),
                        source.failureCode()))
                .toList();
        return new DebugSettingsDiagnostics(
                inputs.settingsGeneration(),
                models,
                capabilities,
                inputs.guide().map(guide -> debugGuide(inputs, guide, summary)),
                sources,
                inputs.sourcesKnown(),
                inputs.sourcesRetained(),
                failureCodes(inputs));
    }

    private static DebugModelProfile debugModel(ModelProfileSettingsView.Profile profile) {
        ModelProfileDefinition definition = profile.definition();
        return new DebugModelProfile(
                safeIdentifier(definition.id()),
                definition.protocol(),
                authority(definition.baseUri()),
                safeIdentifier(definition.model()),
                definition.enabled(),
                profile.available(),
                profile.credentialPresent(),
                profile.effectiveContextWindowTokens(),
                definition.metadata() != null);
    }

    private static DebugGuide debugGuide(
            DiagnosticsInputs inputs, GuideSnapshot guide, GuideSummary summary) {
        Optional<GuideRequestSnapshot> request = selectedSession(guide)
                .flatMap(SettingsDiagnosticsAggregator::latestRequest);
        return new DebugGuide(
                inputs.historyScopeKind(),
                safeIdentifier(guide.selectedSession()),
                guide.modelMode(),
                guide.clientModelAvailable(),
                guide.serverModelAvailable(),
                guide.persistence().state(),
                guide.persistence().submittedGeneration(),
                guide.persistence().committedGeneration(),
                inputs.historyActivity().pendingWrites(),
                inputs.historyActivity().deleting(),
                summary.activeRequests(),
                request.map(value -> new DebugRequest(
                        value.requestId(),
                        value.topology(),
                        value.status(),
                        value.retryAfterMillis(),
                        value.tools().size(),
                        value.sources().size())),
                new DebugContext(
                        summary.checkpointCount(),
                        summary.successfulCheckpoints(),
                        summary.failedCheckpoints(),
                        inputs.estimatedContextTokens()),
                debugHistory(guide));
    }

    private static DebugHistory debugHistory(GuideSnapshot guide) {
        GuideSessionSnapshot selected = selectedSession(guide).orElse(null);
        SemanticLayoutCache.Stats cache = SemanticLayoutCache.globalStats();
        if (selected == null) {
            return new DebugHistory(0, 0, null, null,
                    GuideHistoryPageState.IDLE,
                    cache.hits(), cache.misses(), 0);
        }
        GuideHistoryWindowSnapshot window = selected.historyWindow();
        long fallbacks = selected.requests().stream()
                .flatMap(request -> request.timeline().stream())
                .filter(GuideTimelineEntry.Assistant.class::isInstance)
                .map(GuideTimelineEntry.Assistant.class::cast)
                .mapToLong(assistant -> assistant.semantic().diagnostics().size())
                .sum();
        return new DebugHistory(
                selected.requests().size(), window.totalRequests(),
                window.firstLoaded() == null ? null : window.firstLoaded().sequence(),
                window.lastLoaded() == null ? null : window.lastLoaded().sequence(),
                window.state(), cache.hits(), cache.misses(), fallbacks);
    }

    private static List<String> failureCodes(DiagnosticsInputs inputs) {
        TreeSet<String> codes = new TreeSet<>();
        for (ModelProfileSettingsView.Profile profile : inputs.models().profiles()) {
            add(codes, profile.failure());
        }
        add(codes, inputs.models().metadataFailure());
        final class $oaPattern0_Holder { dev.openallay.settings.model.ModelConnectionResult value; ModelConnectionResult.Failure bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = inputs.models().connectionResult()) instanceof dev.openallay.settings.model.ModelConnectionResult.Failure && (($oaPattern0_holder.bound = (ModelConnectionResult.Failure) $oaPattern0_holder.value) != null))) {
            codes.add(safeCode($oaPattern0_holder.bound.code()));
        }
        inputs.guide().ifPresent(guide -> {
            add(codes, guide.persistence().failure());
            for (GuideSessionSnapshot session : guide.sessions()) {
                for (GuideRequestSnapshot request : session.requests()) {
                    add(codes, request.failure());
                }
                for (ContextCheckpoint checkpoint : session.checkpoints()) {
                    if (checkpoint.status() == ContextCheckpoint.Status.FAILED) {
                        codes.add(safeCode(checkpoint.failureCode()));
                    }
                }
            }
        });
        inputs.sources().stream()
                .map(SourceStatus::failureCode)
                .filter(Objects::nonNull)
                .forEach(codes::add);
        return List.copyOf(codes);
    }

    private static GuideSummary summarizeGuide(Optional<GuideSnapshot> optional) {
        if (optional.isEmpty()) return GuideSummary.empty();
        GuideSnapshot guide = optional.orElseThrow();
        long active = guide.sessions().stream()
                .flatMap(session -> session.requests().stream())
                .filter(request -> !request.terminal())
                .count();
        Optional<GuideSessionSnapshot> selected = selectedSession(guide);
        if (selected.isEmpty()) {
            return new GuideSummary(active, 0, 0, 0);
        }
        List<ContextCheckpoint> checkpoints = selected.orElseThrow().checkpoints();
        int successful = (int) checkpoints.stream()
                .filter(value -> value.status() == ContextCheckpoint.Status.SUCCEEDED).count();
        int failed = checkpoints.size() - successful;
        return new GuideSummary(active, checkpoints.size(), successful, failed);
    }

    private static Optional<GuideSessionSnapshot> selectedSession(GuideSnapshot guide) {
        return guide.sessions().stream()
                .filter(session -> session.sessionId().equals(guide.selectedSession()))
                .findFirst();
    }

    private static Optional<GuideRequestSnapshot> latestRequest(GuideSessionSnapshot session) {
        if (session.requests().isEmpty()) return Optional.empty();
        return session.requests().stream()
                .filter(request -> !request.terminal())
                .reduce((first, second) -> second)
                .or(() -> Optional.of(session.requests().get(session.requests().size() - 1)));
    }

    private static SettingsDiagnosticCard card(
            Domain domain, FriendlyStatus status, List<Metric> metrics) {
        return card(domain, status, List.of(), metrics);
    }

    private static SettingsDiagnosticCard card(
            Domain domain, FriendlyStatus status, List<String> noteKeys, List<Metric> metrics) {
        String suffix = domain.name().toLowerCase(Locale.ROOT);
        return new SettingsDiagnosticCard(
                domain,
                status,
                "screen.openallay.settings.diagnostics." + suffix + ".title",
                "screen.openallay.settings.diagnostics.status."
                        + status.name().toLowerCase(Locale.ROOT),
                noteKeys,
                metrics);
    }

    private static Metric metric(String key, long value) {
        return new Metric(key, value);
    }

    private static Metric optionalMetric(String key, Long value) {
        return new Metric(key, value);
    }

    private static int count(List<Boolean> values) {
        return (int) values.stream().filter(Boolean::booleanValue).count();
    }

    private static String authority(URI endpoint) {
        String authority = endpoint.getRawAuthority();
        if (authority == null || authority.isBlank()) return "https://redacted.invalid";
        String candidate = endpoint.getScheme().toLowerCase(Locale.ROOT) + "://" + authority;
        String safe = safe(candidate, "");
        return safe.isEmpty() ? "https://redacted.invalid" : safe;
    }

    private static void add(TreeSet<String> codes, GuideFailure failure) {
        if (failure != null) codes.add(safeCode(failure.code()));
    }

    private static String safeCode(String value) {
        return safe(value, "redacted_failure_code");
    }

    private static String safeIdentifier(String value) {
        return safe(value, "redacted");
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.isBlank() || value.length() > 160) return fallback;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("authorization")
                || lower.contains("secret")
                || lower.contains("bearer")
                || lower.contains("api_key")
                || lower.contains("apikey")
                || lower.contains("token=")
                || lower.startsWith("sk-")
                || lower.contains("://sk-")) {
            return fallback;
        }
        return value.matches("[a-zA-Z0-9_./:-]+") ? value : fallback;
    }

    @dev.openallay.value.ValueType(GuideSummary.ValueSchemaProvider.class)
private static final class GuideSummary {
    private final long activeRequests;
    private final int checkpointCount;
    private final int successfulCheckpoints;
    private final int failedCheckpoints;
    private GuideSummary(long activeRequests, int checkpointCount, int successfulCheckpoints, int failedCheckpoints) {
        this.activeRequests = activeRequests;
        this.checkpointCount = checkpointCount;
        this.successfulCheckpoints = successfulCheckpoints;
        this.failedCheckpoints = failedCheckpoints;
    }
    public long activeRequests() { return activeRequests; }
    public int checkpointCount() { return checkpointCount; }
    public int successfulCheckpoints() { return successfulCheckpoints; }
    public int failedCheckpoints() { return failedCheckpoints; }
private static GuideSummary empty() {
            return new GuideSummary(0, 0, 0, 0);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideSummary)) return false;
        GuideSummary that = (GuideSummary) other;
        return activeRequests == that.activeRequests && checkpointCount == that.checkpointCount && successfulCheckpoints == that.successfulCheckpoints && failedCheckpoints == that.failedCheckpoints;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(activeRequests);
        hash = 31 * hash + Integer.hashCode(checkpointCount);
        hash = 31 * hash + Integer.hashCode(successfulCheckpoints);
        hash = 31 * hash + Integer.hashCode(failedCheckpoints);
        return hash;
    }
    @Override public String toString() { return "GuideSummary[activeRequests=" + activeRequests + ", checkpointCount=" + checkpointCount + ", successfulCheckpoints=" + successfulCheckpoints + ", failedCheckpoints=" + failedCheckpoints + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideSummary> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideSummary.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideSummary>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideSummary.class, "activeRequests", GuideSummary::activeRequests), new dev.openallay.value.ValueSchema.Component<>(GuideSummary.class, "checkpointCount", GuideSummary::checkpointCount), new dev.openallay.value.ValueSchema.Component<>(GuideSummary.class, "successfulCheckpoints", GuideSummary::successfulCheckpoints), new dev.openallay.value.ValueSchema.Component<>(GuideSummary.class, "failedCheckpoints", GuideSummary::failedCheckpoints)), arguments -> new GuideSummary((Long) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3]));
        }
    }
}
}
