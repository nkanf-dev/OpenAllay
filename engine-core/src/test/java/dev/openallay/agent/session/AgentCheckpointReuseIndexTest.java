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

final class AgentCheckpointReuseIndexTest {
    private static final String SUMMARY = "{\"goals\":[],\"preferences\":[],\"completedTopics\":[],"
            + "\"currentTasks\":[],\"decisions\":[],\"unresolvedQuestions\":[],\"evidenceReferences\":[]}";

    @Test
    void failedDiagnosticsAreNotReusableAndSuccessfulSourceIdentityIsDeduplicated() {
        AgentSessionStore store = new AgentSessionStore();
        AgentSessionKey key = new AgentSessionKey(UUID.randomUUID(), "test");
        List<ModelMessage> source = List.of(ModelMessage.userText("original"));
        store.hydrate(key, source);
        AgentSessionStore.Lease lease = lease(store.reserve(key, UUID.randomUUID()));
        ContextCheckpoint failed = checkpoint(source, false);
        assertTrue(store.recordCheckpoint(lease, failed));
        assertTrue(store.checkpoints(key).isEmpty());
        assertEquals(ContextCheckpoint.Status.FAILED, failed.status(), "Original diagnostic unchanged");
        ContextCheckpoint first = checkpoint(source, true);
        ContextCheckpoint second = checkpoint(source, true);
        assertTrue(store.recordCheckpoint(lease, first));
        assertTrue(store.recordCheckpoint(lease, second));
        assertEquals(List.of(second), store.checkpoints(key));
        assertTrue(store.finish(lease, source));
        assertEquals(List.of(second), store.checkpoints(key), "Matching source remains reusable");
    }

    @Test
    void replacingActiveProjectionPrunesRuntimeOnlyAndOldLeaseCannotChangeSuccessor() {
        AgentSessionStore store = new AgentSessionStore();
        AgentSessionKey key = new AgentSessionKey(UUID.randomUUID(), "test");
        List<ModelMessage> source = List.of(ModelMessage.userText("original"));
        store.hydrate(key, source);
        AgentSessionStore.Lease old = lease(store.reserve(key, UUID.randomUUID()));
        ContextCheckpoint diagnostic = checkpoint(source, true);
        assertTrue(store.recordCheckpoint(old, diagnostic));
        assertTrue(store.finish(old, List.of(ModelMessage.userText("derived memory"))));
        assertTrue(store.checkpoints(key).isEmpty());
        assertEquals(ContextCheckpoint.Status.SUCCEEDED, diagnostic.status());
        assertEquals(ContextSourceHash.compute(dev.openallay.json.EngineJson.create(), source), diagnostic.sourceHash());
        AgentSessionStore.Lease successor = lease(store.reserve(key, UUID.randomUUID()));
        assertFalse(store.recordCheckpoint(old, checkpoint(source, true)));
        assertFalse(store.finish(old, source));
        assertTrue(store.status(key).active());
        assertTrue(store.finish(successor, successor.history()));
    }

    private static ContextCheckpoint checkpoint(List<ModelMessage> source, boolean success) {
        return new ContextCheckpoint(UUID.randomUUID(), 0, source.size(),
                ContextSourceHash.compute(dev.openallay.json.EngineJson.create(), source), "test", Instant.EPOCH,
                success ? ContextCheckpoint.Status.SUCCEEDED : ContextCheckpoint.Status.FAILED,
                success ? SUMMARY : null, success ? null : "summary_malformed",
                success ? null : "Actual failure", 100);
    }

    @SuppressWarnings("unchecked")
    private static AgentSessionStore.Lease lease(ToolResult<AgentSessionStore.Lease> reservation) {
        return ((ToolResult.Success<AgentSessionStore.Lease>) reservation).value();
    }
}
