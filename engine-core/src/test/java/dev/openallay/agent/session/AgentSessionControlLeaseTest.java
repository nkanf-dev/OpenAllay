package dev.openallay.agent.session;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.model.ModelMessage;
import dev.openallay.tool.ToolResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AgentSessionControlLeaseTest {
    private final AgentSessionKey key = new AgentSessionKey(UUID.randomUUID(), "main");

    @Test void cancelledWorkerCannotOverwritePreparingOrPublishedControlProjection() {
        AgentSessionStore store = new AgentSessionStore();
        AgentSessionKey key = new AgentSessionKey(UUID.randomUUID(), "main");
        AgentSessionStore.Lease request = ((ToolResult.Success<AgentSessionStore.Lease>)
                store.reserve(key, UUID.randomUUID())).value();
        List<ModelMessage> initial = List.of(ModelMessage.userText("original goal"));
        store.recordContext(request, initial, initial);
        assertTrue(store.cancel(key, request.requestId()));
        AgentSessionStore.ControlLease control = ((ToolResult.Success<AgentSessionStore.ControlLease>)
                store.reserveControl(key, UUID.randomUUID(), List.of())).value();
        List<ModelMessage> late = List.of(ModelMessage.userText("stale cancelled worker projection"));
        assertFalse(store.finalizeCancelled(request, late));
        List<ModelMessage> published = List.of(ModelMessage.userText("new manual projection"));
        assertTrue(store.publishControl(control, published));
        assertFalse(store.finalizeCancelled(request, late));
        assertEquals(published, store.history(key));
    }

    @Test void preparingAndAbortingLeavesOriginalContextAndSkillIndexUnchanged() {
        AgentSessionStore store = new AgentSessionStore();
        List<ModelMessage> original = List.of(ModelMessage.userText("original"));
        store.hydrate(key, original);
        var control = success(store.reserveControl(key, UUID.randomUUID(), null));
        assertEquals(original, control.history());
        assertEquals("agent_busy", assertInstanceOf(ToolResult.Failure.class,
                store.reserve(key, UUID.randomUUID())).code());
        assertEquals("compact_busy", assertInstanceOf(ToolResult.Failure.class,
                store.reserveControl(key, UUID.randomUUID(), null)).code());
        store.releaseControl(control);
        var request = success(store.reserve(key, UUID.randomUUID()));
        assertEquals(original, request.history());
        assertTrue(request.retainedSkills().ranges().isEmpty());
    }

    @Test void successfulPublicationChangesOnlyActualProjectionAndStalePublicationFails() {
        AgentSessionStore store = new AgentSessionStore();
        List<ModelMessage> original = List.of(ModelMessage.userText("original"));
        List<ModelMessage> derived = List.of(ModelMessage.userText("summary"));
        store.hydrate(key, original);
        var control = success(store.reserveControl(key, UUID.randomUUID(), null));
        assertTrue(store.ownsControl(control));
        assertTrue(store.publishControl(control, derived));
        assertFalse(store.publishControl(control, original));
        assertEquals(derived, success(store.reserve(key, UUID.randomUUID())).history());
    }

    @Test void durableSeedIsDetachedUntilPublicationAndClearRevokesControl() {
        AgentSessionStore store = new AgentSessionStore();
        List<ModelMessage> original = List.of(ModelMessage.userText("durable source"));
        var control = success(store.reserveControl(key, UUID.randomUUID(), original));
        assertEquals(original, control.history());
        assertFalse(store.hasContext(key));
        assertThrows(IllegalStateException.class, () -> store.hydrate(key, original));
        store.clear(key);
        assertTrue(control.cancellation().isCancelled());
        assertFalse(store.publishControl(control, original));
        assertTrue(success(store.reserve(key, UUID.randomUUID())).history().isEmpty());
    }

    private static <T> T success(ToolResult<T> result) {
        assertInstanceOf(ToolResult.Success.class, result);
        return ((ToolResult.Success<T>) result).value();
    }
}
