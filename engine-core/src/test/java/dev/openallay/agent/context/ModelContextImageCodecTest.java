package dev.openallay.agent.context;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.image.ImageReference;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ModelContextImageCodecTest {
    private static final ImageReference IMAGE = new ImageReference("a".repeat(64), "image/png", 320, 240, 1024);

    @Test
    void associatedInputRoundTripsFullSourceIdentityAndRequiresCurrentNullableField() {
        var anchor = dev.openallay.world.InputObservationFixtures.anchor(IMAGE);
        var input = ModelMessage.userText("Which item is this?").withInputObservation(anchor);
        var codec = new ModelContextCodec();
        String encoded = codec.encode(List.of(input));
        assertEquals(List.of(input), codec.decode(encoded));
        var restored = codec.decode(encoded).getFirst().inputObservation().orElseThrow();
        assertEquals(anchor.associationId(), restored.associationId());
        assertEquals(anchor.capturedAt(), restored.capturedAt());
        assertEquals(anchor.focus().actorId(), restored.focus().actorId());
        assertEquals(anchor.image().orElseThrow().capturedAt(), restored.image().orElseThrow().capturedAt());
        var sourceJson = com.google.gson.JsonParser.parseString(encoded).getAsJsonObject()
                .getAsJsonArray("messages").get(0).getAsJsonObject().getAsJsonObject("inputObservation");
        assertEquals(com.google.gson.JsonParser.parseString("{\"seconds\":" + anchor.capturedAt().getEpochSecond()
                + ",\"nanos\":" + anchor.capturedAt().getNano() + "}"), sourceJson.get("capturedAt"));
        assertEquals(com.google.gson.JsonParser.parseString("{\"seconds\":"
                + anchor.image().orElseThrow().capturedAt().getEpochSecond() + ",\"nanos\":"
                + anchor.image().orElseThrow().capturedAt().getNano() + "}"),
                sourceJson.getAsJsonObject("image").get("capturedAt"));
        assertEquals(new java.math.BigDecimal("9007199254740993.125"), restored.focus().mainHand().components()
                .getAsJsonObject("minecraft:custom_data").get("precise").getAsBigDecimal());
        assertEquals(java.util.Set.of("role", "content", "inputObservation"), com.google.gson.JsonParser.parseString(encoded)
                .getAsJsonObject().getAsJsonArray("messages").get(0).getAsJsonObject().keySet());
        assertEquals(List.of(ModelMessage.userInput("", List.of(), java.util.Optional.of(anchor))),
                codec.decode(codec.encode(List.of(ModelMessage.userInput("", List.of(), java.util.Optional.of(anchor))))));
        var focusOnly = ModelMessage.userText("this block").withInputObservation(
                dev.openallay.world.InputObservationFixtures.anchor(null));
        assertEquals(List.of(focusOnly), codec.decode(codec.encode(List.of(focusOnly))));
        String plain = codec.encode(List.of(ModelMessage.userText("plain")));
        assertTrue(plain.contains("\"inputObservation\":null"));
        var missing = com.google.gson.JsonParser.parseString(plain).getAsJsonObject();
        missing.getAsJsonArray("messages").get(0).getAsJsonObject().remove("inputObservation");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(missing.toString()));
        for (String invalid : List.of(
                encoded.replace("\"menuSlot\":4", "\"menuSlot\":4.5"),
                encoded.replace("\"containerId\":3,", ""),
                encoded.replace("\"initialized\":true", "\"initialized\":\"true\""),
                encoded.replace("\"pauseScreen\":false", "\"pauseScreen\":false,\"extra\":1"),
                encoded.replace("\"nanos\":125000000", "\"nanos\":125000000.0"),
                encoded.replace("\"nanos\":125000000", "\"nanos\":1000000000"),
                encoded.replace("\"nanos\":125000000", "\"nanos\":125000000,\"extraTime\":1"))) {
            assertThrows(IllegalArgumentException.class, () -> codec.decode(invalid));
        }
        String firstHash = ContextSourceHash.compute(new com.google.gson.Gson(), List.of(input));
        var different = new dev.openallay.world.ClientObservationAnchor(java.util.UUID.randomUUID(), anchor.capturedAt(),
                anchor.focus(), anchor.image());
        assertNotEquals(firstHash, ContextSourceHash.compute(new com.google.gson.Gson(),
                List.of(ModelMessage.userText("Which item is this?").withInputObservation(different))));
    }

    @Test void imageOnlyUserContextRoundTripsMetadataWithoutBinary() {
        var messages = List.of(ModelMessage.userInput("", List.of(IMAGE)),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("A map"))));
        var codec = new ModelContextCodec();
        String encoded = codec.encode(messages);
        assertTrue(encoded.contains("\"type\":\"image\""));
        assertFalse(encoded.contains("base64"));
        assertFalse(encoded.contains("data:"));
        assertEquals(messages, codec.decode(encoded));
    }

    @Test void exactImageShapeRejectsExtraPathsAndFractionalDimensions() {
        String valid = new ModelContextCodec().encode(List.of(ModelMessage.userInput("look", List.of(IMAGE))));
        assertThrows(IllegalArgumentException.class, () -> new ModelContextCodec().decode(
                valid.replace("\"width\":320", "\"width\":320,\"path\":\"/tmp/image.png\"")));
        assertThrows(IllegalArgumentException.class, () -> new ModelContextCodec().decode(
                valid.replace("\"width\":320", "\"width\":320.5")));
        assertThrows(IllegalArgumentException.class, () -> new ModelContextCodec().decode(
                valid.replace("\"byteSize\":1024", "\"byteSize\":0")));
    }

    @Test void nestedToolImagesRoundTripExactCurrentShapeWithoutCreatingPlayerTurn() {
        var call = new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                "capture", "capture", new com.google.gson.JsonObject())));
        var result = new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                "capture", new com.google.gson.JsonPrimitive("captured"), false, List.of(IMAGE, IMAGE))));
        var messages = List.of(call, result);
        var codec = new ModelContextCodec();
        String encoded = codec.encode(messages);
        assertTrue(encoded.contains("\"images\":["));
        assertFalse(encoded.contains("base64"));
        assertEquals(messages, codec.decode(encoded));
        assertEquals(1, ContextStructure.units(messages).size());
        assertEquals(2, dev.openallay.model.image.ModelImages.occurrences(messages).size());
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                encoded.replace("\"width\":320", "\"width\":320,\"path\":\"/tmp/faux.png\"")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                encoded.replace("\"images\":[", "\"oldImages\":[")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                encoded.replace("\"error\":false", "\"error\":true")));
    }


    @Test void carriedImageOriginRoundTripsAndNullIsRequiredForPlayerImageCurrentShape() {
        var summary = new ModelMessage(ModelRole.USER, List.of(new ModelContent.Text("derived memory"),
                new ModelContent.Image(IMAGE, "source-call")));
        var codec = new ModelContextCodec();
        String encoded = codec.encode(List.of(summary));
        assertEquals(List.of(summary), codec.decode(encoded));
        assertTrue(encoded.contains("\"originToolUseId\":\"source-call\""));
        var player = codec.encode(List.of(ModelMessage.userInput("look", List.of(IMAGE))));
        assertTrue(player.contains("\"originToolUseId\":null"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                player.replace(",\"originToolUseId\":null", "")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                encoded.replace("\"originToolUseId\":\"source-call\"", "\"originToolUseId\":42")));
    }


    @Test void safeContextExcludesOnlyPrivateReasoningAndPreservesImages() {
        var input = ModelMessage.userInput("inspect", List.of(IMAGE));
        var assistant = new ModelMessage(ModelRole.ASSISTANT, List.of(
                new ModelContent.Reasoning("private", "signature"), new ModelContent.Text("visible")));
        var safe = ModelContextCodec.safe(List.of(input, assistant));
        assertEquals(input, safe.getFirst());
        assertEquals(List.of(new ModelContent.Text("visible")), safe.getLast().content());
    }
}
