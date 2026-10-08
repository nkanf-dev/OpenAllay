package dev.openallay.client.gui.clipboard;

import org.lwjgl.system.JNI;

/** The generated JNI overload follows the target LWJGL BOOL carrier. */
final class MacBooleanNumber {
    private MacBooleanNumber() {}
    static long trueValue() {
        return trueValue(MacClipboardNativeApi.type("NSNumber"), MacClipboardNativeApi.selector("numberWithBool:"), MacClipboardNativeApi.SEND);
    }
    static long trueValue(long type, long selector, long send) {
        return JNI.invokePPP(type, selector, true, send);
    }
}
