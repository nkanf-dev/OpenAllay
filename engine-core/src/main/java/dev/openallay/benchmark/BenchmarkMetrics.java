package dev.openallay.benchmark;

public record BenchmarkMetrics(
        boolean success,
        int modelTurns,
        int toolCalls,
        int javascriptCalls,
        int skillLoads,
        int skillReloads,
        int duplicateSkillLoads,
        int invalidCalls,
        int correctedCalls,
        String terminalCode) {
    public BenchmarkMetrics {
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
    }
}
