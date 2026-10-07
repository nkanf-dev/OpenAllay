package dev.openallay.model.image;

import java.io.IOException;

/** Bounds checked before a managed image is decoded or copied to a provider request. */
@dev.openallay.value.ValueType(ImageInputLimits.ValueSchemaProvider.class)
public final class ImageInputLimits {
    private final long maxByteSize;
    private final int maxDimension;
    private final long maxPixels;
    public ImageInputLimits(long maxByteSize, int maxDimension, long maxPixels) {

        if (maxByteSize <= 0 || maxByteSize > Integer.MAX_VALUE
                || maxDimension <= 0 || maxPixels <= 0) {
            throw new IllegalArgumentException("image input limits must be positive and fit a byte array");
        }

        this.maxByteSize = maxByteSize;
        this.maxDimension = maxDimension;
        this.maxPixels = maxPixels;
    }
    public long maxByteSize() { return maxByteSize; }
    public int maxDimension() { return maxDimension; }
    public long maxPixels() { return maxPixels; }
public static final long DEFAULT_MAX_BYTE_SIZE = 5L * 1024 * 1024;
public static final int DEFAULT_MAX_DIMENSION = 8000;
public static final long DEFAULT_DECODE_MEMORY_BUDGET = 128L * 1024 * 1024;
public static final int DECODE_BUDGET_BYTES_PER_PIXEL = 16;
public static final long DEFAULT_MAX_PIXELS =
            DEFAULT_DECODE_MEMORY_BUDGET / DECODE_BUDGET_BYTES_PER_PIXEL;
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ImageInputLimits)) return false;
        ImageInputLimits that = (ImageInputLimits) other;
        return maxByteSize == that.maxByteSize && maxDimension == that.maxDimension && maxPixels == that.maxPixels;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(maxByteSize);
        hash = 31 * hash + Integer.hashCode(maxDimension);
        hash = 31 * hash + Long.hashCode(maxPixels);
        return hash;
    }
    @Override public String toString() { return "ImageInputLimits[maxByteSize=" + maxByteSize + ", maxDimension=" + maxDimension + ", maxPixels=" + maxPixels + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ImageInputLimits> schema() {
            return new dev.openallay.value.ValueSchema<>(ImageInputLimits.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ImageInputLimits>>asList(new dev.openallay.value.ValueSchema.Component<>(ImageInputLimits.class, "maxByteSize", ImageInputLimits::maxByteSize), new dev.openallay.value.ValueSchema.Component<>(ImageInputLimits.class, "maxDimension", ImageInputLimits::maxDimension), new dev.openallay.value.ValueSchema.Component<>(ImageInputLimits.class, "maxPixels", ImageInputLimits::maxPixels)), arguments -> new ImageInputLimits((Long) arguments[0], (Integer) arguments[1], (Long) arguments[2]));
        }
    }
}
