package dev.openallay.model.image;

import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Typed visual input traversal shared by provider projections and artifact owners. */
public final class ModelImages {
    private ModelImages() {}

    /** Preserves every visual occurrence and its order, including repeated references. */
    public static List<ImageReference> occurrences(List<ModelMessage> messages) {
        ArrayList<ImageReference> images = new ArrayList<>();
        for (ModelMessage message : List.copyOf(messages)) {
            for (ModelContent content : message.content()) {
                if (content instanceof ModelContent.Image image) images.add(image.reference());
                else if (content instanceof ModelContent.ToolResult result) images.addAll(result.images());
            }
        }
        return List.copyOf(images);
    }

    /** Complete typed references for retention or transport, never inferred from JSON fields. */
    public static List<ImageReference> uniqueReferences(List<ModelMessage> messages) {
        return unique(occurrences(messages));
    }

    /** Rejects conflicting metadata before a hash is used as an artifact identity. */
    public static List<ImageReference> unique(List<ImageReference> references) {
        LinkedHashMap<String, ImageReference> images = new LinkedHashMap<>();
        for (ImageReference image : List.copyOf(references)) {
            Objects.requireNonNull(image, "image");
            ImageReference previous = images.putIfAbsent(image.sha256(), image);
            if (previous != null && !previous.equals(image)) {
                throw new IllegalArgumentException("Conflicting metadata for the same image hash");
            }
        }
        return List.copyOf(images.values());
    }

    /** Visual evidence carried into a derived summary retains its tool origin, not player intent. */
    public static List<ModelContent> observationContent(List<ModelMessage> messages) {
        ArrayList<ModelContent> content = new ArrayList<>();
        for (ModelMessage message : messages) {
            for (ModelContent block : message.content()) {
                if (block instanceof ModelContent.Image image) content.add(image);
                else if (block instanceof ModelContent.ToolResult result && !result.images().isEmpty()) {
                    result.images().forEach(image -> content.add(new ModelContent.Image(image, result.toolUseId())));
                }
            }
        }
        return List.copyOf(content);
    }

    /** Provider-only attribution generated from typed provenance, never recovered from text. */
    public static String observationLabel(String toolUseId) {
        if (toolUseId == null || toolUseId.isBlank()) throw new IllegalArgumentException("Tool observation requires an origin");
        return "OpenAllay tool observation for tool call " + toolUseId + "; not a new player message. "
                + "Capture metadata is in the original tool result.";
    }

    public static boolean hasImages(List<ModelMessage> messages) {
        for (ModelMessage message : messages) {
            for (ModelContent content : message.content()) {
                if (content instanceof ModelContent.Image
                        || content instanceof ModelContent.ToolResult result && !result.images().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
}
