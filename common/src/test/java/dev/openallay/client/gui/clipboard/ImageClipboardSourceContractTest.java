package dev.openallay.client.gui.clipboard;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Pure source contracts: no clipboard, native framework, desktop Toolkit, or provider access. */
final class ImageClipboardSourceContractTest {
    @Test
    void macUsesExplicitlyLoadedAppKitAndSamePrivateBoardReaderAsProduction() throws IOException {
        String mac = source("MacImageClipboard.java");
        assertTrue(mac.contains("MacOSXLibrary.create("));
        assertTrue(mac.contains("/System/Library/Frameworks/AppKit.framework/AppKit"));
        assertTrue(mac.contains("static ImageClipboard capture(long board)"));
        assertTrue(mac.contains("return capture(NativeApi.message(NativeApi.type(\"NSPasteboard\"), \"generalPasteboard\"))"));
        assertTrue(mac.contains("\"imageTypes\""));
        assertTrue(mac.contains("\"initWithData:\""));
        assertTrue(mac.contains("\"representationUsingType:properties:\""));
        assertFalse(mac.contains("Toolkit.getDefaultToolkit"));
        assertFalse(mac.contains("new javax.swing.ImageIcon"));
        assertFalse(mac.contains("ClipboardImageEncoder.bitmap"));
        assertFalse(mac.contains("ProcessBuilder"));
    }

    @Test
    void nativeCopiedFilesUseAppKitLocalImageClassificationAndRetainedOwnership() throws IOException {
        String mac = source("MacImageClipboard.java");
        assertTrue(mac.contains("NSPasteboardURLReadingFileURLsOnlyKey"));
        assertTrue(mac.contains("NSPasteboardURLReadingContentsConformToTypesKey"));
        assertTrue(mac.contains("\"public.image\""));
        assertTrue(mac.contains("\"isFileURL\""));
        assertTrue(mac.contains("NativeApi.constant(\"NSPasteboardURLReadingFileURLsOnlyKey\")"));
        assertTrue(mac.contains("NativeApi.message(data, \"retain\")"));
        assertTrue(mac.contains("NativeApi.message(url, \"retain\")"));
        assertTrue(mac.contains("if (consumed.compareAndSet(false, true)) release(representations)"));
        assertTrue(mac.contains("imageOffered ? ImageClipboard.Read::unavailable : ImageClipboard.Read::empty"));
        assertFalse(mac.contains("stringForType:"), "ordinary text is not a copied file path");
    }

    @Test
    void nativeDecodeAndReleaseRemainWorkerOwnedAndInvalidRepresentationsRemainUnavailable() throws IOException {
        String mac = source("MacImageClipboard.java");
        int worker = mac.indexOf("public Read read()");
        assertTrue(worker > mac.indexOf("static ImageClipboard capture(long board)"));
        assertTrue(mac.indexOf("BufferedImage image = decode(representation)") > worker);
        assertTrue(mac.indexOf("MemoryUtil.memByteBuffer(bytes, encoded.length).get(encoded)") > worker);
        assertTrue(mac.contains("release(representations);\n                NativeApi.release(pool, \"drain\")"));
        assertTrue(mac.contains("if (!consumed.compareAndSet(false, true)) return Read.unavailable()"));
        assertTrue(source("ImageClipboard.java").contains("extends AutoCloseable"));
    }

    private static String source(String name) throws IOException {
        Path packagePath = Path.of("src/main/java/dev/openallay/client/gui/clipboard", name);
        if (!Files.exists(packagePath)) packagePath = Path.of("common").resolve(packagePath);
        return Files.readString(packagePath);
    }
}
