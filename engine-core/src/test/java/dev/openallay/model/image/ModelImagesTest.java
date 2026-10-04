package dev.openallay.model.image;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.agent.tool.AgentToolResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ModelImagesTest {
    private static final ImageReference FIRST = new ImageReference("a".repeat(64), "image/png", 20, 10, 42);
    private static final ImageReference SECOND = new ImageReference("b".repeat(64), "image/jpeg", 30, 20, 43);

    @Test void traversalPreservesOccurrencesWhileOwnershipUsesUniqueTypedRefs() {
        var refs = new ArrayList<>(List.of(SECOND, FIRST, SECOND));
        var result = new ModelContent.ToolResult("call", new JsonPrimitive("view"), false, refs);
        refs.clear();
        var messages = List.of(ModelMessage.userInput("look", List.of(FIRST)),
                new ModelMessage(ModelRole.USER, List.of(result)));
        assertEquals(List.of(FIRST, SECOND, FIRST, SECOND), ModelImages.occurrences(messages));
        assertEquals(List.of(FIRST, SECOND), ModelImages.uniqueReferences(messages));
        assertTrue(ModelImages.hasImages(messages));
        assertThrows(UnsupportedOperationException.class, () -> result.images().clear());
        var carried = ModelImages.observationContent(messages);
        assertEquals(java.util.Arrays.asList(null, "call", "call", "call"), carried.stream()
                .map(ModelContent.Image.class::cast).map(ModelContent.Image::originToolUseId)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
    }

    @Test void arbitraryJsonMetadataNeverBecomesVisualInputAndFailuresCannotPublishPixels() {
        var json = new JsonObject();
        json.addProperty("sha256", FIRST.sha256());
        json.addProperty("mimeType", FIRST.mimeType());
        var plain = new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult("plain", json, false)));
        assertFalse(ModelImages.hasImages(List.of(plain)));
        assertThrows(IllegalArgumentException.class, () -> new ModelContent.ToolResult("failed", json, true, List.of(FIRST)));
        assertThrows(IllegalArgumentException.class, () -> new AgentToolResult("tool", json, true, null, List.of(FIRST)));
        var normalized = new JsonObject();
        normalized.addProperty("status", "success");
        normalized.add("value", json);
        var result = new AgentToolResult("tool", normalized, false, null, List.of(FIRST));
        assertEquals(List.of(FIRST), result.images());
        result.modelValue(768);
        assertEquals(List.of(FIRST), result.images());
    }

    @Test void conflictingHashMetadataIsRejectedBeforeOwnershipOrTransport() {
        var changed = new ImageReference(FIRST.sha256(), FIRST.mimeType(), 21, 10, FIRST.byteSize());
        assertThrows(IllegalArgumentException.class, () -> ModelImages.unique(List.of(FIRST, changed)));
        assertThrows(IllegalArgumentException.class, () -> new ModelContent.ToolResult(
                "call", new JsonPrimitive("view"), false, List.of(FIRST, changed)));
    }
}
