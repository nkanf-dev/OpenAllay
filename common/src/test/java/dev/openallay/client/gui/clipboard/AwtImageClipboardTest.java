package dev.openallay.client.gui.clipboard;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class AwtImageClipboardTest {
    @Test
    void ordinaryTextAndFileListsNeverTransferOrScanFiles() {
        AtomicInteger transfers = new AtomicInteger();
        Transferable textAndFiles = new Transferable() {
            public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.stringFlavor, DataFlavor.javaFileListFlavor}; }
            public boolean isDataFlavorSupported(DataFlavor flavor) { return flavor.equals(DataFlavor.stringFlavor) || flavor.equals(DataFlavor.javaFileListFlavor); }
            public Object getTransferData(DataFlavor flavor) { transfers.incrementAndGet(); throw new AssertionError("Must not read this flavor"); }
        };
        assertEquals(ImageClipboard.Status.EMPTY, new AwtImageClipboard(() -> textAndFiles).read().status());
        assertEquals(0, transfers.get());
    }

    @Test
    void imageAndTextClipboardReadsOnlyBitmapAndPreservesFullPixels() throws Exception {
        BufferedImage source = new BufferedImage(123, 61, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(122, 60, 0xFF123456);
        AtomicInteger transfers = new AtomicInteger();
        Transferable imageAndText = new Transferable() {
            public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.imageFlavor, DataFlavor.stringFlavor}; }
            public boolean isDataFlavorSupported(DataFlavor flavor) { return flavor.equals(DataFlavor.imageFlavor) || flavor.equals(DataFlavor.stringFlavor); }
            public Object getTransferData(DataFlavor flavor) {
                assertEquals(DataFlavor.imageFlavor, flavor);
                transfers.incrementAndGet(); return source;
            }
        };
        ImageClipboard.Read read = new AwtImageClipboard(() -> imageAndText).read();
        assertEquals(ImageClipboard.Status.IMAGE, read.status());
        ClipboardImageEncoder.Encoded encoded = ClipboardImageEncoder.encode(read.image());
        try (var input = new ByteArrayInputStream(encoded.png())) {
            BufferedImage full = ImageIO.read(input);
            assertEquals(123, full.getWidth()); assertEquals(61, full.getHeight());
            assertEquals(0xFF123456, full.getRGB(122, 60));
        }
        assertTrue(encoded.preview().width() <= 40); assertTrue(encoded.preview().height() <= 40);
        assertEquals(1, transfers.get());
    }

    @Test
    void unavailableOrHeadlessClipboardHasFriendlyFailureWithoutDesktopAccess() {
        assertEquals(ImageClipboard.Status.UNAVAILABLE, new AwtImageClipboard(() -> {
            throw new java.awt.HeadlessException();
        }).read().status());
        assertEquals(ImageClipboard.Status.UNAVAILABLE, new AwtImageClipboard(() -> {
            throw new IllegalStateException("Clipboard busy");
        }).read().status());
        assertEquals(ImageClipboard.Status.EMPTY, new AwtImageClipboard(() -> null).read().status());
    }

    @Test
    void previewAndEncodedBuffersAreImmutable() throws Exception {
        ClipboardImageEncoder.Encoded encoded = ClipboardImageEncoder.encode(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB));
        byte[] first = encoded.png(); first[0] = 0;
        assertNotEquals(0, encoded.png()[0]);
        int[] pixels = encoded.preview().argb(); pixels[0] = 123;
        assertEquals(0, encoded.preview().argb()[0]);
    }
}
