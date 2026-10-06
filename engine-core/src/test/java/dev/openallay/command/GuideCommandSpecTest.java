package dev.openallay.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.guide.GuideCommandFacade;
import dev.openallay.guide.GuideContextProvider;
import dev.openallay.guide.GuideNotice;
import dev.openallay.guide.GuideRemoteEndpoint;
import dev.openallay.guide.GuideServiceManager;
import dev.openallay.tool.ToolResult;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class GuideCommandSpecTest {
    @Test
    void freezesLiteralOrderArgumentKindsAndFallback() {
        assertEquals("guide", GuideCommandSpec.ROOT);
        assertEquals(List.of(
                ":NONE:OPEN", "cancel:NONE:CANCEL", "retry:NONE:RETRY", "clear:NONE:CLEAR",
                "status:NONE:STATUS", "skills:NONE:SKILLS", "sources:NONE:SOURCES",
                "model/list:NONE:MODEL_LIST", "model/profile:ID_WORD:MODEL_PROFILE",
                "model/client:NONE:MODEL_CLIENT", "model/server:NONE:MODEL_SERVER",
                "session/list:NONE:SESSION_LIST", "session/new:ID_WORD:SESSION_SELECT",
                "session/switch:ID_WORD:SESSION_SELECT", "session/close:ID_WORD:SESSION_CLOSE",
                "ask:QUESTION_GREEDY:ASK", ":QUESTION_GREEDY:ASK"),
                GuideCommandSpec.routes().stream().map(route ->
                        String.join("/", route.literals()) + ":" + route.argument() + ":" + route.action())
                        .toList());
        assertEquals("id", CommandArgument.ID_WORD.argumentName());
        assertEquals(CommandArgument.Kind.WORD, CommandArgument.ID_WORD.kind());
        assertEquals("question", CommandArgument.QUESTION_GREEDY.argumentName());
        assertEquals(CommandArgument.Kind.GREEDY, CommandArgument.QUESTION_GREEDY.kind());
        assertThrows(UnsupportedOperationException.class, () -> GuideCommandSpec.routes().clear());
        assertThrows(UnsupportedOperationException.class, () ->
                GuideCommandSpec.routes().get(1).literals().clear());
    }

    @Test
    void actorFreeSourceActionDoesNotResolveAnActor() {
        GuideContextProvider contexts = new GuideContextProvider() {
            @Override public ToolResult<dev.openallay.context.ToolInvocationContext> capture(
                    java.util.Set<dev.openallay.context.ContextCapability> capabilities, String correlation) {
                throw new AssertionError("sources must not capture actor context");
            }
            @Override public ToolResult<Integer> refreshKnowledge() {
                return new ToolResult.Failure<>("refresh_failed", "not refreshed");
            }
        };
        GuideCommandFacade facade = new GuideCommandFacade(null, null, contexts, null);
        List<GuideNotice> notices = new ArrayList<>();
        assertEquals(1, GuideCommandSpec.dispatch(facade, GuideCommandSpec.Action.SOURCES, null,
                () -> { throw new AssertionError("sources must not resolve actor"); }, notices::add));
        assertEquals("refresh_failed: not refreshed", notices.get(0).message());
    }

    @Test
    void dispatchKeepsExecutionActorAndFacadeFeedback() {
        GuideContextProvider contexts = (capabilities, correlation) -> new ToolResult.Success<>(
                dev.openallay.context.ToolInvocationContext.developmentConsole(correlation));
        GuideRemoteEndpoint remote = new GuideRemoteEndpoint() {
            @Override public boolean serverModelAvailable() { return false; }
            @Override public boolean serverToolsAvailable() { return false; }
            @Override public boolean ask(UUID id, String session, String question,
                    java.util.function.Consumer<dev.openallay.agent.AgentEvent> events) { return false; }
            @Override public boolean cancel(UUID id) { return false; }
            @Override public void disconnect() {}
        };
        GuideServiceManager services = new GuideServiceManager(null, remote, contexts,
                Runnable::run, Clock.systemUTC(), dev.openallay.json.EngineJson.create());
        List<UUID> openedActors = new ArrayList<>();
        GuideCommandFacade facade = new GuideCommandFacade(null, services, contexts, service -> {
            openedActors.add(service.snapshot().actorId());
            return new ToolResult.Failure<>("gui_unavailable", "not built");
        });
        UUID actor = UUID.randomUUID();
        AtomicInteger actorReads = new AtomicInteger();
        java.util.function.Supplier<UUID> source = () -> { actorReads.incrementAndGet(); return actor; };
        List<GuideNotice> notices = new ArrayList<>();
        assertEquals(1, GuideCommandSpec.dispatch(facade, GuideCommandSpec.Action.OPEN, null, source, notices::add));
        assertEquals(List.of(actor), openedActors);
        assertEquals(1, actorReads.get());
        assertTrue(notices.get(0).message().contains("gui_unavailable"));

        assertEquals(1, GuideCommandSpec.dispatch(facade, GuideCommandSpec.Action.SESSION_SELECT,
                "other", source, notices::add));
        assertEquals("other", services.forActor(actor).snapshot().selectedSession());
        assertEquals(2, actorReads.get());
        assertEquals(1, GuideCommandSpec.dispatch(facade, GuideCommandSpec.Action.ASK,
                "question with spaces", source, notices::add));
        assertEquals(3, actorReads.get());
        assertTrue(notices.stream().anyMatch(notice -> notice.message().contains("model_not_configured")));
    }
}
