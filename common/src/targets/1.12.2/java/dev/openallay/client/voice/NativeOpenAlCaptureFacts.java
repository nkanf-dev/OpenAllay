package dev.openallay.client.voice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC10;

/** LWJGL 2 installs its ALC11 capture stubs when the game initializes OpenAL 1.1. */
final class NativeOpenAlCaptureFacts {
    private NativeOpenAlCaptureFacts() {}
    static boolean available() {
        // Never create or replace Minecraft's playback context/native library.
        if (!AL.isCreated() || AL.getDevice() == null) return false;
        IntBuffer value = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();
        ALC10.alcGetInteger(AL.getDevice(), ALC10.ALC_MAJOR_VERSION, value);
        int major = value.get(0);
        ALC10.alcGetInteger(AL.getDevice(), ALC10.ALC_MINOR_VERSION, value);
        int minor = value.get(0);
        return major > 1 || (major == 1 && minor >= 1);
    }
}
