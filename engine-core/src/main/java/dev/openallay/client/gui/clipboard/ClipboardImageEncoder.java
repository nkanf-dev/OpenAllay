package dev.openallay.client.gui.clipboard;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Off-client-thread, in-memory preparation. The full bitmap is encoded, not the thumbnail. */
public final class ClipboardImageEncoder {
    private static final int PREVIEW_SIZE = 40;
    private ClipboardImageEncoder() {}

    /** Decode managed image bytes with the shared reader's single-owner image-stream lifetime. */
    public static BufferedImage decode(byte[] encodedImage) throws IOException {
        java.util.Objects.requireNonNull(encodedImage, "encodedImage");
        return ClipboardImageDecoder.read(new java.io.ByteArrayInputStream(encodedImage));
    }

    public static BufferedImage bitmap(Image image) throws IOException {
        if (!(image instanceof BufferedImage)) {
            // AWT clipboard images can be lazy ToolkitImages. Load their pixels on the worker.
            // macOS native decoding returns BufferedImage and never enters this Toolkit path.
            javax.swing.ImageIcon loaded = new javax.swing.ImageIcon(image);
            if (loaded.getImageLoadStatus() != java.awt.MediaTracker.COMPLETE) {
                throw new IOException("Clipboard image could not be loaded");
            }
            image = loaded.getImage();
        }
        int width = image.getWidth(null);
        int height = image.getHeight(null);
        if (width <= 0 || height <= 0) throw new IOException("Clipboard image has no bitmap dimensions");
        BufferedImage bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = bitmap.createGraphics();
        try {
            if (!graphics.drawImage(image, 0, 0, null)) throw new IOException("Clipboard image is incomplete");
        } finally {
            graphics.dispose();
        }
        return bitmap;
    }

    public static Encoded encode(BufferedImage source) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            if (!ImageIO.write(source, "png", output)) throw new IOException("PNG encoder unavailable");
        }
        double scale = Math.min(1.0, Math.min((double) PREVIEW_SIZE / source.getWidth(),
                (double) PREVIEW_SIZE / source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int[] preview = new int[width * height];
        // BufferedImage pixels do not initialize a desktop Toolkit on the macOS path.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                preview[y * width + x] = source.getRGB(
                        Math.min(source.getWidth() - 1, x * source.getWidth() / width),
                        Math.min(source.getHeight() - 1, y * source.getHeight() / height));
            }
        }
        return new Encoded(bytes.toByteArray(), source.getWidth(), source.getHeight(),
                new Preview(width, height, preview));
    }

    @dev.openallay.value.ValueType(Encoded.ValueSchemaProvider.class)
public static final class Encoded {
    private final byte[] png;
    private final int width;
    private final int height;
    private final Preview preview;
    public Encoded(byte[] png, int width, int height, Preview preview) {
 png = png.clone(); java.util.Objects.requireNonNull(preview, "preview");
        this.png = png;
        this.width = width;
        this.height = height;
        this.preview = preview;
    }
    public int width() { return width; }
    public int height() { return height; }
    public Preview preview() { return preview; }
 public byte[] png() { return png.clone(); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Encoded)) return false;
        Encoded that = (Encoded) other;
        return java.util.Objects.equals(png, that.png) && width == that.width && height == that.height && java.util.Objects.equals(preview, that.preview);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(png);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + java.util.Objects.hashCode(preview);
        return hash;
    }
    @Override public String toString() { return "Encoded[png=" + png + ", width=" + width + ", height=" + height + ", preview=" + preview + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Encoded> schema() {
            return new dev.openallay.value.ValueSchema<>(Encoded.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Encoded>>asList(new dev.openallay.value.ValueSchema.Component<>(Encoded.class, "png", Encoded::png), new dev.openallay.value.ValueSchema.Component<>(Encoded.class, "width", Encoded::width), new dev.openallay.value.ValueSchema.Component<>(Encoded.class, "height", Encoded::height), new dev.openallay.value.ValueSchema.Component<>(Encoded.class, "preview", Encoded::preview)), arguments -> new Encoded((byte[]) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Preview) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(Preview.ValueSchemaProvider.class)
public static final class Preview {
    private final int width;
    private final int height;
    private final int[] argb;
    public Preview(int width, int height, int[] argb) {

            if (width <= 0 || height <= 0 || (long) width * height != argb.length) {
                throw new IllegalArgumentException("invalid preview dimensions");
            }
            argb = argb.clone();

        this.width = width;
        this.height = height;
        this.argb = argb;
    }
    public int width() { return width; }
    public int height() { return height; }
 public int[] argb() { return argb.clone(); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Preview)) return false;
        Preview that = (Preview) other;
        return width == that.width && height == that.height && java.util.Objects.equals(argb, that.argb);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + java.util.Objects.hashCode(argb);
        return hash;
    }
    @Override public String toString() { return "Preview[width=" + width + ", height=" + height + ", argb=" + argb + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Preview> schema() {
            return new dev.openallay.value.ValueSchema<>(Preview.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Preview>>asList(new dev.openallay.value.ValueSchema.Component<>(Preview.class, "width", Preview::width), new dev.openallay.value.ValueSchema.Component<>(Preview.class, "height", Preview::height), new dev.openallay.value.ValueSchema.Component<>(Preview.class, "argb", Preview::argb)), arguments -> new Preview((Integer) arguments[0], (Integer) arguments[1], (int[]) arguments[2]));
        }
    }
}
}
