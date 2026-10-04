package dev.openallay.model;

import com.google.gson.annotations.JsonAdapter;
import dev.openallay.model.image.ImageReference;
import dev.openallay.world.ClientObservationAnchor;
import dev.openallay.world.ClientObservationAnchorJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ModelMessage(ModelRole role, List<ModelContent> content,
        @JsonAdapter(value = ClientObservationAnchorJson.OptionalAdapter.class, nullSafe = false)
        Optional<ClientObservationAnchor> inputObservation) {
    public ModelMessage(ModelRole role, List<ModelContent> content) {
        this(role, content, Optional.empty());
    }

    /** Associate one admitted player input without turning source context into literal player text. */
    public ModelMessage withInputObservation(ClientObservationAnchor anchor) {
        return requireUserInput(new ModelMessage(role, content, Optional.of(
                Objects.requireNonNull(anchor, "anchor"))));
    }
    public ModelMessage {
        Objects.requireNonNull(role, "role");
        content = List.copyOf(content);
        inputObservation = Objects.requireNonNull(inputObservation, "inputObservation");
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Model message content must not be empty");
        }
        if (inputObservation.isPresent()) {
            validateUserInput(role, content, hasAssociatedImage(inputObservation));
            var anchor = inputObservation.orElseThrow();
            if (anchor.image().isPresent()) {
                var image = anchor.image().orElseThrow();
                if (!image.actorId().equals(anchor.focus().actorId())
                        || !image.dimension().equals(anchor.focus().dimension())) {
                    throw new IllegalArgumentException("Input reference source identities differ");
                }
            }
        } else if (content.stream().anyMatch(ModelContent.Image.class::isInstance)) {
            validateVisualInput(role, content, false);
        }
    }

    public static ModelMessage userText(String text) {
        return new ModelMessage(ModelRole.USER, List.of(new ModelContent.Text(text)));
    }

    /** Creates typed player input. An image-only message does not need filler text. */
    public static ModelMessage userInput(String text, List<ImageReference> images) {
        return userInput(text, images, Optional.empty());
    }

    public static ModelMessage userInput(String text, List<ImageReference> images,
            Optional<ClientObservationAnchor> observation) {
        Objects.requireNonNull(images, "images");
        Objects.requireNonNull(observation, "observation");
        List<ModelContent> blocks = new ArrayList<>();
        if (text != null && !text.isBlank()) {
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

    /** Distinguishes player input from USER-role tool results. */
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
        if (content.stream().anyMatch(block -> block instanceof ModelContent.Image image
                && image.originToolUseId() != null)) {
            throw new IllegalArgumentException("Player input cannot contain tool-origin images");
        }
    }

    private static void validateVisualInput(ModelRole role, List<ModelContent> content, boolean associatedImage) {
        if (role != ModelRole.USER) {
            throw new IllegalArgumentException("Visual input must have USER role");
        }
        boolean nonempty = associatedImage;
        for (ModelContent block : content) {
            if (block instanceof ModelContent.Text text) {
                nonempty |= !text.text().isBlank();
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
}
