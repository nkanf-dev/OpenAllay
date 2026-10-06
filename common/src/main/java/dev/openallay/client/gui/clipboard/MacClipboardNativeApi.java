package dev.openallay.client.gui.clipboard;

import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.macosx.MacOSXLibrary;
import org.lwjgl.system.macosx.ObjCRuntime;

/** Actual selected native AppKit primitives; the one canonical clipboard algorithm owns capture/decoding. */
final class MacClipboardNativeApi {
    static final SharedLibrary APP_KIT = MacOSXLibrary.create(
                "/System/Library/Frameworks/AppKit.framework/AppKit");
    static final long SEND = messageSend();

    static long messageSend() {
            long send = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
            if (send == 0 || APP_KIT.address() == 0) throw new IllegalStateException("The macOS image clipboard API is unavailable");
            return send;
        }

    static long type(String name) {
            long type = ObjCRuntime.objc_getClass(name);
            if (type == 0) throw new IllegalStateException("Missing macOS clipboard class: " + name);
            return type;
        }

    static long constant(String name) {
            long symbol = APP_KIT.getFunctionAddress(name);
            if (symbol == 0) throw new IllegalStateException("Missing macOS clipboard constant: " + name);
            return MemoryUtil.memGetAddress(symbol);
        }
    static long selector(String name) { return ObjCRuntime.sel_registerName(name); }
    static long message(long target, String selector) { return JNI.invokePPP(target, selector(selector), SEND); }
    static long argument(long target, String selector, long argument) {
            return JNI.invokePPPP(target, selector(selector), argument, SEND);
        }
    static long count(long array) { return JNI.invokePPP(array, selector("count"), SEND); }
    static long at(long array, long index) { return JNI.invokePPPP(array, selector("objectAtIndex:"), index, SEND); }
    static void set(long dictionary, long value, long key) {
            JNI.invokePPPPV(dictionary, selector("setObject:forKey:"), value, key, SEND);
        }
    static long string(String text) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            return argument(type("NSString"), "stringWithUTF8String:", MemoryUtil.memAddress(stack.UTF8(text)));
        }
    }
    static long pool() {
            long pool = message(message(type("NSAutoreleasePool"), "alloc"), "init");
            if (pool == 0) throw new IllegalStateException("Could not create a macOS clipboard pool");
            return pool;
        }
    static void release(long object, String selector) {
            if (object != 0) JNI.invokePPV(object, selector(selector), SEND);
        }
    
    static String utf8(long address) { return MemoryUtil.memUTF8(address); }
    static boolean contains(long values, long value) { return JNI.invokePPPZ(values, selector("containsObject:"), value, SEND); }
    static long readObjects(long board, long classes, long options) { return JNI.invokePPPPP(board, selector("readObjectsForClasses:options:"), classes, options, SEND); }
    static boolean isFileUrl(long url) { return JNI.invokePPZ(url, selector("isFileURL"), SEND); }
    static long png(long bitmap, long properties) { return JNI.invokePPPPP(bitmap, selector("representationUsingType:properties:"), 4L, properties, SEND); }
    static long length(long data) { return JNI.invokePPP(data, selector("length"), SEND); }
    static void copyBytes(long address, byte[] target) { MemoryUtil.memByteBuffer(address, target.length).get(target); }
}
