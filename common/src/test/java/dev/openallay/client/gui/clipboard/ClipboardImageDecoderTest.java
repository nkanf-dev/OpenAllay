package dev.openallay.client.gui.clipboard;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class ClipboardImageDecoderTest {
    @Test
    void validPngReturnsItsPixelsWithoutSecondImageStreamCloseFailure() throws Exception {
        BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(2, 1, 0xFF123456);
        try (var bytes = new ByteArrayInputStream(ClipboardImageEncoder.encode(source).png())) {
            BufferedImage decoded = ClipboardImageDecoder.read(bytes);
            assertNotNull(decoded);
            assertEquals(3, decoded.getWidth());
            assertEquals(2, decoded.getHeight());
            assertEquals(0xFF123456, decoded.getRGB(2, 1));
        }
    }

    @Test
    void matureImageReadersDecodeJpegWithoutOwningTheCallersByteStream() throws Exception {
        BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, "jpeg", encoded));
        var bytes = new CloseTrackedStream(encoded.toByteArray());
        try (bytes) {
            BufferedImage decoded = ClipboardImageDecoder.read(bytes);
            assertNotNull(decoded);
            assertEquals(3, decoded.getWidth());
            assertEquals(2, decoded.getHeight());
            assertEquals(0, bytes.closes, "The input byte stream is caller-owned");
        }
        assertEquals(1, bytes.closes);
    }

    @Test
    void absentOrMalformedImageDataDoesNotBecomeABitmap() throws Exception {
        assertNull(ClipboardImageDecoder.read(new ByteArrayInputStream(new byte[0])));
        assertNull(ClipboardImageDecoder.read(new ByteArrayInputStream(new byte[] {1, 2, 3})));
        byte[] pngHeaderOnly = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        assertThrows(IOException.class, () -> ClipboardImageDecoder.read(new ByteArrayInputStream(pngHeaderOnly)));
    }

    private static final class CloseTrackedStream extends FilterInputStream {
        int closes;
        private CloseTrackedStream(byte[] bytes) { super(new ByteArrayInputStream(bytes)); }
        @Override public void close() throws IOException { closes++; super.close(); }
    }
}
