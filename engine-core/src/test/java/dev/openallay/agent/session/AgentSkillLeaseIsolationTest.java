package dev.openallay.agent.session;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.*;
import dev.openallay.skill.*;
import dev.openallay.tool.ToolResult;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

final class AgentSkillLeaseIsolationTest {
    @Test
    void blockedCancelledPrepareCannotOverwriteSuccessorSkillFacts() throws Exception {
        AgentSessionStore sessions = new AgentSessionStore();
        AgentSessionKey key = new AgentSessionKey(UUID.randomUUID(), "main");
        AgentSessionStore.Lease old = lease(sessions.reserve(key, UUID.randomUUID()));
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        assertTrue(repository.reload(List.of(new SkillSource("pack", "guide/SKILL.md",
                Map.of("guide/SKILL.md", "---\nname: guide\ndescription: Guide\n---\nOld body."))), Set.of()));
        LoadSkillTool oldTool = new LoadSkillTool(repository.snapshot(Set.of()));
        LoadSkillTool.Output body = ((ToolResult.Success<LoadSkillTool.Output>) oldTool.invoke(
                ToolInvocationContext.developmentConsole("old"), new LoadSkillTool.Input("guide"))).value();
        JsonObject arguments = new JsonObject(); arguments.addProperty("name", "guide");
        List<ModelMessage> oldProjection = List.of(new ModelMessage(ModelRole.ASSISTANT,
                List.of(new ModelContent.ToolUse("old-load", "openallay__load_skill", arguments))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult("old-load",
                        new JsonPrimitive(body.modelText()), false))));
        CompletableFuture<Void> started = new CompletableFuture<>();
        CompletableFuture<Void> release = new CompletableFuture<>();
        CompletableFuture<Void> latePrepare = CompletableFuture.runAsync(() -> {
            started.complete(null); release.join();
            oldTool.prepareContext("old", oldProjection, old.retainedSkills());
        });
        started.get(2, TimeUnit.SECONDS);
        assertTrue(sessions.cancel(key, old.requestId()));
        AgentSessionStore.Lease successor = lease(sessions.reserve(key, UUID.randomUUID()));
        assertNotSame(old.retainedSkills(), successor.retainedSkills());
        LoadSkillTool currentTool = new LoadSkillTool(repository.snapshot(Set.of()));
        currentTool.prepareContext("current", List.of(ModelMessage.userText("New projection.")),
                successor.retainedSkills());
        release.complete(null); latePrepare.get(2, TimeUnit.SECONDS);
        assertTrue(successor.retainedSkills().ranges().isEmpty());
        LoadSkillTool.Output actual = ((ToolResult.Success<LoadSkillTool.Output>) currentTool.invoke(
                ToolInvocationContext.developmentConsole("current"), new LoadSkillTool.Input("guide"))).value();
        assertEquals(LoadSkillTool.LoadState.COMPLETE, actual.state());
        assertEquals("Old body.", actual.content());
        assertFalse(sessions.finish(old, oldProjection), "A late old request must not publish history");
    }

    @Test
    void normalCompletionKeepsTheSameFactOwnerWhileHydrationReplacesIt() {
        AgentSessionStore sessions = new AgentSessionStore();
        AgentSessionKey key = new AgentSessionKey(UUID.randomUUID(), "main");
        AgentSessionStore.Lease first = lease(sessions.reserve(key, UUID.randomUUID()));
        assertTrue(sessions.finish(first, List.of(ModelMessage.userText("Context."))));
        AgentSessionStore.Lease second = lease(sessions.reserve(key, UUID.randomUUID()));
        assertSame(first.retainedSkills(), second.retainedSkills());
        assertTrue(sessions.finish(second, second.history()));
        sessions.hydrate(key, List.of(ModelMessage.userText("Restored context.")));
        assertNotSame(second.retainedSkills(), lease(sessions.reserve(key, UUID.randomUUID())).retainedSkills());
    }

    private static AgentSessionStore.Lease lease(ToolResult<AgentSessionStore.Lease> result) {
        return ((ToolResult.Success<AgentSessionStore.Lease>) result).value();
    }
}
