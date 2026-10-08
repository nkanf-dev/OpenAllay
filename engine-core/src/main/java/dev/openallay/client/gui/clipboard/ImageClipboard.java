package dev.openallay.client.gui.clipboard;

import java.awt.image.BufferedImage;

/** Reads image representations or explicitly copied local image files, only after a paste action. */
@FunctionalInterface
public interface ImageClipboard extends AutoCloseable {
    Read read();

    /** Capture platform ownership on the client if needed; bitmap work belongs to read(). */
    default ImageClipboard capture() { return this; }

    /** Release an owned native capture if work is rejected or has finished. Idempotent. */
    @Override default void close() {}

    @dev.openallay.value.ValueType(Read.ValueSchemaProvider.class)
public static final class Read {
    private final Status status;
    private final BufferedImage image;
    public Read(Status status, BufferedImage image) {

            java.util.Objects.requireNonNull(status, "status");
            if ((status == Status.IMAGE) != (image != null)) {
                throw new IllegalArgumentException("clipboard image status must match its data");
            }

        this.status = status;
        this.image = image;
    }
    public Status status() { return status; }
    public BufferedImage image() { return image; }
public static Read empty() { return new Read(Status.EMPTY, null); }
public static Read unavailable() { return new Read(Status.UNAVAILABLE, null); }
public static Read image(BufferedImage image) { return new Read(Status.IMAGE, image); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Read)) return false;
        Read that = (Read) other;
        return java.util.Objects.equals(status, that.status) && java.util.Objects.equals(image, that.image);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(image);
        return hash;
    }
    @Override public String toString() { return "Read[status=" + status + ", image=" + image + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Read> schema() {
            return new dev.openallay.value.ValueSchema<>(Read.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Read>>asList(new dev.openallay.value.ValueSchema.Component<>(Read.class, "status", Read::status), new dev.openallay.value.ValueSchema.Component<>(Read.class, "image", Read::image)), arguments -> new Read((Status) arguments[0], (BufferedImage) arguments[1]));
        }
    }
}

    enum Status { IMAGE, EMPTY, UNAVAILABLE }
}
