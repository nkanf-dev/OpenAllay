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

    @Test void safeContextExcludesOnlyPrivateReasoningAndPreservesImages() {
        var input = ModelMessage.userInput("inspect", List.of(IMAGE));
        var assistant = new ModelMessage(ModelRole.ASSISTANT, List.of(
                new ModelContent.Reasoning("private", "signature"), new ModelContent.Text("visible")));
        var safe = ModelContextCodec.safe(List.of(input, assistant));
        assertEquals(input, safe.getFirst());
        assertEquals(List.of(new ModelContent.Text("visible")), safe.getLast().content());
    }
}
