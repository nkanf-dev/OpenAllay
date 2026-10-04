package dev.openallay.model;

import dev.openallay.model.image.ImageReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ModelMessage(ModelRole role, List<ModelContent> content) {
    public ModelMessage {
        Objects.requireNonNull(role, "role");
        content = List.copyOf(content);
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Model message content must not be empty");
        }
        if (content.stream().anyMatch(ModelContent.Image.class::isInstance)) {
            validateVisualInput(role, content);
        }
    }

    public static ModelMessage userText(String text) {
        return new ModelMessage(ModelRole.USER, List.of(new ModelContent.Text(text)));
    }

    /** Creates typed player input. An image-only message does not need filler text. */
    public static ModelMessage userInput(String text, List<ImageReference> images) {
        Objects.requireNonNull(images, "images");
        List<ModelContent> blocks = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            blocks.add(new ModelContent.Text(text));
        }
        for (ImageReference image : images) {
            blocks.add(new ModelContent.Image(image));
        }
        return requireUserInput(new ModelMessage(ModelRole.USER, blocks));
    }

    /** Distinguishes player input from USER-role tool results. */
    public static ModelMessage requireUserInput(ModelMessage message) {
        Objects.requireNonNull(message, "message");
        validateUserInput(message.role(), message.content());
        return message;
    }

    private static void validateUserInput(ModelRole role, List<ModelContent> content) {
        validateVisualInput(role, content);
        if (content.stream().anyMatch(block -> block instanceof ModelContent.Image image
                && image.originToolUseId() != null)) {
            throw new IllegalArgumentException("Player input cannot contain tool-origin images");
        }
    }

    private static void validateVisualInput(ModelRole role, List<ModelContent> content) {
        if (role != ModelRole.USER) {
            throw new IllegalArgumentException("Visual input must have USER role");
        }
        boolean nonempty = false;
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
