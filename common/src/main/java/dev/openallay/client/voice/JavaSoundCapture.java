package dev.openallay.client.voice;

import dev.openallay.client.voice.AudioCapture.CaptureException;
import dev.openallay.client.voice.AudioCapture.Failure;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/** Uses the installed JavaSound provider for capture and any device-format conversion. */
public final class JavaSoundCapture implements AudioCapture.Factory {
    public static final String DEFAULT_DEVICE_ID = OpenAlCapture.DEFAULT_DEVICE_ID;
    private static final AudioFormat FORMAT = new AudioFormat(16_000, 16, 1, true, false);
    private static final int LINE_BUFFER_BYTES = 6_400;
    private static final int MAX_READ_BYTES = 4_096;
    private static final long EMPTY_READ_PARK_NANOS = 2_000_000L;
    private final LineProvider lines;
    private final CaptureOpenOwner opening;

    /** Does not enumerate devices, load macOS frameworks, or open a microphone. */
    public JavaSoundCapture() {
        this(new SystemLines(), MacMicrophonePermission::checkBeforeOpen);
    }

    JavaSoundCapture(LineProvider lines, CaptureOpenOwner.PermissionPreflight permission) {
        this(lines, permission, Duration.ofSeconds(10));
    }

    JavaSoundCapture(LineProvider lines, CaptureOpenOwner.PermissionPreflight permission, Duration openTimeout) {
        this.lines = Objects.requireNonNull(lines);
        opening = new CaptureOpenOwner(permission, deviceId -> new Session(Objects.requireNonNull(lines.line(
                deviceId == null || deviceId.isBlank() ? DEFAULT_DEVICE_ID : deviceId))), openTimeout);
    }

    @Override
    public AudioCapture open(String deviceId, VoiceCancellation cancellation) throws Exception {
        return opening.open(deviceId, cancellation);
    }

    @Override
    public List<AudioCapture.Device> devices() {
        // No permission preflight or line.open here: listing must never request access.
        return List.copyOf(lines.devices());
    }

    interface LineProvider {
        TargetDataLine line(String deviceId) throws Exception;
        List<AudioCapture.Device> devices();
    }

    private static final class Session implements CaptureOpenOwner.Prepared {
        private final TargetDataLine line;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Session(TargetDataLine line) {
            this.line = line;
        }

        @Override
        public void open() throws Exception {
            line.open(FORMAT, LINE_BUFFER_BYTES);
            if (!FORMAT.matches(line.getFormat())) throw new CaptureException(Failure.UNSUPPORTED_FORMAT,
                    "The microphone did not provide 16 kHz mono signed 16-bit PCM.");
        }

        @Override public void start() { line.start(); }

        @Override public void closeAfterAbandonedOpen() {
            closed.set(true);
            release(line);
        }

        @Override
        public int read(byte[] buffer) throws Exception {
            Objects.requireNonNull(buffer);
            if (closed.get()) return -1;
            if (buffer.length < FORMAT.getFrameSize()) {
                throw new IllegalArgumentException("The capture buffer must hold at least one PCM frame.");
            }
            try {
                if (!line.isOpen() || !line.isRunning()) {
                    if (closed.get()) return -1;
                    throw new CaptureException(Failure.DEVICE_DISCONNECTED,
                            "The microphone stopped or disconnected. Select a device and try again.");
                }
                int available = line.available();
                if (available < 0) {
                    throw new CaptureException(Failure.READ_FAILED,
                            "The microphone returned an invalid available byte count.");
                }
                int length = Math.min(Math.min(available, buffer.length), MAX_READ_BYTES);
                length -= length % FORMAT.getFrameSize();
                if (length == 0) {
                    // Runtime owns the loop. Park only its capture worker, never a client thread.
                    LockSupport.parkNanos(EMPTY_READ_PARK_NANOS);
                    return closed.get() ? -1 : 0;
                }
                // JavaSound guarantees a read up to available() bytes does not block.
                int count = line.read(buffer, 0, length);
                if (closed.get()) return -1;
                if (count < 0 || count > length || count % FORMAT.getFrameSize() != 0) {
                    throw new CaptureException(Failure.READ_FAILED,
                            "The microphone returned an invalid PCM byte count.");
                }
                return count;
            } catch (CaptureException failure) {
                close();
                throw failure;
            } catch (RuntimeException | LinkageError failure) {
                if (closed.get()) return -1;
                close();
                throw new CaptureException(Failure.READ_FAILED,
                        "Could not read the microphone. Check the device and try again.", failure);
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) release(line);
        }
    }

    private static void release(TargetDataLine line) {
        if (line == null) return;
        try {
            line.close();
        } catch (RuntimeException | LinkageError ignored) {
            // A broken provider must not prevent closing the device.
        } finally {
            try {
                line.stop();
            } catch (RuntimeException | LinkageError ignored) {
                // close() has no checked failure path and is safe during cancellation.
            }
        }
    }

    private static final class SystemLines implements LineProvider {
        private final DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);

        @Override
        public TargetDataLine line(String deviceId) throws Exception {
            if (DEFAULT_DEVICE_ID.equals(deviceId)) return AudioSystem.getTargetDataLine(FORMAT);
            for (MixerDevice device : mixerDevices()) {
                if (device.id().equals(deviceId)) {
                    return (TargetDataLine) AudioSystem.getMixer(device.info()).getLine(info);
                }
            }
            throw new CaptureException(Failure.DEVICE_UNAVAILABLE,
                    "The selected microphone is no longer available. Refresh devices and select one.");
        }

        @Override
        public List<AudioCapture.Device> devices() {
            List<AudioCapture.Device> result = new ArrayList<>();
            result.add(new AudioCapture.Device(DEFAULT_DEVICE_ID, "System default microphone"));
            for (MixerDevice device : mixerDevices()) {
                result.add(new AudioCapture.Device(device.id(), device.info().getName()));
            }
            return result;
        }

        private List<MixerDevice> mixerDevices() {
            List<MixerDevice> result = new ArrayList<>();
            Map<String, Integer> duplicates = new HashMap<>();
            for (Mixer.Info mixer : AudioSystem.getMixerInfo()) {
                String key = mixer.getName() + "\0" + mixer.getVendor() + "\0"
                        + mixer.getDescription() + "\0" + mixer.getVersion();
                int occurrence = duplicates.merge(key, 1, Integer::sum);
                String id = "javasound:" + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(key.getBytes(StandardCharsets.UTF_8)) + ":" + occurrence;
                try {
                    if (AudioSystem.getMixer(mixer).isLineSupported(info)) {
                        result.add(new MixerDevice(id, mixer));
                    }
                } catch (IllegalArgumentException | SecurityException unavailable) {
                    // Skip an inaccessible mixer without opening it or requesting permission.
                }
            }
            return result;
        }
    }

    private record MixerDevice(String id, Mixer.Info info) {}
}
