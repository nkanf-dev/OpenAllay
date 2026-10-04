package dev.openallay.bridge.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.image.ImageReference;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class BridgeImagePayloadTest {
    private static final byte[] BYTES = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a9ZkAAAAASUVORK5CYII=");
    private static final ImageReference REFERENCE = new ImageReference(
            ResultChunker.sha256(BYTES), "image/png", 1, 1, BYTES.length);

    @Test
    void associatedHistoryRequestSteerAndEventHaveExactImageClosureAndSourceRoundTrip() {
        var anchor = dev.openallay.world.InputObservationFixtures.anchor(REFERENCE);
        var input = ModelMessage.userInput("", List.of(), java.util.Optional.of(anchor));
        var detached = ServerAgentHistoryMessage.from(input);
        var attachment = ServerAgentImageAttachment.from(REFERENCE, BYTES);
        var request = new ServerAgentRequestPayload(UUID.randomUUID(), "main", input, true,
                List.of(detached), List.of(attachment));
        var codec = new BridgeJsonCodec();
        String wire = codec.encode(request);
        assertEquals(request, codec.decode(wire, ServerAgentRequestPayload.class));
        assertEquals(input, detached.toModelMessage());
        assertEquals("[Image]", request.question());
        assertEquals(anchor, codec.decode(wire, ServerAgentRequestPayload.class).userInput().inputObservation().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentRequestPayload(UUID.randomUUID(), "main",
                ModelMessage.userText("continue"), true, List.of(detached), List.of()));
        var steer = new ServerAgentSteerPayload(UUID.randomUUID(), UUID.randomUUID(),
                ServerAgentSteerPayload.Operation.PUT, detached, List.of(attachment));
        assertEquals(steer, codec.decode(codec.encode(steer), ServerAgentSteerPayload.class));
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentSteerPayload(UUID.randomUUID(), UUID.randomUUID(),
                ServerAgentSteerPayload.Operation.PUT, detached, List.of()));
        var applied = new dev.openallay.agent.AgentEvent.SteerApplied(UUID.randomUUID(), input);
        var eventCodec = new ServerAgentEventCodec(new com.google.gson.Gson());
        assertEquals(applied, eventCodec.decode(eventCodec.encode(request.requestId(), applied), request.requestId()));
        JsonObject malformed = JsonParser.parseString(wire).getAsJsonObject();
        malformed.getAsJsonObject("userInput").remove("inputObservation");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(malformed.toString(), ServerAgentRequestPayload.class));
        assertThrows(IllegalArgumentException.class, () -> new ModelMessage(ModelRole.USER,
                List.of(new ModelContent.Image(REFERENCE, "not-player")), java.util.Optional.of(anchor)));
    }

    @Test
    void imageOnlyUserInputAndHistoryRoundTripWithMetadataOnlyReferences() {
        var image = imageMessage();
        var history = List.of(ServerAgentHistoryMessage.from(image));
        var attachment = ServerAgentImageAttachment.from(REFERENCE, BYTES);
        var payload = new ServerAgentRequestPayload(UUID.randomUUID(), "main", image, true,
                history, List.of(attachment));
        var codec = new BridgeJsonCodec();

        String encoded = codec.encode(payload);
        assertEquals(payload, codec.decode(encoded, ServerAgentRequestPayload.class));
        historyOnlyImageRequiresTheSameExactAttachmentClosure();
        JsonObject object = JsonParser.parseString(encoded).getAsJsonObject();
        JsonObject input = object.getAsJsonObject("userInput");
        assertEquals(image, payload.userInput().toModelMessage());
        assertEquals("[Image]", payload.question());
        assertTrue(!input.toString().contains("base64Data"));
        assertEquals(java.util.Set.of("role", "content", "inputObservation"), input.keySet());
        JsonObject block = input.getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals(java.util.Set.of("kind", "image", "originToolUseId"), block.keySet());
        assertEquals(java.util.Set.of("sha256", "mimeType", "width", "height", "byteSize"),
                block.getAsJsonObject("image").keySet());
        assertEquals(java.util.Set.of("reference", "base64Data"), object.getAsJsonArray("imageAttachments")
                .get(0).getAsJsonObject().keySet());
        assertEquals(REFERENCE, attachment.reference());
        org.junit.jupiter.api.Assertions.assertArrayEquals(BYTES, attachment.bytes());
        assertEquals(1, payload.imageAttachments().size(), "duplicate use of one image needs one attachment");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                encoded.replace("\"mimeType\":\"image/png\"", "\"mimeType\":\"image/png\",\"path\":\"/tmp/image.png\""),
                ServerAgentRequestPayload.class));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                encoded.replace("\"width\":1", "\"width\":\"1\""), ServerAgentRequestPayload.class));
    }

    @Test
    void missingExtraDuplicateAndConflictingAttachmentsAreRejected() {
        attachmentHashAndCanonicalBase64AreValidatedWithoutChangingMetadata();
        var image = imageMessage();
        var attachment = ServerAgentImageAttachment.from(REFERENCE, BYTES);
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentRequestPayload(
                UUID.randomUUID(), "main", image, true, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentRequestPayload(
                UUID.randomUUID(), "main", ModelMessage.userText("text"), true,
                List.of(), List.of(attachment)));
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentRequestPayload(
                UUID.randomUUID(), "main", image, true, List.of(), List.of(attachment, attachment)));
        ImageReference conflicting = new ImageReference(REFERENCE.sha256(), "image/png", 2, 1, BYTES.length);
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentRequestPayload(
                UUID.randomUUID(), "main", image, true,
                List.of(ServerAgentHistoryMessage.from(new ModelMessage(ModelRole.USER,
                        List.of(new ModelContent.Image(conflicting))))), List.of(attachment)));
    }

    @Test
    void oldRequestShapeAndStringOrUrlImageInputsAreNotAccepted() {
        var codec = new BridgeJsonCodec();
        var payload = new ServerAgentRequestPayload(UUID.randomUUID(), "main", "question", true);
        JsonObject current = JsonParser.parseString(codec.encode(payload)).getAsJsonObject();
        current.remove("userInput");
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(current.toString(), ServerAgentRequestPayload.class));
        JsonObject imageRequest = JsonParser.parseString(codec.encode(new ServerAgentRequestPayload(
                UUID.randomUUID(), "main", imageMessage(), true, List.of(),
                List.of(ServerAgentImageAttachment.from(REFERENCE, BYTES))))).getAsJsonObject();
        imageRequest.getAsJsonObject("userInput").getAsJsonArray("content").get(0)
                .getAsJsonObject().addProperty("image", "https://example.test/image.png");
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(imageRequest.toString(), ServerAgentRequestPayload.class));
    }

    @Test
    void nestedToolImageHistoryRequiresExactBytesAndRoundTripsTheCurrentShape() {
        var canonical = List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        "view", "capture", new JsonObject()))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "view", new com.google.gson.JsonPrimitive("captured"), false, List.of(REFERENCE, REFERENCE)))));
        var history = canonical.stream().map(ServerAgentHistoryMessage::from).toList();
        var attachment = ServerAgentImageAttachment.from(REFERENCE, BYTES);
        var request = new ServerAgentRequestPayload(UUID.randomUUID(), "main", ModelMessage.userText("continue"),
                true, history, List.of(attachment));
        var codec = new BridgeJsonCodec();
        String encoded = codec.encode(request);
        assertEquals(request, codec.decode(encoded, ServerAgentRequestPayload.class));
        assertEquals(canonical, request.history().stream().map(ServerAgentHistoryMessage::toModelMessage).toList());
        var wire = JsonParser.parseString(encoded).getAsJsonObject();
        var result = wire.getAsJsonArray("history").get(1).getAsJsonObject()
                .getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals(java.util.Set.of("kind", "toolUseId", "json", "error", "images"), result.keySet());
        assertEquals(2, result.getAsJsonArray("images").size());
        result.remove("images");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(wire.toString(), ServerAgentRequestPayload.class));
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentRequestPayload(UUID.randomUUID(), "main",
                ModelMessage.userText("continue"), true, history, List.of()));
    }


    @Test
    void carriedImageOriginSurvivesHistoryWireButCannotEnterPlayerRequestOrSteer() {
        var observation = new ModelMessage(ModelRole.USER, List.of(new ModelContent.Text("derived memory"),
                new ModelContent.Image(REFERENCE, "original-view")));
        var history = List.of(ServerAgentHistoryMessage.from(observation));
        var attachment = ServerAgentImageAttachment.from(REFERENCE, BYTES);
        var request = new ServerAgentRequestPayload(UUID.randomUUID(), "main", ModelMessage.userText("continue"),
                true, history, List.of(attachment));
        var codec = new BridgeJsonCodec();
        String wire = codec.encode(request);
        assertEquals(request, codec.decode(wire, ServerAgentRequestPayload.class));
        assertEquals(observation, request.history().getFirst().toModelMessage());
        assertTrue(wire.contains("\"originToolUseId\":\"original-view\""));
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentRequestPayload(UUID.randomUUID(), "main",
                observation, true, List.of(), List.of(attachment)));
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentSteerPayload(UUID.randomUUID(), UUID.randomUUID(),
                ServerAgentSteerPayload.Operation.PUT, history.getFirst(), List.of(attachment)));
        JsonObject absent = JsonParser.parseString(wire).getAsJsonObject();
        absent.getAsJsonArray("history").get(0).getAsJsonObject().getAsJsonArray("content")
                .get(1).getAsJsonObject().remove("originToolUseId");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(absent.toString(), ServerAgentRequestPayload.class));
        var player = ModelMessage.userInput("look", List.of(REFERENCE));
        var applied = new dev.openallay.agent.AgentEvent.SteerApplied(UUID.randomUUID(), player);
        var eventCodec = new ServerAgentEventCodec(new com.google.gson.Gson());
        UUID requestId = UUID.randomUUID();
        assertEquals(applied, eventCodec.decode(eventCodec.encode(requestId, applied), requestId));
    }


    private void historyOnlyImageRequiresTheSameExactAttachmentClosure() {
        var history = List.of(ServerAgentHistoryMessage.from(imageMessage()));
        var attachment = ServerAgentImageAttachment.from(REFERENCE, BYTES);
        var request = new ServerAgentRequestPayload(UUID.randomUUID(), "main",
                ModelMessage.userText("follow up"), true, history, List.of(attachment));
        var codec = new BridgeJsonCodec();
        assertEquals(request, codec.decode(codec.encode(request), ServerAgentRequestPayload.class));
        assertEquals(List.of(REFERENCE), request.imageAttachments().stream()
                .map(ServerAgentImageAttachment::reference).toList());
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentRequestPayload(UUID.randomUUID(),
                "main", ModelMessage.userText("follow up"), true, history, List.of()));
        JsonObject wire = JsonParser.parseString(codec.encode(request)).getAsJsonObject();
        wire.addProperty("question", "changed display");
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(wire.toString(), ServerAgentRequestPayload.class));
    }

    private void attachmentHashAndCanonicalBase64AreValidatedWithoutChangingMetadata() {
        var wrongHash = new ImageReference("a".repeat(64), "image/png", 1, 1, BYTES.length);
        var attachment = ServerAgentImageAttachment.from(wrongHash, BYTES);
        assertThrows(IllegalArgumentException.class, attachment::bytes);
        String encoded = java.util.Base64.getEncoder().encodeToString(BYTES);
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentImageAttachment(REFERENCE,
                encoded.substring(0, encoded.length() - 1)));
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentImageAttachment(REFERENCE,
                "-" + encoded.substring(1)));
        var oneByte = new ImageReference(ResultChunker.sha256(new byte[] {1}), "image/png", 1, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentImageAttachment(oneByte, "AR=="));
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentImageAttachment(oneByte, "AQ=A"));
    }

    private static ModelMessage imageMessage() {
        return new ModelMessage(ModelRole.USER, List.of(new ModelContent.Image(REFERENCE)));
    }
}
