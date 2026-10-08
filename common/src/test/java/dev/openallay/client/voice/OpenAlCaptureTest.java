package dev.openallay.client.voice;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Pure fake capture ports: no OpenAL library, physical device, real audio or OS permission call. */
final class OpenAlCaptureTest {
    @Test void constructionAndExplicitRefreshDoNotCheckPermissionOrOpenCapture() throws Exception {
        FakePort port = new FakePort(); int[] permission = {0};
        OpenAlCapture factory = factory(port, () -> permission[0]++);
        assertEquals(0, port.availabilityChecks); assertEquals(0, port.enumerations); assertEquals(0, port.opens);
        List<AudioCapture.Device> devices = factory.devices();
        assertEquals(2, devices.size());
        assertEquals(new AudioCapture.Device("default", "System default microphone"), devices.getFirst());
        assertEquals("Fake microphone", devices.get(1).name());
        assertTrue(devices.get(1).id().startsWith("openal:"));
        assertEquals(1, port.enumerations); assertEquals(0, port.opens); assertEquals(0, permission[0]);
        assertThrows(UnsupportedOperationException.class, () -> devices.add(new AudioCapture.Device("x", "x")));
    }

    @Test void explicitOpenChecksPermissionBeforeAnyNativeCaptureQuery() throws Exception {
        FakePort port = new FakePort(); int[] permission = {0};
        OpenAlCapture factory = factory(port, () -> {
            assertEquals(0, port.availabilityChecks); assertEquals(0, port.enumerations); assertEquals(0, port.opens);
            permission[0]++;
        });
        try (AudioCapture capture = factory.open("default")) {
            assertEquals(1, permission[0]); assertNull(port.lastName);
            assertEquals(16_000, port.sampleRate); assertEquals(3_200, port.bufferFrames);
            assertEquals(1, port.opens); assertEquals(1, port.starts); assertEquals(0, port.enumerations);
        }
        assertEquals(1, port.stops); assertEquals(1, port.closes);
    }

    @Test void blankSelectionUsesNativeDefaultAndNeverMatchesADisplayName() throws Exception {
        FakePort port = new FakePort(); OpenAlCapture factory = factory(port, () -> {});
        for (String id : new String[] {null, "", "  ", "default"}) {
            try (AudioCapture capture = factory.open(id)) { assertNull(port.lastName); }
        }
        AudioCapture.CaptureException failure = assertThrows(AudioCapture.CaptureException.class,
                () -> factory.open("System default microphone"));
        assertEquals(AudioCapture.Failure.DEVICE_UNAVAILABLE, failure.failure());
        assertEquals(4, port.opens);
    }

    @Test void explicitSelectionReenumeratesStableIdsAndNeverFallsBackWhenUnplugged() throws Exception {
        FakePort port = new FakePort(); OpenAlCapture factory = factory(port, () -> {});
        String id = factory.devices().get(1).id();
        port.names = List.of("Different microphone", "Fake microphone", "Fake microphone");
        try (AudioCapture capture = factory.open(id)) { assertEquals("Fake microphone", port.lastName); }
        assertEquals(2, port.enumerations); assertEquals(3, factory.devices().size());
        port.names = List.of();
        var failure = assertThrows(AudioCapture.CaptureException.class, () -> factory.open(id));
        assertEquals(AudioCapture.Failure.DEVICE_UNAVAILABLE, failure.failure()); assertEquals(1, port.opens);
        // JDK-only IDs also need an explicit native selection, not an arbitrary microphone fallback.
        failure = assertThrows(AudioCapture.CaptureException.class, () -> factory.open("javasound:stale:1"));
        assertEquals(AudioCapture.Failure.DEVICE_UNAVAILABLE, failure.failure()); assertEquals(1, port.opens);
    }

    @Test void missingBackendIsNotReportedAsADeviceOrPermissionFailure() throws Exception {
        FakePort port = new FakePort(); port.available = false;
        OpenAlCapture factory = factory(port, () -> {});
        var refresh = assertThrows(AudioCapture.CaptureException.class, factory::devices);
        assertEquals(AudioCapture.Failure.BACKEND_UNAVAILABLE, refresh.failure());
        var open = assertThrows(AudioCapture.CaptureException.class, () -> factory.open("default"));
        assertEquals(AudioCapture.Failure.BACKEND_UNAVAILABLE, open.failure());
        assertEquals(0, port.enumerations); assertEquals(0, port.opens);
        port.availabilityFailure = new UnsatisfiedLinkError("fake unavailable runtime");
        open = assertThrows(AudioCapture.CaptureException.class, () -> factory.open("default"));
        assertEquals(AudioCapture.Failure.BACKEND_UNAVAILABLE, open.failure());
        assertSame(port.availabilityFailure, open.getCause());
    }

    @Test void enumerationFailureIsTypedWithoutPermissionOrCaptureOpen() {
        FakePort port = new FakePort(); int[] permission = {0};
        port.enumerationFailure = new IllegalStateException("fake runtime stopped");
        var failure = assertThrows(AudioCapture.CaptureException.class,
                () -> factory(port, () -> permission[0]++).devices());
        assertEquals(AudioCapture.Failure.BACKEND_UNAVAILABLE, failure.failure());
        assertSame(port.enumerationFailure, failure.getCause());
        assertEquals(0, permission[0]); assertEquals(0, port.opens);
    }

    @Test void macDeniedRestrictedAndMissingDescriptionFailBeforeBackendOrDeviceCheck() {
        for (MacMicrophonePermission.Status status : List.of(MacMicrophonePermission.Status.DENIED,
                MacMicrophonePermission.Status.RESTRICTED, MacMicrophonePermission.Status.NOT_DETERMINED)) {
            FakePort port = new FakePort(); port.available = false;
            OpenAlCapture factory = factory(port, () -> MacMicrophonePermission.check(new MacMicrophonePermission.Authorization() {
                public MacMicrophonePermission.Status status() { return status; }
                public boolean hasUsageDescription() { return false; }
            }));
            var failure = assertThrows(MacMicrophonePermission.PermissionException.class, () -> factory.open("missing"));
            assertEquals(switch (status) {
                case DENIED -> MacMicrophonePermission.Failure.DENIED;
                case RESTRICTED -> MacMicrophonePermission.Failure.RESTRICTED;
                default -> MacMicrophonePermission.Failure.LAUNCHER_NOT_PREPARED;
            }, failure.failure());
            assertEquals(0, port.availabilityChecks); assertEquals(0, port.enumerations); assertEquals(0, port.opens);
        }
    }

    @Test void nativeDeviceAndFormatErrorsRemainTypedAndCannotStartCapture() {
        for (AudioCapture.Failure kind : List.of(AudioCapture.Failure.DEVICE_UNAVAILABLE,
                AudioCapture.Failure.UNSUPPORTED_FORMAT, AudioCapture.Failure.OPEN_FAILED)) {
            FakePort port = new FakePort(); port.openFailure = new AudioCapture.CaptureException(kind, "fake native error");
            var failure = assertThrows(AudioCapture.CaptureException.class, () -> factory(port, () -> {}).open("default"));
            assertSame(port.openFailure, failure); assertEquals(kind, failure.failure());
            assertEquals(0, port.starts); assertEquals(0, port.closes);
        }
        FakePort port = new FakePort(); port.startFailure = new AudioCapture.CaptureException(
                AudioCapture.Failure.OPEN_FAILED, "fake start failed");
        var failure = assertThrows(AudioCapture.CaptureException.class, () -> factory(port, () -> {}).open("default"));
        assertSame(port.startFailure, failure); assertEquals(1, port.closes);
    }

    @Test void providerCancellingItselfIsAnOpenFailureAndNextExplicitOpenCanProceed() throws Exception {
        FakePort port = new FakePort() {
            boolean cancelOnce = true;
            @Override public Long open(String name, int rate, int frames) throws AudioCapture.CaptureException {
                if (cancelOnce) { cancelOnce = false; throw new CancellationException("Provider cancelled itself"); }
                return super.open(name, rate, frames);
            }
        };
        OpenAlCapture factory = factory(port, () -> {});
        VoiceCancellation cancellation = new VoiceCancellation();
        var failure = assertThrows(AudioCapture.CaptureException.class, () -> factory.open("default", cancellation));
        assertEquals(AudioCapture.Failure.OPEN_FAILED, failure.failure());
        assertInstanceOf(CancellationException.class, failure.getCause());
        assertFalse(cancellation.cancelled());
        assertEquals(0, port.starts); assertEquals(0, port.closes);
        try (AudioCapture capture = factory.open("default", new VoiceCancellation())) {
            assertEquals(1, port.starts);
        }
        assertEquals(1, port.closes);
    }

    @Test void interruptedCallerGetsTypedFailureAndLateNativeHandleIsClosedWithoutStarting() throws Exception {
        BlockingPort port = new BlockingPort();
        OpenAlCapture factory = factory(port, () -> {});
        VoiceCancellation cancellation = new VoiceCancellation();
        CompletableFuture<Throwable> result = new CompletableFuture<>();
        CompletableFuture<Boolean> interruptionPreserved = new CompletableFuture<>();
        Thread caller = Thread.ofVirtual().start(() -> {
            try {
                AudioCapture capture = factory.open("default", cancellation);
                capture.close();
                result.complete(new AssertionError("Interrupted opening unexpectedly succeeded"));
            } catch (Throwable failure) {
                result.complete(failure);
            } finally {
                interruptionPreserved.complete(Thread.currentThread().isInterrupted());
            }
        });
        try {
            assertTrue(port.entered.await(2, TimeUnit.SECONDS));
            caller.interrupt();
            var failure = assertInstanceOf(AudioCapture.CaptureException.class, result.get(2, TimeUnit.SECONDS));
            assertEquals(AudioCapture.Failure.OPEN_FAILED, failure.failure());
            assertInstanceOf(InterruptedException.class, failure.getCause());
            assertTrue(interruptionPreserved.get(2, TimeUnit.SECONDS));
            assertFalse(cancellation.cancelled());
            assertEquals(0, port.starts); assertEquals(0, port.closes);
            port.allowReturn.countDown();
            assertTrue(port.closed.await(2, TimeUnit.SECONDS));
            assertEquals(0, port.starts); assertEquals(1, port.closes);
            try (AudioCapture capture = factory.open("default", new VoiceCancellation())) {
                assertEquals(1, port.starts);
            }
            assertEquals(2, port.closes);
        } finally { port.allowReturn.countDown(); caller.interrupt(); }
    }

    @Test void readsOnlyAvailableWholeFramesAndPreservesPcmBytes() throws Exception {
        FakePort port = new FakePort();
        try (AudioCapture capture = factory(port, () -> {}).open("default")) {
            assertEquals(0, capture.read(new byte[4096])); assertEquals(0, port.reads);
            port.availableFrames = 7; byte[] bytes = new byte[9];
            assertEquals(8, capture.read(bytes)); assertEquals(4, port.lastReadFrames);
            assertArrayEquals(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 0}, bytes);
            port.availableFrames = 20_000; assertEquals(4096, capture.read(new byte[8192]));
            assertEquals(2048, port.lastReadFrames);
            assertThrows(IllegalArgumentException.class, () -> capture.read(new byte[1]));
            assertThrows(NullPointerException.class, () -> capture.read(null));
        }
    }

    @Test void closeIsIdempotentAndReadAfterCloseDoesNotCallNativeProvider() throws Exception {
        FakePort port = new FakePort(); AudioCapture capture = factory(port, () -> {}).open("default");
        capture.close(); capture.close();
        assertEquals(-1, capture.read(new byte[4096]));
        assertEquals(1, port.stops); assertEquals(1, port.closes); assertEquals(0, port.reads);
        assertEquals(0, port.sampleQueries);
    }

    @Test void disconnectAndReadFailureCloseTheNativeHandleAndRemainTyped() throws Exception {
        for (boolean disconnected : List.of(false, true)) {
            FakePort port = new FakePort(); AudioCapture capture = factory(port, () -> {}).open("default");
            port.connected = !disconnected;
            if (!disconnected) { port.availableFrames = 2; port.readFailure = new IllegalStateException("fake driver failure"); }
            var failure = assertThrows(AudioCapture.CaptureException.class, () -> capture.read(new byte[4096]));
            assertEquals(disconnected ? AudioCapture.Failure.DEVICE_DISCONNECTED : AudioCapture.Failure.READ_FAILED,
                    failure.failure());
            assertEquals(1, port.closes); assertEquals(-1, capture.read(new byte[4096]));
        }
    }

    @Test void invalidAvailableCountAndCleanupFailureCannotLeakADevice() throws Exception {
        FakePort port = new FakePort(); AudioCapture capture = factory(port, () -> {}).open("default");
        port.availableFrames = -1; port.stopFailure = new IllegalStateException("fake broken stop");
        port.closeFailure = new IllegalStateException("fake broken close");
        var failure = assertThrows(AudioCapture.CaptureException.class, () -> capture.read(new byte[4096]));
        assertEquals(AudioCapture.Failure.READ_FAILED, failure.failure());
        assertEquals(1, port.stops); assertEquals(1, port.closes); assertEquals(0, port.reads);
        assertDoesNotThrow(capture::close);
    }

    @Test void cancelAndTimeoutFenceAnUninterruptibleNativeOpenAndCloseItsLateHandle() throws Exception {
        for (boolean timeout : List.of(false, true)) {
            BlockingPort port = new BlockingPort();
            OpenAlCapture factory = new OpenAlCapture(port, () -> {}, timeout ? Duration.ofMillis(100) : Duration.ofSeconds(2));
            VoiceCancellation cancellation = new VoiceCancellation();
            CompletableFuture<AudioCapture> result = asyncOpen(factory, cancellation);
            try {
                assertTrue(port.entered.await(2, TimeUnit.SECONDS));
                if (timeout) {
                    ExecutionException failure = assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
                    assertEquals(AudioCapture.Failure.OPEN_TIMEOUT,
                            assertInstanceOf(AudioCapture.CaptureException.class, failure.getCause()).failure());
                } else {
                    cancellation.cancel();
                    assertThrows(CancellationException.class, () -> {
                        try { result.get(2, TimeUnit.SECONDS); }
                        catch (ExecutionException failure) {
                            if (failure.getCause() instanceof CancellationException cancelled) throw cancelled;
                            throw failure;
                        }
                    });
                }
                var busy = assertThrows(AudioCapture.CaptureException.class, () -> factory.open("default"));
                assertEquals(AudioCapture.Failure.OPEN_BUSY, busy.failure());
                port.allowReturn.countDown(); assertTrue(port.closed.await(2, TimeUnit.SECONDS));
                assertEquals(0, port.starts); assertEquals(1, port.closes);
                try (AudioCapture capture = factory.open("default")) {
                    assertEquals(1, port.starts);
                }
                assertEquals(2, port.closes);
            } finally { port.allowReturn.countDown(); }
        }
    }

    private static OpenAlCapture factory(FakePort port, CaptureOpenOwner.PermissionPreflight permission) {
        return new OpenAlCapture(port, permission, Duration.ofSeconds(2));
    }
    private static CompletableFuture<AudioCapture> asyncOpen(OpenAlCapture factory, VoiceCancellation cancellation) {
        CompletableFuture<AudioCapture> result = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try { result.complete(factory.open("default", cancellation)); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }
    private static class FakePort implements OpenAlCapture.NativePort<Long> {
        boolean available = true, connected = true;
        List<String> names = List.of("Fake microphone");
        int availabilityChecks, enumerations, opens, starts, stops, closes, reads, sampleQueries;
        int sampleRate, bufferFrames, availableFrames, lastReadFrames;
        String lastName;
        LinkageError availabilityFailure;
        AudioCapture.CaptureException openFailure, startFailure;
        RuntimeException readFailure, stopFailure, closeFailure, enumerationFailure;
        public boolean available() { availabilityChecks++; if (availabilityFailure != null) throw availabilityFailure; return available; }
        public List<String> names() {
            enumerations++; if (enumerationFailure != null) throw enumerationFailure; return names;
        }
        public Long open(String name, int rate, int frames) throws AudioCapture.CaptureException {
            opens++; lastName = name; sampleRate = rate; bufferFrames = frames;
            if (openFailure != null) throw openFailure;
            return 42L;
        }
        public void start(Long device) throws AudioCapture.CaptureException { starts++; if (startFailure != null) throw startFailure; }
        public OpenAlCapture.Connection connection(Long device) {
            return connected ? OpenAlCapture.Connection.CONNECTED : OpenAlCapture.Connection.DISCONNECTED;
        }
        public int availableFrames(Long device) { sampleQueries++; return availableFrames; }
        public void read(Long device, byte[] target, int frames) {
            reads++; lastReadFrames = frames; assertTrue(frames <= availableFrames); assertTrue(frames * 2 <= 4096);
            if (readFailure != null) throw readFailure;
            for (int i = 0; i < frames * 2; i++) target[i] = (byte) i;
        }
        public void stop(Long device) { stops++; if (stopFailure != null) throw stopFailure; }
        public void close(Long device) { closes++; if (closeFailure != null) throw closeFailure; }
    }
    private static final class BlockingPort extends FakePort {
        final CountDownLatch entered = new CountDownLatch(1), allowReturn = new CountDownLatch(1), closed = new CountDownLatch(1);
        @Override public Long open(String name, int rate, int frames) throws AudioCapture.CaptureException {
            entered.countDown(); boolean done = false;
            while (!done) {
                try { done = allowReturn.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { /* Simulates an uninterruptible native driver. */ }
            }
            return super.open(name, rate, frames);
        }
        @Override public void close(Long device) { super.close(device); closed.countDown(); }
    }
}
