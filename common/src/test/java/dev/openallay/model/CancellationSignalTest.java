package dev.openallay.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CancellationSignalTest {
    @Test
    void errorsAndLateListenersCannotInterruptCleanup() {
        CancellationSignal signal = new CancellationSignal();
        List<String> calls = new ArrayList<>();
        signal.onCancel(() -> { calls.add("error"); throw new AssertionError("secret-token"); });
        signal.onCancel(() -> { calls.add("thread-death"); throw new ThreadDeath(); });
        signal.onCancel(() -> calls.add("cleanup"));
        assertDoesNotThrow(() -> { signal.cancel(); });
        assertEquals(List.of("error", "thread-death", "cleanup"), calls);
        assertDoesNotThrow(() -> signal.onCancel(() -> { throw new Error("secret-token"); }));
    }

    @Test
    void rejectingExecutorStillNotifiesEveryListenerAfterImmediateRevocation() {
        CancellationSignal signal = new CancellationSignal();
        List<String> calls = new ArrayList<>();
        signal.onCancel(() -> calls.add("cleanup"));
        assertTrue(signal.cancel(command -> { throw new java.util.concurrent.RejectedExecutionException(); }));
        assertTrue(signal.isCancelled());
        assertEquals(List.of("cleanup"), calls);
    }

    @Test
    void executorThatRunsThenThrowsDoesNotNotifyTwice() {
        CancellationSignal signal = new CancellationSignal();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        signal.onCancel(calls::incrementAndGet);
        assertTrue(signal.cancel(command -> { command.run(); throw new AssertionError("secret-token"); }));
        assertEquals(1, calls.get());
    }

    @Test
    void failingListenerDoesNotPreventOtherRevocationsOrTerminalCleanup() {
        CancellationSignal signal = new CancellationSignal();
        List<String> calls = new ArrayList<>();
        signal.onCancel(() -> { calls.add("first"); throw new IllegalStateException("secret-token"); });
        signal.onCancel(() -> calls.add("second"));
        assertTrue(signal.cancel());
        assertTrue(signal.isCancelled());
        assertEquals(List.of("first", "second"), calls);
        assertFalse(signal.cancel());
        assertEquals(List.of("first", "second"), calls);
    }
}
