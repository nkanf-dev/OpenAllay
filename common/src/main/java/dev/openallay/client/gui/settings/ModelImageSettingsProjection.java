package dev.openallay.client.gui.settings;

import dev.openallay.model.image.ImageInputCapability;

/** Profile-local manual image-input choice; null means metadata-owned automatic discovery. */
public record ModelImageSettingsProjection(ImageInputCapability selected) {
    private static final String PREFIX = "screen.openallay.settings.models.image_input.";

    public String selectedLabelKey() {
        return PREFIX + (selected == null ? "auto" : selected.encoded());
    }

    public String explanationKey() {
        return PREFIX + "description";
    }

    public ImageInputCapability next() {
        if (selected == null) return ImageInputCapability.SUPPORTED;
        return switch (selected) {
            case SUPPORTED -> ImageInputCapability.UNSUPPORTED;
            case UNSUPPORTED -> ImageInputCapability.UNKNOWN;
            case UNKNOWN -> null;
        };
    }
}
