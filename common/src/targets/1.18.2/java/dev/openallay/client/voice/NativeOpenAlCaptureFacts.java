package dev.openallay.client.voice;

import org.lwjgl.openal.ALC;
import org.lwjgl.system.FunctionProviderLocal;

/** Reads the same core capture addresses as LWJGL's native ALC capability constructor. */
final class NativeOpenAlCaptureFacts {
    private NativeOpenAlCaptureFacts() {}
    static boolean available() {
        FunctionProviderLocal provider = ALC.getFunctionProvider();
        return provider.getFunctionAddress("alcCaptureOpenDevice") != 0
                && provider.getFunctionAddress("alcCaptureCloseDevice") != 0
                && provider.getFunctionAddress("alcCaptureStart") != 0
                && provider.getFunctionAddress("alcCaptureStop") != 0
                && provider.getFunctionAddress("alcCaptureSamples") != 0;
    }
}
