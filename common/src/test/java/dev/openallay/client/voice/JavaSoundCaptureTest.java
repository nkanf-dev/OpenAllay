package dev.openallay.client.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import org.junit.jupiter.api.Test;

/** No production AudioSystem provider, native library, real microphone, or OS permission call. */
final class JavaSoundCaptureTest {
    @Test
    void constructionAndExplicitDeviceRefreshDoNotCheckPermissionOrAcquireALine() {
        FakeLines lines = new FakeLines();
        int[] permissionChecks = {0};
        JavaSoundCapture factory = new JavaSoundCapture(lines, () -> permissionChecks[0]++);

        assertEquals(0, lines.deviceRefreshes);
        assertEquals(0, lines.acquisitions);
        List<AudioCapture.Device> devices = factory.devices();
        assertEquals(List.of(new AudioCapture.Device("default", "System default microphone"),
                new AudioCapture.Device("fake", "Fake microphone")), devices);
        assertEquals(1, lines.deviceRefreshes);
        assertEquals(0, lines.acquisitions);
        assertEquals(0, permissionChecks[0]);
        assertThrows(UnsupportedOperationException.class,
                () -> devices.add(new AudioCapture.Device("other", "Other")));
    }

    @Test
    void explicitOpenChecksPermissionFirstAndStartsExactStandardPcm() throws Exception {
        FakeLines lines = new FakeLines();
        int[] permissionChecks = {0};
        JavaSoundCapture factory = new JavaSoundCapture(lines, () -> {
            assertEquals(0, lines.acquisitions);
            permissionChecks[0]++;
        });
        try (AudioCapture ignored = factory.open("fake")) {
            assertEquals(1, permissionChecks[0]);
            assertEquals("fake", lines.lastDeviceId);
            assertEquals(0, lines.deviceRefreshes);
            assertEquals(1, lines.line.opens);
            assertEquals(1, lines.line.starts);
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, lines.line.format.getEncoding());
            assertEquals(16_000f, lines.line.format.getSampleRate());
            assertEquals(16_000f, lines.line.format.getFrameRate());
            assertEquals(16, lines.line.format.getSampleSizeInBits());
            assertEquals(1, lines.line.format.getChannels());
            assertEquals(2, lines.line.format.getFrameSize());
            assertFalse(lines.line.format.isBigEndian());
        }
    }

    @Test
    void blankDeviceUsesSystemDefault() throws Exception {
        FakeLines lines = new FakeLines();
        JavaSoundCapture factory = factory(lines);
        for (String id : new String[] {null, "", "  ", "default"}) {
            try (AudioCapture ignored = factory.open(id)) {
                assertEquals("default", lines.lastDeviceId);
            }
        }
    }

    @Test
    void emptyOrIncompleteAvailableFrameNeverCallsBlockingRead() throws Exception {
        FakeLines lines = new FakeLines();
        try (AudioCapture capture = factory(lines).open("fake")) {
            lines.line.availableBytes = 0;
            assertEquals(0, capture.read(new byte[4096]));
            lines.line.availableBytes = 1;
            assertEquals(0, capture.read(new byte[4096]));
            assertEquals(0, lines.line.reads);
        }
    }

    @Test
    void readUsesOnlyAvailableWholeFramesAndPreservesRawPcm() throws Exception {
        FakeLines lines = new FakeLines();
        try (AudioCapture capture = factory(lines).open("fake")) {
            lines.line.availableBytes = 7;
            byte[] buffer = new byte[9];
            assertEquals(6, capture.read(buffer));
            assertEquals(6, lines.line.lastReadLength);
            assertArrayEquals(new byte[] {0, 1, 2, 3, 4, 5, 0, 0, 0}, buffer);
            assertEquals(1, lines.line.reads);
        }
    }

    @Test
    void readIsBoundedByBufferAnd4096ByteMaximum() throws Exception {
        FakeLines lines = new FakeLines();
        try (AudioCapture capture = factory(lines).open("fake")) {
            lines.line.availableBytes = 20_000;
            assertEquals(4096, capture.read(new byte[8192]));
            assertEquals(4096, lines.line.lastReadLength);
            lines.line.availableBytes = 20_000;
            assertEquals(4, capture.read(new byte[5]));
            assertEquals(4, lines.line.lastReadLength);
        }
    }

    @Test
    void closeIsIdempotentAndReadAfterCloseEndsWithoutCallingTheLine() throws Exception {
        FakeLines lines = new FakeLines();
        AudioCapture capture = factory(lines).open("fake");
        capture.close();
        capture.close();
        assertEquals(1, lines.line.stops);
        assertEquals(1, lines.line.closes);
        assertEquals(-1, capture.read(new byte[4096]));
        assertEquals(0, lines.line.reads);
        assertEquals(0, lines.line.availableCalls);
    }

    @Test
    void closeStillReleasesDeviceWhenStopFailsAndNeverThrows() throws Exception {
        FakeLines lines = new FakeLines();
        AudioCapture capture = factory(lines).open("fake");
        lines.line.stopFailure = new IllegalStateException("unplugged");
        lines.line.closeFailure = new IllegalStateException("broken close");
        assertDoesNotThrow(capture::close);
        assertDoesNotThrow(capture::close);
        assertEquals(1, lines.line.stops);
        assertEquals(1, lines.line.closes);
    }

    @Test
    void failedOpenClosesAcquiredLineAndHasTypedCause() {
        FakeLines lines = new FakeLines();
        lines.line.openFailure = new LineUnavailableException("busy");
        JavaSoundCapture.CaptureException failure = assertThrows(JavaSoundCapture.CaptureException.class,
                () -> factory(lines).open("fake"));
        assertEquals(JavaSoundCapture.Failure.OPEN_FAILED, failure.failure());
        assertSame(lines.line.openFailure, failure.getCause());
        assertEquals(0, lines.line.starts);
        assertEquals(1, lines.line.stops);
        assertEquals(1, lines.line.closes);
    }

    @Test
    void failedStartClosesAcquiredLine() {
        FakeLines lines = new FakeLines();
        lines.line.startFailure = new IllegalStateException("unplugged");
        JavaSoundCapture.CaptureException failure = assertThrows(JavaSoundCapture.CaptureException.class,
                () -> factory(lines).open("fake"));
        assertEquals(JavaSoundCapture.Failure.OPEN_FAILED, failure.failure());
        assertEquals(1, lines.line.closes);
    }

    @Test
    void wrongProviderFormatIsRejectedWithoutCustomConversion() {
        FakeLines lines = new FakeLines();
        lines.line.reportedFormat = new AudioFormat(48_000, 16, 2, true, false);
        JavaSoundCapture.CaptureException failure = assertThrows(JavaSoundCapture.CaptureException.class,
                () -> factory(lines).open("fake"));
        assertEquals(JavaSoundCapture.Failure.UNSUPPORTED_FORMAT, failure.failure());
        assertEquals(0, lines.line.starts);
        assertEquals(1, lines.line.closes);
    }

    @Test
    void disconnectedDeviceFailsAndReleasesTheLine() throws Exception {
        FakeLines lines = new FakeLines();
        AudioCapture capture = factory(lines).open("fake");
        lines.line.running = false;
        JavaSoundCapture.CaptureException failure = assertThrows(JavaSoundCapture.CaptureException.class,
                () -> capture.read(new byte[4096]));
        assertEquals(JavaSoundCapture.Failure.DEVICE_DISCONNECTED, failure.failure());
        assertEquals(1, lines.line.closes);
        assertEquals(-1, capture.read(new byte[4096]));
        assertEquals(0, lines.line.reads);
    }

    @Test
    void providerReadFailureIsTypedAndClosesTheLine() throws Exception {
        FakeLines lines = new FakeLines();
        AudioCapture capture = factory(lines).open("fake");
        lines.line.availableBytes = 4;
        lines.line.readFailure = new IllegalStateException("broken driver");
        JavaSoundCapture.CaptureException failure = assertThrows(JavaSoundCapture.CaptureException.class,
                () -> capture.read(new byte[4096]));
        assertEquals(JavaSoundCapture.Failure.READ_FAILED, failure.failure());
        assertSame(lines.line.readFailure, failure.getCause());
        assertEquals(1, lines.line.closes);
    }

    @Test
    void invalidProviderByteCountsFailInsteadOfEmittingPartialSamples() throws Exception {
        for (int count : new int[] {-1, 3, 4098}) {
            FakeLines lines = new FakeLines();
            AudioCapture capture = factory(lines).open("fake");
            lines.line.availableBytes = 4;
            lines.line.reportedRead = count;
            JavaSoundCapture.CaptureException failure = assertThrows(JavaSoundCapture.CaptureException.class,
                    () -> capture.read(new byte[4096]));
            assertEquals(JavaSoundCapture.Failure.READ_FAILED, failure.failure());
            assertEquals(1, lines.line.closes);
        }
    }

    @Test
    void negativeAvailableCountFailsWithoutReading() throws Exception {
        FakeLines lines = new FakeLines();
        AudioCapture capture = factory(lines).open("fake");
        lines.line.availableBytes = -1;
        JavaSoundCapture.CaptureException failure = assertThrows(JavaSoundCapture.CaptureException.class,
                () -> capture.read(new byte[4096]));
        assertEquals(JavaSoundCapture.Failure.READ_FAILED, failure.failure());
        assertEquals(0, lines.line.reads);
        assertEquals(1, lines.line.closes);
    }

    @Test
    void buffersSmallerThanOnePcmFrameAreRejected() throws Exception {
        FakeLines lines = new FakeLines();
        try (AudioCapture capture = factory(lines).open("fake")) {
            assertThrows(IllegalArgumentException.class, () -> capture.read(new byte[1]));
            assertThrows(NullPointerException.class, () -> capture.read(null));
            assertEquals(0, lines.line.reads);
        }
    }

    @Test
    void macAuthorizedStatusAllowsOpenWithoutRequiringBundleMetadata() throws Exception {
        FakeAuthorization authorization = new FakeAuthorization(MacMicrophonePermission.Status.AUTHORIZED, false);
        FakeLines lines = new FakeLines();
        try (AudioCapture ignored = new JavaSoundCapture(lines,
                () -> MacMicrophonePermission.check(authorization)).open("fake")) {
            assertEquals(1, lines.acquisitions);
            assertEquals(1, authorization.statusChecks);
            assertEquals(0, authorization.descriptionChecks);
        }
    }

    @Test
    void macDeniedAndRestrictedStatusesFailBeforeLineAcquisition() {
        for (MacMicrophonePermission.Status status : List.of(
                MacMicrophonePermission.Status.DENIED, MacMicrophonePermission.Status.RESTRICTED)) {
            FakeAuthorization authorization = new FakeAuthorization(status, true);
            FakeLines lines = new FakeLines();
            JavaSoundCapture factory = new JavaSoundCapture(lines,
                    () -> MacMicrophonePermission.check(authorization));
            MacMicrophonePermission.PermissionException failure = assertThrows(
                    MacMicrophonePermission.PermissionException.class, () -> factory.open("fake"));
            assertEquals(status == MacMicrophonePermission.Status.DENIED
                    ? MacMicrophonePermission.Failure.DENIED : MacMicrophonePermission.Failure.RESTRICTED,
                    failure.failure());
            assertEquals(0, lines.acquisitions);
            assertEquals(0, lines.line.opens);
            assertEquals(0, authorization.descriptionChecks);
            assertTrue(failure.getMessage().contains(status == MacMicrophonePermission.Status.DENIED
                    ? "Privacy & Security" : "restrictions"));
        }
    }

    @Test
    void macNotDeterminedWithoutUsageDescriptionHasActionableSafeFailure() {
        FakeAuthorization authorization = new FakeAuthorization(MacMicrophonePermission.Status.NOT_DETERMINED, false);
        FakeLines lines = new FakeLines();
        MacMicrophonePermission.PermissionException failure = assertThrows(
                MacMicrophonePermission.PermissionException.class, () -> new JavaSoundCapture(lines,
                        () -> MacMicrophonePermission.check(authorization)).open("fake"));
        assertEquals(MacMicrophonePermission.Failure.LAUNCHER_NOT_PREPARED, failure.failure());
        assertTrue(failure.getMessage().contains("NSMicrophoneUsageDescription"));
        assertTrue(failure.getMessage().contains("does not modify app bundles"));
        assertEquals(0, lines.acquisitions);
        assertEquals(0, lines.line.opens);
    }

    @Test
    void preparedMacHostUsesOnlyStandardExplicitLineOpenNotANativeRequestCallback() throws Exception {
        FakeAuthorization authorization = new FakeAuthorization(MacMicrophonePermission.Status.NOT_DETERMINED, true);
        FakeLines lines = new FakeLines();
        JavaSoundCapture factory = new JavaSoundCapture(lines, () -> MacMicrophonePermission.check(authorization));
        factory.devices();
        assertEquals(0, authorization.statusChecks);
        assertEquals(0, authorization.descriptionChecks);
        try (AudioCapture ignored = factory.open("fake")) {
            assertEquals(1, authorization.statusChecks);
            assertEquals(1, authorization.descriptionChecks);
            assertEquals(1, lines.line.opens);
            assertEquals(1, lines.line.starts);
        }
    }

    @Test
    void unknownMacStatusFailsSafelyBeforeLineAcquisition() {
        FakeAuthorization authorization = new FakeAuthorization(MacMicrophonePermission.Status.UNKNOWN, true);
        FakeLines lines = new FakeLines();
        MacMicrophonePermission.PermissionException failure = assertThrows(
                MacMicrophonePermission.PermissionException.class, () -> new JavaSoundCapture(lines,
                        () -> MacMicrophonePermission.check(authorization)).open("fake"));
        assertEquals(MacMicrophonePermission.Failure.CHECK_FAILED, failure.failure());
        assertEquals(0, lines.acquisitions);
    }

    @Test
    void failedMacNativeCheckIsReportedAsTypedFailureWithoutOpeningALine() {
        FakeLines lines = new FakeLines();
        UnsatisfiedLinkError nativeFailure = new UnsatisfiedLinkError("fake missing framework");
        MacMicrophonePermission.Authorization authorization = new MacMicrophonePermission.Authorization() {
            @Override public MacMicrophonePermission.Status status() { throw nativeFailure; }
            @Override public boolean hasUsageDescription() { throw new AssertionError("must not run"); }
        };
        MacMicrophonePermission.PermissionException failure = assertThrows(
                MacMicrophonePermission.PermissionException.class, () -> new JavaSoundCapture(lines,
                        () -> MacMicrophonePermission.check(authorization)).open("fake"));
        assertEquals(MacMicrophonePermission.Failure.CHECK_FAILED, failure.failure());
        assertSame(nativeFailure, failure.getCause());
        assertEquals(0, lines.acquisitions);
    }

    private static JavaSoundCapture factory(FakeLines lines) {
        return new JavaSoundCapture(lines, () -> {});
    }

    @Test
    void cancellationClosesLineWhileOpenIsBlockedAndNextOpenCanProceed() throws Exception {
        BlockingOpenLine blocked = new BlockingOpenLine(false);
        FakeLine next = new FakeLine();
        java.util.concurrent.atomic.AtomicInteger acquired = new java.util.concurrent.atomic.AtomicInteger();
        JavaSoundCapture factory = new JavaSoundCapture(new JavaSoundCapture.LineProvider() {
            public TargetDataLine line(String device) { return acquired.getAndIncrement() == 0 ? blocked : next; }
            public List<AudioCapture.Device> devices() { return List.of(); }
        }, () -> {}, Duration.ofSeconds(2));
        VoiceCancellation cancellation = new VoiceCancellation();
        CompletableFuture<AudioCapture> first = asyncOpen(factory, cancellation);
        assertTrue(blocked.entered.await(2, TimeUnit.SECONDS));
        cancellation.cancel();
        assertTrue(blocked.closedGate.await(2, TimeUnit.SECONDS), "Selected line must see close before open returns");
        assertCancelled(first);
        assertTrue(blocked.finished.await(2, TimeUnit.SECONDS));
        try (AudioCapture capture = factory.open("next", new VoiceCancellation())) {
            assertTrue(next.isRunning());
        }
        assertFalse(blocked.isOpen());
        assertEquals(0, blocked.starts);
    }

    @Test
    void timeoutAndCancellationFenceUninterruptibleLateOpenWithoutStartingIt() throws Exception {
        BlockingOpenLine late = new BlockingOpenLine(true);
        FakeLines fallback = new FakeLines();
        JavaSoundCapture factory = new JavaSoundCapture(new JavaSoundCapture.LineProvider() {
            int count;
            public TargetDataLine line(String device) { return count++ == 0 ? late : fallback.line; }
            public List<AudioCapture.Device> devices() { return List.of(); }
        }, () -> {}, Duration.ofMillis(100));
        CompletableFuture<AudioCapture> first = asyncOpen(factory, new VoiceCancellation());
        assertTrue(late.entered.await(2, TimeUnit.SECONDS));
        ExecutionException timedOut = assertThrows(ExecutionException.class, () -> first.get(2, TimeUnit.SECONDS));
        assertEquals(JavaSoundCapture.Failure.OPEN_TIMEOUT,
                assertInstanceOf(JavaSoundCapture.CaptureException.class, timedOut.getCause()).failure());
        assertTrue(late.closedGate.await(2, TimeUnit.SECONDS));
        var busy = assertThrows(JavaSoundCapture.CaptureException.class,
                () -> factory.open("busy", new VoiceCancellation()));
        assertEquals(JavaSoundCapture.Failure.OPEN_BUSY, busy.failure());
        late.allowLateReturn.countDown();
        assertTrue(late.finished.await(2, TimeUnit.SECONDS));
        try (AudioCapture capture = factory.open("after-late", new VoiceCancellation())) {
            assertTrue(fallback.line.isRunning());
        }
        assertFalse(late.isOpen(), "A late successful open must be closed again");
        assertEquals(0, late.starts, "Cancelled/timed-out opening must never start capture");
    }

    @Test
    void cancellationDuringPermissionPreflightReturnsBeforePermissionCheckCompletes() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), exit = new CountDownLatch(1);
        FakeLines lines = new FakeLines();
        JavaSoundCapture factory = new JavaSoundCapture(lines, () -> {
            entered.countDown();
            boolean done = false;
            while (!done) {
                try { done = exit.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { /* Fake uninterruptible OS call. */ }
            }
        }, Duration.ofSeconds(2));
        VoiceCancellation cancellation = new VoiceCancellation();
        CompletableFuture<AudioCapture> first = asyncOpen(factory, cancellation);
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS)); cancellation.cancel(); assertCancelled(first);
            assertEquals(0, lines.acquisitions);
        } finally { exit.countDown(); }
    }

    private static CompletableFuture<AudioCapture> asyncOpen(JavaSoundCapture factory, VoiceCancellation cancellation) {
        CompletableFuture<AudioCapture> result = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try { result.complete(factory.open("fake", cancellation)); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }
    private static void assertCancelled(CompletableFuture<AudioCapture> result) {
        assertThrows(CancellationException.class, () -> {
            try { result.get(2, TimeUnit.SECONDS); }
            catch (ExecutionException failure) {
                if (failure.getCause() instanceof CancellationException cancelled) throw cancelled;
                throw failure;
            }
        });
    }
    private static final class BlockingOpenLine extends FakeLine {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch closedGate = new CountDownLatch(1);
        final CountDownLatch allowLateReturn = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        final boolean lateReturn;
        BlockingOpenLine(boolean lateReturn) { this.lateReturn = lateReturn; }
        @Override public void open(AudioFormat format, int size) throws LineUnavailableException {
            entered.countDown();
            try {
                boolean done = false;
                while (!done) {
                    try { done = (lateReturn ? allowLateReturn : closedGate).await(2, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { /* Provider may ignore interrupts. */ }
                }
                if (lateReturn) super.open(format, size);
                else throw new LineUnavailableException("Closed while opening");
            } finally { finished.countDown(); }
        }
        @Override public void close() { super.close(); closedGate.countDown(); }
    }

    private static final class FakeAuthorization implements MacMicrophonePermission.Authorization {
        private final MacMicrophonePermission.Status status;
        private final boolean description;
        private int statusChecks;
        private int descriptionChecks;

        private FakeAuthorization(MacMicrophonePermission.Status status, boolean description) {
            this.status = status;
            this.description = description;
        }

        @Override public MacMicrophonePermission.Status status() { statusChecks++; return status; }
        @Override public boolean hasUsageDescription() { descriptionChecks++; return description; }
    }

    private static final class FakeLines implements JavaSoundCapture.LineProvider {
        private final FakeLine line = new FakeLine();
        private int acquisitions;
        private int deviceRefreshes;
        private String lastDeviceId;

        @Override
        public TargetDataLine line(String deviceId) {
            acquisitions++;
            lastDeviceId = deviceId;
            return line;
        }

        @Override
        public List<AudioCapture.Device> devices() {
            deviceRefreshes++;
            return List.of(new AudioCapture.Device("default", "System default microphone"),
                    new AudioCapture.Device("fake", "Fake microphone"));
        }
    }

    private static class FakeLine implements TargetDataLine {
        private AudioFormat format;
        private AudioFormat reportedFormat;
        private volatile boolean open;
        private volatile boolean running;
        private int availableBytes;
        private int opens;
        volatile int starts;
        private int stops;
        private int closes;
        private int reads;
        private int availableCalls;
        private int lastReadLength;
        private Integer reportedRead;
        private LineUnavailableException openFailure;
        private RuntimeException startFailure;
        private RuntimeException readFailure;
        private RuntimeException stopFailure;
        private RuntimeException closeFailure;

        @Override
        public void open(AudioFormat format, int bufferSize) throws LineUnavailableException {
            opens++;
            if (openFailure != null) throw openFailure;
            this.format = format;
            open = true;
        }

        @Override public void open(AudioFormat format) throws LineUnavailableException { open(format, 6400); }
        @Override public void open() { throw new AssertionError("format must be explicit"); }

        @Override
        public void start() {
            starts++;
            if (startFailure != null) throw startFailure;
            running = true;
        }

        @Override
        public int available() {
            availableCalls++;
            return availableBytes;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            reads++;
            lastReadLength = length;
            assertEquals(0, offset);
            assertTrue(length <= availableBytes, "read must not wait for more bytes");
            assertEquals(0, length % 2, "read must contain whole PCM frames");
            assertTrue(length <= 4096);
            if (readFailure != null) throw readFailure;
            for (int i = 0; i < length; i++) buffer[offset + i] = (byte) i;
            availableBytes -= length;
            return reportedRead == null ? length : reportedRead;
        }

        @Override
        public void stop() {
            stops++;
            running = false;
            if (stopFailure != null) throw stopFailure;
        }

        @Override
        public void close() {
            closes++;
            open = false;
            running = false;
            if (closeFailure != null) throw closeFailure;
        }

        @Override public AudioFormat getFormat() { return reportedFormat == null ? format : reportedFormat; }
        @Override public boolean isOpen() { return open; }
        @Override public boolean isRunning() { return running; }
        @Override public boolean isActive() { return running; }
        @Override public int getBufferSize() { return 6400; }
        @Override public int getFramePosition() { return 0; }
        @Override public long getLongFramePosition() { return 0; }
        @Override public long getMicrosecondPosition() { return 0; }
        @Override public float getLevel() { return 0; }
        @Override public void drain() { throw new AssertionError("capture must not drain"); }
        @Override public void flush() { throw new AssertionError("capture must not discard samples"); }
        @Override public Line.Info getLineInfo() { return new DataLine.Info(TargetDataLine.class, format); }
        @Override public Control[] getControls() { return new Control[0]; }
        @Override public boolean isControlSupported(Control.Type type) { return false; }
        @Override public Control getControl(Control.Type type) { throw new IllegalArgumentException("unsupported"); }
        @Override public void addLineListener(LineListener listener) {}
        @Override public void removeLineListener(LineListener listener) {}
    }
}
