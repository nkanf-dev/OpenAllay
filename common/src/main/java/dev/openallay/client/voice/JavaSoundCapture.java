package dev.openallay.client.voice;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/** Uses the installed JavaSound provider for capture and any device-format conversion. */
public final class JavaSoundCapture implements AudioCapture.Factory {
    public static final String DEFAULT_DEVICE_ID = "default";
    private static final AudioFormat FORMAT = new AudioFormat(16_000, 16, 1, true, false);
    private static final int LINE_BUFFER_BYTES = 6_400;
    private static final int MAX_READ_BYTES = 4_096;
    private static final long EMPTY_READ_PARK_NANOS = 2_000_000L;
    private final LineProvider lines;
    private final PermissionPreflight permission;
    private final Semaphore openingSlot = new Semaphore(1);
    private final long openTimeoutNanos;

    /** Does not enumerate devices, load macOS frameworks, or open a microphone. */
    public JavaSoundCapture() {
        this(new SystemLines(), MacMicrophonePermission::checkBeforeOpen);
    }

    JavaSoundCapture(LineProvider lines, PermissionPreflight permission) {
        this(lines, permission, Duration.ofSeconds(10));
    }

    JavaSoundCapture(LineProvider lines, PermissionPreflight permission, Duration openTimeout) {
        this.lines = Objects.requireNonNull(lines);
        this.permission = Objects.requireNonNull(permission);
        if (openTimeout.isZero() || openTimeout.isNegative()) throw new IllegalArgumentException("openTimeout");
        this.openTimeoutNanos = openTimeout.toNanos();
    }

    @Override
    public AudioCapture open(String deviceId, VoiceCancellation cancellation) throws Exception {
        Objects.requireNonNull(cancellation).check();
        long deadline = System.nanoTime() + openTimeoutNanos;
        // A stuck native provider cannot be forcibly interrupted by Java. Keep at most one
        // outstanding opener per factory, and fail subsequent attempts within the same deadline.
        while (!openingSlot.tryAcquire(Math.min(TimeUnit.MILLISECONDS.toNanos(25),
                Math.max(0, deadline - System.nanoTime())), TimeUnit.NANOSECONDS)) {
            cancellation.check();
            if (System.nanoTime() >= deadline) throw new CaptureException(Failure.OPEN_BUSY,
                    "The previous microphone open has not stopped. Restart the game or select another capture provider.");
        }
        Opening opening = new Opening(deviceId, cancellation);
        boolean transferred = false;
        try (AutoCloseable hook = cancellation.onCancel(opening::abort)) {
            opening.thread = Thread.ofVirtual().name("openallay-microphone-open").unstarted(opening::run);
            opening.thread.start();
            try {
                AudioCapture capture = opening.result.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                cancellation.check();
                transferred = true;
                return capture;
            } catch (TimeoutException failure) {
                throw new CaptureException(Failure.OPEN_TIMEOUT,
                        "Opening the microphone timed out. Check the device and microphone permission.");
            } catch (ExecutionException failure) {
                cancellation.check();
                Throwable cause = failure.getCause();
                if (cause instanceof Exception exception) throw exception;
                if (cause instanceof LinkageError linkage) throw new CaptureException(Failure.OPEN_FAILED,
                        "The JavaSound capture provider could not load.", linkage);
                throw new CaptureException(Failure.OPEN_FAILED, "Could not open the microphone.");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Microphone open interrupted");
            }
        } finally {
            if (!transferred) opening.abort();
        }
    }

    /** Owns permission, line acquisition, open and start before a stream can be transferred. */
    private final class Opening {
        private final String deviceId;
        private final VoiceCancellation cancellation;
        private final CompletableFuture<AudioCapture> result = new CompletableFuture<>();
        private final AtomicBoolean aborted = new AtomicBoolean();
        private final AtomicBoolean closeScheduled = new AtomicBoolean();
        private final AtomicReference<Session> selected = new AtomicReference<>();
        private volatile Thread thread;
        Opening(String deviceId, VoiceCancellation cancellation) {
            this.deviceId = deviceId; this.cancellation = cancellation;
        }
        private void check() {
            cancellation.check();
            if (aborted.get()) throw new CancellationException("Microphone open abandoned");
        }
        private void abort() {
            aborted.set(true);
            result.cancel(false);
            Thread opener = thread;
            if (opener != null) opener.interrupt();
            Session session = selected.get();
            if (session != null && closeScheduled.compareAndSet(false, true)) {
                // Never block a client/cancel callback on a provider's stop/close implementation.
                Thread.ofVirtual().name("openallay-microphone-open-close").start(session::close);
            }
        }
        private void run() {
            Session session = null;
            boolean ready = false;
            Throwable failure = null;
            try {
                check();
                permission.check();
                check();
                TargetDataLine line = Objects.requireNonNull(lines.line(
                        deviceId == null || deviceId.isBlank() ? DEFAULT_DEVICE_ID : deviceId));
                session = new Session(line);
                selected.set(session); // Publish the line before any potentially blocking open/start.
                check();
                line.open(FORMAT, LINE_BUFFER_BYTES);
                check();
                if (!FORMAT.matches(line.getFormat())) throw new CaptureException(Failure.UNSUPPORTED_FORMAT,
                        "The microphone did not provide 16 kHz mono signed 16-bit PCM.");
                line.start();
                check();
                ready = true;
            } catch (Exception known) {
                if (known instanceof CancellationException || aborted.get() || cancellation.cancelled())
                    failure = new CancellationException("Microphone open cancelled");
                else if (known instanceof MacMicrophonePermission.PermissionException || known instanceof CaptureException)
                    failure = known;
                else failure = new CaptureException(known instanceof IllegalArgumentException
                                ? Failure.UNSUPPORTED_FORMAT : Failure.OPEN_FAILED,
                        "Could not open the microphone. Check the selected device and microphone permission.", known);
            } catch (LinkageError linkage) {
                failure = new CaptureException(Failure.OPEN_FAILED,
                        "The JavaSound capture provider could not load.", linkage);
            } finally {
                // An abandoned open may finish after close() on an unopened line. Close again
                // after that late return so it cannot resurrect recording or leak a device.
                if ((!ready || aborted.get() || cancellation.cancelled()) && session != null) {
                    closeScheduled.set(true);
                    if (aborted.get() || cancellation.cancelled()) release(session.line);
                    else session.close();
                }
                openingSlot.release();
            }
            if (failure instanceof CancellationException || aborted.get() || cancellation.cancelled()) result.cancel(false);
            else if (failure != null) result.completeExceptionally(failure);
            else if (!result.complete(session) && session != null) release(session.line);
        }
    }

    @Override
    public List<AudioCapture.Device> devices() {
        // No permission preflight or line.open here: listing must never request access.
        return List.copyOf(lines.devices());
    }

    public enum Failure {
        DEVICE_UNAVAILABLE, UNSUPPORTED_FORMAT, OPEN_FAILED, OPEN_TIMEOUT, OPEN_BUSY, READ_FAILED, DEVICE_DISCONNECTED
    }

    public static final class CaptureException extends Exception {
        private final Failure failure;

        CaptureException(Failure failure, String message) {
            super(message);
            this.failure = failure;
        }

        CaptureException(Failure failure, String message, Throwable cause) {
            super(message, cause);
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }

    @FunctionalInterface
    interface PermissionPreflight {
        void check() throws Exception;
    }

    interface LineProvider {
        TargetDataLine line(String deviceId) throws Exception;
        List<AudioCapture.Device> devices();
    }

    private static final class Session implements AudioCapture {
        private final TargetDataLine line;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Session(TargetDataLine line) {
            this.line = line;
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
