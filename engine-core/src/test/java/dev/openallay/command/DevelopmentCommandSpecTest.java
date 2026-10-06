package dev.openallay.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.devmode.DevelopmentCommandHandler;
import dev.openallay.devmode.DevelopmentToolInspector;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import dev.openallay.trace.replay.ReplayMetrics;
import dev.openallay.trace.replay.ReplayReport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DevelopmentCommandSpecTest {
    private final DevelopmentCommandHandler handler =
            new DevelopmentCommandHandler(new DevelopmentToolInspector(new ToolRegistry()));

    @Test
    void freezesDevelopmentGrammar() {
        assertEquals("openallay", DevelopmentCommandSpec.ROOT);
        assertEquals("dev", DevelopmentCommandSpec.GROUP);
        assertEquals(List.of("tools:NONE:TOOLS", "replay:TRACE_WORD:REPLAY", "invoke:TOOL_GREEDY:INVOKE"),
                DevelopmentCommandSpec.routes().stream().map(route -> String.join("/", route.literals())
                        + ":" + route.argument() + ":" + route.action()).toList());
        assertEquals("trace", CommandArgument.TRACE_WORD.argumentName());
        assertEquals(CommandArgument.Kind.WORD, CommandArgument.TRACE_WORD.kind());
        assertEquals("tool", CommandArgument.TOOL_GREEDY.argumentName());
        assertEquals(CommandArgument.Kind.GREEDY, CommandArgument.TOOL_GREEDY.kind());
    }

    @Test
    void preservesToolsAndInvokeFeedbackIncludingFailureReturn() {
        Source source = new Source();
        assertEquals(1, DevelopmentCommandSpec.dispatch(handler, DevelopmentCommandSpec.Action.TOOLS, null, source));
        assertTrue(source.successes.isEmpty());
        assertEquals(1, DevelopmentCommandSpec.dispatch(handler, DevelopmentCommandSpec.Action.INVOKE,
                "missing tool with spaces", source));
        assertEquals(List.of("FAILURE unknown_tool: Unknown tool: missing tool with spaces"), source.successes);
        assertTrue(source.failures.isEmpty());
        assertEquals(0, source.replayCalls);
    }

    @Test
    void preservesReplaySourceFeedbackAndReturnCode() {
        Source source = new Source();
        assertEquals(1, DevelopmentCommandSpec.dispatch(handler, DevelopmentCommandSpec.Action.REPLAY, "exact:trace", source));
        assertEquals("exact:trace", source.trace);
        assertEquals(1, source.replayCalls);
        assertEquals(source.report.chatLines(), source.successes);
        source.successes.clear();
        source.report = report(false);
        assertEquals(0, DevelopmentCommandSpec.dispatch(handler, DevelopmentCommandSpec.Action.REPLAY, "failed", source));
        assertEquals(source.report.chatLines(), source.successes);
        source.successes.clear();
        source.replayFailure = true;
        assertEquals(0, DevelopmentCommandSpec.dispatch(handler, DevelopmentCommandSpec.Action.REPLAY, "missing", source));
        assertEquals(List.of("FAILURE unknown_trace: Unknown trace: missing"), source.failures);
        assertTrue(source.successes.isEmpty());
    }

    @Test
    void traceSuggestionsKeepSourceOrderAndSilenceFailures() {
        Source source = new Source();
        assertEquals(List.of("b", "a"), DevelopmentCommandSpec.traceSuggestions(source));
        source.idsFailure = true;
        assertEquals(List.of(), DevelopmentCommandSpec.traceSuggestions(source));
        assertTrue(source.successes.isEmpty());
        assertTrue(source.failures.isEmpty());
        assertEquals(0, source.replayCalls);
    }

    private static ReplayReport report(boolean passed) {
        return new ReplayReport("trace", passed, List.of(), new ReplayMetrics(0, 0, 0, 0, 0, 0, 0), null);
    }

    private static final class Source implements DevelopmentCommandSpec.Source {
        final List<String> successes = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        ReplayReport report = report(true);
        boolean replayFailure;
        boolean idsFailure;
        String trace;
        int replayCalls;
        @Override public void success(String line) { successes.add(line); }
        @Override public void failure(String line) { failures.add(line); }
        @Override public ToolResult<List<String>> traceIds() {
            return idsFailure ? new ToolResult.Failure<>("load_failed", "not loaded")
                    : new ToolResult.Success<>(List.of("b", "a"));
        }
        @Override public ToolResult<ReplayReport> replay(String traceId) {
            trace = traceId;
            replayCalls++;
            return replayFailure ? new ToolResult.Failure<>("unknown_trace", "Unknown trace: " + traceId)
                    : new ToolResult.Success<>(report);
        }
    }
}
