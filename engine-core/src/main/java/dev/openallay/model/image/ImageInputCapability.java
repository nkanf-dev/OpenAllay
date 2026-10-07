package dev.openallay.model.image;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Published image-input support. Unknown is not permission to send an image. */
public enum ImageInputCapability {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN;

    public String encoded() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ImageInputCapability parse(String value) {
        return valueOf(Objects.requireNonNull(value, "value").toUpperCase(Locale.ROOT));
    }

    /** A missing input list is unknown; other multimodal flags are not evidence. */
    public static ImageInputCapability fromInputModalities(List<String> modalities) {
        if (modalities == null) return UNKNOWN;
        if (modalities.stream().anyMatch(value -> value == null || dev.openallay.util.Java8Strings.isBlank(value))) {
            throw new IllegalArgumentException("input modalities must be nonblank text");
        }
        return modalities.contains("image") ? SUPPORTED : UNSUPPORTED;
    }
}
