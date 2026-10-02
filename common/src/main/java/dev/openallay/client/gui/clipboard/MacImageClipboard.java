package dev.openallay.client.gui.clipboard;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.macosx.ObjCRuntime;

/** NSPasteboard only. No screen capture, AppleEvents, subprocesses, or AWT Toolkit. */
final class MacImageClipboard {
    private MacImageClipboard() {}

    /** Called by the client for explicit paste. Retain data before draining the client pool. */
    static ImageClipboard capture() {
        long send = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
        long poolClass = ObjCRuntime.objc_getClass("NSAutoreleasePool");
        long pasteboardClass = ObjCRuntime.objc_getClass("NSPasteboard");
        long stringClass = ObjCRuntime.objc_getClass("NSString");
        if (send == 0 || poolClass == 0 || pasteboardClass == 0 || stringClass == 0) {
            return ImageClipboard.Read::unavailable;
        }
        long pool = message(message(poolClass, "alloc", send), "init", send);
        if (pool == 0) return ImageClipboard.Read::unavailable;
        List<Long> images = new ArrayList<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long board = message(pasteboardClass, "generalPasteboard", send);
            if (board == 0) return ImageClipboard.Read::unavailable;
            // These are actual bitmap representations, not file paths or promised files.
            for (String type : new String[] {"public.png", "public.jpeg", "public.tiff"}) {
                ByteBuffer utf8 = stack.UTF8(type);
                long nativeType = JNI.invokePPPP(stringClass,
                        ObjCRuntime.sel_registerName("stringWithUTF8String:"), MemoryUtil.memAddress(utf8), send);
                if (nativeType == 0) continue;
                long data = JNI.invokePPPP(board, ObjCRuntime.sel_registerName("dataForType:"), nativeType, send);
                if (data != 0) images.add(message(data, "retain", send));
            }
        } catch (RuntimeException | LinkageError failed) {
            for (long data : images) JNI.invokePPV(data, ObjCRuntime.sel_registerName("release"), send);
            throw failed;
        } finally {
            JNI.invokePPV(pool, ObjCRuntime.sel_registerName("drain"), send);
        }
        if (images.isEmpty()) return ImageClipboard.Read::empty;
        AtomicBoolean consumed = new AtomicBoolean();
        return () -> {
            if (!consumed.compareAndSet(false, true)) return ImageClipboard.Read.unavailable();
            long workerPool = message(message(poolClass, "alloc", send), "init", send);
            try {
                for (long data : images) {
                    long length = JNI.invokePPN(data, ObjCRuntime.sel_registerName("length"), send);
                    if (length <= 0 || length > Integer.MAX_VALUE) continue;
                    long bytes = message(data, "bytes", send);
                    if (bytes == 0) continue;
                    byte[] encoded = new byte[(int) length];
                    MemoryUtil.memByteBuffer(bytes, encoded.length).get(encoded);
                    try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(encoded))) {
                        BufferedImage image = ImageIO.read(input);
                        if (image != null) return ImageClipboard.Read.image(image);
                    } catch (java.io.IOException invalidImage) {
                        // Try another representation captured from the same clipboard item.
                    }
                }
                return ImageClipboard.Read.unavailable();
            } finally {
                for (long data : images) JNI.invokePPV(data, ObjCRuntime.sel_registerName("release"), send);
                if (workerPool != 0) JNI.invokePPV(workerPool, ObjCRuntime.sel_registerName("drain"), send);
            }
        };
    }

    private static long message(long target, String selector, long send) {
        return JNI.invokePPP(target, ObjCRuntime.sel_registerName(selector), send);
    }
}
