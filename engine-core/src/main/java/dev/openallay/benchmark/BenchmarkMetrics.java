package dev.openallay.benchmark;

@dev.openallay.value.ValueType(BenchmarkMetrics.ValueSchemaProvider.class)
public final class BenchmarkMetrics {
    private final boolean success;
    private final int modelTurns;
    private final int toolCalls;
    private final int javascriptCalls;
    private final int skillLoads;
    private final int skillReloads;
    private final int duplicateSkillLoads;
    private final int invalidCalls;
    private final int correctedCalls;
    private final String terminalCode;
    public BenchmarkMetrics(boolean success, int modelTurns, int toolCalls, int javascriptCalls, int skillLoads, int skillReloads, int duplicateSkillLoads, int invalidCalls, int correctedCalls, String terminalCode) {

        if (modelTurns < 0
                || toolCalls < 0
                || javascriptCalls < 0
                || skillLoads < 0
                || skillReloads < 0
                || duplicateSkillLoads < 0
                || invalidCalls < 0
                || correctedCalls < 0) {
            throw new IllegalArgumentException("Benchmark counters must not be negative");
        }
        terminalCode = terminalCode == null ? "" : terminalCode;

        this.success = success;
        this.modelTurns = modelTurns;
        this.toolCalls = toolCalls;
        this.javascriptCalls = javascriptCalls;
        this.skillLoads = skillLoads;
        this.skillReloads = skillReloads;
        this.duplicateSkillLoads = duplicateSkillLoads;
        this.invalidCalls = invalidCalls;
        this.correctedCalls = correctedCalls;
        this.terminalCode = terminalCode;
    }
    public boolean success() { return success; }
    public int modelTurns() { return modelTurns; }
    public int toolCalls() { return toolCalls; }
    public int javascriptCalls() { return javascriptCalls; }
    public int skillLoads() { return skillLoads; }
    public int skillReloads() { return skillReloads; }
    public int duplicateSkillLoads() { return duplicateSkillLoads; }
    public int invalidCalls() { return invalidCalls; }
    public int correctedCalls() { return correctedCalls; }
    public String terminalCode() { return terminalCode; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BenchmarkMetrics)) return false;
        BenchmarkMetrics that = (BenchmarkMetrics) other;
        return success == that.success && modelTurns == that.modelTurns && toolCalls == that.toolCalls && javascriptCalls == that.javascriptCalls && skillLoads == that.skillLoads && skillReloads == that.skillReloads && duplicateSkillLoads == that.duplicateSkillLoads && invalidCalls == that.invalidCalls && correctedCalls == that.correctedCalls && java.util.Objects.equals(terminalCode, that.terminalCode);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(success);
        hash = 31 * hash + Integer.hashCode(modelTurns);
        hash = 31 * hash + Integer.hashCode(toolCalls);
        hash = 31 * hash + Integer.hashCode(javascriptCalls);
        hash = 31 * hash + Integer.hashCode(skillLoads);
        hash = 31 * hash + Integer.hashCode(skillReloads);
        hash = 31 * hash + Integer.hashCode(duplicateSkillLoads);
        hash = 31 * hash + Integer.hashCode(invalidCalls);
        hash = 31 * hash + Integer.hashCode(correctedCalls);
        hash = 31 * hash + java.util.Objects.hashCode(terminalCode);
        return hash;
    }
    @Override public String toString() { return "BenchmarkMetrics[success=" + success + ", modelTurns=" + modelTurns + ", toolCalls=" + toolCalls + ", javascriptCalls=" + javascriptCalls + ", skillLoads=" + skillLoads + ", skillReloads=" + skillReloads + ", duplicateSkillLoads=" + duplicateSkillLoads + ", invalidCalls=" + invalidCalls + ", correctedCalls=" + correctedCalls + ", terminalCode=" + terminalCode + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<BenchmarkMetrics> schema() {
            return new dev.openallay.value.ValueSchema<>(BenchmarkMetrics.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<BenchmarkMetrics>>asList(new dev.openallay.value.ValueSchema.Component<>(BenchmarkMetrics.class, "success", BenchmarkMetrics::success), new dev.openallay.value.ValueSchema.Component<>(BenchmarkMetrics.class, "modelTurns", BenchmarkMetrics::modelTurns), new dev.openallay.value.ValueSchema.Component<>(BenchmarkMetrics.class, "toolCalls", BenchmarkMetrics::toolCalls), new dev.openallay.value.ValueSchema.Component<>(BenchmarkMetrics.class, "javascriptCalls", BenchmarkMetrics::javascriptCalls), new dev.openallay.value.ValueSchema.Component<>(BenchmarkMetrics.class, "skillLoads", BenchmarkMetrics::skillLoads), new dev.openallay.value.ValueSchema.Component<>(BenchmarkMetrics.class, "skillReloads", BenchmarkMetrics::skillReloads), new dev.openallay.value.ValueSchema.Component<>(BenchmarkMetrics.class, "duplicateSkillLoads", BenchmarkMetrics::duplicateSkillLoads), new dev.openallay.value.ValueSchema.Component<>(BenchmarkMetrics.class, "invalidCalls", BenchmarkMetrics::invalidCalls), new dev.openallay.value.ValueSchema.Component<>(BenchmarkMetrics.class, "correctedCalls", BenchmarkMetrics::correctedCalls), new dev.openallay.value.ValueSchema.Component<>(BenchmarkMetrics.class, "terminalCode", BenchmarkMetrics::terminalCode)), arguments -> new BenchmarkMetrics((Boolean) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (Integer) arguments[4], (Integer) arguments[5], (Integer) arguments[6], (Integer) arguments[7], (Integer) arguments[8], (String) arguments[9]));
        }
    }
}
