package dev.openallay.model.image;

/** Portable image metadata. Managed paths and encoded image bytes never belong in transcripts. */
@dev.openallay.value.ValueType(ImageReference.ValueSchemaProvider.class)
public final class ImageReference {
    private final String sha256;
    private final String mimeType;
    private final int width;
    private final int height;
    private final long byteSize;
    public ImageReference(String sha256, String mimeType, int width, int height, long byteSize) {

        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("image sha256 must be 64 lowercase hexadecimal characters");
        }
        if (!"image/png".equals(mimeType) && !"image/jpeg".equals(mimeType)) {
            throw new IllegalArgumentException("only PNG and JPEG image references are supported");
        }
        if (width <= 0 || height <= 0 || byteSize <= 0) {
            throw new IllegalArgumentException("image dimensions and byte size must be positive");
        }

        this.sha256 = sha256;
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
        this.byteSize = byteSize;
    }
    public String sha256() { return sha256; }
    public String mimeType() { return mimeType; }
    public int width() { return width; }
    public int height() { return height; }
    public long byteSize() { return byteSize; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ImageReference)) return false;
        ImageReference that = (ImageReference) other;
        return java.util.Objects.equals(sha256, that.sha256) && java.util.Objects.equals(mimeType, that.mimeType) && width == that.width && height == that.height && byteSize == that.byteSize;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sha256);
        hash = 31 * hash + java.util.Objects.hashCode(mimeType);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + Long.hashCode(byteSize);
        return hash;
    }
    @Override public String toString() { return "ImageReference[sha256=" + sha256 + ", mimeType=" + mimeType + ", width=" + width + ", height=" + height + ", byteSize=" + byteSize + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ImageReference> schema() {
            return new dev.openallay.value.ValueSchema<>(ImageReference.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ImageReference>>asList(new dev.openallay.value.ValueSchema.Component<>(ImageReference.class, "sha256", ImageReference::sha256), new dev.openallay.value.ValueSchema.Component<>(ImageReference.class, "mimeType", ImageReference::mimeType), new dev.openallay.value.ValueSchema.Component<>(ImageReference.class, "width", ImageReference::width), new dev.openallay.value.ValueSchema.Component<>(ImageReference.class, "height", ImageReference::height), new dev.openallay.value.ValueSchema.Component<>(ImageReference.class, "byteSize", ImageReference::byteSize)), arguments -> new ImageReference((String) arguments[0], (String) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (Long) arguments[4]));
        }
    }
}
