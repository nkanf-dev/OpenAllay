package dev.openallay.client.voice;

import dev.openallay.client.voice.AudioCapture.CaptureException;
import dev.openallay.client.voice.AudioCapture.Failure;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALCdevice;

/** Public LWJGL 2 capture bindings. Each lease is the actual ALCdevice, never a playback device. */
final class NativeOpenAlCapturePort implements OpenAlCapture.NativePort<ALCdevice> {
    private static final int FRAME_BYTES = 2;

    @Override public boolean available() { return NativeOpenAlCaptureFacts.available(); }
    @Override public List<String> names() {
        // LWJGL 2's native alcGetString preserves the full double-NUL capture list.
        String names = ALC10.alcGetString(null, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER);
        return names == null ? dev.openallay.util.Java8Collections.listOf() : dev.openallay.util.Java8Collections.toList(Arrays.stream(names.split("\u0000"))
                .filter(name -> !dev.openallay.util.Java8Strings.isBlank(name)));
    }
    @Override public ALCdevice open(String name, int sampleRate, int bufferFrames) throws CaptureException {
        ALCdevice device = ALC11.alcCaptureOpenDevice(name, sampleRate, AL10.AL_FORMAT_MONO16, bufferFrames);
        if (device == null) {
            int error = ALC10.alcGetError(null);
            {
dev.openallay.client.voice.AudioCapture.Failure $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((error)) {
case ALC10.ALC_INVALID_DEVICE:
{
$oaSwitch0_exit_result = Failure.DEVICE_UNAVAILABLE; break $oaSwitch0_exit;
}
case ALC10.ALC_INVALID_VALUE:
case ALC10.ALC_INVALID_ENUM:
{
$oaSwitch0_exit_result = Failure.UNSUPPORTED_FORMAT; break $oaSwitch0_exit;
}
default:
{
$oaSwitch0_exit_result = Failure.OPEN_FAILED; break $oaSwitch0_exit;
}
}
}
throw new CaptureException($oaSwitch0_exit_result, "Could not open the microphone with the game's OpenAL capture provider.");
}
        }
        return device;
    }
    @Override public void start(ALCdevice device) throws CaptureException {
        ALC11.alcCaptureStart(device);
        check(device, Failure.OPEN_FAILED);
    }
    @Override public OpenAlCapture.Connection connection(ALCdevice device) throws CaptureException {
        if (!device.isValid()) return OpenAlCapture.Connection.DISCONNECTED;
        if (!ALC10.alcIsExtensionPresent(device, "ALC_EXT_disconnect"))
            return OpenAlCapture.Connection.UNAVAILABLE;
        // Query the actual extension enum. LWJGL 2 has no EXTDisconnect wrapper.
        int connectedToken = ALC10.alcGetEnumValue(device, "ALC_CONNECTED");
        check(device, Failure.READ_FAILED);
        if (connectedToken == 0) return OpenAlCapture.Connection.UNAVAILABLE;
        return integer(device, connectedToken) != 0
                ? OpenAlCapture.Connection.CONNECTED : OpenAlCapture.Connection.DISCONNECTED;
    }
    @Override public int availableFrames(ALCdevice device) throws CaptureException {
        return integer(device, ALC11.ALC_CAPTURE_SAMPLES);
    }
    @Override public void read(ALCdevice device, byte[] target, int frames) throws CaptureException {
        ByteBuffer pcm = ByteBuffer.allocateDirect(frames * FRAME_BYTES).order(ByteOrder.nativeOrder());
        ALC11.alcCaptureSamples(device, pcm, frames);
        check(device, Failure.READ_FAILED);
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) pcm.get(target, 0, frames * FRAME_BYTES);
        else for (int i = 0; i < frames; i++) {
            short sample = pcm.getShort(i * FRAME_BYTES);
            target[i * FRAME_BYTES] = (byte) sample;
            target[i * FRAME_BYTES + 1] = (byte) (sample >>> 8);
        }
    }
    @Override public void stop(ALCdevice device) { ALC11.alcCaptureStop(device); }
    @Override public void close(ALCdevice device) { ALC11.alcCaptureCloseDevice(device); }
    private static int integer(ALCdevice device, int token) throws CaptureException {
        IntBuffer value = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();
        ALC10.alcGetInteger(device, token, value);
        check(device, Failure.READ_FAILED);
        return value.get(0);
    }
    private static void check(ALCdevice device, Failure failure) throws CaptureException {
        if (ALC10.alcGetError(device) != ALC10.ALC_NO_ERROR)
            throw new CaptureException(failure, "The OpenAL microphone operation failed.");
    }
}
