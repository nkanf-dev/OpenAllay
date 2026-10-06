package dev.openallay.bridge.protocol;

import com.google.gson.JsonObject;
import dev.openallay.model.image.ImageReference;
import dev.openallay.model.image.ModelImages;
import java.util.List;
import java.util.Objects;

/** Bytes exist only in this current-shape client Tool transport envelope. */
public record ToolExecutionMessage(
        JsonObject result, List<ServerAgentImageAttachment> imageAttachments) {
    public ToolExecutionMessage {
        result = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(result, "result"));
        imageAttachments = List.copyOf(imageAttachments);
        List<ImageReference> supplied = imageAttachments.stream()
                .map(ServerAgentImageAttachment::reference).toList();
        if (ModelImages.unique(supplied).size() != supplied.size()) {
            throw new IllegalArgumentException("Duplicate Tool image attachment");
        }
        if (result.has("status") && "failure".equals(result.get("status").getAsString())
                && !imageAttachments.isEmpty()) {
            throw new IllegalArgumentException("Failed Tool results cannot upload images");
        }
    }

    @Override public JsonObject result() { return dev.openallay.json.JsonTrees.copy(result); }

    /** Typed trusted output references, never a search through arbitrary result JSON. */
    public void requireImages(List<ImageReference> references) {
        List<ImageReference> required = ModelImages.unique(references);
        List<ImageReference> supplied = imageAttachments.stream()
                .map(ServerAgentImageAttachment::reference).toList();
        if (!new java.util.HashSet<>(required).equals(new java.util.HashSet<>(supplied))) {
            throw new IllegalArgumentException("Tool image attachments must exactly match typed references");
        }
    }
}
