package dev.openallay.model;

import com.google.gson.annotations.JsonAdapter;
import dev.openallay.model.image.ImageReference;
import dev.openallay.world.ClientObservationAnchor;
import dev.openallay.world.ClientObservationAnchorJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@dev.openallay.value.ValueType(ModelMessage.ValueSchemaProvider.class)
public final class ModelMessage {
    private final ModelRole role;
    private final List<ModelContent> content;
    @JsonAdapter(value = ClientObservationAnchorJson.OptionalAdapter.class, nullSafe = false) private final Optional<ClientObservationAnchor> inputObservation;
    public ModelMessage(ModelRole role, List<ModelContent> content, Optional<ClientObservationAnchor> inputObservation) {

        Objects.requireNonNull(role, "role");
        content = dev.openallay.util.Java8Collections.listCopyOf(content);
        content.forEach(ModelContent::requireKnown);
        inputObservation = Objects.requireNonNull(inputObservation, "inputObservation");
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Model message content must not be empty");
        }
        if (inputObservation.isPresent()) {
            validateUserInput(role, content, hasAssociatedImage(inputObservation));
            ClientObservationAnchor anchor = inputObservation.orElseThrow(java.util.NoSuchElementException::new);
            if (anchor.image().isPresent()) {
                dev.openallay.world.WorldViewCapture image = anchor.image().orElseThrow(java.util.NoSuchElementException::new);
                if (!image.actorId().equals(anchor.focus().actorId())
                        || !image.dimension().equals(anchor.focus().dimension())) {
                    throw new IllegalArgumentException("Input reference source identities differ");
                }
            }
        } else if (content.stream().anyMatch(ModelContent.Image.class::isInstance)) {
            validateVisualInput(role, content, false);
        }

        this.role = role;
        this.content = content;
        this.inputObservation = inputObservation;
    }
    public ModelRole role() { return role; }
    public List<ModelContent> content() { return content; }
    public Optional<ClientObservationAnchor> inputObservation() { return inputObservation; }
public ModelMessage(ModelRole role, List<ModelContent> content) {
        this(role, content, Optional.empty());
    }
public ModelMessage withInputObservation(ClientObservationAnchor anchor) {
        return requireUserInput(new ModelMessage(role, content, Optional.of(
                Objects.requireNonNull(anchor, "anchor"))));
    }
public static ModelMessage userText(String text) {
        return new ModelMessage(ModelRole.USER, dev.openallay.util.Java8Collections.listOf(new ModelContent.Text(text)));
    }
public static ModelMessage userInput(String text, List<ImageReference> images) {
        return userInput(text, images, Optional.empty());
    }
public static ModelMessage userInput(String text, List<ImageReference> images,
            Optional<ClientObservationAnchor> observation) {
        Objects.requireNonNull(images, "images");
        Objects.requireNonNull(observation, "observation");
        List<ModelContent> blocks = new ArrayList<>();
        if (text != null && !dev.openallay.util.Java8Strings.isBlank(text)) {
            blocks.add(new ModelContent.Text(text));
        }
        for (ImageReference image : images) {
            blocks.add(new ModelContent.Image(image));
        }
        if (blocks.isEmpty() && hasAssociatedImage(observation)) {
            blocks.add(new ModelContent.Text(""));
        }
        return requireUserInput(new ModelMessage(ModelRole.USER, blocks, observation));
    }
public static ModelMessage requireUserInput(ModelMessage message) {
        Objects.requireNonNull(message, "message");
        validateUserInput(message.role(), message.content(), hasAssociatedImage(message.inputObservation()));
        return message;
    }
private static boolean hasAssociatedImage(Optional<ClientObservationAnchor> observation) {
        return observation.flatMap(ClientObservationAnchor::image).isPresent();
    }
private static void validateUserInput(ModelRole role, List<ModelContent> content, boolean associatedImage) {
        validateVisualInput(role, content, associatedImage);
        if (content.stream().anyMatch(block -> block instanceof ModelContent.Image
                && ((ModelContent.Image) block).originToolUseId() != null)) {
            throw new IllegalArgumentException("Player input cannot contain tool-origin images");
        }
    }
private static void validateVisualInput(ModelRole role, List<ModelContent> content, boolean associatedImage) {
        if (role != ModelRole.USER) {
            throw new IllegalArgumentException("Visual input must have USER role");
        }
        boolean nonempty = associatedImage;
        for (ModelContent block : content) {
            if (block instanceof ModelContent.Text) {
                ModelContent.Text text = (ModelContent.Text) block;
                nonempty |= !dev.openallay.util.Java8Strings.isBlank(text.text());
            } else if (block instanceof ModelContent.Image) {
                nonempty = true;
            } else {
                throw new IllegalArgumentException("Visual input supports only text and images");
            }
        }
        if (!nonempty) {
            throw new IllegalArgumentException("Visual input requires text or an image");
        }
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelMessage)) return false;
        ModelMessage that = (ModelMessage) other;
        return java.util.Objects.equals(role, that.role) && java.util.Objects.equals(content, that.content) && java.util.Objects.equals(inputObservation, that.inputObservation);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(role);
        hash = 31 * hash + java.util.Objects.hashCode(content);
        hash = 31 * hash + java.util.Objects.hashCode(inputObservation);
        return hash;
    }
    @Override public String toString() { return "ModelMessage[role=" + role + ", content=" + content + ", inputObservation=" + inputObservation + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelMessage> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelMessage.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelMessage>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelMessage.class, "role", ModelMessage::role), new dev.openallay.value.ValueSchema.Component<>(ModelMessage.class, "content", ModelMessage::content), new dev.openallay.value.ValueSchema.Component<>(ModelMessage.class, "inputObservation", ModelMessage::inputObservation)), arguments -> new ModelMessage((ModelRole) arguments[0], (List) arguments[1], (Optional) arguments[2]));
        }
    }
}
