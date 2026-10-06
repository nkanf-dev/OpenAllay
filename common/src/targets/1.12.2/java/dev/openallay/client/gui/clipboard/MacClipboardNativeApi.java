package dev.openallay.client.gui.clipboard;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

/** Real stock JNA 4.4 Objective-C ABI primitives; capture/decoding stays canonical. */
final class MacClipboardNativeApi {
    private static final NativeLibrary APP_KIT = NativeLibrary.getInstance("/System/Library/Frameworks/AppKit.framework/AppKit");
    private static final NativeLibrary OBJC = NativeLibrary.getInstance("/usr/lib/libobjc.A.dylib");
    private static final Function SEND_FUNCTION = OBJC.getFunction("objc_msgSend");
    static final long SEND = Pointer.nativeValue(SEND_FUNCTION);
    private MacClipboardNativeApi() {}
    private static Pointer pointer(long value) { return value == 0 ? null : new Pointer(value); }
    private static long address(Pointer value) { return Pointer.nativeValue(value); }
    static long type(String name) {
        long value = address(OBJC.getFunction("objc_getClass").invokePointer(new Object[]{name}));
        if (value == 0) throw new IllegalStateException("Missing macOS clipboard class: " + name);
        return value;
    }
    static long selector(String name) { return address(OBJC.getFunction("sel_registerName").invokePointer(new Object[]{name})); }
    static long message(long target, String selector) {
        return address(SEND_FUNCTION.invokePointer(new Object[]{pointer(target), pointer(selector(selector))}));
    }
    static long argument(long target, String selector, long argument) {
        return address(SEND_FUNCTION.invokePointer(new Object[]{pointer(target), pointer(selector(selector)), pointer(argument)}));
    }
    static long constant(String name) {
        Pointer symbol = APP_KIT.getGlobalVariableAddress(name);
        if (symbol == null) throw new IllegalStateException("Missing macOS clipboard constant: " + name);
        return address(symbol.getPointer(0));
    }
    static long count(long values) {
        return ((NativeLong) SEND_FUNCTION.invoke(NativeLong.class,
                new Object[]{pointer(values), pointer(selector("count"))})).longValue();
    }
    static long at(long values, long index) {
        return address(SEND_FUNCTION.invokePointer(new Object[]{pointer(values), pointer(selector("objectAtIndex:")), new NativeLong(index)}));
    }
    static void set(long dictionary, long value, long key) {
        SEND_FUNCTION.invokeVoid(new Object[]{pointer(dictionary), pointer(selector("setObject:forKey:")), pointer(value), pointer(key)});
    }
    static long string(String text) {
        byte[] utf8 = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Memory bytes = new Memory(utf8.length + 1L);
        bytes.write(0, utf8, 0, utf8.length);
        bytes.setByte(utf8.length, (byte) 0);
        return address(SEND_FUNCTION.invokePointer(new Object[]{pointer(type("NSString")), pointer(selector("stringWithUTF8String:")), bytes}));
    }
    static long pool() {
        long value = message(message(type("NSAutoreleasePool"), "alloc"), "init");
        if (value == 0) throw new IllegalStateException("Could not create a macOS clipboard pool");
        return value;
    }
    static void release(long object, String selector) {
        if (object != 0) SEND_FUNCTION.invokeVoid(new Object[]{pointer(object), pointer(selector(selector))});
    }
    static String utf8(long address) { return pointer(address).getString(0, "UTF-8"); }
    static boolean contains(long values, long value) {
        return ((Byte) SEND_FUNCTION.invoke(Byte.class,
                new Object[]{pointer(values), pointer(selector("containsObject:")), pointer(value)})).byteValue() != 0;
    }
    static long readObjects(long board, long classes, long options) {
        return address(SEND_FUNCTION.invokePointer(new Object[]{pointer(board), pointer(selector("readObjectsForClasses:options:")), pointer(classes), pointer(options)}));
    }
    static boolean isFileUrl(long url) {
        return ((Byte) SEND_FUNCTION.invoke(Byte.class,
                new Object[]{pointer(url), pointer(selector("isFileURL"))})).byteValue() != 0;
    }
    static long png(long bitmap, long properties) {
        return address(SEND_FUNCTION.invokePointer(new Object[]{pointer(bitmap), pointer(selector("representationUsingType:properties:")), new NativeLong(4), pointer(properties)}));
    }
    static long length(long data) {
        return ((NativeLong) SEND_FUNCTION.invoke(NativeLong.class,
                new Object[]{pointer(data), pointer(selector("length"))})).longValue();
    }
    static void copyBytes(long address, byte[] target) { pointer(address).read(0, target, 0, target.length); }
    static long booleanNumber(byte value) {
        return address(SEND_FUNCTION.invokePointer(new Object[]{pointer(type("NSNumber")), pointer(selector("numberWithBool:")), value}));
    }
}
