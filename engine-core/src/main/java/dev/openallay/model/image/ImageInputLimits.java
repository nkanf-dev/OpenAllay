package dev.openallay.model.image;

import java.io.IOException;

/** Bounds checked before a managed image is decoded or copied to a provider request. */
public record ImageInputLimits(long maxByteSize, int maxDimension, long maxPixels) {
    // Application envelope: PNG/JPEG, 5 MiB of compressed source bytes (before Base64).
    // This is NOT the exact provider allowance. The direct Claude API documents 10 MB
    // of Base64-encoded data per image and 8000 pixels on either axis. Partner gateways
    // have different limits. Each provider codec checks its own published constraints.
    // https://platform.claude.com/docs/en/build-with-claude/vision
    public static final long DEFAULT_MAX_BYTE_SIZE = 5L * 1024 * 1024;
    public static final int DEFAULT_MAX_DIMENSION = 8000;

    // Application memory policy, NOT a provider resolution limit. Budget 128 MiB
    // for one decode: up to 8 bytes/pixel for 16-bit RGBA plus an equal allowance
    // for reader working buffers. The compressed input has its separate byte limit.
    public static final long DEFAULT_DECODE_MEMORY_BUDGET = 128L * 1024 * 1024;
    public static final int DECODE_BUDGET_BYTES_PER_PIXEL = 16;
    public static final long DEFAULT_MAX_PIXELS =
            DEFAULT_DECODE_MEMORY_BUDGET / DECODE_BUDGET_BYTES_PER_PIXEL;

    public ImageInputLimits {
        if (maxByteSize <= 0 || maxByteSize > Integer.MAX_VALUE
                || maxDimension <= 0 || maxPixels <= 0) {
            throw new IllegalArgumentException("image input limits must be positive and fit a byte array");
        }
    }

    public static ImageInputLimits defaults() {
        return new ImageInputLimits(DEFAULT_MAX_BYTE_SIZE, DEFAULT_MAX_DIMENSION, DEFAULT_MAX_PIXELS);
    }

    public void validate(ImageReference reference) throws IOException {
        java.util.Objects.requireNonNull(reference, "reference");
        checkByteSize(reference.byteSize());
        checkDimensions(reference.width(), reference.height());
    }

    void checkByteSize(long byteSize) throws IOException {
        if (byteSize <= 0 || byteSize > maxByteSize) {
            throw new IOException("image exceeds the configured encoded byte limit");
        }
    }

    void checkDimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || width > maxDimension || height > maxDimension
                || (long) width * height > maxPixels) {
            throw new IOException("image exceeds the configured dimension or decode pixel limit");
        }
    }
}
