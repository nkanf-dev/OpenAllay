package dev.openallay.bridge.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.openallay.agent.AgentEvent;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ServerAgentSteerProtocolTest {
    private final BridgeJsonCodec bridge = new BridgeJsonCodec();
    private final ServerAgentEventCodec events = new ServerAgentEventCodec(dev.openallay.json.EngineJson.create());

    @Test
    void exactPutAndNullableRemoveShapesKeepBothCorrelations() {
        UUID request = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        for (ServerAgentSteerPayload value : List.of(
                new ServerAgentSteerPayload(request, message, ServerAgentSteerPayload.Operation.PUT,
                        ServerAgentHistoryMessage.from(ModelMessage.userText("只读取方块状态 ⚙"))),
                new ServerAgentSteerPayload(request, message, ServerAgentSteerPayload.Operation.REMOVE, null))) {
            String json = bridge.encode(value);
            JsonObject body = dev.openallay.json.JsonTrees.parse(json).getAsJsonObject();
            assertEquals(Set.of("requestId", "messageId", "operation", "message", "imageAttachments"), dev.openallay.json.JsonTrees.keys(body));
            assertEquals(value, bridge.decode(json, ServerAgentSteerPayload.class));
            JsonObject missing = dev.openallay.json.JsonTrees.copy(body);
            missing.remove("message");
            assertThrows(IllegalArgumentException.class,
                    () -> bridge.decode(missing.toString(), ServerAgentSteerPayload.class));
            JsonObject extra = dev.openallay.json.JsonTrees.copy(body);
            extra.addProperty("version", 1);
            assertThrows(IllegalArgumentException.class,
                    () -> bridge.decode(extra.toString(), ServerAgentSteerPayload.class));
            if (value.operation() == ServerAgentSteerPayload.Operation.REMOVE) {
                assertTrue(body.get("message").isJsonNull());
            }
        }
    }

    @Test
    void rejectsNonUserToolExchangeAndWrongOperationShapes() {
        UUID request = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentSteerPayload(
                request, message, ServerAgentSteerPayload.Operation.PUT,
                ServerAgentHistoryMessage.from(new ModelMessage(ModelRole.ASSISTANT,
                        List.of(new ModelContent.Text("not a player instruction"))))));
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentSteerPayload(
                request, message, ServerAgentSteerPayload.Operation.PUT,
                ServerAgentHistoryMessage.from(new ModelMessage(ModelRole.USER,
                        List.of(new ModelContent.ToolResult("tool", new JsonObject(), false))))));
        String put = bridge.encode(new ServerAgentSteerPayload(request, message,
                ServerAgentSteerPayload.Operation.PUT,
                ServerAgentHistoryMessage.from(ModelMessage.userText("instruction"))));
        for (String malformed : List.of(
                put.replace("\"PUT\"", "\"REMOVE\""),
                put.replace("\"PUT\"", "\"FUTURE\""),
                put.replace("\"role\":\"USER\"", "\"role\":\"USER\",\"extra\":true"),
                put.replace("\"text\":\"instruction\"", "\"text\":\"instruction\",\"extra\":true"))) {
            assertThrows(IllegalArgumentException.class,
                    () -> bridge.decode(malformed, ServerAgentSteerPayload.class));
        }
        JsonObject missingPut = dev.openallay.json.JsonTrees.parse(put).getAsJsonObject();
        missingPut.add("message", com.google.gson.JsonNull.INSTANCE);
        assertThrows(IllegalArgumentException.class,
                () -> bridge.decode(missingPut.toString(), ServerAgentSteerPayload.class));
    }

    @Test
    void appliedRejectedAndReleasedRoundTripAsNonTerminalCorrelatedEvents() {
        UUID request = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        for (AgentEvent event : List.of(
                new AgentEvent.SteerApplied(message, ModelMessage.userText("edited instruction")),
                new AgentEvent.SteerRejected(message),
                new AgentEvent.RequestReleased())) {
            ServerAgentEventPayload payload = events.encode(request, event);
            assertFalse(payload.terminal());
            assertEquals(event, events.decode(payload, request));
            assertThrows(IllegalArgumentException.class,
                    () -> events.decode(payload, UUID.randomUUID()));
            assertThrows(IllegalArgumentException.class, () -> events.decode(
                    new ServerAgentEventPayload(request, payload.eventType(), payload.eventJson(), true), request));
            JsonObject extra = dev.openallay.json.JsonTrees.parse(payload.eventJson()).getAsJsonObject();
            extra.addProperty("extra", true);
            assertThrows(IllegalArgumentException.class, () -> events.decode(
                    new ServerAgentEventPayload(request, payload.eventType(), extra.toString(), false), request));
        }
        ServerAgentEventPayload applied = events.encode(request,
                new AgentEvent.SteerApplied(message, ModelMessage.userText("instruction")));
        assertEquals(Set.of("messageId", "message"),
                dev.openallay.json.JsonTrees.keys(dev.openallay.json.JsonTrees.parse(applied.eventJson()).getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> events.decode(
                new ServerAgentEventPayload(request, "steer_applied",
                        applied.eventJson().replace("\"USER\"", "\"ASSISTANT\""), false), request));
    }

    @Test
    void uploadedImageMetadataMustExactlyMatchTheUserMessageReferences() {
        UUID request = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        byte[] bytes = {1, 2, 3};
        var reference = new dev.openallay.model.image.ImageReference(ResultChunker.sha256(bytes),
                "image/png", 1, 1, bytes.length);
        var message = ServerAgentHistoryMessage.from(new ModelMessage(ModelRole.USER,
                List.of(new ModelContent.Image(reference))));
        var attachment = ServerAgentImageAttachment.from(reference, bytes);
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentSteerPayload(
                request, messageId, ServerAgentSteerPayload.Operation.PUT, message));
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentSteerPayload(
                request, messageId, ServerAgentSteerPayload.Operation.PUT, message,
                List.of(attachment, attachment)));
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentSteerPayload(
                request, messageId, ServerAgentSteerPayload.Operation.REMOVE, null,
                List.of(attachment)));
        var value = new ServerAgentSteerPayload(request, messageId,
                ServerAgentSteerPayload.Operation.PUT, message, List.of(attachment));
        assertEquals(value, bridge.decode(bridge.encode(value), ServerAgentSteerPayload.class));
        String encoded = bridge.encode(value);
        for (String malformed : List.of(
                encoded.replace("\"base64Data\":", "\"extra\":true,\"base64Data\":"),
                encoded.replace("\"width\":1", "\"width\":1,\"extra\":true"),
                encoded.replace("\"byteSize\":3", "\"byteSize\":3.5"))) {
            assertThrows(IllegalArgumentException.class,
                    () -> bridge.decode(malformed, ServerAgentSteerPayload.class));
        }
    }

    @Test
    void cancelPartialAskAcknowledgesOnlyAnExistingActorScopedAssembly() {
        UUID actor = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        ServerAgentRequestChunker.Reassembler incoming = new ServerAgentRequestChunker.Reassembler();
        var chunks = new ServerAgentRequestChunker().split(request, "pending request", 2);
        assertTrue(incoming.accept(actor, chunks.getFirst()).isEmpty());
        assertFalse(incoming.cancel(UUID.randomUUID(), request));
        assertEquals(1, incoming.activeAssemblies());
        assertTrue(incoming.cancel(actor, request));
        assertEquals(0, incoming.activeAssemblies());
        assertFalse(incoming.cancel(actor, request));
    }
}
