package dev.openallay.agent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.model.ModelMessage;
import dev.openallay.tool.ToolResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AgentSessionStoreTest {
    @Test
    void rejectsConcurrentWorkAndPreservesCompletedHistory() {
        AgentSessionStore store = new AgentSessionStore();
        UUID actor = UUID.randomUUID();
        AgentSessionKey key = new AgentSessionKey(actor, "main");
        AgentSessionStore.Lease lease = success(store.reserve(key, UUID.randomUUID())).value();
        ToolResult.Failure<AgentSessionStore.Lease> busy = failure(
                store.reserve(key, UUID.randomUUID()));
        assertEquals("agent_busy", busy.code());

        List<ModelMessage> history = List.of(ModelMessage.userText("first"));
        assertTrue(store.finish(lease, history));
        AgentSessionStore.Lease next = success(store.reserve(key, UUID.randomUUID())).value();
        assertEquals(history, next.history());
    }

    @Test
    void cancelAndClearAreRequestScoped() {
        AgentSessionStore store = new AgentSessionStore();
        UUID actor = UUID.randomUUID();
        AgentSessionKey key = new AgentSessionKey(actor, "main");
        AgentSessionStore.Lease lease = success(store.reserve(key, UUID.randomUUID())).value();
        assertTrue(store.cancel(key));
        assertTrue(lease.cancellation().isCancelled());
        assertInstanceOf(ToolResult.Success.class, store.reserve(key, UUID.randomUUID()));
        assertFalse(store.cancel(new AgentSessionKey(UUID.randomUUID(), "main")));
        store.clear(key);
        assertFalse(store.status(key).active());
        assertEquals(0, store.status(key).historyMessages());
    }

    @Test
    void cancellationRetainsSafeProgressAndImmediatelyAdmitsFencedReplacement() {
        AgentSessionStore store = new AgentSessionStore();
        AgentSessionKey key = new AgentSessionKey(UUID.randomUUID(), "main");
        UUID repeatedIdentity = UUID.randomUUID();
        AgentSessionStore.Lease old = success(store.reserve(key, repeatedIdentity)).value();
        List<ModelMessage> safe = List.of(ModelMessage.userText("completed exchange stays available"));
        assertTrue(store.recordContext(old, safe, safe));
        assertTrue(store.cancel(key));
        AgentSessionStore.Lease replacement = success(store.reserve(key, repeatedIdentity)).value();
        assertEquals(safe.getFirst(), replacement.history().getFirst());
        assertTrue(replacement.history().getLast().content().getFirst().toString().contains("agent_cancelled"));
        assertFalse(store.recordContext(old, List.of(ModelMessage.userText("late old")), safe));
        assertFalse(store.finish(old, List.of(ModelMessage.userText("late old"))));
        assertTrue(store.status(key).active());
        assertEquals(repeatedIdentity, store.status(key).requestId());
        assertTrue(store.finish(replacement, replacement.history()));
    }

    @Test
    void delayedCancellationForOldRequestCannotRevokeSuccessorInSameSession() {
        AgentSessionStore store = new AgentSessionStore();
        AgentSessionKey key = new AgentSessionKey(UUID.randomUUID(), "main");
        UUID oldId = UUID.randomUUID();
        AgentSessionStore.Lease old = success(store.reserve(key, oldId)).value();
        assertTrue(store.cancel(key, oldId));
        UUID successorId = UUID.randomUUID();
        AgentSessionStore.Lease successor = success(store.reserve(key, successorId)).value();
        assertFalse(store.cancel(key, oldId));
        assertFalse(successor.cancellation().isCancelled());
        assertTrue(old.cancellation().isCancelled());
        assertEquals(successorId, store.status(key).requestId());
        assertTrue(store.finish(successor, successor.history()));
    }

    @Test
    void differentSessionsForOneActorCanBeActiveTogether() {
        AgentSessionStore store = new AgentSessionStore();
        UUID actor = UUID.randomUUID();
        AgentSessionKey first = new AgentSessionKey(actor, "main");
        AgentSessionKey second = new AgentSessionKey(actor, "automation");

        assertInstanceOf(ToolResult.Success.class, store.reserve(first, UUID.randomUUID()));
        assertInstanceOf(ToolResult.Success.class, store.reserve(second, UUID.randomUUID()));
        assertEquals(List.of(second, first), store.sessions(actor));

        store.clearActor(actor);
        assertTrue(store.sessions(actor).isEmpty());
    }

    @Test
    void restoredHistoryIsInstalledOnlyWhenItsLeaseWins() {
        AgentSessionStore store = new AgentSessionStore();
        AgentSessionKey key = new AgentSessionKey(UUID.randomUUID(), "main");
        List<ModelMessage> firstHistory = List.of(ModelMessage.userText("first"));
        AgentSessionStore.Lease first = success(
                store.reserveWithHistory(key, UUID.randomUUID(), firstHistory)).value();

        ToolResult.Failure<AgentSessionStore.Lease> busy = failure(
                store.reserveWithHistory(
                        key, UUID.randomUUID(), List.of(ModelMessage.userText("second"))));

        assertEquals("agent_busy", busy.code());
        assertEquals(firstHistory, first.history());
        assertTrue(store.finish(first, firstHistory));
        assertEquals(firstHistory, success(store.reserve(key, UUID.randomUUID())).value().history());
    }

    @Test
    void steerInboxIsRequestScopedEditableOrderedAndNeverReplaysConsumedIds() {
        AgentSessionStore store = new AgentSessionStore();
        AgentSessionKey key = new AgentSessionKey(UUID.randomUUID(), "main");
        UUID requestId = UUID.randomUUID();
        AgentSessionStore.Lease lease = success(store.reserve(key, requestId)).value();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(new ToolResult.Success<>(false),
                store.steer(key, UUID.randomUUID(), first, ModelMessage.userText("wrong request")));
        assertEquals(new ToolResult.Success<>(true),
                store.steer(key, requestId, first, ModelMessage.userText("first")));
        store.steer(key, requestId, second, ModelMessage.userText("second"));
        store.steer(key, requestId, first, ModelMessage.userText("edited first"));
        List<AgentSessionStore.Steer> delivered = store.drainSteers(lease);
        assertEquals(List.of(first, second), delivered.stream().map(AgentSessionStore.Steer::messageId).toList());
        assertEquals(ModelMessage.userText("edited first"), delivered.getFirst().message());
        assertTrue(store.drainSteers(lease).isEmpty());
        assertEquals(new ToolResult.Success<>(false),
                store.steer(key, requestId, first, ModelMessage.userText("duplicate")));
        assertFalse(store.cancelSteer(key, requestId, first));
    }

    @Test
    void finalTurnSealAndCancellationRevokePendingInstructionsWithoutDraining() {
        AgentSessionStore store = new AgentSessionStore();
        AgentSessionKey key = new AgentSessionKey(UUID.randomUUID(), "main");
        UUID requestId = UUID.randomUUID();
        AgentSessionStore.Lease lease = success(store.reserve(key, requestId)).value();
        UUID id = UUID.randomUUID();
        store.steer(key, requestId, id, ModelMessage.userText("last turn supplement"));
        store.sealSteers(lease);
        assertTrue(store.drainSteers(lease).isEmpty());
        assertEquals(new ToolResult.Success<>(false),
                store.steer(key, requestId, UUID.randomUUID(), ModelMessage.userText("late")));
        assertTrue(store.cancel(key, requestId));
        AgentSessionStore.Lease replacement = success(store.reserve(key, UUID.randomUUID())).value();
        assertTrue(store.drainSteers(replacement).isEmpty());
        assertFalse(store.cancelSteer(key, requestId, id));
    }

    @SuppressWarnings("unchecked")
    private static ToolResult.Success<AgentSessionStore.Lease> success(
            ToolResult<AgentSessionStore.Lease> result) {
        return (ToolResult.Success<AgentSessionStore.Lease>)
                assertInstanceOf(ToolResult.Success.class, result);
    }

    @SuppressWarnings("unchecked")
    private static ToolResult.Failure<AgentSessionStore.Lease> failure(
            ToolResult<AgentSessionStore.Lease> result) {
        return (ToolResult.Failure<AgentSessionStore.Lease>)
                assertInstanceOf(ToolResult.Failure.class, result);
    }
}
