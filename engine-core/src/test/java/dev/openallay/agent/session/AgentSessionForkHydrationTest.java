package dev.openallay.agent.session;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.agent.context.ContextSourceHash;
import dev.openallay.model.ModelMessage;
import dev.openallay.tool.ToolResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AgentSessionForkHydrationTest {
    @Test
    void forkHydrationCopiesOnlyImmutableContextWithoutSharingLeaseCancellationOrRetainedSkills() {
        AgentSessionStore store = new AgentSessionStore(); UUID actor = UUID.randomUUID();
        AgentSessionKey source = new AgentSessionKey(actor, "main"); AgentSessionKey branch = new AgentSessionKey(actor, "branch");
        List<ModelMessage> context = List.of(ModelMessage.userText("safe completed task"));
        ContextCheckpoint checkpoint = new ContextCheckpoint(UUID.randomUUID(), 0, 1,
                ContextSourceHash.compute(dev.openallay.json.EngineJson.create(), context), "test-model", Instant.EPOCH,
                ContextCheckpoint.Status.SUCCEEDED, "summary", null, null, 10);
        store.hydrate(source, context, List.of(checkpoint));
        AgentSessionStore.Lease activeSource = ((ToolResult.Success<AgentSessionStore.Lease>)
                store.reserve(source, UUID.randomUUID())).value();
        store.hydrate(branch, context, List.of(checkpoint));
        assertFalse(store.status(branch).active());
        AgentSessionStore.Lease activeBranch = ((ToolResult.Success<AgentSessionStore.Lease>)
                store.reserve(branch, UUID.randomUUID())).value();
        assertEquals(context, activeBranch.history());
        assertEquals(List.of(checkpoint), activeBranch.checkpoints());
        assertNotSame(activeSource.cancellation(), activeBranch.cancellation());
        assertNotSame(activeSource.retainedSkills(), activeBranch.retainedSkills());
        store.clear(source);
        assertTrue(activeSource.cancellation().isCancelled());
        assertFalse(activeBranch.cancellation().isCancelled());
        assertTrue(store.status(branch).active());
    }
}
