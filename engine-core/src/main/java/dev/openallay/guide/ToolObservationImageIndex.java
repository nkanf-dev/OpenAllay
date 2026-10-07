package dev.openallay.guide;

import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.image.ImageReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable published view of actual original-context Tool image occurrences. */
final class ToolObservationImageIndex {
    private ToolObservationImageIndex() {}

    static Map<UUID, Map<String, List<ImageReference>>> build(
            Map<UUID, List<ModelMessage>> originals) {
        Map<UUID, Map<String, List<ImageReference>>> requests = new LinkedHashMap<>();
        originals.forEach((requestId, messages) -> {
            Map<String, List<ImageReference>> images = new LinkedHashMap<>();
            for (ModelMessage message : messages) {
                for (ModelContent content : message.content()) {
                    if (content instanceof ModelContent.ToolResult && !((ModelContent.ToolResult) content).images().isEmpty()) {
                        ModelContent.ToolResult result = (ModelContent.ToolResult) content;
                        ArrayList<ImageReference> occurrences = new ArrayList<>(
                                images.getOrDefault(result.toolUseId(), dev.openallay.util.Java8Collections.listOf()));
                        occurrences.addAll(result.images());
                        images.put(result.toolUseId(), dev.openallay.util.Java8Collections.listCopyOf(occurrences));
                    }
                }
            }
            if (!images.isEmpty()) requests.put(requestId, dev.openallay.util.Java8Collections.mapCopyOf(images));
        });
        return dev.openallay.util.Java8Collections.mapCopyOf(requests);
    }
}
