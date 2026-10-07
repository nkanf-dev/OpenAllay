package dev.openallay.settings.diagnostics;

import dev.openallay.guide.GuideHistoryPageState;
import dev.openallay.guide.GuideModelMode;
import dev.openallay.guide.GuidePersistenceSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideTopology;
import dev.openallay.model.config.ModelProtocol;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Friendly cards plus an independently typed, redacted Debug Mode projection. */
@dev.openallay.value.ValueType(SettingsDiagnosticsSnapshot.ValueSchemaProvider.class)
public final class SettingsDiagnosticsSnapshot {
    private final List<SettingsDiagnosticCard> cards;
    private final Optional<DebugSettingsDiagnostics> debug;
    public SettingsDiagnosticsSnapshot(List<SettingsDiagnosticCard> cards, Optional<DebugSettingsDiagnostics> debug) {

        cards = dev.openallay.util.Java8Collections.listCopyOf(cards);
        debug = Objects.requireNonNull(debug, "debug");

        this.cards = cards;
        this.debug = debug;
    }
    public List<SettingsDiagnosticCard> cards() { return cards; }
    public Optional<DebugSettingsDiagnostics> debug() { return debug; }
@dev.openallay.value.ValueType(DebugSettingsDiagnostics.ValueSchemaProvider.class)
public static final class DebugSettingsDiagnostics {
    private final long settingsGeneration;
    private final List<DebugModelProfile> models;
    private final DebugCapabilities capabilities;
    private final Optional<DebugGuide> guide;
    private final List<DebugSource> sources;
    private final boolean sourcesKnown;
    private final boolean sourcesRetained;
    private final List<String> failureCodes;
    public DebugSettingsDiagnostics(long settingsGeneration, List<DebugModelProfile> models, DebugCapabilities capabilities, Optional<DebugGuide> guide, List<DebugSource> sources, boolean sourcesKnown, boolean sourcesRetained, List<String> failureCodes) {

            if (settingsGeneration < 0) {
                throw new IllegalArgumentException("debug generation is invalid");
            }
            models = dev.openallay.util.Java8Collections.listCopyOf(models);
            Objects.requireNonNull(capabilities, "capabilities");
            guide = Objects.requireNonNull(guide, "guide");
            sources = dev.openallay.util.Java8Collections.listCopyOf(sources);
            failureCodes = dev.openallay.util.Java8Collections.listCopyOf(failureCodes);
            failureCodes.forEach(code -> requireTechnical(code, "failureCode"));

        this.settingsGeneration = settingsGeneration;
        this.models = models;
        this.capabilities = capabilities;
        this.guide = guide;
        this.sources = sources;
        this.sourcesKnown = sourcesKnown;
        this.sourcesRetained = sourcesRetained;
        this.failureCodes = failureCodes;
    }
    public long settingsGeneration() { return settingsGeneration; }
    public List<DebugModelProfile> models() { return models; }
    public DebugCapabilities capabilities() { return capabilities; }
    public Optional<DebugGuide> guide() { return guide; }
    public List<DebugSource> sources() { return sources; }
    public boolean sourcesKnown() { return sourcesKnown; }
    public boolean sourcesRetained() { return sourcesRetained; }
    public List<String> failureCodes() { return failureCodes; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DebugSettingsDiagnostics)) return false;
        DebugSettingsDiagnostics that = (DebugSettingsDiagnostics) other;
        return settingsGeneration == that.settingsGeneration && java.util.Objects.equals(models, that.models) && java.util.Objects.equals(capabilities, that.capabilities) && java.util.Objects.equals(guide, that.guide) && java.util.Objects.equals(sources, that.sources) && sourcesKnown == that.sourcesKnown && sourcesRetained == that.sourcesRetained && java.util.Objects.equals(failureCodes, that.failureCodes);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(settingsGeneration);
        hash = 31 * hash + java.util.Objects.hashCode(models);
        hash = 31 * hash + java.util.Objects.hashCode(capabilities);
        hash = 31 * hash + java.util.Objects.hashCode(guide);
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        hash = 31 * hash + Boolean.hashCode(sourcesKnown);
        hash = 31 * hash + Boolean.hashCode(sourcesRetained);
        hash = 31 * hash + java.util.Objects.hashCode(failureCodes);
        return hash;
    }
    @Override public String toString() { return "DebugSettingsDiagnostics[settingsGeneration=" + settingsGeneration + ", models=" + models + ", capabilities=" + capabilities + ", guide=" + guide + ", sources=" + sources + ", sourcesKnown=" + sourcesKnown + ", sourcesRetained=" + sourcesRetained + ", failureCodes=" + failureCodes + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DebugSettingsDiagnostics> schema() {
            return new dev.openallay.value.ValueSchema<>(DebugSettingsDiagnostics.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DebugSettingsDiagnostics>>asList(new dev.openallay.value.ValueSchema.Component<>(DebugSettingsDiagnostics.class, "settingsGeneration", DebugSettingsDiagnostics::settingsGeneration), new dev.openallay.value.ValueSchema.Component<>(DebugSettingsDiagnostics.class, "models", DebugSettingsDiagnostics::models), new dev.openallay.value.ValueSchema.Component<>(DebugSettingsDiagnostics.class, "capabilities", DebugSettingsDiagnostics::capabilities), new dev.openallay.value.ValueSchema.Component<>(DebugSettingsDiagnostics.class, "guide", DebugSettingsDiagnostics::guide), new dev.openallay.value.ValueSchema.Component<>(DebugSettingsDiagnostics.class, "sources", DebugSettingsDiagnostics::sources), new dev.openallay.value.ValueSchema.Component<>(DebugSettingsDiagnostics.class, "sourcesKnown", DebugSettingsDiagnostics::sourcesKnown), new dev.openallay.value.ValueSchema.Component<>(DebugSettingsDiagnostics.class, "sourcesRetained", DebugSettingsDiagnostics::sourcesRetained), new dev.openallay.value.ValueSchema.Component<>(DebugSettingsDiagnostics.class, "failureCodes", DebugSettingsDiagnostics::failureCodes)), arguments -> new DebugSettingsDiagnostics((Long) arguments[0], (List) arguments[1], (DebugCapabilities) arguments[2], (Optional) arguments[3], (List) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6], (List) arguments[7]));
        }
    }
}
@dev.openallay.value.ValueType(DebugModelProfile.ValueSchemaProvider.class)
public static final class DebugModelProfile {
    private final String profileId;
    private final ModelProtocol protocol;
    private final String endpointAuthority;
    private final String modelId;
    private final boolean enabled;
    private final boolean available;
    private final boolean credentialPresent;
    private final Integer effectiveContextWindowTokens;
    private final boolean metadataPresent;
    public DebugModelProfile(String profileId, ModelProtocol protocol, String endpointAuthority, String modelId, boolean enabled, boolean available, boolean credentialPresent, Integer effectiveContextWindowTokens, boolean metadataPresent) {

            requireTechnical(profileId, "profileId");
            Objects.requireNonNull(protocol, "protocol");
            requireEndpointAuthority(endpointAuthority);
            requireTechnical(modelId, "modelId");
            if (effectiveContextWindowTokens != null && effectiveContextWindowTokens <= 0) {
                throw new IllegalArgumentException("effective context window must be positive");
            }

        this.profileId = profileId;
        this.protocol = protocol;
        this.endpointAuthority = endpointAuthority;
        this.modelId = modelId;
        this.enabled = enabled;
        this.available = available;
        this.credentialPresent = credentialPresent;
        this.effectiveContextWindowTokens = effectiveContextWindowTokens;
        this.metadataPresent = metadataPresent;
    }
    public String profileId() { return profileId; }
    public ModelProtocol protocol() { return protocol; }
    public String endpointAuthority() { return endpointAuthority; }
    public String modelId() { return modelId; }
    public boolean enabled() { return enabled; }
    public boolean available() { return available; }
    public boolean credentialPresent() { return credentialPresent; }
    public Integer effectiveContextWindowTokens() { return effectiveContextWindowTokens; }
    public boolean metadataPresent() { return metadataPresent; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DebugModelProfile)) return false;
        DebugModelProfile that = (DebugModelProfile) other;
        return java.util.Objects.equals(profileId, that.profileId) && java.util.Objects.equals(protocol, that.protocol) && java.util.Objects.equals(endpointAuthority, that.endpointAuthority) && java.util.Objects.equals(modelId, that.modelId) && enabled == that.enabled && available == that.available && credentialPresent == that.credentialPresent && java.util.Objects.equals(effectiveContextWindowTokens, that.effectiveContextWindowTokens) && metadataPresent == that.metadataPresent;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(profileId);
        hash = 31 * hash + java.util.Objects.hashCode(protocol);
        hash = 31 * hash + java.util.Objects.hashCode(endpointAuthority);
        hash = 31 * hash + java.util.Objects.hashCode(modelId);
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + Boolean.hashCode(credentialPresent);
        hash = 31 * hash + java.util.Objects.hashCode(effectiveContextWindowTokens);
        hash = 31 * hash + Boolean.hashCode(metadataPresent);
        return hash;
    }
    @Override public String toString() { return "DebugModelProfile[profileId=" + profileId + ", protocol=" + protocol + ", endpointAuthority=" + endpointAuthority + ", modelId=" + modelId + ", enabled=" + enabled + ", available=" + available + ", credentialPresent=" + credentialPresent + ", effectiveContextWindowTokens=" + effectiveContextWindowTokens + ", metadataPresent=" + metadataPresent + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DebugModelProfile> schema() {
            return new dev.openallay.value.ValueSchema<>(DebugModelProfile.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DebugModelProfile>>asList(new dev.openallay.value.ValueSchema.Component<>(DebugModelProfile.class, "profileId", DebugModelProfile::profileId), new dev.openallay.value.ValueSchema.Component<>(DebugModelProfile.class, "protocol", DebugModelProfile::protocol), new dev.openallay.value.ValueSchema.Component<>(DebugModelProfile.class, "endpointAuthority", DebugModelProfile::endpointAuthority), new dev.openallay.value.ValueSchema.Component<>(DebugModelProfile.class, "modelId", DebugModelProfile::modelId), new dev.openallay.value.ValueSchema.Component<>(DebugModelProfile.class, "enabled", DebugModelProfile::enabled), new dev.openallay.value.ValueSchema.Component<>(DebugModelProfile.class, "available", DebugModelProfile::available), new dev.openallay.value.ValueSchema.Component<>(DebugModelProfile.class, "credentialPresent", DebugModelProfile::credentialPresent), new dev.openallay.value.ValueSchema.Component<>(DebugModelProfile.class, "effectiveContextWindowTokens", DebugModelProfile::effectiveContextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(DebugModelProfile.class, "metadataPresent", DebugModelProfile::metadataPresent)), arguments -> new DebugModelProfile((String) arguments[0], (ModelProtocol) arguments[1], (String) arguments[2], (String) arguments[3], (Boolean) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6], (Integer) arguments[7], (Boolean) arguments[8]));
        }
    }
}
@dev.openallay.value.ValueType(DebugCapabilities.ValueSchemaProvider.class)
public static final class DebugCapabilities {
    private final int catalogEntries;
    private final int availableEntries;
    private final int enabledEntries;
    private final int knowledgeSources;
    private final int tools;
    private final int skills;
    private final int configuredRecipeSources;
    private final int enabledRecipeSources;
    private final int unknownDisabledEntries;
    public DebugCapabilities(int catalogEntries, int availableEntries, int enabledEntries, int knowledgeSources, int tools, int skills, int configuredRecipeSources, int enabledRecipeSources, int unknownDisabledEntries) {

            if (catalogEntries < 0 || availableEntries < 0 || enabledEntries < 0
                    || knowledgeSources < 0 || tools < 0 || skills < 0
                    || configuredRecipeSources < 0 || enabledRecipeSources < 0
                    || unknownDisabledEntries < 0) {
                throw new IllegalArgumentException("debug capability counts must not be negative");
            }
            if (availableEntries > catalogEntries
                    || enabledEntries > availableEntries
                    || knowledgeSources + tools + skills != catalogEntries
                    || enabledRecipeSources > configuredRecipeSources) {
                throw new IllegalArgumentException("debug capability counts are inconsistent");
            }

        this.catalogEntries = catalogEntries;
        this.availableEntries = availableEntries;
        this.enabledEntries = enabledEntries;
        this.knowledgeSources = knowledgeSources;
        this.tools = tools;
        this.skills = skills;
        this.configuredRecipeSources = configuredRecipeSources;
        this.enabledRecipeSources = enabledRecipeSources;
        this.unknownDisabledEntries = unknownDisabledEntries;
    }
    public int catalogEntries() { return catalogEntries; }
    public int availableEntries() { return availableEntries; }
    public int enabledEntries() { return enabledEntries; }
    public int knowledgeSources() { return knowledgeSources; }
    public int tools() { return tools; }
    public int skills() { return skills; }
    public int configuredRecipeSources() { return configuredRecipeSources; }
    public int enabledRecipeSources() { return enabledRecipeSources; }
    public int unknownDisabledEntries() { return unknownDisabledEntries; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DebugCapabilities)) return false;
        DebugCapabilities that = (DebugCapabilities) other;
        return catalogEntries == that.catalogEntries && availableEntries == that.availableEntries && enabledEntries == that.enabledEntries && knowledgeSources == that.knowledgeSources && tools == that.tools && skills == that.skills && configuredRecipeSources == that.configuredRecipeSources && enabledRecipeSources == that.enabledRecipeSources && unknownDisabledEntries == that.unknownDisabledEntries;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(catalogEntries);
        hash = 31 * hash + Integer.hashCode(availableEntries);
        hash = 31 * hash + Integer.hashCode(enabledEntries);
        hash = 31 * hash + Integer.hashCode(knowledgeSources);
        hash = 31 * hash + Integer.hashCode(tools);
        hash = 31 * hash + Integer.hashCode(skills);
        hash = 31 * hash + Integer.hashCode(configuredRecipeSources);
        hash = 31 * hash + Integer.hashCode(enabledRecipeSources);
        hash = 31 * hash + Integer.hashCode(unknownDisabledEntries);
        return hash;
    }
    @Override public String toString() { return "DebugCapabilities[catalogEntries=" + catalogEntries + ", availableEntries=" + availableEntries + ", enabledEntries=" + enabledEntries + ", knowledgeSources=" + knowledgeSources + ", tools=" + tools + ", skills=" + skills + ", configuredRecipeSources=" + configuredRecipeSources + ", enabledRecipeSources=" + enabledRecipeSources + ", unknownDisabledEntries=" + unknownDisabledEntries + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DebugCapabilities> schema() {
            return new dev.openallay.value.ValueSchema<>(DebugCapabilities.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DebugCapabilities>>asList(new dev.openallay.value.ValueSchema.Component<>(DebugCapabilities.class, "catalogEntries", DebugCapabilities::catalogEntries), new dev.openallay.value.ValueSchema.Component<>(DebugCapabilities.class, "availableEntries", DebugCapabilities::availableEntries), new dev.openallay.value.ValueSchema.Component<>(DebugCapabilities.class, "enabledEntries", DebugCapabilities::enabledEntries), new dev.openallay.value.ValueSchema.Component<>(DebugCapabilities.class, "knowledgeSources", DebugCapabilities::knowledgeSources), new dev.openallay.value.ValueSchema.Component<>(DebugCapabilities.class, "tools", DebugCapabilities::tools), new dev.openallay.value.ValueSchema.Component<>(DebugCapabilities.class, "skills", DebugCapabilities::skills), new dev.openallay.value.ValueSchema.Component<>(DebugCapabilities.class, "configuredRecipeSources", DebugCapabilities::configuredRecipeSources), new dev.openallay.value.ValueSchema.Component<>(DebugCapabilities.class, "enabledRecipeSources", DebugCapabilities::enabledRecipeSources), new dev.openallay.value.ValueSchema.Component<>(DebugCapabilities.class, "unknownDisabledEntries", DebugCapabilities::unknownDisabledEntries)), arguments -> new DebugCapabilities((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (Integer) arguments[4], (Integer) arguments[5], (Integer) arguments[6], (Integer) arguments[7], (Integer) arguments[8]));
        }
    }
}
@dev.openallay.value.ValueType(DebugGuide.ValueSchemaProvider.class)
public static final class DebugGuide {
    private final SettingsDiagnosticsAggregator.HistoryScopeKind scopeKind;
    private final String selectedSessionId;
    private final GuideModelMode modelMode;
    private final boolean clientModelAvailable;
    private final boolean serverModelAvailable;
    private final GuidePersistenceSnapshot.State persistenceState;
    private final long submittedGeneration;
    private final long committedGeneration;
    private final int pendingWrites;
    private final boolean deleting;
    private final long activeRequestCount;
    private final Optional<DebugRequest> request;
    private final DebugContext context;
    private final DebugHistory history;
    public DebugGuide(SettingsDiagnosticsAggregator.HistoryScopeKind scopeKind, String selectedSessionId, GuideModelMode modelMode, boolean clientModelAvailable, boolean serverModelAvailable, GuidePersistenceSnapshot.State persistenceState, long submittedGeneration, long committedGeneration, int pendingWrites, boolean deleting, long activeRequestCount, Optional<DebugRequest> request, DebugContext context, DebugHistory history) {

            Objects.requireNonNull(scopeKind, "scopeKind");
            requireTechnical(selectedSessionId, "selectedSessionId");
            Objects.requireNonNull(modelMode, "modelMode");
            Objects.requireNonNull(persistenceState, "persistenceState");
            if (submittedGeneration < 0 || committedGeneration < 0
                    || committedGeneration > submittedGeneration
                    || pendingWrites < 0 || activeRequestCount < 0) {
                throw new IllegalArgumentException("debug Guide counts are invalid");
            }
            request = Objects.requireNonNull(request, "request");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(history, "history");

        this.scopeKind = scopeKind;
        this.selectedSessionId = selectedSessionId;
        this.modelMode = modelMode;
        this.clientModelAvailable = clientModelAvailable;
        this.serverModelAvailable = serverModelAvailable;
        this.persistenceState = persistenceState;
        this.submittedGeneration = submittedGeneration;
        this.committedGeneration = committedGeneration;
        this.pendingWrites = pendingWrites;
        this.deleting = deleting;
        this.activeRequestCount = activeRequestCount;
        this.request = request;
        this.context = context;
        this.history = history;
    }
    public SettingsDiagnosticsAggregator.HistoryScopeKind scopeKind() { return scopeKind; }
    public String selectedSessionId() { return selectedSessionId; }
    public GuideModelMode modelMode() { return modelMode; }
    public boolean clientModelAvailable() { return clientModelAvailable; }
    public boolean serverModelAvailable() { return serverModelAvailable; }
    public GuidePersistenceSnapshot.State persistenceState() { return persistenceState; }
    public long submittedGeneration() { return submittedGeneration; }
    public long committedGeneration() { return committedGeneration; }
    public int pendingWrites() { return pendingWrites; }
    public boolean deleting() { return deleting; }
    public long activeRequestCount() { return activeRequestCount; }
    public Optional<DebugRequest> request() { return request; }
    public DebugContext context() { return context; }
    public DebugHistory history() { return history; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DebugGuide)) return false;
        DebugGuide that = (DebugGuide) other;
        return java.util.Objects.equals(scopeKind, that.scopeKind) && java.util.Objects.equals(selectedSessionId, that.selectedSessionId) && java.util.Objects.equals(modelMode, that.modelMode) && clientModelAvailable == that.clientModelAvailable && serverModelAvailable == that.serverModelAvailable && java.util.Objects.equals(persistenceState, that.persistenceState) && submittedGeneration == that.submittedGeneration && committedGeneration == that.committedGeneration && pendingWrites == that.pendingWrites && deleting == that.deleting && activeRequestCount == that.activeRequestCount && java.util.Objects.equals(request, that.request) && java.util.Objects.equals(context, that.context) && java.util.Objects.equals(history, that.history);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(scopeKind);
        hash = 31 * hash + java.util.Objects.hashCode(selectedSessionId);
        hash = 31 * hash + java.util.Objects.hashCode(modelMode);
        hash = 31 * hash + Boolean.hashCode(clientModelAvailable);
        hash = 31 * hash + Boolean.hashCode(serverModelAvailable);
        hash = 31 * hash + java.util.Objects.hashCode(persistenceState);
        hash = 31 * hash + Long.hashCode(submittedGeneration);
        hash = 31 * hash + Long.hashCode(committedGeneration);
        hash = 31 * hash + Integer.hashCode(pendingWrites);
        hash = 31 * hash + Boolean.hashCode(deleting);
        hash = 31 * hash + Long.hashCode(activeRequestCount);
        hash = 31 * hash + java.util.Objects.hashCode(request);
        hash = 31 * hash + java.util.Objects.hashCode(context);
        hash = 31 * hash + java.util.Objects.hashCode(history);
        return hash;
    }
    @Override public String toString() { return "DebugGuide[scopeKind=" + scopeKind + ", selectedSessionId=" + selectedSessionId + ", modelMode=" + modelMode + ", clientModelAvailable=" + clientModelAvailable + ", serverModelAvailable=" + serverModelAvailable + ", persistenceState=" + persistenceState + ", submittedGeneration=" + submittedGeneration + ", committedGeneration=" + committedGeneration + ", pendingWrites=" + pendingWrites + ", deleting=" + deleting + ", activeRequestCount=" + activeRequestCount + ", request=" + request + ", context=" + context + ", history=" + history + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DebugGuide> schema() {
            return new dev.openallay.value.ValueSchema<>(DebugGuide.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DebugGuide>>asList(new dev.openallay.value.ValueSchema.Component<>(DebugGuide.class, "scopeKind", DebugGuide::scopeKind), new dev.openallay.value.ValueSchema.Component<>(DebugGuide.class, "selectedSessionId", DebugGuide::selectedSessionId), new dev.openallay.value.ValueSchema.Component<>(DebugGuide.class, "modelMode", DebugGuide::modelMode), new dev.openallay.value.ValueSchema.Component<>(DebugGuide.class, "clientModelAvailable", DebugGuide::clientModelAvailable), new dev.openallay.value.ValueSchema.Component<>(DebugGuide.class, "serverModelAvailable", DebugGuide::serverModelAvailable), new dev.openallay.value.ValueSchema.Component<>(DebugGuide.class, "persistenceState", DebugGuide::persistenceState), new dev.openallay.value.ValueSchema.Component<>(DebugGuide.class, "submittedGeneration", DebugGuide::submittedGeneration), new dev.openallay.value.ValueSchema.Component<>(DebugGuide.class, "committedGeneration", DebugGuide::committedGeneration), new dev.openallay.value.ValueSchema.Component<>(DebugGuide.class, "pendingWrites", DebugGuide::pendingWrites), new dev.openallay.value.ValueSchema.Component<>(DebugGuide.class, "deleting", DebugGuide::deleting), new dev.openallay.value.ValueSchema.Component<>(DebugGuide.class, "activeRequestCount", DebugGuide::activeRequestCount), new dev.openallay.value.ValueSchema.Component<>(DebugGuide.class, "request", DebugGuide::request), new dev.openallay.value.ValueSchema.Component<>(DebugGuide.class, "context", DebugGuide::context), new dev.openallay.value.ValueSchema.Component<>(DebugGuide.class, "history", DebugGuide::history)), arguments -> new DebugGuide((SettingsDiagnosticsAggregator.HistoryScopeKind) arguments[0], (String) arguments[1], (GuideModelMode) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4], (GuidePersistenceSnapshot.State) arguments[5], (Long) arguments[6], (Long) arguments[7], (Integer) arguments[8], (Boolean) arguments[9], (Long) arguments[10], (Optional) arguments[11], (DebugContext) arguments[12], (DebugHistory) arguments[13]));
        }
    }
}
@dev.openallay.value.ValueType(DebugRequest.ValueSchemaProvider.class)
public static final class DebugRequest {
    private final UUID requestId;
    private final GuideTopology topology;
    private final GuideRequestStatus status;
    private final Long retryAfterMillis;
    private final int toolCount;
    private final int sourceCount;
    public DebugRequest(UUID requestId, GuideTopology topology, GuideRequestStatus status, Long retryAfterMillis, int toolCount, int sourceCount) {

            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(topology, "topology");
            Objects.requireNonNull(status, "status");
            if (retryAfterMillis != null && retryAfterMillis < 0
                    || toolCount < 0 || sourceCount < 0) {
                throw new IllegalArgumentException("debug request counts are invalid");
            }

        this.requestId = requestId;
        this.topology = topology;
        this.status = status;
        this.retryAfterMillis = retryAfterMillis;
        this.toolCount = toolCount;
        this.sourceCount = sourceCount;
    }
    public UUID requestId() { return requestId; }
    public GuideTopology topology() { return topology; }
    public GuideRequestStatus status() { return status; }
    public Long retryAfterMillis() { return retryAfterMillis; }
    public int toolCount() { return toolCount; }
    public int sourceCount() { return sourceCount; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DebugRequest)) return false;
        DebugRequest that = (DebugRequest) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(topology, that.topology) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(retryAfterMillis, that.retryAfterMillis) && toolCount == that.toolCount && sourceCount == that.sourceCount;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(topology);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(retryAfterMillis);
        hash = 31 * hash + Integer.hashCode(toolCount);
        hash = 31 * hash + Integer.hashCode(sourceCount);
        return hash;
    }
    @Override public String toString() { return "DebugRequest[requestId=" + requestId + ", topology=" + topology + ", status=" + status + ", retryAfterMillis=" + retryAfterMillis + ", toolCount=" + toolCount + ", sourceCount=" + sourceCount + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DebugRequest> schema() {
            return new dev.openallay.value.ValueSchema<>(DebugRequest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DebugRequest>>asList(new dev.openallay.value.ValueSchema.Component<>(DebugRequest.class, "requestId", DebugRequest::requestId), new dev.openallay.value.ValueSchema.Component<>(DebugRequest.class, "topology", DebugRequest::topology), new dev.openallay.value.ValueSchema.Component<>(DebugRequest.class, "status", DebugRequest::status), new dev.openallay.value.ValueSchema.Component<>(DebugRequest.class, "retryAfterMillis", DebugRequest::retryAfterMillis), new dev.openallay.value.ValueSchema.Component<>(DebugRequest.class, "toolCount", DebugRequest::toolCount), new dev.openallay.value.ValueSchema.Component<>(DebugRequest.class, "sourceCount", DebugRequest::sourceCount)), arguments -> new DebugRequest((UUID) arguments[0], (GuideTopology) arguments[1], (GuideRequestStatus) arguments[2], (Long) arguments[3], (Integer) arguments[4], (Integer) arguments[5]));
        }
    }
}
@dev.openallay.value.ValueType(DebugContext.ValueSchemaProvider.class)
public static final class DebugContext {
    private final int checkpointCount;
    private final int successfulCheckpoints;
    private final int failedCheckpoints;
    private final Long estimatedProjectionTokens;
    public DebugContext(int checkpointCount, int successfulCheckpoints, int failedCheckpoints, Long estimatedProjectionTokens) {

            if (checkpointCount < 0 || successfulCheckpoints < 0 || failedCheckpoints < 0
                    || successfulCheckpoints + failedCheckpoints != checkpointCount
                    || estimatedProjectionTokens != null && estimatedProjectionTokens < 0) {
                throw new IllegalArgumentException("debug context counts are invalid");
            }

        this.checkpointCount = checkpointCount;
        this.successfulCheckpoints = successfulCheckpoints;
        this.failedCheckpoints = failedCheckpoints;
        this.estimatedProjectionTokens = estimatedProjectionTokens;
    }
    public int checkpointCount() { return checkpointCount; }
    public int successfulCheckpoints() { return successfulCheckpoints; }
    public int failedCheckpoints() { return failedCheckpoints; }
    public Long estimatedProjectionTokens() { return estimatedProjectionTokens; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DebugContext)) return false;
        DebugContext that = (DebugContext) other;
        return checkpointCount == that.checkpointCount && successfulCheckpoints == that.successfulCheckpoints && failedCheckpoints == that.failedCheckpoints && java.util.Objects.equals(estimatedProjectionTokens, that.estimatedProjectionTokens);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(checkpointCount);
        hash = 31 * hash + Integer.hashCode(successfulCheckpoints);
        hash = 31 * hash + Integer.hashCode(failedCheckpoints);
        hash = 31 * hash + java.util.Objects.hashCode(estimatedProjectionTokens);
        return hash;
    }
    @Override public String toString() { return "DebugContext[checkpointCount=" + checkpointCount + ", successfulCheckpoints=" + successfulCheckpoints + ", failedCheckpoints=" + failedCheckpoints + ", estimatedProjectionTokens=" + estimatedProjectionTokens + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DebugContext> schema() {
            return new dev.openallay.value.ValueSchema<>(DebugContext.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DebugContext>>asList(new dev.openallay.value.ValueSchema.Component<>(DebugContext.class, "checkpointCount", DebugContext::checkpointCount), new dev.openallay.value.ValueSchema.Component<>(DebugContext.class, "successfulCheckpoints", DebugContext::successfulCheckpoints), new dev.openallay.value.ValueSchema.Component<>(DebugContext.class, "failedCheckpoints", DebugContext::failedCheckpoints), new dev.openallay.value.ValueSchema.Component<>(DebugContext.class, "estimatedProjectionTokens", DebugContext::estimatedProjectionTokens)), arguments -> new DebugContext((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Long) arguments[3]));
        }
    }
}
@dev.openallay.value.ValueType(DebugHistory.ValueSchemaProvider.class)
public static final class DebugHistory {
    private final long loadedRequests;
    private final long totalRequests;
    private final Long firstLoadedCount;
    private final Long lastLoadedCount;
    private final GuideHistoryPageState pageState;
    private final long cacheHits;
    private final long cacheMisses;
    private final long semanticFallbackCount;
    public DebugHistory(long loadedRequests, long totalRequests, Long firstLoadedCount, Long lastLoadedCount, GuideHistoryPageState pageState, long cacheHits, long cacheMisses, long semanticFallbackCount) {

            if (loadedRequests < 0 || totalRequests < loadedRequests
                    || firstLoadedCount != null && firstLoadedCount < 0
                    || lastLoadedCount != null && lastLoadedCount < 0
                    || cacheHits < 0 || cacheMisses < 0 || semanticFallbackCount < 0) {
                throw new IllegalArgumentException("debug history counts are invalid");
            }
            Objects.requireNonNull(pageState, "pageState");
            if ((firstLoadedCount == null) != (lastLoadedCount == null)
                    || firstLoadedCount != null && firstLoadedCount > lastLoadedCount) {
                throw new IllegalArgumentException("debug history cursor counts are invalid");
            }

        this.loadedRequests = loadedRequests;
        this.totalRequests = totalRequests;
        this.firstLoadedCount = firstLoadedCount;
        this.lastLoadedCount = lastLoadedCount;
        this.pageState = pageState;
        this.cacheHits = cacheHits;
        this.cacheMisses = cacheMisses;
        this.semanticFallbackCount = semanticFallbackCount;
    }
    public long loadedRequests() { return loadedRequests; }
    public long totalRequests() { return totalRequests; }
    public Long firstLoadedCount() { return firstLoadedCount; }
    public Long lastLoadedCount() { return lastLoadedCount; }
    public GuideHistoryPageState pageState() { return pageState; }
    public long cacheHits() { return cacheHits; }
    public long cacheMisses() { return cacheMisses; }
    public long semanticFallbackCount() { return semanticFallbackCount; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DebugHistory)) return false;
        DebugHistory that = (DebugHistory) other;
        return loadedRequests == that.loadedRequests && totalRequests == that.totalRequests && java.util.Objects.equals(firstLoadedCount, that.firstLoadedCount) && java.util.Objects.equals(lastLoadedCount, that.lastLoadedCount) && java.util.Objects.equals(pageState, that.pageState) && cacheHits == that.cacheHits && cacheMisses == that.cacheMisses && semanticFallbackCount == that.semanticFallbackCount;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(loadedRequests);
        hash = 31 * hash + Long.hashCode(totalRequests);
        hash = 31 * hash + java.util.Objects.hashCode(firstLoadedCount);
        hash = 31 * hash + java.util.Objects.hashCode(lastLoadedCount);
        hash = 31 * hash + java.util.Objects.hashCode(pageState);
        hash = 31 * hash + Long.hashCode(cacheHits);
        hash = 31 * hash + Long.hashCode(cacheMisses);
        hash = 31 * hash + Long.hashCode(semanticFallbackCount);
        return hash;
    }
    @Override public String toString() { return "DebugHistory[loadedRequests=" + loadedRequests + ", totalRequests=" + totalRequests + ", firstLoadedCount=" + firstLoadedCount + ", lastLoadedCount=" + lastLoadedCount + ", pageState=" + pageState + ", cacheHits=" + cacheHits + ", cacheMisses=" + cacheMisses + ", semanticFallbackCount=" + semanticFallbackCount + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DebugHistory> schema() {
            return new dev.openallay.value.ValueSchema<>(DebugHistory.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DebugHistory>>asList(new dev.openallay.value.ValueSchema.Component<>(DebugHistory.class, "loadedRequests", DebugHistory::loadedRequests), new dev.openallay.value.ValueSchema.Component<>(DebugHistory.class, "totalRequests", DebugHistory::totalRequests), new dev.openallay.value.ValueSchema.Component<>(DebugHistory.class, "firstLoadedCount", DebugHistory::firstLoadedCount), new dev.openallay.value.ValueSchema.Component<>(DebugHistory.class, "lastLoadedCount", DebugHistory::lastLoadedCount), new dev.openallay.value.ValueSchema.Component<>(DebugHistory.class, "pageState", DebugHistory::pageState), new dev.openallay.value.ValueSchema.Component<>(DebugHistory.class, "cacheHits", DebugHistory::cacheHits), new dev.openallay.value.ValueSchema.Component<>(DebugHistory.class, "cacheMisses", DebugHistory::cacheMisses), new dev.openallay.value.ValueSchema.Component<>(DebugHistory.class, "semanticFallbackCount", DebugHistory::semanticFallbackCount)), arguments -> new DebugHistory((Long) arguments[0], (Long) arguments[1], (Long) arguments[2], (Long) arguments[3], (GuideHistoryPageState) arguments[4], (Long) arguments[5], (Long) arguments[6], (Long) arguments[7]));
        }
    }
}
@dev.openallay.value.ValueType(DebugSource.ValueSchemaProvider.class)
public static final class DebugSource {
    private final String sourceId;
    private final String generation;
    private final SettingsDiagnosticsAggregator.SourceState state;
    private final Integer itemCount;
    private final String failureCode;
    public DebugSource(String sourceId, String generation, SettingsDiagnosticsAggregator.SourceState state, Integer itemCount, String failureCode) {

            requireTechnical(sourceId, "sourceId");
            if (generation != null) requireTechnical(generation, "generation");
            Objects.requireNonNull(state, "state");
            if (itemCount != null && itemCount < 0) {
                throw new IllegalArgumentException("debug source count must not be negative");
            }
            if (failureCode != null) requireTechnical(failureCode, "failureCode");
            boolean available = state == SettingsDiagnosticsAggregator.SourceState.AVAILABLE;
            boolean generated = state == SettingsDiagnosticsAggregator.SourceState.AVAILABLE
                    || state == SettingsDiagnosticsAggregator.SourceState.PARTIAL;
            if (generated != (generation != null) || available != (failureCode == null)) {
                throw new IllegalArgumentException(
                        "debug source state and diagnostics are inconsistent");
            }

        this.sourceId = sourceId;
        this.generation = generation;
        this.state = state;
        this.itemCount = itemCount;
        this.failureCode = failureCode;
    }
    public String sourceId() { return sourceId; }
    public String generation() { return generation; }
    public SettingsDiagnosticsAggregator.SourceState state() { return state; }
    public Integer itemCount() { return itemCount; }
    public String failureCode() { return failureCode; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DebugSource)) return false;
        DebugSource that = (DebugSource) other;
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
    @Override public String toString() { return "DebugSource[sourceId=" + sourceId + ", generation=" + generation + ", state=" + state + ", itemCount=" + itemCount + ", failureCode=" + failureCode + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DebugSource> schema() {
            return new dev.openallay.value.ValueSchema<>(DebugSource.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DebugSource>>asList(new dev.openallay.value.ValueSchema.Component<>(DebugSource.class, "sourceId", DebugSource::sourceId), new dev.openallay.value.ValueSchema.Component<>(DebugSource.class, "generation", DebugSource::generation), new dev.openallay.value.ValueSchema.Component<>(DebugSource.class, "state", DebugSource::state), new dev.openallay.value.ValueSchema.Component<>(DebugSource.class, "itemCount", DebugSource::itemCount), new dev.openallay.value.ValueSchema.Component<>(DebugSource.class, "failureCode", DebugSource::failureCode)), arguments -> new DebugSource((String) arguments[0], (String) arguments[1], (SettingsDiagnosticsAggregator.SourceState) arguments[2], (Integer) arguments[3], (String) arguments[4]));
        }
    }
}
private static void requireTechnical(String value, String name) {
        if (value == null || !value.matches("[a-zA-Z0-9_./:-]{1,160}")) {
            throw new IllegalArgumentException(name + " must be a bounded technical identifier");
        }
        if (containsCredentialVocabulary(value)) {
            throw new IllegalArgumentException(name + " contains forbidden credential vocabulary");
        }
    }
private static boolean containsCredentialVocabulary(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("authorization")
                || lower.contains("secret")
                || lower.contains("bearer")
                || lower.contains("api_key")
                || lower.contains("apikey")
                || lower.contains("token=")
                || lower.startsWith("sk-")
                || lower.contains("://sk-");
    }
private static void requireEndpointAuthority(String value) {
        if (value == null || value.length() > 200 || containsCredentialVocabulary(value)) {
            throw new IllegalArgumentException(
                    "endpointAuthority contains forbidden credential vocabulary");
        }
        URI endpoint;
        try {
            endpoint = URI.create(value);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("endpointAuthority must be a redacted URI authority");
        }
        String scheme = endpoint.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                || endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null
                || endpoint.getFragment() != null
                || (endpoint.getPath() != null && !endpoint.getPath().isEmpty())) {
            throw new IllegalArgumentException("endpointAuthority must omit credentials and paths");
        }
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SettingsDiagnosticsSnapshot)) return false;
        SettingsDiagnosticsSnapshot that = (SettingsDiagnosticsSnapshot) other;
        return java.util.Objects.equals(cards, that.cards) && java.util.Objects.equals(debug, that.debug);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(cards);
        hash = 31 * hash + java.util.Objects.hashCode(debug);
        return hash;
    }
    @Override public String toString() { return "SettingsDiagnosticsSnapshot[cards=" + cards + ", debug=" + debug + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SettingsDiagnosticsSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(SettingsDiagnosticsSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SettingsDiagnosticsSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(SettingsDiagnosticsSnapshot.class, "cards", SettingsDiagnosticsSnapshot::cards), new dev.openallay.value.ValueSchema.Component<>(SettingsDiagnosticsSnapshot.class, "debug", SettingsDiagnosticsSnapshot::debug)), arguments -> new SettingsDiagnosticsSnapshot((List) arguments[0], (Optional) arguments[1]));
        }
    }
}
