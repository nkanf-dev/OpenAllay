package dev.openallay.command;

import dev.openallay.guide.GuideCommandFacade;
import dev.openallay.guide.GuideModelMode;
import dev.openallay.guide.GuideNotice;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Finite command routes. Native projectors own parsing, completion and source custody. */
public final class GuideCommandSpec {
    public static final String ROOT = "guide";

    public enum Action {
        OPEN, CANCEL, RETRY, CLEAR, STATUS, SKILLS, SOURCES,
        MODEL_LIST, MODEL_PROFILE, MODEL_CLIENT, MODEL_SERVER,
        SESSION_LIST, SESSION_SELECT, SESSION_CLOSE, ASK
    }

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

    private static final List<Route> ROUTES = List.of(
            route(Action.OPEN, CommandArgument.NONE),
            route(Action.CANCEL, CommandArgument.NONE, "cancel"),
            route(Action.RETRY, CommandArgument.NONE, "retry"),
            route(Action.CLEAR, CommandArgument.NONE, "clear"),
            route(Action.STATUS, CommandArgument.NONE, "status"),
            route(Action.SKILLS, CommandArgument.NONE, "skills"),
            route(Action.SOURCES, CommandArgument.NONE, "sources"),
            route(Action.MODEL_LIST, CommandArgument.NONE, "model", "list"),
            route(Action.MODEL_PROFILE, CommandArgument.ID_WORD, "model", "profile"),
            route(Action.MODEL_CLIENT, CommandArgument.NONE, "model", "client"),
            route(Action.MODEL_SERVER, CommandArgument.NONE, "model", "server"),
            route(Action.SESSION_LIST, CommandArgument.NONE, "session", "list"),
            route(Action.SESSION_SELECT, CommandArgument.ID_WORD, "session", "new"),
            route(Action.SESSION_SELECT, CommandArgument.ID_WORD, "session", "switch"),
            route(Action.SESSION_CLOSE, CommandArgument.ID_WORD, "session", "close"),
            route(Action.ASK, CommandArgument.QUESTION_GREEDY, "ask"),
            route(Action.ASK, CommandArgument.QUESTION_GREEDY));

    private GuideCommandSpec() {}

    public static List<Route> routes() { return ROUTES; }

    private static Route route(Action action, CommandArgument argument, String... literals) {
        return new Route(List.of(literals), argument, action);
    }

    /** Resolve the execution actor once, only for actions that use an actor. */
    public static int dispatch(
            GuideCommandFacade guide, Action action, String value,
            Supplier<UUID> actor, Consumer<GuideNotice> notices) {
        switch (action) {
            case OPEN -> guide.open(actor.get(), notices);
            case CANCEL -> guide.cancel(actor.get(), notices);
            case RETRY -> guide.retry(actor.get(), notices);
            case CLEAR -> guide.clear(actor.get(), notices);
            case STATUS -> guide.status(actor.get(), notices);
            case SKILLS -> guide.skills(notices);
            case SOURCES -> guide.sources(notices);
            case MODEL_LIST -> guide.models(actor.get(), notices);
            case MODEL_PROFILE -> guide.modelProfile(actor.get(), value, notices);
            case MODEL_CLIENT -> guide.model(actor.get(), GuideModelMode.CLIENT, notices);
            case MODEL_SERVER -> guide.model(actor.get(), GuideModelMode.SERVER, notices);
            case SESSION_LIST -> guide.sessions(actor.get(), notices);
            case SESSION_SELECT -> guide.select(actor.get(), value, notices);
            case SESSION_CLOSE -> guide.close(actor.get(), value, notices);
            case ASK -> guide.ask(actor.get(), value, notices);
        }
        return 1;
    }
}
