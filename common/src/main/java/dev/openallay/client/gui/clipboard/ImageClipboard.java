package dev.openallay.client.gui.clipboard;

import java.awt.image.BufferedImage;

/** Reads only image data, and only after an explicit paste action. Never reads file-list flavors. */
@FunctionalInterface
public interface ImageClipboard {
    Read read();

    /** Capture platform ownership on the client if needed; bitmap work belongs to read(). */
    default ImageClipboard capture() { return this; }

    record Read(Status status, BufferedImage image) {
        public Read {
            java.util.Objects.requireNonNull(status, "status");
            if ((status == Status.IMAGE) != (image != null)) {
                throw new IllegalArgumentException("clipboard image status must match its data");
            }
        }
        public static Read empty() { return new Read(Status.EMPTY, null); }
        public static Read unavailable() { return new Read(Status.UNAVAILABLE, null); }
        public static Read image(BufferedImage image) { return new Read(Status.IMAGE, image); }
    }

    enum Status { IMAGE, EMPTY, UNAVAILABLE }
}
