package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.image.ImageReference;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ToolObservationImageIndexTest {
    @Test void onlyTypedOriginalToolImagesArePublishedWithOccurrencesAndRequestIdentity() {
        var image = new ImageReference("a".repeat(64), "image/png", 2, 2, 70);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        JsonObject fake = new JsonObject(); fake.addProperty("image", "not a typed image");
        var originals = Map.of(first, List.of(new ModelMessage(ModelRole.USER, List.of(
                new ModelContent.ToolResult("observed", fake, false, List.of(image, image))))),
                second, List.of(ModelMessage.userInput("ordinary", List.of(image))));
        var index = ToolObservationImageIndex.build(originals);
        assertEquals(List.of(image, image), index.get(first).get("observed"));
        assertFalse(index.containsKey(second));
        assertThrows(UnsupportedOperationException.class, () -> index.clear());
        assertThrows(UnsupportedOperationException.class, () -> index.get(first).clear());
        assertThrows(UnsupportedOperationException.class, () -> index.get(first).get("observed").clear());
    }
}
