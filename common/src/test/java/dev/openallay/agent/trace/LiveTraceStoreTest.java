package dev.openallay.agent.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.openallay.agent.AgentState;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LiveTraceStoreTest {
    @TempDir java.nio.file.Path temporary;

    @Test
    void preservesFullTraceWhileRedactingConfiguredSecrets() throws Exception {
        String secret = "one-time-test-key";
        JsonObject payload = new JsonObject();
        payload.addProperty("question", "完整问题，不截断");
        payload.addProperty("debug", "authorization=" + secret);
        LiveAgentTrace trace = trace(payload);
        LiveTraceStore store = new LiveTraceStore(temporary, Set.of(secret));

        store.record(trace);
        String encoded = store.encoded(trace.requestId());
        assertTrue(encoded.contains("完整问题，不截断"));
        assertFalse(encoded.contains(secret));
        assertTrue(Files.exists(temporary.resolve(trace.requestId() + ".json")));
    }

    @Test
    void persistenceFollowsDebugModeAndRedactsConfiguredSecrets() throws Exception {
        String secret = "configured-profile-secret";
        java.util.concurrent.atomic.AtomicBoolean debug = new java.util.concurrent.atomic.AtomicBoolean();
        LiveTraceStore store = new LiveTraceStore(temporary, Set.of(secret), debug::get);
        LiveAgentTrace offTrace = trace(new JsonObject());
        store.record(offTrace);
        assertFalse(Files.exists(temporary.resolve(offTrace.requestId() + ".json")));

        debug.set(true);
        JsonObject payload = new JsonObject();
        payload.addProperty("provider", "cookie=" + secret);
        payload.addProperty("authorization", "Bearer header-only-secret");
        payload.addProperty("cookieHeader", "session=header-only-cookie");
        payload.addProperty("source", "return 'full javascript source';");
        LiveAgentTrace onTrace = trace(payload);
        store.record(onTrace);
        var path = temporary.resolve(onTrace.requestId() + ".json");
        assertTrue(Files.exists(path));
        String persisted = Files.readString(path);
        assertTrue(persisted.contains("full javascript source"));
        assertFalse(persisted.contains(secret));
        assertFalse(persisted.contains("header-only-secret"));
        assertFalse(persisted.contains("session=header-only-cookie"));

        debug.set(false);
        LiveAgentTrace toggledOff = trace(new JsonObject());
        store.record(toggledOff);
        assertFalse(Files.exists(temporary.resolve(toggledOff.requestId() + ".json")));
    }

    @Test
    void disabledPersistenceWritesNothingAndDoesNotApplyRetention() throws Exception {
        LiveTraceStore store = new LiveTraceStore(null, Set.of());
        LiveAgentTrace first = trace(new JsonObject());
        LiveAgentTrace second = trace(new JsonObject());
        store.record(first);
        store.record(second);

        assertEquals(2, store.ids().size());
        assertTrue(Files.list(temporary).findAny().isEmpty());
    }

    private static LiveAgentTrace trace(JsonObject payload) {
        Instant now = Instant.now();
        return new LiveAgentTrace(
                UUID.randomUUID(), UUID.randomUUID(), "main", now, now,
                AgentState.COMPLETED,
                List.of(new LiveTraceEvent("request", 1, payload)),
                "answer", null);
    }
}
