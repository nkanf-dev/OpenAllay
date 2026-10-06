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

    public record Route(List<String> literals, CommandArgument argument, Action action) {
        public Route { literals = List.copyOf(literals); }
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
