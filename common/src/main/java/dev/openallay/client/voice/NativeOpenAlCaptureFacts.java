package dev.openallay.client.voice;

import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;

/** Reads capture function pointers from the game's current OpenAL implementation. */
final class NativeOpenAlCaptureFacts {
    private NativeOpenAlCaptureFacts() {}
    static boolean available() {
        ALCCapabilities caps = ALC.getCapabilities();
        return caps.alcCaptureOpenDevice != 0 && caps.alcCaptureCloseDevice != 0
                && caps.alcCaptureStart != 0 && caps.alcCaptureStop != 0 && caps.alcCaptureSamples != 0;
    }
}
