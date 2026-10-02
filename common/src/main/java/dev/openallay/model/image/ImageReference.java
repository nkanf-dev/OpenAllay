package dev.openallay.model.image;

/** Portable image metadata. Managed paths and encoded image bytes never belong in transcripts. */
public record ImageReference(String sha256, String mimeType, int width, int height, long byteSize) {
    public ImageReference {
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("image sha256 must be 64 lowercase hexadecimal characters");
        }
        if (!"image/png".equals(mimeType) && !"image/jpeg".equals(mimeType)) {
            throw new IllegalArgumentException("only PNG and JPEG image references are supported");
        }
        if (width <= 0 || height <= 0 || byteSize <= 0) {
            throw new IllegalArgumentException("image dimensions and byte size must be positive");
        }
    }
}
