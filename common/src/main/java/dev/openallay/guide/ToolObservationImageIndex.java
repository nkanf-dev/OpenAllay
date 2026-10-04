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
                    if (content instanceof ModelContent.ToolResult result && !result.images().isEmpty()) {
                        ArrayList<ImageReference> occurrences = new ArrayList<>(
                                images.getOrDefault(result.toolUseId(), List.of()));
                        occurrences.addAll(result.images());
                        images.put(result.toolUseId(), List.copyOf(occurrences));
                    }
                }
            }
            if (!images.isEmpty()) requests.put(requestId, Map.copyOf(images));
        });
        return Map.copyOf(requests);
    }
}
