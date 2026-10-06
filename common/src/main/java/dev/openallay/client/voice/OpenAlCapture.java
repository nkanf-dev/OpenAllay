package dev.openallay.client.voice;

import dev.openallay.client.voice.AudioCapture.CaptureException;
import dev.openallay.client.voice.AudioCapture.Failure;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALUtil;
import org.lwjgl.openal.EXTDisconnect;
import org.lwjgl.system.MemoryStack;

/** Uses Minecraft's bundled OpenAL capture provider, including its native format conversion. */
public final class OpenAlCapture implements dev.openallay.client.voice.AudioCapture.Factory {
    public static final String DEFAULT_DEVICE_ID = "default";
    private static final int SAMPLE_RATE = 16_000;
    private static final int BUFFER_FRAMES = 3_200;
    private static final int FRAME_BYTES = 2;
    private static final int MAX_READ_BYTES = 4_096;
    private final NativePort nativePort;
    private final CaptureOpenOwner opening;

    /** Does not load a library, enumerate devices, check permission, or open capture. */
    public OpenAlCapture() {
        this(new LwjglPort(), MacMicrophonePermission::checkBeforeOpen, Duration.ofSeconds(10));
    }

    OpenAlCapture(NativePort nativePort, CaptureOpenOwner.PermissionPreflight permission, Duration timeout) {
        this.nativePort = Objects.requireNonNull(nativePort);
        opening = new CaptureOpenOwner(permission, this::acquire, timeout);
    }

    @Override public AudioCapture open(String deviceId, VoiceCancellation cancellation) throws Exception {
        return opening.open(deviceId, cancellation);
    }

    private CaptureOpenOwner.Prepared acquire(String deviceId) throws CaptureException {
        requireAvailable();
        if (deviceId == null || deviceId.isBlank() || DEFAULT_DEVICE_ID.equals(deviceId)) {
            // null is OpenAL's default-device selector. A translated display name is never an ID.
            return new Session(null);
        }
        String name = deviceNames().get(deviceId);
        if (name == null) throw new CaptureException(Failure.DEVICE_UNAVAILABLE,
                "The selected microphone is no longer available. Refresh devices and select one.");
        return new Session(name);
    }

    @Override public List<AudioCapture.Device> devices() throws CaptureException {
        // Metadata only. Neither permission preflight nor alcCaptureOpenDevice belongs here.
        requireAvailable();
        Map<String, AudioCapture.Device> devices = new LinkedHashMap<>();
        devices.put(DEFAULT_DEVICE_ID, new AudioCapture.Device(DEFAULT_DEVICE_ID, "System default microphone"));
        deviceNames().forEach((id, name) -> devices.put(id, new AudioCapture.Device(id, name)));
        return List.copyOf(devices.values());
    }

    private void requireAvailable() throws CaptureException {
        try {
            if (nativePort.available()) return;
        } catch (RuntimeException | LinkageError failure) {
            throw new CaptureException(Failure.BACKEND_UNAVAILABLE,
                    "The game's OpenAL microphone capture provider could not load.", failure);
        }
        throw new CaptureException(Failure.BACKEND_UNAVAILABLE,
                "The game's OpenAL runtime does not support microphone capture.");
    }

    private Map<String, String> deviceNames() throws CaptureException {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> names;
        try { names = nativePort.names(); }
        catch (RuntimeException | LinkageError failure) {
            throw new CaptureException(Failure.BACKEND_UNAVAILABLE,
                    "The game's OpenAL capture device list is unavailable.", failure);
        }
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            String id = "openal:" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(name.getBytes(StandardCharsets.UTF_8));
            result.putIfAbsent(id, name); // OpenAL selects by name; duplicate names cannot select separate devices.
        }
        return result;
    }

    /** Fake ports in tests never load LWJGL, touch a physical device or ask OS permission. */
    interface NativePort {
        boolean available();
        List<String> names();
        long open(String name, int sampleRate, int bufferFrames) throws CaptureException;
        void start(long device) throws CaptureException;
        boolean connected(long device) throws CaptureException;
        int availableFrames(long device) throws CaptureException;
        void read(long device, byte[] target, int frames) throws CaptureException;
        void stop(long device);
        void close(long device);
    }

    private final class Session implements dev.openallay.client.voice.CaptureOpenOwner.Prepared {
        private final String name;
        // A native handle must not be freed while read/start is using it. Open runs outside
        // this lock, so cancellation can fence an uninterruptible native open immediately.
        private final Object lock = new Object();
        private long device;
        private boolean closed;
        Session(String name) { this.name = name; }

        @Override public void open() throws CaptureException {
            synchronized (lock) {
                if (closed) throw new java.util.concurrent.CancellationException("Microphone open cancelled");
            }
            long opened = nativePort.open(name, SAMPLE_RATE, BUFFER_FRAMES);
            if (opened == 0) throw new CaptureException(Failure.OPEN_FAILED, "Could not open the microphone.");
            synchronized (lock) {
                if (!closed) { device = opened; return; }
            }
            // Cancel may precede the native handle: release that late return without starting it.
            release(opened);
            throw new java.util.concurrent.CancellationException("Microphone open cancelled");
        }

        @Override public void start() throws CaptureException {
            synchronized (lock) {
                if (closed) throw new java.util.concurrent.CancellationException("Microphone open cancelled");
                nativePort.start(device);
            }
        }

        @Override public int read(byte[] buffer) throws CaptureException {
            Objects.requireNonNull(buffer);
            if (buffer.length < FRAME_BYTES) throw new IllegalArgumentException("The capture buffer must hold one PCM frame.");
            synchronized (lock) {
                if (closed) return -1;
                try {
                    if (!nativePort.connected(device)) throw new CaptureException(Failure.DEVICE_DISCONNECTED,
                            "The microphone stopped or disconnected. Select a device and try again.");
                    int available = nativePort.availableFrames(device);
                    if (available < 0) throw new CaptureException(Failure.READ_FAILED,
                            "The microphone returned an invalid available sample count.");
                    int frames = Math.min(available, Math.min(buffer.length, MAX_READ_BYTES) / FRAME_BYTES);
                    if (frames == 0) return 0;
                    // OpenAL capture of at most ALC_CAPTURE_SAMPLES frames is nonblocking.
                    nativePort.read(device, buffer, frames);
                    return frames * FRAME_BYTES;
                } catch (CaptureException failure) {
                    close();
                    throw failure;
                } catch (RuntimeException | LinkageError failure) {
                    close();
                    throw new CaptureException(Failure.READ_FAILED,
                            "Could not read the microphone. Check the device and try again.", failure);
                }
            }
        }

        @Override public void close() {
            long selected;
            synchronized (lock) {
                if (closed) return;
                closed = true;
                selected = device;
                device = 0;
            }
            release(selected);
        }

        private void release(long selected) {
            if (selected == 0) return;
            try { nativePort.stop(selected); }
            catch (RuntimeException | LinkageError ignored) { /* Still release a broken device. */ }
            finally {
                try { nativePort.close(selected); }
                catch (RuntimeException | LinkageError ignored) { /* Cleanup cannot break cancellation. */ }
            }
        }
    }

    /** Does not create/destroy ALC, change game playback contexts, or use model-runtime IPC. */
    private static final class LwjglPort implements NativePort {
        @Override public boolean available() {
            return NativeOpenAlCaptureFacts.available();
        }
        @Override public List<String> names() {
            List<String> names = ALUtil.getStringList(0, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER);
            return names == null ? List.of() : names;
        }
        @Override public long open(String name, int sampleRate, int bufferFrames) throws CaptureException {
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
        @Override public void start(long device) throws CaptureException {
            ALC11.alcCaptureStart(device);
            check(device, Failure.OPEN_FAILED);
        }
        @Override public boolean connected(long device) throws CaptureException {
            if (!ALC10.alcIsExtensionPresent(device, "ALC_EXT_disconnect")) return true;
            boolean connected = ALC10.alcGetInteger(device, EXTDisconnect.ALC_CONNECTED) != 0;
            check(device, Failure.READ_FAILED);
            return connected;
        }
        @Override public int availableFrames(long device) throws CaptureException {
            int frames = ALC10.alcGetInteger(device, ALC11.ALC_CAPTURE_SAMPLES);
            check(device, Failure.READ_FAILED);
            return frames;
        }
        @Override public void read(long device, byte[] target, int frames) throws CaptureException {
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
        @Override public void stop(long device) { ALC11.alcCaptureStop(device); }
        @Override public void close(long device) { ALC11.alcCaptureCloseDevice(device); }
        private static void check(long device, Failure failure) throws CaptureException {
            if (ALC10.alcGetError(device) != ALC10.ALC_NO_ERROR)
                throw new CaptureException(failure, "The OpenAL microphone operation failed.");
        }
    }
}
