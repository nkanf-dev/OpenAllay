package dev.openallay.client.voice;

import dev.openallay.client.voice.MacMicrophonePermission.Status;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.macosx.MacOSXLibrary;
import org.lwjgl.system.macosx.ObjCRuntime;

final class NativeMacMicrophoneAuthorization implements MacMicrophonePermission.Authorization {
    @Override
    public Status status() {
        long send = NativeApi.SEND;
        long pool = NativeApi.pool();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long captureDevice = NativeApi.requireClass("AVCaptureDevice");
            long mediaType = NativeApi.string(stack, "soun"); // AVMediaTypeAudio's native value.
            long value = JNI.invokePPPP(captureDevice,
                    ObjCRuntime.sel_registerName("authorizationStatusForMediaType:"), mediaType, send);
            return switch ((int) value) {
                case 0 -> Status.NOT_DETERMINED;
                case 1 -> Status.RESTRICTED;
                case 2 -> Status.DENIED;
                case 3 -> Status.AUTHORIZED;
                default -> Status.UNKNOWN;
            };
        } finally {
            NativeApi.drain(pool);
        }
    }

    @Override
    public boolean hasUsageDescription() {
        long send = NativeApi.SEND;
        long pool = NativeApi.pool();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long bundle = NativeApi.message(NativeApi.requireClass("NSBundle"), "mainBundle");
            if (bundle == 0) return false;
            long key = NativeApi.string(stack, "NSMicrophoneUsageDescription");
            long description = JNI.invokePPPP(bundle,
                    ObjCRuntime.sel_registerName("objectForInfoDictionaryKey:"), key, send);
            if (description == 0 || !JNI.invokePPPZ(description,
                    ObjCRuntime.sel_registerName("isKindOfClass:"), NativeApi.requireClass("NSString"), send)) {
                return false;
            }
            long bytes = NativeApi.message(description, "UTF8String");
            return bytes != 0 && !MemoryUtil.memUTF8(bytes).isBlank();
        } finally {
            NativeApi.drain(pool);
        }
    }

/** Keep the framework loaded: Objective-C classes outlive a single capture session. */
private static final class NativeApi {
    private static final SharedLibrary AV_FOUNDATION = MacOSXLibrary.create(
            "/System/Library/Frameworks/AVFoundation.framework/AVFoundation");
    private static final long SEND = messageSend();

    private static long messageSend() {
        long send = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
        if (send == 0 || AV_FOUNDATION.address() == 0) {
            throw new IllegalStateException("The macOS permission API is unavailable.");
        }
        return send;
    }

    private static long requireClass(String name) {
        long type = ObjCRuntime.objc_getClass(name);
        if (type == 0) throw new IllegalStateException("Missing macOS class: " + name);
        return type;
    }

    private static long string(MemoryStack stack, String text) {
        long string = JNI.invokePPPP(requireClass("NSString"),
                ObjCRuntime.sel_registerName("stringWithUTF8String:"),
                MemoryUtil.memAddress(stack.UTF8(text)), SEND);
        if (string == 0) throw new IllegalStateException("Could not create a native permission string.");
        return string;
    }

    private static long message(long target, String selector) {
        return JNI.invokePPP(target, ObjCRuntime.sel_registerName(selector), SEND);
    }

    private static long pool() {
        long pool = message(message(requireClass("NSAutoreleasePool"), "alloc"), "init");
        if (pool == 0) throw new IllegalStateException("Could not create a native permission pool.");
        return pool;
    }

    private static void drain(long pool) {
        JNI.invokePPV(pool, ObjCRuntime.sel_registerName("drain"), SEND);
    }
}
}
