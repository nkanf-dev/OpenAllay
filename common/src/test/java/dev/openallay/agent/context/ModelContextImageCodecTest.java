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
