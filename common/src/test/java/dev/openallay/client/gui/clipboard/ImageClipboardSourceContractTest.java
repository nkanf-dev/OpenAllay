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
        String mac = source("common", "MacImageClipboard.java");
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
        String mac = source("common", "MacImageClipboard.java");
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
        String mac = source("common", "MacImageClipboard.java");
        int worker = mac.indexOf("public Read read()");
        assertTrue(worker > mac.indexOf("static ImageClipboard capture(long board)"));
        assertTrue(mac.indexOf("BufferedImage image = decode(representation)") > worker);
        assertTrue(mac.indexOf("MemoryUtil.memByteBuffer(bytes, encoded.length).get(encoded)") > worker);
        assertTrue(mac.contains("release(representations);\n                NativeApi.release(pool, \"drain\")"));
        assertTrue(mac.contains("if (!consumed.compareAndSet(false, true)) return Read.unavailable()"));
        assertTrue(source("engine-core", "ImageClipboard.java").contains("extends AutoCloseable"));
    }

    @Test
    void macLengthsAndCountsUseTheRetainedFullWidthJniCarrierBeforeAnyBufferNarrowing() throws IOException {
        String mac = source("common", "MacImageClipboard.java");
        String length = "long length = JNI.invokePPP(data, NativeApi.selector(\"length\"), NativeApi.SEND);";
        String bounds = "if (length <= 0 || length > Integer.MAX_VALUE) return null;";
        String allocation = "byte[] encoded = new byte[(int) length];";
        assertTrue(mac.contains(length));
        assertTrue(mac.indexOf(bounds) > mac.indexOf(length));
        assertTrue(mac.indexOf(allocation) > mac.indexOf(bounds));
        assertTrue(mac.contains("private static long count(long array) { return JNI.invokePPP(array, selector(\"count\"), SEND); }"));
        assertTrue(mac.contains("for (long i = 0, count = NativeApi.count(nativeTypes); i < count; i++)"));
        assertTrue(mac.contains("for (long i = 0, count = NativeApi.count(urls); i < count; i++)"));
        assertFalse(mac.contains("JNI.invokePPN("), "LWJGL 3.3.1 has no such overload");
        assertFalse(mac.contains("JNI.invokePPI("), "NSUInteger is not a 32-bit int");
        assertFalse(mac.contains("Integer.toUnsignedLong("));
        assertFalse(mac.contains("getDeclaredMethod("));
    }

    private static String source(String module, String name) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) root = root.getParent();
        if (root == null) throw new IOException("Unable to locate repository root");
        return Files.readString(root.resolve(module + "/src/main/java/dev/openallay/client/gui/clipboard/" + name));
    }
}
