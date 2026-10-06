package dev.openallay.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.model.image.ImageReference;
import java.util.Objects;

public sealed interface ModelContent
        permits ModelContent.Text,
                ModelContent.Image,
                ModelContent.Reasoning,
                ModelContent.ToolUse,
                ModelContent.ToolResult {
    record Text(String text) implements ModelContent {
        public Text {
            Objects.requireNonNull(text, "text");
        }
    }

    /** A managed image reference, never a path, URL or binary payload. */
    record Image(ImageReference reference, String originToolUseId) implements ModelContent {
        public Image {
            Objects.requireNonNull(reference, "reference");
            if (originToolUseId != null && originToolUseId.isBlank()) {
                throw new IllegalArgumentException("Image tool origin must not be blank");
            }
        }

        public Image(ImageReference reference) {
            this(reference, null);
        }
    }

    record Reasoning(String text, String signature) implements ModelContent {
        public Reasoning {
            Objects.requireNonNull(text, "text");
        }
    }

    record ToolUse(String id, String name, JsonObject input) implements ModelContent {
        public ToolUse {
            if (id == null || id.isBlank() || name == null || name.isBlank()) {
                throw new IllegalArgumentException("Tool-use id and name are required");
            }
            input = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(input, "input"));
        }

        @Override
        public JsonObject input() {
            return dev.openallay.json.JsonTrees.copy(input);
        }
    }

    record ToolResult(String toolUseId, JsonElement value, boolean error,
                      java.util.List<ImageReference> images) implements ModelContent {
        public ToolResult {
            if (toolUseId == null || toolUseId.isBlank()) {
                throw new IllegalArgumentException("Tool result requires toolUseId");
            }
            value = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(value, "value"));
            images = java.util.List.copyOf(images);
            dev.openallay.model.image.ModelImages.unique(images);
            if (error && !images.isEmpty()) {
                throw new IllegalArgumentException("Failed tool results cannot publish images");
            }
        }

        public ToolResult(String toolUseId, JsonElement value, boolean error) {
            this(toolUseId, value, error, java.util.List.of());
        }

        @Override
        public JsonElement value() {
            return dev.openallay.json.JsonTrees.copy(value);
        }
    }
}
