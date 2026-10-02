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

    public static BufferedImage bitmap(Image image) throws IOException {
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
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
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

    public record Encoded(byte[] png, int width, int height, Preview preview) {
        public Encoded { png = png.clone(); java.util.Objects.requireNonNull(preview, "preview"); }
        @Override public byte[] png() { return png.clone(); }
    }

    public record Preview(int width, int height, int[] argb) {
        public Preview {
            if (width <= 0 || height <= 0 || (long) width * height != argb.length) {
                throw new IllegalArgumentException("invalid preview dimensions");
            }
            argb = argb.clone();
        }
        @Override public int[] argb() { return argb.clone(); }
    }
}
