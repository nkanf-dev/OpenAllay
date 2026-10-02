package dev.openallay.bridge.protocol;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import dev.openallay.agent.AgentEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class RequestReleasedCodecTest {
    @Test void releaseIsExactEmptyNonterminalRequestScopedEvent() {
        var codec = new ServerAgentEventCodec(new Gson());
        UUID id = UUID.randomUUID();
        var payload = codec.encode(id, new AgentEvent.RequestReleased());
        assertEquals("request_released", payload.eventType());
        assertEquals("{}", payload.eventJson());
        assertFalse(payload.terminal());
        assertInstanceOf(AgentEvent.RequestReleased.class, codec.decode(payload, id));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(payload, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new ServerAgentEventPayload(
                id, payload.eventType(), "{\"extra\":1}", false), id));
    }
}
