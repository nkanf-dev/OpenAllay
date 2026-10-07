package dev.openallay.guide.e2e;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideTopology;
import dev.openallay.guide.GuideToolStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@dev.openallay.value.ValueType(GuideE2EReport.ValueSchemaProvider.class)
public final class GuideE2EReport {
    private final String loader;
    private final String gameVersion;
    private final String modVersion;
    private final String scenario;
    private final GuideTopology topology;
    private final UUID requestId;
    private final String sessionId;
    private final List<GuideRequestStatus> transitions;
    private final List<String> toolIds;
    private final List<ToolProbe> toolProbes;
    private final List<EvidenceMetadata> evidence;
    private final List<String> timelineKinds;
    private final Map<String, Long> semanticMetrics;
    private final List<String> semanticDiagnosticCodes;
    private final List<String> controlledComponentTypes;
    private final String historyPageState;
    private final Map<String, Long> historyMetrics;
    private final GuideRequestStatus outcome;
    private final String failureCode;
    private final String failureMessage;
    private final Map<String, Long> timingsMillis;
    private final Map<String, String> payloadHashes;
    public GuideE2EReport(String loader, String gameVersion, String modVersion, String scenario, GuideTopology topology, UUID requestId, String sessionId, List<GuideRequestStatus> transitions, List<String> toolIds, List<ToolProbe> toolProbes, List<EvidenceMetadata> evidence, List<String> timelineKinds, Map<String, Long> semanticMetrics, List<String> semanticDiagnosticCodes, List<String> controlledComponentTypes, String historyPageState, Map<String, Long> historyMetrics, GuideRequestStatus outcome, String failureCode, String failureMessage, Map<String, Long> timingsMillis, Map<String, String> payloadHashes) {

        transitions = List.copyOf(transitions);
        toolIds = List.copyOf(toolIds);
        toolProbes = List.copyOf(toolProbes);
        evidence = List.copyOf(evidence);
        timelineKinds = List.copyOf(timelineKinds);
        semanticMetrics = Map.copyOf(semanticMetrics);
        semanticDiagnosticCodes = List.copyOf(semanticDiagnosticCodes);
        controlledComponentTypes = List.copyOf(controlledComponentTypes);
        if (historyPageState == null || historyPageState.isBlank()) {
            throw new IllegalArgumentException("historyPageState is required");
        }
        historyMetrics = Map.copyOf(historyMetrics);
        timingsMillis = Map.copyOf(timingsMillis);
        payloadHashes = Map.copyOf(payloadHashes);

        this.loader = loader;
        this.gameVersion = gameVersion;
        this.modVersion = modVersion;
        this.scenario = scenario;
        this.topology = topology;
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.transitions = transitions;
        this.toolIds = toolIds;
        this.toolProbes = toolProbes;
        this.evidence = evidence;
        this.timelineKinds = timelineKinds;
        this.semanticMetrics = semanticMetrics;
        this.semanticDiagnosticCodes = semanticDiagnosticCodes;
        this.controlledComponentTypes = controlledComponentTypes;
        this.historyPageState = historyPageState;
        this.historyMetrics = historyMetrics;
        this.outcome = outcome;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.timingsMillis = timingsMillis;
        this.payloadHashes = payloadHashes;
    }
    public String loader() { return loader; }
    public String gameVersion() { return gameVersion; }
    public String modVersion() { return modVersion; }
    public String scenario() { return scenario; }
    public GuideTopology topology() { return topology; }
    public UUID requestId() { return requestId; }
    public String sessionId() { return sessionId; }
    public List<GuideRequestStatus> transitions() { return transitions; }
    public List<String> toolIds() { return toolIds; }
    public List<ToolProbe> toolProbes() { return toolProbes; }
    public List<EvidenceMetadata> evidence() { return evidence; }
    public List<String> timelineKinds() { return timelineKinds; }
    public Map<String, Long> semanticMetrics() { return semanticMetrics; }
    public List<String> semanticDiagnosticCodes() { return semanticDiagnosticCodes; }
    public List<String> controlledComponentTypes() { return controlledComponentTypes; }
    public String historyPageState() { return historyPageState; }
    public Map<String, Long> historyMetrics() { return historyMetrics; }
    public GuideRequestStatus outcome() { return outcome; }
    public String failureCode() { return failureCode; }
    public String failureMessage() { return failureMessage; }
    public Map<String, Long> timingsMillis() { return timingsMillis; }
    public Map<String, String> payloadHashes() { return payloadHashes; }
@dev.openallay.value.ValueType(ToolProbe.ValueSchemaProvider.class)
public static final class ToolProbe {
    private final String toolId;
    private final GuideToolStatus status;
    private final String section;
    private final String failureCode;
    public ToolProbe(String toolId, GuideToolStatus status, String section, String failureCode) {
        this.toolId = toolId;
        this.status = status;
        this.section = section;
        this.failureCode = failureCode;
    }
    public String toolId() { return toolId; }
    public GuideToolStatus status() { return status; }
    public String section() { return section; }
    public String failureCode() { return failureCode; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ToolProbe)) return false;
        ToolProbe that = (ToolProbe) other;
        return java.util.Objects.equals(toolId, that.toolId) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(section, that.section) && java.util.Objects.equals(failureCode, that.failureCode);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(toolId);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(section);
        hash = 31 * hash + java.util.Objects.hashCode(failureCode);
        return hash;
    }
    @Override public String toString() { return "ToolProbe[toolId=" + toolId + ", status=" + status + ", section=" + section + ", failureCode=" + failureCode + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ToolProbe> schema() {
            return new dev.openallay.value.ValueSchema<>(ToolProbe.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ToolProbe>>asList(new dev.openallay.value.ValueSchema.Component<>(ToolProbe.class, "toolId", ToolProbe::toolId), new dev.openallay.value.ValueSchema.Component<>(ToolProbe.class, "status", ToolProbe::status), new dev.openallay.value.ValueSchema.Component<>(ToolProbe.class, "section", ToolProbe::section), new dev.openallay.value.ValueSchema.Component<>(ToolProbe.class, "failureCode", ToolProbe::failureCode)), arguments -> new ToolProbe((String) arguments[0], (GuideToolStatus) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideE2EReport)) return false;
        GuideE2EReport that = (GuideE2EReport) other;
        return java.util.Objects.equals(loader, that.loader) && java.util.Objects.equals(gameVersion, that.gameVersion) && java.util.Objects.equals(modVersion, that.modVersion) && java.util.Objects.equals(scenario, that.scenario) && java.util.Objects.equals(topology, that.topology) && java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(transitions, that.transitions) && java.util.Objects.equals(toolIds, that.toolIds) && java.util.Objects.equals(toolProbes, that.toolProbes) && java.util.Objects.equals(evidence, that.evidence) && java.util.Objects.equals(timelineKinds, that.timelineKinds) && java.util.Objects.equals(semanticMetrics, that.semanticMetrics) && java.util.Objects.equals(semanticDiagnosticCodes, that.semanticDiagnosticCodes) && java.util.Objects.equals(controlledComponentTypes, that.controlledComponentTypes) && java.util.Objects.equals(historyPageState, that.historyPageState) && java.util.Objects.equals(historyMetrics, that.historyMetrics) && java.util.Objects.equals(outcome, that.outcome) && java.util.Objects.equals(failureCode, that.failureCode) && java.util.Objects.equals(failureMessage, that.failureMessage) && java.util.Objects.equals(timingsMillis, that.timingsMillis) && java.util.Objects.equals(payloadHashes, that.payloadHashes);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(loader);
        hash = 31 * hash + java.util.Objects.hashCode(gameVersion);
        hash = 31 * hash + java.util.Objects.hashCode(modVersion);
        hash = 31 * hash + java.util.Objects.hashCode(scenario);
        hash = 31 * hash + java.util.Objects.hashCode(topology);
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(transitions);
        hash = 31 * hash + java.util.Objects.hashCode(toolIds);
        hash = 31 * hash + java.util.Objects.hashCode(toolProbes);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        hash = 31 * hash + java.util.Objects.hashCode(timelineKinds);
        hash = 31 * hash + java.util.Objects.hashCode(semanticMetrics);
        hash = 31 * hash + java.util.Objects.hashCode(semanticDiagnosticCodes);
        hash = 31 * hash + java.util.Objects.hashCode(controlledComponentTypes);
        hash = 31 * hash + java.util.Objects.hashCode(historyPageState);
        hash = 31 * hash + java.util.Objects.hashCode(historyMetrics);
        hash = 31 * hash + java.util.Objects.hashCode(outcome);
        hash = 31 * hash + java.util.Objects.hashCode(failureCode);
        hash = 31 * hash + java.util.Objects.hashCode(failureMessage);
        hash = 31 * hash + java.util.Objects.hashCode(timingsMillis);
        hash = 31 * hash + java.util.Objects.hashCode(payloadHashes);
        return hash;
    }
    @Override public String toString() { return "GuideE2EReport[loader=" + loader + ", gameVersion=" + gameVersion + ", modVersion=" + modVersion + ", scenario=" + scenario + ", topology=" + topology + ", requestId=" + requestId + ", sessionId=" + sessionId + ", transitions=" + transitions + ", toolIds=" + toolIds + ", toolProbes=" + toolProbes + ", evidence=" + evidence + ", timelineKinds=" + timelineKinds + ", semanticMetrics=" + semanticMetrics + ", semanticDiagnosticCodes=" + semanticDiagnosticCodes + ", controlledComponentTypes=" + controlledComponentTypes + ", historyPageState=" + historyPageState + ", historyMetrics=" + historyMetrics + ", outcome=" + outcome + ", failureCode=" + failureCode + ", failureMessage=" + failureMessage + ", timingsMillis=" + timingsMillis + ", payloadHashes=" + payloadHashes + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideE2EReport> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideE2EReport.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideE2EReport>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "loader", GuideE2EReport::loader), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "gameVersion", GuideE2EReport::gameVersion), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "modVersion", GuideE2EReport::modVersion), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "scenario", GuideE2EReport::scenario), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "topology", GuideE2EReport::topology), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "requestId", GuideE2EReport::requestId), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "sessionId", GuideE2EReport::sessionId), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "transitions", GuideE2EReport::transitions), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "toolIds", GuideE2EReport::toolIds), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "toolProbes", GuideE2EReport::toolProbes), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "evidence", GuideE2EReport::evidence), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "timelineKinds", GuideE2EReport::timelineKinds), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "semanticMetrics", GuideE2EReport::semanticMetrics), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "semanticDiagnosticCodes", GuideE2EReport::semanticDiagnosticCodes), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "controlledComponentTypes", GuideE2EReport::controlledComponentTypes), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "historyPageState", GuideE2EReport::historyPageState), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "historyMetrics", GuideE2EReport::historyMetrics), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "outcome", GuideE2EReport::outcome), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "failureCode", GuideE2EReport::failureCode), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "failureMessage", GuideE2EReport::failureMessage), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "timingsMillis", GuideE2EReport::timingsMillis), new dev.openallay.value.ValueSchema.Component<>(GuideE2EReport.class, "payloadHashes", GuideE2EReport::payloadHashes)), arguments -> new GuideE2EReport((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (GuideTopology) arguments[4], (UUID) arguments[5], (String) arguments[6], (List) arguments[7], (List) arguments[8], (List) arguments[9], (List) arguments[10], (List) arguments[11], (Map) arguments[12], (List) arguments[13], (List) arguments[14], (String) arguments[15], (Map) arguments[16], (GuideRequestStatus) arguments[17], (String) arguments[18], (String) arguments[19], (Map) arguments[20], (Map) arguments[21]));
        }
    }
}
