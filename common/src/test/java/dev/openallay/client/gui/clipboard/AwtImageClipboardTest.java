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
    void ordinaryTextIsNotTreatedAsAnImageFilePath() {
        AtomicInteger transfers = new AtomicInteger();
        Transferable text = new Transferable() {
            public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.stringFlavor}; }
            public boolean isDataFlavorSupported(DataFlavor flavor) { return flavor.equals(DataFlavor.stringFlavor); }
            public Object getTransferData(DataFlavor flavor) { transfers.incrementAndGet(); throw new AssertionError("Must not read ordinary text"); }
        };
        assertEquals(ImageClipboard.Status.EMPTY, new AwtImageClipboard(() -> text).read().status());
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
    void encodedImageFlavorsAndLaterValidRepresentationsAreDecoded() throws Exception {
        DataFlavor png = new DataFlavor("image/png;class=java.io.InputStream");
        byte[] pixels = ClipboardImageEncoder.encode(sample()).png();
        Transferable representations = transferable(java.util.Map.of(
                DataFlavor.imageFlavor, "not an Image",
                png, new ByteArrayInputStream(pixels)));
        ImageClipboard.Read read = new AwtImageClipboard(() -> representations).read();
        assertEquals(ImageClipboard.Status.IMAGE, read.status());
        assertEquals(0xFF123456, read.image().getRGB(2, 1));
    }

    @Test
    void advertisedInvalidImageIsUnavailableRatherThanEmpty() throws Exception {
        DataFlavor png = new DataFlavor("image/png;class=java.io.InputStream");
        assertEquals(ImageClipboard.Status.UNAVAILABLE, new AwtImageClipboard(() -> transferable(
                java.util.Map.of(png, new ByteArrayInputStream(new byte[] {1, 2, 3})))).read().status());
        assertEquals(ImageClipboard.Status.UNAVAILABLE, new AwtImageClipboard(() -> transferable(
                java.util.Map.of(DataFlavor.imageFlavor, "not an Image"))).read().status());
    }

    @Test
    void lazyAwtImageFlavorIsLoadedBeforeDimensionsAreRead() {
        int[] argb = {0xFF112233, 0xFF445566, 0xFF778899, 0xFF123456};
        java.awt.Image lazy = java.awt.Toolkit.getDefaultToolkit().createImage(
                new java.awt.image.MemoryImageSource(2, 2, argb, 0, 2));
        ImageClipboard.Read read = new AwtImageClipboard(() -> transferable(
                java.util.Map.of(DataFlavor.imageFlavor, lazy))).read();
        assertEquals(ImageClipboard.Status.IMAGE, read.status());
        assertEquals(2, read.image().getWidth());
        assertEquals(0xFF123456, read.image().getRGB(1, 1));
    }

    @Test
    void copiedLocalImageFilesAndUriListsAreRealImageRepresentations(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        java.nio.file.Path image = directory.resolve("copied image.png");
        java.nio.file.Files.write(image, ClipboardImageEncoder.encode(sample()).png());
        java.nio.file.Path text = directory.resolve("not-an-image.txt");
        java.nio.file.Files.writeString(text, "ordinary document");
        ImageClipboard.Read files = new AwtImageClipboard(() -> transferable(java.util.Map.of(
                DataFlavor.javaFileListFlavor, java.util.List.of(text.toFile(), image.toFile())))).read();
        assertEquals(ImageClipboard.Status.IMAGE, files.status());
        assertEquals(0xFF123456, files.image().getRGB(2, 1));
        DataFlavor uris = new DataFlavor("text/uri-list;class=java.lang.String");
        ImageClipboard.Read uri = new AwtImageClipboard(() -> transferable(java.util.Map.of(
                uris, "# Copied file\r\n" + image.toUri() + "\r\n"))).read();
        assertEquals(ImageClipboard.Status.IMAGE, uri.status());
        assertEquals(0xFF123456, uri.image().getRGB(2, 1));
        assertEquals(ImageClipboard.Status.EMPTY, new AwtImageClipboard(() -> transferable(java.util.Map.of(
                DataFlavor.javaFileListFlavor, java.util.List.of(text.toFile())))).read().status());
        assertEquals(ImageClipboard.Status.EMPTY, new AwtImageClipboard(() -> transferable(java.util.Map.of(
                uris, "https://example.invalid/image.png\n"))).read().status());
    }

    @Test
    void unreadableCopiedImageFileIsUnavailableAndLaterValidImageStillWins(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        java.nio.file.Path broken = directory.resolve("broken.png");
        java.nio.file.Files.writeString(broken, "not PNG data");
        java.nio.file.Path good = directory.resolve("good.png");
        java.nio.file.Files.write(good, ClipboardImageEncoder.encode(sample()).png());
        assertEquals(ImageClipboard.Status.UNAVAILABLE, new AwtImageClipboard(() -> transferable(java.util.Map.of(
                DataFlavor.javaFileListFlavor, java.util.List.of(broken.toFile())))).read().status());
        assertEquals(ImageClipboard.Status.IMAGE, new AwtImageClipboard(() -> transferable(java.util.Map.of(
                DataFlavor.javaFileListFlavor, java.util.List.of(broken.toFile(), good.toFile())))).read().status());
    }

    @Test
    void captureSnapshotsThePasteSelectionWithoutReadingItsPixels() {
        AtomicInteger selections = new AtomicInteger();
        AtomicInteger transfers = new AtomicInteger();
        BufferedImage first = sample();
        Transferable selected = new Transferable() {
            public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.imageFlavor}; }
            public boolean isDataFlavorSupported(DataFlavor flavor) { return flavor.equals(DataFlavor.imageFlavor); }
            public Object getTransferData(DataFlavor flavor) { transfers.incrementAndGet(); return first; }
        };
        var clipboard = new AwtImageClipboard(() -> selections.getAndIncrement() == 0 ? selected : null);
        ImageClipboard captured = clipboard.capture();
        assertEquals(1, selections.get());
        assertEquals(0, transfers.get());
        assertEquals(ImageClipboard.Status.IMAGE, captured.read().status());
        assertEquals(1, selections.get());
        assertEquals(1, transfers.get());
    }

    @Test
    void brokenFileListDoesNotHideALaterValidUriRepresentation(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        java.nio.file.Path image = directory.resolve("good.png");
        java.nio.file.Files.write(image, ClipboardImageEncoder.encode(sample()).png());
        DataFlavor uris = new DataFlavor("text/uri-list;class=java.lang.String");
        Transferable offered = new Transferable() {
            public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.javaFileListFlavor, uris}; }
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return flavor.equals(DataFlavor.javaFileListFlavor) || flavor.equals(uris);
            }
            public Object getTransferData(DataFlavor flavor) throws java.io.IOException {
                if (flavor.equals(DataFlavor.javaFileListFlavor)) throw new java.io.IOException("unreadable file-list representation");
                return image.toUri().toString();
            }
        };
        assertEquals(ImageClipboard.Status.IMAGE, new AwtImageClipboard(() -> offered).read().status());
    }

    private static BufferedImage sample() {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(2, 1, 0xFF123456);
        return image;
    }

    private static Transferable transferable(java.util.Map<DataFlavor, Object> values) {
        return new Transferable() {
            public DataFlavor[] getTransferDataFlavors() { return values.keySet().toArray(DataFlavor[]::new); }
            public boolean isDataFlavorSupported(DataFlavor flavor) { return values.containsKey(flavor); }
            public Object getTransferData(DataFlavor flavor) throws java.awt.datatransfer.UnsupportedFlavorException {
                if (!values.containsKey(flavor)) throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
                return values.get(flavor);
            }
        };
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
