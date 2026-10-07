package dev.openallay.guide.e2e;

import dev.openallay.guide.GuideModelMode;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/** Explicit, inert-by-default configuration for the real Minecraft client harness. */
@dev.openallay.value.ValueType(GuideClientE2EConfig.ValueSchemaProvider.class)
public final class GuideClientE2EConfig {
    private final String scenario;
    private final String sessionId;
    private final String question;
    private final GuideModelMode modelMode;
    private final Path reportPath;
    private final Path tracePath;
    private final boolean shutdownAfterReport;
    private final int historySeedRequests;
    public GuideClientE2EConfig(String scenario, String sessionId, String question, GuideModelMode modelMode, Path reportPath, Path tracePath, boolean shutdownAfterReport, int historySeedRequests) {

        if (scenario == null || dev.openallay.util.Java8Strings.isBlank(scenario)) throw new IllegalArgumentException("scenario is required");
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid sessionId");
        }
        if (question == null || dev.openallay.util.Java8Strings.isBlank(question)) throw new IllegalArgumentException("question is required");
        java.util.Objects.requireNonNull(modelMode, "modelMode");
        java.util.Objects.requireNonNull(reportPath, "reportPath");
        java.util.Objects.requireNonNull(tracePath, "tracePath");
        if (historySeedRequests < 0) {
            throw new IllegalArgumentException("historySeedRequests must not be negative");
        }

        this.scenario = scenario;
        this.sessionId = sessionId;
        this.question = question;
        this.modelMode = modelMode;
        this.reportPath = reportPath;
        this.tracePath = tracePath;
        this.shutdownAfterReport = shutdownAfterReport;
        this.historySeedRequests = historySeedRequests;
    }
    public String scenario() { return scenario; }
    public String sessionId() { return sessionId; }
    public String question() { return question; }
    public GuideModelMode modelMode() { return modelMode; }
    public Path reportPath() { return reportPath; }
    public Path tracePath() { return tracePath; }
    public boolean shutdownAfterReport() { return shutdownAfterReport; }
    public int historySeedRequests() { return historySeedRequests; }
public static final String ENABLED = "openallay.e2e.enabled";
public GuideClientE2EConfig(
            String scenario,
            String sessionId,
            String question,
            GuideModelMode modelMode,
            Path reportPath,
            boolean shutdownAfterReport) {
        this(scenario, sessionId, question, modelMode, reportPath, shutdownAfterReport, 0);
    }
public GuideClientE2EConfig(
            String scenario,
            String sessionId,
            String question,
            GuideModelMode modelMode,
            Path reportPath,
            boolean shutdownAfterReport,
            int historySeedRequests) {
        this(
                scenario,
                sessionId,
                question,
                modelMode,
                reportPath,
                java.nio.file.Paths.get(reportPath.toString() + ".trace.json"),
                shutdownAfterReport,
                historySeedRequests);
    }
public static Optional<GuideClientE2EConfig> from(Properties properties) {
        if (!Boolean.parseBoolean(properties.getProperty(ENABLED, "false"))) {
            return Optional.empty();
        }
        String mode = properties.getProperty("openallay.e2e.modelMode", "client")
                .toUpperCase(Locale.ROOT);
        return Optional.of(new GuideClientE2EConfig(
                properties.getProperty("openallay.e2e.scenario", "real-client-guide"),
                properties.getProperty("openallay.e2e.session", "e2e"),
                required(properties, "openallay.e2e.question"),
                GuideModelMode.valueOf(mode),
                java.nio.file.Paths.get(required(properties, "openallay.e2e.report")),
                java.nio.file.Paths.get(properties.getProperty(
                        "openallay.e2e.trace",
                        required(properties, "openallay.e2e.report") + ".trace.json")),
                Boolean.parseBoolean(properties.getProperty("openallay.e2e.shutdown", "true")),
                nonNegativeInteger(properties, "openallay.e2e.historySeedRequests", 0)));
    }
private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) throw new IllegalArgumentException(key + " is required");
        return value;
    }
private static int nonNegativeInteger(Properties properties, String key, int fallback) {
        int value = Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
        if (value < 0) throw new IllegalArgumentException(key + " must not be negative");
        return value;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideClientE2EConfig)) return false;
        GuideClientE2EConfig that = (GuideClientE2EConfig) other;
        return java.util.Objects.equals(scenario, that.scenario) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(question, that.question) && java.util.Objects.equals(modelMode, that.modelMode) && java.util.Objects.equals(reportPath, that.reportPath) && java.util.Objects.equals(tracePath, that.tracePath) && shutdownAfterReport == that.shutdownAfterReport && historySeedRequests == that.historySeedRequests;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(scenario);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(question);
        hash = 31 * hash + java.util.Objects.hashCode(modelMode);
        hash = 31 * hash + java.util.Objects.hashCode(reportPath);
        hash = 31 * hash + java.util.Objects.hashCode(tracePath);
        hash = 31 * hash + Boolean.hashCode(shutdownAfterReport);
        hash = 31 * hash + Integer.hashCode(historySeedRequests);
        return hash;
    }
    @Override public String toString() { return "GuideClientE2EConfig[scenario=" + scenario + ", sessionId=" + sessionId + ", question=" + question + ", modelMode=" + modelMode + ", reportPath=" + reportPath + ", tracePath=" + tracePath + ", shutdownAfterReport=" + shutdownAfterReport + ", historySeedRequests=" + historySeedRequests + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideClientE2EConfig> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideClientE2EConfig.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideClientE2EConfig>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideClientE2EConfig.class, "scenario", GuideClientE2EConfig::scenario), new dev.openallay.value.ValueSchema.Component<>(GuideClientE2EConfig.class, "sessionId", GuideClientE2EConfig::sessionId), new dev.openallay.value.ValueSchema.Component<>(GuideClientE2EConfig.class, "question", GuideClientE2EConfig::question), new dev.openallay.value.ValueSchema.Component<>(GuideClientE2EConfig.class, "modelMode", GuideClientE2EConfig::modelMode), new dev.openallay.value.ValueSchema.Component<>(GuideClientE2EConfig.class, "reportPath", GuideClientE2EConfig::reportPath), new dev.openallay.value.ValueSchema.Component<>(GuideClientE2EConfig.class, "tracePath", GuideClientE2EConfig::tracePath), new dev.openallay.value.ValueSchema.Component<>(GuideClientE2EConfig.class, "shutdownAfterReport", GuideClientE2EConfig::shutdownAfterReport), new dev.openallay.value.ValueSchema.Component<>(GuideClientE2EConfig.class, "historySeedRequests", GuideClientE2EConfig::historySeedRequests)), arguments -> new GuideClientE2EConfig((String) arguments[0], (String) arguments[1], (String) arguments[2], (GuideModelMode) arguments[3], (Path) arguments[4], (Path) arguments[5], (Boolean) arguments[6], (Integer) arguments[7]));
        }
    }
}
