package dev.openallay.client.voice;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import dev.openallay.client.voice.MacMicrophonePermission.Status;

/** Stock JNA 4.4 Objective-C ABI. Loaded only by explicit macOS capture permission preflight. */
final class NativeMacMicrophoneAuthorization implements MacMicrophonePermission.Authorization {
    @Override public Status status() {
        Pointer pool = NativeApi.pool();
        try {
            // NSInteger is a native signed long on both supported macOS ABIs.
            NativeLong value = (NativeLong) NativeApi.SEND.invoke(NativeLong.class, new Object[] {
                    NativeApi.type("AVCaptureDevice"), NativeApi.selector("authorizationStatusForMediaType:"),
                    NativeApi.string("soun") });
            {
dev.openallay.client.voice.MacMicrophonePermission.Status $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((value.intValue())) {
case 0:
{
$oaSwitch0_exit_result = Status.NOT_DETERMINED; break $oaSwitch0_exit;
}
case 1:
{
$oaSwitch0_exit_result = Status.RESTRICTED; break $oaSwitch0_exit;
}
case 2:
{
$oaSwitch0_exit_result = Status.DENIED; break $oaSwitch0_exit;
}
case 3:
{
$oaSwitch0_exit_result = Status.AUTHORIZED; break $oaSwitch0_exit;
}
default:
{
$oaSwitch0_exit_result = Status.UNKNOWN; break $oaSwitch0_exit;
}
}
}
return $oaSwitch0_exit_result;
}
        } finally { NativeApi.drain(pool); }
    }
    @Override public boolean hasUsageDescription() {
        Pointer pool = NativeApi.pool();
        try {
            Pointer bundle = NativeApi.message(NativeApi.type("NSBundle"), "mainBundle");
            if (bundle == null) return false;
            Pointer description = NativeApi.SEND.invokePointer(new Object[] { bundle,
                    NativeApi.selector("objectForInfoDictionaryKey:"), NativeApi.string("NSMicrophoneUsageDescription") });
            if (description == null) return false;
            // Objective-C BOOL is one byte; both signed-char and C-bool use zero for false.
            byte isString = ((Byte) NativeApi.SEND.invoke(Byte.class, new Object[] { description,
                    NativeApi.selector("isKindOfClass:"), NativeApi.type("NSString") })).byteValue();
            if (isString == 0) return false;
            Pointer bytes = NativeApi.message(description, "UTF8String");
            return bytes != null && !dev.openallay.util.Java8Strings.isBlank(bytes.getString(0, "UTF-8"));
        } finally { NativeApi.drain(pool); }
    }
    private static final class NativeApi {
        // Keep frameworks loaded while Objective-C classes and selectors remain in use.
        private static final NativeLibrary AV_FOUNDATION = NativeLibrary.getInstance(
                "/System/Library/Frameworks/AVFoundation.framework/AVFoundation");
        private static final NativeLibrary OBJC = NativeLibrary.getInstance("/usr/lib/libobjc.A.dylib");
        private static final Function GET_CLASS = OBJC.getFunction("objc_getClass");
        private static final Function SELECTOR = OBJC.getFunction("sel_registerName");
        private static final Function SEND = OBJC.getFunction("objc_msgSend");
        private static Pointer type(String name) {
            Pointer value = GET_CLASS.invokePointer(new Object[] { name });
            if (value == null) throw new IllegalStateException("Missing macOS class: " + name);
            return value;
        }
        private static Pointer selector(String name) { return SELECTOR.invokePointer(new Object[] { name }); }
        private static Pointer message(Pointer receiver, String selector) {
            return SEND.invokePointer(new Object[] { receiver, selector(selector) });
        }
        private static Pointer string(String text) {
            // These permission keys/media types are ASCII; JNA owns the call-duration C string.
            Pointer value = SEND.invokePointer(new Object[] { type("NSString"),
                    selector("stringWithUTF8String:"), text });
            if (value == null) throw new IllegalStateException("Could not create a native permission string.");
            return value;
        }
        private static Pointer pool() {
            Pointer pool = message(message(type("NSAutoreleasePool"), "alloc"), "init");
            if (pool == null) throw new IllegalStateException("Could not create a native permission pool.");
            return pool;
        }
        private static void drain(Pointer pool) { SEND.invokeVoid(new Object[] { pool, selector("drain") }); }
    }
}
