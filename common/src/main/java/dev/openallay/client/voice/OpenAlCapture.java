package dev.openallay.client.voice;

import dev.openallay.client.voice.AudioCapture.CaptureException;
import dev.openallay.client.voice.AudioCapture.Failure;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Uses Minecraft's bundled OpenAL capture provider, including its native format conversion. */
public final class OpenAlCapture implements dev.openallay.client.voice.AudioCapture.Factory {
    public static final String DEFAULT_DEVICE_ID = "default";
    private static final int SAMPLE_RATE = 16_000;
    private static final int BUFFER_FRAMES = 3_200;
    private static final int FRAME_BYTES = 2;
    private static final int MAX_READ_BYTES = 4_096;
    private final NativePort<?> nativePort;
    private final CaptureOpenOwner opening;

    /** Does not load a library, enumerate devices, check permission, or open capture. */
    public OpenAlCapture() {
        this(new NativeOpenAlCapturePort(), MacMicrophonePermission::checkBeforeOpen, Duration.ofSeconds(10));
    }

    <D> OpenAlCapture(NativePort<D> nativePort, CaptureOpenOwner.PermissionPreflight permission, Duration timeout) {
        this.nativePort = Objects.requireNonNull(nativePort);
        opening = new CaptureOpenOwner(permission, deviceId -> acquire(nativePort, deviceId), timeout);
    }

    @Override public AudioCapture open(String deviceId, VoiceCancellation cancellation) throws Exception {
        return opening.open(deviceId, cancellation);
    }

    private <D> CaptureOpenOwner.Prepared acquire(NativePort<D> port, String deviceId) throws CaptureException {
        requireAvailable();
        if (deviceId == null || dev.openallay.util.Java8Strings.isBlank(deviceId) || DEFAULT_DEVICE_ID.equals(deviceId)) {
            // null is OpenAL's default-device selector. A translated display name is never an ID.
            return new Session<>(port, null);
        }
        String name = deviceNames().get(deviceId);
        if (name == null) throw new CaptureException(Failure.DEVICE_UNAVAILABLE,
                "The selected microphone is no longer available. Refresh devices and select one.");
        return new Session<>(port, name);
    }

    @Override public List<AudioCapture.Device> devices() throws CaptureException {
        // Metadata only. Neither permission preflight nor alcCaptureOpenDevice belongs here.
        requireAvailable();
        Map<String, AudioCapture.Device> devices = new LinkedHashMap<>();
        devices.put(DEFAULT_DEVICE_ID, new AudioCapture.Device(DEFAULT_DEVICE_ID, "System default microphone"));
        deviceNames().forEach((id, name) -> devices.put(id, new AudioCapture.Device(id, name)));
        return dev.openallay.util.Java8Collections.listCopyOf(devices.values());
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
            if (name == null || dev.openallay.util.Java8Strings.isBlank(name)) continue;
            String id = "openal:" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(name.getBytes(StandardCharsets.UTF_8));
            result.putIfAbsent(id, name); // OpenAL selects by name; duplicate names cannot select separate devices.
        }
        return result;
    }

    /** Fake ports in tests never load LWJGL, touch a physical device or ask OS permission. */
    enum Connection { CONNECTED, DISCONNECTED, UNAVAILABLE }

    interface NativePort<D> {
        boolean available();
        List<String> names();
        D open(String name, int sampleRate, int bufferFrames) throws CaptureException;
        void start(D device) throws CaptureException;
        Connection connection(D device) throws CaptureException;
        int availableFrames(D device) throws CaptureException;
        void read(D device, byte[] target, int frames) throws CaptureException;
        void stop(D device);
        void close(D device);
    }

    private static final class Session<D> implements dev.openallay.client.voice.CaptureOpenOwner.Prepared {
        private final NativePort<D> nativePort;
        private final String name;
        // A native handle must not be freed while read/start is using it. Open runs outside
        // this lock, so cancellation can fence an uninterruptible native open immediately.
        private final Object lock = new Object();
        private D device;
        private boolean closed;
        Session(NativePort<D> nativePort, String name) { this.nativePort = nativePort; this.name = name; }

        @Override public void open() throws CaptureException {
            synchronized (lock) {
                if (closed) throw new java.util.concurrent.CancellationException("Microphone open cancelled");
            }
            D opened = nativePort.open(name, SAMPLE_RATE, BUFFER_FRAMES);
            if (opened == null) throw new CaptureException(Failure.OPEN_FAILED, "Could not open the microphone.");
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
                    if (nativePort.connection(device) == Connection.DISCONNECTED) throw new CaptureException(Failure.DEVICE_DISCONNECTED,
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
            D selected;
            synchronized (lock) {
                if (closed) return;
                closed = true;
                selected = device;
                device = null;
            }
            release(selected);
        }

        private void release(D selected) {
            if (selected == null) return;
            try { nativePort.stop(selected); }
            catch (RuntimeException | LinkageError ignored) { /* Still release a broken device. */ }
            finally {
                try { nativePort.close(selected); }
                catch (RuntimeException | LinkageError ignored) { /* Cleanup cannot break cancellation. */ }
            }
        }
    }

}
