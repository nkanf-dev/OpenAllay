package dev.openallay.client.gui.clipboard;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.macosx.MacOSXLibrary;
import org.lwjgl.system.macosx.ObjCRuntime;

/** NSPasteboard/AppKit only. No screen capture, AppleEvents, subprocesses, or AWT Toolkit. */
final class MacImageClipboard {
    private MacImageClipboard() {}

    /** Called by the client for explicit paste. Capture ownership, not bitmap conversion. */
    static ImageClipboard capture() {
        long pool = NativeApi.pool();
        try {
            return capture(NativeApi.message(NativeApi.type("NSPasteboard"), "generalPasteboard"));
        } finally {
            NativeApi.release(pool, "drain");
        }
    }

    /** The same production reader also accepts a private/named pasteboard for native fixtures. */
    static ImageClipboard capture(long board) {
        if (board == 0) return ImageClipboard.Read::unavailable;
        List<Representation> representations = new ArrayList<>();
        long pool = NativeApi.pool();
        boolean imageOffered = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long offeredTypes = NativeApi.message(board, "types");
            Set<String> imageTypes = new LinkedHashSet<>(List.of("public.png", "public.jpeg", "public.tiff"));
            long nativeTypes = NativeApi.message(NativeApi.type("NSImage"), "imageTypes");
            for (long i = 0, count = NativeApi.count(nativeTypes); i < count; i++) {
                long nativeType = NativeApi.at(nativeTypes, i);
                long utf8 = NativeApi.message(nativeType, "UTF8String");
                if (utf8 != 0) imageTypes.add(MemoryUtil.memUTF8(utf8));
            }
            for (String type : imageTypes) {
                long nativeType = NativeApi.string(stack, type);
                if (!JNI.invokePPPZ(offeredTypes, NativeApi.selector("containsObject:"), nativeType, NativeApi.SEND)) continue;
                imageOffered = true;
                long data = NativeApi.argument(board, "dataForType:", nativeType);
                if (data != 0) representations.add(new Representation(NativeApi.message(data, "retain"), false));
            }
            // Finder and other apps can copy an image as a file URL, not inline PNG/TIFF.
            // Let AppKit classify local image files. Never interpret ordinary clipboard text as a path.
            long classes = NativeApi.argument(NativeApi.type("NSArray"), "arrayWithObject:", NativeApi.type("NSURL"));
            long options = NativeApi.message(NativeApi.type("NSMutableDictionary"), "dictionary");
            long yes = MacBooleanNumber.trueValue(NativeApi.type("NSNumber"), NativeApi.selector("numberWithBool:"), NativeApi.SEND);
            NativeApi.set(options, yes, NativeApi.constant("NSPasteboardURLReadingFileURLsOnlyKey"));
            long imageContentTypes = NativeApi.argument(NativeApi.type("NSArray"), "arrayWithObject:",
                    NativeApi.string(stack, "public.image"));
            NativeApi.set(options, imageContentTypes,
                    NativeApi.constant("NSPasteboardURLReadingContentsConformToTypesKey"));
            long urls = JNI.invokePPPPP(board, NativeApi.selector("readObjectsForClasses:options:"),
                    classes, options, NativeApi.SEND);
            for (long i = 0, count = NativeApi.count(urls); i < count; i++) {
                long url = NativeApi.at(urls, i);
                if (url == 0 || !JNI.invokePPZ(url, NativeApi.selector("isFileURL"), NativeApi.SEND)) continue;
                imageOffered = true;
                representations.add(new Representation(NativeApi.message(url, "retain"), true));
            }
        } catch (RuntimeException | LinkageError failed) {
            release(representations);
            throw failed;
        } finally {
            NativeApi.release(pool, "drain");
        }
        if (representations.isEmpty()) {
            return imageOffered ? ImageClipboard.Read::unavailable : ImageClipboard.Read::empty;
        }
        return new Capture(representations);
    }

    private record Representation(long object, boolean fileUrl) {}

    private static final class Capture implements ImageClipboard {
        private final List<Representation> representations;
        private final AtomicBoolean consumed = new AtomicBoolean();

        private Capture(List<Representation> representations) {
            this.representations = List.copyOf(representations);
        }

        @Override
        public Read read() {
            if (!consumed.compareAndSet(false, true)) return Read.unavailable();
            long pool = 0;
            try {
                pool = NativeApi.pool();
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
                NativeApi.release(pool, "drain");
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
        long allocated = NativeApi.message(NativeApi.type("NSImage"), "alloc");
        long image = NativeApi.argument(allocated,
                representation.fileUrl() ? "initWithContentsOfURL:" : "initWithData:", data);
        if (image == 0) return null;
        try {
            long tiff = NativeApi.message(image, "TIFFRepresentation");
            if (tiff == 0) return null;
            long bitmap = NativeApi.argument(NativeApi.type("NSBitmapImageRep"), "imageRepWithData:", tiff);
            if (bitmap == 0) return null;
            long properties = NativeApi.message(NativeApi.type("NSDictionary"), "dictionary");
            // NSBitmapImageFileTypePNG = 4. Native rasterization stays on the worker.
            long png = JNI.invokePPPPP(bitmap, NativeApi.selector("representationUsingType:properties:"),
                    4L, properties, NativeApi.SEND);
            return png == 0 ? null : readData(png);
        } finally {
            NativeApi.release(image, "release");
        }
    }

    private static BufferedImage readData(long data) throws IOException {
        // 64-bit macOS NSUInteger uses LWJGL's retained pointer-width return carrier.
        long length = JNI.invokePPP(data, NativeApi.selector("length"), NativeApi.SEND);
        if (length <= 0 || length > Integer.MAX_VALUE) return null;
        long bytes = NativeApi.message(data, "bytes");
        if (bytes == 0) return null;
        byte[] encoded = new byte[(int) length];
        MemoryUtil.memByteBuffer(bytes, encoded.length).get(encoded);
        return ClipboardImageDecoder.read(new ByteArrayInputStream(encoded));
    }

    private static void release(List<Representation> representations) {
        for (Representation representation : representations) NativeApi.release(representation.object(), "release");
    }

    /** objc_getClass alone does not load AppKit in a bare JVM. Keep its classes loaded for all captures. */
    private static final class NativeApi {
        private static final SharedLibrary APP_KIT = MacOSXLibrary.create(
                "/System/Library/Frameworks/AppKit.framework/AppKit");
        private static final long SEND = messageSend();

        private static long messageSend() {
            long send = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
            if (send == 0 || APP_KIT.address() == 0) throw new IllegalStateException("The macOS image clipboard API is unavailable");
            return send;
        }

        private static long type(String name) {
            long type = ObjCRuntime.objc_getClass(name);
            if (type == 0) throw new IllegalStateException("Missing macOS clipboard class: " + name);
            return type;
        }

        private static long constant(String name) {
            long symbol = APP_KIT.getFunctionAddress(name);
            if (symbol == 0) throw new IllegalStateException("Missing macOS clipboard constant: " + name);
            return MemoryUtil.memGetAddress(symbol);
        }
        private static long selector(String name) { return ObjCRuntime.sel_registerName(name); }
        private static long message(long target, String selector) { return JNI.invokePPP(target, selector(selector), SEND); }
        private static long argument(long target, String selector, long argument) {
            return JNI.invokePPPP(target, selector(selector), argument, SEND);
        }
        private static long count(long array) { return JNI.invokePPP(array, selector("count"), SEND); }
        private static long at(long array, long index) { return JNI.invokePPPP(array, selector("objectAtIndex:"), index, SEND); }
        private static void set(long dictionary, long value, long key) {
            JNI.invokePPPPV(dictionary, selector("setObject:forKey:"), value, key, SEND);
        }
        private static long string(MemoryStack stack, String text) {
            return argument(type("NSString"), "stringWithUTF8String:", MemoryUtil.memAddress(stack.UTF8(text)));
        }
        private static long pool() {
            long pool = message(message(type("NSAutoreleasePool"), "alloc"), "init");
            if (pool == 0) throw new IllegalStateException("Could not create a macOS clipboard pool");
            return pool;
        }
        private static void release(long object, String selector) {
            if (object != 0) JNI.invokePPV(object, selector(selector), SEND);
        }
    }
}
