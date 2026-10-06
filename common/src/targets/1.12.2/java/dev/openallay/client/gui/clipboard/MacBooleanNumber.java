package dev.openallay.client.gui.clipboard;

/** Objective-C BOOL uses a signed-byte carrier; NSNumber true is byte 1, not a pointer-width integer. */
final class MacBooleanNumber {
    private MacBooleanNumber() {}
    static long trueValue() { return MacClipboardNativeApi.booleanNumber((byte) 1); }
}
