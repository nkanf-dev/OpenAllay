package dev.openallay.command;

import dev.openallay.devmode.DevelopmentCommandHandler;
import dev.openallay.tool.ToolResult;
import dev.openallay.trace.replay.ReplayReport;
import java.util.List;

/** Development routes and feedback; the native source retains permissions and replay custody. */
public final class DevelopmentCommandSpec {
    public static final String ROOT = "openallay";
    public static final String GROUP = "dev";

    public enum Action { TOOLS, REPLAY, INVOKE }

    public record Route(List<String> literals, CommandArgument argument, Action action) {
        public Route { literals = List.copyOf(literals); }
    }

    public interface Source {
        void success(String line);
        void failure(String line);
        ToolResult<List<String>> traceIds();
        ToolResult<ReplayReport> replay(String traceId);
    }

    private static final List<Route> ROUTES = List.of(
            new Route(List.of("tools"), CommandArgument.NONE, Action.TOOLS),
            new Route(List.of("replay"), CommandArgument.TRACE_WORD, Action.REPLAY),
            new Route(List.of("invoke"), CommandArgument.TOOL_GREEDY, Action.INVOKE));

    private DevelopmentCommandSpec() {}

    public static List<Route> routes() { return ROUTES; }

    public static List<String> traceSuggestions(Source source) {
        ToolResult<List<String>> result = source.traceIds();
        if (result instanceof ToolResult.Success<List<String>> success) {
            return success.value();
        }
        return List.of();
    }

    public static int dispatch(
            DevelopmentCommandHandler handler, Action action, String value, Source source) {
        return switch (action) {
            case TOOLS -> {
                handler.listTools().forEach(source::success);
                yield 1;
            }
            case INVOKE -> {
                source.success(handler.invoke(value));
                yield 1;
            }
            case REPLAY -> {
                ToolResult<ReplayReport> result = source.replay(value);
                if (result instanceof ToolResult.Success<ReplayReport> success) {
                    success.value().chatLines().forEach(source::success);
                    yield success.value().passed() ? 1 : 0;
                }
                ToolResult.Failure<ReplayReport> failure = (ToolResult.Failure<ReplayReport>) result;
                source.failure("FAILURE " + failure.code() + ": " + failure.message());
                yield 0;
            }
        };
    }
}
