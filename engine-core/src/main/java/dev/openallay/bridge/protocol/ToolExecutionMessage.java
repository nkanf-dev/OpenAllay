package dev.openallay.bridge.protocol;

import com.google.gson.JsonObject;
import dev.openallay.model.image.ImageReference;
import dev.openallay.model.image.ModelImages;
import java.util.List;
import java.util.Objects;

/** Bytes exist only in this current-shape client Tool transport envelope. */
@dev.openallay.value.ValueType(ToolExecutionMessage.ValueSchemaProvider.class)
public final class ToolExecutionMessage {
    private final JsonObject result;
    private final List<ServerAgentImageAttachment> imageAttachments;
    public ToolExecutionMessage(JsonObject result, List<ServerAgentImageAttachment> imageAttachments) {

        result = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(result, "result"));
        imageAttachments = dev.openallay.util.Java8Collections.listCopyOf(imageAttachments);
        List<ImageReference> supplied = dev.openallay.util.Java8Collections.toList(imageAttachments.stream()
                .map(ServerAgentImageAttachment::reference));
        if (ModelImages.unique(supplied).size() != supplied.size()) {
            throw new IllegalArgumentException("Duplicate Tool image attachment");
        }
        if (result.has("status") && "failure".equals(result.get("status").getAsString())
                && !imageAttachments.isEmpty()) {
            throw new IllegalArgumentException("Failed Tool results cannot upload images");
        }

        this.result = result;
        this.imageAttachments = imageAttachments;
    }
    public List<ServerAgentImageAttachment> imageAttachments() { return imageAttachments; }
 public JsonObject result() { return dev.openallay.json.JsonTrees.copy(result); }
public void requireImages(List<ImageReference> references) {
        List<ImageReference> required = ModelImages.unique(references);
        List<ImageReference> supplied = dev.openallay.util.Java8Collections.toList(imageAttachments.stream()
                .map(ServerAgentImageAttachment::reference));
        if (!new java.util.HashSet<>(required).equals(new java.util.HashSet<>(supplied))) {
            throw new IllegalArgumentException("Tool image attachments must exactly match typed references");
        }
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ToolExecutionMessage)) return false;
        ToolExecutionMessage that = (ToolExecutionMessage) other;
        return java.util.Objects.equals(result, that.result) && java.util.Objects.equals(imageAttachments, that.imageAttachments);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(result);
        hash = 31 * hash + java.util.Objects.hashCode(imageAttachments);
        return hash;
    }
    @Override public String toString() { return "ToolExecutionMessage[result=" + result + ", imageAttachments=" + imageAttachments + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ToolExecutionMessage> schema() {
            return new dev.openallay.value.ValueSchema<>(ToolExecutionMessage.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ToolExecutionMessage>>asList(new dev.openallay.value.ValueSchema.Component<>(ToolExecutionMessage.class, "result", ToolExecutionMessage::result), new dev.openallay.value.ValueSchema.Component<>(ToolExecutionMessage.class, "imageAttachments", ToolExecutionMessage::imageAttachments)), arguments -> new ToolExecutionMessage((JsonObject) arguments[0], (List) arguments[1]));
        }
    }
}
