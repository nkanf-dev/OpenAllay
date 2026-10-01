package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.openallay.agent.AgentEvent;
import dev.openallay.bridge.protocol.CapabilityPayload;
import dev.openallay.bridge.protocol.ServerAgentEventPayload;
import dev.openallay.bridge.protocol.ServerAgentRequestPayload;
import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class PayloadGuideRemoteEndpointTest {
    @Test
    void sendsActualModelContextWithServerRequests() {
        FakePort port = new FakePort();
        PayloadGuideRemoteEndpoint endpoint = new PayloadGuideRemoteEndpoint(port, new Gson());
        UUID request = UUID.randomUUID();
        List<dev.openallay.model.ModelMessage> actual = List.of(
                dev.openallay.model.ModelMessage.userText("old question"),
                new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.ASSISTANT,
                        List.of(new dev.openallay.model.ModelContent.Text("old answer"))));

        assertTrue(endpoint.askWithContext(request, "main", "follow up", actual, ignored -> {}));

        assertEquals(request, port.request.requestId());
        assertEquals(
                List.of("USER:old question", "ASSISTANT:old answer"),
                port.request.history().stream()
                        .map(message -> message.role() + ":" + message.content().getFirst().text())
                        .toList());
    }

    @Test
    void sendsRealToolInputsAndPlaintextFailureInsteadOfDisplaySurrogates() {
        FakePort port = new FakePort();
        PayloadGuideRemoteEndpoint endpoint = new PayloadGuideRemoteEndpoint(port, new Gson());
        com.google.gson.JsonObject input = new com.google.gson.JsonObject();
        input.addProperty("source", "return mc.items.filter(x => x.id); ");
        List<dev.openallay.model.ModelMessage> actual = List.of(
                new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.ASSISTANT,
                        List.of(new dev.openallay.model.ModelContent.ToolUse("actual", "openallay__run_javascript", input))),
                new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.USER,
                        List.of(new dev.openallay.model.ModelContent.ToolResult("actual",
                                new com.google.gson.JsonPrimitive("code: javascript_error\nmessage: .filter is undefined"), true))));
        assertTrue(endpoint.askWithContext(UUID.randomUUID(), "main", "follow up", actual, ignored -> {}));
        assertEquals(actual, port.request.history().stream().map(
                dev.openallay.bridge.protocol.ServerAgentHistoryMessage::toModelMessage).toList());
        dev.openallay.agent.context.ContextStructure.units(actual);
    }

    @Test
    void malformedRemoteEventFailsOnlyItsRequestAndCancelsTransport() {
        FakePort port = new FakePort();
        PayloadGuideRemoteEndpoint endpoint = new PayloadGuideRemoteEndpoint(port, new Gson());
        UUID request = UUID.randomUUID();
        List<AgentEvent> events = new ArrayList<>();

        assertTrue(endpoint.ask(request, "main", "question", events::add));
        port.events.accept(new ServerAgentEventPayload(
                request, "future_event", "{}", false));

        AgentEvent.Failed failed = assertInstanceOf(AgentEvent.Failed.class, events.getFirst());
        assertEquals("server_protocol_error", failed.code());
        assertEquals(List.of(request), port.cancelled);
    }

    private static final class FakePort implements PayloadGuideRemoteEndpoint.Port {
        private Consumer<ServerAgentEventPayload> events;
        private ServerAgentRequestPayload request;
        private final List<UUID> cancelled = new ArrayList<>();
        @Override public CapabilityPayload capabilities() {
            return new CapabilityPayload(
                    List.of(), true,
                    256_000, 8_192, 2_000, "test/model");
        }
        @Override public boolean ask(
                ServerAgentRequestPayload request, Consumer<ServerAgentEventPayload> events) {
            this.request = request;
            this.events = events;
            return true;
        }
        @Override public boolean cancel(UUID requestId) {
            cancelled.add(requestId);
            return true;
        }
        @Override public void disconnect() {}
    }
}
