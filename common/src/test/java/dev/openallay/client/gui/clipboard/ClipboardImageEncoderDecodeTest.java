package dev.openallay.client.gui.clipboard;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Pure managed-byte decoder checks. No clipboard, native texture, AppKit, or desktop Toolkit. */
final class ClipboardImageEncoderDecodeTest {
    @Test
    void publicDelegateReturnsExactPngPixelsWithoutAnImageStreamSecondClose() throws Exception {
        BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x40123456);
        source.setRGB(2, 1, 0xFFABCDEF);
        byte[] png = ClipboardImageEncoder.encode(source).png();
        BufferedImage decoded = ClipboardImageEncoder.decode(png);
        assertNotNull(decoded);
        assertEquals(3, decoded.getWidth());
        assertEquals(2, decoded.getHeight());
        assertEquals(0x40123456, decoded.getRGB(0, 0));
        assertEquals(0xFFABCDEF, decoded.getRGB(2, 1));
        assertNotNull(ClipboardImageEncoder.decode(png), "Each call owns one independent image-reader scope");
    }

    @Test
    void publicDelegateUsesStandardReaderForNonPngLargerThanThirtyKiB() throws Exception {
        BufferedImage source = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(48271L);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) source.setRGB(x, y, random.nextInt(0x1000000));
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, "jpeg", encoded));
        byte[] jpeg = encoded.toByteArray();
        assertTrue(jpeg.length > 30 * 1024, "Exercise a non-PNG managed payload, not a tiny header fixture");
        assertEquals(0xFF, Byte.toUnsignedInt(jpeg[0]));
        assertEquals(0xD8, Byte.toUnsignedInt(jpeg[1]));
        BufferedImage decoded = ClipboardImageEncoder.decode(jpeg);
        assertNotNull(decoded);
        assertEquals(512, decoded.getWidth());
        assertEquals(512, decoded.getHeight());
    }

    @Test
    void sharedReaderNeverClosesCallerByteStreamAndCallerClosesExactlyOnce() throws Exception {
        BufferedImage source = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(1, 0, 0xFF123456);
        CloseTrackedStream bytes = new CloseTrackedStream(ClipboardImageEncoder.encode(source).png());
        try (bytes) {
            BufferedImage decoded = ClipboardImageDecoder.read(bytes);
            assertNotNull(decoded);
            assertEquals(0xFF123456, decoded.getRGB(1, 0));
            assertEquals(0, bytes.closes, "The reader scope owns the image stream, not its caller's byte stream");
        }
        assertEquals(1, bytes.closes);
    }

    @Test
    void observationTextureSourceUsesSharedPublicDelegateWithNoSecondOwningImageStream() throws IOException {
        String textures = source("common", "client/observation/ObservationImageTextures.java");
        assertTrue(textures.contains("ClipboardImageEncoder.decode(bytes.value())"));
        assertFalse(textures.contains("MemoryCacheImageInputStream"));
        assertFalse(textures.contains("ImageIO.read("));
        String encoder = source("engine-core", "client/gui/clipboard/ClipboardImageEncoder.java");
        assertTrue(encoder.contains("public static BufferedImage decode(byte[] encodedImage) throws IOException"));
        assertTrue(encoder.contains("ClipboardImageDecoder.read(new java.io.ByteArrayInputStream(encodedImage))"));
    }

    private static String source(String module, String relative) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) root = root.getParent();
        if (root == null) throw new IOException("Unable to locate repository root");
        return Files.readString(root.resolve(module + "/src/main/java/dev/openallay/" + relative));
    }

    private static final class CloseTrackedStream extends FilterInputStream {
        int closes;
        CloseTrackedStream(byte[] encoded) { super(new ByteArrayInputStream(encoded)); }
        @Override public void close() throws IOException {
            closes++;
            if (closes != 1) throw new IOException("Byte stream closed more than once");
            super.close();
        }
    }
}
