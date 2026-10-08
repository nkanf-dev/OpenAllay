package dev.openallay.client.voice;

import dev.openallay.client.voice.AudioCapture.CaptureException;
import dev.openallay.client.voice.AudioCapture.Failure;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALUtil;
import org.lwjgl.openal.EXTDisconnect;
import org.lwjgl.system.MemoryStack;

/** Does not create/destroy ALC, change game playback contexts, or use model-runtime IPC. */
final class NativeOpenAlCapturePort implements OpenAlCapture.NativePort<Long> {
    private static final int FRAME_BYTES = 2;
    @Override public boolean available() {
        return NativeOpenAlCaptureFacts.available();
    }
    @Override public List<String> names() {
        List<String> names = ALUtil.getStringList(0, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER);
        return names == null ? List.of() : names;
    }
    @Override public Long open(String name, int sampleRate, int bufferFrames) throws CaptureException {
        long device = ALC11.alcCaptureOpenDevice(name, sampleRate, AL10.AL_FORMAT_MONO16, bufferFrames);
        if (device == 0) {
            int error = ALC10.alcGetError(0);
            throw new CaptureException(switch (error) {
                case ALC10.ALC_INVALID_DEVICE -> Failure.DEVICE_UNAVAILABLE;
                case ALC10.ALC_INVALID_VALUE, ALC10.ALC_INVALID_ENUM -> Failure.UNSUPPORTED_FORMAT;
                default -> Failure.OPEN_FAILED;
            }, "Could not open the microphone with the game's OpenAL capture provider.");
        }
        return device;
    }
    @Override public void start(Long device) throws CaptureException {
        ALC11.alcCaptureStart(device);
        check(device, Failure.OPEN_FAILED);
    }
    @Override public OpenAlCapture.Connection connection(Long device) throws CaptureException {
        if (!ALC10.alcIsExtensionPresent(device, "ALC_EXT_disconnect")) return OpenAlCapture.Connection.UNAVAILABLE;
        boolean connected = ALC10.alcGetInteger(device, EXTDisconnect.ALC_CONNECTED) != 0;
        check(device, Failure.READ_FAILED);
        return connected ? OpenAlCapture.Connection.CONNECTED : OpenAlCapture.Connection.DISCONNECTED;
    }
    @Override public int availableFrames(Long device) throws CaptureException {
        int frames = ALC10.alcGetInteger(device, ALC11.ALC_CAPTURE_SAMPLES);
        check(device, Failure.READ_FAILED);
        return frames;
    }
    @Override public void read(Long device, byte[] target, int frames) throws CaptureException {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pcm = stack.malloc(frames * FRAME_BYTES).order(ByteOrder.nativeOrder());
            ALC11.alcCaptureSamples(device, pcm, frames);
            check(device, Failure.READ_FAILED);
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) pcm.get(target, 0, frames * FRAME_BYTES);
            else for (int i = 0; i < frames; i++) {
                short sample = pcm.getShort(i * FRAME_BYTES);
                target[i * FRAME_BYTES] = (byte) sample;
                target[i * FRAME_BYTES + 1] = (byte) (sample >>> 8);
            }
        }
    }
    @Override public void stop(Long device) { ALC11.alcCaptureStop(device); }
    @Override public void close(Long device) { ALC11.alcCaptureCloseDevice(device); }
    private static void check(Long device, Failure failure) throws CaptureException {
        if (ALC10.alcGetError(device) != ALC10.ALC_NO_ERROR)
            throw new CaptureException(failure, "The OpenAL microphone operation failed.");
    }
}
