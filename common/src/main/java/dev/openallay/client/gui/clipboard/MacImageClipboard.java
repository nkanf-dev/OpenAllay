package dev.openallay.client.gui.clipboard;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** NSPasteboard/AppKit only. No screen capture, AppleEvents, subprocesses, or AWT Toolkit. */
final class MacImageClipboard {
    private MacImageClipboard() {}

    /** Called by the client for explicit paste. Capture ownership, not bitmap conversion. */
    static ImageClipboard capture() {
        long pool = MacClipboardNativeApi.pool();
        try {
            return capture(MacClipboardNativeApi.message(MacClipboardNativeApi.type("NSPasteboard"), "generalPasteboard"));
        } finally {
            MacClipboardNativeApi.release(pool, "drain");
        }
    }

    /** The same production reader also accepts a private/named pasteboard for native fixtures. */
    static ImageClipboard capture(long board) {
        if (board == 0) return ImageClipboard.Read::unavailable;
        List<Representation> representations = new ArrayList<>();
        long pool = MacClipboardNativeApi.pool();
        boolean imageOffered = false;
        try {
            long offeredTypes = MacClipboardNativeApi.message(board, "types");
            Set<String> imageTypes = new LinkedHashSet<>(dev.openallay.util.Java8Collections.listOf("public.png", "public.jpeg", "public.tiff"));
            long nativeTypes = MacClipboardNativeApi.message(MacClipboardNativeApi.type("NSImage"), "imageTypes");
            for (long i = 0, count = MacClipboardNativeApi.count(nativeTypes); i < count; i++) {
                long nativeType = MacClipboardNativeApi.at(nativeTypes, i);
                long utf8 = MacClipboardNativeApi.message(nativeType, "UTF8String");
                if (utf8 != 0) imageTypes.add(MacClipboardNativeApi.utf8(utf8));
            }
            for (String type : imageTypes) {
                long nativeType = MacClipboardNativeApi.string(type);
                if (!MacClipboardNativeApi.contains(offeredTypes, nativeType)) continue;
                imageOffered = true;
                long data = MacClipboardNativeApi.argument(board, "dataForType:", nativeType);
                if (data != 0) representations.add(new Representation(MacClipboardNativeApi.message(data, "retain"), false));
            }
            // Finder and other apps can copy an image as a file URL, not inline PNG/TIFF.
            // Let AppKit classify local image files. Never interpret ordinary clipboard text as a path.
            long classes = MacClipboardNativeApi.argument(MacClipboardNativeApi.type("NSArray"), "arrayWithObject:", MacClipboardNativeApi.type("NSURL"));
            long options = MacClipboardNativeApi.message(MacClipboardNativeApi.type("NSMutableDictionary"), "dictionary");
            long yes = MacBooleanNumber.trueValue();
            MacClipboardNativeApi.set(options, yes, MacClipboardNativeApi.constant("NSPasteboardURLReadingFileURLsOnlyKey"));
            long imageContentTypes = MacClipboardNativeApi.argument(MacClipboardNativeApi.type("NSArray"), "arrayWithObject:",
                    MacClipboardNativeApi.string("public.image"));
            MacClipboardNativeApi.set(options, imageContentTypes,
                    MacClipboardNativeApi.constant("NSPasteboardURLReadingContentsConformToTypesKey"));
            long urls = MacClipboardNativeApi.readObjects(board, classes, options);
            for (long i = 0, count = MacClipboardNativeApi.count(urls); i < count; i++) {
                long url = MacClipboardNativeApi.at(urls, i);
                if (url == 0 || !MacClipboardNativeApi.isFileUrl(url)) continue;
                imageOffered = true;
                representations.add(new Representation(MacClipboardNativeApi.message(url, "retain"), true));
            }
        } catch (RuntimeException | LinkageError failed) {
            release(representations);
            throw failed;
        } finally {
            MacClipboardNativeApi.release(pool, "drain");
        }
        if (representations.isEmpty()) {
            return imageOffered ? ImageClipboard.Read::unavailable : ImageClipboard.Read::empty;
        }
        return new Capture(representations);
    }

    @dev.openallay.value.ValueType(Representation.ValueSchemaProvider.class)
private static final class Representation {
    private final long object;
    private final boolean fileUrl;
    private Representation(long object, boolean fileUrl) {
        this.object = object;
        this.fileUrl = fileUrl;
    }
    public long object() { return object; }
    public boolean fileUrl() { return fileUrl; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Representation)) return false;
        Representation that = (Representation) other;
        return object == that.object && fileUrl == that.fileUrl;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(object);
        hash = 31 * hash + Boolean.hashCode(fileUrl);
        return hash;
    }
    @Override public String toString() { return "Representation[object=" + object + ", fileUrl=" + fileUrl + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Representation> schema() {
            return new dev.openallay.value.ValueSchema<>(Representation.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Representation>>asList(new dev.openallay.value.ValueSchema.Component<>(Representation.class, "object", Representation::object), new dev.openallay.value.ValueSchema.Component<>(Representation.class, "fileUrl", Representation::fileUrl)), arguments -> new Representation((Long) arguments[0], (Boolean) arguments[1]));
        }
    }
}

    private static final class Capture implements ImageClipboard {
        private final List<Representation> representations;
        private final AtomicBoolean consumed = new AtomicBoolean();

        private Capture(List<Representation> representations) {
            this.representations = dev.openallay.util.Java8Collections.listCopyOf(representations);
        }

        @Override
        public Read read() {
            if (!consumed.compareAndSet(false, true)) return Read.unavailable();
            long pool = 0;
            try {
                pool = MacClipboardNativeApi.pool();
                for (Representation representation : representations) {
                    try {
                        BufferedImage image = decode(representation);
                        if (image != null) return Read.image(image);
                    } catch (IOException | RuntimeException invalidImage) {
                        // Another representation of the copied image can still decode.
                    }
                }
                return Read.unavailable();
            } finally {
                release(representations);
                MacClipboardNativeApi.release(pool, "drain");
            }
        }

        @Override
        public void close() {
            if (consumed.compareAndSet(false, true)) release(representations);
        }
    }

    private static BufferedImage decode(Representation representation) throws IOException {
        long data = representation.object();
        if (!representation.fileUrl()) {
            // Use Java's mature decoders when possible. Some native TIFF/image variants need AppKit.
            try {
                BufferedImage image = readData(data);
                if (image != null) return image;
            } catch (IOException | RuntimeException invalidForJava) {
                // AppKit supports more native formats and TIFF compression variants than Java ImageIO.
            }
        }
        long allocated = MacClipboardNativeApi.message(MacClipboardNativeApi.type("NSImage"), "alloc");
        long image = MacClipboardNativeApi.argument(allocated,
                representation.fileUrl() ? "initWithContentsOfURL:" : "initWithData:", data);
        if (image == 0) return null;
        try {
            long tiff = MacClipboardNativeApi.message(image, "TIFFRepresentation");
            if (tiff == 0) return null;
            long bitmap = MacClipboardNativeApi.argument(MacClipboardNativeApi.type("NSBitmapImageRep"), "imageRepWithData:", tiff);
            if (bitmap == 0) return null;
            long properties = MacClipboardNativeApi.message(MacClipboardNativeApi.type("NSDictionary"), "dictionary");
            // NSBitmapImageFileTypePNG = 4. Native rasterization stays on the worker.
            long png = MacClipboardNativeApi.png(bitmap, properties);
            return png == 0 ? null : readData(png);
        } finally {
            MacClipboardNativeApi.release(image, "release");
        }
    }

    private static BufferedImage readData(long data) throws IOException {
        // 64-bit macOS NSUInteger uses LWJGL's retained pointer-width return carrier.
        long length = MacClipboardNativeApi.length(data);
        if (length <= 0 || length > Integer.MAX_VALUE) return null;
        long bytes = MacClipboardNativeApi.message(data, "bytes");
        if (bytes == 0) return null;
        byte[] encoded = new byte[(int) length];
        MacClipboardNativeApi.copyBytes(bytes, encoded);
        return ClipboardImageDecoder.read(new ByteArrayInputStream(encoded));
    }

    private static void release(List<Representation> representations) {
        for (Representation representation : representations) MacClipboardNativeApi.release(representation.object(), "release");
    }

}
