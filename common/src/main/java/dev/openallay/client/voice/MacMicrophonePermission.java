package dev.openallay.client.voice;

import java.util.Locale;
import java.util.Objects;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.macosx.MacOSXLibrary;
import org.lwjgl.system.macosx.ObjCRuntime;

/** Read-only macOS preflight. Never loads native code until an explicit capture open. */
final class MacMicrophonePermission {
    private MacMicrophonePermission() {}

    static void checkBeforeOpen() throws PermissionException {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac")) return;
        check(new NativeAuthorization());
    }

    /** Test seam: injected authorization must not contact the OS. */
    static void check(Authorization authorization) throws PermissionException {
        Objects.requireNonNull(authorization);
        try {
            switch (authorization.status()) {
                case AUTHORIZED -> {
                    return;
                }
                case DENIED -> throw new PermissionException(Failure.DENIED,
                        "Microphone access is denied. Enable the game or launcher in System Settings > "
                                + "Privacy & Security > Microphone, then restart the game.");
                case RESTRICTED -> throw new PermissionException(Failure.RESTRICTED,
                        "macOS restricts microphone access for this game. Check Screen Time or device restrictions.");
                case NOT_DETERMINED -> {
                    if (!authorization.hasUsageDescription()) {
                        throw new PermissionException(Failure.LAUNCHER_NOT_PREPARED,
                                "This Java/game host has no NSMicrophoneUsageDescription. Use a macOS launcher "
                                        + "that supplies a microphone usage description for the game process, "
                                        + "then restart and try recording. OpenAllay does not modify app bundles.");
                    }
                    // Only explicit capture open/start may proceed for a prepared host.
                    // Do not construct an Objective-C block or request access during startup/listing.
                    // The provider's first-use permission prompt needs launcher-specific verification.
                }
                case UNKNOWN -> throw new PermissionException(Failure.CHECK_FAILED,
                        "macOS returned an unknown microphone permission status. Restart the game and "
                                + "check System Settings > Privacy & Security > Microphone.");
            }
        } catch (PermissionException failure) {
            throw failure;
        } catch (Exception | LinkageError failure) {
            throw new PermissionException(Failure.CHECK_FAILED,
                    "Could not check macOS microphone permission. Restart the game with a launcher "
                            + "that supports microphone access.", failure);
        }
    }

    enum Status {
        NOT_DETERMINED, RESTRICTED, DENIED, AUTHORIZED, UNKNOWN
    }

    enum Failure {
        DENIED, RESTRICTED, LAUNCHER_NOT_PREPARED, CHECK_FAILED
    }

    static final class PermissionException extends Exception {
        private final Failure failure;

        PermissionException(Failure failure, String message) {
            super(message);
            this.failure = failure;
        }

        PermissionException(Failure failure, String message, Throwable cause) {
            super(message, cause);
            this.failure = failure;
        }

        Failure failure() {
            return failure;
        }
    }

    interface Authorization {
        Status status() throws Exception;
        boolean hasUsageDescription() throws Exception;
    }

    private static final class NativeAuthorization implements Authorization {
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
