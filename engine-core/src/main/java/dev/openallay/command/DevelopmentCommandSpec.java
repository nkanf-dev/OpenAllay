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

    @dev.openallay.value.ValueType(Route.ValueSchemaProvider.class)
public static final class Route {
    private final List<String> literals;
    private final CommandArgument argument;
    private final Action action;
    public Route(List<String> literals, CommandArgument argument, Action action) {
 literals = List.copyOf(literals);
        this.literals = literals;
        this.argument = argument;
        this.action = action;
    }
    public List<String> literals() { return literals; }
    public CommandArgument argument() { return argument; }
    public Action action() { return action; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Route)) return false;
        Route that = (Route) other;
        return java.util.Objects.equals(literals, that.literals) && java.util.Objects.equals(argument, that.argument) && java.util.Objects.equals(action, that.action);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(literals);
        hash = 31 * hash + java.util.Objects.hashCode(argument);
        hash = 31 * hash + java.util.Objects.hashCode(action);
        return hash;
    }
    @Override public String toString() { return "Route[literals=" + literals + ", argument=" + argument + ", action=" + action + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Route> schema() {
            return new dev.openallay.value.ValueSchema<>(Route.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Route>>asList(new dev.openallay.value.ValueSchema.Component<>(Route.class, "literals", Route::literals), new dev.openallay.value.ValueSchema.Component<>(Route.class, "argument", Route::argument), new dev.openallay.value.ValueSchema.Component<>(Route.class, "action", Route::action)), arguments -> new Route((List) arguments[0], (CommandArgument) arguments[1], (Action) arguments[2]));
        }
    }
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
